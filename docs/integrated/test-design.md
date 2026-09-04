# Project Alice - Test Design

## 1. Document Information

| Item | Value |
|---|---|
| Document | `test-design.md` |
| Version | 14 |
| Status | Phase 1–4 Design Approved / Phase 4 Registry Integrated |
| Target | Phase 1–4 Test Detailed Design / Cross-Phase Safety & E2E Gate |
| Last Updated | 2026-09-02 JST |

本ドキュメントは、Project AliceにおけるSoftware Test、Integration Test、External Service Smoke Test、Security ScanおよびAI Evaluationの実行構成を定義する。

Phase 1は、AI Coding Assistantが追加の設計判断をせずにTestを実装できる粒度まで確定する。Phase 2〜4は、後続PhaseでPhase 1のTest Architectureを大幅変更しないための境界と必須検証観点を定義する。

---

## 2. Purpose

Testの目的は、単にCoverageの数値を上げることではない。

- Domain RuleとUse Caseが決定どおり動作することを証明する
- HTTP / SSE / Persistence / AI ProviderのContract破壊を検出する
- Retry、Timeout、DisconnectおよびPartial FailureでDataが不整合にならないことを確認する
- Secret、Personal Dataおよび危険な操作がSecurity Boundaryを越えないことを確認する
- AIの確率的な回答品質を、Software Testとは分けて継続評価する
- AI Coding Assistantが実装を変更したとき、意図しないRegressionを早く発見する

初心者向けに整理すると、Unit Testは「部品単体」、Integration Testは「部品同士の接続」、E2E Testは「利用者から見た一連の流れ」、AI Evaluationは「回答の質」を確認する。

---

## 3. Related Documents and Source of Truth

| Document | Ownership |
|---|---|
| `requirements.md` | Product Requirement / Success Criteria |
| `mvp.md` | Phase Scope |
| `alice-architecture.md` | System Boundary / Dependency Direction |
| `backend-design.md` | Backend Layer / Use Case / Port |
| `frontend-design.md` | Flutter Architecture、State、API / SSE Client、Frontend Test Input |
| `frontend-ui-design.md` | Phase 1 User Flow、Visual Token、Component、Interaction、AccessibilityおよびUI Test Input |
| `phase2-frontend-ui-design.md` | Phase 2 Memory管理画面、Navigation、StateおよびUI Test Input |
| `api-design.md` | HTTP / SSE ContractとAPI固有Test Input |
| `database-design.md` | DynamoDB BehaviorとDatabase固有Test Input |
| `ai-design.md` | AI Software Test Case、Fake Contract、Evaluation Dataset / Rubric / Acceptance Criteria |
| `security-design.md` | Security InvariantとSecurity Test Input |
| `development-environment.md` | Local Development Toolと起動方法 |
| `decisions.md` | Accepted Architecture Decision |
| `test-design.md` | Test Framework、Test Layer、Environment、Mock Server、Scanner、Command、Gate |

本ドキュメントは、他Documentが所有するBusiness Contractを変更しない。矛盾を発見した場合は、Testで片方を選ぶのではなく、実装前に所有DocumentまたはADRを整合させる。

---

## 4. Scope by Phase

| Phase | Design Scope | Implementation Scope |
|---|---|---|
| Phase 1 | Conversation、AI Text Generation、DynamoDB、REST / SSE、Security、AI Evaluationを詳細化 | 本DocumentのPhase 1 Test InfrastructureとTest Caseを実装 |
| Phase 2 | Personal MemoryのContract / Security / Evaluation境界 | Phase 1では実装しない |
| Phase 3 | Tool / External ServiceのContract、Permission、Approval、Audit Test境界 | Phase 1では実装しない |
| Phase 4 | Agent、PC、Browser、VoiceのSimulation / Safety / Evaluation境界 | Phase 1では実装しない |

将来用の空Test Package、Fake、Dataset、RunnerまたはCI JobをPhase 1へ先行追加しない。

---

## 5. Core Test Principles

### 5.1 Deterministic by Default

通常Testは、同じ入力で同じ結果になることを必須とする。

- Real OpenAI APIを呼び出さない
- Real AWSへ接続しない
- System Clock、実時間Sleepおよび無Seed Randomへ依存しない
- Test実行順序へ依存しない
- Developer用DynamoDB Tableを共有しない
- Networkや利用料金の有無で成否を変えない

### 5.2 Test Pyramid

数が多く高速なUnit Testを土台とし、外部境界に近づくほどTest数を絞る。

| Layer | Main Purpose | Speed | External Dependency |
|---|---|---:|---|
| Unit Test | Rule、Use Case、Mapping | Fast | None |
| Slice Test | Spring MVC / JSON等の限定範囲 | Fast | None |
| Port Contract Test | Interface実装間の共通Behavior | Fast〜Medium | Fake / Local Mock |
| Adapter Integration Test | AWS SDK / OpenAI HTTP Mapping | Medium | Local Container / Mock Server |
| Backend Integration Test | APIからPersistenceまで | Medium | DynamoDB Local |
| E2E Test | Flutterを含む利用Flow | Slow | Local Backend |
| External Smoke / AI Evaluation | 実Provider互換性・回答品質 | Slow / Costあり | Explicit Opt-in |

### 5.3 Behavior over Implementation Detail

private methodの呼出回数ではなく、公開Contractと観測可能な結果を検証する。RefactoringでBehaviorが変わらない限り、Testが大量に壊れない構造を優先する。

### 5.4 No Test-only Product Behavior

Testを通すために、Production CodeへTest専用のBusiness分岐を追加してはならない。時間、ID、DelayまたはExternal Capabilityの差し替えはPortや標準的なDependency Injectionで実現する。

---

## 6. Confirmed Phase 1 Test Technology

| Purpose | Decision |
|---|---|
| Main Test Framework | JUnit Jupiter。`spring-boot-starter-test`経由 |
| Assertion | AssertJ |
| Mocking | Mockito。局所的Interaction検証に限定 |
| Application Port Test Double | Hand-written Fakeを優先 |
| Spring MVC Slice | MockMvc |
| Real HTTP / SSE Client | JDK 25 `HttpClient` |
| Integration Test Runner | Maven Failsafe Plugin `3.5.6`（Spring Boot Parent管理） |
| DynamoDB Container | Testcontainers `GenericContainer` |
| DynamoDB Image | `amazon/dynamodb-local:3.3.0` |
| OpenAI HTTP Mock | `org.wiremock:wiremock-standalone:3.13.2` |
| Coverage | JaCoCo `0.8.15` |
| Dependency Vulnerability Scan | OWASP Dependency-Check Maven Plugin `13.0.0` |
| Secret Scan | Gitleaks `8.29.1`、Container ImageをDigest Pin |

Rules:

- Spring Boot 4.1.0が管理するJUnit、AssertJ、MockitoおよびTestcontainersの互換Versionを利用し、理由なく個別Versionを上書きしない
- WireMock 4.xは2026-08-16時点でBetaのため採用しない
- GitleaksはVersion TagだけでなくContainer Digestも固定する
- Test Tool更新はRelease Note、Java 25互換性、Canary Testおよび全Test結果を確認して行う
- Library変更でProduction Architectureを変更する必要が生じた場合はDesign / ADRを先に更新する

### 6.1 Maven Coordinates and Version Ownership

| Purpose | Maven Coordinate | Version Ownership |
|---|---|---|
| Spring Test Bundle | `org.springframework.boot:spring-boot-starter-test` | Spring Boot `4.1.0` |
| Testcontainers Core | `org.testcontainers:testcontainers` | Spring Boot管理 `2.0.5` |
| Testcontainers JUnit Jupiter | `org.testcontainers:testcontainers-junit-jupiter` | Spring Boot管理 `2.0.5` |
| WireMock | `org.wiremock:wiremock-standalone` | Project固定 `3.13.2` |
| Maven Surefire | `org.apache.maven.plugins:maven-surefire-plugin` | Spring Boot Parent管理 `3.5.6` |
| Maven Failsafe | `org.apache.maven.plugins:maven-failsafe-plugin` | Spring Boot Parent管理 `3.5.6` |
| JaCoCo | `org.jacoco:jacoco-maven-plugin` | Project固定 `0.8.15` |
| OWASP Dependency-Check | `org.owasp:dependency-check-maven` | Project固定 `13.0.0` |

WireMockは依存Libraryを内包してTest Classpathとの衝突を抑えられる`wiremock-standalone`を`test` Scopeで使用する。Spring Bootが管理するVersionは`pom.xml`で個別上書きせず、Project固定のToolだけVersionを明示する。

Gitleaksの実行資産は次へ配置する。

```text
scripts/security/
├── gitleaks.env
├── scan-secrets.sh
├── verify-gitleaks-canary.sh
├── gitleaks-canary.toml
└── testdata/gitleaks-canary/
```

`gitleaks.env`は次の形式で、導入時に検証したContainer Digestを記録する。

```text
GITLEAKS_IMAGE=ghcr.io/gitleaks/gitleaks:v8.29.1@sha256:<verified-digest>
```

`<verified-digest>`のPlaceholderが残った状態ではSecurity Scanを成功扱いにしてはならない。実装開始時に公式配布元と取得したImageを照合し、実Digestへ置換する。

### 6.2 Why Fake and Mock Are Different

Fakeは、Test用に単純化した動作可能な実装である。例えば`FakeTextGenerationProvider`は指定したDeltaを順番に返す。

Mockは、呼出回数や引数等のInteractionを検証するTest Doubleである。すべてをMockito Mockにすると、内部実装へTestが密結合しやすい。Project AliceではScenarioを表現する必要があるPortはFake、単純な「呼ばれないこと」等はMockを利用する。

---

## 7. Test Source Layout and Naming

Production PackageをMirrorする。

```text
backend/
├── src/test/java/com/projectalice/backend/
│   ├── conversation/
│   │   ├── presentation/
│   │   ├── application/
│   │   ├── domain/
│   │   └── infrastructure/
│   ├── ai/
│   │   ├── application/
│   │   ├── infrastructure/openai/
│   │   └── evaluation/
│   └── support/
│       ├── fixture/
│       ├── fake/
│       ├── contract/
│       └── integration/
├── src/test/resources/
│   ├── application-test.yml
│   ├── wiremock/
│   └── ai-evals/conversation/
│       ├── phase1-cases.jsonl
│       └── phase1-rubric.md
└── target/ai-evals/<run-id>/
```

| Pattern | Runner | Meaning |
|---|---|---|
| `*Test` | Maven Surefire | Unit、Slice、Fake / WireMock Contract Test |
| `*IT` | Maven Failsafe | DynamoDB Localまたは起動済みBackendを使うIntegration Test |
| `*OpenAiSmokeIT` | Failsafe + `openai-integration-test` Profile | Real OpenAI Smoke Test |
| `*AwsDynamoDbSmokeIT` | Failsafe + `aws-dynamodb-smoke` Profile | Real AWS Compatibility Smoke Test |
| `*AiEvaluationIT` | Failsafe + `ai-evaluation` Profile | AI Quality Evaluation Runner |

`support`には複数Testから本当に共有するFixture / Fakeだけを置く。Production CodeへTest Helperを配置しない。

