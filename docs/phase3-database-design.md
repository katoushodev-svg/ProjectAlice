# Project Alice — Phase 3 Database Design

Status: **APPROVED / COMPLETE**

### DB3-001
Persistence domains are separated: Connector Definition / Binding / Permission / Approval / Tool Operation / Execution Journal / Audit Projection.

### DB3-002
External service current state is not a DynamoDB Source of Truth.

### DB3-003
Connector Definition is based on build-time allowlist; DB cannot enable unknown code.

### DB3-004
Binding stores identity/state/nonsecret metadata/Credential Reference, never credential body.

### DB3-005
Permission model: Connector/Binding/Operation/Resource Scope/Effect/Version.

### DB3-006
Approval is operation/version-bound, single-use evidence.

### DB3-007
Tool Operation is the primary durable aggregate for writes.

### DB3-008
Execution Journal is append-oriented and separate from current state.

### DB3-009
Audit Source is separate from user-visible history projection.

### DB3-010
Conversation History, Personal Memory and Tool persistence remain separate logical models.

### DB3-011
Do not force DynamoDB single-table design; use access-pattern-driven small table set.

### DB3-012
Tool Operation and Execution Journal are grouped by operation partition where appropriate.

### DB3-013
Binding/Permission keys are designed from actual query patterns; no Scan assumption.

### DB3-014
Alice-generated IDs are primary identities; provider IDs are not primary system identities.

### DB3-015
GSIs only for real queries.

### DB3-016
Conditional Writes protect transitions, approval consumption and permission version.

### DB3-017
Execution Fence/Operation Version are persisted.

### DB3-018
TTL may clean temporary records but is not a security-expiration decision mechanism.

### DB3-019
Retention differs by data type.

### DB3-020
Large external result/diff/log content is not embedded directly in operation/audit items.

### DB3-021
Before external write: Approval consumption + Durable Intent + PREPARED occur in one consistency boundary.

### DB3-022
Use DynamoDB Transaction or equivalent atomic conditional write; no partial success.

### DB3-023
External connector dispatch occurs outside the DB transaction.

### DB3-024
Operation state is persisted around dispatch so crash windows remain recoverable.

### DB3-025
UNKNOWN keeps same Operation identity and becomes a reconciliation target.

### DB3-026
Recovery Worker gets an efficient query path for nonterminal/recovery-required operations.

### DB3-027
Execution Journal and Security Audit are append-only.

### DB3-028
User-visible history is a regenerable projection.

### DB3-029
External write accepted/succeeded + result persistence failure → UNKNOWN / RECOVERY_REQUIRED, not retry.

### DB3-030
If durable intent/audit source cannot be persisted, fail closed before dispatch.

### DB3-031
Each persisted item carries Schema Version.

### DB3-032
Schema Version and business/concurrency versions are separate.

### DB3-033
Secrets/credentials are excluded from normal Phase 3 tables; sensitive attributes minimized and encrypted when necessary.

### DB3-034
Backup/Restore restores Alice internal persistence, not external service state.

### DB3-035
After restore, bindings, permissions and nonterminal operations are revalidated.

### DB3-036
Local and production DynamoDB share the same logical schema contract.

### DB3-037
Migration favors backward-compatible phased read/write evolution.

### DB3-038
Infrastructure Mapper handles old schema versions; Domain does not branch on persistence schema.

### DB3-039
Correctness/security-critical reads may require strong/transactional consistency.

### DB3-040
History/list projections may use eventual consistency.
