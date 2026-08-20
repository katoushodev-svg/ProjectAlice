# Project Alice - API Design

## 1. Document Information

| Item | Value |
|---|---|
| Document | `api-design.md` |
| Project | Project Alice |
| Target | Phase 1 Backend API |
| Status | Approved |
| Last Updated | 2026-08-19 JST |

本ドキュメントは、Project Alice Phase 1におけるFlutter ClientとSpring Boot Backend間のAPI Contractを定義する。

本ドキュメントは以下の事項についてSource of Truthとして扱う。

- API Endpoint
- HTTP Method
- Request / Response Schema
- Validation
- Pagination
- Server-Sent Events（SSE）Contract
- HTTP Status
- API Error Format
- Idempotency
- API Versioning
- APIから見た正常終了・異常終了条件

AI Coding Assistantおよび開発者は、本ドキュメントで確定したAPI Contractを独自判断で変更してはならない。

本ドキュメントとArchitectureまたはAccepted ADRとの矛盾を発見した場合、実装を先行せず、関連Design DocumentまたはADRを更新する。

---

## 2. Related Documents

| Document | Responsibility |
|---|---|
| `requirements.md` | Phase 1機能要件・非機能要件 |
| `mvp.md` | Phase Scope・成功条件 |
| `alice-architecture.md` | System Architecture |
| `backend-design.md` | Backend内部Architecture・Layer責務 |
| `frontend-design.md` | Flutter内部Architecture・State・API / SSE Client |
| `database-design.md` | DynamoDB・保存順序・整合性・Cursor実装 |
| `ai-design.md` | AI Model・Prompt・Context・AI Streaming |
| `security-design.md` | Security・Secret・Logging |
| `test-design.md` | API・SSE・RetryのTest Strategy |
| `decisions.md` | Accepted Architecture Decision |

---

## 3. Goals

Phase 1 API Designでは以下を実現する。

- FlutterからAliceへText Messageを送信できる
- Aliceの返答を生成途中から表示できる
- Conversation Historyを保存後に取得できる
- アプリ再起動後も過去のConversation Historyを表示できる
- 外部AI ProviderおよびPersistence TechnologyをAPIへ漏らさない
- 重複送信、通信切断およびエラーを制御可能にする
- AI Coding AssistantがAPI Contractを推測せず実装できる状態にする

---

## 4. Phase 1 API Scope

### 4.1 In Scope

- 単一Conversationの基本情報取得
- 単一ConversationのMessage History取得
- User Message送信
- Alice ResponseのSSE Streaming
- Cursor Pagination
- Request Validation
- API Error Response
- Request Correlation
- Idempotency
- 同時送信制御

### 4.2 Out of Scope

Phase 1では以下のAPIを実装しない。

- Conversation一覧
- 独立したConversation作成API
- Conversation削除
- Conversation History Reset
- Message削除
- Message編集
- AI Response再生成専用API
- Personal Memory API
- RAG API
- Tool API
- GitHub API
- AWS操作API
- Voice API
- PC Agent API
- Authentication API
- Authorization API

将来必要になる可能性だけを理由として、上記APIを先行実装しない。

---

## 5. Phase 1 Conversation Model

Phase 1のFlutter Applicationでは、単一Conversationのみを継続利用する。

そのため、Phase 1 APIではConversation IDをPath Parameterとして要求しない。

```text
/api/v1/conversation
```

Conversationは、最初のUser Message送信時にBackendが自動作成する。

独立した`CreateConversationUseCase`およびConversation作成Endpointは実装しない。

`ListConversationsUseCase`はPhase 1では実装しない。

---

## 6. Base Path and Versioning

Phase 1 APIのBase Pathは以下とする。

```text
/api/v1
```

Versioning Ruleは以下とする。

- Responseへの任意Field追加は、原則として`v1`内で許可する
- Requestへの任意Field追加は、BackendとFlutterのCompatibilityを確認した場合に限り、`v1`内で許可する
- Required Fieldの削除または意味変更には新しいAPI Versionを必要とする
- Field Typeの変更には新しいAPI Versionを必要とする
- SSE Eventの既存Semantics変更には新しいAPI Versionを必要とする
- Flutterは未知のResponse Fieldおよび未知のSSE Eventを無視する
- Backendは未知のRequest JSON Fieldを受理せず、`400 Bad Request`を返す

RequestへOptional Fieldを追加する場合、追加Fieldを受理するBackendを先にDeployするか、BackendとFlutterを同一Releaseとして更新する。

新しいFlutterから古いBackendへ未知Fieldを送信する組み合わせはCompatibleとみなさない。

---

## 7. API Endpoint Summary

| Method | Endpoint | Use Case | Success Response |
|---|---|---|---|
| `GET` | `/api/v1/conversation` | `GetConversationUseCase` | `200 application/json` |
| `GET` | `/api/v1/conversation/messages` | `GetConversationMessagesUseCase` | `200 application/json` |
| `POST` | `/api/v1/conversation/messages` | `SendMessageUseCase` | `200 text/event-stream` |

Phase 1では`201 Created`および`204 No Content`を正常系Contractとして使用しない。

最初のMessage送信時にConversationを作成する場合も、Message送信Endpointの主目的はAlice ResponseのStreamingであるため、`200 OK`を返す。

---

## 8. Common HTTP Policy

### 8.1 Character Encoding

RequestおよびResponseの文字コードはUTF-8とする。

### 8.2 Cache Policy

Conversation Historyには個人情報が含まれる可能性があるため、すべてのAPI Responseへ以下を設定する。

```http
Cache-Control: no-store
```

