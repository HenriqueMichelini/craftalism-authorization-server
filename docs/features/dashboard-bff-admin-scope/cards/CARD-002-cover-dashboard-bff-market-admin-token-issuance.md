# CARD-002: Cover Dashboard BFF Market Admin Token Issuance

## Status

planned

## Objective

Add regression coverage and auth-server documentation proving that only the
confidential `dashboard-bff` client can obtain `market:admin` tokens.

## Context

`CARD-001` makes seeded-client scopes registration-specific and grants
`market:admin` to `dashboard-bff`. Because scope authorization is
security-sensitive, the authorization server must prove positive issuance,
negative Minecraft access, and startup reconciliation behavior before the
change is deployed.

This repository owns token issuance tests and auth-server setup documentation.
Deployment rollout and end-to-end API/BFF smoke tests remain owned outside this
repository.

## Dependency

- `CARD-001-register-dashboard-bff-market-admin-scope.md`

## Required Reading

- `AGENTS.md`
- `docs/repo-contract-map.md`
- `docs/repo-requirement-pack.md`
- `README.md`
- `java/src/test/java/io/github/HenriqueMichelini/craftalism/authserver/ClientRegistrationServiceIntegrationTest.java`
- `java/src/test/java/io/github/HenriqueMichelini/craftalism/authserver/TokenEndpointIntegrationTest.java`

## Expected Behavior

- Valid `dashboard-bff` client credentials can request a token whose granted
  scope contains `market:admin`.
- Valid `minecraft-server` client credentials cannot request
  `market:admin`.
- Client-registration tests prove each seeded client receives and reconciles
  only its intended scopes.
- Auth-server documentation identifies `market:admin` as a
  `dashboard-bff`-only scope and keeps its secret server-side.

## Acceptance Criteria

- [ ] Token endpoint integration coverage proves `dashboard-bff` can request
  `market:admin` successfully.
- [ ] The issued JWT subject is `dashboard-bff` and its scope claim contains
  `market:admin`.
- [ ] Token endpoint integration coverage proves `minecraft-server` receives
  `invalid_scope` when requesting `market:admin`.
- [ ] Client registration integration coverage proves new and reconciled
  `dashboard-bff` registrations include `market:admin`.
- [ ] Client registration integration coverage proves `minecraft-server`
  registrations exclude `market:admin`.
- [ ] Existing `api:read` and `api:write` token issuance coverage continues to
  pass.
- [ ] `README.md` documents the client-specific scope boundary and a secure
  server-side `dashboard-bff` token request example.
- [ ] The full auth-server test suite passes.

## Expected Files To Change

```text
README.md
java/src/test/java/io/github/HenriqueMichelini/craftalism/authserver/ClientRegistrationServiceIntegrationTest.java
java/src/test/java/io/github/HenriqueMichelini/craftalism/authserver/TokenEndpointIntegrationTest.java
```

## Constraints

- Do not weaken negative-path assertions.
- Do not expose `DASHBOARD_BFF_CLIENT_SECRET` or access tokens in logs,
  browser-visible configuration, test output, or documentation examples.
- Do not add API route tests or dashboard/BFF proxy tests in this repository.
- Do not treat successful auth-server tests as proof that production has been
  deployed or restarted.

## Validation Commands

Run from `java/`:

```bash
./gradlew test --tests io.github.HenriqueMichelini.craftalism.authserver.ClientRegistrationServiceIntegrationTest --tests io.github.HenriqueMichelini.craftalism.authserver.TokenEndpointIntegrationTest
./gradlew test
```

## Out Of Scope

- Building or publishing a production image.
- Updating deployment manifests or secrets.
- Restarting the production authorization server or dashboard BFF.
- End-to-end production smoke tests against `craftalism-api`.

## Suggested Commit Message

```text
test(auth): cover dashboard bff market admin scope
```
