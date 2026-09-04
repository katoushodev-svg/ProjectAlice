Project Alice - Backend Design

1. Overview

本ドキュメントでは、Project Alice Backendの内部Architecture、Module / Package構成、責務、依存関係および実装ルールを定義する。

Project Alice Backendは以下を中心に担当する。

* Flutter Client向けAPI
* Alice Core
* Conversation制御
* AI Provider連携
* Conversation History管理
* DynamoDB連携
* Configuration
* Error Handling
* Logging
* 将来的なMemory / Tool / Agent Integration

Project Aliceでは実装の多くをAI Coding Assistantへ依頼する方針である。

そのため本ドキュメントは、Backend実装におけるSource of Truthの一つとして扱う。

AIおよび開発者は、本ドキュメントで定義したModule Boundary、Layer Responsibility、Dependency Ruleを独自判断で変更してはならない。

⸻

2. Goals

Backend Designでは以下を重視する。

2.1 Maintainability

機能追加や仕様変更による影響範囲を限定する。

2.2 Extensibility

将来的な以下の機能追加に対応できる構造とする。

* Personal Memory
* RAG
* GitHub Integration
* AWS Integration
* PC Agent
* Browser Automation
* Voice
* Autonomous Agent

2.3 Testability

Business LogicをSpring Framework、OpenAI、AWS、DynamoDB等の具体的なInfrastructureから分離し、Unit Test可能な構造とする。

2.4 Replaceability

OpenAIやDynamoDB等の外部TechnologyをAlice Coreへ直接依存させない。

将来的なProvider / Infrastructure変更の影響を限定する。

2.5 AI Implementability

AI Coding Assistantが、

* どこにコードを配置するか
* どのLayerへ責務を持たせるか
* どこへ依存してよいか
* どの設計判断を変更してはいけないか

を判断できる構造とする。

2.6 Avoid Overengineering

将来必要になる可能性だけを理由として、Phase 1で不要なModule、Interface、Abstraction、Infrastructureを先行実装しない。

⸻

3. Adopted Backend Technology

Project Alice Backendでは以下を正式採用する。

Item	Technology / Version
Language	Java
Java / JDK	25
Framework	Spring Boot 4.1.0
Build Tool	Maven
Maven Version	3.9.16
Build Execution	Maven Wrapper
Persistence	DynamoDB
AI Provider	OpenAI API

Backend実装ではこの構成を前提とする。

詳細なDevelopment Environmentはdevelopment-environment.mdに従う。

⸻

4. Architecture Strategy

Project Alice Backendでは、

Feature-based Package Structure + Layer Separation

を採用する。

最上位を技術Layer単位ではなくFeature単位で分割し、その内部を責務Layerによって分離する。

⸻

5. Why Feature-based Structure

以下のようなRepository全体をLayerだけで分割する構造は採用しない。

controller/
service/
repository/
model/

小規模なCRUD Applicationでは単純で扱いやすいが、Project Aliceでは将来的に以下のようなFeatureが追加される。

conversation
memory
tool
github
aws
agent
voice

Layer単位だけで分割すると、一つのFeatureを変更するためにRepository内の複数Directoryを横断する必要がある。

そのためProject Aliceでは、

Feature
    ↓
Layer

の順番でPackageを構成する。

⸻

6. Target Package Structure

Project Alice Backendが成長した際のTarget Structureは概念的に以下とする。

com.projectalice.backend
│
├── conversation/
│   ├── presentation/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── memory/
│   ├── presentation/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── tool/
│   ├── presentation/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
└── shared/

これはTarget Structureであり、すべてのPackageをPhase 1で作成することを意味しない。

必要になったFeature / Layerのみ段階的に追加する。

空Packageや将来用Classを大量に先行作成しない。

⸻

7. Phase 1 Package Structure

Phase 1では基本AI会話を成立させる。

主要Featureはconversationとする。

概念的なPackage Structureは以下とする。

