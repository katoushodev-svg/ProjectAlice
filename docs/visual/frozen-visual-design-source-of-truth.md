# Project Alice — Frozen Visual Design Source of Truth

**Document ID:** UI-VIS-SOT-001  
**Version:** 1.0  
**Date:** 2026-09-05 JST  
**Status:** Frozen — Implementation-Facing Visual Source of Truth  
**Scope:** Project Alice visual appearance, spatial composition, component appearance, multi-display presentation, Golden visual regression rules  
**Implementation status:** Not authorized by this document alone

---

# 1. Purpose

This document consolidates the approved Project Alice visual design into one implementation-facing contract.

AI implementation must not infer a different visual direction from generic application conventions. When an implementation detail is not defined here or in an explicitly referenced lower-level visual specification, the implementation AI must report the gap instead of inventing a new visual pattern.

---

# 2. Governing Visual Documents

The visual design chain is:

1. `UI-BL-001 — Alice Experience / Visual Baseline`
2. `UI-VIS-SOT-001 — Frozen Visual Design Source of Truth` (this document)
3. `Visual Detailed Design v2`
4. `CVD-001 — Alice Core / Background / Spatial Frame`
5. `CVD-002 v2 — Conversation / Message / Composer — Right Spatial Conversation`
6. `CVD-003 — Context Panels / Agent / Approval / Permission / Unknown / Emergency`
7. `CVD-004 — Desktop Spatial Composition / Multi-Display / Responsive`
8. `Golden View Specification 001`
9. Component implementation / FIP
10. Generated mockups / Golden captures

A lower layer may implement or refine a higher layer but may not reinterpret it.

Generated images are evidence/review aids only. If an image conflicts with this Source of Truth, the image is wrong.

---

# 3. Core Experience

**Conversation is Alice.**

Alice is experienced primarily through:
- Alice Core as Presence;
- Conversation as communication;
- contextual capabilities when needed.

Memory, Tool, Agent, Permission, Approval, Execution, Browser, Filesystem, Application and OS capabilities do not become independent primary products merely because they exist.

**Capability Growth ≠ Dashboard Growth.**

---

# 4. Alice Core

## 4.1 Meaning

Alice Core is:
- Alice's visual Presence;
- a presentation of semantic Alice state.

Alice Core is not:
- a logo;
- avatar;
- button;
- microphone;
- loading spinner;
- authority indicator;
- permission indicator;
- risk lamp.

## 4.2 Current visual family

- irregular particle-mesh sphere;
- cyan luminous center;
- electric-blue network;
- controlled violet filaments;
- sparse white energy points;
- three thin orbital/interface rings;
- restrained ticks/nodes;
- local glow;
- abstract dark-space surroundings.

## 4.3 Replaceable renderer contract

`AliceCoreState → AliceCorePresentationModel / Presentation Contract → AliceCoreRenderer → Visual Expression`

The renderer is replaceable.

The following may not leak into Domain/Application contracts:
- particle count;
- geometry;
- shader data;
- image path;
- painter/canvas technology;
- 2D/3D technology;
- animation framework;
- ring implementation.

Replacing the visual renderer is a Presentation change unless semantic Alice states themselves change.

---

# 5. Color / Surface Language

Canonical Phase 1 tokens remain:

| Token | Value |
|---|---|
| background | `#020812` |
| backgroundElevated | `#07111F` |
| surface | `#0D1828` |
| surfaceStrong | `#0D3A73` |
| textPrimary | `#F5F8FF` |
| textSecondary | `#AEB9CA` |
| border | `#1B304A` |
| primary | `#1677E8` |
| coreCyan | `#00D9FF` |
| coreBlue | `#176BFF` |
| coreViolet | `#6C45FF` |
| coreAmber | `#F6A623` |
| error | `#FFB4AB` |
| errorSurface | `#3B1D1C` |

Visual character:
- dark-first;
- deep navy / near-black;
- cyan/electric blue;
- controlled violet;
- restrained glass surfaces;
- thin borders;
- localized glow;
- red reserved for destructive/critical surfaces.

No generic light-theme reinterpretation is allowed without a new approved baseline decision.

---

# 6. Explicit Text Restraint

Normal product UI must not contain generated decorative slogans/taglines.

Forbidden examples include:
- `Your Life, More You.`
- `Your Life...`
- `いつも、あなたと。`
- `もっと、あなたらしく。`
- `A smarter life, a calmer you.`
- `A calmer tomorrow, with you.`
- other AI-invented marketing/decorative copy.

