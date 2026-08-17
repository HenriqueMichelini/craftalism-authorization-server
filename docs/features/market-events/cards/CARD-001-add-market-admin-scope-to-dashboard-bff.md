# CARD-001: Add Market Admin Scope To Dashboard BFF

## Status

implemented

## Objective

Allow the configured server-side dashboard/BFF client to request `market:admin` tokens for backend Market Events admin table routes.

## Context

The Market Events backend handoff states that dashboard/admin event routes live under `/api/dashboard/market/events` and require the dedicated event-admin authority `SCOPE_market:admin`; generic `SCOPE_api:write` is not sufficient. This repository does not own those routes or the table view, but it does own seeded confidential OAuth client scopes.

The existing dashboard/BFF client is optional and confidential. It is currently seeded with `api:read` and `api:write` when `DASHBOARD_BFF_CLIENT_SECRET` is configured.

## Required Reading

- `../contract.md`
- `../../../repo-contract-map.md`
- `../../../repo-requirement-pack.md`
- `../../../../../craftalism-api/docs/features/market-events/handoff.md`
- `../../../../../craftalism-api/docs/features/market-events/cards/CARD-013-add-event-admin-authorization-boundary.md`

## Expected Behavior

When `DASHBOARD_BFF_CLIENT_SECRET` is configured, the seeded `dashboard-bff` registered client allows `api:read`, `api:write`, and `market:admin` scopes. Existing dashboard/BFF clients missing `market:admin` are reconciled on startup without losing existing supported scopes, authentication methods, grant type, token format, or token lifetime.

A token request for the configured dashboard/BFF client with `scope=market:admin` succeeds and emits an access token whose scope claim includes `market:admin`. A token request for the Minecraft client with `scope=market:admin` is not made valid by this card unless a separate contract explicitly grants that scope.

## Acceptance Criteria

- [x] The dashboard/BFF seeded-client scope set includes `market:admin` in addition to existing `api:read` and `api:write`.
- [x] Existing dashboard/BFF registrations are reconciled when they lack `market:admin`.
- [x] The Minecraft seeded-client scope contract is not broadened to include `market:admin`.
- [x] Token endpoint tests prove the dashboard/BFF client can request `market:admin`.
- [x] Tests prove the dashboard/BFF client still supports the existing write-scope behavior.
- [x] README or local auth documentation explains that Market Events admin/table calls must use a server-side dashboard/BFF token with `market:admin`, not browser-visible secrets.

## Expected Files to Change

```text
README.md
java/src/main/java/io/github/HenriqueMichelini/craftalism/authserver/service/ClientRegistrationService.java
java/src/test/java/io/github/HenriqueMichelini/craftalism/authserver/ClientRegistrationServiceIntegrationTest.java
java/src/test/java/io/github/HenriqueMichelini/craftalism/authserver/TokenEndpointIntegrationTest.java
```

## Constraints

- Do not implement backend Market Events routes or dashboard table UI in this repository.
- Do not expose or document a browser-side OAuth secret.
- Do not replace the existing dashboard/BFF client unless a separate card scopes a new client.
- Do not alter issuer, JWKS, discovery, token lifetime, or client authentication method behavior except where needed to preserve existing seeded-client reconciliation.

## Validation Commands

```bash
rtk ./gradlew test --tests io.github.HenriqueMichelini.craftalism.authserver.ClientRegistrationServiceIntegrationTest --tests io.github.HenriqueMichelini.craftalism.authserver.TokenEndpointIntegrationTest
```

Run from `java/`.

Fallback when targeted Gradle filters are not reliable:

```bash
rtk ./gradlew test
```

Run from `java/`.

## Out of Scope

- Market Events backend persistence, lifecycle, scheduler, pricing, blocking, quote, and admin-controller behavior.
- Dashboard table columns, filters, pagination, sorting, history display, and row action UX.
- Public Market Events snapshot display behavior.
- A new user-auth flow or browser-visible OAuth client.

## Completion Notes

Implemented by separating the seeded scope contracts for the Minecraft client and dashboard/BFF client. The dashboard/BFF client now allows and reconciles `market:admin`; the Minecraft client remains limited to `api:read` and `api:write`. README troubleshooting now documents server-side Market Events admin token acquisition.
