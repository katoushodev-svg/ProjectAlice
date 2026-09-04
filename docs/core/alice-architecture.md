Project Alice - Architecture Design

1. Overview

本ドキュメントでは、Project Alice全体のシステムアーキテクチャを定義する。

Project Aliceは、ユーザー専用のパーソナルAIアシスタント「Alice」を実現するための長期開発プロジェクトである。

Aliceは単なるチャットアプリケーションではなく、将来的に以下の能力を持つことを想定する。

* AIとの自然な会話
* 長期Memory
* ユーザー理解
* 技術支援
* 外部サービス連携
* 音声入出力
* PC操作
* ブラウザ操作
* Agentによるタスク実行

そのため、本システムでは特定のAIモデルやUI、外部サービス、保存技術へ強く依存しない構造を採用し、各コンポーネントの責務を明確に分離する。

⸻

2. Architecture Goals

Project Aliceのアーキテクチャでは以下を重視する。

2.1 Maintainability

個人開発として長期間保守可能な構造とする。

必要以上に複雑な構造やインフラを初期段階から導入しない。

⸻

2.2 Extensibility

以下の機能を将来的に追加可能な構造とする。

* AI Provider追加
* Memory機能追加
* Tool追加
* 外部サービス追加
* Voice Interface追加
* PC Agent追加
* 新しいClient追加

新機能追加時にAlice Core全体へ大規模な変更が発生しない構造を目指す。

⸻

2.3 Provider Independence

Alice自体を特定のAIモデルへ依存させない。

AIモデルはAliceの推論能力を提供する外部コンポーネントとして扱う。

初期AI ProviderとしてOpenAI APIを利用するが、将来的に以下への変更・追加を可能とする。

* OpenAI
* Claude
* Gemini
* Local LLM
* その他AI Provider

⸻

2.4 Memory Portability

Alice MemoryはAI Providerおよび具体的な保存技術から独立した論理概念として扱う。

AIモデルや保存方式を変更した場合でも以下の情報を継続利用できる構造とする。

* 会話履歴
* ユーザープロフィール
* 好み
* 技術経験
* プロジェクト情報
* 過去の判断
* 利用履歴

ただし、Conversation HistoryとPersonal Memoryは異なる責務を持つ。

Phase 1ではConversation Historyをconversation Featureで管理し、Phase 2以降でPersonal Memoryを独立したmemory Featureとして追加する。

⸻

2.5 Security

Aliceは個人的な情報やPC内の情報を扱うため、セキュリティを重要な設計要件として扱う。

特に以下を考慮する。

* API Keyの安全な管理
* 認証情報のMemory保存禁止
* 外部送信データの制御
* PC Agentの実行権限制御
* Tool実行履歴の記録
* 重要操作に対するユーザー確認

⸻

2.6 Observability

Aliceが実行した処理を後から確認可能な状態とする。

特にAgentやToolについて以下を記録可能とする。

* 実行内容
* 使用Tool
* 開始・終了時刻
* 成否
* エラー内容
* 必要に応じたユーザー確認結果

⸻

3. Technology Stack

Project Aliceでは以下を基本技術スタックとする。

Layer	Technology
Client	Flutter
Backend	Spring Boot / Java
AI	OpenAI API
Database	DynamoDB
Agent	Python
Browser Automation	Playwright
Cloud	AWS

初期AI ProviderとしてOpenAI APIを利用するが、Alice CoreはOpenAIへ直接依存しない。

DynamoDBについてもConversation HistoryやMemoryそのものではなく、それらを永続化するための初期保存技術として扱う。

⸻

4. High-Level Architecture

Project Aliceの基本構成を以下とする。

User
 │
 ▼
┌─────────────────────┐
│   Alice Interface   │
│      Flutter        │
└──────────┬──────────┘
           │
           │ API
           ▼
┌──────────────────────────────────┐
│ Alice Backend                    │
│ Spring Boot                      │
│                                  │
│   ┌──────────────────────────┐   │
│   │       Alice Core         │   │
│   │                          │   │
│   │ Conversation / Context   │   │
│   │ Decision / Orchestration │   │
│   └────────────┬─────────────┘   │
│                │                 │
│       ┌────────┼────────┐        │
│       │        │        │        │
│       ▼        ▼        ▼        │
│   AI Port   Memory    Tool Port  │
│              Port                │
│       │        │        │        │
└───────┼────────┼────────┼────────┘
        │        │        │
        ▼        ▼        ├───────────────┐
     OpenAI    Memory     │               │
                │         ▼               ▼
                │     External APIs    PC Agent
                ▼      / Services       Python
             DynamoDB                     │
                                         ├─ OS Control
                                         └─ Playwright