### 8.3 Authentication and Authorization

Phase 1ではAuthenticationおよびAuthorization Headerを要求しない。

Phase 1 Security Boundaryは以下とする。

```text
Single User
Local Development
Default Bind: 127.0.0.1
Public Internet Access: prohibited
Authentication: none
Authorization: none
```

BackendをPublic Internetへ公開してはならない。

### 8.4 Content Types

| Context | Content-Type |
|---|---|
| JSON Response | `application/json` |
| Message Send Request | `application/json` |
| SSE Response | `text/event-stream` |
| HTTP Error Response | `application/problem+json` |

### 8.5 Phase 1 Response Size Limits

Phase 1では、BackendとFlutterの双方が次の上限を強制する。

| Response | Maximum Raw Body Size |
|---|---:|
| `GET /api/v1/conversation` JSON | 64 KiB（65,536 Byte） |
| `GET /api/v1/conversation/messages` JSON | 16 MiB（16,777,216 Byte） |
| `application/problem+json` | 64 KiB（65,536 Byte） |
| `POST /api/v1/conversation/messages` SSE Stream全体 | 8 MiB（8,388,608 Byte） |

`Content-Length`が存在する場合は読込前に確認する。ただしHeaderが存在しない、または実際のBodyと一致しない可能性があるため、実際に受信・送信したByte数にも同じ上限を適用する。

Message Historyの`limit`は最大取得件数であり、16 MiB上限へ到達する場合、Backendは指定件数より少ないMessageでPageを終了してよい。その場合も`hasMore`と`nextCursor`をSection 12.7のInvariantに従って返す。単一Message自体がContent上限を満たさない場合は、Page分割で回避せずContract違反として扱う。

---

## 9. Request Correlation

BackendはすべてのHTTP Requestに対し、UUID形式の`requestId`を生成する。

すべてのResponseへ以下のHeaderを設定する。

```http
X-Request-Id: 9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb
```

同一の`requestId`を以下で利用する。

- Response Header
- Problem Details
- SSE Event Payload
- Backend Log

Flutterから送信された`X-Request-Id`は採用せず、Backendが必ず生成する。

`requestId`にConversation Content、User情報、OpenAI情報またはDynamoDB情報を含めてはならない。

---

## 10. Common Message Schema

Phase 1のAPIで利用するMessage Schemaは以下とする。

```json
{
  "id": "1a2b3c4d-5e6f-4789-8abc-def012345678",
  "role": "assistant",
  "content": "こんにちは。今日はどうしましたか？",
  "createdAt": "2026-08-14T15:00:02.000+09:00"
}
```

### 10.1 Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | Yes | Backendが生成するOpaque Message ID |
| `role` | string | Yes | `user`または`assistant` |
| `content` | string | Yes | Message本文。Userは最大10,000、Assistantは最大50,000 Unicode Code Point |
| `createdAt` | string | Yes | JST Offset付きISO 8601 Date-Time |

Message ContentはRoleごとに次を満たす。

| Role | Maximum Length |
|---|---:|
| `user` | 10,000 Unicode Code Point |
| `assistant` | 50,000 Unicode Code Point |

文字数はUTF-8 Byte、UTF-16 Code UnitまたはGrapheme ClusterではなくUnicode Code Point単位で判定する。Contentを上限へ合わせて切り詰めてはならない。

### 10.2 Timestamp Format

APIはDate-TimeをJSTで返し、小数秒はミリ秒3桁へ固定する。

```text
2026-08-14T15:00:02.000+09:00
```

UTC、Time Zone情報のないLocal Date-Timeまたはミリ秒以外の小数秒精度をAPI Responseとして返してはならない。

DynamoDBの保存形式は`database-design.md`で定義し、Persistence形式をAPIへ漏らさない。

### 10.3 Message Schema Non-Fields

Phase 1のMessage Schemaへ以下を含めない。

- AI Provider名
- AI Model名
- Token Usage
- OpenAI固有ID
- Conversation ID
- DynamoDB Partition Key / Sort Key
- Personal Memory
- Infrastructure Status

---

## 11. Get Conversation

### 11.1 Endpoint

```http
GET /api/v1/conversation
Accept: application/json
```

