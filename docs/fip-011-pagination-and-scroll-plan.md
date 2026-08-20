# Project Alice - FIP-011 Pagination and Scroll Implementation Plan

## 1. Document Information

| Item | Value |
|---|---|
| Document | `fip-011-pagination-and-scroll-plan.md` |
| FIP | FIP-011 Pagination and Scroll |
| Status | Draft |
| Draft Planning | In Progress / User Review Pending |
| Implementation | Not Started |
| Review Required After Phase 2-4 Design | Yes |
| Target | Phase 1 iOS Frontend |
| Last Updated | 2026-08-20 JST |

本ドキュメントは、Phase 1 Conversation ScreenのOlder History Pagination、Scroll Anchor、Follow-latest、`最新へ`およびKeyboard Resize時の位置維持を、後からGitHub Copilot等のAI Coding Assistantへ実装依頼できる粒度で定義するDraft実装計画である。

本Draft作成時点では、ソースコード、Test Code、Dependency、AssetまたはXcode設定を変更しない。Phase 2〜4設計後のCross-phase ReviewおよびPhase 0 Final Design Reviewが完了するまで、FIP-011を実装してはならない。

---

## 2. Purpose / Goal

FIP-011の目的は、長いConversation Historyを安全に追加取得しながら、Userが現在読んでいる位置を守り、最新の会話を見ている場合だけStreamingへ自然に追従することである。

達成目標:

- 最新50件からOlder Pageを上方向に取得する
- Opaque Cursorを変更せず使用する
- 上端200 logical pixelsへのThreshold Crossingで一度だけ取得する
- Older Page追加前後でMessage ID / Offset Anchorを維持する
- 最新側120 logical pixelsをFollow-latest境界とする
- 過去を読んでいるUserをStreamingで最下部へ戻さない
- 未表示更新がある場合だけ`最新へ`を表示する
- Keyboard / Composer Resize時も適切なAnchorを維持する
- Pagination Failureで既存History、Cursor、Draftを失わない
- `reverse: true`や二重縦Scrollへ変更しない

初心者向けに整理すると、古いMessageを上へ追加するとList全体の高さが増える。何もしないと、今読んでいたMessageが突然別の位置へ移動する。Scroll Anchorは「今見ているMessageと、その画面内の位置」を記録し、追加後も同じ場所へ戻す仕組みである。

---

## 3. Scope

### 3.1 In Scope

- `GET /api/v1/conversation/messages?limit=50&cursor=...`
- Older Page Trigger / Single-flight Guard
- Non-scrollable HistoryのManual Load Action
- Loading / Failure / Oldest Boundary Row
- Same-cursor Retry
- `INVALID_CURSOR`時のLatest Page Reconciliation
- Canonical Message Prepend / Deduplication
- Message ID / Viewport Offset Anchor
- Follow-latest / Reading-history State
- Streaming Auto-follow
- `最新へ`Button
- Keyboard / Composer Resize Anchor
- User Scroll Priority
- Scroll / Pagination Accessibility
- Unit / Widget / Integration Test計画

### 3.2 Out of Scope

- Initial History Load
- Send / SSE / RetryのNetwork実装
- Multiple Conversation
- Pull-to-refresh
- Background Pagination
- Pagination Prefetch Queue
- Search / Jump to Date / Jump to Message
- Scroll Positionの端末永続化
- Desktop Shortcut / Mouse固有UX
- Personal Memory、Tool、Agent、Voice

---

## 4. Prerequisites

- FIP-001 / FIP-002 Completed / Approved
- FIP-003〜FIP-010 Draft Planning Completed / User Confirmed
- UI-010 History Pagination Accepted
- UI-011 Keyboard / Scroll Accepted
- RD-010-001 Accepted / Applied
- Phase 2〜4設計後のCross-phase Review必須

---

## 5. Source of Truth

| Concern | Source of Truth |
|---|---|
| Message API、Cursor、Ordering、Limit | `api-design.md` |
| Frontend Pagination / Concurrent Rule | `frontend-design.md` |
| UI Threshold、Anchor、Latest、Keyboard | `frontend-ui-design.md` UI-010 / UI-011 |
| MessagePage / Reducer / Cursor State | `fip-005-application-state-plan.md` |
| Single Scroll / Shell / Controller Ownership | `fip-007-screen-shell-plan.md` |
| Initial latest 50件 / Initial Position | `fip-008-initial-history-plan.md` |
| Streaming / Error更新 | `fip-009-send-and-streaming-plan.md` / `fip-010-retry-and-failure-plan.md` |
| Security / Non-logging | `security-design.md` |
| UI / Pagination Test | `test-design.md` |

