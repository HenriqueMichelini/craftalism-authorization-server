# Craftalism Authorization Server

OAuth 2.0 authorization server for trusted Craftalism services. It issues short-lived, RSA-signed JWT access tokens through the `client_credentials` grant and publishes the metadata and keys that resource servers need to validate them.

## Capabilities

- Issues self-contained JWT access tokens at `POST /oauth2/token`.
- Publishes a JSON Web Key Set (JWKS) at `GET /oauth2/jwks`.
- Exposes OAuth 2.0 authorization-server and OpenID Connect discovery metadata.
- Supports client-authenticated token introspection and revocation.
- Stores registered clients, authorizations, and consents in PostgreSQL.
- Seeds and reconciles the configured Minecraft client and an optional dashboard/BFF client at startup.
- Denies application routes that are not part of the authorization-server protocol or the public health endpoint.

The configured service clients support only the `client_credentials` grant. This project does not configure an end-user login or authorization-code flow.

## Technology

| Component | Version or implementation |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.11 |
| Authorization framework | Spring Authorization Server |
| Runtime database | PostgreSQL through Spring JDBC |
| Test database | H2 in PostgreSQL compatibility mode |
| Build | Gradle Wrapper 9.2.1 |
| Container | Multi-stage Eclipse Temurin 17 image |

## Security and persistence

Two ordered Spring Security filter chains define the HTTP surface:

1. The authorization-server chain handles OAuth 2.0 and OpenID Connect protocol endpoints.
2. The stateless fallback chain permits the health probe and denies every other unmatched request.

The service initializes the Spring Authorization Server JDBC tables from `schema.sql` on every startup. The statements use `CREATE TABLE IF NOT EXISTS`; the configured PostgreSQL database itself must already exist.

New seeded clients use both `client_secret_basic` and `client_secret_post`, receive self-contained access tokens with a five-minute lifetime, and do not require user consent. On startup, existing seeded registrations are reconciled for secret, authentication methods, grant type, and allowed scopes.

## Prerequisites

- Java 17 or newer. A system Gradle installation is not required.
- A PostgreSQL database when running the application.
- Docker Engine and Docker Compose v2 only when working with the container configuration.
- OpenSSL or another way to create a PKCS#8 RSA private key and X.509 public key for persistent environments.

## Configuration

| Environment variable | Default | Description |
|---|---|---|
| `DB_URL` | none | JDBC URL, for example `jdbc:postgresql://localhost:5432/authserver`. |
| `DB_USER` | none | Database username. |
| `DB_PASSWORD` | none | Database password. |
| `AUTH_ISSUER_URI` | `http://craftalism-auth-server:9000` | Issuer written to tokens and discovery metadata. Set this to the canonical URL used by resource servers. |
| `MINECRAFT_CLIENT_ID` | `minecraft-server` | Client ID for the required Minecraft service client. |
| `MINECRAFT_CLIENT_SECRET` | none | Required secret for the Minecraft service client. Blank values fail startup. |
| `DASHBOARD_BFF_CLIENT_ID` | `dashboard-bff` | Client ID for the optional server-side dashboard/BFF client. |
| `DASHBOARD_BFF_CLIENT_SECRET` | empty | Enables the dashboard/BFF client when set. Keep this secret out of browser-visible configuration. |
| `RSA_PRIVATE_KEY` | empty | PKCS#8 PEM-encoded RSA private key. Real newlines and literal `\n` separators are accepted. |
| `RSA_PUBLIC_KEY` | empty | X.509 PEM-encoded RSA public key. Real newlines and literal `\n` separators are accepted. |
| `RSA_ALLOW_EPHEMERAL` | `false` | Allows an ephemeral 2048-bit RSA key pair when persistent key material is missing. Use only for local development. |

`DB_URL`, `DB_USER`, `DB_PASSWORD`, and `MINECRAFT_CLIENT_SECRET` are required for a normal application start. Provide both RSA key variables, or explicitly enable ephemeral keys. The `local`, `dev`, and `test` Spring profiles also allow ephemeral keys.

If only one RSA key is supplied, it is not used as a partial key pair. Startup fails unless ephemeral mode is allowed.

### Generate RSA keys

This repository does not include a key-generation script. One way to create keys with OpenSSL is:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem

export RSA_PRIVATE_KEY="$(cat private.pem)"
export RSA_PUBLIC_KEY="$(cat public.pem)"
```

Ephemeral keys change at every restart, so previously issued tokens can no longer be verified. Do not enable ephemeral mode in a persistent environment.

## Run locally

Create the target PostgreSQL database first, then run from the Gradle project directory:

```bash
cd java
export DB_URL='jdbc:postgresql://localhost:5432/authserver'
export DB_USER='your_user'
export DB_PASSWORD='your_password'
export AUTH_ISSUER_URI='http://localhost:9000'
export MINECRAFT_CLIENT_SECRET='replace_me'
export RSA_ALLOW_EPHEMERAL='true'
./gradlew bootRun
```

The example explicitly enables ephemeral keys for local development. For persistent use, omit `RSA_ALLOW_EPHEMERAL` and set `RSA_PRIVATE_KEY` and `RSA_PUBLIC_KEY` instead.

The service listens on `http://localhost:9000`. Verify startup with:

