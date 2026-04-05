# Repo Requirement Pack: craftalism-authorization-server

## Repo Role
`craftalism-authorization-server` is the authoritative token issuer for the ecosystem. It must define and expose correct issuer metadata, discovery/JWKS behavior, and auth-side configuration safety.

## Owned Contracts
- `auth-issuer`
  - Own canonical issuer identity and token issuance-side behavior
  - Own issuer metadata, discovery, and JWKS exposure correctness

## Consumed Contracts
- `ci-cd`
  - Meet backend quality-gate standards for PR/push/release workflows
- `testing`
  - Meet testing expectations appropriate for a security-critical service
- `documentation`
  - Keep issuer/setup/troubleshooting docs accurate and aligned with implementation

## Current Priority Areas
- Verify issuer configuration correctness and clarity
- Verify discovery/JWKS/metadata behavior against the issuer contract
- Verify consumers can align to the issuer contract without ambiguity
- Verify configuration failure modes are explicit and diagnosable
- Improve CI/CD quality gates if missing or weak
- Improve test coverage if insufficient for a security-critical repo
- Align docs with real issuer/setup behavior

## Local Requirements
- Keep security chain behavior clear and intentional
- Keep bootstrap/registered-client behavior correct and understandable
- Maintain key material and metadata exposure safely
- Keep startup/configuration errors diagnosable
- Avoid confusing or irrelevant security rules

## Governance Requirements
- Comply with shared `ci-cd`, `testing`, and `documentation` standards
- Treat issuer behavior as an ecosystem-level source of truth
- Do not redefine API- or client-owned concerns here

## Out of Scope
- API transfer behavior
- Plugin command UX
- Dashboard route/client behavior
- Deployment script ownership beyond auth-consumed runtime expectations
- API-owned error, route, idempotency, and incident contracts

## Audit Questions
- Is the issuer contract fully and clearly implemented?
- Are issuer metadata and JWKS/discovery stable and accurate?
- Are startup/configuration defaults safe and diagnosable?
- Are CI/CD and tests strong enough for a security-critical repo?
- Are docs aligned with actual setup and behavior?

## Success Criteria
- Issuer behavior is authoritative and stable
- Consumers can trust and configure against this repo without ambiguity
- Docs match implementation
- CI/CD and tests provide meaningful confidence
