# Project Alice — Phase 3 Tool Design

Status: **APPROVED / COMPLETE**

## Architecture decisions

### TOOL3-001–081
**Core Tool architecture**  
Independent tool feature; Conversation consumes tool.application capability; LLM Tool Call is proposal only; Tool owns validation, permission, approval and execution; Connector-specific SDK types stay outside Core; identity, permission, risk, approval, execution, idempotency, reconciliation and audit models are separated.

### TOOL3-082
**Calendar capability set**  
Service-independent List/Search/Free Time/Create/Update/Delete operations.

### TOOL3-083
**Calendar Connector Port**  
Provider/EventKit types must not leak across the port.

### TOOL3-084
**Free Time ownership**  
Deterministically calculated in tool.application from canonical event/busy interval data.

### TOOL3-085
**Calendar Reference**  
Opaque within Connector Binding.

### TOOL3-086
**Calendar access capabilities**  
Read/Create/Update/Delete/Availability are separate capabilities.

### TOOL3-087
**Initial calendar write exclusions**  
Attendee invitations, organizer changes, attachments and meeting URL issuance are excluded.

### TOOL3-088
**Event identity/version**  
Event Reference and Version Evidence are separate.

### TOOL3-089
**Version fallback**  
If strong version unsupported, use Snapshot Digest as best-effort precondition.

### TOOL3-090
**Timed events**  
Persist Instant, IANA time zone and original local date-time semantics.

### TOOL3-091
**Time-zone resolution**  
User explicit → existing event → user setting → confirmed session → ask. Never silently use OS default.

### TOOL3-092
**All-day events**  
Represented as local date range with exclusive end.

### TOOL3-093
**Recurring writes**  
Occurrence/Future/Entire Series scopes; ask when ambiguous.

### TOOL3-094
**Calendar query bounds**  
Time range, scope, permission and result limits are mandatory.

### TOOL3-095
**Sensitive notes**  
Not unconditionally keyword-searched or injected into AI context.

### TOOL3-096
**Free-time busy semantics**  
Busy/Tentative/Unavailable/Busy all-day are treated as occupied.

### TOOL3-097
**Partial availability**  
Must be represented as incomplete.

### TOOL3-098
**Calendar target resolution**  
Fresh Reference → user selection → bounded search.

### TOOL3-099
**Material calendar changes**  
Calendar/time/TZ/recurrence/third-party impact changes require re-approval.

### TOOL3-100
**Calendar preferences**  
Tool settings, not Personal Memory.

### TOOL3-101
**Calendar write target**  
User explicit → user default → unique candidate; otherwise ask.

### TOOL3-102
**No heuristic-only target selection**  
Do not choose solely from provider default, past use, calendar name or Memory.

### TOOL3-103
**Apple connector**  
Initial implementation uses EventKit.

### TOOL3-104
**Apple Runtime**  
EventKit adapter isolated to trusted iOS/macOS runtime, not Spring Core.

### TOOL3-105
**Backend↔Apple boundary**  
Only allowlisted operations and canonical contracts.

### TOOL3-106
**macOS runtime**  
Same connector runtime boundary; non-Apple devices require explicit connection.

### TOOL3-107
**No silent fallback**  
If Apple Runtime offline, no silent CalDAV/Web/Phase4 fallback.

### TOOL3-108
**Apple security layers**  
OS Authorization, Connector Binding, Alice Permission and Operation Approval are separate.

### TOOL3-109
**Calendar-level Alice permission**  
Alice keeps calendar-level permission even if OS access is broad.

### TOOL3-110
**Event store notifications**  
Mark cache stale only; do not infer exact diff or success.

### TOOL3-111
**Native durable journal**  
Apple Runtime journals operation-bound request before mutation.

### TOOL3-112
**EventKit idempotency**  
No native idempotency assumption; crash window → UNKNOWN + reconcile.

### TOOL3-113
**Calendar provenance**  
Source/scope/observation/time-zone/reference-version retained.

### TOOL3-114
**Calendar and Memory**  
Dynamic calendar events are not unconditionally copied to Personal Memory.

### TOOL3-115–130
**Web/Search capability**  
Service-independent read capability; Search and Page Fetch separate; provenance and attribution retained; bounded retrieval; untrusted content; SSRF-aware fetch policy; no silent browser fallback; private/authenticated web remains separate.

### TOOL3-131–156
**GitHub capability**  
Canonical port; read-first initial scope; generic write prohibited; external commits require permission/approval; dynamic state revalidation; no CLI/browser fallback; native idempotency not assumed; private data minimized; secrets excluded from context/audit.

### TOOL3-157–181
**AWS capability**  
Canonical port; read-only inventory/status/logs/metrics prioritized; write operations split; account/region/service/resource permission scope; no CLI/shell/browser fallback; dynamic state revalidated; IAM/secrets/delete/monetary commits excluded from normal initial capability.

## Cross-capability review

CR3-001 through CR3-010: **PASS**

Reviewed boundaries:
- Tool / Connector / Operation
- Permission / Credential / Approval
- Risk / External Commit
- Execution / Idempotency / UNKNOWN / Reconciliation
- Dynamic state / Source of Truth
- Freshness / Version
- Provenance / Privacy / Data minimization
- Personal Memory boundary
- Provider-specific SDK isolation
- Phase 4 Agent / Browser boundary

No redesign required.
