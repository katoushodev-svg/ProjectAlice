# Project Alice - Security Design

## 1. Document Information

| Item | Value |
|---|---|
| Document | `security-design.md` |
| Project | Project Alice |
| Target | Phase 1 Security Detailed Design / Phase 2 Security・Logging・Observability Detailed Design / Phase 3〜4 Security Architecture |
| Status | Phase 1–2 Approved / Phase 3〜4 Security Architecture |
| Last Updated | 2026-09-01 JST |

本ドキュメントは、Project AliceにおけるNetwork Boundary、Authentication、Authorization、Secret、Personal Data、External Provider、Logging、Tool Permission、Agent ExecutionおよびIncident ResponseのSecurity Designを定義する。

Phase 1は実装へ直接投入できる詳細度まで確定する。Phase 2はPersonal Memory、Backup / Restore、Private LAN、LoggingおよびObservabilityを実装へ直接投入できる詳細度まで設計する。Phase 3〜4はArchitecture、責務、Trust Boundary、Permission、ApprovalおよびAudit要件を確定し、具体的なCloud Service、Identity Providerまたは暗号Library等は実装時再確認としてよい。

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

Phase 1〜2の実装を開始するうえでSecurity上の未決定事項は残さない。

次は対応Phaseまたは関連Designで確定する。

- Phase 3 Audit Data Model / Storage / Retention
- Phase 4 Backend-Agent ProtocolとCredential
- Cloud / Remote Authentication方式
- CI ProviderとSecurity Scan Artifact Retention

これらをPhase 1〜2実装時に推測して先行実装しない。Phase 2のLAN Access、Memory Retention / Deletion、Backup / Restore、Key、LoggingおよびObservabilityはSections 31〜44で確定済みである。

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

### 27.4 Phase 2 Detailed Review Result

| Item | Value |
|---|---|
| Review Date | 2026-09-01 JST |
| Review Scope | Phase 2 Security・Logging・Observability Detailed Design |
| Cross-document Scope | Requirements、Memory、API、AI、Database、Frontend、Test、Traceability |
| Decisions | SEC2-001〜038 Accepted |
| Findings | SEC-CR-001〜009 Resolved |
| Result | Completed — Passed |
| Implementation Readiness | Reviewed — Implementation Ready |

Focused Re-reviewでは、Purpose別Key、Provider直前Gate、AI Memory Budget、Log / Evidence Authority、Metric / Health、Actuator、Retention、Client PrivacyおよびTest Traceabilityを確認した。Critical、High、Medium、LowのOpen Findingは0件である。Phase 2実装時に新しい矛盾またはSecurity Boundary変更が発生しない限り、同一設計範囲の追加レビューを要求しない。

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

---

## 31. Phase 2 Local Device Access Security

### 31.1 Status and Scope

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-161〜API2-168 |
| Requirement | P2-FR-037、P2-NFR-007 |

Phase 2はSingle User / Local Developmentを維持する。Multi-user Account、Password Login、Cookie SessionまたはCloud Identity Providerを導入しない。ただし、Personal Memoryを実機iPhoneまたは別Machine上のDesktop Clientから利用する場合は、LoopbackのTrust Boundaryを越えるためAllowed Device AuthenticationとTLSを必須とする。

### 31.2 Security Profiles

| Profile | Boundary | Required Controls |
|---|---|---|
| `LOOPBACK_ONLY` | 同一Mac、`127.0.0.1` | Loopback Bind、Authenticationなし |
| `PRIVATE_LAN_SECURE` | 個人所有端末からPrivate LAN | 明示Bind、HTTPS only、Allowed Device Token、Firewall |

`LOOPBACK_ONLY`をDefaultとし、Private LANへ自動昇格しない。`PRIVATE_LAN_SECURE`ではConversationを含む全`/api/v1/**`を保護する。Memory Endpointだけを保護して自然言語Memory操作を未認証公開してはならない。

`0.0.0.0`、Plain HTTP LAN Listener、認証なしFallbackおよびTLS検証無効化を禁止する。Security Material不足、Certificate不正、Token Digest Key不足またはBind Address不正では起動をFail Closedとする。

### 31.3 Allowed Device Credential

- TokenはCSPRNGで256 bit以上とし、一端末一Credentialとする。
- ClientはiOS / macOS Keychain、Windows Credential Manager等のPlatform Secure Storageへ保存する。
- Flutter Application Data、Shared Preferences、Source Code、Build Log、Clipboard常駐またはCrash Reportへ保存しない。
- BackendはToken原文を永続化せず、専用KeyによるHMAC-SHA-256 DigestとContent-free Device Metadataだけを保持する。
- Token Digest KeyをTLS Private Key、Cursor Key、Deletion Guard KeyまたはBackup Passphraseと共有しない。
- Token発行・Revoke・RotateはLoopback Local Administrationから行い、未認証Pairing Endpointを公開しない。
- Token原文は発行時に一度だけ表示し、Command ArgumentやShell Historyへ含めない。

端末表示名、OS名、IP Address、User-AgentまたはCertificate情報だけをAuthentication根拠にしない。Revoke済み、未知、不一致および形式不正Tokenを外部で区別しない。

### 31.4 TLS and Host Boundary

Private LAN Server CertificateはClientが使用するHost名またはAddressをSANに持ち、FlutterはChain、Hostnameおよび有効期限を通常検証する。Local Development CAを使う場合も、対象端末へ明示Trust設定し、汎用的な`badCertificateCallback`やATS全許可を使用しない。

FirewallはAlice HTTPS PortをPrivate LAN Interfaceへ限定する。Public Interface、Guest Network、Internet Port ForwardingまたはVPN Exit Nodeへ公開しない。Network構成の変更は`development-environment.md`へ反映し、実機E2E前にSecurity Testを実施する。

### 31.5 Authentication Failure and Logging

AuthenticationはBody解析とUse Case実行より前に行う。失敗は`401 DEVICE_AUTHENTICATION_REQUIRED`、`WWW-Authenticate: Bearer realm="alice-local"`および`REAUTHENTICATE_DEVICE`へMappingする。不安全なTransportをApplication Boundaryで検出した場合は、CredentialやBodyを解析せず`403 SECURE_TRANSPORT_REQUIRED`とする。

Authorization Header、Token、Digest、TLS Private Key、認証失敗理由詳細およびPlatform Secure Storage値をResponse、Application Log、Access Log、Metric LabelまたはTest Reportへ出力しない。AuditはDevice ID、Operation Type、成功 / 失敗Category、時刻およびRequest ID等のContent-free Metadataに限定する。

### 31.6 Desktop Compatibility

Allowed DeviceはiPhoneに限定しない。同じAPI ContractとCredential LifecycleをDesktop版でも使用し、Secure Storage AdapterだけをPlatformごとに差し替える。

| Platform | Credential Storage |
|---|---|
| iOS | Keychain |
| macOS | Keychain |
| Windows | Credential Manager |
| 将来の対応Platform | OS標準の同等Secure Storage。未提供ならPrivate LAN利用不可 |

