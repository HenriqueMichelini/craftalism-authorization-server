# Repo Requirement Pack: craftalism-authorization-server

## Repo Role
`craftalism-authorization-server` is the authoritative token issuer for the ecosystem. It defines and exposes correct issuer metadata, discovery/JWKS behavior, and auth-side configuration safety.

## Owned Contracts
- `auth-issuer`
  - Own issuance-side issuer truth
  - Own token issuance behavior
  - Own issuer metadata, discovery, and JWKS exposure correctness

## Consumed Contracts
- `ci-cd`
  - Meet backend quality-gate standards for PR/push/release workflows
- `testing`
  - Meet testing expectations appropriate for a security-critical service
- `documentation`
  - Keep issuer/setup/troubleshooting docs accurate and aligned with implementation
- `security-access-control`
  - Keep auth-surface exposure and token assumptions explicit and aligned with implementation

## Current Phase Objective
This phase is limited to:
- verifying or implementing missing issuance-side behavior for the `auth-issuer` contract
- correcting documentation drift directly related to issuer behavior
- correcting CI/CD or testing gaps only where they materially weaken trust in this repo’s owned contract

This phase is not for broader ecosystem auth redesign.

## Required This Phase
- Verify issuance-side `auth-issuer` behavior and classify it as:
  - already compliant
  - partially compliant
  - missing
  - incorrectly implemented
- Implement only confirmed issuer-contract gaps in this repo
- Fix documentation only where it directly contradicts actual issuer/setup behavior
- Fix CI/CD or testing only where:
  - required standards are clearly violated, and
  - the gap materially weakens confidence in issuer behavior

## Not Required This Phase
- API validation-side enforcement changes
- Plugin-side token consumption changes
- Dashboard auth feature rollout
- Deployment-wide configuration redesign beyond documenting/clarifying expected inputs
- Broad security model changes outside issuer ownership

## Local Requirements
- Keep security chain behavior clear and intentional
- Keep bootstrap/registered-client behavior correct and understandable
- Maintain key material and metadata exposure safely
- Keep startup/configuration errors diagnosable
- Avoid confusing or irrelevant security rules

## Governance Requirements
- Comply with shared `ci-cd`, `testing`, `documentation`, and `security-access-control` standards
- Treat issuance-side issuer behavior as an ecosystem source of truth
- Do not redefine API- or client-owned concerns here

## Out of Scope
- API transfer behavior
- API validation-side ownership beyond documenting the contract boundary
- Plugin command UX
- Dashboard route/client behavior
- Deployment script ownership beyond auth-consumed runtime expectations
- API-owned error, route, idempotency, and incident contracts

## Audit Questions
- Is the issuance-side issuer contract fully and clearly implemented?
- Are issuer metadata and JWKS/discovery stable and accurate?
- Are startup/configuration defaults safe and diagnosable?
- Is auth-surface exposure aligned with the shared security/access-control standard?
- Are tests and CI/CD sufficient to trust this repo’s owned contract?

## Success Criteria
- Issuance-side issuer behavior is authoritative and stable
- Consumers can trust and configure against this repo without ambiguity
- Docs match implementation where issuer ownership applies
- CI/CD and tests meet minimum required confidence for this phase