### 11.2 Success Response

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
X-Request-Id: 9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb
```

```json
{
  "id": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "createdAt": "2026-08-14T15:00:00.000+09:00",
  "updatedAt": "2026-08-14T15:05:30.000+09:00"
}
```

### 11.3 Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | Yes | Backendが生成するOpaque Conversation ID |
| `createdAt` | string | Yes | Conversation作成日時、JST |
| `updatedAt` | string | Yes | Conversation Historyへ最後にMessageが正常保存された日時、JST |

Phase 1の`updatedAt`は、Conversation Historyへ最後に正常保存されたMessageの`createdAt`と一致させる。

User Messageのみが保存されるFailure StrategyをDatabase Designで採用した場合、そのUser Messageの`createdAt`が`updatedAt`となる。

将来Conversation Metadataを追加した場合も、Phase 1の`updatedAt`の意味を独自に変更してはならない。

### 11.4 Conversation Not Created

最初のMessageがまだ送信されておらず、Conversationが作成されていない場合は`404 Not Found`を返す。

```json
{
  "type": "urn:project-alice:problem:conversation-not-found",
  "title": "Conversation not found",
  "status": 404,
  "detail": "Conversationはまだ作成されていません。",
  "code": "CONVERSATION_NOT_FOUND",
  "requestId": "4de9670d-7533-4fc8-a432-3be5263db590"
}
```

### 11.5 Excluded Fields

Phase 1では以下を返さない。

- Conversation Title
- Conversation Summary
- Owner User
- Message Count
- AI Model
- Personal Memory

---

## 12. Get Conversation Messages

### 12.1 Endpoint

```http
GET /api/v1/conversation/messages
```

### 12.2 Query Parameters

| Parameter | Type | Required | Default | Constraint | Description |
|---|---|---:|---:|---|---|
| `limit` | integer | No | `50` | `1` to `100` | 取得件数 |
| `cursor` | string | No | none | Opaque value | 次のPage取得位置 |

### 12.3 First Page

Cursorを指定しない最初のRequestでは、最新のMessage群を取得する。

```http
GET /api/v1/conversation/messages?limit=50
```

### 12.4 Additional Page

Flutterで上方向へScrollした場合、直前のResponseに含まれる`nextCursor`をそのまま指定する。

```http
GET /api/v1/conversation/messages?limit=50&cursor=opaque-cursor-value
```

FlutterはCursorの内容を生成、解析または変更してはならない。

### 12.5 Success Response

```json
{
  "messages": [
    {
      "id": "2b3c4d5e-6f70-489a-8bcd-ef0123456789",
      "role": "user",
      "content": "こんにちは",
      "createdAt": "2026-08-14T15:00:00.000+09:00"
    },
    {
      "id": "3c4d5e6f-7081-49ab-8cde-f0123456789a",
      "role": "assistant",
      "content": "こんにちは。今日はどうしましたか？",
      "createdAt": "2026-08-14T15:00:02.000+09:00"
    }
  ],
  "nextCursor": "opaque-cursor-value",
  "hasMore": true
}
```

### 12.6 Ordering

各Responseの`messages`は、古いMessageから新しいMessageの順に並べる。

```text
oldest
  ↓
newest
```

最初のRequestで最新Pageを取得する場合も、そのPage内の配列順序は古い順から新しい順とする。

### 12.7 Pagination Invariants

以下の関係を必ず維持する。

```text
hasMore = true
→ nextCursor is non-null

hasMore = false
→ nextCursor is null
```

### 12.8 Empty Conversation History

Conversationが作成されていない、またはMessageが存在しない場合は、Errorではなく`200 OK`と空のCollectionを返す。

```json
{
  "messages": [],
  "nextCursor": null,
  "hasMore": false
}
```

### 12.9 Invalid Cursor

不正、改変済み、または解釈不能なCursorには`400 Bad Request`と`INVALID_CURSOR`を返す。

DynamoDBのKey Object、SDK ObjectまたはKey AttributeをCursorとして直接公開してはならない。

Cursor Encoding、ValidationおよびPersistenceとのMappingは`database-design.md`で定義する。

### 12.10 Response Size-aware Pagination

Backendは最大件数だけでなく、JSON UTF-8 Encoding後のResponse Sizeが16 MiBを超えないようPageを構築する。

- Messageは古い順から新しい順というResponse内Orderingを維持する
- 16 MiBへ到達する直前の完全なMessageまでを返す
- Message Contentを途中で分割または切り詰めない
- 未返却Messageがある場合は`hasMore=true`と有効な`nextCursor`を返す
- 追加確認用に取得したMessageを現在Pageへ含めない

---

## 13. Send Message

### 13.1 Endpoint

```http
POST /api/v1/conversation/messages
Content-Type: application/json
Accept: text/event-stream
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

### 13.2 Request Body

```json
{
  "content": "こんにちは"
}
```

### 13.3 Request Fields

| Field | Type | Required | Constraint | Description |
|---|---|---:|---|---|
| `content` | string | Yes | 1 to 10,000 Unicode characters | User Message本文 |

Flutterは以下をRequest Bodyへ送信しない。

- `role`
- Conversation ID
- Message ID
- Created Date-Time
- AI Provider
- AI Model

このEndpointから受信したMessageは、Backendが必ずUser Messageとして扱う。

### 13.4 Conversation Creation

Conversationが存在しない場合、`SendMessageUseCase`が単一Conversationを自動作成する。

Conversation作成のための追加HTTP Requestを要求しない。

### 13.5 Success Status

Request Validationおよび事前条件を満たした場合、SSE Responseを開始する。

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-store
X-Request-Id: 9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb
```

Conversationが初回作成された場合も`201 Created`ではなく`200 OK`を返す。

### 13.6 Pre-stream Checks

Backendは以下を完了するまでSSE ResponseをCommitしてはならない。

- Content-Type / Accept Validation
- JSON Parse
- Request DTO Validation
- `Idempotency-Key`形式確認
- Idempotency Conflict確認
- 同一Idempotency RequestがProcessing中かの確認
- Conversation Busy確認

上記で失敗した場合は、SSE EventではなくProblem Detailsを返す。

`200 OK`および`stream.started`送信後に発生したErrorは、`stream.failed`で通知する。

---

## 14. Send Message Validation

### 14.1 Validation Rules

- `content`は必須とする
- `content`はJSON Stringとする
- Empty Stringを禁止する
- Space、Tab、Line Break等のWhitespaceのみのMessageを禁止する
- 10,000 Unicode Code Pointを超えるMessageを禁止する
- 未知のRequest JSON Fieldを禁止する
- `Idempotency-Key`を必須とする
- `Idempotency-Key`はUUID形式とする
- `Content-Type: application/json`を要求する
- Clientが`text/event-stream`を明示的に受理できない場合は拒否する

### 14.2 Content Preservation

Whitespaceのみか確認するためにTrim相当の判定を利用してよいが、Userが入力した`content`自体を変更してはならない。

```text
Validation
→ Whitespaceを除くとEmptyか確認