Backendと同じMac上のDesktop版は`LOOPBACK_ONLY`を利用できる。別Machineから接続するDesktop版は`PRIVATE_LAN_SECURE`を必須とする。Internet / Cloud Accessへ本Token方式をそのまま昇格せず、Section 19のPromotion Gateで再設計する。

---

## 32. Phase 2 Restore Temporary Storage Security

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-193〜API2-205、MD-114〜MD-122 |

Restore Temporary ArtifactはPermission制限した専用Directoryだけへ作成する。Symbolic Link、Hard Link、Path Traversal、User FilenameおよびDirectory外Pathを使用しない。Logical File SizeをQuotaへ計上し、Sparse File等で上限を回避させない。

Cancel、Failure、Disconnect、Expiry、Invalidation、Terminal、正常終了、Startup、5分以内の周期Sweeperおよび新Reservation前に同じIdempotent Cleanupを実行する。Process RestartでPlan Keyを失ったSealed Stateは再利用せず、PlanをInvalidatedとしてArtifactを削除する。

QuotaはActive Plan / Lease 1件、Retained 544 MiB、Working 768 MiBとする。Cleanup完了前に空き容量へ戻さない。削除失敗は`CLEANUP_PENDING`として扱い、Path、Filename、Archive Sizeまたは本文をResponse / Logへ出さない。

Metric / AuditはActive Count、Reserved Bytes、Cleanup Pending Count、Duration、Reason CodeおよびRequest ID等のContent-free情報だけを持つ。Passphrase、Archive Digest、Plan Key、Temporary Path、Memory本文またはUser FilenameをLabelへ使用しない。

---

## 33. Phase 2 Memory Relation Review Protection

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-206〜API2-220、MD-123〜MD-133、DB2-178〜DB2-192 |

Relation ReviewのCandidateとMutation PreviewはPersonal Dataを含む短命Sensitive Stateとして暗号化する。暗号KeyはReview Recordへ保存せず、Key Versionと失効可能な保護境界を用いる。Problem、Log、Metric、Trace、Idempotency FingerprintまたはError Detailへ本文、関連Memory ID一覧、Score、推論理由およびPreviewを出さない。

GET / Resolveには共通Security Profile、`Cache-Control: no-store`およびAllowed Device Authenticationを適用する。Resolveの`If-Match`とIdempotency-KeyはAuthorizationの代替ではなく、任意Target指定、Secret保存、Sensitivity引下げ、削除Guard回避または新Relation無視を許可しない。

Terminal、期限切れまたはInvalidation時はCandidate / Preview KeyとArtifactをCleanupし、Content-free Resultだけを最低24時間保持する。Cleanup失敗時はReviewを再利用可能に戻さず、内部Cleanup PendingとしてFail Closedに扱う。AuditはReview ID、Operation、Relation Type、Resolution、Status、件数および安全なReason Codeだけを記録できる。

Relation State Keyは専用Purpose-separated Provider Boundaryで管理し、Device、Cursor、Guard、Search、Idempotency、BackupまたはConfirmation Token Keyと共有しない。同時参照Versionは最大2個、暗号化に使う`WRITE_ACTIVE`は常に1個とする。Rotation後の新Reviewは新Versionだけを使い、既存Reviewは固定Versionのまま30分以内の失効とCleanupへ収束させる。

旧VersionはActive、PreparingおよびCleanup Pending参照数がすべて0で、Version別Reference Queryも空であることを確認するまで削除しない。Key欠落、未知Version、Provider一時不能またはAEAD失敗では、現在Key・別Version・平文・AI再生成へFallbackせず、Reviewを利用不能にしてCleanupする。Startupと5分以内のSweeperは最大2 VersionのEncrypt / Decrypt CapabilityをProbeし、失敗時はRelation Review Create / GET / ResolveだけをFail Closedにする。

Review Cleanupは暗号化Stateを先に利用不能化し、Strong確認後の一つのTransactionだけがReview Release Marker、Global Active Count、Key Version Count / Reference、Active DirectoryおよびContent-free Resultを確定する。Count解放結果が不明な場合は同じReview ID / Cleanup Epochを照合し、再試行で二重減算しない。Active Directoryには本文、Target ID、Ciphertext、DigestまたはReasonを保存しない。

---

## 34. Phase 2 Security・Logging・Observability Detailed Design

### 34.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted |
| Scope | Personal Memory、Search、AI Context、Backup / Restore、Private LAN、Logging、Metric、Health、Recovery |
| Requirements | P2-FR-001〜065、P2-NFR-001〜008 |
| Domain / API | MD-001〜133、API2-001〜234 |
| Persistence | DB2-001〜243 |

本Section以降は、Phase 2で既にAcceptedとなったDomain、APIおよびDatabaseのSecurity Invariantを、実装者が一意に適用できる横断Contractへ統合する。Algorithm、Field、RetentionまたはFailure Semanticsを所有Documentと重複定義する場合は、各技術詳細を所有Documentへ委譲し、本Documentは許可・禁止・Fail Closed Boundaryを所有する。

Phase 2ではSingle User前提を維持するが、Single Userを「認証・暗号・Privacy保護が不要」という意味に使用しない。Local Machine、Private LAN Device、OpenAI、DynamoDB、Temporary StorageおよびExport済みArchiveを別Trust Boundaryとして扱う。

### 34.2 Security Invariants

1. Memory本文を通常Log、Metric Label、Health Detail、Problem DetailsまたはOperation Bindingへ複製しない。
2. Secretは明示依頼でもPersonal Memoryへ保存せず、AI Context、Backup、Search Projection、RevisionまたはGuardへ転記しない。
3. Sensitive MemoryはAutomatic Captureせず、回答へ直接必要で、回答利用設定が有効な場合だけProvider Context候補にできる。
4. AIが提案した分類、Sensitivity、Relationまたは保存判断をSecurity Decisionとして信用しない。
5. Key、Digest、Cursor、Token、CiphertextまたはInternal ScoreをClientへ公開しない。
6. 削除・Restore・Relation・Projectionの状態が不明な場合、古い状態や推測結果へFallbackしない。
7. Observability障害をDomain Dataの保存先で代替せず、Domain DataをObservabilityへ複製して調査可能性を補わない。
8. Desktop版を含む全Clientへ同じBackend Security Contractを適用し、Platform差はSecure StorageとTransport Adapterへ隔離する。

---

## 35. Phase 2 Data Classification and Handling

### 35.1 Classification

| Class | Examples | Security Meaning |
|---|---|---|
| `OPERATIONAL_METADATA` | Duration、Count、Outcome、Safe Reason Code | 本文を含まない運用情報。組合せによる推測にも配慮する |
| `SECURITY_METADATA` | Device ID、Operation ID、Key State、Plan Status、Request ID | 本文を含まなくてもSecurity状態や行動を示す内部情報 |
| `PERSONAL_CONTENT` | 通常Memory本文、Category、Conversation由来Candidate | ユーザー固有のPersonal Data |
| `SENSITIVE_PERSONAL_CONTENT` | 健康、悩み、家族事情等のSensitive Memory本文 | 利用目的とExternal Transferをさらに限定するPersonal Data |
| `SECRET` | API Key、Password、Passphrase、Device Token、Private Key、Confirmation Token原文 | 漏えい時に権限・暗号・本人確認を破る情報 |