上記はProject AliceのTarget Architectureを示す。

Alice Backendをシステムの中心とし、Clientからの要求を受け付ける。

Aliceとして何を行うかの判断および処理フローの制御はAlice Coreが担当する。

AI、Memory、Toolsなどの外部機能はPort / Interfaceを介して利用する。

Phase 1ではこのTarget Architectureのすべてを実装せず、Conversation Historyをconversation Featureの責務として実装する。

独立したMemory Feature / Memory PortはPhase 2以降でPersonal Memoryを導入する際に追加する。

⸻

5. Core Components

5.1 Alice Interface

Technology

Flutter

Responsibilities

Alice InterfaceはユーザーとAliceの接点を担当する。

主な責務は以下とする。

* ユーザー入力受付
* Alice回答表示
* 会話履歴表示
* ユーザー操作受付
* Backend API呼び出し
* 将来的な音声入出力
* デバイス固有機能との連携

Non-Responsibilities

以下の処理はClient側では行わない。

* AI Provider直接呼び出し
* Aliceの主要判断ロジック
* Memory管理
* Tool選択
* API Key管理
* 外部サービスの認証情報管理

Aliceの主要ロジックはBackendへ集約する。

⸻

5.2 Alice Backend

Technology

Spring Boot / Java

Role

Alice BackendはProject Aliceを実行するためのBackend Applicationである。

ClientとAlice Core、および各Infrastructureを接続し、Aliceを動作させるためのアプリケーション基盤を提供する。

Spring Boot自体をAliceとはみなさない。

Responsibilities

Alice Backendは主に以下を担当する。

* API公開
* Request / Response処理
* Dependency Injection
* 設定管理
* 認証・認可
* データ整合性・処理整合性の制御
* ログ
* エラーハンドリング
* Alice Coreの起動・接続
* Infrastructure実装の接続

Non-Responsibilities

Alice BackendのFramework / Infrastructure層では以下の判断を行わない。

* AIを利用するかどうか
* どのMemoryを利用するか
* どのToolを利用するか
* Toolをどの順番で実行するか
* Aliceとしてどのような回答を生成するか

これらはAlice Coreの責務とする。

⸻

5.3 Alice Core

Role

Alice CoreはAlice固有の判断・処理を担当する論理的な中核領域である。

Alice Coreは単一クラスとして実装するものではない。

複数のUse Case、Domain Logic、Application Service等によって構成される。

Responsibilities

Alice Coreは主に以下を担当する。

* ユーザー要求の解釈
* 会話処理
* Context生成
* Conversation History利用
* Memory利用判断
* AI利用判断
* Tool利用判断
* Tool実行フロー制御
* Alice人格・行動方針の適用
* 処理結果の評価
* 最終回答生成

External Access

Alice Coreは外部機能を直接実装しない。

必要な能力をPort / Interfaceとして要求する。

Target Architectureでは以下を想定する。

Alice Core
    │
    ├── AI Port
    ├── Memory Port
    └── Tool Port

Phase 1では独立したMemory Portを実装せず、Conversation History PersistenceにはConversationRepository等のConversation Feature側のRepository Portを利用する。

Infrastructure側がこれらのPortを実装する。

Principle

Alice Coreは以下の具体技術へ直接依存しない。

* OpenAI
* DynamoDB
* AWS
* Playwright
* OS固有API
* 外部サービス固有SDK

⸻

5.4 AI Layer

Purpose

Aliceへ推論能力を提供する。

AIモデル自体をAliceとは分離する。

Concept

Alice Core
    │
    ▼
AI Provider Interface
    │
    ├── OpenAI Provider
    ├── Claude Provider
    ├── Gemini Provider
    └── Local LLM Provider

Phase 1ではOpenAI Providerのみ実装する。

AI Provider Responsibilities

* AI APIとの通信
* Request変換
* Response変換
* Provider固有エラー変換
* Timeout処理
* Provider固有設定処理

