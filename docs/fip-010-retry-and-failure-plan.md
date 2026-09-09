# Project Alice - FIP-010 Retry and Failure Implementation Plan

## 1. Document Information

| Item | Value |
|---|---|
| Document | `fip-010-retry-and-failure-plan.md` |
| FIP | FIP-010 Retry and Failure |
| Status | Approved / Implementation Ready |
| Draft Planning | Completed |
| Implementation | Not Started |
| Cross-phase Review | PASS |
| Target | Phase 1 iOS Frontend |
| Last Updated | 2026-09-09 JST |

本ドキュメントは、Phase 1 FrontendのFailure Presentation、安全なRetry、Result Unknown確認、Terminal FailureおよびPartial Assistant Contentを、後からGitHub Copilot等のAI Coding Assistantへ実装依頼できる粒度で定義するDraft実装計画である。

本Draft作成時点では、ソースコード、Test Code、Dependency、AssetまたはXcode設定を変更しない。Phase 2〜4設計後のCross-phase Reviewは完了済みである。実装は承認済みSource of Truthとプロジェクト全体のImplementation Gateに従う。

---

## 2. Purpose / Goal

FIP-010の目的は、失敗をすべて同じ「再試行」にまとめず、処理結果の確実性とIdempotency Stateに応じて安全なRecovery ActionだけをUserへ提示することである。

達成目標:

- Known FailureとResult Unknownを区別する
- Same-key結果確認とNew Logical Sendを区別する
- `REQUEST_IN_PROGRESS`の`Retry-After`を守る
- Terminal Failureを同じKeyで再送しない
- Partial Assistant ContentをCanonical Historyと区別する
- Protocol / Decode ErrorではConversationを再同期する
- Original Content、Canonical HistoryおよびComposer Draftを必要以上に失わない
- Automatic POST Retry / Automatic SSE Reconnectを行わない
- Error Codeを安全な固定日本語文言とActionへMappingする
- VoiceOverを含め、ErrorとRecovery Actionの関係を理解可能にする

初心者向けに整理すると、「通信が切れた」は「送信に失敗した」と同じではない。Backendでは処理が完了しているかもしれないため、新しい送信としてやり直すと同じMessageが二重に保存される可能性がある。FIP-010は、その違いをUserにも実装にも明確にする設計である。

---

## 3. Scope

### 3.1 In Scope

- Send FailureのPresentation Mapping
- Result Unknownの`結果を確認`
- Same Content / Same Idempotency Keyによる結果確認Request
- `REQUEST_IN_PROGRESS`待機制御
- `CONVERSATION_BUSY` / `SERVICE_UNAVAILABLE`のUser-driven Retry
- `IDEMPOTENCY_KEY_CONFLICT`のConversation Reload
- Protocol / Decode / Resource Limit FailureのReconciliation
- Terminal `stream.failed`のFailed Response
- Partial Assistant Content表示
- `同じ内容でもう一度送る`によるNew Logical Send
- Recovery中の多重操作防止
- Error Accessibility / Semantics
- Recovery Unit / Widget / Gateway Test計画

### 3.2 Out of Scope

- Automatic POST Retry
- Automatic SSE Reconnect
- Background Polling
- Failed Sendの端末永続化
- App再起動後のPending Send復元
- Older History Pagination Retryの完成
- Follow-latest Scroll Policy
- Backend Idempotency State変更
- Backend Processing Lease変更
- Multiple Conversation
- Authentication / Authorization
- Personal Memory、Tool Calling、Agent、Voice

Initial Load Failureの基本UIと再読込はFIP-008、送信とSSE TransportはFIP-009、Older Page RetryはFIP-011が担当する。

---

## 4. Prerequisites

- FIP-001 / FIP-002 Completed / Approved
- FIP-003〜FIP-009 Draft Planning Completed / User Confirmed
- API Design Review Approved
- Database Design Review Approved
- Frontend UI Decision UI-009 Accepted
- Phase 2〜4設計後にCross-phase Reviewを実施する

---

## 5. Source of Truth

