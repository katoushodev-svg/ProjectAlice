# Project Alice - API Design

## 1. Document Information

| Item | Value |
|---|---|
| Document | `api-design.md` |
| Project | Project Alice |
| Target | Phase 1–2 Backend API |
| Status | Phase 1: Approved / Phase 2: Reviewed — Implementation Ready |
| Last Updated | 2026-09-01 JST |

本ドキュメントは、Project Alice Phase 1–2におけるFlutter ClientとSpring Boot Backend間のAPI Contractを定義する。Phase 1 ContractはApprovedであり、Phase 2 ContractはCR-001〜CR-009の横断レビュー、Requirement TraceabilityおよびUser Approvalを完了したImplementation Baselineである。実装完了またはRelease Readyを意味せず、実装時は本Contractと関連Design Documentを変更せず遵守する。

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
data: {"requestId":"9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb","userMessage":{"id":"4d5e6f70-8192-4abc-8def-0123456789ab","role":"user","content":"こんにちは","createdAt":"2026-08-20T23:00:00.000+09:00"}}
```

```json
{
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb",
  "userMessage": {
    "id": "4d5e6f70-8192-4abc-8def-0123456789ab",
    "role": "user",
    "content": "こんにちは",
    "createdAt": "2026-08-20T23:00:00.000+09:00"
  }
}
```

| Field | Type | Required | Description |
|---|---|---:|---|
| `requestId` | string | Yes | 現在のHTTP Requestの識別子 |
| `userMessage` | Message | Yes | Start Transactionで保存済みのCanonical User Message |

`stream.started`はUser MessageのPersistence完了とIdempotency Stateの`PROCESSING`開始を意味する。Assistant Messageの生成またはPersistence完了は意味しない。

FlutterはPending User Messageを`userMessage`へ置換する。Pending ContentとCanonical `userMessage.content`が完全一致しない場合はProtocol Errorとし、本文一致を推測して補正してはならない。

Completed / Failed Replayでも`stream.started`へ保存済みCanonical User Messageを含める。`assistant.completed`は従来どおり同じCanonical User Messageを再度含め、FlutterはMessage IDおよびField一致を確認する。

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

---

## 31. Phase 2 Personal Memory API — Boundary and Endpoint Baseline

### 31.1 Status and Purpose

| Item | Value |
|---|---|
| Status | Accepted |
| Implementation | Not Started |
| Review Required After Phase 2–4 Design | Yes |

本Sectionは、Phase 2のMemory管理画面とBackend間で公開するHTTP APIの境界、および既存Conversation APIとPersonal Memoryの接続方針を定義する。

初心者向けに整理すると、HTTP APIへ公開するのは「ユーザーがMemoryを見て管理するための入口」である。Conversation中の自動保存候補抽出や回答用Memory検索まで、Backend内部の処理を一つずつHTTP Endpointとして公開するわけではない。

```text
iOS Memory Management UI
        │ HTTP API
        ▼
Memory Presentation
        ▼
Memory Application Use Cases

Conversation Use Case
        │ In-process Application Boundary
        ▼
Memory Application Use Cases
```

### 31.2 API Version and Resource Ownership

Phase 2でもBase Pathは`/api/v1`を継続する。Phase 2の追加は新しいResourceの追加であり、Approved済みPhase 1 Contractの破壊的変更ではないため、新しいAPI Major Versionを作成しない。

Single-user環境を前提とするため、Pathへ`userId`を含めない。将来Multi-user化する場合、AuthenticationされたPrincipalとResource Ownershipの設計を先に確定し、Client指定の`userId`だけで所有者を決めない。

### 31.3 Public Endpoint Baseline

| Method | Endpoint | Main Use Case | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/memories` | `ListMemoriesUseCase` | Category、StateおよびCursorによる一覧取得 |
| `POST` | `/api/v1/memories/search` | `ListMemoriesUseCase` | Memory本文を含み得る検索条件による管理検索 |
| `POST` | `/api/v1/memories` | `RegisterMemoryUseCase` | 管理画面からの明示登録 |
| `GET` | `/api/v1/memories/{memoryId}` | `GetMemoryUseCase` | Memory詳細取得 |
| `PATCH` | `/api/v1/memories/{memoryId}` | `UpdateMemoryUseCase` | Content、CategoryまたはStateの部分更新 |
| `POST` | `/api/v1/memories/{memoryId}/confirm` | `ConfirmMemoryUseCase` | Contentを変更しない明示確認 |
| `DELETE` | `/api/v1/memories/{memoryId}` | `DeleteMemoryUseCase` | 一意に特定したMemoryの個別削除 |
| `GET` | `/api/v1/memory-preferences` | `GetMemoryPreferencesUseCase` | Memory設定取得 |
| `PATCH` | `/api/v1/memory-preferences` | `UpdateMemoryPreferencesUseCase` | 自動保存・回答利用設定更新 |
| `GET` | `/api/v1/conversation/messages/{messageId}/memory-usage` | `ExplainMemoryUsageUseCase` | 指定Alice回答でContextへ含めたMemoryの説明 |
| `POST` | `/api/v1/memory-deletion-plans` | `PreviewMemoryDeletionUseCase` | 複数または全削除の対象Snapshotと確認情報を作成 |
| `GET` | `/api/v1/memory-deletion-plans/{deletionPlanId}` | `PreviewMemoryDeletionUseCase` | 削除対象と有効期限を再確認 |
| `POST` | `/api/v1/memory-deletion-plans/{deletionPlanId}/confirm` | `ConfirmMemoryDeletionPlanUseCase` | Review済みPlanの短命Confirmation Tokenを発行 |
| `POST` | `/api/v1/memory-deletion-plans/{deletionPlanId}/execute` | `DeleteMemoriesUseCase` | Previewで固定した対象を確認後に削除 |
| `POST` | `/api/v1/memory-backups/export` | `ExportMemoryBackupUseCase` | 暗号化Backup Archiveを生成 |
| `POST` | `/api/v1/memory-restore-plans` | `InspectMemoryBackupUseCase` | Archiveを検証しRestore Planを作成 |
| `GET` | `/api/v1/memory-restore-plans/{restorePlanId}` | `GetMemoryRestorePlanUseCase` | Restore PlanとAction Pageを取得 |
| `PATCH` | `/api/v1/memory-restore-plans/{restorePlanId}` | `ResolveMemoryRestorePlanUseCase` | Resolution、Preferencesおよび確認状態を更新 |
| `POST` | `/api/v1/memory-restore-plans/{restorePlanId}/confirm` | `ConfirmMemoryRestorePlanUseCase` | Review済みPlanの短命Confirmation Tokenを発行 |
| `POST` | `/api/v1/memory-restore-plans/{restorePlanId}/cancel` | `CancelMemoryRestorePlanUseCase` | Review中Planを明示CancelしTemporary StateをCleanup |
| `POST` | `/api/v1/memory-restore-plans/{restorePlanId}/execute` | `ExecuteMemoryRestoreUseCase` | 確認済みRestore Planを実行 |
| `GET` | `/api/v1/memory-relation-reviews/{relationReviewId}` | `GetMemoryRelationReviewUseCase` | Candidate、関連Memory PageおよびAllowed Resolutionを取得 |
| `POST` | `/api/v1/memory-relation-reviews/{relationReviewId}/resolve` | `ResolveMemoryRelationUseCase` | ReviewへBinding済みのUser Resolutionを一度だけ実行 |

### 31.4 Why Management Text Search Uses POST

Memoryの検索語には人物、悩み、嗜好等の個人情報が含まれ得る。`GET /memories?q=...`のように検索本文をURLへ含めると、Browser History、ProxyまたはAccess Logへ残る可能性が高くなる。

そのため次を分ける。

- Category、State、Cursor等、本文を含まないFilterは`GET /memories`を使う。
- 自由入力の検索語は`POST /memories/search`のJSON Bodyへ入れる。
- BackendはどちらのRequestについてもMemory本文や検索語を通常Logへ出力しない。

`POST /memories/search`はDataを変更しないQuery Endpointである。ここではHTTP Methodの純粋さより、個人情報をURLへ載せないことを優先する。

### 31.5 Internal-only Application Boundaries

次はPhase 2で必要だが、Flutter向けHTTP Endpointとして公開しない。

| Capability | Caller | Reason |
|---|---|---|
| Answer-time関連Memory検索 | `SendMessageUseCase` | Backend内部のContext構築処理である |
| ConversationからのMemory Candidate抽出 | Conversation完了後のApplication Flow | User向けResource操作ではない |
| Relation Decision | Register / Capture / Update Flow | Domain判断の内部Stepである |
| Re-registration Guard照合 | Save Policy | Guard情報をClientへ漏らさない |
| Search Projection再構築 | Repair / Operation Flow | Infrastructure運用処理である |
| Memory Usage Trace記録 | Conversation / AI Flow | Trace生成をClient入力で偽装させない |

自然言語による「覚えて」「忘れて」は既存の`POST /api/v1/conversation/messages`から受け付ける。Conversation Applicationは意図を解釈した後、HTTPを自己呼出しせず、Memory Application BoundaryをProcess内で利用する。

### 31.6 Concurrency Baseline

`PersonalMemory.version`をHTTPの`ETag`へ対応付ける。

```http
ETag: "memory-7f1b-v3"
```

Memoryを変更する`PATCH`、`DELETE`および`confirm`では、直前に取得した`ETag`を`If-Match` Headerで必須送信する。

```http
If-Match: "memory-7f1b-v3"
```

| Condition | Result |
|---|---|
| `If-Match`なし | `428 Precondition Required` |
| 現在Versionと一致 | Mutationを実行 |
| 現在Versionと不一致 | `412 Precondition Failed` |

`ETag`は「自分が見た後に別の更新がなかったか」を確認する印である。これにより、iOSと将来のPC UIが同じMemoryを開いている場合に、古い画面から新しい変更を上書きすることを防ぐ。

Memory Preferencesにも同じ考え方を適用する。具体的なPreference Version表現はSchema設計で確定する。

### 31.7 Idempotency Baseline

再送による重複作成・重複削除を防ぐため、次のEndpointではUUID形式の`Idempotency-Key`を必須とする。

- `POST /api/v1/memories`
- `POST /api/v1/memory-deletion-plans`
- `POST /api/v1/memory-deletion-plans/{deletionPlanId}/execute`
- `POST /api/v1/memory-restore-plans`
- `POST /api/v1/memory-restore-plans/{restorePlanId}/cancel`

同じKeyと同じRequestは同じ論理結果へ収束させる。同じKeyを異なるRequest Bodyで再利用した場合は`409 IDEMPOTENCY_KEY_CONFLICT`とする。保存期間、Processing LeaseおよびPersistence ModelはDatabase詳細設計で確定する。

Read-only Queryおよび`If-Match`で保護される単一Resource Mutationへは、不要なIdempotency-Keyを要求しない。

### 31.8 Deletion Plan Baseline

複数削除と全削除は、対象確認と実行を分離する。

```text
Deletion条件を送信
      ↓
Deletion Plan作成
      ↓
対象件数・対象Summary・有効期限を確認
      ↓
同じDeletion PlanをExecute
```

Deletion Planは作成時点の`memoryId`とExpected Versionを固定する。Execute時に対象を検索し直して増減させない。

次の場合は実行しない。

- Planが期限切れ
- 対象MemoryのVersionが変わった
- Planが既に別の論理結果へ完了している
- Reset Generationまたは削除境界が変わった

Individual Deleteは、対象が一意で最新Versionを`If-Match`で確認できるためDeletion Planを必須としない。UIからの個別削除確認はFrontend UXとして別途行う。

### 31.9 Pagination and Ordering Baseline

Memory一覧、Memory検索およびDeletion Planの対象表示にはOpaque Cursor Paginationを使用する。

| Rule | Baseline |
|---|---|
| Default `limit` | `20` |
| Maximum `limit` | `100` |
| Cursor | Clientが解析・生成しないOpaque Value |
| Default Ordering | `updatedAt`の新しい順。Tieは`memoryId`の安定順 |
| Filter Binding | Cursorを生成したFilter / Search条件以外へ再利用不可 |
| Invalid / Modified Cursor | `400 INVALID_CURSOR` |

Response Size上限へ先に到達した場合は、指定件数より少ない完全なMemory ItemでPageを終了する。Memory Contentを途中で切って返さない。

### 31.10 Common Security and Cache Rules

Phase 1の共通RuleをMemory APIにも適用する。

- `Cache-Control: no-store`
- Backend生成の`X-Request-Id`
- JSONはUTF-8
- Errorは`application/problem+json`
- Unknown Request Fieldは`400 VALIDATION_ERROR`
- OpenAI / AWS / DynamoDB固有情報をResponseへ公開しない
- Memory本文、検索語、Sensitive情報、削除GuardおよびConfirmation内部情報を通常Logへ出力しない

Phase 2はSingle-user Local Developmentを維持し、Section 41のNetwork Security Profileを適用する。

- Defaultの`LOOPBACK_ONLY`は`127.0.0.1`へ限定し、Authenticationを要求しない。
- 実機iPhoneまたは別Machine上のDesktop Clientから接続する`PRIVATE_LAN_SECURE`では、公開する全`/api/v1/**`へTLSとAllowed Device Authenticationを必須とする。
- CredentialはiOS / macOS Keychain、Windows Credential Manager等、各PlatformのSecure Storageへ保存する。
- Private LANで安全な構成を成立させられない場合、AuthenticationなしへFallbackせず起動または接続を拒否する。

### 31.11 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-001 | Phase 2 Memory APIを既存`/api/v1`へ追加し、Phase 1 Contractを変更しない | Accepted |
| API2-002 | Single-userのためMemory Pathへ`userId`を含めない | Accepted |
| API2-003 | Memory管理用HTTP APIとConversation内部のMemory Application Boundaryを分離する | Accepted |
| API2-004 | 自由入力Memory検索を`POST /api/v1/memories/search`とし、検索語をURLへ含めない | Accepted |
| API2-005 | Memory MutationでVersion由来の`ETag`と`If-Match`を使用する | Accepted |
| API2-006 | `If-Match`欠落を`428`、Version不一致を`412`とする | Accepted |
| API2-007 | Create、Deletion Plan作成およびDeletion実行で`Idempotency-Key`を必須とする | Accepted |
| API2-008 | 複数・全削除をDeletion PlanのPreviewとExecuteへ分離する | Accepted |
| API2-009 | Deletion Plan作成時に対象Memory IDとExpected Versionを固定する | Accepted |
| API2-010 | 一覧・検索・削除対象表示にOpaque Cursor Paginationを使用する | Accepted |
| API2-011 | 自然言語Memory操作はConversation APIからMemory Application Boundaryへ接続し、専用自然言語Endpointを作らない | Accepted |
| API2-012 | Backup / Restore EndpointをBackup詳細設計後に確定する | Accepted |

### 31.12 Next API Design Topics

本Baseline承認後、次の順序で詳細化する。

1. Common Memory Resource SchemaとField Validation
2. List / Search Response、FilterおよびCursor Contract
3. Register / Get / Update / Confirm / Individual Delete Contract
4. Memory Preferences Contract
5. Memory Usage Transparency Contract
6. Deletion Plan Preview / Execute Contract
7. Phase 2 Problem DetailsとError Mapping
8. Response / Request Size Limits
9. Backup / Restore設計後のAPI追加
10. Phase 2 API Design Review

---

## 32. Phase 2 Common Memory Resource Schema and Validation

### 32.1 Design Status

| Item | Value |
|---|---|
| Status | Accepted |
| Implementation | Not Started |
| Depends On | API2-001〜API2-012 Accepted、`memory-design.md` MD-009〜MD-021 Accepted |

本Sectionは、Memory管理APIで共通利用する`MemoryResource`と、HTTP入力からDomain Validationへ進むまでの共通Validationを定義する。

API ResponseはDomain Entity、DynamoDB Itemまたは検索ProjectionをそのままSerializeしない。ClientがMemoryを理解・管理するために必要なFieldだけを、ProviderおよびPersistenceから独立したContractとして返す。

### 32.2 `MemoryResource`

```json
{
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "content": "仕事ではJavaを使用している。",
  "category": "ENGINEERING",
  "captureType": "EXPLICIT",
  "sensitivityLevel": "NORMAL",
  "state": "ACTIVE",
  "version": 1,
  "etag": "\"memory-7f1f8e2a-v1\"",
  "createdAt": "2026-08-27T21:30:00.000+09:00",
  "updatedAt": "2026-08-27T21:30:00.000+09:00",
  "confirmedAt": "2026-08-27T21:30:00.000+09:00"
}
```

| Field | Type | Required | Nullable | Description |
|---|---|---:|---:|---|
| `memoryId` | string | Yes | No | Backend生成のOpaque Memory ID |
| `content` | string | Yes | No | ユーザーが管理画面で理解できるCanonical Memory本文 |
| `category` | string enum | Yes | No | Section 32.5のTop-level Category Code |
| `captureType` | string enum | Yes | No | Memoryの生成経路。`EXPLICIT`または`AUTOMATIC` |
| `sensitivityLevel` | string enum | Yes | No | `NORMAL`または`SENSITIVE` |
| `state` | string enum | Yes | No | `ACTIVE`または`RESOLVED` |
| `version` | integer | Yes | No | 1から始まる単調増加Version。最大値はSigned 64-bit範囲 |
| `etag` | string | Yes | No | 現在Versionに対応するOpaque Strong Entity Tag。HTTP Headerへそのまま使用できる値 |
| `createdAt` | string | Yes | No | 初回保存日時 |
| `updatedAt` | string | Yes | No | 管理対象情報の最終変更日時 |
| `confirmedAt` | string | Yes | Yes | 最後の明示登録・編集・肯定日時。未確認は`null` |

`confirmedAt`は、未確認時にField自体を省略せず、明示的に`null`を返す。これにより、Flutterが「未取得」と「未確認」を推測で区別する必要をなくす。

```json
{
  "confirmedAt": null
}
```

### 32.3 ETag and Version Representation

`version`と`etag`は目的を分ける。

| Value | Purpose |
|---|---|
| `version` | UI表示、診断およびResponse間のVersion比較 |
| `etag` | `If-Match`へ渡すConcurrency Token |

Clientは`version`または`memoryId`から`etag`を組み立てない。Responseの`etag`文字列を変更せず、Mutation Requestの`If-Match`へ設定する。

単一Memoryを返すResponseでは、HTTP `ETag` HeaderとBodyの`etag`を一致させる。

```http
ETag: "memory-7f1f8e2a-v1"
```

一覧・検索Responseでは各Itemへ`etag`を含める。Collection Response全体の`ETag`を、個別Memory Mutationの`If-Match`へ使用してはならない。

Mutation成功後は更新後の`version`と`etag`を返し、Clientは古い値を破棄する。

### 32.4 Timestamp Contract

Memory日時はPhase 1の共通日時方針と同じく、JST Offset付きISO 8601、ミリ秒3桁固定とする。

```text
2026-08-27T21:30:00.000+09:00
```

ResponseではUTC、Offsetなし日時またはミリ秒以外の精度を返さない。

次のInvariantを満たす。

```text
createdAt <= updatedAt
confirmedAt is null OR createdAt <= confirmedAt
```

`confirmedAt`が`updatedAt`より新しくなることは許可する。内容を変更しない`ConfirmMemoryUseCase`では、`confirmedAt`と`version`を更新するが、内容変更日時である`updatedAt`を変更しないためである。

### 32.5 Stable Enum Codes

#### Category

```text
PROFILE
PREFERENCE
PERSON
LIFE_CONTEXT
PLACE
ENGINEERING
PROJECT
OTHER
```

#### Capture Type

```text
EXPLICIT
AUTOMATIC
```

#### Sensitivity Level

```text
NORMAL
SENSITIVE
```

#### State

```text
ACTIVE
RESOLVED
```

Requestでは大文字・小文字を区別し、上記Codeとの完全一致を要求する。未知Code、表示Label、日本語Labelおよび空文字を受理しない。未知Categoryを`OTHER`へ、未知Stateを`ACTIVE`へ変換しない。

Responseへ将来新しいEnum Codeを追加する場合は、Client Compatibility、ArchiveおよびMigrationを事前確認する。既存Codeの意味を変更または再利用しない。

### 32.6 Server-owned and Client-writable Fields

次はServer-owned Fieldであり、Create / Update Request BodyでClientに指定させない。

- `memoryId`
- `captureType`
- `version`
- `etag`
- `createdAt`
- `updatedAt`
- `confirmedAt`

管理画面から登録したMemoryの`captureType`はBackendが`EXPLICIT`に設定する。自動保存Memoryを編集または確認しても`captureType`を`EXPLICIT`へ変更しない。

Clientが変更を要求できるFieldは、Endpoint別Contractで許可された場合の次のFieldに限定する。

- `content`
- `category`
- `sensitivityLevel`
- `state`

具体的なRequired / Optional、DefaultおよびPATCH Semanticsは、Register / Update Contractで確定する。Endpointで許可されていないFieldや未知Fieldは黙って無視せず`400 VALIDATION_ERROR`とする。

### 32.7 Memory Content Canonicalization

HTTP入力の`content`は次の順でCanonicalizeしてから、長さ、IdempotencyおよびDomain Validationへ利用する。

1. `CRLF`および単独`CR`を`LF`へ統一する。
2. UnicodeをNFCへ正規化する。
3. 先頭と末尾のUnicode Whitespaceを除去する。
4. Canonicalized Contentに対してCode Point数とUTF-8 Byte数を検証する。

内部の空白または改行を一律にCollapseしない。内容を上限へ合わせて切り詰めない。正規化後の値が元の意味を変える可能性がある場合、推測で書換えずValidation ErrorまたはUser修正へ戻す。

### 32.8 Content Limits

| Constraint | Limit |
|---|---:|
| Minimum after canonicalization | 1 Unicode Code Point |
| Maximum | 2,000 Unicode Code Point |
| Maximum UTF-8 Size | 8 KiB（8,192 Byte） |

両方の最大値を同時に適用する。Unicode Code Point上限内でもUTF-8 Byte上限を超える場合は拒否する。

`NUL`、C0 / C1 Control Characterは改行`LF`を除いて拒否する。Memory本文を複数の無関係な事実、Conversation TranscriptまたはDocument全文として保存しないことはDomain Validationでも検証する。

### 32.9 Validation Layers and Order

```text
HTTP / JSON Validation
        ↓
Canonicalization
        ↓
Field Constraint Validation
        ↓
Application / Domain Validation
        ↓
Save Policy・Relation Decision・Persistence
```

Presentation Validationは、JSON形式、Required Field、未知Field、型、Enum、文字数およびByte数等を扱う。

Application / Domain Validationは、Secret、保存禁止情報、Atomicity、Categoryの妥当性、センシティブ性、再登録防止および既存Memoryとの関係を扱う。

HTTP形式上Validであることを、保存可能という意味にしてはならない。特にSecretは、文字数やEnumが正しくても保存を拒否する。Secret検出結果や一致した文字列をError Responseまたは通常Logへ含めない。

### 32.10 Common Field Validation Codes

Phase 1のField-level Validation Codeに加え、Phase 2 Memory APIで少なくとも次を使用する。

| Code | Meaning |
|---|---|
| `TOO_LARGE` | UTF-8 Byte上限超過 |
| `INVALID_ENUM` | 許可されていないEnum Code |
| `INVALID_CHARACTER` | 許可されていないControl Characterを含む |
| `IMMUTABLE_FIELD` | ClientがServer-owned Fieldを指定した |

同一Fieldが複数Ruleへ違反した場合のError順序と、Domain ErrorのHTTP Status / CodeはPhase 2 Problem Details設計で確定する。Responseへ拒否対象のMemory本文をEchoしない。

### 32.11 Resource Schema Non-fields

`MemoryResource`へ次を含めない。

- Memory Revision History
- Conversation Message本文またはSource Message ID
- Candidate抽出結果・AIの推論・Confidence
- Relation Score・検索Score・Embedding
- Re-registration Guard、Keyed DigestまたはSemantic Key
- Memory Reset Generation
- Confirmation Bindingまたは内部Token
- DynamoDB Partition Key / Sort Key
- AI Provider、ModelまたはSDK固有Field
- Backup暗号化情報
- User IDまたはOwner ID

Revision、Memory Usage、Deletion PlanおよびBackupで必要な情報は、それぞれの専用Contractで定義する。一つの巨大Responseへ内部情報を集約しない。

### 32.12 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-013 | 共通`MemoryResource`を管理APIのCanonical Response Schemaとする | Accepted |
| API2-014 | `confirmedAt`をRequired Nullable Fieldとし、未確認を`null`で表す | Accepted |
| API2-015 | `version`をBodyへ、Opaque Strong Entity Tagを`etag`へ含め、単一ResourceのHTTP `ETag` Headerと一致させる | Accepted |
| API2-016 | Clientは`etag`を組み立てず、Response値を変更せず`If-Match`へ使用する | Accepted |
| API2-017 | Server-owned FieldをRequestで指定させず、Endpointごとに許可した管理対象Fieldだけを受理する | Accepted |
| API2-018 | Category、Capture Type、SensitivityおよびStateを大文字小文字を区別するStable Enum Codeで表す | Accepted |
| API2-019 | Contentを改行統一、Unicode NFCおよび外側Whitespace除去後に検証する | Accepted |
| API2-020 | Content上限を2,000 Unicode Code Pointかつ8 KiBとし、切詰めや内部Whitespace Collapseを行わない | Accepted |
| API2-021 | HTTP Field ValidationとApplication / Domain Validationを分離し、Secret等をField形式だけで保存可能と判断しない | Accepted |
| API2-022 | Memory ResourceからRevision、Source、AI判断、検索Score、削除GuardおよびInfrastructure情報を除外する | Accepted |

### 32.13 Next API Design Topics

API2-013〜API2-022承認後、次の順序で詳細化する。

1. List / Search Response、FilterおよびCursor Contract
2. Register / Get / Update / Confirm / Individual Delete Contract
3. Memory Preferences Contract
4. Memory Usage Transparency Contract
5. Deletion Plan Preview / Execute Contract
6. Phase 2 Problem DetailsとError Mapping
7. Response / Request Size Limits
8. Backup / Restore設計後のAPI追加
9. Phase 2 API Design Review

---

## 33. Phase 2 Memory List and Management Search Contract

### 33.1 Design Status

| Item | Value |
|---|---|
| Status | Accepted |
| Implementation | Not Started |
| Depends On | API2-001〜API2-022 Accepted |

本Sectionは、管理画面でMemoryを一覧表示・絞込み・自由入力検索するContractを定義する。回答生成時の関連Memory検索、重複判定検索および検索Infrastructureの内部APIは対象外とする。

```text
管理画面の分類・状態Filter
        ↓
GET /api/v1/memories

Memory本文を対象にした自由入力検索
        ↓
POST /api/v1/memories/search
```

両Endpointは同じCollection Envelopeと`MemoryResource`を返すが、検索目的と並び順を区別する。

### 33.2 Collection Response Envelope

一覧と検索は次の共通Envelopeを使用する。

```json
{
  "memories": [
    {
      "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
      "content": "仕事ではJavaを使用している。",
      "category": "ENGINEERING",
      "captureType": "EXPLICIT",
      "sensitivityLevel": "NORMAL",
      "state": "ACTIVE",
      "version": 1,
      "etag": "\"memory-7f1f8e2a-v1\"",
      "createdAt": "2026-08-27T21:30:00.000+09:00",
      "updatedAt": "2026-08-27T21:30:00.000+09:00",
      "confirmedAt": "2026-08-27T21:30:00.000+09:00"
    }
  ],
  "nextCursor": "opaque-cursor-value",
  "hasMore": true
}
```

| Field | Type | Required | Nullable | Description |
|---|---|---:|---:|---|
| `memories` | array of `MemoryResource` | Yes | No | 現在Pageの完全なMemory Resource |
| `nextCursor` | string | Yes | Yes | 次Page取得用Opaque Cursor。続きがなければ`null` |
| `hasMore` | boolean | Yes | No | 次Pageが存在するか |

次のInvariantを維持する。

```text
hasMore = true  → nextCursor is non-null AND memories is non-empty
hasMore = false → nextCursor is null
```

空の結果はErrorにせず`200 OK`とする。

```json
{
  "memories": [],
  "nextCursor": null,
  "hasMore": false
}
```

`totalCount`および`estimatedTotalCount`はPhase 2で返さない。Concurrent Mutation下で一貫しない件数を表示することと、検索Infrastructureへ不要なCount負荷を要求することを避ける。

### 33.3 List Memories Request

```http
GET /api/v1/memories?category=ENGINEERING&category=PROJECT&state=ACTIVE&limit=20
```

| Query Parameter | Type | Required | Default | Constraint |
|---|---|---:|---|---|
| `category` | repeated enum | No | All | Section 32.5のCategory。最大8種類 |
| `state` | repeated enum | No | All | `ACTIVE`、`RESOLVED`。最大2種類 |
| `captureType` | repeated enum | No | All | `EXPLICIT`、`AUTOMATIC`。最大2種類 |
| `sensitivityLevel` | repeated enum | No | All | `NORMAL`、`SENSITIVE`。最大2種類 |
| `confirmationStatus` | repeated enum | No | All | `CONFIRMED`、`UNCONFIRMED`。最大2種類 |
| `limit` | integer | No | `20` | `1`〜`100` |
| `cursor` | string | No | none | Opaque Cursor、最大2,048 ASCII Character |

同一Dimension内の値はOR、異なるDimension間はANDで評価する。

```text
category = ENGINEERING OR PROJECT
AND
state = ACTIVE
```

同じ値の重複指定、空値、未知Enum、同一Parameterの上限超過および未知Query Parameterは`400 VALIDATION_ERROR`とする。Filterの指定順序は意味を持たず、BackendがCanonicalな集合として扱う。

Filter未指定は「全値」を意味する。暗黙に`ACTIVE`、`NORMAL`または`EXPLICIT`だけへ限定しない。`RESOLVED`および`SENSITIVE`も管理画面から確認可能にする。

`confirmationStatus`は保存Fieldではなく、次のDerived Filterである。

```text
CONFIRMED   → confirmedAt is non-null
UNCONFIRMED → confirmedAt is null
```

### 33.4 List Ordering

一覧の順序を固定する。