Alice Core Responsibilities

以下はAI Provider側へ持たせない。

* Alice人格
* Conversation History選択
* Memory選択
* Tool選択
* Business Rule
* ユーザー固有判断

⸻

5.5 Memory

Purpose

Aliceがユーザーについて継続的に理解するための情報を管理する。

MemoryはAliceにおける重要な長期資産として扱う。

Architecture Principle

MemoryとDatabaseを同一概念として扱わない。

また、Phase 1で扱うConversation Historyと、Phase 2以降で追加するPersonal Memoryを同一の実装Featureとして扱わない。

Alice Memory（広義）
├── Conversation History
├── Personal Memory
├── Engineering Memory
└── Project Memory

広義のAlice MemoryにはConversation Historyも含まれるが、Backend実装上はConversation HistoryとPersonal Memoryを明確に分離する。

Conversation History ≠ Personal Memory

Conversation History

Conversation Historyは会話そのものを成立・継続させるための情報である。

主な対象:

* Conversation
* Message
* User Message
* Assistant Message
* Conversation ID
* Message Order
* Timestamp

Phase 1ではConversation Historyをconversation Feature内で管理する。

Alice Core
    │
    ▼
Conversation Repository Port
    │
    ▼
Persistence Infrastructure
    │
    ▼
DynamoDB

Phase 1ではConversation Historyのために独立したmemory FeatureやMemory Portを作成しない。

Personal Memory

Personal MemoryはAliceがユーザーについて長期的に保持する情報である。

主な対象:

* ユーザープロフィール
* 好み
* 趣味
* 生活情報
* Long-term Context
* User-specific Knowledge
* Remembered Facts

Personal MemoryはPhase 1では実装しない。

Phase 2開始時に独立したmemory Featureとして追加する。

概念構造:

Alice Core
    │
    ▼
Memory Port
    │
    ▼
Memory Infrastructure
    │
    ▼
Persistence / Search Technology

Additional Memory Categories

将来的に以下のMemory Categoryを検討する。

Engineering Memory

* 技術経験
* 開発方針
* 学習履歴
* 設計判断

Project Memory

* Project Alice
* その他開発プロジェクト
* プロジェクト方針
* 技術構成

Initial Storage

DynamoDB

DynamoDBはConversation Historyおよび将来的なMemoryを永続化するための初期保存技術として扱う。

DynamoDB自体をMemoryとはみなさない。

Future Extension

将来的に以下を検討する。

* Embedding
* Semantic Search
* RAG
* Vector Store / Vector Database
* Memory Ranking
* Memory Summarization

これらを導入した場合でも、Alice Coreが具体的な保存・検索技術へ直接依存しない構造を維持する。

DynamoDBのみですべてのMemory検索要件を満たすことを前提としない。

高度な意味検索やRAGが必要になった場合は、DynamoDBとは別の検索・ベクトル基盤を追加するかを別途設計する。

Phase 1ではConversation Historyの保存・参照を中心とし、Personal Memoryを含む複雑なMemory機構は実装しない。

⸻

5.6 Tools

Purpose

Aliceが外部サービスや外部環境と接続するための機能を提供する。

ToolはAlice Coreから具体実装を直接利用せず、抽象化されたInterfaceを介して利用する。

Tool Examples

将来的に以下を想定する。

* Calendar Tool
* GitHub Tool
* AWS Tool
* Web Search Tool
* Weather Tool
* Restaurant Tool
* File Tool
* PC Control Tool
* Browser Tool

Tool Execution Model

Toolによって実行先は異なる。

すべてのToolをPC Agent経由で実行する構造とはしない。

Alice Core
    │
    ▼
Tool Port
    │
    ├── GitHub Tool
    │      └── GitHub API
    │
    ├── Calendar Tool
    │      └── Calendar API
    │
    ├── AWS Tool
    │      └── AWS API / SDK
    │
    └── PC Control Tool
           │
           ▼
       Python Agent
           │
           ├── OS Control
           └── Playwright

外部APIをBackendから安全に直接利用できる場合は、Backend側のTool Infrastructureとして実装する。

ローカルPC上での操作が必要な場合はPC Agentを利用する。

Principle

Alice CoreはToolの具体的なAPI仕様、SDK仕様、実行環境を認識しない。

Tool実装側で外部サービスとの差異を吸収する。

