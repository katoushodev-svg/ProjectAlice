# Project Alice - FIP-009 Send and Streaming Implementation Plan

## 1. Document Information

| Item | Value |
|---|---|
| Document | `fip-009-send-and-streaming-plan.md` |
| FIP | FIP-009 Send and Streaming |
| Status | Approved / Implementation Ready |
| Draft Planning | Completed |
| Implementation | Not Started |
| Cross-phase Review | PASS |
| Target | Phase 1 iOS Frontend |
| Last Updated | 2026-09-09 JST |

本ドキュメントは、Phase 1 FrontendのMessage送信、SSE受信、Streaming表示およびCanonical Completionを、後からGitHub Copilot等のAI Coding Assistantへ実装依頼できる粒度で定義するDraft実装計画である。

本Draft作成時点では、ソースコード、Test Code、Dependency、AssetまたはXcode設定を変更しない。Phase 2〜4設計後のCross-phase Reviewは完了済みである。実装は承認済みSource of Truthとプロジェクト全体のImplementation Gateに従う。

---

## 2. Purpose / Goal

FIP-009の目的は、Userが入力したMessageを一度だけ論理送信し、Backendから届くSSEを安全に逐次処理し、最後にBackendのCanonical Messageで画面状態を確定することである。

達成目標:

- 入力内容を変更せずに送信する
- 一つの送信操作につきUUID v4 Idempotency Keyを一度だけ生成する
- POST開始からTerminal Eventまで同じKeyとContentを維持する
- SSEを全体Bufferせず、Frame単位でSize Limitを適用する
- `stream.started`、`assistant.delta`、Terminal Eventの順序を検証する
- 最初のDeltaをすぐ表示し、後続Deltaを50ms単位でまとめて滑らかに表示する
- `assistant.completed`のCanonical User / Assistant MessageをSource of Truthとする
- 自動POST Retryや自動SSE Reconnectを行わない
- Result UnknownとKnown Failureを区別し、FIP-010へ安全に引き渡す
- FIP-008で保留したConcrete HTTP GatewayとProduction Wiringを完成させる

初心者向けに整理すると、SSEはAliceの返答を少しずつ受け取る仕組みである。ただし、途中まで見えた文章は確定履歴ではない。最後の`assistant.completed`に含まれるMessageだけが、Backendに保存された正式な会話履歴になる。

---

## 3. Scope

### 3.1 In Scope

- Composer入力ValidationとCharacter Counter
- UUID v4 Idempotency Key生成
- Pending User Message表示
- `POST /api/v1/conversation/messages`
- Concrete `ConversationGateway` HTTP Adapter
- GET / POST共通のProduction Wiring
- Bounded JSON / Problem Details読込
- Incremental SSE ParseとProtocol Validation
- SSE Resource Limit Enforcement
- Sending / Thinking / Streaming表示
- Delta Presentation Batching
- Canonical Completion Merge
- Connection Close、TimeoutおよびProtocol FailureのState接続
- Riverpod Controller / Provider接続
- Unit Test、Widget TestおよびGateway Integration Test計画

### 3.2 Out of Scope

- Automatic POST Retry
- Automatic SSE Reconnect
- Error Recovery ButtonおよびFailure別日本語文言の完成
- Failed Partial Responseの操作UI
- `REQUEST_IN_PROGRESS`のCountdown UI
- Initial Load Retry UI
- Older History Pagination
- Follow-latest Scroll Policyの完成
- Keyboard ShortcutまたはDesktop固有操作
- Conversation History削除
- Multiple Conversation
- Authentication / Authorization
- Personal Memory、Tool Calling、Agent、Voice
- Streaming中のApp再起動復元またはPending Send端末永続化

FIP-010はFailure Recovery、FIP-011はPagination / Scrollを担当する。FIP-009はFailureを正しいStateへ接続するが、Recovery UIを独自に完成させない。

---

## 4. Prerequisites