`Content-free`は独立Classではない。本文を含まないOperation IDやDevice IDも`SECURITY_METADATA`であり、Log、RetentionおよびAccessを無制限にしてよいことを意味しない。

### 35.2 Handling Matrix

| Destination / Use | Operational | Security Metadata | Personal Content | Sensitive Content | Secret |
|---|---:|---:|---:|---:|---:|
| Authoritative DynamoDB | Yes | 必要なRecordだけ | Yes | Yes | 原文No |
| Search Projection | Aggregate / State only | Version等だけ | Digest / ID Projection | Digest / ID Projection | No |
| OpenAI Context | 必要最小限 | Public Contract上不要 | Direct relevance時 | Direct necessity時だけ | No |
| Encrypted Memory Backup | Manifest / Resultの必要分 | Internal Operation StateはNo | Yes | Yes | No |
| Application Log | Allowlistのみ | Request相関の必要分 | No | No | No |
| Metric / Health | Aggregateのみ | Bounded Stateのみ | No | No | No |
| Usage Transparency | Result / Reason | Memory ID / Versionの必要分 | 現在Resourceから表示 | 現在Resourceから表示 | No |
| Crash / Analytics | Phase 2 Default無効 | No | No | No | No |

Search ProjectionのDigest、Token PageおよびPostingはPersonal Contentの代替表示やBackup Sourceに使用しない。暗号化済みRelation Previewも`SENSITIVE_PERSONAL_CONTENT`として扱い、Ciphertextであることを理由に通常LogやExportへ出さない。

### 35.3 Secret Detection and Rejection

Memory CandidateがSecretに該当する、または安全に判定できない場合は、次の順序で処理する。

1. Memory保存、Revision作成、Search Projection、Guard生成およびBackup対象化を行わない。
2. 外部Responseは安定した`SENSITIVE_INFORMATION_NOT_STORABLE`等のPublic Codeと一般説明だけを返す。
3. Secret値、部分文字列、形式、検出位置、FingerprintまたはProvider自由文理由をLog、Metric、Problem、Usage TraceまたはAuditへ出さない。
4. 明示保存RequestでもRuleを緩和しない。
5. Secret検出結果をモデルへ再送して説明文を生成しない。

元のUser Messageは`conversation` Featureが所有するConversation Historyであり、Memory拒否だけを理由に自動削除しない。ただしMemory側へ再複製せず、将来Conversation削除を追加する場合は別RequirementとSecurity Reviewを行う。

### 35.4 Backup and External File Boundary

Memory BackupはMemory本文とPreferencesだけを承認済みArchive Contractで暗号化する。Allowed Device、Key Control、Guard、Usage Trace、Deletion Receipt、Operation Binding、Relation State、Search ProjectionおよびSecurity Eventを含めない。

Export成功後のFileはAliceの管理外となる。UIは保存先、共有、Cloud SyncまたはOS Backupによる二次RetentionをAliceが削除できないことを明示する。BackendはExport済みFileを追跡するためのPath、FilenameまたはArchive Digestを永続化しない。

---

## 36. AI Provider Context Security

### 36.1 Memory Is Untrusted Data

Memory本文はユーザー情報であり、System / Developer Instruction、Tool Approval、Authorization、Permission Policyまたは実行命令ではない。Memory内の「以前の指示」「承認済み」「この後のRuleを無視」等をInstructionとして解釈しない。

Context BuilderはMemoryを専用の構造化Data Blockへ入れ、PromptのInstruction領域と分離する。XML / JSON等の表現を使用しても、DelimiterだけをSecurity Boundaryとみなさず、Application側のPermissionとValidationを継続する。

### 36.2 Final Context Authorization Gate

Search Ranking後、Provider Request構築の直前に各MemoryをAuthoritative Repositoryから再取得し、次をすべて検証する。

- `ACTIVE`で、削除・Cleanup・Recovery Fenceの対象ではない
- 検索候補の`memoryId`とVersionがAuthoritative Resourceに一致する
- Answer-use Preferenceが有効
- Current / Historicalの利用目的が質問と一致する
- Sensitive Memoryは質問へ直接必要である
- Secret、保存禁止、Corruptまたは未対応Schemaではない
- Reset Point、Deletion GuardおよびRelation Reviewの未確定状態を回避していない
- Context件数、1,500 Token推定、12 KiBの全上限内

一つでも不成立ならそのMemoryを除外する。Authoritative判定自体が不可能な場合、検索結果をそのまま使用せず、Memoryなしまたは検証済みの安全な部分集合で会話を継続し、Usage Traceを`PARTIAL`または`UNAVAILABLE`にする。

### 36.3 Sensitive Memory Use

Sensitive Memoryは次をすべて満たす場合だけProviderへ送信できる。

1. 明示保存済みである。
2. Answer-use Preferenceが有効である。
3. 現在のUser Requestへ直接必要である。
4. 通常MemoryまたはUser Requestだけでは同等の回答ができない。
5. Usage TraceへMemory ID、Versionおよび安全なInclusion Reasonを記録できる。

一般的なPersonalization、会話を親しみやすくする目的、将来役立つ可能性またはScoreの高さだけでは直接必要とみなさない。Sensitive本文、質問、Model自由文理由または内部ScoreをTraceへ保存しない。

### 36.4 Provider Transfer and Failure

- OpenAI Requestでは`store=false`を必須とする。
- Provider Request / Response Body、Memory Context BlockおよびRaw Error BodyをHTTP Wire Logへ出さない。
- Providerへ送るMemoryは最終選択済み本文だけとし、検索候補40件やRevisionを一括送信しない。
- Provider障害時に別Provider、Local Modelまたは古いResponseへ自動Fallbackしない。
- Provider Data Usage、Abuse Monitoring Retention、Eligible ModelおよびRegional ProcessingはPhase 2実装開始時とProvider変更時に公式情報で再確認する。

Usage Trace保存失敗だけで正常回答を失敗へ変えないが、`NOT_RECORDED`を明示し、Content-free Failure EventとMetricを残す。Trace失敗時にMemory本文SnapshotをLogへ書いて代替しない。

---

## 37. Phase 2 Cryptographic Key and Secret Registry

### 37.1 Purpose-separated Registry

