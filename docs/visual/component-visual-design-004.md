# Project Alice — Component Visual Design 004
## Desktop Spatial Composition / Multi-Display / Responsive / Golden Views

**Date:** 2026-09-05 JST
**Status:** Proposed — User Approval Required
**Parents:** UI-BL-001 / Visual Detailed Design v2 / CVD-001 / CVD-002 v2 / CVD-003
**Scope:** Visual appearance and multi-display presentation only

# 1. Core Principle

Multi-display expands Alice's presentation space; it does not create multiple Alices.

There is exactly one **Primary Alice Presence** at a time.

- Alice Core is not duplicated across displays by default.
- Conversation is visually connected to Alice but may live on a separate display.
- Capability/detail surfaces use additional displays only when useful.
- Display count must not turn Alice into a permanent dashboard.

# 2. Canonical Triple-Display Layout

For a three-display setup with a landscape center display and a portrait display on the right:

`Left Work Space | Center Alice Presence | Right Portrait Conversation`

## 2.1 Center display — Alice Presence Display

Purpose: make the entire center display feel like Alice's presence.

Default:
- Alice Core centered.
- Core is substantially larger than single-display mode.
- No Conversation.
- No permanent System/Memory/Agent panels.
- No generic navigation.
- No microphone icon.
- No slogans/taglines.
- Abstract dark-space background only.

Reference scale:
- Core sphere target: 58–68% of shorter display dimension.
- Ring/glow envelope: 76–90% of shorter display dimension.
- Keep a safe visual margin of at least 5% from physical display edges.
- Core remains optically centered even if rings are asymmetric.

The center display expresses Idle / Listening / Thinking / Responding / Agent Running / Approval Attention / Verifying primarily through Alice Core state and restrained ambient cues.

Critical text is not baked into the Core.

## 2.2 Right portrait display — Conversation Display

Purpose: dedicated talk surface.

Canonical content:
- minimal `Alice` identity/header;
- vertically scrolling Conversation;
- User messages right;
- Alice messages left;
- Composer fixed at bottom;
- contextual Conversation-related state such as Approval / Permission / Unknown may appear inline or as a contextual sheet.

No Alice Core duplicate.
No avatar thumbnails.
No default microphone.
No decorative slogan.
No permanent capability navigation.

Reference portrait behavior:
- content horizontal padding: 16–24 px depending on width;
- User message max width: 78%;
- Alice message max width: 88%;
- Composer: full usable width minus safe horizontal margins;
- only message viewport is the primary vertical scroll.

## 2.3 Left display — Optional Work/Detail Display

Default:
- not required for Alice to function;
- may remain available to the user's normal desktop/work applications.

When Alice needs extended space, it may host:
- Agent Detail;
- Execution Detail;
- Browser/Filesystem/Application result surfaces;
- development/task information;
- other explicitly opened subordinate detail.

Rules:
- no second Alice Core;
- no permanent Alice dashboard;
- detail remains subordinate to the center Alice Presence;
- user may close/return the display to ordinary desktop use.

# 3. Dual-Display Layout

## Preferred case: center/landscape + right portrait
- Landscape display: Alice Core Presence.
- Portrait display: Conversation.
- Detail surfaces are on-demand overlays/sheets or replace subordinate space temporarily.

## Two landscape displays
Preferred:
- Primary display: large Alice Core + minimal spatial context.
- Secondary display: Conversation.
- Conversation uses a centered/narrow reading column rather than stretching bubbles across the full screen.

# 4. Single-Display Fallback

The already accepted CVD-002 v2 remains canonical:

`Large Alice Core | Conversation on right`

Rules:
- Core remains dominant.
- Conversation width preferred around 400 px.
- Secondary panels collapse first.
- No permanent dashboard navigation.

# 5. Display Role Model

Visual roles:
- **Presence Display** — one display only; owns the primary Alice Core.
- **Conversation Display** — zero or one dedicated display; owns the main talk surface when separated.
- **Detail Display** — zero or more available displays; subordinate/on-demand.

