# Project Alice — Visual Detailed Design

**Document:** `visual-detailed-design.md`  
**Version:** 2  
**Date:** 2026-09-04 JST  
**Status:** Accepted / Frozen — UI-VIS-SOT-001 Aligned  
**Scope:** Visual appearance only. No API / Domain / Permission / Agent / Tool behavior changes.  
**Parent Baseline:** UI-BL-001 — Alice Experience / Visual Baseline  
**Related:** UI-XP-001, UI-012, UI-013, UI-014, UI-016, FIP-006, FIP-007  
**Reference:** User-approved `Project Alice — Cross-Phase Frontend / Visual Principle` image

---

# 1. Purpose

This document defines Project Alice's visual appearance at a level where an AI coding assistant should not need to invent or reinterpret the visual design.

This document is intentionally limited to:

- color;
- typography;
- spacing;
- surface;
- border;
- glow;
- icon treatment;
- Alice Core visual presentation;
- message appearance;
- composer appearance;
- contextual card appearance;
- desktop spatial composition;
- mobile visual composition;
- state-specific visual presentation;
- visual responsive rules;
- animation appearance;
- visual regression rules.

This document does **not** decide:
- whether an action is allowed;
- Permission / Approval semantics;
- Agent execution rules;
- API payloads;
- persistence;
- business-state transitions.

Those remain owned by their existing Source of Truth.

---

# 2. Visual Source-of-Truth Order

Visual decisions follow:

1. `UI-BL-001`
2. this `visual-detailed-design.md`
3. `UI-XP-001`
4. `UI-012`
5. Phase-specific UI
6. Component Design
7. FIP
8. Implementation
9. Generated mockup

A generated mockup can never override this document.

---

# 3. Visual Identity

Project Alice must look:

- futuristic;
- intelligent;
- calm;
- personal;
- dark;
- spatial;
- luminous;
- technical without looking like an admin console;
- inspired by JARVIS-like ambient intelligence, but visually original to Alice.

It must **not** look like:

- generic SaaS dashboard;
- generic ChatGPT clone;
- enterprise admin panel;
- social chat application;
- mobile messenger;
- game HUD with excessive visual noise;
- cyberpunk neon overload;
- permanent system-monitor dashboard.

The visual hierarchy is:

`Alice Presence → Conversation → Contextual Capability → Detail`

---

# 4. Canonical Color Tokens

Existing accepted Phase 1 tokens remain unchanged.

| Token | Hex | Visual role |
|---|---:|---|
| `background` | `#020812` | deepest screen background |
| `backgroundElevated` | `#07111F` | slightly raised dark region |
| `surface` | `#0D1828` | assistant bubbles / input / standard card |
| `surfaceStrong` | `#0D3A73` | user bubble / selected emphasis |
| `textPrimary` | `#F5F8FF` | primary text |
| `textSecondary` | `#AEB9CA` | metadata / helper text |
| `border` | `#1B304A` | standard border / divider |
| `primary` | `#1677E8` | principal interactive blue |
| `coreCyan` | `#00D9FF` | Alice Core cyan |
| `coreBlue` | `#176BFF` | Alice Core blue |
| `coreViolet` | `#6C45FF` | Alice Core violet |
| `coreAmber` | `#F6A623` | limited Alice Core interface accent |
| `error` | `#FFB4AB` | error text |
| `errorSurface` | `#3B1D1C` | error surface |

## 4.1 Cross-phase visual semantic additions

These are visual aliases, not new authority states.

| Visual role | Value / source |
|---|---|
| `successVisual` | `#22D36A` |
| `warningVisual` | `#FFB02E` |
| `dangerVisual` | `#FF4D4F` |
| `criticalSurface` | deep red-black, target `#2A0C10` |
| `glassSurface` | `surface` with 78–92% opacity depending on backdrop |
| `glassBorder` | `coreBlue` or `border` at restrained opacity |

Raw values must not be scattered through components.

---

# 5. Typography

System fonts remain the baseline.

## 5.1 Mobile tokens — fixed

