# Project Alice — Component Visual Design 002 v2
## Conversation / Message / Composer — Right Spatial Conversation

**Date:** 2026-09-05 JST
**Status:** Proposed — User Approval Required
**Parents:** UI-BL-001 / Visual Detailed Design v2 (Accepted) / CVD-001 (Accepted)
**Scope:** Visual appearance only
**Supersedes:** CVD-002 proposal placing Conversation beneath Alice Core

# 1. Revised Desktop Principle

Desktop spatial hierarchy:

`Large Alice Core (center) → Conversation (right) → Contextual capability/detail surfaces (outer/peripheral)`

Conversation is moved from beneath Alice Core to the **right side of Alice Core**.

This is not a generic permanent chat sidebar. It is Alice's active conversation surface and must remain visually connected to the central Core.

# 2. Reference Geometry — 1536×1024

## Alice Core
Accepted CVD-001 values remain:
- sphere preferred target: 540 px;
- visual envelope: ~710 px;
- central spatial anchor;
- never enclosed in a default card.

## Conversation region
Proposed reference:
- location: center-right / right of Core;
- preferred width: **400 px**;
- acceptable responsive range: **360–440 px**;
- preferred visible message height: **460–560 px**;
- preferred target: **510 px**;
- Core-envelope to Conversation gap: **24–32 px**;
- Composer aligned to Conversation width;
- Composer minimum height: 56 px;
- Send: 48×48 px.

Conversation begins approximately around the upper-middle of the Core rather than at the absolute top of the application, preserving the Core's spatial dominance.

# 3. Right-side Conversation Appearance

- transparent/spatial integration preferred;
- optional restrained dark glass surface for readability;
- no large `CONVERSATION` title;
- no independent app-style toolbar;
- no profile/avatar header;
- no permanent close/minimize controls;
- no miniature Alice Core.

User:
- right aligned;
- `surfaceStrong`;
- max width 78% of Conversation region.

Alice:
- left aligned;
- `surface`;
- max width 88% of Conversation region.

Default:
- no avatar;
- no role label;
- no timestamp;
- no read receipt.

# 4. Composer

Placed at bottom of right Conversation region.

Default Phase 1:
- text field;
- Send only;
- placeholder `メッセージを入力`;
- 1–5 lines;
- no microphone;
- no attachment;
- no Tool/Model selector.

# 5. Context Panel Collision Rule

Because Conversation now owns the principal right-side region, ordinary Context Panels must not compete with it.

Priority:
1. Alice Core
2. Conversation
3. Current critical/contextual state
4. Secondary status panels

Rules:
- System/Memory status may use left/peripheral region when contextually useful.
- Agent/Approval/Permission/Unknown/Emergency may temporarily occupy an outer-right or overlay/detail region only when needed.
- A critical Approval/Emergency surface may temporarily reduce available Conversation width, but must not permanently replace it.
- Secondary panels collapse/hide before Alice Core or Conversation becomes unusable.
- Do not stack Activity + Agent + Approval + Unknown + Emergency permanently beside Conversation.

# 6. Spatial Connection

Conversation must visually read as communication emerging from Alice.

Use one or more restrained techniques:
- shared background illumination;
- subtle cyan spatial line/accent;
- proximity to Core envelope;
- aligned vertical rhythm.

Do not use:
- comic speech-tail from the Core;
- bright connecting beam;
- physical cable;
- heavy card frame connecting Core and Conversation.

# 7. Responsive Desktop

Wide:
- Core center-left/center;
- Conversation right;
- contextual panels peripheral.

Medium:
- secondary panels collapse first;
- Core remains large;
- Conversation remains right while practical.

Narrow:
- switch to the already accepted mobile/tablet vertical shell rather than shrinking Core into a dashboard icon.

# 8. Explicit Prohibitions

- Conversation below Core as the default Desktop composition.
- Generic full-height messenger sidebar.
- Permanent capability navigation.
- User/Alice avatars.
- repeated Alice Core thumbnails.
- default timestamps/read receipts.
- microphone icon.
- decorative slogans.
- shrinking Alice Core to make room for secondary panels.

# 9. Approval Items

**CVD2-APP-001-R — Right-side placement**
Approve Conversation to the right of Alice Core as the canonical Desktop composition.

**CVD2-APP-002-R — Conversation size**
Approve preferred 400 px width (360–440 responsive range) and ~510 px visible message height.

**CVD2-APP-003-R — Core/Conversation spacing**
Approve 24–32 px visual gap between Core envelope and Conversation.

**CVD2-APP-004-R — Panel priority**
Approve `Core → Conversation → critical contextual state → secondary panels`, with secondary panels collapsing first.

**CVD2-APP-005-R — Message/Composer rules**
Retain no-avatar/no-timestamp message styling and Text + Send minimal Composer.

After approval, CVD-002 v2 becomes Accepted and supersedes the earlier below-Core proposal.