com.projectalice.backend
│
├── conversation/
│   │
│   ├── presentation/
│   │
│   ├── application/
│   │
│   ├── domain/
│   │
│   └── infrastructure/
│
└── shared/

Phase 1では独立したmemory Featureを作成しない。

Conversation Historyはconversation Featureの責務として扱う。

⸻

8. Conversation History and Personal Memory

Project AliceではConversation HistoryとPersonal Memoryを異なる概念として扱う。

8.1 Conversation History

Conversation Historyは会話そのものを成立・継続させるための情報である。

例:

* Conversation
* Message
* User Message
* Assistant Message
* Conversation ID
* Message Order
* Timestamp

Phase 1ではConversation Historyをconversation Feature内で管理する。

conversation
    │
    ├── Conversation
    ├── Message
    └── Conversation History

Conversation HistoryのPersistenceにはDynamoDBを利用する。

⸻

8.2 Personal Memory

Personal MemoryはAliceがユーザーについて長期的に保持する情報である。

例:

* User Preference
* Profile Information
* Long-term Context
* User-specific Knowledge
* Remembered Facts

Personal MemoryはConversation Historyとは別Featureとして扱う。

Phase 1では実装しない。

Phase 2開始時に以下を追加する。

com.projectalice.backend
│
├── conversation/
│
└── memory/

⸻

8.3 Design Principle

以下を同一概念として扱わない。

Conversation History ≠ Personal Memory

また、

Memory ≠ DynamoDB

とする。

MemoryはAliceのCapability / Domain Conceptであり、DynamoDBはPersistence Technologyである。

⸻

9. Layer Model

各Feature内部では以下のLayerを基本とする。

Presentation
      │
      ▼
Application
      │
      ▼
Domain

Infrastructureは外部Technologyとの接続を担当する。

Infrastructure
      │
      ▼
Application / Domain Port

⸻

10. Presentation Layer

Purpose

外部Interfaceとの境界を担当する。

Phase 1では主にHTTP REST APIを担当する。

Responsibilities

* HTTP Request受付
* Request DTOのValidation
* Request DTOからApplication Inputへの変換
* Use Case呼び出し
* Application OutputからResponse DTOへの変換
* HTTP Status決定
* API Error Response

Examples

ConversationController
SendMessageRequest
SendMessageResponse

Non-Responsibilities

Presentation Layerでは以下を行わない。

* AliceのBusiness Logic
* Prompt生成
* OpenAI API直接呼び出し
* DynamoDB直接アクセス
* Conversation History保存ロジック
* Tool選択
* Domain Ruleの実装

Controllerを巨大なBusiness Logic Classにしない。

⸻

11. Application Layer

Purpose

Alice BackendのUse Caseを実行する。

Application LayerはFeature内の処理フローを制御する。

Responsibilities

* Use Case実行
* Domain Objectの利用
* AI Provider Port呼び出し
* Repository Port呼び出し
* 処理順序の制御
* Transaction / Consistency Boundaryの制御
* Application Level Validation

Phase 1 Example

代表的なUse Case:

SendMessageUseCase

概念的な処理フロー:

SendMessageUseCase
        │
        ├── Conversation History取得
        │
        ├── AI Request Context構築
        │
        ├── AI Provider呼び出し
        │
        ├── User Message保存
        │
        ├── Assistant Message保存
        │
        └── Response返却

実際の保存順序、Failure時の整合性制御等はDatabase / API / Backend詳細設計で具体化する。

Non-Responsibilities

Application Layerは以下へ直接依存しない。

OpenAI SDK
AWS SDK
DynamoDB Client
HTTP Clientの具体実装

外部Technologyとの通信にはPortを利用する。

⸻

12. Domain Layer

Purpose

AliceのBusiness ConceptおよびBusiness Ruleを表現する。

Domain Layerは最もInfrastructureから独立したLayerとする。

Responsibilities

* Domain Entity
* Value Object
* Domain Rule
* Domain Validation
* Domain Serviceが必要な場合のBusiness Logic

Phase 1では過剰なDomain Modelを作らない。

実際にBusiness Ruleを持つConceptのみDomainとして表現する。

