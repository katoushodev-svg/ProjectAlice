Alice Decision Records

Overview

本ドキュメントは、Project Aliceにおける設計・技術選定・方針決定の理由を記録する。

単なる結果ではなく、

* なぜその選択をしたか
* どんな選択肢があったか
* 将来的な影響

を残すことを目的とする。

⸻

ADR-001 Aliceの基本方針

Status

Accepted

Decision

Aliceは単なるチャットAIではなく、個人専用AIアシスタントとして設計する。

Context

一般的なAIサービスは、質問に対して回答することはできるが、ユーザー固有の価値観や過去の判断を継続的に理解することは難しい。

Reason

Aliceでは以下を重視する。

* ユーザー理解
* 長期記憶
* 個人最適化
* 継続的な成長

⸻

ADR-002 Memoryを中心とした設計

Status

Accepted

Decision

AliceではMemoryを重要な中核コンポーネントとして扱う。

Memoryと具体的なAIモデルおよび保存技術を分離する。

Context

AIモデル自体は将来的に変更される可能性がある。

また、Memoryの保存・検索技術についても将来的に変更・拡張される可能性がある。

しかし、

* ユーザー情報
* 判断基準
* 過去履歴
* プロジェクト情報

はAliceの価値そのものである。

Reason

以下を分離する。

AI Model
 ↓
推論能力
Memory
 ↓
ユーザー理解
Persistence
 ↓
Memoryの保存・検索

AIモデルや具体的な保存技術に依存しないMemory設計を目指す。

⸻

ADR-003 段階的開発

Status

Accepted

Decision

最初から全機能を実装しない。

Context

パーソナルAIは対象範囲が広く、最初から完成形を目指すと開発が進まない。

また、将来必要になる可能性だけを理由に機能やInfrastructureを導入すると、個人開発として維持できない複雑さになる可能性がある。

Reason

以下の順番で成長させる。

1. 会話
2. 記憶
3. 外部連携
4. 開発支援
5. 音声・PC操作
6. 自律化

各Phaseで必要な設計・実装のみ追加する。

⸻

ADR-004 基本技術スタック

Status

Accepted

Decision

Project Aliceの基本技術スタックとして以下を採用する。

Layer	Technology
Client	Flutter
Backend	Spring Boot / Java
AI	OpenAI API
Database	DynamoDB
Agent	Python
Browser Automation	Playwright
Cloud	AWS

ただし、Alice Coreはこれらの具体技術へ過度に依存しない構造とする。

Context

Aliceは長期開発プロジェクトであり、将来的にAI Provider、Database、外部サービス、実行環境などが変更される可能性がある。

一方で、技術選定を長期間未確定のままにすると設計・実装を開始できない。

そのため、現時点で基本技術スタックを確定しつつ、変更可能性の高い外部技術との依存をInterface / Portによって分離する。

Reason

技術選定では以下を重視する。

* 保守性
* 拡張性
* 開発者の既存技術経験
* 学習効果
* 運用コスト
* 長期運用可能性

採用技術を確定することと、特定技術へ密結合することは分けて考える。

Consequences

基本技術スタックを前提として設計・実装を進める。

ただし、AI Provider、Persistence、Tool等については可能な範囲で抽象化し、将来的な変更によるAlice Coreへの影響を限定する。

⸻

ADR-005 AI Provider分離

Status

Accepted

Decision

AIモデルへの直接依存を避け、AI Provider Interfaceを設ける。

初期ProviderとしてOpenAI APIを利用する。

Alice CoreはOpenAI固有のRequest / ResponseやSDKへ直接依存しない。

Context

将来的に、

* OpenAI
* Claude
* Gemini
* Local LLM
* その他AI Provider

など複数モデルを利用する可能性がある。

AIモデルはAliceの推論能力を提供するコンポーネントであり、Aliceそのものではない。

Reason

AIモデル変更による影響範囲を限定する。

また、Aliceの人格、Memory、Tool、Business Rule等を特定AI Providerから独立して維持できるようにする。

Consequences

AI Provider固有の以下の処理はInfrastructure側で吸収する。

* API通信
* Request変換
* Response変換
* Provider固有設定
* Provider固有エラー
* Timeout

Alice CoreではProvider共通のInterfaceのみを利用する。

⸻

ADR-006 Personal AIとしての方向性

Status

Accepted

Decision

Aliceは仕事だけではなく、生活全般を支援するAIを目指す。

Context

Aliceには以下の役割がある。

* 技術支援
* 生活支援
* 情報整理
* PC操作支援

Reason

ユーザーの日常全体を支援することで、より価値のあるAIになるため。

⸻

ADR-007 Alice CoreとInfrastructureの分離

Status

Accepted

Decision

Alice固有の判断ロジックをAlice Coreへ配置し、外部技術との通信・具体実装をInfrastructureへ分離する。

Alice Coreから以下へ直接依存しない。

* OpenAI
* DynamoDB
* AWS
* Playwright
* OS固有API
* 外部サービス固有SDK

Context

Aliceは将来的に多数の外部サービス、AI Provider、Memory技術、PC Agentと接続する。

Alice Coreがこれらへ直接依存すると、外部技術の変更がAlice全体へ影響する。

Reason

Alice固有ロジックを長期的に維持し、外部技術変更による影響範囲を限定するため。

Consequences

Alice Coreが必要とする外部能力はPort / Interfaceとして定義する。

Infrastructure側がそのInterfaceを実装する。

⸻

ADR-008 MemoryとPersistenceの分離

Status

Accepted

Decision

Alice Memoryと具体的な保存技術を別の概念として扱う。

DynamoDBはMemoryそのものではなく、Memoryを永続化する初期保存技術として利用する。

Context

Alice Memoryは将来的に以下へ拡張される可能性がある。

* DynamoDB
* Embedding
* Semantic Search
* Vector Store / Vector Database
* RAG
* Memory Ranking
* Memory Summarization

DynamoDBのみですべてのMemory検索要件を満たすことは前提としない。

特に高度な意味検索やRAGが必要となった場合、DynamoDBとは別の検索・ベクトル基盤を追加する可能性がある。

MemoryとDynamoDBを同一概念として設計すると、将来的なMemory高度化が具体的なDatabase設計へ強く依存する。

Reason

MemoryをAliceの長期資産として維持し、保存・検索技術の変更や追加を可能にするため。

Consequences

Alice CoreはDynamoDBやAWS SDKへ直接依存しない。

Memory / Repository Interfaceを介して保存・検索機能を利用する。

Conversation HistoryについてはConversation Repository Interface、
Personal MemoryについてはMemory Interfaceを介して
Persistence / Search機能を利用する。

将来、Semantic SearchやRAGが必要になった場合は、要件を整理した上で検索・ベクトル基盤を別途設計し、必要に応じて新しいADRを作成する。

⸻

ADR-009 ToolとPC Agentの分離

Status

Accepted

Decision

ToolとPC Agentを別の概念として扱う。

ToolはAliceが利用可能な外部能力を表す。

PC AgentはローカルPC操作を必要とするToolの実行基盤の一つとして扱う。

Context

Aliceでは将来的に以下のToolを利用する。