```text
updatedAt DESC
memoryId ASC
```

同じ`updatedAt`のMemoryは`memoryId`で安定順序を決める。Phase 2ではClient指定Sort、Category順およびContent辞書順を導入しない。

`ConfirmMemoryUseCase`で`confirmedAt`だけが変わった場合、確認操作だけを理由に一覧先頭へ移動させない。Content、Category、SensitivityまたはStateが変更され`updatedAt`が更新された場合は新しい位置へ移動する。

### 33.5 Management Search Request

```http
POST /api/v1/memories/search
Content-Type: application/json
```

```json
{
  "query": "Javaの経験",
  "filters": {
    "categories": ["ENGINEERING", "PROJECT"],
    "states": ["ACTIVE"],
    "captureTypes": ["EXPLICIT", "AUTOMATIC"],
    "sensitivityLevels": ["NORMAL", "SENSITIVE"],
    "confirmationStatuses": ["CONFIRMED", "UNCONFIRMED"]
  },
  "limit": 20
}
```

次Pageでは同じ`query`、同じ`filters`および同じ`limit`へ、Responseの`nextCursor`を追加する。

```json
{
  "query": "Javaの経験",
  "filters": {
    "categories": ["ENGINEERING", "PROJECT"],
    "states": ["ACTIVE"]
  },
  "limit": 20,
  "cursor": "opaque-cursor-value"
}
```

| Field | Type | Required | Default | Constraint |
|---|---|---:|---|---|
| `query` | string | Yes | none | Canonicalization後1〜500 Unicode Code Point、最大2 KiB |
| `filters` | object | No | All | Section 33.6のFilter Object |
| `limit` | integer | No | `20` | `1`〜`100` |
| `cursor` | string | No | none | Opaque Cursor、最大2,048 ASCII Character |

`query`が空、空白のみまたは省略された場合は自由入力検索として受理しない。検索語なしの絞込みには`GET /api/v1/memories`を使用する。

### 33.6 Search Filter Object

| Field | Type | Required | Constraint |
|---|---|---:|---|
| `categories` | array of enum | No | 1〜8、重複不可 |
| `states` | array of enum | No | 1〜2、重複不可 |
| `captureTypes` | array of enum | No | 1〜2、重複不可 |
| `sensitivityLevels` | array of enum | No | 1〜2、重複不可 |
| `confirmationStatuses` | array of enum | No | 1〜2、重複不可 |

省略したDimensionは全値を意味する。空配列は「全値」または「結果なし」へ暗黙変換せず、`400 VALIDATION_ERROR`とする。

GETと同じく、配列内はOR、Dimension間はANDで評価する。配列順序は意味を持たない。未知Field、未知Enum、重複値および上限超過を受理しない。

### 33.7 Search Query Canonicalization and Limits

検索語は次の順でCanonicalizeする。

1. `CRLF`および単独`CR`を`LF`へ統一する。
2. UnicodeをNFCへ正規化する。
3. 先頭と末尾のUnicode Whitespaceを除去する。
4. 文字数とUTF-8 Byte数を検証する。

| Constraint | Limit |
|---|---:|
| Minimum after canonicalization | 1 Unicode Code Point |
| Maximum | 500 Unicode Code Point |
| Maximum UTF-8 Size | 2 KiB（2,048 Byte） |

検索語をResponse、通常Log、Access Logまたは平文Cursorへ含めない。内部Whitespaceを無条件にCollapseせず、検索AdapterがTokenization等に必要な派生表現を作成する場合も元のRequestを通常Logへ残さない。

### 33.8 Search Result and Ordering

検索ResponseもSection 33.2のCollection Envelopeを使用し、各Itemは完全な`MemoryResource`とする。

Phase 2では次をResponseへ返さない。

- Relevance Score
- Keyword Hit Count
- Embedding Distance
- Provider固有Ranking理由
- Contentを複製したHighlight / Snippet

Memory本文は最大2,000 Code Pointの短い管理Resourceであるため、Phase 2では別Snippetを返さず`content`を表示する。検索Technologyの交換によってFlutter Contractを変更しない。

検索順序は次のTotal Orderとする。

```text
Internal Relevance Rank DESC
updatedAt DESC
memoryId ASC
```

Internal Relevance Rankの表現とWeightはClientへ公開しない。同点時は`updatedAt`と`memoryId`で安定順序を保証する。検索TechnologyがScoreを生成しない場合も、AdapterはContract上のDeterministic Rankを返す。

### 33.9 Cursor Binding

CursorはClientが生成、解析、Decodeまたは変更しないOpaque Valueとする。

論理的に、少なくとも次へBindingする。

- Endpoint Purpose（ListまたはManagement Search）
- Canonicalized Queryの非可逆Digest（検索時のみ）
- Canonicalized Filter Set
- `limit`
- Ordering Contract Version
- Page Boundary
- 発行時刻と有効期限

List CursorをSearchへ、Search CursorをListへ流用しない。Filter、Queryまたは`limit`を変えた場合は最初のPageから取得し直す。

CursorのDefault有効期間は15分とし、Configuration可能にする。期限切れ、改変、別条件への流用または解釈不能なCursorは`400 INVALID_CURSOR`とする。

Cursorは次のいずれかで実装し、単なる平文JSONのBase64 Encodingだけで改変防止済みとみなさない。

- Integrity保護されたOpaque Token
- Backend側Stateを参照する推測困難なOpaque ID

具体的な署名方式、Key管理、PersistenceおよびCursor SchemaはDatabase / Security Designで確定する。CursorへMemory本文、検索語、SecretまたはSensitive情報を平文で含めない。

### 33.10 Pagination under Concurrent Mutation

Cursorは順序と条件の継続位置を保証するが、Phase 2の一覧・検索をDatabase Snapshotとして固定しない。

Page取得中にMemoryの登録、更新または削除が発生した場合、次が起こり得る。

- 更新されたMemoryが新しい位置へ移動し、現在のPagination Sessionでは表示されない
- 削除されたMemoryが後続Pageから消える
- 新規Memoryが現在位置より前へ追加され、後続Pageには現れない

Backendは一つのPage内で同じ`memoryId`を重複返却せず、Cursor境界を厳密に適用する。ただし複数Page全体の完全Snapshot、一度だけの表示および`totalCount`整合性は保証しない。

Mutation成功後、Flutterは現在の一覧・検索Sessionを破棄し、最初のPageから再取得する。画面Refreshでも同様に新しいSessionを開始する。

将来、実際のUX要件からSnapshot Paginationが必要と確認された場合、Revision SnapshotまたはSearch Session Persistenceを別設計する。

### 33.11 Failure and Security Rules

- 一覧・検索Responseへ`Cache-Control: no-store`を設定する。
- Memory本文、検索語、CursorおよびSensitive Filter条件を通常Logへ出力しない。
- Flutter向け管理検索が失敗した場合、無関係な一覧結果または一部だけの検索結果を正常な`200 OK`として返さない。
- 必要な検索Capabilityが利用不能な場合は、Phase 2 Error Mappingで定義する安全なErrorへ変換する。
- 回答生成時の「Memoryなしで会話継続」というFallbackを、管理画面検索APIへそのまま適用しない。
- Search Indexの古いContentを返さず、検索候補のIDからAuthoritative Memoryを再取得してResourceを構築する。

### 33.12 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-023 | List / Searchで`memories`、`nextCursor`、`hasMore`の共通Collection Envelopeを使用する | Accepted |
| API2-024 | GET ListでCategory、State、Capture Type、SensitivityおよびConfirmation Statusの複数値Filterを提供する | Accepted |
| API2-025 | 同一Filter Dimension内をOR、異なるDimension間をANDとし、Filter未指定を全値として扱う | Accepted |
| API2-026 | `CONFIRMED` / `UNCONFIRMED`を`confirmedAt`から導く管理用Filterとする | Accepted |
| API2-027 | List順序を`updatedAt DESC, memoryId ASC`へ固定し、Phase 2でClient指定Sortを導入しない | Accepted |
| API2-028 | 自由入力検索をRequired `query`、Optional `filters`、`limit`および`cursor`を持つPOST JSON Contractとする | Accepted |
| API2-029 | Search Query上限を500 Unicode Code Pointかつ2 KiBとし、NFC等のCanonicalization後に検証する | Accepted |
| API2-030 | Search Responseを完全な`MemoryResource`とし、Score、HighlightおよびProvider固有情報を返さない | Accepted |
| API2-031 | Search順序を内部関連性、`updatedAt`、`memoryId`によるTotal Orderとする | Accepted |
| API2-032 | CursorをPurpose、Query Digest、Filter、limit、OrderingおよびPage BoundaryへBindingする | Accepted |
| API2-033 | Cursor Default有効期間を15分とし、平文検索語・Memory本文・Secretを含めない | Accepted |
| API2-034 | Empty Resultを`200`とし、`hasMore`と`nextCursor`のInvariantを維持する | Accepted |
| API2-035 | Phase 2のCollection ResponseへTotal Countを含めない | Accepted |
| API2-036 | Concurrent Mutation中の複数PageをSnapshot保証せず、Mutation後は最初のPageから再取得する | Accepted |
| API2-037 | 管理検索失敗を無関係な一覧結果や部分結果の正常Responseへ変換しない | Accepted |

### 33.13 Next API Design Topics

API2-023〜API2-037承認後、次の順序で詳細化する。

1. Register / Get / Update / Confirm / Individual Delete Contract
2. Memory Preferences Contract
3. Memory Usage Transparency Contract
4. Deletion Plan Preview / Execute Contract
5. Phase 2 Problem DetailsとError Mapping
6. Response / Request Size Limits
7. Backup / Restore設計後のAPI追加
8. Phase 2 API Design Review

---

## 34. Phase 2 Memory Register, Get, Update, Confirm and Individual Delete Contract

### 34.1 Design Status

| Item | Value |
|---|---|
| Status | Accepted |
| Implementation | Not Started |
| Depends On | API2-001〜API2-037 Accepted |

本Sectionは、単一Memoryを管理する次のEndpoint Contractを定義する。

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/memories` | 管理画面から明示登録 |
| `GET` | `/api/v1/memories/{memoryId}` | 詳細取得 |
| `PATCH` | `/api/v1/memories/{memoryId}` | Content、Category、SensitivityまたはStateの更新 |
| `POST` | `/api/v1/memories/{memoryId}/confirm` | 内容を変えない明示確認 |
| `DELETE` | `/api/v1/memories/{memoryId}` | 一意に特定したMemoryの削除 |

自然言語による登録・更新・確認・削除は、Section 31の方針どおりConversation APIから同じApplication Use Caseへ接続する。

### 34.2 Common Mutation Response

登録、更新および確認でMemoryが存在する結果は、次の`MemoryMutationResponse`を返す。

```json
{
  "outcome": "UPDATED",
  "memory": {
    "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
    "content": "仕事ではJavaを使用している。",
    "category": "ENGINEERING",
    "captureType": "EXPLICIT",
    "sensitivityLevel": "NORMAL",
    "state": "ACTIVE",
    "version": 2,
    "etag": "\"memory-7f1f8e2a-v2\"",
    "createdAt": "2026-08-27T21:30:00.000+09:00",
    "updatedAt": "2026-08-28T20:00:00.000+09:00",
    "confirmedAt": "2026-08-28T20:00:00.000+09:00"
  }
}
```

| Field | Type | Required | Description |
|---|---|---:|---|
| `outcome` | string enum | Yes | 今回の論理結果 |
| `memory` | `MemoryResource` | Yes | 処理後のCanonical Resource |

Phase 2では次のOutcomeを使用する。

| Outcome | Meaning |
|---|---|
| `CREATED` | 新しいMemoryを保存した |
| `UPDATED` | 既存Memoryの管理対象情報を変更した |
| `CONFIRMED` | 内容を変更せずユーザーの明示確認を記録した |
| `NO_CHANGE` | Requestは成功したが既存Resourceに変更が不要だった |

`PARTIAL`、`UNKNOWN`または`FAILED`を成功ResponseのOutcomeとして返さない。完全成功でない場合は、Phase 2 Problem Details Contractへマップする。

Resourceを含むMutation Responseでは、HTTP `ETag` Headerと`memory.etag`を一致させる。

### 34.3 Register Memory

#### Request

```http
POST /api/v1/memories
Content-Type: application/json
Accept: application/json
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

```json
{
  "content": "仕事ではJavaを使用している。",
  "category": "ENGINEERING",
  "sensitivityLevel": "NORMAL",
  "state": "ACTIVE"
}
```

| Field | Type | Required | Default | Rule |
|---|---|---:|---|---|
| `content` | string | Yes | none | Section 32のCanonicalizationと上限を適用 |
| `category` | enum | Yes | none | Section 32.5のCategory Code |
| `sensitivityLevel` | enum | No | Domain判定 | `NORMAL`または`SENSITIVE` |
| `state` | enum | No | `ACTIVE` | `ACTIVE`または`RESOLVED` |

`captureType`はBackendが`EXPLICIT`に設定する。ID、Version、ETagおよび日時をClientに指定させない。

`sensitivityLevel`省略時はDomain Policyが確定する。Clientが`SENSITIVE`を指定した場合、Backendは勝手に`NORMAL`へDown-gradeしない。Clientが`NORMAL`を指定した内容をDomain PolicyがSensitiveと判断した場合、`NORMAL`のまま保存または黙って値を変更せず、明示的なSensitivity不一致として拒否する。具体的なProblem CodeはSection 38で定義する。

#### Success

新規保存時は`201 Created`を返す。

```http
HTTP/1.1 201 Created
Location: /api/v1/memories/7f1f8e2a-4b3c-4d5e-8f60-123456789abc
ETag: "memory-7f1f8e2a-v1"
```

```json
{
  "outcome": "CREATED",
  "memory": { "...": "MemoryResource" }
}
```

`Location`はResponseの`memoryId`に対応する相対Pathとする。

### 34.4 Register Relation Handling

Register Requestも保存前に既存MemoryとのRelation Decisionを行う。

| Relation | API Result |
|---|---|
| `NONE` | 新規Memoryを作成し`201 CREATED` |
| `RELATED_INDEPENDENT` | 独立Memoryとして作成し`201 CREATED` |
| `EQUIVALENT` | 既存Memoryを明示確認し`200 CONFIRMED`。確認日時の更新も不要なReplayでは`200 NO_CHANGE` |
| `COMPLEMENTARY_SAME_FACT` | MutationせずRelation Reviewを作成し`409 MEMORY_RELATION_REVIEW_REQUIRED` |
| `SUPERSEDES` | MutationせずRelation Reviewを作成し`409 MEMORY_RELATION_REVIEW_REQUIRED` |
| `CONFLICT` | MutationせずRelation Reviewを作成し`409 MEMORY_RELATION_REVIEW_REQUIRED` |
| `UNCERTAIN` | MutationせずRelation Reviewを作成し`409 MEMORY_RELATION_REVIEW_REQUIRED` |

管理画面の「新規登録」は既存Memoryの編集操作ではない。補足、変更、矛盾または不確実な関係がある場合、Section 45の短命`MemoryRelationReview`を取得し、ユーザーが`UPDATE_TARGET`、`ADD_AS_NEW`または`SKIP`を明示選択する。任意のPATCHまたはRegister再送でRelation判定を迂回しない。

Equivalentで既存Memoryを返す場合も、`Location`はその既存MemoryのResource Pathを示す。

`409` ResponseへMemory本文、関連Memory ID一覧、AIの推論または検索Scoreを含めない。Section 38.7の`relationReviewId`、URI、Relation Type、件数および期限だけを返す。

### 34.5 Register Idempotency

`Idempotency-Key`はUUID Canonical形式を必須とする。Canonicalized Request Bodyを同一性判定へ使用する。

| Existing Record | Result |
|---|---|
| none | Register処理を開始 |
| same Key / same Canonical Request | 保存済みHTTP Status、Outcome、Memory、LocationおよびETagを論理的にReplay |
| same Key / different Canonical Request | `409 IDEMPOTENCY_KEY_CONFLICT` |
| same Key / processing | `409 REQUEST_IN_PROGRESS`と`Retry-After` |

Replay時も現在のHTTP Requestへ新しい`requestId`を発行する。保存期間、LeaseおよびDynamoDB ModelはDatabase Designで確定する。

Validationで拒否したRequestをIdempotency処理開始済みとして記録しない。Domain判定開始後のTerminal Resultをどこまで保存するかは、Failure / Persistence設計で確定する。

### 34.6 Get Memory

```http
GET /api/v1/memories/{memoryId}
Accept: application/json
```

存在する場合は`200 OK`、Bodyに`MemoryResource`、HeaderにBodyと同じ`ETag`を返す。

```http
HTTP/1.1 200 OK
ETag: "memory-7f1f8e2a-v2"
Cache-Control: no-store
```

削除済みまたは存在しないMemoryは`404 MEMORY_NOT_FOUND`とする。削除済み本文、Deletion Guardまたは削除理由を返さない。

`memoryId`はClientにとってOpaqueだが、Backendが生成する小文字・ハイフン付きCanonical UUID v4形式（36 ASCII Character）をPresentationで検証する。不正形式は`400 VALIDATION_ERROR`、形式は正しいが存在しないIDは`404 MEMORY_NOT_FOUND`とする。

### 34.7 Update Memory

#### Request

```http
PATCH /api/v1/memories/{memoryId}
Content-Type: application/json
If-Match: "memory-7f1f8e2a-v2"
```

```json
{
  "content": "業務でJavaを継続的に使用している。",
  "category": "ENGINEERING",
  "sensitivityLevel": "NORMAL",
  "state": "ACTIVE"
}
```

Request Bodyは独自の部分更新Objectとして扱い、RFC 7396 JSON Merge Patchではない。`Content-Type`は`application/json`とする。

指定可能なFieldは次の4つに限定する。

- `content`
- `category`
- `sensitivityLevel`
- `state`

少なくとも一つのFieldを必須とする。Field省略は維持を意味する。`null`は削除またはDefaultへのResetとして扱わず、`400 VALIDATION_ERROR`とする。全Fieldを一つのAtomic Mutationとして検証・適用する。

`captureType`、ID、Version、ETag、日時その他のServer-owned Fieldを指定できない。

#### Concurrency

`If-Match`を必須とする。

| Condition | Result |
|---|---|
| Headerなし | `428 PRECONDITION_REQUIRED` |
| 現在のStrong ETagと一致 | 更新判定へ進む |
| Version不一致 | `412 PRECONDITION_FAILED` |
| Weak ETag、`*`、複数値または不正形式 | `400 VALIDATION_ERROR` |

ClientはBodyの`version`からETagを作らず、直前のResponseで取得した`etag`を変更せず送信する。

#### Success and No Change

変更がある場合は`200 UPDATED`と更新後Resourceを返す。

- `version`を1増加する。
- `updatedAt`をMutation成功時刻へ更新する。
- 管理画面編集は明示操作のため`confirmedAt`も更新する。
- `memoryId`、`createdAt`および`captureType`を維持する。
- Content、Category、SensitivityまたはStateの変更をRevisionへ記録する。

Canonicalization後に全指定Fieldが現在値と同じ場合は`200 NO_CHANGE`とする。Version、ETag、`updatedAt`および`confirmedAt`を変更せず、Revisionを追加しない。

### 34.8 Update Domain and Relation Rules

PATCHもSecret禁止、Category、Sensitivity、Atomicityおよび再登録防止Ruleを通過する必要がある。

- `SENSITIVE`から`NORMAL`への変更は、更新後ContentをDomain Policyが`NORMAL`と判断できる場合だけ許可する。
- Category不一致を`OTHER`へ逃がさない。
- 更新後のMemoryが別の既存Memoryと`EQUIVALENT`、`COMPLEMENTARY_SAME_FACT`、`SUPERSEDES`、`CONFLICT`または`UNCERTAIN`になる場合、別Memoryを暗黙Merge・削除・上書きせず、Section 45のRelation Reviewを作成して`409`とする。
- Request全体を適用できない場合、一部Fieldだけを更新しない。

同じMemoryの明確な訂正はPATCHで完結できるが、別Memoryを巻き込む統合は`ResolveMemoryRelationUseCase`でだけ実行する。汎用Merge EndpointやClient指定`override`は追加しない。

### 34.9 Confirm Memory

```http
POST /api/v1/memories/{memoryId}/confirm
If-Match: "memory-7f1f8e2a-v2"
Content-Length: 0
```

Request Bodyは送信しない。Non-empty Body、`Content-Type`付きJSON Objectまたは確認理由等の未知入力を受理しない。

成功時は`200 CONFIRMED`と更新後Resourceを返す。

- Content、Category、Sensitivity、State、`memoryId`、`createdAt`、`updatedAt`および`captureType`を変更しない。
- `confirmedAt`を確認成功時刻へ更新する。
- `version`とETagを1つ進める。
- Content-bearing `MemoryRevision`は追加しない。
- 必要ならContent-free Operation Auditへ確認操作を記録できる。

確認は内容が現在も正しいという明示操作であり、単なるGET、一覧表示またはユーザーが否定しなかったことから実行しない。

`If-Match` RuleはPATCHと同じとする。通信結果が不明な場合、同じ古いETagで自動再実行せずGETで最新Resourceを取得し、`confirmedAt`とETagを確認する。

### 34.10 Individual Delete

```http
DELETE /api/v1/memories/{memoryId}
If-Match: "memory-7f1f8e2a-v3"
```

Request Bodyは送信しない。`If-Match` RuleはPATCHと同じとする。

完全成功時は`200 OK`とContent-freeな`MemoryDeletionReceipt`を返す。

```json
{
  "outcome": "DELETED",
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "deletedAt": "2026-08-28T20:15:00.000+09:00"
}
```

| Field | Type | Required | Description |
|---|---|---:|---|
| `outcome` | string enum | Yes | `DELETED`固定 |
| `memoryId` | string | Yes | 削除対象のOpaque ID |
| `deletedAt` | string | Yes | 完全削除を確認したJST日時 |

Receiptへ削除本文、Category、Sensitivity、Revision、FingerprintまたはGuard内部情報を含めない。

`PersonalMemory`、Content-bearing Revision、検索Copy、CacheおよびPending処理の削除と、Re-registration Guard反映の両方を確認できた場合だけ`DELETED`を返す。一部失敗または結果不明を`200 DELETED`へ変換しない。

### 34.11 Delete Retry and Reconciliation

Individual Deleteへ`Idempotency-Key`を追加要求しない。対象`memoryId`と削除前のStrong ETagを論理Operation Identityとして扱う。

完全削除後は、Content-free Operation Auditに同じ`memoryId`、削除前Version、結果および`deletedAt`を保持できる。同じIDと同じ削除前ETagによるRetryでは、削除を再実行せず保存済みDeletion Receiptを`200`でReplayする。

| Condition | Result |
|---|---|
| 同じID / 同じ削除前ETag / 完全削除済み | 保存済み`200 DELETED`をReplay |
| IDは形式上ValidだがMemoryも一致する完了Receiptもない | `404 MEMORY_NOT_FOUND` |
| Memoryは存在するがETag不一致 | `412 PRECONDITION_FAILED` |
| 削除Operationが処理中または回復中 | `409 REQUEST_IN_PROGRESS`または安全なService Error |

Replay用RecordはContent-freeとし、削除した情報を復元できる値を保持しない。Retention、Recovery StateおよびPersistence方式はDatabase / Security Designで確定する。

GETはDeletion Receiptを返さない。削除後の通常GETは常に`404 MEMORY_NOT_FOUND`とする。

### 34.12 Status and Header Summary

| Operation | Success Status | Required Request Header | Success Response Header |
|---|---:|---|---|
| Register new | `201` | `Idempotency-Key` | `Location`、`ETag` |
| Register equivalent | `200` | `Idempotency-Key` | `Location`、`ETag` |
| Get | `200` | none | `ETag` |
| Update | `200` | `If-Match` | new/current `ETag` |
| Confirm | `200` | `If-Match` | new `ETag` |
| Individual Delete | `200` | `If-Match` | none |

すべてのResponseへ`Cache-Control: no-store`と`X-Request-Id`を設定する。Resourceを含むResponseではBodyとHeaderのETagを一致させる。

### 34.13 Common Failure Boundaries

- Infrastructure、DynamoDBまたはAI Provider固有ErrorをResponseへ公開しない。
- 失敗時にRequestのMemory本文をProblem DetailsへEchoしない。
- ETag不一致時に古いClient入力を自動適用しない。
- UpdateまたはConfirmが失敗した場合、成功したResourceとしてVersionを進めない。
- Deleteの完全成功を確認できない場合、Deletion Receiptを生成しない。
- TimeoutまたはNetwork切断後にClientが結果を推測せず、GETまたは同じDelete Operation IdentityでReconcileできるようにする。

具体的なProblem Type、Code、Extension、RetryabilityおよびHTTP MappingはPhase 2 Problem Details設計で確定する。

### 34.14 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-038 | Register、UpdateおよびConfirmでOutcomeとCanonical Resourceを持つ`MemoryMutationResponse`を使用する | Accepted |
| API2-039 | Register RequestでContentとCategoryを必須、SensitivityとStateをOptionalとする | Accepted |
| API2-040 | Register時の`captureType`をServerが`EXPLICIT`に設定し、State Defaultを`ACTIVE`とする | Accepted |
| API2-041 | Client指定SensitivityとDomain判定が安全側で不一致の場合、黙って保存・変更せず明示的に拒否する | Accepted |
| API2-042 | Registerの新規保存を`201 CREATED`、Equivalentを`200 CONFIRMED`または`NO_CHANGE`とする | Accepted |
| API2-043 | Registerで補足・変更・矛盾・不確実なRelationを検出した場合、既存Memoryを自動更新せず`409`とする | Accepted |
| API2-044 | RegisterでUUID `Idempotency-Key`を必須とし、Canonical Request単位で結果をReplayする | Accepted |
| API2-045 | Getで`MemoryResource`とStrong ETagを返し、削除済み・不存在を`404`とする | Accepted |
| API2-046 | PATCHを4つの管理対象Fieldだけを許可するAtomicなJSON部分更新Contractとする | Accepted |
| API2-047 | PATCH、ConfirmおよびDeleteで単一Strong ETagの`If-Match`を必須とし、Weak ETag、Wildcardおよび複数値を禁止する | Accepted |
| API2-048 | PATCHの実変更でVersion、updatedAt、confirmedAtおよびRevisionを更新し、No Changeでは更新しない | Accepted |
| API2-049 | PATCH後に別Memoryとの統合・矛盾判断が必要な場合、暗黙Mergeや削除を行わず`409`とする | Accepted |
| API2-050 | ConfirmをBodyなしの専用Endpointとし、confirmedAt、VersionおよびETagだけを進める | Accepted |
| API2-051 | ConfirmでContent-bearing Revisionを追加せず、必要なContent-free Auditだけを許可する | Accepted |
| API2-052 | Individual Deleteの完全成功を`200 MemoryDeletionReceipt`で返し、削除Contentを含めない | Accepted |
| API2-053 | Content削除とRe-registration Guard反映の両方を確認できるまで`DELETED`を返さない | Accepted |
| API2-054 | Individual DeleteをMemory IDと削除前ETagでReconcileし、完全削除済みRetryへContent-free ReceiptをReplayする | Accepted |
| API2-055 | Delete後のGETを常に`404`とし、通常Resource取得からDeletion Receiptを分離する | Accepted |
| API2-056 | Resource ResponseのETag一致、`Cache-Control: no-store`および`X-Request-Id`を共通適用する | Accepted |

### 34.15 Next API Design Topics

API2-038〜API2-056承認後、次の順序で詳細化する。

1. Memory Preferences Contract
2. Memory Usage Transparency Contract
3. Deletion Plan Preview / Execute Contract
4. Phase 2 Problem DetailsとError Mapping
5. Response / Request Size Limits
6. Backup / Restore設計後のAPI追加
7. Phase 2 API Design Review

---

## 35. Phase 2 Memory Preferences Contract

### 35.1 Design Status

| Item | Value |
|---|---|
| Status | Accepted |
| Implementation | Not Started |
| Depends On | API2-001〜API2-056 Accepted、`memory-design.md` MD-013 Accepted |