Persistence / AI Input
→ Original Contentを維持
```

Code、Markdown、IndentationおよびLine BreakをBackendが勝手に削除してはならない。

### 14.3 Character Counting

文字数上限はUTF-8 Byte数、Java UTF-16 Code Unit数またはGrapheme Cluster数ではなく、Unicode Code Point単位で判定する。

Java実装はCode Point Countを使用し、本Contractを満たすこと。

### 14.4 Maximum HTTP Request Body Size

`POST /api/v1/conversation/messages`のHTTP Request Body上限は128 KiB（131,072 Byte）とする。

この上限にはJSON Property名、Quote、Escapeおよびその他のJSON Encodingを含む。

上限を超えた場合は`413 PAYLOAD_TOO_LARGE`を返す。

上限値はConfigurationから変更可能とするが、API Contractを変更せず128 KiB未満へ設定してはならない。

Spring ContainerまたはFramework LevelでRequestが拒否された場合も、可能な限り共通Problem Detailsへ変換する。

---

## 15. SSE Streaming Contract

### 15.1 Transport

Alice ResponseはServer-Sent Events（SSE）でStreamingする。

```text
Message Send: HTTP POST
Alice Response: SSE
```

Phase 1ではWebSocketを使用しない。

### 15.2 Event Sequence

正常時のSequenceは以下とする。

```text
stream.started
    ↓
assistant.delta
    ↓
assistant.delta
    ↓
assistant.completed
    ↓
connection closed by Backend
```

失敗時のSequenceは以下とする。

```text
stream.started
    ↓
assistant.delta (zero or more)
    ↓
stream.failed
    ↓
