## 6. Phase 4 — Agent / PC / Browser / Application / Voice

### 6.1 Purpose

Aliceがユーザーの目的をConversation中心の体験から理解し、安全なGoal / Plan / Execution lifecycleへ分解したうえで、Tool、PC、Browser、ApplicationおよびVoice Capabilityを必要な場合だけ組み合わせて支援できる状態を作る。

### 6.2 Functional Requirements

#### 6.2.1 Goal / Planning / Orchestration

| ID | Requirement |
|---|---|
| P4-FR-001 | User Goalを安全なTask / Stepへ分解できること |
| P4-FR-002 | User Goalに応じてTool / Agent Capabilityを選択できること |
| P4-FR-003 | Permission / Approval / Deadline / Step Limitの範囲内で実行できること |
| P4-FR-004 | Observation / Resultを評価し、Continue / Replan / Ask / Wait Approval / Finish / Stopを選択できること |
| P4-FR-005 | 危険・重要・不可逆な操作ではRiskに応じた確認・Approvalを要求すること |
| P4-FR-006 | PC Agentを通じてFile / Application / OS操作を扱えること |
| P4-FR-007 | Browser操作を扱えること |
| P4-FR-008 | Voice InputをConversation Entryとして扱えること |
| P4-FR-009 | Voice Outputを扱えること |
| P4-FR-010 | Voiceを単独のAuthenticationまたはHigh-risk Approval要素として扱わないこと |
| P4-FR-011 | Agent / Tool / PC / Browser / Application ExecutionをAudit可能にすること |
| P4-FR-012 | UserにChat / Tool / Agent / Voiceの手動Mode選択を要求せずAliceがCapabilityを推論すること |
| P4-FR-013 | Goal → Plan → Execute → Observe → EvaluateのLifecycleを持つこと |
| P4-FR-014 | PlanがStep、順序、依存関係および必要Capabilityを表現できること |
| P4-FR-015 | ObservationまたはCurrent Stateに応じて安全にReplanできること |
| P4-FR-016 | 必要情報が不足・曖昧な場合は推測せずNeeds User Inputへ遷移できること |
| P4-FR-017 | Step数、Replan回数、Duration、Side-effect同時実行等に有限上限を持つこと |
| P4-FR-018 | Background ExecutionでもPermission / Approval / Safety / Limitsを維持すること |
| P4-FR-019 | Background ExecutionがApproval / User Input待ちとなり必要に応じ通知・再入場できること |

#### 6.2.2 Execution Lifecycle / Outcome / Recovery

| ID | Requirement |
|---|---|
| P4-FR-020 | AgentExecutionがPlanning / Running / Waiting for Approval / Needs User Input / Completed / Failed / Cancelled / Unknown Outcomeを区別すること |
| P4-FR-021 | 必要に応じPausedをCancelとは別状態として扱えること |
| P4-FR-022 | Lifecycle StatusとExecution Outcomeを分離すること |
| P4-FR-023 | OutcomeをSuccess / Failure / Partial Success / Unknownとして区別すること |
| P4-FR-024 | Stopは新規Actionを止める意味でありRollbackを意味しないこと |
| P4-FR-025 | Cancelは送信済み・実行済みSide Effectを未実行と誤認しないこと |
| P4-FR-026 | Reverse / CompensationをCancel / Stopとは別Use Caseとして扱うこと |
| P4-FR-027 | Reverse / Compensation時もCurrent State / Permission / Risk / Approvalを再評価すること |
| P4-FR-028 | Unknown Outcomeで成功・失敗を推測しないこと |
| P4-FR-029 | Unknown Outcomeでは可能な限りCurrent External Stateを確認すること |
| P4-FR-030 | Side EffectのUnknown OutcomeでBlind Retryしないこと |
| P4-FR-031 | Retry時にExecution Identity / Idempotency / Duplicate Side Effect Riskを考慮すること |
| P4-FR-032 | Backend / Executor / Browser / Network Failure後にExecution StateをRecoveryできること |
| P4-FR-033 | Recovery時にPrevious Execution / Current State / Permission / Approval / Authorityを再確認すること |
| P4-FR-034 | Crash後にHigh-risk Side Effectを無条件自動Resumeしないこと |
| P4-FR-035 | Goal / Execution / Action / Attemptを安定したIdentityとCorrelationで追跡できること |