- FIP-001 Completed / Approved
- FIP-002 Completed / Approved
- FIP-003 Domain Foundation Draft Planning Completed
- FIP-004 API Contract Foundation Draft Planning Completed
- FIP-005 Application State Draft Planning Completed
- FIP-006 Visual Foundation Draft Planning Completed
- FIP-007 Screen Shell Draft Planning Completed
- FIP-008 Initial History Draft Planning Completed
- Phase 1 API Design Review Approved
- Phase 1 Detailed Design Cross Review Approved

Implementation再開時には、Phase 2〜4設計後のCross-phase Reviewを通過した最新版だけを使用する。

---

## 5. Source of Truth

| Concern | Source of Truth |
|---|---|
| Endpoint、HTTP、SSE、Limit、Idempotency | `api-design.md` |
| Frontend Layer、State、Configuration | `frontend-design.md` |
| Screen、Composer、Thinking、Streaming表示 | `frontend-ui-design.md` |
| Domain Value Object | `fip-003-domain-foundation-plan.md` |
| DTO、Parser、Bounded Contract | `fip-004-api-contract-foundation-plan.md` |
| State、Reducer、Failure | `fip-005-application-state-plan.md` |
| Theme / Alice Core | `fip-006-visual-foundation-plan.md` |
| Screen Shell | `fip-007-screen-shell-plan.md` |
| Initial Canonical History | `fip-008-initial-history-plan.md` |
| Security / Logging | `security-design.md` |
| Test Level / Fake / Integration | `test-design.md` |

矛盾を発見した場合、AIは推測で実装せず、該当Source of Truthを先に修正する。

---

## 6. Fixed Phase 1 API Contract

### 6.1 Send Request

```http
POST /api/v1/conversation/messages
Content-Type: application/json
Accept: text/event-stream
Idempotency-Key: <UUID v4>

{"content":"<original user content>"}
```

Frontendは次を送信しない。

- Conversation ID
- User / Assistant Role
- Message ID
- Timestamp
- Provider名
- Model名
- PromptまたはMemory情報

Conversationが未作成の場合はBackendが最初の送信時に生成する。成功ResponseはConversation作成有無にかかわらず`200 OK`のSSEである。

### 6.2 Pre-stream Failure

HTTP `200`と`stream.started`より前の失敗はRFC 9457 Problem Detailsとして処理する。

- Bodyは64 KiBを上限とする
- Backendの安定したError CodeからCategoryへMappingする
- Raw `detail`やRaw BodyをLogへ出さない
- `REQUEST_IN_PROGRESS`では`Retry-After` delta-secondsを取得する
- Contract必須Fieldの欠落や不正な`Retry-After`は`protocolViolation`とする

### 6.3 Normal SSE Sequence

```text
stream.started
assistant.delta × 0..N
assistant.completed
connection close
```

### 6.4 Failure SSE Sequence

```text
stream.started
assistant.delta × 0..N
stream.failed
connection close
```

`assistant.completed`または`stream.failed`のどちらか一つだけをTerminal Eventとして受理する。Terminal Event後のEvent、Terminal Eventの重複、Started前のDeltaはProtocol Violationである。

### 6.5 Replay Sequence

Completed RequestのReplayではDeltaがなくてもよい。

```text
stream.started
assistant.completed
```

Failed RequestのReplay:

```text
stream.started
stream.failed
```

FrontendはDeltaの存在を成功条件にしてはならない。

---

## 7. Content Validation and Preservation

### 7.1 Validation Rule

送信可能なContentは次をすべて満たす。

- Stringである
- Emptyではない
- Whitespace-onlyではない
- Unicode Code Pointで10,000以下

Character CountはDartのUTF-16 code unit数ではなく`runes.length`相当で判定する。絵文字等を不当に2文字として数えないためである。

### 7.2 Preservation Rule

ValidationではTrim相当の確認を行ってよいが、送信ContentをTrim、Normalize、Truncateまたは改行変換してはならない。

```text
User Input == Pending Content == JSON Request Content
```

前後の空白や改行を含む有効Messageは、そのままBackendへ送る。

### 7.3 Composer Behavior

- Inputは1〜5行
- Enterは改行
- Send Button Tapだけで送信
- 9,000 Code Points以上で`9,000 / 10,000`形式のCounterを表示
- 10,000超過時はInline Errorを表示しSendを無効化
- Whitespace-onlyは通常Sendを無効化し、入力中に過剰なError表示をしない
- Sending / Streaming中は追加送信を無効化する

