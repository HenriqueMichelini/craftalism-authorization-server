# Craftalism Authorization Server Portfolio Backlog

Date: 2026-04-10

## Purpose

This backlog focuses on raising confidence in the most security-sensitive
service without redesigning the auth model.

Source:

- [portfolio-evolution-roadmap.md](/home/henriquemichelini/IdeaProjects/craftalism/docs/portfolio-evolution-roadmap.md)
- [repo-requirement-pack.md](/home/henriquemichelini/IdeaProjects/craftalism-authorization-server/docs/repo-requirement-pack.md)

## Now

### High priority

- Add PostgreSQL-backed integration tests for token issuance, discovery, JWKS,
  and seeded client behavior.
- Add negative-path tests for invalid client credentials, malformed grant
  requests, issuer mismatch, and invalid RSA configuration.
- Add explicit startup validation and failure messaging for incomplete or unsafe
  key-material configuration.

### Medium priority

- Add structured auth-event logs and metrics for token issuance failures, client
  auth failures, and revocation/introspection access.
- Add clearer docs that separate local/dev allowances from production-safe
  configuration.

## Next

### High priority

- Add a documented RSA key-rotation procedure with JWKS consumer overlap.
- Add integration checks that prove issuer metadata, discovery endpoints, and
  deployment configuration remain aligned.
- Add clearer scope and client-lifecycle guidance for current and future
  machine-to-machine clients.

### Medium priority

- Add more explicit troubleshooting guidance for downstream API validation
  failures caused by issuer drift or hostname misconfiguration.

## Later

- Add multi-key readiness if practical rotation needs justify it.
- Add deeper protocol conformance checks to strengthen security credibility for
  reviewers.

## Done When

- Issuer behavior is easy to trust and diagnose.
- Key-management practices are credible for the project scope.
- Auth misconfiguration fails clearly instead of silently degrading confidence.