Candidate Domain Concepts

Phase 1では以下が候補となる。

Conversation
Message
MessageRole

最終的なDomain ModelのProperty等はDatabase DesignおよびAPI Designと合わせて確定する。

Dependency Restrictions

Domain Layerから以下へ依存しない。

Spring Web
OpenAI SDK
AWS SDK
DynamoDB SDK
HTTP
Database
Controller

Domain ModelへInfrastructure固有AnnotationやSDK Objectを持ち込まないことを原則とする。

⸻

13. Infrastructure Layer

Purpose

外部Technologyおよび外部Serviceとの接続を担当する。

Phase 1 Responsibilities

* OpenAI API Integration
* DynamoDB Integration
* External SDK Configuration
* Port Implementation
* External Data Mapping

Examples

概念的には以下のAdapterを配置する。

OpenAiAiProvider
DynamoDbConversationRepository

名称は実装時のConventionに合わせて調整可能だが、責務は維持する。

Principle

InfrastructureはAlice Coreが定義するPortを実装する。

Alice Core
     │
     ▼
Port
     ▲
     │ implements
Infrastructure Adapter

Infrastructure固有ModelをApplication / Domainへ漏らさない。

⸻

14. Dependency Rule

Project Alice Backendでは依存方向を明確に制限する。

基本方向:

Presentation
      │
      ▼
Application
      │
      ▼
Domain

InfrastructureはApplication / Domain側で定義されたPortへ依存する。

Infrastructure
      │
      ▼
Port

重要なのは、Alice Core側からInfrastructureの具体実装へ依存しないことである。

⸻

15. Forbidden Dependencies

以下の依存を禁止する。

Domain ─X→ Presentation
Domain ─X→ Infrastructure
Domain ─X→ OpenAI SDK
Domain ─X→ AWS SDK
Domain ─X→ DynamoDB SDK
Application ─X→ Controller
Application ─X→ OpenAI SDK
Application ─X→ DynamoDB SDK
Presentation ─X→ DynamoDB
Presentation ─X→ OpenAI API

外部Technologyの変更によってApplication / Domainが直接変更される構造を避ける。

⸻

16. Alice Core

Alice Coreは特定のJava Package名そのものではなく、Alice固有の判断・Use Case・Domain Logicを表すArchitecture上の概念である。

Backend内部では主に以下がAlice Coreを構成する。

Application Layer
+
Domain Layer

必要に応じてFeature間のApplication CoordinationもAlice Coreに含まれる。

Alice Coreへ以下を直接持ち込まない。

* OpenAI固有実装
* DynamoDB固有実装
* AWS SDK固有実装
* HTTP Transport固有実装
* Flutter固有実装

⸻

17. AI Provider Boundary

Alice CoreからOpenAI APIを直接呼び出さない。

概念構造:

SendMessageUseCase
        │
        ▼
AiProvider
        ▲
        │ implements
OpenAiAiProvider
        │
        ▼
OpenAI API

AiProviderはAlice Core側から利用するPortとする。

具体的なOpenAI API Client、SDK、HTTP通信方式等はInfrastructure側へ隔離する。

⸻

18. AI Provider Responsibilities

AI Provider Boundaryでは概念的に以下を扱う。

Input:

AI Request Context

Output:

AI Response

Application LayerがOpenAI固有Request / Response Objectを直接扱わない構造とする。

例えば以下のような依存は禁止する。

SendMessageUseCase
        │
        ▼
OpenAI SDK Request Object

代わりに、

SendMessageUseCase
        │
        ▼
Alice Internal Model
        │
        ▼
AiProvider

とする。

具体的なAI Model、Prompt、Context構築方式等はai-design.mdで定義する。

⸻

19. AiProvider Ownership Policy

Phase 1 Ownership

Phase 1では、AiProvider Portはconversation Featureが所有する。

概念的な配置は以下を基本候補とする。

conversation/
└── application/
    └── port/
        └── AiProvider