---

## 8. Idempotency Key Lifecycle

### 8.1 Creation

- Userが有効なSend ButtonをTapした時点でUUID v4を一度だけ生成する
- Application Testでは生成処理を差し替え可能にする
- Phase 1では`uuid: 4.6.0`をApproved Dependencyとして追加する
- UUID以外のRandom ID形式をAIが選択してはならない

### 8.2 Retention

同一Logical Sendでは次を固定する。

- Original Content
- Idempotency Key
- Pending Send identity

Network切断やTerminal未受信でも、新しいKeyへ自動交換してはならない。

### 8.3 Disposal

- `assistant.completed`受理後にPending Sendを破棄する
- `stream.failed`受理後もTerminal FailureとしてPending送信処理を終了する
- Terminal未受信ではResult UnknownとしてKeyとContentをMemory上に保持する
- App再起動ではPendingを復元・自動再送せず、Canonical Historyを再取得する
- Message ContentとKeyを端末へ永続化しない

### 8.4 Retry Boundary

FIP-009は自動Retryしない。User操作によるRetry、同じKeyを使う条件およびResult確認UIはFIP-010で実装する。

---

## 9. Client Timeout Configuration

Backend SSEの既定Total Timeoutは180秒である。Frontendが同じ瞬間に打ち切ると、BackendのTerminal Eventが移動中に破棄される可能性があるため、Frontend側には10秒のDelivery Graceを設ける。

### 9.1 Approved Draft Values

| Setting | Default | Valid Range | Use |
|---|---:|---:|---|
| `ALICE_HTTP_REQUEST_TIMEOUT_SECONDS` | 10 | 1〜60 | GETおよびSSE Response Header受信まで |
| `ALICE_SSE_CLIENT_TIMEOUT_SECONDS` | 190 | 180〜600 | POST開始からTerminal Event受理まで |

### 9.2 Configuration Ownership

- FIP-009で`AppConfiguration`へ二つのDurationを追加する
- Environment未指定時は上記Defaultを一箇所のNamed Constantから適用する
- HTTP Adapter内へ`10`、`180`または`190`のMagic Numberを置かない
- Environment値はASCII Decimal Integerだけを許可する
- Empty、Whitespace、符号、小数、範囲外を起動時に拒否する
- Error MessageへRaw Environment Valueを含めない
- `Local.xcconfig.example`にはPlaceholderと説明だけを追加し、実値をCommitしない

BackendのSSE Timeoutを180秒より長く変更する場合、Frontend Client TimeoutもBackend以上に更新してDetailed Designを整合させる。

### 9.3 Timeout Result

- Response Header前のTimeoutは既知のRequest失敗として扱う
- POST送信後またはSSE開始後のTimeoutはResult Unknownとして扱う
- Timeout時はStream SubscriptionをCancelし、追加Bufferを停止する
- Automatic RetryまたはAutomatic Reconnectを行わない

---

## 10. Streaming Resource Limits

Frontendは`Content-Length`だけを信頼せず、実際に受信したByte数を計測する。

| Resource | Limit |
|---|---:|
| Conversation JSON | 64 KiB |
| Message History JSON | 16 MiB |
| Problem Details JSON | 64 KiB |
| Send Request Body | 128 KiB |
| Single SSE Frame | 1 MiB |
| Single `assistant.delta` JSON Data | 64 KiB |
| Accumulated Assistant Text | 50,000 Unicode Code Points |
| Entire SSE Stream | 8 MiB |
| Non-comment SSE Events | 10,000 |

Rules:

- Limit超過を検出した時点で購読をCancelする
- Limit超過後に追加DataをBufferしない
- Partial Assistant TextをCanonical化しない
- SSE CommentもStream Byte Limitへ含める
- SSE CommentはNon-comment Event Countへ含めない
- 未知のNon-comment Eventは表示上無視してもEvent Countへ含める
- Decompressed / decoded前後で契約が曖昧にならないよう、HTTPから得た実受信Byteを基準にする

---

## 11. SSE Parse and Validation Pipeline

処理順序を次に固定する。