---

## 6. Fixed Layout Boundary

- Message Viewportを画面内唯一の主要縦Scroll領域とする
- Header、Alice Core Region、ComposerをMessage Scroll Childへ含めない
- Message順は古いものから新しいものとする
- `reverse: true`を使用しない
- Code / Tableの横Scroll、Composer 5行超過時の内部Scrollだけを局所例外とする
- `ScrollController`は上位Presentation Logicが所有し、Widget Buildごとに再生成しない
- Scroll Positionを端末Storageへ保存しない

---

## 7. Pagination API Contract

```http
GET /api/v1/conversation/messages?limit=50&cursor=<opaque-nextCursor>
Accept: application/json
```

Rules:

- Cursorは直前Response値を完全にそのまま使用する
- Parse、Decode、Trim、Normalize、生成または表示しない
- `hasMore=true`では非Empty `nextCursor`必須
- `hasMore=false`では`nextCursor=null`
- Response Message順はPage内で古い順から新しい順
- 16 MiB Size GuardをFIP-009 Concrete Gatewayで適用する
- Failure時にCursorを変更しない
- Success時だけResponseのPagination Stateへ更新する

---

## 8. Pagination Trigger

### 8.1 Conditions

次をすべて満たした場合だけOlder Page取得Intentを発行する。

1. `hasMore=true`
2. `nextCursor`が存在する
3. Conversation Statusが`ready`
4. Pagination Failureが未解決でない
5. 別のPagination / Send / Recovery Requestが進行中でない
6. Viewportが上端から200 logical pixels以内
7. Threshold外から内へ入ったUser Scrollである

### 8.2 Edge-trigger Rule

`isInsideOlderThreshold`をPresentation Scroll Stateへ保持する。

- Outside -> Insideで一度だけIntentを発行する
- Insideに留まるだけでは追加Intentを発行しない
- Page成功後もInsideなら自動連続取得しない
- Userが一度Outsideへ戻り、再びInsideへ上方向Scrollした場合に再度発行できる
- Programmatic Anchor RestorationをUser Threshold Crossingとして扱わない

### 8.3 Listener Boundary

Scroll ListenerはGatewayを直接呼ばない。`loadOlderRequested` IntentをControllerへ渡し、ControllerがApplication Stateを再確認してRequestを開始する。

---

## 9. Non-scrollable History

Messageが少なくViewport自体をScrollできず、かつ`hasMore=true`の場合、上端へ次を表示する。

```text
[ 以前のメッセージを読み込む ]
```

- Automatic Requestを繰り返さない
- User Tapで一Pageだけ取得する
- Touch Targetは44 x 44 pt以上
- Semantic Labelは`以前のメッセージを読み込む`
- Loading中は無効化する

---

## 10. Pagination Loading and Failure

### 10.1 Loading

既存Historyを維持し、上端へStatus Rowを表示する。

```text
以前のメッセージを読み込んでいます
```

- 小さなIndeterminate Progressを併記する
- Full-screen Loadingへ戻さない
- Composer Draftを保持する
- Pagination完了までSend Buttonを無効化する
- Status RowをMessageとして扱わない

### 10.2 Failure

```text
以前のメッセージを読み込めませんでした
[ 再試行 ]
```

- 既存HistoryとScroll位置を維持する
- 同じCursorでUser-driven Retryする
- Retry中はButtonを無効化する
- Failure解消までAutomatic Triggerを停止する
- Raw CursorやBackend Detailを表示しない

### 10.3 Invalid Cursor

`INVALID_CURSOR`では同じCursorを再送しない。

- Action Labelは`会話を再読み込み`
- CursorなしでLatest PageからReconciliationする
- 成功まで既存Historyを不必要に消去しない
- 本文やTimestampでMessage対応を推測しない

---

## 11. Merge Rule

Older Page成功時:

