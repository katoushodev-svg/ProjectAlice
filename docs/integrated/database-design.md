# Project Alice - Database Design

## 1. Document Information

| Item | Value |
|---|---|
| Document | `database-design.md` |
| Project | Project Alice |
| Target | Phase 1 Conversation / Phase 2 Personal Memory Persistence |
| Status | Phase 1: Approved / Phase 2: Reviewed — Implementation Ready |
| Last Updated | 2026-08-31 JST |

本ドキュメントは、Project Alice Phase 1のConversation HistoryとPhase 2のPersonal MemoryにおけるDynamoDB詳細設計を定義する。Phase 1 ContractはApprovedであり、Phase 2 Sectionは承認単位ごとに`Proposed`または`Accepted`を明示する。

本ドキュメントは以下の事項についてSource of Truthとして扱う。

- DynamoDB Table / Key Design
- Persistence Item Schema
- Access Pattern
- Message Ordering
- Message保存順序
- Atomicity / Failure Consistency
- Cursor Encoding / Integrity
- Idempotency Record
- Processing Lease
- Conversation Busy Lock
- DynamoDB Development Environment

AI Coding Assistantおよび開発者は、本ドキュメントで確定したPersistence Contractを独自判断で変更してはならない。

---

## 2. Related Documents

| Document | Responsibility |
|---|---|
| `requirements.md` | Phase 1機能要件・非機能要件 |
| `mvp.md` | Phase Scope・成功条件 |
| `alice-architecture.md` | System Architecture |
| `backend-design.md` | Backend内部Architecture・Layer責務 |
| `api-design.md` | API Contract・Pagination・IdempotencyのObservable Rule |
| `development-environment.md` | DynamoDB Development Environment |
| `security-design.md` | Secret・Logging・Data Protection |
| `test-design.md` | Persistence Test Strategy |
| `decisions.md` | Accepted Architecture Decision |
| `memory-design.md` | Phase 2 Personal Memory Domain / Lifecycle / Port Boundary |
| `phase2-requirement-traceability-matrix.md` | Phase 2 Requirement / Design / Test ID Traceability |

---

## 3. Goals

Phase 1 Database Designでは以下を実現する。

- 単一ConversationのConversation Historyを永続化できる
- 最新MessageおよびCursorより古いMessageを効率的に取得できる
- AI Context生成用の最新Messageを取得できる
- 重複Message送信および重複AI呼び出しを防止できる
- 同一Conversationへの同時送信を制御できる
- Failure時のPersistence状態を回復可能かつ追跡可能にする
- DynamoDB固有ModelをApplication / Domainへ漏らさない
- AI Coding AssistantがTable、Key、TransactionおよびFailure Ruleを推測せず実装できる状態にする

---

## 4. Phase 1 Scope

### 4.1 In Scope

- Conversation Item
- Message Item
- Idempotency Item
- Conversation History Persistence
- Cursor Paginationに必要なKey Design
- Message送信のAtomicity / Failure Consistency
- Processing Lease
- Conversation Busy Lock
- Local Development / Integration Test用DynamoDB環境

### 4.2 Out of Scope

- Personal Memory
- User Profile / Preference
- Conversation一覧
- Conversation削除・Reset
- Message編集・削除
- Full-text Search
- Semantic Search
- Vector Search / RAG
- Multi User Partitioning
- Tool Execution History
- Audit Log専用Storage

Phase 1のConversation Historyのために`memory` FeatureまたはPersonal Memory Itemを作成してはならない。

---

## 5. Architecture Boundary

Conversation HistoryはCore側が所有する`ConversationRepository`を介して永続化する。

```text
Application / Domain
        │
        ▼
ConversationRepository
        ▲
        │ implements
DynamoDbConversationRepository
        │
        ▼
DynamoDB
```

以下をApplication / Domainへ公開してはならない。

- DynamoDB SDK Object
- DynamoDB AttributeValue
- Partition Key / Sort Key
- Table Name
- LastEvaluatedKey
- DynamoDB固有Exception

---

## 6. Confirmed Access Patterns

Phase 1では以下のAccess Patternのみを必須とする。

| ID | Access Pattern |
|---|---|
| DB-AP-01 | 単一Conversationの基本情報を取得する |
| DB-AP-02 | 最新Messageを最大100件取得する |
| DB-AP-03 | Cursorより古いMessageを最大100件取得する |
| DB-AP-04 | AI Context用に最新Messageを指定件数取得する |
| DB-AP-05 | 初回送信時にConversationを作成する |
| DB-AP-06 | User Messageを保存する |
| DB-AP-07 | Assistant Messageを保存する |
| DB-AP-08 | `Idempotency-Key`の状態を取得する |
| DB-AP-09 | Message送信開始時にIdempotencyと排他状態を確保する |
| DB-AP-10 | 成功時にIdempotencyをCompletedへ更新する |
| DB-AP-11 | 失敗時にIdempotencyをFailedへ更新する |
| DB-AP-12 | 期限切れのProcessing / Busy Lockを回復する |

Conversation一覧、Message検索、DeleteまたはPersonal Memory用のAccess Patternを先行追加してはならない。

---

## 7. Confirmed Table Strategy

### 7.1 Single Table

Phase 1のConversation Persistenceには1つのDynamoDB Tableを使用する。

```text
Conversation Table
├── Conversation Item
├── Message Item
└── Idempotency Item
```

### 7.2 Index Policy

Phase 1ではGlobal Secondary IndexおよびLocal Secondary Indexを作成しない。

すべてのAccess PatternはPrimary Keyに対する`GetItem`、`Query`、`PutItem`、`UpdateItem`、`DeleteItem`または`TransactWriteItems`で実現する。

### 7.3 Scan Policy

Applicationの通常処理でDynamoDB `Scan`を使用してはならない。

TestまたはLocal DevelopmentのData Cleanupでの利用可否はTest Design / Development Environmentで別途定義する。

### 7.4 Configuration

Table NameはEnvironment Configurationから指定し、Java Source CodeへHard Codingしない。

具体的なTable NameおよびEnvironment Variable名は本ドキュメントのConfiguration Sectionで定義する。

---

## 8. Confirmed Primary Key Design

### 8.1 Key Attribute Names

TableのPrimary Keyは以下とする。

| Attribute | DynamoDB Type | Role |
|---|---|---|
| `pk` | String | Partition Key |
| `sk` | String | Sort Key |

Key Attribute名はInfrastructure固有とし、Application / DomainまたはAPIへ公開しない。

### 8.2 Item Key Patterns

| Item Type | `pk` | `sk` |
|---|---|---|
| Conversation | `CONVERSATION#PRIMARY` | `METADATA` |
| Message | `CONVERSATION#PRIMARY` | `MESSAGE#<20-digit-sequence>` |
| Idempotency | `CONVERSATION#PRIMARY` | `IDEMPOTENCY#<UUID>` |

Example:

```text
pk = CONVERSATION#PRIMARY
sk = MESSAGE#00000000000000000001
```

### 8.3 Single Conversation Partition

Phase 1の全Conversation Item、Message ItemおよびIdempotency Itemには固定Partition Key `CONVERSATION#PRIMARY`を使用する。

この固定Keyは単一ConversationというPhase 1 ScopeをPersistence上で保証するInfrastructure Detailであり、APIへ公開するConversation IDではない。

APIへ公開するConversation IDはConversation ItemのAttributeとして別途保持する。

`CONVERSATION#<conversationId>`のようにPublic Conversation IDをPartition Keyに埋め込まない。その方式ではConversation IDを指定しないPhase 1 APIから対象Conversationを解決するために、Pointer ItemまたはIndexが追加で必要となる。

### 8.4 Message Sort Key

Message Sort Keyには、正のMessage Sequenceを20桁の十進数でゼロ埋めして使用する。

```text
MESSAGE#00000000000000000001
MESSAGE#00000000000000000002
MESSAGE#00000000000000000003
```

Date-Time、UUIDまたはAI ProviderのIDをMessage順序の決定に使用しない。

Message Queryは`pk = CONVERSATION#PRIMARY`と`begins_with(sk, "MESSAGE#")`を使用する。

最新Messageの取得ではDynamoDB Queryを降順で実行し、API ResponseへMappingする前に取得Page内を古いMessageから新しいMessageの順へ並び替える。

### 8.5 Idempotency Sort Key

Idempotency ItemはFlutterが送信したUUID形式の`Idempotency-Key`をSort KeyのSuffixに使用する。

```text
IDEMPOTENCY#550e8400-e29b-41d4-a716-446655440000
```

`Idempotency-Key`はKeyを構築する前にAPI DesignのUUID Validationを完了していなければならない。

---

## 9. Confirmed Item Schemas

### 9.1 Common Infrastructure Attributes

すべてのItemは以下を持つ。

| Attribute | Type | Description |
|---|---|---|
| `pk` | String | Partition Key |
| `sk` | String | Sort Key |
| `itemType` | String | Infrastructure内のItem識別子 |

`itemType`は`CONVERSATION`、`MESSAGE`または`IDEMPOTENCY`とする。APIまたはDomain Modelへ公開しない。

### 9.2 Conversation Item

```json
{
  "pk": "CONVERSATION#PRIMARY",
  "sk": "METADATA",
  "itemType": "CONVERSATION",
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-08-14T15:00:00.000+09:00",
  "updatedAt": "2026-08-14T15:05:30.000+09:00",
  "lastMessageSequence": 2
}
```

| Attribute | Type | Required | Description |
|---|---|---:|---|
| `conversationId` | String | Yes | APIへ返すBackend生成UUID |
| `createdAt` | String | Yes | Conversation作成日時、JST |
| `updatedAt` | String | Yes | 最後に正常保存されたMessageの`createdAt` |
| `lastMessageSequence` | Number | Yes | 最後に採番したMessage Sequence |

Conversationは最初のUser Messageと同じTransactionで作成するため、保存済みConversation Itemの`lastMessageSequence`は1以上となる。

Processing LeaseおよびBusy LockのAttributeは、後続のConcurrency Control Sectionで定義する。

### 9.3 Message Item

```json
{
  "pk": "CONVERSATION#PRIMARY",
  "sk": "MESSAGE#00000000000000000002",
  "itemType": "MESSAGE",
  "messageId": "a690f9b2-21b7-41a8-929c-20f9f9703d6f",
  "sequence": 2,
  "role": "assistant",
  "content": "こんにちは。今日はどうしましたか？",
  "createdAt": "2026-08-14T15:00:02.123+09:00",
  "idempotencyKey": "eb8fd6ec-3365-47b2-a5c7-90c019a209d9"
}
```

| Attribute | Type | Required | Description |
|---|---|---:|---|
| `messageId` | String | Yes | APIへ返すBackend生成UUID |
| `sequence` | Number | Yes | Conversation内で一意かつ単調増加する順序 |
| `role` | String | Yes | `user`または`assistant` |
| `content` | String | Yes | Original Message Content |
| `createdAt` | String | Yes | Message作成日時、JST |
| `idempotencyKey` | String | Yes | Message送信操作のUUID |

User Messageとその応答として生成されたAssistant Messageには同じ`idempotencyKey`を保存する。

`idempotencyKey`はMessageの追跡とCompleted Resultの復元に使用するInfrastructure Attributeであり、Phase 1 APIへ公開しない。

Message ItemへConversation ID、AI Provider、AI Model、Token Usage、OpenAI IDまたはPersonal Memoryを追加しない。

### 9.4 Message Item Size Safety

DynamoDBの1 Itemあたり400 KB上限へ到達しないよう、Phase 1のMessage `content`はUTF-8で300,000 Byte以下とする。

- User MessageにはAPI Designの10,000 Unicode Code Pointおよび128 KiB HTTP Body上限も適用する
- Assistant MessageにはAPI / AI Designの50,000 Unicode Code Point上限も適用する
- `content`を保存前にUTF-8 Byte数で検証する
- 上限超過時にContentを無言で切り詰めない
- 上限超過をDynamoDB SDKへ送信してValidation Errorに依存しない

300,000 ByteはMessage Metadataおよび将来の小規模なSchema追加に対する余裕を残すPersistence Safety Limitである。APIの文字数上限とDatabaseのByte上限は目的が異なるため、両方を検証する。

Role別の保存条件は次とする。

| Role | Unicode Code Point Limit | UTF-8 Byte Limit |
|---|---:|---:|
| `user` | 10,000 | 300,000 Byte |
| `assistant` | 50,000 | 300,000 Byte |

Unicode Code Point上限またはUTF-8 Byte上限のどちらか一方でも超えたMessageを保存してはならない。Assistant上限超過時はPartial Contentを保存せず、`ai-design.md`のGeneration Failureへ収束させる。

---

## 10. Confirmed ID Generation

| ID | Generator | Format |
|---|---|---|
| Conversation ID | Backend | UUID v4 |
| Message ID | Backend | UUID v4 |
| Idempotency Key | Flutter | UUID |

Backendが生成するUUIDは、小文字のハイフン付きCanonical形式で保存する。

Conversation IDおよびMessage IDはAPI Contract上Opaque Stringとして扱い、FlutterはUUIDであることをBusiness Logicの前提にしない。

Message順序はUUIDではなく`sequence`で決定する。

---

## 11. Confirmed Date-Time Storage

ConversationおよびMessageの業務日時は、JST Offset付きISO 8601 Stringで保存する。

```text
2026-08-14T15:00:02.123+09:00
```

精度はミリ秒に固定する。UTC、Time ZoneのないLocal Date-Timeまたは異なる小数秒精度を混在させない。

Java実装はJSTへ正規化した`OffsetDateTime`相当の値を使用し、テスト可能にするため`Clock`を注入する。

DynamoDB TTLに利用する期限管理Attributeに限り、DynamoDB仕様に従ってEpoch SecondsのNumber型を使用する。TTL AttributeはAPIへ公開する業務日時として扱わない。

---

## 12. Confirmed Message Ordering

Message Sequenceは1から開始し、Messageを保存するごとにConversation Itemの`lastMessageSequence + 1`を採番する。

Conversation Itemの`lastMessageSequence`をExpected ValueとするConditionを使用し、Sequenceの重複更新を防止する。

Sequenceは以下を満たす。

- Conversation内で一意である
- 正の64-bit Integerの範囲で単調増加する
- Sort Keyでは20桁のゼロ埋めStringとする
- 欠番を許容する
- 連続していることをBusiness RuleまたはPaginationの前提にしない
- User / Assistantの奇数・偶数を前提にしない
- User / Assistantが必ず交互であることを前提にしない

`createdAt`、UUIDまたはDynamoDBのPhysical OrderをMessage順序のSource of Truthにしない。

---

## 13. Confirmed Persistence Flow

### 13.1 Strategy

Phase 1ではUser MessageをAI Provider呼び出し前に永続化する。

User MessageとAssistant Messageを1つのTransactionで同時保存する方式は採用しない。AI Provider呼び出しはDynamoDB Transaction内で実行できず、長時間の外部API呼び出しをTransaction Boundaryとして扱わないためである。

### 13.2 Normal Flow

```text
1. Request Validation
2. Idempotency / Busy状態確認
3. Start Transaction
   ├── Conversation作成または更新
   ├── User Message保存
   ├── Idempotency = PROCESSING
   └── Busy Lock取得
4. stream.started
5. Conversation History取得 / Context構築
6. AI Provider呼び出し
7. assistant.delta送信
8. Completion Transaction
   ├── Assistant Message保存
   ├── Conversation更新
   ├── Idempotency = COMPLETED
   └── Busy Lock解除
9. assistant.completed
```

`assistant.completed`はCompletion Transactionが成功し、User MessageおよびAssistant MessageがConversation History取得APIから取得可能となった後だけ送信する。

### 13.3 Start Transaction

Conversationが存在する場合、Start Transactionは以下をAtomicに実行する。

- Conversation Itemの`lastMessageSequence`および`updatedAt`をUser Messageの値へ更新する
- User Message Itemを作成する
- Idempotency Itemを`PROCESSING`で作成する
- Conversation Busy Lockを取得する

Conversationが存在しない場合、Start Transactionは以下をAtomicに実行する。

- `attribute_not_exists(pk)`のConditionでConversation Itemを作成する
- Sequence `1`のUser Message Itemを作成する
- Idempotency Itemを`PROCESSING`で作成する
- Conversation ItemへBusy Lockを設定する

並行する初回Requestの両方がConversation作成に成功してはならない。Condition Failureが発生したRequestは現在状態を再取得し、IdempotencyまたはBusy Ruleに従って結果を返す。

### 13.4 Completion Transaction

Completion Transactionは以下をAtomicに実行する。

- Assistant Message Itemを作成する
- Conversation Itemの`lastMessageSequence`および`updatedAt`をAssistant Messageの値へ更新する
- Idempotency Stateを`PROCESSING`から`COMPLETED`へ更新する
- 対象`Idempotency-Key`が所有するBusy Lockを解除する

Assistant Message ID、Sequence、Contentおよび`createdAt`はCompletion Transactionの最初の実行前に1度だけ確定し、Transaction Retryで同じ値を使用する。

---

## 14. Confirmed Failure Consistency

### 14.1 Start Transaction Failure

Start Transactionが失敗した場合は以下とする。

- User Messageを保存しない
- Conversationを部分的に更新しない
- AI Providerを呼び出さない
- SSE Responseを開始しない
- API DesignのHTTP Error Contractに従う

### 14.2 AI Provider Failure

AI Response生成が失敗した場合、Failure Transactionで以下をAtomicに実行する。

- Idempotency Stateを`FAILED`へ更新する
- Failure Codeを保存する
- Busy Lockを解除する

その上で`stream.failed`を送信する。

User MessageはConversation Historyに残し、削除またはRollbackしない。Conversation `updatedAt`はUser Messageの`createdAt`と一致する。

### 14.3 Assistant Message Policy

- Streaming途中のPartial Assistant ContentをDynamoDBへ保存しない
- Assistant MessageはAI Response全文が完成した後だけ保存する
- AI生成失敗時はAssistant Message Itemを作成しない
- User MessageとAssistant Messageが必ず交互であることを前提にしない

### 14.4 Completion Transaction Failure

Completion Transactionが失敗した場合、一部の変更だけをCommitしてはならない。

- `assistant.completed`を送信しない
- Partial Assistant Messageを保存しない
- 可能な場合はFailure Transactionで`FAILED`とBusy Lock解除を保存する
- Applicationが制御可能な場合は`stream.failed`を送信する
- Failure状態を保存できない場合は`PROCESSING`とBusy LockをLease回復対象とする

### 14.5 Unknown Transaction Result

Network Timeout等によりCompletion Transactionの結果が不明な場合、同じAssistant Messageの値を維持したままIdempotency Itemを再取得する。

| Retrieved State | Result |
|---|---|
| `COMPLETED` | Completion Transaction成功と判定する |
| `PROCESSING` | 同じ値でCompletion TransactionをRetry可能 |
| `FAILED` | Failureとして扱う |
| Item取得不能 | 結果不明とし、Terminal Successを返さない |

異なるAssistant Message ID、Sequence、ContentまたはDate-Timeを生成し直してRetryしてはならない。

### 14.6 No Compensating Message Delete

Failure回復を理由として、正常保存済みUser MessageまたはAssistant Messageを補償的に削除してはならない。

---

## 15. Confirmed Idempotency Design

### 15.1 Processing Item Schema

```json
{
  "pk": "CONVERSATION#PRIMARY",
  "sk": "IDEMPOTENCY#550e8400-e29b-41d4-a716-446655440000",
  "itemType": "IDEMPOTENCY",
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "requestContentHash": "<SHA-256 hex>",
  "state": "PROCESSING",
  "userMessageId": "1adc7e8c-9033-4702-9df7-0c40beebf21b",
  "userMessageSequence": 1,
  "createdAt": "2026-08-14T15:00:00.000+09:00",
  "updatedAt": "2026-08-14T15:00:00.000+09:00",
  "leaseExpiresAtEpochSeconds": 1786687500
}
```

### 15.2 Common Attributes

| Attribute | Type | Required | Description |
|---|---|---:|---|
| `idempotencyKey` | String | Yes | Flutterが生成したUUID |
| `requestContentHash` | String | Yes | Original ContentのSHA-256 Hash |
| `state` | String | Yes | `PROCESSING`、`COMPLETED`または`FAILED` |
| `userMessageId` | String | Yes | Start Transactionで保存したUser Message ID |
| `userMessageSequence` | Number | Yes | User MessageのSequence |
| `createdAt` | String | Yes | Idempotency Item作成日時、JST |
| `updatedAt` | String | Yes | Stateの最終更新日時、JST |

### 15.3 Request Content Hash

`requestContentHash`は以下で生成する。

```text
lowercaseHex(SHA-256(UTF-8(original content)))
```

Hash対象はJSON Decode後のOriginal `content` Stringとする。Trim、Unicode Normalization、Line Ending変換またはCase Conversionを行ってはならない。

同じ`Idempotency-Key`の非期限切れItemと`requestContentHash`が異なる場合、`IDEMPOTENCY_KEY_CONFLICT`とする。

Phase 1のRequest Bodyは`content`のみのため本方式を使用する。将来Request Fieldを追加する場合、Hash対象とCanonicalization RuleをAPI Designと同時に更新する。

### 15.4 State-specific Attributes

| State | Required Attributes |
|---|---|
| `PROCESSING` | `leaseExpiresAtEpochSeconds` |
| `COMPLETED` | `assistantMessageId`, `assistantMessageSequence`, `completedAt` |
| `FAILED` | `failureCode`, `failedAt`, `expiresAtEpochSeconds` |

`COMPLETED`または`FAILED`へ遷移する際、`leaseExpiresAtEpochSeconds`を削除する。

Completed Replayでは`userMessageSequence`および`assistantMessageSequence`からMessage Sort Keyを再構築し、対応するMessage Itemを直接取得する。

`requestId`はIdempotency Itemへ保存しない。ReplayではAPI Designに従い、現在のHTTP Request用に新しい`requestId`を生成する。

### 15.5 State Transition

```text
Not Found
    │
    ▼
PROCESSING
    ├──→ COMPLETED
    └──→ FAILED
```

- `COMPLETED`および`FAILED`はTerminal Stateとする
- Terminal Stateから別Stateへ変更しない
- 期限切れ`PROCESSING`は`FAILED`へのみ遷移する
- 期限切れ`PROCESSING`からAI Providerを再呼び出しない
- 期限切れ時のFailure Codeは`REQUEST_INTERRUPTED`とする

---

## 16. Confirmed Processing Lease and Busy Lock

### 16.1 Conversation Busy Attributes

Message送信中のConversation Itemは以下を持つ。

```json
{
  "activeIdempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "busyLeaseExpiresAtEpochSeconds": 1786687500
}
```

| Attribute | Type | Required while busy | Description |
|---|---|---:|---|
| `activeIdempotencyKey` | String | Yes | Busy Lock所有者 |
| `busyLeaseExpiresAtEpochSeconds` | Number | Yes | Busy LockのLease期限 |

Idle時は両AttributeをConversation Itemから削除する。`null`またはEmpty Stringで保存しない。

### 16.2 Acquisition and Release

Start Transactionで以下をAtomicに実行する。

- Idempotency Itemを`PROCESSING`で作成する
- Conversation Itemの`activeIdempotencyKey`を対象Keyに設定する
- IdempotencyとConversationのLease期限を同じEpoch Secondsに設定する
- User Messageを保存する

CompletionまたはFailure Transactionでは、`activeIdempotencyKey`が対象Keyと一致するConditionを使用してBusy Lockを解除する。他Requestが所有するLockを解除してはならない。

### 16.3 Observable Behavior

| Current State | Behavior |
|---|---|
| Same Key / Active `PROCESSING` | `409 REQUEST_IN_PROGRESS`および`Retry-After: 2` |
| Different Key / Active Busy Lock | `409 CONVERSATION_BUSY` |
| Same Key / `COMPLETED` | 保存済みResultをReplay |
| Same Key / `FAILED` | 保存済みFailureをReplay |
| Same Key / Different Content | `409 IDEMPOTENCY_KEY_CONFLICT` |

Idempotency StateおよびBusy Lockの判定にはStrongly Consistent Readを使用する。

### 16.4 Lease Configuration

- Processing Lease Defaultは300秒とする
- Busy Lock LeaseはProcessing Leaseと同じ期限とする
- Processing LeaseはSSE Timeoutより最低60秒長くする
- Default SSE Timeout 180秒に対し、Default Processing Leaseは300秒とする
- Phase 1でLease Renewalを実装しない
- Backend起動時にConfigurationの大小関係を検証する
- `processingLeaseSeconds < sseTimeoutSeconds + 60`の場合はBackend起動を失敗させる

---

## 17. Confirmed Recovery and Retention

### 17.1 Lazy Recovery

Phase 1ではBackground Job、Scheduled SweeperまたはMessage Queueを導入しない。

期限切れProcessing / Busy Lockは、次のMessage Send Requestが対象状態を検出したときにLazy Recoveryする。

Recovery Transactionは以下をAtomicに実行する。

- 旧Idempotency Itemが`PROCESSING`でありLease期限切れであることをConditionで確認する
- 旧Idempotency Itemを`FAILED`へ更新する
- Failure Codeを`REQUEST_INTERRUPTED`とする
- Conversation Itemの`activeIdempotencyKey`が旧Keyと一致する場合だけBusy Lockを解除する

同じ期限切れKeyによる再確認Requestの場合、Recovery後に`REQUEST_INTERRUPTED`をReplayする。新しい`PROCESSING`としてAI Providerを呼び出してはならない。

異なる新しいKeyのRequestが期限切れBusy Lockを検出した場合、Recovery完了後に通常のStart Transactionを試行する。Condition Failure時はStateを再取得して判定する。

### 17.2 Retention

| State | Retention Rule |
|---|---|
| `COMPLETED` | 対応するConversation Historyが存在する間保持 |
| `FAILED` | Terminal Failureから最低24時間保持 |
| `PROCESSING` | TTL削除せずLease Recovery対象とする |

`FAILED`のみ`expiresAtEpochSeconds`をDynamoDB TTL Attributeとして設定する。Retention Configurationを24時間未満にしてはならない。

DynamoDB TTLの物理削除タイミングに依存せず、Backendは`expiresAtEpochSeconds`を読み取ってLogical Expirationを判定する。

保証期間終了後の`FAILED` ItemがTTLで未削除の場合、期限条件を付けて削除または新しい`PROCESSING` Itemへ置換可能とする。この場合、同じKeyは新規Requestとして処理される可能性がある。

Flutterは保証期間終了後のFailed Keyを自動または結果確認目的で再送しない。

---

## 18. Confirmed Cursor Pagination

### 18.1 Cursor Format

API Cursorは以下の署名付きOpaque Stringとする。

```text
Base64Url(payloadJsonBytes) + "." + Base64Url(HMAC-SHA256(payloadJsonBytes))
```

Payload:

```json
{
  "v": 1,
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "exclusiveStartSequence": 51
}
```

| Field | Type | Description |
|---|---|---|
| `v` | Number | Cursor Format Version、Phase 1は`1` |
| `conversationId` | String | Cursorを生成したConversation ID |
| `exclusiveStartSequence` | Number | 次のPageで除外開始位置とするMessage Sequence |

CursorにDynamoDB `LastEvaluatedKey`、AttributeValueまたはTable Nameを直接格納しない。

### 18.2 Signing Key

Cursor Signing Keyは最低32 ByteのRandom Valueとし、Base64形式のEnvironment Variable `ALICE_CURSOR_SIGNING_KEY`から取得する。

- Source CodeへHard Codingしない
- Git RepositoryへCommitしない
- Logへ出力しない
- Backend再起動後も同じKeyを使用する
- Key変更後の旧Cursorは`INVALID_CURSOR`とする

Cursor自体に有効期限は設けない。Phase 1でKey Rotation用の複数Key保持は実装しない。

### 18.3 Validation

Backendは以下の順でCursorを検証する。

1. Cursor全体が1,024 ASCII Character以内である
2. `payload.signature`の2 Segmentである
3. Base64Url Decode可能である
4. HMAC-SHA256 Signatureが一致する
5. PayloadがUTF-8 JSON Objectである
6. Required Fieldだけを持ち、未知Fieldがない
7. `v = 1`である
8. `conversationId`が現在のConversationと一致する
9. `exclusiveStartSequence`が正の64-bit Integerである

HMACの比較にはConstant-time Comparisonを使用する。いずれかの検証に失敗した場合は`400 INVALID_CURSOR`とし、詳細な検証失敗理由をClientへ返さない。

### 18.4 DynamoDB Query Mapping

Message Queryは以下を使用する。

```text
pk = CONVERSATION#PRIMARY
begins_with(sk, "MESSAGE#")
ScanIndexForward = false
ConsistentRead = true
```

Cursorの`exclusiveStartSequence`から以下をInfrastructure内で再構築し、DynamoDB `ExclusiveStartKey`として使用する。

```text
pk = CONVERSATION#PRIMARY
sk = MESSAGE#<20-digit-exclusiveStartSequence>
```

Flutterへ返すMessageは、取得Page内でSequenceの昇順へ並び替える。

### 18.5 Logical Page Size

DynamoDB Queryは1回のResponse Sizeに上限があるため、1回の低レベルQueryだけでAPIの`limit`を満たすことを前提にしない。

Repositoryは`limit + 1`件のMessageを収集するか、DynamoDBの最終Pageへ到達するまで、`LastEvaluatedKey`を利用して内部Queryを継続する。

```text
requested limit = 50
internal target = 51
```

| Internal Result | API Result |
|---|---|
| `limit + 1`件取得 | 先頭`limit`件を返し、`hasMore = true` |
| `limit`件以下で最終Page到達 | 取得分を返し、`hasMore = false` |

`hasMore = true`の場合、APIへ実際に返すPageの最も古いMessage Sequenceを`exclusiveStartSequence`としてCursorを生成する。確認用に追加取得した1件は現在Pageへ含めず、次Pageで再取得する。

---

## 19. Confirmed Read Consistency

### 19.1 Strongly Consistent Reads

Phase 1では以下のすべてにStrongly Consistent Readを使用する。

- Conversation基本情報取得
- Conversation History取得
- AI Context用Conversation History取得
- Idempotency Item取得
- Busy Lock判定
- Completed Replay用User / Assistant Message取得
- Transaction結果不明時のState再確認

DynamoDB `GetItem`または`Query`で`ConsistentRead = true`を指定する。

### 19.2 Reason

API Designの`assistant.completed`は、User MessageおよびAssistant MessageがPersistenceに正常保存され、Conversation History取得APIから取得可能になったことを意味する。

Eventually Consistent Readでは直前の正常Writeが一時的に反映されない可能性があるため、このAPI ContractとIdempotencyの即時判定に使用しない。

### 19.3 Trade-off

Strongly Consistent ReadはEventually Consistent ReadよりRead Capacity消費が大きい。

Phase 1はSingle User / Single Conversationであり、読み取りCostよりも、直前のMessage、Idempotency StateおよびBusy Lockを正確に取得できることを優先する。

Phase 1でGSIを使用しない方針は、Strongly Consistent Readの適用と整合する。

---

## 20. Confirmed DynamoDB Development Environment

### 20.1 Standard Local Environment

Phase 1の日常的なLocal DevelopmentおよびIntegration TestではDynamoDB Localを使用する。

| Item | Decision |
|---|---|
| Runtime | Docker Compose |
| Image | `amazon/dynamodb-local:3.3.0` |
| Version Policy | Exact Version Tagを固定し、`latest`を使用しない |
| Host Bind | `127.0.0.1:8000` |
| DynamoDB Mode | `-sharedDb` |
| Data | Persistent Docker Volume |
| Telemetry | `-disableTelemetry` |

Spring BootはMac上でMaven Wrapperから起動し、DynamoDB LocalだけをContainerとして起動する。

```text
Developer Machine
├── Spring Boot
└── Docker Compose
    └── DynamoDB Local 3.3.0
```

DynamoDB LocalをPublic Network InterfaceへBindしてはならない。

### 20.2 Development Data

通常のLocal DevelopmentではPersistent Volumeを使用し、Container停止・再起動後もConversation Historyを維持する。

Integration Testは通常開発用Tableを共有せず、Test専用Tableを作成・破棄する。

### 20.3 Table Creation

Spring Bootの通常起動時にTableを自動作成または更新しない。

- Local Setup用の明示的なCommand / ScriptでTableを作成する
- Tableが存在し、Key SchemaとTTL Attributeが正しい場合は再作成しない
- Tableが存在しない、またはSchemaが異なる場合はBackend起動を明確なConfiguration Errorで失敗させる
- AWS EnvironmentでApplication RuntimeにTable作成権限を要求しない

### 20.4 Local and AWS Usage

| Purpose | Environment |
|---|---|
| Daily Local Development | DynamoDB Local |
| Integration Test | DynamoDB LocalのTest専用Table |
| Transaction Conflict Unit Test | Fake / Mock |
| AWS Compatibility Smoke Test | AWS DynamoDB、必要な場合のみ |
| Future Cloud Runtime | AWS DynamoDB |

DynamoDB LocalはAWS DynamoDBと完全に同一ではない。特にTransaction Conflict等のLocalで再現できないBehaviorはFake / Mockでテストする。

---

## 21. Confirmed Capacity and Configuration

### 21.1 AWS Capacity Mode

AWS DynamoDB Tableを作成する場合はOn-demand Capacity Modeを使用する。

```text
BillingMode = PAY_PER_REQUEST
```

Phase 1でProvisioned Capacity、Auto ScalingまたはReserved Capacityを設定しない。

### 21.2 Local Configuration

| Environment Variable | Local Value | Description |
|---|---|---|
| `ALICE_DYNAMODB_TABLE_NAME` | `project-alice-conversation-local` | Conversation Table |
| `ALICE_DYNAMODB_ENDPOINT` | `http://localhost:8000` | DynamoDB Local Endpoint |
| `AWS_REGION` | `ap-northeast-1` | Local SDK Configuration |
| `AWS_ACCESS_KEY_ID` | `local` | Local Dummy Credential |
| `AWS_SECRET_ACCESS_KEY` | `local` | Local Dummy Credential |
| `ALICE_CURSOR_SIGNING_KEY` | Base64-encoded 32+ Byte Secret | Cursor HMAC Key |
| `ALICE_PROCESSING_LEASE_SECONDS` | `300` | Processing / Busy Lease |
| `ALICE_FAILED_RETENTION_SECONDS` | `86400` | Failed Idempotency Retention |