Phase 1におけるAI利用は、Conversation FeatureのSendMessage等のUse Caseから利用されることを前提とする。

そのため、現時点ではAI利用のためだけに独立したai Featureまたは共通AI Moduleを作成しない。

⸻

Reason

Phase 1ではAI Providerを利用する主要Use CaseがConversation Featureに限定されている。

この段階で、

ai/
shared/ai/
common/ai/

等の共通Moduleを先行して作成すると、将来利用される可能性だけを理由とした過剰な抽象化となる。

Project Aliceでは、必要になるまで共通化しない方針を維持する。

⸻

Future Re-evaluation Condition

将来、Conversation以外の複数FeatureがAI Providerを直接利用する必要が発生した場合、AiProviderの所有Boundaryを再設計する。

再検討の対象例:

conversation
    └── AiProvider利用
memory
    └── AiProvider利用
engineering
    └── AiProvider利用
agent
    └── AiProvider利用

この状態になった場合、

memory.application
        │
        ▼
conversation.application.port.AiProvider

のように、別Featureがconversation Feature内部のPortへ依存する構造は採用しない。

⸻

Promotion Criteria

以下のいずれかが発生した場合、AiProviderの所有Boundary再設計を検討する。

* 2つ以上の独立FeatureがAiProviderを直接利用する
* Conversation Featureに属さないAI Use Caseが追加される
* Feature間でAI Provider契約を共有する必要が発生する
* conversation Featureへの不自然な依存が発生する

単に「将来使うかもしれない」という理由だけでは再設計しない。

⸻

Future Candidate Structure

必要性が発生した場合、例えば以下のような独立Boundaryを候補とする。

com.projectalice.backend
│
├── conversation/
│
├── memory/
│
├── ai/
│   ├── application/
│   │   └── port/
│   │       └── AiProvider
│   │
│   └── infrastructure/
│       └── OpenAiProvider
│
└── ...

ただし、上記構造を現時点で正式採用するものではない。

具体的なFeature BoundaryおよびPackage構造は、実際に複数FeatureからAI利用が必要になった時点で再設計する。

⸻

ADR Requirement

AiProviderの所有Boundaryをconversation Featureから別Module / Featureへ移動する場合は、Architectureに影響する変更として扱う。

変更前に、

* 利用Feature
* Dependency Direction
* Module Boundary
* Infrastructure配置
* 既存Use Caseへの影響

を確認し、必要に応じてADRとしてDecisionを記録する。

⸻

Implementation Rule

Phase 1では以下を基本とする。

SendMessageUseCase
        │
        ▼
conversation.application.port.AiProvider
        ▲
        │ implements
OpenAI Infrastructure Adapter

AI Coding Assistantは、将来利用を理由として独自にai Feature、shared-ai Module、共通AI Layer等を追加してはならない。

複数Featureからの共通利用が実際に必要になった場合のみ、設計書およびADRを更新した上でBoundaryを変更する。

⸻

20. Conversation Repository Boundary

Conversation HistoryのPersistenceについてもDynamoDBへ直接依存させない。

概念構造:

SendMessageUseCase
        │
        ▼
ConversationRepository
        ▲
        │ implements
DynamoDbConversationRepository
        │
        ▼
DynamoDB

ConversationRepositoryはAlice Core側から利用するPortとする。

DynamoDB SDK ObjectをApplication / Domainへ公開しない。

⸻

21. Port Placement

Portは、それを必要とするCore側に所有させる。

例えば、

conversation/
└── application/
    └── port/
        ├── AiProvider.java
        └── ConversationRepository.java

のような配置を基本候補とする。

PortをInfrastructure側に定義しない。

理由は、Alice Coreが必要とする契約をAlice Core自身が定義し、Infrastructureがその契約へ従う構造にするためである。

ただし、Portが明確なDomain ConceptでありDomain Layerに属する方が自然な場合は、Backend Designとの整合を保った上でDomain側への配置を許容する。

具体的なClass単位の配置は実装設計時に決定する。

⸻

22. DTO and Domain Model Separation

