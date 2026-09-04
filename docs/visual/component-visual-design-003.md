# Project Alice — Component Visual Design 003
## Context Panels / Agent / Approval / Permission / Unknown / Emergency

**Date:** 2026-09-05 JST
**Status:** Proposed — User Approval Required
**Parents:** UI-BL-001 / Visual Detailed Design v2 (Accepted) / CVD-001 (Accepted) / CVD-002 v2 (Accepted)
**Scope:** Visual appearance only

# 1. Desktop hierarchy

Canonical Desktop hierarchy:

`Alice Core → Conversation (right) → Current Critical Context → Secondary Context`

Context panels are not permanent dashboard columns. They appear only when useful to the current interaction.

# 2. Shared Context Panel appearance

Reference widths:
- Compact: 220 px
- Standard: 260 px
- Critical/detail: 300–340 px

Appearance:
- deep navy translucent surface;
- 1 px restrained border;
- radius 14 px preferred;
- internal padding 14–16 px;
- title 12–13 px Semibold;
- body 12–14 px;
- restrained cyan/blue accents;
- no strong glow except localized state emphasis.

No panel may visually dominate Alice Core.

# 3. Secondary Status Panels

System Status / Memory Status:
- left/peripheral region;
- compact;
- shown only when contextually useful;
- maximum 2 secondary status panels simultaneously in normal Desktop state;
- no large metrics;
- no decorative charts unless the use case requires them.

# 4. Agent Running

Default = compact contextual card.

Contents:
- `AGENT RUNNING`;
- one-line current activity;
- optional progress indicator;
- optional `詳細` action.

Preferred width: 260–300 px.
Blue/cyan visual state.
No raw logs, Tool calls, observations or execution internals by default.

# 5. Approval Required

Approval is visually prominent but contextual.

Hierarchy:
1. `APPROVAL REQUIRED`
2. action
3. target
4. effect
5. risk
6. actions

Normal/medium:
- dark surface;
- amber accent where useful.

High/Critical:
- deep red-black surface;
- red border/title/icon;
- local red emphasis only.

Preferred width: 320–340 px.

# 6. Permission Required

Permission must not be visually confused with Approval.

Visual emphasis:
- blue/cyan + controlled violet;
- scope/lifetime/capability clearly grouped;
- permission icon distinct from approval icon;
- no red unless the actual risk state requires it.

Preferred width: 320–340 px.

# 7. Unknown / Verifying

- dark navy/violet-neutral surface;
- `VERIFYING…` or concise Japanese equivalent;
- no green success;
- no red failure unless authoritative failure is known;
- optional subtle verification progress;
- preferred width: 280–320 px.

Visual character: uncertain but controlled.

# 8. Emergency Stop

- critical red-black surface;
- red border/title;
- concise Japanese explanation;
- one unmistakable Stop action;
- no full-screen red flash;
- no alarm animation;
- no rollback implication.

Preferred width: 300–340 px.

# 9. Placement model

Normal:
- Core remains central.
- Conversation remains canonical right-side interaction region.
- Secondary status panels use left/peripheral region.

When Agent Running:
- compact Agent card appears peripheral to Conversation/Core.

When Approval / Permission / Unknown / Emergency is active:
- current critical context may temporarily occupy an outer-right or overlay/detail position adjacent to Conversation;
- it may reduce visible secondary panels;
- it must not permanently replace Conversation.

Only one primary critical-context card is foregrounded at a time.

# 10. Density rule

Normal Desktop target:
- Alice Core;
- Conversation;
- 0–2 secondary status panels;
- at most 1 active Agent/critical contextual card.

Forbidden default composition:
`System + Memory + Quick Actions + Agent + Activity + Approval + Unknown + Emergency` all visible simultaneously.

# 11. Transition appearance

Context panel appearance/disappearance:
- 180–260 ms;
- fade + small spatial translation;
- no large flying cards;
- no bounce;
- no dramatic zoom.

Reduce Motion:
- fade only or immediate state change.

# 12. Approval items

**CVD3-APP-001** — Shared panel widths and dark-glass appearance.

**CVD3-APP-002** — Normal density = 0–2 secondary panels + at most 1 active Agent/critical context.

**CVD3-APP-003** — Agent Running remains compact and blue/cyan by default.

**CVD3-APP-004** — Approval and Emergency use localized red/amber emphasis; Permission remains visually distinct.

**CVD3-APP-005** — Unknown/Verifying uses neutral navy/violet and never implies success/failure prematurely.

**CVD3-APP-006** — Critical context may temporarily occupy outer-right/overlay detail but does not permanently replace Conversation.

After approval, proceed to CVD-004: Desktop Spatial Composition / Responsive / State Golden Views.