Local Dummy CredentialはAWSの実Credentialではなく、DynamoDB LocalへのSDK Request構築のためだけに使用する。

Local Profileでは`ALICE_DYNAMODB_ENDPOINT`を必須とし、Loopback Address以外のEndpointを拒否する。これによりLocal Profileから誤ってAWS DynamoDBへ書き込むことを防止する。

Localの`AWS_REGION = ap-northeast-1`はSDK構築用の値であり、将来のCloud用AWS Regionを確定するDecisionではない。

### 21.3 TTL

TableのDynamoDB TTL Attribute名は`expiresAtEpochSeconds`とする。

TTLの物理削除タイミングをIntegration Testの完了条件にしない。Logical ExpirationはApplicationが`Clock`とAttribute値で検証する。

---

## 22. Documentation Boundary

Database ContractとLocal Setup Procedureは分離する。

| Document | Responsibility |
|---|---|
| `database-design.md` | Table、Key、Item、Transaction、Consistency、VersionおよびConfiguration Contract |
| `dynamodb-local-setup.md` | Docker導入前提、起動・停止、Table Setup、確認、Data Reset、Troubleshooting |

AI Coding AssistantにPersistence実装を依頼する場合は`database-design.md`を必須Inputとする。

Local Environment構築または環境障害対応を依頼する場合だけ、`dynamodb-local-setup.md`を追加Inputとする。

---

## 23. Confirmed Logging and Security

### 23.1 Purpose

Database Logは障害原因と処理結果を追跡するために使用する。

会話内容を再現するための保存先としてLogを使用してはならない。Conversation Historyの正式な保存先はDynamoDBであり、LogはObservability情報だけを保持する。

### 23.2 Structured Logging

Database AdapterのLogは、検索しやすいKey-Value形式のStructured Logとする。

標準Field:

| Field | Description |
|---|---|
| `event` | Stable Event Name |
| `requestId` | 1回のAPI Requestを追跡するID |
| `operation` | `GetItem`、`Query`、`TransactWriteItems`等の論理Operation |
| `outcome` | `SUCCESS`、`BUSY`、`CONFLICT`、`FAILED` |
| `durationMs` | Operation所要時間 |
| `itemCount` | 読み書きしたItem数。必要な場合のみ |
| `retryCount` | Retry回数。Retryが発生した場合のみ |
| `conversationId` | 障害調査に必要な場合だけ記録するOpaque UUID |
| `messageSequence` | 対象Messageを特定する必要がある場合だけ記録する |
| `errorCategory` | ApplicationへMappingした安定したError分類 |
| `exceptionType` | Infrastructure ExceptionのClass名。失敗時のみ |
| `awsRequestId` | AWS SDKが返したRequest ID。存在する場合のみ |

`requestId`はRequest追跡用であり、Idempotency Keyとして使用またはDynamoDBへ永続化しない。

推奨Event:

| Level | Event | Usage |
|---|---|---|
| `INFO` | `database.schema.validated` | 起動時Schema検証成功 |
| `INFO` | `conversation.persistence.completed` | Start / Completion / Failure Transaction成功 |
| `INFO` | `conversation.recovery.completed` | Lazy Recovery成功 |
| `WARN` | `conversation.persistence.conflict` | Conditional Check、BusyまたはState Conflict |
| `WARN` | `conversation.lease.expired` | 期限切れLeaseを検出 |
| `ERROR` | `database.operation.failed` | DynamoDB Operation失敗 |
| `ERROR` | `database.transaction.outcome.unknown` | Transaction結果を確定できない |

通常成功した低Level Operationをすべて`INFO`で出力しない。必要な場合だけ`DEBUG`を使用し、Conversation Contentは`DEBUG`でも出力しない。

### 23.3 Prohibited Log Data

以下をLogへ出力してはならない。

- User Message Content
- Assistant Message Content
- AI Request / Response全文
- DynamoDB Item全文
- OpenAI API Key
- AWS Access Key / Secret Access Key / Session Token
- Cursor Signing Key
- Signed Cursor全文
- Credentialを含むEnvironment Variable
- AWS SDK Request / Response Object全文
- Exception Messageに含まれる可能性があるContentまたはCredential

AWS SDK Errorは、Error Code、Cancellation Reason Code、HTTP Status、Request IDおよびException Typeを必要な範囲で抽出して記録する。SDK Objectの`toString()`をそのままLogへ出力しない。

### 23.4 Exception Boundary

DynamoDB固有ExceptionはInfrastructure内で分類し、Applicationが扱えるErrorへ変換する。

```text
AWS SDK Exception
        ↓
Infrastructure Error Classification
        ↓
Application Error
        ↓
Presentation Error Mapping
```

`ConditionalCheckFailedException`や`TransactionCanceledException`を一律にUnexpected Errorとしない。Cancellation Reasonを確認し、Busy、Idempotency Conflict、State ConflictまたはInfrastructure Failureへ分類する。

OpenAI、AWS SDKまたはDynamoDB固有ExceptionをControllerまで伝播させない。

### 23.5 Local Credential and Endpoint Safety

Local Profileでは以下を同時に必須とする。

- `ALICE_DYNAMODB_ENDPOINT`がLoopback Addressを指す
- `AWS_ACCESS_KEY_ID=local`
- `AWS_SECRET_ACCESS_KEY=local`
- Regionが設定されている

Local ProfileでEndpointが未指定、またはLoopback以外を指す場合は起動を失敗させる。

実AWS CredentialをCompose File、Source Code、Test DataまたはGit Repositoryへ保存しない。

### 23.6 AWS Runtime Permission Boundary

将来AWS DynamoDBへ接続する場合、Backend Runtimeには起動時検証とAccess Patternに必要なOperationだけを許可する。

Phase 1 Access Patternから必要になり得るOperation:

- `dynamodb:DescribeTable`
- `dynamodb:DescribeTimeToLive`
- `dynamodb:GetItem`
- `dynamodb:Query`
- `dynamodb:PutItem`
- `dynamodb:UpdateItem`
- `dynamodb:DeleteItem`
- `dynamodb:TransactWriteItems`

以下のTable管理権限をBackend Runtimeへ付与しない。

- `dynamodb:CreateTable`
- `dynamodb:UpdateTable`
- `dynamodb:DeleteTable`
- `dynamodb:UpdateTimeToLive`

Table管理はSetup / Infrastructure用Identityへ分離する。ResourceはProject Aliceの対象Table ARNへ限定し、`*`を使用した全Table権限を標準としない。

AWS接続時は長期間固定されたIAM User Credentialより、実行環境へ付与されたIAM Role等のTemporary Credentialを優先する。具体的なCloud IdentityはCloud Deployment設計時に決定する。

### 23.7 Expression Safety

DynamoDB ExpressionへUser Inputを直接文字列連結しない。

- Attribute Nameは実装側で定義した固定値を使用する
- Attribute ValueはExpression Attribute ValuesへBindする
- Table NameとEndpointは検証済みConfigurationから取得する
- API Cursorから復元したKeyは署名、VersionおよびConversation Scopeを検証してから使用する

---

## 24. Confirmed Database Test Requirements

### 24.1 Test Layers

Database関連Testを以下の3層へ分ける。

| Layer | Environment | Purpose |
|---|---|---|
| Unit Test | JVM内 | Key生成、Mapping、Hash、Cursor、時刻、State Transition、Error分類 |
| Integration Test | DynamoDB Local 3.3.0 | AWS SDK、Table Schema、Query、Condition、Transaction、Pagination |
| AWS Compatibility Smoke Test | AWS DynamoDB。必要時のみ | IAM、AWS固有Behavior、Localとの差分 |

DynamoDB LocalだけですべてのDynamoDB Behaviorを保証しない。

### 24.2 Unit Test Requirements

最低限以下をUnit Testする。

- PK / SK生成Rule
- 20桁ゼロ埋めMessage Sequence
- DynamoDB ItemとDomain / Application ModelのMapping
- Message ContentのUTF-8 Byte上限
- UUID v4 Validation
- JST Offset付きミリ秒精度Date-Time Serialization
- Original ContentのSHA-256 Hash
- Idempotency State Transition
- 5分Processing LeaseとBusy判定
- Lazy Recoveryと`REQUEST_INTERRUPTED`への変換
- TTLに依存しないLogical Expiration
- Cursor Encode / Decode / HMAC検証
- Cursor改ざん、Version不一致、Conversation Scope不一致の拒否
- DynamoDB ErrorからApplication ErrorへのMapping
- Read Requestで`ConsistentRead = true`が設定されること
- Logへ禁止Dataが出力されないこと

時刻依存TestはSystem Clockを直接使用せず、固定可能な`Clock`を注入する。

### 24.3 Integration Test Environment

Integration Testは以下を満たす。

- `amazon/dynamodb-local:3.3.0`を使用する
- 通常開発用Tableを共有しない
- Test Suiteごとに一意なTest Tableを使用する
- Test開始前にTableとTTLを作成する
- Test終了後にTest Tableを削除する
- 実AWS SDKと実DynamoDB Adapterを使用する
- Test Dataに実際のConversation ContentまたはCredentialを使用しない
- 実行順序へ依存しない

Containerの起動方法やTest Frameworkの最終選定は`test-design.md`で定義する。本ドキュメントは必要なBehaviorとIsolationを規定する。

### 24.4 Required Integration Scenarios

| Scenario | Expected Result |
|---|---|
| Table Schema Validation | `pk` / `sk`、Indexなし、TTL Attributeが設計どおり |
| Message Size Boundary | 300,000 Byteを受理し、超過をSDK呼び出し前に拒否する |
| Role-specific Content Boundary | User 10,000 / Assistant 50,000 Unicode Code Pointを境界値Testする |
| Conversation Metadata Read | Strongly Consistent `GetItem`で取得できる |
| Message History Read | Strongly Consistent `Query`でSequence順に取得できる |
| Start Transaction | User Message、Idempotency、Busy StateがAtomicに保存される |
| Duplicate Completed Request | 既存Responseを返し、Messageを追加しない |
| Same Key / Different Content | Idempotency Conflictとなる |
| Active Lease | BusyとなりAI再実行へ進まない |
| Expired Lease | Lazy RecoveryでStateが収束する |
| Completion Transaction | Assistant MessageとCompleted StateがAtomicに保存される |
| Failure Transaction | User Messageを保持し、Failed Stateへ更新される |
| Conditional Conflict | Partial Updateを残さない |
| Pagination | `limit + 1`とCursorで重複・欠落なく取得できる |
| Invalid Cursor | DynamoDBへQueryする前に拒否される |
| Startup Validation Failure | Table不存在またはSchema不一致でBackend起動が失敗する |

### 24.5 Failure Simulation

DynamoDB Localで正確に再現できない以下はFake / Mockで検証する。

- Transaction Conflict
- Throttling
- Network Timeout
- AWS Permission Denied
- AWS SDKが返すUnknown Transaction Result
- SDK Retry後も結果を確定できないCase

Fake / MockはDynamoDB Adapterの正常系Integration Testを置き換えるものではなく、Localで再現できないFailure Pathを補うために使用する。

### 24.6 TTL Test Rule

DynamoDB TTLの物理削除完了を待つTestを作成しない。

TTLは期限到達後すぐにItemを削除する保証ではないため、Testでは以下を分ける。

- TTL Attribute値が正しいEpoch Secondsで保存されること
- Applicationが固定`Clock`を用いて期限切れと判定すること
- 期限切れItemをBusiness Logic上で再利用しないこと

### 24.7 Test Success Criteria

Database Implementationは以下をすべて満たした場合に完了とする。

- Unit Testが成功する
- DynamoDB Local Integration Testが成功する
- Test間でDataが混在しない
- Applicationの通常経路で`Scan`が呼ばれない
- Strongly Consistent Read設定が検証される
- Failure時に設計外のPartial Stateが残らない
- 禁止DataがLogへ出力されない

---

## 25. Detailed Design Ownership

| Topic | Source of Truth |
|---|---|
| DynamoDB固有Log項目・禁止Data | 本ドキュメント |
| Application全体のLogging Policy | `security-design.md` |
| Secret Management全体 | `security-design.md` |
| Database Test Scenario | 本ドキュメント |
| Test Framework・実行分類・CI | `test-design.md` |

`security-design.md`または`test-design.md`を詳細化する際は、本ドキュメントのDatabase Contractと矛盾させない。

---

## 26. Requirements Traceability

| Requirement ID | Database Design / Verification |
|---|---|
| `P1-FR-002` | 固定Partitionによる単一Conversation Model |
| `P1-FR-006` | User / Assistant Message ItemとTransaction Boundary |
| `P1-FR-007` | Sequence順Query、Cursor Pagination、Strongly Consistent Read |
| `P1-FR-008`, `NFR-006` | Idempotency Item、Busy Lock、Lease、Terminal Result Replay、Unknown Result再確認 |
| `NFR-002` | DynamoDB ItemをDomain / Provider Modelから分離するRepository Adapter |
| `NFR-003` | Loopback限定DynamoDB Local、Credential / Content非Logging、Least Privilege |
| `NFR-004`, `NFR-005` | Phase 1 Single Table最小構成、Personal Memory Item非実装 |
| `NFR-007` | Repository Contract Test、DynamoDB Local Integration Test、Clock / ID制御 |
| `NFR-008` | 非機密Structured LoggingとFailure Category |
| `NFR-009` | DynamoDB Local `3.3.0`固定、Docker Compose、再現可能なTable Setup |

---

## 27. AI Coding Assistant Rules

AI Coding Assistantは以下を独自判断で変更してはならない。

- DynamoDBを別のPersistence Technologyへ変更する
- Phase 1でTableを複数に分割する
- GSI / LSIを追加する
- Applicationの通常処理で`Scan`を使用する
- DynamoDB ModelをDomain Modelとして使用する
- Personal Memory Itemを追加する
- Table NameをSource CodeへHard Codingする
- Public Conversation IDをPartition Keyとして使用する
- MessageのSort KeyへDate-Time、UUIDまたはProvider固有IDを使用する
- Conversation IDまたはMessage IDにUUID v4以外の形式を導入する
- ConversationまたはMessageの業務日時をUTCで保存する
- Message Sequenceに連続性、奇数・偶数またはUser / Assistantの交互を仮定する
- User Message保存前にAI Providerを呼び出する
- AI Provider失敗時に保存済みUser Messageを削除する
- Partial Assistant ContentをConversation Historyとして保存する
- Completion Transaction成功前に`assistant.completed`を送信する
- `requestId`をIdempotency Itemへ永続化する
- 期限切れ`PROCESSING`からAI Providerを再呼び出しする
- 対象Keyが一致しないBusy Lockを解除する
- Background JobまたはScheduled SweeperをPhase 1へ追加する
- DynamoDB TTLの物理削除タイミングだけでLogical Expirationを判定する
- DynamoDB `LastEvaluatedKey`またはAttributeValueをAPI Cursorへ直接公開する
- CursorのHMAC検証前にPayloadを信頼して使用する
- 1回のDynamoDB Queryが必ずAPI `limit`件を返すと仮定する
- Phase 1のConversation、Message、IdempotencyまたはBusy Lock読み取りにEventually Consistent Readを使用する
- DynamoDB Local Imageに`latest`を使用する
- DynamoDB LocalをLoopback以外のInterfaceへBindする
- Local ProfileでDynamoDB Endpoint未指定のままBackendを起動する
- Spring Bootの通常起動時にDynamoDB Tableを作成または変更する
- AWS DynamoDBでProvisioned Capacityを独自に導入する
- 300,000 UTF-8 Byteを超えるMessage ContentをDynamoDBへ保存する
- Message ContentをDatabase上限へ合わせて無言で切り詰める
- User Message、Assistant Message、DynamoDB Item全文またはCredentialをLogへ出力する
- AWS SDK Request / Response ObjectをそのままLogへ出力する
- Local Dummy CredentialをAWS Environmentで使用する
- Backend RuntimeへTable作成・変更・削除権限を付与する
- DynamoDB ExpressionへUser Inputを直接文字列連結する
- Integration Testで通常開発用Tableを共有する
- DynamoDB TTLの物理削除完了を待つTestを作成する
- DynamoDB LocalだけでAWS固有Failure Behaviorを保証したと判断する
- 未確定のKey、TransactionまたはFailure Ruleを推測して実装する

本ドキュメントと矛盾する実装が必要な場合、実装前にDesign DocumentまたはADRを更新する。

---

## 28. Phase 1 Current Status

Phase 1 Database Design Review Result:

```text
APPROVED
```

Reviewで検出した誤記、TTL設定確認権限およびMessage Item Size Safetyは修正済みである。修正必須の未解決事項はない。

確定済み:

- Phase 1 Access Pattern
- Single Table
- GSI / LSIなし
- Application通常処理でScan禁止
- Conversation / Message / Idempotencyを同一Tableで管理
- `pk` / `sk` Key Attribute
- 固定Partition Key `CONVERSATION#PRIMARY`
- Messageの20桁ゼロ埋めSequence Sort Key
- IdempotencyのUUID Sort Key
- Conversation Item Schema
- Message Item Schema
- Conversation / Message IDのUUID v4
- JST Offset付きミリ秒精度のDate-Time
- Message Sequenceによる一意な順序
- Message Contentの300,000 UTF-8 Byte Safety Limit
- User MessageのAI呼び出し前保存
- Start / Completion / Failure Transaction Boundary
- AI失敗時のUser Message保持
- Partial Assistant Messageの非保存
- Unknown Transaction Resultの再確認Rule
- Idempotency Item SchemaとState Transition
- Original ContentのSHA-256 HashによるConflict検出
- Processing / Busy Lockの5分Lease
- Strongly Consistent ReadによるState判定
- Lazy Recoveryと`REQUEST_INTERRUPTED`への収束
- Completed / FailedのRetention Rule
- HMAC-SHA256署名付きOpaque Cursor
- Cursorからの`ExclusiveStartKey`再構築
- Internal `limit + 1`取得による`hasMore`判定
- すべてのPhase 1 ReadへのStrongly Consistent Read適用
- DynamoDB Local 3.3.0 / Docker Compose
- Persistent Local VolumeとLoopback Bind
- 明示的Table SetupとRuntimeでの自動作成禁止
- AWS DynamoDBのOn-demand Capacity Mode
- Local ConfigurationとTTL Attribute
- Local Setup Procedureの別紙化
- Database Structured Loggingと禁止Data
- DynamoDB Exception分類Boundary
- Local Credential / Endpoint Safety
- AWS Runtime Permission Boundary
- DynamoDB Expression Safety
- Unit / Integration / AWS Compatibility Test Boundary
- Required Integration Test Scenario
- DynamoDB Localで再現できないFailureのTest方針
- TTL Test Rule

Phase 1で次に進んだ設計対象（履歴）:

```text
AI Design
```

Phase 2の現在の設計対象は、Section 33以降のPersonal Memory Persistenceである。Phase 1の`APPROVED`はPhase 2 Draftの承認を意味しない。

---

## 29. Phase 2 Allowed Device Credential Persistence Boundary

### 29.1 Status and Ownership

| Item | Value |
|---|---|
| Status | Accepted / Implemented by Section 38.5 |
| Source Decisions | API2-163〜API2-168、DB2-109〜DB2-120 |
| Owner | Phase 2 Security Infrastructure Adapter |

Allowed Device CredentialはPersonal Memory本文と分離したSecurity Dataである。Conversation Item、PersonalMemory Item、Memory Backup Archive、Search ProjectionまたはUser-visible Exportへ混在させない。

### 29.2 Logical Record

```text
AllowedDeviceCredential
├── deviceId
├── displayName
├── tokenDigest
├── digestKeyVersion
├── status
├── createdAt
├── rotatedAt
└── revokedAt
```

| Field | Rule |
|---|---|
| `deviceId` | Backend生成のOpaque UUID v4 |
| `displayName` | 管理用。Authentication根拠にしない |
| `tokenDigest` | Token原文ではなく専用KeyによるHMAC-SHA-256 |
| `digestKeyVersion` | Rotation時の安全なKey選択用。Key自体を保存しない |
| `status` | `ACTIVE`または`REVOKED` |
| Date-Time | JST Offset付き・ミリ秒精度 |

Token原文、Authorization Header、TLS Private Key、Backup Passphrase、Platform Secure Storage値またはMemory本文をRecordへ保存しない。

### 29.3 Required Access Patterns

| Access Pattern | Result |
|---|---|
| Token DigestでActive Deviceを解決 | 最大1件。重複を許可しない |
| Device IDで状態取得 | Local Administration用 |
| DeviceをRevoke | 条件付き更新で`ACTIVE → REVOKED` |
| Device TokenをRotate | 新Digest確立後に旧Digestを無効化 |

認証Requestごとに高頻度の`lastUsedAt`書込みを必須としない。必要なSecurity AuditはContent-free Eventとして別Boundaryへ記録し、Authentication Hot PathとPersonal Memory Table Capacityを不要に結合しない。

### 29.4 Consistency and Failure Rules

- 発行は新DigestのDurable保存を確認してからToken原文を一度だけ表示する。
- Revoke結果が不明な場合、成功と案内せずAuthoritative Recordを再確認する。
- Rotateは新Token有効化と旧Token無効化の順序を固定し、中間状態をTestする。
- Digest Keyを取得できない場合は全Private LAN AuthenticationをFail Closedとする。
- Revoke済みCredentialをTTLだけで自動削除しない。Section 38.5.5に従い最低30日保持してからLogical Expiration / TTLを適用できる。

Physical Schema、PK / SK、Token Locator、Active Directory、Control、Issue / Rotate / Revoke TransactionおよびDigest Key RotationはSection 38.5をAuthoritativeとする。P2-DB-AP-017、DB2-109〜DB2-120およびTest Design Sections 31 / 43へTraceし、Phase 1 Conversation TableへSecurity Itemを追加しない。

---

## 30. Phase 2 Confirmation Plan Lifetime Boundary

### 30.1 Status

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-173〜API2-184 |
| Applies To | Deletion Plan / Restore Plan Persistence Adapter |

Plan Metadataは`createdAt`、固定`reviewExpiresAt = createdAt + 4時間`、Plan Versionおよび不変Target / Action Set Versionを分離して保持する。GET、PATCH、Confirm、Pollingまたは再接続でReview期限を延長しない。

Confirmation Token原文を永続化しない。必要な場合はToken Digest、Plan ID、Plan Version、Binding Digest、`confirmationExpiresAt`および無効化状態だけを短命Security Stateとして保持する。Token期限は発行から15分以内かつReview期限までとし、Durable Execute Intent確立時に同じPlanの全Tokenを無効化する。

Restore Action Pageの固定集合と順序は不変`actionSetVersion`へBindingする。Plan VersionはPATCH / Confirm / ExecuteのConcurrency Controlへ使用し、Resolution更新だけでAction Set Versionを進めない。

Logical ExpirationはApplication読取時に必ず判定し、DynamoDB TTLの物理削除時刻へ依存しない。Review期限到達時はToken、Preview本文、Hydration CacheおよびSealed StateをCleanupし、Content-free Plan Metadata / Resultは最低24時間保持する。具体Table / Key / TransactionはPhase 2 Database詳細設計で確定する。

---

## 31. Phase 2 Restore Reservation and Cleanup Persistence Boundary

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-193〜API2-205、MD-114〜MD-122 |

Single User全体でActive Restore PlanまたはInspection Leaseを一つだけ保持する。Active Slot、Working Byte Reservation、Idempotency RecordおよびPlan確立を条件付き書込みで直列化し、並行Requestが両方成功しないようにする。

Inspection LeaseはOperation ID、Idempotency-Key、開始・期限、Reserved BytesおよびProcess Instanceだけを保持し、固定30分でLogical Expirationする。Passphrase、Archive Byte、Memory本文またはTemporary PathをDynamoDBへ保存しない。

Retained Byte実測値、Cleanup State、Manifest IDおよびContent-free ResultをPlan Metadataへ関連付ける。Quota解放はSensitive Artifact削除とPending Worker無効化を確認した条件付き遷移でだけ行う。Cleanup失敗時は内部`CLEANUP_PENDING`を保持し、Active Slot / Byteを解放しない。

Restore Plan CreateとCancelのIdempotency Recordは最低24時間保持する。同じKey・同じOperationを同じ結果へ収束させ、別Archive / Path / Planへの再利用をConflictとする。PassphraseをFingerprintへ含めない。DynamoDB TTLの物理削除をQuota解放またはCleanup完了の根拠にしない。

---

## 32. Phase 2 Memory Relation Review Persistence Boundary

| Item | Value |
|---|---|
| Status | Accepted |
| Source Decisions | API2-206〜API2-220、MD-123〜MD-133 |

`MemoryRelationReview`はPersonalMemory本体と分離した短命Operation Resourceとして保持する。Logical RecordはReview ID、Source Operation / Memory ID / Expected Version、Canonical Candidate Digest、暗号化Candidate、Relation Type、Related Memory ID / Expected Version、Allowed Resolution Plan、Mutation Preview、Deletion Guard / Reset Generation、Review Version、Status、Idempotency Binding、作成時刻および固定30分期限を持つ。

- Active ReviewはSingle User全体で最大20件、関連Memoryは一Review最大100件とし、条件付きReservationで並行超過を防ぐ。
- Register / Updateの同一Idempotency-Key Replayは同じReview IDへ収束させる。
- Resolve IntentはReview Version、Strong ETag相当、Resolution、Allowed TargetおよびResolve Idempotency-KeyへBindingし、一度だけDurable化する。
- Source / Related Version、GuardまたはRelation集合が変化した場合、旧Reviewを`INVALIDATED`へ遷移しMutationしない。
- Logical Expirationは読取・実行時に必ず判定し、DynamoDB TTLの物理削除へ依存しない。
- Terminal / Expired / Invalidated後は暗号化CandidateとPreviewを再利用不能にし、Content-free Metadata / Resultだけを最低24時間保持する。

Table、PK / SK、IndexおよびTransaction ExpressionはPhase 2 Database詳細設計で確定するが、Candidate本文、Preview本文、Relation理由、Secretまたは暗号KeyをIdempotency Record、Metricおよび通常Logへ複製しない。

---

## 33. Phase 2 Persistence Access Pattern and Table Strategy

### 33.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted |
| Scope | Table Boundary、Access Pattern、Key Namespace、Scan / Index Policy |
| Physical Item Schema | Section 34以降で設計 |
| Source | `memory-design.md`、`api-design.md` Phase 2 Reviewed Baseline |

本Sectionは、Phase 2のItem Fieldを先に決めず、必要なRead / WriteからTableとKey Boundaryを固定する。PersonalMemory、Revision、Preferences、Guard、Plan等の具体的なPK / SKとTransactionは、承認済みAccess Patternに基づいて後続Sectionで設計する。

### 33.2 Recommended Table Boundary

Phase 2では既存Conversation Tableを変更せず、専用Memory Tableを一つ追加する。

```text
Conversation Table（Phase 1、既存）
└── CONVERSATION#PRIMARY

Memory Table（Phase 2、新規）
├── Authoritative Memory / Revision / Preferences
├── Management / Search Projection
├── Guard / Reset Point
├── Usage Trace
├── Deletion / Restore / Relation Review
├── Idempotency / Durable Intent / Lease
└── Allowed Device Credential
```

| Boundary | Rule |
|---|---|
| Conversation Table | Phase 1 Schema、Indexなし、`CONVERSATION#PRIMARY`を維持 |
| Memory Table | Phase 2 Personal Memoryと関連Operation Stateを所有 |
| Cross-table Transaction | Conversation回答とMemory保存の正常Flowでは使用しない |
| Failure Isolation | Memory取得・自動保存失敗をConversation Transactionへ連鎖させない |
| Backup | Memory Table自体をUser Backup形式として公開しない |

別Table採用はApplication / DomainへTableを公開することを意味しない。`ConversationRepository`とMemory各Portは別Adapterを使用し、DynamoDB SDK、Table Name、PK / SKおよび`LastEvaluatedKey`をCoreへ漏らさない。

### 33.3 Table Configuration Baseline

| Configuration | Phase 1 | Phase 2 Proposal |
|---|---|---|
| Environment Variable | `ALICE_DYNAMODB_TABLE_NAME` | `ALICE_MEMORY_DYNAMODB_TABLE_NAME` |
| Local Table Name | `project-alice-conversation-local` | `project-alice-memory-local` |
| Primary Key | `pk` + `sk` | `pk` + `sk` |
| TTL Attribute | `expiresAtEpochSeconds` | `expiresAtEpochSeconds` |
| Capacity | On-demand | On-demand |
| Runtime Table Management | 禁止 | 禁止 |

Local / AWSとも二つのTable設定を混同しない。Memory Table未設定時にConversation Table名へFallbackせず、Phase 2 FeatureをFail Closedで起動不可またはUnavailableとする。Table作成・更新・TTL設定はRuntime IdentityではなくSetup / Infrastructure Boundaryが行う。

### 33.4 Phase 2 Access Pattern Catalog

| ID | Access Pattern | Consistency / Result Boundary |
|---|---|---|
| P2-DB-AP-001 | Memory IDからAuthoritative PersonalMemoryを取得 | Strongly Consistent Read |
| P2-DB-AP-002 | 新規MemoryをID未使用条件付きで作成 | Conditional Write、重複ID禁止 |
| P2-DB-AP-003 | Expected Version一致時だけMemory更新とRevision追加 | Atomic Transaction |
| P2-DB-AP-004 | Memory本文・Revision削除とGuard反映を実行 | ResultをSuccess / Partial / Unknownで照合 |
| P2-DB-AP-005 | All / Category / State / Capture / Sensitivity / Confirmation軸で管理一覧 | Query、安定順序、Opaque Cursor |
| P2-DB-AP-006 | MemoryPreferences Singletonを取得・条件付き更新 | Strong Read / Expected Version |
| P2-DB-AP-007 | Memory ID単位でRevisionを時系列取得 | Historical Queryだけに限定 |
| P2-DB-AP-008 | MemoryResetPointを取得・単調更新 | Strong Read / Generation条件 |
| P2-DB-AP-009 | Keyed FingerprintからRe-registration Guardを照合・更新 | DigestからDirect Get / Conditional Write |
| P2-DB-AP-010 | Assistant Message IDからMemoryUsageTraceを取得 | Content-free ID / Version Trace |
| P2-DB-AP-011 | Deletion Plan、固定Target Page、Confirmation、Execution Resultを操作 | Plan Version / Action Set / Durable Intent |
| P2-DB-AP-012 | Restore Lease、Plan、Action Page、Reservation、Resultを操作 | Single Active Slot / Quota / Cleanup State |
| P2-DB-AP-013 | Relation Review、Related Page、Resolve Intent、Resultを操作 | 30分期限 / Expected Version / Idempotency |
| P2-DB-AP-014 | Mutation OperationのIdempotency / Unknown Resultを照合 | Same Key + Same Digestへ収束 |
| P2-DB-AP-015 | Export対象MemoryとPreferencesの安定Snapshotを構築 | Source Version再確認 / Partial Archive禁止 |
| P2-DB-AP-016 | Management / Answer / Relation用Search Projectionを更新・取得・Repair | Candidate ID / Versionのみを返しAuthoritative再取得 |
| P2-DB-AP-017 | Token DigestからAllowed Device Credentialを解決・失効・Rotate | Active最大1件 / Fail Closed |

Phase 2 Physical Schemaは上記17 PatternのいずれかへTraceできなければ追加しない。逆にAccess Patternを満たせない場合は、実装でScanや自由なKey組立てを追加せず本Sectionを更新する。

### 33.5 Key Namespace Boundary

Memory TableではItem Typeごとに固定Prefixを予約し、User Inputを未検証のままKeyへ連結しない。

| Logical Namespace | Owned Data |
|---|---|
| `MEMORY#...` | Authoritative PersonalMemoryとRevision |
| `MEMORY_INDEX#...` | 管理一覧用の派生Index Item |
| `MEMORY_SETTINGS#...` | Preferences / Reset Point等のSingleton |
| `MEMORY_GUARD#...` | Re-registration Guard |
| `MEMORY_TRACE#...` | Memory Usage Trace |
| `MEMORY_DELETION_PLAN#...` | Deletion Plan / Target / Result |
| `MEMORY_RESTORE_PLAN#...` | Restore Lease / Plan / Action / Result |
| `MEMORY_RELATION_REVIEW#...` | Relation Review / Related Target / Result |
| `MEMORY_OPERATION#...` | Idempotency / Durable Intent / Reconciliation |
| `DEVICE_CREDENTIAL#...` | Allowed Device Credential |

PrefixはInfrastructure DetailでありAPI IDへ公開しない。具体的なSuffix、Sort Key、AttributeおよびIndex Item数は後続Schemaで確定する。

### 33.6 Index and Scan Policy

初期Phase 2 Memory TableではGSI / LSIを作成せず、Primary Key上の専用Index Itemを使用する案を採用する。

- Authoritative MemoryとDerived Index Itemを同じItemとして兼用しない。
- Index ItemはMemory ID、Version、安定Sort情報および安全なFilter属性に限定し、Memory本文を複製しない。
- 一覧は`MEMORY_INDEX#<dimension>#<value>`等の固定Partitionを`Query`する。
- 複数Filterは承認済みAnchor Dimensionから候補を取得し、Bounded Hydration / Validationで絞る。
- DynamoDB `FilterExpression`だけを正確なPaginationの根拠にしない。
- Applicationの通常処理、API、Answer Retrieval、Relation判定およびRepairで`Scan`を使用しない。
- Local Maintenance / MigrationでScanが必要な場合は、通常Runtimeから分離した明示Command、Bounded Page、AuditおよびDry Runを必須とする。

DynamoDBのFilterはQuery後に適用され、Filter結果が0件でも`LastEvaluatedKey`を返し得る。そのため、APIの`limit`とCursorを単純な`Query Limit + FilterExpression`へ直接対応させない。具体的なAnchor選択とPage Filling Algorithmは管理Index設計で確定する。

### 33.7 Transaction and Failure Boundary

- 一つのMemory Mutationに必要なAuthoritative Item、Revision、Index差分、Operation Stateは、DynamoDB上限内なら同一`TransactWriteItems`でAtomicに更新する。
- 100件超のDelete-all / Restore等を一Transactionへ押し込まず、固定Plan、Durable Intent、Chunk ResultおよびReconciliationで処理する。
- Conversation Message保存とAutomatic Memory保存を同一Transactionへ含めない。
- Transaction結果不明時は同じOperation ID / Idempotency-KeyでAuthoritative Stateを再取得し、新しいMemory IDやRevision IDを生成し直さない。
- DynamoDBの上限へ合わせるためにUser-visible Resultを成功へ偽装、Targetを黙って省略またはMemory本文を切断しない。