⸻

5.7 PC Agent

Technology

Python

Purpose

Alice Backendからの要求に応じ、ユーザーPC上でローカル操作を実行する。

PC AgentはToolの実行基盤の一つであり、すべてのToolがPC Agentを利用するわけではない。

Responsibilities

* OS操作
* アプリ起動
* ファイル操作
* ローカル情報取得
* Browser Automation
* 実行結果返却

Browser Automation

Playwrightを利用する。

例:

* Webページ操作
* YouTube操作
* ブラウザ上の作業自動化

Security Principle

PC Agentは非常に強い権限を持つ可能性がある。

そのため将来的に以下を設計する。

* 実行可能Command制限
* Allow List
* User Confirmation
* 実行履歴
* Timeout
* 権限分離
* 危険操作ブロック

Alice Backendから任意コードを無制限実行できる構造にはしない。

⸻

6. Communication Architecture

6.1 Flutter → Backend

初期構成ではHTTP APIを利用する。

Flutter
   │
   │ HTTP / HTTPS
   ▼
Spring Boot REST API

Phase 1では同期Request / Responseを基本とする。

将来的にリアルタイム音声会話などが必要になった場合、WebSocket等を追加検討する。

⸻

6.2 Backend → AI Provider

BackendからAI Provider Interfaceを介してAI APIを利用する。

Alice Core
    │
    ▼
AI Provider Interface
    │
    ▼
OpenAI Provider
    │
    ▼
OpenAI API

OpenAI固有のRequest / ResponseをAlice Coreへ露出させない。

⸻

6.3 Backend → Conversation History

Phase 1ではAlice CoreはConversation Repository Portを介してConversation Historyへアクセスする。

具体的なPersistence InfrastructureがDynamoDBとの通信を担当する。

Alice Core
    │
    ▼
Conversation Repository Port
    │
    ▼
DynamoDB Infrastructure
    │
    ▼
DynamoDB

Alice CoreからDynamoDB固有APIやAWS SDKの詳細を直接利用しない。

⸻

6.4 Backend → Memory

Personal MemoryはPhase 2以降で導入する。

導入後はAlice CoreからMemory Portを介してMemoryへアクセスする。

具体的なPersistence / Search Infrastructureが保存・検索技術との通信を担当する。

Alice Coreから具体的なDatabase、Vector Store、External Memory Service等へ直接依存しない。

⸻

6.5 Backend → External Tools

Backendから外部APIを利用するToolについては、Tool Interfaceを介して利用する。

外部サービス固有のAPI、SDK、認証方式をAlice Coreへ露出させない。

⸻

6.6 Backend → PC Agent

PC AgentはBackendとは別Processとして扱う。

通信方法の詳細はAgent設計時に決定する。

候補として以下がある。

* HTTP API
* WebSocket
* gRPC

現時点では通信方式を確定しない。

PC Agent実装開始前に要件を整理し、ADRとして決定する。

⸻

6.7 Long-Running Processing

Phase 1では同期Request / Responseによる処理を基本とする。

将来的にToolやAgentによって長時間処理が必要となった場合は、非同期実行モデルを別途設計する。

対象例:

* 大規模Repository解析
* 複数Toolを利用するAgent Task
* 長時間Browser Automation
* バックグラウンド情報収集

必要になるまではMessage Queue、Job Queue等の非同期Infrastructureを導入しない。

⸻

7. Dependency Direction

Project Aliceでは、Alice Coreを外部技術から保護することを重視する。

基本的な依存方向は以下とする。

Interface / Presentation
          │
          ▼
     Application
          │
          ▼
        Domain
          ▲
          │
   Infrastructure

Infrastructure側がAlice Core側で定義されたPort / Interfaceを実装する。

Alice CoreからInfrastructureの具体実装へ直接依存しない。

具体的には以下の依存を避ける。

Alice Core ─X→ OpenAI
Alice Core ─X→ DynamoDB
Alice Core ─X→ Playwright
Alice Core ─X→ AWS SDK

代わりに、

Alice Core
    │
    ▼
Port / Interface
    ▲
    │
Infrastructure

の構造を基本とする。

⸻

8. Example Request Flow

Phase 1の基本的な会話処理を以下とする。

以下はコンポーネント間の責務と処理関係を示す概念フローであり、Messageの永続化タイミング、保存順序、Failure時の整合性戦略を規定するものではない。