---

## 8. Maven Lifecycle and Commands

### 8.1 Standard Commands

| Command | Purpose | Docker | Real External API |
|---|---|---:|---:|
| `./mvnw test` | Fast Feedback | No | No |
| `./mvnw verify` | Phase 1 Full Backend Gate | Yes | No |
| `./mvnw verify -Popenai-integration-test` | Real OpenAI Smoke | Yes | OpenAI only |
| `./mvnw verify -Paws-dynamodb-smoke` | AWS DynamoDB Compatibility | Yes | AWS only |
| `./mvnw verify -Pai-evaluation` | AI Quality Evaluation | Yes | OpenAI by default |
| `./mvnw dependency-check:check` | Dependency Vulnerability Scan | No | Vulnerability DB only |

すべてのBackend Build / TestはMaven Wrapperを使用する。Integration Testを実行するときは`integration-test` Goalだけで止めず、Cleanupと結果検証を含む`verify`を実行する。

### 8.2 Default Inclusion Rules

- `./mvnw test`は`*Test`だけを実行する
- `./mvnw verify`は`*Test`に加えて通常の`*IT`を実行する
- `external-openai`、`external-aws`、`ai-evaluation` TagはDefaultから除外する
- 各Opt-in Profileは対応Tagだけを追加実行する
- `openai-integration-test`でCredentialがない場合は`SKIPPED`と明示し、Real OpenAI未実行としてReportする
- `aws-dynamodb-smoke`でCredentialまたは専用Table設定がない場合は、Profileの前提条件違反としてBuildを失敗させる
- `ai-evaluation`でCredential、Provider設定またはDatasetがない場合は失敗とし、空のEvaluation ReportやAccepted Baselineを生成しない
- AI Evaluationの実行Case数がDatasetのCase数と一致しない場合はAcceptance Failureとする
- `-DskipTests`を通常の完了条件に使用しない

---

## 9. Test Profiles and Configuration

### 9.1 `test` Profile

`test` Profileは次を保証する。

- Real OpenAI Client Beanを生成せず`FakeTextGenerationProvider`とFake `InputTokenCounter`を使用する
- `OPENAI_API_KEY`を要求しない
- DynamoDB EndpointはTestcontainersが動的に設定する
- Server PortはRandom Portを使用する
- Test Table名はFailsafe JVMごとに一意である
- Cursor Signing Keyには32 ByteのSynthetic Test Keyを使用する
- Logging Levelを上げてもSecretまたはContent全文を出力しない
- Production Security Invariantを無効化しない

### 9.2 Configuration Injection

Spring Integration Testでは`@DynamicPropertySource`またはSpring Bootの同等機構を利用し、動的Port、Table名およびDummy Credentialを注入する。固定Port `8000`をIntegration TestへHard Codeしない。

Local用Dummy AWS CredentialはDynamoDB LocalのRequest署名作成だけに使い、Real Credentialと誤認しない明確な値を使用する。

Cursor Test Keyは次の公開Test Dataを使用できる。

```text
ALICE_CURSOR_SIGNING_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
```

これは32 Byteのゼロ値をBase64表現したTest専用値である。`local` / Productionでは使用せず、Logへ出力しない。

### 9.3 Startup Configuration Tests

Application Context Runnerまたは最小のStartup Testで次を確認する。

- Default値がAPI / Database / AI / Security Designと一致する
- Environment Variable Overrideが正しい型へ変換される
- Unknown PropertyをSilent IgnoreせずStartupが失敗する
- Invalid Model、Enum、Token、Duration、Retry CountでStartupが失敗する
- Input Count、Generation、Use Case、SSEおよびProcessing LeaseのDeadline順序が不正ならStartupが失敗する
- Missing / Blank OpenAI API Keyで`local` Startupが失敗する
- Missing Keyでも`test` ProfileがFake Providerで起動する
- `test` ProfileでReal OpenAI Clientが生成されない
- OpenAI ClientがSingletonとして再利用される
- Configuration Objectの`toString`、Startup LogまたはEnvironment表示へSecretが出ない
- Wildcard / Non-loopback Server BindまたはDynamoDB Endpointを`local` Profileが拒否する
- 有効な32 Byte Cursor Signing Keyで起動できる
- Cursor Signing KeyがMissing、Blank、Invalid Base64または32 Byte未満なら起動に失敗する

---

## 10. Unit Test Design

Unit TestはSpring Context、Docker、File SystemまたはNetworkを起動しない。

### 10.1 Domain

- Entity / Value Objectの生成RuleとInvariant
- Conversation / Messageの状態遷移
- Role、Sequence、UUID v4、JST Date-TimeのValidation
- Message Contentの文字数およびUTF-8 Byte Boundary
- Idempotency State TransitionとLease判定
- Error CategoryとRetry可否

### 10.2 Application

- `SendMessageUseCase`の正常Flow
- Conversationがない最初のMessageでの作成Flow
- 既存Historyの利用
- Providerを呼ぶ前のValidation Failure
- User Message、Assistant Message、Failure StateのPersistence順序と整合性
- Duplicate、Busy、Conflict、Replay
- First Delta前だけのRetry
- First Delta後のRetry禁止
- Timeout、Cancellation、Client Disconnect
- Output Limitを正常完了に偽装しないこと

### 10.3 Prompt and Context

Prompt / Context固有Caseは`ai-design.md` Section 17をSource of Truthとする。特に次を含める。

- Prompt Sectionの順序
- Alice Identity / Personality
- User入力とSystem Instructionの分離
- History Message Order
- Context BudgetとHistory Selection
- 自然な場面だけ「楠瑛」を敬称なしで扱うRule
- Provider-independent ModelからOpenAI ModelへのMapping

LLMの自然文出力全文をUnit Testで完全一致させない。Prompt Builder自体の確定Templateや構造は、Security上安全なTest Fixtureを用いて一致検証してよい。

### 10.4 Infrastructure Logic without I/O

- DynamoDB Key / Item Mapping
- 20桁ゼロ埋めSequence
- SHA-256 Hash
- Cursor Encode / Decode / HMAC
- DynamoDB / OpenAI Error Mapping
- JST Offset付きミリ秒精度Serialization
- Provider Finish Reason Mapping
- TelemetryでUsage不明を`0`と偽装しないこと

---

## 11. Spring Slice Test Design

### 11.1 Controller Slice

`@WebMvcTest`とMockMvcを使用し、Application Use CaseはFake / Mockへ置換する。

確認対象:

- Endpoint、HTTP Method、Content-Type、Accept
- Request ValidationとUnknown Field拒否
- HTTP StatusとRFC 9457 Problem Details
- `Cache-Control: no-store`
- `requestId`等のResponse Header
- Provider / AWS / Stack Trace / SecretをResponseへ含めない
- CORS Wildcardが存在しない

### 11.2 JSON Test

`@JsonTest`またはObjectMapper単体Testで次を確認する。

- API SchemaのField名とNullability
- UTC `Z`ではなくJST `+09:00`を持つDate-Time
- ミリ秒精度
- Unknown Request Fieldの拒否
- Optional Response Field追加時に既存Fieldの名前、型およびSemanticsが変化しないこと

SSEの実Transport順序はSlice Testだけで完了とせず、Backend Integration Testでも確認する。

Unknown Response FieldおよびUnknown SSE EventをFlutterが安全に無視できることはFrontendの責務であり、Flutter側のSerialization / Event Handling Testで検証する。Backend TestだけでClient互換性を満たしたと判定してはならない。

---

## 12. Port Contract Tests

Contract Testは「同じInterfaceの実装が、共通の約束を守るか」を確認する再利用可能なTest Suiteである。

### 12.1 `TextGenerationProvider`

`ai-design.md` Section 17.6を正式Caseとし、少なくとも次を全実装へ適用する。

- Input Role / Order維持
- Delta Order維持
- Completionは1回だけ
- Terminal後にDelta / Errorを通知しない
- Deadline / Cancellation伝播
- Finish Reason / ErrorのProvider-independent Mapping
- Secret / Raw Request / Raw ResponseをErrorへ含めない

FakeとOpenAI Adapterの両方が同じContract Test Suiteを満たす。ただしProvider Token Usageは共通Port Contract外とする。

### 12.2 `ConversationRepository`

Fake RepositoryとDynamoDB Adapterへ、意味的に共通なRepository Contractを適用する。

- Conversation取得
- Message順序
- Start / Completion / FailureのAtomic Contract
- Idempotency Duplicate / Conflict / Busy / Replay
- Paginationの重複・欠落なし
- Domain / Application Error Mapping

DynamoDB固有のPK / SK、Condition Expression、`ConsistentRead`等は共通ContractではなくDynamoDB Adapter Integration Testで確認する。

---

## 13. OpenAI Adapter Test and WireMock

Phase 1のOpenAI Adapter TestはWireMock 3.13.2をJVM内のRandom Portで起動する。Real OpenAI APIへ接続しない。

### 13.1 Required Request Verification

- Request Endpoint / Method / Header
- Authorization値そのものはReportへ出さず、設定されたことだけを検証
- Model、Reasoning Effort、Output Limit
- Input Message Role / Order / Content Mapping
- `store=false`がすべてのGeneration Requestに存在する
- SDK Retryが`0`
- RequestごとにClientを再生成しない
- Production / `local` ProfileでBase URL Overrideが無効

### 13.2 Required Response Scenarios

- Single Delta + Completion
- Multiple Delta + Completion
- Usageあり / Usageなし
- Output Limit Finish
- Malformed / Unknown Event
- 4xx、429、5xx
- Failure Before First Delta
- Connection close After First Delta
- Response Delay / Timeout
- Cancellation

WireMockのRequest JournalはTest終了時にResetする。Raw Request / Responseを通常Test Reportへ保存しない。

### 13.3 TLS Verification Test

Test ProfileだけでHTTPS Mock Endpointを注入可能にし、次を確認する。

- TrustされたTest CAのHTTPS Endpointへ接続できる
- TrustされていないCertificateまたはHostname不一致で失敗する
- HTTP EndpointをProduction / `local` Profileが拒否する

Certificate Validationを無効化するTest用Product設定を追加してはならない。

---

## 14. DynamoDB Local Integration Test

### 14.1 Container Lifecycle

Testcontainers `GenericContainer`で`amazon/dynamodb-local:3.3.0`をFailsafe JVM単位に1回起動する。

- Image Tagを固定し、`latest`を使用しない
- Host PortはRandom Mappingとする
- `getHost()`と`getMappedPort(8000)`からEndpointを組み立てる
- Reusable Container機能はDefault CIで使用しない
- Container Readyを有限TimeoutのWait Strategyで確認する
- Test終了時はTestcontainersにより破棄する

手動Local Developmentは`dynamodb-local-setup.md`のDocker Composeを利用する。自動TestはData Isolationと再現性のためTestcontainersを利用し、同じContainerを手作業で事前起動することを要求しない。

### 14.2 Table Isolation

- Failsafe JVMごとに`alice-test-<run-id>`形式の一意なTable名を生成する
- Spring Application Context起動前にTableとTTLを作成する
- Failsafe JVM終了時にTableを削除する
- Test Method間で共有状態を前提にしない
- Test DataはSynthetic Dataだけを使用する
- Integration TestのParallel ExecutionはPhase 1では無効とする