AWS DynamoDBの`TransactWriteItems`は最大100 Actionかつ合計4 MiBであり、各Itemは最大400 KiBである。Project AliceはこれらをService上限として認識するが、設計上のMemory Content / Page / Transaction上限はより小さい値を後続Sectionで固定する。

### 33.8 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-001 | Phase 1 Conversation Tableを変更せずPhase 2専用Memory Tableを追加する | Accepted |
| DB2-002 | ConversationとMemoryの正常FlowでCross-table Transactionを使用しない | Accepted |
| DB2-003 | Memory Tableへ別Configuration、`pk` / `sk`、共通TTL Attributeを設定する | Accepted |
| DB2-004 | Phase 2 Physical Schemaを17個のAccess PatternへTraceする | Accepted |
| DB2-005 | Item Typeごとの固定Key Namespaceを使用しCoreへKeyを公開しない | Accepted |
| DB2-006 | 初期Memory TableでGSI / LSIを作成せずPrimary Key上の派生Index Itemを使用する | Accepted |
| DB2-007 | Authoritative MemoryとDerived Indexを分離しIndexへ本文を複製しない | Accepted |
| DB2-008 | 通常RuntimeのScanを禁止しFilterExpressionだけにPaginationを依存させない | Accepted |
| DB2-009 | 小規模MutationはAtomic Transaction、大規模MutationはPlan / Chunk / Reconciliationを使用する | Accepted |
| DB2-010 | Transaction / ItemのAWS上限より小さいAlice固有上限を後続Schemaで固定する | Accepted |

### 33.9 Approval and Next Design Unit

DB2-001〜DB2-010はAcceptedである。次の承認単位はSection 34の`PersonalMemory`、`MemoryRevision`および`MemoryPreferences` Authoritative Schemaである。

### 33.10 Official DynamoDB References

- [TransactWriteItems API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_TransactWriteItems.html)
- [Query API and FilterExpression behavior](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Query.html)
- [Scanning tables in DynamoDB](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Scan.html)
- [DynamoDB constraints](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Constraints.html)

---

## 34. Phase 2 Authoritative Memory and Preferences Schema

### 34.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted |
| Scope | `PersonalMemory`、`MemoryRevision`、`MemoryPreferences`のKey、Attribute、Version、Transaction |
| Depends On | DB2-001〜DB2-010、MD-009〜MD-015 / MD-046〜MD-048、API2-013〜API2-021 / API2-057〜API2-069 Accepted |
| Implemented by Design | 管理一覧Index: Section 35、Search Projection: Section 36、Guard / Deletion: Section 37、Operation / Device: Section 38、Restore / Relation / Export: Sections 39〜47 |

本SectionはMemory本文と設定のAuthoritative Itemだけを定義する。API DTO、Domain ObjectまたはBackup RecordをDynamoDB Itemとして直接利用しない。派生IndexはAuthoritative Itemから再生成可能とし、Sections 35〜47のAccepted Contractに従って同一TransactionまたはRecovery可能なProjection処理へ統合する。

### 34.2 Key Pattern

| Item | `pk` | `sk` |
|---|---|---|
| PersonalMemory | `MEMORY#<memoryId>` | `META` |
| MemoryRevision | `MEMORY#<memoryId>` | `REVISION#<20-digit revisionNumber>` |
| MemoryPreferences | `MEMORY_SETTINGS#GLOBAL` | `PREFERENCES` |
| Memory Feature State | `MEMORY_SETTINGS#GLOBAL` | `FEATURE_STATE` |

`memoryId`は小文字・ハイフン付きCanonical UUID v4だけをInfrastructure Adapterで受理する。KeyをAPIへ返さず、User InputをPrefixやDelimiterへ未検証のまま連結しない。

Revision Sort Keyの例:

```text
REVISION#00000000000000000001
REVISION#00000000000000000007
REVISION#00000000000000000012
```

20桁のゼロ埋めによりString Sort Keyの辞書順と数値順を一致させる。Revision取得は`pk = MEMORY#<memoryId>`かつ`begins_with(sk, "REVISION#")`でQueryし、最新順は`ScanIndexForward = false`を使用する。

### 34.3 PersonalMemory Item

```json
{
  "pk": "MEMORY#7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "sk": "META",
  "itemType": "PERSONAL_MEMORY",
  "schemaVersion": 1,
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "content": "仕事ではJavaを使用している。",
  "category": "ENGINEERING",
  "captureType": "EXPLICIT",
  "sensitivityLevel": "NORMAL",
  "memoryState": "ACTIVE",
  "version": 1,
  "createdAt": "2026-08-30T20:00:00.000+09:00",
  "updatedAt": "2026-08-30T20:00:00.000+09:00",
  "confirmedAt": "2026-08-30T20:00:00.000+09:00"
}
```

| Attribute | DynamoDB Type | Required | Rule |
|---|---|---:|---|
| `pk` | S | Yes | `MEMORY#` + Canonical Memory ID |
| `sk` | S | Yes | 固定値`META` |
| `itemType` | S | Yes | 固定値`PERSONAL_MEMORY` |
| `schemaVersion` | N | Yes | Item表現Version。初期値`1` |
| `memoryId` | S | Yes | Key内IDと完全一致 |
| `content` | S | Yes | Canonicalized後1〜2,000 Code Pointかつ最大8,192 UTF-8 Byte |
| `category` | S | Yes | API / DomainでAcceptedの8 Code |
| `captureType` | S | Yes | `EXPLICIT`または`AUTOMATIC`。作成後不変 |
| `sensitivityLevel` | S | Yes | `NORMAL`または`SENSITIVE` |
| `memoryState` | S | Yes | `ACTIVE`または`RESOLVED` |
| `version` | N | Yes | `1`開始、Signed 64-bit正数の範囲で単調増加 |
| `createdAt` | S | Yes | JST Offset付き、ミリ秒3桁。作成後不変 |
| `updatedAt` | S | Yes | 管理対象情報の最終変更日時 |
| `confirmedAt` | S | No | 未確認時はAttribute自体を保存しない |

DynamoDB上の`confirmedAt`欠落をDomainのEmpty Optional、APIのRequired Nullable `null`へMapperで変換する。DynamoDB `NULL`型と空Stringを未確認表現として混在させない。

PersonalMemoryはTTL対象外であり、`expiresAtEpochSeconds`を持たない。削除要求では`DELETED`へ更新せず、Authoritative ItemとContent-bearing Revisionを物理削除する。

### 34.4 MemoryRevision Item

```json
{
  "pk": "MEMORY#7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "sk": "REVISION#00000000000000000002",
  "itemType": "MEMORY_REVISION",
  "schemaVersion": 1,
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "revisionNumber": 2,
  "beforeVersion": 1,
  "afterVersion": 2,
  "changeType": "UPDATE",
  "changedFields": ["CONTENT", "SENSITIVITY_LEVEL"],
  "beforeSnapshot": {
    "content": "Javaを仕事で使っている。",
    "category": "ENGINEERING",
    "captureType": "EXPLICIT",
    "sensitivityLevel": "NORMAL",
    "memoryState": "ACTIVE",
    "updatedAt": "2026-08-30T20:00:00.000+09:00",
    "confirmedAt": "2026-08-30T20:00:00.000+09:00"
  },
  "afterSnapshot": {
    "content": "仕事ではJavaを使用している。",
    "category": "ENGINEERING",
    "captureType": "EXPLICIT",
    "sensitivityLevel": "SENSITIVE",
    "memoryState": "ACTIVE",
    "updatedAt": "2026-08-30T20:10:00.000+09:00",
    "confirmedAt": "2026-08-30T20:10:00.000+09:00"
  },
  "changedAt": "2026-08-30T20:10:00.000+09:00",
  "changeSource": "MANAGEMENT_API",
  "sourceReferences": [],
  "reasonCode": "CONTENT_EDIT"
}
```

| Attribute | Type | Required | Rule |
|---|---|---:|---|
| `pk` / `sk` | S | Yes | Memory Partitionと20桁Revision Key |
| `itemType` | S | Yes | 固定値`MEMORY_REVISION` |
| `schemaVersion` | N | Yes | 初期値`1` |
| `memoryId` | S | Yes | Partition内Memory IDと一致 |
| `revisionNumber` | N | Yes | `afterVersion`と同じ値 |
| `beforeVersion` | N | No | 新規Item作成のRevisionでは省略、それ以外はExpected Version |
| `afterVersion` | N | Yes | Mutation成功後のPersonalMemory Version |
| `changeType` | S | Yes | `CREATE`、`UPDATE`または`RESTORE` |
| `changedFields` | L of S | Yes | 下記Stable Field Code。重複なし、固定順 |
| `beforeSnapshot` | M | No | Create Revisionでは省略 |
| `afterSnapshot` | M | Yes | Mutation成功後のContent-bearing Snapshot |
| `changedAt` | S | Yes | Mutation成功時刻 |
| `changeSource` | S | Yes | 下記Stable Source Code |
| `sourceReferences` | L of S | Yes | Content-free Opaque ID。0〜8件、各200 UTF-8 Byte以下、合計2 KiB以下 |
| `reasonCode` | S | Yes | 下記Stable Reason Code |

Snapshotは`content`、`category`、`captureType`、`sensitivityLevel`、`memoryState`、`updatedAt`およびOptional `confirmedAt`だけを持つ。Conversation本文、AI Prompt / Response、Relation Score、Secret検出結果またはProvider Metadataを含めない。

Stable Code:

| Axis | Values |
|---|---|
| `changedFields` | `CONTENT`、`CATEGORY`、`SENSITIVITY_LEVEL`、`MEMORY_STATE` |
| `changeSource` | `MANAGEMENT_API`、`CONVERSATION_EXPLICIT`、`CONVERSATION_AUTOMATIC`、`RESTORE` |
| `reasonCode` | `INITIAL_SAVE`、`CONTENT_EDIT`、`CLASSIFICATION_CORRECTION`、`STATE_TRANSITION`、`COMPLEMENTARY_MERGE`、`SUPERSESSION`、`RESTORE_MERGE` |

複数Field変更時は`changedFields`を`CONTENT`、`CATEGORY`、`SENSITIVITY_LEVEL`、`MEMORY_STATE`の固定順で保存する。`reasonCode`は主たるDomain判断を一つ選び、自由記述を保存しない。

### 34.5 Revision Creation and Retention

- 新規PersonalMemory作成時に`revisionNumber = 1`のRevisionを作成する。通常登録は`CREATE`、Restoreによる新規導入は`RESTORE`とする。
- Content、Category、SensitivityまたはStateの一つ以上が変化した場合、更新後Versionと同じRevision Numberで一件作成する。
- `ConfirmMemoryUseCase`で`confirmedAt`とVersionだけが変わる場合、Content-bearing Revisionを作成しない。
- No ChangeではPersonalMemory、Version、TimestampおよびRevisionを変更しない。
- Confirmation-only Mutation後はRevision Numberに欠番が生じ得る。欠番を破損と判断せず、同じRevision Numberの再利用もしない。
- RevisionはPersonalMemoryが存在する間TTLなしで保持する。
- PersonalMemory削除時は同Partitionの全Revisionを削除対象とし、削除済み本文をRevisionへ残さない。

### 34.6 Create Transaction

新規保存のCore Transactionは固定Memory ID、固定時刻および固定Revision KeyをTransaction構築前に決定し、次の2 Actionを含む。

| Action | Item | Condition |
|---:|---|---|
| 1 | Put PersonalMemory `META` | `attribute_not_exists(pk) AND attribute_not_exists(sk)` |
| 2 | Put Revision `#000...001` | `attribute_not_exists(pk) AND attribute_not_exists(sk)` |

両Putは一つの`TransactWriteItems`でAtomicに実行する。新規Memoryは`version = 1`、`createdAt = updatedAt`、明示登録なら`confirmedAt`も同時刻とする。Automatic Captureは`confirmedAt`を省略する。

Sections 35、36、37、38および45に従い、必要な6 Management Index、Projection Intent、Fence / BoundaryおよびDurable Operation Itemを同じTransactionへ追加する。Core 2 Actionだけを先行Commitし、Cross-cutting Action失敗を別成功として扱わない。

### 34.7 Semantic Update Transaction

ApplicationはStrongly Consistent Readで取得したPersonalMemoryとExpected Versionから、変更前後Snapshot、`nextVersion = expectedVersion + 1`、Revision Keyおよび変更時刻をTransaction前に確定する。

| Action | Item | Condition |
|---:|---|---|
| 1 | Update PersonalMemory `META` | `#itemType = :personalMemoryType AND #version = :expected AND #version < :longMax` |
| 2 | Put `REVISION#<nextVersion>` | `attribute_not_exists(pk) AND attribute_not_exists(sk)` |

Update Action自身のConditionでVersionを検査し、同じItemへ別の`ConditionCheck`を追加しない。DynamoDB Transactionは同一Itemを複数Actionの対象にできないためである。

Updateでは次をAtomicに設定する。

- 変更対象の`content`、`category`、`sensitivityLevel`または`memoryState`
- `version = nextVersion`
- `updatedAt = changedAt`
- 明示編集の場合だけ`confirmedAt = changedAt`

`memoryId`、`captureType`および`createdAt`は変更しない。Version不一致はPrecondition Failureへ変換し、Transaction Conflict / Throttling / Timeoutと区別する。結果不明時は同じMemory IDとRevision KeyでStrong Readし、Authoritative VersionとRevision存在を照合する。

### 34.8 Confirmation-only and No-change Update

Confirmation-only MutationはPersonalMemory一件へのConditional Updateとする。

```text
Condition: #itemType = :personalMemoryType
       AND #version = :expected
       AND #version < :longMax

Set: confirmedAt = :confirmedAt
     #version = :nextVersion
```

`updatedAt`を変更せず、Revisionを作成しない。Section 35に従ってConfirmed / Unconfirmed Index差分だけを同一Transactionへ含め、Section 45のTXL-003でAction / Byte上限を検証する。

入力をCanonicalizeして現在値と比較した結果、管理対象Fieldも`confirmedAt`も変化しない場合は`NO_CHANGE`を返し、Write、Version増加またはRevision作成を行わない。比較後に並行更新が起きた場合はExpected Version Conditionで検出する。

### 34.9 MemoryPreferences Item and Initialization

```json
{
  "pk": "MEMORY_SETTINGS#GLOBAL",
  "sk": "PREFERENCES",
  "itemType": "MEMORY_PREFERENCES",
  "schemaVersion": 1,
  "autoSaveEnabled": true,
  "answerUseEnabled": true,
  "version": 1,
  "updatedAt": "2026-08-30T20:00:00.000+09:00"
}
```

MemoryPreferencesはSingle User全体で一件だけ保持し、TTLを設定しない。正常な新規環境と初期化後のData Lossを区別するため、同じSettings PartitionへContent-freeなFeature Stateを一件保持する。

```json
{
  "pk": "MEMORY_SETTINGS#GLOBAL",
  "sk": "FEATURE_STATE",
  "itemType": "MEMORY_FEATURE_STATE",
  "schemaVersion": 1,
  "preferencesInitialized": false,
  "createdAt": "2026-08-30T20:00:00.000+09:00"
}
```

Phase 2 Table Setup / Migration CommandはFeature Stateだけを`preferencesInitialized = false`で作成する。BackendのPhase 2初期化処理または最初のPreferences GETは、Preferencesが未作成かつFeature Stateが`false`の場合だけ、次を同一Transactionで実行する。

| Action | Item | Condition |
|---:|---|---|
| 1 | Put MemoryPreferences defaults | `attribute_not_exists(pk) AND attribute_not_exists(sk)` |
| 2 | Update Feature State | `#itemType = :featureStateType AND #preferencesInitialized = :false` |

Transaction成功時にPreferencesを`true / true`、`version = 1`、`updatedAt = logical initialization time`で作成し、Feature Stateを`preferencesInitialized = true`へ変更してOptional `preferencesInitializedAt`を同時刻で設定する。並行初期化でConditionが失敗した処理はPreferencesをStrong Readし、作成済みの同じ結果へ収束する。

Feature Stateが`true`なのにPreferencesが存在しない、Feature State自体が欠落・破損している、またはPersistence障害で不存在と区別できない場合、Defaultを再作成しない。Phase 2 Configuration / Data Integrity ErrorとしてMemory Answer UseとAutomatic CaptureをFail Closedにし、管理APIは修復可能なService Unavailableを返す。

設定変更は現在ItemをStrong Readし、実値が変わる場合だけ次を一つのConditional Updateで行う。

```text
Condition: #itemType = :memoryPreferencesType
       AND #version = :expected
       AND #version < :longMax

Set: #autoSaveEnabled = :autoSaveEnabled
     #answerUseEnabled = :answerUseEnabled
     #version = :nextVersion
     #updatedAt = :changedAt
```

一方だけを変更するRequestでも、Applicationが現在値とMergeした完全な2 Booleanを保存する。No ChangeではWrite、Versionおよび`updatedAt`を変更しない。Preferences Revision HistoryはPhase 2では作成しない。

### 34.10 Physical Type and Size Safety

- `version`、`revisionNumber`、`beforeVersion`、`afterVersion`および`schemaVersion`はDynamoDB Numberとして保存し、Java `long`のChecked Arithmeticを使用する。
- TimestampはJST Offset付き・ミリ秒3桁固定のStringとして保存する。
- BooleanはDynamoDB BOOLを使用し、`"true"` / `"false"` Stringを使用しない。
- Optional Attributeは未設定時に省略し、空String、DynamoDB NULLおよびSentinel値を混在させない。
- ExpressionではすべてのAttribute名へExpression Attribute Nameを使用し、Reserved Wordとの偶然の衝突へ依存しない。本SectionのExpression例にある`pk` / `sk`等も実装時は`#pk` / `#sk`へAliasする。
- AdapterはDynamoDB AttributeへMapping後、Request送信前にKey、Attribute名、値およびNested構造を含むItem Sizeを検証し、上限へ合わせた切詰めを行わない。

Alice固有の最大Item Size:

| Item | Maximum Encoded Item Size |
|---|---:|
| PersonalMemory | 16 KiB |
| MemoryRevision | 32 KiB |
| MemoryPreferences | 4 KiB |
| Memory Feature State | 4 KiB |

この上限はAttribute名、KeyおよびNested Mapを含むDynamoDB Item全体へ適用する。API上限内のContentでもItem上限を超えた場合は保存前に明示的なSize Errorとし、DynamoDBの400 KiB上限まで許容しない。

### 34.11 Retry and Idempotency Boundary

Memory ID、Revision Number、Timestamp、SnapshotおよびOperation DigestをRetryごとに再生成しない。AWSの`ClientRequestToken`を使用できるが、そのIdempotency期間だけをAliceのDurable Idempotency Contractにしない。

同じTokenで異なるTransaction Parameterを送信しない。10分を超えるRetry、Process再起動および結果不明へ対応するDurable Operation ItemはP2-DB-AP-014のSchemaで設計し、Create / Update Transactionへ組み込む。

### 34.12 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-011 | PersonalMemory本体とRevisionを`MEMORY#<memoryId>` Partitionへ配置する | Accepted |
| DB2-012 | PersonalMemory ItemのKey、型、Stable Code、VersionおよびTimestampをSection 34.3のSchemaへ固定する | Accepted |
| DB2-013 | 未確認`confirmedAt`をAttribute欠落で表しDomain Optional / API `null`へ変換する | Accepted |
| DB2-014 | Revision Sort Keyを更新後Versionの20桁ゼロ埋めとしConfirmation-onlyによる欠番を許可する | Accepted |
| DB2-015 | RevisionへBounded Snapshot、変更Field、Source、Opaque ReferenceおよびReason Codeを保持する | Accepted |
| DB2-016 | CreateとSemantic UpdateでRevisionを作成しConfirmation-only / No Changeでは作成しない | Accepted |
| DB2-017 | RevisionへTTLを設定せずMemory削除時にContent-bearing Revisionも削除する | Accepted |
| DB2-018 | 新規PersonalMemoryとCreate Revisionを同一Conditional Transactionで作成する | Accepted |
| DB2-019 | Semantic UpdateをExpected Version付き本体Updateと新Revision PutのAtomic Transactionにする | Accepted |
| DB2-020 | No ChangeではWrite、Version、TimestampおよびRevisionを変更しない | Accepted |
| DB2-021 | Feature Stateを使ってMemoryPreferencesを一度だけ条件付き初期化し初期化後の欠落はFail Closedにする | Accepted |
| DB2-022 | PreferencesをExpected Versionで条件付き更新しNo Changeでは更新しない | Accepted |
| DB2-023 | DynamoDB Number / BOOL / Optional / Timestampの物理表現をSection 34.10へ固定する | Accepted |
| DB2-024 | PersonalMemory 16 KiB、Revision 32 KiB、Preferences / Feature State 4 KiBのItem上限を適用する | Accepted |
| DB2-025 | RetryでIdentityを再生成せずAWS Tokenだけに依存しないDurable Idempotency Boundaryを設ける | Accepted |

### 34.13 Integration Record

DB2-011〜DB2-025はAcceptedである。管理一覧、Search Projection、Guard / Deletion、Operation / Device、Restore / Relation / ExportおよびCross-review補強はSections 35〜47、DB2-026〜DB2-243として実装設計済みである。Section 34のCore Transactionを単独で実装せず、Section 45の最終Transaction Ledgerを使用する。

### 34.14 Official DynamoDB References

- [DynamoDB transactions: how it works](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html)
- [TransactWriteItems API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_TransactWriteItems.html)
- [Key condition expressions for Query](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Query.KeyConditionExpressions.html)
- [DynamoDB constraints](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Constraints.html)

---

## 35. Phase 2 Management List Index and Pagination

### 35.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted |
| Scope | `GET /api/v1/memories`用Derived Index、複数Filter、Ordering、Hydration、Cursor、Repair |
| Depends On | DB2-001〜DB2-025、API2-023〜API2-027 / API2-032〜API2-037 Accepted |
| Out of Scope | 自由入力Management Search、Answer Retrieval、Relation SearchのRanking / Projection |

本Sectionは管理一覧の`updatedAt DESC, memoryId ASC`をPrimary Key Queryだけで実現する。Derived IndexをSource of Truthにせず、候補Memory IDから最新PersonalMemoryをStrong ReadしてResponseを構築する。

### 35.2 Index Set per Memory

一つのPersonalMemoryにつき、次の6個のIndex Itemを保持する。

| Dimension | Partition Key Pattern | Value Count per Memory |
|---|---|---:|
| All | `MEMORY_INDEX#ALL` | 1 |
| Category | `MEMORY_INDEX#CATEGORY#<category>` | 1 |
| State | `MEMORY_INDEX#STATE#<memoryState>` | 1 |
| Capture Type | `MEMORY_INDEX#CAPTURE#<captureType>` | 1 |
| Sensitivity | `MEMORY_INDEX#SENSITIVITY#<sensitivityLevel>` | 1 |
| Confirmation | `MEMORY_INDEX#CONFIRMATION#<CONFIRMED|UNCONFIRMED>` | 1 |

Filter値をすべて指定するDimensionは、Filter Canonicalizationで未指定と同じ「All」へ縮約する。Index Partition名は固定Enum Codeだけから構築し、User入力の任意文字列を連結しない。

### 35.3 Stable Ordering Sort Key

Sort Keyは次とする。

```text
UPDATED#<19-digit reverseEpochMillis>#<memoryId>
```

```text
reverseEpochMillis = Long.MAX_VALUE - updatedAt.toInstant().toEpochMilli()
```

例:

```text
pk = MEMORY_INDEX#CATEGORY#ENGINEERING
sk = UPDATED#9223370248766575807#7f1f8e2a-4b3c-4d5e-8f60-123456789abc
```

`reverseEpochMillis`を19桁ゼロ埋めし、`ScanIndexForward = true`でQueryする。新しい`updatedAt`ほど小さいSort Keyとなり、同時刻ではCanonical Memory IDの辞書順が昇順になる。これによりAPIのTotal Orderを次へ一致させる。

```text
updatedAt DESC
memoryId ASC
```

`updatedAt`は1970-01-01以降、Signed 64-bit Epoch Millisecondへ変換可能なAccepted Project日時だけを使用する。Sort Keyの時刻とItem内`indexedUpdatedAt`が一致しないItemを受理しない。

### 35.4 Management Index Item

```json
{
  "pk": "MEMORY_INDEX#CATEGORY#ENGINEERING",
  "sk": "UPDATED#9223370279343575807#7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "itemType": "MEMORY_MANAGEMENT_INDEX",
  "schemaVersion": 1,
  "dimension": "CATEGORY",
  "dimensionValue": "ENGINEERING",
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "indexedVersion": 7,
  "indexedUpdatedAt": "2026-08-30T20:10:00.000+09:00"
}
```

| Attribute | Type | Required | Rule |
|---|---|---:|---|
| `pk` / `sk` | S | Yes | Section 35.2 / 35.3の固定形式 |
| `itemType` | S | Yes | 固定値`MEMORY_MANAGEMENT_INDEX` |
| `schemaVersion` | N | Yes | 初期値`1` |
| `dimension` | S | Yes | `ALL`、`CATEGORY`、`STATE`、`CAPTURE`、`SENSITIVITY`、`CONFIRMATION` |
| `dimensionValue` | S | Yes | Dimensionに対応するStable Code。ALLは`ALL` |
| `memoryId` | S | Yes | Canonical Memory ID |
| `indexedVersion` | N | Yes | Index作成・更新時に参照したPersonalMemory Version |
| `indexedUpdatedAt` | S | Yes | Sort Key生成に使用した`updatedAt` |

Index ItemへMemory本文、`confirmedAt`日時、Revision、Source Reference、Search Token、EmbeddingまたはSecretを保存しない。Item上限は2 KiBとする。TTLは設定せず、Authoritative MutationまたはRepairで削除する。

### 35.5 Atomic Mutation Integration

#### Create

新規Memory作成Transactionへ6個のIndex Putを追加する。

```text
PersonalMemory Put        1
Create Revision Put       1
Management Index Put      6
---------------------------
Core Total                8 Actions
```

各Index Putは`attribute_not_exists(#pk) AND attribute_not_exists(#sk)`を条件とする。

#### Semantic Update

Content、Category、SensitivityまたはStateの実変更では`updatedAt`が変わるため、旧6 IndexをDeleteし、新6 IndexをPutする。

```text
PersonalMemory Update     1
Revision Put              1
Old Index Delete          6
New Index Put             6
---------------------------
Core Total               14 Actions
```

旧Index Deleteは`memoryId`と`indexedUpdatedAt`一致を条件とし、別Mutationが作成したItemを削除しない。新Index PutはKey未使用を条件とする。Category等が変わらなくても`updatedAt`が変わるためSort Keyが変わり、全6件を入れ替える。

意味変更時のEffective `changedAt` / `updatedAt`は、ミリ秒精度へ正規化したうえで次により決定する。

```text
effectiveChangedAt = max(clockNow, current.updatedAt + 1 millisecond)
```

これにより同一ミリ秒内の連続Mutationでも`updatedAt`を厳密に単調増加させ、旧Indexと新Indexが同じKeyにならないようにする。Revisionの`changedAt`も同じEffective Timestampを使用する。Clock逆行時に古い日時へ戻さず、異常回数をContent-free Metricで監視する。

#### Confirmation-only

`confirmedAt`だけの更新では`updatedAt`を変更しない。

- `UNCONFIRMED`から`CONFIRMED`へ変わる場合、旧Confirmation IndexをDeleteし、新Confirmation IndexをPutする。
- 既に`CONFIRMED`のMemoryを再確認する場合、Management Index Keyは変更しない。
- ALL、Category、State、CaptureおよびSensitivity Indexは書き直さない。

後続のDurable Operation Itemを加えても、一Memoryの通常Create / UpdateをSection 35.13のAlice Transaction上限内に収める。

旧Indexの欠落または条件不一致でTransactionが失敗した場合、Rootだけを更新したりIndex Delete条件を外したりしない。Index Inconsistencyとして最新Rootを再取得し、Section 35.14のRepair完了後、同じUser IntentとExpected Versionがなお有効な場合だけ再評価する。

### 35.6 Index Version and Final Validation

`indexedVersion`は診断とRepair判断に使うが、PersonalMemory Versionとの単純な完全一致だけで候補を破棄しない。Confirmation-only UpdateはVersionを進めても、Confirmation以外の5 Index Keyと一覧順序を変えないためである。

候補Indexを取得した後、Strong Readした最新PersonalMemoryから、そのDimensionについて期待される`pk` / `sk`を再計算する。

| Validation | Result |
|---|---|
| Root不存在 | 候補除外、Dangling Index Repair |
| Expected `pk` / `sk`と一致 | Versionが進んでいても候補として利用可能 |
| Expected `pk` / `sk`と不一致 | 候補除外、Stale Index Repair |
| Root Field / Enum / Timestamp不正 | 候補除外、Data Integrity Error |

ResponseのContent、Category、State、Sensitivity、Confirmation、VersionおよびETagは、すべて最新PersonalMemoryから構築する。Index Attributeだけから`MemoryResource`を生成しない。

### 35.7 Filter Canonicalization and Anchor Selection

FilterはAPI Validation後、次の順でCanonicalizeする。

1. Dimension内の値をStable Code順へ並べる。
2. 全Enum値が指定されたDimensionを削除する。
3. 残ったDimensionを固定順へ並べる。
4. Canonical Filter SetのDomain-separated Keyed DigestをCursor Bindingへ使用する。

Filterが残らなければ`ALL` PartitionをAnchorとする。

Filterがある場合、各Dimensionの静的Selectivity Ratioを計算する。

```text
selected value count / total value count
```

最小RatioのDimensionをAnchorに選ぶ。同率の場合は次の固定優先順を使う。

```text
CATEGORY → STATE → CAPTURE → SENSITIVITY → CONFIRMATION
```

例:

```text
category = 2 / 8 = 0.25
state    = 1 / 2 = 0.50

Anchor = CATEGORY
```

Phase 2ではCardinality CounterやQuery Planner統計を追加しない。Single Userの実測で候補読取が上限へ頻繁に達する場合に限り、CounterまたはGSIをDesign Changeとして検討する。

### 35.8 Multi-value Anchor Merge

Anchor Dimensionで複数値が指定された場合、値ごとのPartitionを独立した昇順StreamとしてQueryし、BackendでSort KeyのK-way Mergeを行う。

```text
CATEGORY#ENGINEERING ─┐
                      ├─ Sort Key ascending merge ─ Candidate Order
CATEGORY#PROJECT ─────┘
```

各Streamは`ConsistentRead = true`かつ同じSort Key形式を使用するため、Merge後も`updatedAt DESC, memoryId ASC`を維持できる。一つのMemoryは一Dimension内で一値だけを持つため、同じAnchor Dimensionの複数Stream間で同一Memoryが重複しない。それでもPage構築時は`memoryId` Setで重複を防止する。

DynamoDB `FilterExpression`で他Dimensionを絞らない。他FilterはAuthoritative Hydration後に評価する。

### 35.9 Bounded Candidate Collection and Hydration

一覧の`limit`は1〜100である。Backendは次のAlice内部上限を適用する。

| Internal Boundary | Limit |
|---|---:|
| Low-level Query page | 最大100 Index Item |
| Candidate evaluation per API request | `max(200, limit × 5)`、最大500件 |
| BatchGetItem per call | 最大100 PersonalMemory |
| BatchGet retry | 初回 + 最大3回、Exponential Backoff + Jitter |
| Response | API Contractどおり最大100件かつ2 MiB |

Candidateは最大100件ずつ、Memory Tableに対する`BatchGetItem`の`ConsistentRead = true`でHydrateする。BatchGetは返却順を保証しないため、`memoryId`でMap化し、Candidate Stream順へ並べ直す。`UnprocessedKeys`はBounded Retryし、解消できなければ管理API全体を安全なService Errorとする。取得できた一部だけを完全な一覧として`200`にしない。

Hydrate後、最新PersonalMemoryに対してExistence、Index Key、すべてのFilter、SchemaおよびSizeを検証する。条件を満たすものだけをPageへ追加する。

### 35.10 Page Filling and Cursor Boundary

Backendは候補を順に評価し、次のいずれかまで内部Queryを継続する。

- `limit + 1`件の有効Memoryを確認した
- 次のResourceを追加するとSerialized Responseが2 MiBを超える
- すべてのAnchor Streamが終端へ到達した
- Candidate Evaluation上限へ到達した

`limit + 1`件目を確認した場合、先頭`limit`件だけを返し、次CursorのBoundaryは「最後に返したMemory」とする。確認用の1件は次Pageで再評価する。

2 MiB上限へ到達した場合も、次に追加予定だった有効Memoryを現在Pageで消費せず、最後に返したMemoryをCursor Boundaryとする。単一`MemoryResource`はAccepted API上限上32 KiB以下であるため、正常な最初のResourceを一件も返せない状態はContract / Serialization Errorとして扱う。

Evaluation上限へ到達した場合:

| Valid Result | Remaining Candidate | API Result |
|---:|---:|---|
| 1件以上 | Yes | 取得済みMemoryを返し、最後に評価済みのCandidateをCursor Boundaryとして`hasMore = true` |
| 0件 | Yes | 不完全なEmpty Pageを返さず`SERVICE_UNAVAILABLE` |
| Any | No | 取得済み結果、`hasMore = false` |

Underlying Queryの`LastEvaluatedKey`だけを「次の有効結果が存在する」証拠にしない。Buffer済みCandidateまたは追加Queryによって後続候補の存在を確認する。APIの`hasMore = true`なら`memories`が空でないInvariantを維持する。

### 35.11 List Cursor Persistence Boundary

Phase 2のMemory List Cursorは、Backend Stateを保存しないIntegrity保護付きTokenとする。Payloadの論理要素は次とする。

| Field | Meaning |
|---|---|
| `cursorVersion` | Cursor Schema Version、初期値1 |
| `purpose` | `MEMORY_LIST`固定 |
| `filterDigest` | Canonical Filter SetのDomain-separated Keyed Digest |
| `limit` | Request limit |
| `orderingVersion` | List Ordering Contract Version、初期値1 |
| `anchorDimension` | `ALL`または選択Dimension |
| `anchorValuesDigest` | Canonical Anchor Value SetのDomain-separated Keyed Digest |
| `boundaryUpdatedAt` | 最後に返却または評価済みの論理境界 |
| `boundaryMemoryId` | 同順位の安定境界 |
| `issuedAt` / `expiresAt` | 発行時刻と15分以内の期限 |

