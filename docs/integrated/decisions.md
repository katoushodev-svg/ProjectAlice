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

ADR-015 Shared Execution Authority Boundary

Status

Accepted

⸻

Decision

Tool / Agent / PC / Browser / Applicationで共通するPermission、Approval、Risk、Execution Authority、Durable Intent、Execution Fence、Outcome、Cancellation、Recovery、Auditをshared `execution` Featureが所有する。

`tool`はTool Definition / Registry / Connector Capability / tool-specific validation / adapterを保持し、`agent`はGoal / Plan / AgentExecution orchestration / AgentAction / Observation / Evaluation / Replanningを保持する。

Dependencyは`agent.application → execution.application`および`tool.application → execution.application`とし、`execution`から`agent`またはtool-specific infrastructureへの逆依存を禁止する。

Result

Phase 3で`tool`が所有していた共通Execution InvariantはPhase 4以降shared `execution`へ昇格するが、Phase 3のPermission / Approval / Risk / Durable Intent / Idempotency / Unknown Outcome semanticsを変更しない。

⸻

ADR-016 Conversation-centered Capability Orchestration

Status

Accepted

⸻

Decision

ConversationをPrimary User-facing Entry Pointとし、AliceがUser Goalに応じてConversation-only / Tool / Agent / Composite Capabilityを選択する。恒常的なChat / Tool / Agent Mode選択をUserへ要求しない。

⸻

ADR-017 Cross-Phase Alice Experience Principle

Status

Accepted

⸻

Decision

Alice Core + ConversationをPrimary Experienceとし、Memory / Tool / Agent / Permission / Approval / Voice / ExecutionをContextualまたはSubordinate Capabilityとして統合する。UI-012をCross-phase Visual Baselineとして維持し、Capability GrowthをDashboard Growthへ直結させない。

⸻

ADR-018 Agent Orchestration / Executor Separation

Status

Accepted

⸻

Decision

Backend `agent` FeatureがGoal / Plan / Observation / Evaluation / Replanningを所有し、top-level `agent/` RuntimeはFilesystem / Terminal / Browser / Application / OSのvalidated executionのみを担当する。ExecutorはAlice Coreの判断ロジックを所有しない。

⸻

Phase 4 Detailed Decision Index

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
