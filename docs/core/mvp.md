# Alice MVP

## 1. Document Information

| Item | Value |
|---|---|
| Document | `mvp.md` |
| Status | Approved |
| Last Updated | 2026-08-16 JST |
| Governing Decision | Accepted ADR-014 |

Project Aliceは最初からすべてのCapabilityを実装しない。ArchitectureはPhase 0でPhase 1〜4を見通して設計し、実装は各Phaseに必要な最小範囲だけを追加する。

## 2. Phase 0 — Project-wide Design

### Purpose

Project Alice全体の方向性、Architecture、責務、Boundaryおよび開発基盤を整える。

### Required Design Depth

| Target | Required Depth |
|---|---|
| Phase 1 | AI Coding Assistantが独自の設計判断をせず実装できるDetailed Design |
| Phase 2〜4 | Architecture、責務、Boundary、主要Model、主要Flow、Port、SecurityおよびPermission |

変更可能性が高いProvider Model、SDK、Libraryまたは細かなParameterは、対象Phaseの実装時再確認としてよい。

### Status

進行中。Phase 0 Architecture ReviewはApproved。Phase 1 Detailed Design横断修正およびPhase 2〜4設計を継続する。

---

## 3. Phase 1 — Conversation + AI + Conversation History

### Purpose

Aliceと自然なテキスト会話ができる状態を作る。

### Scope

- Smartphone向けFlutter Application
- Single Conversation
- Text Chat
- Alice Personality
- OpenAI Adapterを通したAI Text Generation
- SSE Streaming表示
- Conversation History保存・参照
- IdempotencyとSafe Retry

### Out of Scope

- Personal Memory
- RAG / Vector Search
- External Tools
- Voice
- PC / Browser Operation
- Autonomous Agent
- Multi User / Multi Conversation
- Public Internet Backend

### Success Criteria

- Aliceとして一貫した回答ができる
- 生成途中の回答を表示できる
- Conversation Historyを保存・再取得できる
- App再起動後もHistoryを表示できる
- 通信再試行によってMessageまたはAI呼出しが重複しない

---

## 4. Phase 2 — Personal Memory

### Purpose

Aliceをユーザー専用AIへ成長させる。

### Architecture Scope

- User Profile
- Preference
- Engineering Memory
- Project Memory
- Memory Extraction / Retrieval
- Conversation Contextへの関連Memory統合
- Retention / Deletion / Sensitive Data Control

### Success Criteria

Aliceが関連するPersonal Memoryを必要な範囲で利用し、ユーザーに合わせた回答を返せる。

---

## 5. Phase 3 — Tools / External Services + Engineering Support

### Purpose

Aliceが外部情報を取得・操作し、生活と開発を支援できる状態を作る。

### Architecture Scope

- Tool Registry / Selection Boundary
- Read / Write Risk Classification
- Permission / Approval
- Calendar、Web、店舗情報
- GitHub / AWS Integration
- Code / Architecture / Requirement Review
- Tool ResultのContext統合
- Audit

### Success Criteria

Aliceが必要なToolを提案し、PermissionとApprovalに従って情報取得または操作を実行できる。

---

## 6. Phase 4 — Agent + PC / Browser Operation + Voice

### Purpose

Aliceがユーザーの目的を安全なStepへ分解し、PC、Browser、VoiceおよびToolを組み合わせて支援できる状態を作る。

### Architecture Scope

- Task Planning / Re-planning
- Step / Deadline / Cost Limit
- User Confirmation
- Agent Execution State
- BackendとPython PC AgentのTrust Boundary
- OS / Application / File Operation
- Playwright Browser Operation
- Speech To Text / Text To Speech
- Wake Word Boundary
- Operation Audit / Recovery

### Success Criteria

AliceがUser Controlを維持し、危険操作を無断実行せず、目的達成に必要な複数Stepを安全に支援できる。

---

## 7. Development Principles

- Design First
- 実際に利用して改善する
- Conversation HistoryとPersonal Memoryを分離する
- AliceとAI Providerを分離する
- Tool ProposalとExecution Authorityを分離する
- 必要な機能だけを各Phaseで実装する
- 危険操作はApprovalとAuditを持つ

Phaseの詳細Requirement IDは`requirements.md`をSource of Truthとする。