1. PageをDomain MessageへMappingする
2. Page内順序を古い順から新しい順として維持する
3. Existing Message IDと重複するItemを除外する
4. 同じIDでFieldが異なる場合は`protocolViolation`
5. New Older MessagesをCanonical List先頭へ追加する
6. Pending / Temporary / Failed Presentation Stateを削除しない
7. Responseの`hasMore` / `nextCursor`を採用する

重複除外後に0件でもPagination StateはResponse値へ進める。同じCursorを再取得するLoopを作らない。

---

## 12. Scroll Anchor Model

Presentation専用の`MessageScrollAnchor`を定義する。

| Field | Type | Meaning |
|---|---|---|
| `messageId` | Message ID | 最上部で見えているCanonical Message |
| `viewportOffset` | `double` | Message上端とMessage Viewport上端の相対Offset |

Rules:

- Canonical MessageだけをAnchorにする
- Cursor、Content、TimestampをAnchorへ含めない
- Memory内だけに保持する
- Debug String / LogへMessage IDとOffsetをまとめて出さない
- Anchor取得不能を本文一致等で補完しない

---

## 13. Anchor Capture and Restore

### 13.1 Capture

Older Page Request開始直前に、現在Build済みのMessage Render Objectから最上部で見えているCanonical Messageを選び、Viewport相対Offsetを記録する。

### 13.2 Stable Key Registry

- Canonical Message IDごとに安定したGlobalKey相当を割り当てる
- BuildごとにKeyを再生成しない
- 削除済みMessageのKeyをRegistryから除去する
- Pending / Loading / Error RowへCanonical Message Keyを割り当てない
- Anchor Capture時だけBuild済みContextを走査し、Scroll毎Frameで全Messageを測定しない

### 13.3 Restore

Page Merge後のLayout完了時:

1. 同じMessage IDのRender Objectを取得する
2. Viewport先頭へRevealするScroll Offsetを計算する
3. 保存済み`viewportOffset`を差し引いてTarget Offsetを得る
4. Scroll Extent範囲へClampする
5. `jumpTo`で一度だけ復元する
6. 復元操作をUser Scrollとして扱わない

Pagination RestoreをAnimationにしない。Userが読んでいる位置を視覚的に移動させないためである。

---

## 14. Oldest Boundary

`hasMore=false`かつHistoryが非Emptyで上端に到達した場合、先頭Decorationとして表示する。

```text
会話の始まり
```

- Domain Messageへ追加しない
- TimestampやRoleを付けない
- Empty Conversationでは表示しない
- Messageとして読み上げない
- 追加によってAnchorを移動させない

---

## 15. Follow-latest State

Presentation専用State:

| Field | Type | Meaning |
|---|---|---|
| `mode` | `followingLatest` / `readingHistory` | 自動追従可否 |
| `hasUnseenLatestUpdate` | `bool` | 最新側に未表示更新があるか |
| `programmaticScrollInProgress` | `bool` | User Scrollとの識別用 |
| `isInsideOlderThreshold` | `bool` | Pagination Edge Trigger用 |

初期History表示後は`followingLatest`とする。

### 15.1 Threshold

- 最下部から120 logical pixels以内: `followingLatest`
- 120 logical pixelsを超えてUserが離れた: `readingHistory`
- Userが120以内へ戻った: `followingLatest`へ戻りUnseenをClear
- Programmatic Scrollだけで`readingHistory`へ遷移しない

Threshold判定にはNormal Listの`extentAfter`相当を使用し、`reverse: true`前提の式を使用しない。

---

## 16. Streaming Auto-follow

### 16.1 followingLatest

次のLayout更新後に最下部を維持する。

- Pending User Message追加
- `考えています…`表示
- Batched Temporary Assistant Text更新
- Canonical Completion
- Inline Send Error表示

First Deltaおよび50ms Presentation Batchごとに、次Frameで最大一回だけScrollを更新する。Delta一件ごとに新しいAnimationを開始しない。

Streaming中の高さ追従には`jumpTo(maxScrollExtent)`相当を使用し、Animation競合を避ける。

### 16.2 readingHistory

- Delta / Completionで現在位置を動かさない
- `hasUnseenLatestUpdate=true`へ設定する
- User Drag中にAuto-followを再開しない
- UserがSend Buttonを明示Tapした場合だけ`followingLatest`へ戻し、最新へ移動する

Older Page追加はLatest Updateとして扱わない。

