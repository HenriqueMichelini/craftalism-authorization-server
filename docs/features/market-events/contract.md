# Market Events Auth Contract

## Purpose

Define the authorization-server responsibilities needed by Market Events dashboard table-view work.

This repository does not own Market Events state, backend event APIs, dashboard table rendering, filters, sorting, pagination, or admin workflows. It owns only token issuance and registered-client bootstrap behavior needed by trusted server-side clients.

## Source Handoff

- `../../../../craftalism-api/docs/features/market-events/handoff.md`
- `../../../../craftalism-api/docs/features/market-events/cards/CARD-013-add-event-admin-authorization-boundary.md`

## Repository Ownership

`craftalism-authorization-server` owns:

- issuing OAuth2 access tokens for configured confidential service clients
- the scopes each seeded confidential client is allowed to request
- idempotent reconciliation of seeded registered-client scope drift
- issuer, discovery, and JWKS behavior consumed by downstream services

This repository consumes:

- the backend-owned Market Events admin authorization requirement: `SCOPE_market:admin`
- the backend-owned route family: `/api/dashboard/market/events`
- the dashboard/backend decision that browser-visible code must not contain confidential OAuth client secrets

## Local Requirement

When the server-side dashboard/BFF client is configured, it must be able to request a token that includes `market:admin` so it can call backend Market Events admin table and control routes that require `SCOPE_market:admin`.

The seeded dashboard/BFF client must continue to support its existing `api:read` and `api:write` scopes unless a separate feature explicitly changes that client contract.

## Non-Goals

- Do not implement Market Events persistence, scheduling, lifecycle, pricing, blocking, quote behavior, or admin APIs in this repository.
- Do not implement dashboard table UI, filters, sorting, pagination, rows, columns, or action controls in this repository.
- Do not expose a browser-facing OAuth secret or add browser-side token acquisition.
- Do not broaden public `/api/market/**` behavior or redefine backend route security.
- Do not rename the backend-owned `market:admin` authority without an upstream contract change.

## Assumptions And Open Questions

- Assumption: the backend authorization boundary remains `SCOPE_market:admin`, which corresponds to an OAuth scope value of `market:admin` in issued tokens.
- Assumption: the existing `dashboard-bff` confidential client is the intended server-side caller for Market Events admin table data and actions.
- Open question: if a later platform decision introduces a separate event-admin service client, this repository should add a new scoped client instead of overloading the existing dashboard/BFF client further.
