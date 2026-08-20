# Project Alice - Security Design

## 1. Document Information

| Item | Value |
|---|---|
| Document | `security-design.md` |
| Project | Project Alice |
| Target | Phase 1 Security Detailed Design / Phase 2〜4 Security Architecture |
| Status | Approved |
| Last Updated | 2026-08-19 JST |

本ドキュメントは、Project AliceにおけるNetwork Boundary、Authentication、Authorization、Secret、Personal Data、External Provider、Logging、Tool Permission、Agent ExecutionおよびIncident ResponseのSecurity Designを定義する。

Phase 1は実装へ直接投入できる詳細度まで確定する。Phase 2〜4はArchitecture、責務、Trust Boundary、Permission、ApprovalおよびAudit要件を確定し、具体的なCloud Service、Identity Providerまたは暗号Library等は実装時再確認としてよい。

本ドキュメントで定義するSecurity Invariantを、AI Coding Assistantまたは開発者が一般Configurationだけで無効化してはならない。

---

## 2. Related Documents and Ownership

| Document | Responsibility |
|---|---|
| `product-definition.md` | Aliceの目的・Product Identity |
| `requirements.md` | Functional / Non-functional Requirement |
| `mvp.md` | Phase Scope |
| `alice-architecture.md` | System Architecture・Trust Boundary |
| `repository-structure.md` | Repository / Feature Boundary |
| `backend-design.md` | Backend Layer・Dependency・Exception Boundary |
| `api-design.md` | HTTP / SSE Contract・Validation・Safe Error |
| `database-design.md` | DynamoDB Data・Retention・Cursor署名・AWS Permission |
| `ai-design.md` | AI Data Flow・Prompt Injection・Provider Security・Observability |
| `development-environment.md` | Local Environment・Run Procedure |
| `test-design.md` | Security Testの実行分類・Framework・Scanner・CI Command / Gate |
| `decisions.md` | Accepted Architecture Decision |

本ドキュメントはApplication全体のSecurity PolicyのSource of Truthである。API Field、DynamoDB ItemまたはAI Provider Mappingの詳細は、それぞれの所有Documentを優先する。

Document間の矛盾を発見した場合、実装者が安全そうな方を推測で選ばず、実装前にDesignまたはADRを整合させる。

---

## 3. Security Goals

Project Aliceでは次を保護する。

- OpenAI API Key、Cursor Signing Keyおよび将来のExternal Credential
- Conversation HistoryとPersonal Memory
- Alice Promptと内部Configuration
- Tool Argument / Result
- PC、BrowserおよびVoiceから取得する情報
- UserのFile、Application、Cloud ResourceおよびAccount
- OpenAI / AWS等の利用枠とCost
- Aliceが提案、承認、実行した操作のTraceability

基本原則:

1. Default Deny
2. Least Privilege
3. Data Minimization
4. SecretをClient、Prompt、PersistenceまたはLogへ漏らさない
5. LLM OutputをPermissionまたは実行権限とみなさない
6. External ContentをUntrusted Dataとして扱う
7. 危険操作は対象を明示したApproval後だけ実行する
8. Security Boundary変更を一般的な実装詳細として扱わない

---

## 4. Security Scope by Phase

| Phase | Required Security Design | Implementation Scope |
|---|---|---|
| Phase 1 | Localhost、Secret、Conversation Data、OpenAI、Logging、Safe Error | 本DocumentのPhase 1項目を実装する |
| Phase 2 | Personal Memory分類、利用目的、訂正・削除、Context最小化 | Phase 1では設計のみ |
| Phase 3 | Tool Permission、Approval、Credential Isolation、Audit | Phase 1では設計のみ |
| Phase 4 | Agent制限、PC / Browser Sandbox、Backend-Agent認証、Voice Privacy | Phase 1では設計のみ |

Phase 2〜4の空Security Component、Permission Interface、Audit TableまたはAuthentication InfrastructureをPhase 1へ先行実装しない。

---

## 5. Threat Model and Assumptions

### 5.1 Phase 1 Trust Assumptions

Phase 1では次を前提とする。

- Single User
- Developer Machine上のLocal Development
- BackendはLoopbackだけでListenする
- Developer MachineのOS AccountはUser本人が管理する
- BackendをPublic Internetへ公開しない
- Flutter、BackendおよびDynamoDB Localは同一の信頼された開発環境で利用する

### 5.2 Protected Against

- Backend Portの意図しないLAN / Internet公開
- RepositoryやLogへのCredential混入
- API ResponseからのInternal Detail漏洩
- OpenAI / AWS SDK Objectの上位Layerへの漏洩
- Conversation Contentの無条件Logging
- Providerへ送信するDataの過剰化
- 重複送信による意図しないAI API呼び出し
- 将来のPrompt InjectionによるPermission回避
- LLM提案からPC / Tool操作への直接実行

### 5.3 Out of Scope for Phase 1

- OS AdministratorまたはRoot権限を取得したMalwareからの完全防御
- 物理的に奪取され、Disk Encryptionも解除されたDeveloper Machine
- Public Service向けDDoS Protection
- Multiple User間のData Isolation
- Cloud Account全体のOrganization Governance

これらが不要という意味ではない。Phase 1の前提を超える利用を開始する前にSecurity Architectureを更新する。

---

## 6. Data Classification