* GitHub
* Calendar
* AWS
* Web
* Weather
* Restaurant
* PC Control
* Browser Control

これらすべてがPC Agentを必要とするわけではない。

例えばGitHub APIやCalendar API、AWS APIはBackendから直接利用可能である一方、OS操作やローカルブラウザ操作はPC Agentを必要とする。

Reason

すべてのToolをPC Agent経由にすると、不必要な通信経路や依存関係が発生する。

Toolと実行環境を分離することで、Toolごとに適切な実行方式を選択可能にする。

Consequences

外部APIを利用するToolはBackend Infrastructureとして実装可能とする。

ローカルPC操作が必要なToolについてはPython PC Agentを利用する。

Alice CoreはToolの具体的な実行環境を認識しない。

⸻

ADR-010 Phase 1の同期通信方針

Status

Accepted

Decision

Phase 1では同期Request / Response方式を基本とする。

長時間処理用の非同期実行InfrastructureはPhase 1では導入しない。

Context

Phase 1の主要処理は、

* テキストチャット
* AI API連携
* 会話履歴保存
* 会話履歴参照

であり、初期段階からMessage QueueやJob Queueを導入する必要性は低い。

一方、将来的なAgent機能では長時間処理が必要になる可能性がある。

Reason

現時点で不要なInfrastructureを導入せず、個人開発として管理可能な複雑さに抑えるため。

Consequences

Phase 1ではREST APIによる同期処理を基本とする。

将来的に以下のような処理が必要となった段階で非同期実行方式を再検討する。

* 大規模Repository解析
* 複数Toolを利用するAgent Task
* 長時間Browser Automation
* バックグラウンド情報収集

具体方式は必要になった時点で別途ADRとして決定する。

⸻

ADR-011 DynamoDBを初期Persistenceとして採用

Status

Accepted

Decision

Project Aliceの初期PersistenceとしてAWS DynamoDBを採用する。

Phase 1では主にConversationおよびMessage等の会話履歴保存に利用する。

Context

Project Aliceでは、Phase 1から会話履歴を保存する必要がある。

初期Persistenceについては、以下の観点を重視する。

* 個人開発としての運用負荷
* AWSとの親和性
* 将来的なCloud展開
* スケーラビリティ
* 実装・運用経験
* 初期コスト

DynamoDBはAWS上でマネージドに利用でき、サーバー管理が不要である。

また、Project Aliceでは将来的にAWSをCloud基盤として利用する方針であるため、初期Persistenceとして採用する。

Alternatives

初期Database候補として以下を検討した。

PostgreSQL

メリット:

* RDBとして一般的
* SQLを利用可能
* Relationを扱いやすい
* Vector Extension等への拡張可能性

デメリット:

* DB Instance管理が必要になる可能性がある
* Aliceの初期要件に対して運用構成が重くなる可能性がある

Firestore

メリット:

* Serverless
* 開発が容易
* Firebaseとの連携が容易

デメリット:

* Project AliceのCloud方針であるAWSとはCloud Platformが分かれる
* AWSサービスとの統合を考えるとDynamoDBの方が自然

DynamoDB

メリット:

* Serverless / Fully Managed
* AWSとの親和性が高い
* 高い可用性
* スケール管理が容易
* 初期運用負荷を抑えやすい

デメリット:

* RDBとはデータ設計思想が異なる
* Access Patternを意識したTable設計が必要
* Ad-hocな複雑検索には向かない
* Semantic SearchやVector Search用途には別基盤が必要になる可能性が高い

Reason

Phase 1では主にConversation履歴を保存するため、RDBの複雑なRelationや高度な検索機能は必須ではない。

また、AWSをCloud基盤として利用するProject Aliceにおいて、初期運用負荷を抑えながら利用できる点を評価しDynamoDBを採用する。

Consequences

DynamoDBのTable設計はRDBのEntity設計とは異なり、Access Patternを前提として設計する。

そのため、Database設計時には以下を事前に定義する。

* 取得したいデータ
* Queryパターン
* Partition Key
* Sort Key
* Secondary Index

将来、Semantic SearchやRAG等が必要となった場合でも、DynamoDBへ無理にすべての責務を持たせない。

必要に応じて別の検索・ベクトル基盤を追加する。

⸻

ADR-012 AIによる実装を前提とした設計優先方針

Status

Accepted

Decision

Project Aliceでは、実装コードの多くをAIによって生成する方針とする。

そのため、実装開始前に設計書を十分に作り込み、AIが設計書から一貫した実装判断を行える状態を作ることを重視する。

Context

AIによるコード生成では、設計上の曖昧さがあると以下の問題が発生しやすい。

* 責務配置の不統一
* Layer間の依存方向の逆転
* AIごとに異なる実装方式
* 不要なFramework / Library導入
* 既存設計との不整合
* 将来的な保守性低下

Project Aliceは長期運用を前提としているため、単に動作するコードではなく、一貫した設計に基づくコードを生成する必要がある。

Reason

設計書をAI実装のSource of Truthとして利用することで、生成コードの品質と一貫性を高めるため。

Consequences

Phase 0ではコード実装より設計書整備を優先する。

少なくとも以下の設計情報を実装前に明確化する。

* Architecture
* Module / Repository Structure
* Backend Design
* API Design
* Database Design
* AI Design
* Security Design
* Test Design

AIへ実装を依頼する際は、関連設計書を前提条件として明示する。

設計と実装が矛盾する場合は、実装を設計へ合わせることを基本とし、設計変更が必要な場合は先に設計書およびADRを更新する。

⸻

ADR-013: Backend Runtime / Framework / Build Toolの選定

Status

Accepted

⸻

Context

Project Alice Phase 1では、Backend ApplicationをSpring Boot / Javaで実装する。

Backendの設計およびAIによる実装を開始するにあたり、Development Environmentの再現性を確保し、実装時の技術選択を統一するため、以下を正式に決定する必要がある。

* Java / JDK Version
* Spring Boot Version
* Backend Build Tool
* Build Tool Version
* Build Execution方式

Project Aliceは短期的なPrototypeではなく、長期的に保守・拡張する個人向けAI Assistantを目標としている。

また、実装コードの多くをGitHub Copilot等のAI Coding Assistantによって生成する方針である。

そのため、Backend技術選定では以下を重視する。

* 長期保守性
* Versionの明確性
* Build再現性
* Spring Bootとの互換性
* AIが一貫した構成で実装できること
* 開発者環境への依存を最小化すること

⸻

Decision

Project Alice Backendでは以下を正式採用する。

Item	Decision
Java / JDK	25
Spring Boot	4.1.0
Backend Build Tool	Maven
Maven Version	3.9.16
Build Execution	Maven Wrapper

Backend実装、Local Development、Test、Buildでは原則としてこの構成を使用する。

AIおよび開発者が個別の実装判断によって異なるJava Version、Spring Boot Version、Build Toolへ変更してはならない。

変更が必要になった場合は、互換性および影響範囲を確認した上で本ADRまたは新規ADRによってDecisionを更新する。

⸻

Java / JDK

Java / JDKはVersion 25を採用する。