| Concern | Source of Truth |
|---|---|
| Idempotency、Replay、Retry-After、Error Code | `api-design.md` |
| User Message保存、Terminal Failure、Lease | `database-design.md` |
| Frontend State / Retry Rule | `frontend-design.md` |
| Error UI / Action Label / Partial Content | `frontend-ui-design.md` UI-009 |
| DTO / Problem Details / SSE Decode | `fip-004-api-contract-foundation-plan.md` |
| Failure / Result Certainty / Reducer | `fip-005-application-state-plan.md` |
| Send / Streaming / Gateway | `fip-009-send-and-streaming-plan.md` |
| Security / Non-logging | `security-design.md` |
| Failure / Replay Test | `test-design.md` |

---

## 6. Required API Contract Correction

### 6.1 Problem

現在の`stream.started`は`requestId`だけを返す。一方、Backendは`stream.started`より前にUser MessageをCanonical Historyへ保存する。

その後`stream.failed`へ到達した場合、Frontendが保持しているのは送信前に作ったPending Contentだけであり、Backendが生成した次を取得できない。

- Canonical User Message ID
- Canonical Sequenceに対応する位置
- Canonical `createdAt`

Historyを再取得して本文や末尾位置だけで照合すると、同じContentを複数回送った場合に誤ったMessageを対応付ける可能性がある。AIへこの推測を委ねてはならない。

### 6.2 Accepted Decision RD-010-001

`stream.started`へCanonical `userMessage`を追加する。

```text
event: stream.started
data: {
  "requestId": "9af98b1e-5aa8-4d67-bf53-f657ddfe3dcb",
  "userMessage": {
    "id": "2b3c4d5e-6f70-489a-8bcd-ef0123456789",
    "role": "user",
    "content": "こんにちは",
    "createdAt": "2026-08-20T23:00:00.000+09:00"
  }
}
```

Rules:

- `userMessage`はCommon Message Schemaを使用する
- Roleは必ず`user`
- Start Transaction成功後に保存されたCanonical Messageだけを返す
- Flutterは受信時にPending User MessageをCanonical User Messageへ置換する
- `assistant.completed`にも従来どおりCanonical `userMessage`を含め、Replayと最終整合確認に使用する
- Completed / Failed Replayの`stream.started`にも保存済みCanonical User Messageを含める
- Pre-stream Failureでは`stream.started`を送らず、User Messageは保存されない

### 6.3 Reason

この変更により、Normal Completion、Terminal Failure、Failed Replayのすべてで、Frontendが本文一致等の推測をせずUser MessageをCanonical化できる。

### 6.4 Alternatives

| Alternative | Decision | Reason |
|---|---|---|
| `stream.failed`だけへUser Messageを追加 | Not selected | Normal Streaming開始時にCanonical化できず、Event間で契約が非対称になる |
| History本文・末尾位置で推測する | Rejected | 同一Contentや連続User Messageで誤対応する |
| User MessageをPendingのまま残す | Rejected | Terminal Failure後もCanonical ID / Timestampを持てない |

### 6.5 Applied Follow-up

本Decisionは採用済みであり、次へ横断反映した。

- `api-design.md`
- `frontend-design.md`
- `fip-004-api-contract-foundation-plan.md`
- `fip-005-application-state-plan.md`
- `fip-009-send-and-streaming-plan.md`
- `test-design.md`

これはArchitecture変更ではなく、既存Persistence FlowをFrontendへ安全に公開するAPI Detailed Design補正である。

---

## 7. Recovery Vocabulary

FIP-010ではAction名を次に固定する。

| Internal Action | UI Label | Meaning |
|---|---|---|
| `editInput` | Buttonなし | Composer内容を修正する |
| `retrySameSend` | `もう一度試す` | 同じContent・同じKeyで同じLogical Sendを再実行 |
| `checkResult` | `結果を確認` | 同じContent・同じKeyで保存済み結果または処理状態を確認 |
| `reloadConversation` | `会話を再読み込み` | POSTせずCanonical Historyを再同期 |
| `sendSameContentAsNew` | `同じ内容でもう一度送る` | 新しいLogical Sendとして新しいUUID v4を生成 |
| `dismissFailure` | Actionなし、または補助的な閉じる操作 | Terminal Failure表示を終了する |

`retrySameSend`と`sendSameContentAsNew`は名前も処理も異なる。共通の`retry()` Method一つへ曖昧に集約してはならない。

---

