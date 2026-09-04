# Project Alice — Alice Experience / Visual Baseline

**Decision ID:** UI-BL-001  
**Date:** 2026-09-04 JST  
**Status:** Accepted / Frozen Visual Baseline  
**Applies to:** Phase 1–4 Frontend / Visual Design / FIP / UI Review / Image Mockups  
**Reference Image:** User-approved `Project Alice — Cross-Phase Frontend / Visual Principle` board supplied on 2026-09-04  
**Related:** UI-XP-001, UI-012, UI-013, UI-016, ADR-017, FIP-006, FIP-007

---

## 1. Purpose

This document freezes the user-approved visual direction for Project Alice and prevents future design drift.

All future UI documents, FIPs, mockups, image-generation prompts, implementation reviews, and frontend changes MUST preserve this baseline unless the user explicitly approves a new visual-baseline decision.

This document does not redefine backend authority, Permission, Approval, Agent, Tool, Memory, or Execution semantics. It defines how those accepted capabilities appear within the Alice experience.

---

## 2. Source-of-Truth Priority

For visual / frontend presentation decisions, use this order:

1. **UI-BL-001 — this Frozen Visual Baseline**
2. **UI-XP-001 — Cross-Phase Alice Experience Principle**
3. **UI-012 — Visual Direction**
4. Phase-specific UI decisions
5. Screen / Component design
6. FIP
7. Implementation
8. Generated mockups / images

A lower layer MUST NOT reinterpret or visually replace a higher layer.

If a generated image conflicts with UI-BL-001, the image is wrong. The baseline is not changed to match the image.

---

## 3. Core Experience

### 3.1 Conversation is Alice

Conversation remains the primary Alice experience.

Memory, Tool, Agent, Permission, Approval, Voice, Execution, Filesystem, Browser, Application and OS capabilities are capabilities **inside Alice**, not independent products that replace the primary experience.

### 3.2 Alice Core is Presence

Alice Core represents Alice's presence.

It is not:
- a logo;
- a generic app icon;
- a button;
- a loading spinner;
- a backend entity;
- an execution-authority indicator.

The accepted visual direction is a luminous, organic, futuristic core using:
- dark-space background;
- cyan / electric blue;
- violet accents;
- particle mesh;
- internal light;
- soft energy flow;
- radial / orbital structures;
- subtle state-dependent motion.

### 3.3 Alice Core MUST be replaceable

The current Alice Core visual is **not permanently coupled to application logic**.

Required architecture:

`AliceCoreState → AliceCorePresentationModel / Presentation Contract → AliceCoreRenderer → Visual Expression`

Rules:
- `AliceCoreState` expresses presentation-relevant semantic state only.
- Application / Domain logic MUST NOT depend on the current particle/orb renderer.
- Screen layout MUST reserve an Alice Core region without depending on the renderer's internal implementation.
- The current renderer may later be replaced by another renderer, asset, shader, 2D/3D implementation, animation system, or visual character without changing Conversation, Agent, Permission, Tool, Memory, or Execution business logic.
- Renderer-specific geometry, particle count, shader data, animation controller, asset path, canvas implementation, or visual technology MUST NOT leak into Domain/Application contracts.
- Renderer replacement MUST preserve accessibility, semantic state, reduced-motion behavior, layout contract and state mapping.
- Replacing the Alice Core visual is a Presentation change, not an Alice Core semantic redesign, unless semantic states themselves change.

---

## 4. Visual Language

### 4.1 Theme

Project Alice uses a **dark-first / dark-only baseline** for the accepted experience.

Visual character:
- futuristic;
- intelligent;
- calm;
- personal;
- JARVIS-inspired spatial presentation;
- high information clarity without looking like a generic admin dashboard.

### 4.2 Color Direction

Baseline family:
- Background: near-black / deep navy
- Surface: dark navy
- Elevated Surface: slightly brighter navy
- Primary: electric blue
- Cyan: luminous cyan
- Violet: controlled violet accent
- Success: green
- Warning: amber
- Danger / Critical: red

Exact implementation tokens remain governed by UI-012 / FIP-006 where values are already fixed.