Project Aliceは新規開発であり、既存のLegacy Applicationや過去のJava Runtimeとの互換性を維持する必要がない。

そのため、Backend開発開始時点で採用するJava Versionを25へ統一する。

Backend Source Code、Build、Test、Local DevelopmentはJDK 25を基準とする。

⸻

Spring Boot

Spring BootはVersion 4.1.0を採用する。

Project Alice Backendは新規Applicationとして構築するため、旧世代のSpring Bootを前提としたCompatibilityを維持する必要はない。

Backend Designおよび実装ではSpring Boot 4.1.0を基準とする。

AIがSpring Boot 3.x等の異なるMajor Versionを前提としてDependencyやConfigurationを生成しないよう、本VersionをDevelopment Environment上の固定値として扱う。

⸻

Backend Build Tool

Backend Build ToolとしてMavenを採用する。

候補としてMavenおよびGradleを検討した。

Project AliceではBuild Configurationの柔軟性よりも、

* Configurationの明示性
* Build Structureの理解しやすさ
* Dependency管理の一貫性
* AIによる生成結果の安定性
* 長期的な保守容易性

を優先する。

Mavenではpom.xmlを中心としてBuildおよびDependencyを管理でき、Project固有の複雑なBuild Logicを導入せずにSpring Boot Applicationを構築できる。

そのためProject Alice BackendではMavenを採用する。

⸻

Maven Version

Maven Versionは3.9.16を採用する。

Backend Build Environmentでは本Versionを基準とする。

ただし、Developer MachineへインストールされたGlobal Mavenを直接Buildの前提とはしない。

Build ExecutionにはMaven Wrapperを利用する。

⸻

Maven Wrapper

Project Alice BackendではMaven WrapperをRepositoryへ含める。

想定構成:

backend/
├── .mvn/
├── mvnw
├── mvnw.cmd
└── pom.xml

BuildおよびTestは原則としてMaven Wrapper経由で実行する。

macOS / Linux:

./mvnw

Windows:

mvnw.cmd

これによりDeveloper MachineへインストールされているMaven Versionへの依存を減らし、開発者、CI/CD、AIが同じBuild Environmentを利用できる状態を目指す。

⸻

Alternatives Considered

Java / JDK 21

Java 21も候補とした。

Java 21は成熟したLTS Versionであり、既存Libraryや既存SystemとのCompatibilityを重視する場合には有力な選択肢となる。

一方、Project Aliceは新規開発であり、既存SystemとのJava Version Compatibilityを維持する必要がない。

そのため、Backend開発開始時点で採用するVersionとしてJDK 25を選択した。

⸻

Spring Boot 3.x

Spring Boot 3.xも候補となる。

既存ApplicationからのMigrationや既存LibraryとのCompatibilityを優先する場合には有力である。

Project Aliceでは新規ApplicationとしてBackendを構築するため、Spring Boot 3.xとのCompatibilityを維持する必要がない。

そのためSpring Boot 4.1.0を採用した。

⸻

Gradle

Backend Build ToolとしてGradleも候補とした。

Gradleには以下の利点がある。

* 柔軟なBuild Configuration
* Custom Taskを構築しやすい
* 大規模Buildへの拡張性

一方、その柔軟性によってBuild Configurationの実装方法に複数の選択肢が生まれやすい。

Project AliceではAIによる実装を基本とするため、Build Configurationの自由度よりも一貫性と明示性を優先する。

そのためMavenを採用した。

⸻

Global Mavenのみを利用する方式

Developer MachineへMaven 3.9.16を直接Installし、Global Mavenのみを利用する方式も候補となる。

この方式では環境構築は単純になる一方、Developer Machine、CI/CD等で異なるMaven Versionが利用される可能性がある。

Project AliceではBuild Reproducibilityを重視するため、Maven Wrapperを採用する。

⸻

Consequences

Positive

* Backend Runtime Versionが統一される
* Spring Boot Versionが統一される
* AIが異なるFramework Versionを前提として実装するリスクを減らせる
* pom.xmlを中心としてDependencyを管理できる
* Maven WrapperによりBuild Environmentを再現しやすい
* Developer MachineのGlobal Maven Versionへの依存を減らせる
* Local / CI/CD間のBuild差異を減らせる
* Backend Designの前提条件が明確になる

Negative

* JDKまたはSpring BootのVersion変更時にはCompatibility確認が必要になる
* Spring Boot 4.xに対応していないLibraryを利用する場合、Library選定またはVersionの再検討が必要になる可能性がある
* MavenのBuild ConfigurationはGradleと比較して冗長になる場合がある
* Maven Wrapper関連FileをRepositoryで管理する必要がある

⸻

Implementation Rules

Backend実装では以下を固定値として扱う。

Java / JDK Version : 25
Spring Boot Version: 4.1.0
Build Tool         : Maven
Maven Version      : 3.9.16
Build Execution    : Maven Wrapper

AIによる実装でも同じVersionを使用する。

AIが独自判断で以下を行ってはならない。

* Java Version変更
* Spring Boot Version変更
* MavenからGradleへの変更
* Maven Version変更
* Maven Wrapperを使用しないBuild方式への変更

変更が必要な場合は実装を先行せず、設計およびADRを更新する。

⸻

Related Documents

* alice-architecture.md
* repository-structure.md
* development-environment.md
* backend-design.md
* decisions.md

⸻

Result

Project Alice Backendの標準技術構成を以下として確定する。

Java 25
   │
   ▼
Spring Boot 4.1.0
   │
   ▼
Maven 3.9.16
   │
   ▼
Maven Wrapper

以降のBackend DesignおよびBackend Implementationは、本ADRを前提として進める。

⸻

ADR-014 Phase 0設計範囲とAI Capability Boundaryの変更

Status

Accepted

⸻

Context

従来のPhase 0では、Project Alice全体のArchitectureを考慮しつつ、主にPhase 1 Conversation MVPの実装開始に必要な詳細設計を完成させる方針としていた。

またPhase 1ではAIを直接利用するFeatureがconversationだけであったため、AI Provider Portをconversation Featureが所有し、複数FeatureからAI利用が必要になった時点でBoundaryを再検討する方針としていた。

その後、Phase 0をProject Alice全体の設計フェーズへ拡張し、次をPhase 0の設計対象とする方針へ変更した。

* Phase 1: Conversation
* Phase 2: Personal Memory
* Phase 3: Tools / External Services
* Phase 4: Agent / PC操作 / Browser操作 / Voice

Phase 2〜4のArchitectureを具体化した結果、conversation、memory、toolおよびagent等の複数Featureが、それぞれ独立したAI Capabilityを必要とすることが設計上確定した。

このため、従来定義していたAI Provider BoundaryのPromotion Criteriaが、実装前の設計段階で満たされた。

⸻

Decision

Phase 0をProject Alice Phase 1〜4全体の設計フェーズとする。

設計の確定度は次のとおりとする。

* Phase 1は実装へ直接投入できる詳細度まで確定する
* Phase 2〜4はArchitecture、責務、Boundary、主要Data Model、主要Flow、Port、SecurityおよびPermissionを確定する
* 変更可能性が高いProvider Model、SDK、Libraryまたは細かなParameterはImplementation-time Reconfirmationとしてよい
* 設計は将来を見据えるが、実装は各Phaseで必要なものだけを追加する