| Purpose Code | Primitive / Material | Persistent Data | Rotation / Failure Scope |
|---|---|---|---|
| `CONVERSATION_CURSOR_SIGNING` | HMAC-SHA-256 Key | Phase 1 Cursor | Phase 1 Contractを維持 |
| `MEMORY_CURSOR_SIGNING` | HMAC-SHA-256 Key | Memory List / Search Cursor | 対象QueryをFail Closed |
| `MEMORY_SEARCH_TERM` | HMAC-SHA-256 Key | Term Digest / Posting | Search Capabilityだけ閉じる |
| `MEMORY_DELETION_GUARD` | HMAC-SHA-256 Key | Guard Fingerprint | Automatic Capture / Guard判定を閉じる |
| `MEMORY_IDEMPOTENCY` | HMAC-SHA-256 Key | Client Key Digest | 対象Mutationを閉じる |
| `MEMORY_REQUEST_INTEGRITY` | HMAC-SHA-256 Key | Canonical Request Digest | 対象Mutationを閉じる |
| `MEMORY_CONFIRMATION_TOKEN` | HMAC-SHA-256 Key | Token Digest | Confirm / Executeを閉じる |
| `MEMORY_USAGE_TRACE_INTEGRITY` | HMAC-SHA-256 Key | Trace Digest | Trace記録を閉じ、回答は継続可能 |
| `ALLOWED_DEVICE_TOKEN_DIGEST` | HMAC-SHA-256 Key | Device Token Digest | `PRIVATE_LAN_SECURE`起動をFail Closed |
| `MEMORY_RELATION_STATE_AEAD` | AES-256-GCM Key | Candidate / Preview Ciphertext | Relation Reviewだけ閉じる |
| `BACKUP_PASSPHRASE_DERIVED` | Argon2id Derived Key | 永続化しない | Request Scopeだけ。再利用・Server保管禁止 |
| `PRIVATE_LAN_TLS_PRIVATE_KEY` | TLS Private Key | OS / File Secret Boundary | `PRIVATE_LAN_SECURE`起動をFail Closed |

異なるPurpose Codeで同じRaw Key Byteを共有しない。Environment Variable名だけを分けて同じ値を設定することも禁止する。Backup Passphrase、Derived KeyおよびPlan KeyをRegistryの長期Keyへ昇格しない。

### 37.2 Provider Boundary and Metadata

Application / DomainはKey Material、Environment Variable名、File Path、Secret HandleまたはProvider SDKを参照せず、Purpose別Portから必要なSign、Verify、Digest、EncryptまたはDecrypt Capabilityだけを利用する。

安全に保持できるKey MetadataはPurpose Code、Key Version、Lifecycle State、Activated At、Retired AtおよびContent-free Reference Countに限定する。Key Material、Key Fingerprint、Provider Path、Secret Handle、Derived ValueまたはKnown-answer Test VectorをDynamoDB、Backup、Log、MetricまたはAPIへ保存しない。

### 37.3 Startup Capability Gate

Startup時に有効化されるCapabilityごとに、Algorithm、Key長、Active Version数、Write Version、Purpose BindingおよびSign / VerifyまたはEncrypt / DecryptのKnown-answer Probeを実行する。Probeは本番Dataを使用しない。

| Gate Failure | Required Behavior |
|---|---|
| LAN TLS / Device Key | `PRIVATE_LAN_SECURE`を起動しない |
| Cursor Key | 対象Pagination / Search EndpointをUnavailableにする |
| Search Key | 管理検索はService Error、回答はMemoryなし、Relation比較は不完全結果を返さない |
| Guard Key | Automatic CaptureとGuard依存MutationをFail Closed。通常Readは継続 |
| Idempotency / Request / Confirmation Key | 対象Mutation、ConfirmまたはExecuteをFail Closed |
| Trace Integrity Key | 回答は継続、Traceは`NOT_RECORDED`、Failure Metricを記録 |
| Relation AEAD Key | Section 33どおりRelation ReviewだけFail Closed |

不足Keyを別Purpose Key、Default値、平文Hash、現在Write Key、古いProcess CacheまたはAI再生成で補わない。Key復旧後もUnknown Operationを新しいOperationとして再実行せず、Authoritative Recordを照合する。

### 37.4 Rotation and Retirement

Version付きPhase 2 Keyは同時Read可能Versionを最大2個、Write Activeを常に1個とする。新VersionはCapability Probe後にWrite Activeへ切り替え、旧Versionは参照0件、対象Projection / Credential / Guard / ReviewのMigrationまたは明示再発行完了、Version別Strong Queryが空であることを確認してからRetireする。

Digestだけから原文を復元・推測して新VersionへMigrationしない。SearchはAuthoritative Memoryから再構築し、Device Credentialは再発行し、Guardは元の非復元情報だけでは安全にMigrationできない場合に旧Keyを保持する。Key削除結果不明はRetire完了と扱わない。

---

## 38. Phase 2 Logging Contract

### 38.1 Log Channels and Authority

| Channel | Purpose | Authority | Default Retention |
|---|---|---|---|
| Application Log | 障害調査、処理Outcome、Latency | Domain / Operation結果のSource of Truthではない | 7日、合計200 MB |
| Security Event Log | Device・Key・Backup・Restore・削除等のContent-free Security Event | 証跡補助。Domain状態のSource of Truthではない | 30日、合計100 MB |
| Memory Usage Trace | 回答時Memory利用のUser-facing Transparency | Usage表示のAuthoritative Record | Assistant Messageと同期間 |
| Dynamo Operation Resource | Retry、Partial / Unknown、Cleanup、Result照合 | Operation結果のAuthoritative Record | 各Accepted Retention |

Phase 2で汎用Audit Tableを新設しない。Tool実行AuditはPhase 3の別設計とする。Security Event Log書込み失敗を補うためにMemory本文やSecretをApplication Logへ出さない。

### 38.2 Common Application Event Schema

Application Logは次のAllowlist Fieldだけを構造化出力できる。

| Field | Rule |
|---|---|
| `timestamp` | JST Offset付き・ミリ秒精度 |
| `level` | `INFO`、`WARN`、`ERROR` |
| `eventName` | Version管理されたStable Code |
| `requestId` | Request相関。Metric Labelにはしない |
| `feature` | `conversation`、`memory`等のBounded Code |
| `operation` | Stable Operation Code |
| `outcome` | `SUCCESS`、`NO_CHANGE`、`PARTIAL`、`UNKNOWN`、`REJECTED`、`FAILED`等のBounded Code |
| `safeReasonCode` | Allowlistされた非機密Code。自由文禁止 |
| `durationMs` | 0以上。未取得を0で補わない |
| `attempt` | Bounded Retry Count |
| `itemCount` / `byteCount` | 必要な集計値。IDや内訳を伴わない |
| `profile` | `LOOPBACK_ONLY`または`PRIVATE_LAN_SECURE` |

Application Logへ`memoryId`、Revision ID、Plan ID、Review ID、Guard ID、Device ID、Operation ID、Assistant Message ID、Cursor、Digest、Key Version、Category、Sensitivity、Search TermまたはUser FilenameをDefaultで出さない。Authoritative Operation調査はRequest IDから安全な管理手順で行い、通常LogをData Indexにしない。

### 38.3 Security Event Schema

Security Event LogはCommon Fieldに加え、次だけを許可する。

- Content-freeな`operationId`、`planId`、`reviewId`または`deviceId`のうちEvent対象を一つ
- `eventFamily`: `DEVICE`、`KEY`、`MEMORY_DELETE`、`BACKUP`、`RESTORE`、`RELATION`、`RECOVERY`、`CONFIGURATION`
- `actorType`: `LOCAL_ADMIN`、`ALLOWED_DEVICE`、`SYSTEM`
- 件数、Duration、Safe Result / Reason Code