### 4.3 Surface / Component Style

- dark translucent / glass-like panels are permitted;
- thin blue/cyan borders and restrained glow;
- rounded corners;
- subtle depth;
- information panels must remain legible;
- glow must support hierarchy, not reduce text readability;
- red is reserved for destructive / critical / emergency semantics.

---

## 5. Alice Core State Expression

The current renderer visually changes without changing the underlying Alice identity.

Representative expressions:
- Idle — quiet / low-energy presence
- Listening — voice/listening energy
- Thinking — concentrated internal motion
- Processing / Agent Running — active structured energy
- Responding — outward response energy
- Approval Required — attention state, without implying approval is granted
- Unknown / Verifying — uncertain / verification state
- Emotional Resonance — optional subtle expression, never a security or authority signal

Exact animation is renderer-owned and replaceable.

---

## 6. Primary UI Experience Patterns

These are **states/presentations of one Alice experience**, not permanent product modes.

### A. Ambient / Voice
- Alice Core is visually dominant.
- Minimal chrome.
- Header remains simple.
- Listening state and voice input appear around/below the Core.
- Conversation UI does not need to dominate when no conversation content requires it.

### B. Conversation
- Header: `Alice`.
- Alice Core remains present but yields space to conversation.
- Conversation messages are the primary information surface.
- Composer remains at bottom.
- Information density remains lower than management/detail views.

### C. Agent Running — Minimal
- Conversation remains visible.
- Agent progress appears as a compact contextual card/status.
- Do not transform the entire primary screen into an execution dashboard.
- Detail is opened on demand.

### D. Approval Required
- The requested action, target, effect/change, risk and other required approval context appear prominently.
- Approval UI is contextual.
- Approval does not become a permanent navigation destination on the primary screen.
- High-risk/destructive semantics use the accepted red/critical treatment.

### E. Permission Required
- Same progressive-disclosure principle as Approval.
- Permission scope is understandable before action.
- Permission UI must not imply that Memory, voice, login state, AI proposal, or possession of credentials grants authority.

### F. Unknown Outcome / Verifying
- Alice explicitly communicates that the result is not known.
- Verification state is visible.
- UI must not present success/failure until authoritative observation exists.

### G. Emergency Stop
- Critical red treatment.
- Clearly communicates that future dispatch/actions are stopped.
- Must not visually imply rollback of already-dispatched effects.

---

## 7. Mobile / Phase 1 Screen Shell

The accepted Phase 1 shell remains authoritative for the mobile conversation screen.

Structure:

1. Top Safe Area
2. Fixed Header
3. Responsive, non-scroll Alice Core Region
4. Single primary Message Viewport
5. Composer
6. Bottom Safe Area

Rules:
- Header center displays exactly `Alice`.
- No unnecessary status/action clutter in the Phase 1 header.
- Alice Core region is non-scroll.
- Message Viewport is the only primary vertical scroll region.
- Composer is fixed above Bottom Safe Area.
- Keyboard follows the composer.
- When keyboard space is constrained, Message/Composer take priority over Core.
- Core may shrink and, when necessary, temporarily hide.
- Core renderer replacement must not alter this shell contract.
- Composer supports 1–5 lines.
- Send target is at least 44×44.
- Phase 1 does not add Navigation / Conversation List / Settings / Voice / Tool / Model selection merely to resemble later phases.

---

## 8. Desktop Spatial Layout — Accepted Direction

Desktop may use the approved **JARVIS-style spatial layout**.

The Alice Core is the spatial center / visual anchor.

Contextual panels may appear around it, such as:
- System Status
- Memory Status
- Agent Status
- Resource Monitor
- Quick Actions
- Execution Detail
- Approval
- Permission
- Verification / Unknown Outcome
- Emergency Stop

However:

**This is not permission to convert Alice into a generic permanent admin dashboard.**

Rules:
- Alice Core + Conversation remain the experiential center.
- Panels appear according to context, role, device and user intent.
- Detailed management is subordinate/on-demand.
- Capability Growth ≠ automatic Primary Navigation Growth.
- A desktop layout may show more simultaneous spatial context than mobile, but the information hierarchy must still read as Alice first, capabilities second.
- The reference board's desktop composition is the accepted visual direction; future desktop mockups must remain recognizably within this design family.