#### 6.2.3 Permission / Approval / Risk / Authority

| ID | Requirement |
|---|---|
| P4-FR-036 | Permission評価へDefault Denyを適用すること |
| P4-FR-037 | PermissionをCapability × Operation × Scopeに加え必要に応じDevice / ExecutorへBindingできること |
| P4-FR-038 | Read / Write / Action / Delete / External Commit / Privileged-System Operationを区別すること |
| P4-FR-039 | Permission LifetimeとしてAllow Once / Session / Persistent / Deny等を扱えること |
| P4-FR-040 | PermissionをInspect / Change / Narrow / Revoke / Disableできること |
| P4-FR-041 | Alice / LLM / Agent / Tool / External Content / Executorが自身のPermissionを拡張できないこと |
| P4-FR-042 | Memory / Preference / Conversation History / AI ProposalをPermissionとして扱わないこと |
| P4-FR-043 | PermissionをApprovalおよびExecution Intentと分離すること |
| P4-FR-044 | RiskをLow / Medium / High / Criticalで表現すること |
| P4-FR-045 | RiskをAction / Target / Scope / Current State等のContextから評価すること |
| P4-FR-046 | Destructive / Commit / Third-party / Irreversible / Privileged OperationのRiskを適切にEscalateすること |
| P4-FR-047 | ApprovalをAction / Target / Arguments / Effect / Risk / PermissionへBindingすること |
| P4-FR-048 | Material Change時にApprovalを無効化し再Approvalを要求すること |
| P4-FR-049 | ApprovalをSingle-useとし他Actionへ転用しないこと |
| P4-FR-050 | High-risk Operationで対象と影響を再表示するStrong Approvalを要求できること |
| P4-FR-051 | 条件を満たすLow-risk Explicit Commandを当該Operation限定Approval Evidenceとして扱えること |
| P4-FR-052 | Execution Method変更時にPermission / Risk / Approvalを再評価すること |
| P4-FR-053 | Tool / Connector FailureからAgent / PC OperationへSilent Authority Escalationしないこと |
| P4-FR-054 | AliceがHard Safety Boundaryを無効化・緩和できないこと |

#### 6.2.4 Credential / Executor / Device Trust

| ID | Requirement |
|---|---|
| P4-FR-055 | CredentialをConversation / Personal Memoryから分離すること |
| P4-FR-056 | CredentialをLLM Prompt / Normal Context / Log / Audit / Insecure Client Storageへ露出しないこと |
| P4-FR-057 | Executor Identityを識別できること |
| P4-FR-058 | BackendとExecutor間でAuthentication / Command Authenticityを保証すること |
| P4-FR-059 | Replay Prevention / Expiration / Integrity Validationを行うこと |
| P4-FR-060 | Executor自身もCapability / Operation Scopeを強制すること |
| P4-FR-061 | Device Trust / Binding失効後は新しいExecution Authorityを受け付けないこと |
| P4-FR-062 | Disconnect / Timeout / Heartbeat Lossを成功扱いせずRecovery / Unknown Outcomeへ接続すること |
| P4-FR-063 | Side-effecting ActionのConcurrencyを安全に制御すること |
| P4-FR-064 | Stale Assignment / Fence / AuthorityによるLate Executionを拒否すること |

#### 6.2.5 Filesystem / Terminal / OS / Application

