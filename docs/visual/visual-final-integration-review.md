# Project Alice — Visual Final Integration Review

**Review ID:** UI-VIS-REV-001
**Date:** 2026-09-05 JST
**Status:** REVIEW COMPLETE — FIXES REQUIRED BEFORE REPOSITORY SYNC
**Scope:** UI-BL-001, UI-VIS-SOT-001, existing Frontend/UI/Test/FIP documents, accepted Visual Detailed Design/CVD/Golden decisions

## 1. Review Result

The newly accepted visual direction is internally coherent, but the currently retained older Library documents have not yet been mechanically superseded/updated everywhere.

Result:

- Critical: 0
- High: 2
- Medium: 2
- Low: 0
- Visual semantic baseline: PASS
- Cross-document integration: FAIL pending approved consistency fixes
- Repository Sync: BLOCKED
- Implementation: BLOCKED

## 2. Findings

### UI-VIS-F01 — HIGH — Old Visual Detailed Design remains Draft and contains superseded decisions

The Library copy of `visual-detailed-design.md` is still Version 1 / Draft and retains open visual decisions that were subsequently approved or superseded.

Examples:
- Section 31 still lists desktop canvas, panel widths, colors, listening treatment, Core ratio, renderer timing as open.
- Desktop Conversation allows below-Core placement.
- Mobile Ambient text still allows a microphone control, while the accepted later baseline prohibits a default microphone icon.

Resolution:
- Supersede the old Draft with the accepted Visual Detailed Design v2 / UI-VIS-SOT-001 rules.
- Mark the old Version 1 as superseded, not active SoT.
- Canonical Desktop Conversation = right side of Core.
- Default microphone icon = prohibited.

### UI-VIS-F02 — HIGH — Existing formal Frontend/Test/FIP documents do not yet reference UI-VIS-SOT-001

Existing `frontend-ui-design.md`, `frontend-design.md`, `test-design.md`, `frontend-implementation-plan.md`, FIP-006, FIP-007 and FIP-012 predate the final visual freeze.

Resolution:
Add a normative reference:
`UI-BL-001 → UI-VIS-SOT-001 → relevant CVD/Golden → existing UI/FIP implementation contract`.

No backend semantics change.

### UI-VIS-F03 — MEDIUM — Golden registry is incomplete for the newly accepted multi-display contract

Existing test design contains broad Golden categories such as Ambient, Conversation, Agent Running, Approval, Unknown, Emergency Stop and Desktop Spatial, but does not yet enumerate the accepted single/dual/triple-display, disconnect migration and Reduce Motion multi-display states.

Resolution:
Add GV-01–GV-13 / equivalent Golden registry and the Visual Regression Gate.

### UI-VIS-F04 — MEDIUM — Phase 1 FIP language must distinguish architecture replaceability from premature future implementation

The accepted Alice Core renderer contract requires replacement capability at the presentation boundary, while the Phase 1 plan correctly prohibits creating speculative future packages/interfaces merely for later phases.

Resolution:
Clarify:
- Phase 1 implementation must avoid coupling business/application logic to the current Core renderer.
- This does not require implementing a speculative cross-phase renderer framework in Phase 1.
- Replaceability is achieved by presentation isolation and dependency direction at the implemented scope.

## 3. No-conflict confirmations

The following remain consistent:
- Conversation History ≠ Personal Memory.
- Alice Core is Presence, not authority.
- Renderer replacement is Presentation-only.
- Capability Growth ≠ Dashboard Growth.
- Approval / Permission / Unknown / Emergency remain distinct.
- Emergency Stop does not imply rollback.
- Voice is not identity or authority.
- Phase 1 mobile shell remains Header → Core → Message → Composer.
- Phase 1 default Composer remains Text + Send.
- Desktop/multi-display additions do not expand Phase 1 implementation scope by themselves.

## 4. Required Integration Patch Set

Update/reissue:
1. `visual-detailed-design.md` → accepted/frozen v2-aligned edition.
2. `frontend-ui-design.md` → add UI-VIS-SOT-001 hierarchy, right-side Desktop Conversation, multi-display role rules.
3. `frontend-design.md` → add Presentation-only display-role/rendering boundary.
4. `test-design.md` → add GV-01–GV-13 and Visual Regression Gate.
5. `frontend-implementation-plan.md` → add Visual SoT bundle requirement before UI slices.
6. `FIP-006` → tokens/Core renderer isolation + frozen visual reference.
7. `FIP-007` → preserve Phase 1 mobile shell; note desktop/multi-display is future implementation scope.
8. `FIP-012` → Golden/Visual Drift tests reference UI-VIS-SOT-001.
9. Phase 0 Final Review → reopen only the visual-delta integration gate, not backend design.

## 5. Gate State

```text
Functional / Architecture Design: PASS
Visual Direction: FROZEN
Visual Detailed Design: ACCEPTED
CVD-001–004: ACCEPTED
Golden State Design: ACCEPTED
Visual Cross-document Integration: FIX REQUIRED
Repository Sync Gate: OPEN
Implementation Authorization: BLOCKED
```

## 6. Next Step

Apply the approved consistency-only patch set above, then run a focused Visual Cross-document Re-review.

If Critical / High / Blocking Medium = 0 after patching:
- close the visual-delta review;
- update Phase 0 Final Design Review;
- proceed to Repository Sync Gate.

No implementation begins during this work.