| Classification | Examples | Rule |
|---|---|---|
| Secret | API Key、Access Token、Cursor Signing Key、AWS Secret | Source、Log、Conversation、Memoryへ保存しない |
| Sensitive Personal Data | Conversation、Personal Memory、Audio、Screenshot、File Content | 必要な目的に限定し、DefaultでLogへ出力しない |
| Internal Security Data | Prompt、Permission Policy、Audit Detail、Internal Endpoint | Clientへ不要に公開しない |
| Internal Identifier | `requestId`、`conversationId`、Provider Request ID | Contentを埋め込まず、用途と公開範囲を限定する |
| Public Configuration | Port名、Environment Variable名、非Secret Default | Repositoryへ保存可能 |

HashまたはFingerprintへ変換しただけでPersonal Dataではなくなるとはみなさない。元Dataとの照合に利用できる値はInternal Security Dataとして扱う。

---

## 7. Phase 1 Trust Boundaries and Data Flow

```text
Flutter
  │ HTTP / localhost
  ▼
Spring Boot Backend
  ├── HTTP / localhost ──→ DynamoDB Local
  └── HTTPS ─────────────→ OpenAI API
```

Phase 1で信頼境界を越えるDataは次のとおりである。

| Flow | Data | Security Rule |
|---|---|---|
| Flutter → Backend | User Message、Idempotency Key | API Validation後に処理する |
| Backend → Flutter | Conversation、SSE Delta、Safe Error | Secret、Stack Trace、Provider Detailを含めない |
| Backend → DynamoDB Local | Conversation、Message、Idempotency State | Loopback限定。ConversationのSource of Truth |
| Backend → OpenAI | Prompt、選択済みHistory、Current Message | HTTPS、最小化、`store=false` |

Input Token Count RequestとText Generation Requestは、それぞれOpenAIへPromptと選択済みConversation Contentを送信する。Token CountがLocal処理ではないことを実装者とUserが認識できるよう、Data Flow上のExternal Transferとして扱う。

---

## 8. Phase 1 Network Boundary

### 8.1 Standard Configuration

Phase 1 Backendは次を必須とする。

| Item | Decision |
|---|---|
| Backend Bind | `127.0.0.1` |
| DynamoDB Local Bind | `127.0.0.1:8000` |
| Public Internet Inbound | Prohibited |
| Flutter → Backend | Local HTTPを許可 |
| Backend → OpenAI | HTTPSのみ |
| Actuator | Backendと同じLoopback Boundary |

`0.0.0.0`、`::`またはLoopback以外のAddressをPhase 1標準Profileで使用してはならない。

### 8.2 Startup Validation

Local Profileでは起動時に次を検証する。

- Backend Bind AddressがLoopbackである
- DynamoDB EndpointがLoopbackである
- DynamoDB LocalのHTTP利用はLocal Profileだけである
- OpenAI Base URLが公式HTTPS Endpointである
- ActuatorがLoopback以外へ公開されていない

安全条件を満たさない場合、警告だけで継続せずStartupを失敗させる。

### 8.3 Flutter Device / LAN Exception

Flutter実機からDeveloper Machineへ接続する場合、Loopbackでは通信できない。

LAN Accessは一般Environment Variable一つで有効化できる隠れたModeとして実装しない。必要になった時点で次を決定し、`security-design.md`と`development-environment.md`を先に更新する。

- 接続するPrivate Network
- Bind Address
- OS FirewallのSource制限
- Backend Authenticationの要否
- TLSまたはDevelopment Certificate
- CORS / Origin Policy
- 利用終了後の無効化手順

LAN Accessを開始しただけでPublic Internet Accessが許可されたことにはならない。

### 8.4 CORS and CSRF

Phase 1のNative Flutter ClientはBrowser CORS Enforcementを前提としない。BackendはDefaultでCORSを有効化せず、`*` Originを許可しない。

Phase 1はCookieまたはBrowser Session Authenticationを使用しないため、CSRF Token Infrastructureを追加しない。将来Flutter WebまたはCookie Authenticationを導入する場合、CORSとCSRFを同時に再設計する。

CORSはAuthenticationまたはNetwork Access Controlの代替ではない。

---

## 9. Authentication and Authorization

### 9.1 Phase 1 Decision

Phase 1ではAuthenticationおよびAuthorizationを実装しない。

理由:

- Single User
- Loopback限定
- Public Backendではない
- Multi User、Remote AccessおよびCloud公開がPhase 1 Requirementにない

JWT、OAuth、OpenID Connect、Amazon CognitoまたはSpring Securityを、将来利用する可能性だけを理由に追加しない。

### 9.2 Invalidating Conditions

次のいずれかが発生した場合、AuthenticationなしというDecisionは無効になる。

- BackendをLoopback以外へBindする
- LAN外またはInternetから接続する
- AWSへBackendをDeployする
- Multiple Userを扱う
- PC AgentへRemote Commandを送る
- Toolで外部AccountまたはResourceを変更する
- Sensitive MemoryへRemote Accessする

この場合、実装前にIdentity、Session、Token Storage、Revocation、Authorization、TLSおよびAuditを決定し、必要に応じADRを作成する。

---

## 10. Phase 1 Secret Management

### 10.1 Secret Inventory

| Secret | Phase 1 Source | Owner | Notes |
|---|---|---|---|
| `OPENAI_API_KEY` | Environment Variable | `ai.infrastructure.openai` | Real External Credential |
| `ALICE_CURSOR_SIGNING_KEY` | Environment Variable | API / Cursor Infrastructure | Base64、32 Byte以上 |
| AWS Real Credential | Not used | Future AWS Runtime | Phase 1 Localでは使用禁止 |