```text
HTTP Byte Stream
  -> Entire Stream Byte Counter
  -> Incremental UTF-8 Decode
  -> SSE Frame Boundary Detection
  -> Single Frame Size Check
  -> Event / Data Field Parse
  -> Non-comment Event Count
  -> JSON Data Size Check
  -> DTO Decode / Contract Validation
  -> Application Send Event Mapping
  -> ConversationStateReducer
  -> Presentation Delta Batching
  -> Widget Render
```

### 11.1 UTF-8

- Split multi-byte characterを正しく結合するIncremental Decoderを使用する
- Invalid UTF-8は`protocolViolation`
- Raw invalid bytesをLogへ出さない

### 11.2 Line Endings

SSE Parserは契約で許可されたLF / CRLFを扱う。Chunk境界とLine境界が一致する前提を置かない。

### 11.3 Event Dispatch

- Comment EventはHeartbeatとしてDisplay / Contentへ反映しない
- `stream.started`で`activeRequestId`を設定する
- `assistant.delta`は同じRequest IDだけを受理する
- `assistant.completed`はCanonical DTOをDomain Modelへ変換してReducerへ渡す
- `stream.failed`は安全なCodeからFailure CategoryへMappingする
- Unknown Event TypeはForward Compatibilityのため表示上無視する
- Contract順序違反は`protocolViolation`

### 11.4 Terminal Handling

- Terminal受理時に未表示Delta Bufferを直ちにFlushする
- その後ReducerへTerminal Eventを渡す
- SubscriptionをCancel / Closeする
- Terminal後に到着したByteやEventを状態へ反映しない
- Connection Close時にTerminal未受信なら`resultUnknown`

---

## 12. Canonical Completion Rule

`assistant.completed`は次を含む。

- `requestId`
- Canonical `userMessage`
- Canonical `assistantMessage`

FrontendはDelta連結結果を正式なAssistant Messageとして保存しない。

Completion処理:

1. 未表示DeltaをPresentationへFlushする
2. Pending User MessageをCanonical `userMessage`へ置換する
3. Temporary Assistant Textを破棄する
4. Canonical `assistantMessage`をMessage ListへMergeする
5. Message ID重複をFIP-005のMerge Ruleで処理する
6. `activeRequestId`とPending SendをClearする
7. Stateを`ready`へ遷移する
8. Alice Core Stateを`idle`へ戻す

同じMessage IDかつ同じCanonical FieldはDuplicateとして無害に扱う。同じIDでFieldが異なる場合は`protocolViolation`としてReconciliation対象にする。

---

## 13. Presentation Streaming Policy

### 13.1 Thinking State

Send開始から最初のDeltaまで:

- Alice Coreを`thinking`
- Message List左側へ`考えています…`
- Screen Readerへ`Aliceが回答を作成中です`を一度だけ通知
- Delta未到着でもAssistant Bubbleを空文字で作らない

### 13.2 First Delta

- 最初のVisible DeltaはTimer待ちせず即時表示する
- `考えています…`をTemporary Assistant Textへ置換する
- Alice Coreを`streaming`へ変更する

### 13.3 Subsequent Delta

- 50msをBaselineとして複数Deltaを一つのPresentation更新へまとめる
- Cross-phase Reviewで33〜100msの範囲内に調整可能とする
- Raw DeltaごとのResource CountとProtocol Validationは省略しない
- BatchingはUI Rebuild頻度だけを減らし、Canonical Dataを変更しない
- DeltaごとにScreen Reader Announcementを行わない

### 13.4 Completion

- Terminal Event時は50ms待たず残りをFlushする
- Canonical Messageへ置換する
- Screen ReaderへCompletionを一度だけ通知する
- ScrollをDeltaごとに強制しない。Follow-latest PolicyはFIP-011が所有する

---

## 14. State and Data Flow

```text
Valid Send Tap
  -> Original Content capture
  -> UUID v4 generation
  -> PendingSend creation
  -> status: sending
  -> pending user message display
  -> POST /messages
  -> stream.started
  -> thinking display
  -> first assistant.delta
  -> status: streaming
  -> temporary assistant text update
  -> assistant.completed
  -> canonical user + assistant merge
  -> pending / temporary clear
  -> status: ready
```

