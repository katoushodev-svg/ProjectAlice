# Project Alice - Development Environment Design

## 1. Overview

本ドキュメントでは、Project Aliceを開発するためのDevelopment Environmentに関する設計方針を定義する。

対象には以下を含む。

- Development Machine
- Runtime / SDK
- IDE / Development Tool
- Local Application実行
- AWS接続
- DynamoDB開発環境
- Environment Variables
- Secrets
- Environment Separation
- Development Setup

本ドキュメントの目的は、Project Aliceを再現可能な開発環境で実装・テストできる状態を作ることである。

また、Project AliceではAIによる実装を基本方針とするため、AIが環境構築や実装時に使用すべきRuntime、Command、Configurationを判断できる状態を目指す。

---

## 2. Goals

Development Environmentでは以下を重視する。

### 2.1 Reproducibility

別環境でも可能な限り同じDevelopment Environmentを再現できるようにする。

開発者PCにのみ存在する暗黙的な設定へ依存しない。

### 2.2 Simplicity

個人開発として維持可能な構成とする。

Phase 1で不要なContainer、Virtual Machine、Cloud Development Environment等を理由なく導入しない。

### 2.3 Security

API KeyやCredential等のSecretをSource CodeおよびGit Repositoryへ保存しない。

### 2.4 Version Consistency

Java、Flutter等のRuntime / SDK Versionを明示し、開発環境による動作差異を可能な範囲で防止する。

### 2.5 AI Reproducibility

AIがコードや環境構築手順を生成する際に、使用するTechnology VersionやDevelopment Commandを判断できる状態とする。

AIが独自判断でRuntime VersionやDevelopment Toolを変更しない。

---

## 3. Development Machine

Project Aliceは初期段階ではLocal Developmentを基本とする。

Phase 1では主に以下をDeveloper Machine上で実行する。

```text
Developer Machine
│
├── Flutter
│
├── Spring Boot
│      │
│      ├── OpenAI API
│      └── DynamoDB Local
│
└── Docker Compose
       │
       └── DynamoDB Local 3.3.0
```

Phase 1の日常的なLocal Developmentでは、Conversation PersistenceとしてDynamoDB Localを使用する。

Spring BootはDeveloper Machine上でMaven Wrapperから直接起動し、DynamoDB LocalのみDocker Composeで起動する。

OpenAI APIについてはNetwork経由で接続する。

AWS DynamoDBはPhase 1の日常的なLocal Developmentの標準Persistenceとはしない。

PC AgentはPhase 1では実装対象外とする。

---

## 4. IDE / Development Tools

### 4.1 Primary IDE

Project Aliceの主要開発環境としてVisual Studio Codeを使用する。

用途:

- Source Code編集
- Design Document編集
- Git操作
- Terminal
- Flutter開発
- Java / Spring Boot開発
- Python開発
- AI Coding Assistance

### 4.2 AI Coding Assistance

実装支援にはGitHub Copilotを利用することを基本方針とする。

AIへ実装を依頼する場合は、Project Aliceの設計書をSource of Truthとして扱う。

AIは設計書およびADRと矛盾するArchitecture Decisionを独自に変更してはならない。

具体的なAI Implementation Ruleは`repository-structure.md`および`decisions.md`に従う。

### 4.3 Git

Source Code、Design Document、Test Code、Infrastructure Definition等をGitで管理する。

Repository StrategyはMonorepoとする。

---

## 5. Runtime / SDK

Project Aliceで使用する主要Runtime / SDKは以下とする。

| Component | Runtime / SDK | Version |
|---|---|---|
| Frontend | Flutter / Dart | Flutter `3.47.0` / Dart `3.13.0` |
| Backend | Java / JDK | 25 |
| Agent | Python | TBD |
| Browser Automation | Playwright | Phase 4実装前に再確認 |

Backend Framework / Build Environmentとして以下を正式採用する。