AI Provider Boundaryはconversation Feature所有から、独立した`ai` Feature所有へ変更する。

`ai` Featureは、Alice Coreから利用するProvider-independentなAI Capability ContractとProvider Adapterを管理する。

```text
conversation ─┐
memory       ─┼──→ ai.application capability ports
tool         ─┤               ↑ implements
agent        ─┘        ai.infrastructure provider adapters
```

AI Capabilityは一つの巨大な`AiProvider` Interfaceへ統合せず、Capability-specific Portへ分割する。

概念:

* Text Generation
* Input Token Counting
* Structured Generation
* Tool Calling
* Speech To Text
* Text To Speech
* 必要時のRealtime Voice

Phase 1で実装するのは次だけとする。

* `TextGenerationProvider`
* `InputTokenCounter`
* Provider-independent Text Generation Model
* OpenAI Text Generation Adapter
* OpenAI Input Token Counting Adapter
* Phase 1 AI Configuration

Phase 2〜4用の未使用Port、Adapter、Package、Configurationまたは空実装をPhase 1へ先行追加しない。

`ai` FeatureはAlice Personality、Conversation History Selection、Personal Memory利用判断、Tool Permission、Agent PlanningまたはFeature固有Business Ruleを所有しない。

⸻

Superseded Decisions

本ADRは次の既存方針の対象範囲を更新する。

* ADR-003の「各Phase開始時に、そのPhaseだけの設計を追加する」というPhase 0設計範囲
* Phase 1 AiProviderをconversation Featureが所有するBackend Design上のDecision

ADR-003の段階的実装、不要なInfrastructureを先行実装しない原則は維持する。

ADR-005 AI Provider分離およびADR-007 Alice CoreとInfrastructureの分離は維持し、本ADRによって具体的なOwnershipとContract分割を明確化する。

⸻

Alternatives Considered

Conversation Feature OwnershipをPhase 1だけ維持する

Phase 1の実装量は小さくなるが、Phase 2開始時にPort、Adapter、Packageおよび依存方向を移動する必要がある。

Phase 2〜4のAI利用が設計上確定した現在は、意図的に短命なBoundaryを実装することになるため採用しない。

単一の汎用AiProvider Interface

Text、Structured Output、Tool CallingおよびVoiceを一つのInterfaceへ統合すると、利用Featureが不要なMethodへ依存し、Provider Capability差も表現しにくくなるため採用しない。

Provider SDKを各Featureから直接利用する

OpenAI固有ObjectとErrorがAlice Coreへ広がり、Provider交換可能性とTestabilityを損なうため採用しない。

⸻

Consequences

Positive

* Phase 2〜4追加時にAI Boundaryを移動する大規模Refactoringを避けられる
* Feature間でconversation内部Portへ依存する不自然な構造を防止できる
* Provider固有SDKを一つのInfrastructure Boundaryへ隔離できる
* Capabilityごとに異なるInput、Result、TimeoutおよびProvider Supportを表現できる
* Phase 1から将来用Capabilityを実装せずにArchitectureの整合を維持できる

Negative

* Phase 1からconversationに加えて最小の`ai` Featureが必要になる
* conversationとai間のApplication Boundaryを明示する必要がある
* 既存Architecture、Repository、BackendおよびAPI DesignのOwnership記述を更新する必要がある

⸻

Implementation Rules

* Phase 1の`ai` FeatureへConversation固有Business Logicを配置しない
* conversation Featureから`ai.infrastructure`へ直接依存しない
* conversation Featureは`ai.application`のCapability PortとProvider-independent Modelだけを利用する
* OpenAI SDK Object、EventまたはExceptionを`ai` Feature外へ公開しない
* Phase 2〜4用の空Portまたは空AdapterをPhase 1へ追加しない
* Capability追加時は対応するPhase Designを更新してから実装する
* Provider、Model、SDKまたはSecurity BoundaryをAI Coding Assistantが独自に決定しない

⸻

Related Documents

* alice-architecture.md
* repository-structure.md
* backend-design.md
* api-design.md
* ai-design.md
* database-design.md
* test-design.md
* decisions.md

⸻

Result

Project AliceのAI利用は、独立した`ai` Featureが所有するCapability-specific Portを介して行う。

Phase 1 Source CodeではConversation Text Generationに必要な最小Capabilityだけを実装し、Phase 2〜4の設計を理由として未使用機能を先行実装しない。

⸻

ADR-015 Memory Token EstimatorのApplication契約と実行時Fallback

Status

Accepted

⸻

Context

Phase 2のMemory Retrievalでは、最終Memory件数、推定Token数、UTF-8 Byte数の上限を同時に適用する。初期上限は、最終Memory最大8件、推定1,500 tokens、Memory本文のUTF-8最大12 KiBである。

`memory-design.md` §14.18では、Token Estimatorの具体的な実装をDeferredとしている。一方、Application Boundaryとしては、実行時の推定失敗とApplication構成不備を区別し、推定を利用できない場合の安全な動作を定義する必要がある。この区別がないと、必須Dependencyの登録漏れをFallbackで隠したり、推定していないPayloadがToken Budgetを満たすと誤って扱ったりする可能性がある。

Decision

1. Memory Featureは、`memory.application`にProvider非依存の`TokenEstimator` Application Portを所有する。
2. Estimatorへの入力は、推定対象となる実際のProvider非依存Memory Context Payloadを、最終的にContextへ追加する順序で表現する。`PersonalMemory` Aggregate全体、検索Metadata、またはMemory Contextとして送信しない情報を推定対象にしない。
3. Resultは、推定成功（`Estimated`。非負の推定Token数を含む）と推定不能（`Unavailable`）を区別する。推定不能を成功扱いしたり、推定値を捏造したりしない。
4. `TokenEstimator`がApplication構成に登録されていない場合はConfiguration Errorとし、起動時に検出する。通常の実行時推定失敗として扱わない。
5. 登録済みEstimatorが実行時に推定に失敗した場合、Memory Retrievalは§14.16のFallbackを適用する。最終Memory最大8件、Memory本文のUTF-8最大12 KiBを守り、Memory本文を途中で切断しない。Token推定値が存在しないため、このFallbackでは1,500 tokens以内を保証しない。内部Result / Traceでは、推定成功と推定不能Fallbackを区別する。
6. EstimatorのAlgorithm、Library、推定精度の目標、およびPayloadの具体的なEncodingは引き続きDeferredとする。本ADRが確定するのはApplication契約と失敗時の区別のみであり、未確定事項を埋める仮Estimatorや本番実装を許可するものではない。

Alternatives Considered

Estimator未登録を実行時推定失敗として扱う

採用しない。Application構成の不備を隠し、一時的な推定失敗と区別できなくなるため。

推定失敗時にも1,500 tokens以内を保証したものとして扱う

採用しない。推定値が存在せず、その保証を裏付けられないため。Fallbackでは件数とUTF-8 Byteの上限を維持するが、Token上限は保証しない。