`AWS_ACCESS_KEY_ID=local`と`AWS_SECRET_ACCESS_KEY=local`はDynamoDB Local用Dummy値であり、実AWS Credentialではない。Local Profile以外でこの値を使用してはならない。

### 10.2 Allowed Injection

Phase 1ではSecretの実値をOS Process EnvironmentとしてBackendへ注入する。

許可:

- 起動前にShell Sessionへ設定する
- IDEのUser Local Run Configurationへ、Repository外・共有対象外の設定として保存する
- OSまたは将来のManaged Secret StoreからEnvironmentへ注入する

禁止:

- Source CodeへのHard Coding
- `application.yml` / `application.properties`
- Git管理対象の`.env`
- Maven Command Line Argument
- Docker Compose File
- Test FixtureまたはSnapshot
- Flutter Application
- DynamoDB / Conversation History / Personal Memory
- Prompt、Log、Exception Message、Metric Tag
- Issue、Pull Request、Screenshotまたは設計書

Command Line ArgumentはProcess一覧やShell Historyへ残り得るため、Secret受け渡しに使用しない。

### 10.3 Startup Validation

Secretは専用Configuration ObjectへBindingし、必要なComponentだけへ渡す。

- Missing、EmptyまたはWhitespaceのみならStartup Failure
- Secret ValueをValidation Errorへ含めない
- Secret Configuration Objectの自動`toString()`を禁止する
- Environment全体をDumpしない
- Key Prefix、Suffix、LengthをLogへ出力しない
- OpenAI Keyの形式をAlice独自Ruleで固定しない

`test` ProfileはReal OpenAI Clientを生成せずFake Providerを利用するため、`OPENAI_API_KEY`を要求しない。

### 10.4 Least Privilege and Separation

OpenAIはProject Alice専用Projectと専用API Keyを使用する。

Phase 1で必要なCapabilityは次に限定する。

- Responses API Request
- Responses Input Token Count

利用可能なPlatform Controlに応じて、Files、Fine-tuning、Administration等の未使用Permissionを付与しない。Development、ProductionまたはCIを将来分離する場合、それぞれ別Project / Credentialを使用する。

OpenAI ProjectのSpend Alertおよび利用上限は実装開始時に設定する。具体的金額は運用予算に依存するため本Documentでは固定しない。

### 10.5 Cursor Signing Key

Cursor署名は`database-design.md`に従いHMAC-SHA-256を使用する。

- 32 Byte以上のRandom Valueを使用する
- Base64 Encodingした値をEnvironment Variableへ設定する
- User MessageやAPI Keyから派生させない
- OpenAI API Keyと共用しない
- Signed Cursor全文をLogへ出力しない
- 比較はConstant-time Comparisonを使用する

Phase 1でKeyをRotationすると既存Cursorが無効になることを許容する。複数Keyを同時利用するRotation Infrastructureは実装しない。

### 10.6 Rotation and Exposure Response

Secret漏洩または漏洩の疑いがある場合:

1. Backendを停止し、必要ならNetwork Accessを遮断する
2. Provider側で旧Credentialを無効化する
3. 新しいCredentialを発行する
4. Local Environmentを更新しBackendを再起動する
5. Usage、Logおよび変更履歴から影響範囲を確認する
6. Source、Git History、Build Artifact、CIおよび共有資料への混入を確認する
7. 再発防止のDesign / Testを追加する

漏洩したCredentialを再利用しない。Incident記録にもSecret実値を貼り付けない。

Phase 1では定期Rotation周期を固定しない。漏洩、権限変更、利用者変更またはProvider Guidance変更時にRotationする。

---

## 11. OpenAI Data Protection

### 11.1 Data Sent in Phase 1

OpenAIへ送信するのは次に限定する。

- Alice Base Identity
- Conversation Reply Policy
- Context Strategyで選択されたConversation History
- Current User Message

送信しないData:

- API KeyまたはCursor Signing Key
- DynamoDB Key
- Idempotency Key
- `requestId`、`conversationId`
- Backend Exception / Stack Trace
- Application Log
- EnvironmentまたはConfiguration全体
- 選択されていないConversation History

UserがMessageへ入力したSecretを完全に自動検出することは困難である。Phase 1では誤検出によって入力を書き換えず、UIと利用手順でCredentialをConversationへ入力しないよう案内する。

### 11.2 Storage Policy

すべてのPhase 1 Responses API Requestで次を明示する。

```text
store = false
```

Alice Conversation HistoryのSource of TruthはDynamoDBであり、OpenAI Response IDまたはProvider Conversation Stateへ依存しない。

`store=false`は、OpenAIへDataを送信しないこと、またはProvider側Retentionが常にゼロになることを意味しない。OpenAI公式Documentationでは、API Dataは明示的にOpt-inしない限りModel Trainingへ使用されない一方、DefaultのAbuse Monitoring LogにはPrompt / Response等が含まれ、原則最大30日保持され得ると説明されている。

Provider Data Policy、Retention、Model EligibilityおよびRegional Endpointは、実装開始時とProvider変更時に公式Documentationで再確認する。

### 11.3 Transport

- OpenAI通信はHTTPSのみ
- TLS Certificate検証を無効化しない
- Arbitrary Base URL OverrideをLocal / Production Profileで許可しない
- ProxyまたはCustom CAが必要になった場合はSecurity Reviewを先に行う
- Provider CredentialをFlutterへ配布しない

### 11.4 Provider Incident

