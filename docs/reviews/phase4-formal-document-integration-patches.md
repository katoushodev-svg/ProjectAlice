# Project Alice — Phase 4 Formal Document Integration Patches


**Date:** 2026-09-04 JST  
**Status:** Approved Integration Set  


このファイルは、現在のProject Filesが直接書換えできない環境でも、承認済みPhase 4設計を正式Repositoryへ機械的に反映できるようにした統合パッチ仕様である。新しいArchitecture Decisionは追加しない。


## 1. requirements.md


既存 `## 6. Phase 4 ...` セクション全体を `requirements-phase4-section.md` の内容で置換する。P4-FR-001〜011のIDは保持し、P4-FR-012〜121とP4-NFR-001〜024を追加する。


## 2. decisions.md

> Cross-Phase Review correction: existing `ADR-014 Phase 0設計範囲とAI Capability Boundaryの変更` remains the stable ADR-014. Phase 4 ADRs are therefore numbered ADR-015〜018.



既存ADRの末尾へ以下を追加する。

### ADR-015 Shared Execution Authority Boundary
**Status:** Accepted  
Tool / Agent / PC / Browser / Applicationで共通するPermission、Approval、Risk、Execution Authority、Durable Intent、Execution Fence、Outcome、Cancellation、Recovery、Auditをshared `execution` Featureが所有する。

### ADR-016 Conversation-centered Capability Orchestration
**Status:** Accepted  
ConversationをPrimary User-facing Entry Pointとし、AliceがUser Goalに応じてConversation-only / Tool / Agent / Composite Capabilityを選択する。恒常的なChat / Tool / Agent Mode選択をUserへ要求しない。

### ADR-017 Cross-Phase Alice Experience Principle
**Status:** Accepted  
Alice Core + ConversationをPrimary Experienceとし、Memory / Tool / Agent / Permission / Approval / Voice / ExecutionをContextualまたはSubordinate Capabilityとして統合する。UI-012をCross-phase Visual Baselineとして維持し、Capability GrowthをDashboard Growthへ直結させない。

### ADR-018 Agent Orchestration / Executor Separation
**Status:** Accepted  
Backend `agent` FeatureがGoal / Plan / Observation / Replanningを所有し、top-level `agent/` RuntimeはPC / Browser / Application / OSのvalidated executionのみを担当する。ExecutorはAlice Coreの判断ロジックを所有しない。

### Phase 4 Detailed Decision Index
- AGENT4-001〜013 — Agent / Execution Architecture — Accepted
- AGENT4-014〜020 — PC / Environment Boundary — Accepted
- AGENT4-021〜027 — Voice / Background — Accepted
- AGENT4-028〜041 — Permission / Approval / Executor Safety — Accepted
- AGENT4-042〜048 — Browser — Accepted
- AGENT4-049〜055 — Application UI — Accepted
- AGENT4-056〜097 — Phase 4 detailed API / Persistence / Security / AI / OS-related decisions — Accepted; exact per-ID topic mapping must use the accepted original decision text and must not be reconstructed from memory
- AGENT4-098-R〜104-R — Frontend / Alice Experience — Accepted
- AGENT4-105〜111 — Test Design — Accepted
- AGENT4-112〜118 — Traceability — Accepted
- UI-XP-001 — Cross-Phase Frontend / Visual Principle — Accepted


## 3. repository-structure.md



Phase 4 target structureとして以下を追加する。これはTarget Structureであり、Phase 1へ空Packageを先行作成しない。

```text
backend/
└── .../
    ├── conversation/
    ├── memory/
    ├── tool/
    ├── agent/
    └── execution/
```

Ownership:
- `conversation`: User-facing Conversation / Conversation History / capability orchestration entry / final response
- `memory`: Personal Memory / preference / lifecycle。Execution Authorityを持たない
- `tool`: Tool Definition / Registry / Connector Capability / tool-specific validation & adapter
- `agent`: Goal / Plan / AgentExecution orchestration / AgentAction / Observation / Evaluation / Replanning
- `execution`: Permission / Approval / Risk / Execution Authority / Durable Intent / Fence / Outcome / Cancellation / Recovery / Audit / Executor-Device Authority
- top-level `agent/`: Python Executor Runtime。Filesystem / Terminal / Browser / Application / OS adapter

`backend.agent` = Alice Agent Orchestration。`project-alice/agent/` = Local Executor Runtime。