## 8. Failure Classification

### 8.1 Result Certainty

| Certainty | Meaning | Allowed Direction |
|---|---|---|
| Not Sent | Backend処理開始前と確定 | Same-key Retryまたは入力修正 |
| `knownFailed` | Terminal Failureが保存済み | New Logical Sendまたは終了 |
| `resultUnknown` | Backend結果を確認できない | Same-key Result CheckまたはReconciliation |

正常完了はFailureではなくCanonical Completionとして`ready`へ遷移する。

### 8.2 Conservative Rule

RequestがBackendへ到達した可能性を否定できない場合は`resultUnknown`として扱う。HTTP Client Exception名だけでNot Sentと断定しない。

---

## 9. Recovery Policy Matrix

| Condition | Message | Primary Action | Key Rule | Pending Rule |
|---|---|---|---|---|
| Empty / Whitespace | `メッセージを入力してください。` | Input修正 | Key生成前 | Composer維持 |
| >10,000 Code Points | `メッセージは10,000文字以内で入力してください。` | Input修正 | Key生成前または破棄 | ComposerへOriginal復元 |
| Request Body Too Large | `メッセージのデータ量が大きすぎます。内容を短くしてください。` | Input修正 | 失敗Keyを再利用しない | ComposerへOriginal復元 |
| Network Failure確定・Pre-dispatch | `メッセージを送信できませんでした。` | `もう一度試す` | Same Key | Pending保持 |
| Network切断・Terminalなし | `送信結果を確認できませんでした。` | `結果を確認` | Same Key | Pending保持 |
| `REQUEST_IN_PROGRESS` | `メッセージはまだ処理中です。` | `結果を確認` | Retry-After後Same Key | Pending保持 |
| `CONVERSATION_BUSY` | `Aliceは別の回答を作成中です。完了後にもう一度お試しください。` | `もう一度試す` | Same Key | Pending保持 |
| Pre-stream `SERVICE_UNAVAILABLE` | `Aliceを一時的に利用できません。` | `もう一度試す` | Same Key | Pending保持 |
| `IDEMPOTENCY_KEY_CONFLICT` | `送信状態に矛盾が見つかりました。会話を再読み込みしてください。` | `会話を再読み込み` | POST再送禁止 | Reconciliation中保持 |
| Protocol / Decode Error | `表示を同期できませんでした。` | `会話を再読み込み` | POST再送禁止 | 未解決なら保持 |
| Response / SSE Limit | `表示を安全に続けられませんでした。` | `会話を再読み込み` | POST自動再送禁止 | 未解決なら保持 |
| Terminal `stream.failed` | Code別固定文言 | New SendはSecondary | Same Key再送禁止 | Canonical Userを保持 |
| Unknown Error after dispatch | `送信結果を確認できませんでした。` | `結果を確認` | Same Key | Pending保持 |

Backendの`detail`またはSSE `message`を直接表示せず、安定した`code`から既知の固定文言へMappingする。

---

## 10. Same-key Result Check Flow

### 10.1 Preconditions

- `pendingSend`が存在する
- Failureが`resultUnknown`または`requestInProgress`
- ContentとIdempotency Keyが変更されていない
- 別のRecovery / Send / Pagination処理が進行中でない
- `retryNotBefore`がある場合は現在時刻が到達済み

### 10.2 Flow

```text
User taps 結果を確認
  -> action disabled
  -> same OutgoingMessageを取得
  -> same POST /messages
  -> REQUEST_IN_PROGRESS
       -> Retry-Afterを更新
       -> actionを期限までdisabled
  -> completed replay
       -> stream.started + assistant.completed
       -> canonical merge
       -> ready
  -> failed replay
       -> stream.started + stream.failed
       -> terminal failure UI
  -> terminalなし切断
       -> resultUnknownを維持
```

Replayでは`requestId`が新しくなるため、過去の`requestId`との一致を要求しない。一つのHTTP Attempt内だけでSSE Eventの`requestId`一致を検証する。

### 10.3 No Automatic Loop

`REQUEST_IN_PROGRESS`受信後、Retry-After経過時にButtonを有効化するだけとする。Timer満了を契機に自動POSTしない。

---

## 11. Retry-After State