OpenAI Authentication Failure、異常UsageまたはCredential漏洩を検知した場合、Raw Provider Error BodyをClientへ返さない。Keyを停止・Rotationし、Provider Request ID等の安全なMetadataで調査する。

---

## 12. Conversation Data at Rest

### 12.1 Persistence

Conversation HistoryはDynamoDB LocalのPersistent Volumeへ保存する。

Phase 1 Application独自のField-level Encryptionは導入しない。代わりにDeveloper MachineのOS Account ProtectionとFull-disk EncryptionをLocal Data Protection Boundaryとする。

実際の個人Conversationを継続保存するDeveloper Machineでは次を有効にする。

- OS Login Password
- Automatic Screen Lock
- Full-disk Encryption
- OS Security Update

### 12.2 Retention

- Conversation / MessageはPhase 1で自動Expirationしない
- Partial Assistant Messageは保存しない
- Failed Idempotency Recordは最低24時間保持する
- Conversation削除 / Reset APIはPhase 1 Scope外

自動BackupまたはCloud Syncを標準機能として導入しない。Backupを追加する場合、Conversationと同じSensitive Personal DataとしてEncryption、Access、RetentionおよびDeletionを設計する。

### 12.3 Local Data Reset

DynamoDB LocalのData ResetはDevelopment用の明示的な破壊操作として扱う。

- 対象Volume / Tableを明示する
- Backend停止後に実行する
- 実AWS Endpointでは実行できない手順にする
- Reset前にConversationが失われることをUserへ明示する

SSD、BackupまたはCloud Syncからの物理的な完全消去をApplicationだけで保証しない。

---

## 13. API and SSE Security

### 13.1 Response Protection

すべてのConversation API Responseへ次を設定する。

```http
Cache-Control: no-store
```

API / SSE Errorへ次を含めない。

- Stack Trace
- Java / SDK Exception
- Raw OpenAI / AWS Error Body
- Credential
- Internal Endpoint
- DynamoDB Key
- Server File Path

Error Messageは`api-design.md`で定義した固定またはSanitized Messageだけを使用する。

### 13.2 Request Protection

API Designで定義した次のValidationをSecurity Boundaryの一部として維持する。

- Content-Type / Accept
- Unknown JSON Field拒否
- Message文字数とUTF-8 Byte上限
- UUID形式のIdempotency Key
- Single Conversation同時送信制御
- Same Key / Different Content Conflict

IdempotencyとConversation Busy Lockは、Client Bugまたは再送によるAI Costの重複を防ぐ。Public Service向けRate Limiterの代替ではない。

### 13.3 SSE

- Provider Eventを直接Flutterへ公開しない
- SSE DeltaをLogへ出力しない
- Client切断後はDelta送信だけを停止し、GenerationをTerminal Stateへ収束させる
- Retryは同じIdempotency Keyを使用する
- Terminal Event前にAssistant Message保存成功を確認する
- Single Frame 1 MiB、Single Delta 64 KiB、Stream全体8 MiB、非Comment Event 10,000件およびTemporary Assistant Text 50,000 Unicode Code Pointの上限をBackend / Flutter双方で強制する
- `Content-Length`だけを信用せず、実際に送受信したByte数を計測する
- 上限超過後はそれ以上Bufferへ追加せず、購読または送信を停止する
- Partial Assistant TextをCanonical Messageとして保存または表示確定しない
- 上限超過時にRaw Frame、Delta、ContentまたはCursorをLogへ出力しない

### 13.3.1 Response Resource Limits

Phase 1の防御的上限は次とする。

| Target | Limit |
|---|---:|
| Conversation JSON Response | 64 KiB |
| Message History JSON Response | 16 MiB |
| Problem Details Response | 64 KiB |
| Single SSE Frame | 1 MiB |
| Single `assistant.delta` JSON Data | 64 KiB |
| Temporary / Canonical Assistant Text | 50,000 Unicode Code Point |
| Entire SSE Stream | 8 MiB |
| Non-comment SSE Event Count | 10,000 Events |

これらはAvailabilityとMemory SafetyのSecurity Boundaryである。一般Configurationで無制限へ変更できる設計にしてはならない。将来上限を変更する場合は、API、AI、Database、Frontend、SecurityおよびTest Designを同時に更新する。

### 13.4 Assistant Output

LLM OutputはUntrusted Textとして扱う。

- OutputをHTML、JavaScript、Shell CommandまたはOS操作として自動実行しない
- Markdownを表示する場合、Raw HTMLをDefaultで無効化またはSanitizeする
- Linkを開く操作はUser Actionとする
- Tool / Agent実装前は、回答中の命令だけでExternal Actionを開始しない

Frontend固有のSanitization LibraryはFlutter Designで決定する。

### 13.5 Flutter Local Data

Phase 1 FlutterはConversation HistoryのSource of Truthにならない。表示中のMessageをMemory上に保持できるが、独自の永続CacheをDefaultでは追加せず、Application再起動後はBackendから再取得する。

- OpenAI API Key、AWS CredentialまたはCursor Signing KeyをFlutterへ保存しない
- User / Assistant MessageをFlutter Log、Crash ReportまたはAnalyticsへ送信しない
- Idempotency KeyはLogical Message SendとRetryに必要な期間だけ保持する
- ClipboardへConversationを自動Copyしない
- Persistent Cache、Crash ReportingまたはAnalyticsを追加する場合はData、Retention、EncryptionおよびExternal Transferを先に設計する

---

## 14. Logging, Metrics and Actuator

### 14.1 Logging Purpose