| ID | Requirement |
|---|---|
| P4-FR-065 | Filesystem ScopeをCanonicalに評価すること |
| P4-FR-066 | Path Traversal / Symlink Escape等によるScope Escapeを防止すること |
| P4-FR-067 | File Read / Create-Write / Overwrite / Move / Deleteを区別すること |
| P4-FR-068 | Destructive File OperationへRisk / Approvalを適用すること |
| P4-FR-069 | Terminal OperationをExecutable / Arguments / Working Directory / Environment等のStructured Actionとして扱うこと |
| P4-FR-070 | LLM生成の任意Shell CommandをValidationなしで実行しないこと |
| P4-FR-071 | Privileged / Administrator / System Operationを通常Operationと分離すること |
| P4-FR-072 | Privileged Operationに専用Permission / Risk / Approvalを適用すること |
| P4-FR-073 | Application UI OperationをRead / Interact / Commitへ分離すること |
| P4-FR-074 | Window / Element / Targetが曖昧な状態でCommitしないこと |
| P4-FR-075 | Focus / Current State / Stale UI Elementを検証すること |
| P4-FR-076 | Clipboard / Drag & Drop / File PickerをSensitive Boundaryとして扱うこと |
| P4-FR-077 | Sensitive UI Operationへ強化されたSafetyを適用すること |
| P4-FR-078 | Process / System / Device Controlを独立Capability / Scopeとして扱うこと |

#### 6.2.6 Browser

| ID | Requirement |
|---|---|
| P4-FR-079 | Browser OperationをRead / Interact / Commitへ分離すること |
| P4-FR-080 | Form InputをSubmission Authorityとして扱わないこと |
| P4-FR-081 | Browser Commit前にOrigin / Action / Effectを確認すること |
| P4-FR-082 | Browser Session / OriginをScopeとして扱うこと |
| P4-FR-083 | Authenticated Browser SessionをPermission / Approvalとして扱わないこと |
| P4-FR-084 | DownloadをExecute Authorityとして扱わないこと |
| P4-FR-085 | UploadでTarget / File / Data Scope / Riskを評価すること |
| P4-FR-086 | Browser / Web ContentをUntrusted External Contentとして扱うこと |
| P4-FR-087 | External Contentの命令でGoal / Permission / Approval / Safety / Allowlistを変更しないこと |
| P4-FR-088 | AIが悪意あるAction Proposalを生成してもExecution Boundaryで拒否できること |
| P4-FR-089 | Browser Send / Submitの結果不明時にCurrent Stateを確認しDuplicate Submissionを防止すること |

#### 6.2.7 Voice / Ambient Interaction

| ID | Requirement |
|---|---|
| P4-FR-090 | VoiceをConversation I/Oとして扱い独立Execution Authorityを与えないこと |
| P4-FR-091 | Voice使用に明示的Activationを必要とすること |
| P4-FR-092 | Always-on Microphoneを必須としないこと |
| P4-FR-093 | Microphone StateをUserが認識できること |
| P4-FR-094 | Ambient Audioを自動的にConfirmed User Instructionとみなさないこと |
| P4-FR-095 | Voice Recognition AmbiguityをRiskに応じて確認すること |
| P4-FR-096 | Voice InputをIdentity Proofとして扱わないこと |
| P4-FR-097 | VoiceだけでHigh / Critical Risk Approvalを完結しないこと |
| P4-FR-098 | Riskに応じてScreen / Strong ApprovalへEscalateできること |
| P4-FR-099 | Voice Data Retention / Provider送信をPrivacy Controlの対象とすること |
| P4-FR-100 | UserがVoice / Agent Interactionを停止できること |

#### 6.2.8 Audit / User Control

| ID | Requirement |
|---|---|
| P4-FR-101 | Agent Execution HistoryをConversation History / Personal Memoryと分離すること |
| P4-FR-102 | Execution HistoryでGoal / Major Action / Approval / Observation / Outcome / Failureを説明できること |
| P4-FR-103 | Audit Source of TruthをUser-visible Conversation / History Projectionと分離すること |
| P4-FR-104 | AuditへCredential / Secret / Full Prompt / Chain-of-thoughtを保存しないこと |
| P4-FR-105 | User-visible Execution Historyを安全なAudit Projectionとして提供できること |
| P4-FR-106 | Goal / Execution / Outcome / Conversation Responseを相関し誤った成功表現をしないこと |
| P4-FR-107 | Agent Execution中にEmergency Stopを利用できること |
| P4-FR-108 | Emergency Stop後に新規Dispatchを防止しAlready-dispatched ActionのOutcomeを検証すること |