Failure:

```text
Pre-stream Problem Details
  -> mapped ConversationFailure
  -> status: sendFailed

stream.failed
  -> terminal known failure
  -> partial text remains non-canonical
  -> status: sendFailed

close / timeout without terminal
  -> resultUnknown
  -> same content + key retained in memory
  -> status: sendFailed
```

---

## 15. Concurrency Rules

- `initialLoading`中にSendしない
- `sending`または`streaming`中に次のSendを開始しない
- `loadingOlder`中にSendしない
- Send中にPaginationを開始しない
- Riverpod Controllerは二重TapをStateで防ぐ
- WidgetのButton Disableだけを排他制御として信頼しない
- Backend `CONVERSATION_BUSY`を正しくFailureへMappingする

Phase 1は単一Conversationかつ一度に一つの生成処理とする。

---

## 16. Concrete Gateway Responsibilities

FIP-009で`ConversationGateway` Portの全MethodをConcrete Adapterへ接続する。

### 16.1 GET Methods

- `GET /api/v1/conversation`
- `GET /api/v1/conversation/messages`
- FIP-004 DTO / Mapperを利用する
- ResponseをSize上限内で読み込む
- CursorをOpaque StringとしてQueryへ渡す
- `404 CONVERSATION_NOT_FOUND`を未作成ConversationへMappingする

### 16.2 Send Method

- Shared `http.Client`を使用する
- Adapter自身がClientをCloseしない
- AppConfiguration Originへ固定Pathを結合する
- User InputからPathまたはHostを構築しない
- Required Headerだけを設定する
- JSON Bodyに`content`だけを含める
- Request Body Byte Sizeを送信前に確認する
- `http.Client.send()`でStreamed Responseを取得する
- Non-200 ResponseはBounded Problem Detailsとして処理する
- `200`で`Content-Type: text/event-stream`を確認する
- SSE BodyをIncrementalに処理する

### 16.3 Cancellation

Controller Dispose、Terminal、TimeoutまたはLimit超過時にStream SubscriptionをCancelする。Cancel後のBackend処理停止は保証されないため、Terminal未受信なら結果をUnknownとして扱う。

---

## 17. Error Mapping

| Source | Application Category | Result Certainty |
|---|---|---|
| Local content invalid | `validation` | Not Sent |
| `CONVERSATION_BUSY` | `conversationBusy` | Known Failure |
| `REQUEST_IN_PROGRESS` + valid Retry-After | `requestInProgress` | Unknown / Existing request processing |
| `IDEMPOTENCY_KEY_REUSED` | `idempotencyConflict` | Known Failure |
| `GENERATION_FAILED` | `generationFailed` | Known Failure |
| `RESPONSE_TIMEOUT` terminal | `responseTimeout` | Known Failure |
| Network error before request dispatch | `networkUnavailable` | Not Sent |
| Network close after dispatch without terminal | `resultUnknown` | Unknown |
| Invalid SSE / contract sequence | `protocolViolation` | Unknown after dispatch |
| Limit exceeded | Matching limit category | Unknown after dispatch |
| Unmapped server error | `serverFailure` or `unknown` | Conservative |

Result CertaintyはHTTP Client Exceptionの種類だけで断定しない。Request送信後にBackendが処理した可能性がある場合はUnknown側へ倒す。

---

## 18. Logging and Security

Logへ出力しないもの:

- User Message Content
- Assistant Delta / Completed Content
- Raw JSON / Raw SSE Frame
- Idempotency Key
- Cursor
- Base URL Raw Value
- Credential / Token
- System Prompt
- Provider / Model Request Response

許可される最小情報:

- Operation名
- Sanitized Failure Category
- HTTP Status Class
- Non-sensitive Request ID
- Size Limit種別
- Duration / Event Count等のContentを含まないMetric

Request IDをUser ContentやIdempotency Keyと同じLog Recordへ結合しない。Phase 1ではAuthentication Headerを追加しない。

---

## 19. Dependency Decision

FIP-009 Implementation時に追加するDependency:

```yaml
dependencies:
  uuid: 4.6.0
```

