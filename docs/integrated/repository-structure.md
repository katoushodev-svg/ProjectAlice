Project Alice - Repository & Module Structure Design

1. Overview

本ドキュメントでは、Project Aliceのリポジトリ構成および各モジュールの責務を定義する。

Project Aliceは以下の複数コンポーネントから構成される。

* Flutter Client
* Spring Boot Backend
* Python PC Agent
* Infrastructure
* Architecture / Design Documents

これらは相互に関連して変更される可能性が高いため、初期構成では単一Git RepositoryによるMonorepo構成を採用する。

本ドキュメントでは、実装コードそのものではなく、

* どのコンポーネントをどこへ配置するか
* 各Directory / Moduleが何を担当するか
* 何を配置してはいけないか
* コンポーネント間の依存関係

を定義する。

⸻

2. Repository Strategy

2.1 Decision

Project AliceはMonorepo構成を採用する。

project-alice/
├── README.md
├── docs/
├── frontend/
├── backend/
├── agent/
├── infra/
└── scripts/

⸻

2.2 Reason

Project AliceではFlutter、Spring Boot、Python Agent、AWS Infrastructureが同一システムとして連携する。

例えばAPI仕様を変更した場合、

* Backend
* Flutter Client
* API Design Document

を同時に変更する可能性がある。

PC Agentとの通信仕様変更では、

* Backend
* Agent
* Architecture Document

が同時に変更される可能性がある。

Monorepoとすることで、これらを同一Commit / Pull Requestとして管理可能にする。

⸻

2.3 Advantages

* システム全体を一つのRepositoryで確認できる
* DesignとImplementationの変更を同時管理できる
* API変更をClient / Backend間で追跡しやすい
* AIへRepository全体をContextとして与えやすい
* 個人開発としてRepository管理が単純になる
* Cross-component変更を一つのPull Requestで扱える

⸻

2.4 Trade-offs

将来的に以下の問題が発生する可能性がある。

* Repository Size増加
* CI/CDの複雑化
* ComponentごとのRelease管理
* Build時間増加

Project Aliceが十分に大規模化し、独立したRelease Cycleが必要になった場合はMulti Repository化を再検討する。

現時点では導入しない。

⸻

3. Root Structure

Project AliceのRoot Directoryを以下とする。

以下はProject Aliceが成長した際のTarget Repository Structureを示す。

各Directoryは必要となったPhaseで作成し、未使用Directoryを先行して作成することは必須としない。

project-alice/
│
├── README.md
├── docs/
├── frontend/
├── backend/
├── agent/
├── infra/
└── scripts/

各Directoryの責務を以下で定義する。

⸻

4. README.md

Purpose

Repository全体のEntry Pointとする。

Responsibilities

READMEには以下を記載する。

* Project Alice概要
* Project Purpose
* System Overview
* Main Technology Stack
* Directory Overview
* Development開始方法へのリンク
* Design Documentsへのリンク

詳細なArchitectureやRequirementはREADMEへ直接記載せず、docs/配下のDocumentへ誘導する。

⸻

5. docs/

Purpose

Project Aliceの設計・要件・意思決定を管理する。

設計書はAI ImplementationにおけるSource of Truthとして扱う。

Initial Structure

docs/
├── product-definition.md
├── requirements.md
├── mvp.md
├── alice-architecture.md
├── repository-structure.md
├── backend-design.md
├── api-design.md
├── database-design.md
├── ai-design.md
├── security-design.md
├── development-environment.md
├── test-design.md
└── decisions.md

Responsibilities

product-definition.md

AliceというProductの目的・価値・役割を定義する。

requirements.md

機能要件・非機能要件を定義する。

mvp.md

Development Phaseおよび各PhaseのScopeを定義する。

alice-architecture.md

Project Alice全体のArchitectureを定義する。

repository-structure.md

Repository構成とModule責務を定義する。

backend-design.md

Spring Boot Backend内部の設計を定義する。

api-design.md

Client / Backend等のAPI契約を定義する。

database-design.md

DynamoDBのAccess Pattern、Table、Key、Index等を定義する。

ai-design.md

Alice人格、Context、AI Provider、Prompt等のAI関連設計を定義する。

security-design.md

Authentication、Secrets、PC Agent権限などのSecurity方針を定義する。

development-environment.md

Local Development Environmentおよび開発に必要なRuntime / SDK / Credential / Environment設定を定義する。

主な対象は以下とする。