---

## 9. Progressive Disclosure

Default:
`Conversation / Alice Core`

When necessary:
`Contextual status / Tool result / Agent progress / Approval / Permission / Verification`

On demand:
`Execution Detail / Memory Management / Permission Management / Trusted Devices / Safety / Settings / Audit / History`

Do not expose all management surfaces merely because the capability exists.

---

## 10. Navigation Rule

A permanent capability navigation list such as:

`Chat / Memory / Tools / Agents / Executions / Files`

MUST NOT be introduced as the default primary experience solely because those capabilities exist.

If desktop subordinate navigation is later required, it must:
- preserve Alice + Conversation as primary;
- be justified by an explicit use case;
- follow progressive disclosure;
- pass Visual Drift Review against this baseline.

---

## 11. Language

- Short system semantics may use English-first labels (`ONLINE`, `AGENT RUNNING`, `APPROVAL REQUIRED`, `VERIFYING`, etc.).
- Explanations, decisions, warnings and user-facing communication where meaning matters are Japanese-first for the current product baseline.
- Labels must remain concise and readable in the dark HUD presentation.

---

## 12. Visual Drift Gate

Every future frontend/UI document, FIP, mockup and implementation review MUST check:

- [ ] Conversation + Alice Core remain primary.
- [ ] Alice Core still represents Presence.
- [ ] Alice Core renderer is replaceable and Presentation-only.
- [ ] No business logic depends on renderer implementation.
- [ ] Dark futuristic visual language is preserved.
- [ ] Cyan / electric-blue / violet luminous language is preserved.
- [ ] Capability Growth has not become unjustified dashboard/navigation growth.
- [ ] Contextual capabilities use progressive disclosure.
- [ ] Agent Running remains compact by default.
- [ ] Approval / Permission / Unknown / Emergency states remain visually distinct.
- [ ] Mobile Screen Shell remains Header → Core → Message → Composer.
- [ ] Desktop spatial layout remains Alice-centered rather than admin-dashboard-centered.
- [ ] Accessibility / reduced-motion / readability remain valid.
- [ ] Generated mockups are checked against this document before being accepted.

Any failed item is **Visual Drift** and blocks UI approval until corrected or explicitly superseded by a user-approved baseline decision.

---

## 13. AI / Image Generation Rule

When an AI assistant generates or edits a Project Alice UI:

1. It MUST read/reference UI-BL-001 first.
2. It MUST NOT invent a new information architecture.
3. It MUST NOT turn Project Alice into a generic SaaS/admin dashboard.
4. It MUST preserve the accepted dark JARVIS-style design family.
5. It MUST preserve Alice Core as the central presence.
6. It MUST treat the current Alice Core renderer as replaceable.
7. It MUST not add permanent capability navigation merely for visual richness.
8. If the requested image conflicts with this baseline, the conflict must be surfaced before the new image is accepted as design.

---

## 14. Change Control

UI-BL-001 is a **Frozen Visual Baseline**.

It may be changed only when:
- the user explicitly requests a visual-direction change; and
- the change is recorded as a new approved visual-baseline revision/decision; and
- affected UI/FIP/Test documents are reviewed for drift.

Ordinary implementation work, AI coding, Cross-Phase expansion, or new capability addition MUST NOT silently modify this baseline.

---

## 15. Final Decision

The user-approved `Project Alice — Cross-Phase Frontend / Visual Principle` board is adopted as the canonical visual reference for the current Project Alice experience.

The baseline combines:
- Conversation-first Alice experience;
- central luminous Alice Core;
- dark futuristic/JARVIS visual language;
- contextual spatial presentation;
- progressive disclosure;
- minimal Agent Running presentation;
- explicit Approval / Permission / Unknown / Emergency states;
- Phase 1 mobile Screen Shell;
- replaceable Alice Core renderer.

**Status: ACCEPTED / FROZEN.**
