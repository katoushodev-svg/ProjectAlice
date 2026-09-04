# Project Alice — Phase 3 Design Package

Status: **COMPLETE / APPROVED**  
Phase: **Phase 3 — Tools / External Services**  
Final Design Review: **PASS**  
Cross-Design Consistency Review: **PASS**

このパッケージは、Phase 3で確定・承認されたArchitecture-level Designをまとめた正式成果物です。

## Included files

1. `phase3-tool-design.md`
2. `phase3-api-design.md`
3. `phase3-database-design.md`
4. `phase3-security-design.md`
5. `phase3-ai-design.md`
6. `phase3-frontend-design.md`
7. `phase3-test-design.md`
8. `phase3-final-design-review.md`

## Core principles

- Conversation remains the primary interaction surface.
- AI Tool Call is a proposal, not execution authority.
- Tool / Connector / Operation responsibilities are separated.
- Credential / Alice Permission / Approval are independent.
- External writes use durable execution intent, fencing, idempotency and reconciliation.
- `UNKNOWN` is never guessed as success or failure.
- External dynamic state is not Personal Memory by default.
- External content is untrusted data.
- Apple Calendar is the initial priority connector and uses a trusted Apple Runtime boundary.
- Phase 4 responsibilities such as generic PC control, shell execution and browser automation remain out of scope.