* Java / JDK Version
* Flutter SDK
* Python Version
* AWS Credential
* DynamoDB LocalまたはAWS DynamoDBの利用方針
* Environment Variables
* Local / Development / Productionの環境分離
* IDE / Development Tool
* Local起動方法

test-design.md

Unit / Integration / API / E2E等のTest Strategyを定義する。

decisions.md

Architecture Decision Recordを管理する。

Source of Truth / Conflict Resolution Rule

Project Aliceでは、設計書およびADRをAI ImplementationのSource of Truthとして扱う。

複数のDocument間で内容が競合した場合、AIまたは開発者が独自解釈によって実装を進めてはならない。

Documentの責務および優先関係を以下のように定義する。

Product Definition
    │
    ▼
Requirements / MVP
    │
    ▼
Architecture
    │
    ▼
Accepted ADR
    │
    ▼
Detailed Design
    │
    ▼
Implementation

1. Product Definition

対象:

* product-definition.md

Project AliceというProductの目的、価値、基本Conceptを定義する。

下位の設計書はProduct Definitionで定義されたProject Aliceの基本思想と矛盾してはならない。

⸻

2. Requirements / MVP

対象:

* requirements.md
* mvp.md

実装すべき機能、非機能要件、Development Phase、Phase Scopeを定義する。

Detailed DesignやImplementationが、Requirements / MVPで定義されたScopeを独自に変更してはならない。

特に、

* Phase追加
* Phase Scope変更
* Requirement削除
* Requirement追加

を下位設計の判断だけで行ってはならない。

⸻

3. Architecture

対象:

* alice-architecture.md
* repository-structure.md

Project Alice全体の構造、Component Boundary、Dependency Direction、Repository / Module Boundary等を定義する。

Detailed DesignはArchitectureで定義された責務分離やDependency Ruleと矛盾してはならない。

⸻

4. Accepted ADR

対象:

* decisions.md内のStatus: AcceptedであるADR

Accepted ADRは、特定のArchitecture Decisionについて正式なDecisionを表す。

Accepted ADRは、そのDecisionの対象範囲について既存のArchitectureまたはDetailed Designより優先する。

例えばArchitecture Documentに旧Technologyが記載されており、その後Accepted ADRでTechnology変更が決定された場合、Accepted ADRのDecisionを正とする。

ただし、ADRによってArchitectureが変更された場合は、関連するArchitecture / Detailed Design Documentも速やかに更新し、文書間の不整合を残さない。

Status: ProposedのADRは正式Decisionとして扱わない。

⸻

5. Detailed Design

対象例:

* backend-design.md
* api-design.md
* database-design.md
* ai-design.md
* security-design.md
* development-environment.md
* test-design.md

Detailed DesignはRequirements、MVP、Architecture、Accepted ADRを具体的な実装設計へ落とし込む。

Detailed Designは上位DocumentのDecisionを変更してはならない。

必要な変更が判明した場合は、Detailed Designだけを変更するのではなく、影響する上位DocumentまたはADRを先に更新する。

⸻

6. Implementation

対象:

* Source Code
* Configuration
* Infrastructure Definition
* Test Code

Implementationは確定済みDesign Documentに従う。

Source Codeの現状を理由として、Design DocumentのDecisionを自動的に変更しない。

DesignとImplementationが矛盾する場合は、以下のどちらであるかを確認する。

1. ImplementationがDesignに違反している
2. Design自体を変更する必要がある

1の場合はImplementationを修正する。

2の場合は先にDesign Documentおよび必要なADRを更新した後、Implementationを変更する。

⸻

Conflict Resolution

Document間の矛盾を発見した場合、以下の順番で判断する。

Rule 1: Accepted ADRを確認する

対象となるDecisionについてAccepted ADRが存在する場合、そのDecisionを優先する。

ただし、関連Documentが古い状態である可能性があるため、ADRと整合するようDocumentも更新する。

Rule 2: Documentの責務を確認する

同じ内容について複数Documentが記載している場合、その事項を本来管理するDocumentを優先する。

例:

Phase Scope
→ requirements.md / mvp.md
System Architecture
→ alice-architecture.md
Repository Structure
→ repository-structure.md
Backend内部設計
→ backend-design.md
API Contract
→ api-design.md
DynamoDB Design
→ database-design.md
AI / Prompt / Context
→ ai-design.md
Security
→ security-design.md
Development Environment
→ development-environment.md
Testing
→ test-design.md
Architecture Decision
→ decisions.md

