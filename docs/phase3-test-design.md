# Project Alice — Phase 3 Test Design

Status: **APPROVED / COMPLETE**

### TEST3-001
Use multiple test levels: Unit / Component / Contract / Integration / E2E / Security / Recovery.

### TEST3-002
Permission/Risk/Approval/Execution policy must be unit-testable without external SDKs.

### TEST3-003
Define canonical Connector Port contract suites for EventKit/GitHub/AWS/Web adapters.

### TEST3-004
Contract tests verify provider SDK types/errors do not leak into Core/API.

### TEST3-005
Use Fake/Stub/Mock Connectors as formal test infrastructure instead of directly calling external services by default.

### TEST3-006
Fake connectors must deterministically simulate timeout/partial/rate-limit/unknown/async/stale states.

### TEST3-007
Write tests assert external side-effect count and prevent duplicate dispatch.

### TEST3-008
Transaction tests verify atomic Approval consumption + Durable Intent + PREPARED.

### TEST3-009
Concurrency tests cover Operation Version / Execution Fence / stale executor.

### TEST3-010
Use DynamoDB Local for integration tests with the same logical schema/condition/transaction contract.

### TEST3-011
AWS-specific behavior not reproduced locally is tested separately in cloud/contract integration tests.

### TEST3-012
Migration tests cover old-schema reads, new-schema writes and phased compatibility.

### TEST3-013
Consistency tests verify security-critical reads do not rely on stale state.

### TEST3-014
Permission matrix spans Connector × Binding × Operation × Resource Scope × Allow/Deny/Ask.

### TEST3-015
Negative tests cover default deny, specific deny, ask, permission version change and immediate revoke.

### TEST3-016
Broad credential scope must not bypass narrower Alice Permission.

### TEST3-017
Approval tests verify binding to Operation ID/version/target/arguments/impact/risk/permission version.

### TEST3-018
Negative approval tests cover material change, expiry, reuse, double submit and rejected operation replay.

### TEST3-019
High Risk cannot execute without dedicated approval.

### TEST3-020
UNKNOWN must reconcile the same operation and never trigger blind new-operation retry.

### TEST3-021
Fault injection covers external write success/possible success followed by Alice persistence failure → UNKNOWN/RECOVERY_REQUIRED.

### TEST3-022
Crash windows are tested independently before dispatch, during dispatch, after dispatch and during result persistence.

### TEST3-023
Recovery Worker resumes nonterminal operations and prioritizes reconciliation when side effect is uncertain.

### TEST3-024
Multiple Recovery Workers cannot cause duplicate side effects due to execution fencing.

### TEST3-025
Cancel Request and Cancelled remain distinct; cancel-requested may still end Succeeded/Unknown.

### TEST3-026
After permission/credential revoke, new operations stop while already-dispatched operations may only reconcile.

### TEST3-027
Prompt injection is tested from Web, GitHub issue/PR/source, AWS logs and Calendar notes.

### TEST3-028
Prompt injection cannot obtain permission, approval, credentials or Phase 4 Agent authority.

### TEST3-029
Data-exfiltration tests reject attempts to send private repo/Memory/credentials/AWS data to external URLs.

### TEST3-030
Web Fetch negative tests cover localhost/private IP/link-local/metadata endpoint/redirect SSRF.

### TEST3-031
Secret redaction tests cover headers, SDK errors, exceptions, logs, API responses, audit and AI context.

### TEST3-032
Security logging tests enforce allowlisted fields and prohibit full request/response logging.

### TEST3-033
AI tool-selection tests evaluate correct proposal from the minimal available tool set.

### TEST3-034
Hallucinated tool/parameter/enum/missing required args fail closed.

### TEST3-035
Deterministic policy overrides erroneous AI Risk/Permission/Approval judgment.

### TEST3-036
AI must not phrase UNKNOWN/PARTIAL/IN_PROGRESS as SUCCEEDED/COMPLETE.

### TEST3-037
Partial/truncated/rate-limited results are not described as complete.

### TEST3-038
Stale external observations are not asserted as current without re-read or uncertainty language.

### TEST3-039
AI provider failure cannot modify an already-executed Tool Operation outcome.

### TEST3-040
Provider fallback uses the same canonical tool/security/execution contract.

### TEST3-041
Tool-loop call/time/data/page/retry budgets stop runaway loops safely.

### TEST3-042
Multi-tool-call tests verify dependency order and no Read→Write authority inheritance.

### TEST3-043
Dynamic external state is not unconditionally saved to Personal Memory.

### TEST3-044
Explicit Memory capture still passes Phase 2 Memory Policy.

### TEST3-045
Apple Calendar tests emphasize timed/all-day/TZ/DST/recurrence/free-time behavior.

### TEST3-046
Multiple calendar candidates never cause silent target selection.

### TEST3-047
EventKit runtime offline, OS permission denied, invalid binding and Alice permission denied are distinct failures.

### TEST3-048
Apple Runtime mutation crash reproduces EventKit no-native-idempotency UNKNOWN/reconciliation behavior.

### TEST3-049
Backend↔Apple Runtime rejects unknown runtime, bad identity, operation mismatch and replay.

### TEST3-050
GitHub write tests cover duplicate POST prevention and current-state revalidation.

### TEST3-051
GitHub stale branch/PR state causes stale target/version and re-approval when required.

### TEST3-052
Private GitHub data does not leak into public-web results or unnecessary conversation context.

### TEST3-053
AWS tests cover account/region/resource scope and Alice Permission precedence over credential authority.

### TEST3-054
AWS async/timeout/throttling/unknown-5xx does not blind retry.

### TEST3-055
Excluded AWS operations such as IAM/secrets/delete/direct monetary commit remain unreachable from normal tool path.

### TEST3-056
API contract tests cover canonical errors, HTTP mapping, retry hint, completeness metadata and opaque cursor.

### TEST3-057
Frontend timeout does not mark operation failed; it re-fetches authoritative Operation Resource.

### TEST3-058
Frontend approval tests cover back/close ≠ reject, double-submit prevention and stale approval blocking.

### TEST3-059
UNKNOWN UI prioritizes reconcile rather than blind retry.

### TEST3-060
Connector UI keeps Enabled/Authenticated/Permitted/Available/OS Authorization distinct.

### TEST3-061
Offline/app restart restores operation state from backend.

### TEST3-062
Sensitive data is not unnecessarily retained in Flutter widget state/local cache/crash reports.

### TEST3-063
Accessibility tests verify non-color-only state communication and screen-reader labels.

### TEST3-064
Audit tests prohibit overwrite and record corrections as new append entries.

### TEST3-065
Execution-history projection failure does not change external operation outcome.

### TEST3-066
Backup/restore tests revalidate permissions, bindings and nonterminal operations.

### TEST3-067
Restore never implies external state rollback and never re-dispatches already-sent operations blindly.

### TEST3-068
SDK/dependency updates require connector contract and regression tests.

### TEST3-069
Automated tests use synthetic/test accounts rather than production credentials/private calendars/production AWS resources.

### TEST3-070
Release gate requires all critical security/execution tests to pass; duplicate/UNKNOWN/security-bypass failures block release.

## Release-blocking categories

- Permission bypass
- Approval bypass
- Duplicate external write
- Execution Fence failure
- UNKNOWN blind retry
- Credential leak
- Prompt-injection privilege escalation
- SSRF
- Stale approval execution
- Revoked-credential write
- Apple Runtime spoofing