Conversation Historyの具体的な保存タイミング、User Message / Assistant Messageの保存順序、AI API失敗時の扱い、DynamoDB書き込み失敗時の整合性制御等については、backend-design.mdおよびdatabase-design.mdで別途決定する。

ユーザー:

Alice、Spring Bootについて教えて

処理フロー:

1. Flutter
   ユーザー入力受付
        ↓
2. Spring Boot API
   Request受付
        ↓
3. Alice Core
   会話要求処理
        ↓
4. Conversation Repository Port
   必要なConversation Historyを要求
        ↓
5. Persistence Infrastructure
   DynamoDBからConversation History取得
        ↓
6. Alice Core
   Alice人格・Conversation HistoryからContext生成
        ↓
7. AI Provider Interface
        ↓
8. OpenAI Provider
        ↓
9. OpenAI API
        ↓
10. Alice Core
    AI Response処理・最終回答生成
        ↓
11. Conversation Repository Port
    Conversation Historyの永続化を要求
        ↓
12. Persistence Infrastructure
    DynamoDBへConversation Historyを永続化
        ↓
13. Spring Boot API
        ↓
14. Flutter
    Alice回答表示

このフローにおいて、Spring Boot InfrastructureはAliceとしての判断を行わない。

Alice Coreが処理を制御し、Infrastructureは要求された外部処理を実行する。

また、このフロー図の番号順はConversation Historyの永続化順序を保証するものではない。

⸻

9. Deployment Concept

Project Aliceは段階的に実行環境を拡張する。

Phase 1

開発初期はBackendおよびFlutterをローカル環境で実行し、DatabaseとしてDynamoDBを利用する。

Developer Machine
Flutter
   │
   ▼
Spring Boot
   │
   ├──────────────→ OpenAI API
   │
   └──────────────→ DynamoDB

DynamoDBをローカルエミュレーションするか、AWS上の実サービスを開発環境から利用するかについては、Development Environment / Database Designで決定する。

Future

将来的には以下の構成を想定する。

Flutter
   │
   ▼
AWS
   │
   ├── Alice Backend
   │
   ├── DynamoDB
   │
   └── Supporting Services
   │
   ▼
Home / Local PC
   │
   └── Alice PC Agent

クラウド上のAlice BackendとローカルPC Agentを分離する。

具体的なAWSサービスについてはInfrastructure設計時に決定する。

⸻

10. Phase 1 Architecture Scope

Phase 1では以下のみを対象とする。

Flutter
   │
   ▼
Spring Boot
   │
   ▼
Alice Core
   │
   ├── AI Provider Port
   │       │
   │       └── OpenAI Provider
   │
   └── Conversation Repository Port
            │
            └── DynamoDB Infrastructure

Phase 1で実装する主要機能:

* テキストチャット
* Alice人格
* AI API連携
* Conversation History保存
* Conversation History参照

Phase 1ではConversation Historyをconversation Feature内で管理する。

独立したmemory FeatureおよびPersonal MemoryはPhase 1では実装しない。

以下はPhase 1では実装しない。

* Personal Memory
* RAG
* Vector Search
* GitHub連携
* AWS操作Tool / AWS情報取得機能
* PC Agent
* Playwright
* Voice
* Autonomous Agent
* 非同期Agent実行基盤

将来機能を考慮した設計は行うが、不要なInterfaceやInfrastructureを先行して実装しない。

⸻

11. Architecture Principles

Project Aliceでは以下を基本原則とする。

AP-001 AliceとAIモデルを分離する

AIモデルはAliceへ推論能力を提供するコンポーネントであり、Aliceそのものではない。

⸻

AP-002 Memoryを長期資産として扱う

AI Providerや保存技術を変更してもMemoryを維持できる構造とする。

Conversation Historyについても保存技術から分離し、Phase 1ではconversation Featureの資産として扱う。

⸻

AP-003 Alice Coreを外部技術から分離する

Alice CoreからOpenAI、DynamoDB、AWS、Playwright等へ直接依存しない。

⸻

AP-004 Interfaceを介して外部機能を利用する

AI、Conversation History Persistence、Memory、Toolsなどの外部機能はPort / Interfaceによって抽象化する。

⸻

AP-005 Backend InfrastructureにAliceの判断を持たせない