Dependency:
`agent.application → execution.application`, `tool.application → execution.application`.
`execution`は`agent`またはtool-specific infrastructureへ逆依存しない。

禁止:
Frontend → PC Agent direct、Executor → Alice Core direct、Memory → Execution Authority、AI Provider → Executor direct、External Content → Permission。


## 4. ai-design.md


本Bundleの更新済み `ai-design.md` を使用する。末尾の `Phase 4 Formal Integration Resolution — 2026-09-04` が競合時のNormative Sectionである。


## 5. security-design.md


本Bundleの更新済み `security-design.md` を使用する。末尾の `Phase 4 Formal Security Integration — 2026-09-04` が競合時のNormative Sectionである。


## 6. api-design.md



Phase 4後続Sectionとして以下を追加する。

- AgentExecution resource: executionId, goalSummary, status, outcome, currentActivity, progress, waitingReason, timestamps, availableActions
- lifecycle: PLANNING / RUNNING / WAITING_FOR_APPROVAL / NEEDS_USER_INPUT / optional PAUSED / COMPLETED / FAILED / CANCELLED / UNKNOWN_OUTCOME
- outcome: SUCCESS / FAILURE / PARTIAL_SUCCESS / UNKNOWN
- Approval resource: action-bound, single-use, material-change invalidation。ClientはRiskやApproval statusを任意上書きできない
- Permission resource: Capability / Operation / Scope / Device-Executor summary / Lifetime / Effect / Status。Risk override / Hard Safety bypass APIは禁止
- execution control: Cancel, optional Pause/Resume, Emergency Stopを区別
- UNKNOWN_OUTCOME: Verify Current State / ReconciliationをPrimary ContractとしBlind RetryをPrimary Contractにしない
- Executor / Device: trust state / supported capability / lastSeen / binding version。SecretをUser-facing APIへ返さない
- User-facing APIとAuthenticated Executor Protocolを分離
- ObservationはExecutor結果でありBackendがAuthoritative Outcomeへ評価する
- Problem Detailsで PERMISSION_REQUIRED / DENIED / APPROVAL_REQUIRED / STALE / EXPIRED / USER_INPUT_REQUIRED / TARGET_AMBIGUOUS / TARGET_CHANGED / CAPABILITY_UNAVAILABLE / EXECUTOR_UNAVAILABLE / EXECUTION_CONFLICT / EXECUTION_CANCELLED / UNKNOWN_OUTCOME / RECOVERY_REQUIRED / SAFETY_BOUNDARY_DENIED 等を区別
- Client disconnect ≠ AgentExecution cancel。Background execution stateはdurableに再取得可能
- Full Prompt / CoT / Credential / Raw provider response / internal policy / signing material / DynamoDB keyを公開しない


## 7. database-design.md



Phase 4 Persistence Boundaryとして以下のLogical Aggregateを追加する。

- AgentExecution
- AgentPlan / Plan Version
- AgentAction / Action Version / Attempt
- Observation
- Permission / Permission Decision reference
- Approval
- Risk Decision reference
- Durable Execution Intent
- Execution Fence
- Executor Assignment / Device Binding
- Cancellation / Emergency Stop state
- Recovery / Verification state
- Audit Event

Rules:
- Conversation History ≠ Personal Memory ≠ Execution Persistence ≠ Audit SoT
- Full Conversation / Full Memory / Credential / Hidden Prompt / CoT / unbounded Screenshot / Terminal Output / File ContentをExecution Persistenceへ複製しない
- Replan後は旧Action / Approval / Executor Commandが実行できないVersion/Fence contractを持つ
- Side-effecting ActionはApproval Consumption + Durable Intent + PREPAREDを競合安全なBoundaryで確定してからDispatch
- Dispatch progress stateとOutcomeを分離
- UNKNOWN_OUTCOMEはdurable stateでありrestart後もverification/recovery requirementを失わない
- Emergency Stop stateをdurableに保持しrestart後にsilent resumeしない
- TTLはAuthority / Fence validity / Approval expiry / Success判定のSource of Truthにしない


## 8. frontend-ui-design.md



UI-012より上位のCross-Phase PrincipleとしてUI-XP-001を追加する。

Hierarchy:
UI-XP-001 → UI-012 → Phase-specific UI → Screen / Component / FIP。