各Test Method開始前に、Test専用Setup DynamoDB ClientでTable内のItemを削除する。Table全件ScanはTest Data Cleanupに限って許可し、Production RepositoryまたはProduction CodeへScan処理を追加してはならない。

### 14.3 Mandatory Bootstrap Order

DynamoDB Local Integration Testは次の順序で起動・終了する。

1. Failsafe JVMを起動する
2. DynamoDB Local Containerを起動し、Readyを確認する
3. Run IDとTable名を生成する
4. Tableを作成し、`ACTIVE`を待機してからTTL属性`expiresAtEpochSeconds`を設定・確認する
5. Endpoint、Table名、Dummy AWS CredentialおよびCursor Test KeyをSpringへ動的注入する
6. Spring Application Contextを起動する
7. Integration Testを実行する
8. Spring Application Contextを終了する
9. Test Tableを削除する
10. Containerを停止する

Implementation Rules:

- Table作成をSpring Context起動後の`@BeforeAll`だけへ配置してはならない
- `@DynamicPropertySource`から参照する共有Integration Test EnvironmentがContainer起動とTable作成を担い、Table Ready後に限りPropertyを公開する
- Table Statusが`ACTIVE`であること、およびTTL属性名が`expiresAtEpochSeconds`であることを有限Timeoutで確認する
- 通常のBackend Integration Testは同一Failsafe JVM、同一Tableおよび同一Context設定を共有し、Spring Context Cacheを利用できる
- Missing / Invalid Configurationを検証するStartup Failure Testは、Application Context Runnerまたは専用Contextで通常Integration Test群から分離する
- Test MethodまたはTest ClassごとにTable名を変更してSpring Context Cacheを破棄しない
- Setup途中で失敗した場合も、作成済みTableとContainerを終了処理でCleanupする

### 14.4 Required Scenarios

`database-design.md` Section 24.4の全Scenarioを実装する。

- Table Schema / TTL
- 300,000 Byte Boundary
- Strongly Consistent `GetItem` / `Query`
- Start / Completion / Failure Transaction
- Duplicate Completed / Different Content Conflict
- Active / Expired Lease
- Conditional ConflictでPartial Updateなし
- Cursor Pagination
- Invalid CursorはDynamoDB呼出前に拒否
- Table不存在 / Schema不一致でStartup失敗
- Application通常経路で`Scan`を呼ばない

DynamoDB TTLの物理削除を待たない。Transaction Conflict、Throttling、Permission Denied、Network TimeoutおよびUnknown Transaction Result等、DynamoDB Localで正確に再現できないFailureは制御可能なFake / Mockで補う。

---

## 15. Backend Integration Test

`@SpringBootTest(webEnvironment = RANDOM_PORT)`で実Application Contextを起動し、JDK 25 `HttpClient`からHTTP / SSEへ接続する。

構成:

```text
JDK HttpClient
      ↓ HTTP / SSE
Spring Boot Application
      ├── FakeTextGenerationProvider
      └── DynamoDB Adapter
              ↓
       DynamoDB Local 3.3.0
```

Required Flow:

1. 最初のMessageで単一Conversationを作成し、Alice Responseを保存する
2. 既存Historyを利用して次のMessageを送る
3. 保存済みConversationとMessage Historyを取得する
4. Duplicate RetryでAIを再呼出しせず保存済みResultをReplayする
5. Busy / Conflict / Failed / Timeoutで設計済み状態へ収束する
6. Disconnect後もGenerationを継続し、Terminal Stateを保存する
7. Backend Shutdown / Use Case DeadlineでProvider Cancellationを行う

Backend Integration TestでReal OpenAIを使用しない。

---

## 16. API and SSE Test Matrix

### 16.1 Pre-stream HTTP

- Valid Requestは`200 OK`と`text/event-stream`
- Validation、Unknown Field、Size超過、Invalid ID / Cursor
- Unsupported Media Type / Not Acceptable
- Conversation Not Found
- Idempotency Conflict / Request In Progress + `Retry-After: 2`
- Safe RFC 9457 Problem Details
- `Cache-Control: no-store`
- Conversation JSONは64 KiB、Message History JSONは16 MiB、Problem Detailsは64 KiBを上限とする
- 各Responseで上限ちょうどを受理し、1 Byte超過を拒否する
- `Content-Length`欠落または実Bodyより小さい値でも、実際に受信・生成したUTF-8 Byte数で上限を強制する
- Message Historyは16 MiBへ到達する前の完全なMessageまででPageを終了し、`hasMore` / `nextCursor` Invariantを維持する

### 16.2 SSE Event Order

正常Flow:

```text
stream.started（Canonical userMessageを含む）
assistant.delta（0回以上）
assistant.completed
Connection Close
```

失敗Flow:

```text
stream.started（Canonical userMessageを含む）
assistant.delta（0回以上）
stream.failed
Connection Close
```

`conversation.started`および`assistant.started`はPhase 1 API Contractに存在しないため、送信または期待してはならない。

Completed Replay:

```text
stream.started（保存済みCanonical userMessageを含む）
assistant.completed
Connection Close
```

Failed Replay:

```text
stream.started（保存済みCanonical userMessageを含む）
stream.failed
Connection Close
```

Replayでは`assistant.delta`を再送せず、AI Providerを再呼び出ししない。

確認項目:

- Event名、Payload、`requestId`、Date-Time
- `stream.started.userMessage`がStart Transactionで保存済みのCanonical User Messageと一致する
- Pending Content不一致、Role不一致、CompletedのUser Message不一致をProtocol Errorとして拒否する
- Failed Flow / Failed ReplayでもCanonical User MessageをFrontendへ保持できる
- Delta到着順と結合結果
- `assistant.completed`は保存済み全文を返す
- Terminal EventはExactly Once
- Terminal後のEventなし
- Provider固有Event / Model / Token / SDK Objectを公開しない
- Backendが定義済みEventの名前、Payloadおよび順序をAPI Version内で変更しないこと
- 未定義の`conversation.started`または`assistant.started`を送信しないこと
- 単一SSE Frameは1 MiB、単一`assistant.delta` JSON Dataは64 KiBを上限とする
- SSE Stream全体は8 MiB、非Comment Eventは10,000件を上限とする
- Temporary / Canonical Assistant Textは50,000 Unicode Code Pointを上限とする
- 各上限ちょうどを受理し、1 Byte、1 Eventまたは1 Code Point超過を拒否する
- 上限超過後に追加Buffer、Partial TextのCanonical化、Raw Data Loggingまたは新しい`Idempotency-Key`による自動再送を行わない

### 16.3 Timeout and Heartbeat

- Default SSE Timeout 180秒
- Heartbeatは15秒間隔のSSE Comment
- HeartbeatをMessage Contentへ含めない
- Timeout時は設計済みErrorとPersistence Stateへ収束する

通常Testで180秒待たない。Test Configurationと制御可能なClock / Schedulerを用いて短縮し、実時間Sleepを避ける。

### 16.4 Disconnect

- Client切断がGeneration Cancel条件にならない
- 切断後はSSE送信を停止する
- Callbackの送信失敗をGeneration失敗に変換しない
- Success / FailureをPersistenceへ保存する
- 同じIdempotency KeyのRetryでReplayできる

### 16.5 Resource Limit Boundary Matrix

| Boundary | Maximum | Exact Maximum | Over Limit |
|---|---:|---|---|
| Conversation JSON | 64 KiB | Accepted | `responseTooLarge` |
| Message History JSON | 16 MiB | Accepted | `responseTooLarge` |
| Problem Details | 64 KiB | Accepted | `responseTooLarge` |
| Single SSE Frame | 1 MiB | Accepted | `sseFrameTooLarge` |
| Single Delta JSON Data | 64 KiB | Accepted | `sseFrameTooLarge` |
| Entire SSE Stream | 8 MiB | Accepted | `sseStreamTooLarge` |
| Non-comment SSE Event | 10,000 | Accepted | `tooManySseEvents` |
| Assistant Text | 50,000 Code Point | Accepted | `assistantContentTooLong` |

Backendでは生成・Serialization・Streaming境界、Frontendでは受信・Frame Parsing・Event Decode・Text累積境界をそれぞれTestする。巨大な固定Fixtureだけに依存せず、境界付近のPayloadをTest Builderで生成する。失敗時のException、LogおよびTest Failure OutputへRaw Body、Frame、DeltaまたはContent全文を含めない。

---

## 17. Real External Service Smoke Tests

### 17.1 OpenAI

`openai-integration-test` Profileでのみ実行する。

- Synthetic Promptを少数使用する
- Streaming Event受信、Finish Reason、Usage Mappingを確認する
- 自然文全文一致をしない
- Credential、Conversation History、Personal MemoryまたはPrivate Dataを送らない
- Call回数、Model、Token Usage、Cost見積りを記録する
- Default Local Testおよび通常Merge Gateに含めない

### 17.2 AWS DynamoDB

`aws-dynamodb-smoke` Profileでのみ実行する。

- 専用AWS Account / Region / Tableを利用する
- Least Privilege Credentialを利用する
- IAM、Real DynamoDB TransactionおよびDynamoDB Localとの差分だけを確認する
- Production Tableを使用しない
- 明示的なCleanupを行う

Phase 1 Local Developmentの完了にReal AWS Smokeを毎回要求しない。AWS利用開始時またはSDK / IAM / Table Configuration変更時に実行する。

---

## 18. AI Quality Evaluation

### 18.1 Separation from Software Tests

AI Outputは確率的であるため、Software TestとAI Evaluationを分離する。

- Software Test: Contractどおり動くか
- AI Evaluation: 回答が正確、自然、安全でAliceらしいか

### 18.2 Runner and Placement

Phase 1 RunnerをTest Sourceへ置き、Production Artifactへ含めない。

```text
backend/src/test/java/com/projectalice/backend/ai/evaluation/
├── ConversationEvaluationRunner.java
└── ConversationAiEvaluationIT.java
```

Runnerは`TextGenerationProvider` Portを通し、OpenAI SDKを直接呼ばない。Dataset / Rubric / Result Layoutは`ai-design.md` Section 17.9〜17.18に従う。

### 18.3 Execution

```text
./mvnw verify -Pai-evaluation
```

- Default Providerは明示設定されたOpenAI Adapter
- Dataset最低50 Cases
- Deterministic Gateを先に実行
- Candidate OutputとScoreをRun単位で生成
- Initial Baselineは全CaseをHuman Review
- Critical SafetyまたはMajor Persona Violationは平均点で相殺しない
- Resultは`backend/target/ai-evals/<run-id>/`へ生成しGit Commitしない
- Credential、Provider設定またはDatasetが不足する場合は実行失敗とし、空の成功ReportまたはAccepted Baselineを生成しない
- 実行Case数がDatasetのCase数と一致しない場合はAcceptance Failureとする

### 18.4 Acceptance

Acceptance Criteriaは`ai-design.md` Section 17.13を変更せず適用する。

- Overall Average 4.0 / 5.0以上
- Category Average 3.8 / 5.0以上
- Critical Safety Deterministic Gate 100%
- Major Persona Violation 0件
- Critical `mustInclude` / `mustNotInclude` 100%
- 全CaseがGeneration Deadline内
- Output Limit Failure 0件