Adapterは論理BoundaryからSort Keyを再構築し、各Anchor Streamを`sk > :boundarySortKey`で再開する。DynamoDB `LastEvaluatedKey`、Table名または生の`pk` / `sk`をAPI Cursor Contractへ公開しない。複数Streamごとの位置をCursorへ保持せず、共通Total Order Boundaryを使用する。

FilterとAnchorは値域が小さく通常Hashでは辞書照合できるため、平文SHA-256等を使用しない。Cursor専用KeyとPurpose別Domain Separationを使うKeyed Digestとする。TokenへMemory本文、Filterの平文値、検索語、Sensitive情報またはCredentialを含めない。署名AlgorithmとKey RotationはSecurity Designで確定する。Cursor改変、期限切れ、Purpose / Filter / limit / Ordering不一致は`INVALID_CURSOR`とする。

### 35.12 Concurrent Mutation Behavior

本一覧はDatabase Snapshotではない。Cursor後にMemoryが更新されて先頭側へ移動した場合、現在Sessionの後続Pageに現れないことがある。削除済みMemoryはHydrationで除外し、新規MemoryがBoundaryより前へ追加されても現在Sessionへ差し込まない。

一Page内ではMemory IDを重複させない。Mutation成功後にFrontendがSessionを破棄して先頭から再取得するAPI2-036を維持する。

### 35.13 Transaction and Item Safety Limits

Phase 2の一Memory通常Mutationへ次のAlice上限を適用する。

| Boundary | Alice Limit |
|---|---:|
| Transaction Action | 最大25 Action |
| Transaction Aggregate Encoded Size | 最大512 KiB |
| Management Index Item | 最大2 KiB |

上限はTransaction構築後、DynamoDB Request前に検証する。超過時にIndex、RevisionまたはOperation Itemを黙って省略せず、Mutation全体を失敗させる。Delete-all、Restore等の大規模処理はこの通常Mutationへ押し込まず、Accepted Plan / Chunk / Reconciliation Boundaryを使用する。

### 35.14 Index Repair and Audit

通常ReadでDanglingまたはStale Indexを検出した場合、Responseへ古い内容を返さず、Content-freeなRepair Intentを記録する。Repairは次を再確認してから実行する。

1. PersonalMemoryをStrong Readする。
2. Repair対象Indexの`memoryId`、`indexedVersion`および`indexedUpdatedAt`が検出時と一致することを確認する。
3. Root不存在なら対象Indexだけを条件付き削除する。
4. Root存在なら最新Rootから期待される6 Indexを再計算し、古いItem削除と不足Item作成をBounded Transactionで行う。
5. 並行Mutationで条件不一致になった場合、上書きせず新しい状態を再評価する。

Read Request内で無制限に同期Repairせず、Rate Limit付きWorkerまたは明示Maintenance Commandへ渡す。Repair Intent、LogおよびMetricへMemory本文やFilter値を記録しない。

Missing Indexは通常一覧から発見できないため、Atomic Mutationを第一防御とする。全件整合監査が必要な場合だけ、通常Runtime権限と分離したMaintenance CommandでBounded Scanを許可する。

- Dry RunをDefaultとする。
- Page Size、最大評価件数および実行時間を必須指定する。
- Authoritative Rootから期待Indexを再計算する。
- 修正前に件数とContent-free差分を表示する。
- User確認後だけApplyする。
- Memory本文、Revision SnapshotまたはSecretを出力しない。

### 35.15 Failure and Observability

少なくとも次のContent-free Metricを記録する。

- Candidate evaluated count
- Hydrated count
- Filter rejected count
- Dangling / Stale index count
- BatchGet retry / unresolved key count
- Candidate budget exhausted count
- Repair queued / succeeded / conflict / failed count

Memory ID、Index Key、Filter値、Cursor、Memory本文およびSensitive属性を通常Metric / Log Labelにしない。Candidate上限到達が継続する場合、上限だけを無条件に増やさず、Filter分布、Anchor選択およびGSI / Counter導入要否をDesign Reviewする。

### 35.16 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-026 | 一MemoryにつきALLと5 Filter Dimensionの計6 Management Index Itemを保持する | Accepted |
| DB2-027 | Reverse Epoch MillisecondとMemory IDで`updatedAt DESC, memoryId ASC`を構成する | Accepted |
| DB2-028 | Management Indexを2 KiB以下のContent-free Derived Itemとする | Accepted |
| DB2-029 | CreateでRoot、Revisionおよび6 Indexを8 ActionのAtomic Transactionへ含める | Accepted |
| DB2-030 | Semantic Updateで旧6 Index削除と新6 Index作成をRoot / RevisionとAtomicに行う | Accepted |
| DB2-031 | Confirmation-onlyでは必要なConfirmation Indexだけを差し替える | Accepted |
| DB2-032 | Index Version完全一致ではなく最新RootからExpected Keyを再計算して候補を検証する | Accepted |
| DB2-033 | 全値Filterを削除しKeyed Digest化したCanonical Filter SetをCursor Bindingへ使用する | Accepted |
| DB2-034 | 静的Selectivity Ratioと固定Tie-breakでAnchor Dimensionを決定する | Accepted |
| DB2-035 | Multi-value Anchorを複数Query StreamのK-way MergeでTotal Orderへ統合する | Accepted |
| DB2-036 | 最大500 CandidateをStrong BatchGetでHydrateし返却順を再構成する | Accepted |
| DB2-037 | `limit + 1`、終端またはCandidate上限までPage Fillingし不完全Empty Pageを返さない | Accepted |
| DB2-038 | List Cursorを共通論理BoundaryへBindingした15分のIntegrity保護Tokenとする | Accepted |
| DB2-039 | Concurrent Mutation下でSnapshotを保証せずMutation後は先頭から再取得する | Accepted |
| DB2-040 | Read検出のStale / Dangling IndexをContent-free Intent経由で条件付きRepairする | Accepted |
| DB2-041 | Missing Index全件監査だけを分離Maintenance CommandのBounded Scanで行う | Accepted |
| DB2-042 | 通常Transactionを25 Action / 512 KiB以下、Index Itemを2 KiB以下とする | Accepted |
| DB2-043 | Management SearchのRelevance ProjectionをList Indexから分離して後続設計する | Accepted |
| DB2-044 | Semantic Mutationの`updatedAt`を直前値より最低1ms進めIndex Key衝突とClock逆行を防ぐ | Accepted |

### 35.17 Approval and Next Design Unit

DB2-026〜DB2-044はAcceptedである。次の承認単位はSection 36のSearch Projection、Keyword Candidate GenerationおよびSemantic Search昇格条件である。

### 35.18 Official DynamoDB References

- [Query API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Query.html)
- [Key condition expressions for Query](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Query.KeyConditionExpressions.html)
- [Paginating table Query results](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Query.Pagination.html)
- [BatchGetItem API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchGetItem.html)
- [DynamoDB transaction constraints](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Constraints.html)

---

## 36. Phase 2 Keyword Search Projection and Promotion

### 36.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted |
| Initial Search Technology | DynamoDB Keyword Inverted Index |
| Scope | Search Analyzer、Projection、Posting、Update Intent、Candidate Generation、Management Search Session、Eval |
| Depends On | DB2-001〜DB2-044、MD-067〜MD-085、API2-023〜API2-037 Accepted |
| Not Adopted Initially | Vector Store、Embedding、外部Search Service、AI Reranking必須化 |

Phase 2初期実装は、構造化FilterとDynamoDB Keyword Inverted Indexを組み合わせる。Semantic Searchへ交換・追加できるPort Boundaryを維持するが、将来利用する可能性だけを理由にVector Storeを先行導入しない。

### 36.2 Purpose Separation and Shared Infrastructure

| Purpose | Posting Scope | Candidate / Result Boundary |
|---|---|---|
| `MANAGEMENT_SEARCH` | Currentのみ | 完全性を確認後、最大500件のSession候補をRanking |
| `ANSWER_CURRENT` | Currentのみ | 最大40候補。Failure時はMemoryなしで会話継続 |
| `ANSWER_HISTORICAL` | Current + Historical | Historical Intentが明示された場合だけRevision候補を追加 |
| `RELATION_DECISION` | Currentのみ | 最大40候補。不完全時に`NONE` / `CREATE_NEW`を確定しない |

`AnswerMemorySearchPort`、`RelatedMemorySearchPort`および`MemoryManagementQueryPort`はApplication Contractを分離する。Infrastructure内ではAnalyzer、Term Digest、Posting RepositoryおよびHydration Componentを共有できるが、PurposeごとのFilter、Ranking、Failure Semanticsおよび上限を混同しない。

### 36.3 Keyword Analyzer `KW_V1`

Search用Normalizationは保存本文を変更せず、派生Term生成にだけ使用する。Analyzer Versionを`KW_V1`として固定する。

1. `CRLF` / 単独`CR`を`LF`へ統一する。
2. Unicode NFKCへ正規化する。
3. `Locale.ROOT`相当の小文字化を行う。
4. Letter / Number以外のUnicode文字を区切りとして扱い、連続区切りを一つにする。
5. Latin / Digitの連続列およびCJK / Hiragana / Katakanaの連続列を本文から決定的に抽出する。
6. CJK / Kana列から3-gram、2-gram、1-gramを生成する。
7. 重複を除き、Stable Priorityと最初の出現位置で並べる。

Term Kindと優先順:

```text
LEXICAL
NGRAM_3
NGRAM_2
NGRAM_1
```

初期Versionでは言語別Stop Word List、Stemming、Fuzzy Edit Distanceまたは外部形態素解析器を必須化しない。日本語の空白なし検索をN-gramで扱い、英数字はLexical Termを優先する。

| Term Kind | `weightCode` |
|---|---:|
| `LEXICAL` | 4 |
| `NGRAM_3` | 3 |
| `NGRAM_2` | 2 |
| `NGRAM_1` | 1 |

64 Code Pointを超える連続列は、64文字ごとのLexical Termへ単純分割して完全一致とみなさず、N-gramを主な検索単位とする。`weightCode`は候補順序用Signalであり、真偽やSecurity判断に使用しない。

| Analyzer Boundary | Limit |
|---|---:|
| Document unique terms | 最大512 |
| Query unique terms | 最大32 |
| Single term | 1〜64 Unicode Code Point、最大256 UTF-8 Byte |
| Query-side Entity / Attribute / Scope hints | 合計最大32。Documentへ永続化しない |

512 Termを超えたDocumentは、上記Priorityで512件を保持して`fallbackRequired = true`とする。同時にFallback Postingへ登録し、検索時に最新Authoritative Contentを直接Analyzerへ通して一致を再確認する。Term切捨てだけを理由に検索対象から消さない。

### 36.4 Keyed Term Digest and Rotation

TermをPartition Keyへ平文保存しない。次のDomain-separated HMAC-SHA-256を使用し、32 Byte全体をBase64url without paddingで表現する。

```text
termDigest = HMAC-SHA-256(
    searchTermKey,
    "alice-memory-search-term\0" + analyzerVersion + "\0" + normalizedTerm
)
```

Search KeyにはVersionを付ける。

```text
MEMORY_SEARCH#TERM#<tokenKeyVersion>#CURRENT#<termDigest>
MEMORY_SEARCH#TERM#<tokenKeyVersion>#HISTORICAL#<termDigest>
```

Key、Normalized Term、Digestおよび一致対象を通常Log / Metricへ出力しない。Rotation時は旧・新の最大2 Key Versionを一時的にReadし、Authoritative Content / Revisionから新Projectionを再構築する。旧Key VersionのPostingを削除確認後にRead対象から外す。Digestだけから旧Termを新Keyへ変換しない。

### 36.5 Projection State and Term Pages

Current Projectionの管理Item:

| Item | `pk` | `sk` |
|---|---|---|
| Projection Metadata | `MEMORY_SEARCH#PROJECTION#<memoryId>` | `META` |
| Semantic Version Term Page | same | `VERSION#<20-digit semanticVersion>#TERMS#<4-digit page>` |

Projection Metadata例:

```json
{
  "pk": "MEMORY_SEARCH#PROJECTION#7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "sk": "META",
  "itemType": "MEMORY_SEARCH_PROJECTION",
  "schemaVersion": 1,
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "activeSemanticVersion": 7,
  "analyzerVersion": "KW_V1",
  "tokenKeyVersion": 1,
  "currentTermCount": 84,
  "currentTermPageCount": 1,
  "fallbackRequired": false,
  "projectionDigest": "base64url-keyed-digest",
  "status": "READY",
  "builtAt": "2026-08-30T21:00:00.000+09:00"
}
```

Term PageはSemantic Versionごとに最大100件のTerm Descriptorを持つ。Page-level Attributeとして`semanticVersion`、`analyzerVersion`、`tokenKeyVersion`およびPage番号を持ち、各Descriptorは`termDigest`、`termKind`および`weightCode`だけを保持する。Normalized Termや本文を保持しない。新VersionのPageを別KeyへStageするため、Worker失敗時にActive VersionのPageを上書きしない。旧VersionのPageは、そのVersionがHistorical Revisionになった後の削除・Key Rotation・Repairにも使用する。

`activeSemanticVersion`は最後にProjection化したCreate / Semantic Update後のMemory Versionである。Confirmation-only Updateでは進めない。Root `version`が進んでもCurrent ContentとSemantic Fieldsが変わらなければ、既存ProjectionをCurrentとして利用できる。

Document ProjectionはCanonical ContentだけからProviderなしで再生成可能にする。AI由来のSubject / Attribute / Scopeを平文またはDigestとしてProjectionへ固定しない。これらはQuery-side Hintとして同じAnalyzerへ通し、本文由来Postingとの一致Signalに使用する。

Projection Metadataは16 KiB、Term Pageは16 KiB以下とする。TTLを設定せず、Memory削除時に全Pageを削除する。

### 36.6 Current and Historical Posting Items

Current Posting:

```text
pk = MEMORY_SEARCH#TERM#<keyVersion>#CURRENT#<termDigest>
sk = MEMORY#<memoryId>
```

Historical Posting:

```text
pk = MEMORY_SEARCH#TERM#<keyVersion>#HISTORICAL#<termDigest>
sk = MEMORY#<memoryId>#REVISION#<20-digit revisionNumber>
```

Postingは最大1 KiBで、次だけを保持する。

- `itemType = MEMORY_SEARCH_POSTING`
- `schemaVersion = 1`
- `memoryId`
- Currentは`semanticVersion`、Historicalは`revisionNumber`
- `analyzerVersion`
- `tokenKeyVersion`
- `termKind`
- `weightCode`

Current Postingは管理検索、現在回答およびRelation判定だけでQueryする。Historical Postingは`ANSWER_HISTORICAL`でのみQueryし、Revision Repositoryから該当Revisionを取得して最終検証する。管理画面検索へRevisionを混入させない。

Fallback Postingは次の固定Partitionを使用する。

```text
MEMORY_SEARCH#FALLBACK#KW_V1#CURRENT
MEMORY_SEARCH#FALLBACK#KW_V1#HISTORICAL
```

Fallback候補はAuthoritative Content / Revision Snapshotを取得し、Request Scope内で`KW_V1`を再適用して実一致を確認する。Fallback Itemにも本文やTermを保存しない。

### 36.7 Durable Projection Intent

CreateおよびContent、Category、SensitivityまたはStateのSemantic Mutation Transactionへ、Content-freeなProjection Intentを一件追加する。

```text
pk = MEMORY_SEARCH#PENDING
sk = AVAILABLE#<13-digit nextAttemptEpochMillis>#<13-digit createdEpochMillis>#<memoryId>#<20-digit targetVersion>
```

| Attribute | Rule |
|---|---|
| `itemType` | `MEMORY_SEARCH_INTENT` |
| `operation` | `UPSERT`または`DELETE` |
| `intentId` | Retryで変えないUUID v4 |
| `memoryId` | Canonical ID |
| `targetVersion` | Mutation後Version |
| `createdAt` | Intent作成時刻 |
| `attemptCount` / `nextAttemptAt` | Bounded Retry用。Sort Keyと一致 |
| Content fields | 保存禁止 |

Projection Intent Itemは最大4 KiBとする。Retry理由、Exception本文、Query、TermまたはMemory本文をItemへ追加せず、詳細診断はContent-freeなReason CodeとTrace IDへ限定する。

CreateはRoot / Revision / 6 Management Index / Intentの9 Action、Semantic UpdateはRoot / Revision / 旧新12 Management Index / Intentの15 Actionとなり、Accepted 25 Action上限内に収まる。Confirmation-only UpdateはProjection Intentを作成しない。

Intent作成までをAuthoritative Transactionへ含める。Intent作成に失敗したMutationを成功扱いにしない。一方、Commit後のProjection Worker失敗によってAuthoritative MemoryをRollbackまたは削除しない。

Workerは`AVAILABLE#000...`から現在時刻までをKey ConditionでQueryする。Retry延期時は同じ`intentId`を維持し、旧Intent Deleteと新しい`nextAttemptAt` KeyへのPutを小さいTransactionで行う。Attribute FilterだけでRetry時刻を選別しない。

### 36.8 Projection Worker and Activation

WorkerはIntentを古い順に取得し、Memory単位の新しいIntentへ安全に収束させる。Intentが複数Version分溜まっている場合、Current Projectionは最新VersionへCoalesceできるが、存在するMemoryRevisionのSemantic Versionを飛ばしてHistorical検索不能にしてはならない。

1. PersonalMemoryをStrong Readする。
2. Intentが削除済みまたは新VersionにSupersedeされていないか確認する。
3. 既存Active Semantic VersionがTargetより古い場合、そのVersioned Term PageからHistorical Postingを確定する。
4. Activeと最新Targetの間に未処理のSemantic Revisionがある場合、Confirmation-onlyの欠番を除き、各After SnapshotからVersioned Term PageとHistorical Postingを順に補完する。Activeがまだない場合は最新Targetより前の全Semantic Revisionを対象とする。最新Target VersionはHistorical化しない。
5. 最新Current Contentから`KW_V1` Termを決定的に生成する。
6. 新Current Postingと新Semantic Version Term Pageを最大25 Writeずつ作成する。
7. Fallback要否を反映する。
8. 全Write成功確認後、Projection MetadataのActive Version更新とWorkerがClaimしたTarget Intent一件の削除を小さいTransactionでAtomicに行う。
9. `targetVersion <= activeSemanticVersion`となったSuperseded Intentを最大25件ずつ削除する。すべて消えるまでManagement Search Readinessは未完了のままとする。
10. 不要になった旧Current Postingを削除する。Historical削除用Versioned Term Pageは保持する。

Derived Posting / PageのBulk Put / Deleteは最大25 Operationの`BatchWriteItem`を使用できる。`UnprocessedItems`へExponential Backoff + Jitterを適用し、初回 + 最大5 Retryとする。すべて処理できるまでActive Versionを切り替えない。

Crash後のRetryでも同じAnalyzer、Key Version、Memory IDおよびTarget Versionから同じPosting Keyを再生成する。途中生成物はSearch最終検証で信用せず、Repair Workerが回収可能にする。

### 36.9 Search Readiness by Purpose

Pending Intentの存在をPrimary Key Query・Strong Readで確認する。

| Purpose | Pending / Projection Failure時 |
|---|---|
| Management Search | Candidate取得前後の両方で確認し、一件でも未完了なら部分結果を`200`で返さずService Error |
| Answer Current / Historical | Ready Projectionだけを利用し、Memoryなしまたは検証済み部分候補で継続。Traceを`PARTIAL` / `UNAVAILABLE` |
| Relation Decision | 検証済みRelation候補は評価可能だが、検索不完全時に`NONE` / `CREATE_NEW`を確定しない |

Management Search開始後にConcurrent Mutationが発生した場合は、候補取得後およびSearch Sessionを`READY`へ公開する直前の再確認で検出する。MutationがProjection反映まで完了してPendingが消えた場合は最新Projectionを利用できる。Readiness判定へDynamoDB TTLの物理削除を使用しない。

### 36.10 Query Analysis and Candidate Bounds

Management Search Query、Current User ContentまたはRelation Candidateへ`KW_V1`を適用し、最大32 Query Termを生成する。Entity / Attribute / Scope Hintがある場合はQuery-side Term Sourceとして優先し、その後に完全Lexical、3-gram、2-gram、1-gram、最初の出現位置の順で選ぶ。Hint自体をSearch Projectionへ永続化しない。

| Purpose | Posting Evaluated | Unique Candidate | Final Candidate Pool |
|---|---:|---:|---:|
| Management Search Session Build | 最大5,000 | 最大500 | 最大500 |
| Answer Current / Historical | 最大1,000 | 最大200 | 最大40 |
| Relation Decision | 最大1,000 | 最大200 | 最大40 |
| Fallback Bucket | Current / Historical各最大100 | 上記へ含む | 上記へ含む |

TermごとのPostingをQueryし、`memoryId`または`memoryId + revisionNumber`で統合する。同じSourceのScoreを単純加算し続けず、Distinct Term Match、Term Kind、CoverageおよびPurpose別Signalへ正規化する。

Management Searchで5,000 Postingまたは500 Unique Candidateを超え、全一致集合を確定できない場合、上位だけを完全結果として返さずService Errorとする。Answerは安全なPartial / NoneへFallbackし、RelationはIncompleteとして`NONE`を禁止する。

### 36.11 Deterministic Keyword Rank

Management SearchのKeyword順位は、Provider固有Float Scoreではなく次のLexicographic Tupleで決める。

1. 最新ContentにCanonical Query全体が連続一致するか — Yes優先
2. Query-side Entity / Attribute / Scope Hint一致数 — 多い順
3. Distinct Query Term Coverage — 高い順。分数は整数のCross Multiplicationで比較
4. Term Kind Weight合計 — 高い順
5. `updatedAt` — 新しい順
6. `memoryId` — 昇順

Authoritative Hydration後に最新ContentへAnalyzerを再適用して1〜4を再計算する。Postingに残る旧Weightだけで最終順位を決めない。

Answer / Relation PortはこのKeyword Signalを返し、Application側でEntity、Intent、Scope、Temporal、Confirmation、SensitivityおよびRedundancyを追加評価する。Keyword RankをMemoryの真偽、保存可否、Security許可またはTool権限に使用しない。

### 36.12 Management Search Session and Cursor

Relevance順の複数Pageを安定して返すため、Management Searchだけ短命なBackend Sessionを使用する。

| Item | `pk` | `sk` |
|---|---|---|
| Session Metadata | `MEMORY_SEARCH#SESSION#<searchSessionId>` | `META` |
| Result Page | same | `RESULTS#<4-digit page>` |

Session Metadataは次を保持する。

- Random UUID v4 Session ID
- Keyed Query DigestとKeyed Filter Digest
- `limit`、Ordering Version、Analyzer Version
- Candidate Count / Result Count
- Status `READY`
- `createdAt`、Logical `expiresAt`
- DynamoDB TTL `expiresAtEpochSeconds`

同時Session上限は20個の固定Slotで競合なく制御する。

```text
pk = MEMORY_SEARCH#SESSION_SLOT
sk = SLOT#00 ... SLOT#19
```

候補集合と順位を上限内で確定した後、Session IDから決めた開始Slotを基準に最大20 Slotを順に試し、Slot不存在またはLogical期限切れの場合だけConditional TransactionでSlot取得と`BUILDING` Metadata作成を行う。SlotはSession IDとLogical期限だけを持ち、期限切れならTTL物理削除前でも再利用できる。Session Slot Itemは最大4 KiBとする。

Result Page書込み完了後にMetadataを`READY`へ条件付き更新する。Cursorは`READY`だけへ発行する。失敗・期限切れCleanupは同じSession IDで所有するSlotだけを解放し、別SessionのSlotを削除しない。空きSlotがなければ既存Sessionを強制削除せずService Errorとする。

Result Pageは順位順の`memoryId`、Projection Semantic Version、Content-free Rank Tupleだけを最大100件保持する。Memory本文、Query、Filter平文、Term、Digest入力またはRevision Snapshotを保存しない。

| Session Boundary | Limit |
|---|---:|
| Active Session | Single User最大20 |
| Result IDs | 一Session最大500 |
| Result Page Item | 最大64 KiB |
| Metadata Item | 最大8 KiB |
| Logical Lifetime | 15分 |

初回検索で全候補集合を上限内に確定・Hydrate・Rankできた場合だけSessionを`READY`にする。構築途中SessionをCursorへ公開しない。Session作成失敗や上限超過を部分的な`200`へ変換しない。

Search CursorはPurpose、Session ID、Result Offset、Query / Filter Digest、`limit`、Ordering Version、発行時刻およびSession期限へBindingする。次PageではResult IDを順に最新Authoritative MemoryへHydrateし、削除・変更・Filter / Query不一致を除外しながらPageを埋める。Session作成後に新しく一致したMemoryを途中追加せず、Refresh時に新Sessionを作る。

TTL物理削除前でもLogical期限を過ぎたSessionを使用しない。期限切れは`INVALID_CURSOR`とし、Session Itemを通常Backupへ含めない。Session Slot、MetadataおよびPageのCleanup / ReconciliationはTTL削除完了へ依存しない。

### 36.13 Stale Posting and Final Validation

Search Sourceの結果をそのまま利用せず、Purposeに応じて次を再確認する。

| Candidate | Final Source |
|---|---|
| Current Posting | 最新PersonalMemory、Current Projection Metadata、Reset Generation |
| Historical Posting | 対象MemoryRevision、PersonalMemory存在、Historical Intent |
| Management Session ID | 最新PersonalMemoryと現在のQuery / Filter再評価 |

Current Posting候補はProjection MetadataをStrong BatchGetし、`semanticVersion`が`activeSemanticVersion`と異なる場合はActive Current候補として使用しない。Root VersionだけがConfirmationにより進んでいても、Active Semantic VersionとContent再評価が一致すれば利用できる。

Stale / Orphan Postingは候補から除外してRepair対象とする。Projection由来ContentをAI ContextやAPI Responseへ返さず、必ずAuthoritative Content / Revision Snapshotを利用する。AI Provider呼出し直前にMemory ID、VersionおよびReset Generationを再確認する。

### 36.14 Delete, Repair and Rebuild

PersonalMemory削除では、Projection MetadataとTerm Pageから次を列挙する。

- Current Posting
- Historical Posting
- Fallback Posting
- Projection Metadata / Term Page
- Pending Intent
- Search Session内の対象参照はHydration時に無効化

Content-bearing Memory / Revisionと検索Copyの削除を確認できるまでDeletion Receiptを成功にしない。多数PostingはDelete Plan / Chunk / Reconciliationで処理し、一Transactionへ押し込まない。

RepairはIntent Retry、Stale Posting CleanupおよびMemory単位Rebuildを通常手段とする。Missing Projectionの全件発見またはAnalyzer / Key Version Migrationだけ、通常Runtimeと分離したBounded Maintenance Scanを許可する。Dry Run、最大件数、時間上限、Content-free差分および明示Applyを必須とする。

### 36.15 Eval Baseline and Semantic Promotion

初期Keyword Adapterは、匿名化・合成された最低300 Scenarioで評価する。

| Purpose | Minimum Scenario | Target |
|---|---:|---:|
| Answer Current / Historical | 100 | Required Memory Recall@40 ≥ 95% |
| Relation Decision | 100 | Related Memory Recall@40 ≥ 98% |
| Management Search | 100 | Expected Result Recall@100 ≥ 95% |
| Answer Context | 上記内 | Precision@8 ≥ 85% |
| Safety | 全Scenario | Deleted / Disallowed Sensitive / Non-historical Resolved混入 0件 |

日本語の表記揺れ、漢字 / かな / カナ、英数字混在、言い換え、同名別対象、否定、時点、Sensitive、Resolvedおよび削除を含める。Providerや実ユーザー本文をEval Artifactへ無断複製しない。

Test DesignでCPU、Memory件数、DynamoDB Local / AWS条件を固定するReference Performance Profile上の初期Target:

| Metric | Target |
|---|---:|
| Answer / Relation Candidate Generation p95 | 300 ms以下 |
| Management Search Session Build p95 | 1,000 ms以下 |
| Projection Pending Age p95 | 5秒以下 |
| Projection Pending Age Maximum | 30秒以下 |
| Candidate上限によるManagement Search Failure | Eval Requestの1%未満 |

次のいずれかが発生した場合、Analyzer調整、Index方式変更またはSemantic Searchを比較評価する。

- Recall / Precision Targetを調整後も連続2回の正式Evalで満たさない
- 1,000 Memory DatasetでLatencyまたはCandidate上限Failure Targetを満たさない
- Relation Recall不足に起因する重複・誤更新が再現可能な形で確認された
- Posting Storage / Write AmplificationがAuthoritative Memory運用を阻害する
- 日本語の言い換えがKeyword改善だけでは十分取得できない

Semantic Search追加を自動決定しない。同じDatasetでKeyword、SemanticおよびHybridを比較し、Recall、Precision、Safety、Latency、Cost、Privacy、削除整合性および再構築時間をReviewする。

外部Vector Store、Embedding Provider、新しいSearch Service、Sensitive Contentの外部送信または大きなCost / Operation変更を伴う場合はADRを必須とする。採用時はKeywordを即時削除せず、Dual-read Eval、Backfill、Delete Propagation、RollbackおよびProvider交換手順を先に定義する。

### 36.16 Failure and Observability

少なくとも次のContent-free Metricを保持する。

- Pending Intent count / age / retry
- Projection build duration / term count / fallback count
- Posting evaluated / unique candidate / final candidate count
- Current / Historical / Fallback source count
- Search Session create / expire / quota / limit failure
- Stale / orphan posting and repair result
- Purpose別Recall / Precision / latency Eval結果

Term、Digest、Query、Memory ID、本文、Sensitive属性またはSession ResultをMetric Label / 通常Logへ含めない。Search障害時、管理検索は部分結果を成功扱いせず、回答はMemoryなしで継続し、Relationは不完全な`NONE`を返さない。

### 36.17 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-045 | Phase 2初期SearchをDynamoDB Keyword Inverted IndexとしVector Storeを先行導入しない | Accepted |
| DB2-046 | Purpose別Search Portを分離したままAnalyzer / Posting Infrastructureを共有する | Accepted |
| DB2-047 | Versioned Analyzer `KW_V1`でNFKC、Case変換、LexicalおよびCJK N-gramを生成する | Accepted |
| DB2-048 | Document 512 / Query 32 Term上限とContent-free Fallback Postingを使用する | Accepted |
| DB2-049 | Search TermをVersion付きDomain-separated HMAC DigestでKey化し最大2 VersionでRotateする | Accepted |
| DB2-050 | Projection MetadataとTerm PageへDigest / Kindだけを保持しNormalized Contentを保存しない | Accepted |
| DB2-051 | CurrentとHistorical Postingを分離しHistoricalを明示的過去質問だけで利用する | Accepted |
| DB2-052 | Create / Semantic Update TransactionへContent-free Projection Intentを追加する | Accepted |
| DB2-053 | WorkerがPosting / Pageを段階生成し完了後だけActive Semantic Versionを切り替える | Accepted |
| DB2-054 | Management SearchでPendingを前後確認し不完全な部分結果を`200`にしない | Accepted |
| DB2-055 | Answer検索障害をPartial / UnavailableとしてMemoryなしでConversation継続可能にする | Accepted |
| DB2-056 | Relation検索不完全時に`NONE` / `CREATE_NEW`を確定しない | Accepted |
| DB2-057 | Purpose別Posting / Candidate / Fallback上限をSection 36.10へ固定する | Accepted |
| DB2-058 | Management Keyword順位をProvider非依存のDeterministic Tupleで決定する | Accepted |
| DB2-059 | Management Searchを最大500 ID・20 Active・15分のContent-free SessionでPage化する | Accepted |
| DB2-060 | Search Session Pageでも最新Authoritative MemoryをHydrateしQuery / Filterを再評価する | Accepted |
| DB2-061 | Active Semantic Version、Revision、Reset Generationを最終検証してStale Postingを除外する | Accepted |
| DB2-062 | Deletion成功前にCurrent / Historical / Fallback PostingとProjectionを削除確認する | Accepted |
| DB2-063 | Intent RetryとMemory単位Rebuildを通常Repair、全件Scanを分離Maintenanceに限定する | Accepted |
| DB2-064 | Projection / Posting / Session Item SizeとBatchWrite上限をSection 36へ固定する | Accepted |
| DB2-065 | 300 ScenarioのRecall / Precision / Safety / Latency Targetで初期Keyword Adapterを評価する | Accepted |
| DB2-066 | Semantic Searchを定量比較後にのみ採用し外部Service等を伴う場合はADRを必須とする | Accepted |

### 36.18 Approval and Next Design Unit

DB2-045〜DB2-066はAcceptedである。次の承認単位はSection 37のRe-registration Guard、Memory Reset Point、Deletion Receipt / Durable OperationおよびDelete-all ChunkのPersistence Schemaである。

### 36.19 Official DynamoDB References

- [BatchWriteItem API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchWriteItem.html)
- [Query API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Query.html)
- [BatchGetItem API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchGetItem.html)
- [DynamoDB TTL](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html)

---

## 37. Phase 2 Deletion Guard, Reset Point and Durable Operation

### 37.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted |
| Scope | Re-registration Guard、Memory Reset Point、Deletion Plan、Target / Chunk、Deletion Receipt、Idempotency、Recovery |
| Depends On | DB2-001〜DB2-066、MD-052〜MD-066、API2-050〜API2-056 / API2-085〜API2-104 Accepted |
| User-visible Source | `api-design.md` Sections 34、37、38、42 |

本Sectionは、削除本文を残さず自動再登録を防ぎ、個別・複数・全削除を同じRecovery Modelへ収束させるPhysical Schemaを定義する。DynamoDBの一Transactionへ最大10,000 Targetを押し込まず、Target単位のAtomic CutoverとContent-freeなPlan / Chunk / Receiptを組み合わせる。

### 37.2 Key Namespace