connection closed by Backend
```

### 15.3 Terminal Events

Backendが制御可能な正常終了またはApplication Error終了では、以下のいずれかExactly OneのTerminal Event送信を試みる。

- `assistant.completed`
- `stream.failed`

Terminal Event送信後、BackendはSSE Connectionを閉じる。

Network Loss、Client DisconnectまたはBackend Process Failure等により、Terminal Eventを送信できない、またはFlutterが受信できない場合がある。

FlutterがTerminal Eventを受信せずConnectionが閉じた場合、結果不明としてIdempotency Contractに従う。

### 15.4 Provider Independence

SSE Event名およびPayloadへOpenAI固有Event、Chunk ID、Model名、Token情報またはSDK Objectを公開してはならない。

SSEはAlice API独自のProvider-independent Contractとする。

---

## 16. SSE Event Schemas

### 16.1 `stream.started`

BackendがRequestを受理し、Alice Response生成処理を開始したことを表す。

```text
event: stream.started
data: {"requestId":"9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb"}
```

```json
{
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb"
}
```

`stream.started`はMessageのPersistence完了を意味しない。

Message IDをいつ確定するかは、Persistence Strategyと合わせて`database-design.md`で決定する。

### 16.2 `assistant.delta`

Alice Responseの今回追加するText Fragmentを表す。

```text
event: assistant.delta
data: {"requestId":"9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb","delta":"こんにちは"}
```

```json
{
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb",
  "delta": "こんにちは"
}
```

Flutterは、同一SSE Connectionで受信した`delta`を到着順に連結する。

Phase 1では以下をPayloadへ追加しない。

- Token Number
- Chunk Number
- OpenAI Chunk ID
- Message Position
- Provider Name
- Model Name

Empty `delta` Eventを生成する必要はない。

一つの`assistant.delta` EventのUTF-8 JSON Dataは64 KiB（65,536 Byte）以下とする。Provider Adapterから受け取った一つのText Fragmentが上限を超える場合、Backendは文字順序と内容を変更せず、Unicode Scalar境界で複数Deltaへ分割する。

### 16.3 `assistant.completed`

User MessageおよびAssistant Messageの生成とPersistenceが正常に完了し、Conversation History取得APIから取得可能になったことを表す。

```text
event: assistant.completed
data: { ... }
```

```json
{
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb",
  "userMessage": {
    "id": "4d5e6f70-8192-4abc-8def-0123456789ab",
    "role": "user",
    "content": "こんにちは",
    "createdAt": "2026-08-14T15:00:00.000+09:00"
  },
  "assistantMessage": {
    "id": "5e6f7081-92a3-4bcd-8ef0-123456789abc",
    "role": "assistant",
    "content": "こんにちは。今日はどうしましたか？",
    "createdAt": "2026-08-14T15:00:02.000+09:00"
  }
}
```

`assistant.delta`でTextをStreaming済みの場合も、`assistant.completed`はBackendが保存したAssistant Message全文を返す。

Flutterは`assistant.completed`のMessageをCanonical Resultとして、Temporary Messageおよび連結済みTextを確定する。

Assistant Message全文は50,000 Unicode Code Point以下とする。上限を超えた生成結果は切り詰めて保存せず、正常な`assistant.completed`として扱わない。`ai-design.md`のFailure Policyに従い、`stream.failed`の`RESPONSE_GENERATION_FAILED`へ収束させる。

### 16.4 `stream.failed`

SSE開始後にMessage送信処理が完了できなかったことを表す。

```text
event: stream.failed
data: {"requestId":"9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb","code":"RESPONSE_GENERATION_FAILED","message":"Aliceの応答を生成できませんでした。"}
```

```json
{
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb",
  "code": "RESPONSE_GENERATION_FAILED",
  "message": "Aliceの応答を生成できませんでした。"
}
```

| Field | Type | Required | Description |
|---|---|---:|---|
| `requestId` | string | Yes | 失敗したHTTP処理の識別子 |
| `code` | string | Yes | Alice API固有Error Code |
| `message` | string | Yes | Userへ表示可能な安全な説明 |

以下をPayloadへ含めてはならない。

- Java Exception Class
- Stack Trace
- OpenAIのRaw Error Response
- AWS SDKのRaw Error Response
- DynamoDBのRaw Error Response
- Credential
- Secret
- Internal Endpoint

`retryable`はPhase 1のSSE Error Payloadへ含めない。

---

## 17. SSE Timeout and Heartbeat

### 17.1 Timeout

Phase 1のSSE全体Timeout Defaultは180秒とする。

Timeout値はConfigurationから変更可能とし、Source CodeへHard Codingしない。

180秒以内にTerminal Eventへ到達しなかった場合、可能であれば以下を送信する。

```text
event: stream.failed
data: {"requestId":"9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb","code":"RESPONSE_TIMEOUT","message":"Aliceの応答がタイムアウトしました。"}
```

その後、BackendはSSE Connectionを閉じる。

### 17.2 Heartbeat

SSE Connection中は15秒間隔でHeartbeatを送信する。

```text
: heartbeat
```

HeartbeatはSSE Commentとし、Flutterは画面表示またはMessage Contentへ追加しない。

Heartbeat間隔はConfigurationから変更可能とする。

### 17.3 SSE Resource Limits

SSEは次の有限上限を持つ。

| Target | Limit |
|---|---:|
| Single SSE Frame | 1 MiB（1,048,576 Byte） |
| Single `assistant.delta` JSON Data | 64 KiB（65,536 Byte） |
| Accumulated Temporary Assistant Text | 50,000 Unicode Code Point |
| Entire SSE Stream | 8 MiB（8,388,608 Byte） |
| Non-comment SSE Event Count | 10,000 Events |

Raw SSE Byte数にはEvent、Data、改行、区切りおよびHeartbeat Commentを含める。Event Countには未知Eventを含むすべての非Comment Frameを含め、Heartbeat Commentは含めない。

Backendは上限到達前に生成処理を停止し、可能な場合は`stream.failed`と`RESPONSE_GENERATION_FAILED`を送信するための余裕を確保する。上限を超えたPartial Assistant Textを保存せず、`assistant.completed`を送信しない。

Flutterは`Content-Length`だけに依存せず、実際の受信Byte、Frame Byte、Event Countおよび累積Unicode Code Pointを計測する。上限超過時は購読をCancelし、部分ResponseをCanonical Messageへ昇格させず、FIP-010のResult Unknown / History Reconciliationへ接続する。新しいIdempotency Keyで自動Retryしてはならない。

---

## 18. Idempotency

### 18.1 Required Header

`POST /api/v1/conversation/messages`では`Idempotency-Key` Headerを必須とする。

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

FlutterはUserの一回の送信操作ごとに新しいUUIDを生成する。

### 18.2 `Idempotency-Key` and `requestId`

| Identifier | Owner | Scope | Purpose |
|---|---|---|---|
| `Idempotency-Key` | Flutter | Logical Message Send | 重複実行防止 |
| `requestId` | Backend | One HTTP Request | Correlation・Logging |

同一のLogical Message Sendを再試行する場合、Flutterは同じ`Idempotency-Key`を使用する。

新しい`Idempotency-Key`で自動再送してはならない。

### 18.3 Duplicate Request Behavior

| Existing State | Same Key / Same Content | Result |
|---|---|---|
| Not found | First execution | 通常処理 |
| Processing | Duplicate while processing | `409 REQUEST_IN_PROGRESS` |
| Completed | Completed duplicate | 保存済みResultをSSEでReplayし、AIを再呼び出ししない |
| Failed | Failed duplicate | 保存済みFailureをReplayし、AIを再呼び出ししない |
| Any state | Same Key / Different Content | `409 IDEMPOTENCY_KEY_CONFLICT` |

Validationで拒否したRequestは、Idempotency処理を開始したものとして記録しない。

### 18.4 Completed Replay

完了済みResultのReplayでは以下を送信する。

```text
stream.started
assistant.completed
```

`assistant.delta`はReplayしない。

### 18.5 Failed Replay

失敗済みResultのReplayでは以下を送信する。

```text
stream.started
stream.failed
```

### 18.6 Persistence Boundary

Idempotency RecordのDynamoDB Model、Access Patternおよび具体的なState Transitionは`database-design.md`で定義する。

ただしClientから見える保証は以下とする。

- Completed Keyは、対応するConversation Historyが存在する間、同一処理を再実行しない
- Failed KeyはTerminal Failureから最低24時間、同じFailureをReplayする
- Processing StateのLease Defaultは5分とする
- Lease中の同一Key Requestには`409 REQUEST_IN_PROGRESS`と`Retry-After: 2`を返す
- Leaseが切れたProcessing Stateは、AIを再呼び出しせずAtomically Failedへ収束させる
- Lease切れによりFailedへ収束したRequestは`REQUEST_INTERRUPTED`をReplayする
- FlutterはFailed Keyを24時間経過後に自動Retryしてはならない

LeaseおよびRetention値はConfiguration可能とする。ただし、Completed Keyの保証をConversation Historyより短くしてはならず、Failed Keyの保持期間を24時間未満にしてはならない。

Failed Keyの保証期間終了後にRecordが削除済みの場合、Backendは同じKeyを新規Requestとして処理する可能性がある。そのためFlutterは、24時間経過後も過去のFailed Keyを自動または結果確認目的で再送してはならない。Userが明示的に新しい送信操作を行った場合は、新しい`Idempotency-Key`を使用する。

### 18.7 requestId on Replay

CompletedまたはFailed ResultをReplayする場合、現在のRetry HTTP Requestに対して新しいUUID `requestId`を生成する。

Response HeaderおよびReplayするすべてのSSE Eventには、現在のHTTP Requestの`requestId`を設定する。

元Requestの`requestId`をReplayしてはならない。

保存済みMessageまたはError Dataから、現在の`requestId`を持つSSE Payloadを再構築する。

---

## 19. Concurrent Send Control

Phase 1の単一Conversationでは、一度に一つの`SendMessageUseCase`のみ実行可能とする。

Alice Response生成中に、異なる`Idempotency-Key`で新しいMessageを送信した場合は以下を返す。

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
```