Prompt、Model、Context Construction等の変更時は、`ai-design.md`のEvaluation Triggerに従ってSmoke / Standard / Focused Evaluationを選択する。

---

## 19. Security Test and Scan Design

### 19.1 Automated Security Tests

`security-design.md` Section 22のPhase 1項目をUnit、Slice、AdapterまたはIntegration Testへ割り当てる。

| Concern | Primary Verification |
|---|---|
| Loopback Bind / Invalid Bind拒否 | Configuration Test + Startup IT |
| DynamoDB Loopback Endpoint | Configuration Test |
| HTTPS / TLS Verification | OpenAI Adapter IT |
| `store=false` / SDK Retry `0` | WireMock Adapter Test |
| CORS / Safe Error / `no-store` | Controller Slice + Backend IT |
| Secret / Content非Logging | Log Capture Test |
| Fake Provider in `test` | Application Context Test |
| Actuator Exposure | Backend IT |
| MDC Propagation / Cleanup | Async / SSE Integration Test |
| Metric Cardinality | Meter Registry Test |
| Log Rotation | Logging Configuration Test |
| Response / SSE Resource Limit | Controller / SSE Integration Test + Flutter Parser Test |

`health`と`metrics`以外のActuator Endpointが公開されないこと、Health DetailへCredentialやConfigurationが含まれないことを実HTTPで確認する。

Additional Required Assertions:

- API Response、Problem DetailsおよびSSE EventへSecretを含めない
- Startup、Error、Debug LogへSecretを含めない
- Message、Prompt、DeltaおよびDynamoDB Item全文をLogへ含めない
- Provider / AWS Error BodyをClientへ含めない
- Assistant OutputをOS、Browser、ToolまたはScriptとして自動実行しない
- Log Rotationは7日かつ合計200 MB上限と一致する
- `conversationId`、`messageId`、`requestId`等をMetric Tagへ入れない
- Async / SSE完了後にMDCをClearし、次のRequestへ値を漏らさない
- Response / SSE上限超過時に接続を停止し、追加BufferまたはPartial Assistant Messageの永続化を行わない

### 19.2 Secret Scan

Gitleaks 8.29.1を使用し、Git HistoryとWorking TreeをScanする。

```text
gitleaks git --redact --no-banner --exit-code 1
gitleaks dir . --redact --no-banner --exit-code 1
```

- CIではDigest固定Containerを使用する
- FindingのSecret値をLog / Artifactへ露出しない
- Allowlistは最小範囲、理由、Owner、期限を持つ
- Scanner自身のCanaryとして、専用Synthetic Fixtureが検出されるSelf-testを持つ
- Canary Fixtureは本物のCredential形式と誤認されない管理されたTest Dataとし、通常Scan対象から明示的に分離する

`scripts/security/scan-secrets.sh`は`gitleaks.env`に記録したDigest固定Imageだけを利用する。TagだけのImageまたはLocalに偶然存在する別VersionへFallbackしてはならない。

Canary Self-testは次のContractを満たす。

- `gitleaks-canary.toml`にProject専用Rule ID `project-alice-canary`を定義する
- `testdata/gitleaks-canary/`には、そのRuleだけに一致するSynthetic Markerを1件置く
- `verify-gitleaks-canary.sh`はCanary用ConfigurationとCanary Directoryだけを対象にScanする
- 検出件数が正確に1件で、Rule IDが`project-alice-canary`の場合だけ成功する
- 0件、2件以上、別Ruleでの検出またはScanner ErrorはCI Failureとする
- Redact済みJSON ReportをRepository Rootの`target/security/gitleaks-canary.json`へ出力し、Marker値をConsoleへ表示しない
- 通常のGit History / Working Tree ScanではCanary Fixtureだけを狭いPath単位でAllowlistし、他のFindingを除外しない

### 19.3 Dependency Vulnerability Scan

OWASP Dependency-Check Maven Plugin 13.0.0を使用する。

- CVSS 7.0以上でGateをFailureにする
- Scanner Error時もFailureにする
- HTML / JSON / SARIF Reportを`target/`へ生成する
- SuppressionはCVE、理由、影響評価、Owner、期限を必須とする
- Suppression期限切れはFailureとする
- Vulnerability DatabaseはCacheし、最低7日以内に更新する
- CI Provider確定後はNVD Mirrorまたは信頼できるCacheを検討する

False Positiveを理由にThresholdを上げない。個別Suppressionで根拠を記録する。

---

## 20. Coverage and Quality Gate

JaCoCo 0.8.15で手書きBackend Codeを計測する。

| Metric | Minimum |
|---|---:|
| Line Coverage | 80% |
| Branch Coverage | 70% |

Rules:

- `./mvnw verify`でThreshold未達ならFailure
- Surefireが実行するUnit / Slice Testと、通常Failsafeが実行するIntegration TestのCoverageを結合する
- Unit / Slice Testは`target/jacoco.exec`、Integration Testは`target/jacoco-it.exec`へExecution Dataを出力する
- Maven `verify` Phaseで両Execution Dataを`target/jacoco-merged.exec`へMergeする
- 結合後のReport生成とCoverage Checkは`target/jacoco-merged.exec`を入力として実行する
- 結合Reportの固定出力先は`backend/target/site/jacoco-merged/`とする
- Real OpenAI Smoke、AWS SmokeおよびAI Quality Evaluationは再現性ある通常Coverage Gateへ含めない
- Generated Sourceだけを除外可能とする
- DTO、Configuration、MapperまたはInfrastructureを数値調整目的で一括除外しない
- Critical RuleはCoverage値に関係なくRequired Scenario Testを持つ
- Coverage低下をTest削除や除外追加で解決しない

Coverage 100%でも正しいAssertionがなければ品質は保証できない。Coverageは「実行された範囲」の指標であり、「正しさ」の点数ではない。

---

## 21. Test Data, Time, ID and Concurrency

### 21.1 Test Data

- Synthetic Dataだけを使用する
- Real Conversation、Personal Memory、Private Repository Content、SecretをFixtureへ含めない
- Builder / Factoryで必要項目を明示する
- Boundary値のByte数はUTF-8で計算する
- SnapshotへCredential、Prompt全文、Delta全文を残さない

### 21.2 Time and ID

- `Clock`を注入し、JST固定時刻を使用する
- `Thread.sleep`でLease、TTL、RetryまたはTimeoutを待たない
- UUID GeneratorまたはID Supplierを差し替え可能にする
- Retry中は同じAssistant Message ID、Sequence、Content、Date-Timeを維持する
- Date-Time AssertionはJST `+09:00`とミリ秒精度を確認する

### 21.3 Concurrency

- Phase 1 Integration TestはMethod並列を無効にする
- Concurrency Rule自体はLatch、Barrierまたは制御可能なFakeで再現する
- Timingだけに依存するRace Testを作成しない
- Same Conversation / Same Idempotency Keyの競合を明示的に同期させて検証する

---

## 22. Failure, Timeout and Flaky Test Policy

### 22.1 Failure Injection

Fake / Mockは次を明示的に注入できる。

- Retryable / Non-retryable Failure
- Failure Before / After First Delta
- Timeout / Cancellation
- DynamoDB Conditional Conflict / Throttling / Unknown Result
- Client Disconnect / SSE Callback Failure
- Malformed Provider Event

### 22.2 Timeout

すべてのAsync / Integration Testに有限Timeoutを設定する。Failure時に無限待機しない。Business Timeoutの検証ではTest用短縮値またはVirtual / Controlled Timeを使い、Production Defaultそのものを実時間で待たない。

### 22.3 Flaky Test

- Failed Testを自動RetryしてGreenに見せない
- 原因不明のままIgnore / Disableしない
- Network / Time / Shared State / Randomnessの原因を特定する
- 一時隔離が必要な場合はIssue、Owner、期限を記録し、Merge Gateの代替検証を用意する
- 同じCommitで再実行結果だけが変わるTestはRelease Gateに使用しない

---

## 23. Reports, Logging and Artifact Handling

| Artifact | Location | Git Commit |
|---|---|---:|
| Unit Test Report | `backend/target/surefire-reports/` | No |
| Integration Report | `backend/target/failsafe-reports/` | No |
| Unit Coverage Execution Data | `backend/target/jacoco.exec` | No |
| Integration Coverage Execution Data | `backend/target/jacoco-it.exec` | No |
| Merged Coverage Execution Data | `backend/target/jacoco-merged.exec` | No |
| Merged Coverage Report | `backend/target/site/jacoco-merged/` | No |
| Dependency Scan | `backend/target/dependency-check-report.*` | No |
| Gitleaks Canary Report | `target/security/gitleaks-canary.json` | No |
| AI Evaluation | `backend/target/ai-evals/<run-id>/` | No |

Test Reportへ次を含めない。

- API Key、AWS Credential、Authentication Token
- Full Prompt / Conversation Content / SSE Delta
- Raw OpenAI / AWS Request / Response
- DynamoDB Item全文
- Environment Variable一覧

Failure MessageはCase ID、Safe Error Category、Expected / Actualの非機密Metadataを中心とする。

---

## 24. Provider-neutral CI Gates

CI Product自体は未決定であり、本Documentでは特定Providerを採用しない。どのCIでも次のStage Semanticsを維持する。

| Stage | Command / Action | Merge Gate |
|---|---|---:|
| Secret Scan | Pinned Gitleaks | Yes |
| Frontend Static Analysis | `flutter analyze` | Yes |
| Frontend Unit / Widget Test | `flutter test` | Yes |
| Fast Test | `./mvnw test` | Yes |
| Full Backend Test | `./mvnw verify` | Yes |
| Coverage | JaCoCo Check in `verify` | Yes |
| Dependency Scan | `./mvnw dependency-check:check` | Yes |
| Real OpenAI Smoke | Explicit Manual / Scheduled | No for normal change |
| AI Quality Evaluation | Release / AI Behavior Change | Acceptance Gate when triggered |
| AWS Smoke | AWS Change / Release | No for Local-only Phase 1 |

CIはJDK 25、Maven Wrapper、Dockerを使用する。Test ReportとScanner ReportはSecretを含まないことを確認したうえでFailure解析用Artifactとして保存する。RetentionはCI Provider選定時にSecurity Designへ追記する。

---

## 25. Phase 1 E2E Boundary

### 25.1 Backend Happy-path E2E

Phase 1では、iOS Simulatorを使用する最低1本のHappy-path E2Eを実装する。

```text
Flutter Integration Test
        ↓
Local Spring Boot Backend
        ├── FakeTextGenerationProvider
        └── DynamoDB Local
```

対象:

- UserがMessageを送信する
- 生成中Deltaが順番に表示される
- Completed Messageが確定表示される
- App再起動後に保存済みHistoryを表示できる

E2EでReal OpenAIを使用しない。Flutter `3.47.0`、Dart `3.13.0`、iOS 15以上のiOS SimulatorおよびFlutter SDKの`integration_test`を使用する。CIのSimulator Runtime / Device Modelと正確なIntegration Test Commandは実装開始時に固定するが、Test ToolまたはTarget Platformを変更してはならない。