| Token | Size | Weight |
|---|---:|---|
| `title` | 22 | Regular |
| `body` | 17 | Regular |
| `bodyEmphasis` | 17 | Semibold |
| `supporting` | 14 | Regular |
| `caption` | 12 | Regular |
| `code` | 14 | Regular Monospace |

## 5.2 Desktop visual scale — proposed for this design

Desktop reuses semantic hierarchy but allows more compact system-panel labels.

| Token | Size | Weight | Use |
|---|---:|---|---|
| `desktopTitle` | 18 | Medium | Alice / major panel heading |
| `desktopPanelTitle` | 13 | Semibold | SYSTEM STATUS, AGENT RUNNING |
| `desktopBody` | 14 | Regular | normal content |
| `desktopSupporting` | 12 | Regular | secondary labels |
| `desktopCaption` | 11 | Regular | metadata / timestamps |
| `desktopHeroStatus` | 16 | Regular | Listening / Thinking state |

Rules:
- Main communication remains highly readable.
- HUD labels can be compact but never microscopic.
- Uppercase is reserved for short system semantics.
- Japanese descriptive text is not forced to uppercase-like visual treatment.
- Monospace is used only for code / technical identifiers where useful.

---

# 6. Spacing and Radius

Existing accepted tokens remain:

| Token | Value |
|---|---:|
| `space1` | 4 |
| `space2` | 8 |
| `space3` | 12 |
| `space4` | 16 |
| `space6` | 24 |
| `space8` | 32 |
| `radiusSmall` | 4 |
| `radiusMedium` | 12 |
| `radiusLarge` | 18 |
| `radiusPill` | 999 |

## 6.1 Desktop panel spacing

Desktop spatial panels use:
- internal padding: 12–16;
- card-to-card gap: 12–16;
- major spatial region gap: 20–28;
- content-to-border safe distance: minimum 12;
- icon-to-label gap: 8;
- panel title to content gap: 12.

---

# 7. Background

## 7.1 Base

The background is not flat black.

Required visual construction:
1. `background` base;
2. subtle radial blue illumination around Alice Core;
3. extremely faint particle/star/network texture;
4. restrained vignette toward screen edges;
5. no photographic city, mountain, landscape, person or decorative stock image in the default Alice experience.

## 7.2 Background motion

Optional:
- very slow particle drift;
- subtle parallax;
- near-imperceptible energy flow.

Forbidden:
- fast starfield;
- high-contrast moving grid;
- rain / glitch effect;
- bright full-screen animation;
- movement that competes with reading.

Reduce Motion disables decorative movement.

---

# 8. Surface / Glass Panel System

Panels are visually subordinate to Alice Core.

## 8.1 Standard panel

- Fill: dark glass / `surface` family.
- Border: 1 px.
- Border color: `border`, with selective cyan/blue highlight.
- Radius: 12–18.
- Shadow: minimal; dark ambient separation rather than Material-style drop shadow.
- Glow: local and subtle; not around every card.

## 8.2 Active / selected panel

Use:
- slightly brighter border;
- small cyan/blue accent line or icon;
- limited outer glow.

Do **not** brighten the whole screen.

## 8.3 Critical panel

For Approval High/Critical, Emergency Stop or critical safety:
- deep red-black surface;
- red border;
- red title/icon;
- primary destructive/emergency action visually unmistakable;
- surrounding UI remains dark, not globally red.

---

# 9. Icon Style

- line-based;
- geometric;
- thin-to-medium stroke;
- cyan / pale blue / white;
- no multicolor illustrations in normal controls;
- icon tile may use restrained circular or rounded-square container;
- icon style remains consistent across panels.

Forbidden:
- emoji as primary control icon;
- skeuomorphic icons;
- cartoon icons;
- random icon families mixed together.

---

# 10. Alice Core — Canonical Visual Appearance

## 10.1 Current renderer

Current Alice Core renderer remains:

- irregular three-dimensional particle mesh sphere;
- cyan center glow;
- electric-blue particle network;
- violet secondary energy;
- small white highlights;
- thin mesh connections;
- surrounding interface rings;
- sparse scale/tick marks;
- at most two small amber arcs;
- weak outer glow.

