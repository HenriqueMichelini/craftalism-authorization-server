# Repo Contract Map: craftalism-authorization-server

## Repository Role
`craftalism-authorization-server` is the authoritative token issuer for the ecosystem. It owns token issuance behavior, issuer metadata, JWKS/discovery behavior, and auth-side configuration safety.

## Owned Contracts
- `auth-issuer`
  - Owns the issuance side of the ecosystem issuer contract
  - Owns correctness of issuer metadata, discovery, and JWKS exposure

## Consumed Contracts
- `ci-cd`
  - Must comply with backend quality gates
- `testing`
  - Must meet auth-service testing expectations
- `documentation`
  - Must keep issuer/setup/troubleshooting docs accurate
- Deployment/runtime configuration standards where relevant to issuer alignment

## Local-Only Responsibilities
- Security chain clarity
- Registered client bootstrap behavior
- Key material lifecycle and metadata exposure
- Configuration safety and startup diagnosability
- Auth service documentation and troubleshooting accuracy

## Out of Scope
- API transfer behavior
- Plugin command UX
- Dashboard client logic
- Deployment script ownership beyond consumed env/runtime expectations
- API-owned error/route/idempotency/incident contracts

## Compliance Questions
- Does this repo fully and clearly implement the issuer contract?
- Are issuer metadata and discovery stable and well-documented?
- Are auth-specific docs aligned with real behavior?
- Are tests and CI/CD strong enough for a security-critical service?
- Are there confusing or irrelevant security rules that should be cleaned up?

## Success Signal
This repo is compliant when it acts as a clear, stable, and authoritative auth source that other services can trust and configure against without ambiguity.