Logは障害調査と処理状態の確認に使用する。Conversationを再構築する保存先またはTool Auditの代用として使用しない。

### 14.2 Allowed Log Data

必要な範囲で次を記録できる。

- Stable Event Name
- `requestId`
- Opaqueな`conversationId`
- Provider / Model / Workload
- Duration、Attempt、Outcome、Safe Error Category
- Token Usage、選択 / 除外History件数
- Provider Request ID
- Client Disconnect有無
- `promptFingerprint`。Prompt本文を含めず、Metric Tagには使用しない
- Message Sequence、Item Count、AWS Request ID等の安全なPersistence Metadata

存在しない値を`0`または架空IDで補わない。

### 14.3 Prohibited Log Data

- SecretまたはCredentialの全部・一部
- Authorization Header
- User / Assistant Message本文
- Conversation History / Personal Memory本文
- Prompt本文、Text Delta
- OpenAI Request / Response Body
- Provider Error Body全文
- DynamoDB Item全文
- Idempotency Key、Signed Cursor全文
- Tool Argument / Result全文
- Raw Audio、Screenshot、File Content
- Environment一覧

SDK ObjectまたはConfiguration Objectの`toString()`をそのままLogへ渡さない。HTTP Body LoggingはDefaultで無効にする。

### 14.4 Stack Trace

Unexpected Internal ErrorではStack Traceを`ERROR`で記録できる。ただしInfrastructure BoundaryでProvider Body、CredentialおよびConversation Contentを除去したExceptionだけを上位へ渡す。

Expected Failureをすべて`ERROR`にせず、Validation、Busy、Conflict、Rate Limit、Timeout等を安定したCategoryへ分類する。

### 14.5 Local Log Persistence

| Item | Decision |
|---|---|
| Active File | `backend/logs/alice-backend.log` |
| Rotation | Daily |
| Retention | 7 days |
| Total Size Cap | 200 MB |
| Repository | `backend/logs/`をIgnore |

Console LogもTerminalまたはIDEに残る可能性があるため、File Logと同じ禁止Data Ruleを適用する。

### 14.6 Metrics

Metric TagはLow-cardinality値だけを使用する。`requestId`、`conversationId`、Provider Request ID、Prompt FingerprintまたはException MessageをTagにしない。

Token Usageが取得できない場合はFieldを省略し、`0`として記録しない。

### 14.7 Actuator

Phase 1で公開するのは次だけとする。

```text
/actuator/health
/actuator/metrics
```

Loopbackだけで利用し、Health DetailをDefaultで表示しない。次を公開しない。

- `/actuator/env`
- `/actuator/configprops`
- `/actuator/heapdump`
- `/actuator/loggers`
- `/actuator/mappings`
- `/actuator/threaddump`
- その他Allowlistへ含めていないEndpoint

Actuator Endpointを`*`で一括公開してはならない。Health CheckのたびにOpenAI APIへ接続しない。

---

## 15. Dependency and Build Security

Phase 1では次を守る。

- Maven Wrapper経由でBuildする
- Dependency Versionを`pom.xml`またはDependency Managementで再現可能にする
- HTTP Maven Repositoryを追加しない
- 不明なRepository、PluginまたはCopied Binaryを追加しない
- SecretをBuild Log、Test ReportまたはGenerated Artifactへ含めない
- Production Codeで不要なDevelopment ToolをRuntime Dependencyへ含めない

Dependency Vulnerability ScanおよびSecret ScanのTool、Version、実行Command、CanaryおよびCI Stageは`test-design.md`をSource of Truthとする。Phase 1では、Digest固定のGitleaksとOWASP Dependency-Checkを同Documentで定義されたGateとして実行する。Critical / High Findingを未確認のまま無視してはならない。誤検知または受容するRiskは、根拠と影響範囲を記録してReviewする。

---

## 16. Phase 2 Personal Memory Security

Personal MemoryはConversation Historyより長期的で、複数Conversationへ影響するSensitive Personal Dataとして扱う。

Phase 2実装前に次を確定する。

- Memory CategoryとSensitivity
- 記憶する目的
- Userによる確認、訂正、削除
- RetentionとExpiration
- Contextへ利用したMemoryのTraceability
- Providerへ送信するMemoryの最小化
- Backup / Export / Import Security
- Sensitive MemoryをToolまたはAgentへ渡す条件

禁止:

- CredentialをPersonal Memoryとして保存する
- 全Memoryを毎回LLMへ送信する
- Userが訂正・削除できない不可視MemoryをDefaultにする
- Memory本文をApplication Logへ出力する
- Memory内の命令文をSystem Instructionとして扱う

AuthenticationなしでRemote Memory Accessを開始してはならない。

---

## 17. Phase 3 Tool Security

### 17.1 Authority Boundary

LLMはTool Callを提案できるが、実行権限を持たない。

```text
LLM Proposal
   ↓
Schema Validation
   ↓
Permission Policy
   ↓
User Approval when required
   ↓
Tool Executor
   ↓
Audit Record
```

このBoundaryをProvider Tool Calling機能へ委譲しない。

### 17.2 Risk and Approval

| Risk | Default Policy |
|---|---|
| Read-only / Low Risk | Policyで許可されたScopeのみ実行可能 |
| Reversible Write | 原則として事前Approval必須 |
| Irreversible / High Impact | 毎回事前Approval必須 |

Approvalは次へBindingする。

- Tool Name
- Target
- Canonicalized ArgumentsまたはHash
- Risk Level
- Approval Expiration
- Single Execution