The Core must feel:
- alive;
- intelligent;
- controlled;
- elegant;
- not explosive;
- not like a magic spell;
- not like a generic glowing logo.

## 10.2 Core base asset

Existing contract remains:
- `alice_core_base.png`;
- PNG;
- 1024×1024;
- transparent;
- sphere ≈ 78–80% of canvas;
- ≥10% transparent margin;
- no text;
- no rings baked in;
- no background baked in.

## 10.3 Rings

Existing baseline:
- 3 rings;
- approximately 106%, 116%, 126% of Core diameter;
- line widths 1.0 / 0.75 / 0.75;
- 48 ticks on outer ring;
- every fourth tick longer;
- maximum 2 amber arcs;
- arc length 12–18 degrees.

## 10.4 Glow

- outer glow max opacity 0.22;
- thinking pulse max opacity 0.35;
- glow remains local;
- never under important text.

## 10.5 Replaceability contract

The visual renderer is replaceable.

The UI must depend on:

`AliceCorePresentationModel → AliceCoreRenderer`

not on:
- PNG filename;
- particle count;
- RingPainter;
- CustomPainter;
- shader implementation;
- 3D implementation;
- animation framework.

The current Phase 1 implementation can remain simple internally. The **visual detailed design explicitly requires that later replacement of the renderer does not require redesign of screen layout, Conversation, Agent, Permission or execution semantics.**

---

# 11. Alice Core Visual States

## 11.1 Idle
- low-energy cyan/blue glow;
- slow 12s ring rotation;
- no aggressive pulse;
- Core remains clearly alive.

## 11.2 Listening
- cyan energy slightly increases;
- lower waveform may appear outside the Core;
- microphone affordance may appear below the Core in voice-capable phase;
- Core itself remains dominant;
- avoid giant microphone replacing Alice.

## 11.3 Thinking
- concentrated center glow;
- 6s ring rotation;
- 2s breathing pulse;
- subtle violet energy increase.

## 11.4 Streaming / Responding
- stable center;
- 8s ring movement;
- outward energy slightly more visible than Thinking;
- no rapid flicker.

## 11.5 Agent Running
- structured/ordered energy;
- optional directional ring activity;
- Core remains blue/cyan;
- agent progress is primarily expressed in contextual UI, not only Core animation.

## 11.6 Approval Required
- Core may use restrained amber emphasis;
- do not make Core red simply because an approval card is visible;
- approval state is communicated by the card and text.

## 11.7 Unknown / Verifying
- reduced certainty: slower asymmetric ring activity or restrained cyan-violet state;
- visible `VERIFYING`/Japanese explanation outside Core;
- no success green until authoritative outcome.

## 11.8 Emergency Stop
- Core can visually quiet/dim;
- critical red remains in the emergency control surface;
- avoid making the whole Core a flashing red alarm.

---

# 12. Mobile Conversation Screen Appearance

Existing structure is immutable:

`Header → Alice Core Region → Message Viewport → Composer`

## 12.1 Header

- centered `Alice`;
- content height 44 at standard scale;
- background: `backgroundElevated` or visually seamless dark elevation;
- no hamburger;
- no settings;
- no online indicator;
- no model/provider;
- no profile avatar;
- no notification bell.

Visual goal:
**quiet identity strip, not app toolbar.**

## 12.2 Alice Core region

Normal:
`clamp(body height × 0.34, 180, 260)`

Keyboard:
`clamp(body height × 0.22, 96, 120)`

Visual:
- Core centered;
- enough negative space around Core;
- no card/border enclosing the Core in the default mobile view;
- background energy may subtly concentrate behind it.

## 12.3 Message viewport

- horizontal padding 16;
- vertical flow remains spacious;
- no avatars;
- no repeated `Alice` label;
- no timestamp clutter.

## 12.4 User message

- right aligned;
- maximum width 78%;
- `surfaceStrong`;
- radius approximately 16–18;
- horizontal padding 14;
- vertical padding 10;
- text `textPrimary`;
- no profile icon;
- no read receipt.

