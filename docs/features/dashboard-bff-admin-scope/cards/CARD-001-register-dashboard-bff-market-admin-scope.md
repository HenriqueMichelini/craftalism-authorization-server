# CARD-001: Register Dashboard BFF Market Admin Scope

## Status

planned

## Objective

Allow the confidential `dashboard-bff` client to request `market:admin`
without granting that authority to the `minecraft-server` client.

## Context

Production requests for:

- `GET /api/dashboard/market/events`
- `GET /api/dashboard/market/event-templates`

require a Bearer token containing `market:admin`. The authorization server
currently rejects a valid `dashboard-bff` client-credentials request for that
scope with `invalid_scope` because all seeded clients share the same
`api:read` and `api:write` scope set.

This repository owns registered-client bootstrap, reconciliation, and token
issuance scope eligibility. It does not own API route authorization,
dashboard behavior, BFF proxy behavior, or deployment rollout.

## Required Reading

- `AGENTS.md`
- `docs/repo-contract-map.md`
- `docs/repo-requirement-pack.md`
- `README.md`
- `java/src/main/java/io/github/HenriqueMichelini/craftalism/authserver/service/ClientRegistrationService.java`

## Expected Behavior

- A configured `dashboard-bff` registration allows:
  - `api:read`
  - `api:write`
  - `market:admin`
- A `minecraft-server` registration continues to allow only:
  - `api:read`
  - `api:write`
- Existing registered clients are reconciled to their client-specific scope
  sets on startup.
- Scope reconciliation does not broaden unrelated clients.
- Existing client authentication methods, `client_credentials` grant type,
  secret reconciliation, and token lifetime behavior remain unchanged.

## Acceptance Criteria

- [ ] Seeded-client scope configuration is registration-specific rather than
  using one shared scope set for every client.
- [ ] New `dashboard-bff` registrations include `market:admin`.
- [ ] New `minecraft-server` registrations do not include `market:admin`.
- [ ] Existing `dashboard-bff` registrations missing `market:admin` are
  reconciled to include it.
- [ ] Existing `minecraft-server` registrations containing unauthorized scope
  drift are reconciled back to the Minecraft scope set.
- [ ] No browser-facing client or user-login flow is introduced.
- [ ] No API, dashboard, BFF proxy, or deployment repository behavior is
  changed.

## Expected Files To Change

```text
java/src/main/java/io/github/HenriqueMichelini/craftalism/authserver/service/ClientRegistrationService.java
```

## Constraints

- Do not add `market:admin` to a shared scope set used by
  `minecraft-server`.
- Do not grant `market:admin` to all current or future clients by default.
- Keep `dashboard-bff` optional when its secret is not configured.
- Preserve idempotent startup registration and reconciliation.
- Do not change token claim shape, issuer metadata, JWKS behavior, grant types,
  or client authentication methods.

## Validation Commands

Run from `java/`:

```bash
./gradlew test --tests io.github.HenriqueMichelini.craftalism.authserver.ClientRegistrationServiceIntegrationTest
```

## Out Of Scope

- API-side `SCOPE_market:admin` enforcement.
- Dashboard authentication or browser token storage.
- BFF route allowlist or forwarding changes.
- Production deployment, image rollout, or service restart.

## Suggested Commit Message

```text
feat(auth): grant market admin scope to dashboard bff
```