```json
{
  "type": "urn:project-alice:problem:conversation-busy",
  "title": "Conversation is busy",
  "status": 409,
  "detail": "Aliceが応答中です。完了後にもう一度送信してください。",
  "code": "CONVERSATION_BUSY",
  "requestId": "f1138625-44d8-4946-b665-89a737c577d6"
}
```

FlutterもAlice Response生成中はSend Actionを無効化する。

BackendはClient側制御だけに依存せず、同時実行を必ず拒否する。

同時実行制御のAtomic OperationおよびPersistence実装は`database-design.md`で定義する。

---

## 20. Connection Loss and Retry

### 20.1 Connection Loss Detection

Flutterが`assistant.completed`または`stream.failed`を受信せずConnectionが終了した場合、処理結果は不明とする。

FlutterのSSE Connection切断は、実行中のAI GenerationをCancelする条件ではない。

BackendはClientへのDelta送信を停止してAI Generationを継続し、成功時はCanonical Assistant MessageとCompleted Idempotency Resultを保存する。失敗時はDatabase Designで定義されたTerminal Failureへ収束させる。

これにより、Flutterは同じ`Idempotency-Key`で結果を安全に確認できる。切断後のSSE送信失敗をAI Generation失敗として扱ってはならない。

### 20.2 Retry Rule

Flutterは以下を行ってはならない。

- 新しい`Idempotency-Key`で自動再送する
- 同一User操作から複数のMessage Sendを生成する
- Partial Deltaだけを正常完了として保存する

結果確認または通信再試行には、同じ`Idempotency-Key`を使用する。

BackendはIdempotency Stateに基づき、以下のいずれかを返す。

- `REQUEST_IN_PROGRESS`および`Retry-After: 2`
- 保存済みCompleted Result
- 保存済みFailed Result

Userが明示的に新しい送信操作を実行した場合のみ、新しい`Idempotency-Key`を生成する。

---

## 21. HTTP Problem Details

### 21.1 Format

SSE開始前のAPI ErrorはRFC 9457 Problem Details形式で返す。

```http
Content-Type: application/problem+json
```

```json
{
  "type": "urn:project-alice:problem:validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "入力内容に問題があります。",
  "code": "VALIDATION_ERROR",
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb"
}
```

### 21.2 Common Fields

| Field | Type | Required | Description |
|---|---|---:|---|
| `type` | string | Yes | Problem Type URI |
| `title` | string | Yes | Stable Problem Summary |
| `status` | integer | Yes | HTTP Status Code |
| `detail` | string | Yes | 今回のErrorに関する安全な説明 |
| `code` | string | Yes | Flutterが利用するAlice API Error Code |
| `requestId` | string | Yes | HTTP Request Correlation ID |

`status`は実際のHTTP Status Codeと一致させる。

Flutterは`detail`の文字列解析で処理を分岐せず、`code`を利用する。

### 21.3 Validation Errors

Field Validation Errorでは`errors`を追加する。

```json
{
  "type": "urn:project-alice:problem:validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "入力内容に問題があります。",
  "code": "VALIDATION_ERROR",
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb",
  "errors": [
    {
      "field": "content",
      "code": "NOT_BLANK",
      "message": "メッセージを入力してください。"
    }
  ]
}
```

| Field | Type | Required | Description |
|---|---|---:|---|
| `field` | string | Yes | Invalid Request Field |
| `code` | string | Yes | Field-level Validation Code |
| `message` | string | Yes | Userへ表示可能な説明 |

### 21.4 Error Isolation

External SDK ExceptionをProblem Detailsへ直接変換してはならない。

```text
Infrastructure Exception
        ↓
Application / Domain Error
        ↓
Presentation Error Mapping
        ↓
Problem Details or stream.failed
```

---

## 22. HTTP Error Mapping

| HTTP Status | Code | Typical Condition |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | Empty content、文字数超過、limit範囲外、未知Field |
| `400` | `MALFORMED_REQUEST` | JSON Parse Error |
| `400` | `INVALID_CURSOR` | Cursorが不正または解釈不能 |
| `400` | `IDEMPOTENCY_KEY_REQUIRED` | Idempotency-Key未指定 |
| `400` | `INVALID_IDEMPOTENCY_KEY` | UUID形式ではないIdempotency-Key |
| `404` | `CONVERSATION_NOT_FOUND` | 作成前のConversation取得 |
| `406` | `NOT_ACCEPTABLE` | SSEを受理できないAccept指定 |
| `409` | `CONVERSATION_BUSY` | 別のMessage処理中 |
| `409` | `REQUEST_IN_PROGRESS` | 同一Idempotency-Keyが処理中 |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 同一Keyを異なるContentで再利用 |
| `413` | `PAYLOAD_TOO_LARGE` | HTTP Request Bodyが128 KiBを超過 |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | JSON以外のRequest |
| `500` | `INTERNAL_ERROR` | Unexpected Backend Error |
| `503` | `SERVICE_UNAVAILABLE` | 必要なExternal Capabilityが利用不能 |