| Item | `pk` | `sk` |
|---|---|---|
| Guard Metadata | `MEMORY_GUARD#ID#<guardId>` | `META` |
| Guard Fingerprint Locator | `MEMORY_GUARD#FP#<keyVersion>#<fingerprintDigest>` | `GUARD#<guardId>` |
| Reset / Boundary Singleton | `MEMORY_SETTINGS#PRIMARY` | `RESET_POINT` |
| Mutation Fence | `MEMORY_OPERATION#MUTATION_FENCE` | `ACTIVE` |
| Deletion Plan Metadata | `MEMORY_DELETION_PLAN#<deletionPlanId>` | `META` |
| Fixed Target | same | `TARGET#<8-digit ordinal>` |
| Execution Chunk | same | `CHUNK#<4-digit chunkNumber>` |
| Confirmation State | same | `CONFIRMATION#<confirmationId>` |
| Deletion Operation Metadata | `MEMORY_OPERATION#DELETE#<operationId>` | `META` |
| Target Receipt | same | `TARGET#<8-digit ordinal>` |
| Idempotency Binding | `MEMORY_OPERATION#IDEMPOTENCY#<operationType>#<keyDigest>` | `META` |

APIへDynamoDB Key、Guard ID、Operation ID、Chunk番号またはFingerprint Digestを公開しない。Plan IDとMemory IDだけを既存API Contractに従って公開する。

### 37.3 Guard Fingerprint Boundary

削除実行直前に、Authoritative Contentから`GuardFingerprintPort`が1〜8個のCanonical Semantic Keyを生成する。Persistence AdapterはSemantic Keyを保存せず、専用Secret Keyで次を計算し、入力を破棄する。

```text
fingerprintDigest = HMAC-SHA-256(
    guardKey,
    "alice-memory-reregistration-guard\0"
    + semanticKeyVersion + "\0"
    + canonicalSemanticKey
)
```

Digestは32 Byte全体をBase64url Encodingし、切り詰めない。Guard用KeyをSearch Term、Cursor、Device CredentialまたはIdempotency Keyと共有しない。`semanticKeyVersion`は意味Key生成規則、`keyVersion`はSecret Key世代を表し、混同しない。

Semantic Keyの語彙・意味抽出規則はAI / Security DesignでVersion管理する。PersistenceはVersion、件数、長さおよびDigest形式を検証するが、本文を再正規化して独自Fingerprintを作らない。

### 37.4 Guard Metadata and Locator

Guard Metadata例:

```json
{
  "pk": "MEMORY_GUARD#ID#7aa8e54f-52f2-4eac-a23a-123456789abc",
  "sk": "META",
  "itemType": "MEMORY_REREGISTRATION_GUARD",
  "schemaVersion": 1,
  "guardId": "7aa8e54f-52f2-4eac-a23a-123456789abc",
  "status": "ACTIVE",
  "semanticKeyVersion": "GUARD_SEMANTIC_V1",
  "keyVersions": [1],
  "fingerprints": [
    { "keyVersion": 1, "digest": "base64url-full-digest" }
  ],
  "categoryHint": "ENGINEERING",
  "deletionScope": "SELECTED",
  "resetGeneration": 3,
  "deletionOperationId": "16fb7fa5-b248-44a9-b937-23456789abcd",
  "deletedAt": "2026-08-30T22:30:00.000+09:00"
}
```

Guard Metadataは最大8 KiB、Locatorは最大2 KiB、Fingerprintは重複排除後に一Guard最大8個とする。`status`は`ACTIVE`または明示再登録中の`REINTRODUCTION_PENDING`だけを使用する。`deletionScope`は`INDIVIDUAL`、`SELECTED`、`FILTERED`または`ALL`である。Locatorは`guardId`、`semanticKeyVersion`、`keyVersion`および`statusHint = ACTIVE`だけを持ち、本文、要約、Embedding、Category以外の属性、元Memory IDまたはConversation Referenceを保持しない。`statusHint`はAuthoritativeではなく、候補判定時にGuard MetadataをStrong Readして`ACTIVE`または`REINTRODUCTION_PENDING`を確認する。

GuardへTTLを設定しない。明示的再登録または管理上の明示削除まで保持し、通常Backupへ含めない。Guard Keyを失って既存Digestを検証できない場合、Digestだけから新Key VersionへMigrationしない。対象Versionを`UNAVAILABLE`としてAutomatic CaptureをFail Closedにし、Restoreでは既存のDeletion-history警告Contractを使用する。

### 37.5 Guard Lookup and Explicit Re-registration

Automatic Candidateは、保持中のGuard Key / Semantic Key Versionごとに最大8 Digestを生成し、Locator PartitionをStrong Queryする。取得したGuard MetadataをStrong BatchGetし、一件でも`ACTIVE`一致があれば自動保存しない。結果上限100 Guardを超えた場合、上位だけを「一致なし」と扱わず`GUARD_CHECK_INCOMPLETE`としてFail Closedにする。

明示再登録ではGuardを黙って無視せず、Content-freeなRe-registration Operationを作成する。Mutation FenceのGuard Mutation Modeを取得し、同じCandidateに一致するGuardを再確認してOperationへBindingする。Guard Metadataを`REINTRODUCTION_PENDING`へ遷移してもAutomatic Captureでは引き続き一致扱いとする。新しいMemory作成とOperationの`COMMITTED`遷移をAtomic Transactionにし、その後Locator / Metadataを最大25 Deleteずつ除去する。Crash時はOperationを基準にCommit完了またはGuard復旧へ収束させ、Guardだけ解除された状態を成功扱いにしない。

このGuard Mutation ModeはSection 37.8のMutation Fenceと同じ固定Slotを使用し、Delete Workerと明示再登録を直列化する。端末やPresentation種別に依存せず、自然言語、iOS、将来Desktop UIおよびRestoreは同じBoundaryを使用する。

### 37.6 Reset Point and Deletion Boundary

`RESET_POINT`は初回Feature State初期化時に一度だけ作成する。

```json
{
  "pk": "MEMORY_SETTINGS#PRIMARY",
  "sk": "RESET_POINT",
  "itemType": "MEMORY_RESET_POINT",
  "schemaVersion": 1,
  "generation": 3,
  "resetAt": "2026-08-20T18:00:00.000+09:00",
  "sourceMessageCutoff": "opaque-conversation-boundary",
  "resetOperationId": "0b4dcbe2-ef9d-47ec-a340-3456789abcde",
  "deletionBoundaryVersion": 142,
  "pendingReset": null,
  "updatedAt": "2026-08-30T22:30:00.000+09:00"
}
```

| Field | Rule |
|---|---|
| `generation` | 完全なDelete-allごとに1増加。減少・再利用禁止 |
| `sourceMessageCutoff` | Conversation本文を含まないOpaque Boundary。以前または同一Sourceの再抽出を禁止 |
| `deletionBoundaryVersion` | PersonalMemory Create / Update / Delete / Reintroduction / Restoreごとに単調増加 |
| `pendingReset` | Delete-all実行中だけOperation ID、次Generation、Cutoff、開始時刻を保持 |

通常Memory Mutation TransactionはMutation Fence不存在をConditionCheckし、同じTransactionで`deletionBoundaryVersion`を進める。Preferences変更とReadだけでは進めない。Deletion Planは作成時のBoundary VersionへBindingするため、別端末、Background WorkerまたはRestoreによる変更をExecute前に検出できる。

Section 37採用後の通常CreateはSection 36の9 ActionへFence ConditionとBoundary Updateを加え最大11 Action、Semantic Updateは15 Actionへ同2 Actionを加え最大17 Actionとなる。Confirmation-only Update、明示再登録およびRestoreも同じFence / Boundary Ruleを使用し、25 Action上限を超える場合はMutation全体を開始しない。

Delete-allでは全Target検証前に短い`VALIDATING` Mutation Fenceを取得し、新規Memory Mutationを止める。検証不一致ならContentを削除せずFenceを解放してPlanを`INVALIDATED`にする。検証成功後だけ`pendingReset`とExecution Intentを確立する。全Targetの`DELETED`確認後にだけ`generation`、Cutoffおよび`resetAt`を確定し、`pendingReset`を消去する。

### 37.7 Deletion Plan Schema and Build

Plan MetadataはScope、Status、Target Count、不変`targetSetVersion`、Target Set Digest、Plan Version、作成時Boundary / Reset Generation、Review期限、Result CountおよびLogical Retentionを保持する。Memory本文、Filter平文、Query、FingerprintまたはRevision Snapshotを保持しない。Structured FilterはKeyed Canonical Digestだけを保持する。

Fixed Target Item:

```json
{
  "pk": "MEMORY_DELETION_PLAN#9b3c0a4c-6d5e-4f70-a182-3456789abcde",
  "sk": "TARGET#00000042",
  "itemType": "MEMORY_DELETION_TARGET",
  "ordinal": 42,
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "expectedVersion": 3,
  "targetSetVersion": 1,
  "status": "READY",
  "reasonCode": null
}
```

Targetは`memoryId ASC`の固定順とし、最大10,000件、一Item一Target、最大4 KiBとする。Plan作成は`BUILDING` Metadataを条件付き作成し、TargetとChunkを最大25 PutずつStageする。全件数、順序、DigestおよびExpected Versionを検証した後だけMetadataを`READY`へ公開する。途中PlanをAPIへ返さず、失敗時はIdempotency BindingからCleanupまたは同じBuildへResumeする。

Targetの`memoryId`、`expectedVersion`、`ordinal`および`targetSetVersion`は公開後に変更しない。`status` / `reasonCode`はTarget Receiptから更新できる表示用Projectionであり、RecoveryのSource of Truthにはしない。Plan APIはTarget ItemとOperation側Receiptを照合して最新Statusを返す。

`SELECTED`は最大100 ID、`FILTERED`と`ALL`はAccepted Management Indexから候補を取得し、Authoritative RootをStrong HydrationしてTargetを固定する。通常Runtime Scanを使用しない。Target 0件ではPlan Itemを残さず、Idempotency Resultへ`NO_TARGETS`だけを記録できる。

### 37.8 Confirmation and Atomic Execution Start

Confirmation ItemはToken原文ではなく専用KeyによるToken Digest、Plan ID、Plan Version、Target Set Digest、Reset Generation、Deletion Boundary Version、発行時刻、15分以内かつReview期限までの`confirmationExpiresAt`および使用状態を保持する。最大4 KiBとし、Logical期限を必ず確認する。

Executeは次の順序で開始する。

1. Plan、Idempotency BindingおよびConfirmationをStrong Readする。
2. Mutation Fenceを条件付き取得し、他のDeletion / Reintroduction Executionと競合させない。
3. Fence取得後に全Target ID / Expected Version、Target Set Digest、Reset GenerationおよびDeletion Boundary Versionを再検証する。
4. 不一致ならContentを削除せずPlanを`INVALIDATED`、Tokenを無効化しFenceを解放する。
5. 一致した場合だけPlanを`EXECUTING`へ更新し、Deletion Operation Metadataを作成し、Tokenを消費する。
6. `ALL`では同じStart Transactionで`pendingReset`を設定する。

`202 Accepted`はStep 5または6までDurableに成功した後だけ返せる。Start時にReset Generation、Deletion Boundary Version、`currentMemoryCount`、Target Set Version / Digestおよび同一Operationで固定する`finalizationId`をPlan、Deletion OperationおよびMutation FenceへBindingする。

Mutation FenceはOwner Operation ID、Mode（`VALIDATING`、`EXECUTING`、`RECONCILING`、`FINALIZING`）、Lease Epoch、Baseline、Lease期限およびHeartbeatだけを持つ最大4 KiB Itemとし、本文やTarget一覧を保持しない。Lease期限切れは解放条件ではない。Operation / Plan / ReceiptをStrong照合し、同じOperation IDの新Lease Epochへ条件付きTakeoverする。

### 37.9 Per-target Atomic Cutover

Individual Deleteは外部向けDeletion Planを作成しないが、一TargetのDeletion Operation Metadata、Mutation FenceおよびTarget Receiptを使用する。複数・全削除と同じCutover / Cleanup / Reconciliation Ruleへ収束させ、同期Response待機時間内に`DELETED`まで到達した場合だけ`200 MemoryDeletionReceipt`を返す。

Workerは各TargetのRootをStrong Readし、削除直前にGuard FingerprintをMemory上で生成する。次を25 Action以下の一Transactionで行う。

1. Mutation Fenceが同じOperation ID / Lease EpochであることをConditionCheckする。
2. PersonalMemory RootをExpected Version条件付きで削除する。
3. ALLと5 Filter DimensionのManagement Index 6 Itemを削除する。
4. Guard Metadataを未使用`guardId`条件で作成する。
5. 最大8 Guard Fingerprint Locatorを作成する。
6. Target Receiptを`CUTOVER_COMMITTED`へ条件付き作成または更新する。

最大Action数は18であり、Aliceの通常Transaction上限25以内に収まる。Root削除とGuard確立の片方だけをCommitしない。Transaction結果が不明な場合はRoot、Guard MetadataおよびReceiptをStrong Readし、同じOperation IDで照合する。新しいGuard IDを生成して再実行しない。

このCutover後、Rootが存在しないため管理一覧・回答・Relation・Search Sessionの最終Hydrationから除外される。Revision、Search Posting、Projection Page、Pending Intent、Cache等のCleanupが未完了であるため、まだユーザー向け`DELETED`ではない。

### 37.10 Cleanup and Deletion Receipt

Target Receiptは次のLifecycleを使用する。

| Status | Meaning |
|---|---|
| `READY` | Cutover前。Plan Target存在かつReceipt未作成でも同義 |
| `CUTOVER_COMMITTED` | Root削除とGuard確立がAtomicに完了 |
| `CLEANUP_PENDING` | Revision / Search / Cache / Pending削除中 |
| `DELETED` | 全Content-bearing Copy削除とGuard有効性を確認済み |
| `FAILED` | Rootを削除せず最終失敗を確認、または安全なRecovery不能 |
| `UNKNOWN` | 最終状態を確定できずReconciliation必須 |

Cleanup対象はMemoryRevision、Current / Historical / Fallback Posting、Projection Metadata / Page、Projection Intent、Relation Review / Candidate、Answer / Search Cacheおよび対象を再書込み得るPending Workである。DynamoDB ItemはQueryまたはGuard / Projection Manifestから列挙し、最大25 Deleteずつ処理する。`BatchWriteItem`の部分成功をTarget成功へ変換しない。

最後にRoot不存在、全Revision不存在、Projection / Posting / Pending不存在、Guard Metadata `ACTIVE`およびLocator完全性、Cache / Worker無効化を確認してReceiptを`DELETED`へ進める。`MemoryDeletionReceipt` APIはこの状態だけから生成する。Receiptへ本文、Category、Sensitivity、Fingerprint、Revision数、検索語または内部Retry回数を含めない。

### 37.11 Chunk Lease and Recovery

Target 25件を一Execution Chunkとし、10,000 Targetで最大400 Chunkとする。Chunk ItemはOrdinal範囲、Status、Attempt Count、Lease Owner、Lease Epoch、Lease期限、Deleted / Failed / Unknown CountおよびReason Codeを保持する。

| Chunk Status | Rule |
|---|---|
| `PENDING` | 未取得 |
| `LEASED` | 一Workerが処理中 |
| `COMPLETED` | 範囲内TargetがすべてTerminal |
| `RECONCILING` | Receipt / Root / Guard / Cleanupを再照合中 |

最大4 Chunkを並列処理できるが、同一Targetを複数Workerが処理しない。WorkerはChunk Leaseを条件付き取得し、TargetごとのAtomic Cutover / Cleanupを行う。Crash後はReceiptをSource of Truthとして再開し、Chunk CountはChunkを一度だけTerminal化するTransactionでPlan Aggregateへ加算する。

`UNKNOWN`は自動的に`FAILED`へ変換しない。Root不存在かつReceipt / Guardが不整合なら本文が戻らないようReadから除外したままReconciliationする。RootがExpected Versionで残りCutover未成立を確認できれば`NOT_DELETED`へ、Root不存在、同Operation Receipt、Guard `ACTIVE`および全Content-bearing Copy不存在を確認できれば`DELETED`へ確定する。別Version、Receipt不一致、Guardだけ存在またはRepository取得不能は`UNKNOWN`のままとする。

Target Unknownが一件でも残る間はFenceを`RECONCILING`で維持し、通常Memory Mutation、別Deletion Execute、Restore Execute、Relation ResolveおよびBackup Exportを禁止する。Lease期限、Retry上限またはTTLを理由にFenceを解放しない。全Targetが`DELETED`または`NOT_DELETED`へ確定した場合だけFinalizationへ進む。

### 37.12 Finalization, Partial Result and Reset

Plan Resultは次を満たす。

```text
requestedCount = targetCount
requestedCount = deletedCount + failedCount + unknownCount
COMPLETED => deletedCount = requestedCount
PARTIAL   => deletedCount > 0 AND failedCount > 0 AND unknownCount = 0
FAILED    => failedCount = requestedCount
UNKNOWN   => unknownCount > 0
```

`UNKNOWN`は非ExecutableなRecovery状態であり、同じOperationのReconciliationによって`COMPLETED`、`PARTIAL`または`FAILED`へ収束できる。Unknownが一件でも残る間はCount、BoundaryおよびPlan Terminal Resultを確定せず、Pending ResetとFenceを維持する。

`unknownCount = 0`をStrong確認した後、次を一つのFinalization Transactionで確定する。

1. Reset Pointの`currentMemoryCount`を`baselineCount - deletedCount`へ更新する。
2. `deletedCount > 0`の場合だけ`deletionBoundaryVersion`を一度進める。
3. PlanとDeletion Operationを`COMPLETED`、`PARTIAL`または`FAILED`へTerminal化する。
4. Execute Idempotency Bindingを同じ結果へ更新する。
5. `ALL`の全件DeletedだけReset Generationを進め、Partial / FailedではPending Resetを取消する。
6. 同じOperation ID / Lease Epoch / `FINALIZING`条件でMutation Fenceを削除する。

Transaction全体がCommitしない限りFenceを解放しない。Finalization応答が不明な場合は同じ`finalizationId`でReset Point、Plan、Operation、BindingおよびFenceをStrong Reconcileし、Countを二重減算しない。

### 37.13 Idempotency, Retention and TTL

Plan Create、Individual Delete、Plan ExecuteおよびExplicit Re-registrationは別`operationType`でIdempotency Bindingを持つ。BindingはKey原文ではなくDomain-separated HMAC Digest、Canonical Request Digest、Operation ID、Status、Result Reference、作成時刻およびLogical期限だけを保持する。同じKey・同じRequestを同じOperationへ収束させ、異なるRequestへの再利用をConflictとする。

| Item | Logical Retention / TTL Rule |
|---|---|
| Active Guard / Reset Point | TTLなし |
| Ready Plan | 固定4時間Review期限 |
| Confirmation | 最大15分かつReview期限まで |
| Terminal Plan / Target / Receipt | Terminal化後最低24時間 |
| Idempotency Binding | 最終結果確定後最低24時間 |
| Mutation Fence / Lease | TTLを所有権判定に使用しない |

Logical期限を過ぎたPlan / TokenをDynamoDB TTL物理削除前でも使用しない。TTLはCleanup補助であり、Quota解放、Fence Takeover、Token失効またはDeletion成功の根拠にしない。

### 37.14 Item and Operation Limits

| Boundary | Limit |
|---|---:|
| Guard Fingerprints | 一Guard最大8 |
| Guard Lookup | 一Candidate最大100 Guard |
| Deletion Plan Target | 最大10,000 |
| Fixed Target Item | 最大4 KiB |
| Plan Metadata | 最大16 KiB |
| Chunk / Receipt / Confirmation / Fence | 各最大4 KiB |
| Target per Chunk | 25 |
| Concurrent Chunk | 最大4 |
| Per-target Cutover Transaction | 最大18 Action / 128 KiB |
| Plan Build / Cleanup BatchWrite | 一回最大25 Operation、初回 + 最大5 Retry |

上限超過時にGuard、Target、ReceiptまたはCleanup対象を黙って省略しない。Guard Fingerprintが8件を超える場合はFingerprint生成を失敗させ、本文削除を開始しない。Target上限超過ではPlanを作成しない。

### 37.15 Failure, Security and Observability

通常Log / Metric LabelへMemory本文、Target ID一覧、Fingerprint、Guard ID、Confirmation Token / Digest、Idempotency Key / Digest、Conversation Cutoff、Cursor、Search TermまたはException Messageを記録しない。

Content-free Metric:

- Plan build / execute / reconcile count and duration
- Target / Chunk status count
- Cutover / Cleanup retry and age
- Guard lookup complete / matched / incomplete count
- Mutation Fence acquisition / takeover / hold duration
- Reset pending age / generation advance result
- Receipt unknown / reconciliation result

削除後のRoot不存在だけを成功Metricへ使用せず、Receipt `DELETED`とGuard / Cleanup検証完了を基準にする。Guard Key未取得、Reset Singleton欠落、Fence所有権不明またはReceipt不整合ではFail Closedにする。

### 37.16 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-067 | Guard MetadataとFingerprint Locatorを分離しDigestからStrong Queryできる構造にする | Accepted |
| DB2-068 | Guard Fingerprintを専用KeyのDomain-separated HMAC-SHA-256完全Digestとする | Accepted |
| DB2-069 | Semantic Key 1〜8件だけをDigest化し本文・要約・Embedding・元Memory IDをGuardへ保持しない | Accepted |
| DB2-070 | GuardへTTLを設定せず明示再登録まで保持し、Key不明時は自動保存をFail Closedにする | Accepted |
| DB2-071 | Guard Locator取得後にMetadataをStrong Readし`ACTIVE` / `REINTRODUCTION_PENDING`だけを再登録防止へ使用する | Accepted |
| DB2-072 | 明示再登録をGuard Mutation LeaseとDurable Operationで処理しGuard解除だけの中間成功を禁止する | Accepted |
| DB2-073 | Reset PointへGeneration、Conversation CutoffおよびDeletion Boundary Versionを保持する | Accepted |
| DB2-074 | PersonalMemory MutationでFence不存在確認とDeletion Boundary更新を同一Transactionにする | Accepted |
| DB2-075 | Delete-all検証中からMutation Fenceを取得し成功時だけPending Resetを確立する | Accepted |
| DB2-076 | Deletion PlanをMetadata、一Target一Itemおよび25 Target Chunkで構成する | Accepted |
| DB2-077 | Plan Targetを最大10,000件・Memory ID順・Expected Version付きの不変集合にする | Accepted |
| DB2-078 | Plan Buildを非公開`BUILDING`へStageし全件検証後だけ`READY`へ公開する | Accepted |
| DB2-079 | Confirmation Token原文を保存せずPlan / Target / Reset / BoundaryへDigest Bindingする | Accepted |
| DB2-080 | Execute前にMutation Fence取得後の全Target再検証を行い不一致時は無削除でInvalidatedにする | Accepted |
| DB2-081 | Root削除、6 Management Index削除、Guard確立およびReceipt Cutoverを最大18 ActionでAtomic化する | Accepted |
| DB2-082 | Root Cutover後もRevision / Search / Cache / Pending Cleanup完了まで`DELETED`を返さない | Accepted |
| DB2-083 | Target ReceiptをCutover、Cleanup、Deleted、Failed、Unknownへ分けてRecovery Sourceとする | Accepted |
| DB2-084 | 25 Target Chunk、最大4並列、Lease Epochおよび条件付きTerminal集計を使用する | Accepted |
| DB2-085 | Unknownを失敗へ変換せずRoot / Guard / Receipt / CleanupをStrong Reconciliationする | Accepted |
| DB2-086 | Delete-all Resetを全Target Deleted時だけ確定しPartial / UnknownではGenerationを進めない | Accepted |
| DB2-087 | Plan Create、Individual Delete、Execute、Re-registrationのIdempotency Boundaryを分離する | Accepted |
| DB2-088 | Guard / Resetを無期限、Plan / Receipt / Idempotencyを最低24時間の論理Retentionとする | Accepted |
| DB2-089 | TTL物理削除をFence、Token、Quota、SuccessまたはTakeover判定へ使用しない | Accepted |
| DB2-090 | Guard / Plan / Chunk / ReceiptのItem Size、Batch、Actionおよび並列上限をSection 37.14へ固定する | Accepted |

### 37.17 Approval and Next Design Unit

DB2-067〜DB2-090はAcceptedである。次の承認単位はSection 38のMemory Usage Trace、Mutation Idempotency共通SchemaおよびAllowed Device CredentialのPersistence Schemaである。

### 37.18 Official DynamoDB References

- [DynamoDB transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html)
- [DynamoDB transaction constraints](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Constraints.html)
- [BatchWriteItem API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchWriteItem.html)
- [DynamoDB TTL](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html)


---

## 38. Phase 2 Usage Trace, Mutation Idempotency and Allowed Device Credential Design

### 38.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted |
| Scope | Memory Usage Trace、Phase 2 Mutation Idempotency、Allowed Device Credential |
| Depends On | DB2-001〜DB2-090、API2-007 / API2-044 / API2-070〜API2-084 / API2-101 / API2-142〜API2-160 / API2-163〜API2-168 / API2-197〜API2-205 / API2-206〜API2-220 Accepted |
| Security Source | `security-design.md` Section 31 |

本Sectionは、回答時Memory利用の透明性、Mutation Retryの一意性およびPrivate LAN端末認証を、Personal Memory本文から分離したPhysical Schemaとして定義する。三つのDataは同じMemory Tableを使用するが、Key、Secret、Retention、BackupおよびFailure Boundaryを共有しない。

### 38.2 Key Map

| Item | `pk` | `sk` |
|---|---|---|
| Usage Trace | `MEMORY_TRACE#MESSAGE#<assistantMessageId>` | `META` |
| Trace Failure Audit | same | `FAILURE` |
| Idempotency Binding | `MEMORY_OPERATION#IDEMPOTENCY#<operationType>#<keyDigest>` | `META` |
| Device Control | `DEVICE_CREDENTIAL#CONTROL` | `STATE` |
| Device Metadata | `DEVICE_CREDENTIAL#ID#<deviceId>` | `META` |
| Token Digest Locator | `DEVICE_CREDENTIAL#TOKEN#<digestKeyVersion>#<tokenDigest>` | `ACTIVE` |
| Active Administration Directory | `DEVICE_CREDENTIAL#DIRECTORY#ACTIVE` | `DEVICE#<deviceId>` |

Usage Trace ID、Idempotency Digest、Token Digest、Device Credential KeyおよびDynamoDB KeyをPublic APIへ公開しない。API上の`messageId`、`memoryId`およびPlan / Review IDだけを既存Contractどおり返す。

### 38.3 Memory Usage Trace

#### 38.3.1 Trace Schema

正常に保存済みのAssistant Message一件につき、最大一件のTrace Metadataを保持する。

```json
{
  "pk": "MEMORY_TRACE#MESSAGE#5e6f7081-92a3-4bcd-8ef0-123456789abc",
  "sk": "META",
  "itemType": "MEMORY_USAGE_TRACE",
  "schemaVersion": 1,
  "assistantMessageId": "5e6f7081-92a3-4bcd-8ef0-123456789abc",
  "generationRequestId": "request-opaque-id",
  "availability": "AVAILABLE",
  "retrievalStatus": "COMPLETE",
  "reasonCode": null,
  "retrievalModeCodes": ["STRUCTURED", "KEYWORD"],
  "candidateCount": 5,
  "includedCount": 2,
  "estimatedTokens": 120,
  "utf8Bytes": 540,
  "resetGenerationAtUse": 3,
  "includedMemories": [
    {
      "sequence": 0,
      "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
      "memoryVersionAtUse": 3,
      "temporalRole": "CURRENT",
      "relevanceReasonCodes": ["ENGINEERING_RELEVANCE"],
      "modelReferenceStatus": "REPORTED"
    }
  ],
  "traceDigest": "base64url-keyed-digest",
  "traceDigestKeyVersion": 1,
  "recordedAt": "2026-08-30T22:45:00.000+09:00"
}
```

Traceは最大16 KiBとし、`includedMemories`はContext投入順で0〜8件、一EntryのReason Codeは1〜4件とする。`retrievalModeCodes`は`STRUCTURED`、`KEYWORD`、`HISTORICAL`等のProvider非依存Stable Codeを最大4件保持する。Memory未使用時も`includedMemories = []`の最小Traceを作成する。`candidateCount`は0〜40の件数だけを保持し、取得候補のID一覧を保存しない。`generationRequestId`は回答生成時の内部相関にだけ使用し、API ResponseやMetric Labelへ出さない。

TraceへMemory本文、当時のMemory Snapshot、User Query、Conversation本文、Prompt、Provider Raw Response、Score、Embedding、Guard、SecretまたはChain of Thoughtを保存しない。`traceDigest`は専用KeyによるCanonical Trace FieldのHMACであり、本文DigestやMetric Labelとして使用しない。

#### 38.3.2 Write Ordering and Idempotency

Assistant Messageの保存成功または結果照合後にだけTraceを書き込む。Conversation TableとMemory TableをCross-table Transactionで結合せず、Trace失敗によって正常なAssistant MessageをRollbackまたは失敗扱いにしない。

Trace Putは`attribute_not_exists(pk)`で一意作成する。同じAssistant MessageのRetryで既存`traceDigestKeyVersion`を使った`traceDigest`が一致すれば成功済みとして扱い、異なるDigestなら既存Traceを上書きせず`TRACE_INTEGRITY_CONFLICT`としてContent-free Failure Auditを残す。旧Integrity Keyを利用できない場合も既存Traceを上書きせずTrace Retryを失敗させる。RetryごとにIncluded順序、Model Reference StatusまたはRecorded Atを再生成しない。

Trace保存に失敗した場合、Request Scope内で初回 + 最大3 Retryを行える。最終失敗後もConversation Responseは維持し、Transparency APIはMessage存在を確認したうえで`NOT_RECORDED`を返す。Trace Repository自体のRead可否を判断できない障害は`NOT_RECORDED`へ変換せず`SERVICE_UNAVAILABLE`とする。

#### 38.3.3 Read and Current Resource Hydration

Transparency APIは次の順序で処理する。

1. Conversation RepositoryからMessageを取得し、保存済みAssistant Messageであることを確認する。
2. `assistantMessageId`からTraceをStrong Getする。
3. Traceがあれば最大8 `memoryId`をMemory RepositoryからStrong BatchGetする。
4. 現在Versionと比較して`UNCHANGED`、`UPDATED`、`DELETED`または`UNAVAILABLE`を組み立てる。

Traceに保存したVersionとReason Codeは回答当時の記録として変更しない。Memory削除後もTrace Itemを更新・削除せず、現在Root不存在から`DELETED / currentMemory = null`を返す。削除済み本文やRevisionをTraceから復元しない。

#### 38.3.4 Retention, Message Deletion and Backup

Usage Traceは対応Assistant Messageと同じ論理Retentionを持つ。Conversation MessageにTTLがない間はTraceにもTTLを設定しない。将来Messageへ明示Retention / Deleteを導入する場合、同じMessage IDのTrace Delete IntentをConversation削除Operationへ含め、TTL物理削除の時刻一致へ依存しない。

Messageが存在しない場合はTraceだけが残っていてもAPIへ公開しない。Orphan TraceはMaintenance Reconciliationで削除できるが、通常Runtime Scanを追加しない。Usage Trace、Failure AuditおよびTrace DigestをMemory Backup / Restore、User ExportまたはSearch Projectionへ含めない。

### 38.4 Common Mutation Idempotency

#### 38.4.1 Digest and Operation Type

UUID Idempotency-Key原文をKeyまたはAttributeへ保存せず、専用Secretで次を計算する。

```text
keyDigest = HMAC-SHA-256(
    idempotencyKeySecret,
    "alice-memory-idempotency-key\0" + operationType + "\0" + canonicalUuid
)

requestDigest = HMAC-SHA-256(
    idempotencyRequestSecret,
    "alice-memory-idempotency-request\0" + operationType + "\0" + canonicalRequestBytes
)
```

二つのSecretをCursor、Guard、SearchまたはDevice Credential Keyと共有しない。Digestは32 Byte全体をBase64url Encodingする。Canonical RequestへPassphrase、Confirmation Token原文、Authorization Header、Secret検出値またはTemporary File Pathを含めない。Restore Plan CreateはArchive Digestと安全なOption、ExecuteはPlan / Version / Action Set Bindingを使用する。

Stable `operationType`:

```text
MEMORY_REGISTER
DELETION_PLAN_CREATE
DELETION_PLAN_EXECUTE
RESTORE_PLAN_CREATE
RESTORE_PLAN_EXECUTE
RESTORE_PLAN_CANCEL
RELATION_REVIEW_RESOLVE
MEMORY_DELETE_NATURAL
```

`MEMORY_DELETE_NATURAL`はClient Idempotency-Keyを要求せず、Canonical Memory IDとExpected VersionからDomain-separated Operation Identity Digestを作る。Operation TypeごとにNamespaceを分離するが、Clientは別の論理操作へ同じUUIDを意図的に再利用しない。

#### 38.4.2 Binding Schema and Lifecycle

```json
{
  "pk": "MEMORY_OPERATION#IDEMPOTENCY#MEMORY_REGISTER#base64url-key-digest",
  "sk": "META",
  "itemType": "MEMORY_IDEMPOTENCY_BINDING",
  "schemaVersion": 1,
  "operationType": "MEMORY_REGISTER",
  "requestDigest": "base64url-request-digest",
  "operationId": "a26efc5d-42df-49af-8d40-123456789abc",
  "status": "PROCESSING",
  "leaseEpoch": 1,
  "leaseOwner": "process-instance-id",
  "leaseExpiresAt": "2026-08-30T22:50:00.000+09:00",
  "resultType": null,
  "resultReference": null,
  "safeOutcomeCode": null,
  "createdAt": "2026-08-30T22:45:00.000+09:00",
  "updatedAt": "2026-08-30T22:45:00.000+09:00",
  "expiresAtEpochSeconds": null
}
```

Statusは`PROCESSING`、`COMPLETED`、`FAILED`または`UNKNOWN`とする。Bindingは最大8 KiBで、Resource本文、HTTP Response Body、Memory Candidate、Archive、Passphrase、Confirmation Token、Relation理由またはException Messageを保持しない。`resultReference`はMemory / Plan / Review / Operation IDとVersion等、Replayに必要なContent-free Locatorだけを保持する。

#### 38.4.3 Reservation, Replay and Completion

Application / Presentationの形式、Size、Unknown Fieldおよび必須Header Validationを通過した後にだけ`PROCESSING` Bindingを`attribute_not_exists(pk)`で予約する。並行する同一Keyの片方だけがOwnerとなり、他方はStrong Getして次を判定する。