| Item | Adopted Version / Tool |
|---|---|
| Java / JDK | 25 |
| Spring Boot | 4.1.0 |
| Backend Build Tool | Maven |
| Maven | 3.9.16 |
| Build Execution | Maven Wrapper |

Agent等の未確定VersionはCompatibility、LTS、利用Library等を確認した上で各Componentの実装開始前に確定する。

確定したVersionは本ドキュメントへ記録し、必要に応じてADRへ判断理由を記録する。

---

## 6. Frontend Development Environment

### Technology

Flutter

### Phase 1

Phase 1ではiOS向けFlutter ClientをLocalで実行する。

主要用途は以下とする。

- テキスト入力
- Alice回答表示
- Backend API通信
- Conversation UI

Phase 1 Frontend Development Environmentは以下で固定する。

| Item | Decision |
|---|---|
| Flutter SDK | `3.47.0` Stable |
| Dart SDK | `3.13.0`（Flutter SDK同梱） |
| Target Platform | iOS Smartphone |
| Minimum iOS Version | iOS 15 |
| Default Development Device | iOS Simulator |
| Backend Base URL | `http://127.0.0.1:8080` |
| Package Lock | `pubspec.lock`をCommit |

Flutter SDKのVersion ManagerはPhase 1で追加しない。公式SDKを`3.47.0`へ固定し、次のCommandで実際のVersionとiOS Toolchainを確認する。

```bash
flutter --version
flutter doctor -v
```

iOS BuildにはXcodeを使用する。Xcodeの具体的なPatch VersionはPhase 1実装開始時にFlutter `3.47.0`およびDeveloper MachineのmacOSとの互換性を公式情報で再確認し、本Documentへ記録する。この再確認によってTarget PlatformやMinimum iOS Versionを独自変更してはならない。

iOS Simulatorから同じMac上のBackendへはLoopbackで接続する。実機iPhoneで検証する場合は、`security-design.md`に従ってPrivate LAN、Firewall、Backend Bind AddressおよびLocal Network Permissionを確認する。

---

## 7. Backend Development Environment

### Technology

Spring Boot / Java

### 7.1 Adopted Backend Stack

Project Alice Backendでは以下を正式採用する。

```text
Java / JDK Version : 25
Spring Boot Version: 4.1.0
Backend Build Tool : Maven
Maven Version      : 3.9.16
Build Execution    : Maven Wrapper
```

これらはProject Alice Backendの標準Development Environmentとして扱う。

AIおよび開発者が独自判断で異なるJava Version、Spring Boot Version、Build Toolへ変更しない。

### 7.2 Phase 1

Phase 1ではSpring Boot BackendをLocalで実行する。

```text
Flutter
   │
   │ HTTP
   ▼
Local Spring Boot
   │
   ├── OpenAI API
   │
   └── DynamoDB Local
          │
          ▼
     Docker Compose
```

BackendのJava / JDK Version、Spring Boot Version、Build Tool、Maven Version、Build Execution方式は正式採用済みとし、本ドキュメントに定義されたVersionを使用する。

AIによるBackend実装では、以下の確定済み構成を使用する。

- Java / JDK Version: 25
- Spring Boot Version: 4.1.0
- Build Tool: Maven
- Maven Version: 3.9.16
- Build Execution: Maven Wrapper

Dependency Management方式、Local起動Command、Test実行CommandについてはBackend Designおよび実装準備時に具体化する。

---

## 8. DynamoDB Development Environment

Project Aliceでは初期PersistenceとしてDynamoDBを利用する。

Phase 1の日常的なLocal DevelopmentおよびIntegration Testでは、DynamoDB Localを標準Development Environmentとして使用する。

### 8.1 Adopted Local Environment

Phase 1では以下の構成を正式採用する。