#### 6.2.9 Frontend / Alice Experience

| ID | Requirement |
|---|---|
| P4-FR-109 | Alice Core + ConversationをPrimary Experienceとすること |
| P4-FR-110 | Memory / Tool / Agent / Execution / Permission / Approval / VoiceをPermanent Primary Navigation必須としないこと |
| P4-FR-111 | Capability UIをProgressive Disclosureで表示すること |
| P4-FR-112 | Agent RunningはConversation内のMinimal Statusを基本とし自動的にAgent Dashboardへ遷移しないこと |
| P4-FR-113 | Execution DetailをOn-demand Subordinate UIとして提供すること |
| P4-FR-114 | Approval UIでAction / Target / Impact / Riskを理解可能にすること |
| P4-FR-115 | Permission RequestをContextualに表示しPermission ManagementをSubordinate UIとすること |
| P4-FR-116 | Unknown OutcomeをSimple Retry ErrorではなくVerification Flowとして表示すること |
| P4-FR-117 | Emergency Stopを到達可能にしつつ通常時に恒常的Visual Dominanceさせないこと |
| P4-FR-118 | Alice CoreをVoice / Ambient / Processing StateのPresence Anchorとして扱うこと |
| P4-FR-119 | Alice Core State SemanticsとRendererを分離すること |
| P4-FR-120 | DesktopでSpatial Contextual Presentationを許容しPermanent Capability Dashboardを必須としないこと |
| P4-FR-121 | Short System SemanticsをEnglish-first、Meaning-critical / Human CommunicationをJapanese-firstとすること |

### 6.3 Phase 4 Non-functional Requirements

| ID | Requirement |
|---|---|
| P4-NFR-001 | Security / Least Privilege: 必要最小Capability・Scope・権限で実行すること |
| P4-NFR-002 | Authority Separation: Goal / Memory / AI Proposal / Permission / Approval / Execution Authorityを分離すること |
| P4-NFR-003 | Credential Isolation: CredentialをConversation / Memory / Prompt / unsafe Logから隔離すること |
| P4-NFR-004 | Untrusted Content Safety: External ContentからAuthorityを獲得できないこと |
| P4-NFR-005 | Bounded Execution: Step / Replan / Duration / Side-effect等に有限上限を持つこと |
| P4-NFR-006 | Reliability: Duplicate Side Effectと誤ったSuccess判定を防止すること |
| P4-NFR-007 | Durable Recovery: Crash / Disconnect後もExecution / Unknown / Recovery Stateを失わないこと |
| P4-NFR-008 | Executor Security: Executor Identity / Authentication / Integrity / Replay Prevention / Scope enforcementを行うこと |
| P4-NFR-009 | Auditability: Authority DecisionとExecutionを安全に追跡可能にすること |
| P4-NFR-010 | Privacy: Voice / Screenshot / File / Browser / Application DataをData Minimizationで扱うこと |
| P4-NFR-011 | AI Provider Independence: Provider固有SDKへAlice Authority / Domainを移譲しないこと |
| P4-NFR-012 | Extensibility: Capability / Executor / RendererをBoundaryを壊さず追加・交換可能にすること |
| P4-NFR-013 | Maintainability: Ownership / Dependency / Contractを明確に分離すること |
| P4-NFR-014 | Testability: State / Authority / Recoveryを決定論的Test可能にすること |
| P4-NFR-015 | Adversarial Testability: Prompt Injection / Replay / Crash / Scope Escape等をFault Injection可能にすること |
| P4-NFR-016 | Observability: Execution / Failure / Recoveryを機密情報なしで観測可能にすること |
| P4-NFR-017 | User Control: Permission / Approval / Stop / Emergency Stop / Detailを理解・操作可能にすること |
| P4-NFR-018 | Accessibility: Motion / Colorだけに依存せずDynamic Type / Contrast / Reduce Motion等を尊重すること |
| P4-NFR-019 | Visual Consistency: UI-012のAlice Identity / Spatial / Calm / Spacious基準をCross-phaseで維持すること |
| P4-NFR-020 | Renderer Replaceability: Alice Core Renderer変更がDomain / Authority Contract変更を要求しないこと |
| P4-NFR-021 | Reproducibility: Runtime / SDK / Browser / Test Tool Versionを実装時に再現可能に固定すること |
| P4-NFR-022 | Cross-phase Compatibility: Phase 1〜3 Accepted Boundaryを壊さないこと |
| P4-NFR-023 | Traceability: Requirement → Design → API/DB → Security → Frontend → Testを追跡できること |
| P4-NFR-024 | Design Drift Prevention: Cross-document / Visual Drift Gateで未承認変更を防止すること |

