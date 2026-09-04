# Project Alice — Phase 3 API Design

Status: **APPROVED / COMPLETE**

### API3-001
Conversation remains primary interaction path; no universal execution endpoint as normal frontend path.

### API3-002
Connector management API is separate from Conversation.

### API3-003
Permission management is explicit and separate.

### API3-004
Approval Request/Response is an independent resource, not a boolean on a message.

### API3-005
Tool Operation is an independently trackable resource.

### API3-006
Public frontend API stays canonical; no provider-specific frontend APIs.

### API3-007
Provider SDK/error types do not leak into public DTOs.

### API3-008
Client cannot authoritatively select Risk or approval requirement.

### API3-009
Canonical error taxonomy separates transport/validation/permission/approval/conflict/external/unknown.

### API3-010
New operation retry and same-operation reconciliation are distinct.

### API3-011
Conversation response can include canonical Tool Proposal, Approval Requirement and Operation Reference.

### API3-012
Approval data includes Operation ID/version/target summary/risk/impact.

### API3-013
Connector DTO exposes Supported/Enabled/Authenticated/Permitted/Available independently.

### API3-014
Binding DTO contains identity/state/nonsecret metadata only.

### API3-015
Operation DTO separates Execution State and Outcome State.

### API3-016
HTTP status and Alice domain error code are separate.

### API3-017
Write request binds to Alice Operation ID/Idempotency Identity.

### API3-018
Pagination uses opaque cursors.

### API3-019
List/search responses carry completeness metadata.

### API3-020
Timestamps use ISO8601/RFC3339; Instant/local date/time/TZ semantics are kept separate.

### API3-021
Resource groups: Conversations, Connectors, Connector Bindings, Permissions, Tool Operations.

### API3-022
Mutation validation = Schema + Domain + Policy.

### API3-023
Update uses operation/resource version concurrency evidence.

### API3-024
Approval submit binds Operation ID + Version; stale approval rejected.

### API3-025
Approve and Reject are explicit; Reject is not timeout/cancel.

### API3-026
Cancel Request is not immediate Cancelled.

### API3-027
Reconcile checks same-operation state only; creates no new side effect.

### API3-028
Enable/Disable is separate from Authenticate/Disconnect.

### API3-029
Permission Grant/Revoke is a dedicated mutation.

### API3-030
Async mutation returns Operation Resource rather than claiming final external success.

### API3-031
HTTP and canonical error mapping is consistent.

### API3-032
Common error codes include APPROVAL_REQUIRED, PERMISSION_DENIED, STALE_TARGET, RATE_LIMITED, UNKNOWN_OUTCOME.

### API3-033
Provider errors, stack traces and SDK exceptions are not exposed directly.

### API3-034
Credentials/secrets/tokens/sensitive external data are excluded from API response/error/debug metadata.

### API3-035
Public API has an explicit version boundary.

### API3-036
Additive DTO changes are compatible; semantic deletion/change is breaking.

### API3-037
Frontend-backend timeout and backend-connector timeout are distinct.

### API3-038
Canonical retry hint is explicit; client does not infer retry solely from HTTP.

### API3-039
Initial operation-state updates use polling; push/streaming is optional.

### API3-040
Polling is bounded and follows recommended intervals and terminal-state rules.