---

## 17. Latest Button

表示条件:

```text
mode == readingHistory
AND hasUnseenLatestUpdate == true
```

Presentation:

```text
[ ↓ 最新へ ]
```

- Composer上部右下
- 44 x 44 pt以上
- Message、Composer、Pagination Rowを隠さない
- BadgeやDelta数を表示しない
- Semantic Labelは`最新のメッセージへ移動`
- Tapで最下部へ移動し`followingLatest`へ戻す
- UnseenをClearする

Reduce Motion無効時は200msの短いStandard Curve Animationを使用する。Reduce Motion有効時は即時移動する。User Drag開始時はAnimationを停止可能にする。

---

## 18. Keyboard and Composer Resize

### 18.1 Fixed Behavior

- Composer TapでKeyboard表示
- Message Area空白TapでKeyboardを閉じる
- 下方向DragでInteractive Dismissを許可
- Returnは改行
- Send後もFocusを維持
- Selectable Text、Code、Link操作をDismiss Tapにしない
- Draft、Validation Error、Counterを保持する

### 18.2 Resize Anchor

Resize直前:

| Position | Anchor |
|---|---|
| Latestから120以内 | Bottom Anchor |
| Latestから120超 | Top Visible Message ID / Offset |

Keyboard InsetsまたはComposer行数変更を検出した時点でCoordinatorがAnchorをCaptureし、Layout更新後に一度復元する。

- followingLatestでは最下部をKeyboard上へ維持する
- readingHistoryではTop Message / Offsetを維持する
- Keyboard HeightをHard Codeしない
- Interactive Dismissの各Frameで新しいAnchorを取り直さない
- Core Resizeだけを理由に最新へ移動しない
- Focusを失わない

---

## 19. User Scroll Priority

- User Drag開始時に実行中Auto-scroll Animationを停止する
- User操作中のDeltaでAuto-followを再開しない
- Programmatic Anchor RestoreとUser Scrollを区別する
- Status Bar Tapで上端へ移動した場合もUser Scrollとして扱う
- Text Selection中に位置を強制変更しない
- iOS標準Scroll Physicsを維持する

---

## 20. Concurrent Operation

- `sending` / `streaming`中にPaginationを開始しない
- `loadingOlder`中にSendを開始しない
- Recovery中にPaginationを開始しない
- Streaming中のThreshold到達をQueueしない
- Screenが`ready`へ戻った後の新しいUser Scrollだけで再評価する
- Loading中のKeyboard ResizeではPagination Anchorを優先する

---

## 21. Accessibility

- Load開始を`以前のメッセージを読み込んでいます`として一度通知
- 完了を`以前のメッセージを読み込みました`として一度通知可能
- VoiceOver Focusを新しいOlder Messageへ強制移動しない
- Anchor MessageのFocus / Reading Positionを維持する
- Retry Buttonに具体的Labelを付ける
- `最新へ`を通常Navigationで到達可能にする
- Streaming DeltaごとにFocusを移動しない
- `会話の始まり`をMessageとして読み上げない
- Reduce MotionをLatest Button Animationへ反映する

---

## 22. Security and Privacy

Log / Analytics / Crash Reportへ次を出力しない。

- Cursor
- Message Content
- Scroll Anchor Message IDとOffsetの組合せ
- Pending / Partial Content
- Raw Response / Error Detail

Scroll Position、Anchor、Follow StateおよびCursorを端末へ永続化しない。

---

## 23. Dependency Decision

FIP-011のために新しいPackageを追加しない。

使用するもの:

- Flutter `ScrollController`
- `ScrollNotification` / `ScrollMetricsNotification`
- `RenderAbstractViewport` / RenderBoxによるAnchor計測
- `WidgetsBinding` Post-frame Callback
- Stable GlobalKey Registry

外部Scroll Position PackageやVisibility DetectorをAIが独自追加してはならない。Framework制約で実装不能と判明した場合は、Dependency追加前にDesign Decisionとして報告する。

---

## 24. Expected File Structure