Persistent identity text is limited to functionally required product identity such as `Alice` or `Project Alice`.

---

# 7. Default Chrome Prohibitions

Do not add by default:
- `Home / Chat / Memory / Tools / Agents / Executions / Files / Settings` permanent capability navigation;
- generic search bar;
- notification bell;
- profile/avatar control;
- SaaS account chrome;
- decorative marketing header;
- default microphone button;
- model selector;
- Tool selector.

A later accepted use case may introduce a contextual control, but it must not silently redefine the primary Alice experience.

---

# 8. Single-Display Desktop

Canonical layout:

`Large Alice Core | Conversation on right`

Reference:
- canvas for Golden review: 1536×1024;
- Core sphere preferred target: 540 px;
- Core visual envelope: ~710 px;
- Conversation preferred width: 400 px;
- responsive range: 360–440 px;
- visible message height target: ~510 px;
- Core-to-Conversation gap: 24–32 px.

Priority under layout pressure:

`Alice Core → Conversation → Critical Context → Secondary Panels`

Secondary panels collapse before Alice Core is materially reduced.

---

# 9. Conversation

Conversation remains visually connected to Alice and must not look like an independent generic messenger.

User:
- right aligned;
- `surfaceStrong`;
- max 78% of Conversation region in the accepted right-side design.

Alice:
- left aligned;
- `surface`;
- max 88%.

Default:
- no avatars;
- no miniature Alice Core;
- no repeated role labels;
- no timestamps;
- no read receipts.

Long responses scroll naturally. Structured text does not create a new unrelated visual language.

---

# 10. Composer

Default Phase 1:
- text entry;
- Send only.

Desktop preferred:
- aligned to Conversation;
- minimum height 56 px;
- Send target 48×48 px;
- text grows 1–5 lines;
- placeholder `メッセージを入力`.

Do not add by default:
- microphone;
- attachment;
- plus menu;
- Tool selector;
- Model selector;
- emoji control.

---

# 11. Context Panels

Shared appearance:
- compact 220 px;
- standard 260 px;
- critical/detail 300–340 px;
- deep navy translucent surface;
- 1 px restrained border;
- preferred radius 14 px;
- padding 14–16 px.

Normal Desktop density:
- 0–2 secondary status panels;
- at most one active Agent/critical contextual surface.

Do not display System + Memory + Quick Actions + Agent + Activity + Approval + Unknown + Emergency simultaneously as a default dashboard.

---

# 12. State Surfaces

## Agent Running
- compact;
- blue/cyan;
- current activity;
- optional progress;
- Detail on demand;
- no raw logs by default.

## Approval Required
- contextual;
- action / target / effect / risk / actions;
- amber where appropriate;
- localized red for high/critical risk.

## Permission Required
- visually distinct from Approval;
- blue/cyan + controlled violet;
- scope / lifetime / capability emphasized.

## Unknown / Verifying
- navy/violet neutral treatment;
- no success green;
- no failure red until authoritative outcome;
- communicates uncertainty as controlled verification.

## Emergency Stop
- localized critical red-black surface;
- unmistakable Stop action;
- no full-screen flashing red;
- no rollback implication;
- Alice Core itself does not become a flashing red alarm.

---

# 13. Canonical Multi-Display Model

Multi-display creates more presentation space, not multiple Alices.

There is exactly one **Primary Alice Presence**.

Visual display roles:
- Presence Display;
- Conversation Display;
- Detail Display.

These are presentation roles only.

---

# 14. Triple-Display Canonical Layout

**Left Optional Work / Detail | Center Alice Presence | Right Portrait Conversation**

## Center — Presence Display
- dedicated giant Alice Core;
- no Conversation by default;
- no permanent status panels;
- no navigation;
- no microphone;
- no slogan.

Dedicated Presence scale:
- sphere = 58–68% of shorter usable display dimension;
- ring/glow envelope = 76–90%;
- safe visual margin ≥5%.

## Right Portrait — Conversation Display
- minimal Alice identity/header;
- vertically scrolling Conversation;
- Composer fixed at bottom;
- no duplicate Alice Core;
- no avatar;
- no microphone;
- contextual Approval/Permission/Unknown may appear as contextual sheets/cards.

## Left — Optional Work / Detail
Default ownership remains with the user.

It may contain:
- normal development/work applications;
- Browser;
- Files;
- Agent Detail;
- Execution Detail;
- other explicitly opened subordinate detail.