既存Dependency:

- `flutter_riverpod: 3.4.2`
- `http: 1.6.0`

追加しないもの:

- SSE専用Package
- Automatic Retry Package
- WebSocket Package
- JSON Code Generator
- State Code Generator
- Logging SDK

標準Dart Stream、UTF-8 DecoderおよびFIP-004 Parser Contractで要件を満たす。

---

## 20. Expected File Structure

Implementation時の概念構成:

```text
frontend/
├── lib/
│   ├── app/
│   │   └── app_configuration.dart                    # timeout設定を追加
│   └── conversation/
│       ├── application/
│       │   └── conversation_controller.dart          # Send orchestration
│       ├── infrastructure/
│       │   └── http/
│       │       ├── conversation_http_gateway.dart    # GET / POST concrete adapter
│       │       └── conversation_http_policy.dart     # injected timeout / limit policy
│       └── presentation/
│           ├── providers/
│           │   └── conversation_providers.dart       # production wiring / UUID factory
│           └── widgets/
│               ├── conversation_composer.dart
│               ├── pending_user_message.dart
│               ├── thinking_indicator.dart
│               └── streaming_assistant_message.dart
├── ios/Flutter/
│   └── Local.xcconfig.example                        # optional timeout examples
└── test/
    ├── app/
    │   └── app_configuration_test.dart
    └── conversation/
        ├── application/
        │   └── conversation_send_flow_test.dart
        ├── infrastructure/http/
        │   └── conversation_http_gateway_test.dart
        └── presentation/
            └── conversation_send_widget_test.dart
```

FIP-003〜FIP-008で既に計画されたFileが存在する場合は新しい重複Fileを作らず、正式なPathへ最小変更する。

---

## 21. Component Responsibilities

| Component | Responsibility | Must Not Do |
|---|---|---|
| `ConversationController` | Validate済みSend開始、Pending作成、Stream Event dispatch | JSON / SSE parse、UI文言決定 |
| `ConversationHttpGateway` | HTTP、Bounded Read、DTO Mapping、SSE stream | Widget State、Retry UI、Content Logging |
| `ConversationHttpPolicy` | Timeout / LimitをTyped値で保持 | Environmentを直接読む |
| UUID Factory Provider | UUID v4をSend Actionごとに一回生成 | Retry時に自動再生成 |
| `ConversationComposer` | Input、Counter、Send Gesture | Network call、Content変換 |
| `ThinkingIndicator` | First Delta前の待機表示 | TimerでFake Delta生成 |
| `StreamingAssistantMessage` | Temporary Text表示 | Canonical Message生成 |
| Reducer | Event順序とCanonical Merge | HTTP / Timer / Provider参照 |

---

## 22. Implementation Procedure

Implementation再開後は次の順序で進める。

1. Phase 2〜4 Cross-phase Review後の文書版を固定する
2. `uuid: 4.6.0`だけを追加しLockfileを更新する
3. `AppConfiguration`へ二つのTimeout設定とValidation Testを追加する
4. `ConversationFailure.retryAfter`を実装する
5. Concrete `ConversationHttpGateway`のGET Methodを実装する
6. POST Request構築とPre-stream Failure処理を実装する
7. Incremental SSE Pipelineと全Limitを実装する
8. Gateway Integration Testを追加する
9. ControllerへSend OrchestrationとUUID Factoryを接続する
10. Composer Validation / Counter / Pending表示を実装する
11. Thinking / First Delta / 50ms Batchingを実装する
12. Canonical Completion Mergeを接続する
13. Production Provider Wiringと`AliceApp` Home接続を行う
14. Widget / Semantics Testを追加する
15. Format、Analyze、Test、iOS Simulator確認を実行する

各StepでScope外変更が生じた場合は停止してDesignを確認する。

---

## 23. Unit Test Plan

### 23.1 Content

- Empty拒否
- Whitespace-only拒否
- 10,000 Code Points許可
- 10,001 Code Points拒否
- EmojiをCode Point単位で数える
- 前後空白を保持する
- 改行を保持する

### 23.2 Idempotency