These are presentation roles, not security/permission roles.

# 6. Display Connect / Disconnect Behavior

## Additional display connected
Do not automatically scatter Alice across every display.

Preferred behavior:
- retain current layout initially;
- offer/remember an approved display-role layout;
- when the canonical multi-display layout is active, move Conversation to its assigned display and expand the Presence Display Core smoothly.

## Conversation display disconnected
- Conversation returns to the Presence Display using CVD-002 v2 single-display composition.
- Core reduces from dedicated-display scale to single-display scale.
- no conversation state is lost.

## Presence display disconnected
- elect the configured/available primary display as the new Presence Display;
- Alice Core moves as one presence rather than being duplicated.

# 7. Mixed Resolution / DPI

- layout uses logical dimensions, not physical pixels alone;
- Core scale derives primarily from shorter usable display dimension;
- safe margins and OS reserved regions are respected;
- text remains within accepted accessibility ranges;
- different DPI values must not cause visual scale discontinuity across roles.

# 8. Portrait Display Rules

Portrait Conversation is intentionally optimized for reading:
- Conversation uses vertical space aggressively;
- Core is not shown merely to fill unused space;
- Composer stays bottom-fixed;
- long messages remain readable;
- contextual Approval/Permission/Unknown surfaces use width-efficient sheets/cards;
- execution detail does not permanently replace Conversation.

# 9. Multi-Display State Golden Set

Required reference views:

MD-GOLDEN-01 — Single Display / Idle  
MD-GOLDEN-02 — Single Display / Conversation  
MD-GOLDEN-03 — Dual Display / Core + Portrait Conversation  
MD-GOLDEN-04 — Triple Display / Left Work + Center Core + Right Portrait Conversation  
MD-GOLDEN-05 — Triple Display / Agent Running  
MD-GOLDEN-06 — Triple Display / Approval Required  
MD-GOLDEN-07 — Triple Display / Unknown / Verifying  
MD-GOLDEN-08 — Conversation Display Disconnect → Single Display fallback  
MD-GOLDEN-09 — Presence Display Disconnect → Presence migration  
MD-GOLDEN-10 — Reduce Motion multi-display

# 10. Explicit Prohibitions

- duplicate Alice Core on every monitor;
- put Conversation over the center Core in canonical triple-display mode;
- use the center Presence Display as a permanent dashboard;
- fill the right portrait Conversation display with capability navigation;
- force the left display to Alice when the user is using it for normal work;
- add decorative slogans;
- add default microphone controls;
- move content between displays in a way that loses Conversation state;
- shrink the center Core merely to expose secondary status panels.

# 11. Approval Items

**CVD4-APP-001 — Triple canonical layout**  
Approve `Left optional Work/Detail | Center Alice Presence | Right Portrait Conversation`.

**CVD4-APP-002 — Dedicated Presence scale**  
Approve Core sphere = 58–68% of center display shorter dimension; ring/glow envelope = 76–90%.

**CVD4-APP-003 — One Presence rule**  
Approve exactly one primary Alice Core across displays by default; no duplicate Core on Conversation/Detail displays.

**CVD4-APP-004 — Portrait Conversation role**  
Approve the right portrait display as the canonical dedicated Conversation surface, with Composer fixed at bottom and no Core duplicate.

**CVD4-APP-005 — Left display freedom**  
Approve left display as optional user workspace / on-demand Alice detail rather than a mandatory Alice screen.

**CVD4-APP-006 — Fallback**  
Approve automatic visual fallback to CVD-002 v2 when the Conversation display is disconnected, without losing Conversation state.

**CVD4-APP-007 — Golden set**  
Approve MD-GOLDEN-01–10 as required multi-display visual regression/reference views.

After approval, CVD-004 becomes Accepted and the next step is to freeze state-by-state Golden compositions and consolidate the Visual Design package.