TargetまたはArgumentsが変更された場合、以前のApprovalを再利用しない。Tool Category全体への無期限ApprovalをDefaultにしない。

### 17.3 Credential Isolation

- Tool CredentialはExecutor側だけが保持する
- LLM ContextへCredentialを渡さない
- Tool ResultからCredential FieldをSchemaで除去する
- Toolごとに別Credential / Scopeを使用する
- Read ToolへWrite Permissionを付与しない

### 17.4 Untrusted Tool Data

Web Page、Email、Issue、Source Code、FileおよびTool ResultはUntrusted Dataである。その中の命令でPermission、ApprovalまたはTarget Allowlistを変更しない。

URL取得Toolでは実装前にScheme、Domain、Redirect、Private Address、Link-local AddressおよびDNS Rebindingへの対策を定義する。

### 17.5 Audit

Application LogとAudit Logを分離する。Auditには少なくとも次を記録する。

- Tool / Operation
- 安全なTarget識別子
- Risk Level
- Approval Request / Result
- Execution Start / End
- Outcome
- Cancel / Failure

Secret、Token、Personal Content全文はAuditへ保存しない。Audit Data Model、Integrity、RetentionおよびAccess ControlはPhase 3 Database / Security Designで確定する。

---

## 18. Phase 4 Agent, PC and Browser Security

### 18.1 Agent Limits

Agent Runには有限の制限を持たせる。

- Maximum Step
- Maximum Duration
- Maximum CostまたはProvider Call
- Concurrent Side-effecting StepはDefault `1`
- Cancellation
- Safe Stop

Re-planによってTargetまたはArgumentsが変わった場合、再Approvalを必要とする。

### 18.2 Backend and PC Agent Boundary

BackendとPython PC Agent間では次を必須Requirementとする。

- Mutual Authentication
- Command Integrity / Authenticity
- Replay Prevention
- Short-livedまたはRotatable Credential
- Command ID、Timestamp / Expiration、Nonce等の検証
- Permission ScopeのBackend / Agent双方での検証
- Network Source制限

具体的なProtocol、Key ManagementおよびTransportはPhase 4実装前に決定する。共有固定SecretをSourceへ埋め込む方式は採用しない。

### 18.3 OS and File Operations

- Directory / File Allowlist
- Application Allowlist
- Command Type Allowlist
- SymlinkとPath Traversal対策
- Working Directory固定
- Dangerous Command Denylistだけに依存しない
- 最小OS権限
- Delete / Overwrite前のApproval

LLMが生成した任意Shell CommandをValidationなしで実行しない。

### 18.4 Browser Operations

- Browser ProfileとCredential Scopeを分離する
- Domain Allowlist
- Download / Upload Target制限
- Password、Session Cookie、Payment情報をLLMへ送らない
- Cross-origin NavigationとRedirectを再検証する
- Purchase、投稿、送信、削除等は事前Approvalを必要とする
- Page ContentによるPrompt InjectionをPermission変更として扱わない

### 18.5 Voice

- Wake WordはAuthenticationではない
- Voice経由でもTool / Agent Approvalを省略しない
- High-impact Actionは画面等で対象を確認できるFlowを持つ
- Raw AudioをDefaultで永続化しない
- Speech TranscriptをSensitive Personal Dataとして扱う
- Screen Lock中に許可する操作を明示的に制限する

---

## 19. Cloud and Remote Promotion Gate

BackendをAWSまたはRemote Networkへ移行する前に、少なくとも次を確定する。

- User / Device Identity
- Authentication Protocol
- Authorization Model
- Access Token StorageとRotation
- Session Revocation
- TLS Termination
- Network Segmentation / Firewall
- Secret Manager
- DynamoDB Encryption / Backup / PITR
- Central Log / MetricのAccessとRetention
- Rate Limit / Abuse Protection
- Security Alert / Incident Process
- Data Residency Requirement

AWS RuntimeではLong-lived IAM User Keyより、IAM Role等のTemporary Credentialを優先する。OpenAIでWorkload Identity等の短期Credentialが利用可能で、Project Aliceの利用条件に適合する場合は実装時に再評価する。

具体的なIdentity ProviderとしてCognito、OAuth Provider等を現時点で先行決定しない。

---

## 20. Incident Response

### 20.1 Incident Types

- Credential Exposure
- Backendの意図しないNetwork公開
- Conversation / Memory漏洩
- Unauthorized Tool / Agent Action
- Abnormal Provider Cost / Usage
- Malicious Dependency
- AuditまたはLogの改ざん疑い

### 20.2 Common Procedure

1. 実行とExternal Accessを停止する
2. CredentialをRevoke / Rotateする
3. DataとLogの追加流出を防ぐ
4. 安全なIdentifierとTimestampで影響範囲を確認する
5. User Data、External ResourceおよびCostへの影響を評価する
6. 必要なRecoveryを実施する
7. Design、Testおよび運用手順を更新する

Incident調査のためでもConversation、SecretまたはProvider BodyをIssueへ無条件に貼り付けない。

---

## 21. Phase 1 Security Configuration Contract