`ConversationRecoveryState`を`ConversationScreenState`へ追加する。

| Field | Type | Meaning |
|---|---|---|
| `action` | `ConversationRecoveryAction` | 現在許可されたAction |
| `inProgress` | `bool` | Recovery Request実行中 |
| `retryNotBefore` | `DateTime?` | Actionを再度許可するDevice上の時刻 |

Rules:

- `retryNotBefore`はHTTP Response受信時刻 + `retryAfter`から生成する
- Device上の一時的なUI制御であり永続化しない
- User向けに絶対時刻を表示しない
- Phase 1では大きなCountdownを表示しない
- Standard Dart `Timer`で期限到達時にStateを更新する
- TimerはController Dispose時にCancelする
- `DateTime Function()`をProviderから注入し、TestでFake Clockへ差し替える
- Timer完了でPOSTを開始しない
- Action連打を`inProgress`とController Guardの両方で防ぐ

新しいClock Packageは追加しない。

---

## 12. Canonical User Message Transition

RD-010-001採用後の処理:

1. Send TapでPending User Messageを表示する
2. `stream.started.userMessage`をDecode / Validateする
3. ContentがPending Original Contentと完全一致することを確認する
4. Pending表示をCanonical User Messageへ置換する
5. Canonical Message ID / `createdAt`を保持する
6. Normal Completionでは`assistant.completed.userMessage`とID / Field一致を再確認する
7. Terminal FailureではCanonical User Messageを残す

不一致は`protocolViolation`であり、本文をどちらかへ推測統合しない。

---

## 13. Terminal Stream Failure

`stream.failed`は同じKeyを再送しても保存済みFailureがReplayされるTerminal Resultである。

| SSE Code | UI Message |
|---|---|
| `RESPONSE_GENERATION_FAILED` | `Aliceの回答を生成できませんでした。` |
| `RESPONSE_TIMEOUT` | `Aliceの回答がタイムアウトしました。` |
| `MESSAGE_SAVE_FAILED` | `回答を会話履歴へ保存できませんでした。` |
| `REQUEST_INTERRUPTED` | `処理を最後まで確認できませんでした。` |
| `INTERNAL_ERROR` / Unknown | `予期しない問題が発生しました。` |

Rules:

- `結果を確認`を表示しない
- Same-key POSTをRecovery Actionとして実行しない
- Canonical User MessageはHistoryへ残す
- Assistant Messageは作らない
- Partial Contentは現在Sessionだけ保持できる
- `同じ内容でもう一度送る`をSecondary Actionとして表示できる
- Userが何もしない選択を許可する

---

## 14. Send Same Content as New

`同じ内容でもう一度送る`はRetryではなく、新しいUser Actionである。

1. UserがButtonを明示的にTapする
2. Terminal FailureのOriginal Contentを取得する
3. 新しいUUID v4を一度だけ生成する
4. 新しい`OutgoingMessage`を作る
5. 元のCanonical User MessageとFailed Responseを履歴上に残す
6. 新しいPending User Messageを追加する
7. FIP-009 Send Flowを開始する

元のKeyを使用しない。Buttonの連打で複数UUIDを生成しない。ContentはTrim、Normalizeまたは改変しない。

---

## 15. Partial Assistant Content

Partial Contentは次の条件でのみ表示する。

- 一件以上のVisible Deltaを受信済み
- Terminal `stream.failed`へ到達した
- 現在のApplication Session中である

Presentation:

- Label: `途中までの回答`
- Semantic Label: `Alice、途中までの回答`
- Text Selection / Copyを可能にする
- Canonical Alice Bubbleと異なるFailure Surfaceを使用する
- Canonical Timestampを表示しない
- Message IDを生成しない
- Canonical Message Listへ追加しない
- App再起動またはReconciliation後に復元されると案内しない
- Contentが空ならPartial Component自体を表示しない

Incomplete Markdownを安全に表示できるRenderer Contractを利用する。Remote Imageを取得せず、Raw HTMLを実行せず、Render失敗時はPlain Textへ安全にFallbackする。

---

## 16. Conversation Reconciliation

### 16.1 Purpose

Reconciliationは画面上のCanonical HistoryをBackendへ合わせる処理であり、Idempotency Resultそのものを本文一致で推測する処理ではない。

