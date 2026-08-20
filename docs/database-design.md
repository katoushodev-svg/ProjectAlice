# Project Alice - Database Design

## 1. Document Information

| Item | Value |
|---|---|
| Document | `database-design.md` |
| Project | Project Alice |
| Target | Phase 1 Conversation Persistence |
| Status | Approved |
| Last Updated | 2026-08-19 JST |

本ドキュメントは、Project Alice Phase 1におけるConversation HistoryのDynamoDB詳細設計を定義する。

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

## 28. Current Status

Database Design Review Result:

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

次の設計対象:

```text
AI Design
```