Rule 3: 下位Documentが上位Decisionを変更しない

Detailed DesignやImplementationが、Requirements / MVP / Architecture / Accepted ADRの内容を独自に変更してはならない。

Rule 4: 矛盾を推測で解消しない

AI Coding AssistantはDocument間の矛盾を発見した場合、独自解釈でどちらかを選択して実装してはならない。

重要な矛盾はDesign Issueとして扱い、実装を進める前に対象Documentを修正する。

⸻

AI Implementation Rule

AI Coding AssistantはImplementation開始前に、対象Taskに関連するDesign Documentを確認する。

AIは以下を行ってはならない。

* 古いDocumentだけを根拠にArchitectureを変更する
* Proposed ADRをAccepted Decisionとして扱う
* Detailed DesignからRequirements Scopeを変更する
* Source Codeの現状をDesignより優先する
* Document間の矛盾を独自解釈で解消する

矛盾または未決定事項を発見した場合は、実装より先にDesign / ADRの更新が必要な事項として扱う。

⸻

Design Consistency Principle

Project Aliceでは、Documentの優先順位を利用して不整合を放置することを目的としない。

優先順位は、一時的に矛盾が存在した場合の判断基準である。

最終的には関連Documentを更新し、

Requirements
    ↓
Architecture
    ↓
ADR
    ↓
Detailed Design
    ↓
Implementation

が相互に整合した状態を維持する。

⸻

6. frontend/

Technology

Flutter

Purpose

Alice Interfaceを実装する。

Responsibilities

* ユーザー入力
* Alice回答表示
* 会話画面
* Backend API通信
* UI State管理
* Client固有設定
* 将来的な音声入出力
* Device固有機能

Non-Responsibilities

Frontendでは以下を行わない。

* OpenAI API直接呼び出し
* DynamoDB直接アクセス
* Aliceの主要Business Logic
* Conversation History管理
* Personal Memory管理
* Tool選択
* AWS Credential管理
* 外部サービスCredential管理

Initial Structure Concept

詳細はFrontend Design時に決定する。

初期配置イメージは以下とする。

frontend/
├── lib/
├── test/
├── pubspec.yaml
└── ...

Flutter内部のFeature / Layer構成については実装前に別途設計する。

⸻

7. backend/

Technology

Spring Boot / Java

Purpose

Project AliceのBackend Applicationを実装する。

Responsibilities

Backendが最終的に担う責務は以下とする。

* REST API
* Alice Core
* AI Provider Integration
* Conversation History
* Memory Integration
* DynamoDB Integration
* Tool Integration
* Authentication / Authorization
* Logging
* Error Handling
* Agent Integration

Conversation HistoryとPersonal Memoryは異なる責務として扱う。

Conversation Historyは会話を成立・継続させるための情報であり、Phase 1ではconversation Feature内で管理する。

Personal MemoryはAliceがユーザーについて長期的に保持する情報であり、Phase 2以降で独立したmemory Featureとして追加する。

Conversation History ≠ Personal Memory

Phase 1 Responsibilities

Phase 1では以下の責務のみを実装対象とする。

* REST API
* Alice Core
* AI Provider Integration
* Conversation History
* Conversation Repository
* DynamoDB Integration
* Logging
* Error Handling
* Configuration

Phase 1では独立したmemory Featureを実装しない。

Conversation Historyはconversation Featureの責務として扱う。

Tool Integration、Agent Integration、Personal Memory、本格的なAuthentication / Authorization等の将来機能をPhase 1で先行実装しない。

Architecture上定義したAlice CoreとInfrastructureの責務分離を維持する。

Initial Structure Concept

詳細なPackage構成はbackend-design.mdで定義する。

Repository Levelでは以下のみを確定する。

backend/
├── src/
│   ├── main/
│   └── test/
├── build configuration
└── application configuration

Java Package、Layer、Feature Module等の詳細はBackend Designに従う。

Phase 1ではconversation Featureを中心として構成し、将来用のmemory、tool、agent等の空Packageを先行作成しない。

⸻

8. agent/

Technology

Python

Purpose

PC Agentを実装する。

Alice Backendからの要求を受け、ローカルPC上で必要な操作を実行する。

Responsibilities

* OS操作
* アプリ起動
* ファイル操作
* ローカル情報取得
* Browser Automation
* Backendへの実行結果返却

PlaywrightによるBrowser Automationも原則としてAgent配下で管理する。

Non-Responsibilities

AgentはAlice Coreとしての判断を行わない。