### 25.2 Frontend Compatibility Test

次のForward Compatibility Caseは実Backendを使うHappy-path E2Eへ含めず、FlutterのSerialization / Event Handling Testで検証する。

- Responseへ未知のOptional Fieldが追加されてもFlutterが既知Fieldを読み取れる
- 未知のSSE EventをFlutterが無視し、その後の既知Eventを処理できる
- JSON、SSE Frame、SSE Stream、Event CountおよびAssistant Textの境界Fixtureから安全なError CategoryへMappingできる
- HTTP Chunkを1 Byte単位、UTF-8 Multi-byte途中、行途中およびFrame途中で分割しても同じSize判定になる

FlutterのJSON DecoderおよびSSE Event DispatcherへSynthetic FixtureまたはFake Transportを入力する。これらのCaseを作るために、Spring Boot BackendへTest専用Field、Event、EndpointまたはBusiness分岐を追加してはならない。

### 25.3 Frontend Widget and Golden Test Contract

`frontend-ui-design.md`のUI-001〜UI-016をFrontend UI Test Inputとして使用し、少なくとも次を`flutter test`で検証する。

- Width 375、390、430 logical pixelsのPortrait Layout
- Empty、History、Sending、Streaming、Partial Failure、Terminal Error、PaginationおよびKeyboard Open
- User / Alice / Pending / Streaming MessageのAlignment、最大幅およびSemantics
- Dynamic Type標準・拡大時のText切断、OverlapおよびHorizontal Overflow防止
- Composerの1〜5行拡張、10,000 Code Point Validationおよび44 x 44 pt以上のSend Action
- Keyboard表示時のAlice Core縮小または非表示、Composer FocusおよびMessage Scroll Anchor維持
- Follow-latest、`最新へ`、Older Page追加およびUser Scroll優先
- Markdown Fixture、Invalid / Incomplete Markdown、Code、Table、LinkおよびImage非読込
- Partial Assistant Contentが`途中までの回答`としてCanonical Messageと区別されること
- 初回Delta即時表示、50 ms Baseline Coalescing、Terminal Event時のPending Delta FlushおよびContent順序維持

Golden Testは少なくとも次を対象とする。

- Width 375、390、430の通常Conversation
- Empty、Initial Loading、Thinking、Streaming、ErrorおよびPagination
- Dynamic Type標準・拡大
- Reduce Motion有効時のStatic Alice Core
- Alice Core表示、縮小および非表示Boundary
- Core Assetを96、180、260 logical pixelsで表示した状態

AnimationをGolden Testへ入力する場合はClockまたはAnimation Controllerを固定し、任意Frame Timingへ依存させない。Golden Baselineの更新は意図したVisual変更としてReviewし、CI失敗を解消する目的だけで自動更新してはならない。

### 25.4 Frontend Accessibility Test Contract

Automated Semantics / Widget Testでは次を確認する。

- Header、Message、Error、Retry、Composer、Sendおよび`最新へ`のReading Order
- Message RoleとStreaming / Partial Failure StateのAccessible Label
- Icon-only ButtonのLabel、Enabled / Disabled StateおよびFocus Indicator
- Alice Coreが装飾状態で不要なFocus Targetにならないこと
- Streaming DeltaごとにScreen Reader Announcementを発生させないこと
- Core縮小・非表示およびKeyboard開閉でFocusを失わないこと
- Reduce Motion有効時にRing Rotation、Pulseおよび継続Scale変化が停止すること

Phase 1 UI ReviewではiOS Simulatorまたは実機を使用し、VoiceOver、Dynamic Type最大付近、Reduce Motion、Increase ContrastおよびHardware KeyboardをManual確認する。Automated Semantics TestだけでAccessibility完了とは判定しない。

### 25.5 Alice Core Asset and Performance Test Contract

`frontend/assets/images/alice_core/alice_core_base.png`について次を検証する。

- Assetが存在しFlutterからDecodeできる
- PNG、1024 x 1024、sRGB、Alpha Channel付きである
- 四隅が完全透過であり、黒・白・Checkerboard背景が焼き込まれていない
- File Sizeが2 MB以下である
- Ring、文字、LogoまたはWatermarkを含まないことをVisual Reviewする
- Dark BackgroundおよびCheckerboard上でClipping、White / Black HaloがないことをVisual Reviewする

Alice Core PerformanceはProfile Modeで代表的なMinimum Support Deviceまたは同等Simulatorを使用して確認する。

- Coreを`RepaintBoundary`へ隔離し、AnimationでMessage List、HeaderまたはComposerが継続Repaintされない
- AppがInactive / Backgroundの間は継続Animationを停止する
- 60 Hz表示でUI / Raster Frame p95が16.7 ms以内を目標とする
- 未達時はUI-013に従いGlow、Ring DetailまたはAnimation FrequencyをDesign Reviewで段階的に削減し、Particle数をRuntimeで不定に変更しない

### 25.6 Frontend UI Gate

通常Merge Gateでは`flutter analyze`と`flutter test`を必須とする。Golden Testを`flutter test`へ含める。

次の場合はManual UI / Accessibility / Profile Reviewを追加Gateとする。

- Visual Token、Layout、Alice Core、AnimationまたはGolden Baselineの変更
- Accessibility Semantics、FocusまたはDynamic Type対応の変更
- Flutter / iOS VersionまたはRendering Behaviorへ影響するDependency変更
- Phase 1 Release Candidate

---

## 26. Future Phase Test Architecture

### 26.1 Phase 2 Personal Memory

- Memory Extraction / Retrieval / Correction / DeletionのContract Test
- Sensitive Memory分類と利用制限
- Conversationを越えた継続性
- Irrelevant / Contradicting MemoryをContextへ混入しないこと
- User Data Export / Deletion / Retention
- Memoryを取得していないときに記憶を偽らないEvaluation

Phase 2開始前にStorage、Search、Dataset、Acceptance Criteriaを確定する。Phase 1にFake Memoryや空Datasetを追加しない。

### 26.2 Phase 3 Tools / External Services

- Tool PortごとのContract Test
- External APIはLocal Mock / Sandboxを優先
- Read / Change OperationのPermission差
- ApprovalとTarget / ArgumentsのBinding
- Idempotency、Timeout、Partial Failure
- Tool Output Prompt Injection
- Audit Event完全性
- Permission Denied時にExecutorが呼ばれないこと

Destructive OperationをProduction Resourceで自動Testしない。

### 26.3 Phase 4 Agent / PC / Browser

- PlannerとExecutorを分離したSimulation
- Maximum Step / Time / Cost
- Infinite Loop / Duplicate Action防止
- Approval、Replay Prevention、Cancellation / Resume
- File System / OS / BrowserのSandbox
- 実行済みと未実行のTraceability
- Side-effecting Stepの直列実行

### 26.4 Phase 4 Voice

- Synthetic AudioとConsent済みDataset
- STT日本語品質、固有名詞、Latency
- Wake Word False Positive / False Negative
- Barge-in、Correction、Provider Failure
- Voice経由でもTool / Agent Approvalを省略しない
- Audio Retention / Deletion

Phase 2〜4の具体Library、SDK、Browser Version、Device Matrixおよび性能値は各Phase実装前に再確認する。

---

## 27. Requirement Traceability

| Requirement ID | Requirement / Contract | Primary Test |
|---|---|---|
| `P1-FR-001` | Smartphone ApplicationからText Chat | Flutter Widget + Backend IT + E2E |
| `P1-FR-002` | 単一Conversationを継続利用 | Use Case Unit + Backend IT + E2E |
| `P1-FR-003`, `NFR-001` | Provider-independentなAI Integration | Port Contract + OpenAI Adapter Test |
| `P1-FR-004` | Alice人格・品質 | Prompt Test + AI Evaluation |
| `P1-FR-005` | Alice Response StreamingとCanonical Result確定 | SSE Parser Unit + Fake Provider Unit + SSE Backend IT + E2E |
| `P1-FR-006`, `P1-FR-007`, `NFR-002` | Conversation History保存 / 参照 | Repository Contract + DynamoDB IT + Backend IT + E2E |
| `P1-FR-008`, `NFR-006` | Safe Retry / Duplicate防止 / Partial Failure整合性 | Use Case Unit + DynamoDB Transaction IT + Backend Replay IT + Flutter State Test |
| `NFR-003` | Localhost Boundary、Secret / Content非Logging、OpenAI `store=false` | Configuration + Startup IT + Log Capture + Gitleaks + WireMock Adapter Test |
| `NFR-004`, `NFR-005` | 不要な先行実装を避け、Boundaryを維持 | Architecture Test + Dependency Review |
| `NFR-007` | Clock、ID、Provider、Repository等を制御可能にする | Unit / Contract / Integration Test Suite |
| `NFR-008` | 機密情報を記録せず処理を追跡 | Log Capture + Request ID Test |
| `NFR-009` | VersionとBuildの再現性 | Wrapper / Lock File / CI Environment Test |

実装時は、各Test ClassまたはTest Case Metadataへ上表のRequirement IDを記載する。Test名だけからRequirementを推測させてはならない。

---

## 28. AI Coding Assistant Implementation Rules

AI Coding Assistantは次を守る。

- Real OpenAI / AWSをDefault Testへ追加しない
- Unit TestからSpring Context、DockerまたはNetworkを起動しない
- `*Test`と`*IT`の命名を独自変更しない
- TestのためにProduction Security Invariantを無効化しない
- `Thread.sleep`でTimeout、Lease、TTLまたはRetryを検証しない
- Dynamic Portを固定値へ置き換えない
- Developer用DynamoDB TableをIntegration Testで共有しない
- DynamoDB LocalをMockだけで置き換えない
- OpenAI Adapter ContractをFakeだけで完了としない
- Natural Language Output全文を一般Unit Testで固定しない
- Secret、実ConversationまたはPersonal DataをFixture / Snapshot / Reportへ含めない
- Coverage達成のために意味のないAssertionや除外を追加しない
- Failed / Flaky Testを根拠なくDisableしない
- Phase 2〜4の空Test InfrastructureをPhase 1へ追加しない
- Test FrameworkまたはScannerを独自判断で変更しない

---

## 29. Phase 1 Completion Criteria

Phase 1 Test Implementationは次をすべて満たした場合に完了とする。

- `./mvnw test`がDocker / Credentialなしで成功する
- `./mvnw verify`がDynamoDB Local 3.3.0を自動起動して成功する
- `flutter analyze`が成功する
- `flutter test`が成功する
- iOS Simulator上のRequired `integration_test`が成功する
- UI-001〜UI-016のRequired Widget / Golden / Accessibility Testが成功する
- `alice_core_base.png`がUI-016のGeometry、Alpha、SizeおよびDecode条件を満たす
- Phase 1 Release CandidateでVoiceOver、Dynamic Type、Reduce Motion、Increase ContrastおよびAlice Core PerformanceのManual Reviewが完了する
- API / SSE / Database / AI / SecurityのRequired Scenarioが実装される
- Real OpenAI / AWSがDefault Testで呼ばれない
- Line 80% / Branch 70%のCoverage Gateを満たす
- Digest固定Gitleaks、Canary Self-testおよびDependency-Check Gateを満たす
- Test間に共有Data、実行順序依存または既知Flakinessがない
- Test ReportへSecret / Personal Contentが出力されない
- AI Evaluation Dataset最低50 CasesとRubricがVersion管理される
- Accepted AI Baselineが`ai-design.md`のCriteriaを満たす
- Test Commandと前提条件がREADMEまたはDevelopment Environmentから参照できる