| Item | Decision |
|---|---|
| Runtime | Docker Compose |
| DynamoDB Local Image | `amazon/dynamodb-local:3.3.0` |
| Version Policy | Exact Version Tag |
| Host Bind | `127.0.0.1:8000` |
| DynamoDB Mode | `-sharedDb` |
| Development Data | Persistent Docker Volume |
| Telemetry | Disabled |

構成:

```text
Developer Machine
│
├── Spring Boot
│      │
│      └── http://localhost:8000
│
└── Docker Compose
       │
       └── DynamoDB Local 3.3.0
```

Spring Boot Application自体はContainer化せず、Developer Machine上でMaven Wrapperから起動する。

DynamoDB LocalのみDocker Composeで起動する。

DynamoDB LocalをPublic Network InterfaceへBindしてはならない。

### 8.2 Development Data

通常のLocal DevelopmentではPersistent Docker Volumeを使用する。

Containerの停止・再起動によってConversation Historyが失われない構成とする。

Integration Testでは通常開発用Tableを共有せず、Test専用Tableを作成・破棄する。

Spring Bootの通常起動時にDynamoDB Tableを自動作成または更新しない。

Local Tableの作成は明示的なSetup CommandまたはScriptによって行う。

### 8.3 AWS DynamoDB

AWS DynamoDBはPhase 1の日常的なLocal Developmentの標準Persistenceとして使用しない。

将来的なCloud Environment、AWS固有Behaviorの確認または限定的なSmoke Test等で必要になった場合に利用する。

DynamoDB LocalとAWS DynamoDBにはBehavior上の差異が存在するため、DynamoDB LocalのみですべてのAWS固有Behaviorを保証しない。

具体的な利用範囲は`database-design.md`および`test-design.md`に従う。

### 8.4 Detailed Setup Procedure

DynamoDB Localの具体的な導入・操作手順は、本ドキュメントへ重複して記載しない。

以下を参照する。

- `dynamodb-local-setup.md`

同ドキュメントでは主に以下を定義する。

- Docker Compose構成
- DynamoDB Local起動・停止
- Local Environment Variable設定
- Local Table作成
- TTL設定
- Table確認
- Development Data Reset
- Troubleshooting

Database Contractについては`database-design.md`をSource of Truthとする。

`dynamodb-local-setup.md`によってTable / Key / Item Schema / Transaction / Consistency Ruleを変更してはならない。

---

## 9. AWS Credentials

AWS ResourceへLocal Environmentからアクセスする場合、AWS CredentialをSource Codeへ記述しない。

禁止例:

- AWS Access KeyをJava Sourceへ直接記述
- AWS Secret Access Keyを`application.yml`へCommit
- Credential FileをGit RepositoryへCommit

Credentialの具体的な取得・管理方式はSecurity Designと合わせて決定する。

可能な限りAWS SDK標準のCredential Provider Mechanismを利用し、Application固有のCredential管理実装を作らない。

DynamoDB Localで必要となるDummy Credentialは、実AWS Credentialとは明確に分離する。

実AWS CredentialをDynamoDB Local用ConfigurationまたはDocker Compose Fileへ記載しない。

---

## 10. OpenAI API Credentials

OpenAI API KeyをSource CodeおよびGit管理対象のConfiguration Fileへ直接保存しない。

ApplicationからはEnvironment Variable等の外部Configurationを介して取得する。

具体的なVariable NameおよびConfiguration Binding方式は`ai-design.md`および`security-design.md`で決定する。

---

## 11. Environment Variables

Environment依存値はSource Codeから分離する。

対象例:

- OpenAI API Key
- AWS Region
- DynamoDB Endpoint
- DynamoDB Table Name
- Backend Port
- External API Endpoint
- Cursor Signing Key
- Processing Lease
- Failed Idempotency Retention

Environment Variable名は各設計書で定義し、Application内で無秩序に追加しない。

DynamoDB関連の具体的なEnvironment Variableは`database-design.md`をSource of Truthとする。