以下はBackend / Alice Coreの責務とする。

* Toolを実行するかどうか
* どのToolを利用するか
* Task Planning
* User Intent解釈
* Alice人格
* Conversation History利用判断
* Memory管理

Agentは要求された許可済み操作を実行するExecution Componentとして扱う。

Initial Structure Concept

agent/
├── src/
├── tests/
├── configuration
└── dependencies

Python Package構成はAgent Design時に決定する。

Phase 1ではAgent自体を実装しない。

⸻

9. infra/

Purpose

Project AliceのInfrastructure定義を管理する。

Responsibilities

将来的に以下を配置する。

* AWS Infrastructure as Code
* DynamoDB Infrastructure Definition
* Backend Deployment Definition
* IAM Configuration
* Network Configuration
* Monitoring Configuration
* Cloud Environment Configuration

Principle

AWS Console上だけで構築した設定を唯一のSource of Truthとしない。

可能な範囲でInfrastructure as Codeによる再現可能な構成を目指す。

具体的なIaC TechnologyはInfrastructure Design時に決定する。

Phase 1

Phase 1では必要最小限のInfrastructureのみ管理する。

将来的なAWS構成を先行して大量に作成しない。

⸻

10. scripts/

Purpose

Development / Maintenance用の補助Scriptを配置する。

Examples

* Local development setup
* Build helper
* Test helper
* Data migration
* Development utility

Principle

Application Business Logicをscripts/へ配置しない。

Production ApplicationがScriptへ依存する構造を作らない。

⸻

11. Dependency Rules

Repository Levelで以下の依存原則を定義する。

frontend
    │
    ▼
backend
    │
    ├── AI Provider
    ├── DynamoDB
    ├── External APIs
    └── agent

11.1 Frontend

FrontendはBackend APIのみに依存することを基本とする。

Frontendから以下へ直接依存しない。

frontend ─X→ OpenAI
frontend ─X→ DynamoDB
frontend ─X→ PC Agent

⸻

11.2 Backend

BackendはAliceの中央制御を担当する。

Backendから以下を利用する。

* AI Provider
* DynamoDB
* External APIs
* PC Agent

ただしAlice Coreと具体Infrastructureの依存分離はArchitecture Designに従う。

Phase 1ではConversation History PersistenceについてもAlice CoreからDynamoDBへ直接依存せず、Conversation Repository Portを介して利用する。

Alice Core
    │
    ▼
Conversation Repository Port
    │
    ▼
DynamoDB Infrastructure

Backend Runtime / Framework / Build Toolについては、ADR-013およびdevelopment-environment.mdにて決定済みとする。

⸻

11.3 Agent

AgentはBackendからの実行要求を受ける。

AgentからAlice Coreを直接呼び出さない。

Backend → Agent
Agent ─X→ Alice Core

Aliceの判断ロジックをAgentへ分散させない。

⸻

12. Configuration & Secrets

SecretsをRepositoryへCommitしない。

対象例:

* OpenAI API Key
* AWS Access Key
* OAuth Client Secret
* External API Key
* Agent Authentication Secret

ConfigurationとSecretを分離する。

具体的な管理方式はsecurity-design.md / development-environment.mdで決定する。

⸻

13. Git Rules

Repository全体をGitで管理する。

以下はVersion Control対象とする。

* Source Code
* Design Documents
* Test Code
* Infrastructure Definition
* Build Configuration

以下は原則Commitしない。

* Secrets
* IDE固有Cache
* Build Output
* Runtime Log
* Local Temporary File

具体的な.gitignoreは各Technologyに合わせて設定する。

⸻

14. AI Implementation Rules

Project Aliceでは設計書をAI ImplementationのSource of Truthとして扱う。

AIへ実装を依頼する場合、対象Moduleに関連するDesign Documentを参照させる。

例:

Backend実装:

requirements.md
mvp.md
alice-architecture.md
repository-structure.md
backend-design.md
api-design.md
database-design.md
ai-design.md
security-design.md
development-environment.md
test-design.md
decisions.md

AIはArchitecture、Technology、Module Boundary、Dependency Direction、External Interface等の重要な設計判断を独自に変更または確定してはならない。

特にPhase 1では、Conversation HistoryをPersonal Memoryとして扱ったり、独立したmemory Featureを作成したりしてはならない。

Phase 1のConversation Historyはconversation Feature内で管理し、PersistenceにはConversation Repository Portを利用する。