API DTOとDomain Modelを原則として分離する。

HTTP Request
     │
     ▼
Request DTO
     │
     ▼
Application / Domain
     │
     ▼
Response DTO
     │
     ▼
HTTP Response

以下のようにAPI ModelをDomain Modelとしてそのまま使用する構造を避ける。

Request DTO = Domain Entity

理由:

* API変更をDomainへ直接波及させない
* Domain変更をAPIへ直接波及させない
* Validation責務を分離する
* 将来的なVoice / Agent等の別Interface追加へ対応しやすくする

ただし、単純な値のためだけに意味のないMapping Classを大量生成しない。

⸻

23. Infrastructure Model Separation

DynamoDB Entity / Item RepresentationとDomain Modelも原則として分離する。

概念:

DynamoDB Item
      │
      ▼
Infrastructure Mapper
      │
      ▼
Domain / Application Model

これによりDynamoDB固有のKey StructureやAttribute DesignをAlice Coreへ漏らさない。

具体的なPersistence Modelはdatabase-design.mdで定義する。

⸻

24. Spring Framework Dependency Policy

Spring BootはApplication Frameworkとして利用するが、すべてのLayerをSpringへ強く依存させない。

Presentation / InfrastructureではSpring Frameworkを積極的に利用してよい。

例:

* REST Controller
* Configuration
* Dependency Injection
* Validation
* HTTP Client
* Exception Handler

Application LayerではDependency Injection等のために必要最小限のSpring利用を許容する。

Domain Layerは可能な限りPlain Javaとして維持する。

Domain Objectへ不要なSpring Annotationを付与しない。

⸻

25. Dependency Injection

Object間の依存関係はConstructor Injectionを基本とする。

Field Injectionは原則使用しない。

推奨:

Constructor Injection

非推奨:

@Autowired
private SomeService service;

理由:

* Dependencyが明示される
* Unit Testしやすい
* Immutableな構造を作りやすい
* 必要Dependencyを把握しやすい
* AIによる生成コードの一貫性を保ちやすい

Spring Componentの具体的なAnnotation方針は実装設計時に定義する。

⸻

26. Configuration

Application ConfigurationとSecretを分離する。

Backendでは以下のようなConfigurationが必要になる。

候補:

* OpenAI API Configuration
* DynamoDB Configuration
* AWS Region
* DynamoDB Endpoint
* DynamoDB Table Name
* Backend Port
* AI Model Configuration

Environment固有値をSource CodeへHard Codingしない。

Configurationの管理方針はdevelopment-environment.mdおよびsecurity-design.mdに従う。

⸻

27. Exception Handling

Backendでは例外処理をLayerごとに無秩序に実装しない。

基本方針:

Infrastructure Exception
        │
        ▼
Application / Domain Error
        │
        ▼
Presentation Error Mapping
        │
        ▼
HTTP Error Response

PresentationではGlobal Exception Handlingを利用することを基本候補とする。

例えばSpring MVCの場合、@RestControllerAdvice等による集中管理を検討する。

ただし具体的なError Code、Response Format、HTTP Status Mappingはapi-design.mdで定義する。

⸻

28. External Exception Isolation

OpenAI SDKやAWS SDK等が返すExceptionをそのままControllerまで伝播させない。

禁止例:

OpenAI SDK Exception
        │
        └────────────→ HTTP Response

外部Technology固有ExceptionはInfrastructure Boundaryで変換する。

これにより外部Provider変更によってAPI Error Contractが直接変化することを防ぐ。

⸻

29. Logging

BackendではApplicationの動作確認および障害調査に必要なLoggingを行う。

最低限以下を考慮する。

* Request処理開始 / 終了
* Use Case失敗
* External API失敗
* DynamoDB Access失敗
* Unexpected Error

以下をLogへ出力しない。

* OpenAI API Key
* AWS Credential
* Authentication Token
* その他Secret

Conversation ContentやAI Request / Responseには個人情報が含まれる可能性がある。

そのため全文Loggingを無条件に行わない。