- 一Send ActionでUUID Factoryを一度だけ呼ぶ
- DeltaごとにKeyを再生成しない
- Terminal未受信Failureで同じKey / Contentを保持する
- Completed / Failed TerminalでPending処理を終了する
- 二重Tapで二つ目のKeyを生成しない

### 23.3 SSE

- Chunk境界をまたぐUTF-8
- Chunk境界をまたぐLine / Frame
- Comment Heartbeat無視
- Zero Delta Completion
- Multiple Delta Completion
- `stream.failed`
- Unknown Event表示無視とEvent Count加算
- Started前Delta拒否
- Request ID不一致拒否
- Duplicate Terminal拒否
- TerminalなしCloseをResult Unknownへ変換

### 23.4 Limits

- 各上限ちょうどを許可
- 各上限+1を拒否
- Content-Lengthなしでも実Byteで停止
- Frame Limit超過後にBufferを増やさない
- Event Count超過
- Assistant Code Point超過
- Stream Byte超過

### 23.5 Canonical Completion

- Pending UserをCanonical Userへ置換
- Temporary AssistantをCanonical Assistantへ置換
- Delta連結結果とCanonical本文が異なってもCanonicalを採用
- Same ID / Same FieldsをDeduplicate
- Same ID / Different FieldsをProtocol Violation

---

## 24. Gateway Integration Test Plan

実Network Serviceへ依存せず、Process内Fake HTTP Serverまたは`http.BaseClient` Test Doubleで次を検証する。

- Exact Path / Method / Header / JSON Body
- Unknown Request Fieldを送らない
- Request Body 128 KiB Guard
- 200 + `text/event-stream`
- Non-200 Problem Details 64 KiB Guard
- Invalid Content-Type
- Header Timeout
- SSE Client Timeout / Delivery Grace
- Connection Close without Terminal
- Subscription Cancellation
- ClientをGatewayがCloseしない
- Raw Content / KeyがFailure文字列へ漏れない

Test FixtureへActual SecretまたはPersonal Conversationを使用しない。

---

## 25. Widget and Accessibility Test Plan

- Valid Send TapでPending User Messageを即時表示
- Send後にInputをClearしFocusを維持
- Sending中Send Button無効
- 9,000未満でCounter非表示
- 9,000以上でCounter表示
- 10,001でError / Send無効
- `考えています…`表示
- First DeltaでThinkingがStreaming Textへ置換
- CompletionでCanonical Bubbleへ置換
- Sending開始Announcementが一回
- DeltaごとにAnnouncementしない
- Completion Announcementが一回
- Text Scale 200%でComposerとStreaming Messageが利用可能
- `Key`, `requestId`, Raw ErrorがSemantics Labelへ出ない

Golden TestはFIP-012へ集約し、FIP-009ではBehaviorとSemanticsを中心に検証する。

---

## 26. Acceptance Criteria

FIP-009 Implementationは次をすべて満たすこと。

1. Original Contentを変更せず送信する
2. UUID v4を一Logical Sendにつき一度だけ生成する
3. Exact POST Contractを満たす
4. SSEをIncrementalに処理し全Resource Limitを守る
5. Event順序とRequest IDを検証する
6. Zero Delta Completionを成功として扱う
7. First Deltaを即時表示し後続を50ms単位でまとめる
8. `assistant.completed`のCanonical MessageをSource of Truthにする
9. TerminalなしCloseをResult Unknownにする
10. Automatic POST Retry / Reconnectを行わない
11. Sending中のConcurrent Sendを防ぐ
12. Concrete GatewayのGET / POSTが完成してProduction Wiringされる
13. Content、Key、Cursor、Raw PayloadをLogへ出さない
14. Unit / Integration / Widget Testが合格する
15. Scope外のRecovery / Pagination / Phase 2〜4機能を実装しない

---

## 27. Completion Conditions

FIP-009 Implementation完了条件:

- Approved Dependencyだけが追加されている
- Configuration ValidationがTest済み
- Concrete Gateway全Methodが実装済み
- Send / SSE / Canonical Flowが実装済み
- FailureがFIP-005 Stateへ接続済み
- Recovery UIはFIP-010へ残されている
- `dart format lib test`成功
- `flutter analyze`成功
- `flutter test`全件成功
- iPhone SimulatorでSend、Streaming、Completionを確認済み
- Review指摘が解消済み