---

## 30. Open Decisions and Implementation-time Reconfirmation

### 30.1 Non-blocking Open Decision

| Item | Status | Timing |
|---|---|---|
| CI Provider | Undecided | CI導入時。Stage Semanticsは本Documentで確定済み |
| Flutter Integration TestのCI Simulator Runtime / Device Model / Command | `integration_test`とiOS Simulatorを使用 | Frontend実装開始時。App Architectureを変更しない実行環境確認 |

### 30.2 Implementation-time Reconfirmation

次はArchitecture Decisionではなく、導入時の互換性・安全性確認である。

- Spring Boot 4.1.0 BOMが管理するJUnit / Mockito / AssertJ / Testcontainers Version
- Gitleaks Image Digestと既知Regressionの有無
- OWASP Vulnerability Data取得 / Cache方式
- TestcontainersとDocker RuntimeのmacOS / CI互換性
- WireMock 3.13.2とJava 25 / OpenAI SDK HTTP Stackの互換性

再確認で互換性問題が見つかった場合、同じ責務を満たすPatch / Minor Version更新はDesign Documentを更新して行う。Test BoundaryやSecurity Gateを変更する場合はArchitecture Decisionとして扱う。

Phase 1 Backend / Frontend Test実装を分岐させるArchitecture上の未決定事項は残さない。画面仕様は`frontend-ui-design.md`の承認後にImplementation Gateを通過する。

---

## 31. Review Checklist

- [x] Test Layerと責務が重複・欠落していない
- [x] Default TestがReal External Serviceへ接続しない
- [x] API / SSEのTerminal、Timeout、Disconnectを検証できる
- [x] DynamoDB Local Testが実AdapterとTest専用Tableを使う
- [x] OpenAI AdapterをWireMockで検証できる
- [x] Secret / ContentがTest Artifactへ漏れない
- [x] AI Software TestとQuality Evaluationが分離されている
- [x] Coverage数値だけで品質を判断していない
- [x] Phase 2〜4のBoundaryを設計しつつ先行実装を要求していない
- [x] AI Coding AssistantがFramework、Command、Namingを推測する必要がない

### 31.1 Review Result

| Item | Value |
|---|---|
| Review Date | 2026-08-16 JST |
| Review Scope | Phase 1 Test Detailed Design / Phase 2〜4 Test Architecture |
| Cross-document Scope | API、Database、AI、Security、Backend、Accepted ADR |
| Result | Approved |
| Required Correction | None |

Test Design Reviewおよび修正後再レビューの結果、Phase 1実装を分岐させる矛盾または未決定事項は確認されなかった。Section 30の項目は導入時の互換性・安全性再確認であり、Test Architectureの未決定とは扱わない。

### 31.2 Phase 2 Detailed Test Review Result

| Item | Value |
|---|---|
| Review Date | 2026-09-01 JST |
| Review Scope | Phase 2 Requirement、API、Persistence、Security、Frontend Test Contract |
| Stable IDs | P2-TC-FR-001〜065、P2-TC-NFR-001〜008、P2-DB-TC-001〜017、P2-SEC-TC-001〜020 |
| Result | Completed — Passed |
| Open Finding | 0 |

Phase 2のRequirement 73件、Persistence Access Pattern 17件およびSecurity Test 20件を実行可能なTest Layerへ割り当てた。新しいRequirementまたは設計境界変更がない限り、Test ID追加だけを目的とした再レビューRoundを要求しない。

---

## 32. Glossary

| Term | Meaning |
|---|---|
| Unit Test | 単一のRuleやClassを外部依存なしで検証するTest |
| Integration Test | 複数Componentまたは実Adapterの接続を検証するTest |
| E2E Test | User操作から結果表示までの一連の流れを検証するTest |
| Test Double | Fake、Mock、Stub等、Testで実依存の代わりに使うもの |
| Fake | 単純化した動作可能なTest実装 |
| Mock | 呼出や引数等のInteractionを検証するTest Double |
| Contract Test | 同じPortを実装するComponentが共通の約束を守るか検証するTest |
| Smoke Test | 実Serviceとの最小限の接続・互換性確認 |
| AI Evaluation | AI回答の品質をRubricやHuman Reviewで測る評価 |
| Coverage | Test実行時に通過したCodeの割合。正しさそのものではない |
| Flaky Test | Code変更なしでも成功・失敗が変わる不安定なTest |

---

## 33. References

- JUnit User Guide: https://docs.junit.org/
- Spring Boot Testing: https://docs.spring.io/spring-boot/reference/testing/
- Apache Maven Failsafe Plugin: https://maven.apache.org/surefire/maven-failsafe-plugin/
- Testcontainers for Java: https://java.testcontainers.org/
- WireMock: https://wiremock.org/docs/
- JaCoCo: https://www.jacoco.org/jacoco/
- OWASP Dependency-Check: https://owasp.org/www-project-dependency-check/
- Gitleaks: https://gitleaks.io/
- OpenAI Prompt Engineering: https://developers.openai.com/api/docs/guides/prompt-engineering
- OpenAI Production Best Practices: https://developers.openai.com/api/docs/guides/production-best-practices
- OpenAI Evals Guidance: https://developers.openai.com/blog/eval-skills

---

## 34. Summary

Phase 1のTest Architectureは次のように確定する。

1. 日常TestはJUnit JupiterとFakeを中心に高速・決定論的に実行する
2. `./mvnw verify`ではTestcontainersでDynamoDB Local 3.3.0を自動起動する
3. OpenAI AdapterはWireMock 3.13.2、Real OpenAIは明示Opt-in Smokeだけで検証する
4. HTTP / SSEは実ServerとJDK `HttpClient`でEvent順序、Timeout、Disconnectまで確認する
5. AI Software Testと回答品質Evaluationを分離する
6. Gitleaks、OWASP Dependency-Check、JaCoCoをMerge Gateへ組み込む
7. Phase 2〜4のTest / Evaluation Boundaryは設計するが、Phase 1へ未使用Componentを実装しない

---

## 35. Phase 2 Private LAN and Restore Acknowledgement Test Contract

### 35.1 Status and Traceability

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-161〜API2-172 |
| Requirements | P2-FR-026、027、037、063、065、P2-NFR-006、007 |

### 35.2 Security Profile Matrix

| Case | Expected Result |
|---|---|
| `LOOPBACK_ONLY` + `127.0.0.1` | Authenticationなしで許可 |
| `LOOPBACK_ONLY` + LAN Bind | Startup Failure |
| `PRIVATE_LAN_SECURE` + Valid TLS + Active Token | 許可 |
| Private LAN + Plain HTTP | Client送信拒否またはConnection拒否 |
| Missing / Invalid / Unknown / Revoked Token | 同じ`401 DEVICE_AUTHENTICATION_REQUIRED` |
| Certificate Chain / Hostname / Expiry不正 | TLS Failure、検証無効化なし |
| Security Material不足 | Fail Closed、HTTP Fallbackなし |
| Memory以外の`/api/v1/**` | 同じAuthentication Boundary |

Unit TestはProfile Validation、Token形式、HMAC Digest、Constant-time Comparison、Error MappingおよびRedactionを検証する。Integration TestはFilter ChainがBody解析 / Controller実行より前にAuthenticationを行うことを検証する。

すべての`DEVICE_AUTHENTICATION_REQUIRED`で`retryable = false`、`recoveryAction = REAUTHENTICATE_DEVICE`および同じ安全な外部Detailになることを確認する。

### 35.3 Credential Lifecycle Test

- CSPRNG Tokenが256 bit以上であること
- Backend PersistenceとLogにToken原文が存在しないこと
- 発行成功前にTokenをUserへ表示しないこと
- Revoke後に旧Tokenを拒否すること
- Rotate時に新Tokenを許可し旧Tokenを拒否すること
- Persistence Timeout / Result Unknownで成功表示しないこと
- Digest Key欠落または未知Key VersionでFail Closedとなること
- Authorization Header、Token、Digest、Certificate KeyがTest Reportへ出ないこと

Platform Secure Storage AdapterはiOS / macOS KeychainとWindows Credential ManagerのContract Testを共通Suiteで実行する。Phase 2 Release Gateでは実機iPhoneからPrivate LAN HTTPS接続するE2Eを最低1本実施する。Desktop版追加時は同じSuiteを対象Platformへ追加し、API ContractをForkしない。

### 35.4 Restore Warning Contract Test

| Assessment | `deletionHistoryWarningAcknowledged` PATCH | Expected State |
|---|---|---|
| `AVAILABLE` | Field省略 | Response `null`、通常条件で`READY`可能 |
| `AVAILABLE` | `true`または`false` | `400 INVALID_COMBINATION` |
| `UNAVAILABLE` | Field省略 | 初期`false`、`NEEDS_REVIEW` |
| `UNAVAILABLE` | `true` | Version / ETag更新、他条件充足時だけ`READY` |
| `UNAVAILABLE` | `false` | 確認解除、`NEEDS_REVIEW` |

さらに次を検証する。

- GET、画面表示、全体確認または再導入確認だけでAcknowledgementが`true`にならない
- Acknowledgement変更で旧Confirmation Tokenが無効になる
- TokenがAssessmentとAcknowledgementへBindingされる
- Archive / Installation / Guard Boundary変化でPlanがInvalidatedとなる
- `false`または`null`の意味をFlutterが取り違えない
- Warning本文、Archive Memory本文およびTokenをLog / Golden Failureへ出さない

### 35.5 Focused Re-review Gate

CR-001とCR-002のFocused Re-reviewは次を満たした場合にPassとする。

1. API、Security、Database、FrontendおよびTestのProfile名とError Codeが一致する。
2. iPhone専用Storage表現がなく、Platform Secure Storageへ一般化されている。
3. Restore AcknowledgementがResponse、PATCH、LifecycleおよびToken Bindingで一貫する。
4. Private LANにAuthenticationなしの迂回Endpointがない。
5. Secret / Personal DataのResponse、LogおよびTest Artifact除外が一貫する。

---

## 36. Phase 2 Plan Lifetime and Cursor Contract Test

### 36.1 Status

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-173〜API2-184 |

Fake Clockを使用して次の境界を決定的に検証する。

| Boundary | Accept | Reject / Recovery |
|---|---|---|
| Plan Review | `createdAt + 4h`未満 | 到達後`PLAN_EXPIRED`、新Plan作成 |
| Confirmation Token | 発行から15分未満かつReview期限内 | Token期限切れは`RECONFIRM_PLAN` |
| Page Cursor | 発行から30分未満かつReview期限内 | 期限切れは先頭Page再取得 |

GET、PATCH、Confirm失敗、Pollingおよび再接続でReview期限が延長されないことを確認する。Token期限切れだけでPlanが`EXPIRED`にならず、同じPlan Versionへ再Confirmできること、最初のDurable Execute Intentで同Planの全Tokenが無効になることを検証する。

