# Project Alice — Phase 3 Security Design

Status: **APPROVED / COMPLETE**

### SEC3-001
Credentials are handled in a dedicated security boundary, separate from Conversation/Memory/normal Tool data.

### SEC3-002
Connector Binding stores Credential Reference and safe metadata only.

### SEC3-003
Credentials are encrypted at rest; plaintext is not persisted in normal DB/log/API responses.

### SEC3-004
Connector authentication and Alice Permission are separate.

### SEC3-005
Alice Permission is always no broader than external credential authority.

### SEC3-006
Permission enforcement occurs at tool.application execution boundary.

### SEC3-007
Approval Evidence binds operation/version/target/risk/permission version and cannot be reused.

### SEC3-008
High Risk requires a dedicated confirmation path distinct from ordinary conversation text.

### SEC3-009
Secret redaction is layered across adapter/logging/API boundaries.

### SEC3-010
External content and AI output are not security authorities.

### SEC3-011
Initial credential storage favors OS/platform secret stores; normal DynamoDB is not the credential store.

### SEC3-012
Local development does not use repository/.env/plain config as the formal credential store.

### SEC3-013
Apple Calendar runtime must be explicitly registered as a trusted runtime.

### SEC3-014
Backend↔Apple Runtime requires runtime identity and request/session authentication; network reachability is insufficient trust.

### SEC3-015
Runtime communication protects confidentiality/integrity and binds requests/results to canonical Operation identity.

### SEC3-016
Credential rotation validates new credential before binding cutover.

### SEC3-017
Credential revoke/auth expiry blocks new operations; dispatched operations may reconcile only.

### SEC3-018
Authentication errors and Alice Permission errors remain distinct.

### SEC3-019
Phase 3 stays local-first; control APIs are not unnecessarily public.

### SEC3-020
Network location never replaces application authorization.

### SEC3-021
Web/GitHub/AWS logs/Calendar Notes and other retrieved content are untrusted data.

### SEC3-022
Prompt injection cannot acquire Tool permission, approval or credential authority.

### SEC3-023
Tool Results enter AI context through canonical envelope with source/trust/provenance/content type.

### SEC3-024
Attempts to exfiltrate credentials, Memory or private connector data are rejected.

### SEC3-025
Web Fetch validates scheme/host/redirect/address ranges to prevent SSRF.

### SEC3-026
URLs discovered in external content are not automatically fetched without a new policy evaluation.

### SEC3-027
Commands/scripts in source/issues/logs are not executable authority.

### SEC3-028
Approval UI displays backend-generated canonical operation summary, not AI prose alone.

### SEC3-029
Approval submit is rejected if displayed/current operation evidence no longer matches.

### SEC3-030
Security Audit is append-only; corrections are new entries.

### SEC3-031
Security Audit access is separated from normal conversation/tool access.

### SEC3-032
Retention Classes are defined by data type; not everything is kept indefinitely.

### SEC3-033
Deletion preserves minimum audit/execution integrity while separating deletable payload.

### SEC3-034
Credential leak, unauthorized connector use and unknown side effects are security incidents with disable/revoke/freeze/reconcile response.

### SEC3-035
Logging is allowlist-based; full request/response and sensitive external data are not standard logs.

### SEC3-036
SDK/dependency versions are managed and updates require vulnerability/compatibility verification.

### SEC3-037
Local development keeps production-like permission/approval/secret boundaries.

### SEC3-038
Test credentials/mocks/local data are isolated from production bindings and credentials.

### SEC3-039
Negative security tests cover bypass, stale approval, duplicate execution, prompt injection, SSRF, secret leakage and revoked credentials.

### SEC3-040
Security-critical uncertainty fails closed; writes/external commits do not proceed without sufficient security assurance.