本Sectionは、自動保存と通常回答へのMemory利用を管理するSingleton Resource `MemoryPreferencesResource`のHTTP Contractを定義する。

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/memory-preferences` | 現在設定の取得 |
| `PATCH` | `/api/v1/memory-preferences` | 一つまたは両方の設定変更 |

Preferenceは個々のMemoryのStateではない。設定をOFFにしてもMemory本文を削除、解決済み化または変更しない。

### 35.2 `MemoryPreferencesResource`

```json
{
  "autoSaveEnabled": true,
  "answerUseEnabled": true,
  "version": 1,
  "etag": "\"memory-preferences-v1\"",
  "updatedAt": "2026-08-28T21:00:00.000+09:00"
}
```

| Field | Type | Required | Nullable | Description |
|---|---|---:|---:|---|
| `autoSaveEnabled` | boolean | Yes | No | 通常Conversationから自動保存候補を処理するか |
| `answerUseEnabled` | boolean | Yes | No | 通常回答でPersonal Memoryを検索・利用するか |
| `version` | integer | Yes | No | 1から始まるPreferences固有の単調増加Version |
| `etag` | string | Yes | No | 現在Versionに対応するOpaque Strong Entity Tag |
| `updatedAt` | string | Yes | No | 設定値を最後に変更したJST日時 |

Preferencesの`version`とETagは、各`PersonalMemory`のVersionから独立する。Clientは`version`からETagを組み立てず、Response値を変更せず`If-Match`へ使用する。

HTTP `ETag` HeaderとBodyの`etag`を一致させる。日時形式はSection 10.2および32.4と同じ、JST Offset付き・ミリ秒3桁固定とする。

### 35.3 Initial State and Singleton Lifecycle

Single-userのPhase 2では`MemoryPreferences`を論理的に一つだけ所有し、Resource IDや`userId`をPathへ含めない。

初期値はAccepted Domain Designどおり次とする。

```text
autoSaveEnabled = true
answerUseEnabled = true
version = 1
updatedAt = logical initialization time
```

新規環境でPersistence Itemがまだ存在しない場合、BackendはDefault値をConditionalに初期化し、同時初期化では一つの結果へ収束させる。この内部初期化は論理設定を変更せず、GETの安全性を壊す操作として扱わない。

一度作成済みのPreferences Itemが読めない、破損した、またはPersistence障害で不存在と区別できない場合、Defaultの`true / true`へ勝手に戻さない。正常な新規初期化とData Loss / Failureを区別する仕組みはDatabase Designで確定する。

Preferencesは通常操作で削除しない。`DELETE`、`POST`作成およびReset専用EndpointはPhase 2で提供しない。Defaultへ戻す場合はPATCHで両Fieldへ`true`を明示する。

### 35.4 Get Memory Preferences

```http
GET /api/v1/memory-preferences
Accept: application/json
```

正常時は常に`200 OK`と現在の`MemoryPreferencesResource`を返す。

```http
HTTP/1.1 200 OK
ETag: "memory-preferences-v1"
Cache-Control: no-store
```

Singletonであるため、正常な初期化可能状態を`404`にしない。現在値を信頼して取得できない場合は、推測したDefaultを`200`で返さず、安全なService Errorとする。

### 35.5 Update Memory Preferences

```http
PATCH /api/v1/memory-preferences
Content-Type: application/json
If-Match: "memory-preferences-v1"
```

```json
{
  "autoSaveEnabled": false,
  "answerUseEnabled": true
}
```

| Field | Type | Required | Rule |
|---|---|---:|---|
| `autoSaveEnabled` | boolean | No | 指定時だけ更新 |
| `answerUseEnabled` | boolean | No | 指定時だけ更新 |

少なくとも一つのFieldを必須とする。省略は現在値の維持を意味する。`null`、数値、文字列、未知Fieldおよび空Objectは`400 VALIDATION_ERROR`とする。両Fieldを指定した場合、一つのAtomic Mutationとして適用する。

`If-Match`を必須とし、Section 34.7と同じRuleを適用する。

| Condition | Result |
|---|---|
| Headerなし | `428 PRECONDITION_REQUIRED` |
| 現在のStrong ETagと一致 | 更新判定へ進む |
| Version不一致 | `412 PRECONDITION_FAILED` |
| Weak ETag、`*`、複数値または不正形式 | `400 VALIDATION_ERROR` |

### 35.6 Preferences Mutation Response

```json
{
  "outcome": "UPDATED",
  "preferences": {
    "autoSaveEnabled": false,
    "answerUseEnabled": true,
    "version": 2,
    "etag": "\"memory-preferences-v2\"",
    "updatedAt": "2026-08-28T21:10:00.000+09:00"
  }
}
```

| Field | Type | Required | Description |
|---|---|---:|---|
| `outcome` | string enum | Yes | `UPDATED`または`NO_CHANGE` |
| `preferences` | `MemoryPreferencesResource` | Yes | 処理後のCanonical Resource |

値が一つでも変わる場合は`200 UPDATED`とする。

- `version`を1増加する。
- `updatedAt`をMutation成功時刻へ更新する。
- 新しいStrong ETagを返す。

全指定値が現在値と同じ場合は`200 NO_CHANGE`とする。Version、ETagおよび`updatedAt`を変更しない。

HTTP `ETag` Headerと`preferences.etag`を一致させる。PATCHへ`Idempotency-Key`を要求しない。通信結果が不明な場合、古いETagで自動再実行せずGETで最新値を取得してReconcileする。

### 35.7 Four Valid Combinations

次の4状態をすべて正常な設定として許可する。

| autoSaveEnabled | answerUseEnabled | Behavior |
|---:|---:|---|
| `true` | `true` | 自動保存し、通常回答で保存済みMemoryを利用する |
| `false` | `true` | 自動保存しないが、保存済みMemoryは通常回答へ利用できる |
| `true` | `false` | 自動保存するが、通常回答へMemoryを利用しない |
| `false` | `false` | 自動保存も通常回答への利用も行わない |

片方をOFFにしたことから、もう片方の値を推測変更しない。

### 35.8 Auto-save Runtime Semantics

`autoSaveEnabled = false`は、通常ConversationからのAutomatic Captureだけを停止する。

停止しない操作：

- 管理画面からの明示登録・更新・確認・削除
- Conversation中の明示的な「覚えて」要求
- Memory一覧・検索・詳細取得
- Backup / Restoreの明示操作

Automatic Capture Flowは少なくとも次の2点でPreferenceを確認する。

1. Candidate抽出を開始する前
2. Automatic MutationをCommitする直前

Commit直前に`autoSaveEnabled = false`または参照したPreferences Versionの変更を検出した場合、Pending Candidateを保存しない。Userの明示登録へ勝手に変換しない。

OFFからONへ変更しても、過去のConversation Historyを遡って自動抽出しない。変更後に新しく完了する対象Conversationから適用する。

設定変更前に完全Commit済みのMemoryは削除またはRollbackしない。

### 35.9 Answer-use Runtime Semantics

`answerUseEnabled = false`は、通常回答のContextへPersonal Memoryを自動追加する処理を停止する。

通常回答Flowでは少なくとも次の時点でPreferenceを確認する。

1. Answer-time Memory Retrievalを開始する前
2. AI ProviderへContextを送信する直前

Provider呼出し直前に`answerUseEnabled = false`またはPreferences Version変更を検出した場合、取得済みMemoryをContextから除外し、MemoryなしでConversationを継続する。

設定変更前にAI Providerへ既に送信済みのRequestからMemoryを回収・取消することはできない。OFFへの変更は、それ以降にProviderへ送信するRequestへ適用する。

`answerUseEnabled = false`でも、次は利用可能とする。

- Memory管理画面
- 明示的なMemory一覧・詳細・検索・削除要求
- 「Aliceが何を覚えているか」の明示確認
- Backup / Restore

この設定は通常回答への自動利用を制御するものであり、ユーザー自身によるMemory管理・透明性確認を禁止するAuthorizationではない。

### 35.10 Failure-safe Behavior

Preferencesを信頼して取得できない場合、用途ごとに次の動作とする。

| Context | Behavior |
|---|---|
| 通常回答 | Personal MemoryをContextへ含めずConversationを継続 |
| Automatic Capture | Candidate抽出・自動保存を行わない。Conversation回答は継続可能 |
| Preferences GET | 推測Defaultを返さずService Error |
| Preferences PATCH | 更新成功を返さずService Error |
| 明示Memory管理 | 対象操作にPreferenceが不要なら継続可能 |

Preferences取得失敗を`true`として扱わない。障害時のFallbackでMemory本文を削除、設定値を上書きまたは新しいDefault Itemを作成しない。

### 35.11 Persistence, Backup and Logging Boundaries

- PreferencesはPersonal Memory本文と別の論理Resourceとして保存する。
- Delete-allおよび個別Memory削除でPreferencesを削除・Resetしない。
- Backup ArchiveへPreferencesを含めるが、Restore時の競合・Preview・適用方法はBackup / Restore設計で確定する。
- Boolean設定、Versionおよび更新成否は通常Logへ記録可能だが、Memory本文や関連Contextを同じLogへ含めない。
- DynamoDB Key、Conditional ExpressionおよびItem SchemaをAPIへ公開しない。
- Preferences変更はContent-bearing Memory Revisionを作成しない。必要なら独立したContent-free Operation Auditを利用する。

### 35.12 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-057 | `MemoryPreferencesResource`を2つのBoolean、独立Version、ETagおよびupdatedAtで表す | Accepted |
| API2-058 | PreferencesをPath IDなしのSingle-user Singleton Resourceとし、初期値を`true / true`とする | Accepted |
| API2-059 | 新規環境ではDefaultをConditional初期化し、既存値を読めない場合はDefaultへ勝手に戻さない | Accepted |
| API2-060 | Preferences GETを正常時`200`とし、値を信頼できない場合は推測DefaultではなくService Errorとする | Accepted |
| API2-061 | Preferences PATCHで一つ以上のBooleanだけをAtomicに変更し、Strong ETagの`If-Match`を必須とする | Accepted |
| API2-062 | Preferences Mutationを`UPDATED` / `NO_CHANGE`で区別し、実変更時だけVersionとupdatedAtを進める | Accepted |
| API2-063 | auto-saveとanswer-useの4組合せをすべて許可し、一方から他方を推測変更しない | Accepted |
| API2-064 | auto-saveをCandidate抽出前とAutomatic Commit直前に確認し、OFFまたはVersion変更時は保存しない | Accepted |
| API2-065 | auto-saveをONに戻しても過去Conversationを遡って自動抽出しない | Accepted |
| API2-066 | answer-useをRetrieval前とProvider呼出し直前に確認し、OFFまたはVersion変更時はMemoryをContextから除外する | Accepted |
| API2-067 | Preferences OFFでも明示Memory管理、透明性確認およびBackup / Restoreを許可する | Accepted |
| API2-068 | Preferences取得不能時、通常回答と自動保存ではOFF相当へFail-safeし、管理APIではService Errorとする | Accepted |
| API2-069 | Preferences変更でPersonal Memory本文、State、RevisionおよびDeletion Guardを変更しない | Accepted |

### 35.13 Next API Design Topics

API2-057〜API2-069承認後、次の順序で詳細化する。

1. Memory Usage Transparency Contract
2. Deletion Plan Preview / Execute Contract
3. Phase 2 Problem DetailsとError Mapping
4. Response / Request Size Limits
5. Backup / Restore設計後のAPI追加
6. Phase 2 API Design Review

---

## 36. Phase 2 Memory Usage Transparency Contract

### 36.1 Design Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Implementation | Not Started |
| Depends On | API2-001〜API2-069 Accepted、`memory-design.md` MD-081 Accepted |

本Sectionは、指定したAlice回答の生成時にPersonal Memoryがどのように扱われたかを、ユーザーが確認するためのHTTP Contractを定義する。

```http
GET /api/v1/conversation/messages/{messageId}/memory-usage
```

Transparencyは因果関係の完全な証明ではない。少なくとも次を区別して説明する。

```text
検索候補として取得した
        ↓
最終的にAI Contextへ含めた
        ↓
Modelが参照したと報告した
```

検索候補に入ったことだけを「回答に利用した」と表示しない。Modelの自己申告だけを確定的な因果関係として扱わない。

### 36.2 Target Message Rules

`messageId`はBackendが生成する小文字・ハイフン付きCanonical UUID v4形式（36 ASCII Character）とする。

| Condition | Result |
|---|---|
| ID形式不正 | `400 VALIDATION_ERROR` |
| Message不存在 | `404 MESSAGE_NOT_FOUND` |
| User Messageその他の非Assistant Message | `404 MEMORY_USAGE_NOT_FOUND` |
| 保存済みAssistant Message | Section 36.3の`200` Response |

Phase 2開始前のAssistant MessageやTrace記録に失敗した回答も、Message自体が存在する場合は`404`にせず、`availability = NOT_RECORDED`として区別する。

### 36.3 `MemoryUsageResponse`

```json
{
  "messageId": "5e6f7081-92a3-4bcd-8ef0-123456789abc",
  "availability": "AVAILABLE",
  "retrieval": {
    "status": "COMPLETE",
    "reason": null,
    "candidateCount": 5,
    "includedCount": 2,
    "estimatedTokens": 120,
    "utf8Bytes": 540
  },
  "memories": [
    {
      "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
      "memoryVersionAtUse": 3,
      "temporalRole": "CURRENT",
      "relevanceReasonCodes": ["ENGINEERING_RELEVANCE"],
      "modelReferenceStatus": "REPORTED",
      "resourceStatus": "UNCHANGED",
      "currentMemory": { "...": "MemoryResource" }
    }
  ],
  "traceRecordedAt": "2026-08-28T21:30:02.000+09:00"
}
```

| Field | Type | Required | Nullable | Description |
|---|---|---:|---:|---|
| `messageId` | string | Yes | No | 対象Assistant Message ID |
| `availability` | enum | Yes | No | `AVAILABLE`または`NOT_RECORDED` |
| `retrieval` | object | Yes | Yes | 記録済みの検索・Context投入Summary |
| `memories` | array | Yes | No | AI Contextへ実際に含めたMemory。最大8件 |
| `traceRecordedAt` | string | Yes | Yes | Trace記録日時。未記録なら`null` |

`availability = AVAILABLE`では`retrieval`と`traceRecordedAt`を非nullとする。`availability = NOT_RECORDED`では`retrieval = null`、`memories = []`、`traceRecordedAt = null`とする。

### 36.4 Not-recorded Response

```json
{
  "messageId": "5e6f7081-92a3-4bcd-8ef0-123456789abc",
  "availability": "NOT_RECORDED",
  "retrieval": null,
  "memories": [],
  "traceRecordedAt": null
}
```

`NOT_RECORDED`は「Memoryを利用していない」という意味ではない。Traceが存在しないため判定できないことを表す。Phase 2以前の回答と記録失敗を、Clientが推測で区別しない。

Trace Store自体の障害により、存在するTraceを取得できるか判断できない場合は`NOT_RECORDED`へ偽装せずService Errorとする。

### 36.5 Retrieval Summary

| Field | Type | Required | Nullable | Description |
|---|---|---:|---:|---|
| `status` | enum | Yes | No | `NOT_ATTEMPTED`、`COMPLETE`、`PARTIAL`、`UNAVAILABLE` |
| `reason` | enum | Yes | Yes | 検索未実施・不完全・利用不能の安全な理由 |
| `candidateCount` | integer | Yes | Yes | 検索候補数。判定不能なら`null` |
| `includedCount` | integer | Yes | No | Contextへ含めた件数。0〜8 |
| `estimatedTokens` | integer | Yes | Yes | Memory Contextの推定Token。判定不能なら`null` |
| `utf8Bytes` | integer | Yes | Yes | Memory ContextのUTF-8 Byte数。判定不能なら`null` |

Statusの意味：

| Status | Meaning |
|---|---|
| `NOT_ATTEMPTED` | 設定OFF等により検索を開始しなかった |
| `COMPLETE` | 検索・検証・Context選択を正常完了した。候補0件も含む |
| `PARTIAL` | 一部検索Sourceが失敗したが、検証済みMemoryだけで回答を継続した |
| `UNAVAILABLE` | Memoryを取得できず、Memoryなしで回答を継続した |

Phase 2の安全なReason Codeは次に限定する。

| Reason | Applicable Status |
|---|---|
| `ANSWER_USE_DISABLED` | `NOT_ATTEMPTED` |
| `PREFERENCES_UNAVAILABLE` | `UNAVAILABLE` |
| `SEARCH_PARTIALLY_UNAVAILABLE` | `PARTIAL` |
| `SEARCH_UNAVAILABLE` | `UNAVAILABLE` |
| `FINAL_VALIDATION_EXCLUDED_ALL` | `COMPLETE`または`PARTIAL` |

`COMPLETE`かつ通常完了では`reason = null`とする。Infrastructure名、Exception、Provider名または内部Retry状態をReasonへ含めない。

次のInvariantを満たす。

```text
includedCount = memories.length
0 <= candidateCount <= 40, when non-null
0 <= includedCount <= 8
estimatedTokens <= 1,500, when non-null
utf8Bytes <= 12,288, when non-null
```

検索候補40件のMemory IDや本文は返さず、`candidateCount`だけを返す。

### 36.6 Included Memory Usage Item

```json
{
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "memoryVersionAtUse": 3,
  "temporalRole": "CURRENT",
  "relevanceReasonCodes": ["ENGINEERING_RELEVANCE"],
  "modelReferenceStatus": "REPORTED",
  "resourceStatus": "UPDATED",
  "currentMemory": { "...": "MemoryResource at current version" }
}
```

| Field | Type | Required | Nullable | Description |
|---|---|---:|---:|---|
| `memoryId` | string | Yes | No | Contextへ含めたMemory ID |
| `memoryVersionAtUse` | integer | Yes | No | 回答生成時に使用したVersion |
| `temporalRole` | enum | Yes | No | `CURRENT`または`HISTORICAL` |
| `relevanceReasonCodes` | array of enum | Yes | No | Userへ説明可能な関連理由。1〜4件、重複不可 |
| `modelReferenceStatus` | enum | Yes | No | Modelの参照報告状態 |
| `resourceStatus` | enum | Yes | No | 現在のAuthoritative Memoryとの関係 |
| `currentMemory` | `MemoryResource` | Yes | Yes | 現在Resource。削除・取得不能なら`null` |

`relevanceReasonCodes`は次の安定Codeへ限定する。

```text
DIRECT_TOPIC_MATCH
PROFILE_RELEVANCE
PREFERENCE_RELEVANCE
PERSON_RELEVANCE
LIFE_CONTEXT_RELEVANCE
PLACE_RELEVANCE
ENGINEERING_RELEVANCE
PROJECT_RELEVANCE
HISTORICAL_REQUEST
OTHER_RELEVANCE
```

内部Ranking Score、WeightまたはAIの自由文理由をCodeへ変換せず返さない。

### 36.7 Model Reference Status

| Value | Meaning |
|---|---|
| `REPORTED` | ModelがこのMemoryを参照したと報告した |
| `NOT_REPORTED` | Modelの報告対象に含まれなかった |
| `UNAVAILABLE` | Provider Contractが参照報告を返さない、または安全に判定できない |

`NOT_REPORTED`は「回答へ影響しなかった」ことを証明しない。Contextへ含めた時点でModelが参照できる状態だったことをUIで明示する。

Providerが返したRaw Reasoning、Chain of Thought、Log Probabilityまたは自由文自己分析をTransparency Responseへ含めない。Model Reference情報は、Aliceが定義したIDベースの構造化出力を検証できた場合だけ採用する。

### 36.8 Current Resource Status

Traceは回答当時のMemory本文Snapshotを保持しない。`memoryId`と`memoryVersionAtUse`から現在のAuthoritative Resourceを取得し、次を返す。

| Status | `currentMemory` | Meaning |
|---|---|---|
| `UNCHANGED` | non-null | 現在も同じVersion |
| `UPDATED` | non-null | Memoryは存在するが回答後にVersionが変わった |
| `DELETED` | `null` | Memoryは削除済みまたは通常Resourceとして存在しない |
| `UNAVAILABLE` | `null` | 現在Resourceの取得可否を安全に判定できない |

`UPDATED`の場合、`currentMemory`は現在値であり回答当時の本文ではない。UIは「回答後に更新済み」と表示し、現在本文を当時使用した本文として表示しない。

`DELETED`の場合、削除済み本文、Revision、要約、EmbeddingまたはGuard情報を復元・返却しない。

一部ResourceのHydrationに失敗した場合も、取得可能なUsage Itemを失わない。対象Itemを`UNAVAILABLE`として返し、Response全体を成功可能とする。Trace Store自体を取得できない場合はSection 36.4どおりService Errorとする。

### 36.9 Ordering and Pagination

`memories`は回答時のContext投入順で返す。同じ優先度内では、回答生成時に確定した順序を維持する。

Final Context上限が8件であるため、このEndpointへPagination、Cursor、`limit`、FilterまたはSort Parameterを追加しない。未知Query Parameterは`400 VALIDATION_ERROR`とする。

### 36.10 Trace Recording and Answer Completion

正常に保存したAssistant Messageごとに、Memoryを利用しなかった場合も最小Traceを記録する。

Examples:

| Situation | Recorded Result |
|---|---|
| answer-use OFF | `NOT_ATTEMPTED / ANSWER_USE_DISABLED / includedCount 0` |
| 検索成功・候補なし | `COMPLETE / reason null / includedCount 0` |
| 検索失敗で会話継続 | `UNAVAILABLE / SEARCH_UNAVAILABLE / includedCount 0` |
| 一部検索失敗 | `PARTIAL / SEARCH_PARTIALLY_UNAVAILABLE` |

Trace保存失敗だけを理由に、正常に生成・保存できたConversation回答を失敗扱いにしない。可能ならContent-freeなFailure Auditを残し、Transparency APIでは`NOT_RECORDED`として扱う。

TraceはAssistant Message IDへ一意にBindingし、Client入力からUsage Itemを生成・偽装させない。Assistant Message保存とTrace保存のTransaction、RetryおよびRecovery方式はDatabase Designで確定する。

### 36.11 Retention, Deletion and Backup

- Usage Traceは対応するAssistant Messageが存在する間、原則として同じ期間保持する。
- Conversation Messageが将来削除される場合、対応Traceも削除する。
- Individual / Multiple / Delete-all Memory操作ではConversation Historyを削除しないため、Content-free Usage Traceも残せる。
- Traceへ回答当時のMemory本文Snapshotを保存しない。
- Memory削除後は`resourceStatus = DELETED`、`currentMemory = null`とし、削除内容を再表示しない。
- Usage TraceをPhase 2 Memory Backup Archiveへ含めない。Conversation関連Dataとして扱う。

RetentionをConversation Historyより短くする場合は、ユーザーが古い回答のTransparencyを確認できなくなる影響を明示し、Security / Database / Requirementsを更新する。

### 36.12 Security and Non-fields

Responseへ次を含めない。

- System Prompt、Developer PromptまたはMemory Context全体
- Current User Messageまたは検索Query全文
- 回答当時のMemory本文Snapshot
- Retrieved Candidate個別ID・Content・Score
- Ranking Score、Weight、EmbeddingまたはVector Distance
- Provider Raw Response、Chain of ThoughtまたはHidden Reasoning
- DynamoDB Key、Index名またはSearch Provider情報
- Re-registration Guard、FingerprintまたはSecret
- Tool Permission、ApprovalまたはSecurity Policy

すべてのResponseへ`Cache-Control: no-store`と`X-Request-Id`を設定する。Memory本文を含み得る`currentMemory`、Trace ID、検索状態およびReason Codeを通常Logへ出力しない。

### 36.13 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-070 | Assistant Message単位のMemory Usageを既存`/memory-usage` Subresourceから取得する | Accepted |
| API2-071 | Message不存在と非Assistant Messageを`404`で区別し、Assistant MessageのTrace未記録は`200 NOT_RECORDED`とする | Accepted |
| API2-072 | `MemoryUsageResponse`でAvailability、Retrieval Summary、Included Memory ItemsおよびTrace日時を返す | Accepted |
| API2-073 | Retrievalを`NOT_ATTEMPTED`、`COMPLETE`、`PARTIAL`、`UNAVAILABLE`へ分類する | Accepted |
| API2-074 | Retrieved Candidateは件数だけを返し、Contextへ含めた最大8件だけを個別表示する | Accepted |
| API2-075 | Included Itemへ使用時Version、Temporal Role、安定Reason CodeおよびModel Reference Statusを含める | Accepted |
| API2-076 | Model Referenceを`REPORTED`、`NOT_REPORTED`、`UNAVAILABLE`で表し、因果関係の証明とみなさない | Accepted |
| API2-077 | Traceへ回答当時のMemory本文Snapshotを保持せず、現在ResourceをAuthoritative Repositoryから取得する | Accepted |
| API2-078 | 現在Resourceを`UNCHANGED`、`UPDATED`、`DELETED`、`UNAVAILABLE`へ分類し、更新後本文を当時本文として表示しない | Accepted |
| API2-079 | Usage ItemをContext投入順で返し、最大8件のためPaginationを導入しない | Accepted |
| API2-080 | Memoryを使わなかったAssistant回答にも最小Traceを記録する | Accepted |
| API2-081 | Trace保存失敗だけで正常なConversation回答を失敗扱いにしない | Accepted |
| API2-082 | Usage TraceをAssistant Messageと同期間保持し、Memory Backup Archiveへ含めない | Accepted |
| API2-083 | Memory削除後もContent-free Traceを保持可能とするが、削除本文を復元・表示しない | Accepted |
| API2-084 | Prompt、Query、Retrieved候補詳細、Score、Embedding、Provider Raw情報およびGuardをResponseから除外する | Accepted |

### 36.14 Next API Design Topics

API2-070〜API2-084は承認済みである。次の順序で詳細化する。

1. Deletion Plan Preview / Execute Contract
2. Phase 2 Problem DetailsとError Mapping
3. Response / Request Size Limits
4. Backup / Restore設計後のAPI追加
5. Phase 2 API Design Review

---

## 37. Phase 2 Deletion Plan Preview / Execute Contract

### 37.1 Purpose and Scope

本Sectionは、複数Memoryまたは全Memoryを削除する際のPreview、明示確認、実行および結果確認を定義する。個別削除はSection 34.12の`DELETE /api/v1/memories/{memoryId}`を使用し、Deletion Planを必須としない。

Deletion Planは削除条件そのものではなく、Preview時点で固定した削除対象集合を表す。Execute時にFilterや検索語を再評価して対象を追加・削除してはならない。

### 37.2 Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/memory-deletion-plans` | 対象を解決し、Preview可能なPlanを作成する |
| `GET` | `/api/v1/memory-deletion-plans/{deletionPlanId}` | Plan、対象Pageおよび実行結果を取得する |
| `POST` | `/api/v1/memory-deletion-plans/{deletionPlanId}/confirm` | Review済みPlanの短命Confirmation Tokenを発行する |
| `POST` | `/api/v1/memory-deletion-plans/{deletionPlanId}/execute` | Preview済みの固定対象を削除する |

Plan作成とExecuteにはUUID形式の`Idempotency-Key`を必須とする。GETにはIdempotency-Keyを要求しない。すべてのResponseへ`Cache-Control: no-store`と`X-Request-Id`を設定する。

`deletionPlanId`はBackend生成のCanonical UUID v4とする。形式不正は`400 VALIDATION_ERROR`、存在しないPlanは`404 DELETION_PLAN_NOT_FOUND`とする。

### 37.3 Create Deletion Plan Request

Request Bodyは`scope`によるTagged Unionとし、Unknown FieldおよびScopeに不要なFieldを拒否する。

Selected targets:

```json
{
  "scope": "SELECTED",
  "memoryIds": [
    "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
    "8a2b9f3b-5c4d-4e6f-9071-23456789abcd"
  ]
}
```

Filtered targets:

```json
{
  "scope": "FILTERED",
  "filters": {
    "category": "ENGINEERING",
    "state": "RESOLVED"
  }
}
```

All targets:

```json
{
  "scope": "ALL"
}
```

| Scope | Required Field | Rule |
|---|---|---|
| `SELECTED` | `memoryIds` | 1〜100件。重複不可。存在するIDだけを暗黙採用せず、不存在をValidation Resultで区別する |
| `FILTERED` | `filters` | Section 33.2のStructured Filterを1つ以上指定する。自由入力`query`は使用しない |
| `ALL` | なし | `ACTIVE`と`RESOLVED`を含む全Personal Memoryを対象にする |

`FILTERED`で空Filterを許可しない。全件削除は必ず`scope = ALL`として明示し、空Filterを全件削除へ暗黙変換しない。Semantic Searchや曖昧な自然言語一致をBulk Delete条件へ直接使用しない。自然言語操作はApplication層で対象候補を解決し、確認可能な固定ID集合へ変換してから同じUse Caseを使用する。

Planが保持できる対象は初期値10,000件を上限とする。上限超過時はPlanを一部作成せず、`413 DELETION_PLAN_TARGET_LIMIT_EXCEEDED`とする。この値の変更はSecurity、LatencyおよびRecovery Testを伴うConfiguration変更として扱う。

### 37.4 Plan Creation Result

対象が1件以上ある場合は`201 Created`、`Location: /api/v1/memory-deletion-plans/{deletionPlanId}`を返す。

```json
{
  "outcome": "PLAN_CREATED",
  "plan": {
    "deletionPlanId": "9b3c0a4c-6d5e-4f70-a182-3456789abcde",
    "scope": "FILTERED",
    "status": "READY",
    "targetCount": 24,
    "targetPage": {
      "items": [
        {
          "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
          "expectedVersion": 3,
          "status": "READY",
          "memory": { "...": "MemoryResource" },
          "reasonCode": null
        }
      ],
      "nextCursor": "opaque-cursor",
      "hasMore": true
    },
    "retainedData": [
      "CONVERSATION_HISTORY",
      "MEMORY_PREFERENCES",
      "EXPORTED_BACKUPS"
    ],
    "version": 1,
    "etag": "\"opaque-plan-etag\"",
    "createdAt": "2026-08-28T22:00:00.000+09:00",
    "reviewExpiresAt": "2026-08-29T02:00:00.000+09:00",
    "executedAt": null,
    "result": null
  }
}
```

対象が0件の場合はPlanを作成せず、`200 OK`を返す。

```json
{
  "outcome": "NO_TARGETS",
  "plan": null
}
```

`SELECTED`に不存在または削除済みIDが1件でも含まれる場合、一部のIDだけでPlanを作成しない。`422 DELETION_TARGET_NOT_FOUND`として、不一致件数だけを安全なExtension Fieldで返す。Memory本文や存在する他IDの詳細をErrorへ含めない。

### 37.5 Deletion Plan Resource

| Field | Type | Required | Nullable | Description |
|---|---|---:|---:|---|
| `deletionPlanId` | string | Yes | No | Plan ID |
| `scope` | enum | Yes | No | `SELECTED`、`FILTERED`、`ALL` |
| `status` | enum | Yes | No | Section 37.6のLifecycle Status |
| `targetCount` | integer | Yes | No | Preview時に固定した対象件数 |
| `targetPage` | object | Yes | No | 固定対象のPageまたは実行結果Page |
| `retainedData` | array of enum | Yes | No | 削除対象外Dataの明示 |
| `version` | integer | Yes | No | Plan状態の単調増加Version |
| `etag` | string | Yes | No | Plan Versionに対応するStrong ETag |
| `createdAt` | string | Yes | No | JST Offset付き作成日時 |
| `reviewExpiresAt` | string | Yes | No | 作成から固定4時間のReview期限 |
| `executedAt` | string | Yes | Yes | 最初に実行を受理した日時 |
| `result` | object | Yes | Yes | Execute後のSummary |

`retainedData`は常に次を含む。

```text
CONVERSATION_HISTORY
MEMORY_PREFERENCES
EXPORTED_BACKUPS
```

Memory Revision、検索Projection、Cache、Pending処理および再登録防止Guardは内部削除処理へ含まれるが、独立したユーザーデータ件数として`targetCount`へ加算しない。

### 37.6 Plan Lifecycle