この段階で推定Algorithmを確定し実装する

採用しない。Algorithmと具体的なEncodingは承認済みMemory設計でDeferredとなっているため。未確定事項を暗黙に決めず、Application契約のみを先に確定する。

Consequences

Positive

- Memory ApplicationはAI Provider SDKおよび`ai` FeatureのProvider固有Adapterから独立する。
- Configuration ErrorがFallback動作によって隠されない。
- 実行時Fallbackの制約が明確になり、根拠のないToken Budget保証を行わない。
- 承認済みのOwnershipと失敗時の意味を維持したまま、後続作業でEstimatorを実装できる。

Negative

- 実行時推定失敗時は、Memory Contextが1,500 tokens以内に収まることを保証できない。
- Estimatorの実装方式と推定精度は、別途設計・検証する必要がある。

Implementation Rules

- Memory FeatureへOpenAI SDK依存を導入しない。`ai.application`の`InputTokenCounter`をMemory Featureから直接再利用しない。
- 実際のMemory Context Payloadに含まれないFieldを推定対象にしない。
- Budget適合のためにMemory本文を途中で切断しない。
- 推定不能を0 tokensまたは推定成功として表現しない。
- 必須Estimatorの登録を起動時に検証する。
- Algorithm、Library、推定精度、Payloadの具体的なEncodingは、別途設計されるまでDeferredのままとする。
- 推定成功、実行時推定失敗とFallback、Estimator未登録による構成エラー、件数・UTF-8上限の同時適用、およびFallback時に1,500 tokensを保証しないことをテストする。

Related Documents

- `memory-design.md` §14.9、§14.16、§14.18
- `ai-design.md`
- `test-design.md`

Result

Memory Token EstimatorのApplication契約と実行時Fallbackの意味をAcceptedとする。EstimatorのAlgorithmと実装はDeferredのままである。本ADR単独ではSource CodeやDependency Injection設定を変更しない。

⸻

ADR-016 Memory Context Payload Serializationと累積Token計数

Status

Accepted

⸻

Context

ADR-015はMemory Application所有のProvider非依存`TokenEstimator`と、実際にMemory Contextへ含めるPayloadを推定対象にする契約を確定した。一方、Memory payloadの具体形式と候補追加時の計数方法はDeferredだった。

Phase 2の実装前に、Memory Applicationが返す`MemoryContextItem`と、将来Conversation Contextへ渡すデータの間で、計数対象を再現可能にする必要がある。Memory専用1,500-token BudgetはPhase 1の`ai.application.InputTokenCounter`が計数するGeneration Request全体の64,000-token Budgetとは責務と対象範囲が異なる。

Decision

1. Memory Applicationは、選別対象となる`MemoryContextItem`列から、本文だけを要素とする順序付きcompact JSON文字列配列を単一のProvider非依存Payloadとして生成する。0件の場合はMemory Context blockを作らず空文字列とする。
2. Payloadの配列順序は最終選別順序と一致させる。Memory本文は保存時にDomainが確定した文字列を使い、Payload生成時にtrim、Unicode正規化、空白変更または内容変更をしない。JSONに必要なquote、backslash、control character escapingおよび不対surrogate code unitのlowercase hex escapingを行い、有効なsurrogate pairとその他のUnicodeをUTF-8で保持し、BOMなしで出力する。
3. Payloadへ含めるMemoryContextItem Fieldは`content`のみとする。ID、Version、Category、State、Sensitivity Level、Temporal RoleおよびRelevance Reason Codesは選別・Validation・Trace用の内部情報として扱う。Sensitivity Level等のPolicy MetadataをProviderへ開示しない。Provider-neutralなContext Source Type `PERSONAL_MEMORY`は将来Context構造で別途表現し、Payload本文には重ねて挿入しない。
4. 12 KiB上限はPayload Serialization overheadではなく、選択Memory本文のUTF-8 byte数合計に適用する。TokenEstimatorはcompact JSON配列全体を推定し、JSON framingおよびescaping overheadもToken数に含める。
5. 候補はRanking、DeduplicationおよびDiversity後の順序で評価する。各候補の採否では、既選択候補と当該候補から再生成した累積Payload全体を計数する。個別計数の合算は採用しない。Tokenまたはbyte上限を超える候補は省略し、後続候補を現在の選択済みListに対して評価する。8件に達した時点で終了する。本文は切断しない。
6. Budget計数に使った選択済み本文順とPayload本文を、後続Conversation Contextへそのまま渡す。同じPayload Stringを再利用し、AI Context BuilderおよびProvider Adapterが本文をtrim、再serialize、再分割またはラベル付けしない。Provider Requestのrole / block framingはMemory専用Payload外の構造であり、Generation Request全体の計数はAI Featureの`InputTokenCounter`が担う。
7. TokenEstimator実行時失敗時は、Token Budgetによる部分選択を破棄し、元のdiversified候補から§14.16の最大8件・本文UTF-8最大12 KiB Fallbackを適用する。Fallback時は1,500-token上限を保証しない。Estimator未登録はADR-015どおり起動時Configuration Errorとする。
8. Token Estimation Algorithm、Library、Encodingおよび精度目標は本ADRで決定しない。Payload Serialization形式を決定することは、JTokkit等のTokenizer採用を決定することを意味しない。

Alternatives Considered

Memory本文を個別計数し、その合計を累積値として使う

実装は単純で候補単位の見積もりを再利用できるが、JSON区切りやescaping overheadを含まず、BPEの結合境界変化によって最終Payload計数と異なり得るため採用しない。

Memory本文を空行で連結する

読みやすく単純だが、Memory本文自体が改行や空行を含む場合の境界表現が曖昧になる。JSON配列なら各本文を一要素として決定的に区別でき、構造とescaping overheadも明示的に計数できるため採用しない。

Category、Temporal Role、SensitivityまたはID等を本文に付加する

Modelに追加情報を渡せる一方、それら全FieldをProviderへ開示する必要性は承認設計にない。Sensitivityなど内部Policy情報の不用意な開示とtoken増加を避けるため、現時点では本文Payloadへ含めない。

候補ごとの計数で上限超過したら検索順の残りを終了する

保守的で処理回数は少ないが、後続候補の本文が小さくても採用機会を失う。Ranking順は維持しつつ、超過候補を省略して後続を個別に累積評価する方が既存Byte上限選択のskip動作と整合する。

Consequences

Positive

- Memory Token Budgetの対象がProvider非依存かつ再現可能となる。
- Estimatorは最終候補列と同一の累積文字列を計数し、分割計数の境界誤差を避ける。
- 検索・Trace MetadataとMemory本文を分離し、Policy MetadataのProvider開示を防ぐ。
- Token Budgetと12 KiB本文Byte Budgetの対象差が明示される。

Negative

- Compact JSON serialization overheadもMemory Token Budgetを消費する。
- 候補ごとに累積Payloadを再生成・再計数するため、個別計数よりCPU処理が増える。
- 将来Provider Context MappingがPayload本文を変更した場合は、設計違反となり、Memory側SerializerとContext接続を同時に見直す必要がある。