Security EventへMemory ID一覧、Target一覧、本文、Category、Sensitivity、Token、Digest、Key Version、Filename、Path、Archive Size、IP Address、User-AgentまたはException Messageを含めない。Wrong PassphraseとArchive破損は同じReason Familyにする。

### 38.4 Event Families

最低限、次の開始・Terminal EventをStable Event Nameとして持つ。

| Family | Events |
|---|---|
| Memory Mutation | register、update、confirm、delete、delete-plan、re-registration |
| Context | retrieval、authorization、provider-transfer、usage-trace-record |
| Backup / Restore | export、inspect、plan、confirm、execute、cancel、cleanup |
| Relation | create、resolve、invalidate、cleanup、slot-release |
| Device | issue、authenticate-failure、rotate、revoke |
| Key | startup-gate、rotation、retirement、capability-loss、recovery |
| Projection | publish、lag-threshold、repair、integrity-failure |
| Recovery | fence-enter、reconcile、finalize、fence-release |

開始EventにTerminal結果を推測して入れない。`UNKNOWN`、`PARTIAL`および`CLEANUP_PENDING`を`FAILED`または`SUCCESS`へ丸めない。

### 38.5 Exception and Redaction Boundary

Infrastructure AdapterはProvider / AWS / Filesystem ExceptionをSafe Exceptionへ変換し、上位へRaw Response Body、Request URI Query、Header、Item DumpまたはPathを渡さない。Expected FailureはStable Codeで記録し、Unexpected FailureだけSanitized Stack Traceを`ERROR`へ出せる。

Logger引数にDomain Entity、DTO、SDK Object、Configuration Object、HTTP BodyまたはFile Objectを直接渡さない。MDCはRequest / Async処理終了時に必ずClearし、別RequestへRequest IDを漏らさない。Access LogはQuery String、Authorization Header、Request / Response BodyおよびMultipart Filenameを記録しない。

### 38.6 Local File Protection

- `backend/logs/`はRepository対象外、Ownerだけが読めるPermissionとする。
- Active / Rotated FileをMemory Backup、Support BundleまたはCrash Reportへ自動添付しない。
- Application Logは日次Rotation、7日、200 MB、Security Event Logは日次Rotation、30日、100 MBを同時上限とする。
- Retention超過Fileは通常Rotationで削除し、削除失敗をContent-free Metricへ記録する。
- Debug Modeでも禁止Data Ruleを緩和しない。

---

## 39. Metrics, Health and Observability

### 39.1 Metric Rules

Metric名、Tag KeyおよびTag値は固定Registryで管理する。Tagは次のBounded Dimensionだけを許可する。

- `feature`
- `operation`
- `outcome`
- `safe_reason`
- `profile`
- `capability`
- `state`

`requestId`、Domain ID、Device ID、Memory ID、Key Version、Category、Sensitivity、Search Term、Digest、Provider Request ID、Exception Class / Message、PathまたはFilenameをTagにしない。件数、Byte、Duration、AgeおよびVersion数はTagではなくCounter、GaugeまたはHistogramのValueとする。

### 39.2 Required Metric Registry

| Metric | Type | Allowed Dimensions |
|---|---|---|
| `alice_memory_operations_total` | Counter | operation、outcome、safe_reason |
| `alice_memory_operation_duration_seconds` | Histogram | operation、outcome |
| `alice_memory_context_candidates` | Histogram | outcome |
| `alice_memory_context_included` | Histogram | outcome |
| `alice_memory_usage_trace_failures_total` | Counter | safe_reason |
| `alice_memory_projection_pending_age_seconds` | Gauge / Histogram | state |
| `alice_memory_projection_failures_total` | Counter | safe_reason |
| `alice_memory_cleanup_pending` | Gauge | capability |
| `alice_memory_cleanup_oldest_age_seconds` | Gauge | capability |
| `alice_memory_recovery_fence_active` | Gauge | state |
| `alice_memory_restore_reserved_bytes` | Gauge | state |
| `alice_memory_restore_working_bytes` | Gauge | state |
| `alice_memory_relation_active` | Gauge | state |
| `alice_memory_allowed_devices` | Gauge | state |
| `alice_memory_security_capability` | Gauge | capability、state |

Memory総件数、Category別件数、Sensitive件数、検索語頻度または特定Memory利用回数をMetricとして公開しない。必要なUI Countは認証済みAPI ResponseまたはAuthoritative Controlから取得し、Observabilityへ転用しない。

### 39.3 Health Components

| Component | Checks | No-check Rule |
|---|---|---|
| `memoryRepository` | 必須Table / Control SchemaへBounded Read可能 | Scan、本文列挙をしない |
| `memoryKeyCapabilities` | 有効Capability Gateの集約状態 | Key Material / Versionを表示しない |
| `memoryProjection` | Pending AgeとWorker状態 | Search Term / Memory IDを表示しない |
| `memoryTemporaryStorage` | Directory Permission、Quota Control、Cleanup状態 | Path / Filenameを表示しない |
| `memoryRecovery` | Fence、Unknown、Cleanup Pendingの集約状態 | Operation ID / Targetを表示しない |

LivenessはProcessがEvent Loop / Threadを処理できるかだけを判定し、DynamoDB、OpenAIまたはFilesystem障害で再起動Loopを作らない。Readinessは有効Profileの必須Capabilityを確認する。部分Capability障害は全Backendを無条件`DOWN`にせず、影響Capabilityを`DEGRADED`またはUnavailableにする。

Health ProbeはOpenAI呼出し、Memory本文読取、Decrypt、KDF、Backup生成、全Table ScanまたはMutationを行わない。外部ResponseのHealth DetailはDefault非表示とする。

### 39.4 Management Endpoint Boundary

Phase 2でもManagement ListenerはLoopbackへ固定し、`/actuator/health`と`/actuator/metrics`だけをAllowlistする。`PRIVATE_LAN_SECURE`のApplication ListenerへActuatorを公開しない。Desktop版が別Machineから利用する場合もManagement EndpointをClientへ公開せず、通常APIの安全なRecovery表示だけを使用する。

`env`、`configprops`、`heapdump`、`loggers`、`mappings`、`threaddump`、その他任意Debug EndpointおよびWildcard Exposureを禁止する。Metric Exporter、Remote APMまたはCentral Log転送はPhase 2 Defaultで導入しない。

### 39.5 Thresholds and Degradation

| Signal | Initial Objective / Threshold | Required State |
|---|---|---|
| Projection Pending Age p95 | 5秒以下 | 超過が5分継続で`DEGRADED` |
| Projection Pending Age maximum | 30秒以下 | 超過が5分継続で`DEGRADED` |
| Cleanup Pending oldest age | 10分以下 | 超過で対象Capabilityを`DEGRADED` |
| Recovery Fence age | 5分以下 | 超過でMutation Capabilityを`DEGRADED` |
| Temporary retained / working usage | 各上限の90%未満 | 90%以上で`DEGRADED`、上限到達で新処理拒否 |
| Required Key Capability | 常時Available | 不成立を即時Capability Unavailable |
| Usage Trace write | Best effort、継続失敗なし | 5分継続失敗でTransparencyを`DEGRADED` |