Draft計画書作成完了は、FIP-009 Implementation完了を意味しない。

---

## 28. Dependencies on Other FIPs

| FIP | Relationship |
|---|---|
| FIP-003 | Message Content、ID、TimestampのDomain Ruleを利用 |
| FIP-004 | DTO、Problem Details、SSE Parser、Limitを利用 |
| FIP-005 | Gateway Port、State、Reducer、Failureを利用 |
| FIP-006 | Alice Core StateとVisual Tokenを利用 |
| FIP-007 | Screen Shell / Composer領域へ接続 |
| FIP-008 | Initial Canonical HistoryとProduction Screen接続を引き継ぐ |
| FIP-010 | Error Mapping、Retry、Result Unknown Recoveryを完成する |
| FIP-011 | PaginationとFollow-latest Scrollを完成する |
| FIP-012 | Golden / E2E / Final Hardeningを行う |

---

## 29. Phase 2-4 Extension Notes

Cross-phase Reviewでは次を確認する。

- Personal Memory ContextがFrontend Send Contractへ不要に漏れていないか
- Tool Calling Event追加時もUnknown SSE Event処理が安全か
- AgentのLong-running TaskがPhase 1 Timeout Modelと混同されないか
- Voice Transcriptが同じMessage Content Ruleを再利用できるか
- Approval / Permission EventをConversation Deltaとして誤処理しないか
- DesktopでEnter / Shortcut Policyを別途追加できるか

FIP-009ではPhase 2〜4のEvent名、Payload、UIまたはPermission仕様を確定しない。

---

## 30. AI Coding Assistant Constraints

AIは次を独自変更してはならない。

- Endpoint / Header / JSON / SSE Event名
- UUID v4 Lifecycle
- Resource Limit
- Canonical Completion Rule
- No Automatic Retry / Reconnect
- Timeout Default / Range
- Logging禁止情報
- Dependency Version
- State / Reducer Boundary

局所的なPrivate Method名、Test Helper名および標準的なDart実装詳細は、Designと矛盾しない範囲で判断してよい。

---

## 31. Draft Review Checklist

### Contract

- [x] POST ContractがExactに定義されている
- [x] Pre-streamとPost-stream Failureが分離されている
- [x] ReplayでZero Deltaを許可している
- [x] Canonical CompletionをSource of Truthとしている

### Safety

- [x] 全Size / Event Limitが記載されている
- [x] Automatic Retry / Reconnectを禁止している
- [x] TerminalなしCloseをResult Unknownとしている
- [x] Sensitive Loggingを禁止している

### State / UI

- [x] Pending / Temporary / Canonicalが分離されている
- [x] ThinkingからStreamingへの遷移が定義されている
- [x] Delta BatchingとTerminal Flushが定義されている
- [x] Concurrent Sendを禁止している

### Planning Boundary

- [x] Source Codeを変更していない
- [x] FIP-010 / FIP-011責務を先行実装していない
- [x] Phase 2〜4の具体仕様を推測していない
- [x] Cross-phase Reviewを必須としている

---

---

## 32. Formal Cross-phase Review Resolution

```text
FIP-009 Document: Approved / Implementation Ready
FIP-009 Draft Planning: Completed
FIP-009 Cross-phase Review: PASS
FIP-009 Implementation: Not Started
```

- Result: **PASS**
- Critical Finding: 0
- High Finding: 0
- Blocking Medium Finding: 0
- Low Finding: 0
- Architecture redesign: None
- Implementation Ready: YES
- Implementation: Not Started

The review confirms that this FIP remains within Phase 1 Conversation scope and does not introduce Phase 2 Personal Memory, Phase 3 Tool / External Service execution authority, or Phase 4 Agent / PC / Browser / Voice execution semantics. Existing cross-phase terminology corrections, where applicable, are documentation alignment only and do not alter architecture ownership.

**Resolution: PASS — Approved / Implementation Ready.**

Implementation may proceed in dependency order, subject to the project-level implementation gate and the approved Source of Truth.
