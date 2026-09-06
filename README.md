# Project Alice

**A.L.I.C.E. --- Ambient Life Intelligence & Companion Engine**\
**「日常に溶け込み、生活と共にある知的パートナーエンジン」**

Project Alice is a personal AI assistant designed to support the user
across conversation, personal context, external services, software
development, applications, browsers, and devices. The goal is not simply
to provide answers, but to create an environment in which Alice can
assist with everyday decisions and work while preserving explicit
boundaries for memory, permission, approval, risk, and execution
authority.

## Current Status

  Area                           Status
  ------------------------------ -----------------------------------------------
  Phase 0 --- Design             **COMPLETE**
  Phase 0 Final Design Review    **PASS**
  Repository Sync Gate           **CLOSED / VERIFIED**
  Implementation Authorization   **GRANTED**
  FIP-001                        Completed / Approved
  FIP-002                        Completed / Approved
  FIP-003                        **NEXT --- Domain Foundation**
  FIP-004--012                   Approved / Implementation Ready / Not Started
  FIP-013--014                   Deferred

Phase 1--4 formal design, Cross-Phase Review, FIP-003--012 Cross-Phase
Re-review, Phase 0 Final Design Review, and canonical repository
synchronization are complete. Implementation resumes from **FIP-003
Domain Foundation** in dependency order.

## Development Phases

### Phase 1 --- Conversation

Build the core Alice conversation experience: text conversation,
AI-provider integration, conversation history, frontend/backend
boundaries, streaming, failure handling, accessibility, and the visual
Alice experience.

### Phase 2 --- Personal Memory

Add long-term user-specific memory while keeping **Conversation
History** and **Personal Memory** as separate concepts. Memory is user
data and context; it does not grant permission, approval, or execution
authority.

### Phase 3 --- Tools / External Services

Allow Alice to obtain information and interact with external services
through controlled Tool / Connector boundaries. Tool selection is not
approval, and connector permissions do not automatically become Agent
permissions.

### Phase 4 --- Agent / PC / Browser / Application / Voice

Extend the conversation-centered Alice experience with safe Goal → Plan
→ Execute → Observe → Evaluate lifecycles and contextual use of Tool,
PC, Browser, Application, and Voice capabilities. High-impact actions
remain subject to permission, risk, approval, and execution-authority
boundaries.

## Core Design Principles

-   Conversation remains the primary interaction surface.
-   Conversation History ≠ Personal Memory.
-   Memory ≠ Permission / Approval / Execution Authority.
-   Goal / AI Proposal ≠ Execution Authority.
-   Permission ≠ Approval.
-   Tool Permission ≠ Automatic Agent Permission.
-   External content is untrusted and is not a user instruction or
    permission grant.
-   Form input ≠ submission.
-   Browser login state ≠ authority.
-   Stop / Cancel / Emergency Stop ≠ rollback.
-   Failure ≠ Unknown Outcome.
-   Recovery ≠ blind replay.
-   Voice is not identity or strong approval by itself.
-   Credentials are not stored as Conversation History or Personal
    Memory.
-   Capability growth must not turn Alice into a generic dashboard.

## Architecture

Project Alice uses a monorepo because the Flutter client, Spring Boot
backend, Python Agent, infrastructure, and formal design documents form
one system and may need coordinated changes.

Target structure:

``` text
ProjectAlice/
├── README.md
├── .github/        # Copilot repository instructions and FIP execution prompts
├── docs/           # Requirements, architecture, detailed design, ADRs, FIPs, reviews
├── frontend/       # Flutter client
├── backend/        # Spring Boot backend (created when required by implementation)
├── agent/          # Python executor/runtime (created when required by implementation)
├── infra/          # Infrastructure definitions (created when required)
└── scripts/        # Development/operations scripts (created when required)
```

Unused target directories are not required to be created in advance. The
actual repository may therefore contain only the directories needed by
the current implementation phase.