詳細なSecurity / Logging Policyはsecurity-design.mdで定義する。

⸻

30. Validation

Validationは責務に応じて配置する。

Presentation Validation

例:

* Required Field
* String Length
* Request Format

Application Validation

例:

* Use Case実行条件
* Application Level Constraint

Domain Validation

例:

* Domain Rule
* Value Object Constraint

同じValidation Logicを複数Layerへ無意味に重複させない。

⸻

31. Mapping

Layer間のModel変換を明示する。

主なMapping:

Request DTO
    ↓
Application Input
Application / Domain
    ↓
Response DTO
DynamoDB Model
    ↕
Domain / Application Model
AI Internal Model
    ↕
OpenAI Model

Mapping処理が単純な場合、専用Mapper Classを必ず作成する必要はない。

Mappingが複雑化または複数箇所で再利用される場合にMapperを導入する。

過剰なMapper Layerを先行作成しない。

⸻

32. Shared Package

sharedはFeatureに属さない共通要素を配置するために利用する。

ただしsharedを何でも置く場所にしない。

配置候補:

* 共通Error Model
* Feature非依存のTechnical Utility
* 共通Configuration
* Cross-cutting Concern

以下のようなFeature固有Business Logicをsharedへ配置しない。

Conversation Logic
Memory Logic
Tool Logic

Feature固有Classは対象Feature内へ配置する。

⸻

33. Feature-to-Feature Dependency

将来的に複数Featureが追加された場合、一つのFeatureが別FeatureのInfrastructure実装へ直接依存しない。

禁止例:

memory.application
        │
        ▼
conversation.infrastructure

Feature間連携が必要な場合はApplication Boundaryまたは明示的なPort / Interfaceを利用する。

Feature同士を密結合させない。

具体的なFeature Integration Patternは必要になったPhaseで設計する。

⸻

34. Phase 1 Request Flow

Phase 1の基本Request Flowは以下とする。

Flutter Client
      │
      │ HTTP
      ▼
ConversationController
      │
      ▼
SendMessageUseCase
      │
      ├───────────────┐
      │               │
      ▼               ▼
Conversation       AiProvider
Repository             │
      │                 ▼
      ▼             OpenAI Adapter
DynamoDB Adapter       │
      │                 ▼
      ▼             OpenAI API
DynamoDB

Application Layerが全体のUse Case Flowを制御する。

Controller、OpenAI Adapter、DynamoDB AdapterへBusiness Flowを分散させない。

⸻

35. Phase 1 Conversation Flow

概念的な会話処理は以下とする。

1. FlutterからMessage受信
        ↓
2. Presentation Validation
        ↓
3. SendMessageUseCase実行
        ↓
4. Conversation History取得
        ↓
5. AI Request Context構築
        ↓
6. AiProvider呼び出し
        ↓
7. OpenAI API呼び出し
        ↓
8. AI Response取得
        ↓
9. Conversation History更新
        ↓
10. Response DTO生成
        ↓
11. FlutterへResponse

具体的なHistory取得件数、Context Window、Prompt構築、保存順序等はai-design.mdおよびdatabase-design.mdで決定する。

⸻

36. Phase 1 Scope

Phase 1 Backendでは以下を実装対象とする。

* REST API
* Conversation Feature
* Alice Coreの基本Use Case
* AI Provider Port
* OpenAI Provider Adapter
* Conversation Repository Port
* DynamoDB Conversation Repository Adapter
* Conversation History
* Configuration
* Validation
* Error Handling
* Logging
* Unit Test
* 必要なIntegration Test

⸻

37. Phase 1 Non-Scope

以下はPhase 1では実装しない。

* Personal Memory Feature
* RAG
* Vector Search
* GitHub Integration
* AWS操作Tool
* PC Agent
* Browser Automation
* Voice
* Autonomous Agent
* Complex Async Processing
* 将来用の空Feature
* 将来用の不要なInterface / Adapter

将来必要になる可能性だけを理由として先行実装しない。

⸻

38. Phase 1 Use Cases