Restoreでは100 Page相当のAction Setを生成し、Resolution PATCHでPlan Versionが変化しても不変`actionSetVersion`のCursorで次Pageへ進めることを確認する。Action集合・順序変更、別Plan流用、改変、Cursor期限切れまたはPlan Terminal化では拒否する。

Review期限、Terminal、Invalidation、Process RestartおよびCleanup Failureの各経路でToken、Preview本文、Hydration CacheおよびSealed Stateが利用不能になり、Content-free Metadata / Resultだけが最低24時間照合可能であることを検証する。Mobile / Desktop Client Contract Testは同じRecovery Actionと時間境界を共有する。

---

## 37. Phase 2 Backup Size Boundary Test

### 37.1 Status

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-185〜API2-192、MD-109〜MD-113 |

| Target | Accept | Reject |
|---|---:|---:|
| Decrypted Payload | 267,386,880 Byte | 267,386,881 Byte |
| Encrypted Archive | 268,435,456 Byte | 268,435,457 Byte |
| Multipart Request | 269,484,032 Byte | 269,484,033 Byte |
| Public Header | 4,096 Byte | 4,097 Byte |

ExportのPayload生成・Envelope予測・完成File実測、RestoreのMultipart・Archive Part・Declared Length・復号Sink、FlutterのDownload / Upload事前検証を同じContract Fixtureで試験する。`Content-Length`欠落・虚偽・Overflow・Chunked Transferでも実読込上限を超えないことを確認する。

Size超過は`413 BACKUP_ARCHIVE_TOO_LARGE`、危険な構造Parameterは`422 UNSAFE_ARCHIVE_PARAMETERS`となることを確認する。Partial Archive、切断Payload、Record省略またはRaw ByteをFailure Log / Test Artifactへ残さない。

---

## 38. Phase 2 Restore Quota and Cleanup Test

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-193〜API2-205、MD-114〜MD-122、DB2-165〜DB2-177 |

並行する二つのPlan Createで、Active Slot / Byte Reservationを一つだけ取得できることを検証する。同じIdempotency-KeyのRetryは同じPlan / Failureへ収束し、別Archiveへの再利用はConflictとなることを確認する。

Restore Control成功・Crypto Control競合、およびCrypto Control成功・Restore Control競合をFault Injectionし、Transaction Abort後に片側Reservationが残らないことを確認する。同時Export / Inspect、二つのInspect、二つのExportで固定Lock順序とError優先順位を共通Contract Testする。

Retained 544 MiB、Working 768 MiB、Inspection Lease 30分の境界値と上限超過をFake Clock / Bounded Fileで検証する。新Requestが既存Planを自動失効させず、Resume / Cancelへ誘導されることを確認する。

Validation / KDF / AEAD / Size Failure、Client Disconnect、Cancel、Expiry、Invalidation、Terminal、Process Restart、周期SweeperおよびReservation前CleanupをFault Injectionする。Artifact、Pending WorkerおよびKeyが利用不能になるまでQuotaを解放しないこと、Cleanup失敗を成功表示しないことを確認する。

KDF前 / 中、AEAD直後、Crypto Slot解放前後およびPlan Publish前後でCrashさせ、同じOperation ID / Lease EpochへRecoveryすることを確認する。`CLEANUP_PENDING`中は新Upload / Exportを拒否し、Cleanup完了後のTerminal ResultとControl解放がAtomicに可視化されることを検証する。

Mobile / Desktop Contract TestでIdempotency-Key再利用、Cancel可能状態、`EXECUTING` Cancel禁止およびApplication終了非Cancelを共通検証する。Test Failure ArtifactへPassphrase、Archive Byte、Temporary PathまたはMemory本文を出力しない。

---

## 39. Phase 2 Memory Relation Review Contract Test

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-206〜API2-220、MD-123〜MD-133、DB2-178〜DB2-192 |

`COMPLEMENTARY_SAME_FACT`、`SUPERSEDES`、`CONFLICT`、`UNCERTAIN`の各Relationについて、`UPDATE_TARGET`、`ADD_AS_NEW`、`SKIP`のAllowed Matrix、推奨表示およびUser明示選択をContract Testする。ProblemはReview ID / URI / Type / Count / Expiryだけを返し、関連ID一覧、本文、Scoreおよび理由を含まないことを確認する。

Fake Clockで固定30分の直前 / 到達、Active 20 / 21件、Related 100 / 101件、Page 20件、Response 2 MiBおよびResolve Body 4 KiBを境界試験する。Source / Related Version Race、Guard / Reset変化、新Relation発生、ETag不一致、未知Target、禁止ResolutionおよびHard Rule違反でMutationされないことを検証する。

同一Register / Update Idempotency-Keyが同じReviewへ、同一Resolve Keyが同じTerminal / Unknown Resultへ収束すること、異なるPayloadへのKey再利用がConflictになることを確認する。Crash / TimeoutをDurable Intent前後へ注入し、`UNKNOWN`を新規Operationで再実行しない。

UIと自然言語の両経路で同じ`ResolveMemoryRelationUseCase` Contract Suiteを実行する。Active Reviewが複数ある状態の「はい」「それで」ではMutationせず確認し、明示的なReview / Resolutionだけが実行されることをE2Eで確認する。Failure Artifact、LogおよびSnapshotへCandidate / Preview本文、暗号Material、Memory本文またはTokenを残さない。

Relation State Keyについて、新旧VersionでのCreate / GET / Resolve、Rotation後の新Version固定、2 Version参照中の3番目Rotation拒否、Active / Preparing / Cleanup Pending CountとReference Query照合を検証する。Write / Decrypt Key欠落、Provider一時不能、未知Version、AAD / Ciphertext改変、別Review / TargetへのCopyではFallback復号せずReviewがInvalidation / Cleanupへ進むことを確認する。

Startup / Sweeper、Retirement、CleanupおよびCount更新の各境界へCrash / Timeoutを注入し、参照中Keyの早期削除、Version Count二重減算、Count不一致中のRetirementが発生しないことを検証する。Relation KeyとDevice / Cursor / Guard / Idempotency / Backup Keyを交換したNegative Testは必ず失敗させる。

---

## 40. Phase 2 Requirement Test ID Registry

| Item | Value |
|---|---|
| Status | Accepted |
| Source | `phase2-requirement-traceability-matrix.md` |
| IDs | P2-TC-FR-001〜065、P2-TC-NFR-001〜008 |

専用Matrixの73個のStable Test IDをPhase 2 Test Report / Case MetadataのRequirement Keyとして使用する。Test Class名、Method名またはFrameworkをIDへ埋め込まず、一つの実行可能Testが複数Requirementを検証する場合は全該当IDを付与する。

CIはApproved Requirementの全IDが少なくとも一つの実行可能Test、Contract Suite、E2EまたはAI EvaluationへBindingされていることを検証する。未Binding、未知ID、重複定義またはMatrixの`OPEN GATE`をPhase 2 Implementation Readiness Failureとする。Test実装前の詳細設計段階では、MatrixにTest Levelと責務Ownerが割り当てられていることをGateとする。

---

## 41. Phase 2 Field Validation Ordering Test

| Item | Value |
|---|---|
| Status | Accepted |
| Source | CR-008、API2-227〜API2-230 |
| Requirement IDs | P2-TC-NFR-004、P2-TC-NFR-006 |

13個のField Error Codeについて、全Codeが固定優先順位へ一度だけ含まれることをContract Fixtureで検証する。同一Pathへ複数Violationを人工的に与え、最上位一件だけが返ること、異なるPathではSchema順とArray Index順が維持されることを確認する。

特に次の抑止組合せを固定する。

| Primary Error | Suppressed Validation |
|---|---|
| `REQUIRED` | Value-dependentな全Validation |
| `AT_LEAST_ONE_REQUIRED` | Empty Object配下のField Validation |
| `INVALID_TYPE` | Format / Enum / Character / Blank / Length / Size / Range / Duplicate |
| `UNKNOWN_FIELD` | Unknown Valueの内容Validation |
| `INVALID_COMBINATION` | Combinationより後段の意味Validation |

Top-level Empty PATCHの`field = "$"`、Nested Empty Filterの`field = "filters"`、未知Fieldだけを含むObject、50 / 51件の切断境界およびValidator登録順を入れ替えた場合の同一ResponseをTestする。Rejected ValueまたはMemory本文をFailure Snapshotへ含めない。

---

## 42. Phase 2 Deletion Recovery Fence and Atomic Finalization Test

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | DB2-151〜DB2-164 |
| Review Finding | DB-CR-001 Resolved |
| Requirement IDs | P2-TC-FR-024〜028、P2-TC-FR-060〜065、P2-TC-NFR-004 |

Fake Clock、Fault InjectionおよびDynamoDB Local Contract Testで、Target Cutover前、Commit直後、Cleanup中、Receipt更新前後およびFinalization Transaction前後のCrashを注入する。Root、Guard、ReceiptおよびContent-bearing Copyの組合せを`DELETED`、`NOT_DELETED`、`RECOVERY_PENDING`または`UNKNOWN`へ一意に分類できることを検証する。

`UNKNOWN`が一件でも残る間はCreate、Update、Confirm、Delete、Automatic Capture、Restore Execute、Relation ResolveおよびBackup Exportを拒否し、ReadがRoot不存在のMemory本文を返さないことを確認する。Lease期限切れ、Process RestartおよびWorker TakeoverでもFenceを強制解放せず、同じOperation ID / `finalizationId`を使用する。

Finalization Contract Testは次を検証する。

- `unknownCount = 0`の場合だけTerminal化する
- `currentMemoryCount = baselineCount - deletedCount`を一度だけ適用する
- `deletedCount > 0`の場合だけBoundaryを一度進める
- `ALL` CompletedだけReset Generationを進める
- `ALL` Partial / FailedではPending Resetを同Transactionで取消する
- Plan、Operation、Idempotency Binding、Reset PointおよびFence解放がAtomicに可視化される
- Timeoutによる結果不明後のRetryでCountを二重減算しない

Backup ExportはBuild、Seal、HTTP Response Commitの各時点でFenceを注入し、Partial Archiveを返さずTemporary ArtifactをCleanupすることを検証する。Failure Artifact、LogおよびMetric LabelへMemory ID、Target一覧、Guard Digest、Boundary値、Count値、Operation IDまたはException Messageを残さない。

---

## 43. Phase 2 Persistence Access Pattern and Medium Review Contract Test

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | DB2-001〜DB2-243 |
| Review Findings | DB-CR-004〜DB-CR-008 Resolved |
| Stable Registry | P2-DB-TC-001〜017 |

P2-DB-TC-001〜017をP2-DB-AP-001〜017へ一対一で割り当て、各Case Metadataに検証対象DB2 DecisionとUnit、Persistence Port Contract、DynamoDB Local Integration、Crash / Unknown Recovery、Security NegativeのTest Layerを付与する。未割当Decision、未知Decision、重複Test IDまたはLedger未登録TransactionをImplementation Readiness Failureとする。