Primary Experience = Ambient/Voice + Conversation。
Contextual Capability = Memory usage, Tool result, Agent status, Approval, Permission request, Unknown Outcome。
Subordinate Management = Memory Management, Permission Management, Execution Detail, Trusted Devices, Safety, Settings, Audit/History。

Capability Growth ≠ Primary Navigation Growth。
Alice Core = Presence; not logo/button/loading spinner/backend entity。
AliceCoreState → Presentation Contract → Renderer → Visual Expression。Rendererは交換可能。
Current visual direction: Fluid / Organic / Particle Mesh / Cyan / Electric Blue / Violet / Internal Light / Soft Energy Flow。

Presentation patterns:
Ambient/Voice, Conversation, Agent Running, Approval Required, Permission Required, Execution Detail, Unknown Outcome/Verifying, Emergency Stop。
これらは別Modeではなく一つのAlice Experienceの状態。

Desktopは「必要な情報がAlice Coreの周囲に出現し、役目を終えると消える」Spatial Presentationを許容しPermanent DashboardをDefaultにしない。

Short system semanticsはEnglish-first、意味理解・意思決定が重要な説明はJapanese-first。
Visual Drift Gateを正式Review要件とする。


## 9. frontend-design.md



Cross-phase Frontend Architecture Sectionを追加する。

- Frontendはrender / user input collection / approval decision submission / permission action / cancel-stop request / detail requestを担当する
- FrontendはRisk / Permission / Execution Authority / Success-from-timeoutを決定しない
- Primary = Ambient/Voice, Conversation
- Contextual = Tool Result, Agent Status, Approval, Permission Request, Unknown Outcome
- Subordinate = Memory Management, Permission Management, Execution Detail, Trusted Devices, Safety/Settings, Audit/History
- AliceCoreStateはDomain enumの直露出ではなくPresentation Mappingとする
- Phase 1に将来用route/package/interfaceを先行実装しない


## 10. test-design.md



Phase 4正式Test Registryを追加し、Future Phase扱いを終了する。

Source Decisions:
- AGENT4-105 State Machine
- AGENT4-106 Permission / Approval / Risk
- AGENT4-107 PC / Browser / Application Safety
- AGENT4-108 Prompt Injection / Adversarial
- AGENT4-109 Crash / Recovery / Unknown Outcome
- AGENT4-110 Voice / Ambient / Safety
- AGENT4-111 Cross-Phase E2E / Visual Drift Gate

Required registries:
- AgentExecution normal/invalid transition
- Lifecycle Status ≠ Outcome
- Capability × Operation × Scope × Device × Permission Lifetime × Risk × Approval matrix
- Filesystem traversal/symlink/scope/destructive tests
- Structured Terminal / privilege tests
- Browser READ≠INTERACT≠COMMIT, FORM INPUT≠SUBMIT, DOWNLOAD≠EXECUTE, LOGIN≠PERMISSION, Origin isolation
- Application focus / stale element / clipboard / drag-drop / sensitive UI
- External-content prompt injection with execution-boundary rejection
- Crash-point injection before/after dispatch and no blind retry
- stale fence / replay / stale executor rejection
- voice ambiguity / ambient audio / voice≠identity / screen escalation
- Emergency Stop semantics
- Golden/visual review: Ambient, Conversation, Agent Running, Approval, Unknown Outcome, Emergency Stop, Desktop Spatial
- Cross-phase E2E with filesystem/test/fix/test/push approval flow

Acceptance:
Critical Open = 0, High Open = 0, Blocking Medium Open = 0。


## 11. phase3-tool-design.md



Section 38へ以下のResolution Noteを追加する。

**Phase 4 Resolution — Accepted 2026-09-04**

AGENT4-001のAcceptedにより、Phase 3でtool Featureが所有していた共通Execution InvariantのうちPermission、Approval、Risk、Execution Authority、Durable Execution Intent、Execution Fence、Outcome、Cancellation、Recovery、AuditはPhase 4以降shared `execution` contractへ昇格する。

Tool Definition、Registry、Connector Binding、Connector-specific validation / capability / provenanceは引き続きtool Featureが所有する。

この昇格はPhase 3の既存Permission / Approval / Risk / Durable Intent / Idempotency / Unknown Outcome semanticsを変更しない。Phase 3のToolOperationを無制限Agent Authorityへ昇格しない。

Document metadataの`Draft — Detailed Design in Progress`はProject statusと不一致のためCross-Phase Metadata整合時に`Design Complete`へ更新する。