### 16.2 Flow

```text
User taps 会話を再読み込み
  -> current canonical history / pending / draftを保持
  -> GET conversation
  -> GET latest messages limit=50
  -> success: canonical listをIDで置換・merge
  -> unresolved pendingは本文一致でclearしない
  -> failure: previous stateを維持
```

### 16.3 Pending Rule

- PendingをContent、Role、Timestamp近似またはList末尾位置でCanonical Messageへ対応付けない
- RD-010-001で取得済みのCanonical User IDがある場合だけIDで対応付ける
- 未解決のResult UnknownはReconciliation後も`結果を確認`可能な状態として保持する
- Terminal FailureのPartial ContentはReconciliation後に破棄可能だが、Canonical User Messageは残す
- Reconciliation成功だけを理由に新しいKeyを生成しない

---

## 17. Error Component Structure

Inline Error Componentの順序:

1. Icon
2. Titleまたは短い説明
3. 必要な場合だけ補足説明
4. Primary Recovery Action
5. 必要な場合だけSecondary Action

Rules:

- Errorを赤色だけで表さない
- Snackbarだけで消える表示にしない
- 一つのErrorに複数のPrimary Actionを置かない
- Stack Trace、Provider、Model、SDK、AWS情報、Endpointを表示しない
- `requestId`と内部Codeを通常画面へ常時表示しない
- Error解消前に別の新規Sendを開始させない

---

## 18. Accessibility

- Error Titleを一度だけLive Announcementする
- Rebuildごとに同じErrorを再Announcementしない
- Error、対象Message、Actionの順で理解できるSemanticsを構成する
- Buttonへ`結果を確認`、`会話を再読み込み`等の具体的Labelを付ける
- Disabled / In Progress状態をVoiceOverへ伝える
- Partial Contentを`Alice、途中までの回答`として区別する
- FocusをError出現時に強制移動しない
- ColorだけでErrorやDisabledを表現しない

---

## 19. Security and Privacy

Log、Analytics、Crash ReportまたはError Stringへ次を含めない。

- Original / Pending User Content
- Partial Assistant Content
- Canonical Conversation Content
- Idempotency Key
- Cursor
- Raw Problem Details
- Raw SSE Frame
- Secret / Credential

Recovery StateはMemory内だけに保持する。Clipboardへ自動コピーしない。Partial ContentのCopyはUserの明示操作だけとする。

---

## 20. Expected File Structure

```text
frontend/lib/conversation/
├── application/
│   ├── conversation_controller.dart
│   ├── conversation_recovery_action.dart
│   └── conversation_recovery_state.dart
├── presentation/
│   ├── providers/
│   │   └── conversation_recovery_providers.dart
│   └── widgets/
│       ├── conversation_error_panel.dart
│       ├── message_send_error_view.dart
│       ├── streaming_failure_view.dart
│       └── partial_assistant_message.dart
└── infrastructure/
    └── http/
        └── conversation_http_gateway.dart

frontend/test/conversation/
├── application/
│   ├── conversation_recovery_policy_test.dart
│   └── conversation_recovery_flow_test.dart
└── presentation/
    ├── conversation_error_panel_test.dart
    └── partial_assistant_message_test.dart
```

既存Fileと責務が重複する場合は新規Fileを増やさず、FIP-005〜FIP-009の正式Pathへ最小変更する。

---

## 21. Component Responsibilities

| Component | Responsibility | Must Not Do |
|---|---|---|
| Recovery Policy | Failureから許可ActionをPure Mapping | HTTP、Timer、UI文言描画 |
| Controller | User Action実行、排他、Timer管理、State遷移 | Raw Error解析、Content Logging |
| Gateway | Same-key POST、GET Reconciliation | Action Label決定、自動Retry |
| Error Panel | Title、説明、Action表示 | Key生成、Network呼出し |
| Partial Message | Non-canonical Content表示 | Message ID / Timestamp生成 |
| UUID Factory | New Logical SendだけでUUID v4生成 | Same-key Result Checkで生成 |

---

## 22. State and Data Flow

### 22.1 Result Unknown

```text
sendFailed(resultUnknown)
  -> checkResult available
  -> User tap
  -> same key POST
  -> replay completed | replay failed | request in progress | unknown again
```