Phase 1では、RequirementsおよびMVPで定義された以下の成功条件を満たす必要がある。

* ユーザーとAliceがテキストで会話できる
* Aliceとして一貫した回答ができる
* Conversation Historyを保存できる
* 保存したConversation Historyを後から確認できる

これらをBackend Use Caseへ対応付ける。

Required Use Cases

SendMessage

ユーザーがAliceへMessageを送信し、Aliceから回答を取得する。

主な責務:

* Conversationの特定または生成
* Conversation History取得
* AI Request Context構築
* AI Provider呼び出し
* Alice Response生成
* Conversation History永続化
* Response返却

概念的なUse Case名:

SendMessageUseCase

⸻

GetConversation

指定されたConversationの基本情報を取得する。

目的:

* 過去Conversationの表示
* Conversation詳細画面等で利用する基本情報の取得

取得対象の具体的なFieldはapi-design.mdおよびdatabase-design.mdで定義する。

概念的なUse Case名:

GetConversationUseCase

⸻

GetConversationMessages

指定されたConversationに属するMessage Historyを取得する。

目的:

Requirementsで定義された、

保存した会話履歴を後から参照できること

を満たす。

主な責務:

* Conversationの特定
* Conversation Repositoryを通じたMessage History取得
* Message順序の維持
* Application Output生成

概念的なUse Case名:

GetConversationMessagesUseCase

Pagination、取得件数、Sort Order等の詳細はapi-design.mdおよびdatabase-design.mdで決定する。

⸻

Optional Use Case

ListConversations

ユーザーが過去のConversation一覧を確認する必要がある場合に利用する。

概念的なUse Case名:

ListConversationsUseCase

Phase 1でConversation一覧UIを提供する場合は実装対象とする。

具体的な必要性はFrontend / API Designで確定する。

⸻

Conversation Creation

Conversationを独立したUse Caseとして作成するかどうかは現時点では確定しない。

候補:

Option A
CreateConversation
        ↓
SendMessage

または、

Option B
SendMessage
    ↓
Conversationが存在しなければ生成

Phase 1では不要なAPI / Use Caseを増やさないことを優先し、具体方式はapi-design.mdで決定する。

⸻

Requirements Traceability

Phase 1 RequirementとBackend Use Caseの対応は以下とする。

Requirement / Success Condition	Backend Use Case
テキストでAliceと会話できる	SendMessage
AI Providerを通してLLMを利用できる	SendMessage
Alice人格を反映した回答ができる	SendMessage
Conversation Historyを保存できる	SendMessage
保存したConversationを取得できる	GetConversation
保存したMessage Historyを参照できる	GetConversationMessages
Conversation一覧を表示する	ListConversations（必要な場合）

Backend実装開始時には、Phase 1 Requirementが少なくとも一つのUse Caseまたは明示的なBackend責務へ対応していることを確認する。

⸻

39. Phase 2 Evolution

Phase 2ではPersonal Memoryを追加する。

概念:

com.projectalice.backend
│
├── conversation/
│
├── memory/
│   ├── presentation/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
└── shared/

Conversation Historyは引き続きconversation Featureで管理する。

Personal Memoryのみmemory Featureへ配置する。

conversation
→ 会話履歴
memory
→ Aliceの長期記憶

両者を混同しない。

⸻

40. Future Evolution

Project Aliceの成長に合わせて必要なFeatureを追加する。

概念例:

com.projectalice.backend
│
├── conversation/
├── memory/
├── tool/
├── github/
├── aws/
├── agent/
└── shared/

ただしFeature名やBoundaryは実際のRequirementに基づいて決定する。

将来のArchitectureを予測してPhase 1で全Packageを作成しない。

⸻

41. Testing Considerations

Architecture上、Application / DomainをInfrastructureから分離することでUnit Testを容易にする。

例えばSendMessageUseCaseのUnit Testでは、実際のOpenAI APIやDynamoDBへ接続せず、

Fake / Mock AiProvider
Fake / Mock ConversationRepository

を利用可能な構造とする。