| Configuration | Required Value / Rule |
|---|---|
| Backend Address | `127.0.0.1` |
| DynamoDB Endpoint | Loopback `http://localhost:8000`相当 |
| OpenAI Base URL | Official HTTPS Endpoint |
| `OPENAI_API_KEY` | Local / Real Provider ProfileでRequired |
| `ALICE_CURSOR_SIGNING_KEY` | Base64 32 Byte以上 |
| Responses Storage | `store=false`、一般設定で変更不可 |
| CORS | Disabled by default |
| Authentication | None in Phase 1 only |
| Actuator Exposure | `health,metrics` only |
| Health Details | Hidden by default |
| Log Retention | 7 days |
| Log Total Size | 200 MB |
| Full Content Logging | Prohibited |
| Conversation JSON Response | 64 KiB maximum |
| Message History JSON Response | 16 MiB maximum |
| Problem Details Response | 64 KiB maximum |
| Single SSE Frame / Delta | 1 MiB / 64 KiB maximum |
| SSE Stream / Event Count | 8 MiB / 10,000 Events maximum |
| Assistant Text | 50,000 Unicode Code Point maximum |

Security Invariantと運用調整可能値を分離する。`store=false`、Loopback Bind、Secret非Logging等を一般Environment Variableで無効化可能にしない。

---

## 22. Security Test Requirements

本SectionのSecurity要件は、`test-design.md`で次の正式なTestへ割り当てる。

### 22.1 Network and Configuration

- Backendが`127.0.0.1`へBindする
- Wildcard / Non-loopback BindでLocal Profile Startupが失敗する
- DynamoDB EndpointがNon-loopbackならStartupが失敗する
- OpenAI通信がHTTPSでTLS検証を行う
- `store=false`がすべてのGeneration Requestへ設定される
- CORS Wildcardが存在しない

### 22.2 Secret

- Missing / Blank SecretでStartupが失敗する
- `test` ProfileはReal CredentialなしでFake Providerを使用する
- Startup / Error / Debug LogへSecretが出ない
- API ResponseとSSEへSecretが出ない
- Configuration Objectの文字列表現へSecretが出ない
- Repository / Build ArtifactにSecret-like Dataが含まれない

### 22.3 Data and Error

- Message、Prompt、Delta、DynamoDB Item全文がLogへ出ない
- Provider / AWS Error BodyがClientへ出ない
- `Cache-Control: no-store`が設定される
- Unknown Field、Size超過、Invalid CursorがSafe Errorになる
- JSON / Problem Details / SSE Frame / SSE Stream / Event Count / Assistant Textの各Boundaryで上限超過を拒否する
- 上限超過時にPartial TextをCanonical Messageへ確定せず、Raw PayloadをLogへ出さない
- Assistant Outputが自動実行されない

### 22.4 Actuator and Logging

- `health`と`metrics`以外が公開されない
- Health DetailへCredentialまたはConfigurationが出ない
- Log Rotationが7日 / 200 MBと一致する
- High-cardinality IDがMetric Tagへ入らない
- Async / SSE処理後にMDCがClearされる

### 22.5 Future Phase Security Tests

- Permission DeniedでTool Executorが呼ばれない
- ApprovalがTarget / ArgumentsへBindingされる
- Re-plan後に旧Approvalを再利用しない
- Prompt InjectionでPermissionを変更できない
- ReplayされたAgent Commandを拒否する
- Voice経由でApprovalを省略できない

Test Framework、Secret Scanner、Dependency Scanner、Mock ServerおよびCI Commandは、作成済みの`test-design.md`をSource of Truthとする。本SectionはSecurity上必要な検証対象を所有し、実行TechnologyやCommandを重複定義しない。

---

## 23. Requirements Traceability

| Requirement ID | Security Design / Verification |
|---|---|
| `P1-FR-001`, `P1-FR-005` | FlutterからLocal Backendへの限定通信、SSE Content非Logging |
| `P1-FR-008`, `NFR-006` | Idempotency Key非Logging、Retry / Failure時のSafe Error |
| `NFR-001`, `NFR-002` | Provider Credential / DataとAlice Core / Persistenceの分離 |
| `NFR-003` | Loopback、Secret Management、Data Minimization、Provider Transfer、Log禁止Data |
| `NFR-004`, `NFR-005` | Phase 1で不要なAuthentication / Agent Security Infrastructureを先行実装しない |
| `NFR-007` | Security Configuration、Log Capture、Secret Scan、Adapter Test |
| `NFR-008` | 非機密Audit MetadataとRequest ID、機密Content非記録 |
| `NFR-009` | Security Scanner / Dependency Version固定と導入時再確認 |
| `P2-FR-001`〜`P2-FR-006` | Personal Memory分類、Consent、Retention、Correction / Deletion Boundary |
| `P3-FR-008` | Tool Risk Classification、Permission、Approval |
| `P4-FR-003`, `P4-FR-005` | Agent Step Limit、危険操作前Approval、User Control |
| `P4-FR-010` | VoiceをAuthenticationまたは危険操作Approvalの代替にしない |
| `P4-FR-011` | Agent / Tool / PC / Browser OperationのAudit |

---

## 24. AI Coding Assistant Implementation Rules

AI Coding Assistantは次を行ってはならない。

- Phase 1 Backendを`0.0.0.0`へBindする
- Debug目的で認証なしLAN Accessを追加する
- CORS `*`を追加する
- SecretをConfiguration FileまたはSampleへ記載する
- Secretを一部MaskしてLogへ出す
- Request / Response Body Loggingを有効化する
- ActuatorをWildcard公開する
- Provider ErrorをそのままAPIへ返す
- `store=false`を省略または設定可能にする
- User ContentをSecret検出目的で無言変更する
- LLM OutputをTool Permissionとして扱う
- Phase 2〜4用Security ComponentをPhase 1へ先行実装する
- `test-design.md`に定義されたTest Framework、Scanner、VersionまたはCI Gateを独自判断で変更する

