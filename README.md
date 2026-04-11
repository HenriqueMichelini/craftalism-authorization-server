# Craftalism Authorization Server

> OAuth 2.1 and OpenID Connect authorization server that issues short-lived, RSA-signed JWT access tokens for trusted Craftalism services.

---

## Overview

The authorization server centralizes authentication for the Craftalism ecosystem. It is designed for machine-to-machine flows: trusted clients (currently the Minecraft game server plugin) authenticate via `client_credentials` and receive a signed JWT that downstream services validate locally against the published JWKS.

**Key capabilities:**

- Issues access tokens via the OAuth 2.0 `client_credentials` grant at `POST /oauth2/token`.
- Publishes RSA public keys at `GET /oauth2/jwks` for local token verification by downstream services.
- Exposes standard OIDC and OAuth 2.0 discovery metadata.
- Supports token introspection (`/oauth2/introspect`) and revocation (`/oauth2/revoke`).
- Seeds one registered client (`minecraft-server` by default) idempotently on startup.
- Enforces a deny-by-default HTTP policy; only explicitly listed public endpoints are accessible without a valid token.

---

## Architecture

The service is a layered Spring Boot application with explicit security configuration.

### Security model

Two filter chains run in order:

1. **Order 1 — Protocol chain:** handles all `/oauth2/*` and discovery endpoints according to OAuth 2.0/OIDC rules.
2. **Order 2 — Fallback chain:** stateless, session-less. Permits only the following paths; all others are denied with 401/403:
   - `GET /actuator/health`
   - `GET /oauth2/jwks`
   - `GET /.well-known/oauth-authorization-server`
   - `GET /.well-known/openid-configuration`

### Key components

**`AuthorizationServerConfig`** — wires protocol endpoints, JDBC-backed repositories and services, JWT/JWK components, issuer metadata, and password encoding.

**`SecurityConfig`** — defines the fallback filter chain.

**`RsaKeyConfig`** — parses PEM keys from environment variables, or generates an ephemeral RSA key pair if none are provided.

**`ClientRegistrationService`** — seeds the registered OAuth client at startup. The operation is idempotent.

**JDBC persistence** — Spring Authorization Server's JDBC repositories persist clients, authorizations, and consents into tables initialized by `schema.sql`.

---

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Security / Auth | Spring Security + Spring Authorization Server |
| Database Access | Spring JDBC |
| Database (runtime) | PostgreSQL |
| Database (tests) | H2 (PostgreSQL compatibility mode) |
| Build Tool | Gradle Wrapper |
| Packaging | Docker multi-stage image |

---

## Prerequisites

- Java 17+
- Docker Engine 20.10+ and Docker Compose v2+ *(for containerized deployment only)*
- A running PostgreSQL instance *(for local Gradle run)*

---

## Configuration

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | — | **Required.** JDBC connection string, e.g. `jdbc:postgresql://localhost:5432/authserver`. |
| `DB_USER` | — | **Required.** Database username. |
| `DB_PASSWORD` | — | **Required.** Database password. |
| `MINECRAFT_CLIENT_SECRET` | — | **Required.** Secret for the seeded `minecraft-server` OAuth client. |
| `AUTH_ISSUER_URI` | `http://craftalism-auth-server:9000` | JWT issuer URI. Must match the value configured in downstream services (especially `spring.security.oauth2.resourceserver.jwt.issuer-uri` in the API). |
| `MINECRAFT_CLIENT_ID` | `minecraft-server` | Client ID for the seeded OAuth client. |
| `RSA_PRIVATE_KEY` | — | PEM-encoded RSA private key. Supports literal `\n` as line separator. |
| `RSA_PUBLIC_KEY` | — | PEM-encoded RSA public key. Supports literal `\n` as line separator. |
| `RSA_ALLOW_EPHEMERAL` | `false` | Allows startup without RSA key material. Intended only for local/dev/test use. |

> **Important:** If `RSA_PRIVATE_KEY` and `RSA_PUBLIC_KEY` are not provided, the service generates an ephemeral key pair on startup. Tokens issued with an ephemeral key become unverifiable after a restart. Do not use ephemeral keys in persistent environments.

> **Docker note:** inside the Docker network, `localhost` points to the current container, not the auth server. If the API validates tokens against `http://craftalism-auth-server:9000` but this service issues tokens with `iss=http://localhost:9000`, the API will reject every token with 401. Keep one canonical issuer URI across all services.