| Status | Meaning | Execute Allowed |
|---|---|---:|
| `READY` | Preview済みで確認待ち | Yes |
| `EXECUTING` | 実行Intentを永続化し処理中 | No |
| `COMPLETED` | 全対象のContent削除と必要なGuard反映を確認済み | No |
| `PARTIAL` | 一部だけ完了し、残りは失敗または未完了 | No |
| `FAILED` | 対象を1件も完全削除できず、最終的な失敗を確認済み | No |
| `UNKNOWN` | 1件以上の最終状態を確定できず、同一OperationでRecovery中 | No |
| `EXPIRED` | Execute開始前にReview期限を過ぎた | No |
| `INVALIDATED` | 対象Version、Reset Generationまたは削除境界が変化した | No |

確定Terminal Statusは`COMPLETED`、`PARTIAL`、`FAILED`、`EXPIRED`および`INVALIDATED`である。`UNKNOWN`はClientから再Executeできないが、同一OperationのRecoveryによって確定Terminal Statusへ収束できる非実行可能状態とする。Terminal Planを別の論理操作へ再利用しない。

Plan Review期限は作成から固定4時間とし、GET、PATCH、Confirm、Pollingまたは再接続で延長しない。期限後はTokenとSensitive Temporary Stateを無効化して`EXPIRED`へ遷移する。Plan MetadataとContent-free Resultは、Network切断後の結果照合のため期限切れまたはTerminal化から最低24時間取得可能とする。PlanへMemory本文Snapshotを永続化しない。

### 37.7 Target Page and Hydration

`GET /api/v1/memory-deletion-plans/{deletionPlanId}`は`limit`と`cursor`だけを受け付ける。Default 20、Maximum 100とし、CursorはPlan ID、不変のTarget Set Versionおよび対象順序へBindingする。有効期間は発行から最大30分かつ`reviewExpiresAt`までとし、Plan Versionだけの変更では無効化しない。

```json
{
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "expectedVersion": 3,
  "status": "READY",
  "memory": { "...": "MemoryResource" },
  "reasonCode": null
}
```

Target Status:

| Status | `memory` | Meaning |
|---|---|---|
| `READY` | non-null | Preview時Versionと現在Versionが一致 |
| `STALE` | non-null | 現在Resourceは存在するがVersionが変化 |
| `DELETED` | `null` | このPlanにより完全削除済み |
| `FAILED` | `null` | 最終的な削除失敗を確認済み |
| `UNKNOWN` | `null` | 最終状態を確定できない |
| `UNAVAILABLE` | `null` | Preview表示用Resourceを一時取得不能 |

`memory`はGET時にAuthoritative Repositoryから取得する現在Resourceであり、Plan作成時のSnapshotではない。Version差分を検出した場合、Planを`INVALIDATED`へ遷移させる。更新後本文をPreview時に確認した本文として表示しない。

Planの固定対象順はPreview時に確定し、Page間で変更しない。Content Size上限へ達した場合は完全なItem単位でPageを終了し、Memory本文を切断しない。

### 37.8 Execute Request

Execute直前に`POST /api/v1/memory-deletion-plans/{deletionPlanId}/confirm`をBodyなし・Strong `If-Match`付きで呼び、Section 42.4〜42.7の短命Tokenを取得する。

```http
POST /api/v1/memory-deletion-plans/{deletionPlanId}/execute
Idempotency-Key: 6d8424f4-33f1-4be4-9428-31c991f7f8c1
If-Match: "opaque-plan-etag"
Content-Type: application/json
```

```json
{
  "confirmationToken": "opaque-single-use-token"
}
```

`confirmationToken`はPlan ID、固定対象Digest、Plan Version、Reset Generationおよび発行から15分以内かつReview期限までの有効期限へBindingする。最大2,048 ASCII CharacterのOpaque Valueとし、Clientは解析・生成しない。Token、Token Digestおよび固定対象Digestを通常Logへ記録しない。

Executeは`If-Match`を必須とする。欠落は`428 PRECONDITION_REQUIRED`、Plan ETag不一致は`412 PRECONDITION_FAILED`とする。Token欠落・形式不正は`400 VALIDATION_ERROR`、不一致・使用済みは`409 DELETION_CONFIRMATION_INVALID`、Token期限切れは`409 DELETION_CONFIRMATION_EXPIRED`とする。Review期限内ならConfirmを再実行できる。

### 37.9 Execute Preflight and Atomic Start Boundary

削除開始前に、次をすべて検証する。

1. Planが`READY`で期限内である
2. `If-Match`とPlan Versionが一致する
3. Confirmation TokenのBindingが一致する
4. 全対象MemoryがPreview時のIDとExpected Versionで存在する
5. Reset Generationと削除境界が変わっていない
6. 同じPlanの別Executeが開始していない

1件でも不一致ならContent削除を開始せず、Planを`INVALIDATED`へ遷移させて`409 DELETION_PLAN_INVALIDATED`を返す。Clientは最新対象で新しいPlanを作成し直す。

全検証後、Execution IntentとToken消費を永続化してから`EXECUTING`へ遷移する。Execute時にFilter、検索Queryまたは現在一覧を再評価して対象を増減させない。

Preflight後に並行更新やInfrastructure Failureが発生する可能性があるため、各対象削除もExpected Versionで保護する。この段階の競合や失敗は、実際に完了した対象を失わず`PARTIAL`または`UNKNOWN`へ反映する。

### 37.10 Execute Response and Result Polling

実行がResponse待機時間内にTerminal Statusへ到達した場合は`200 OK`で最新Plan Resourceを返す。処理継続中の場合は`202 Accepted`、`Location`と`Retry-After`を返し、Bodyに`status = EXECUTING`のPlan Resourceを含める。

`202`はExecution Intentの永続化に成功した場合だけ返す。Clientは`GET /api/v1/memory-deletion-plans/{deletionPlanId}`でTerminal Statusを確認する。Network切断を削除失敗と推測せず、同じIdempotency-KeyでExecuteを再送するかGETで照合する。

Result Summary:

```json
{
  "requestedCount": 24,
  "deletedCount": 23,
  "failedCount": 1,
  "unknownCount": 0,
  "completedAt": "2026-08-28T22:03:10.000+09:00"
}
```

次のInvariantを満たす。

```text
requestedCount = targetCount
requestedCount = deletedCount + failedCount + unknownCount
status = COMPLETED なら deletedCount = requestedCount
status = PARTIAL なら deletedCount > 0 かつ failedCount > 0 かつ unknownCount = 0
status = FAILED なら deletedCount = 0 かつ failedCount = requestedCount
status = UNKNOWN なら unknownCount > 0 かつ完全な分類を保証できない
```

`UNKNOWN`中の`completedAt`は`null`とし、全Target分類とAtomic Finalization完了後にだけ確定日時を設定する。

`PARTIAL`を`COMPLETED`または一般的な成功Messageとして表示しない。`UNKNOWN`では削除済みとも未削除とも断定せず、通常Memory Mutation、Restore、Relation ResolveおよびBackup ExportがRecovery完了まで一時利用不能であることをContent-freeに通知する。Clientは新しいExecuteを送らず、同じPlanをGETで照合する。

### 37.11 Delete-all Reset Point Rule

`scope = ALL`では、全対象のContent削除、必要なGuard反映および派生Data無効化を確認できた後にだけMemory Reset Pointを完了状態へ進める。

`UNKNOWN`では全件Reset完了と扱わず、Recovery FenceとPending Resetを維持する。全Targetを`DELETED`または`NOT_DELETED`へ確定した後だけ、同一Operation内で`COMPLETED`、`PARTIAL`または`FAILED`へ収束する。

`ALL`の`COMPLETED`だけReset Generationを進める。`PARTIAL`または`FAILED`ではPending ResetをAtomicに取消し、確定削除件数に応じてCount / Boundaryだけを更新する。Plan Result、Reset PointおよびFence解放が同時に確定するまでClientへ安定状態として公開しない。

Conversation History、Memory Preferencesおよび既にExportしたBackupは、Delete-allでも削除しない。

### 37.12 Idempotency and Replay

- Plan作成の同一Key・同一Bodyは同じPlanまたは同じ`NO_TARGETS`結果へ収束する。
- Executeの同一Key・同一Plan・同一Bodyは、処理を重複開始せず現在または保存済みの同じ論理結果を返す。
- 同一Keyを異なるBody、PathまたはPlanへ再利用した場合は`409 IDEMPOTENCY_KEY_CONFLICT`とする。
- Terminal Planへ新しいKeyでExecuteした場合は`409 DELETION_PLAN_NOT_EXECUTABLE`とする。
- `EXECUTING` Planへ別KeyでExecuteした場合は`409 DELETION_EXECUTION_IN_PROGRESS`とする。

Idempotency Replayでは初回と同じHTTP Statusを機械的に固定せず、同じOperation Identityに対する現在のAuthoritative Plan Resourceを返せる。これにより初回`202 EXECUTING`後の再送で、確定済み`200 COMPLETED`を返せる。

### 37.13 Security, Logging and Non-fields

Plan、Token、TargetおよびResultへ次を含めない。

- Memory Revision本文または削除済み本文Snapshot
- Re-registration Fingerprint、Key VersionまたはDigest入力
- Search Query、Semantic Score、EmbeddingまたはVector Distance
- Conversation本文またはMemory抽出元Message
- DynamoDB Key、Transaction ID、Queue名またはProvider情報
- Exception Message、Stack Traceまたは内部Retry回数

Operation AuditにはPlan ID、Operation Type、件数、Statusおよび非機密Reason Codeを保持できる。Memory本文、Target一覧、Confirmation Token、FingerprintおよびCursorを通常Logへ記録しない。

GETによるPreview Targetの取得もMemory Readとして扱い、通常のResource Security Ruleを適用する。Plan期限切れ後またはTerminal化後はConfirmation Tokenを返さない。

### 37.14 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-085 | 複数・全削除をDeletion PlanのPreviewとExecuteへ分離する | Accepted |
| API2-086 | Plan Scopeを`SELECTED`、`FILTERED`、`ALL`へ限定し、空Filterを全件削除へ変換しない | Accepted |
| API2-087 | Semantic Searchや自由入力QueryをBulk Delete条件へ直接使用せず、確認可能な固定対象へ変換する | Accepted |
| API2-088 | Plan作成時に対象ID、Expected Version、順序、Reset Generationおよび削除境界を固定する | Accepted |
| API2-089 | Plan対象上限を初期10,000件、SELECTED入力上限を100件とする | Accepted |
| API2-090 | 対象0件ではPlanを作成せず`200 NO_TARGETS`を返し、SELECTEDの一部不存在を暗黙除外しない | Accepted |
| API2-091 | Preview対象をOpaque CursorでPage表示し、Memory本文SnapshotをPlanへ永続化しない | Accepted |
| API2-092 | Plan状態を`READY`、`EXECUTING`、`COMPLETED`、`PARTIAL`、`FAILED`、`UNKNOWN`、`EXPIRED`、`INVALIDATED`で表す | Accepted |
| API2-093 | Plan Review期限を固定4時間、Confirmation Token期限を発行から15分以内とし、Content-free ResultをTerminal化後最低24時間取得可能にする | Accepted |
| API2-094 | Confirmation TokenをPlan、固定対象、Version、Reset Generationおよび期限へBindingし、Executeで`If-Match`とともに要求する | Accepted |
| API2-095 | Execute前に全対象Versionと削除境界を検証し、不一致時は削除を開始せずPlanを`INVALIDATED`とする | Accepted |
| API2-096 | Execute時に対象を再検索せず、Preflight後の各削除もExpected Versionで保護する | Accepted |
| API2-097 | 同期完了時は`200`、処理継続時はDurable Intent確立後だけ`202`を返し、GETで結果確認可能にする | Accepted |
| API2-098 | Batch結果にRequested、Deleted、Failed、Unknown件数を持ち、`PARTIAL`と`UNKNOWN`を成功へ偽装しない | Accepted |
| API2-099 | Delete-allのReset Pointを全対象の完全削除とGuard反映確認後だけ確定する | Accepted |
| API2-100 | Conversation History、Memory PreferencesおよびExport済みBackupをDeletion Planの削除対象外として常に明示する | Accepted |
| API2-101 | Plan作成とExecuteを別々にIdempotent化し、同じ論理Operationの再送で現在結果へ収束させる | Accepted |
| API2-102 | Terminal Planの再利用と実行中Planの別Operation開始を拒否する | Accepted |
| API2-103 | Confirmation Token、Target一覧、FingerprintおよびMemory本文を通常Logへ記録しない | Accepted |
| API2-104 | Plan ResponseからRevision本文、削除済みSnapshot、検索内部情報およびInfrastructure情報を除外する | Accepted |

DB2-151〜DB2-164との統合により、`UNKNOWN`は同一Deletion OperationのRecovery状態、確定Terminalは`COMPLETED` / `PARTIAL` / `FAILED`として扱う。Client-visible Enumは変更せず、再Execute禁止、Polling、Reset確定および一時的なMutation制限をSections 37.6、37.10、37.11へ反映済みである。

### 37.15 Next API Design Topics

API2-085〜API2-104は承認済みである。次の順序で詳細化する。

1. Phase 2 Problem DetailsとError Mapping
2. Response / Request Size Limits
3. Backup / Restore設計後のAPI追加
4. Phase 2 API Design Review

---

## 38. Phase 2 Problem Details and Error Mapping

### 38.1 Purpose and Compatibility

Phase 2 Memory APIはSection 21のRFC 9457 Problem Details形式とSection 22のPhase 1共通Error Codeを継続利用する。本SectionはMemory固有のCode、Extension、Validation順序、Retry可否およびEndpoint Mappingを追加するものであり、Phase 1 Error Contractを破壊的に変更しない。

FlutterはHTTP Statusまたは`detail`だけで処理を分岐せず、`code`と本Sectionの`recoveryAction`を使用する。Backend内部のException Class、AWS / OpenAI固有CodeまたはPersistence TechnologyをPublic Error Codeにしない。

### 38.2 Phase 2 Common Problem Fields

Phase 2 Memory EndpointのProblem DetailsはSection 21.2の共通Fieldに加え、次を必須とする。

```json
{
  "type": "urn:project-alice:problem:precondition-failed",
  "title": "Precondition failed",
  "status": 412,
  "detail": "対象は表示後に変更されています。最新状態を取得してください。",
  "code": "PRECONDITION_FAILED",
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb",
  "retryable": false,
  "recoveryAction": "REFRESH_RESOURCE"
}
```

| Field | Type | Required | Description |
|---|---|---:|---|
| `retryable` | boolean | Yes | 同じ論理RequestをClientが自動再試行してよいか |
| `recoveryAction` | enum | Yes | Clientが次に行う安全な処理 |

`retryable = true`は、新しいIdempotency-Keyや変更済みBodyで再送してよいという意味ではない。同じOperation Identityと同じRequestを、Contractで許可された場合だけ再試行する。

Recovery Action:

| Value | Client Behavior |
|---|---|
| `CORRECT_REQUEST` | 入力を修正して新しいUser操作として送信する |
| `REFRESH_RESOURCE` | Authoritative ResourceをGETし直し、最新ETagでUserに再確認を求める |
| `REPLAY_SAME_REQUEST` | 同じPath、Body、Idempotency-KeyまたはDelete Operation Identityで再送する |
| `RETRY_LATER` | `Retry-After`を尊重して同じRequestを再試行する |
| `CREATE_NEW_DELETION_PLAN` | 最新対象でPreviewから作り直す |
| `CREATE_NEW_RESTORE_PLAN` | Archiveを再検証し、最新状態でRestore Previewから作り直す |
| `RECONFIRM_PLAN` | Plan内容を維持したままUserへ最終確認を再提示し、新Tokenを取得する |
| `RESUME_OR_CANCEL_RESTORE_PLAN` | 既存Restore Planを再開するか、User確認後に明示Cancelする |
| `RESTART_MEMORY_OPERATION` | CandidateをUserへ再確認し、新しいRegister / Update Operationとして開始する |
| `USER_REVIEW` | 自動処理せず、関係・分類・Sensitivity等をUserが確認する |
| `REAUTHENTICATE_DEVICE` | 自動再送を止め、Platform Secure Storageの端末Credentialを再設定する |
| `NONE` | Client側で安全な自動Recoveryを行わない |

`Retry-After` Headerを返す場合、`retryable = true`かつ`recoveryAction = RETRY_LATER`または`REPLAY_SAME_REQUEST`とする。秒数をProblem Bodyへ重複保持せず、HeaderをSource of Truthとする。

### 38.3 Problem Type and Code Stability

`type`は次の形式とする。

```text
urn:project-alice:problem:<lowercase-kebab-case-code>
```

例：`MEMORY_NOT_FOUND`は`urn:project-alice:problem:memory-not-found`。

一つの`code`は一つの意味とDefault HTTP Statusを持つ。既存Codeの意味変更や別Statusへの再利用を行わない。新しい詳細理由が必要な場合は新Codeまたは承認済みExtensionを追加する。

`title`はCodeごとの安定した英語Summaryとし、`detail`はUserへ表示可能な日本語説明とする。Client Logicはどちらの文字列にも依存しない。

### 38.4 Validation Order

複数の問題が同時に存在するRequestは、次の順序で評価する。

1. Listener、Interface、TLS HandshakeおよびSecurity Profile
2. Request LineとHeader Size Hard Limit
3. `PRIVATE_LAN_SECURE`のAllowed Device Authentication
4. HTTP Request Size、Method、Content-Type、Accept
5. JSON SyntaxとEncoding
6. Path ID、Query Parameter、必須HeaderおよびHeader形式
7. Body Shape、Required Field、型、Unknown Field、Tagged Union
8. Canonicalization後の文字、Code Point、Byte、Enumおよび件数制約
9. Application / Domain Policy
10. Resource存在、現在Version、Idempotency StateおよびOperation State
11. Infrastructure実行結果

前段でRequest全体を安全に解釈できない場合、後段のDomain判定やResource検索を行わない。たとえばMalformed JSONを`MEMORY_NOT_FOUND`にせず`MALFORMED_REQUEST`とする。

Domain Policyでは次の順序を使用する。

1. 保存禁止情報
2. Sensitivity整合性
3. Atomicityと長期情報としての成立性
4. Category整合性
5. Re-registration Policy
6. Existing Memory Relation

保存禁止情報が含まれる場合、後続判定結果や一致した既存Memoryを返さない。

### 38.5 Field Validation Errors

`VALIDATION_ERROR`ではSection 21.3の`errors`を使用する。Field PathはSchema上の名前をDot / Index記法で表す。

```json
{
  "field": "memoryIds[1]",
  "code": "INVALID_FORMAT",
  "message": "Memory IDの形式が正しくありません。"
}
```

同一RequestのField Errorは次の順で安定化する。

1. Endpoint Schemaで定義したField順
2. Array Index昇順
3. 同一Schema Pathでは`REQUIRED`、`AT_LEAST_ONE_REQUIRED`、`INVALID_TYPE`、`UNKNOWN_FIELD`、`INVALID_COMBINATION`、`INVALID_FORMAT`、`INVALID_ENUM`、`INVALID_CHARACTER`、`NOT_BLANK`、`TOO_LONG`、`TOO_LARGE`、`OUT_OF_RANGE`、`DUPLICATE_VALUE`の順

Object-levelの`AT_LEAST_ONE_REQUIRED`はTop-level Bodyなら`field = "$"`、Nested ObjectならそのContaining Object Path（例：`filters`）へ一件だけ付与する。未知Fieldだけが存在しても、認識済みFieldが一つ以上あるとは扱わない。

Validation抑止Ruleは次のとおりとする。

- `REQUIRED`または`AT_LEAST_ONE_REQUIRED`が成立するPathでは、値を前提とする後続Codeを返さない。
- `INVALID_TYPE`ではFormat、Enum、Character、Blank、Length、Size、RangeおよびDuplicateを評価しない。
- `UNKNOWN_FIELD`は未知Field自身へ一件だけ返し、その値の内容を追加検証しない。
- `INVALID_COMBINATION`は参加FieldがKnownかつ型・形式を安全に解釈できる場合だけ評価する。
- 同一Path・同一Validation Stageで複数Violationが成立する場合、固定優先順位の最上位一件だけを返す。

Phase 2で次のField-level Codeを追加する。

| Code | Meaning |
|---|---|
| `INVALID_TYPE` | JSON値の型がSchemaと異なる |
| `DUPLICATE_VALUE` | 重複禁止のArrayまたは集合に同じ値がある |
| `INVALID_COMBINATION` | Tagged UnionやField組合せが不正 |
| `AT_LEAST_ONE_REQUIRED` | FilterやPATCHで1 Field以上が必要 |

一つのResponseで返すField Errorは最大50件とし、超過時は先頭50件だけを返してTop-levelに`errorsTruncated = true`を追加する。省略がなければField自体を省略し、`false`を冗長に返さない。Rejected Value、Memory本文、検索語またはTokenを`errors`へEchoしない。

### 38.6 Domain Validation Problems

HTTP / JSONとして正しくても保存・更新できない入力は`422 Unprocessable Content`とする。

| Code | Condition | Recovery Action |
|---|---|---|
| `MEMORY_CONTENT_PROHIBITED` | Secretその他の保存禁止情報を検出 | `CORRECT_REQUEST` |
| `MEMORY_SENSITIVITY_MISMATCH` | 指定SensitivityがDomain判定と安全に一致しない | `USER_REVIEW` |
| `MEMORY_CONTENT_NOT_ATOMIC` | 独立した複数事実が混在し、一つのMemoryとして成立しない | `CORRECT_REQUEST` |
| `MEMORY_CATEGORY_MISMATCH` | Contentの主題とCategoryが一致しない | `USER_REVIEW` |
| `MEMORY_POLICY_REJECTED` | 上記以外のApproved保存基準を満たさない | `CORRECT_REQUEST` |

すべて`retryable = false`とする。修正後の入力は同じ失敗RequestのRetryではなく、新しいUser判断に基づくRequestとして扱う。

`MEMORY_CONTENT_PROHIBITED`のResponseへSecret種別、検出位置、一致文字列、正規表現またはFingerprintを含めない。`MEMORY_CATEGORY_MISMATCH`でBackend推定Categoryを自動確定値として返さない。

### 38.7 Existing Memory Relation Problem

RegisterまたはUpdateで、別Memoryとの補足・変更・矛盾・不確実関係を検出した場合は`409 MEMORY_RELATION_REVIEW_REQUIRED`とする。

```json
{
  "type": "urn:project-alice:problem:memory-relation-review-required",
  "title": "Memory relation review required",
  "status": 409,
  "detail": "既存のMemoryとの関係を確認してください。",
  "code": "MEMORY_RELATION_REVIEW_REQUIRED",
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb",
  "retryable": false,
  "recoveryAction": "USER_REVIEW",
  "relationReviewId": "8a1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "relationReviewUri": "/api/v1/memory-relation-reviews/8a1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "relationType": "CONFLICT",
  "relatedMemoryCount": 1,
  "reviewExpiresAt": "2026-08-29T22:30:00.000+09:00"
}
```

`relationType`は`COMPLEMENTARY_SAME_FACT`、`SUPERSEDES`、`CONFLICT`または`UNCERTAIN`に限定する。AuthoritativeなCandidate、対象集合、Expected VersionおよびMutation PreviewはSection 45のRelation Review Resourceだけが保持する。Problemへ関連Memory ID、本文、Revision、ScoreまたはAI理由を含めない。

Clientは`relationReviewUri`を取得してUserへ比較と選択肢を表示し、Section 45のResolve Endpointを使用する。Review作成に失敗した場合はReview IDなしの`409`を返さず`503 SERVICE_UNAVAILABLE`とする。

### 38.8 Resource, Message and Plan Not-found Mapping

| HTTP | Code | Condition | Recovery Action |
|---:|---|---|---|
| `404` | `MEMORY_NOT_FOUND` | Memory不存在または削除済み | `NONE` |
| `404` | `MESSAGE_NOT_FOUND` | Message IDに対応するMessage不存在 | `NONE` |
| `404` | `MEMORY_USAGE_NOT_FOUND` | MessageはAssistant Messageではない | `NONE` |
| `404` | `DELETION_PLAN_NOT_FOUND` | Plan不存在または保持期間終了 | `NONE` |

すべて`retryable = false`とする。Memory不存在と削除済みを区別せず、Deletion Guardや過去本文の存在を公開しない。Phase 2以前等のAssistant MessageでTraceが記録されていない場合はErrorにせずSection 36の`200 NOT_RECORDED`を返す。

### 38.9 Concurrency and Preconditions

| HTTP | Code | Condition | Retryable | Recovery Action |
|---:|---|---|---:|---|
| `428` | `PRECONDITION_REQUIRED` | 必須`If-Match`がない | false | `REFRESH_RESOURCE` |
| `412` | `PRECONDITION_FAILED` | Strong ETagが現在Versionと不一致 | false | `REFRESH_RESOURCE` |
| `409` | `REQUEST_IN_PROGRESS` | 同一Operationが処理中 | true | `RETRY_LATER` |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 同一Keyを別Requestへ再利用 | false | `CORRECT_REQUEST` |

`REQUEST_IN_PROGRESS`には`Retry-After`を付与する。`PRECONDITION_FAILED`で最新Resourceや最新ETagをProblemへ埋め込まず、通常GETで再取得させる。Clientは古いBodyを最新ETagへ付け替えて自動送信しない。

`IDEMPOTENCY_KEY_REQUIRED`と`INVALID_IDEMPOTENCY_KEY`はPhase 1同様`400`、`retryable = false`、`recoveryAction = CORRECT_REQUEST`とする。

### 38.10 Deletion-specific Problems

| HTTP | Code | Condition | Retryable | Recovery Action |
|---:|---|---|---:|---|
| `413` | `DELETION_PLAN_TARGET_LIMIT_EXCEEDED` | Plan対象が上限10,000件を超過 | false | `CORRECT_REQUEST` |
| `422` | `DELETION_TARGET_NOT_FOUND` | `SELECTED`に不存在・削除済み対象を含む | false | `REFRESH_RESOURCE` |
| `409` | `DELETION_CONFIRMATION_EXPIRED` | Tokenだけが15分期限超過 | false | `RECONFIRM_PLAN` |
| `409` | `DELETION_CONFIRMATION_INVALID` | Token不一致、改変、使用済みまたはBinding不一致 | false | `RECONFIRM_PLAN` |
| `409` | `DELETION_PLAN_INVALIDATED` | 対象Version、Reset Generationまたは削除境界が変化 | false | `CREATE_NEW_DELETION_PLAN` |
| `409` | `DELETION_PLAN_NOT_EXECUTABLE` | Terminal Planを再利用 | false | `CREATE_NEW_DELETION_PLAN` |
| `409` | `DELETION_EXECUTION_IN_PROGRESS` | 別Operationとして同じPlanを実行中 | true | `RETRY_LATER` |
| `409` | `MEMORY_DELETION_RECONCILIATION_REQUIRED` | 個別削除がPartialまたは結果不明 | true | `REPLAY_SAME_REQUEST` |

`DELETION_TARGET_NOT_FOUND`には`missingTargetCount`だけを追加できる。欠落ID、存在する他ID、本文またはGuard情報をProblemへ含めない。

Plan Errorには安全な範囲で`deletionPlanId`と`planStatus`を追加できる。Confirmation Token、対象DigestまたはTarget一覧は返さない。`DELETION_EXECUTION_IN_PROGRESS`には`Retry-After`を付与し、ClientはGETによる結果確認を優先できる。

個別削除の`MEMORY_DELETION_RECONCILIATION_REQUIRED`には次を追加できる。

```json
{
  "operationStatus": "UNKNOWN",
  "memoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc"
}
```

`operationStatus`は`PARTIAL`または`UNKNOWN`だけとする。Clientは同じMemory IDと削除前ETagによる同一Delete OperationをReplayし、別ETagや新しい操作として推測実行しない。

Batch Deleteの`PARTIAL`、`FAILED`および`UNKNOWN`は、HTTP Transport ErrorではなくSection 37のAuthoritative Deletion Plan Resource Statusとして取得する。Problem Detailsへ変換してPlan結果を失わせない。

### 38.11 Cursor and Query Problems

| HTTP | Code | Condition | Retryable | Recovery Action |
|---:|---|---|---:|---|
| `400` | `INVALID_CURSOR` | Cursor不正、改変、期限切れまたは条件不一致 | false | `CORRECT_REQUEST` |
| `400` | `VALIDATION_ERROR` | Filter、Sort、limitまたは未知Query Parameterが不正 | false | `CORRECT_REQUEST` |

Cursor Errorで署名結果、内部条件、Partition Key、Plan Versionまたは有効期限を返さない。ClientはCursorなしで先頭Pageを再取得できるが、それを同じPageの自動Retryとは扱わない。

### 38.12 Required Capability Failure

Memory Repository、Preferences、Management Search、Usage TraceまたはDeletion Execution等、Endpoint完了に必須のCapabilityが一時利用不能な場合は`503 SERVICE_UNAVAILABLE`とする。

| Property | Value |
|---|---|
| `retryable` | `true` |
| `recoveryAction` | `RETRY_LATER` |
| Header | 安全なRetry時刻を判断できる場合のみ`Retry-After` |

Provider名、Table名、Index名、Exception、HTTP Upstream StatusまたはRetry回数を返さない。

Answer-time Memory検索やAutomatic Captureは、Section 10 / 14のGraceful Degradation Ruleに従う。MemoryなしでConversationを正常継続できる失敗を、Conversation API全体の`503`へ昇格させない。Transparency Trace取得自体が判定不能な場合など、要求されたEndpointを完了できない場合だけProblem Detailsを返す。

### 38.13 Unexpected and Result-unknown Errors

予期しないBackend Errorは`500 INTERNAL_ERROR`、`retryable = false`、`recoveryAction = NONE`とする。Clientが自動RetryするとMutation重複や状態誤認の可能性があるため、結果が不明なMutationを単純な`500`に変換しない。

Operation IdentityまたはAuthoritative Operation Resourceで照合できる場合は、次を優先する。

- Register / Plan Create / Plan Execute：同じIdempotency-KeyでReplay
- Individual Delete：同じMemory IDと削除前ETagでReplay
- Deletion Plan：Plan GETでStatus確認
- Update / Confirm：Memory GETでVersionと日時を確認

Infrastructure Timeout後も結果を確定できない個別削除はSection 38.10の`MEMORY_DELETION_RECONCILIATION_REQUIRED`へ変換する。Batch DeleteはPlanを`UNKNOWN`へ収束させる。内部状態が不明なのに新しいOperationを促さない。