本Documentと矛盾する変更が必要な場合、実装より先にDesign / ADRを更新する。

---

## 25. Implementation-time Reconfirmation

次はTechnologyまたはProviderの変化が大きいため、対象Phaseの実装直前に再確認する。

- OpenAI Data Usage / Retention / Regional Processing
- OpenAI Project PermissionとWorkload Identity
- Cloud Secret Management Technology
- Authentication / Identity Provider
- Backend-Agent Mutual Authentication方式
- Command Signing Algorithm / Key Rotation
- Browser Sandbox / Profile Isolation
- Voice Provider Data Retention
- Gitleaks Image Digestと既知Regressionの有無
- OWASP Dependency-CheckのVulnerability Data取得 / Cache方式
- Central Audit StorageとIntegrity Control

再確認は、Architecture BoundaryをAI Coding Assistantが自由に変更してよいという意味ではない。

---

## 26. Open Decisions

Phase 1の実装を開始するうえでSecurity上の未決定事項は残さない。

次は対応Phaseまたは関連Designで確定する。

- LAN Accessを実際に使用する場合の限定構成
- Phase 2 Memory Retention / Deletionの具体値
- Phase 3 Audit Data Model / Storage / Retention
- Phase 4 Backend-Agent ProtocolとCredential
- Cloud / Remote Authentication方式
- CI ProviderとSecurity Scan Artifact Retention

これらをPhase 1実装時に推測して先行実装しない。

---

## 27. Review Checklist

### 27.1 Phase 1

- [x] Loopback BoundaryがAPI、Database、AI Designと一致する
- [x] Authenticationなしの成立条件が限定されている
- [x] Secret Source、Validation、Rotationが一意である
- [x] ConversationのExternal Transferが明示されている
- [x] `store=false`とProvider Retentionの違いが明確である
- [x] Logging禁止DataがAI / Database Designと一致する
- [x] Safe ErrorとActuator ExposureがAPI Designと一致する
- [x] Security Test Inputが定義されている

### 27.2 Phase 2〜4

- [x] Personal MemoryのSecurity Boundaryが定義されている
- [x] LLM ProposalとTool Authorityが分離されている
- [x] Approval、Audit、Credential Isolationが定義されている
- [x] Agent / PC / BrowserのExecution Boundaryが定義されている
- [x] VoiceがAuthenticationまたはApproval代替になっていない
- [x] 未使用Security InfrastructureをPhase 1へ要求していない

### 27.3 Review Result

| Item | Value |
|---|---|
| Review Date | 2026-08-16 JST |
| Review Scope | Phase 1 Detailed Security / Phase 2〜4 Security Architecture |
| Cross-document Scope | API、Database、AI、Backend、Accepted ADR |
| Result | Approved |
| Required Correction | None |

Security Design Reviewでは、Phase 1の実装を分岐させる矛盾または未決定事項は確認されなかった。Phase 2〜4のImplementation-time Reconfirmationは将来Technologyの選定であり、Security Boundary自体の未決定とは扱わない。

---

## 28. Beginner Glossary

| Term | Meaning in Project Alice |
|---|---|
| Loopback | 同じMachine自身だけを指すAddress。`127.0.0.1` |
| Secret | 漏れると他者がAPIやResourceを利用できる秘密情報 |
| Least Privilege | 必要な操作だけを許可し、余分な権限を与えない考え方 |
| CORS | Browserが別OriginのResponseを読めるか制御する仕組み。認証の代わりではない |
| CSRF | Login済みBrowserを悪用して意図しないRequestを送らせる攻撃 |
| Prompt Injection | UserやWeb Content内の命令でAIの方針やTool権限を上書きしようとする攻撃 |
| Idempotency | 同じ送信をRetryしても処理を重複実行しない性質 |
| Audit Log | Aliceが何を提案・承認・実行したかを後から確認する記録 |
| Data Minimization | 目的に必要なDataだけを保存・送信する考え方 |

---

## 29. Official References

2026-08-16 JST時点で次の公式Documentationを確認した。

- OpenAI Production Best Practices  
  <https://developers.openai.com/api/docs/guides/production-best-practices>
- OpenAI Data Controls  
  <https://developers.openai.com/api/docs/guides/your-data>
- OpenAI Platform Permissions  
  <https://developers.openai.com/api/docs/guides/rbac>
- OpenAI Workload Identity Federation  
  <https://developers.openai.com/api/docs/guides/workload-identity-federation>
- AWS SDK for Java 2.x Credentials  
  <https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html>
- AWS IAM Security Best Practices  
  <https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html>

---

## 30. Summary

Phase 1はSingle User / Localhost / Authenticationなしとする。この判断はLoopbackとDeveloper MachineのSecurity Boundaryによってのみ成立し、LAN、CloudまたはRemote Access開始時には無効となる。

SecretはBackend InfrastructureだけがEnvironment Variableから取得し、Client、Source、Prompt、Persistence、LogまたはErrorへ公開しない。

Conversation ContentはDynamoDB Localへ保存され、選択された範囲がOpenAIへExternal Transferされる。OpenAI Requestでは`store=false`を必須とするが、Provider Retentionが常にゼロになるとはみなさない。

Phase 2〜4では、Personal Memory、Tool、Agent、PC、BrowserおよびVoiceを追加する。LLMは提案者であって実行権限者ではなく、Application側のPermission、Approval、ExecutorおよびAuditを必須Boundaryとする。