| Existing Binding | Handling |
|---|---|
| Same `requestDigest`, `PROCESSING` | 現在Operationを返すか`REQUEST_IN_PROGRESS` |
| Same `requestDigest`, Terminal | Result Referenceから現在Resource / Safe ResultをReplay |
| Different `requestDigest` | `IDEMPOTENCY_KEY_CONFLICT` |
| Read / Outcome Unknown | 新Operationを開始せずReconciliation |

短いMutationはBinding Owner / Lease Epoch条件付きで、Authoritative Mutation、Revision / Index / Boundary更新とBinding Terminal更新を同じTransactionへ含める。Memory RegisterはAccepted最大11 ActionへBinding Updateを加えて最大12 Actionとなる。Relation Reviewが必要な場合はReview作成とBinding `COMPLETED / REVIEW_REQUIRED`をAtomicにする。

Deletion、RestoreまたはRelation Resolve等の長いOperationは、BindingをOperation ResourceへAtomicに接続し、以後のLease / Chunk / Cleanupを各ResourceのLifecycleで管理する。Binding Lease期限だけを根拠に処理を最初から再実行せず、Result Resource、Expected Version、Guard、ResetまたはReceiptをStrong ReadしてTakeover / Unknownを決定する。

#### 38.4.4 Terminal Result and Retention

`COMPLETED`はResource ID / Version、Plan / Review ID、Result Statusおよび安全なOutcome Codeを保持できる。`FAILED`はStable Problem CodeとRetry方針だけを保持し、入力値やExceptionを保持しない。`UNKNOWN`は同じOperation IDでReconciliationし、新しいMemory ID、Revision、Plan、ArchiveまたはReviewを生成しない。

| Binding | Logical Retention |
|---|---|
| Completed Memory Register | Memory存在中。削除後最低24時間 |
| Active Plan / Review / Restore | ResourceがActiveな間 |
| Terminal Plan / Review / Restore | Terminal化後最低24時間 |
| Terminal Failure | 最低24時間 |
| Processing / Unknown | 結果確定または明示RecoveryまでTTLなし |

Logical期限後にだけ`expiresAtEpochSeconds`を設定できる。TTL物理削除前でも期限切れBindingを新Operationの成功証拠に使用しない一方、同じKeyを自動再利用させないClient Contractを維持する。`requestId`、Retry回数、Access TokenまたはRaw Idempotency-KeyをBindingへ保存しない。

### 38.5 Allowed Device Credential

#### 38.5.1 Control, Metadata and Locator

Device Control:

```json
{
  "pk": "DEVICE_CREDENTIAL#CONTROL",
  "sk": "STATE",
  "itemType": "DEVICE_CREDENTIAL_CONTROL",
  "schemaVersion": 1,
  "activeCount": 2,
  "maxActiveCount": 20,
  "activeDigestKeyVersions": [1],
  "activeCountByKeyVersion": { "1": 2 },
  "version": 5,
  "updatedAt": "2026-08-30T22:45:00.000+09:00"
}
```

Device Metadata:

```json
{
  "pk": "DEVICE_CREDENTIAL#ID#0af33fa7-3a4d-47d1-9f20-123456789abc",
  "sk": "META",
  "itemType": "ALLOWED_DEVICE_CREDENTIAL",
  "schemaVersion": 1,
  "deviceId": "0af33fa7-3a4d-47d1-9f20-123456789abc",
  "displayName": "Personal MacBook Air",
  "status": "ACTIVE",
  "tokenDigest": "base64url-full-digest",
  "digestKeyVersion": 1,
  "credentialVersion": 1,
  "createdAt": "2026-08-30T22:45:00.000+09:00",
  "rotatedAt": null,
  "revokedAt": null
}
```

Token Locatorは`deviceId`、`credentialVersion`、`digestKeyVersion`および`statusHint = ACTIVE`だけを保持する。Active Administration DirectoryはDevice ID、表示名および作成日時だけを保持し、Credential VersionやToken Digestを複製しない。表示名はNFKC後1〜80 Unicode ScalarかつUTF-8 256 Byte以下、Control Character禁止とするが、Authentication根拠に使用しない。

#### 38.5.2 Token Digest and Authentication Read

TokenはCSPRNGで最低256 bit、Opaque Base64url、最大128 ASCII Characterとする。Backendは原文を永続化せず、専用Keyで次を計算する。

```text
tokenDigest = HMAC-SHA-256(
    deviceTokenDigestKey,
    "alice-allowed-device-token\0" + digestKeyVersion + "\0" + rawTokenBytes
)
```

PRIVATE_LAN RequestはBodyを読む前にToken形式を検証し、最大2個のActive Digest Key VersionでDigestを計算する。LocatorをStrong Getし、該当Device MetadataをStrong Getして、`ACTIVE`、Credential Version、Key VersionおよびDigestをConstant-time比較する。0件または複数件、不一致、Revoke済み、Repository障害およびKey不足はすべてFail Closedにする。

Authentication Hot Pathで`lastUsedAt`を書き込まない。必要な成功 / 失敗AuditはDevice IDを許可された内部Audit Fieldとして別Boundaryへ記録し、IP Address、User-AgentまたはDisplay Nameを認証根拠にしない。

#### 38.5.3 Issue, Rotate and Revoke Transactions

Issue Transaction:

1. 未使用Device IDのMetadata Put
2. 未使用Token Digest Locator Put
3. Administration Directory Put
4. Controlの`activeCount < 20`条件付きCount / Key Version Count更新

Token原文はTransaction成功とStrong Read照合後に一度だけ表示する。結果不明時に成功表示せず、Process Memoryに残るTokenからDigestを再計算してMetadata / Locatorを照合する。原文を復元できない場合は当該CredentialをRevokeして新しいTokenを発行する。

Rotateは新Digest Locator Put、Device MetadataのExpected Credential Version付き更新、旧Locator DeleteおよびControl Key Version Count更新を一Transactionで行う。新Token成功後に旧Tokenを別処理で無効化するのではなく、同じCommit Pointで切り替える。新原文はCommit確認後だけ一度表示する。

RevokeはDevice Metadataを`REVOKED`へ更新してToken Digestを除去し、LocatorとActive Directory Itemを削除し、Control Active Countを減らす4 Action Transactionとする。Revoke成功後に開始するRequestは旧Tokenで認証できない。既に認証済みのIn-flight Requestを巻き戻す保証はしない。

#### 38.5.4 Digest Key Rotation and Startup Gate

Token Digestは原文なしに別Keyで再計算できないため、Key RotationはCredential再発行として行う。旧Key Versionを使用するActive Credentialが0件になるまで旧Keyを削除しない。Phase 2は同時Active Key Versionを最大2個とし、ControlのVersion別CountとMetadata / Locatorを照合してから旧Versionを無効化する。

PRIVATE_LAN_SECURE起動時はControlとActive DirectoryをStrong Readし、最大20件のMetadata / LocatorをStrong BatchGetして、Active Count、Version別Count、設定済みDigest KeyおよびRepository可用性を検証する。Active Credentialが参照するKey不足、Count不整合、Duplicate LocatorまたはControl欠落ではHTTPS Listenerを公開せず起動をFail Closedにする。LOOPBACK_ONLYをRequest単位のFallbackとして併設しない。

#### 38.5.5 Limit, Retention and Non-export

| Boundary | Limit / Retention |
|---|---|
| Active Allowed Device | 最大20件 |
| Active Digest Key Version | 最大2件 |
| Device Metadata / Control | 各最大4 KiB |
| Token Locator / Active Directory | 各最大2 KiB |
| Active Credential | TTLなし |
| Revoked Metadata | 最低30日保持後にLogical Expiration / TTL可 |

Device IDを再利用しない。Revoked Credentialを再有効化せず、新しいTokenが必要ならRotateまたは新規Issueを行う。Device Credential、Control、Directory、Token DigestおよびAuthentication AuditをMemory Backup、Restore、User Export、Search、AI ContextまたはConversation Historyへ含めない。

Authorization Header、Token原文 / Digest、Digest Key、Device IDとNetwork Addressの組合せまたは認証失敗詳細を通常Log / Metric Labelへ出力しない。Local Administration AuditはOperation、Device ID、結果Category、時刻およびRequest ID等のContent-free Metadataに限定する。

### 38.6 Limits, Failure and Observability

| Item | Limit |
|---|---:|
| Usage Trace | 16 KiB、Included Memory最大8 |
| Trace Failure Audit | 2 KiB |
| Idempotency Binding | 8 KiB |
| Short Operation Processing Lease | 最大5分。無期限Heartbeat禁止 |
| Device Issue / Rotate / Revoke | 最大4 Transaction Action |

少なくとも次のContent-free Metricを保持する。

- Usage Trace recorded / not-recorded / integrity-conflict / latency
- Idempotency reserve / replay / conflict / processing-age / unknown
- Device authentication success / failure category
- Credential issue / rotate / revoke result
- Active Device Count / Digest Key Version Count

ID、Digest、Token、Memory Version配列、Reason Code配列、Operation ReferenceまたはDisplay NameをMetric Labelへ使用しない。

### 38.7 Proposed Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-091 | Usage TraceをAssistant Message ID単位のMemory Table ItemとしてStrong Getする | Accepted |
| DB2-092 | Traceへ最大8件のMemory ID / Version / Reason Codeを保持し本文Snapshotや候補ID一覧を保存しない | Accepted |
| DB2-093 | Memory未使用回答にも最小Traceを作成しAPIのNOT_RECORDEDと区別する | Accepted |
| DB2-094 | Assistant Message保存確認後にTraceを書き、Trace失敗でConversation成功をRollbackしない | Accepted |
| DB2-095 | TraceをConditional PutとKeyed Digestで一意化し異なるRetry Payloadで上書きしない | Accepted |
| DB2-096 | Trace Read不能をNOT_RECORDEDへ偽装せず、Trace不存在だけを未記録として扱う | Accepted |
| DB2-097 | TraceをMessageと同RetentionにしMemory削除後も本文なしで保持しBackupへ含めない | Accepted |
| DB2-098 | Transparency取得時に最新MemoryをStrong HydrationしてUpdated / Deleted / Unavailableを判定する | Accepted |
| DB2-099 | Idempotency KeyとCanonical Requestを専用KeyのDomain-separated HMAC完全Digestにする | Accepted |
| DB2-100 | Phase 2のIdempotent Operation TypeをSection 38.4.1のStable Codeへ固定する | Accepted |
| DB2-101 | BindingをConditional Reserveし同一Keyの並行Ownerを一件に限定する | Accepted |
| DB2-102 | Binding状態をProcessing / Completed / Failed / Unknownに分けResult本文を保存しない | Accepted |
| DB2-103 | 同じKeyと異なるRequest DigestをConflictとし、同じRequestは同じResourceへReplayする | Accepted |
| DB2-104 | Short MutationのAuthoritative WriteとBinding Terminal更新を同一Transactionにする | Accepted |
| DB2-105 | Lease期限切れだけで再実行せずOperation ResourceをStrong Reconciliationする | Accepted |
| DB2-106 | Validation拒否をBindingへ記録せずApplication開始後のReplay対象結果だけを保持する | Accepted |
| DB2-107 | Completed RegisterをResource存続中、その他Terminal Bindingを最低24時間保持する | Accepted |
| DB2-108 | Individual DeleteをMemory IDとExpected Version由来のNatural Operation Identityで照合する | Accepted |
| DB2-109 | Device Metadata、Token Locator、DirectoryおよびControlを分離する | Accepted |
| DB2-110 | Device Tokenを専用KeyのDomain-separated HMAC-SHA-256完全Digestとして保存する | Accepted |
| DB2-111 | LocatorとMetadataをStrong ReadしStatus / Version / DigestをConstant-time検証する | Accepted |
| DB2-112 | Device IssueをMetadata / Locator / Directory / Countの4 Action Transactionにする | Accepted |
| DB2-113 | Rotateで新旧LocatorとMetadataをAtomicに切替えCommit後だけ新Tokenを表示する | Accepted |
| DB2-114 | RevokeでLocator削除、Metadata無効化、DirectoryおよびCount更新をAtomicに行う | Accepted |
| DB2-115 | Digest Key RotationをCredential再発行で行い旧Version利用0件までKeyを保持する | Accepted |
| DB2-116 | Active Device最大20件、Active Digest Key Version最大2件をControlで条件付き制御する | Accepted |
| DB2-117 | PRIVATE_LAN_SECURE起動時にControl / Key / Repository不整合をFail Closedにする | Accepted |
| DB2-118 | Active CredentialへTTLを設定せずRevoked Metadataを最低30日保持する | Accepted |
| DB2-119 | Device CredentialをBackup / Restore / Export / AI Contextへ含めない | Accepted |
| DB2-120 | Usage Trace、IdempotencyおよびDevice Item Size / Lease / Transaction上限をSection 38.6へ固定する | Accepted |

### 38.8 Approval and Next Design Unit

DB2-091〜DB2-120はAcceptedである。次の承認単位はSection 39のRestore Plan / Reservation、Relation ReviewおよびBackup Export SnapshotのPersistence Schemaである。

### 38.9 Official DynamoDB References

- [DynamoDB read consistency](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadConsistency.html)
- [DynamoDB condition expressions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ConditionExpressions.html)
- [DynamoDB transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html)
- [DynamoDB TTL](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html)
- [DynamoDB error retries and exponential backoff](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Programming.Errors.html)

---

## 39. Phase 2 Restore Plan, Relation Review and Backup Export Snapshot Design

### 39.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted |
| Scope | Restore Reservation / Plan / Action / Result、Relation Review / Resolution、Stable Backup Export Snapshot |
| Access Patterns | P2-DB-AP-012、P2-DB-AP-013、P2-DB-AP-015 |
| Depends On | DB2-001〜DB2-120、MD-086〜MD-133、API2-139〜API2-220 |
| Implementation | Not Started |

本Sectionは、最大10,000件を扱うBackup / Restoreと短命Relation Reviewを、DynamoDBの400 KiB Item上限およびTransaction上限へ押し込まず実行するPhysical Schemaを定義する。Archive Byte、Passphrase、復号済みPayload、Memory本文およびTemporary PathをDynamoDBへ保存しない。

### 39.2 Key Map and Data Classification

| Logical Item | `pk` | `sk` | Data Class |
|---|---|---|---|
| Restore Global Control | `MEMORY_RESTORE_PLAN#CONTROL` | `STATE` | Content-free Control |
| Inspection Lease | `MEMORY_RESTORE_PLAN#LEASE#<operationId>` | `META` | Content-free Lease |
| Restore Plan | `MEMORY_RESTORE_PLAN#<restorePlanId>` | `META` | Content-free Metadata |
| Restore Action | same Plan PK | `ACTION#<10-digit ordinal>` | Content-free Binding |
| Resolution Delta | same Plan PK | `RESOLUTION#<20-digit planVersion>` | Content-free User Selection |
| Restore Action Result | same Plan PK | `RESULT#<10-digit ordinal>` | Content-free Result |
| Restore Execute Intent | `MEMORY_OPERATION#RESTORE#<operationId>` | `META` | Durable Intent |
| Relation Global Control | `MEMORY_RELATION_REVIEW#CONTROL` | `STATE` | Content-free Control |
| Relation Review | `MEMORY_RELATION_REVIEW#<relationReviewId>` | `META` | Metadata + Ciphertext Locator |
| Relation Target | same Review PK | `TARGET#<3-digit ordinal>` | Expected Version + Encrypted Preview |
| Relation Result | same Review PK | `RESULT` | Content-free Result |
| Backup Crypto Control | `MEMORY_OPERATION#BACKUP_CRYPTO#CONTROL` | `STATE` | Content-free Lease |
| Export Snapshot | `MEMORY_OPERATION#BACKUP_EXPORT#<exportOperationId>` | `META` | Content-free Metadata |
| Export Snapshot Page | same Export PK | `SOURCE#<4-digit page>` | Memory ID / Version only |

UUID、OrdinalおよびPage番号はAdapterでCanonical ValidationしてからKeyへ使用する。APIはPK / SK、Lease Owner、Artifact Handle、Ciphertext、DigestまたはInternal Operation IDを返さない。

### 39.3 Restore Global Control and Inspection Lease

Restore Global ControlはSingle User全体のActive SlotとTemporary Byte Reservationの唯一のAuthorityとする。

```json
{
  "pk": "MEMORY_RESTORE_PLAN#CONTROL",
  "sk": "STATE",
  "itemType": "MEMORY_RESTORE_CONTROL",
  "schemaVersion": 1,
  "slotState": "INSPECTING",
  "ownerType": "INSPECTION_LEASE",
  "ownerId": "4cb5b766-e03c-4f2a-8219-123456789abc",
  "reservedWorkingBytes": 805306368,
  "retainedBytes": 0,
  "cleanupState": "NONE",
  "controlVersion": 18,
  "updatedAt": "2026-08-31T20:00:00.000+09:00"
}
```

`slotState`は`FREE`、`INSPECTING`、`PLAN_ACTIVE`または`CLEANUP_PENDING`とする。`FREE`だけが新しいInspectionを取得できる。Control Itemを削除して空きを表現せず、新規環境ではSetup / Migrationが`FREE`で作成する。Control欠落・不正はRestoreをFail Closedにする。

Multipart Body受理前のReservation Transaction:

1. Mutation / Recovery Fence不存在をCondition Checkする。
2. Controlを`slotState = FREE`かつExpected `controlVersion`条件で`INSPECTING`へ更新し、固定805,306,368 Byteを予約する。
3. Backup Crypto Controlを`FREE`から`ACTIVE / RESTORE_INSPECT`へ条件付き更新する。
4. 未使用Operation IDのInspection LeaseをPutする。
5. Section 38の`RESTORE_PLAN_CREATE` Idempotency Bindingを同じLeaseへ接続する。

5 Actionを一TransactionでCommitし、Restore ControlまたはCrypto Controlだけを保持した待機を禁止する。Condition競合またはTransaction結果不明時は同じOperation ID / Bindingで両ControlとLeaseをStrong Reconcileし、新Operationを作成しない。

Inspection Leaseは開始時刻、固定30分期限、Lease Epoch、Process Instance、Reserved Bytes、Manifest ID、Cleanup Stateおよび`RESERVED / CRYPTO_RUNNING / ANALYZING / PLAN_ACTIVE / CLEANUP_PENDING` Phaseだけを保持する。Archive Digestは検証完了後のPlan Bindingにだけ保持し、Passphrase、Archive Byte、Client Filename、Temporary PathまたはMemory本文をLeaseへ保存しない。30分期限をHeartbeatで延長しない。

KDF / AEAD完了後、Derived KeyとCrypto Working Bufferの破棄を確認してから、Leaseを`ANALYZING`へ進めCrypto Controlを`FREE`へ戻す一Transactionを実行する。Crypto結果不明またはMaterial Cleanup未完了では両Controlを`CLEANUP_PENDING`に維持する。

Inspection成功時は、全Action ItemとPlan Metadataを`PREPARING`として先に作成し、最後のCommit Transactionで次を行う。

1. 全Action数とAction Set Digestが一致するPlan Metadataを`NEEDS_REVIEW`または`READY`へ遷移する。
2. Control OwnerをLeaseからPlanへ置換し、`reservedWorkingBytes = 0`、実測`retainedBytes <= 570425344`へ置換する。
3. Inspection LeaseをTerminalへ遷移する。
4. Create Idempotency BindingをPlan ID / VersionへTerminal化する。

途中で作成された`PREPARING` ItemはAPIへ公開しない。Commitに到達しないItemはManifestに基づくStartup / Sweeper Cleanup対象とする。

### 39.4 Restore Plan Metadata and Action Set

Restore Plan Metadataは最大32 KiBとし、次を保持する。

```json
{
  "pk": "MEMORY_RESTORE_PLAN#da5f75f9-a5f3-4f29-a183-3456789abcde",
  "sk": "META",
  "itemType": "MEMORY_RESTORE_PLAN",
  "schemaVersion": 1,
  "restorePlanId": "da5f75f9-a5f3-4f29-a183-3456789abcde",
  "status": "NEEDS_REVIEW",
  "strategy": "MERGE_SAFE",
  "archiveSchemaVersion": 1,
  "archiveDigest": "base64url-sha256",
  "artifactManifestId": "opaque-random-id",
  "retainedBytes": 268500000,
  "actionCount": 24,
  "actionSetVersion": "opaque-immutable-version",
  "actionSetDigest": "base64url-keyed-digest",
  "planVersion": 1,
  "latestResolutionVersion": 0,
  "selectionGeneration": 0,
  "preferencesExpectedVersion": 4,
  "preferencesChoice": "KEEP_CURRENT",
  "resetGeneration": 3,
  "deletionBoundaryVersion": 142,
  "deletionHistoryAssessment": "AVAILABLE",
  "reintroductionConfirmed": false,
  "deletionHistoryWarningAcknowledged": null,
  "createdAt": "2026-08-31T20:05:00.000+09:00",
  "reviewExpiresAt": "2026-09-01T00:05:00.000+09:00",
  "expiresAtEpochSeconds": null
}
```

`archiveDigest`はAPIへ返さず通常Logへ出さない。`artifactManifestId`はPermission制限されたRestore Directory内のBackend生成Manifestを照合するOpaque IDであり、Pathではない。Plan Keyまたは復号KeyをDynamoDBへ保存しない。Process RestartでKeyを失った場合はPlanを`INVALIDATED`へ遷移してCleanupする。

Action ItemはArchive順のOrdinalごとに最大8 KiBとし、`recordId`、Proposed Action、初期Selection、Target Memory ID / Expected Version、Related Memory ID最大10件、Guard / Reset判定、安全なReason CodeおよびSealed Artifact内Record Locatorだけを保持する。Archive Memory本文、Category、Preview本文、Secret判定値またはAI理由は保持しない。Plan GETではSealed Artifactを復号してAction ItemのBindingと一致するRecordだけをHydrateする。

Plan作成後にAction集合、順序、Target Expected Versionまたは`actionSetVersion`を変更しない。不一致はPlan全体を`INVALIDATED`とする。Action PageはPlan PartitionへのKey Condition Queryで取得し、最大100件、2 MiB API上限およびCursorの`actionSetVersion` Bindingを適用する。

### 39.5 Atomic Restore Resolution and Selection Compaction

一回のPATCHは最大100 Resolutionを一つの`RESOLUTION#<planVersion>` Delta Itemへまとめ、Plan Metadata更新と2 Action TransactionでAtomic化する。Deltaは最大16 KiBとし、Record ID、Selection、Allowed Target IDおよび前SelectionからのSummary差分だけを保持する。Client本文、Archive本文またはPreviewを複製しない。

PATCH処理はPlan、対象Actionおよび現在有効なSelectionをStrong Readし、全入力を検証してから次を同一Transactionで行う。

1. Expected Plan Version条件でPlan Version、Summary、確認状態および最新Delta Versionを更新する。
2. 未使用Plan VersionのResolution DeltaをPutする。

Action Itemを100件直接更新しないため、DynamoDBの100 Action上限をAPIのResolution 100件上限と衝突させない。GET / Executeは不変Actionへ、現在`selectionGeneration`のSelection Snapshotとそれ以後のDeltaを順番に適用する。

Deltaが128件に達した場合は、全SelectionをRecord順100件単位の不変`SELECTION#<generation>#<page>`へ構築する。Plan Version不変を確認した短いTransactionで`selectionGeneration`と基準Delta Versionを切り替え、旧Snapshot / Deltaは参照不能を確認後にCleanupする。Compaction失敗時も旧GenerationをAuthorityとして継続し、部分的な新Generationを公開しない。

DB2-233〜DB2-243統合後は、96 Deltaで先行Compactionを開始し、Active Delta 128 Itemsまたは合計2,097,152 Bytes到達後の新PATCHをRetryable Backpressureで停止する。Selection GenerationはAuthoritativeとBuilding / Retiringの最大2世代とし、PlanへCompaction ID / Epoch / State / Source Boundaryを保持する。GET / Confirm / Executeは最大228 Items、8,650,752 Bytes以内で完全なSelectionを再構築できる場合だけ継続し、不整合時に旧Selectionを黙示利用しない。

### 39.6 Confirmation, Execute Intent and Per-action Result

Confirmation Token原文は永続化しない。Token Digest ItemはPlan ID、Plan Version、Action Set Version、Selection Generation / Latest Delta、Preferences Choice / Version、Reset Generation、Deletion Boundary、確認状態および15分以内の期限へBindingする。Plan PATCH、Execute Intent確立、Invalidation、ExpiryまたはCancelで利用不能にする。

Execute PreflightはPlan、全Action / Selection、全Target Memory Expected Version、Preferences Version、Reset Generation / Deletion BoundaryおよびSealed ArtifactをStrong Read / 検証する。一つでも不一致ならMemory Mutation開始前に`INVALIDATED`へ遷移する。

Preflight成功時は次を一Transactionで行う。

1. PlanをExpected Version条件で`EXECUTING`へ遷移する。
2. 未使用Execute Operation IDのDurable IntentをPutする。
3. Confirmation Tokenを消費済みにする。
4. `RESTORE_PLAN_EXECUTE` Idempotency BindingをIntentへ接続する。

Actionごとに`RESULT#<ordinal>`を一意のOperation Identityとして使用する。`ADD`、`UPDATE_EXISTING`または`REINTRODUCE_DELETED`はAuthoritative Memory Mutation、Revision / Index / Projection / Guard / Boundary更新、Action ResultおよびPlan Summary Count更新を同じTransactionへ含める。25 ActionのAlice固有上限を超えるMutationは開始しない。`NO_CHANGE`と`SKIP`もResultとSummary更新を条件付きTransactionで確定する。

同一OrdinalのResultが既にTerminalならMutationを再実行しない。結果不明時はResult、Authoritative Memory、Revision、BoundaryおよびOperation BindingをStrong Readして`UNKNOWN`または確定結果へ収束する。Plan Summary Counterだけを全体成功の証拠にせず、全OrdinalのTerminal Resultを照合して`COMPLETED`、`NO_CHANGE`、`PARTIAL`、`FAILED`または`UNKNOWN`を確定する。Preferences適用はMemory Action後の独立した一回限りResultとして管理する。

### 39.7 Restore Cleanup and Retention

Expire、Cancel、Invalidation、Terminal化またはInspection Failure時はPlanを直ちに`FREE`へせず、Controlを`CLEANUP_PENDING`へ遷移する。Cleanup WorkerはArtifact Manifest、Plan Key / Token無効化、Pending Worker停止、Sealed Archive / Preview / Cache / Staging削除を確認し、最後の条件付きTransactionでControlを`FREE`へ戻す。

DynamoDB TTL、File更新時刻またはDirectoryの不存在だけをCleanup成功の証拠にしない。CleanupできないArtifactはRetained / Working Quotaへ計上し続ける。Content-bearing Action HydrationとArtifact LocatorはTerminal化時に削除し、Plan Metadata、Action Result、Safe SummaryおよびIdempotency Bindingは最低24時間保持する。`PROCESSING` / `UNKNOWN`は結果確定までTTLを設定しない。

### 39.8 Relation Review Control and Encrypted State

Relation Global Controlは`activeCount`、`maxActiveCount = 20`、`controlVersion`および更新時刻を保持する。Review作成は、Controlの`activeCount < 20`更新、Review Metadata、Target Item群および元Register / Update Idempotency Bindingの`REVIEW_REQUIRED`結果を一Transactionで行う。Related Target最大100件のためTransactionがAlice固有25 Actionを超える場合は、Targetを`PREPARING`で先行作成し、全件Digest検証後のCommit TransactionでControl、Review公開およびBindingをAtomicに確定する。未公開TargetはSweeper Cleanup対象とする。

Review Metadataは最大16 KiBで、Source Operation、Source Memory ID / Expected Version、Candidate Digest、Relation Type、Target Count / Set Digest、Allowed Resolution、Recommended Resolution、Reset Generation、Deletion Boundary、Review Version、Status、作成時刻および固定30分期限を保持する。

Canonical CandidateとMutation Previewは専用Relation State KeyでApplication-level AEAD暗号化する。DynamoDB ItemはCiphertext、Nonce、AAD Schema、Key VersionおよびCandidate / Target Bindingだけを保持し、暗号Keyは外部Secret Boundaryで管理する。Candidate Ciphertext Itemと各Target Itemは最大32 KiBとし、Target ItemはMemory ID / Expected Version、Allowed Mutation、Preview Ciphertextを持つ。暗号化済みであっても通常Backup / Export、Search、AI Context、LogまたはMetricへ含めない。

期限、Terminal化またはInvalidation時はCiphertext属性を削除してControl Active Countを一度だけ減らす。Content-free Metadata / Resultは最低24時間保持する。Logical ExpirationをRead / Resolve時に必ず判定し、TTL遅延中のCiphertextを再利用しない。

DB2-193〜DB2-201統合後は、Reviewへ`NOT_STARTED → CONTENT_CLEANUP_PENDING → CONTENT_CLEANED → RELEASED`の単調Cleanup State、Cleanup Epochおよび`activeSlotReleased`を保持する。Review PublishとContent-free Active Directory登録をAtomic化し、Startup / SweeperはDirectory QueryでOwnerを列挙する。Ciphertext利用不能をStrong確認した最後のTransactionだけがReview Release、Relation Global Count、Relation Key Count / Reference、Directory削除およびCleanup Resultを一括確定し、Retryによる二重減算を禁止する。

### 39.9 Relation Resolve Intent and Result

ResolveはReview、Source / Target Memory、Reset / GuardおよびIdempotency BindingをStrong ReadしてPreflightする。`If-Match`、Allowed Resolution / Target、全Expected Versionおよび新しいRelation不存在を確認後、Reviewを`PROCESSING`へ遷移し、Resolve Intentと`RELATION_REVIEW_RESOLVE` Bindingを同じTransactionへ確立する。

`UPDATE_TARGET`または`ADD_AS_NEW`は固定Previewだけを使用し、Authoritative Mutation、Revision / Index / Projection / Boundary更新、Review Result、Review Terminal化およびBinding Terminal更新をAlice固有25 Action以内の一Transactionで行う。`SKIP`はReview / Result / BindingだけをAtomicにTerminal化する。結果不明時はReview ResultとAuthoritative ResourceをStrong Reconciliationし、新しいMemory IDを発行し直さない。

Terminal Resultは`CREATED`、`UPDATED`、`SKIPPED`、`FAILED`または`UNKNOWN`とResource Locator / Versionだけを保持する。Candidate本文、Preview、Relation ScoreまたはAI理由をResult / Bindingへ保存しない。Source / Target Version、GuardまたはRelation集合が変わった場合はMutationせず`INVALIDATED`へ遷移する。

### 39.10 Stable Backup Export Snapshot

ExportはIdempotency-Keyを要求せず、Requestごとに新しいExport Operation IDとSnapshotを作る。`MEMORY_OPERATION#BACKUP_CRYPTO#CONTROL`は`FREE / ACTIVE / CLEANUP_PENDING`を持ち、Export / InspectのKDF同時実行をSingle User全体で1件に制限する。Owner、Operation Type、Lease Epoch、固定期限および開始時刻だけを保持し、Passphrase、Derived Key、Archive DigestまたはPayloadを保持しない。

Export開始はMutation Fence不存在、Crypto Control `FREE`、未使用Export OperationおよびSnapshot Metadata `BUILDING`を最大4 Actionの一TransactionでBindingする。Restore InspectionとのCrypto競合ではSnapshot PageやTemporary Archiveを作成しない。Cleanup完了後だけOperation Terminal、Snapshot Cleanup ResultおよびCrypto Control解放を一Transactionで確定する。

安定SnapshotのSource Boundaryは、Memory Reset Pointの`deletionBoundaryVersion`と`currentMemoryCount`、Memory Preferences Versionおよび管理`ALL` Indexから構築する。`currentMemoryCount`はCreate / Delete / Reintroduction / Restore時に既存Boundary Updateと同じActionで増減し、Updateでは値を維持する。負数、10,000超過、Reset Point欠落またはIndex件数不一致はExportをFail Closedにする。

Backup ExportはSnapshot `BUILDING`開始前、`SEALED`公開直前およびHTTP Response Commit直前の三時点でMutation / Recovery Fence不存在をStrong確認する。Fenceを検出した場合はTemporary Snapshot / ArchiveをCleanupし、Partial Archiveを返さずRetry可能な安全な結果へ収束する。

Snapshot作成順序:

1. Reset Point / Deletion Boundary、PreferencesおよびCrypto ControlをStrong Readする。
2. `ALL` IndexをConsistent Readで固定順Queryし、Memory ID / Versionを最大100件ずつSnapshot PageへConditional Putする。
3. 全Memory RootをStrong BatchGetし、Index Version、State、Count、Schema、Secret禁止およびExport Scopeを検証する。
4. Reset Point / Boundary、Preferences Versionおよび全Source Versionを再確認する。
5. Snapshot Metadataを`BUILDING`から`SEALED`へ条件付き遷移する。
6. SEALED Page順にRootを再Hydrateし、Expected Version一致を確認しながらBounded MemoryでPayloadを生成する。
7. 暗号化完了後、最終Boundary / Preferences / Countを再確認してから完成ArchiveをResponseへCommitする。

Snapshot PageはOrdinal、Memory IDおよびExpected Versionだけを最大100件、64 KiB以下で保持する。本文、Category、Capture Type、Sensitivity、日時、Preferences値、Archive IDまたはPassphraseを保存しない。Snapshot MetadataはSource Boundary、Preferences Expected Version、Count、Page Count、Snapshot Digest、Status、作成時刻および固定15分期限だけを保持する。

DB2-202〜DB2-211統合後は、`ALL` IndexからOrdinal連続・Memory ID一意・一ID一VersionのCanonical `(memoryId, version)`集合を作り、Domain-separated `sourceSetDigest`をSnapshot Metadataへ固定する。`currentMemoryCount`、Raw Entry、Unique ID、Snapshot EntryおよびStrong Hydrated Root Countをすべて一致させ、Seal前とResponse Commit直前に集合Digest、Boundary、Preferences VersionおよびFence不存在を再検証する。Index Repair / Promotion中またはMissing + Duplicate等の相殺不整合ではExportをFail Closedにする。

Mutationを検出した場合は最大1回だけ最初からBounded Retryできる。再度変化、Index不整合、Root欠落、Version不一致またはValidation違反を検出した場合は`BACKUP_SOURCE_CHANGED`または`BACKUP_EXPORT_BLOCKED`として全体を失敗させ、Partial Archiveを返さない。Export成功 / 失敗後はSnapshot PageをCleanupし、Content-free Operation Auditだけを保持する。