一方、設計書およびADRと矛盾しない範囲の局所的な実装詳細については、AIが合理的に判断してよい。

局所的な実装詳細の例:

* 変数・メソッド等の命名
* private methodの分割
* 標準的な例外処理の記述
* 設計で指定されていない範囲の小規模な内部実装

ただし、局所的な実装判断であっても既存のArchitecture、責務分離、Dependency Ruleを変更してはならない。

重要な未決定事項を発見した場合は、実装より先に設計またはADRを更新する。

⸻

15. Phase 1 Repository Scope

Phase 1開始時に実装対象となる主要Directoryは以下とする。

project-alice/
├── README.md
├── docs/
├── frontend/
├── backend/
└── infra/

Backendではconversation Featureを中心に実装する。

Conversation Historyはconversation Feature内で管理する。

Phase 1では独立したmemory Featureを作成しない。

agent/は将来利用する構成要素としてRepository上に配置してもよいが、Phase 1では機能実装を行わない。

scripts/についても必要になった時点で作成する。

空Directoryや将来用Boilerplateを大量に先行作成しない。

⸻

16. Repository Evolution

Project Aliceの成長に合わせてRepositoryも段階的に拡張する。

Phase 1
frontend + backend + Conversation History + DynamoDB
        ↓
Phase 2
Personal Memory Feature
        ↓
Phase 3
External Tool追加
        ↓
Phase 4
Agent / PC / Browser / Application / Voice + shared Execution Authority

Phase 1ではConversation Historyをconversation Featureで管理する。

Phase 2でPersonal Memoryが必要になった時点で独立したmemory Featureを追加する。

必要なModuleだけを各Phaseで追加する。

⸻

17. Open Decisions

以下は本設計では確定しない。

* Flutter内部Architecture
* Python Package Structure
* Infrastructure as Code Technology
* CI/CD構成
* Branch Strategy
* Release Strategy
* Versioning Strategy
* Environment Separation方式

Spring Boot Package Structureについてはbackend-design.mdで基本方針を定義済みとする。

Backend Runtime / Framework / Build ToolについてはADR-013およびdevelopment-environment.mdで決定済みとする。

その他の項目は各設計工程で必要になった時点で決定し、重要な判断はADRへ記録する。

⸻

18. Summary

Project Aliceは初期段階ではMonorepoを採用する。

project-alice/
├── README.md
├── docs/
├── frontend/
├── backend/
├── agent/
├── infra/
└── scripts/

上記はTarget Repository Structureであり、各Directoryは必要となるPhaseで段階的に作成する。

各Directoryは以下の責務を持つ。

docs
→ 設計・要件・意思決定
frontend
→ Alice Interface
backend
→ Alice Core / Backend Application
agent
→ Local PC Execution
infra
→ AWS Infrastructure
scripts
→ Development Utility

コンポーネント間の責務を分離し、Alice固有の判断ロジックをBackend / Alice Coreへ集約する。

Phase 1ではBackendのconversation FeatureでConversation Historyを管理する。

conversation
    │
    ├── Conversation
    ├── Message
    └── Conversation History

Conversation HistoryのPersistenceはConversation Repository Portを介してDynamoDB Infrastructureへ接続する。

Alice Core
    │
    ▼
Conversation Repository Port
    │
    ▼
DynamoDB Infrastructure

Personal MemoryはPhase 1では実装しない。

Phase 2開始時に独立したmemory Featureとして追加する。

Conversation History ≠ Personal Memory

Repository Structure自体もProject Aliceの成長に合わせて段階的に拡張し、現時点で不要なModuleやBoilerplateを先行実装しない。

⸻

## 19. Phase 4 Formal Repository Integration — 2026-09-04

**Status:** Accepted / Integrated
**Related ADR:** ADR-015 / ADR-018

Phase 4 target Backend structure:

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

`backend.agent`はAlice Agent Orchestration、`project-alice/agent/`はLocal Executor Runtimeであり、同一責務ではない。

Dependency:

- `agent.application → execution.application`
- `tool.application → execution.application`
- `execution`から`agent`またはtool-specific infrastructureへの逆依存は禁止

Forbidden boundaries:

- Frontend → PC Agent direct
- Executor → Alice Core direct
- Memory → Execution Authority
- AI Provider → Executor direct
- External Content → Permission

Phase 1へ将来用の空Package / Route / Interfaceを先行実装しない。Phase 4構造はTarget Structureであり、各Phaseの実装Scopeに従って段階的に追加する。