Alice must not permanently take over this display.

---

# 15. Dual Display

Preferred landscape + portrait:
- landscape = Presence Display;
- portrait = Conversation Display.

Two landscape displays:
- primary = Presence;
- secondary = Conversation using a constrained reading column.

---

# 16. Display Changes

## Conversation Display disconnected
- Conversation returns to Presence Display using Single-Display canonical composition;
- Core reduces smoothly;
- Conversation state is preserved.

## Presence Display disconnected
- one available configured display becomes Presence Display;
- exactly one Core remains;
- do not temporarily show multiple authoritative-looking Cores.

Additional displays do not automatically receive Alice UI merely because they are connected.

---

# 17. Alice Core State Appearance

Idle:
- low-energy cyan/blue;
- slow restrained motion.

Listening:
- modest cyan increase;
- optional thin waveform/status;
- no default microphone icon.

Thinking:
- concentrated center glow;
- controlled violet increase;
- restrained pulse/ring motion.

Responding:
- stable luminous center;
- subtle outward energy;
- Conversation streams response where visible.

Agent Running:
- structured energy;
- progress primarily expressed by contextual UI.

Approval Attention:
- restrained attention cue only;
- risk is communicated by the contextual surface.

Unknown / Verifying:
- neutral cyan/violet uncertainty expression.

Emergency:
- Core visually quiets;
- critical red remains localized to Emergency UI.

---

# 18. Motion

Accepted Phase 1 reference:
- Idle ring: 12s/rev;
- Thinking ring: 6s/rev;
- Streaming ring: 8s/rev;
- Thinking pulse: 2s;
- pulse scale: 0.98–1.02;
- state transition: 300 ms.

Context panel transitions:
- 180–260 ms;
- fade + small spatial translation;
- no bounce/large zoom.

Reduce Motion:
- decorative motion disabled;
- static state equivalents remain readable;
- information never depends on motion alone.

---

# 19. Golden View Set

Required canonical visual states:

1. Single Display / Idle
2. Single Display / Active Conversation
3. Dual Display / Presence + Portrait Conversation
4. Triple Display / Canonical Idle
5. Triple Display / Listening
6. Triple Display / Thinking / Responding
7. Triple Display / Agent Running
8. Triple Display / Approval Required
9. Triple Display / Unknown / Verifying
10. Triple Display / Emergency Stop
11. Conversation Display Disconnect → Single Display fallback
12. Presence Display Disconnect → Presence migration
13. Reduce Motion

Golden images are review baselines, not editable targets for hiding implementation drift.

---

# 20. Visual Regression Gate

Every UI implementation/mockup must verify:

1. Alice Core remains the first visual focus where Presence is shown.
2. Correct Core scale and placement.
3. Exactly one Primary Alice Core across displays.
4. Conversation uses the correct display role.
5. No forbidden navigation/chrome.
6. No decorative slogan/tagline.
7. No default microphone icon.
8. Correct contextual-panel density.
9. Approval / Permission / Unknown / Emergency remain visually distinct.
10. Correct state color semantics.
11. Readability / contrast / reduced-motion behavior.
12. Multi-display role consistency.
13. Renderer replaceability remains intact.
14. No business logic depends on current Core visual implementation.

Any failed item = **Visual Drift** and blocks visual acceptance.

---

# 21. AI Implementation Contract

Every AI implementation task affecting UI must receive:
- UI-BL-001;
- UI-VIS-SOT-001;
- the relevant CVD;
- relevant FIP;
- relevant Golden reference.

The implementation AI must report:
- visual files changed;
- token changes;
- Golden differences;
- unresolved visual specification;
- assumptions it would otherwise need to invent.

If a required visual decision is unspecified, **stop and report the gap instead of inventing a new visual direction.**

---

# 22. Change Control

This document is Frozen.

It may be changed only by:
1. explicit user-requested visual change;
2. recorded revision/decision;
3. impact review against affected CVD / Golden / FIP / Test documentation;
4. re-running Visual Drift review.

Capability additions, implementation convenience, AI-generated mockups, framework limitations or ordinary refactoring do not automatically authorize changes to this visual baseline.

---

# 23. Implementation Boundary

This document freezes the visual design but does not by itself authorize implementation.

Repository synchronization and the project's remaining final review / implementation gates remain separate requirements.

**Visual Design Status: FROZEN**  
**Visual Source of Truth: UI-VIS-SOT-001**  
**Implementation Authorization: NOT GRANTED BY THIS DOCUMENT**