Implementation Rules

- Memory Application内に一つの決定的Payload Serializerを設け、Budget計数用とContext受渡し用で共有する。
- Serializer、Estimator、候補選択を独立したUnit Testで検証する。
- TestはField除外、候補順序、JSON escaping、Unicode保持、空List、累積計数、超過候補後の継続、8件・12 KiB・1,500-token上限およびUnavailable時の全候補fallbackを保証する。
- AI Context接続Testでは、Budget計数に使用したPayloadとProviderへ渡すContext block contentが完全一致することを検証する。
- Memory ApplicationはOpenAI SDK、Provider固有Requestまたは`ai.application.InputTokenCounter`へ依存しない。

Related Documents

- `memory-design.md` §14.9、§14.10、§14.11、§14.16、§14.18
- `ai-design.md` §11.7、§11.10、§11.11、§11.12
- `test-design.md`
- ADR-015

Result

Memory Context Payloadの本文Field、compact JSON配列Serialization、Byte計数対象、累積Token推定および実行時失敗時のFallbackをAcceptedとする。Memory-to-Conversation Context接続はADR-017で別途確定する。Token Estimation Algorithm、Library、Encodingおよび精度評価は別工程で扱う。本ADRはSource Code、DependencyまたはDI設定を変更しない。

⸻

ADR-017 Memory-to-Conversation Context Integration

Status

Accepted

⸻

Context

ADR-015はMemory Application所有のProvider-independent Token Estimator、実行時Unavailableと起動時Configuration Errorの区別、および推定失敗Fallbackを確定した。ADR-016はMemory Application所有の完成済みcompact JSON Payload、累積計数、payload本文をAI Context接続で変更しない契約を確定した。これらのDecisionではProvider RequestへのContext Mapping、Conversation Applicationとの呼出契約、Generation Request全体Budget時のMemory縮小、およびProvider直前の最終ValidationはDeferredだった。

既存`ai-design.md` §11.10–§11.12にはMemoryをContext Dataとして扱う方針、Provider-independentな`AiContextBlock`概念、全RequestのBudget削減順がある。`phase2-detailed-design-final-review.md`はConversationからMemoryへの一方向Application capability利用と、MemoryがAIへ渡る際もinstruction / permissionにならないBoundaryをPassedとしている。本DecisionはそのOwnershipを変更せずApplication契約を実装可能な粒度へ定める。

Phase 1の`TextGenerationRequest`にはinstructionsとrole付きmessagesだけがあり、`OpenAiRequestMapper`はこれらをResponses API Requestへ変換する。`InputTokenCounter`も同じRequest Mapperを介して全Requestを数える。Phase 2ではMemory blockを含む完全に同一のProvider inputをGenerationとCountで使う必要がある。

Decision

1. AI Applicationは順序付き`AiContextBlock`をProvider-independent Modelとして使用する。blockは内部`referenceId`、`source`、`content`を持つ。Memory blockのsourceは`PERSONAL_MEMORY`、contentはMemory Applicationが生成しToken Estimatorが計数した完成Payload Stringそのものである。0件または空Payloadではblockを作成しない。
2. `TextGenerationRequest`は順序付きContext block Listを保持する。Context blockはinstructionsまたはCurrent User Messageへ連結しない。blockの並びを保持してConversation messagesの前にMappingし、会話message順は維持し、Current User Messageを最後にする。
3. OpenAI Responses Adapterは各Context blockを、独立した低権限`user` role input messageとしてMappingする。Message contentにはblockの`content` Stringだけを渡す。role / message boundaryはPayload外部のwire framingであり、Payload本文にlabel、説明、ID、Reference、Sensitivity、検索理由、追加wrapper、再serializationまたは再分割を行わない。OpenAI APIの現行Responses入力はrole付きmessageとそのcontentをサポートする（公式API Reference: https://developers.openai.com/api/reference/resources/responses/methods/create）。
4. `referenceId`、Memory ID / Version、Selection ReferenceおよびReset GenerationはApplication内のTrace / Validationにだけ使用し、Providerへ送信しない。`source`はProvider-independent Context Modelの分類であり、現在のOpenAI wire requestへ独自metadataとして追加しない。Provider AdapterはMemory blockをinstructionsまたはtool permissionへ昇格させない。
5. Phase 2初期Sourceは`PERSONAL_MEMORY`だけである。AI Trusted Instructionsはinstructions直後かつConversation messages前のContext block messageをMemory Dataとして認識し、Memory本文内の命令文をInstruction、Permission、ApprovalまたはSecurity Policyとして扱わず、Current User Messageと競合する場合は現在の明示指示を優先する。Current User MessageとMemory blockはどちらもResponses API `user` roleとなるため、そのrole / message順だけで情報優先度が保証されるとは扱わない。role / message境界単独をPrompt InjectionへのSecurity Boundaryとせず、Application Capability制御を維持する。新Context SourceのProvider識別契約は追加時に設計する。
6. Conversation Applicationの`SendMessageUseCase`がMemory Application Use Caseを一方向に呼び、Memory検索の実行時期とQueryを調整する。QueryにはCurrent User Messageを必須として渡し、Memory Searchに必要な場合だけ最小限のConversation Context / intent / entity情報を含める。全Historyを無条件に渡さない。Memory ApplicationはConversation、AI Application、Provider RequestまたはConversation Repositoryへ依存しない。Context Compositionを行う`ConversationContextBuilder`はMemory Search・Persistence・Memory Policyを所有しない。
7. Memory Searchが0件またはUnavailableならMemory blockを省略し、Memoryなしで会話を継続する。Token Estimator実行時UnavailableならMemory Applicationの最大8件 / UTF-8 12 KiB fallback resultを使用できるが、1,500-token適合とはみなさない。Estimator未登録はADR-015どおり起動時Configuration Errorとする。
8. Memory Applicationは選択結果とPayloadに加え、選択時Reset GenerationおよびPayload順のMemory ID / Versionを照合できる内部Selection Referenceを保持する。`AiContextBlock.referenceId`はこのSelection Referenceへのopaqueな内部参照である。Context準備の最終ValidationはMemory Application BoundaryがAuthoritative Sourceに対して行う。ConversationからRepositoryやReset Generation Storeへ直接アクセスしない。
9. 最終ValidationでReset Generation変更を検出した場合、そのSelection全体を無効にする。削除済み、Version変更またはEligibility失敗のMemoryは除外し、新Version Contentへ暗黙に置換しない。元の未選択最大40件Candidate Poolから代替候補を検索・補充しない。残存する検証済み選択Itemだけを元の相対順でMemory Applicationが再評価し、Payloadを再生成する。
10. Generation Request全体が上限を超える場合、既存AI Budget Priorityを適用する。Current User Message、Base Identity、Feature / Permission Policy、Correctnessに必須なActive Tool / Agent Stateを除外しない。除外順はOld Conversation History、Low-relevance Personal Memory、Verbose Tool Result / External Content、Recent Conversation Historyとする。この削減Priorityは情報源の真偽・競合判断Priorityとは別である。
11. Memory削減は元の最終選択Listだけに対して行う。ConversationはSelection Referenceと除外する選択Item ReferenceをMemory Applicationへ渡す。Memory Applicationは元Selection順の末尾Itemから一つずつ除外し、除外済みItemを復活させず、他の初回Search候補を加えない。各応答でPayloadを再生成し、最大8件、本文UTF-8 12 KiBおよび推定1,500 tokens Budgetを再適用する。指定可能な縮小上限は元のSelection以下かつ既定上限以下とする。0件は空Selection / blockなしで表す。本文切断は禁止する。
12. Memory縮小はToken EstimatorがUnavailableでも進める。各段階で一項目以上を単調に除外し、返されたfallback Payloadを含むGeneration Request全体をInputTokenCounterで再計数する。Memory Estimatorの値をRequest全体のtoken数から差し引かない。元Selectionは最大8件なので最大8回の縮小でblockなしに到達する。blockが空になっても全体Budgetが超過する場合は残りのAI削減順を適用し、不可削除の最小Requestが超過する場合はGenerationを開始しない。
13. Provider直前Validationは、Count済みRequestのSelection Reference・ID・Version・Reset Generationを再照合する。変化があれば無効Itemを除き、Memory Applicationが再生成・再BudgetしたPayloadでContextを再構築し、Memory BudgetとRequest全体Budgetを再計数してから安定確認を行う。各変更検出は少なくとも一Itemを失効・除外し、追加・代替を行わない。最大8件の除外と、安定確認を含む最大9回のValidationで終了する。Validation / 縮小の反復上限に先に達し、Count Stage Deadlineに残り時間がある場合はMemory blockを空にしてRequestを一度再計数する。Deadlineが先に尽きた場合、またはMemoryなしRequestもBudget超過・計数不能の場合はProviderへ送らない。
14. 最終Validationが保証するのは、Memory ApplicationのAuthoritative Source確認時点でID、Version、Reset GenerationおよびEligibilityが有効だったことまでである。その直後の更新を排除するLock / Reservationは導入せず、検証後の更新が起き得ることを明記する。
15. Text Generation AdapterとInput Token Count Adapterは同一のProvider Request Mapperを共有する。同じ`TextGenerationRequest`からinstructions、Context block順・content、Conversation message順・roleを一致させる。InputTokenCounterはContext blocksを含む完成Request全体を数え、Memory専用1,500-token Estimatorの代替にしない。
16. Search失敗、最終Validation不能またはReset Generation不一致はMemoryなしへ切り替える。Memory本文、検索Query全文、Sensitive Content、秘密情報、Selection ReferenceまたはRaw Provider Responseを通常Logへ出力しない。InputTokenCounter失敗・Deadline超過時は未計数RequestをProviderへ送らず既定AI Failure / Retry Policyを適用する。