```text
frontend/lib/conversation/presentation/
├── scrolling/
│   ├── conversation_scroll_coordinator.dart
│   ├── conversation_scroll_state.dart
│   ├── message_scroll_anchor.dart
│   └── message_anchor_registry.dart
├── widgets/
│   ├── message_viewport.dart
│   ├── older_messages_status_row.dart
│   ├── oldest_boundary_label.dart
│   └── latest_messages_button.dart
└── providers/
    └── conversation_scroll_providers.dart

frontend/test/conversation/presentation/
├── conversation_scroll_coordinator_test.dart
├── pagination_trigger_test.dart
├── scroll_anchor_test.dart
└── latest_messages_button_test.dart
```

既存Fileと責務が重複する場合は重複Classを作らず、FIP-005〜FIP-010の正式Pathへ統合する。

---

## 25. Component Responsibilities

| Component | Responsibility | Must Not Do |
|---|---|---|
| Scroll Coordinator | Follow State、Anchor、Programmatic Scroll | Gateway直接呼出し、Content保持 |
| Conversation Controller | Pagination Intent検証、Gateway実行、Reducer dispatch | Pixel計測、BuildContext保持 |
| Anchor Registry | Message IDとStable Keyの対応 | Cursor / Content保持 |
| Message Viewport | Single Scroll、Notification伝達 | Network Request開始 |
| Pagination Row | Loading / Error / Manual Action | Cursor表示、独自Retry |
| Latest Button | Latestへ移動するUser Action | Delta Count表示、State推測 |

---

## 26. Implementation Procedure

Implementation再開後:

1. FIP-005 Pagination ReducerとFIP-009 Gateway Methodを確認する
2. Presentation Scroll State / Coordinatorを実装する
3. Stable Message Anchor Registryを実装する
4. 200px Edge-triggerを実装する
5. ControllerへOlder Page Intentを接続する
6. Loading / Failure / Manual / Oldest Rowを実装する
7. Merge前Capture / Merge後Restoreを実装する
8. 120px Follow Stateを実装する
9. Streaming Auto-followとUnseen更新を接続する
10. `最新へ`Buttonを実装する
11. Keyboard / Composer Resize Anchorを実装する
12. Accessibilityを実装する
13. Unit / Widget / Integration / Manual Testを実行する

---

## 27. Unit Test Plan

### Pagination State

- `hasMore=false`でRequestなし
- CursorなしでRequestなし
- Same Cursorを変更せず送る
- SuccessだけPagination State更新
- FailureでCursor / Messages維持
- Duplicate ID除外
- Same ID / Different Fields拒否
- New Message 0件でもCursorを進める

### Trigger

- 201pxでは発火しない
- 200px以内へOutsideから入ると一回発火
- Inside滞在で連続発火しない
- Outsideへ戻って再進入で再発火
- Programmatic Restoreで発火しない
- Sending / Streaming / Recovery中に発火しない

### Follow State

- 120px以内でfollowingLatest
- 120px超のUser ScrollでreadingHistory
- Programmatic Scrollで誤遷移しない
- readingHistory中のLatest UpdateでUnseen=true
- Older Page追加でUnseenにしない
- Latest TapでFollow / Unseen Clear

---

## 28. Widget Test Plan

- Non-scrollable + hasMoreでManual Button
- Loading Rowが既存Historyを隠さない
- Pagination FailureでRetry Row
- INVALID_CURSORでReload Action
- Oldest Labelの表示条件
- Prepend後のAnchor ID / Offset維持
- Pagination完了時にBottomへ移動しない
- readingHistory中のDeltaで位置維持
- followingLatest中のStreaming成長へ追従
- Latest Button表示 / 非表示 / Semantics
- Keyboard Open / Close時のBottom / Top Anchor
- Dynamic TypeでComposer Height変更時のAnchor
- User DragでAuto-scroll停止
- CursorがUI / Semanticsへ出ない

---

## 29. Manual Test Matrix

| Device Width | Keyboard | Dynamic Type | Required Check |
|---:|---|---|---|
| 375 | Closed / Open | Standard | Pagination、Anchor、Latest |
| 390 | Closed / Open | Standard / Large | Composer Resize、Follow |
| 430 | Closed / Open | Standard | Long Message、Anchor |

追加確認:

- VoiceOver
- Reduce Motion
- Increase Contrast
- Hardware Keyboard
- Interactive Keyboard Dismiss
- Status Bar Tap
- Selectable Text / Code Block操作

---

## 30. Acceptance Criteria

