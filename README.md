# craftalism-authorization-server

OAuth 2.1 / OpenID Connect authorization server module for the Craftalism platform.

## What this module does

This service issues short-lived JWT access tokens for machine-to-machine clients,
starting with the Minecraft game server plugin. It uses:

- Spring Boot 3
- Spring Authorization Server
- PostgreSQL for registered clients and authorization state
- RSA-signed JWTs exposed through a JWKS endpoint

## Exposed endpoints

- `POST /oauth2/token` - issue access tokens with the `client_credentials` grant
- `POST /oauth2/introspect` - token introspection
- `POST /oauth2/revoke` - token revocation
- `GET /oauth2/jwks` - public signing keys for downstream services
- `GET /.well-known/oauth-authorization-server` - OAuth 2 authorization server metadata
- `GET /.well-known/openid-configuration` - OpenID Connect discovery metadata
- `GET /actuator/health` - health probe

## Environment variables

### Required

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `MINECRAFT_CLIENT_SECRET`

### Optional

- `AUTH_ISSUER_URI` - defaults to `http://localhost:9000`
- `MINECRAFT_CLIENT_ID` - defaults to `minecraft-server`
- `RSA_PRIVATE_KEY`
- `RSA_PUBLIC_KEY`

If RSA keys are not provided, the service generates an ephemeral key pair at startup.
This is acceptable for local experimentation only. In any persistent environment,
inject a stable key pair so previously issued tokens remain verifiable after restarts.

## Local development

```bash
cd java
./gradlew bootRun
```

## Docker

The Docker assets live in `java/`.

```bash
cd java
docker compose up --build
```

## Quality notes

This repository intentionally favors explicit configuration to support learning and
portfolio presentation. Even so, the implementation should still follow production-oriented
basics such as deterministic key identifiers, non-root containers, least-privilege endpoint
exposure, and health checks.