### 39.11 Limits, Recovery and Observability

| Item | Alice Limit |
|---|---:|
| Restore Active Slot | Inspection LeaseまたはPlanを合計1件 |
| Restore Working / Retained Temp | 768 MiB / 544 MiB |
| Restore Action | 最大10,000件、1 Item 8 KiB |
| Restore Resolution Delta | 1 PATCH最大100件、1 Item 16 KiB |
| Relation Review | Active最大20件、Target最大100件 |
| Relation Candidate / Target Item | 各32 KiB |
| Export Snapshot | 最大10,000 Source、1 Page最大100件 / 64 KiB |
| DynamoDB Mutation Transaction | Alice固有最大25 Action |
| BatchWrite Cleanup | 1 Request最大25件、Unprocessed Itemを全件Retry / 照合 |

Startup / 5分以内のSweeperはRestore Control、Backup Crypto Control、Lease、Active Plan / Review、Export Operation、ManifestおよびOperation Bindingを相互照合する。Orphan、期限切れ、Key不足またはOwner不一致を検出しても片側Controlを解放せず、まずContent-free Recovery Stateへ遷移して同じOwner / Lease EpochでCleanup / Reconciliationする。

全MutationはSection 45のTXL-001〜016をTransaction PlanのSource of Truthとし、SDK送信前にAction数、Encoded Request Byte、PK / SK一意性、必須Action集合およびLedger Versionを検証する。25 Action / 512 KiB超過時にIndex、Revision、Boundary、ResultまたはIdempotency Bindingを省略しない。Persistence検証はSection 46のP2-DB-TC-001〜017 RegistryへTraceする。

Content-free MetricはActive Slot / Review Count、Plan / Snapshot件数、Action / Result Count、Cleanup Pending、Lease Age、Resolution Delta Count、Source Changed、InvalidationおよびReconciliation Outcomeに限定する。Memory ID、Record ID、Digest、Ciphertext、Target配列、Path、Archive Size、Passphrase情報またはSelectionをMetric Labelへ使用しない。

### 39.12 Proposed Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-121 | Restore Global ControlをActive SlotとTemporary Byte Reservationの唯一のAuthorityにする | Accepted |
| DB2-122 | Multipart受理前にControl、30分Inspection LeaseおよびCreate BindingをAtomic Reservationする | Accepted |
| DB2-123 | Inspection成功時にLeaseをPlanへAtomic置換し実測Retained Bytesへ切り替える | Accepted |
| DB2-124 | Restore PlanをMetadata、固定Action、Resolution Delta、Action ResultおよびExecute Intentへ分割する | Accepted |
| DB2-125 | Archive Byte、Passphrase、復号済み本文、Plan KeyおよびTemporary PathをDynamoDBへ保存しない | Accepted |
| DB2-126 | Restore ActionをArchive順の最大8 KiB Itemとし本文をSealed ArtifactからHydrateする | Accepted |
| DB2-127 | 一回のPlan PATCHを一Delta ItemとPlan Updateの2 Action TransactionでAtomic化する | Accepted |
| DB2-128 | 128 DeltaごとにImmutable Selection GenerationへCompactionしAtomic Pointer Swapする | Accepted |
| DB2-129 | Confirmation DigestをPlan / Action Set / Selection / Version / Reset /確認状態へBindingする | Accepted |
| DB2-130 | Execute前の全件Preflight後にPlan、Intent、TokenおよびBindingをAtomicに確立する | Accepted |
| DB2-131 | Restore Action MutationとPer-action Resultを同一Transactionにして成功済みOrdinalを再実行しない | Accepted |
| DB2-132 | 全Action ResultをStrong照合してPlan Terminal Statusを確定しCounter単独を証拠にしない | Accepted |
| DB2-133 | Cleanup完了までRestore ControlをCLEANUP_PENDINGとしてQuotaへ残す | Accepted |
| DB2-134 | RestoreのContent-free Metadata / ResultをTerminal後最低24時間保持する | Accepted |
| DB2-135 | Relation Global ControlでActive Review最大20件を条件付き制御する | Accepted |
| DB2-136 | Related Targetが25 Actionを超えるReviewをPREPARING BuildとAtomic Publishで作成する | Accepted |
| DB2-137 | Relation Candidate / PreviewをApplication-level AEAD暗号化した最大32 KiB Itemへ分割する | Accepted |
| DB2-138 | Relation State Keyを外部Secret Boundaryで管理しBackup / Export / Logへ含めない | Accepted |
| DB2-139 | Relation期限・Terminal・Invalidation時にCiphertextを削除しActive Countを一度だけ減らす | Accepted |
| DB2-140 | Resolve IntentをReview Version、Resolution、Allowed TargetおよびIdempotencyへBindingする | Accepted |
| DB2-141 | Relation Mutation、Result、Review TerminalおよびBinding Terminalを同一Transactionにする | Accepted |
| DB2-142 | Relation結果不明時にResourceをStrong Reconciliationし新しいMemory IDを生成しない | Accepted |
| DB2-143 | Backup Crypto ControlでExport / Inspect KDF同時実行を一件へ制限する | Accepted |
| DB2-144 | ExportごとにID / Versionだけの15分Snapshot Resourceを作成する | Accepted |
| DB2-145 | Reset PointへcurrentMemoryCountを追加しBoundaryと同じMutation Actionで維持する | Accepted |
| DB2-146 | ALL Index列挙、Strong Root HydrationおよびBoundary / Preferences再確認でSnapshotをSealする | Accepted |
| DB2-147 | Snapshot Pageを最大100 Source / 64 KiBとし本文やPreferences値を保存しない | Accepted |
| DB2-148 | Export中のSource変更を最大1回だけ再試行し再変化時はPartial Archiveなしで失敗する | Accepted |
| DB2-149 | Archive全Byte確定後の最終Source再確認前にHTTP成功ResponseをCommitしない | Accepted |
| DB2-150 | Restore / Relation / ExportのItem、Action、Lease、CleanupおよびMetric上限をSection 39.11へ固定する | Accepted |

### 39.13 Approval and Next Design Unit

DB2-121〜DB2-150はAcceptedである。P2-DB-AP-001〜P2-DB-AP-017のPhysical Schemaは`phase2-database-design-cross-review.md`で横断レビューする。

### 39.14 Official DynamoDB References

- [DynamoDB transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html)
- [DynamoDB constraints](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Constraints.html)
- [Best practices for large items](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/bp-use-s3-too.html)
- [BatchWriteItem](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/API_BatchWriteItem_v20111205.html)
- [DynamoDB TTL](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html)

---

## 40. DB-CR-001 Resolution — Deletion Recovery Fence and Atomic Boundary Finalization

### 40.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted — Integrated and Focused Re-review Passed |
| Review Finding | DB-CR-001 |
| Scope | Partial / Unknown Delete、Recovery Fence、`currentMemoryCount`、`deletionBoundaryVersion`、Reset Finalization |
| Affected Access Patterns | P2-DB-AP-004、008、011、015 |
| Depends On | DB2-067〜DB2-090、DB2-145〜DB2-149 Accepted |

本Sectionは、一部TargetのRoot Cutover後に結果不明が発生しても、不確定なMemory集合を新しい安定状態として公開しないためのAccepted Resolutionである。Targetを推測で`FAILED`へ変換せず、Recovery Fence下でStrong Reconciliationし、削除件数、Boundary、Reset、Plan ResultおよびFence解放を一つのFinalization Transactionへ収束させる。

### 40.2 Corrected Safety Invariant

```text
Fence absent
    => Root集合、currentMemoryCount、deletionBoundaryVersionが同じCommit済み状態を表す

Target UNKNOWN > 0
    => Recovery Fence active
    => Memory Mutation / Export / Restore Execute / Relation Resolve禁止

Fence release
    => UNKNOWN = 0
    => currentMemoryCount = baselineCount - deletedCount
    => Boundary / Plan / Operation / Resetが同一Transactionで確定済み
```

Root不存在だけを削除成功とせず、`DELETED` Receipt、Guard有効性およびContent-bearing Copy Cleanup完了を必要とする。Rootが残るTargetは削除失敗として確定できるが、Root / Guard / Receiptが矛盾するTargetは`UNKNOWN`のまま維持する。

### 40.3 Mutation Fence Lifecycle

| Mode | Meaning | Fence Release |
|---|---|---:|
| `VALIDATING` | 固定TargetとExpected Versionを全件検証中 | Validation失敗でContent未変更の場合のみ可 |
| `EXECUTING` | 1件以上のTarget Cutover / Cleanupを実行中 | 不可 |
| `RECONCILING` | Unknownまたは未完了TargetをStrong照合中 | 不可 |
| `FINALIZING` | Count / Boundary / ResultのAtomic Commit直前 | Finalization Transactionだけが可 |

FenceはOperation ID、Lease Epoch、Mode、Baseline Reset Generation、Baseline Deletion Boundary Version、Baseline Current Memory Count、Target Set Version、開始時刻および更新時刻を持つ。Memory本文、Target一覧またはFingerprintを保持しない。

Lease期限切れはWorker所有権のTakeover候補であり、Fence解放条件ではない。Takeover WorkerはPlan、Deletion Operation、ChunkおよびReceiptをStrong Readし、同じOperation ID / Fence Epochの新Epochへ条件付き更新してから処理を再開する。

### 40.4 Operations Blocked and Allowed During Recovery

`EXECUTING`、`RECONCILING`または`FINALIZING` Fence中は次を禁止する。

- PersonalMemory Create / Update / Confirm / Delete / Explicit Reintroduction
- Automatic CaptureとCandidateからの保存
- 別Deletion PlanのExecute
- Restore Plan Execute
- Relation Review Resolve
- Backup Export SnapshotのBuild / Seal / Response Commit
- Search Projection PromotionまたはRepairのうちAuthoritative状態を書き換える処理

禁止処理はFenceを削除せず、`MEMORY_MUTATION_RECOVERY_IN_PROGRESS`相当のRetry可能なApplication ErrorへMappingする。Preferences Read / UpdateはMemory Root集合を変更しないため許可できる。ConversationとMemory Readは継続できるが、List / Search / Answer / Plan GETは必ずAuthoritative RootをHydrateし、Root不存在のMemory本文を返さない。

Maintenance RepairであってもFenceを無条件削除する`force unlock`を提供しない。Recoveryは同じOperationをReconcileするか、内容を復元できないTargetを安全な`UNKNOWN`として保持する。

### 40.5 Execution Baseline Binding

Deletion Execute Start Transactionは、既存Fieldに加えて次をPlan、Deletion OperationおよびFenceへ固定する。

```json
{
  "baselineResetGeneration": 3,
  "baselineDeletionBoundaryVersion": 142,
  "baselineCurrentMemoryCount": 240,
  "targetCount": 24,
  "targetSetVersion": 1,
  "targetSetDigest": "base64url-keyed-digest",
  "finalizationId": "7cb1e270-e1f1-4aa2-9f5b-123456789abc"
}
```

Start前に`baselineCurrentMemoryCount >= targetCount`を要求する。ただし削除対象外Memoryがあるため等値は要求しない。`scope = ALL`だけは`baselineCurrentMemoryCount = targetCount`を要求し、不一致ならContentを変更せずPlanを`INVALIDATED`へ遷移する。

`finalizationId`は同じExecute Operationで固定し、Retry / Takeoverで再生成しない。通常Log、Metric LabelまたはAPIへ出さない。

### 40.6 Target Reconciliation Classification

各TargetはPersonalMemory RootとExpected Version、Target ReceiptとOperation ID、Guard Metadata / Fingerprint Locator、Revision、Search Projection / Posting、Pending WorkおよびCache Cleanup ManifestをStrong Readする。

| Classification | Required Evidence | Result |
|---|---|---|
| `DELETED` | Root不存在、同Operation Receipt、Guard `ACTIVE`、全Content-bearing Copy不存在 | `deletedCount`へ加算 |
| `NOT_DELETED` | Expected VersionのRoot存在、Cutover未成立、同Operationの有効Guard不存在 | User-visible Target `FAILED`、`failedCount`へ加算 |
| `RECOVERY_PENDING` | Cutover Evidenceあり、CleanupまたはGuard検証が未完了 | Cleanup / Reconcile継続 |
| `UNKNOWN` | Root / Receipt / Guard / Cleanup Evidenceが矛盾または取得不能 | Fence保持、件数確定禁止 |

別VersionのRoot、Root不存在かつReceiptなし、Guardだけ存在、Receipt Operation不一致またはRepository Read不能を`NOT_DELETED`へ丸めない。Infrastructure一時障害は同OperationでRetryし、結果が判定できない間は`UNKNOWN`とする。

### 40.7 Plan Status During Recovery

| Internal Condition | API Status | Executable |
|---|---|---:|
| WorkerがTarget処理中 | `EXECUTING` | No |
| 1件以上`UNKNOWN`、Recovery継続中 | `UNKNOWN` | No |
| Unknown 0、Finalization前 | `EXECUTING` | No |
| Finalization済み、全件Deleted | `COMPLETED` | No |
| Finalization済み、一部Deleted | `PARTIAL` | No |
| Finalization済み、Deleted 0 / 全件Not Deleted | `FAILED` | No |

API上の`UNKNOWN`は新規Executeを受理しないが、内部Reconciliationによって`COMPLETED`、`PARTIAL`または`FAILED`へ収束できる。これは別Operationへの再利用ではなく、同じOperationの結果確定である。`UNKNOWN` Plan / BindingへTTLを設定せず、結果確定前に24時間Retentionの起算を開始しない。

### 40.8 Atomic Finalization Transaction

全Targetが`DELETED`または`NOT_DELETED`へ確定し、`unknownCount = 0`になった場合だけFenceを`FINALIZING`へ進める。Workerは全ReceiptをStrong Queryし、次を検証する。

```text
targetCount = deletedCount + failedCount
0 <= deletedCount <= baselineCurrentMemoryCount
finalCurrentMemoryCount = baselineCurrentMemoryCount - deletedCount
```

Finalization Transactionは次を一括確定する。

1. Reset PointをBaseline Generation / Boundary / Count条件で更新する。
2. Deletion PlanをExpected Operation / Finalization ID条件でTerminal化する。
3. Deletion OperationをTerminal Result / Count / Finalized Atへ更新する。
4. Execute Idempotency Bindingを同じTerminal Resultへ更新する。
5. Mutation Fenceを同じOperation ID / Lease Epoch / `FINALIZING`条件で削除する。

一つのTransactionで全ActionがCommitしない限りFenceを解放しない。同じItemへConditionCheckとUpdateを別Actionで重ねず、各Update / Delete自身のCondition Expressionを使用する。

### 40.9 Boundary and Count Formula

| Condition | `currentMemoryCount` | `deletionBoundaryVersion` |
|---|---:|---:|
| `deletedCount > 0` | `baselineCount - deletedCount` | `baselineBoundary + 1` |
| `deletedCount = 0` | `baselineCount` | `baselineBoundary` |

一Operationで複数Targetを削除してもBoundaryはFinalization時に一度だけ進める。Per-target CutoverではCount / Boundaryを更新しない。Countが負になる、10,000を超える、Baselineと現在値が一致しない、またはTarget件数Invariantが崩れる場合はFinalizationせず、Fenceを`RECONCILING`へ戻してIntegrity Recoveryを要求する。

### 40.10 Scope-specific Reset Rule

| Scope / Result | Reset Generation | Pending Reset | Boundary / Count |
|---|---|---|---|
| `ALL` + 全件Deleted | `baselineGeneration + 1`へ確定 | Stable Resetへ昇格 | Count 0、Boundary +1 |
| `ALL` + Partial | 進めない | 同Transactionで取消 | Deleted件数だけCount減算、Boundary +1 |
| `ALL` + Failed（Deleted 0） | 進めない | 同Transactionで取消 | Count / Boundary維持 |
| `ALL` + Unknown | 進めない | Recovery Pendingのまま | Fence保持、Finalization禁止 |
| `SELECTED / FILTERED` | 進めない | 使用しない | Deleted件数に応じて更新 |

`ALL`のUnknown中はAutomatic CaptureとConversation再抽出もFenceで禁止する。Partial確定後は完全Delete-allではないためReset Generationを進めず、削除済みTargetの個別Guardだけを維持する。

### 40.11 Finalization Result Unknown and Replay

Finalization TransactionのResponseがTimeout等で不明な場合、新しいFinalization IDやOperationを作らない。Reset PointのBoundary / Count / Finalization ID、Plan、Deletion Operation、Execute Idempotency BindingおよびMutation FenceをStrong Readする。

全Commit済みEvidenceが一致すれば成功としてReplayする。Fenceが残りBaselineも未変更なら同じTransactionを安全にRetryする。Evidenceが混在する状態はAtomicity / Data Integrity ErrorとしてFenceを保持し、`UNKNOWN`から成功へ推測しない。

### 40.12 Export and Other Snapshot Gates

Backup Exportは次の三時点でMutation Fence不存在をStrong確認する。

1. Snapshot `BUILDING`開始前
2. Snapshot `SEALED`公開直前
3. 完成ArchiveのHTTP Response Commit直前

いずれかでFenceを検出した場合、Snapshot PageとTemporary ArchiveをCleanupし、`BACKUP_SOURCE_CHANGED`またはRecovery中を表す安全なRetry結果へ収束する。Restore Execute、Relation Resolve、Explicit Reintroductionおよび通常Memory Mutationは、自身のAuthoritative Transaction内でFence不存在をConditionとして検証する。

### 40.13 Recovery, Retention and Observability

Startupおよび5分以内のSweeperは、Mutation FenceからOperation IDをDirect Getし、Plan / Operation / Chunk / ReceiptをQueryしてRecoveryを再開する。通常Runtime Scanを使用しない。

`UNKNOWN` / `RECONCILING`は結果確定までTTLなしとし、Fence Hold時間に自動上限を設けて強制解放しない。長時間RecoveryはUserへMemory変更とBackupが一時利用不能であることをContent-freeに通知する。

Metric / AuditはFence Mode / Hold Duration / Takeover Count、Target Classification Count、Finalization Attempt / Replay / Integrity ConflictおよびExport blocked件数を記録する。Memory ID、Target一覧、Guard Digest、Boundary値、Count値、Operation IDまたはFailure ExceptionをMetric Labelへ使用しない。

### 40.14 Required Tests

- Target Cutover前 / Commit後 / Cleanup中 / Receipt更新前後のCrash
- `BatchWriteItem` Unprocessed ItemとCleanup Retry
- Root、Guard、Receiptの全Reconciliation組合せ
- Unknown中のCreate / Update / Delete / Restore / Relation Resolve / Export拒否
- ReadがRoot不存在のMemory本文を返さないこと
- Finalization Transaction成功、条件不一致、Timeout Result Unknown
- Finalization RetryでCountを二重減算しないこと
- `ALL` Completed / Partial / Failed / UnknownのReset Generation
- Fence解放とBoundary / Count / Plan ResultのAtomic可視性
- Process Restart / Lease Takeover後も同じFinalization IDを使用すること

### 40.15 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-151 | Target Unknownが一件でも残る間はRecovery Fenceを保持しMemory MutationとExportを禁止する | Accepted |
| DB2-152 | Mutation Fence ModeをValidating / Executing / Reconciling / Finalizingへ固定する | Accepted |
| DB2-153 | Fence期限切れを解放条件にせず同じOperationの条件付きTakeoverだけを許可する | Accepted |
| DB2-154 | Execute Start時にReset Generation、Boundary、Current Count、Target SetおよびFinalization IDを固定する | Accepted |
| DB2-155 | TargetをDeleted / Not Deleted / Recovery Pending / UnknownのEvidence RuleでStrong Reconcileする | Accepted |
| DB2-156 | UnknownをFailedへ自動変換せず矛盾EvidenceではFenceを維持する | Accepted |
| DB2-157 | Unknown 0件の場合だけPlan Terminal Countを確定しFinalizationへ進む | Accepted |
| DB2-158 | Reset Point、Plan、Operation、Idempotency BindingおよびFence解放を一Transactionで確定する | Accepted |
| DB2-159 | Deleted件数をBaseline Countから一度だけ減算し、1件以上削除時にBoundaryを一度だけ進める | Accepted |
| DB2-160 | ALLのCompletedだけReset Generationを進め、Partial / FailedではPending ResetをAtomic取消する | Accepted |
| DB2-161 | API UNKNOWNを非ExecutableなRecovery状態とし同じOperation内で確定結果へ収束可能にする | Accepted |
| DB2-162 | Finalization結果不明時に同じFinalization IDでStrong ReconcileしCount二重減算を防ぐ | Accepted |
| DB2-163 | ExportをBuild / Seal / Response Commitの三時点でFenceとSource BoundaryへGateする | Accepted |
| DB2-164 | Recovery Fence、分類、FinalizationおよびCrash境界の必須TestをSection 40.14へ固定する | Accepted |

### 40.16 Integration and Focused Re-review Record

DB2-151〜DB2-164はDatabase Section 37 / 39、API Section 37、Memory Section 13およびTest Design Section 42へ統合済みである。DB-CR-001のFocused Re-reviewでは、Unknown中のFence保持、Count / Boundaryの一回限り更新、Reset分岐、Export GateおよびCrash Recoveryの一意性を確認し、FindingをResolvedとした。

### 40.17 Official DynamoDB References

- [DynamoDB transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html)
- [DynamoDB constraints](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Constraints.html)
- [DynamoDB read consistency](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadConsistency.html)
- [DynamoDB TTL](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html)

---

## 41. DB-CR-002 Resolution — Atomic Restore and Backup Crypto Reservation

### 41.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted — Integrated and Focused Re-review Passed |
| Review Finding | DB-CR-002 |
| Scope | Restore Inspection Slot、Working Byte Reservation、Backup Crypto Slot、KDF / AEAD、Retry / Expiry / Recovery |
| Affected Access Patterns | P2-DB-AP-012、P2-DB-AP-015 |
| Depends On | DB2-121〜DB2-123、DB2-143 Accepted |

Restore Plan CreateがRestore Global Controlだけを取得した後にBackup Crypto Controlで競合すると、Restore Slotだけが残る可能性がある。本Sectionは、Restore Inspection開始時に両Control、Inspection LeaseおよびIdempotency Bindingを一つのTransactionで取得し、片側だけが予約された状態を作らないためのAccepted Resolutionである。

### 41.2 Corrected Control Model

Restore Global ControlはActive Restore / Working ByteのAuthority、Backup Crypto ControlはArgon2id / AEADを含む高負荷Crypto処理の同時実行Authorityとする。二つを一Itemへ統合せず、Restore Inspectionだけが両方をAtomic取得する。

| Operation | Restore Control | Crypto Control |
|---|---:|---:|
| Restore Inspection | Required | Required |
| Restore Plan Review / Execute | Required | Not held after Inspection Crypto完了 |
| Backup Export | Not required | Required |
| Backup GET / Plan GET | Not required | Not required |

Backup Crypto Controlは`FREE`、`ACTIVE`または`CLEANUP_PENDING`を持つSingletonとする。`ACTIVE`ではOwner Operation ID、Operation Type（`RESTORE_INSPECT` / `BACKUP_EXPORT`）、Lease Epoch、固定期限、Process Instanceおよび開始時刻だけを保持する。Passphrase、Derived Key、Salt、Archive Digest、Archive Byte、Temporary PathまたはExceptionを保持しない。

### 41.3 Canonical Acquisition Order

実装、Fault InjectionおよびRecoveryで次の論理順序を固定する。DynamoDB Transaction内のAction実行順を前提にせず、全必要Resourceを一括Commitする。

1. Mutation / Recovery Fence不存在を条件として確認する。
2. Restore InspectionだけRestore Global Controlを`FREE`から`INSPECTING`へ更新する。
3. Backup Crypto Controlを`FREE`から`ACTIVE`へ更新する。
4. Operation / Inspection Leaseを作成する。
5. Idempotencyを持つRestoreではCreate Bindingを同じOwnerへ接続する。

ExportはStep 1、3、4だけを同一Transactionで取得し、Restore Controlを取得しない。RestoreはStep 2と3を別Requestで順番取得せず、片方のCondition失敗時はTransaction全体をAbortする。待機しながら一方のLockを保持する方式を禁止するため、循環待ちを作らない。

### 41.4 Restore Inspection Atomic Reservation Transaction

Multipart Body受理前に次の最大5 Actionを一Transactionで実行する。

1. Mutation Fence不存在のCondition Check。
2. Restore Global ControlをExpected Version、`slotState = FREE`、Cleanupなし条件で`INSPECTING`へ更新し、固定Working Byteを予約する。
3. Backup Crypto ControlをExpected Version、`state = FREE`条件で`ACTIVE / RESTORE_INSPECT`へ更新する。
4. 未使用Operation ID / Lease EpochのInspection LeaseをPutする。
5. `RESTORE_PLAN_CREATE` Idempotency Bindingを同じOperation / LeaseへPutする。

各Control Update自身へCondition Expressionを持たせ、同じItemへの別ConditionCheckを重ねない。Crypto競合時にRestore Controlだけを残さず、Restore競合時にCrypto Controlだけを残さない。Transaction応答が不明な場合は同じOperation ID / Idempotency Bindingで両ControlとLeaseをStrong Reconcileし、新Operationを作成しない。

### 41.5 Export Atomic Reservation Transaction

Export開始時はMutation Fence不存在、Crypto Control `FREE`、未使用Export Operation IDおよびSnapshot Metadata `BUILDING`を一TransactionでBindingする。ExportはRequestごとにOperation IDを一つ発行し、Transaction結果不明時に新Operation IDで暗黙再試行しない。

別Restore InspectionがCrypto Slotを持つ場合はSnapshot PageやTemporary Archiveを作成せず`BACKUP_OPERATION_IN_PROGRESS`へ収束する。別Exportが所有する場合も同じである。Restore Global Controlの`PLAN_ACTIVE`だけではExportを禁止しないが、Restore Execute中のMutation FenceまたはCrypto `CLEANUP_PENDING`はFail Closedにする。

### 41.6 Inspection Phase Transition and Crypto Release

Inspection Leaseは次のPhaseを持つ。

| Phase | Restore Control | Crypto Control | Rule |
|---|---|---|---|
| `RESERVED` | `INSPECTING` | `ACTIVE` | Multipart受理前 |
| `CRYPTO_RUNNING` | `INSPECTING` | `ACTIVE` | KDF / AEAD中 |
| `ANALYZING` | `INSPECTING` | `FREE` | 認証済みPayloadのDomain検証 / Action Build中 |
| `PLAN_ACTIVE` | Plan Owner | `FREE` | Plan公開済み |
| `CLEANUP_PENDING` | Cleanup Owner | `CLEANUP_PENDING`または`FREE` | Artifact / Key消去確認中 |

KDF / AEAD完了後、Derived KeyとCrypto Working Bufferを破棄したことを確認してから、Inspection Leaseを`ANALYZING`へ更新しCrypto Controlを`FREE`へ戻す一Transactionを実行する。Restore Slot / Byte ReservationはPlan公開またはCleanup完了まで維持する。

Crypto中またはCrypto結果不明時のFailureでは両Controlを`CLEANUP_PENDING`へ遷移し、Key / Buffer / Partial Payload / Temporary Artifactの利用不能を確認したFinal Cleanup Transactionでのみ解放する。Crypto解放後のDomain Validation失敗ではRestore Controlだけを`CLEANUP_PENDING`にし、Artifact Cleanup後に解放する。

### 41.7 Retry, Idempotency and Error Precedence

同じIdempotency-Key / Canonical Request Digestは同じInspection Operation、Reservationまたは保存済みResultへ収束する。別Archiveまたは別RequestへのKey再利用はConflictとする。Passphrase原文や可逆値をBindingへ含めない。

条件競合後は両ControlとBindingをStrong Readし、次の優先順位で安全な外部ResultへMappingする。

1. 同じBinding / OwnerならIdempotent Replay。
2. Restore Controlが別OwnerまたはCleanup中なら`ACTIVE_RESTORE_PLAN_EXISTS`またはTemporary Storage Recovery。
3. Crypto Controlが別OwnerまたはCleanup中なら`BACKUP_OPERATION_IN_PROGRESS`。
4. Evidence不一致またはRead不能ならResult Unknownとして新Upload / KDFを開始しない。

### 41.8 Expiry, Takeover and Startup Recovery

Lease期限は新規取得の許可時刻ではなくRecovery開始条件である。期限到達後もControlを`FREE`へ上書きせず、Owner Operation、Lease Epoch、Binding、ManifestおよびProcess状態をStrong Reconcileする。

Takeover Workerは同じOperation IDのLease Epochを条件付きで進める。Crypto Processを再開できない場合は成功を推測せずCleanupへ収束する。Startupおよび5分以内のSweeperはRestore Control、Crypto Control、Inspection Lease、Export Operation、BindingおよびManifestを同じ順序で照合し、次を禁止する。

- Owner不一致の片側Controlだけ解放
- TTL物理削除またはProcess不存在だけによる解放
- Cleanup未完了状態での新Upload / KDF
- Result Unknownから新Operation IDへの自動切替

### 41.9 Final Cleanup Transactions

Restore Inspection Cleanup完了時は、Inspection Lease Terminal化、Idempotency Binding Result、Restore Control解放および、まだ同Operationが所有している場合のCrypto Control解放を一Transactionで行う。Cryptoが既に`ANALYZING`遷移時に解放済みなら、Owner / Epochを照合してRestore側だけを解放する。

Export Cleanup完了時はExport Operation Terminal化、Snapshot Metadata / Cleanup ResultおよびCrypto Control解放を一Transactionへまとめる。Snapshot PageやFile Artifactの物理CleanupはTransaction前に完了確認し、失敗時はControlを`CLEANUP_PENDING`のまま維持する。

### 41.10 Limits, Observability and Required Tests

Restore Atomic Reservationは最大5 Action、Export Reservationは最大4 Action、Cleanup Finalizationは最大5 Actionとし、Alice上限25 Action / 512 KiB以内に固定する。

必須Test:

- Restore Control成功 / Crypto Control競合、および逆方向で片側Reservationが残らない
- 同時Export / Inspect、二つのInspect、二つのExportの競合
- Reservation Transaction Commit前後のTimeout Result Unknown
- KDF前、KDF中、AEAD後、Crypto Release前後、Plan Publish前後のCrash
- `CLEANUP_PENDING`中の新Upload / Export拒否
- Expiry / Restart / Takeoverで同じOwner / Epoch / Operationを照合する
- Idempotent Replayと異なるArchiveへのKey再利用拒否
- Cleanup失敗時にSlot / Byte / Cryptoを早期解放しない

MetricはOperation Type、Control State、Lease Age Bucket、Conflict / Replay / Cleanup Outcomeに限定する。Operation ID、Archive Digest / Size、Path、Passphrase情報、Owner IDまたはException MessageをLabelへ使用しない。

### 41.11 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-165 | Restore ControlとBackup Crypto Controlを別AuthorityのままRestore InspectionでAtomic取得する | Accepted |
| DB2-166 | Restore InspectionのFence、Restore Control、Crypto Control、Lease、Bindingを最大5 Actionで予約する | Accepted |
| DB2-167 | ExportのFence、Crypto Control、Operation、Snapshot Buildを一TransactionでBindingする | Accepted |
| DB2-168 | 論理Lock順序をFence、Restore Control、Crypto Control、Operation、Bindingへ固定する | Accepted |
| DB2-169 | 片側Lockを保持した待機と段階的なRestore / Crypto取得を禁止する | Accepted |
| DB2-170 | Inspection LeaseをReserved / Crypto Running / Analyzing / Plan Active / Cleanup Pendingへ分ける | Accepted |
| DB2-171 | Crypto Material破棄確認後にLeaseのAnalyzing遷移とCrypto Slot解放をAtomic化する | Accepted |
| DB2-172 | Crypto結果不明またはCleanup未完了時は両ControlをCleanup Pendingで維持する | Accepted |
| DB2-173 | Retryを同じOperation / Bindingへ収束させ条件競合後のError優先順位を固定する | Accepted |
| DB2-174 | Lease期限を解放条件にせず同じOperationのEpoch TakeoverまたはCleanupだけを許可する | Accepted |
| DB2-175 | Startup / SweeperでRestore、Crypto、Lease、Operation、Binding、Manifestを相互照合する | Accepted |
| DB2-176 | Cleanup確認後のControl解放とTerminal Resultを一Transactionへまとめる | Accepted |
| DB2-177 | Reservation / Crypto Phase / Cleanup / Crash境界の必須TestとAction上限をSection 41.10へ固定する | Accepted |

### 41.12 Integration and Focused Re-review Record

DB2-165〜DB2-177はDatabase Section 39.3 / 39.10 / 39.11、API Sections 40 / 44、Memory Section 22およびTest Design Section 38へ統合済みである。Focused Re-reviewで、両Controlの片側残留、循環待ち、期限切れ解放およびCleanup早期解放の経路がないことを確認し、DB-CR-002をResolvedとした。

### 41.13 Official DynamoDB References

- [DynamoDB transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html)
- [DynamoDB condition expressions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ConditionExpressions.html)
- [DynamoDB read consistency](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadConsistency.html)
- [DynamoDB TTL](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html)

---

## 42. DB-CR-003 Resolution — Relation State Key Lifecycle and Startup Gate

### 42.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | DB-CR-003 |
| Scope | Relation State Key Version、Review参照数、Rotation、Retirement、Startup / AEAD Failure Recovery |
| Affected Access Patterns | P2-DB-AP-013 |
| Depends On | DB2-135〜DB2-142 Accepted |

Relation ReviewのCandidate / Preview Ciphertextは短命でもPersonal Dataを含む。旧Keyを早期削除してActive Reviewを復号不能にすることと、旧Keyを参照不明のまま無期限保持することの両方を防ぐため、Relation専用Key ControlとReview Referenceを追加する。

### 42.2 Dedicated Key Boundary and Key Map

Relation State Key Materialは外部Secret Boundaryで管理し、DynamoDB、Backup、Export、Log、Metric、TraceまたはAPIへ保存しない。DatabaseはVersionと参照状態だけを保持する。

| Logical Item | `pk` | `sk` | Content |
|---|---|---|---|
| Relation Key Control | `MEMORY_RELATION_KEY#CONTROL` | `STATE` | Active Version / Count / State |
| Relation Key Reference | `MEMORY_RELATION_KEY#<keyVersion>` | `REVIEW#<relationReviewId>` | Content-free Review Reference |