For key generation and deployment wiring guidance, see the [Craftalism Deployment repository](https://github.com/HenriqueMichelini/craftalism-deployment).

---


## Dashboard/API auth troubleshooting

`GET /api/*` routes are intentionally public in the current API security model, so a `GET /api/players 401` usually means the request never matched the expected read-only route or the wrong service/port is being called.

Focus auth debugging on protected write routes instead:
- `craftalism-dashboard` does not need a bearer token for read-only `GET /api/*` requests today.
- The API requires `Authorization: Bearer <access_token>` on protected write requests such as `POST /api/players` and `POST /api/balances/transfer`.
- The API will return `401` when a protected request is missing a token or the token is malformed, expired, or fails issuer/signature validation.
- This authorization server only seeds the `minecraft-server` machine client by default; any browser-facing authentication must be implemented explicitly in the dashboard/API integration layer.

Minimal verification for a protected route:

```bash
# 1) Obtain token from auth server
curl -s -X POST 'http://localhost:9000/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -u "minecraft-server:${MINECRAFT_CLIENT_SECRET}" \
  -d 'grant_type=client_credentials&scope=api:write'

# 2) Call a protected API route with the token
curl -i -X POST 'http://localhost:3000/api/players' \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer <access_token>" \
  -d '{"uuid":"00000000-0000-0000-0000-000000000001","name":"SmokeTest"}'
```

If step 2 works but another protected client request still gets `401`, the bug is in client-to-API auth propagation or in issuer/JWKS configuration between the API and authorization server.

---

## Running Locally

```bash
cd java
export DB_URL='jdbc:postgresql://localhost:5432/authserver'
export DB_USER='your_user'
export DB_PASSWORD='your_password'
export MINECRAFT_CLIENT_SECRET='replace_me'
./gradlew bootRun
```

Service is available at `http://localhost:9000`.

---

## Running with Docker

```bash
cd java
docker compose up --build
```

| Service | Port | URL |
|---|---|---|
| Authorization Server | 9000 | `http://localhost:9000` |

> **Note:** `docker-compose.yml` expects an external `craftalism-network` and a healthy `postgres` service to already exist. A one-shot `auth-db-init` container attempts to create the `authserver` database before the application starts.

---

## API Reference

### Token issuance

```bash
curl -X POST 'http://localhost:9000/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -u "minecraft-server:${MINECRAFT_CLIENT_SECRET}" \
  -d 'grant_type=client_credentials&scope=api:read api:write'
```

Response:

```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 299,
  "scope": "api:read api:write"
}
```

### OAuth2 protocol endpoints

These endpoints are part of the authorization-server protocol surface. They are not anonymous public routes: token, introspection, and revocation requests require appropriate client authentication according to the OAuth2 protocol.

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/oauth2/token` | client-authenticated | Issue an access token. |
| `POST` | `/oauth2/introspect` | client-authenticated | Introspect a token. |
| `POST` | `/oauth2/revoke` | client-authenticated | Revoke a token. |

### Anonymous discovery and health endpoints

| Method | Path | Access | Description |
|---|---|---|---|
| `GET` | `/oauth2/jwks` | anonymous | Fetch public keys for JWT verification. |
| `GET` | `/.well-known/openid-configuration` | anonymous | OIDC discovery metadata. |
| `GET` | `/.well-known/oauth-authorization-server` | anonymous | OAuth 2.0 server metadata. |
| `GET` | `/actuator/health` | anonymous | Health check. |

---

## Testing

Tests use `application-test.properties` and an H2 in-memory database in PostgreSQL compatibility mode.

```bash
cd java
./gradlew test
```

Test coverage includes: token issuance, JWT shape validation, public endpoint accessibility, and deny-by-default behavior for protected paths.

---

## Project Structure

```text
java/
├── build.gradle
├── docker-compose.yml
├── Dockerfile
└── src/
    ├── main/java/io/github/HenriqueMichelini/craftalism/authserver/
    │   ├── Application.java
    │   ├── config/
    │   │   ├── AuthorizationServerConfig.java
    │   │   ├── RsaKeyConfig.java
    │   │   └── SecurityConfig.java
    │   ├── keys/
    │   │   └── RsaKeyProperties.java
    │   └── service/
    │       └── ClientRegistrationService.java
    └── main/resources/
        ├── application.properties
        ├── application-test.properties
        └── schema.sql
```

---

## Known Limitations

- No `generate-keys.sh` script is present in this repository. RSA keys must be generated externally (for example with OpenSSL) and injected via environment variables.
- No introspection or revocation usage examples are documented.
- Integration tests run against H2, not real PostgreSQL.
- CI currently runs Gradle tests only; no additional static analysis or security scanning gates are configured.

---

## Roadmap

- Add a `generate-keys.sh` utility script for RSA key pair generation.
- Add containerized integration tests against real PostgreSQL.
- Document introspection and revocation endpoint usage with examples.
- Expand CI beyond tests (lint, dependency/security scanning, and build verification).

---

## License

This repository currently does not include a checked-in `LICENSE` file. Align the repository metadata and README with the intended license before claiming one here.