Local Setup時の設定例については`dynamodb-local-setup.md`を参照する。

Secretと通常Configurationを区別する。

---

## 12. Environment Separation

Project Aliceでは将来的に複数Environmentを持つ可能性がある。

候補:

- Local
- Development
- Production

Phase 1では必要以上にEnvironmentを増やさない。

最低限Local Developmentを成立させることを優先する。

Phase 1の日常的なLocal PersistenceにはDynamoDB Localを利用する。

Cloud Deployment開始時にDevelopment / Production Environmentの分離方式を確定する。

---

## 13. Configuration Policy

Application ConfigurationはEnvironment依存値とApplication共通設定を分離する。

基本原則:

```text
Source Code
    │
    ├── Application Default Configuration
    │
    └── External Configuration
             │
             ├── Environment Variable
             └── Secret
```

Environment固有値をSource Codeへ埋め込まない。

Local専用設定をProductionへ誤って適用できない構造を目指す。

特にDynamoDB Local用EndpointをCloud Environmentへ誤適用しない。

Spring BootおよびFlutter固有のConfiguration方式は各Component Designで定義する。

---

## 14. Secret Management

Secretには以下を含む。

- OpenAI API Key
- AWS Credential
- OAuth Client Secret
- External API Key
- Agent Authentication Secret
- Cursor Signing Key

以下を禁止する。

- GitへのCommit
- Source CodeへのHard Coding
- Design Documentへの実値記載
- Test CodeへのProduction Secret記載
- LogへのSecret出力

具体的なSecret Storageについては`security-design.md`で定義する。

---

## 15. Local Startup

Phase 1ではDeveloper Machine上で以下を起動できる状態を標準とする。

```text
1. Development Dependency確認
        ↓
2. Docker ComposeによるDynamoDB Local 3.3.0起動
        ↓
3. Local DynamoDB Table確認 / 必要に応じて作成
        ↓
4. Local Environment Variable設定
        ↓
5. Maven WrapperによるSpring Boot Backend起動
        ↓
6. iOS SimulatorでFlutter Application起動
        ↓
7. Flutter → Backend通信確認
        ↓
8. Backend → OpenAI API通信確認
        ↓
9. Backend → DynamoDB Local通信確認
```

DynamoDB Localの具体的なCommandおよびSetup Procedureは`dynamodb-local-setup.md`に従う。

Backend Applicationの起動時にDynamoDB Tableを暗黙的に作成または変更しない。

BackendのRuntime / Framework / Build Toolは確定済みである。

具体的なSpring Boot起動CommandおよびTest実行CommandはBackend実装準備時に本ドキュメントまたは関連するDevelopment Documentへ追加する。

---

## 16. Dependency Installation

各ComponentのDependencyはComponent標準のDependency Management機構を利用する。

```text
Flutter
→ pubspec.yaml

Spring Boot
→ Maven 3.9.16 / Maven Wrapper

Python
→ Agent Design時に決定
```

Backend Build ToolとしてMaven 3.9.16を正式採用し、Build ExecutionにはMaven Wrapperを使用する。

Developer MachineへインストールされたMaven VersionへBuild結果を依存させない。

Dependency Versionを手作業で各Developer Machineへ個別導入する方式を避ける。

DynamoDB LocalについてはDocker ImageのExact Version TagによってVersionを固定する。

---

## 17. Container Policy

Phase 1では、DynamoDB Localの実行環境としてDocker Composeを使用する。

Container化の対象は必要最小限とし、Phase 1ではSpring Boot BackendおよびFlutter ApplicationをContainer化しない。

標準構成:

```text
Developer Machine
├── Flutter
├── Spring Boot
└── Docker Compose
       └── DynamoDB Local 3.3.0
```

Containerは以下の具体的な目的に限定して利用する。

- DynamoDB Localの再現可能な実行環境
- Local Persistence Environment
- Integration Test Environment