Threshold超過を理由にDataを自動削除、上限を自動拡張、Fenceを強制解放、旧Keyを削除またはPartial結果を成功へ変更しない。Phase 2はLocal運用のためPager / Remote Alertを必須とせず、Health、MetricおよびSecurity Eventで診断可能にする。

### 39.6 Observability Failure

Metric記録失敗だけで正常なDomain TransactionをRollbackしない。Security Event Log失敗もAuthoritative Dynamo Operation結果を変更しない。ただしObservability初期化不能、Log Directory Permission不正またはMetric Registry不整合はStartup WarningとHealth `DEGRADED`にし、禁止Dataが出力される可能性があるConfigurationはStartup Fail Closedとする。

---

## 40. Security Event, Usage Trace and Operation Evidence

### 40.1 Authority Separation

| Question | Authoritative Source |
|---|---|
| 現在のMemory本文・状態は何か | PersonalMemory Aggregate |
| 回答時にどのMemory ID / VersionをContextへ含めたか | MemoryUsageTrace |
| Delete / Restore / Relationの結果は確定したか | Dynamo Operation Resource / Receipt |
| Device CredentialはActiveか | AllowedDeviceCredential |
| 暗号KeyをRetireできるか | Purpose別Key Control / Reference |
| いつSecurity操作を試みたか | Security Event Log（補助証跡） |

Logだけを根拠にDelete、Restore、Key Retirement、Count補正、Fence解放またはRetry成功を確定しない。Security Eventが欠落していてもAuthoritative StateからRecoveryでき、Authoritative Stateが不明ならLogに成功があっても`UNKNOWN`として扱う。

### 40.2 Minimum Security Events

次はContent-free Security Eventを必須とする。

- Allowed Device issue / rotate / revokeと認証失敗集約
- Key startup gate、rotation start / complete、retirement、capability loss
- Delete Plan confirm / execute / partial / unknown / recovery finalize
- Backup export start / complete / failed、Restore inspect / execute / cancel / cleanup
- Relation create / resolve / invalidate / cleanup
- Recovery Fence enter / release、Cleanup Pending閾値超過
- Security Profile変更と起動拒否

Memory Read、一覧表示または検索のたびにSecurity Eventを残すことはPhase 2で要求しない。利用透明性はUsage Trace、障害観測はApplication Log / Metricで担い、Personalな利用履歴を過剰に蓄積しない。

### 40.3 Integrity and Local Trust Limitation

Security Event LogはOwner-only Permission、RotationおよびChannel分離を持つが、Phase 2ではHash Chain、Remote Write-once Storageまたは第三者署名を導入しない。同じMachineの管理者権限を持つ攻撃者に対する完全な改ざん耐性は保証しない。Cloud、Multi-user、Tool実行またはCompliance Auditへ昇格する場合はSection 19のPromotion Gateで再設計する。

---

## 41. Retention, Cleanup and Incident Boundary

### 41.1 Retention Registry

| Data | Phase 2 Retention |
|---|---|
| Active / Resolved PersonalMemory | Userが削除するまで。自動TTLなし |
| MemoryRevision | 親Memoryが存在する間。親削除時に本文を含めてCleanup |
| MemoryPreferences | Userが明示Resetするまで。Memory削除と分離 |
| Deletion Guard / Reset Point | TTLなし。本文復元不能なSecurity Boundaryとして保持 |
| MemoryUsageTrace | 対応Assistant Messageと同期間。Memory Backup対象外 |
| Terminal Content-free Operation Result | 最低24時間 |
| Relation / Restore Sensitive Temporary State | Terminal、Invalidation、Expiry、CancelまたはCleanup時に削除 |
| Revoked Device Credential Metadata | 最低30日後にLogical Expiration / TTL可能 |
| Application Log | 7日、合計200 MB |
| Security Event Log | 30日、合計100 MB |
| Export済みBackup | Alice管理外。自動削除不可 |

Memory RetentionをMetric、Disk Pressure、Log RotationまたはDynamoDB TTLで自動短縮しない。TTLは物理削除の補助であり、API、SecurityまたはQuota解放の根拠にしない。

### 41.2 Cleanup Ordering

Sensitive Artifact、Pending Worker、Retry、CacheおよびKey Materialを先に利用不能化し、Strong確認後だけCount、Slot、Quota、FenceまたはKey Referenceを解放する。Cleanup結果不明では同じOperation ID / Epochへ収束させ、別Operationとして二重解放しない。

Startup、新Reservation前および5分以内の周期SweeperでRestore、Relation、Export、Deletion Fence、Projection IntentおよびTemporary ManifestをBounded Queryする。通常Runtime ScanまたはFilesystem全域Scanを使用しない。

### 41.3 Incident Response

| Incident | Immediate Response |
|---|---|
| Device Token漏えい疑い | Local AdministrationでRevoke / Rotate、LAN Profileを必要なら停止 |
| Key Material欠落 / 不正 | 対象CapabilityをFail Closed、別Key Fallback禁止、Reference照合 |
| Memory本文Log混入 | Log出力停止、対象File隔離・削除、原因修正、SecretならRotate |
| Backup Passphrase / Archive誤共有 | Aliceから回収不能であることを案内、必要なら新Backupを作成 |
| Temporary Artifact削除失敗 | `CLEANUP_PENDING`、Slot / Quota維持、新処理拒否 |
| Operation結果不明 | Recovery Fence維持、Authoritative StateをStrong Reconcile |
| Provider Data Policy変更 | 新規External Transferを再評価し、必要ならMemory Context利用停止 |

Incident調査のために禁止Data Loggingを一時有効化しない。必要な再現はSynthetic Data、Bounded Diagnostic CountおよびSafe Reason Codeで行う。

---

## 42. Frontend, Desktop and Client Privacy

### 42.1 Shared Client Contract

iOS、macOS、Windowsおよび将来Desktop版は同じAPI、ETag、Idempotency、Confirmation、`Cache-Control: no-store`、Allowed Device LifecycleおよびSafe Error Contractを使用する。Desktop版専用にMemory本文、Token、PassphraseまたはPreviewをLocal Fileへ永続Cacheしない。

同じBackend Machine上のDesktop版は`LOOPBACK_ONLY`を利用できる。別MachineのDesktop版は`PRIVATE_LAN_SECURE`を利用し、Credential Manager等のOS標準Secure StorageがないPlatformではPrivate LAN接続を提供しない。

### 42.2 Client Data Handling

- Memory本文、Search Result、Restore PreviewおよびSensitive Explanationは画面表示中のMemory内だけをDefaultとする。
- Allowed Device TokenはPlatform Secure Storageだけへ保存する。
- Backup PassphraseとConfirmation TokenはRequest Scope / Review Scopeだけで保持し、Application Backup、Analytics、Clipboard履歴またはCrash Reportへ保存しない。
- Memory本文やPassphraseを自動Clipboard Copyしない。Userの明示CopyはOS Clipboard Boundaryへ移ることをUI上のSensitive操作として扱う。
- Screenshot防止をSecurity保証とみなさない。OS Screenshot、Screen Recording、Notification PreviewまたはAccessibility Serviceへ表示内容が渡る可能性をDocumentationへ明示する。
- Crash Reporting、Session Replay、AnalyticsおよびRemote Log UploadはPhase 2 Defaultで無効とする。導入前にData Field、Redaction、Retention、Region、ConsentおよびDeletionを別Reviewする。