## 12.5 Alice message

- left aligned;
- maximum width 88%;
- `surface`;
- radius approximately 16–18;
- horizontal padding 14;
- vertical padding 10;
- wider than user message;
- no Alice thumbnail;
- no duplicated Core.

## 12.6 Message rhythm

- gap between messages: 16;
- larger separation around date separators / state blocks: 24;
- avoid chat-app density.

---

# 13. Composer Visual Design

## 13.1 Container

- fixed at bottom;
- dark surface;
- visually integrated rather than a separate large card;
- horizontal padding 16.

## 13.2 Input

- rounded rectangle;
- border `border`;
- focus border `primary`;
- radius 14–18;
- 1–5 lines;
- placeholder `メッセージを入力`;
- text `body`;
- placeholder `textSecondary`.

## 13.3 Send button

- circular;
- minimum 44×44;
- enabled fill `primary`;
- icon: upward arrow;
- icon `textPrimary`;
- no paper-plane messenger styling;
- disabled: dark fill + border + semantic disabled state, not opacity-only.

## 13.4 Phase 1 prohibition

No:
- microphone;
- attachment;
- tool selector;
- model selector;
- plus menu.

Future voice/tool affordances are contextual additions and must not permanently clutter the Phase 1 composer pattern.

---

# 14. Mobile Ambient / Voice Appearance

When voice capability exists:

- Header remains visually light/minimal.
- Alice Core expands and becomes the visual center.
- Conversation content can recede.
- `Alice` name may appear near Core if required by the pattern.
- Listening status is short, e.g. `LISTENING…`.
- Waveform is thin, cyan/blue, restrained.
- Microphone control is circular and visually secondary to the Core.

This is a presentation state of Alice, not a separate permanent tab/mode.

---

# 15. Desktop Spatial Composition

## 15.1 Reference composition

The user-approved cross-phase board's desktop JARVIS-style composition is the visual reference.

The desktop must be recognizably:

- dark;
- spatial;
- Alice-centered;
- Core-dominant;
- information surrounding the Core;
- contextual rather than navigation-driven.

## 15.2 Reference canvas

For mockup and Golden composition review:

**1536 × 1024** is used as the visual reference canvas.

This does not force the final product window to a fixed size.

## 15.3 Primary desktop zones

```text
┌───────────────────────────────────────────────────────────┐
│ Minimal top identity / contextual controls                │
├───────────────┬─────────────────────────┬─────────────────┤
│ Context left  │                         │ Context right   │
│ panels        │      ALICE CORE         │ panels          │
│               │                         │                 │
│               │   conversation/voice    │                 │
├───────────────┴─────────────────────────┴─────────────────┤
│ Contextual conversation / status / action layer           │
└───────────────────────────────────────────────────────────┘
```

The exact number of visible panels depends on context.

## 15.4 Core dominance

At the reference desktop composition:
- central Alice Core visual region should occupy roughly 42–55% of the useful central width;
- the Core itself should remain larger than any single status panel;
- side panels must never visually dominate the Core.

## 15.5 Context rail width

Reference:
- narrow contextual panel: 190–230 px;
- standard contextual panel: 220–280 px;
- detail panel: 280–360 px.

These are visual reference ranges, not API/layout domain constants.

## 15.6 Panel density

Default desktop view:
- 0–3 contextual information groups around Alice;
- not every capability visible simultaneously.

High-detail execution view may temporarily show more panels, but must still preserve Alice spatial identity.

---

# 16. Desktop Top Area

Default:
- small `ALICE` identity;
- optional online/availability status only where the phase/use case genuinely requires it;
- contextual icon controls may appear on the right.

Do not permanently render:
- generic search field;
- notification bell;
- user avatar;
- SaaS account chrome;
unless explicitly required by a later accepted use case.

These elements appeared in an incorrect generated mockup and are **not part of the baseline by default**.

---

# 17. Desktop Context Panels

## 17.1 System Status

Visual:
- compact;
- title in cyan/blue system semantic;
- status dots;
- short labels;
- no giant metrics.

This is optional/contextual, not always visible.