### 22.2 Terminal Failure

```text
stream.failed
  -> canonical user retained
  -> partial content optional
  -> failed response
  -> User ends OR sends same content as new logical send
```

### 22.3 Protocol Failure

```text
protocol / decode / limit failure
  -> stop stream
  -> show sync error
  -> User taps reload
  -> refresh canonical history
  -> unresolved result remains separate if needed
```

---

## 23. Implementation Procedure

Implementation再開後は次の順で進める。

1. RD-010-001を正式Source of Truthへ反映する
2. FIP-004の`StreamStartedEventDto`をCanonical User Message対応へ更新する
3. FIP-005 StateへCanonical User TransitionとRecovery Stateを追加する
4. FIP-009 Gateway / Reducer接続を更新する
5. Pure Recovery Policy Mappingを実装する
6. Same-key Result Check Flowを実装する
7. Retry-After TimerとInjected Clockを実装する
8. Conversation Reconciliationを実装する
9. Terminal Failure / Partial Content Componentを実装する
10. New Logical Send Actionを実装する
11. Accessibility / Semanticsを実装する
12. Unit / Widget / Integration Testを実装する
13. Format / Analyze / Test / iOS Manual確認を行う

---

## 24. Unit Test Plan

### 24.1 Policy Mapping

- 各Failure Categoryが一つの正しいPrimary ActionへMappingされる
- Unknown after dispatchは`checkResult`
- Terminal FailureはSame-key Actionを持たない
- Idempotency ConflictはPOST Actionを持たない
- ValidationはOriginal ContentをComposerへ戻す

### 24.2 Same-key

- Result CheckでKey / Contentが同一
- UUID Factoryが呼ばれない
- New requestIdを正常に受理する
- Completed ReplayのZero Deltaを受理する
- Failed ReplayをTerminal Failureへ移す
- 二重Tapで一Requestだけ開始する

### 24.3 Retry-After

- 2秒受信直後はAction disabled
- Fake Clockで期限前 / ちょうど / 期限後を検証
- Timer完了でPOSTしない
- 新しいRetry-Afterで期限を更新する
- Dispose時にTimerをCancelする
- `Thread.sleep`や実時間待機を使用しない

### 24.4 Canonical User

- `stream.started`でPendingをCanonicalへ置換
- Same Content / Canonical IDを維持
- Content不一致をProtocol Violation
- CompletedのUser MessageとStartedのUser Messageが一致
- Terminal FailureでもCanonical Userを保持

### 24.5 Reconciliation

- 成功時にCanonical MessagesをIDでMerge
- 同一ContentだけでPendingをClearしない
- 失敗時に元History / Pendingを維持
- Reconciliation中の二重Actionを防止

---

## 25. Widget Test Plan

- Result UnknownをPending User Message直下へ表示
- `結果を確認`LabelとSemantics
- REQUEST_IN_PROGRESS期限前のDisabled状態
- Conversation Busyの`もう一度試す`
- Idempotency Conflictの`会話を再読み込み`
- Terminal FailureのCode別固定文言
- PartialなしではFailed Responseだけ表示
- Partialありでは`途中までの回答`を表示
- PartialにTimestampを表示しない
- `同じ内容でもう一度送る`をSecondary Actionとして表示
- Action実行中の連打防止
- ErrorをColorだけで区別しない
- Error Announcementが同じState Rebuildで重複しない
- Raw Detail、Key、Stack Trace、Provider名を表示しない

---

## 26. Integration Test Plan

- Connection close without terminal -> Result Unknown
- Same-key Result Check -> REQUEST_IN_PROGRESS
- Retry-After経過後のUser Action -> Completed Replay
- Same-key Result Check -> Failed Replay
- Terminal Failure後のNew Logical Send -> New UUID一回
- Protocol Error -> History Reconciliation
- Reconciliation後も曖昧なPendingを本文一致でClearしない
- App再起動後はPendingを復元・自動送信しない

Real OpenAIを使用せずFake Transport / Backend Test Doubleを使用する。

---

## 27. Acceptance Criteria

FIP-010 Implementationは次をすべて満たすこと。