Relation KeyをDevice Credential、Cursor、Guard、Search Term、Idempotency、Backup ArchiveまたはConfirmation Token Keyと共有しない。Version番号が一致してもKey Purposeは別物として扱い、Purpose-separated Key Provider Portを使用する。

### 42.3 Relation Key Control Schema

```json
{
  "pk": "MEMORY_RELATION_KEY#CONTROL",
  "sk": "STATE",
  "itemType": "MEMORY_RELATION_KEY_CONTROL",
  "schemaVersion": 1,
  "writeKeyVersion": 7,
  "maxActiveKeyVersions": 2,
  "versions": {
    "6": {
      "state": "DECRYPT_ONLY",
      "activeReviewCount": 3,
      "preparingReferenceCount": 0,
      "cleanupPendingCount": 0
    },
    "7": {
      "state": "WRITE_ACTIVE",
      "activeReviewCount": 8,
      "preparingReferenceCount": 1,
      "cleanupPendingCount": 0
    }
  },
  "controlVersion": 14,
  "updatedAt": "2026-08-31T23:00:00.000+09:00"
}
```

同時に参照可能なKey Versionは最大2個とする。Stateは`WRITE_ACTIVE`、`DECRYPT_ONLY`または`RETIRE_PENDING`とし、`WRITE_ACTIVE`は常に一つだけとする。Countが負、Review Global Countより大きい、未知State、Version欠番またはWrite Version不在では新Review作成をFail Closedにする。

### 42.4 Reference Item and Count Authority

Relation Key ReferenceはReview ID、Key Version、Reference State（`PREPARING`、`ACTIVE`、`CLEANUP_PENDING`）、Review Versionおよび作成時刻だけを保持する。Candidate、Preview、Memory ID、Ciphertext、Nonce、AAD、Relation Type、Target IDまたはReasonを含めない。

Version別CountはControl上の高速Gate、Reference ItemはReconciliation Authorityとする。Strong QueryしたReference件数とControl Countが一致しない場合、旧KeyをRetireせず新Rotationを止める。通常Runtime Scanを使用しない。

Reference Stateは一Reviewにつき一つだけ存在し、Count変更と同じTransactionで条件付き遷移する。Cleanup完了後のReference削除前に`COUNT_RELEASED`相当の条件を満たし、RetryでVersion Countを二重減算しない。

### 42.5 Review Create and Publish Binding

新ReviewはKey ControlをStrong Readし、`WRITE_ACTIVE` Keyの利用可能性とVersionを確認してから暗号化する。Review Metadata、全Ciphertext ItemおよびAADへ同じ`stateKeyVersion`をBindingし、別VersionのKeyでFallback復号しない。

Alice固有25 Action以内の場合、次をReview Publish Transactionへ含める。

1. Relation Global ControlのActive Countを増加する。
2. Relation Key Controlの該当`activeReviewCount`を増加する。
3. Key Referenceを`ACTIVE`でPutする。
4. Review Metadata / Target / Bindingを公開する。

25 Actionを超えるReviewは、先にKey Referenceを`PREPARING`で作成し`preparingReferenceCount`を増加してからCiphertextをStageする。全Target Digest検証後のPublish TransactionでReferenceを`ACTIVE`へ、Preparing Countを減算、Active Countを増加し、ReviewとBindingを公開する。Stage失敗時はCiphertext Cleanup完了後だけPreparing Referenceを解放する。

### 42.6 Rotation Procedure

通常Rotationは次の順序で行う。

1. 外部Secret Boundaryへ新Versionを生成し、Relation Purpose、Encrypt / DecryptおよびAEAD Known-answer Probeを行う。
2. Key ControlをStrong Readし、参照中Versionが2未満であることを確認する。
3. Control Version条件付きTransactionで新Versionを`WRITE_ACTIVE`、旧Write Versionを`DECRYPT_ONLY`へ切り替える。
4. Rotation Commit後に作成するReviewは新Versionだけを使用する。
5. 既存Reviewは再暗号化せず、固定30分のReview LifetimeとCleanupで旧Version参照を0へ収束させる。

既に2 Versionが参照中で旧Version Countが0でない場合、3番目を追加せずRotationを`RELATION_KEY_ROTATION_BLOCKED`として停止する。Emergency Rotationでは旧Reviewを明示的にInvalidation / Cleanupして参照0を確認してから同じProcedureを使用し、Key上限を迂回しない。

### 42.7 Old Key Retirement

旧VersionをRetireできる条件は次のすべてである。

- `activeReviewCount = 0`
- `preparingReferenceCount = 0`
- `cleanupPendingCount = 0`
- Key Version PartitionのStrong Queryが空
- 旧Versionが`WRITE_ACTIVE`ではない

条件成立後、Controlを`RETIRE_PENDING`へ進め、新Reference作成を禁止する。その後、外部Secret BoundaryからKey Materialを削除し、削除確認後にControlからActive Versionを除外する。外部削除結果が不明な場合は`RETIRE_PENDING`を維持してProviderをProbeし、再びDecrypt利用可能へ戻さない。

Retired Version番号とRetired AtのContent-free Auditは保持できるが、Key Material、Key Handle、Secret PathまたはProvider Errorを保持しない。

### 42.8 Startup Gate and Capability Probe

Application Startupおよび5分以内のSweeperは、Controlにある最大2 VersionについてRelation PurposeのKey CapabilityをProbeする。

| Condition | Behavior |
|---|---|
| Write Key正常 | 新Review作成可能 |
| Write Key欠落 / Probe失敗 | 新Review作成を禁止、既存Memory Readは継続 |
| Decrypt-only Key正常 | 参照中ReviewのGET / Resolve可能 |
| 参照中Key欠落 | 該当ReviewをInvalidation / Cleanupへ進める |
| Control欠落 / 不正 | Relation Review Create / GET / ResolveをFail Closed |
| Provider一時不能 | Key欠落と断定せずRetryable Unavailable、Ciphertextを返さない |

Startup Gate失敗を理由にConversation Historyや無関係なMemory Readを停止しない。ただしRegister / UpdateがRelation Reviewを必要とする場合は、安全なRetry可能Resultへ収束させ、暗号化なしのPreviewを保存しない。

### 42.9 Missing Key and AEAD Failure Recovery

GET、ResolveまたはStartup RecoveryでKey欠落、未知Version、AAD不一致またはAEAD Authentication Failureを検出した場合、別Version Key、現在Write Key、平文FallbackまたはAI再生成で復号を試みない。

該当Reviewを内部`INVALIDATING_KEY_UNAVAILABLE`へ条件付き遷移し、GET / Resolveから利用不能にする。Candidate / Preview Ciphertext、Nonce、AADおよびHydration CacheをCleanupし、Reviewを`INVALIDATED`へ確定する。Key Referenceを`CLEANUP_PENDING`へ進め、Cleanup完了と同じTransactionでVersion Countを一度だけ減算する。

外部ResponseはReviewが利用不能であることと再度元操作が必要なことだけを示し、Key Version、Provider、Ciphertext、AEAD Failure Detailまたは関連Memory一覧を含めない。

### 42.10 AAD and Decryption Binding

AADは少なくともPurpose、Schema Version、Relation Review ID、Review Version、State Key Version、Source Operation Type、Candidate / Target種別およびTarget OrdinalをCanonical Encodingする。ItemのReview / Version / OrdinalとAADが一致しない場合は復号しない。

一Review内でCandidateとTarget Previewが異なるKey Versionを持つことを禁止する。Ciphertext移動、Target差替え、別ReviewへのCopyまたは古いPreview ReplayをAEAD検証前後のBinding Checkで拒否する。

### 42.11 Retention, Observability and Required Tests

Key Controlと参照中VersionにTTLを設定しない。Reference ItemはCiphertext CleanupとCount解放完了後だけ削除する。TTL、Review期限、ProviderからのKey不存在またはProcess RestartだけをCount解放の証拠にしない。

必須Test:

- 新旧VersionでのReview作成 / GET / Resolve
- Rotation後の新Reviewが新Versionだけを使用する
- Active 2 Version中の3番目Rotation拒否
- Active / Preparing / Cleanup Pending CountとReference Query照合
- Count 0 / Reference空の前後で旧Key RetirementをGateする
- Write Key欠落、旧Key欠落、Provider一時不能、未知Version
- AAD改変、Ciphertext改変、別Review / TargetへのCopy
- Startup / Sweeper中のInvalidation、Cleanup、Crash、Retry
- Key Count二重減算と早期Key削除が発生しない
- Relation KeyがDevice / Cursor / Guard / Idempotency / Backup Keyと交換不能

MetricはKey State、Active Version Count、Reference State Count、Rotation / Retirement Outcome、Startup Gate Outcomeに限定する。Key Version、Review ID、Provider名、Secret Handle、Ciphertext、Nonce、AADまたはException MessageをLabelへ使用しない。

### 42.12 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-178 | Relation State Key専用ControlとVersion別Reference Partitionを追加する | Accepted |
| DB2-179 | Relation KeyをDevice / Cursor / Guard / Search / Idempotency / Backup Keyと共有しない | Accepted |
| DB2-180 | 同時参照可能なRelation Key Versionを最大2個、Write Activeを1個へ限定する | Accepted |
| DB2-181 | ControlへVersion別Active / Preparing / Cleanup Pending Countを保持する | Accepted |
| DB2-182 | Reference ItemをCount Reconciliation Authorityとし通常Runtime Scanを禁止する | Accepted |
| DB2-183 | Review Publish時にGlobal Count、Key Count、Reference、ReviewおよびBindingをAtomic化する | Accepted |
| DB2-184 | 大規模ReviewをPreparing Key Referenceで保護しCleanup完了前に参照を解放しない | Accepted |
| DB2-185 | Rotation後の新Reviewを新Versionだけで暗号化し既存Reviewを再暗号化しない | Accepted |
| DB2-186 | 2 Version参照中の追加Rotationを禁止しEmergency時も先にReviewをInvalidationする | Accepted |
| DB2-187 | 全Count 0とStrong Reference Query空の両方を旧Key Retirement条件にする | Accepted |
| DB2-188 | RetirementをRetire Pending、外部Key削除、Control除外の順にRecovery可能化する | Accepted |
| DB2-189 | Startup / Sweeperで最大2 VersionのRelation Key CapabilityをProbeする | Accepted |
| DB2-190 | Key欠落 / AEAD失敗時にFallback復号せずReviewをInvalidation / Cleanupする | Accepted |
| DB2-191 | AADをReview / Version / Purpose / TargetへBindingしCiphertext移動を拒否する | Accepted |
| DB2-192 | Rotation / Startup / Missing Key / AEAD / Reference Countの必須TestをSection 42.11へ固定する | Accepted |

### 42.13 Integration Record

DB2-178〜DB2-192はDatabase Section 39.8 / 39.9 / 39.11、Security Section 33、API Section 45、Memory Section 23およびTest Design Section 39へ統合済みである。Focused Re-reviewで、参照中Keyの早期削除、3 Version超過、Key欠落時の平文 / 別Key FallbackおよびCount不一致時のRetirement経路が存在しないことを確認し、DB-CR-003をResolvedとした。

### 42.14 Official DynamoDB References

- [DynamoDB transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html)
- [DynamoDB condition expressions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ConditionExpressions.html)
- [DynamoDB read consistency](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadConsistency.html)
- [DynamoDB TTL](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/TTL.html)

---

## 43. DB-CR-004 Resolution — Relation Review Exactly-once Active Slot Release

### 43.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | DB-CR-004 |
| Affected Access Patterns | P2-DB-AP-013 |
| Depends On | DB2-135〜DB2-142、DB2-178〜DB2-192 Accepted |

Relation Reviewの暗号化Stateを利用不能にした後、Global Active CountとKey Version参照数を一度だけ解放するMonotonic Boundaryを追加する。

### 43.2 Cleanup State and Active Directory

Review Metadataへ`cleanupState = NOT_STARTED | CONTENT_CLEANUP_PENDING | CONTENT_CLEANED | RELEASED`、`activeSlotReleased`、`cleanupEpoch`および`releaseVersion`を追加する。Stateは前進だけを許し、`RELEASED`からActiveへ戻さない。

通常Runtime Scanを避けるため、Content-free Active Directoryを追加する。

| Logical Item | `pk` | `sk` | Content |
|---|---|---|---|
| Active Review Directory | `MEMORY_RELATION_REVIEW#ACTIVE` | `REVIEW#<relationReviewId>` | Review ID、Status、Cleanup State、Expiry、Key Version、Control Version |

DirectoryへCandidate、Target ID、Ciphertext、Digest、Relation TypeまたはReasonを保存しない。Review PublishとDirectory Putを同じTransactionへ含め、Startup / SweeperはDirectory QueryからOwnerを列挙する。

### 43.3 Two-stage Cleanup and Final Release

期限切れ、Terminal、InvalidationまたはKey Failure時は、まずReviewを解決不能にし`CONTENT_CLEANUP_PENDING`へ進める。Candidate / Target Ciphertext、Nonce、AAD、Hydration Cacheおよび未公開Stageを削除し、Strong Read / QueryでContent-bearing Itemが利用不能であることを確認した場合だけ`CONTENT_CLEANED`へ進める。

最後のRelease Transactionは次をAtomicに確定する。

1. Reviewを`activeSlotReleased = false AND cleanupState = CONTENT_CLEANED`条件で`RELEASED`へ更新する。
2. Relation Global Controlを`activeCount > 0`条件で一度だけ減算する。
3. Relation Key Controlの該当Countを`> 0`条件で一度だけ減算し、ReferenceをRelease済みにする。
4. Active Review Directory Itemを削除する。
5. Content-free Cleanup ResultをPutする。

Transaction結果不明では同じReview ID / Cleanup EpochをStrong Readし、Review、両Control、Key Reference、DirectoryおよびCleanup Resultを照合する。`activeSlotReleased = true`なら減算を再実行しない。不整合時は新Review作成とKey Retirementを止め、Reconciliationへ送る。Countを0未満へ進めるRecoveryは存在しない。

### 43.4 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-193 | Review Cleanupへ単調な4段階StateとCleanup Epochを追加する | Accepted |
| DB2-194 | Active ReviewをContent-free DirectoryからQuery可能にする | Accepted |
| DB2-195 | Review PublishとActive Directory登録をAtomic化する | Accepted |
| DB2-196 | 暗号化State利用不能確認前のActive Slot解放を禁止する | Accepted |
| DB2-197 | Review Release、Global Count、Key Count、Reference、DirectoryおよびResultをAtomic化する | Accepted |
| DB2-198 | `activeSlotReleased = false`とCount正数を二重解放防止条件にする | Accepted |
| DB2-199 | Unknown ReleaseをReview ID / Cleanup EpochのStrong Reconciliationへ収束させる | Accepted |
| DB2-200 | Count不一致中は新Review作成とKey RetirementをFail Closedにする | Accepted |
| DB2-201 | Startup / SweeperをDirectory Queryで再開し通常Runtime Scanを禁止する | Accepted |

---

## 44. DB-CR-005 Resolution — Export Exact Source Set Proof

### 44.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | DB-CR-005 |
| Affected Access Patterns | P2-DB-AP-005、P2-DB-AP-015 |

件数一致だけでなく、Export対象が正確に同じMemory集合とVersionであることを証明する。

### 44.2 Canonical Source Set

Consistent Readした`ALL` Indexを`(memoryId ASC, version)`のCanonical Entryへ変換し、Ordinalを`0..N-1`で付与する。次のいずれかがあればSnapshotをSealしない。

- Memory ID重複、Ordinal欠番 / 重複、順序逆転
- 同じMemory IDの複数Version
- Index EntryとStrong RootのVersion不一致
- Dangling Index、Root欠落、Root重複Hydration
- Index Repair / PromotionまたはMutation / Recovery Fence進行中

Snapshot MetadataはDomain-separated Canonical Binary Encodingから計算した`sourceSetDigest`を保持する。Digestは内部照合専用で、API、Archive Header、LogまたはMetricへ出さない。

### 44.3 Seal and Response Commit Gates

次の値をすべて一致させる。

`currentMemoryCount = ALL raw entry count = unique memoryId count = snapshot entry count = hydrated root count`

Seal直前にSnapshot PageからOrdinal連続性とSource Set Digestを再計算し、Strong HydrationしたRoot集合のDigestと一致させる。HTTP Response Commit直前には、Fence不存在を確認して`ALL` IndexとBoundaryを再読込し、Count、Canonical Set Digest、Preferences VersionおよびDeletion BoundaryがSEALED Metadataと一致する場合だけ完成Archiveを返す。

Index Repair / Promotion中はExportをFail Closedにする。変化時はPartial Archiveを返さず最大1回だけ全工程を再開し、再変化時は`BACKUP_SOURCE_CHANGED`へ収束させる。

### 44.4 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-202 | ALL IndexからCanonical `(memoryId, version)`集合を構築する | Accepted |
| DB2-203 | Memory ID一意性、Ordinal連続性、順序および一ID一Versionを検証する | Accepted |
| DB2-204 | Boundary、Raw、Unique、SnapshotおよびHydrated Countの完全一致を要求する | Accepted |
| DB2-205 | Domain-separated Source Set DigestをSnapshot Metadataへ固定する | Accepted |
| DB2-206 | Seal前にSnapshot PageとStrong Root集合のDigestを照合する | Accepted |
| DB2-207 | Response Commit前にALL Index、Boundary、PreferencesおよびDigestを再確認する | Accepted |
| DB2-208 | Repair / Promotion / Mutation / Recovery Fence中のExportを禁止する | Accepted |
| DB2-209 | Source変化時はPartial Archiveなしで最大1回だけBounded Retryする | Accepted |
| DB2-210 | Digest、Memory ID集合およびIndex不整合詳細を外部出力しない | Accepted |
| DB2-211 | Missing + Duplicate等の組合せFault Testを必須化する | Accepted |

---

## 45. DB-CR-006 Resolution — Transaction Action Budget Ledger

### 45.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | DB-CR-006 |
| Affected Access Patterns | P2-DB-AP-002〜004、006、009、011〜014、016〜017 |

全Cross-cutting Actionを加えた最終Transaction Planを、実装とDynamoDB Local Testが共有するLedgerへ固定する。Alice Hard Limitは25 Action / 512 KiBであり、AWS上限がより大きくても緩和しない。

### 45.2 Authoritative Operation Budget

| Ledger ID | Operation | Max Actions | Max Encoded Bytes |
|---|---|---:|---:|
| TXL-001 | Register | 12 | 192 KiB |
| TXL-002 | Semantic Update | 18 | 256 KiB |
| TXL-003 | Confirmation-only Update | 6 | 96 KiB |
| TXL-004 | Individual Delete Cutover | 18 | 128 KiB |
| TXL-005 | Explicit Reintroduction | 14 | 224 KiB |
| TXL-006 | Restore ADD | 14 | 224 KiB |
| TXL-007 | Restore UPDATE_EXISTING | 20 | 288 KiB |
| TXL-008 | Restore REINTRODUCE_DELETED | 16 | 256 KiB |
| TXL-009 | Relation UPDATE_TARGET | 21 | 320 KiB |
| TXL-010 | Relation ADD_AS_NEW | 17 | 256 KiB |
| TXL-011 | Device Issue | 4 | 32 KiB |
| TXL-012 | Device Rotate | 4 | 32 KiB |
| TXL-013 | Device Revoke | 4 | 32 KiB |
| TXL-014 | Restore Plan Start | 5 | 32 KiB |
| TXL-015 | Export Plan Start | 4 | 32 KiB |
| TXL-016 | Plan / Cleanup Finalize | 5 | 64 KiB |

各LedgerはAction Rowとして`logicalKey`、完全なPK / SK Template、`PUT | UPDATE | DELETE`、最大Encoded Item / Expression Size、Condition、必須 / 分岐、同一Item Key Groupを持つ。DynamoDBが同一Itemへの複数Actionを許さないため、同じPK / SKへのConditionは独立`ConditionCheck`にせず、そのItemのPut / Update / Deleteへ統合する。

Transaction Plan BuilderはSDK呼出し前に、全分岐確定後のAction数、Encoded Request Byte、Item Key一意性、必須Action集合およびLedger Versionを検証する。超過時はMutationを一切送らず安全な内部Design-limit Failureとし、Index、Revision、Boundary、ResultまたはIdempotency Bindingを省略して縮小しない。

### 45.3 Action Row Key and Size Catalog

Ledger Fixtureは次のLogical Actionを完全なPK / SKへ展開する。`IDX[6]`の各要素も独立Action Rowであり、配列一件として数えない。

| Code | PK / SK Template | Action | Per-item Max |
|---|---|---|---:|
| `ROOT` | `MEMORY#<memoryId>` / `META` | Put / Update / Delete | 32 KiB |
| `REV` | `MEMORY#<memoryId>` / `REVISION#<20-digit>` | Put | 32 KiB |
| `IDX[6]` | Section 35の各`MEMORY_INDEX#...` / `UPDATED#...#<memoryId>` | Put / Delete | 各2 KiB |
| `PROJ` | `MEMORY_PROJECTION#<memoryId>` / `INTENT#<version>` | Put / Update | 8 KiB |
| `FENCE` | `MEMORY_OPERATION#MUTATION_FENCE` / `STATE` | Update | 4 KiB |
| `BOUNDARY` | `MEMORY_SETTINGS#PRIMARY` / `RESET_POINT` | Update | 4 KiB |
| `BIND` | `MEMORY_OPERATION#IDEMPOTENCY#<type>#<digest>` / `META` | Put / Update | 8 KiB |
| `GUARD` | `MEMORY_GUARD#ID#<guardId>` / `META`またはLocator | Put / Delete | 各8 KiB |
| `RESULT` | Plan / Review PK / `RESULT#<ordinal-or-operation>` | Put / Update | 8 KiB |
| `SUMMARY` | Plan PK / `META` | Update | 8 KiB |
| `REVIEW` | `MEMORY_RELATION_REVIEW#<id>` / `META` | Put / Update | 16 KiB |
| `DEVICE` | Device Metadata / Locator / Directory / ControlのSection 38.5 Key | Put / Update / Delete | 各4 KiB |
| `CONTROL` | Restore / Crypto / Relation Controlの固定Key | Update | 8 KiB |
| `LEASE` | Operation固有PK / `LEASE` | Put / Update / Delete | 8 KiB |

Operation Fixtureは次の最大分岐を展開し、予約枠も具体的なAction Rowとして保持する。

| Ledger | Required maximum composition |
|---|---|
| TXL-001 | `ROOT + REV + IDX[6] + PROJ + FENCE + BOUNDARY + BIND`から最大12 Rowとなる許可分岐 |
| TXL-002 | `ROOT + REV + old IDX[6] + new IDX[6] + PROJ + FENCE + BOUNDARY + BIND`から最大18 Rowとなる許可分岐 |
| TXL-003 | `ROOT + old/new Confirmation IDX + FENCE + BOUNDARY + BIND`、最大6 Row |
| TXL-004 | Root / 6 Index / Guard Metadata・Locator / Receipt / Fence / Boundary / ResultのSection 37 Cutover Row、最大18 |
| TXL-005〜010 | 対応するCreate / Update CoreへGuard、Restore Action Result / SummaryまたはRelation Review / Result / Bindingを加えた最大14 / 14 / 20 / 16 / 21 / 17 Row |
| TXL-011〜013 | Section 38.5のDevice Metadata、Locator、Directory、Control。Issue 4、Rotate 4、Revoke 4 Row |
| TXL-014 | Mutation Fence、Restore Control、Crypto Control、Inspection Lease、Create Bindingの5 Row |
| TXL-015 | Mutation Fence、Crypto Control、Export Operation、Snapshot Metadataの4 Row |
| TXL-016 | Owner Result、Plan / Operation Terminal、Cleanup Result、Control Release、Binding Terminalの最大5 Row |

同じCodeが複数回現れる場合も展開後PK / SKは一意でなければならない。最大Compositionより少ない分岐はLedgerでOptionalと宣言されたRowだけを除外でき、Core Atomicityに必要なRowを実装側判断で削除しない。

### 45.4 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-212 | TXL-001〜016をOperation別Transaction LedgerのSource of Truthにする | Accepted |
| DB2-213 | Alice Hard Limitを25 Action / 512 KiBのまま維持する | Accepted |
| DB2-214 | Ledger Rowへ完全Key、Action種別、最大Size、Conditionおよび分岐を保持する | Accepted |
| DB2-215 | 同一PK / SKへの複数ActionをPlan構築時に拒否する | Accepted |
| DB2-216 | Conditionを同一ItemのWrite Actionへ統合し重複ConditionCheckを禁止する | Accepted |
| DB2-217 | SDK送信前にAction数とEncoded Byteを実測検証する | Accepted |
| DB2-218 | Limit超過時に必須Index / Revision / Boundary / Resultを省略しない | Accepted |
| DB2-219 | Ledger VersionをTransaction PlanとTest ResultへBindingする | Accepted |
| DB2-220 | DynamoDB Local Contract Fixtureを同じLedgerから生成する | Accepted |
| DB2-221 | 全分岐の最大Planと一Action / 一Byte超過を境界試験する | Accepted |
| DB2-222 | Ledger未登録TransactionをImplementation Readiness Failureとする | Accepted |

---

## 46. DB-CR-007 Resolution — Persistence Test Registry

### 46.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | DB-CR-007 |
| Affected Access Patterns | P2-DB-AP-001〜017 |

Stable Test Registry `P2-DB-TC-001〜017`をAccess Pattern番号と一対一に対応させ、各Case Metadataへ検証するDB2 Decision範囲とTest Levelを付与する。

### 46.2 Registry and Required Layers

| Test ID | Access Pattern | Primary Test Layers |
|---|---|---|
| P2-DB-TC-001〜004 | Root Read / Create / Update / Delete | Unit、Port、DynamoDB Local、Crash |
| P2-DB-TC-005〜007 | Management Index / Preferences / Revision | Port、DynamoDB Local、Repair |
| P2-DB-TC-008〜010 | Reset / Guard / Usage Trace | Unit、DynamoDB Local、Security Negative |
| P2-DB-TC-011〜013 | Deletion / Restore / Relation Plan | DynamoDB Local、Crash、Fault Adapter |
| P2-DB-TC-014〜017 | Idempotency / Export / Search / Device | 全5 Layer |

5 LayerはUnit、Persistence Port Contract、DynamoDB Local Integration、Crash / Unknown Recovery、Security Negativeである。Case RegistryはDB2-001〜最新Accepted Decisionを少なくとも一件へ割り当て、未割当Decision、未知Decisionまたは重複Test IDをCIで失敗させる。

必須ScenarioはConditional Race、Transaction Cancellation Reason、Timeout / Disconnect結果不明、BatchGet / BatchWrite Unprocessed Item、Strong / Eventual Read差、TTL遅延、Lease Takeover、Fence競合、Count / Digest不一致、Index Repair中断、Key Rotation / 欠落、Ciphertext / Cursor / Item改変およびCorrupt Itemである。Localで再現できないAWS Failureは決定的Fake / Fault Adapterで注入し、Real AWS Accountを通常CI Gateにしない。

### 46.3 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-223 | P2-DB-TC-001〜017をAccess Pattern対応のStable Registryにする | Accepted |
| DB2-224 | RegistryへDB2 Decision RangeとTest Levelを必須Metadata化する | Accepted |
| DB2-225 | Unit / Port / DynamoDB Local / Crash / Security Negativeの5 Layerを使用する | Accepted |
| DB2-226 | Conditional RaceとTransaction Cancellationを必須Scenarioにする | Accepted |
| DB2-227 | Timeout結果不明とStrong Reconciliationを必須Scenarioにする | Accepted |
| DB2-228 | Batch Unprocessed Item、TTL遅延およびConsistency差を必須Scenarioにする | Accepted |
| DB2-229 | Fence / Lease / Count / Digest / RepairのFault Injectionを必須化する | Accepted |
| DB2-230 | Key欠落、暗号改変およびCorrupt ItemをSecurity Negativeへ含める | Accepted |
| DB2-231 | AWS固有Failureを決定的Fault Adapterで再現する | Accepted |
| DB2-232 | Real AWSを通常CI GateにせずRegistry未充足をReadiness Failureにする | Accepted |

---

## 47. DB-CR-008 Resolution — Restore Resolution Delta Hard Backpressure

### 47.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | DB-CR-008 |
| Affected Access Patterns | P2-DB-AP-012 |

Selection Compactionが遅延または失敗しても、Delta数、読込量およびGeneration数を無制限に増やさない。

### 47.2 Limits and Patch Gate

| Item | Hard Limit |
|---|---:|
| Active Resolution Delta | 128 Items |
| Delta Item | 16 KiB |
| Active Delta Encoded Bytes | 2,097,152 Bytes |
| Selection Generation | 最大2世代（Authoritative + Building / Retiring） |
| Snapshot Page | 最大100 Pages、各64 KiB |
| GET / Confirm / Execute Selection Read | 最大228 Items、8,650,752 Bytes |

96 Delta到達でBackground Compactionを起動し、128件または2,097,152 Bytesのどちらかへ到達したら新しいPATCHを`RESTORE_SELECTION_COMPACTION_IN_PROGRESS`としてRetryableに停止する。既存Plan、旧Authoritative Generationおよび既存Deltaは破棄しない。

### 47.3 Idempotent Compaction

Planは`compactionState = NONE | BUILDING | SWAPPING | CLEANUP_PENDING`、`compactionId`、`compactionEpoch`、Source Generation / Base Delta VersionおよびTarget Digestを保持する。Builderは次Generationを不変Pageとして作り、Page Count、Ordinal、Selection DigestおよびSource Plan Versionを検証する。短いPointer Swap TransactionでPlan Version不変を条件にAuthoritative Generationを切り替える。旧Generationと吸収済みDeltaは新PointerのStrong確認後だけ削除する。

Crash / Timeout後は同じCompaction ID / Epochを再開し、部分的な新GenerationをAuthorityにしない。Hard Limit中もGET / Confirm / Executeは上記読込上限内で旧Authorityと固定Deltaを完全適用できる場合だけ継続する。Digest、Page、DeltaまたはPointerが不整合なら古いSelectionを黙示利用せずFail Closedにする。

### 47.4 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| DB2-233 | Active Deltaを128 Items / 2 MiBへHard Limit化する | Accepted |
| DB2-234 | 96 Deltaで先行Compactionを開始する | Accepted |
| DB2-235 | Hard Limit後の新PATCHをRetryable Backpressureで停止する | Accepted |
| DB2-236 | Backpressureで既存PlanまたはSelectionを破棄しない | Accepted |
| DB2-237 | Selection Generationを同時最大2世代へ限定する | Accepted |
| DB2-238 | Compaction State、ID、EpochおよびSource Boundaryを永続化する | Accepted |
| DB2-239 | Build検証後のPointer SwapをPlan Version条件付きでAtomic化する | Accepted |
| DB2-240 | 新Pointer確認前の旧Generation / Delta Cleanupを禁止する | Accepted |
| DB2-241 | GET / Confirm / Executeを228 Items / 8,650,752 Bytes以内に制限する | Accepted |
| DB2-242 | 不整合時に古いSelectionを黙示利用せずFail Closedにする | Accepted |
| DB2-243 | Compaction Crash / Retry / Expiry / Limit境界Testを必須化する | Accepted |

### 47.5 Integration and Focused Re-review Boundary

DB2-193〜DB2-243は一括承認済みである。Database Section 39、API、Memory、SecurityおよびTest Designへ必要範囲を統合し、DB-CR-004〜008を個別にFocused Re-reviewした。Exactly-once Release、Exact Source Set、Transaction Budget、Persistence Test TraceおよびDelta Backpressureの未定義経路がないことを確認し、5件をResolvedとした。

---

## 48. DB-CR-009 Resolution — Document and Traceability Integration

| Item | Value |
|---|---|
| Status | Resolved — Focused Re-review Passed |
| Review Finding | DB-CR-009 |
| Behavioral Change | None |

本Sectionは新しいPersistence挙動を追加せず、Accepted Contractの参照と文書状態を統合する。

- Section 29をSection 38.5、P2-DB-AP-017およびDB2-109〜DB2-120へ正式接続し、Physical Schema Pendingを解消した。
- Section 34のDeferred / 後続設計表現をSections 35〜47のImplemented-by-design参照へ更新した。
- Phase 2 Requirement Traceability MatrixのP2-NFR-007をDatabase Sections 29 / 38およびTest Sections 31 / 43へ接続した。
- Memory Design Section 18をDatabase Review完了Summaryへ整理し、Section 19から解消済みPersistence / API項目を除外した。

Focused Re-reviewで、Allowed Device Physical Schemaの二重定義、解消済みDatabase Open Decision、旧Pending / Deferred記述およびP2-NFR-007の孤立参照が残っていないことを確認した。DB-CR-009は文書統合のみのため新しいDB2 Decisionを発行しない。

---

---

## Phase 4 Formal Persistence Integration — 2026-09-04

**Status:** Accepted / Integrated
**Source:** AGENT4-056〜097, P4-FR-001〜121, P4-NFR-001〜024

Logical aggregates:

- AgentExecution
- AgentPlan / Plan Version
- AgentAction / Action Version / Attempt
- Observation
- Permission / Permission Decision reference
- Approval
- Risk Decision reference
- Durable Execution Intent
- Execution Fence
- Executor Assignment / Device Binding
- Cancellation / Emergency Stop state
- Recovery / Verification state
- Audit Event

Rules:

- Conversation History ≠ Personal Memory ≠ Execution Persistence ≠ Audit SoT
- Full Conversation / Full Memory / Credential / Hidden Prompt / CoT / unbounded Screenshot / Terminal Output / File ContentをExecution Persistenceへ複製しない
- Replan後は旧Action / Approval / Executor Commandが実行できないVersion/Fence contractを持つ
- Side-effecting ActionはApproval Consumption + Durable Intent + PREPAREDを競合安全なBoundaryで確定してからDispatchする
- Dispatch progress stateとOutcomeを分離する
- `UNKNOWN_OUTCOME`はdurable stateでありrestart後もverification/recovery requirementを失わない
- Emergency Stop stateをdurableに保持しrestart後にsilent resumeしない
- TTLをAuthority / Fence validity / Approval expiry / Success判定のSource of Truthにしない
- Phase 3 `ToolOperation.operationId / operationVersion`はshared execution昇格後もStable Identityとして保持する。AgentAction IDはToolOperation IDを置換せず、AgentActionがToolOperationを起動する場合はCorrelation Referenceで関連付ける。