AI利用判断、Conversation History利用判断、Memory利用判断、Tool選択、処理順序などAlice固有の判断はAlice Coreで行う。

Spring BootおよびInfrastructureはAlice Coreから要求された処理を実行する。

⸻

AP-006 MemoryとPersistenceを分離する

MemoryはAliceの論理的な記憶領域であり、DynamoDB等の保存技術とは分離して扱う。

Conversation HistoryについてもDynamoDBそのものとはみなさず、conversation FeatureのDomain / Application Conceptとして扱う。

⸻

AP-007 Toolと実行環境を分離する

ToolはAliceが利用する能力を表す。

PC AgentはToolを実行するための実行環境の一つとして扱う。

⸻

AP-008 必要な機能のみ実装する

将来利用する可能性だけを理由として、現時点で不要な仕組みを実装しない。

⸻

AP-009 危険な操作は制御可能にする

PC操作や外部サービスへの変更操作については、安全性、確認、監査可能性を重視する。

⸻

AP-010 実行内容を追跡可能にする

特にToolやAgentが行った処理について、後から確認可能な構造を目指す。

⸻

AP-011 Conversation HistoryとPersonal Memoryを分離する

Conversation Historyは会話を成立・継続させるための情報としてconversation Featureで管理する。

Personal MemoryはAliceがユーザーについて長期的に保持する情報として、Phase 2以降に独立したmemory Featureで管理する。

Conversation History ≠ Personal Memory

Phase 1のConversation Historyのために独立したmemory Featureを先行実装しない。

⸻

12. Architecture Evolution

Aliceは以下の順番でアーキテクチャを拡張する。

Phase 1
Chat + AI + Conversation History
      ↓
Phase 2
Personal Memory
      ↓
Phase 3
External Tools
      ↓
Phase 4
Engineering Support
      ↓
Phase 5
Voice + PC Agent
      ↓
Phase 6
Agent Capability

各Phase開始時に必要な設計を追加し、重要な設計判断はArchitecture Decision Recordへ記録する。

⸻

13. Open Architecture Decisions

以下は現時点では確定せず、各機能の設計時に決定する。

* Flutter / Backend間でWebSocketを利用するタイミング
* Backend / PC Agent間通信方式
* 長時間処理における非同期実行方式
* AWS上の具体的なBackend実行環境
* DynamoDBのTable設計
* DynamoDB Localを利用するか
* Phase 1以降のAuthentication方式（Phase 1はsecurity-design.mdによりAuthenticationなしと決定済み）
* Semantic Search導入時期
* Vector Store / Vector Database
* Embedding Provider
* RAG Architecture
* Voice Provider
* Agent Planning方式

未確定事項を推測で実装せず、必要となった段階でADRとして決定する。

⸻

14. Summary

Project AliceのTarget Architectureは以下の構造を基本とする。

User
 ↓
Alice Interface
 ↓
Alice Backend
 ↓
Alice Core
 ├── AI
 ├── Memory
 └── Tools

Alice CoreはAlice固有の判断・処理フローを担当する。

Spring BootはAliceを動作させるBackend Application Frameworkとして利用し、Alice固有の判断ロジックとは分離する。

AI、Memory、Toolsなどの外部能力はPort / Interfaceを介して利用する。

ただし、Phase 1ではConversation HistoryをPersonal Memoryとは分離し、conversation Feature内で管理する。

Phase 1のPersistence構造は以下とする。

Alice Core
    │
    ▼
Conversation Repository Port
    │
    ▼
DynamoDB Infrastructure
    │
    ▼
DynamoDB

Phase 2開始時にPersonal Memoryを扱う独立したmemory Featureを追加する。

Conversation History ≠ Personal Memory

MemoryはDynamoDB等の保存技術から分離し、長期的に維持するAliceの資産として扱う。

DynamoDBは初期Persistenceとして採用するが、将来のSemantic SearchやRAGについては必要に応じて別の検索・ベクトル基盤を追加できる構造とする。

Toolは実行環境から分離し、PC AgentはToolを実行する手段の一つとして扱う。

これにより、

* AIモデル変更
* 保存技術変更
* 新しいTool追加
* 新しいInterface追加
* Memory高度化
* PC Agent追加
* Agent機能追加

に対応可能な長期的なアーキテクチャを目指す。