「一般的な開発環境だから」という理由だけで、Backend、Frontendまたはその他のComponentを追加でContainer化しない。

新しいContainerを追加する場合は、具体的な必要性を確認した上で導入する。

---

## 18. Development Data

Development EnvironmentではProduction Dataへ依存しない。

Conversation等のDevelopment Dataについては、Development Environment専用データとして扱う。

通常のLocal DevelopmentではDynamoDB LocalのPersistent Docker VolumeへDevelopment Dataを保存する。

Integration Testでは通常のLocal Development Dataを共有せず、Test専用Tableを利用する。

将来的にProduction Environmentが存在する場合、Production Dataを無断でLocal Environmentへコピーしない。

Test Data / Seed Dataが必要になった場合は`test-design.md`および`database-design.md`で管理方法を定義する。

---

## 19. Logging

Local DevelopmentではDebuggingに必要なApplication Logを確認可能にする。

ただし以下をLogへ出力しない。

- OpenAI API Key
- AWS Secret
- Authentication Token
- Cursor Signing Key
- その他Credential

AI Request / ResponseおよびConversation Content等に個人情報が含まれる可能性があるため、詳細なLogging Policyは`security-design.md`で定義する。

---

## 20. AI Implementation Environment Rules

AIへ環境構築・実装を依頼する場合は本ドキュメントを参照させる。

AIは以下を独自判断で変更しない。

- Runtime / SDK Version
- Framework Version
- Build Tool
- Build Tool Version
- Build Execution方式
- Environment Structure
- Secret Management方式
- DynamoDB Development方式
- Infrastructure Technology

特にBackendについては以下を固定値として扱う。

```text
Java / JDK Version : 25
Spring Boot Version: 4.1.0
Build Tool         : Maven
Maven Version      : 3.9.16
Build Execution    : Maven Wrapper
```

DynamoDB Development Environmentについては以下を固定値として扱う。

```text
DynamoDB Local Version : 3.3.0
Runtime                : Docker Compose
Host Bind              : 127.0.0.1:8000
Mode                   : -sharedDb
Development Data       : Persistent Docker Volume
```

AIは独自判断で以下を行ってはならない。

- Flutter `3.47.0`またはDart `3.13.0`を変更する
- Phase 1 TargetをiOS以外へ変更する
- Minimum iOS Versionを15未満へ変更する
- `pubspec.lock`をCommit対象から除外する
- SSE専用Package、Code Generationまたは端末PersistenceをPhase 1へ追加する
- DynamoDB LocalをAWS DynamoDBへ置き換える
- DynamoDB LocalのVersionを変更する
- `latest` Tagを使用する
- DynamoDB LocalをPublic Network InterfaceへBindする
- Spring Boot起動時にTableを自動作成・更新する
- Spring Boot Backendを理由なくContainer化する
- DynamoDBのTable / Key / Item SchemaをDevelopment Environment都合で変更する

未決定事項が実装に必要となった場合、AIは推測で確定せず設計判断が必要な事項として扱う。

一方、確定済みDevelopment Environmentの範囲内で必要となる通常のCommand、Configuration、Dependency設定等は合理的に実装してよい。

DynamoDB Localの具体的な環境構築をAIへ依頼する場合は、`dynamodb-local-setup.md`もInputとして提供する。

Conversation Repository、Item Mapping、Transaction、Pagination等のDatabase実装を依頼する場合は、`database-design.md`をSource of Truthとして扱う。

---

## 21. Phase 1 Scope

Phase 1でDevelopment Environmentとして最低限成立させる対象は以下とする。

- Flutter Development Environment
- Spring Boot Development Environment
- OpenAI API接続設定
- DynamoDB Local Development Environment
- Docker ComposeによるDynamoDB Local実行
- Environment Variable管理
- Local Startup Procedure
- Test実行環境

以下はPhase 1では必須としない。

