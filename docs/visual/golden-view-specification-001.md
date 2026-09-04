# Project Alice — Golden View Specification 001
## Desktop / Multi-Display State Reference Set

**Date:** 2026-09-05 JST  
**Status:** Proposed — User Approval Required  
**Parents:** UI-BL-001 / Visual Detailed Design v2 / CVD-001–004 (Accepted)  
**Scope:** Visual regression / state composition only

# 1. Purpose

Golden Views are canonical visual states used by design review and AI implementation.

A generated mockup or implementation that conflicts with these views is a Visual Drift failure. Golden images may not be updated merely to make implementation tests pass.

# 2. Global invariants

Every Golden View must preserve:
- Alice Core = one Primary Presence.
- No duplicate Core across displays.
- No decorative slogans/taglines.
- No default microphone icon.
- No permanent capability navigation.
- No generic SaaS search/bell/avatar chrome.
- Dark abstract-space visual family.
- Conversation is subordinate to Presence but remains primary communication.
- Context panels are progressive/on-demand.
- Alice Core renderer remains replaceable.

# 3. GV-01 — Single Display / Idle

Composition:
- large Alice Core center-left/center;
- Conversation right, visually quiet;
- empty or minimal conversation state;
- no secondary panel unless needed;
- Composer visible.

Core: Idle.
Primary visual focus: Core.

# 4. GV-02 — Single Display / Active Conversation

- Core remains large.
- Conversation right contains 2–5 representative messages.
- User right / Alice left.
- No avatars/timestamps/read receipts.
- Composer fixed to Conversation bottom.
- no secondary dashboard clutter.

# 5. GV-03 — Dual Display / Presence + Portrait Conversation

Landscape Presence Display:
- Core centered;
- sphere 58–68% shorter dimension;
- envelope 76–90%;
- no Conversation/panels by default.

Portrait Conversation Display:
- minimal Alice header;
- conversation full vertical reading surface;
- Composer bottom-fixed;
- no Core duplicate.

# 6. GV-04 — Triple Display / Canonical Idle

Left:
- ordinary user workspace or neutral desktop; Alice does not occupy it by default.

Center:
- dedicated Alice Presence, giant Core only + abstract ambient background.

Right portrait:
- Conversation.

No status dashboard is permanently visible.

# 7. GV-05 — Triple Display / Listening

Center:
- same giant Core;
- cyan energy modestly increased;
- optional thin waveform/status;
- no microphone icon.

Right:
- Conversation remains available but visually calm.

Left:
- user workspace unchanged.

# 8. GV-06 — Triple Display / Thinking / Responding

Thinking:
- center Core uses faster ring/breathing treatment and controlled violet energy.

Responding:
- right portrait Conversation streams Alice response;
- center Core uses Responding presentation;
- no duplicate progress indicator required.

# 9. GV-07 — Triple Display / Agent Running

Center:
- Core remains dominant;
- Agent Running state expressed subtly.

Right:
- Conversation plus one compact `AGENT RUNNING` contextual card.
- current activity + optional progress + Detail.

Left:
- if user explicitly opens detail, may host Agent/Execution Detail.
- otherwise remains user's workspace.

# 10. GV-08 — Triple Display / Approval Required

Center:
- Core remains Alice Presence;
- restrained attention cue only.

Right:
- Approval Required foregrounded adjacent to/within Conversation context.
- action, target, effect, risk, actions visible.
- localized amber/red according to risk.
- Conversation remains recoverable/visible.

Left:
- unaffected unless user opens detail.

# 11. GV-09 — Triple Display / Unknown / Verifying

Center:
- neutral cyan/violet verification expression.

Right:
- `VERIFYING…` / Japanese explanation;
- navy/violet surface;
- no success green;
- no failure red before authoritative result.

Left:
- optional detail only on demand.

# 12. GV-10 — Triple Display / Emergency Stop

Center:
- Core visually quiets; no flashing red orb.

Right:
- localized critical red-black Emergency Stop surface;
- one unmistakable Stop action;
- text explicitly avoids rollback implication.

Left:
- no forced takeover.

# 13. GV-11 — Conversation Display Disconnect

Before:
Center Presence + Right Portrait Conversation.

After:
- Conversation migrates to Presence Display right side using CVD-002 v2.
- Core reduces smoothly to single-display scale.
- no Conversation state loss.
- no second Core appears.

# 14. GV-12 — Presence Display Disconnect

- Alice Presence migrates to configured available primary display.
- exactly one Core remains.
- Conversation retains its role when its display is still available.
- transition must not temporarily present two authoritative-looking Cores.

# 15. GV-13 — Reduce Motion

All layouts remain recognizable.
- no decorative particle drift;
- no ring rotation requirement;
- static state cues remain distinguishable;
- no information depends on animation alone.

# 16. AI visual-regression gate

Before accepting implementation/mockup, verify:
1. Core placement/scale.
2. Conversation display role.
3. No Core duplication.
4. No forbidden chrome.
5. No slogans.
6. No microphone icon.
7. Correct contextual panel density.
8. Correct state color semantics.
9. Readability/contrast.
10. Multi-display role consistency.

Any failure blocks visual acceptance.

# 17. Approval items

**GV-APP-001** — Approve GV-01–04 as canonical Idle/Conversation display compositions.  
**GV-APP-002** — Approve GV-05–06 Listening/Thinking/Responding compositions.  
**GV-APP-003** — Approve GV-07 Agent Running composition.  
**GV-APP-004** — Approve GV-08 Approval composition.  
**GV-APP-005** — Approve GV-09 Unknown/Verifying composition.  
**GV-APP-006** — Approve GV-10 Emergency Stop composition.  
**GV-APP-007** — Approve GV-11–12 display-disconnect migration compositions.  
**GV-APP-008** — Approve GV-13 Reduce Motion composition and the 10-point visual-regression gate.

After approval, consolidate UI-BL-001 + Visual Detailed Design v2 + CVD-001–004 + Golden View Specification into the frozen implementation-facing Visual Design Source of Truth package.
