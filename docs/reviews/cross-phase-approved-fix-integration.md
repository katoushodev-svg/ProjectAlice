# Project Alice — Phase 1〜4 Cross-Phase Review Approved Fix Integration

**Date:** 2026-09-04 JST  
**Status:** Approved Fix Set / Ready for Re-review  
**Implementation:** Not Started / Do Not Start Yet

## 1. Purpose

Phase 1〜4 Cross-Phase Design Consistency Reviewで承認されたFinding修正を、正式Repositoryへ反映可能な統合版としてまとめる。

## 2. Resolved Findings in This Set

### CPR2-NUM-F01 — ADR Decision ID Integrity

- Existing `ADR-014 Phase 0設計範囲とAI Capability Boundaryの変更`をStable IDとして復元・保持。
- Phase 4 ADRを以下へ再採番。
  - ADR-015 Shared Execution Authority Boundary
  - ADR-016 Conversation-centered Capability Orchestration
  - ADR-017 Cross-Phase Alice Experience Principle
  - ADR-018 Agent Orchestration / Executor Separation
- ADR-014〜018の重複なしを機械確認済み。

### CPR2-DOC-F01 — Phase 4 Formal Integration

以下へPhase 4正式統合Sectionを反映。

- `decisions.md`
- `repository-structure.md`
- `api-design.md`
- `database-design.md`
- `frontend-ui-design.md`
- `frontend-design.md`
- `test-design.md`
- `phase3-tool-design.md`
- `phase4-formal-document-integration-patches.md`

`ai-design.md`および`security-design.md`は既存Library上でPhase 4 Formal Integration済みのため、本Fix Setでは再生成していない。

### CPR2-MIG-F01 — ToolOperation Stable Identity Migration Contract

`phase3-tool-design.md`および`database-design.md`へ次を明文化。

- `ToolOperation.operationId / operationVersion`はshared execution昇格後もStable Identity。
- `AgentAction` Identityと`ToolOperation` Identityは別物であり置換しない。
- AgentAction→ToolOperationはCorrelation Referenceで接続。
- ToolOperationにBindingされたPermission / Approval / Durable Intent / Idempotency / Attempt / AuditのIdentity Chainを維持。
- Material Changeは新Operation / 新Versionとして再評価。

## 3. Metadata Corrections

- `phase3-tool-design.md`: `Draft — Detailed Design in Progress` → `Design Complete`
- `test-design.md`: Phase 4正式Test Registry統合済みStatusへ更新
- `repository-structure.md`: 旧Phase 4〜6 Evolution記述を現行Phase 4定義へ更新

## 4. Mechanical Validation

確認済み:

- ADR-014〜018 each exactly once
- obsolete `Phase 5 agent + Voice + Device Control` sequence removed
- shared `execution` ownership present
- UI-XP-001 present
- Phase 4 Test Registry present
- Phase 3 status = Design Complete
- ToolOperation Stable Identity rule present
- Phase 4 API / Persistence integration sections present

## 5. Next Gate

1. Cross-Phase Re-review
2. FIP-003〜012 Re-review
3. Phase 0 Final Design Review
4. Only after PASS: resume implementation from FIP-003