## 17.2 Memory Status

Visual:
- compact counts/summary only when useful;
- not a permanent left navigation destination;
- Personal Memory content itself is not exposed casually.

## 17.3 Agent Status

- compact state label;
- current task summary;
- optional progress;
- a small contextual card.
- `AGENT RUNNING` label uses blue/cyan, not red.

## 17.4 Resource Monitor

- low-priority small panel;
- narrow bars;
- muted labels;
- only in execution/developer context;
- not default consumer home UI.

## 17.5 Quick Actions

- contextual;
- small icon cells or compact actions;
- no permanent feature-launcher dashboard as default.

---

# 18. Conversation on Desktop

Desktop conversation presentation reuses mobile semantics:

- User right;
- Alice left;
- no repeated avatars;
- no repeated role labels;
- dark bubbles;
- generous spacing;
- composer remains visually accessible.

Conversation may appear:
- below Alice Core;
- beside Alice Core;
- or in a contextual layer;

but **it must remain visually connected to Alice, not become a separate generic chat pane.**

---

# 19. Agent Running Card

Default form is **minimal**.

Visual:
- dark panel;
- thin blue/cyan border;
- `AGENT RUNNING` short label;
- one-line current activity;
- thin progress indication if meaningful;
- optional `DETAIL` action.

Do not show by default:
- every step;
- logs;
- observations;
- permissions;
- raw tool calls;
- terminal output.

Those belong to on-demand detail.

---

# 20. Execution Detail — On Demand

When opened:
- larger subordinate panel / sheet / spatial region;
- left-side internal section selection is allowed **inside the detail surface**;
- sections may include overview / steps / actions / observations / permissions / logs;
- internal detail navigation must not become the application's primary navigation.

Visual:
- denser than Conversation;
- still same dark glass family;
- active detail section uses blue accent;
- logs/technical identifiers use compact typography.

---

# 21. Approval Required

## 21.1 Visual hierarchy

Approval card shows:
1. `APPROVAL REQUIRED`;
2. action summary;
3. exact target;
4. meaningful effect/change;
5. risk;
6. action buttons.

## 21.2 Color

- normal/medium approval: dark surface + amber emphasis as needed;
- high/critical/destructive: deep red-black critical surface with red border.

## 21.3 Actions

- Decline/reject: dark secondary button;
- Approve: clear primary button;
- destructive semantics are not encoded by position alone.

Approval is contextual and disappears after resolution.

---

# 22. Permission Required

Same visual family as Approval, but title and scope emphasize:

- capability;
- operation;
- scope;
- device/executor where relevant;
- lifetime.

Do not visually merge Permission and Approval into the same indistinguishable card.

---

# 23. Unknown Outcome / Verifying

Visual:
- no green success;
- no red failure unless failure is known;
- dark blue/violet neutral state;
- label such as `VERIFYING…`;
- subtle spinner/progress only if it represents verification activity;
- Japanese explanation immediately available.

This state should feel:
**uncertain but controlled**, not broken.

---

# 24. Emergency Stop

Visual:
- critical red-black card;
- clear `EMERGENCY STOP`;
- concise Japanese explanation;
- prominent `STOP ALL ACTIONS` or approved localized action;
- lists implications with simple x/stop marks if necessary.

No full-screen flashing red.
No alarm animation.
No implication that prior actions were rolled back.

---

# 25. Visual Responsive Rules

## Mobile
- priority: Composer / Message > Alice Core decoration;
- Core shrinks/hides under pressure;
- one primary vertical scroll.

## Desktop
- priority: Alice Core / Conversation > Context Panels > Detail;
- when width decreases, contextual panels collapse before Core/Conversation;
- detail becomes sheet/overlay before the primary Alice area becomes cramped;
- never solve narrow width by adding a permanent hamburger-driven capability dashboard unless separately approved.

---

# 26. Animation System

Existing Phase 1 values remain:

| Motion | Value |
|---|---:|
| Idle Ring | 12s/rev |
| Thinking Ring | 6s/rev |
| Streaming Ring | 8s/rev |
| Thinking Pulse | 2s |
| Pulse Scale | 0.98–1.02 |
| State Transition | 300ms |