### 38.14 Endpoint Mapping Summary

| Endpoint Group | Main Problem Codes |
|---|---|
| Register | `VALIDATION_ERROR`、Domain Validation Codes、`MEMORY_RELATION_REVIEW_REQUIRED`、Idempotency Codes、`SERVICE_UNAVAILABLE` |
| List / Search / Get | `VALIDATION_ERROR`、`INVALID_CURSOR`、`MEMORY_NOT_FOUND`、`SERVICE_UNAVAILABLE` |
| Update / Confirm | `VALIDATION_ERROR`、Domain Validation Codes、`MEMORY_RELATION_REVIEW_REQUIRED`、`PRECONDITION_REQUIRED`、`PRECONDITION_FAILED`、`MEMORY_NOT_FOUND` |
| Individual Delete | `PRECONDITION_REQUIRED`、`PRECONDITION_FAILED`、`MEMORY_NOT_FOUND`、`REQUEST_IN_PROGRESS`、`MEMORY_DELETION_RECONCILIATION_REQUIRED` |
| Preferences | `VALIDATION_ERROR`、`PRECONDITION_REQUIRED`、`PRECONDITION_FAILED`、`SERVICE_UNAVAILABLE` |
| Memory Usage | `VALIDATION_ERROR`、`MESSAGE_NOT_FOUND`、`MEMORY_USAGE_NOT_FOUND`、`SERVICE_UNAVAILABLE` |
| Deletion Plan Create | `VALIDATION_ERROR`、`DELETION_TARGET_NOT_FOUND`、`DELETION_PLAN_TARGET_LIMIT_EXCEEDED`、Idempotency Codes |
| Deletion Plan Get | `VALIDATION_ERROR`、`INVALID_CURSOR`、`DELETION_PLAN_NOT_FOUND`、`SERVICE_UNAVAILABLE` |
| Deletion Plan Execute | `PRECONDITION_REQUIRED`、`PRECONDITION_FAILED`、Deletion-specific Codes、Idempotency Codes、`SERVICE_UNAVAILABLE` |
| Relation Review Get | `VALIDATION_ERROR`、`INVALID_CURSOR`、`MEMORY_RELATION_REVIEW_NOT_FOUND`、`MEMORY_RELATION_REVIEW_EXPIRED`、`SERVICE_UNAVAILABLE` |
| Relation Review Resolve | `VALIDATION_ERROR`、`PRECONDITION_REQUIRED`、`PRECONDITION_FAILED`、Relation Review-specific Codes、Idempotency Codes、`SERVICE_UNAVAILABLE` |

Protocol共通Errorとして`MALFORMED_REQUEST`、`PAYLOAD_TOO_LARGE`、`UNSUPPORTED_MEDIA_TYPE`、`NOT_ACCEPTABLE`および`INTERNAL_ERROR`を全Endpointへ適用する。`PRIVATE_LAN_SECURE`では、全Endpointへ`SECURE_TRANSPORT_REQUIRED`と`DEVICE_AUTHENTICATION_REQUIRED`も共通適用する。

### 38.15 Sensitive Data and Error Logging

Problem Details、Field Error、LogおよびMetric Labelへ次を含めない。

- RequestのMemory本文、検索語またはConversation本文
- Secret検出値、検出位置、種別またはFingerprint
- Confirmation Token、Cursor、Idempotency-Key全文またはETag全文
- Related Memory本文、Revision、ScoreまたはAI推論
- 削除対象一覧、削除済み本文またはGuard
- Provider Raw Error、SDK Message、Stack TraceまたはInfrastructure Identifier
- `Authorization` Header、Device Token、Token Digest、Certificate Private Keyまたは認証失敗の内部理由

Server Logには`requestId`、Public `code`、HTTP Status、Endpoint Template、Retryable、処理時間および安全な件数を記録できる。Pathの実IDをEndpoint Templateへ正規化し、Error DetailをそのままLog Fieldへ複製しない。

`requestId`は利用者向けResponseと内部相関に使用するが、内部Trace ID、Operation Lease IDまたはProvider Request IDをResponseへ公開しない。

### 38.16 Network Security Problems

| HTTP | Code | Condition | Retryable | Recovery Action |
|---:|---|---|---:|---|
| `401` | `DEVICE_AUTHENTICATION_REQUIRED` | Token欠落、不正形式、不一致、Revoke済みまたは未知端末 | false | `REAUTHENTICATE_DEVICE` |
| `403` | `SECURE_TRANSPORT_REQUIRED` | Application Boundaryで不安全なTransport / Profileを検出 | false | `NONE` |

`DEVICE_AUTHENTICATION_REQUIRED`には`WWW-Authenticate: Bearer realm="alice-local"`を付与する。認証失敗理由を外部で区別せず、同じStatus、Codeおよび安全なDetailへMappingする。TLS HandshakeまたはListener Boundaryで拒否した接続はHTTP Problemを返せない場合がある。

### 38.17 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-105 | Phase 2でもRFC 9457とPhase 1共通Error Contractを継続利用する | Accepted |
| API2-106 | Phase 2 Problemへ`retryable`と`recoveryAction`を必須追加する | Accepted |
| API2-107 | Problem TypeをPublic Code由来の安定URNとし、Title / Detailの文字列解析を禁止する | Accepted |
| API2-108 | Protocol、JSON、Header / Path、Body、Field、Domain、Resource、InfrastructureのValidation順序を固定する | Accepted |
| API2-109 | Field ErrorをSchema順で安定化し、最大50件と`errorsTruncated`を定義する | Accepted |
| API2-110 | 保存禁止、Sensitivity、Atomicity、Categoryおよび保存Policy違反を`422`の安定Codeへ分離する | Accepted |
| API2-111 | Existing Memoryとの補足・変更・矛盾・不確実関係を`409 MEMORY_RELATION_REVIEW_REQUIRED`へ統合する | Accepted |
| API2-112 | Relation ProblemでOpaque関連IDを最大10件だけ返す旧Contract | Accepted（API2-206でSuperseded） |
| API2-113 | Memory不存在と削除済みを同じ`404 MEMORY_NOT_FOUND`として扱う | Accepted |
| API2-114 | ETag競合を自動Retryせず、Authoritative Resource再取得へ誘導する | Accepted |
| API2-115 | Deletion Plan無効化・Token不正・Terminal再利用・実行中を別Codeで表す | Accepted |
| API2-116 | 個別削除のPartial / Unknownを同一Operationで照合する`MEMORY_DELETION_RECONCILIATION_REQUIRED`とする | Accepted |
| API2-117 | Batch Deleteの不完全結果をProblemへ変換せずAuthoritative Plan Statusで保持する | Accepted |
| API2-118 | 必須Memory Capabilityの一時障害を`503 SERVICE_UNAVAILABLE`へ隔離する | Accepted |
| API2-119 | Graceful Degradation可能なAnswer検索・自動保存失敗をConversation全体のErrorへ昇格させない | Accepted |
| API2-120 | 結果不明Mutationを単純な`500`へ変換せずOperation Identityで照合する | Accepted |
| API2-121 | Public ErrorへMemory本文、Secret、Token、Cursor、ETag、内部IDおよびProvider Errorを含めない | Accepted |
| API2-122 | LogをPublic Code、Status、Endpoint Template、Retryabilityおよび安全な件数へ限定する | Accepted |

### 38.18 Next API Design Topics

API2-105〜API2-122は承認済みである。次の順序で詳細化する。

1. Response / Request Size Limits
2. Backup / Restore設計後のAPI追加
3. Phase 2 API Design Review

---

## 39. Phase 2 Request and Response Size Limits

### 39.1 Purpose and Scope

本Sectionは、Phase 2 Memory HTTP APIのRequest Target、Header、BodyおよびResponse Body上限を定義する。目的は、過大入力によるMemory・CPU消費、意図しない大量Data返却、Client側の描画負荷およびError Response肥大化を防ぐことである。

Phase 1 Section 8.5および14.4の上限は変更しない。Phase 2では、Phase 1の128 KiB Request Body上限を共通Hard Limitとして維持し、Endpointごとにより小さい上限を追加する。

Backup / Restore Archiveは通常JSON Requestと性質が異なるため本Sectionの128 KiB上限へ押し込まず、Backup / Restore詳細設計で暗号化Archive専用のSize、Streaming、Temporary StorageおよびDecompression上限を定義する。

### 39.2 Units and Measurement Point

| Unit | Meaning |
|---|---|
| `KiB` | 1,024 Byte |
| `MiB` | 1,048,576 Byte |
| Request Body Size | HTTP Content Decoding後、JSON Parse前のUTF-8 Entity Body全体 |
| Response Body Size | UTF-8 JSON Serialization後、Transport Compression前のEntity Body全体 |

JSON Property名、Quote、Escape、Comma、BracketおよびWhitespaceもBody Sizeへ含める。Domain FieldのUTF-8上限だけを確認してHTTP Body上限を省略しない。

Phase 2 Memory JSONでは`Content-Encoding: identity`を基本とする。将来Compressionを有効化しても、解凍後のEntity Bodyへ同じ上限を適用し、圧縮後Sizeだけで受理しない。

`Content-Length`およびResponse HeaderはEntity Body Sizeに含めない。HeaderとRequest TargetにはSection 39.4の独立上限を適用する。

### 39.3 Global Request Body Hard Limit

Backup / Restoreを除くPhase 2 JSON EndpointのRequest Body共通Hard Limitを128 KiB（131,072 Byte）とする。

- Endpoint固有上限が128 KiB未満の場合、より小さい上限を適用する。
- `Content-Length`が上限超過ならBodyを読み込む前に拒否する。
- `Content-Length`がない、誤っている、またはChunkedであっても、実読込Byteを上限まで計測する。
- 上限を1 Byteでも超えた時点で読込を停止し、`413 PAYLOAD_TOO_LARGE`とする。
- Size超過BodyをJSON Parse、Canonicalize、Domain判定、Log出力またはIdempotency保存へ渡さない。

Container / Framework Levelで拒否する場合も、可能な限りSection 38のProblem Detailsへ変換する。Request Bodyの一部または計測値以外の内容をErrorへEchoしない。

`PAYLOAD_TOO_LARGE`は`retryable = false`、`recoveryAction = CORRECT_REQUEST`とする。同じ過大Bodyを自動再送しない。

### 39.4 Request Target and Header Limits

| Component | Maximum | Failure |
|---|---:|---|
| Request Target全体（Path + `?` + Query） | 8 KiB（8,192 Byte） | `414 URI_TOO_LONG` |
| Request Header Section全体 | 16 KiB（16,384 Byte） | `431 REQUEST_HEADERS_TOO_LARGE` |
| 単一`If-Match` Value | 256 ASCII Character | `400 VALIDATION_ERROR` |
| Cursor Value | 2,048 ASCII Character | `400 VALIDATION_ERROR`または`INVALID_CURSOR` |
| Confirmation Token | 2,048 ASCII Character | `400 VALIDATION_ERROR` |
| `Idempotency-Key` | Canonical UUID 36 ASCII Character | 既存Idempotency Error |

Request TargetとHeader SectionのByte計測はHTTP Serverが受信したEncoding単位で行う。ContainerがApplicationより先に拒否する場合、Connection Safetyを優先し、Problem Detailsへ変換できない可能性をClient Contractとして許容する。

`URI_TOO_LONG`と`REQUEST_HEADERS_TOO_LARGE`は`retryable = false`、`recoveryAction = CORRECT_REQUEST`とする。

検索語はPOST Bodyへ置く既存方針を維持し、Request Target上限を回避するためにURLへ移動しない。Header上限を回避する目的でToken、CursorまたはIdempotency情報を別Headerへ分割しない。

### 39.5 Endpoint-specific Request Body Limits

| Endpoint | Maximum Raw Body Size |
|---|---:|
| `POST /api/v1/memories` | 16 KiB（16,384 Byte） |
| `PATCH /api/v1/memories/{memoryId}` | 16 KiB（16,384 Byte） |
| `POST /api/v1/memories/search` | 16 KiB（16,384 Byte） |
| `PATCH /api/v1/memory-preferences` | 4 KiB（4,096 Byte） |
| `POST /api/v1/memory-deletion-plans` | 16 KiB（16,384 Byte） |
| `POST /api/v1/memory-deletion-plans/{deletionPlanId}/execute` | 4 KiB（4,096 Byte） |
| `POST /api/v1/memory-relation-reviews/{relationReviewId}/resolve` | 4 KiB（4,096 Byte） |

次のEndpointはBodyなしをContractとする。

- `GET`および`DELETE`の全Memory Endpoint
- `POST /api/v1/memories/{memoryId}/confirm`
- `POST /api/v1/memory-deletion-plans/{deletionPlanId}/confirm`
- `POST /api/v1/memory-restore-plans/{restorePlanId}/confirm`
- `POST /api/v1/memory-restore-plans/{restorePlanId}/cancel`
- `GET /api/v1/memory-relation-reviews/{relationReviewId}`

BodyなしEndpointで1 Byte以上のEntity Bodyを受信した場合、意味を推測して無視せず`400 VALIDATION_ERROR`とする。ただし共通Hard Limitを超えるBodyはParse前に`413 PAYLOAD_TOO_LARGE`とする。

### 39.6 Field and Collection Input Limits

HTTP Body上限に加えて、既存のField / Collection上限を同時に適用する。

| Input | Limit |
|---|---:|
| Memory `content` | 2,000 Unicode Code Pointかつ8 KiB UTF-8 |
| Management Search `query` | 500 Unicode Code Pointかつ2 KiB UTF-8 |
| Cursor | 2,048 ASCII Character |
| Deletion `SELECTED.memoryIds` | 100件、重複不可 |
| Deletion Plan固定対象 | 10,000件 |
| Confirmation Token | 2,048 ASCII Character |
| Field Validation Errors | 最大50件返却 |
| Relation Review関連Memory | 一Review最大100件、一Page最大20件 |

HTTP Body上限内であってもField上限を超えるRequestは、Field Contractに従い`400 VALIDATION_ERROR`またはDomain Problemとして拒否する。Field上限を満たしていてもJSON全体がEndpoint Body上限を超える場合は`413 PAYLOAD_TOO_LARGE`とする。

### 39.7 Phase 2 Response Body Limits

| Response | Maximum Raw Body Size |
|---|---:|
| 単一`MemoryResource` | 32 KiB（32,768 Byte） |
| `MemoryMutationResponse` | 32 KiB（32,768 Byte） |
| `MemoryDeletionReceipt` | 4 KiB（4,096 Byte） |
| `MemoryPreferencesResource / MutationResponse` | 8 KiB（8,192 Byte） |
| Memory List / Search Collection Page | 2 MiB（2,097,152 Byte） |
| `MemoryUsageResponse` | 512 KiB（524,288 Byte） |
| Deletion Plan Create / Get / Execute Response | 2 MiB（2,097,152 Byte） |
| Relation Review Get / Resolve Response | 2 MiB（2,097,152 Byte） |
| Phase 2 `application/problem+json` | 64 KiB（65,536 Byte） |

上限はBody全体に適用し、HTTP Headerは含めない。BodyとHeaderにETagがある場合、Body Sizeを理由に片方だけ省略しない。

単一Resource上限はMemory ContentのJSON Escape増加とMetadataを含めて設定する。Serializer設定の変更により最大Valid Resourceが32 KiBを超える場合、Resourceを途中切断せず、SerializerまたはContractを見直す。

### 39.8 Size-aware Pagination

Memory List、Memory SearchおよびDeletion Plan Target Pageでは、`limit`を最大Item件数として扱い、Response Body Size上限を保証件数として扱わない。

```text
Requested limit
      ↓
Complete Itemを順番に追加
      ↓
次ItemでBody上限超過を予測
      ↓
現在ItemまででPage終了
      ↓
hasMore = true + nextCursor
```

Rules:

- Memory ItemまたはTarget Itemを途中で切断しない。
- Content、Category、Timestampその他のFieldをSize都合で省略しない。
- 少なくとも1件のValid Itemが上限内に収まる設計を維持する。
- 上限へ先に達した場合、指定`limit`未満でも正常な`200` Pageを返す。
- `hasMore = true`とし、最後に含めたItemの安定Page Boundaryから`nextCursor`を生成する。
- 0件Pageのまま同じCursorを返す無限Loopを作らない。

次Itemを含めると上限を超えるかは、Cursor、EnvelopeおよびClosing JSON TokenのWorst-case Overheadを予約して判定する。Serialize後に末尾だけ切り落としてValid JSONへ見せかけない。

### 39.9 Non-paginated Response Overflow

単一Resource、Preferences、UsageまたはMutation ResponseはPaginationで分割しない。正常Contract上生成される最大ResponseがSection 39.7へ収まることをImplementation Testで保証する。

Contract違反または予期しない拡張によりResponse上限を超える場合：

- HTTP Response Commit前なら、元のResponseを破棄して上限内の`500 INTERNAL_ERROR`を返す。
- Commit後ならConnectionを終了し、ClientはResponseを成功扱いにしない。
- Bodyを切り詰めた`200`、不完全なJSONまたはField省略版を正常Responseとして返さない。
- Mutation自体が完了済みの場合、ClientはSection 38のOperation Identity / GET Ruleで結果を照合する。

`MemoryUsageResponse`はIncluded Memory最大8件であり、512 KiBを理由にPaginationを追加しない。Contract上限に近づく場合はSchema肥大化の兆候としてReviewする。

### 39.10 Problem Details Size Budget

Problem Details全体の上限はPhase 1と同じ64 KiBとする。次のField Budgetを適用する。

| Field | Maximum |
|---|---:|
| `type` | 256 ASCII Character |
| `title` | 128 Unicode Code Point |
| `detail` | 1,000 Unicode Code Pointかつ4 KiB UTF-8 |
| `code` | 64 ASCII Character |
| `requestId` | Canonical UUID 36 ASCII Character |
| `errors` | 50件 |
| `errors[].field` | 256 ASCII Character |
| `errors[].code` | 64 ASCII Character |
| `errors[].message` | 500 Unicode Code Pointかつ2 KiB UTF-8 |
| Relation Review Problem Extension | Review ID、URI、Relation Type、件数、期限のみ |

Problem Detailsが上限へ近づいた場合、Section 38の安定順序でArray Extensionを安全に省略し、対応する`...Truncated = true`を設定する。必須Field、`retryable`および`recoveryAction`を省略しない。

Request本文、Rejected Value、検索語、Token、Cursor、ETag、Provider ErrorまたはStack TraceをProblem Detailsへ含めてSizeを増やさない。

### 39.11 Server Enforcement

Backendは少なくとも次のBoundaryで上限を強制する。

1. HTTP Server / ContainerのRequest Target・Header・Global Body Guard
2. Presentation層のEndpoint Body・Field Validation
3. Application層のCollection件数・Domain上限
4. JSON Serialization前のResponse Budget計算
5. 実Serialize Byte数の最終確認

Container設定をAPI Contractより小さくしない。Configurationで上限を引き上げても、Field、Security、ClientおよびTest Contractを自動変更したことにはならない。

Size Reject時にBody全体をMemoryへBufferしてから判定せず、可能なBoundaryではBounded Streamとして上限+1 Byteで停止する。Request Size、Response SizeまたはContent LengthをMemory本文と同じLog Eventへ出さない。

### 39.12 Flutter Enforcement

Flutter ClientもResponse Sizeを防御的に監視する。

- `Content-Length`が既知で上限超過ならBody読込を開始しない。
- Headerがない、誤っている、またはTransfer中でも実受信・Decode後Byte数を計測する。
- JSON Parse完了前に上限超過したResponseを破棄する。
- 上限超過Responseを部分的なMemory一覧、Deletion Resultまたは成功Mutationとして表示しない。
- Collection Pageが`limit`未満でも`hasMore / nextCursor`に従う。
- Protocol違反はUser向けに安全なClient Errorとして表示し、Response本文を通常Logへ出さない。

Flutter内部Buffer、HTTP LibraryまたはJSON Decoder固有の例外文をBackend Error Codeとして扱わない。

### 39.13 Backup / Restore Boundary

Backup Export ResponseとRestore Upload / Inspect Requestには本SectionのJSON Body上限を適用しない。後続設計では少なくとも次を別途固定する。

- Encrypted Archive最大File Size
- Decryption後 / Decompression後最大Size
- Archive Entry数とMemory件数
- Compression Ratio / Zip Bomb対策
- Streaming Upload / DownloadとTemporary File上限
- Inspect ResponseとImpact PreviewのPage / Size上限
- Timeout、Cancel、RetryおよびCleanup

Backup / Restore詳細設計前に、128 KiB上限を回避するためBase64 Archiveを通常JSONへ埋め込むEndpointを追加しない。

### 39.14 Boundary Tests

各上限について少なくとも`limit - 1`、`limit`、`limit + 1` Byteまたは件数をTestする。

Test対象：

- Multi-byte UnicodeとJSON Escapeを含むRequest / Response
- `Content-Length`あり、なし、不一致、Chunked
- Endpoint Limit未満だがField Limit超過
- Field Limit内だがEndpoint Body Limit超過
- 100件未満で2 MiBへ達するCollection
- 1件目、Page途中および最後のItemでSize Boundaryへ到達
- Problem Detailsの50件目と51件目
- Container RejectとApplication RejectのError整合
- Mutation完了後にResponse Size Guardが失敗した場合のReconciliation

Test Dataへ実Secretや実Personal Memoryを使用しない。

### 39.15 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-123 | KiB / MiBをBinary Unitとし、RequestはDecode後、ResponseはUTF-8 Serialize後のEntity Bodyで計測する | Accepted |
| API2-124 | Backup / Restoreを除くPhase 2 JSON Requestの共通Hard Limitを128 KiBとする | Accepted |
| API2-125 | Register / Update / Search / Deletion Plan Createを16 KiB、Preferences / Executeを4 KiBに制限する | Accepted |
| API2-126 | BodyなしEndpointでEntity Bodyを黙って無視せず、Global上限内なら`400`、超過なら`413`とする | Accepted |
| API2-127 | Request Targetを8 KiB、Header Sectionを16 KiBに制限し、`414`と`431`を定義する | Accepted |
| API2-128 | 単一Memory系32 KiB、Collection / Deletion Plan 2 MiB、Usage 512 KiB、Preferences 8 KiB、Receipt 4 KiBとする | Accepted |
| API2-129 | Problem Details上限をPhase 1と同じ64 KiBとし、Field / Array Budgetを定義する | Accepted |
| API2-130 | CollectionでByte上限へ先に達した場合、完全Item単位でPageを早期終了する | Accepted |
| API2-131 | Size都合によるMemory本文の切断、Field省略、不完全JSONまたは部分成功表示を禁止する | Accepted |
| API2-132 | Non-paginated Response超過を正常Responseにせず、Commit前は安全な`500`、Commit後はProtocol Failureとする | Accepted |
| API2-133 | Content-Lengthと実Byte数の両方を検証し、上限+1 ByteでBounded Readを停止する | Accepted |
| API2-134 | ServerでContainer、Presentation、Application、Serializationの多層Size Guardを適用する | Accepted |
| API2-135 | FlutterでもContent-Lengthと実受信・Decode後Byte数を検証し、部分Dataを成功表示しない | Accepted |
| API2-136 | ConfigurationをContract未満へ縮小せず、引上げ時も関連Contractを自動変更しない | Accepted |
| API2-137 | Backup / Restoreを通常JSON Size Contractから分離し、Base64 Archive埋込みを禁止する | Accepted |
| API2-138 | 全上限を境界値、Unicode、Escape、PaginationおよびReconciliation Testで検証する | Accepted |

### 39.16 Next API Design Topics

API2-123〜API2-138は承認済みである。次の順序で詳細化する。

1. Backup / Restore Domain・Archive Design
2. Backup / Restore API Contract
3. Phase 2 API Design Review

---

## 40. Phase 2 Backup / Restore API Contract

### 40.1 Purpose and Endpoint Summary