1. Latest 50件からOlder Pageを取得できる
2. CursorをOpaqueのまま同じ値で使用する
3. 200px Threshold Crossingで一Pageだけ取得する
4. Non-scrollable HistoryでManual Loadできる
5. Prepend後もAnchor Message / Offsetを維持する
6. Pagination FailureでHistory / Cursor / Draftを維持する
7. INVALID_CURSORを同じCursorで繰り返さない
8. 120px境界でFollow Stateを切り替える
9. readingHistory中にStreamingで強制移動しない
10. Latest Buttonで追従を再開できる
11. Keyboard / Composer Resizeで適切なAnchorを維持する
12. User ScrollをProgrammatic Scrollより優先する
13. Single Vertical Scroll / non-reverse構造を維持する
14. Cursor / Content / Scroll PositionをLog・永続化しない
15. Scope外Packageや機能を追加しない

---

## 31. Completion Conditions

- Pagination Gateway / Controller接続済み
- Loading / Failure / Manual / Oldest UI実装済み
- Scroll Anchor実装済み
- Follow-latest / Latest Button実装済み
- Keyboard Resize Anchor実装済み
- Unit / Widget / Integration Test成功
- `dart format lib test`成功
- `flutter analyze`成功
- `flutter test`成功
- iOS Simulator Manual Matrix確認済み
- Review指摘解消済み

Draft計画書作成完了はFIP-011 Implementation完了を意味しない。

---

## 32. Dependencies on Other FIPs

| FIP | Relationship |
|---|---|
| FIP-005 | Pagination State / Reducerを利用 |
| FIP-007 | Single Scroll Shell / Controller Ownershipを利用 |
| FIP-008 | Initial Page / Initial Bottom Positionを利用 |
| FIP-009 | Streaming Layout Updateを利用 |
| FIP-010 | Failure / Reconciliationと排他制御 |
| FIP-012 | Golden / E2E / Performance / Final Hardening |

---

## 33. Phase 2-4 Extension Notes

Cross-phase Reviewでは次を確認する。

- Memory関連CardをConversation Messageと同じAnchor対象にしないか
- Tool Result / Approval Card追加時のStable Item ID
- Agent Long-running UpdateがFollow-latestを奪わないか
- Voice Modeの別画面とChat Scroll Stateを混同しないか
- DesktopのPane / Keyboard Shortcutで同じThresholdを固定しないか

FIP-011ではPhase 2〜4のTimeline Item、NavigationまたはDesktop Scroll仕様を確定しない。

---

## 34. AI Coding Assistant Constraints

AIは次を独自変更してはならない。

- 200 / 120 logical pixels Threshold
- 古い順から新しい順
- `reverse: true`禁止
- Cursor Opaque Rule
- Same-cursor Retry
- Anchor Message ID / Offset Rule
- User Scroll Priority
- No Scroll Persistence
- No New Dependency

Scroll Framework上の制約が設計結果を変える場合、代替Packageを追加せず停止して報告する。

---

## 35. Draft Review Checklist

### Pagination

- [x] Trigger条件とEdge Ruleを固定している
- [x] Non-scrollable Manual Actionを定義している
- [x] Same-cursor Retry / INVALID_CURSORを分離している
- [x] Merge / Dedup / Cursor進行を定義している

### Scroll

- [x] Anchor ID / Offsetの取得・復元を定義している
- [x] Follow-latestとReading-historyを分離している
- [x] Latest ButtonとUnseen更新を定義している
- [x] Keyboard / Composer Resizeを定義している

### Safety

- [x] User Scrollを優先している
- [x] Cursor / Content非Loggingを明記している
- [x] Scroll Position非永続化を明記している
- [x] 新規Packageを追加していない

### Planning Boundary

- [x] Source Codeを変更していない
- [x] FIP-012を先行実装していない
- [x] Phase 2〜4の具体仕様を推測していない
- [x] Cross-phase Reviewを必須としている

---

## 36. Current Decision and Next Step

現在の状態:

```text
FIP-011 Document: Draft Created
FIP-011 Draft Planning: In Progress / User Review Pending
FIP-011 Implementation: Not Started
Cross-phase Review: Required
```

本Draftをユーザーが確認・採用した後、Draft Planningを`Completed / User Confirmed`へ更新する。ソースコード実装には進まず、次にFIP-012 Verification and HardeningのDraft実装計画書作成へ進む。
