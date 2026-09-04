# Project Alice — Component Visual Design 001
## Alice Core / Background / Spatial Frame

**Date:** 2026-09-04 JST  
**Status:** Proposed — User Approval Required  
**Parents:** UI-BL-001 / Visual Detailed Design v2 (Accepted)  
**Scope:** Visual appearance only

# CVD-001 Alice Core

## Role
Alice Core is the dominant visual presence of Alice, not a logo, avatar, button, microphone, loading spinner, or authority indicator.

## Desktop reference geometry
Reference canvas: 1536×1024.

- Core sphere diameter: 500–570 px.
- Preferred reference target: 540 px.
- Ring/glow visual envelope: 650–760 px.
- Preferred visual envelope: ~710 px.
- Core center is placed on the principal visual axis of the Alice experience, not inside a card.
- Context panels must collapse/reduce before the Core is materially reduced.

## Appearance
- cyan luminous center;
- electric-blue particle/network body;
- controlled violet energy filaments;
- sparse white energy points;
- three thin orbital/interface rings;
- sparse ticks and orbital nodes;
- local glow only;
- transparent/space background around the Core.

No text, microphone icon, logo mark, status label, slogan, panel border or background is baked into the Core renderer/base asset.

## Renderer boundary
`AliceCoreState → Presentation Contract → AliceCoreRenderer → Visual Expression`

The renderer is replaceable. Particle count, geometry, shader, image asset, CustomPainter, 2D/3D implementation and animation framework are presentation details and may not leak into Domain/Application contracts.

# CVD-002 Background

- Base: near-black/deep navy.
- Very subtle cosmic/particle field.
- Faint radial blue illumination around Alice Core.
- Restrained edge vignette.
- No landscape photography.
- No decorative planet/earth image.
- No marketing illustration.
- No decorative slogan/tagline.
- Background exists to amplify Alice Core, not compete with it.

# CVD-003 Spatial Frame

Default desktop composition:
- Alice Core occupies the center.
- Conversation/composer remains visually attached to Alice.
- Context panels use outer left/right regions.
- Panels do not form permanent capability navigation.
- Default state shows only contextually useful panels.
- No generic search bar, notification bell, user avatar or SaaS header by default.

Reference side-panel widths:
- compact: 190–230 px;
- standard: 220–280 px;
- detail: 280–360 px.

Panels use:
- deep navy translucent surface;
- 1 px restrained border;
- 12–18 px radius;
- compact labels;
- restrained cyan/blue emphasis;
- red localized only to critical surfaces.

# CVD-004 Listening appearance

Default listening state:
- Core itself expresses increased cyan energy;
- optional thin waveform below Core;
- optional functional `Listening…` status;
- no microphone icon/button by default;
- no slogan or decorative sentence.

# CVD-005 Explicit prohibitions

The implementation/mockup must not:
- make Alice Core a small dashboard widget;
- place Core inside a rectangular card;
- add microphone icon by default;
- add generated slogans;
- add decorative scenic imagery;
- add permanent capability navigation;
- add generic SaaS chrome;
- allow side panels to visually dominate Core.

# Approval items

**CVD-APP-001** — Preferred desktop Core target = 540 px sphere / ~710 px visual envelope.  
**CVD-APP-002** — Background is abstract dark-space only; scenic/planet imagery prohibited.  
**CVD-APP-003** — Alice Core is borderless/spatial and never enclosed in a default card.  
**CVD-APP-004** — Listening uses Core state + optional thin waveform/status; no default microphone icon.  
**CVD-APP-005** — Context panels remain outer/subordinate and collapse before the Core is materially reduced.