### 42.3 Reconnect and Stale UI

Clientは再接続後にAuthoritative Resource / Plan / Usage Traceを再取得し、Memory本文Snapshot、古いETag、古いConfirmation Tokenまたは推測Statusから成功を表示しない。`PARTIAL`、`UNKNOWN`、`CLEANUP_PENDING`または`NOT_RECORDED`を一般的な成功Toastへ変換しない。

---

## 43. Phase 2 Security Configuration Contract

### 43.1 Configuration Ownership

Configurationは型付きConfiguration ObjectへBindingし、Startup時にProfile、Algorithm、Key長、Version数、Directory、Permission、Quota、Retention、Management ExposureおよびMetric Registryを検証する。Business LogicからEnvironment Variableを直接参照しない。

禁止するConfiguration:

- Security Ruleを無効化する`allowSecretsInMemory`、`logMemoryContent`等の汎用Switch
- `0.0.0.0`、Plain HTTP LAN、Wildcard Actuator、Certificate検証無効化
- 同じKey値のPurpose間共有またはDefault KeyへのFallback
- Retentionを最低値未満へ短縮する設定
- Cleanup確認なしにSlot、Quota、CountまたはFenceを解放する設定
- Context件数 / Byte上限を無制限にする設定
- Crash / Analyticsを無同意で有効化する設定

### 43.2 Profile Validation

| Profile | Required Startup Material |
|---|---|
| `LOOPBACK_ONLY` | Loopback Bind、Memory Table / Control Schema、利用するMemory Key Capability、Owner-only Log Directory |
| `PRIVATE_LAN_SECURE` | 上記に加え、明示Private Address、TLS Certificate / Key、Allowed Device Digest Key、Firewall / Host Configuration |
| `test` | 本番Secretを使わないFake Key Provider、Synthetic Data、External Transfer無効 |

未使用CapabilityのKeyをPhase 2起動全体の必須条件にしない。一方、有効化したCapabilityのMaterial不足をWarningだけで継続しない。Startup Logは不足したPurpose CodeとSafe Reasonだけを出し、Environment Variable名、Path、Key VersionまたはProvider Handleを出さない。

---

## 44. Verification, Traceability and Accepted Decisions

### 44.1 Required Security Test Registry

| Test ID | Required Verification |
|---|---|
| P2-SEC-TC-001 | Data Classification別のPersistence / Log / Context / Backup許可Matrix |
| P2-SEC-TC-002 | Secret明示保存拒否と全出力Channel非露出 |
| P2-SEC-TC-003 | Sensitive Automatic Capture拒否とDirect Necessity Gate |
| P2-SEC-TC-004 | Provider直前のAuthoritative Version / State / Preference再検証 |
| P2-SEC-TC-005 | Memory内InstructionがTool権限・System指示へ昇格しない |
| P2-SEC-TC-006 | Purpose別Key共有拒否、Startup Gate、最大2 Version、Retirement |
| P2-SEC-TC-007 | Key欠落 / AEAD / HMAC失敗のCapability限定Fail Closed |
| P2-SEC-TC-008 | Application / Security Log Field Allowlistと禁止Data Capture |
| P2-SEC-TC-009 | Log Rotation、Permission、MDC Clear、Access Log Redaction |
| P2-SEC-TC-010 | Metric Tag Cardinalityと禁止Dimension検出 |
| P2-SEC-TC-011 | Health Detail非公開、No-content Probe、Actuator Loopback限定 |
| P2-SEC-TC-012 | Projection / Cleanup / Fence / Quota閾値のDegraded遷移 |
| P2-SEC-TC-013 | Usage Trace、Operation Resource、Security EventのAuthority分離 |
| P2-SEC-TC-014 | Retention、Logical Expiration、TTL遅延、Cleanup順序 |
| P2-SEC-TC-015 | Backup除外SetとExport後外部Boundary |
| P2-SEC-TC-016 | Restore Temporary Path、Link、Quota、Cleanup、Restart |
| P2-SEC-TC-017 | LAN TLS、Device Token、Revoke / Rotate、Body解析前認証 |
| P2-SEC-TC-018 | iOS / macOS / Windows Secure Storage Adapter Contract |
| P2-SEC-TC-019 | Crash / Analytics Default無効とClient no-store |
| P2-SEC-TC-020 | Security Configuration禁止値とStartup Fail Closed |

Test実行Framework、Source LayoutおよびCI Gateは`test-design.md`をSource of Truthとする。本Registryは承認後に同Documentへ一対一で統合し、各Test IDへUnit、Controller、Port Contract、DynamoDB Local、Filesystem、FlutterおよびConfiguration Test Layerを割り当てる。

### 44.2 Requirement Traceability

| Requirement | Security Design Coverage |
|---|---|
| P2-FR-001〜017 | Sections 35、36、37 |
| P2-FR-018〜028 | Sections 38、40、41 |
| P2-FR-029〜044 | Sections 36、39、40 |
| P2-FR-045〜059 | Sections 35、38、42 |
| P2-FR-060〜065 | Sections 35、37、38、41 |
| P2-NFR-001〜003 | Sections 35、36、40、42 |
| P2-NFR-004〜006 | Sections 37〜41、44 |
| P2-NFR-007 | Sections 39、42、43 |
| P2-NFR-008 | Sections 35、37、41 |

### 44.3 Accepted Decisions