### 22.1 Field-level Validation Codes

Phase 1で少なくとも以下を利用する。

| Code | Meaning |
|---|---|
| `REQUIRED` | Required Fieldが存在しない |
| `NOT_BLANK` | EmptyまたはWhitespaceのみ |
| `TOO_LONG` | 最大文字数超過 |
| `OUT_OF_RANGE` | Numeric Valueが許容範囲外 |
| `INVALID_FORMAT` | UUID等の形式不正 |
| `UNKNOWN_FIELD` | 未知のRequest Field |

---

## 23. SSE Error Codes

SSE開始後はHTTP Statusを変更できないため、`stream.failed` Eventを利用する。

| Code | Meaning |
|---|---|
| `RESPONSE_GENERATION_FAILED` | Alice Response生成失敗 |
| `RESPONSE_TIMEOUT` | Response Timeout |
| `MESSAGE_SAVE_FAILED` | Conversation History保存失敗 |
| `REQUEST_INTERRUPTED` | Processing Lease切れ等により処理完了を確認できない |
| `INTERNAL_ERROR` | Unexpected Error |

Provider固有Error CodeをFlutterへ公開してはならない。

失敗時にUser MessageをPersistenceへ残すかどうかは、本API Designでは決定しない。

具体的な保存順序、Partial FailureおよびRecovery Ruleは`database-design.md`で定義する。

---

## 24. Layer and Dependency Rules

### 24.1 Presentation Layer

Presentation Layerは以下を担当する。

- HTTP Request / Response
- Request DTO
- Presentation Validation
- HTTP Status
- Problem Details Mapping
- SSE Event Serialization
- Response Header

SSE Transport固有ClassをApplicationまたはDomainへ渡してはならない。

### 24.2 Application Layer

Application Layerは以下を担当する。

- `SendMessageUseCase`
- `GetConversationUseCase`
- `GetConversationMessagesUseCase`
- Provider-independent Streaming Flow
- Repository Port利用
- `ai.application`の`TextGenerationProvider`および`InputTokenCounter`利用
- Application Error

Application LayerはSpring SSE Class、OpenAI SDK ObjectまたはDynamoDB SDK Objectへ依存してはならない。

### 24.3 Infrastructure Layer

Infrastructure Layerは以下を担当する。

- OpenAI StreamからAlice Internal Streamへの変換
- DynamoDB ModelとCore Modelの変換
- External Exceptionの変換

### 24.4 DTO Separation

API DTOとDomain Modelを原則として分離する。

```text
HTTP Request DTO
       ↓
Application Input
       ↓
Application / Domain
       ↓
Application Output
       ↓
HTTP / SSE Response DTO
```

単純Mappingのためだけに不要なMapper Layerを追加しない。

### 24.5 SSE and Asynchronous Processing Scope

Phase 1のSSEは、1つのHTTP Requestが継続している間にAlice ResponseをStreamingするTransportである。

本APIのSSEおよびClient切断後に同一`SendMessageUseCase`をTerminal Stateまで収束させる処理は、Phase 1でScope外とする自律的な`Complex Async Processing`には該当しない。以下はPhase 1で導入しない。

- Background Job
- Message Queue
- Autonomous Workflow
- Long-running Task Orchestration
- 独立したBackground Jobとして開始・再開する任意の非同期処理

Client切断後のGeneration継続は、開始済みの1回のMessage Sendを完了または失敗へ収束させるためだけに許可する。SSEを実現することだけを理由に、Queue、Job実行基盤または自律処理基盤を追加してはならない。

---

## 25. Requirements Traceability

| Requirement ID | Requirement / User Operation | API / Use Case |
|---|---|---|
| `P1-FR-001` | AliceとTextで会話できる | `POST /api/v1/conversation/messages` / `SendMessageUseCase` |
| `P1-FR-002` | 単一Conversationを継続利用できる | 単数形Endpoint `/api/v1/conversation` |
| `P1-FR-003`, `NFR-001` | Provider-independentにAIを利用できる | APIはProvider固有Modelを公開せず、`SendMessageUseCase`から`AiProvider`を利用 |
| `P1-FR-004` | Aliceとして一貫した回答を生成できる | `SendMessageUseCase`からAI Context / Prompt構築を利用 |
| `P1-FR-005` | Alice Responseを生成途中から表示し、Canonical Resultを確定できる | SSE `assistant.delta` / `assistant.completed` |
| `P1-FR-006` | Conversation Historyを保存できる | `SendMessageUseCase`成功条件 |
| `P1-FR-007` | 保存したConversation Historyを時系列順に確認できる | `GET /api/v1/conversation/messages` / Cursor Pagination |
| `P1-FR-008`, `NFR-006` | Logical Message Sendを安全に再試行・結果確認できる | `Idempotency-Key` / Terminal Result Replay |
| `NFR-003` | Phase 1 Security Boundaryを守る | Local Network Boundary、認証なし、Secret非公開 |
| `NFR-008` | 処理を機密情報なしで追跡できる | `X-Request-Id` / `requestId` |

---

## 26. AI Coding Assistant Implementation Rules

AI Coding Assistantは以下を独自判断で変更してはならない。

- Endpoint Path
- HTTP Method
- Request / Response Field
- Date-Time Format
- Pagination Direction
- Pagination Limit
- SSE Event名
- SSE Event順序
- Idempotency Contract
- HTTP Status Mapping
- Error Code
- Authentication有無
- Conversation Creation方式
- 単一Conversation Scope