Alternatives Considered

MemoryをCurrent User Messageへ連結する

RequestのMessage数を増やさずに済むが、会話上の現在User発話とMemory Dataの構造境界を失い、Current User Messageの完全性・優先性をテストで保証しにくいため採用しない。

Memoryをinstructionsへ連結する

一つのtext fieldで送れるが、指示階層とMemory Dataを混同し、Memory内の命令がTrusted Instructionとして扱われる危険を増やすため採用しない。

OpenAI専用のContext BlockまたはMessage metadataをAI Application Modelへ追加する

Responses API Mappingは可能でも、Provider固有ConceptをApplication Contractへ漏らすため採用しない。ApplicationはSource / Contentを持つProvider-independent blockとし、role MappingをAdapterへ閉じる。

Budget超過ごとに元の最大40件Candidate Poolから検索し直す

候補追加やRanking結果が再実行間に変わり、単調な縮小・有限終了を保証しにくい。Provider直前Validationで除外したMemoryが再追加される可能性もあるため採用しない。選択済み最大8件に限定し、順序を保つ縮小とする。

Token Estimator Unavailable時はMemoryを残してRequest全体が収まると仮定する

Generation Request全体の上限を保証できないため採用しない。推定器なしでもMemory Applicationへ一件ずつ除外を依頼し、そのPayloadを含む全RequestをCounterで確認する。Counter不能なら送信しない。

最終Validation時に変更済みMemoryを最新Contentへ暗黙に置換する

Budget計数済みPayloadとProviderへ送るPayloadが異なり、元のMemory selectionの根拠も失われるため採用しない。該当Memoryを除外して残りから再構成する。

Generation Request全体が収まるまで無制限にValidate / Countを繰り返す

外部Counter Call、Latencyおよび失敗時の無限処理につながるため採用しない。元選択最大8件の単調除外、最大9回のValidation、既定Count Stage Deadlineで終了する。

Consequences

Positive

- Memory Application、Conversation ApplicationおよびProvider Adapterの所有Boundaryが保たれる。
- Memory専用Payloadを変更せず、Memory Budget CountとContext Contentが一致する。
- Estimator Unavailableでも有限なMemory縮小で全Request Budgetを実測し、未計数Requestは送らない。
- Final Validation時に削除・更新・ResetされたMemoryを送信せず、Selection ReferenceをProviderへ漏らさない。
- GenerationとInput Token Countのwire mappingが共通化される。

Negative

- Memory縮小およびFinal Validationに伴いMemory Application ContractとAI Context Builder / Request Modelの変更が必要となる。
- Full Request Budgetを満たすための再計数回数とLatencyが増える。既存Input Token Count Stage Deadlineを超えた場合、Generationを中止する。
- OpenAI `user` roleは構造上低権限だが、role boundaryだけではPrompt Injectionを防げない。Trusted InstructionsとApplication-side Capability controlが必要である。
- Validation後からProvider受信までの競合更新はLock / Reservationを導入しないため排除できない。

Implementation Rules

- Provider input mappingはOpenAI Adapter内に閉じ、Responses APIの現行公式仕様とSDKを実装時にも確認する。
- AI Context blockのcontent、Memory Selection ReferenceおよびProvider wire dataを別の型・責務で扱う。
- Memory Request AdjustmentとFinal ValidationはMemory Applicationが選択順、Eligibility、Serializer、TokenEstimatorおよびfallbackを管理する。
- Context blockがないRequestはPhase 1と同じinstructions/messages構造を保つ。
- Testではblock order、user-role mapping、Current User Messageが最後であること、payload byte-for-byte一致、reference非送信、GenerationとCount Mapper一致、selection縮小単調性、Unavailable時の縮小、reset/version失効、反復上限およびCount失敗時のProvider未呼出しを保証する。
- Memory本文、検索Query、Sensitive Content、Credential、Raw Provider ResponseをLogへ出さない。

Review and Follow-up Trace

- ADR-015: Memory Token Estimator Port、Estimated / Unavailable、Startup Configuration ErrorおよびMemory fallbackを維持する。
- ADR-016: 完成Payload、累積計数、Payload不変性およびProvider-independent Memory Application Boundaryを維持する。
- `phase2-detailed-design-final-review.md` §5のConversation ↔ Memory一方向Boundary、Memory ↔ AIのContext Data境界と整合する。Phase 2全体のReviewを再開せず、そのAccepted BoundaryのApplication Contractを詳細化する。
- `memory-design.md` MD-138〜MD-140および`ai-design.md` §11.10〜§11.12へApplied Follow-upとして反映する。