1. Result UnknownとKnown Failureが異なるActionになる
2. Same-key Result Checkで新しいUUIDを生成しない
3. Retry-After前にActionできず、期限後も自動POSTしない
4. Terminal Failure後に同じKeyをRetryしない
5. New Logical Sendだけ新しいUUIDを一度生成する
6. Partial ContentをCanonical化・永続化しない
7. Protocol / Decode ErrorをHistory Reconciliationへ接続する
8. Pendingを本文一致でCanonical化しない
9. Error Codeを固定された安全なUI文言へMappingする
10. Recovery中の多重Requestを防ぐ
11. Error / Action / Partial StateがVoiceOverで理解可能である
12. Content、Key、Raw ErrorをLogへ出さない
13. Scope外のAutomatic Retry、Pagination、Phase 2〜4機能を実装しない

---

## 28. Completion Conditions

- RD-010-001と関連Source of Truthが整合済み
- Recovery PolicyとStateが実装済み
- Same-key Result Checkが実装済み
- Terminal Failure / Partial UIが実装済み
- New Logical Send Actionが実装済み
- Unit / Widget / Integration Testが成功
- `dart format lib test`成功
- `flutter analyze`成功
- `flutter test`成功
- iOS Simulator Manual Test成功
- Review指摘が解消済み

Draft計画書作成完了はFIP-010 Implementation完了を意味しない。

---

## 29. Dependencies on Other FIPs

| FIP | Relationship |
|---|---|
| FIP-004 | Updated `stream.started` DTO、Problem Detailsを利用 |
| FIP-005 | Failure、Result Certainty、Reducerを拡張 |
| FIP-006 | Failure Visual Tokenを利用 |
| FIP-007 | Error配置領域を利用 |
| FIP-008 | Initial Reload Flowを再利用 |
| FIP-009 | Same-key POST / SSE Transportを再利用 |
| FIP-011 | Pagination Retry / Scrollを追加 |
| FIP-012 | E2E / Golden / Final Hardening |

---

## 30. Phase 2-4 Extension Notes

Cross-phase Reviewでは次を確認する。

- Memory保存失敗をConversation Failureと混同しないか
- Tool実行のRetryがMessage送信Retryと同じPolicyになっていないか
- 危険操作のApprovalを自動Retryしないか
- Agent TaskのResume / CancelとSame-key Message Retryを分離できるか
- Voice入力のDraft / ConfirmationをNew Logical Sendへ安全に接続できるか

FIP-010ではPhase 2〜4のError Code、Approval UI、Task ReplayまたはPermission仕様を確定しない。

---

## 31. AI Coding Assistant Constraints

AIは次を独自変更してはならない。

- Error CategoryとAction Mapping
- Same-key / New-key Rule
- Retry-After Rule
- Partial ContentのNon-canonical扱い
- Error UI固定文言
- No Automatic Retry / Reconnect
- Non-logging Rule
- RD-010-001採用後のAPI Payload

未知Errorを成功扱いしたり、便利さを理由に自動Retryしたりしない。不明点がIdempotency Safetyを変える場合は実装を停止して報告する。

---

## 32. Draft Review Checklist

### Safety

- [x] Result UnknownとTerminal Failureを分離している
- [x] Same-keyとNew-key Actionを分離している
- [x] Automatic Retry / Reconnectを禁止している
- [x] Retry-AfterをUser-driven Actionへ接続している

### Data Consistency

- [x] Pendingを本文一致でCanonical化していない
- [x] Partial ContentをCanonical化していない
- [x] Reconciliation失敗時に既存状態を保護している
- [x] Canonical User Message不足を明示している

### UI / Accessibility

- [x] UI-009の固定文言とAction Labelを反映している
- [x] Snackbarだけに依存していない
- [x] Partial FailureのSemantic Labelを定義している
- [x] Announcement重複防止を定義している

### Planning Boundary

- [x] Source Codeを変更していない
- [x] FIP-011 / FIP-012を先行実装していない
- [x] Phase 2〜4の具体仕様を推測していない
- [x] Cross-phase Reviewを必須としている

---

---

## 33. Formal Cross-phase Review Resolution

```text
FIP-010 Document: Approved / Implementation Ready
FIP-010 Draft Planning: Completed
FIP-010 Cross-phase Review: PASS
FIP-010 Implementation: Not Started
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
