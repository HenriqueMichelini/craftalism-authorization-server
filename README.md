# Craftalism Authorization Server

OAuth 2.1 and OpenID Connect authorization server for the Craftalism ecosystem.

This service is built for machine-to-machine authentication. It issues short-lived, RSA-signed JWT access tokens for trusted clients (currently the Minecraft game server plugin), exposes standard OAuth/OIDC discovery metadata, and provides a JWKS endpoint so downstream services can validate tokens locally.

## Project Purpose

The project centralizes authentication for internal Craftalism services by:

- registering confidential OAuth clients in a database,
- issuing access tokens via `client_credentials`,
- exposing public key material for token verification,
- supporting token introspection and revocation endpoints,
- enforcing a deny-by-default HTTP policy outside explicitly public endpoints.

## Current Feature Set

Implemented features in the current codebase:

- OAuth 2 token issuance (`client_credentials`) at `POST /oauth2/token`.
- OIDC discovery metadata at `GET /.well-known/openid-configuration`.
- Authorization Server metadata at `GET /.well-known/oauth-authorization-server`.
- JWKS publishing for JWT verification at `GET /oauth2/jwks`.
- OAuth 2 introspection and revocation protocol endpoints (`/oauth2/introspect`, `/oauth2/revoke`) provided by Spring Authorization Server.
- JDBC-backed persistence for clients, authorizations, and consents.
- Startup seeding of one service client (`minecraft-server` by default), idempotently.
- RSA key loading from environment variables, with ephemeral fallback for local development.
- Actuator health endpoint at `GET /actuator/health`.
- Integration tests covering token issuance, JWT shape, public endpoints, and deny-by-default behavior.

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.5
- **Security/Auth:** Spring Security + Spring Authorization Server
- **Database Access:** Spring JDBC
- **Database Runtime:** PostgreSQL
- **Test Database:** H2 (PostgreSQL compatibility mode)
- **Build Tool:** Gradle Wrapper (`./gradlew`)
- **Packaging/Runtime:** Spring Boot executable JAR + Docker multi-stage image

## Architecture Overview

This project follows a layered Spring Boot architecture with explicit configuration:

- **Bootstrapping layer**
  - `Application` starts the Spring context.
- **Security/configuration layer**
  - `AuthorizationServerConfig` wires protocol endpoints, JDBC repositories/services, JWT/JWK components, issuer metadata, and password encoding.
  - `SecurityConfig` defines the fallback filter chain (stateless, health/discovery/JWKS public, all else denied).
  - `RsaKeyConfig` parses configured PEM keys or generates ephemeral keys.
- **Domain/service layer**
  - `ClientRegistrationService` seeds a registered OAuth client at startup.
- **Persistence layer**
  - JDBC repositories/services from Spring Authorization Server persist into tables created by `schema.sql`.

### Request Security Model

Two filter chains are used:

1. **Order(1)**: Authorization Server protocol endpoints (`/oauth2/*`, discovery endpoints) authenticated per OAuth rules.
2. **Order(2)**: fallback chain permits only:
   - `/actuator/health`
   - `/oauth2/jwks`
   - `/.well-known/oauth-authorization-server`
   - `/.well-known/openid-configuration`

Everything else is denied.

## Configuration

The service is configured entirely through environment variables/properties.

### Required environment variables

- `DB_URL` (example: `jdbc:postgresql://localhost:5432/authserver`)
- `DB_USER`
- `DB_PASSWORD`
- `MINECRAFT_CLIENT_SECRET`

### Optional environment variables

- `AUTH_ISSUER_URI` (default: `http://localhost:9000`)
- `MINECRAFT_CLIENT_ID` (default: `minecraft-server`)
- `RSA_PRIVATE_KEY` (PEM string; supports literal `\n`)
- `RSA_PUBLIC_KEY` (PEM string; supports literal `\n`)

> If RSA keys are not provided, the application generates an ephemeral RSA key pair on startup. This is convenient for local testing but not safe for persistent environments because previously issued tokens become unverifiable after restart.

## How to Run

## 1) Local run (Gradle)

```bash
cd java
export DB_URL='jdbc:postgresql://localhost:5432/authserver'
export DB_USER='your_user'
export DB_PASSWORD='your_password'
export MINECRAFT_CLIENT_SECRET='replace_me'
./gradlew bootRun
```

Service default URL: `http://localhost:9000`.

## 2) Run tests

```bash
cd java
./gradlew test
```

Tests use `application-test.properties` and H2 in-memory DB.

## 3) Docker

```bash
cd java
docker compose up --build
```

Notes:

- `docker-compose.yml` assumes an external `craftalism-network` and a healthy `postgres` service already exist.
- A one-shot `auth-db-init` container attempts to create the `authserver` database before starting the app.

## API Usage Example

Request a token using client credentials:

```bash
curl -X POST 'http://localhost:9000/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -u "minecraft-server:${MINECRAFT_CLIENT_SECRET}" \
  -d 'grant_type=client_credentials&scope=api:read api:write'
```

Expected response shape:

```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 299,
  "scope": "api:read api:write"
}
```

Fetch public keys used to verify JWT signatures:

```bash
curl 'http://localhost:9000/oauth2/jwks'
```

## Project Structure

```text
.
├── README.md
└── java
    ├── build.gradle
    ├── docker-compose.yml
    ├── dockerfile
    ├── src/main/java/io/github/HenriqueMichelini/craftalism/authserver
    │   ├── Application.java
    │   ├── config
    │   │   ├── AuthorizationServerConfig.java
    │   │   ├── RsaKeyConfig.java
    │   │   └── SecurityConfig.java
    │   ├── keys
    │   │   └── RsaKeyProperties.java
    │   └── service
    │       └── ClientRegistrationService.java
    ├── src/main/resources
    │   ├── application.properties
    │   ├── application-test.properties
    │   └── schema.sql
    └── src/test/java/.../TokenEndpointIntegrationTest.java
```

## Known Gaps / Next Improvements

- Add an explicit key generation utility script (the code/comments reference `generate-keys.sh`, but it is not currently present in this repository).
- Add API-level documentation for introspection and revocation examples.
- Add containerized integration tests against real PostgreSQL.
- Add CI pipeline documentation and badges.

## Recruiter/Portfolio Notes

This project demonstrates practical security engineering concerns:

- protocol-compliant OAuth/OIDC endpoint exposure,
- deterministic JWK key ID derivation,
- stateless security defaults with least-privilege endpoint exposure,
- startup idempotency for client provisioning,
- reproducible build/test setup with Gradle and Docker.