これにより、

SendMessageUseCase
        │
        ├── Fake AiProvider
        └── Fake ConversationRepository

としてUse Case単体を検証できる。

具体的なTest Strategyはtest-design.mdで定義する。

⸻

42. AI Implementation Rules

AI Coding AssistantによるBackend実装では、本ドキュメントをSource of Truthとして扱う。

AIは以下を独自判断で変更しない。

* Feature Boundary
* Layer Boundary
* Dependency Direction
* AI Provider Boundary
* Repository Boundary
* Conversation History / Personal Memoryの境界
* Technology Stack
* Package Architecture
* Phase Scope

AIは以下のような局所的な実装詳細について、本設計と矛盾しない範囲で合理的に判断してよい。

* Variable Name
* Method Name
* private method分割
* 小規模なMapping実装
* 標準的なJava記述
* LocalなRefactoring

重要な設計判断が必要になった場合は実装を先行せず、設計書またはADRを更新する。

⸻

43. Prohibited Implementation Patterns

Project Alice Backendでは以下を禁止する。

ControllerからOpenAI直接呼び出し

Controller
    └── OpenAI SDK

ControllerからDynamoDB直接アクセス

Controller
    └── DynamoDB Client

ApplicationからOpenAI SDK直接利用

UseCase
    └── OpenAI SDK

ApplicationからDynamoDB SDK直接利用

UseCase
    └── DynamoDB Client

DomainへのInfrastructure Annotation混入

Domain
    └── DynamoDB固有Annotation

Conversation HistoryとPersonal Memoryの混同

memory/
    └── Phase 1 Conversation History

将来用Architectureの先行実装

Phase 1
memory/
tool/
github/
aws/
agent/
voice/

必要になっていないFeatureを先に作らない。

⸻

44. Open Decisions

本ドキュメントでは以下の詳細を確定しない。

* API Endpoint
* API Request / Response Schema
* API Error Format
* Conversation Domain Model詳細
* DynamoDB Access Pattern
* DynamoDB Table / Key Design
* AI Model
* System Prompt
* Context Window Strategy
* Conversation History取得件数
* Message保存順序・Failure時の整合性戦略
* OpenAI Client / SDKの具体的選定
* AWS SDKの具体的Configuration
* Logging Format
* Correlation ID方式
* Cloud / Remote Access開始時のAuthentication方式
* Spring Profile構成
* Package / Classの最終命名
* Integration Test Infrastructure

これらは関連するDesign Documentで決定する。

主な設計先:

API
→ api-design.md
DynamoDB
→ database-design.md
AI / Prompt / Context
→ ai-design.md
Security / Authentication
→ security-design.md
Development Environment
→ development-environment.md
Testing
→ test-design.md

Architectureへ影響する重要なDecisionはdecisions.mdへADRとして記録する。

⸻

45. Summary

Project Alice Backendでは、

Feature-based Package Structure + Layer Separation

を採用する。

基本構造:

Feature
│
├── presentation
├── application
├── domain
└── infrastructure

依存方向:

Presentation
      │
      ▼
Application
      │
      ▼
Domain

外部TechnologyはInfrastructureへ隔離する。

Alice Core
    │
    ├── AiProvider Port
    │       ▲
    │       │
    │   OpenAI Adapter
    │
    └── ConversationRepository Port
            ▲
            │
        DynamoDB Adapter

Phase 1ではconversation Featureを中心に実装する。

Phase 1
conversation
├── Presentation
├── Application
├── Domain
├── AI Provider Boundary
└── Conversation History

Conversation Historyはconversation Featureに含める。

Personal MemoryはPhase 1では実装せず、Phase 2で独立したmemory Featureとして追加する。

Conversation History ≠ Personal Memory

Alice CoreをOpenAI、DynamoDB、Spring固有Infrastructureから分離し、将来のProvider変更・機能追加・Testを容易にする。

同時に、Phase 1で不要な抽象化や将来用Featureを先行実装せず、必要なArchitectureだけを段階的に構築する。
