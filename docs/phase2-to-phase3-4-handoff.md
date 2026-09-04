# Project Alice Phase 2 to Phase 3 / 4 Handoff

**Version:** 2  
**Date:** 2026-09-02 JST  
**Status:** Ready  

## 1. Handoff Meaning

FUI2-001 through FUI2-030 were approved and integrated on 2026-09-02 JST. Phase 2 detailed design is `Reviewed — Implementation Ready`. This means implementation can begin from a consistent design baseline; it does not mean implementation, deployment, or production validation is complete.

Phase 3 and Phase 4 design must consume the accepted Phase 2 capabilities through their published Application and API boundaries. They must not redesign Personal Memory as part of Tool or Agent work.

## 2. Frozen Phase 2 Boundaries

| Boundary | Rule for later phases |
|---|---|
| Feature ownership | `memory` owns Personal Memory; `conversation` owns Conversation History |
| Dependency | Other features call `memory.application`; they never access Memory repositories directly |
| AI context | At most 8 Memories, 1,500 estimated tokens, and 12 KiB, with all limits applied together |
| Trust | Memory content is user data, not system instruction, Tool authorization, or current external state |
| Sensitive data | Sensitive Memory is used only when directly necessary; Secret is never stored as Memory |
| Deletion | Hard deletion, revision/index/cache cleanup, fingerprints, and reset points prevent silent revival |
| Concurrency | Version and `ETag` preconditions prevent stale updates |
| Destructive operations | Plan, preview, scoped confirmation, fence, and reconciliation remain mandatory |
| Backup / Restore | Encrypted manual archive, inspection, impact plan, warning, and explicit confirmation |
| Client | iOS first; a future desktop client reuses the same state, API, confirmation, and privacy contracts |

## 3. Phase 2 Capabilities Available to Later Phases

- retrieve eligible Memory for an answer through the Memory Application capability;
- explicitly create, update, confirm, resolve, search, and delete Memory;
- present response-specific Memory usage evidence without hidden reasoning;
- honor independent automatic-capture and answer-time-use preferences;
- prevent deleted Memory from being silently regenerated from older Conversation History;
- export and restore encrypted archives through the accepted plan workflow;
- observe safe metrics, audit events, partial/unknown status, and reconciliation outcomes.

Tool results that represent current weather, schedules, prices, external account state, or other changing facts remain Tool data. They are not promoted to durable Personal Memory merely because they appeared in a response.

## 4. Phase 3 Baseline — Tool Integration

Phase 3 Requirements remain Baseline until their dedicated Requirements Review. The first calendar integration is Apple Calendar, reflecting the approved product priority; Google Calendar may follow later without changing the Tool boundary.

Recommended design sequence:

1. Tool architecture, registry, capability metadata, risk classification, and authorization boundary.
2. Apple Calendar read-only operations and current-state handling.
3. Apple Calendar write operations with preview, scoped confirmation, idempotency, and audit.
4. Current information, web/search, place, and navigation-oriented Tool contracts.
5. GitHub and AWS integrations with least privilege and environment separation.
6. Tool API, persistence, security, logging, observability, frontend, and test designs.
7. One consolidated Phase 3 cross-review and implementation-readiness judgment.

Phase 3 must preserve these invariants:

- Memory never grants Tool permission or approval.
- A remembered preference may shape a proposal but cannot authorize execution.
- Current external state is fetched from the responsible Tool when needed.
- Tool credentials, access tokens, API keys, and recovery material are never stored as Memory.
- Side-effecting operations require the risk-appropriate explicit confirmation boundary.
- Read and write capabilities are separately authorized and auditable.

## 5. Phase 4 Baseline — Agentic Features

Phase 4 Requirements remain Baseline until their dedicated Requirements Review. Recommended design sequence:

1. Permission, approval, revocation, emergency stop, and audit model.
2. Durable task and execution-state model, including resume, retry, timeout, and cancellation.
3. Executor/Agent identity, authentication, capability scoping, and isolation.
4. PC operations with target resolution, preview, confirmation, and rollback where possible.
5. Browser automation with origin, session, credential, download, and prompt-injection boundaries.
6. Voice input/output with privacy and accessibility controls.
7. Failure injection, adversarial tests, observability, and one consolidated final review.

Phase 4 must preserve these invariants:

- Voice is an input/output channel, never an authentication or approval factor by itself.
- Memory is context, never authority.
- An Agent receives only the minimum capabilities needed for the current task.
- Dangerous actions show the exact target and effect before execution.
- Approval is bound to the reviewed action; a generic statement cannot authorize a changed action.
- Agent audit history is stored separately from Personal Memory and Conversation History.
- Cancellation and emergency stop have defined, testable semantics even during partial execution.

## 6. Entry Checklist for the Next Design Thread

- Use the Approved `requirements.md` as the Requirement baseline.
- Treat `memory-design.md`, `api-design.md`, Database Design v18, Security Design v7, and the Phase 2 Traceability Matrix as frozen dependencies.
- Read the final Phase 2 UI and final-review documents after their status changes to Accepted/Passed.
- Begin with Phase 3 Requirements refinement; do not create empty implementation packages before design acceptance.
- Separate product decisions from library/provider selections and keep the latter in ADRs or implementation planning.
- Batch review decisions by coherent boundary and use one final cross-review per phase.

## 7. Suggested New-thread Kickoff

> Project AliceのPhase 3 Tool Integration設計を開始します。`phase2-to-phase3-4-handoff.md`とApproved RequirementsをSource of Truthとして、まずPhase 3 Requirementsの詳細化と横断整合確認を一括で進めてください。最初の外部Calendar IntegrationはApple Calendarを優先し、MemoryはContextであってTool権限・承認・現在情報のSourceではないというPhase 2 Boundaryを維持してください。レビューは境界単位でまとめ、最終横断レビューは一度に集約してください。

## 8. Final Phase 2 Baseline

| Document | Final design version / status |
|---|---|
| `requirements.md` | Version 11 — Phase 2 Approved |
| `memory-design.md` | Version 50 — Reviewed / Implementation Ready |
| `api-design.md` | Version 26 — API2-001〜234 Accepted |
| `database-design.md` | Version 18 — DB2-001〜243 Accepted |
| `security-design.md` | Version 7 — SEC2-001〜038 Accepted |
| `frontend-design.md` | Version 8 — Phase 1〜2 Approved |
| `phase2-frontend-ui-design.md` | Version 2 — FUI2-001〜030 Accepted |
| `test-design.md` | Version 14 — Phase 1〜2 Approved |
| `phase2-requirement-traceability-matrix.md` | Version 4 — 73 / 73 Covered |
| `phase2-detailed-design-final-review.md` | Version 2 — Passed; Critical / High Open 0 |

This file is ready to be used as the first input for the Phase 3 Requirements Review.
