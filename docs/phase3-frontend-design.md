# Project Alice — Phase 3 Frontend Design

Status: **APPROVED / COMPLETE**

### FE3-001
Conversation remains the primary interaction surface.

### FE3-002
Tool Proposal/Approval/Result appear in context; security-critical actions use dedicated UI components.

### FE3-003
Approval UI is visually/structurally separate from AI prose and uses backend canonical summary.

### FE3-004
Approval UI shows Action/Target/Impact/Risk/Material Arguments/Operation Version.

### FE3-005
Approval states Pending/Approved/Rejected/Expired/Stale are distinct.

### FE3-006
Tool Operation has independent progress UI.

### FE3-007
UNKNOWN is not displayed as failure; it means outcome is not confirmed.

### FE3-008
Partial/truncated/incomplete results are visibly incomplete.

### FE3-009
Supported/Enabled/Authenticated/Permitted/Available are distinct in UI.

### FE3-010
Apple Calendar UI separates OS authorization, Alice binding, Alice permission and runtime availability.

### FE3-011
Connector management has a dedicated settings surface.

### FE3-012
Enable/Disable is separate from Authenticate/Disconnect.

### FE3-013
Permission UI supports operation/resource-scope Allow/Deny/Ask.

### FE3-014
Permission expansion displays before/after scope difference.

### FE3-015
Multiple bindings are clearly distinguishable.

### FE3-016
Default connector/binding can be explicit but is not permission or approval.

### FE3-017
Invalid/unavailable default does not silently fall back to another binding.

### FE3-018
Apple Calendar connect flow stages OS authorization → runtime → binding → Alice permission.

### FE3-019
Disconnect UX explains auth/binding/permission/in-progress operation impact.

### FE3-020
Reauthentication never auto-expands or resets Alice permission.

### FE3-021
Close/back/navigation does not equal Reject.

### FE3-022
Approval submission prevents double-submit.

### FE3-023
Cancel is displayed as a cancel request until cancellation is confirmed.

### FE3-024
UNKNOWN/Recovery Required prioritizes Reconcile over Retry.

### FE3-025
Reconcile is presented as same-operation state confirmation.

### FE3-026
Operation History is separate from Conversation History.

### FE3-027
Frontend displays execution-history projection, not raw security audit.

### FE3-028
Stale approval/target/permission disables action until refreshed/re-proposed.

### FE3-029
Offline/app restart re-syncs operation state from backend.

### FE3-030
Notifications focus on important asynchronous/user-action-required state changes.

### FE3-031
Mobile is primary form factor; tablet/desktop retain feature parity through responsive layout.

### FE3-032
Risk/error/approval states are not represented by color alone.

### FE3-033
Secrets are not sent to frontend; sensitive metadata is minimized/masked.

### FE3-034
High Risk confirmation copy explicitly states action/target/impact.

### FE3-035
Frontend loading/timeout and Tool Operation state are separate.

### FE3-036
Navigation rebuild restores approval/operation from backend resource IDs.

### FE3-037
Conversation/Connector/Permission/Approval/Operation frontend state are logically separated.

### FE3-038
Frontend derived UI state does not override backend authoritative security/operation state.

### FE3-039
Frontend cache is UX optimization only, never permission/approval/outcome SoT.

### FE3-040
Frontend tests cover approval mistakes, UNKNOWN, stale, offline recovery, connector state separation, sensitive data and accessibility.