本Sectionは、Section 20のBackup / Restore Domain・Archive DesignをFlutterから利用するHTTP Contractへ変換する。Archive Export、Inspection、Restore Preview、User ResolutionおよびExecuteを分離し、Archive検証前またはUser確認前にMemoryを変更しない。

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/memory-backups/export` | 現在MemoryとPreferencesを暗号化ArchiveとしてDownloadする |
| `POST` | `/api/v1/memory-restore-plans` | ArchiveをUpload・検証し、Restore Planを作成する |
| `GET` | `/api/v1/memory-restore-plans/{restorePlanId}` | Plan SummaryとAction Pageを取得する |
| `PATCH` | `/api/v1/memory-restore-plans/{restorePlanId}` | Review Action、再導入確認、Preferences Choiceを更新する |
| `POST` | `/api/v1/memory-restore-plans/{restorePlanId}/confirm` | Review済みPlanの短命Confirmation Tokenを発行する |
| `POST` | `/api/v1/memory-restore-plans/{restorePlanId}/cancel` | Review中Planを明示CancelしTemporary StateをCleanupする |
| `POST` | `/api/v1/memory-restore-plans/{restorePlanId}/execute` | 確認済みPlanを実行する |

Phase 2ではBackup一覧、Server-side Archive保管一覧、自動Schedule、Cloud Sync、Archive削除APIおよびSnapshot Replace APIを追加しない。Export後のFileはUserが選択した保存先で管理する。

### 40.2 Media Types

| Content | Media Type |
|---|---|
| Backup Archive v1 | `application/vnd.project-alice.memory-backup` |
| Export Request / Plan JSON | `application/json` |
| Restore Inspection Upload | `multipart/form-data` |
| Error | `application/problem+json` |

ArchiveをBase64へ変換してJSONへ埋め込まない。Archive Media Typeだけで内容を信用せず、Section 20のMagic、Envelope、AEADおよびPayload検証を必須とする。

### 40.3 Secure Transport and Access Boundary

Passphraseを含むExport / Inspect Requestは次の接続だけで受理する。

| Connection | Rule |
|---|---|
| Backendと同一HostのLoopback | Phase 2 Local Development BoundaryとしてHTTPを許可可能 |
| iOS SimulatorのLoopback相当接続 | 承認済みLocal構成に限り許可 |
| Private LAN上の実機iPhone | 許可端末AuthenticationとTLSを必須とする |
| 認証なしLAN、Public Internet、平文Remote HTTP | 拒否 |

Remote Requestの判定にClient入力の`Host`、`X-Forwarded-For`または任意Headerだけを信用しない。Security Designで定義したTrusted Proxy / Connection Contextを使用する。

安全なTransport / Authentication要件を満たさない場合は`403 SECURE_TRANSPORT_REQUIRED`、`retryable = false`、`recoveryAction = NONE`とする。Passphraseを受理・解析する前に拒否する。

### 40.4 Export Request

```http
POST /api/v1/memory-backups/export
Content-Type: application/json
Accept: application/vnd.project-alice.memory-backup
```

```json
{
  "passphrase": "user-entered-passphrase"
}
```

| Field | Type | Required | Rule |
|---|---|---:|---|
| `passphrase` | string | Yes | 12〜128 Unicode Code Point、最大512 UTF-8 Byte、Whitespaceのみ不可 |

FlutterはUserへPassphraseを2回入力させ、完全一致を確認してから1値だけをBackendへ送信する。Backend Requestへ確認用2値、Hint、Recovery AnswerまたはPassphrase Strength結果を送らない。

Request Body上限は4 KiBとする。Unknown Field、`null`、数値、Arrayおよび空Objectを`400 VALIDATION_ERROR`とする。PassphraseをTrim、NormalizeまたはCase変換しない。

### 40.5 Export Success Response

Export成功時は`200 OK`で完成済みArchive Byte列を返す。

```http
HTTP/1.1 200 OK
Content-Type: application/vnd.project-alice.memory-backup
Content-Disposition: attachment; filename="alice-memory-backup-20260829-210000JST.alice-memory-backup"
Cache-Control: no-store
X-Request-Id: 9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb
```

FilenameはBackendがJST時刻からASCIIで生成し、User入力、Memory内容、Device名またはArchive IDを含めない。同名Fileの上書き可否はFlutter / OS File PickerがUserへ確認する。

Backendは完全なArchive、AEAD Tagおよび最終Sizeを確定してからResponseをCommitする。Partial Archive、認証未完了Archiveまたは上限超過Archiveを`200`でStreamし始めない。

Archive Response上限は256 MiBとする。`Content-Length`を設定できる完成済みFileとして返し、FlutterはHeaderと実受信Byte数の両方を検証する。Network切断時に途中Fileを正常Backupとして保存しない。

復号前のStrict JSON Payloadは255 MiB（267,386,880 Byte）を上限とする。BackendはPayload生成時と完成Archive生成時の両方でSizeを検証し、Envelopeを含む完成Fileが256 MiBを超える場合も`413 BACKUP_ARCHIVE_TOO_LARGE`とする。

### 40.6 Export Replay and Concurrency

ExportはMemory Mutationではないため`Idempotency-Key`を要求しない。同じRequestの再実行は、新しいSalt、Nonce、Archive IDおよびExport時刻を持つ別Archiveを生成する。Byte列が異なっても、同じSource Snapshotなら論理内容は同等になり得る。

Network切断後に同じPassphraseで再Exportしてよいが、切断したPartial Fileを追記・再利用しない。

Argon2id処理と大規模Snapshot生成の同時負荷を制限するため、Single UserにつきExport / InspectのKDFを同時1件までとする。別KDF処理中は`409 BACKUP_OPERATION_IN_PROGRESS`、`Retry-After`、`retryable = true`、`recoveryAction = RETRY_LATER`を返す。通常ConversationとMemory Readを不必要に停止しない。

ExportはMutation Fence不存在、Backup Crypto Slot、Export OperationおよびSnapshot `BUILDING`を一つのReservation Transactionで取得する。Crypto Slot競合時はSnapshot PageやTemporary Archiveを作成しない。KDF / AEADまたはArtifact Cleanupが未完了の間はSlotを`CLEANUP_PENDING`として保持し、期限切れだけで新Exportへ明け渡さない。

### 40.7 Export Failure Mapping

| HTTP | Code | Condition | Recovery Action |
|---:|---|---|---|
| `400` | `VALIDATION_ERROR` | PassphraseまたはJSON形式不正 | `CORRECT_REQUEST` |
| `406` | `NOT_ACCEPTABLE` | Archive Media Typeを受理できない | `CORRECT_REQUEST` |
| `409` | `BACKUP_SOURCE_CHANGED` | Snapshot作成中にSource Versionが変化 | `RETRY_LATER` |
| `409` | `BACKUP_OPERATION_IN_PROGRESS` | 別Export / InspectのKDF処理中 | `RETRY_LATER` |
| `413` | `BACKUP_ARCHIVE_TOO_LARGE` | Valid DataがArchive上限を超える | `NONE` |
| `422` | `BACKUP_EXPORT_BLOCKED` | Invalid / Prohibited Memoryが存在 | `USER_REVIEW` |
| `503` | `SERVICE_UNAVAILABLE` | 必須Repository / Protection Capability利用不能 | `RETRY_LATER` |

`BACKUP_SOURCE_CHANGED`と`BACKUP_OPERATION_IN_PROGRESS`は`retryable = true`、その他は表のActionに従う。Blocked Responseへ対象件数を含められるが、本文、Secret種別、PassphraseまたはMemory Snapshotを含めない。

### 40.8 Create Restore Plan Request

Archive InspectionはStrict Multipart Requestで行う。

```http
POST /api/v1/memory-restore-plans
Content-Type: multipart/form-data; boundary=...
Accept: application/json
```

Required Part:

| Part Name | Content-Type | Maximum | Rule |
|---|---|---:|---|
| `archive` | `application/vnd.project-alice.memory-backup` | 256 MiB | 1 File、空File不可 |
| `passphrase` | `text/plain; charset=UTF-8` | 512 Byte | 1値、Section 20 Passphrase Rule |

Multipart Request全体上限は257 MiBとする。2 Partをそれぞれ1回だけ要求し、Unknown Part、重複Part、Nested MultipartおよびPart Count超過を拒否する。

`archive` PartのFilenameをTemporary Pathへ使用しない。Path Separator、Control Character、Unicode表示名またはExtensionを形式判定へ使用しない。Archive Byte列はBounded StreamでEncrypted Temporary Fileへ保存し、PassphraseはRequest Scopeだけで保持する。

InspectionはMemory Mutationではないが、重複PlanとTemporary Storage増加を防ぐためUUID形式の`Idempotency-Key`を必須とする。同じKey・同じArchive DigestのRetryを同じPlanまたは保存済みFailureへ収束させる。Wrong Passphrase等で入力を修正する場合は新しいKeyを使用する。Passphrase原文または可逆値をIdempotency Fingerprintへ含めない。KDF同時実行はSection 40.6の1件上限を適用する。

### 40.9 Inspection Success and Plan Creation

Inspection成功時は`201 Created`と`Location`を返す。

```http
HTTP/1.1 201 Created
Location: /api/v1/memory-restore-plans/da5f75f9-a5f3-4f29-a183-3456789abcde
ETag: "opaque-restore-plan-etag"
Cache-Control: no-store
```

```json
{
  "restorePlanId": "da5f75f9-a5f3-4f29-a183-3456789abcde",
  "strategy": "MERGE_SAFE",
  "status": "NEEDS_REVIEW",
  "archive": {
    "schemaVersion": 1,
    "exportedAt": "2026-08-29T21:00:00.000+09:00",
    "memoryCount": 24,
    "deletionHistoryAssessment": "AVAILABLE"
  },
  "deletionHistoryWarningAcknowledged": null,
  "summary": {
    "addCount": 12,
    "noChangeCount": 7,
    "updateCount": 2,
    "reviewRequiredCount": 2,
    "reintroduceDeletedCount": 1,
    "unresolvedCount": 3
  },
  "preferences": {
    "archive": {
      "autoSaveEnabled": true,
      "answerUseEnabled": true,
      "updatedAt": "2026-08-28T19:00:00.000+09:00"
    },
    "current": {
      "autoSaveEnabled": false,
      "answerUseEnabled": true,
      "updatedAt": "2026-08-29T18:00:00.000+09:00"
    },
    "choice": "KEEP_CURRENT"
  },
  "actionPage": {
    "items": [
      { "...": "RestoreActionResource" }
    ],
    "nextCursor": "opaque-cursor",
    "hasMore": true
  },
  "version": 1,
  "etag": "\"opaque-restore-plan-etag\"",
  "createdAt": "2026-08-29T21:05:00.000+09:00",
  "reviewExpiresAt": "2026-08-30T01:05:00.000+09:00",
  "executedAt": null,
  "result": null
}
```

`deletionHistoryAssessment`は`AVAILABLE`または`UNAVAILABLE`とする。`UNAVAILABLE`は、現在環境にGuard / Reset Pointがないため古いArchiveの削除済み内容再導入を完全判定できないことを表す。

`deletionHistoryWarningAcknowledged`はRequired Nullable Fieldとする。Assessmentが`AVAILABLE`なら`null`、`UNAVAILABLE`の初期値は`false`、同じPlanでUserが判定不能警告を明示確認した場合だけ`true`とする。

### 40.10 Restore Plan Lifecycle

| Status | Meaning | Execute Allowed |
|---|---|---:|
| `NEEDS_REVIEW` | 1件以上のAction Resolutionが未確定 | No |
| `READY` | 全Resolutionと確認が確定し実行待ち | Yes |
| `EXECUTING` | Durable Intent確立後の処理中 | No |
| `COMPLETED` | 全選択Mutation完了 | No |
| `NO_CHANGE` | 適用対象がすべて変更不要またはSkip | No |
| `PARTIAL` | 一部だけ成功 | No |
| `FAILED` | 成功を確認できず失敗確定 | No |
| `UNKNOWN` | 1件以上の最終状態を確定不能 | No |
| `INVALIDATED` | Source / Target / Temporary Stateが変化 | No |
| `EXPIRED` | Execute開始前に固定4時間のReview期限超過 | No |
| `CANCELLED` | Userの明示CancelとSensitive Temporary State Cleanupを確認済み | No |

Terminal Statusは`COMPLETED`、`NO_CHANGE`、`PARTIAL`、`FAILED`、`UNKNOWN`、`INVALIDATED`、`EXPIRED`および`CANCELLED`とする。

Plan MetadataとContent-free ResultはTerminal化後最低24時間取得可能とする。期限切れ、InvalidatedまたはTerminal化後はAction本文、Confirmation TokenおよびEphemeral Preview Stateを削除する。

### 40.11 Restore Action Resource

```json
{
  "recordId": "c24e6f6b-81ec-4056-9c75-23456789abcd",
  "proposedAction": "REVIEW_REQUIRED",
  "selection": "UNRESOLVED",
  "archiveMemory": {
    "content": "仕事ではJavaを使用している。",
    "category": "ENGINEERING",
    "captureType": "EXPLICIT",
    "sensitivityLevel": "NORMAL",
    "state": "ACTIVE",
    "createdAt": "2026-08-20T20:00:00.000+09:00",
    "updatedAt": "2026-08-28T20:30:00.000+09:00",
    "confirmedAt": "2026-08-28T20:30:00.000+09:00"
  },
  "targetMemoryId": null,
  "targetExpectedVersion": null,
  "relationType": "UNCERTAIN",
  "relatedMemoryIds": ["7f1f8e2a-4b3c-4d5e-8f60-123456789abc"]
}
```

`archiveMemory`はArchive RecordのUser管理可能Fieldだけを返し、Archive ID、Runtime ID、Digest、ScoreまたはAI推論を含めない。

`relatedMemoryIds`は最大10件とする。Clientは必要に応じて通常Memory GETでCurrent Resourceを取得する。Problem / Planだけを根拠にCurrent Memory本文を推測しない。

### 40.12 Action Pagination and Ordering

`GET /api/v1/memory-restore-plans/{restorePlanId}`は`limit`と`cursor`だけを受け付け、Plan Summaryと指定Action Pageを返す。

| Rule | Value |
|---|---|
| Default `limit` | 20 |
| Maximum `limit` | 100 |
| Response上限 | 2 MiB |
| Ordering | Archive Payload内の固定Record順 |
| Cursor Binding | Plan ID、不変`actionSetVersion`、Ordering、Page Boundary |
| Cursor Lifetime | 発行から最大30分かつ`reviewExpiresAt`まで |

Response Size上限へ先に達した場合は完全Action単位でPageを終了する。Archive Memory本文を途中切断しない。

Resolution、PreferencesまたはAcknowledgementのPATCHではAction集合と順序が変わらないため旧Cursorを無効化しない。Action集合、固定順序、Plan ID、`actionSetVersion`またはCursor期限が一致しない場合は`400 INVALID_CURSOR`とし、先頭Pageから再取得する。

### 40.13 Default Action Selection

| Proposed Action | Initial Selection | Allowed Selection |
|---|---|---|
| `ADD` | `APPLY` | `APPLY`、`SKIP` |
| `NO_CHANGE` | `NO_CHANGE` | 変更不可 |
| `UPDATE_EXISTING` | `APPLY` | `APPLY`、`SKIP` |
| `REVIEW_REQUIRED` | `UNRESOLVED` | `ADD_AS_NEW`、`UPDATE_TARGET`、`SKIP` |
| `REINTRODUCE_DELETED` | `UNRESOLVED` | `REINTRODUCE_AS_NEW`、`SKIP` |

`UPDATE_TARGET`ではInspectionが提示した`relatedMemoryIds`の一つを`targetMemoryId`として指定する。任意の別Memory IDへ変更対象を広げない。Target VersionはBackendがCurrent Resourceから取得してPlanへ固定する。

`REINTRODUCE_AS_NEW`は専用再導入確認なしに確定できない。

### 40.14 Update Restore Plan

```http
PATCH /api/v1/memory-restore-plans/{restorePlanId}
Content-Type: application/json
If-Match: "opaque-restore-plan-etag"
```

```json
{
  "resolutions": [
    {
      "recordId": "c24e6f6b-81ec-4056-9c75-23456789abcd",
      "selection": "UPDATE_TARGET",
      "targetMemoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc"
    }
  ],
  "preferencesChoice": "KEEP_CURRENT",
  "reintroductionConfirmed": false,
  "deletionHistoryWarningAcknowledged": true
}
```

| Field | Required | Rule |
|---|---:|---|
| `resolutions` | No | 1〜100件。`recordId`重複不可 |
| `preferencesChoice` | No | `KEEP_CURRENT`または`APPLY_ARCHIVE` |
| `reintroductionConfirmed` | No | Boolean。`true`は選択中の全再導入対象へBinding |
| `deletionHistoryWarningAcknowledged` | No | Assessmentが`UNAVAILABLE`の場合だけBooleanを指定可能 |

少なくとも一つのFieldを要求する。Request Body上限は64 KiBとする。`targetMemoryId`は`UPDATE_TARGET`の場合だけ必須とし、他Selectionでは禁止する。

PATCHはStrong `If-Match`を必須とする。Plan Version不一致時は`412 PRECONDITION_FAILED`とし、Clientは最新PlanをGETし直す。一つのRequest内ResolutionをAtomicに検証し、一部だけ適用しない。

実変更時はPlan VersionとETagを進め、以前のConfirmation Tokenを無効化する。未解決Actionが0件で、再導入確認条件と削除履歴警告確認条件も満たす場合だけ`status = READY`とする。TokenはPATCH Responseで返さず、Execute直前に専用Confirm Endpointから取得する。

Assessmentが`AVAILABLE`のPlanで`deletionHistoryWarningAcknowledged`を指定した場合は`400 VALIDATION_ERROR / INVALID_COMBINATION`とする。`UNAVAILABLE`で`true`は確認、`false`は確認解除を意味する。Field省略は現在値を維持する。

PATCHに`Idempotency-Key`を要求しない。通信結果不明時は古いETagで再送せず、Plan GETでSelectionとETagを確認する。

Resolution DeltaはServer内部で96件到達時にSelection Compactionを開始する。Active Deltaが128 Itemsまたは2,097,152 Bytesへ到達した場合、新しいPATCHはPlanを失効・削除せず`409 RESTORE_SELECTION_COMPACTION_IN_PROGRESS`を返す。Clientは最新PlanとETagを再取得してRetryできるが、同じ古いETagを自動再送しない。GET / Confirm / ExecuteはDatabase上限内で完全なSelectionを再構築できる場合だけ継続し、Compaction不整合では安全なService Failureへ収束する。

### 40.15 Preferences Choice

Plan作成時の`preferences.choice`は常に`KEEP_CURRENT`とする。ArchiveとCurrentが同じ場合もChoiceを保持するが、Mutationは`NO_CHANGE`となる。

`APPLY_ARCHIVE`へ変更した場合、Plan作成または最新PATCH時のPreferences Versionを固定する。Execute前に不一致ならPlan全体を`INVALIDATED`とし、Memoryだけを先にRestoreしない。

Preferences Choiceを省略したことを`APPLY_ARCHIVE`と解釈しない。

### 40.16 Reintroduction Confirmation

`REINTRODUCE_AS_NEW`を1件以上選択する場合、同じPlan Versionで`reintroductionConfirmed = true`を明示する。UIは少なくとも対象件数、新しいIDになること、過去の削除意思を今回のRestore意思で上書きすることを表示する。

再導入対象Selectionの追加・削除・変更時は既存確認を無効化し、再度`true`を要求する。単なるPlan表示、全体Restore確認またはPreferences適用を再導入確認として扱わない。

`deletionHistoryAssessment = UNAVAILABLE`の場合、判定不能警告の確認もConfirmation TokenへBindingする。具体的なFlutter表示はFrontend Designで確定する。

単なるPlan GET、画面表示、全体Restore確認、`reintroductionConfirmed`またはUserが否定しなかったことを判定不能警告の確認として扱わない。`deletionHistoryWarningAcknowledged = true`を同じPlanへ明示PATCHするまで`READY`へ遷移しない。

### 40.17 Confirmation Token and Plan ETag

`confirmationToken`はPlan ID、Archive Digest、Plan Version、全Action Selection、Target Expected Version、Preferences Choice / Version、Reset Generation、再導入確認、Deletion History Assessment、`deletionHistoryWarningAcknowledged`および期限へBindingする。

Tokenは最大2,048 ASCII CharacterのOpaque Single-use Valueとし、`READY` Planに対する専用Confirm Responseでだけ返す。Clientは解析・生成・永続Backupしない。有効期間は発行から15分以内かつ`reviewExpiresAt`までとする。

Plan ETagはPlan Versionに対応するStrong Entity Tagであり、PATCHとExecuteで`If-Match`に使用する。Action Page内のTarget Memory ETagとは別物である。

### 40.18 Execute Restore Plan

Execute直前に`POST /api/v1/memory-restore-plans/{restorePlanId}/confirm`をBodyなし・Strong `If-Match`付きで呼び、Section 42.4〜42.7の短命Tokenを取得する。

```http
POST /api/v1/memory-restore-plans/{restorePlanId}/execute
Content-Type: application/json
Idempotency-Key: 0c8711ca-5f4b-47d9-8f42-123456789abc
If-Match: "opaque-restore-plan-etag"
```

```json
{
  "confirmationToken": "opaque-single-use-token"
}
```

Request Body上限は4 KiBとする。UUID形式の`Idempotency-Key`、Strong `If-Match`およびConfirmation Tokenをすべて必須とする。

Execute前に次を全件検証する。

1. Planが`READY`かつReview期限内で、Confirmation Tokenも期限内
2. Plan ETagとToken Bindingが一致
3. Ephemeral Preview / Encrypted Archive Stateが利用可能
4. 全Target Memory IDとExpected Versionが一致
5. Preferences Versionが一致
6. Reset GenerationとGuard判定が変わっていない
7. 同じPlanの別Executeが開始していない

一つでも不一致ならMutationを開始せず`409 RESTORE_PLAN_INVALIDATED`とする。Ephemeral StateがProcess Restart等で失われた場合も、新しいPlanを作成してArchiveを再検証する。

Preflight成功後、Durable Restore IntentとToken消費を記録してから`EXECUTING`へ遷移する。ActionごとのMutationはExpected VersionとOperation Identityで保護し、成功済みActionを再実行しない。

### 40.19 Execute Response and Reconciliation

Response待機時間内にTerminalへ到達した場合は`200 OK`で最新Plan Resourceを返す。処理継続中はDurable Intent確立後だけ`202 Accepted`、`Location`、`Retry-After`および`status = EXECUTING`のPlan Resourceを返す。

ClientはPlan GETでTerminal Statusを確認する。Network切断をRestore失敗と推測せず、同じIdempotency-KeyでExecuteをReplayするかGETで照合する。

同一Key・同一Plan・同一Bodyは同じOperationへ収束する。同一Keyを別Plan、PathまたはBodyへ再利用した場合は`409 IDEMPOTENCY_KEY_CONFLICT`とする。

### 40.20 Restore Result Summary

```json
{
  "requestedActionCount": 24,
  "addedCount": 12,
  "updatedCount": 2,
  "reintroducedCount": 1,
  "noChangeCount": 7,
  "skippedCount": 1,
  "failedCount": 1,
  "unknownCount": 0,
  "preferencesResult": "KEPT_CURRENT",
  "completedAt": "2026-08-29T21:12:00.000+09:00"
}
```

`preferencesResult`は`KEPT_CURRENT`、`APPLIED_ARCHIVE`、`NO_CHANGE`、`FAILED`または`UNKNOWN`とする。

次のInvariantを満たす。

```text
requestedActionCount
  = addedCount
  + updatedCount
  + reintroducedCount
  + noChangeCount
  + skippedCount
  + failedCount
  + unknownCount