```bash
curl --fail http://localhost:9000/actuator/health
```

## Seeded clients

| Client | Enabled when | Allowed scopes |
|---|---|---|
| Minecraft (`minecraft-server` by default) | Always; its secret is required | `api:read`, `api:write` |
| Dashboard/BFF (`dashboard-bff` by default) | `DASHBOARD_BFF_CLIENT_SECRET` is set | `api:read`, `api:write`, `market:admin` |

The dashboard/BFF client is confidential and intended for server-side use. In particular, Market Events administrative calls require a server-side token with `market:admin`; browser code must not receive the client secret.

Changing a configured seeded-client secret causes the stored password hash to be updated at the next startup. The seed process also corrects drift in its authentication methods, grant type, and scope set.

## Request a token

The following example uses HTTP Basic client authentication:

```bash
curl --fail-with-body -X POST 'http://localhost:9000/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -u "${MINECRAFT_CLIENT_ID:-minecraft-server}:${MINECRAFT_CLIENT_SECRET}" \
  -d 'grant_type=client_credentials&scope=api:read api:write'
```

An example response is:

```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 299,
  "scope": "api:read api:write"
}
```

Request the Market Events administration scope only from the server-side dashboard/BFF process:

```bash
curl --fail-with-body -X POST 'http://localhost:9000/oauth2/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -u "${DASHBOARD_BFF_CLIENT_ID:-dashboard-bff}:${DASHBOARD_BFF_CLIENT_SECRET}" \
  -d 'grant_type=client_credentials&scope=market:admin'
```

Clients may alternatively send `client_id` and `client_secret` as form fields because `client_secret_post` is enabled.

## Endpoints

| Method | Path | Access | Purpose |
|---|---|---|---|
| `POST` | `/oauth2/token` | Client authenticated | Issue an access token. |
| `POST` | `/oauth2/introspect` | Client authenticated | Inspect server-side token state. |
| `POST` | `/oauth2/revoke` | Client authenticated | Revoke a token in the authorization store. |
| `GET` | `/oauth2/jwks` | Public | Return the current RSA public key. |
| `GET` | `/.well-known/openid-configuration` | Public | Return OpenID Connect discovery metadata. |
| `GET` | `/.well-known/oauth-authorization-server` | Public | Return OAuth 2.0 authorization-server metadata. |
| `GET` | `/actuator/health` | Public | Return service health without details. |

Resource servers that validate self-contained JWTs locally do not consult the authorization store on every request. Consequently, revoking a token does not make those resource servers reject it immediately; they must use introspection or wait for the token's five-minute expiry to observe that change.

## Test and build

Tests use the `test` Spring profile, an H2 in-memory database, test client credentials, and ephemeral RSA keys.

```bash
cd java
./gradlew test
./gradlew build
```

The integration tests cover token issuance through both supported client-authentication methods, JWT claims, client scope restrictions, public JWKS/discovery/health access, deny-by-default behavior, and seeded-client registration and reconciliation.

CI runs `./gradlew --no-daemon build` for pushes to `main` and pull requests. A separate compatibility workflow exercises repeated builds, configuration cache, build cache, parallel execution, and a conservative worker limit. Version tags matching `v*.*.*` trigger verification and publication of the container image to GitHub Container Registry.

## Container image

Build the image from the Java project directory:

```bash
cd java
docker build -t craftalism-authorization-server .
```

The image runs as a non-root user and exposes port 9000. Supply the same required database, issuer, client, and RSA settings described above when starting it.

The checked-in `java/docker-compose.yml` is intended to join an existing external `craftalism-network` and reuse a healthy service named `postgres`. It is not a standalone PostgreSQL stack. The current descriptor also fails `docker compose config` because `auth-db-init.command` and `depends_on` are incorrectly nested under `environment`; correct that Compose configuration before relying on `docker compose up`.

## Project structure

```text
.
├── .github/workflows/          # CI, Gradle compatibility, and image publishing
├── docs/                       # Repository contracts and backlog documentation
├── java/
│   ├── build.gradle
│   ├── docker-compose.yml
│   ├── Dockerfile
│   ├── gradle/                 # Gradle Wrapper files
│   └── src/
│       ├── main/
│       │   ├── java/io/github/HenriqueMichelini/craftalism/authserver/
│       │   └── resources/
│       │       ├── application.properties
│       │       └── schema.sql
│       └── test/               # H2-backed integration tests
├── LICENSE
└── README.md
```

## Current limitations

- Integration tests use H2 rather than a real PostgreSQL instance.
- RSA signing uses one active key pair; the repository does not implement an overlapping key-rotation workflow.
- Database schema setup uses startup SQL rather than versioned migrations.
- The checked-in Compose descriptor needs the correction described above before it can be used.

## License

Licensed under the [MIT License](LICENSE).