| Decision | Description | Status |
|---|---|---|
| SEC2-001 | Phase 2 DataをOperational、Security Metadata、Personal、Sensitive、Secretへ分類する | Accepted |
| SEC2-002 | Content-free Metadataも内部Security Dataとして扱う | Accepted |
| SEC2-003 | Secretを明示依頼でもMemory、Context、Backup、Guard、Logへ保存しない | Accepted |
| SEC2-004 | Memory BackupからSecurity State、Guard、Trace、ProjectionおよびOperation Stateを除外する | Accepted |
| SEC2-005 | MemoryをInstructionではなくUntrusted User DataとしてContextへ隔離する | Accepted |
| SEC2-006 | Provider送信直前にAuthoritative State、Version、Preference、Fenceおよび上限を再検証する | Accepted |
| SEC2-007 | Sensitive Memoryを直接必要な回答だけへ限定しUsage TraceへContent-free理由を残す | Accepted |
| SEC2-008 | 検証不能時は未検証Memoryを使用せずMemoryなしまたは安全な部分集合で継続する | Accepted |
| SEC2-009 | `store=false`、Provider Body非LoggingおよびProvider Policy再確認を必須とする | Accepted |
| SEC2-010 | Phase 2 KeyをPurpose-separated Registryで管理しRaw Key共有を禁止する | Accepted |
| SEC2-011 | Key MaterialをPurpose別Capability Portの外へ公開しない | Accepted |
| SEC2-012 | Startupで有効CapabilityだけをKnown-answer Probeし不足ScopeをFail Closedにする | Accepted |
| SEC2-013 | Version付きKeyをRead最大2 / Write 1としStrong参照0確認後だけRetireする | Accepted |
| SEC2-014 | Digestだけから原文を推測したKey Migrationを禁止する | Accepted |
| SEC2-015 | Application Log、Security Event、Usage TraceおよびOperation ResourceのAuthorityを分離する | Accepted |
| SEC2-016 | Application Log FieldをStable Allowlistへ限定しDomain IDをDefault出力しない | Accepted |
| SEC2-017 | Security EventをContent-freeな限定Eventへ絞る | Accepted |
| SEC2-018 | Raw Exception、SDK Object、HTTP BodyおよびAccess Log QueryをSanitizeする | Accepted |
| SEC2-019 | Application Logを7日 / 200 MB、Security Event Logを30日 / 100 MBとする | Accepted |
| SEC2-020 | Metric DimensionをBounded Registryへ限定しPersonal分類・ID・Key VersionをTagにしない | Accepted |
| SEC2-021 | Phase 2 Required Metric RegistryをSection 39.2へ固定する | Accepted |
| SEC2-022 | HealthをRepository、Key、Projection、Temporary Storage、Recoveryへ分離する | Accepted |
| SEC2-023 | Management Endpointを常にLoopbackへ固定しLAN / Desktop Clientへ公開しない | Accepted |
| SEC2-024 | Projection、Cleanup、Fence、QuotaおよびTraceのDegraded閾値を固定する | Accepted |
| SEC2-025 | Observability失敗で正常Domain結果を変更せず、禁止Data出力ConfigurationはFail Closedにする | Accepted |
| SEC2-026 | Operation結果はDynamo Resource、回答利用はUsage Trace、Security Logは補助証跡とする | Accepted |
| SEC2-027 | Phase 2で汎用Audit Tableや改ざん耐性Remote Auditを先行実装しない | Accepted |
| SEC2-028 | Memory、Revision、Guard、Trace、Operation、Device、LogのRetention Registryを固定する | Accepted |
| SEC2-029 | CleanupでSensitive Stateを先に利用不能化し確認後だけControlを解放する | Accepted |
| SEC2-030 | Startup / 新Reservation前 / 5分以内SweeperでBounded Recoveryする | Accepted |
| SEC2-031 | Incident対応でも禁止Data Loggingを有効化しない | Accepted |
| SEC2-032 | Desktop版へiOSと同じAPI / Security Contractを適用する | Accepted |
| SEC2-033 | TokenとPassphraseをOS Secure Storage / Request Scopeへ限定する | Accepted |
| SEC2-034 | Crash Reporting、Session Replay、Analytics、Remote LogをPhase 2 Default無効とする | Accepted |
| SEC2-035 | Stale UIから成功を推測せずAuthoritative Resourceを再取得する | Accepted |
| SEC2-036 | Security Configurationを型付きStartup Validationへ集約する | Accepted |
| SEC2-037 | Securityを無効化する汎用Debug / Fallback設定を禁止する | Accepted |
| SEC2-038 | P2-SEC-TC-001〜020をPhase 2 Security Test Registryとする | Accepted |

### 44.4 Review Completion

SEC2-001〜038は一括承認済みである。`test-design.md`、Requirement Traceability Matrix、API、AIおよびFrontendへの統合とFocused Re-reviewを完了し、SEC-CR-001〜009をResolvedとした。

---

## Phase 4 Formal Security Integration — 2026-09-04

**Status:** Accepted / Integrated  
**Source:** AGENT4-001〜118, P4-FR-001〜121, P4-NFR-001〜024

This section is normative for Phase 4 and supersedes earlier Phase 4 security text where a conflict exists.

### Security Authority Model

`User Goal ≠ Permission ≠ Approval ≠ Execution Authority`.
Permission and execution authority are owned by the shared `execution` boundary.
Default Deny and Least Privilege apply to Capability × Operation × Scope and, where applicable, Device / Executor.

Alice, LLM, Agent, Tool, External Content, and Executor cannot expand their own permission or relax a Hard Safety Boundary.

### Permission / Approval / Risk

Risk levels are `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
Approval is bound to Action / Action Version / Target / Canonical Arguments or Effect / Risk /
Permission Version / Expiry and is single-use. Material change, revoke, expiry, or relevant authority change
invalidates the prior approval.

### Executor / Device Trust

Backend-to-executor commands require executor identity, authenticated transport or equivalent mutual trust,
command integrity/authenticity, replay prevention, command identity, issue/expiry evidence, and capability scope.
Stale executor assignment, stale fence, stale action version, or revoked device binding cannot authorize late execution.

### Durable Intent / Fence / Dispatch

For side-effecting actions, durable execution intent and current authority/fence must be established before dispatch.
If required durable authority or audit evidence cannot be persisted, external write fails closed.

### Filesystem / Terminal / OS / Application

Filesystem scope is canonicalized and protected from path traversal / symlink escape.
Terminal actions are structured executable + arguments + working directory + bounded environment, not unvalidated arbitrary shell.
Privileged / system change operations use distinct capability, risk, permission, and approval.
Application UI operations distinguish READ / INTERACT / COMMIT and validate window focus / element freshness.

### Browser and Untrusted Content

Browser operations distinguish READ / INTERACT / COMMIT; form input is not submission authority; download is not execute authority.
Browser authentication state does not grant Alice permission or approval.
External content is untrusted data and cannot change Goal, Permission, Approval, Safety Boundary, or allowlist.
Even if an AI proposal is malicious, the execution boundary must reject unauthorized actions.

### Unknown Outcome / Retry / Recovery

If an external side effect may have occurred but the result cannot be confirmed, the execution enters `UNKNOWN_OUTCOME`.
Alice must not guess success/failure and must not blind-retry side-effecting operations.
Current external state is verified and the same execution is reconciled where possible.
Recovery rechecks plan/action version, current permission, approval validity, fence, executor assignment, and current external state.
High-risk side effects are not unconditionally auto-resumed after crash.

### Cancellation / Emergency Stop

Stop / Cancel / Emergency Stop do not imply rollback.
Emergency Stop prevents new dispatch as quickly as possible, attempts cancellation where supported,
and requires outcome verification for already-dispatched operations.
Emergency Stop state is durable enough that restart cannot silently resume prohibited work.

### Voice

Voice is an I/O channel, not identity proof or execution authority.
Ambient audio is not automatically a confirmed user instruction.
High / Critical risk actions may escalate from voice to screen / strong approval.
Raw audio is not persisted by default; transcript and voice data follow privacy/data-minimization controls.

### Credential and Audit Boundary

Credential possession is not execution authority.
Credentials are not stored in Conversation, Personal Memory, LLM prompt, normal log, or normal audit.
Audit excludes credentials, secrets, full prompts, chain-of-thought, and unnecessary full screenshots/files.
User-visible execution history is a safe projection, not the audit source of truth.