## Technology Baseline

  -----------------------------------------------------------------------
  Component                           Technology
  ----------------------------------- -----------------------------------
  Frontend                            Flutter 3.47.0 / Dart 3.13.0

  Frontend State                      flutter_riverpod 3.4.2

  Frontend HTTP                       http 1.6.0

  iOS                                 iOS 15+ / Simulator-first
                                      development

  Backend                             Java / JDK 25

  Backend Framework                   Spring Boot 4.1.0

  Build                               Maven 3.9.16 / Maven Wrapper

  Persistence                         DynamoDB; DynamoDB Local 3.3.0 for
                                      Phase 1 local development

  AI Provider                         OpenAI behind an adapter/provider
                                      boundary

  Agent                               Python --- implementation-time
                                      version pinning required

  Browser Automation                  Playwright --- version reconfirmed
                                      before Phase 4 implementation

  Primary IDE                         Visual Studio Code

  AI Coding Assistant                 GitHub Copilot
  -----------------------------------------------------------------------

Time-sensitive provider, SDK, security-tool, Python, Playwright, and
Voice/OS runtime versions must be reconfirmed immediately before the
implementation slice that introduces them.

## Source of Truth

Formal design documents under `docs/` are the implementation Source of
Truth. README is an entry point and status summary; it does not replace
detailed design documents.

Primary entry documents include:

-   `docs/product-definition.md` --- product purpose and value
-   `docs/requirements.md` --- functional and non-functional
    requirements
-   `docs/mvp.md` --- phase/scope document
-   `docs/alice-architecture.md` --- system architecture
-   `docs/repository-structure.md` --- repository and module boundaries
-   `docs/frontend-design.md` / `docs/backend-design.md` --- component
    design
-   `docs/api-design.md` / `docs/database-design.md` /
    `docs/ai-design.md` --- technical contracts
-   `docs/security-design.md` --- security boundaries
-   `docs/development-environment.md` --- reproducible development
    environment
-   `docs/test-design.md` --- testing strategy and gates
-   `docs/decisions.md` --- accepted architecture decisions
-   `docs/frontend-implementation-plan.md` --- current Phase 1
    implementation order and gate state
-   `docs/reviews/` --- formal reviews and cross-phase verification
    records

For implementation, the current FIP document is the primary task-level
Source of Truth. Supporting design documents should be opened only when
a concrete unresolved implementation question requires them.

## AI-Assisted Implementation Workflow

Project Alice follows a design-first, AI-assisted implementation
process. GitHub Copilot performs implementation work within the
boundaries established by the formal design.

``` text
Approved FIP
    ↓
Copilot Agent — one FIP per session
    ↓
Implement only the current FIP
    ↓
Focused test / fix loop
    ↓
Required validation + full regression gate
    ↓
Acceptance Criteria self-review
    ↓
Human diff review
    ↓
Commit / Push
    ↓
Next FIP in a new Agent session
```

Copilot must not independently change architecture, API contracts,
security models, permission/approval models, dependencies, phase
boundaries, or formal design in order to make an implementation pass.
Commit and push remain explicit user actions.

## Current Implementation Target

The next implementation slice is:

**FIP-003 --- Domain Foundation**

Primary Source:

`docs/fip-003-domain-foundation-plan.md`

FIP-003 establishes the Phase 1 Conversation domain model as Plain Dart,
independent from Flutter UI, Riverpod, HTTP/JSON, SSE, persistence,
OpenAI/AWS SDKs, and later-phase Memory / Tool / Agent / Voice concerns.

After FIP-003 passes its implementation gate, development proceeds
through FIP-004 → FIP-012 in dependency order. FIP-013 and FIP-014
remain deferred until Phase 1 implementation has progressed
sufficiently.

## Development Environment

Development is local-first. The frontend is developed with
Flutter/Xcode, while the Phase 1 backend is designed to run directly on
the developer machine through Maven Wrapper. DynamoDB Local is the only
Phase 1 component intended to run through Docker Compose for local
persistence.

Before implementation, use the commands and version constraints defined
in `docs/development-environment.md` and the current FIP. Do not upgrade
dependencies or runtime versions merely because newer versions are
available.

## Documentation Consistency Note

The repository's final review and current implementation plan establish
Phase 1--4 formal design completion and Phase 4 as **Agent / PC /
Browser / Application / Voice**. If an older document still contains
legacy phase numbering or status wording, do not silently treat that
wording as the current implementation state; reconcile it through the
formal Source-of-Truth process before relying on it for an
implementation-affecting decision.

------------------------------------------------------------------------

Project Alice is currently transitioning from completed system-wide
design into implementation. The immediate objective is to turn the
approved Phase 1 design into a working Alice conversation experience,
beginning with FIP-003.