Cross-phase additions follow the same character:
- calm;
- continuous;
- low amplitude;
- no abrupt reset;
- no blinking as primary signal.

---

# 27. Visual Accessibility

- normal text contrast target ≥4.5:1;
- large/non-text ≥3:1;
- Core is decorative unless explicitly made interactive by a later decision;
- motion never sole signal;
- color never sole state indicator;
- Increase Contrast remains readable;
- Reduce Transparency remains readable;
- Reduce Motion provides static equivalents;
- no glow behind reading-critical text.

---

# 28. Visual Golden / Reference Set

Before implementation is considered visually complete, the following reference views must exist and be reviewed.

## Mobile
1. Empty / Idle
2. History
3. Thinking
4. Streaming
5. Keyboard Open
6. Core Hidden
7. Validation
8. Send Failure
9. Unknown Result
10. Reduce Motion
11. Large Text

## Cross-phase / future visual references
12. Ambient / Listening
13. Agent Running Minimal
14. Approval Required
15. Permission Required
16. Unknown / Verifying
17. Emergency Stop
18. Desktop Alice-centered Idle
19. Desktop Conversation
20. Desktop Agent Running
21. Desktop Execution Detail

All future mockups are compared against these, not judged from memory.

---

# 29. Explicit Visual Prohibitions

AI implementation or mockup generation MUST NOT:

- add `Home / Chat / Memory / Tools / Agents / Executions / Files / Settings` as default permanent primary navigation;
- add generic SaaS search + notification + avatar chrome without a separate accepted requirement;
- replace Alice Core with a generic `A` avatar;
- place avatar thumbnails beside every Alice message;
- create a bright/light default theme;
- use stock landscape photography;
- add excessive neon/glitch;
- turn every capability into a visible dashboard card;
- display technical internals in normal Conversation;
- bake Ring/Text/Status into the replaceable Core base asset;
- couple business state to particle/shader implementation;
- update Golden images merely to make tests pass.

---

# 30. AI Implementation Visual Contract

For every UI implementation task, the AI prompt must include:

- UI-BL-001;
- this Visual Detailed Design;
- relevant UI-012/UI-013/UI-014 section;
- exact component FIP;
- reference image where applicable.

The AI must report:
- visual files changed;
- token differences;
- Golden differences;
- any unimplemented visual specification;
- any visual assumption it would otherwise need to make.

If an appearance is not specified, the AI must **stop and report the gap rather than invent a new visual direction.**

---

# 31. Visual Decisions Resolution

All Version 1 open visual decisions are resolved by the user-approved UI-VIS-SOT-001 / CVD / Golden View baseline.

Resolved rules:
- Desktop system-panel typography follows the accepted compact HUD scale.
- Desktop reference composition is responsive; reference canvas values are Golden/reference values, not product-window constants.
- Contextual rail widths remain visual reference ranges only.
- Cross-phase success/warning/danger visual aliases are accepted tokens and must not be scattered as raw component values.
- Default top area remains minimal; availability status is contextual only.
- Listening treatment is restrained waveform/energy presentation; no default permanent microphone control is introduced.
- Alice Core remains larger/more dominant than any single contextual panel.
- Current Alice Core implementation is replaceable; renderer redesign is not required as speculative Phase 1 work.
- Canonical Desktop Conversation is spatially associated to the **right side of Alice Core** in the accepted composition; below-Core generic chat-pane interpretation is superseded.
- Single / Dual / Triple display behavior follows accepted display-role CVD and Golden states.
- Display disconnect migrates presentation responsibility without changing semantic state or execution authority.

# 32. Final Judgment

**Visual Direction:** PASS — aligned with UI-BL-001 / UI-VIS-SOT-001  
**Architecture Impact:** none  
**Renderer Replaceability:** preserved  
**Open Visual Decisions:** 0  
**Visual Status:** ACCEPTED / FROZEN  
**Implementation Authorization:** not granted by this document; Repository Sync Gate remains authoritative.