- PC Agent Development Environment
- Playwright Environment
- Production Deployment Environment
- Complex CI/CD Environment
- Kubernetes
- Distributed Development Environment
- Cloud IDE
- Spring Boot BackendのContainer化
- Flutter ApplicationのContainer化

---

## 22. Open Decisions

以下は現時点では未決定とする。

- Python Version
- AWS Region
- Environment Separation方式
- Infrastructure as Code Technology
- CI/CD Development Environment
- Phase 1 iOS ToolchainのXcode Patch Version

Backendについて以下は決定済みであり、Open Decisionには含めない。

- Java / JDK Version: 25
- Spring Boot Version: 4.1.0
- Backend Build Tool: Maven
- Maven Version: 3.9.16
- Build Execution: Maven Wrapper

Frontendについて以下は決定済みであり、Open Decisionには含めない。

- Flutter SDK: `3.47.0`
- Dart SDK: `3.13.0`
- Target Platform: iOS Smartphone
- Minimum iOS Version: iOS 15
- Default Development Device: iOS Simulator
- Package Version固定: `pubspec.lock`

DynamoDB Local Development Environmentについて以下は決定済みであり、Open Decisionには含めない。

- Phase 1 Local Persistence: DynamoDB Local
- DynamoDB Local Version: 3.3.0
- DynamoDB Local Runtime: Docker Compose
- Host Bind: `127.0.0.1:8000`
- DynamoDB Mode: `-sharedDb`
- Development Data: Persistent Docker Volume

AWS Regionについては、Local Developmentで使用するRegion設定と、将来的なAWS実環境の正式Regionを区別する。

`dynamodb-local-setup.md`で使用するLocal設定を理由として、Cloud Environmentの正式AWS Regionが確定したものとは扱わない。

残るOpen Decisionは実装に必要となる前に決定する。

Architectureへ影響する重要な判断については`decisions.md`へADRとして記録する。

---

## 23. Summary

Project AliceではLocal Developmentを基本として開発を開始する。

Phase 1の基本Local Development構成は以下とする。

```text
Developer Machine
│
├── Flutter
│      │
│      ▼
├── Spring Boot
│      │
│      ├──────────→ OpenAI API
│      │
│      └──────────→ DynamoDB Local
│
└── Docker Compose
       │
       └── DynamoDB Local 3.3.0
```

Spring BootはDeveloper Machine上でMaven Wrapperから起動する。

DynamoDB LocalはDocker Composeで起動し、`127.0.0.1:8000`へBindする。

通常のLocal DevelopmentではPersistent Docker Volumeを使用する。

DynamoDB Localの具体的な導入・操作手順については`dynamodb-local-setup.md`を参照する。

Database Contractについては`database-design.md`をSource of Truthとする。

Development Environmentでは、

- Reproducibility
- Simplicity
- Security
- Version Consistency
- AI Reproducibility

を重視する。

Backendについては以下を正式採用する。

- Java / JDK 25
- Spring Boot 4.1.0
- Maven 3.9.16
- Maven Wrapper

Phase 1 Frontendについては以下を正式採用する。

- Flutter `3.47.0`
- Dart `3.13.0`
- iOS Smartphone
- Minimum iOS 15
- iOS Simulatorを標準開発・Integration Test Deviceとする
- `pubspec.lock`をCommitする

Phase 1のDynamoDB Local Development Environmentについては以下を正式採用する。

- DynamoDB Local 3.3.0
- Docker Compose
- `127.0.0.1:8000`
- `-sharedDb`
- Persistent Docker Volume

Xcode Patch Versionは、Phase 1実装開始時にFlutter / macOSとの互換性を確認して記録する。これはFrontend ArchitectureまたはTarget Platformの未決定事項ではない。

SecretやEnvironment固有値をSource Codeから分離し、AIを含む開発者が同一の設計方針に基づいて環境構築・実装できる状態を目指す。