### 6.4 Cross-phase Invariants

- Conversation History ≠ Personal Memory
- Personal Memory ≠ Permission
- Personal Memory ≠ Approval
- User Goal ≠ Execution Authority
- Permission ≠ Approval
- Form Input ≠ Submission
- Stop ≠ Rollback
- Failure ≠ Unknown Outcome
- External Content ≠ User Instruction
- Authenticated Session ≠ Permission
- Tool Permission ≠ Automatic Agent Permission
- Connector Failure ≠ Silent Agent Escalation
- Credential ≠ Conversation / Personal Memory Data
- Alice cannot expand its own Permission
- Alice cannot disable or relax a Hard Safety Boundary
- Current External State is obtained from the responsible Tool / Service / Environment
- Alice Core State ≠ Alice Core Renderer
- Capability Growth ≠ Dashboard Growth

### 6.5 Success Criteria

1. User Goalを複数Stepへ分解し、必要Capabilityを選択できる。
2. UserへChat / Tool / Agent等のMode選択を強制しない。
3. Permission / Approval / Risk / Safety Boundaryを守って実行できる。
4. PC / Browser / Application / Voiceを安全なCapability Boundaryで扱える。
5. Side EffectについてPartial / Unknown / Recoveryを正しく扱える。
6. Prompt InjectionやExternal ContentからExecution Authorityを得られない。
7. Stop / Emergency Stop / Recoveryが定義どおり機能する。
8. Conversation中心のAlice Experienceを維持する。
9. 全RequirementがTraceability MatrixでCOVERED、DEFERRED with basis、またはOUT_OF_SCOPE with basisのいずれかとなる。
10. Final ReviewでCritical / High / Blocking Medium GAP = 0となる。

### 6.6 Out of Scope

- Alice自身の自律的なSource変更・Deploy・Self-modification
- Alice / Agent / Tool / LLMによるPermission自己昇格またはSafety Boundary無効化
- Unlimited Autonomous PC / OS Access
- Security Boundary / OS Security / Browser Security / CAPTCHA / MFAの回避
- Conversation / Personal MemoryへのCredential保存
- User Intentを伴わないCritical External Commit
- Payment / Transfer / Contract等の完全自律実行のPhase 4初期実装
- 未承認Device / Accountへの操作
- Multi-agentをPhase 4初期の必須Architectureとすること
- Vision / Camera / Face Recognitionの初期実装
- Alice CoreのParticle Algorithm / Mesh Topology / exact AnimationをCross-phase固定仕様とすること

### 6.7 Requirements Review Record

| Review | Date | Result |
|---|---|---|
| Phase 4 Requirements Formalization Review | 2026-09-04 JST | Approved — P4-FR-001〜121 / P4-NFR-001〜024 |