AI Coding Assistantは以下を行ってはならない。

- Conversation IDをPath Parameterへ追加する
- Phase 1へConversation一覧APIを追加する
- Phase 1へ独立Conversation作成APIを追加する
- OpenAI ObjectをResponseへ返す
- DynamoDB KeyをCursorとして直接返す
- `SseEmitter`、`Flux`等のTransport型をApplication / Domainへ漏らす
- User Messageへ任意の`role`を指定させる
- User Contentを自動Trimして保存する
- `assistant.completed`をPersistence完了前に送信する
- Terminal Eventなしで正常終了としてConnectionを閉じる
- 新しいIdempotency Keyで自動Retryする

本ドキュメントと矛盾する実装が必要になった場合、実装前にDesign DocumentまたはADRを更新する。

---

## 27. Delegated Detailed Decisions

本ドキュメントは以下の詳細を直接定義せず、各Source of Truthへ委譲する。作成済みDocumentで確定した事項は未決定ではなく、そのDocumentのDecisionに従う。

### 27.1 Database Design

- DynamoDB Table / Key Design
- Conversation / Message Persistence Model
- Message ID / Conversation ID生成方式
- Message保存順序
- AI API失敗時のUser Message保持
- Assistant Message部分保存の可否
- Atomicity / Consistency Strategy
- Cursor Encoding
- Cursor Integrity Validation
- Idempotency Record Model
- Idempotency Recordの物理TTLとCompleted Key Mapping（本APIの保証期間を満たす実装とする）
- Processing Leaseの排他・失効検出・Failedへの収束方式
- Conversation Busy Lock実装

### 27.2 AI Design

次は`ai-design.md`で確定済みであり、本API Designから変更しない。

- OpenAI SDK / ClientおよびModel Configuration
- System PromptとAlice Personality
- Context ConstructionとConversation History Selection
- Input / Output Token Budget
- OpenAI Streaming EventからProvider-independent DeltaへのMapping
- AI Timeout、Retry、CancellationおよびClient Disconnect時のGeneration継続
- `ai` FeatureのCapability Port Contract

### 27.3 Security Design

- Secret Management
- Conversation Content Logging Policy
- Error DetailのSecurity Review
- LAN Access時の追加Header / CORS
- Cloud / Remote Access開始時のAuthentication

### 27.4 Test Design

- API Unit Test
- Controller Integration Test
- SSE Event Order Test
- Timeout Test
- Disconnect Test
- Idempotency Test
- Cursor Pagination Test
- OpenAI Fake / Mock Strategy
- DynamoDB Test Environment

作成済みの`test-design.md`を、Test Framework、Mock Server、DynamoDB Test Environment、実行CommandおよびCI GateのSource of Truthとする。上記のAPI / SSE Test Inputは同Documentへ反映済みであり、実装時は両Documentを一致させる。

委譲先で未確定の事項をAI Coding Assistantが推測で確定してはならない。

---

## 28. Review Checklist

### 28.1 Requirement Alignment

- [x] Phase 1 RequirementsとAPIが対応している
- [x] MVP Scope外のAPIが追加されていない
- [x] Conversation HistoryとPersonal Memoryを混同していない

### 28.2 Contract Consistency

- [x] Endpoint、Method、Statusが一貫している
- [x] Message Schemaが全APIで共通である
- [x] Date-TimeがJST Offset付きである
- [x] Pagination Invariantが維持される
- [x] SSE EventとTerminal Ruleが一貫している
- [x] HTTP ErrorとSSE ErrorのBoundaryが明確である

### 28.3 Architecture Alignment

- [x] Application / DomainがHTTP Transportへ依存していない
- [x] OpenAI固有ModelがAPIへ漏れていない
- [x] DynamoDB固有ModelがAPIへ漏れていない
- [x] AI Capability BoundaryがADR-014に従い独立した`ai` Feature所有である
- [x] Phase 1でPersonal Memory APIを実装していない

### 28.4 Reliability and Security

- [x] Idempotencyの保証期間が定義され、重複AI呼び出しを防止する
- [x] Processing Leaseにより`REQUEST_IN_PROGRESS`が永続しない
- [x] 同時送信がBackendでも拒否される
- [x] Applicationが制御できる完了時はTerminal Eventを1つだけ送信する
- [x] Transport切断によりTerminal Eventを受信できない場合を成功扱いしない
- [x] Cacheが禁止されている
- [x] Secret、Stack Trace、SDK Errorを返していない
- [x] BackendがPublic Internetへ公開されていない

---

## 29. References

- RFC 9457: Problem Details for HTTP APIs
- Server-Sent Events
- `requirements.md`
- `mvp.md`
- `backend-design.md`
- `database-design.md`
- `ai-design.md`
- `security-design.md`
- `test-design.md`
- `decisions.md`

---

## 30. Summary

Phase 1 APIは、単一Conversationを対象とする3つのEndpointで構成する。

```text
GET  /api/v1/conversation
GET  /api/v1/conversation/messages
POST /api/v1/conversation/messages
```

Alice ResponseはSSEでStreamingする。

```text
stream.started
assistant.delta
assistant.completed | stream.failed
```

Conversationは最初のMessage送信時に自動作成する。

Conversation History取得にはCursor Paginationを使用する。

重複送信は`Idempotency-Key`で防止し、HTTP処理は`requestId`で追跡する。

API ContractはOpenAIおよびDynamoDB固有Modelから分離する。

Database、AI、SecurityおよびTestの詳細は各Design Documentで決定し、本API Designが未決定のInfrastructure Detailを先取りしない。