Relation ReviewではCleanup各段階とFinal Release Transaction前後へCrash / Timeoutを注入し、Ciphertext利用不能前の解放、Global / Key Count二重減算、負Count、Directory残留およびActive Slot恒久消費が発生しないことを確認する。

ExportではMissing + Duplicate、Dangling + Missing、同一ID複数Version、Ordinal欠番、Stale Version、Repair / Promotion中断を注入する。Raw / Unique / Snapshot / Hydrated CountとCanonical Source Set Digestの全一致がなければSeal / Response Commitされないことを確認する。

TXL-001〜016から最大Transaction Fixtureを生成し、Action数、Encoded Byte、PK / SK一意性、Condition統合および必須Action集合を検証する。各上限ちょうどは成功し、一Actionまたは一Byte超過ではSDK呼出し前に全体を拒否し、Index、Revision、Boundary、ResultまたはBindingを省略しない。

Restore Selectionでは96 / 127 / 128 Delta、2 MiB前後、2 / 3 Generationおよび228 / 229 Itemsの境界を試験する。Build、Pointer Swap、旧Generation Cleanup、Expiryの各段階でCrashさせ、同じCompaction ID / Epochへ収束すること、Hard Limit後のPATCHだけがRetryableに停止し、既存Planを破棄しないことを確認する。

Conditional Race、Transaction Cancellation、Timeout結果不明、BatchGet / BatchWrite Unprocessed Item、Strong / Eventual Read差、TTL遅延、Lease Takeover、Fence競合、Count / Digest不一致、Key Rotation / 欠落、Ciphertext / Cursor / Item改変およびCorrupt Itemを必須Scenarioとする。Localで再現できないAWS Failureは決定的Fault Adapterを使用し、Real AWSを通常CI Gateにしない。

---

## 44. Phase 2 Security・Logging・Observability Test Registry

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Source Decisions | SEC2-001〜038 |
| Review Findings | SEC-CR-001〜009 Resolved |
| Stable Registry | P2-SEC-TC-001〜020 |

| Test ID | Primary Layer | Required Verification |
|---|---|---|
| P2-SEC-TC-001 | Domain + Architecture Contract | Data Classification別のPersistence / Log / Context / Backup許可Matrix |
| P2-SEC-TC-002 | Application + Log Capture | Secret明示保存拒否と全出力Channel非露出 |
| P2-SEC-TC-003 | Domain + Application | Sensitive Automatic Capture拒否とDirect Necessity Gate |
| P2-SEC-TC-004 | Port Contract + Fault Injection | Provider直前のAuthoritative Version / State / Preference再検証 |
| P2-SEC-TC-005 | Prompt Contract + AI Evaluation | Memory内InstructionがTool権限・System指示へ昇格しない |
| P2-SEC-TC-006 | Configuration + Crypto Contract | Purpose別Key共有拒否、Startup Gate、最大2 Version、Retirement |
| P2-SEC-TC-007 | Port Contract + Fault Injection | Key欠落 / AEAD / HMAC失敗のCapability限定Fail Closed |
| P2-SEC-TC-008 | Log Capture + Integration | Application / Security Log Field Allowlistと禁止Data非出力 |
| P2-SEC-TC-009 | Configuration + Async Integration | Log Rotation、Permission、MDC Clear、Access Log Redaction |
| P2-SEC-TC-010 | Meter Registry Contract | Metric Tag Cardinalityと禁止Dimension検出 |
| P2-SEC-TC-011 | Backend HTTP Integration | Health Detail非公開、No-content Probe、Actuator Loopback限定 |
| P2-SEC-TC-012 | Fake Clock + Fault Injection | Projection / Cleanup / Fence / Quota閾値のDegraded遷移 |
| P2-SEC-TC-013 | Application + Persistence Contract | Usage Trace、Operation Resource、Security EventのAuthority分離 |
| P2-SEC-TC-014 | DynamoDB Local + Fake Clock | Retention、Logical Expiration、TTL遅延、Cleanup順序 |
| P2-SEC-TC-015 | Archive Contract + Security Negative | Backup除外SetとExport後外部Boundary |
| P2-SEC-TC-016 | Filesystem + Restart Integration | Restore Temporary Path、Link、Quota、Cleanup、Restart |
| P2-SEC-TC-017 | Backend + Flutter Integration | LAN TLS、Device Token、Revoke / Rotate、Body解析前認証 |
| P2-SEC-TC-018 | Flutter Port Contract | iOS / macOS / Windows Secure Storage Adapter Contract |
| P2-SEC-TC-019 | Flutter Configuration + Widget | Crash / Analytics Default無効とClient `no-store` |
| P2-SEC-TC-020 | Startup Configuration Test | Security Configuration禁止値とStartup Fail Closed |

各Case Metadataへ対象SEC2 DecisionとP2-FR / P2-NFR IDを付与する。P2-SEC-TC-001〜020の未Binding、未知ID、重複定義、禁止Dataを含むFailure ArtifactまたはSecurity Gateを無効化したTest ProfileをPhase 2 Implementation Readiness Failureとする。

P2-SEC-TC-006、007、012、014、016、017はHappy Pathだけで完了扱いにせず、Crash、Timeout、結果不明、Process Restart、Key欠落、Permission不正およびCleanup失敗を決定的Fault Adapterで検証する。Real OpenAI、Real Device Credentialまたは実Personal Dataを通常CIへ使用しない。

---

## 45. Phase 2 Frontend UI Test Registry

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Source Decisions | FUI2-001〜FUI2-030 |
| Review Finding | P2-FINAL-CR-001 Resolved |
| Stable Registry | P2-FUI-TC-001〜012 |

| Test ID | Primary Layer | Required Verification |
|---|---|---|
| P2-FUI-TC-001 | Widget + Repository Contract | ListのLoading、Content、Empty、Pagination、FailureとStale Content抑止 |
| P2-FUI-TC-002 | Widget + API Client Contract | 明示Search、500文字 / 2 KiB、Filter、Cursor Reset、URL非露出 |
| P2-FUI-TC-003 | Widget + Integration | Detail Field、未確認表示、明示ConfirmとCapture Type不変 |
| P2-FUI-TC-004 | Widget + API Contract | Create / Edit Validation、Cancel、Server確認後だけのSuccess |
| P2-FUI-TC-005 | Widget + Concurrency Contract | ETag Conflict、揮発性Draft保持、最新取得、自動上書き禁止 |
| P2-FUI-TC-006 | Widget + Mobile E2E | Relation Review遷移、Allowed Resolution、Review / Target / Version Binding |
| P2-FUI-TC-007 | Widget + Repository Contract | 二つのPreferences独立更新、Failure Rollback、OFFで既存Memory非削除 |
| P2-FUI-TC-008 | Widget + Integration | Usage TraceのUnchanged、Updated、Deleted、Unavailable、NOT_RECORDED |
| P2-FUI-TC-009 | Widget + Mobile E2E | 個別削除確認、Swipe-only抑止、削除後Stale Resource除去 |
| P2-FUI-TC-010 | Widget + Fault-injection E2E | 複数 / 全削除Plan、期限、Token、Partial / Unknown、再照合 |
| P2-FUI-TC-011 | Widget + Security Integration | Backup Passphrase非永続・非Log・非ClipboardとAuthoritative完了 |
| P2-FUI-TC-012 | Widget + Mobile E2E | Restore Inspect、Plan、Warning、Confirm、Resume / Cancel、Result |

各CaseはiOS PresentationとPlatform-neutral Application Stateを同じFixtureで検証する。Desktop版追加時はLayout Adapter固有Testを追加するが、API、State、Concurrency、ConfirmationおよびPrivacy ContractをForkしない。

P2-FUI-TC-005、006、010、012はHappy Pathだけで完了扱いにせず、Timeout、Network切断、Process / Application再開、期限切れ、Version競合、PartialおよびUnknownを決定的に検証する。Golden / Snapshot、Failure Message、Analytics、Crash ArtifactおよびTest ReportへMemory本文、Passphrase、Token、内部Scoreまたは削除済みContentを残さない。

---

## 46. Phase 4 Formal Test Registry — 2026-09-04

**Status:** Accepted / Integrated

Source Decisions:

- AGENT4-105 State Machine
- AGENT4-106 Permission / Approval / Risk
- AGENT4-107 PC / Browser / Application Safety
- AGENT4-108 Prompt Injection / Adversarial
- AGENT4-109 Crash / Recovery / Unknown Outcome
- AGENT4-110 Voice / Ambient / Safety
- AGENT4-111 Cross-Phase E2E / Visual Drift Gate

Required registries / gates:

- AgentExecution normal and invalid transitions
- Lifecycle Status ≠ Outcome
- Capability × Operation × Scope × Device × Permission Lifetime × Risk × Approval matrix
- Filesystem traversal / symlink / scope / destructive tests
- Structured Terminal / privilege tests
- Browser `READ ≠ INTERACT ≠ COMMIT`, `FORM INPUT ≠ SUBMIT`, `DOWNLOAD ≠ EXECUTE`, `LOGIN ≠ PERMISSION`, Origin isolation
- Application focus / stale element / clipboard / drag-drop / sensitive UI
- External-content prompt injection with execution-boundary rejection
- Crash-point injection before / after dispatch and no blind retry
- stale fence / replay / stale executor rejection
- voice ambiguity / ambient audio / voice ≠ identity / screen escalation
- Emergency Stop semantics
- Golden / visual review: Ambient, Conversation, Agent Running, Approval, Unknown Outcome, Emergency Stop, Desktop Spatial
- Cross-phase E2E: filesystem → test → fix → test → push; push is a separate action and requires applicable approval

Acceptance: Critical Open = 0, High Open = 0, Blocking Medium Open = 0.

P4-NFR-021 version pinning remains an implementation-readiness gate and does not weaken the accepted design registry.


---

## 47. UI-VIS-SOT-001 Visual Regression / Golden Registry — 2026-09-05

**Status:** Accepted / Integrated  
**Gate:** Visual Drift is blocking.

Golden / visual states:

| ID | Reference State |
|---|---|
| GV-01 | Mobile Empty / Idle |
| GV-02 | Mobile Conversation / History |
| GV-03 | Mobile Thinking / Streaming |
| GV-04 | Mobile Keyboard Open / Core Reduced |
| GV-05 | Mobile Reduce Motion / Large Text |
| GV-06 | Ambient / Listening |
| GV-07 | Agent Running — Minimal |
| GV-08 | Approval Required / Permission Required |
| GV-09 | Unknown Outcome / Verifying |
| GV-10 | Emergency Stop |
| GV-11 | Desktop Single-display Alice-centered Conversation |
| GV-12 | Dual-display role composition and migration |
| GV-13 | Triple-display role composition, disconnect migration and Reduce Motion |

Visual Regression Gate:
- compare against UI-BL-001 and UI-VIS-SOT-001, not memory;
- Alice Core / Conversation hierarchy must remain intact;
- default permanent capability navigation is prohibited;
- current Core renderer must remain replaceable;
- color, glow, motion, typography, panel density and responsive behavior must remain inside accepted visual contracts;
- Golden images must never be regenerated merely to make a failing test pass;
- display disconnect/reconnect must preserve semantic state and must not invent new execution authority;
- Critical / High / Blocking Medium visual findings must equal 0 before visual approval.