```

`PARTIAL`、`FAILED`または`UNKNOWN`を`COMPLETED`として表示しない。Action PageはExecute後に`ADDED`、`UPDATED`、`REINTRODUCED`、`NO_CHANGE`、`SKIPPED`、`FAILED`または`UNKNOWN`のResult Statusを返せる。

`COMPLETED`は全Actionの`failedCount = 0`、`unknownCount = 0`かつPreferences Resultが`FAILED / UNKNOWN`ではない場合だけ成立する。Memoryの一部成功後にPreferences適用が失敗した場合は`PARTIAL`、Preferences結果が不明な場合は`UNKNOWN`として扱う。`NO_CHANGE`はMemory MutationがなくPreferencesも`KEPT_CURRENT`または`NO_CHANGE`の場合に限る。

### 40.21 Ephemeral Preview State

Plan GETとExecuteに必要なArchive Recordは、Passphraseを保持せず利用できるEphemeral Sealed Stateとして固定4時間のReview期限まで保持できる。

Active Restore PlanとInspection Leaseの合計はSingle Userあたり1件、Retained Temporary Stateは544 MiB、Restore専用Working Directoryは768 MiBをHard Limitとする。Multipart受理前にActive SlotとWorking ByteをAtomic Reservationし、既存Planを新Requestで自動失効させない。

- 復号済みJSONを平文Temporary Fileへ保存しない。
- Validated Payload / PreviewをRandom Plan Keyで再暗号化してTemporary Storageへ置ける。
- Plan KeyはProcess Memoryだけで保持し、Log、DynamoDB、Plan ResponseまたはArchiveへ保存しない。
- Process RestartでKeyを失ったPlanは`INVALIDATED`とし、Archive再Uploadを要求する。
- Execute開始後はContent-free Durable Resultで成功済みActionを追跡する。
- Expire、Terminal、Cancel相当Cleanup、FailureおよびStartup CleanupでSealed Stateを削除する。

具体的なSealing Algorithm、Key Lifetime、Temporary DirectoryおよびCrash RecoveryはSecurity / Database Designで確定する。

### 40.22 Restore Failure Mapping

| HTTP | Code | Condition | Recovery Action |
|---:|---|---|---|
| `400` | `VALIDATION_ERROR` | Multipart、Resolution、Token形式等が不正 | `CORRECT_REQUEST` |
| `400` | `INVALID_CURSOR` | Plan Cursor不正、Action Set不一致または期限切れ | `CORRECT_REQUEST` |
| `403` | `SECURE_TRANSPORT_REQUIRED` | Remote接続Security要件未達 | `NONE` |
| `404` | `RESTORE_PLAN_NOT_FOUND` | Plan不存在または保持期間終了 | `CREATE_NEW_RESTORE_PLAN` |
| `409` | `BACKUP_OPERATION_IN_PROGRESS` | 別KDF処理中 | `RETRY_LATER` |
| `409` | `ACTIVE_RESTORE_PLAN_EXISTS` | Active PlanまたはInspection Leaseが既に存在 | `RESUME_OR_CANCEL_RESTORE_PLAN` |
| `409` | `RESTORE_REVIEW_REQUIRED` | 未解決Actionまたは再導入確認不足 | `USER_REVIEW` |
| `409` | `RESTORE_PLAN_EXPIRED` | 固定4時間のReview期限超過 | `CREATE_NEW_RESTORE_PLAN` |
| `409` | `RESTORE_CONFIRMATION_EXPIRED` | Tokenだけが15分期限超過 | `RECONFIRM_PLAN` |
| `409` | `RESTORE_PLAN_INVALIDATED` | Target / Preferences / Reset / Ephemeral State変化 | `CREATE_NEW_RESTORE_PLAN` |
| `409` | `RESTORE_PLAN_NOT_EXECUTABLE` | Terminal Plan再利用 | `CREATE_NEW_RESTORE_PLAN` |
| `409` | `RESTORE_EXECUTION_IN_PROGRESS` | 別Operationとして実行中 | `RETRY_LATER` |
| `409` | `RESTORE_PLAN_NOT_CANCELLABLE` | 実行中または別Terminal PlanをCancel | `REFRESH_RESOURCE` |
| `409` | `RESTORE_SELECTION_COMPACTION_IN_PROGRESS` | Resolution Delta Hard Limit到達、Selection再構築中 | `RETRY_LATER` |
| `503` | `RESTORE_TEMPORARY_STORAGE_UNAVAILABLE` | Byte Reservation不能またはCleanup未完了 | `RETRY_LATER` |
| `413` | `BACKUP_ARCHIVE_TOO_LARGE` | File / Multipart / Decrypted Payload上限超過 | `NONE` |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | MultipartまたはArchive Part Media Type不正 | `CORRECT_REQUEST` |
| `422` | `INVALID_PASSPHRASE_OR_ARCHIVE` | Passphrase不一致、破損またはAEAD認証失敗 | `CORRECT_REQUEST` |
| `422` | `UNSUPPORTED_ARCHIVE_VERSION` | Envelope / Schema Version未対応 | `NONE` |
| `422` | `UNSAFE_ARCHIVE_PARAMETERS` | KDF、Length、Depthその他が安全範囲外 | `NONE` |
| `422` | `RESTORE_ARCHIVE_CONTENT_INVALID` | Schema / Enum /日時 / Domain Rule不正 | `NONE` |
| `422` | `RESTORE_ARCHIVE_CONTENT_PROHIBITED` | Secret等の保存禁止情報を検出 | `NONE` |
| `503` | `SERVICE_UNAVAILABLE` | 必須Capability利用不能 | `RETRY_LATER` |

Wrong Passphraseと破損・改変をError Detail、Timing、ExtensionまたはAudit Codeで区別しない。Archive Header、Salt、Nonce、Tag、Digest、Passphrase、Record本文またはTemporary PathをProblem Detailsへ含めない。

### 40.23 Size Summary

| Request / Response | Maximum |
|---|---:|
| Export JSON Request | 4 KiB |
| Export Archive Response | 256 MiB |
| Decrypted JSON Payload | 255 MiB |
| Inspect Multipart Request | 257 MiB |
| Archive Part | 256 MiB |
| Passphrase Part | 512 Byte |
| Plan GET / PATCH Response | 2 MiB |
| Plan PATCH Request | 64 KiB |
| Execute Request | 4 KiB |
| Problem Details | 64 KiB |

Backup / Restore上限はSection 39の通常JSON Hard Limitの明示的例外である。その他のHeader、Request Target、Problem DetailsおよびFlutter防御RuleはSection 39を適用する。

### 40.24 Cache, Logging and Non-fields

すべてのResponseへ`Cache-Control: no-store`と`X-Request-Id`を設定する。Archive DownloadをHTTP Cache、Service Worker CacheまたはApplication Cacheへ保存しない。

Access / Application Logへ次を含めない。

- Export JSON BodyまたはMultipart Part Body
- Passphrase、Archive Byte、Archive Digest、Salt、NonceまたはTag
- Archive / Temporary FilenameのUser入力部分
- Memory本文、Preferences値、Action Target一覧またはConfirmation Token
- Cursor、ETag、Idempotency-Key全文またはEphemeral Key

ResponseへDynamoDB Key、Argon2 Library、AES Provider、Temporary Path、Process ID、Stack TraceまたはInternal Retry情報を含めない。

### 40.25 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-139 | Export、Restore Plan Create / Get / Patch / Executeの5 EndpointでBackup / Restoreを構成する | Accepted |
| API2-140 | Archive v1 Media Typeを`application/vnd.project-alice.memory-backup`としBase64 JSONを禁止する | Accepted |
| API2-141 | Export Passphraseを4 KiB以下のJSON Bodyで1値だけ受け取り、Flutter側で2回一致確認する | Accepted |
| API2-142 | ExportへIdempotency-Keyを要求せず、Retryごとに新しいSalt / NonceのArchiveを生成する | Accepted |
| API2-143 | InspectionをArchiveとPassphraseだけを持つStrict Multipart Requestとする | Accepted |
| API2-144 | Remote Backup / Restoreで許可端末AuthenticationとTLSを必須とし、不安全接続をPassphrase受理前に拒否する | Accepted |
| API2-145 | Single UserあたりExport / Inspect KDF同時実行を1件に制限する | Accepted |
| API2-146 | Restore PlanでStrategy、Lifecycle、Archive Summary、Action Summary、Preferences、ETagおよび期限を返す | Accepted |
| API2-147 | Restore Actionを最大100件・2 MiBのOpaque Cursor Pageで取得する | Accepted |
| API2-148 | ActionごとにProposed Action、Initial Selectionおよび許可Resolutionを固定する | Accepted |
| API2-149 | Plan ResolutionをStrong If-Match付きPATCHで最大100件ずつAtomic更新する | Accepted |
| API2-150 | Preferences ChoiceをDefault `KEEP_CURRENT`とし、`APPLY_ARCHIVE`を明示PATCHした場合だけ適用する | Accepted |
| API2-151 | `REINTRODUCE_AS_NEW`へPlan Version単位の専用確認を要求する | Accepted |
| API2-152 | Confirmation TokenをPlan全Selection、Expected Version、Preferences、Resetおよび15分期限へBindingする | Accepted |
| API2-153 | Preview DataをPassphrase非保持のEphemeral Sealed Stateとし、Process Restart時はPlanをInvalidatedとする | Accepted |
| API2-154 | ExecuteでUUID Idempotency-Key、Strong If-MatchおよびConfirmation Tokenを必須とする | Accepted |
| API2-155 | Execute前の全件Preflight後にDurable Intentを確立し、同期`200`または処理中`202`を返す | Accepted |
| API2-156 | Restore結果を件数別SummaryとPlan Statusで保持し、Partial / Unknownを成功へ偽装しない | Accepted |
| API2-157 | Export Archive 256 MiB、Decrypted Payload 255 MiB、Inspect Multipart 257 MiB、Plan 2 MiB、PATCH 64 KiB等の専用Size上限を適用する | Accepted |
| API2-158 | Wrong Passphraseと破損・改変Archiveを同じ`INVALID_PASSPHRASE_OR_ARCHIVE`へMappingする | Accepted |
| API2-159 | Archive、Passphrase、Digest、Memory本文、TokenおよびTemporary PathをResponse / Logから除外する | Accepted |
| API2-160 | Plan Metadata / Resultを最低24時間保持し、本文・Token・Ephemeral Stateを期限・Terminal時にCleanupする | Accepted |

### 40.26 Next API Design Topics

API2-139〜API2-160は承認済み。Phase 2 API Design全体の横断Reviewでは次を確認する。

1. Endpoint / Schema / Error / Sizeの相互整合
2. Backup Security Designへの引継ぎ
3. DynamoDB / Restore Recovery Designへの引継ぎ
4. Flutter Backup UXへの引継ぎ
5. API Contract Test Coverage

---

## 41. Phase 2 Cross Review High Finding Resolution

### 41.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted — Integrated |
| Review Findings | CR-001、CR-002 |
| Implementation | Not Started |
| Depends On | P2-FR-026、P2-FR-027、P2-FR-037、P2-FR-063、P2-FR-065、P2-NFR-007 |

本SectionはPhase 2 API Design Cross Reviewで検出したHigh Finding 2件の解消Decisionと統合結果を記録する。正式なEndpoint / Error / Restore ContractはSections 31、38および40をSource of Truthとし、Security / Database / Frontend / Test Designへ横断反映する。

### 41.2 Network Security Profiles

Phase 2のBackend起動Profileを次の2種類に限定する。

| Profile | Intended Client | Bind / Transport | Authentication |
|---|---|---|---|
| `LOOPBACK_ONLY` | 同一Mac上のFlutter iOS SimulatorとLocal Tool | `127.0.0.1`、Local HTTP | なし |
| `PRIVATE_LAN_SECURE` | 個人所有iPhoneおよび同じSecure構成を使うLocal Client | 明示指定したPrivate LAN Address、HTTPS only | 許可端末Bearer Token必須 |

`LOOPBACK_ONLY`をDefaultとする。`PRIVATE_LAN_SECURE`は明示Configurationと必要なSecurity Materialが揃った場合だけ起動できる。

禁止:

- `0.0.0.0`または全Interfaceへの無条件Bind
- Private LAN上のPlain HTTP Listener
- Private LAN ListenerとAuthenticationなしEndpointの併存
- TLS Certificate検証を無効にしたFlutter Client
- CORS、Private IPまたはUser-AgentをAuthentication代替にすること
- Request BodyやMemory Contentから端末Identityを決めること

Profile変更はApplication Restartを必要とし、Request単位のHeaderやQuery Parameterで切り替えない。

### 41.3 Protected Endpoint Scope

`PRIVATE_LAN_SECURE`ではMemory管理Endpointだけでなく、BackendがNetworkへ公開する全`/api/v1/**`へ同じTransport / Device Authenticationを適用する。

理由は、Conversation APIが自然言語Memory操作と回答用Memory検索へ接続するためである。`/memories`だけを保護して`/conversation/messages`を未保護にすると、Personal Memoryを間接利用・変更できる経路が残る。

Actuator、Debug、Development ConsoleまたはManagement EndpointをPrivate LANへ公開しない。Phase 2 Management Listenerは`security-design.md` Section 39.4に従って常にLoopbackへ固定し、Desktopを含むRemote Clientは通常APIだけを利用する。

### 41.4 Allowed Device Credential

Private LAN Deviceは次のBearer Credentialで認証する。

```http
Authorization: Bearer <opaque-device-token>
```

| Property | Rule |
|---|---|
| Entropy | CSPRNGによる256 bit以上 |
| Representation | Opaque Base64url、最大128 ASCII Character |
| Ownership | 一つのTokenは一つの許可端末Recordへ対応 |
| Client Storage | iOS / macOS Keychain、Windows Credential Manager等のPlatform Secure Storage。Application Data、Log、Clipboard常駐またはSource Codeへ保存しない |
| Backend Storage | Token原文を保持せず、専用KeyによるHMAC-SHA-256 DigestとDevice Metadataだけを保持 |
| Comparison | Constant-time Comparison |
| Lifetime | 固定自動失効をPhase 2では要求しない。明示Revoke / Rotate可能 |
| Scope | Single UserのAlice API利用。RoleやMulti-user Authorizationを導入しない |

Token発行、表示、RevokeおよびRotateはDeveloper Machine上のLocal Administration Commandで行い、未認証のPublic Pairing APIを追加しない。Token原文は発行時に一度だけ表示し、Backend Log、DynamoDB、Shell HistoryまたはReview Artifactへ残さない。QR表示を提供する場合も同じ一回表示Secretとして扱う。

Device表示名は管理用MetadataでありAuthentication根拠にしない。TokenをRevokeした端末は新しいTokenを明示発行するまで再接続できない。

### 41.5 TLS and Network Boundary

`PRIVATE_LAN_SECURE`は次を同時に満たす。

- Backendは明示ConfigurationしたPrivate LAN AddressだけへBindする。
- HTTPS以外のApplication ListenerをLAN Interfaceへ開かない。
- Server CertificateのSANはFlutterが使用するHost名またはAddressと一致させる。
- Flutterは通常のCertificate Chain、Hostnameおよび有効期限検証を行う。
- Local Development CAを使用する場合、Userが対象Deviceへ明示的にTrust設定し、汎用的なCertificate検証無効化を行わない。
- Host Firewallは設定済みAlice HTTPS PortへのPrivate LAN接続だけを許可する。
- Public Interface、Guest Network、VPN Exit NodeまたはInternet Port Forwardingへ公開しない。

TLS Key、Device Token Digest Key、発行済みTokenおよびBackup Passphraseを同じ設定値として再利用しない。

必要なCertificate、Private Key、Token Digest Key、明示Bind AddressまたはAuthentication Repositoryを読み込めない場合、Backendは`PRIVATE_LAN_SECURE`で起動失敗する。安全性を下げてHTTP / AuthenticationなしへFallbackしない。

### 41.6 Authentication Processing and Errors

`PRIVATE_LAN_SECURE`では、Request Bodyを読取りApplication Use Caseへ渡す前にTransport ProfileとAuthenticationを検証する。

処理順序:

1. Listener / TLS Handshakeと接続先Interface
2. Request LineとHeader Size Hard Limit
3. `Authorization` Header形式とAllowed Device照合
4. Method、Content-Type、Accept、Path / Query / Body Validation
5. Application Use Case

FlutterもBase URLがPrivate LAN Hostの場合は`https`以外をStartup Errorとし、TokenをNetworkへ送信しない。Backendが不安全なTransport / Profileを検出できた場合は、AuthorizationまたはBodyを解析せず既存`403 SECURE_TRANSPORT_REQUIRED`を返す。

Tokenが欠落、不正形式、不一致、Revoke済みまたは未知端末の場合は、理由を区別せず次を返す。

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer realm="alice-local"
Cache-Control: no-store
```

```json
{
  "type": "urn:project-alice:problem:device-authentication-required",
  "title": "Device authentication required",
  "status": 401,
  "detail": "この端末からAliceへ接続するには、端末認証を設定してください。",
  "code": "DEVICE_AUTHENTICATION_REQUIRED",
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb",
  "retryable": false,
  "recoveryAction": "REAUTHENTICATE_DEVICE"
}
```

`REAUTHENTICATE_DEVICE`をPhase 2 Recovery Actionへ追加する。Clientは同じTokenを自動再送し続けず、Credential再設定画面へ遷移する。Responseと通常LogへToken、Token Digest、Device Secret、Certificate Key、Authorization Headerまたは認証失敗理由の詳細を含めない。

`LOOPBACK_ONLY`では`Authorization` Headerを要求しない。誤って送信されたCredentialをLogへ出力せず、Security Profileの混同をTestで検出する。

### 41.7 Restore Deletion-history Acknowledgement Resource

Restore Plan Resourceへ次のRequired Nullable Fieldを追加する。

```json
{
  "archive": {
    "deletionHistoryAssessment": "UNAVAILABLE"
  },
  "deletionHistoryWarningAcknowledged": false
}
```

| Assessment | Response Value | Meaning |
|---|---|---|
| `AVAILABLE` | `null` | 判定不能警告は不要 |
| `UNAVAILABLE` | `false` | 警告の明示確認待ち |
| `UNAVAILABLE` | `true` | 同じPlanでUserが判定不能警告を確認済み |

Fieldを省略せず、`null`、`false`、`true`で状態を明示する。`true`は「Archiveに削除済みMemoryが存在しない」という保証ではなく、現在環境では完全判定できないことを理解してRestoreを継続する意思だけを表す。

### 41.8 Restore Plan PATCH Contract

Plan PATCHへ次のOptional Fieldを追加する。

```json
{
  "deletionHistoryWarningAcknowledged": true
}
```

| Condition | Result |
|---|---|
| Assessmentが`UNAVAILABLE`で`true` | 明示確認を記録しPlan Version / ETagを進める |
| Assessmentが`UNAVAILABLE`で`false` | 既存確認を解除し`NEEDS_REVIEW`へ戻す |
| Assessmentが`AVAILABLE`でField指定 | `400 VALIDATION_ERROR / INVALID_COMBINATION` |
| Field省略 | 現在値を維持 |

単なるPlan GET、画面表示、全体Restore確認、`reintroductionConfirmed`またはUserが否定しなかったことから`true`へ変更しない。

同一Plan内のResolutionやPreferences Choice変更だけでは、ArchiveとEnvironmentに関する警告内容は変わらないため、このAcknowledgementを自動解除しない。Archive Digest、Installation、Deletion History AssessmentまたはReset / Guard Boundaryが変わる場合はPlan自体を`INVALIDATED`とし、新しいInspectionで`false`から確認し直す。

### 41.9 READY and Confirmation Token Rule

Restore Planは次をすべて満たした場合だけ`READY`となる。

1. `unresolvedCount = 0`
2. `REINTRODUCE_AS_NEW`選択がある場合、同じPlan Versionで`reintroductionConfirmed = true`
3. `deletionHistoryAssessment = UNAVAILABLE`の場合、`deletionHistoryWarningAcknowledged = true`
4. Preferences Choice、Target Expected Version、Reset GenerationおよびEphemeral StateがValid

Confirmation Tokenへ次を追加Bindingする。

- `deletionHistoryAssessment`
- `deletionHistoryWarningAcknowledged`

Acknowledgement変更時は以前のTokenを無効化する。条件を満たさないPlanへTokenを返さず、Executeを許可しない。Execute RequestへAcknowledgement Booleanを重複送信させず、Plan VersionとSingle-use TokenをSource of Truthとする。

### 41.10 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-161 | Phase 2 Network Security ProfileをDefault `LOOPBACK_ONLY`と明示Opt-in `PRIVATE_LAN_SECURE`へ限定する | Accepted |
| API2-162 | `PRIVATE_LAN_SECURE`で公開する全`/api/v1/**`へTLSとAllowed Device Authenticationを適用する | Accepted |
| API2-163 | Allowed Device Authenticationに256 bit以上のOpaque Bearer Tokenを使用し、各PlatformのSecure Storageへ保存する | Accepted |
| API2-164 | BackendでToken原文を保持せず、専用KeyのHMAC-SHA-256 Digestと端末Metadataだけを保持する | Accepted |
| API2-165 | Device Token発行・Revoke・RotateをLoopback Local Administrationで行い、未認証Pairing APIを追加しない | Accepted |
| API2-166 | Private LANで明示Bind、HTTPS only、Certificate検証およびFirewall制限を必須とし、構成不足時はFail Closedとする | Accepted |
| API2-167 | AuthenticationをBody解析前に行い、不正・欠落・Revoke済みTokenを同じ`401 DEVICE_AUTHENTICATION_REQUIRED`へMappingする | Accepted |
| API2-168 | Recovery Actionへ`REAUTHENTICATE_DEVICE`を追加し、Tokenや認証失敗詳細をResponse / Logへ出力しない | Accepted |
| API2-169 | Restore Plan ResourceへRequired Nullable `deletionHistoryWarningAcknowledged`を追加する | Accepted |
| API2-170 | Assessment `UNAVAILABLE`の場合だけPATCHで警告確認を明示設定・解除できるようにする | Accepted |
| API2-171 | Restore Planの`READY`条件へ削除履歴判定不能警告の明示確認を追加する | Accepted |
| API2-172 | Deletion History AssessmentとAcknowledgementをConfirmation TokenへBindingし、変更時に旧Tokenを無効化する | Accepted |

### 41.11 Approval and Integration Boundary

API2-161〜API2-172は承認済み。次へ一つの整合更新として反映する。

1. Section 31の旧Authentication記述とEndpoint Baseline
2. Section 38のValidation順序、Recovery ActionおよびAuthentication Error
3. Section 40のPlan Resource、PATCH、READYおよびToken Binding
4. `security-design.md`のPrivate LAN / Device Credential詳細
5. `database-design.md`のAllowed Device Credential MetadataとDigest永続化Boundary
6. `frontend-design.md`のPlatform Secure Storage、HTTPS Configurationおよび警告確認UX
7. `test-design.md`のAuthentication / TLS / Restore Confirmation Contract Test
8. Cross Review CR-001 / CR-002のFocused Re-review

---

## 42. Phase 2 Cross Review CR-003 Resolution

### 42.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-003 |
| Implementation | Not Started |
| Affected Contract | Deletion Plan、Restore Plan、Confirmation Token、Plan-scoped Cursor |

本Sectionは、最大10,000件のPlanに対して15分の単一期限を適用していた不整合を解消する。単純な期限延長ではなく、Userが内容を確認できる期間と、破壊的操作を実行する直前確認の有効期間を分離する。

### 42.2 Separate Review and Execution-confirmation Lifetimes

| Lifetime | Initial Value | Sliding | Purpose |
|---|---:|---:|---|
| Plan Review Lifetime | 作成から4時間 | No | Page確認、Resolution、Preferences選択、警告確認 |
| Confirmation Token Lifetime | 発行から15分 | No | 最終確認後のExecuteだけを短時間許可 |
| Plan Page Cursor Lifetime | 発行から30分以内かつPlan Review期限まで | Pageごとに新Cursor | 連続Page Navigation |

Review期限を固定4時間とする。GET、PATCH、Confirm失敗、Background PollingまたはClient再接続で延長しない。4時間を超える場合は、現在状態で新しいPlanを作成し直す。

15分はPlan全体の期限ではなくConfirmation Tokenの期限として維持する。Token期限切れだけを理由にPlanを`EXPIRED`へ遷移させず、Review期限内なら再確認して新Tokenを取得できる。

### 42.3 Plan Resource Time Fields

Deletion PlanとRestore Planの曖昧な`expiresAt`を次へ置き換える。

| Field | Required | Meaning |
|---|---:|---|
| `createdAt` | Yes | Plan作成時刻 |
| `reviewExpiresAt` | Yes | 固定Plan Review期限。`createdAt + 4時間` |
| `executedAt` | Yes / Nullable | 最初にExecution Intentを受理した時刻 |

Plan ResourceへConfirmation Tokenと`confirmationExpiresAt`を常設しない。TokenはSection 42.5の専用Responseだけで返す。

Planの`EXPIRED`は、Execute開始前に`reviewExpiresAt`へ到達した場合だけ使用する。Token期限切れはPlan StateではなくConfirmation Errorである。

### 42.4 Explicit Confirmation Endpoints

次の2 Endpointを追加する。

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/memory-deletion-plans/{deletionPlanId}/confirm` | Review済みDeletion Planの短命Tokenを発行 |
| `POST` | `/api/v1/memory-restore-plans/{restorePlanId}/confirm` | Resolution済みRestore Planの短命Tokenを発行 |

Request Bodyを送信せず、Strong `If-Match`を必須とする。

```http
POST /api/v1/memory-restore-plans/{restorePlanId}/confirm
If-Match: "opaque-plan-etag"
Content-Length: 0
```

Confirm Endpointは次を検証する。

1. PlanがReview期限内である
2. Deletion Planは`READY`である
3. Restore Planは全Resolution、再導入確認および削除履歴警告確認を満たした`READY`である
4. `If-Match`が現在Plan Versionと一致する
5. Target Expected Version、Preferences Version、Reset GenerationおよびGuard Boundaryが変化していない
6. RestoreのEphemeral Preview Stateが利用可能である

不一致時はTokenを発行せず、既存のInvalidation Ruleに従う。ConfirmはMemory Mutationを行わず、Plan VersionとETagも進めない。

### 42.5 Confirmation Response

成功時は`200 OK`と次を返す。

```json
{
  "confirmationToken": "opaque-single-use-token",
  "confirmationExpiresAt": "2026-08-29T22:15:00.000+09:00",
  "planVersion": 24,
  "planEtag": "\"opaque-plan-etag\""
}
```

| Field | Rule |
|---|---|
| `confirmationToken` | 最大2,048 ASCII Character、Opaque |
| `confirmationExpiresAt` | 発行時刻から15分、Review期限を超えない |
| `planVersion` | TokenがBindingされた現在Plan Version |
| `planEtag` | Executeの`If-Match`へそのまま使用するStrong ETag |

ResponseへPlan本文、対象一覧、Memory本文またはToken内部情報を含めない。`Cache-Control: no-store`を必須とする。

### 42.6 Token Reissue and Single-use Semantics

Review期限内かつPlanが変化していなければ、Token期限切れ後にConfirm Endpointを再実行できる。再発行のためArchive再Upload、Deletion Plan再作成またはResolutionやり直しを要求しない。

同じPlan Versionへ複数Tokenが発行され得るが、すべて同じ固定対象とUser選択へBindingされる。最初の有効なExecuteがDurable Intentを確立した時点でPlanを実行中へ遷移し、同じPlanの未使用Tokenをすべて無効化する。Token一つごとの消費状態ではなく、PlanのExecution StateをAuthoritative Boundaryとする。

Plan Version、Selection、Acknowledgement、Preferences Choice、Target Version、Reset GenerationまたはGuard Boundaryが変化した場合、既発行Tokenをすべて無効化する。

### 42.7 Confirmation Error and Recovery

Recovery Actionへ`RECONFIRM_PLAN`を追加する。

| HTTP | Code | Condition | Recovery Action |
|---:|---|---|---|
| `409` | `DELETION_CONFIRMATION_EXPIRED` | Deletion Tokenだけが期限切れ | `RECONFIRM_PLAN` |
| `409` | `RESTORE_CONFIRMATION_EXPIRED` | Restore Tokenだけが期限切れ | `RECONFIRM_PLAN` |
| `409` | `DELETION_CONFIRMATION_INVALID` | Token不一致、改変またはPlan Binding不一致 | `RECONFIRM_PLAN`またはPlan再取得 |
| `409` | `RESTORE_CONFIRMATION_INVALID` | Token不一致、改変またはPlan Binding不一致 | `RECONFIRM_PLAN`またはPlan再取得 |

Plan Review期限切れは既存のPlan Expired Errorとし、新しいPlan作成へ誘導する。ClientはToken期限切れとPlan期限切れを同じMessageへまとめない。

### 42.8 Plan-scoped Cursor Lifetime

Deletion Target PageとRestore Action PageのCursorは、発行から最大30分かつ`reviewExpiresAt`まで有効とする。各Page Responseの`nextCursor`は、そのPage取得時点から同じRuleで新しく発行できるため、連続Navigationを継続できる。

Cursor期限切れはPlan期限切れではない。Clientは同じPlanの先頭Pageから再取得できる。

### 42.9 Restore Action-set Binding

Restore Action Page Cursorを可変のPlan VersionへBindingしない。Plan作成時に固定する不変の`actionSetVersion`へBindingする。

```text
Restore Action Cursor Binding
├── restorePlanId
├── actionSetVersion
├── fixedOrdering
├── pageBoundary
├── issuedAt
└── cursorExpiresAt
```

Resolution PATCH、Preferences ChoiceまたはAcknowledgement変更ではAction集合と固定順序が変わらないため、次Page Cursorを無効化しない。各Actionの`selection`はGET時の現在値をHydrateして返す。

次の場合はCursorを無効化する。

- Planが`INVALIDATED`、`EXPIRED`または他のTerminal Stateになった
- Archive再Inspectionで別Planになった
- Action集合または固定順序が変化した
- Cursor自体が期限切れ、改変または別Planへ流用された

Plan Versionは引き続きPATCH / Confirm / ExecuteのConcurrency Controlに使用し、Action Set Versionと役割を混同しない。

### 42.10 Frontend Behavior

- Plan画面へ`reviewExpiresAt`に基づく残りReview時間を表示できる。
- Userが最終実行確認を行った直後にConfirm Endpointを呼び、続けてExecuteする。
- ConfirmとExecuteの間にNetwork切断した場合、Plan GETで状態を確認し、未実行かつReview期限内なら再確認する。
- Token期限切れではPlan内容を失ったように表示せず、再確認Actionを提示する。
- Review期限切れでは新Plan作成が必要であることを表示する。
- Page Cursor期限切れでは同じPlanの先頭Pageへ戻り、Planを再作成しない。

### 42.11 Cleanup and Retention Boundary

Review期限到達時にConfirmation Token、Preview本文、Hydration CacheおよびRestore Ephemeral StateをCleanupする。Plan MetadataとContent-free Resultは既存RuleどおりTerminal化後最低24時間保持する。

Review期限4時間はTemporary Storageを無制限保持してよいという意味ではない。Section 44のActive Plan 1件、Retained 544 MiB、Working 768 MiBおよびCleanup Ruleを適用する。

### 42.12 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-173 | Deletion / Restore PlanのReview期限を作成から固定4時間とし、Accessで延長しない | Accepted |
| API2-174 | Plan Resourceの`expiresAt`を`reviewExpiresAt`へ置換し、Token期限と分離する | Accepted |
| API2-175 | Deletion / Restore PlanへBodyless・Strong If-Match必須の専用Confirm Endpointを追加する | Accepted |
| API2-176 | Confirmation TokenをConfirm Responseだけで返し、有効期間を発行から15分以内かつReview期限までとする | Accepted |
| API2-177 | Token期限切れでPlanを`EXPIRED`にせず、Review期限内の再Confirmを許可する | Accepted |
| API2-178 | Confirm時にPlan State、全Expected Version、Reset / GuardおよびEphemeral Stateを検証する | Accepted |
| API2-179 | ExecuteのDurable Intent確立時に同じPlanの全Tokenを無効化する | Accepted |
| API2-180 | `RECONFIRM_PLAN`とToken期限切れ専用Error Codeを追加する | Accepted |
| API2-181 | Plan-scoped Cursorを発行から最大30分かつReview期限まで有効とする | Accepted |
| API2-182 | Restore Action Cursorを不変`actionSetVersion`へBindingし、Resolution PATCHで無効化しない | Accepted |
| API2-183 | Plan VersionをPATCH / Confirm / ExecuteのConcurrency、Action Set VersionをPage集合の同一性へ限定する | Accepted |
| API2-184 | Review期限到達時にSensitive Temporary StateをCleanupし、Content-free Metadataを最低24時間保持する | Accepted |

### 42.13 Approval and Integration Boundary

API2-173〜API2-184は承認済みであり、次へ整合反映した。

1. API Section 31のPublic Endpoint Baseline
2. API Section 37のDeletion Plan Resource、Lifecycle、Cursor、Confirm / Execute
3. API Section 38のRecovery ActionとError Mapping
4. API Section 40のRestore Plan Resource、Lifecycle、Cursor、Confirm / Execute
5. Memory Section 20のRestore Plan ModelとMD-105
6. Database DesignのPlan TTL、Action Set VersionおよびToken Boundary
7. Frontend DesignのReview / Confirm UX
8. Test Designの4時間、15分、30分およびCursor継続Contract Test
9. CR-003 Focused Re-review

---

## 43. Phase 2 Cross Review CR-004 Resolution

### 43.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-004 |
| Implementation | Not Started |
| Affected Contract | Backup Export、Restore Inspection、Multipart、Flutter File Handling、Boundary Test |

本Sectionは、Encrypted ArchiveとDecrypted JSON Payloadへ同じ256 MiB上限を適用していた境界矛盾を解消する。外部File上限は維持し、暗号Envelopeと将来互換の安全余白をPayload側で確保する。

### 43.2 Normative Byte Constants

| Constant | Exact Value | Purpose |
|---|---:|---|
| `MAX_ENCRYPTED_ARCHIVE_BYTES` | 268,435,456 Byte（256 MiB） | Export File、Restore Archive Part、Flutter File上限 |
| `MAX_DECRYPTED_PAYLOAD_BYTES` | 267,386,880 Byte（255 MiB） | UTF-8 JSON Payload上限 |
| `MAX_MULTIPART_REQUEST_BYTES` | 269,484,032 Byte（257 MiB） | Archive、Passphrase、Multipart Framingの総上限 |
| `MAX_PUBLIC_HEADER_BYTES` | 4,096 Byte（4 KiB） | Envelope v1 Public Header上限 |
| `AEAD_TAG_BYTES` | 16 Byte | AES-256-GCM Authentication Tag |

ArchiveとPayloadの差1,048,576 ByteをEnvelope Reserveとする。Envelope v1の実OverheadはこのReserve内でなければならず、Header拡張によって超える場合は同じEnvelope Versionのまま上限を破らず、新VersionとCompatibility Reviewを要求する。

設計書、Backend、FlutterおよびTestでMiB表記だけを転記せず、上記Exact Byte値をContract Fixtureまたは同等の生成元から参照する。Platformごとに異なるDecimal MBへ変換しない。

### 43.3 Export Boundary

Exportは次の順でSizeを検証する。

1. Strict JSON PayloadをBounded Sinkへ生成し、255 MiBを1 Byteでも超えた時点で停止する。
2. Public HeaderのExact Byte数、Length Prefix、Ciphertext Lengthおよび16 Byte TagをOverflow-safeに加算する。
3. 完成Archiveの予測値と実Byte数がともに256 MiB以下であることを確認する。
4. 完成Archiveと`Content-Length`が一致した場合だけResponseをCommitする。

Payloadまたは最終Archiveが上限を超える場合は`413 BACKUP_ARCHIVE_TOO_LARGE`とし、Partial Archiveを返さない。255 MiB以下のPayloadでもEnvelope異常によって256 MiBを超える場合はExportを失敗させ、Payloadを切り詰めない。

### 43.4 Restore Inspection Boundary

Restoreは次の独立したHard Limitを適用する。

- Multipart全体は257 MiB以下
- `archive` Partは256 MiB以下
- `passphrase` Partは512 Byte以下
- Multipart Framing、Part HeaderおよびBoundaryを含むArchive以外のByteは、総上限内に収める
- 復号出力は255 MiBのBounded Sinkへ書き込み、上限超過時はJSON Parserへ渡さない

`Content-Length`だけを信用せず、Streaming読込では上限に加えて1 Byteを検出できる境界を使用する。EnvelopeのDeclared Lengthが負値、Overflow、実File残量不一致または256 MiB超過ならKDF前に拒否する。AEAD認証後も復号済みByte数を独立検証する。

File、MultipartまたはDecrypted PayloadのSize超過はすべて`413 BACKUP_ARCHIVE_TOO_LARGE`へMappingする。構造上不正なLengthや安全でないParameterは既存`422 UNSAFE_ARCHIVE_PARAMETERS`を使用し、Size超過と暗号認証失敗を混同しない。

### 43.5 Flutter and Desktop-compatible File Handling

Flutter ClientはMobile / Desktop共通Contractとして次を守る。

- Export Responseの`Content-Length`が256 MiBを超える場合、保存開始前に拒否する。
- 受信実Byte数が`Content-Length`と不一致または256 MiB超過なら、正常Backupとして確定しない。
- Restore選択Fileが256 MiBを超える場合、Upload前に拒否する。
- Client事前検証をSecurity Boundaryとせず、Backendでも同じ上限を必ず再検証する。
- Decrypted PayloadはBackend内部Boundaryであり、Flutterは復号・展開しない。

OS File PickerやFilesystem APIの違いはAdapterへ隔離し、iOS、Android、macOS、WindowsまたはLinuxによってByte上限を変更しない。

### 43.6 Boundary Test Set

少なくとも次のExact Boundaryを共通Fixtureで検証する。

| Target | Accept | Reject |
|---|---:|---:|
| Decrypted Payload | 267,386,880 Byte | 267,386,881 Byte |
| Encrypted Archive | 268,435,456 Byte | 268,435,457 Byte |
| Multipart Request | 269,484,032 Byte | 269,484,033 Byte |
| Public Header | 4,096 Byte | 4,097 Byte |

Export、Restore、HTTP Multipart Parser、Flutter Download / UploadおよびArchive Compatibility Testは同じFixture値を使用する。巨大な固定Fileだけに依存せず、Bounded Stream / Sinkの境界を生成可能なTest Builderで検証する。

### 43.7 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-185 | Encrypted Archive上限を256 MiBのまま維持し、Decrypted JSON Payload上限を255 MiBへ変更する | Accepted |
| API2-186 | Archive、Payload、Multipart、HeaderおよびTagのExact Byte定数をNormative Contractとして一元化する | Accepted |
| API2-187 | ExportでPayload生成、Envelope予測、完成Archive実測の三段階Size検証を行う | Accepted |
| API2-188 | RestoreでMultipart、Archive Part、Declared Lengthおよび復号出力へ独立Hard Limitを適用する | Accepted |
| API2-189 | Streaming Boundaryで上限超過の1 Byteを検出し、`Content-Length`だけを信用しない | Accepted |
| API2-190 | Size超過を`413 BACKUP_ARCHIVE_TOO_LARGE`、危険な構造Parameterを`422 UNSAFE_ARCHIVE_PARAMETERS`へ分離する | Accepted |
| API2-191 | Flutter Mobile / Desktopで同じ256 MiB File上限を適用し、Backend再検証を必須とする | Accepted |
| API2-192 | 255 / 256 / 257 MiBとHeader 4 KiBのAccept / Reject境界を共通Fixtureで検証する | Accepted |

### 43.8 Approval and Integration Boundary

API2-185〜API2-192は承認済みであり、次へ一つの整合更新として反映した。

1. API Sections 40.5、40.8、40.22、40.23およびAPI2-157
2. Memory Section 20.9およびMD-097
3. Frontend DesignのMobile / Desktop File Handling
4. Test DesignのExport / Restore / Multipart / File Boundary Test
5. CR-004 Focused Re-review

本変更はArchive v1の暗号Algorithm、Media TypeまたはFile上限を変更しない。Payload上限だけを安全側へ狭めるため、既存のValid ArchiveでPayloadが255 MiBを超えるものはRestore拒否対象となる。Phase 2未実装のためMigrationは不要だが、実装開始後に定数を変更する場合はArchive Compatibility Decisionを別途要求する。

---

## 44. Phase 2 Cross Review CR-005 Resolution

### 44.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-005 |
| Implementation | Not Started |
| Affected Contract | Restore Plan Create / Cancel、Idempotency、Temporary Storage、Cleanup / Recovery |

本Sectionは、逐次Request、Client Retry、中断またはCleanup失敗によってRestore Temporary Stateが無制限に増えることを防ぐ。Single User用途では複数Planの並行Reviewより、明示的で回復可能な一Plan運用を優先する。

### 44.2 Hard Quotas

| Limit | Exact Value | Scope |
|---|---:|---|
| `MAX_ACTIVE_RESTORE_PLANS` | 1 | `NEEDS_REVIEW`、`READY`、`EXECUTING`とInspection Leaseの合計 |
| `MAX_RESTORE_RETAINED_TEMP_BYTES` | 570,425,344 Byte（544 MiB） | Active Planが保持するEncrypted Archive、Sealed Preview、Manifest、Index等の合計 |
| `MAX_RESTORE_WORKING_TEMP_BYTES` | 805,306,368 Byte（768 MiB） | Inspection中のStagingを含むRestore専用Temporary Directory全体 |
| `RESTORE_INSPECTION_LEASE_LIFETIME` | 30分 | Plan確立前の一時Reservation |

Byte使用量はFilesystemの論理File Sizeを合計し、Sparse File、Hard Link、Symbolic LinkまたはDirectory外Pathで回避できないようにする。Restore専用Directory外へTemporary Artifactを作成しない。Quota値はSingle User全体へ適用し、Client、端末、ConnectionまたはIdempotency-Keyごとに分割しない。

### 44.3 Atomic Reservation Before Upload

AuthenticationとRequest Header検証後、Multipart Bodyを受理する前にRestore Active Slot、最大Working Byte、Backup Crypto Slot、Inspection LeaseおよびCreate Idempotency Bindingを一つのTransactionで取得する。既存Active Plan / Inspection Leaseまたは別Crypto Operationがある場合、新しいArchiveをUploadさせず、片側Reservationを残さない。

Inspection LeaseはOperation ID、Idempotency-Key、開始時刻、期限、Reserved Bytes、Process Instanceおよび`RESERVED / CRYPTO_RUNNING / ANALYZING / PLAN_ACTIVE / CLEANUP_PENDING` Phaseだけを持ち、Passphrase、Archive Byte、Memory本文またはUser Filenameを含めない。30分へ到達した処理は成功へ遷移せず同じOwner / EpochでCleanupする。LeaseをHeartbeatで無期限延長しない。

KDF / AEAD完了後、Derived KeyとCrypto Working Bufferの破棄を確認してからLeaseを`ANALYZING`へ進め、Crypto Slotを同じTransactionで解放する。Restore Active SlotとWorking ByteはPlan公開またはCleanup完了まで維持する。

Inspection成功時は、Working ReservationをActive PlanのRetained Byte実測値へAtomicに置換する。544 MiBを超える場合はPlanを公開せず、全ArtifactをCleanupして失敗する。

### 44.4 Restore Plan Create Idempotency

`POST /api/v1/memory-restore-plans`へUUID形式の`Idempotency-Key`を必須とする。

- 同じKey・同じArchive DigestのRetryは、新Planを作らず同じPlanまたは保存済みFailureへ収束する。
- 同じKeyを異なるArchive Digestへ再利用した場合は`409 IDEMPOTENCY_KEY_CONFLICT`とする。
- Passphrase原文または可逆値をFingerprint / Idempotency Recordへ保存しない。
- Wrong Passphrase後に入力を修正する場合、Clientは新しいIdempotency-Keyを生成する。
- Network切断後は新しいKeyで再作成せず、同じKeyを再送して結果へ収束させる。

同じRequestのRetryは既存Reservationを再利用できるが、別OperationとしてActive Slotを追加取得しない。Idempotency RecordはContent-freeに最低24時間保持する。

### 44.5 Existing Plan Is Never Auto-replaced

新Plan作成またはRetryを理由に、既存Planを自動で`INVALIDATED`、`EXPIRED`または`CANCELLED`へ変更しない。Backendは既存Planの`restorePlanId`、`status`および`reviewExpiresAt`だけを安全なProblem Extensionとして返せる。

Recovery Actionへ`RESUME_OR_CANCEL_RESTORE_PLAN`を追加する。Flutterは既存Planを開くか、Userの明示操作でCancelする。新Archive選択だけをCancel意思として扱わない。

### 44.6 Explicit Cancel Endpoint

次を追加する。

```http
POST /api/v1/memory-restore-plans/{restorePlanId}/cancel
Idempotency-Key: 0c8711ca-5f4b-47d9-8f42-123456789abc
If-Match: "opaque-restore-plan-etag"
Content-Length: 0
```

Request Bodyを禁止し、UUID `Idempotency-Key`とStrong `If-Match`を必須とする。`NEEDS_REVIEW`または`READY`だけをCancelできる。`EXECUTING`はMutation結果不明を避けるためCancelせず、GETによる結果確認へ誘導する。

CancelはPlanをTerminal `CANCELLED`へ遷移し、Confirmation Token、Plan Key、Encrypted Archive、Sealed Preview、Hydration CacheおよびPending Retryを無効化・削除する。Content-free Plan MetadataとCancel Resultは最低24時間保持する。

同じIdempotency-KeyのRetryは同じCancel Resultを返す。既に`CANCELLED`のPlanを同じKeyで再送しても成功結果へ収束する。別Terminal Stateまたは別Keyによる再利用は`409 RESTORE_PLAN_NOT_CANCELLABLE`とする。

### 44.7 Cleanup Success Boundary

Active SlotとRetained Byte Reservationを解放するのは、次を確認した後だけとする。

1. Plan KeyとConfirmation Tokenが利用不能
2. Encrypted Archive、Sealed Preview、Hydration CacheおよびStaging Fileが存在しない
3. Pending Worker / Retryが同Planを再参照できない
4. Content-free Metadataだけが保持Boundaryへ移行済み

Crypto中または結果不明時はBackup Crypto Slotも`CLEANUP_PENDING`へ遷移させる。Key / Buffer / Partial Payloadの利用不能を確認した後、Lease Terminal、Idempotency Result、Restore Slotおよび同Operation所有のCrypto Slotを一つのFinal Cleanup Transactionで解放する。

Cleanupに失敗した場合、Cancel、ExpireまたはInspection Failureを完全成功として返さない。Plan / Leaseを内部`CLEANUP_PENDING`としてQuotaへ残し、`503 RESTORE_TEMPORARY_STORAGE_UNAVAILABLE`、`retryable = true`、`recoveryAction = RETRY_LATER`を返す。Memory本文、Temporary PathまたはFile名をErrorへ含めない。

### 44.8 Cleanup Triggers and Restart Recovery

Cleanupは次のすべてで同じIdempotent処理を実行する。

- Inspection Success前のValidation / KDF / AEAD / Size Failure
- Client DisconnectまたはRequest Cancel
- Explicit Cancel
- Review期限切れ、InvalidationおよびTerminal化
- Processの正常終了
- Process Startup
- 5分以内の周期Sweeper
- 新しいRestore Reservation取得前

StartupではRestore Control、Backup Crypto Control、Inspection Lease、Export Operation、Idempotency BindingおよびRestore専用DirectoryのManifestを照合する。ValidなActive OwnerがないArtifact、期限切れLease、Process RestartでKeyを失ったSealed Stateを同じOwner / EpochのRecoveryへ進める。Filenameや更新時刻だけでUser Dataの所有関係を推測しない。

DynamoDB TTLやOSのTemporary Directory自動削除だけをCleanup成功条件にしない。Startup / Sweeperが削除できないArtifactはQuotaへ計上し、容量が空いたと偽装しない。

### 44.9 Error and Recovery Mapping

| HTTP | Code | Condition | Recovery Action |
|---:|---|---|---|
| `409` | `ACTIVE_RESTORE_PLAN_EXISTS` | Active PlanまたはInspection Leaseが既に存在 | `RESUME_OR_CANCEL_RESTORE_PLAN` |
| `409` | `RESTORE_PLAN_NOT_CANCELLABLE` | `EXECUTING`または別Terminal StateをCancel | `REFRESH_RESOURCE` |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | Keyを別Archive / Operationへ再利用 | `CORRECT_REQUEST` |
| `503` | `RESTORE_TEMPORARY_STORAGE_UNAVAILABLE` | Byte Reservation不能またはCleanup未完了 | `RETRY_LATER` |

Quota Errorへ空きByte、Temporary Path、Archive Size、他Planの内容またはPassphrase情報を含めない。Content-free MetricとしてActive Count、Reserved Bytes、Cleanup Pending Count、Cleanup DurationおよびReason Codeを記録できる。

### 44.10 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-193 | Active Restore PlanとInspection Leaseの合計をSingle Userあたり1件へ制限する | Accepted |
| API2-194 | Retained Temporary Byteを544 MiB、Working Temporary Byteを768 MiBへHard Limit化する | Accepted |
| API2-195 | Multipart受理前にActive SlotとWorking ByteをAtomic Reservationする | Accepted |
| API2-196 | Inspection Leaseを固定30分とし、無期限Heartbeat延長を禁止する | Accepted |
| API2-197 | Restore Plan CreateへUUID Idempotency-Keyを必須化し、Retryを同じ結果へ収束させる | Accepted |
| API2-198 | 新Plan作成で既存Planを自動失効させず、再開または明示Cancelを要求する | Accepted |
| API2-199 | Bodyless・Idempotency-Key・Strong If-Match必須のRestore Plan Cancel Endpointを追加する | Accepted |
| API2-200 | Cancel可能状態をNEEDS_REVIEW / READYへ限定し、CANCELLEDをTerminal Statusへ追加する | Accepted |
| API2-201 | Cleanup完了確認後だけActive SlotとByte Reservationを解放する | Accepted |
| API2-202 | Cleanup失敗をCLEANUP_PENDINGとしてQuotaへ残し、成功へ偽装しない | Accepted |
| API2-203 | Failure、Disconnect、Cancel、Expiry、Startup、周期SweeperおよびReservation前に同じCleanupを実行する | Accepted |
| API2-204 | ACTIVE_RESTORE_PLAN_EXISTSとRESTORE_TEMPORARY_STORAGE_UNAVAILABLEを専用RecoveryへMappingする | Accepted |
| API2-205 | Temporary StorageのMetric / AuditをContent-freeとしPath、本文、Archive情報を除外する | Accepted |

### 44.11 Approval and Integration Boundary

API2-193〜API2-205は承認済みであり、次へ整合反映した。

1. API Sections 31、38、40のEndpoint、Lifecycle、Idempotency、ErrorおよびCleanup
2. Memory Backup / Restore DomainのPlan LifecycleとTemporary Boundary
3. Database DesignのLease、Reservation、IdempotencyおよびStartup Reconciliation
4. Security DesignのDedicated Directory、Permission、Key DestructionおよびCleanup Audit
5. Frontend DesignのResume / Cancel / Retry UX
6. Test DesignのQuota Race、Disconnect、Restart、ExpiryおよびCleanup Failure
7. CR-005 Focused Re-review

---

## 45. Phase 2 Cross Review CR-006 Resolution

### 45.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-006 |
| Implementation | Not Started |
| Affected Contract | Register / Update Relation Review、Management UI、Natural Language Resolution |

本Sectionは、`MEMORY_RELATION_REVIEW_REQUIRED`の後にUserが選択した最終操作を安全に完結できない問題を解消する。Client指定の`override = true`は導入せず、Domain判定結果とCandidateを短命なRelation Review ResourceへBindingする。

### 45.2 Relation Review Resource Creation

RegisterまたはUpdateで`COMPLEMENTARY_SAME_FACT`、`SUPERSEDES`、`CONFLICT`または`UNCERTAIN`を検出した場合、Mutationを行わず`MemoryRelationReview`を作成する。元RequestのIdempotency-Key Replayは同じReview Resourceへ収束する。

`409 MEMORY_RELATION_REVIEW_REQUIRED`は本文、Scoreまたは判定理由を返さず、次の安全な参照だけを追加する。

```json
{
  "code": "MEMORY_RELATION_REVIEW_REQUIRED",
  "retryable": false,
  "recoveryAction": "USER_REVIEW",
  "relationReviewId": "8a1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "relationReviewUri": "/api/v1/memory-relation-reviews/8a1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "relationType": "CONFLICT",
  "relatedMemoryCount": 1,
  "reviewExpiresAt": "2026-08-29T22:30:00.000+09:00"
}
```

API2-206〜API2-220統合時にProblem Responseへの関連ID直返しを廃止し、Authoritativeな対象集合はRelation Review Resourceだけとする。Phase 2は未実装のためCompatibility期間を設けない。Review作成に失敗した場合、Review IDなしの`409`を返さず`503 SERVICE_UNAVAILABLE`とする。

### 45.3 Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/memory-relation-reviews/{relationReviewId}` | Candidate、関連Memory Page、選択肢、状態を取得 |
| `POST` | `/api/v1/memory-relation-reviews/{relationReviewId}/resolve` | Binding済み選択を一度だけ実行 |

すべて`Cache-Control: no-store`を返し、Private LANでは共通Allowed Device Authenticationを要求する。GETはDefault 20、Maximum 20の`limit`とOpaque Cursorを受け付ける。Responseは2 MiB以下、Reviewが保持できる関連Memoryは最大100件とする。

### 45.4 Resource Schema and Binding

```json
{
  "relationReviewId": "8a1f8e2a-4b3c-4d5e-8f60-123456789abc",
  "status": "OPEN",
  "sourceOperation": "REGISTER",
  "sourceMemoryId": null,
  "candidate": {
    "content": "仕事ではJavaを使用している。",
    "category": "ENGINEERING",
    "sensitivityLevel": "NORMAL",
    "state": "ACTIVE"
  },
  "relationType": "CONFLICT",
  "allowedResolutions": ["UPDATE_TARGET", "ADD_AS_NEW", "SKIP"],
  "recommendedResolution": null,
  "relatedMemoryPage": {
    "items": [{
      "memory": { "...": "MemoryResource" },
      "expectedVersion": 3,
      "updatePreview": { "content": "...", "category": "ENGINEERING" }
    }],
    "nextCursor": null,
    "hasMore": false
  },
  "version": 1,
  "etag": "\"opaque-relation-review-etag\"",
  "createdAt": "2026-08-29T22:00:00.000+09:00",
  "reviewExpiresAt": "2026-08-29T22:30:00.000+09:00",
  "result": null
}
```

Reviewは少なくとも次へBindingする。

- Canonical Candidate全FieldのDigestと保護済みPayload
- `REGISTER` / `UPDATE`、Update時のSource Memory ID / Expected Version
- Relation Type
- 全Related Memory ID / Expected Version
- ResolutionごとのAllowed Target IDとMutation Preview
- Deletion Guard / Reset Generation
- Review Version、作成時刻、固定30分期限

CandidateとMutation PreviewはPersonal Dataとして暗号化された短命Stateに保持し、Problem、Log、MetricまたはIdempotency Fingerprintへ本文を出さない。Active Relation ReviewはSingle Userあたり最大20件とする。

一つのReviewに属する全Candidate / Previewは、作成時に固定した単一の`stateKeyVersion`へBindingする。このVersionは公開Resource、ETag、Cursor、Problem、LogまたはMetricへ返さない。GET / ResolveはBinding済みVersionだけで復号し、現在のWrite Keyまたは別VersionによるFallbackを行わない。

### 45.5 Allowed Resolution Matrix

| Relation Type | Allowed Resolution | Recommended |
|---|---|---|
| `COMPLEMENTARY_SAME_FACT` | `UPDATE_TARGET`、`ADD_AS_NEW`、`SKIP` | `UPDATE_TARGET` |
| `SUPERSEDES` | `UPDATE_TARGET`、`ADD_AS_NEW`、`SKIP` | `UPDATE_TARGET` |
| `CONFLICT` | `UPDATE_TARGET`、`ADD_AS_NEW`、`SKIP` | なし |
| `UNCERTAIN` | `UPDATE_TARGET`、`ADD_AS_NEW`、`SKIP` | なし |

`recommendedResolution`は初期選択ではなく、UIが自動送信してよいDefaultでもない。Userの明示選択を必須とする。

- `UPDATE_TARGET`: Review内の`allowedTargetMemoryIds`から一つを指定し、固定Mutation Previewを適用する。
- `ADD_AS_NEW`: Userが独立した事実であると確認し、新しい`memoryId`でCandidateを保存する。
- `SKIP`: Candidateを保存・更新せずReviewを完了する。

Update起点では、Source MemoryまたはDomainが安全なMutation Previewを生成できたRelated MemoryだけをAllowed Targetに含める。任意Memory IDをClientが指定できない。

### 45.6 Resolve Request

```http
POST /api/v1/memory-relation-reviews/{relationReviewId}/resolve
Idempotency-Key: 61b6e8f4-33f1-4be4-9428-31c991f7f8c1
If-Match: "opaque-relation-review-etag"
Content-Type: application/json
```

```json
{
  "resolution": "UPDATE_TARGET",
  "targetMemoryId": "7f1f8e2a-4b3c-4d5e-8f60-123456789abc"
}
```

| Resolution | `targetMemoryId` |
|---|---|
| `UPDATE_TARGET` | Required。ReviewのAllowed Targetと完全一致 |
| `ADD_AS_NEW` | Prohibited |
| `SKIP` | Prohibited |

Request Bodyは4 KiB、UUID `Idempotency-Key`とStrong `If-Match`を必須とする。ClientはCandidate本文、Relation Type、Allowed Target、Override BooleanまたはExpected Versionを再送しない。

### 45.7 Preflight and Narrow Override Boundary

Resolve前に次をすべて再検証する。

1. Reviewが`OPEN`かつ固定30分期限内
2. ETag、Review VersionおよびIdempotency Binding
3. Source / Related Memoryの存在とExpected Version
4. Deletion Guard / Reset Generation
5. Secret、Sensitivity、Category、Atomicityおよび保存禁止Rule
6. 選択がAllowed Resolution / Targetに一致
7. Review作成後に新しい未確認Relationが発生していない

User ResolutionがOverrideできるのは、ReviewへBinding済みのRelation判定だけである。Secret、削除意思、Sensitivity、Category、Version Conflict、未知Targetまたは新規Relationを迂回できない。

VersionまたはRelation集合が変化した場合、旧Reviewを`INVALIDATED`とし、新しいReviewを作成する元Operationへ戻す。古いCandidateを最新ETagへ付け替えて自動実行しない。

### 45.8 Lifecycle and Result

| Status | Meaning |
|---|---|
| `OPEN` | User選択待ち |
| `PROCESSING` | Durable Resolution Intent確立後の処理中 |
| `COMPLETED` | CreateまたはUpdateを確認済み |
| `SKIPPED` | Userが保存しないと確定 |
| `FAILED` | Mutationなしまたは失敗を最終確認 |
| `UNKNOWN` | Mutation最終状態を確定不能 |
| `EXPIRED` | 固定30分期限超過 |
| `INVALIDATED` | Binding対象・Guard・Relation集合変化、Key欠落またはAEAD検証失敗 |

Resultは`CREATED`、`UPDATED`、`SKIPPED`、`FAILED`または`UNKNOWN`を区別し、成功時だけMemory Resource、LocationおよびETagを返す。`UNKNOWN`を新規Requestで再実行せず、同じIdempotency-Keyで照合する。

Terminal化、期限切れまたはInvalidation時はCandidate / PreviewのSensitive StateをCleanupし、Content-free Metadata / Resultを最低24時間保持する。

参照中Relation Keyが利用不能、未知Version、AAD不一致またはAEAD Authentication Failureの場合、Reviewを再利用せず`INVALIDATED`へ進め、暗号化StateをCleanupする。外部Responseは再度元操作が必要であることだけを示し、Key Version、Providerまたは暗号失敗詳細を含めない。Startup Gateが不成立の場合、Relation ReviewのCreate / GET / ResolveだけをFail ClosedまたはRetryable Unavailableとし、無関係なMemory ReadやConversation Historyは停止しない。

### 45.9 Natural Language and Management UI Unification

Management UIとConversation Flowは同じ`ResolveMemoryRelationUseCase`を呼び出す。

- 「別の情報として保存して」→ `ADD_AS_NEW`
- 「既存の〇〇を更新して」→ `UPDATE_TARGET`とAllowed Target
- 「今回は保存しない」→ `SKIP`

自然言語FlowもActive `relationReviewId`へBindingし、自由なClient CommandやPromptだけでRelation Ruleを迂回しない。複数Reviewがある場合、単純な「はい」「それで」から対象ReviewやResolutionを推測せず確認する。

### 45.10 Error Mapping

| HTTP | Code | Condition | Recovery Action |
|---:|---|---|---|
| `404` | `MEMORY_RELATION_REVIEW_NOT_FOUND` | 不存在または保持終了 | `RESTART_MEMORY_OPERATION` |
| `409` | `MEMORY_RELATION_REVIEW_EXPIRED` | 30分期限超過 | `RESTART_MEMORY_OPERATION` |
| `409` | `MEMORY_RELATION_REVIEW_INVALIDATED` | Version / Guard / Relation集合変化 | `REFRESH_RESOURCE` |
| `409` | `MEMORY_RELATION_REVIEW_NOT_EXECUTABLE` | Terminal Review再利用 | `REFRESH_RESOURCE` |
| `409` | `MEMORY_RELATION_REVIEW_LIMIT_EXCEEDED` | Active Review 20件または関連Memory 100件超過 | `USER_REVIEW` |
| `412` | `PRECONDITION_FAILED` | Review ETag不一致 | Review GET |

ErrorへCandidate本文、Mutation Preview、Relation Score、AI理由、Review用暗号MaterialまたはMemory本文を含めない。

### 45.11 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-206 | Relation Review Required時に短命MemoryRelationReview Resourceを作成する | Accepted |
| API2-207 | Review取得とResolution実行の2 Endpointを追加する | Accepted |
| API2-208 | ReviewをCandidate、Source Operation、Relation、全Expected Version、Guardおよび固定30分期限へBindingする | Accepted |
| API2-209 | Relation TypeごとにUPDATE_TARGET、ADD_AS_NEW、SKIPを明示的Allowed Resolutionとする | Accepted |
| API2-210 | UPDATE_TARGETをReview内Allowed Targetと固定Mutation Previewへ限定する | Accepted |
| API2-211 | ADD_AS_NEWをUserが独立事実と明示確認した場合だけ許可する | Accepted |
| API2-212 | ResolveへUUID Idempotency-KeyとStrong If-Matchを必須とする | Accepted |
| API2-213 | User ResolutionのOverride範囲をBinding済みRelationだけへ限定しHard Rule迂回を禁止する | Accepted |
| API2-214 | Source / Related Version、GuardまたはRelation集合変化時にReviewをInvalidatedとする | Accepted |
| API2-215 | Review LifecycleでProcessing、Completed、Skipped、Failed、Unknown、Expired、Invalidatedを区別する | Accepted |
| API2-216 | Candidate / Previewを暗号化短命StateとしTerminal後にCleanupする | Accepted |
| API2-217 | Active Review上限を20件、関連Memory上限を100件、Page上限を20件 / 2 MiBとする | Accepted |
| API2-218 | Initial Register / UpdateのIdempotency Replayを同じReview Resourceへ収束させる | Accepted |
| API2-219 | Management UIと自然言語Flowで同じResolveMemoryRelationUseCaseを使用する | Accepted |
| API2-220 | 複数Review時の曖昧な肯定から対象・Resolutionを推測しない | Accepted |

### 45.12 Approval and Integration Boundary

API2-206〜API2-220は承認済みであり、次へ整合反映した。

1. API Sections 31、34、38、39のEndpoint、Register / Update Result、ErrorおよびSize
2. Memory Section 12のRelation Mutation PlanとConfirmation Boundary
3. Database DesignのReview、Expected Version、IdempotencyおよびRetention
4. Security DesignのCandidate / Preview保護とCleanup
5. Frontend Designの比較・Resolution UX
6. AI / Conversation Designの自然言語Resolution Binding
7. Test Designの全Relation Type、Version Race、Unknownおよび曖昧発話
8. CR-006 Focused Re-review

---

## 46. Phase 2 Cross Review CR-007 Traceability Resolution

### 46.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-007 |
| Source of Truth | `phase2-requirement-traceability-matrix.md` |

P2-FR-001〜065およびP2-NFR-001〜008を個別にPublic API、内部Application Boundary、非API設計およびStable Test IDへ割り当てる。Public APIが不要なRequirementも空欄にせず`N/A — Internal only`とCaller / Use Caseを明記する。

### 46.2 Traceability Gate

- 73件を一行ずつ追跡し、Group Rangeだけの対応で完了扱いにしない。
- Public API、Internal Boundary、Non-API Design、Test IDおよびStatusの空欄を禁止する。
- 未設計・未試験責務は`OPEN GATE`とし、理由とOwnerを追加する。
- Stable Test IDはClass名やFramework名から独立させ、実装時のTest Metadataへ付与する。
- Requirement、ContractまたはDecision変更時はMatrixとTest Metadataを同じChange Setで更新する。
- `OPEN GATE`が一件でも残る場合、Phase 2をImplementation Readyへ昇格しない。

### 46.3 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-221 | 73件のApproved Requirementを専用Matrixで個別追跡する | Accepted |
| API2-222 | Public API不要時もInternal-only Boundaryを明示する | Accepted |
| API2-223 | 未対応責務を空欄ではなくOPEN GATEとして管理する | Accepted |
| API2-224 | RequirementごとにStable Test IDを最低一つ割り当てる | Accepted |
| API2-225 | Requirement / Design / Test変更を同じChange Setで同期する | Accepted |
| API2-226 | Open Gateが残る限りImplementation Readyへ昇格しない | Accepted |

API2-221〜API2-226はTM-001〜TM-006と同じ承認単位で確定した。73件の個別Mapping、Stable Test ID、空欄およびOpen GateをFocused Re-reviewし、新しいHigh / Medium不整合がないことを確認した。

---

## 47. Phase 2 Cross Review CR-008 Validation Ordering Resolution

### 47.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-008 |
| Affected Contract | Section 38.5 Field Validation Errors |

`INVALID_COMBINATION`と`AT_LEAST_ONE_REQUIRED`をField Errorの固定優先順位へ統合し、Validator実装順によってResponseが変化しないようにする。

### 47.2 Accepted Stable Priority

同一Schema PathのErrorは次の順で安定化する。

1. `REQUIRED`
2. `AT_LEAST_ONE_REQUIRED`
3. `INVALID_TYPE`
4. `UNKNOWN_FIELD`
5. `INVALID_COMBINATION`
6. `INVALID_FORMAT`
7. `INVALID_ENUM`
8. `INVALID_CHARACTER`
9. `NOT_BLANK`
10. `TOO_LONG`
11. `TOO_LARGE`
12. `OUT_OF_RANGE`
13. `DUPLICATE_VALUE`

Object-levelの`AT_LEAST_ONE_REQUIRED`はTop-level Bodyなら`field = "$"`、Nested ObjectならそのSchema Path（例：`filters`）へ一件だけ付与する。未知Fieldだけが存在しても「認識済みFieldが一つ以上ある」とは扱わない。

### 47.3 Suppression Rules

- `REQUIRED`または`AT_LEAST_ONE_REQUIRED`が成立するPathでは、値を前提とする後続Codeを返さない。
- `INVALID_TYPE`ではFormat、Enum、Character、Blank、Length、Size、RangeおよびDuplicate検証を行わない。
- `UNKNOWN_FIELD`は未知Field自身へ一件だけ返し、その値の内容を追加検証しない。
- `INVALID_COMBINATION`は参加FieldがKnownかつ型・形式を安全に解釈できる場合だけ評価する。
- 同一Path・同一Validation Stageで複数Violationが成立する場合は優先度最上位の一件だけを返す。
- 異なるPathのErrorは従来どおりEndpoint Schema順、Array Index昇順で返し、全体50件上限を維持する。

### 47.4 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-227 | 13個のField Error Codeの固定優先順位へ追加Codeを統合する | Accepted |
| API2-228 | Object-level AT_LEAST_ONE_REQUIREDのField Pathを`$`またはContaining Object Pathへ固定する | Accepted |
| API2-229 | Missing / Type / Unknown / Combinationの後続Validation抑止Ruleを固定する | Accepted |
| API2-230 | 同一Path・同一Stageでは優先度最上位のError一件だけを返す | Accepted |

API2-227〜API2-230は承認済みであり、Section 38.5とTest Designへ統合した。13 Codeの欠落・重複、順序、Object Path、抑止Ruleおよび50件上限をFocused Re-reviewし、新しい不整合がないことを確認した。

---

## 48. Phase 2 Cross Review CR-009 Document State Resolution

### 48.1 Purpose and Current State

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-009 |
| Affected Contract | Document Information、Sections 31.3、36.1 |

Section 31.3は現在、Backup / Restore 7 Endpoint、Relation Review 2 Endpointを含むPhase 2 Public Endpoint 23件を収録済みであり、未決定記述は残っていない。したがってEndpoint追加を重複実施せず、Baselineが最新であることをFocused Re-review対象とする。

残る編集不整合は、API2-070〜API2-084がAcceptedである一方Section 36.1が`Proposed`であることと、全Phase 2 Decision承認後もDocument Informationが`Phase 2: Draft`であることの二点である。

### 48.2 Final Status Transition

CR-009承認後、次を同じChange Setで一度だけ実行した。

1. Section 36.1を`Accepted / Integrated`へ更新する。
2. Section 31.3の23 Endpoint、Methods、Use Casesおよび重複がないことを再確認する。
3. API2-001〜API2-234のDecision Status、欠番およびSuperseded表記を機械確認する。
4. CR-001〜CR-009のOpen Findingが0件であることを確認する。
5. Document Informationを`Phase 1: Approved / Phase 2: Reviewed — Implementation Ready`へ更新する。
6. 冒頭の「Phase 2はユーザー承認前のDraft」という説明を、承認済みContractとImplementation前提へ更新する。

実装が開始済みであること、実装完了またはRelease Readyを意味するStatusへは変更しない。

### 48.3 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| API2-231 | Section 31.3のPhase 2 Public Endpoint 23件を最新Baselineとして固定する | Accepted |
| API2-232 | Section 36.1をAccepted / Integratedへ更新する | Accepted |
| API2-233 | CR-009最終Review後にDocument Phase 2 StatusをReviewed — Implementation Readyへ一度だけ更新する | Accepted |
| API2-234 | Draft説明を承認済みContractと実装前提の説明へ置換する | Accepted |

API2-231〜API2-234は承認済みである。Section 36.1、Document Information、全Decision / Endpoint / Findingを最終Focused Re-reviewし、Phase 2 API Designを`Reviewed — Implementation Ready`へ昇格した。

---

## Phase 4 Formal API Integration — 2026-09-04

**Status:** Accepted / Integrated
**Source:** AGENT4-056〜097, P4-FR-001〜121, P4-NFR-001〜024

- AgentExecution resource: `executionId`, `goalSummary`, `status`, `outcome`, `currentActivity`, `progress`, `waitingReason`, timestamps, availableActions
- Lifecycle: `PLANNING / RUNNING / WAITING_FOR_APPROVAL / NEEDS_USER_INPUT / optional PAUSED / COMPLETED / FAILED / CANCELLED / UNKNOWN_OUTCOME`
- Outcome: `SUCCESS / FAILURE / PARTIAL_SUCCESS / UNKNOWN`
- Approval resource is Action-bound, single-use, and invalidated by Material Change. Client cannot arbitrarily overwrite Risk or Approval status.
- Permission resource exposes Capability / Operation / Scope / Device-Executor summary / Lifetime / Effect / Status. Risk override and Hard Safety bypass APIs are prohibited.
- Cancel, optional Pause/Resume, and Emergency Stop are distinct controls.
- `UNKNOWN_OUTCOME` uses Verify Current State / Reconciliation as the primary contract; Blind Retry is not the primary contract.
- Executor / Device resources expose trust state / supported capability / lastSeen / binding version without returning Secrets.
- User-facing API and Authenticated Executor Protocol are separate.
- Observation is Executor evidence; Backend evaluates Authoritative Outcome.
- Client disconnect ≠ AgentExecution cancel. Background execution state remains durably retrievable.
- Problem Details distinguish permission, approval, stale/expired, ambiguity/change, capability/executor unavailable, conflict/cancel, unknown/recovery, and safety-boundary denial.
- Full Prompt / CoT / Credential / raw provider response / internal policy / signing material / DynamoDB key are never exposed.
