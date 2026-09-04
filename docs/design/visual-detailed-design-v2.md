# Project Alice — Visual Detailed Design v2

**Date:** 2026-09-04 JST  
**Status:** Proposed Baseline — User Approval Required  
**Parent:** UI-BL-001 — Alice Experience / Visual Baseline  
**Scope:** Visual appearance only

## 1. Accepted direction carried forward

Project Alice uses an Alice-centered, dark, spatial, JARVIS-inspired visual language. Alice Core is the primary visual presence; Conversation is the primary experience. Capability panels are contextual and subordinate, not the primary product structure.

The default visual language is deep navy/near-black, cyan/electric-blue light, controlled violet energy, thin HUD geometry, restrained glass surfaces and generous negative space.

## 2. Desktop composition — revised proposal

The Desktop composition is centered on an intentionally **very large Alice Core**.

### 2.1 Hierarchy
1. Alice Core
2. Conversation / current interaction
3. Contextual capability/status panels
4. On-demand execution/detail surfaces

### 2.2 Alice Core scale
At the reference 1536×1024 composition:
- Alice Core is centered in the main spatial region.
- Core sphere target diameter: approximately **500–570 px**.
- Rings/glow may visually extend to approximately **650–760 px**.
- The Core must be visibly larger than any single panel and must dominate first impression.
- Side panels may not intrude into the Core's principal visual field.
- If layout pressure occurs, contextual panels collapse/reduce before the Core is substantially reduced.

This supersedes the smaller-Core visual proposal in Visual Detailed Design v1.

### 2.3 Core treatment
Current visual expression:
- irregular particle-mesh sphere;
- cyan luminous center;
- electric-blue network;
- restrained violet filaments;
- small white highlights;
- three interface rings;
- sparse ticks;
- restrained glow.

Alice Core remains renderer-replaceable:
`AliceCoreState → Presentation Contract → AliceCoreRenderer → Visual Expression`.

No business/domain/application logic may depend on particle count, geometry, asset, shader, painter, animation framework or current renderer.

## 3. Voice / Listening visual treatment

The previously generated microphone control is **not part of the accepted default visual design**.

When listening is active:
- Alice Core itself expresses the listening state;
- a thin waveform/status treatment may appear contextually;
- text such as `Listening…` may be used where useful;
- a large standalone microphone button/icon is not shown by default;
- microphone affordance may only be introduced later when an explicit interaction requirement requires it.

Voice is a state/presentation of Alice, not a separate permanent mode or primary control.

## 4. Text restraint

Decorative slogans/taglines are prohibited from the normal product UI.

Do not display:
- `Your Life, More You.`
- `Your Life...`
- `いつも、あなたと。`
- `もっと、あなたらしく。`
- `A smarter life, a calmer you.`
- other generated marketing copy or decorative phrases.

Allowed persistent identity text is limited to product identity required by the specific surface, e.g. `Alice` or `Project Alice`.

System-state text and user-facing explanations are allowed when functionally meaningful.

## 5. Default desktop chrome

Do not add generic SaaS chrome by default:
- no generic search bar;
- no notification bell;
- no profile/avatar control;
- no permanent capability navigation;
- no decorative marketing header.

Contextual controls must have an accepted use case.

## 6. Context panels

Panels use dark translucent surfaces, thin restrained borders, 12–18 px radius and compact typography.

Possible contextual panels include:
- System Status;
- Memory Status;
- Agent Status;
- Activity;
- Quick Actions;
- Approval Required;
- Permission Required;
- Unknown / Verifying;
- Emergency Stop;
- Execution Detail.

Their existence in the design system does **not** mean all are simultaneously visible. Default composition should normally show only contextually relevant panels.

## 7. Conversation

Conversation remains visually connected to Alice.

- User: right aligned, `surfaceStrong`, max 78%.
- Alice: left aligned, `surface`, max 88%.
- No repeated avatar or Alice Core thumbnail.
- No repeated role labels/timestamps in normal presentation.
- Composer remains subordinate to Alice Core but clearly accessible.

## 8. Composer

Dark rounded input with blue focus treatment and circular send action.

Phase 1:
- placeholder `メッセージを入力`;
- no microphone;
- no attachment;
- no tool selector;
- no model selector.

## 9. State surfaces

### Agent Running
Compact blue/cyan contextual card; details on demand.

### Approval Required
Contextual dark/amber or critical red surface depending on risk. Action, target, effect and risk remain visually clear.

### Permission Required
Visually distinct from Approval; scope/lifetime/capability emphasized.

### Unknown / Verifying
Neutral blue/violet uncertainty treatment. Never show success/failure before authoritative verification.

### Emergency Stop
Localized critical red-black surface. No full-screen flashing red; no rollback implication.

## 10. Explicit visual prohibitions

AI mockups and implementation must not:
- shrink Alice Core back into a small dashboard widget;
- add a default `Home / Chat / Memory / Tools / Agents / Executions / Files / Settings` capability navigation;
- add generic SaaS search/bell/avatar chrome without an approved use case;
- add decorative slogans/taglines;
- add a default microphone button/icon;
- replace Alice Core with an `A` avatar;
- duplicate Core thumbnails beside Alice messages;
- expose all capability panels simultaneously merely because they exist;
- turn Alice into an admin dashboard;
- couple Alice Core renderer implementation to business logic.

## 11. Preview acceptance criteria

A preview passes only when:
- the very large centered Alice Core is the first visual focus;
- the Core remains recognizably within the approved cyan/blue/violet particle-mesh family;
- no microphone icon is visible by default;
- no decorative slogan/tagline is visible;
- contextual panels are subordinate;
- no permanent capability navigation is introduced;
- the composition reads `Alice first, capabilities second`;
- dark futuristic visual language is preserved.

## 12. Decisions requiring user approval

### VD-APP-001 — Desktop Core scale
Approve 500–570 px sphere / 650–760 px visual envelope at 1536×1024 reference composition.

### VD-APP-002 — Listening treatment
Approve `Core state + optional thin waveform/status text`, with no default microphone icon.

### VD-APP-003 — Text restraint
Approve prohibition of generated decorative slogans/taglines in normal product UI.

### VD-APP-004 — Desktop panel philosophy
Approve contextual side panels while retaining the rule that they are not all permanently visible.

### VD-APP-005 — Reference composition
Approve 1536×1024 as the desktop visual review / Golden composition reference only, not a fixed application window size.

Once VD-APP-001–005 are approved, this document can be promoted to **Accepted / Frozen Visual Detailed Design** and used as the basis for component-level visual specifications and Golden mockups.
