# Project Alice — Phase 3 AI Design

Status: **APPROVED / COMPLETE**

### AI3-001
Only the minimal supported/enabled/permitted tool subset is exposed to the model.

### AI3-002
AI Tool Call is a proposal; tool.application decides execution.

### AI3-003
AI cannot authoritatively determine/override Risk, Permission or Approval requirement.

### AI3-004
Alice Canonical Tool Contract is the source of truth; provider adapters translate it.

### AI3-005
Tool parameters exposed to AI are minimized and exclude credentials/security internals.

### AI3-006
Hallucinated tools/operations/parameters fail closed.

### AI3-007
Every Tool Proposal passes Schema + Domain + Policy validation.

### AI3-008
Ambiguous connector/target binding is not resolved by model guess alone.

### AI3-009
AI context is minimized by use case.

### AI3-010
Provider replacement cannot change Permission/Approval/Execution semantics.

### AI3-011
Tool Result is normalized to Alice Canonical Result Envelope before AI context.

### AI3-012
External Tool Result is UNTRUSTED_EXTERNAL_DATA.

### AI3-013
Tool Result context retains source/provenance/observedAt/scope/completeness.

### AI3-014
Partial/truncated/rate-limited/incomplete data is never presented as complete.

### AI3-015
Freshness evidence is retained for dynamic external state.

### AI3-016
Each additional tool call is independently validated/authorized.

### AI3-017
tool.application manages multi-call dependencies/order; provider parallel semantics are not execution SoT.

### AI3-018
Tool loops have bounded calls/time/data/retries/pages.

### AI3-019
AI response must preserve canonical operation outcome; UNKNOWN/IN_PROGRESS cannot be rewritten as success.

### AI3-020
Tool Result retention is separate from Conversation History.

### AI3-021
Approval-pending operation is independent state; AI does not regenerate the approval target.

### AI3-022
Resume after approval uses approved Operation ID/version, not reinterpreted natural language.

### AI3-023
Tool Proposal retains a user-intent reference for traceability.

### AI3-024
Material user change while pending produces a new proposal/operation.

### AI3-025
Tool failures are canonicalized before AI interpretation.

### AI3-026
Ambiguous/incomplete write requests require clarification.

### AI3-027
Dynamic-target writes use read-before-write when freshness/version evidence requires it.

### AI3-028
AI cannot silently create new side-effect operations after FAILED/UNKNOWN.

### AI3-029
Rejected approval is respected until explicit/new user intent.

### AI3-030
Conversation lifecycle and Operation lifecycle are separate.

### AI3-031
Tool Result → Memory always goes through Phase 2 Memory Capture Policy.

### AI3-032
Dynamic external state is not Personal Memory by default.

### AI3-033
Large Tool Results may be summarized, but summary is not authoritative external state.

### AI3-034
Summary retains critical provenance/freshness/completeness/reference evidence.

### AI3-035
AI provider failure does not change already-dispatched operation outcome.

### AI3-036
Provider fallback preserves the same canonical tool/security/execution policies.

### AI3-037
Incomplete write proposal is not implicitly completed by fallback model.

### AI3-038
Deterministic Alice policy always overrides AI judgment for security/execution.

### AI3-039
AI observability records safe boundary events/metadata, never chain-of-thought or secrets.

### AI3-040
Context/token/tool budgets are explicit and AI tests cover tool selection, hallucination, prompt injection, partial/unknown and provider failure.