Related Documents

- `memory-design.md` §14.9〜§14.16、§14.18、Decision MD-138〜MD-140
- `ai-design.md` §8.8、§11.7、§11.9〜§11.12、§14.10
- `phase2-detailed-design-final-review.md` §5
- ADR-015、ADR-016
- OpenAI Responses API Reference: https://developers.openai.com/api/reference/resources/responses/methods/create
- OpenAI Input Token Count API Reference: https://developers.openai.com/api/reference/resources/responses/subresources/input_tokens/methods/count

Result

Memory-to-Conversation Context Model、OpenAI user-role Mapping、Conversation orchestration、Request Budget縮小、最終Validation、Mapper共有およびFailure PolicyをAcceptedとする。具体的なJava API / Class、Provider SDK Mapping、Selection Reference実装およびTestsは実装工程で定義し、本ADR単独ではSource Code、Dependency、ConfigurationまたはDIを変更しない。

⸻

ADR-018 Answer Search Weighted Ranking and Candidate Pool Selection

Status

Accepted

⸻

Context

DB2-045〜DB2-066はKeyword Search方式、Posting / Unique Candidate上限、最終Answer Candidate Pool最大40件、Search Completenessおよび評価Targetを確定している。DB2-058のDeterministic Keyword RankはManagement Search専用である。Answer SearchではAdapter Keyword SignalとMemory Applicationで評価するEntity、Intent、Scope、Temporal、Confirmation、SensitivityおよびRedundancyを組み合わせる責務が決まっている一方、順位方式、40件への選択責務、Signal重み・尺度および欠損値処理は未決定だった。

Decision

1. Answer Searchは、Eligibility / 安全Policyを先に適用した後、Provider非依存Signalの重み付きスコア方式で候補を順位付けする。DB2-058のManagement Search Tupleは流用しない。Adapter / DynamoDBの返却順を順位にしない。Sensitivity、削除、Version、Reset Generation等のEligibilityまたは安全条件をRanking加点で代替しない。
2. Answer SearchのPosting評価上限は1,000件、Adapterが生成するUnique Candidate上限は200件、Memory Applicationが形成するSearch Candidate Pool上限は40件とする。これらは互いに異なる段階の制限であり、DB2-045〜066の各制約を変更しない。
3. 最大200件から最大40件への選別責務はMemory Applicationに置く。Adapterの入力順で先頭40件へ切り捨てない。Memory Applicationは候補をAuthoritative HydrationしEligibilityを適用した後、Application Relevance Ranking、DeduplicationおよびDiversityを経て最大40件のCandidate Poolを形成し、その後に既定Context Budgetで最大8件を選ぶ。上限は最大数であり、候補が適格でない、重複する、または存在しない場合に件数を埋めない。
4. Search Adapterは候補のList順をRelevance順位として表現しない。Candidate Pool選択はSearch Completenessを変更しない。DB2-065またはDB2-055/057/061により検索が不完全な場合はMD-141に従って`PARTIAL` / `UNAVAILABLE`を保持し、検証済み候補だけを利用する。
5. Signal契約は意味、型、観測元、欠損状態を区別できるProvider非依存表現とする。Keyword特徴はDistinct Term Match、Term Kind、Coverage、Query-side Hint MatchおよびPurpose別Signalを保持する。Application SignalはMemory Applicationで評価する。欠損は「未観測」であり、否定値、Eligibility通過または暗黙のゼロ点へ変換しない。
6. 本Decisionでは具体的な重み、数値尺度、欠損SignalのScore集約規則、Minimum Relevance Thresholdまたは同点処理を定めない。これらはDB2-065の既存評価Targetの下で、定義済みDatasetと再現可能な比較に基づき別途確定する。未確定値を実装上の暗黙Defaultで補わない。
7. Semantic SearchはDB2-066に基づく別判断前に必須化しない。初期Keyword Searchで利用できるSignalだけで動作する。

Alternatives Considered

DB2-058のManagement Search TupleをAnswer Searchへ流用する

既に具体的なTupleがあるため初期実装は簡単だが、DB2-058がManagement Search専用であること、AnswerのTemporal / Confirmation / Redundancy等を含まないことから採用しない。

Adapter返却順の先頭40件をCandidate Poolとする

追加計算は少ないが、返却順が順位でないため選択結果がDynamoDB QueryやTerm順に左右され、DB2-065のRecall評価を歪めるため採用しない。

重み付き方式の代わりに辞書順Tupleを新設する

決定性と説明可能性は高いが、Signal間の絶対優先順位を固定し、既存Accepted契約にない順序を追加することになるため採用しない。

Consequences

Positive

- Search Adapter、Memory Application Ranking、Eligibility、安全PolicyおよびContext Budgetの責務が分離される。
- 最大40件の選択がAdapterの未定義順序に依存しない。
- DB2-058、DB2-065およびDB2-066の適用範囲を維持する。

Negative

- Memory Applicationは最大200 Unique Candidateを対象にHydration / Eligibility / Rankingを行う可能性があり、処理時間をDB2-065の既存Latency Target内で評価する必要がある。
- Rankingを本番採用するにはSignalの値尺度、重み、欠損規則および同点処理を評価後に確定する必要がある。本Decisionだけでは実装可能な最終数値式は完成しない。

Evaluation and Implementation Rules

- 匿名化・合成Scenario最低300件、Answer Current / Historical 100件、Relation 100件、Management 100件、および既存Recall / Precision / Safety / Latency Targetを維持する。新たな合否Targetを追加しない。
- Ranking比較ではAnswer Required Memory Recall@40とAnswer Context Precision@8を既存Targetで評価し、Safety Target 0件を維持する。候補Pool選択、Ranking、Deduplication / DiversityおよびFinal Contextの各段階の結果を区別して記録する。
- 候補200件上限やPosting 1,000件上限による切捨てが起きた場合、Search Completenessを`PARTIAL`等の既存契約で表現し、完全検索として扱わない。
- DB2-065のDataset具体化は同Decisionの範囲内で行い、実ユーザー本文やProvider由来の機微情報を無断複製しない。
- Unit / Integration TestではAdapter順に依存しないこと、最大200→最大40→最大8の各段階、EligibilityがRankingより先であること、超過・部分検索状態の保持、およびSignal欠損と否定Signalの区別を保証する。

Related Documents

- `memory-design.md` §14.7〜§14.9、§14.18、MD-141〜MD-142
- `integrated/database-design.md` Section 36、DB2-045〜DB2-066（特にDB2-057、DB2-058、DB2-065、DB2-066）
- ADR-015、ADR-016、ADR-017

Result

Answer Searchの重み付き順位方式、Application所有の200件から40件へのCandidate Pool選択、および既存Search Completeness / Eval契約との関係をAcceptedとする。具体的な重み、尺度、欠損集約規則、閾値および同点処理は未決定のままとし、DB2-065評価に基づく後続Decisionを必要とする。本ADRではSource Code、Test、Dependency、Database SchemaまたはDB2 Decisionを変更しない。
