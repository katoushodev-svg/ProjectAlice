# Project Alice - Frontend UI Design

## 1. Document Information

| Item | Value |
|---|---|
| Document | `frontend-ui-design.md` |
| Status | Approved |
| Target | Phase 1 iOS Smartphone UI / UX Detailed Design |
| Last Updated | 2026-08-17 JST |

本ドキュメントは、Project Alice Phase 1のiOS Applicationについて、User Flow、画面構造、Wireframe、Visual Style、Component、Interaction、画面状態およびAccessibilityを定義する。

合意済みのFrontend Architecture、API ContractまたはState Machineを本ドキュメントで変更しない。UI要件から変更が必要になった場合は、実装前に該当するSource of Truthを更新する。

---

## 2. Related Documents and Ownership

| Document | Ownership |
|---|---|
| `requirements.md` | Phase 1機能・非機能要件 |
| `mvp.md` | Phase 1 Scope |
| `frontend-design.md` | Frontend Architecture、State、API / SSE、Security、Test Boundary |
| `api-design.md` | HTTP / JSON / SSE Contract |
| `security-design.md` | Local Network、Privacy、Logging |
| `test-design.md` | Widget / Integration / Accessibility Test |

本ドキュメントは次の事項のSource of Truthとする。

- Phase 1 User Flow
- Screen / Overlay構成
- Wireframe
- Layout
- Visual Style
- UI Componentと表示内容
- User Interaction
- Loading / Streaming / Error等の画面状態
- iOS固有UI Behavior
- Accessibility UI Requirement

---

## 3. Confirmed Preconditions

次は上位設計で確定済みであり、本UI設計では再検討しない。

| Item | Confirmed Decision |
|---|---|
| Platform | iOS Smartphone |
| Minimum iOS | iOS 15 |
| Application | Flutter `3.47.0` / Dart `3.13.0` |
| Conversation | Phase 1は単一Conversation |
| Main Interaction | Text Chat |
| Response | SSE Streaming表示 |
| History | BackendをSource of Truthとして再取得 |
| Authentication | なし。Local Development限定 |
| Personal Memory / Voice / Tool | Phase 1 Scope外 |

---

## 4. UI Design Goals

Phase 1 UIは、機能を並べた管理画面ではなく、「Aliceと自然に話す場所」として設計する。

### 4.1 Natural Conversation

- App起動後、余計な選択をせず会話へ入れる
- User MessageとAlice Responseを明確に識別できる
- Streaming中の回答を自然に読み進められる
- AIの処理状態を隠さず、技術用語を過剰に表示しない

### 4.2 Alice Identity

- Aliceは形式張りすぎない女性秘書のイメージとする
- Appを開いた瞬間に未来感と適度な高揚感を得られるVisual Identityを持たせる
- 未来的な演出は情報量を増やすためではなく、Aliceの存在感を抽象的に示すために使用する
- 広い余白、落ち着いた暗色背景および必要最小限の操作要素により、知性と親しみやすさを両立する
- ChatGPT等の既存Serviceをそのまま模倣せず、Alice固有のVisual Toneを持たせる
- 呼称「楠瑛」は会話内容として自然な場面だけで使用し、常時UI Labelにはしない

### 4.3 iOS Familiarity

- Safe Area、Keyboard、Scroll、Dynamic Type等のiOS Behaviorへ適合する
- 独自性のために標準操作を壊さない
- Touch Target、ContrastおよびVoiceOverを後付けにしない

### 4.4 Minimum Phase 1 UI

- Phase 1に不要なHome、Conversation一覧、Memory、Tool、Settings画面を先行作成しない
- 一つのConversation Screenへ必要な操作を集約する
- Debug情報やProvider情報を通常UIへ表示しない

---

## 5. UI Design Process

次の順番で設計し、各Decisionを合意後に確定する。

| Order | Design Topic | Output |
|---:|---|---|
| 1 | Screen Structure / User Flow | Screen一覧、起動から会話までのFlow |
| 2 | Information Architecture | Header、Message Area、Composer等の役割 |
| 3 | Low-fidelity Wireframe | 位置・大きさ・Scroll領域 |
| 4 | Screen State | Loading、Empty、Streaming、Failure、Pagination |
| 5 | Interaction | Send、Retry、Keyboard、Scroll、Copy等 |
| 6 | Visual Direction | Color、Typography、Shape、Alice Identity |
| 7 | Component Specification | Message、Composer、Button、Indicator、Error UI |
| 8 | Accessibility / iOS | VoiceOver、Dynamic Type、Safe Area、Contrast |
| 9 | Responsive Verification | 代表的なiPhone画面幅で確認 |
| 10 | UI Design Review | Implementation可能性と文書間整合を確認 |

---

## 6. Confirmed Phase 1 Screen Structure

### 6.1 Decision UI-001

Phase 1のApplication Screenは、`ConversationScreen`一画面だけとする。

Native Launch ScreenはiOS起動要件として存在するが、Userが操作するApplication Screenには数えない。

```text
App Launch
    ↓
ConversationScreen
    ├── Initial History Loading
    ├── Empty Conversation
    ├── Ready
    ├── Sending / Streaming
    ├── Send Failure
    └── Older History Loading
```

### 6.2 Screen Regions

`ConversationScreen`を次の三領域で構成する。

| Region | Purpose |
|---|---|
| Header | Aliceの名前と必要最小限の状態を表示 |
| Conversation Body | Alice Core、Conversation History、Streaming Response、Inline Errorを表示 |
| Composer | Text入力とSend操作を提供 |

### 6.3 Not Included

Phase 1では次を追加しない。

- Home Screen
- Conversation List / Drawer
- Conversation Title編集
- New Conversation Button
- Memory Screen
- Tool / Agent Screen
- Voice Screen
- Profile / Account Screen
- General Settings Screen
- Provider / Model Selection Screen

### 6.4 Reason

Phase 1は単一Conversationを継続利用するため、HomeやConversation選択を挟む理由がない。一画面構成にすると、App起動から会話までの操作を最短にでき、実装・Test・Accessibilityの複雑さも抑えられる。

### 6.5 Trade-off

将来、複数ConversationやSettingsが必要になった場合はNavigationを追加する必要がある。ただしPhase 1から空のNavigation Structureを実装せず、Requirementが発生したPhaseで設計する。

### 6.6 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-001 | Phase 1 Screen Structure | User操作画面は`ConversationScreen`一画面 | Accepted — 2026-08-16 JST |

---

## 7. Confirmed Information Architecture

### 7.1 Decision UI-002

`ConversationScreen`は、上からHeader、Conversation Body、Composerの順に配置する。Conversation Body内部は、UI-014により非ScrollのAlice Core RegionとScrollableなMessage Viewportへ分ける。

```text
┌─────────────────────────────┐
│ Header                      │
│ Alice                       │
├─────────────────────────────┤
│                             │
│ Conversation Body           │
│ Alice Core Region           │
│ Message Viewport            │
│ History / Stream / Error    │
│                             │
├─────────────────────────────┤
│ Composer                    │
│ Text Input        Send      │
└─────────────────────────────┘
```

これはLow-fidelityな領域図であり、色、余白、形状および正確な寸法はまだ規定しない。

### 7.2 Header

Headerは画面上部へ固定し、Phase 1では次だけを表示する。

- Screen Identityとして`Alice`

次はHeaderへ表示しない。

- Back Button
- Conversation Title
- Conversation Menu
- Provider / Model名
- Token、Request IDまたはDebug情報
- 常時表示のOnline / Offline Indicator

生成中やError等の一時的な処理状態は、Headerへ重複表示せずMessage AreaまたはComposer付近で示す。

### 7.3 Conversation Body and Message Viewport

Conversation BodyはHeaderとComposerの間の残り領域を使用する。その内部でMessage Viewportだけを縦方向へScrollできる唯一の主要領域とする。既存節で使用する`Message Area`は、特に断りがない限りこのMessage Viewportを意味する。

- Conversation Historyを古いMessageから新しいMessageの順に表示する
- 最新Messageを画面下側へ配置する
- Streaming中のAlice ResponseをMessage列の一部として表示する
- Message Sendに関係するErrorやRetryを該当Message付近へInline表示する
- Older History LoadingをMessage List上端で表示する

### 7.4 Composer

Composerは画面下部へ配置し、KeyboardおよびBottom Safe Areaに追従する。

- 複数行Text Input
- Send Button
- 入力ValidationのFeedback
- Sending / Streaming中の送信不可状態

Phase 1ではComposerへVoice、Attachment、ToolまたはModel Selection Buttonを追加しない。

### 7.5 Reason

Conversationの閲覧領域を最大化しながら、入力操作を常に同じ場所へ保てる。処理状態を関連するMessage付近へ表示することで、「何に対するErrorか」を理解しやすくし、Headerを技術的な状態表示で複雑にしない。

### 7.6 Trade-off

Headerが非常に簡素になるため、Visual IdentityはTypography、Colorまたは小さなAlice Symbolで補う必要がある。具体表現はVisual Direction設計で決定する。

### 7.7 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-002 | Information Architecture | 固定Header、Alice Coreを含むConversation Body、Scrollable Message Viewport、下部Composer | Revised by UI-014 — Accepted 2026-08-17 JST |

---

## 8. Confirmed Low-fidelity Wireframe

### 8.1 Decision UI-003

Phase 1の基準Wireframeは、幅390 × 高さ844 logical pixelsのPortrait iPhone Canvasで設計する。

このCanvasは実装を特定Deviceへ固定するものではない。Flutterのlogical pixelを基準に位置関係を確認し、最終的には幅375〜430 logical pixelsのiPhoneでLayoutが成立することを検証する。

### 8.2 Vertical Layout

```text
┌──────────────────────────────┐
│ Top Safe Area                │  iOSが管理
├──────────────────────────────┤
│ Header                       │  content height 44
│            Alice             │
├──────────────────────────────┤
│ Alice Core Region            │  Responsive
│          ( Core )            │
├──────────────────────────────┤
│ Message Viewport             │  Expanded / Scroll
│   Older messages             │
│        ⋮                     │
│   Latest messages            │
├──────────────────────────────┤
│ Composer                     │  min 56 / max 136
│ [ Message input       ] [↑]  │
├──────────────────────────────┤
│ Bottom Safe Area             │  iOSが管理
└──────────────────────────────┘
```

### 8.3 Layout Measurements

| Item | Proposed Value | Meaning |
|---|---:|---|
| Reference Canvas Width | 390 | Wireframe作成基準。固定Device Widthではない |
| Reference Canvas Height | 844 | Wireframe作成基準。固定Device Heightではない |
| Horizontal Screen Padding | 16 | Message Area / Composerの基本左右余白 |
| Header Content Height | 44 | Top Safe Areaを含めない |
| Composer Minimum Height | 56 | 1行入力時。Bottom Safe Areaを含めない |
| Composer Maximum Height | 136 | 入力欄が拡張できる上限。Bottom Safe Areaを含めない |
| Composer Text Lines | 1〜5 | 5行を超えた入力は入力欄内部でScroll |
| Major Region Divider | 1 logical pixel以下 | 必要な場合のみ。Visual Styleで最終決定 |

### 8.4 Flexible Region Rule

HeaderとComposerは必要な高さだけを使用し、残りをConversation Bodyへ割り当てる。Conversation Body内ではUI-014のResponsive RuleによってAlice Core Regionを確保し、残りをMessage Viewportへ割り当てる。画面高さごとの固定Message Viewport Heightを定義しない。

```text
Available Height
- Top Safe Area
- Header
- Composer
- Bottom Safe Area
= Conversation Body
```

### 8.5 Keyboard Behavior Boundary

- Keyboard表示時はComposerをKeyboard直上へ移動する
- Conversation Bodyの高さを縮小し、Headerを画面上部へ維持する
- UI-014どおりAlice Core Regionを先に縮小し、Message ViewportとComposer操作を優先する
- KeyboardによってComposerまたは最新Messageを完全に隠さない
- Keyboard表示時もMessage Areaだけを主要Scroll領域とする
- Keyboard Animationへ追従する具体実装はFlutter / iOS標準Behaviorを優先する

### 8.6 Safe Area Rule

- Header ContentをTop Safe Areaへ侵入させない
- Composer操作部をBottom Safe Areaへ侵入させない
- 背景色はSafe Areaを含む画面端まで描画してよい
- Dynamic IslandまたはHome IndicatorのDevice固有値をHard Codeしない

### 8.7 Responsive Rule

次の条件でHorizontal Overflow、操作欠落またはText切断がないことを確認する。

- Width 375 logical pixels
- Width 390 logical pixels
- Width 430 logical pixels
- Portrait Orientation
- Dynamic Typeの標準および拡大設定

Phase 1はPortrait専用とし、iOS ApplicationのSupported Interface OrientationもPortraitへ固定する。Landscape LayoutはPhase 1 Scope外とし、AI Coding Assistantが独自にLandscape対応を追加しない。iPadまたはDesktop対応PhaseでOrientation Requirementを再評価する。

### 8.8 Reason

固定Pixelで全Deviceを設計するのではなく、HeaderとComposerだけに必要なBoundaryを与え、Message Areaを可変にすると、異なるiPhone Height、KeyboardおよびDynamic Typeへ対応しやすい。1〜5行のComposerは短い会話と長めの技術質問を両立できる。

### 8.9 Trade-off

Composerが5行まで拡張すると小型iPhoneではMessage Areaが狭くなる。上限を設け、超過分を入力欄内部Scrollへ切り替えることで会話表示領域を残す。

### 8.10 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-003 | Low-fidelity Wireframe | Responsive Alice Core、Flexible Message Viewport、44 Header、56〜136 Composer、Safe Area対応 | Revised by UI-014 — Accepted 2026-08-17 JST |

---

## 9. Confirmed Message Presentation

### 9.1 Decision UI-004

User MessageとAlice Messageは左右の位置とSurface Colorが異なるBubble Layoutを採用する。

```text
                         ┌──────────────┐
                         │ User Message │
                         └──────────────┘

┌─────────────────────────────┐
│ Alice Response              │
│ 長い説明、List、Codeも表示  │
└─────────────────────────────┘
```

### 9.2 User Message

- 右寄せのMessage Bubbleとして表示する
- Bubble最大幅はMessage Area有効幅の78%とする
- 短いMessageではContent幅に合わせて縮小する
- User LabelまたはUser Avatarを常時表示しない
- Textは選択・コピー可能とする
- 改行と連続する空行を保持する
- User Label、Delivery Check、Read ReceiptおよびTimestampを通常状態で常時表示しない

### 9.3 Alice Message

- 左寄せのMessage Bubbleとして表示する
- Bubble最大幅はMessage Area有効幅の88%とし、長文、List、CodeおよびTableへUser Messageより広い領域を確保する
- Message先頭へRole Labelとして`Alice`を繰り返し表示しない
- Alice Avatar、`A` MonogramまたはCore Thumbnailを各Messageへ繰り返し表示しない
- Textは選択・コピー可能とする
- Timestampを通常状態で常時表示しない
- Provider名、Model名、Token数またはRequest IDを表示しない

### 9.4 Supported Alice Content Presentation

Alice Responseは技術説明にも使用できるよう、安全なMarkdown Subsetを表示対象とする。

| Content | Phase 1 UI |
|---|---|
| Paragraph / Line Break | Render |
| Heading | Render |
| Bullet / Numbered List | Render |
| Bold / Italic | Render |
| Inline Code | Render |
| Fenced Code Block | Render、横方向Scroll、Copy可能 |
| Link | Linkとして識別しText選択・コピーを可能にする。Phase 1ではTapによる外部遷移を行わない |
| Table | Horizontal Scroll可能な簡易表示 |
| Image | Renderしない |
| Raw HTML | 実行せずTextとして安全に扱う |

Markdown RendererはUI-005で採用した`flutter_markdown_plus 1.0.12`を使用する。AI Coding Assistantが独自にPackageまたはVersionを変更してはならない。

### 9.5 Timestamp

- Backendが返したJSTのCanonical `createdAt`はMessage Modelへ保持する
- 通常のConversation表示ではMessageごとのTimestampを常時表示しない
- 日付境界にはDate Separatorを表示し、形式は`yyyy年M月d日`とする
- 将来、Message DetailまたはAccessibility Actionで時刻表示が必要になった場合はCanonical `createdAt`を24時間表記の`HH:mm`へFormatする
- Streaming中のTemporary Assistant MessageにはCanonical Timestampが存在しないものとして扱う

### 9.6 Streaming Message

- Streaming中も通常のAlice Messageと同じ位置へTextを逐次追加する
- Deltaごとに新しいMessage Componentを作らない
- Streaming中であることをMessage末尾の控えめなIndicatorで示す
- Completion時にTemporary ContentをBackendのCanonical Contentへ置換する
- 置換によってScroll位置が不自然にJumpしないようにする

### 9.7 Failure Relationship

AI生成に失敗しても、保存済みUser Messageは通常のCanonical Messageとして残す。Failure表示とRetry操作は、そのUser Message直後のInline Stateとして関連付ける。

Partial Assistant ContentはCanonical Historyとして扱わない。Failure時はUI-009どおり、受信済みContentが存在する場合だけ`途中までの回答`と明示して現在のApplication Session中に限り表示する。再読込後のCanonical Historyへは復元しない。

### 9.8 Message Spacing Boundary

- 同一Roleが連続してもMessageごとの境界を維持する
- User / Aliceの交互出現を前提にLayoutを実装しない
- Message間SpacingはUI-012の`space4`、16 logical pixelsを使用する
- Role、AlignmentおよびReading Orderを色だけで区別しない

### 9.9 Reason

左右のBubbleで会話感を出しながら、Alice Messageへ広い最大幅を与えて長い説明やCodeの可読性を確保する。Role Label、Avatarおよび時刻を各Messageへ繰り返さないことで、採用Visualの広い余白と低い情報密度を維持する。

### 9.10 Trade-off

Timestampを常時表示しないため、画面だけで個々の送受信時刻を即座に確認できない。一方、Canonical `createdAt`は保持し、日付境界を表示することで履歴の順序性を失わず、必要時に時刻表示を追加できる。

### 9.11 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-004 | Message Presentation | Userは右Bubble、Aliceは左の広幅Bubble、Safe Markdown、Timestampは常時非表示 | Revised by UI-012 — Accepted 2026-08-17 JST |

---

## 10. Confirmed Markdown Renderer Dependency

### 10.1 Decision UI-005

Phase 1のAlice Message Rendererとして`flutter_markdown_plus 1.0.12`を採用する。

```yaml
dependencies:
  flutter_markdown_plus: 1.0.12
```

`pubspec.yaml`ではExact Versionを指定し、解決されたTransitive Dependencyを含む`pubspec.lock`をCommitする。

### 10.2 Candidate Comparison

| Item | `flutter_markdown_plus 1.0.12` | `markdown_widget 2.3.2+8` |
|---|---|---|
| Maintenance | Original `flutter_markdown`の継続Package。直近Releaseあり | 機能豊富だが最終公開から期間が長い |
| Main Capability | GFM、Table、Link、Code、Selection | GFM、TOC、Code Highlight、Link等 |
| Direct Dependencies | `flutter`、`markdown`、`meta`、`path` | Highlight、URL、Visibility、Scroll関連を含む |
| Inline HTML | 非対応 | Custom Node等の拡張範囲が広い |
| Phase 1 Fit | 必要機能を少ないDependencyで満たす | Phase 1にはTOC等が過剰 |

Project Aliceは必要最小限のDependencyを重視するため、Phase 1では`flutter_markdown_plus`を推奨する。

### 10.3 Rendering Mode

Message Area自体がScrollを所有するため、Alice MessageではPackage内の独立Scroll Viewを作らない。

- `MarkdownBody`または同等のNon-scrolling Modeを使用する
- `selectable: true`を有効にする
- GitHub Flavored Markdownを使用する
- ThemeはAliceのDesign Tokenから`MarkdownStyleSheet`へMappingする
- Markdown RendererをDomain / Application Layerへ漏らさない
- Package TypeはPresentation Layer内へ閉じ込める

### 10.4 Security Configuration

- Image Syntaxを検出してもNetwork / File / Asset Imageを読み込まない
- Custom Image Builderは非表示または安全なPlaceholderを返す
- Raw HTMLをWebView等で実行しない
- Linkを自動的に開かない
- Link Schemeを検証し、Phase 1では`https`だけを外部遷移候補とする
- `javascript:`、`data:`、`file:`および不明なSchemeを拒否する
- Phase 1では外部遷移機能と`url_launcher`等のDependencyを追加しない。Linkは選択・コピー可能なTextとして表示する
- Markdown ContentをLogへ出力しない
- Renderer ErrorでApplication全体をCrashさせず、安全なPlain Text Fallbackを表示する

### 10.5 Content Behavior

| Content | Renderer Behavior |
|---|---|
| Paragraph / List / Emphasis | GFMとして表示 |
| Inline Code | Monospace Style |
| Fenced Code | 横Scroll可能なCode Block。Syntax HighlightはPhase 1必須としない |
| Table | 横Scroll可能にする |
| Link | 見た目だけ識別し、Tap BehaviorはInteraction設計に従う |
| Image | 読み込まない |
| Raw HTML | 実行しない。安全なText Fallbackを優先 |
| Invalid Markdown | 可能な範囲でTextとして表示し、Conversationを失わない |

### 10.6 Streaming Performance

Streaming中はDelta受信ごとにMessage List全体を再構築しない。Temporary Alice Messageだけを更新する。

Markdownが途中の不完全なSyntaxでもCrashしてはならない。Streaming中は安全に部分表示し、`assistant.completed`後にCanonical Contentを最終Renderする。

大量の細かなDeltaによる過剰な再描画を避けるため、最初の表示可能なDeltaは直ちに反映し、その後は50 msをBaselineとして受信Deltaを順序どおり結合してUIへ反映する。`assistant.completed`またはTerminal Event受信時は待機中Deltaを直ちにFlushする。50 msはPresentation Performance Parameterであり、Profile結果により33〜100 msの範囲で調整できるが、受信Textを欠落・並べ替えしてはならず、変更時はStreaming Widget Testと体感Latency Reviewを更新する。

### 10.7 Test Requirements

- UI-004で定義したMarkdown SubsetをFixtureで検証する
- Markdown ImageがNetwork Accessを発生させない
- 危険または不明なLink Schemeを開かない
- Invalid / Incomplete MarkdownでCrashしない
- Streaming中とCompletion後でContentが欠落しない
- Selectable TextとCode Copyが利用できる
- Large MessageでMain Threadを長時間Blockしない
- Unknown Markdown Extensionを安全に扱う

### 10.8 Trade-off

`flutter_markdown_plus`はSyntax Highlightを標準の主要目的にしていないため、Phase 1のCode BlockはMonospace表示を基本とする。色付きSyntax Highlightが実際に必要になった場合は、追加Dependencyと性能・Securityを評価して別Decisionとする。

### 10.9 Implementation-time Reconfirmation

実装開始時に次だけを再確認する。

- Flutter `3.47.0` / Dart `3.13.0`とのBuild Compatibility
- 既知のSecurity Issueまたは重大Regression
- LicenseとTransitive Dependency

問題がなければ`1.0.12`を使用する。Version変更が必要な場合は、AIが独自変更せず`frontend-design.md`と本Documentを先に更新する。

### 10.10 Official Reference

- <https://pub.dev/packages/flutter_markdown_plus>
- <https://pub.dev/packages/flutter_markdown_plus/versions>

### 10.11 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-005 | Markdown Renderer | `flutter_markdown_plus 1.0.12`、Non-scrolling、安全なImage / Link制御 | Accepted — 2026-08-16 JST |

---

## 11. Confirmed Composer Structure

### 11.1 Decision UI-006

Phase 1のComposerは、Message入力欄と送信Buttonだけで構成する。Attachment、Voice、Model選択およびTool選択は配置しない。

### 11.2 Layout

- ComposerはScreen下部のSafe Area内に固定する
- 複数行Text Fieldを左側、円形の送信Buttonを右側に配置する
- Text Fieldは1行から最大5行まで自動的に拡張し、それを超えた内容はField内でScrollする
- Software Keyboard表示中もComposer全体をKeyboard上端に追従させる
- App起動時は自動Focusせず、HistoryをKeyboardに隠さない

### 11.3 Input Contract

| Item | Decision |
|---|---|
| Placeholder | `メッセージを入力` |
| Minimum | 空白以外を1 Unicode Code Point以上含む |
| Maximum | 10,000 Unicode Code Point |
| Newline | Return Keyは改行として扱う |
| Send Trigger | Phase 1では送信ButtonのTapだけを送信確定操作とする |
| Input Preservation | User入力をTrim、Unicode正規化または自動切り詰めして送信しない |
| Draft Storage | Memory内だけに保持し、端末Storageへ永続化しない |

空白のみかのValidationではTrim相当の判定を行ってよいが、判定後にBackendへ渡すContentはUserが入力した元の文字列を保持する。この区別により、API Contractと一致させながらCodeや意図的な空白を壊さない。

文字数はUTF-8 Byte数、DartのUTF-16 Code Unit数または見た目上のGrapheme Cluster数ではなく、API Designと同じUnicode Code Pointで数える。Paste等で上限を超えた場合も無断で切り詰めず、入力を保持したまま送信を無効にして理由を表示する。

### 11.4 Character Counter and Validation

- 通常時は文字数Counterを常時表示しない
- 9,000 Code Point以上で`9,000 / 10,000`形式のCounterを表示する
- 10,000 Code Pointを超えた場合はError Styleと説明Textを表示し、送信を無効にする
- 空白のみの入力では、入力中に即座にErrorを出さず、送信Buttonを無効にする
- Counterを1文字ごとにScreen Readerへ読み上げず、上限接近または超過時だけ理解可能な通知を行う

### 11.5 Send Button State

送信Buttonは最低44 x 44 pt相当のTouch Targetを確保し、Semantic Labelを`送信`とする。

次のすべてを満たす場合だけ有効にする。

1. Screenが送信可能なReady Stateである
2. 入力が空白のみではない
3. 入力が10,000 Unicode Code Point以下である
4. 別のMessageを送信中またはStreaming中ではない

無効状態は色だけに依存せず、Tapを受け付けない状態とAccessibility Semanticsの両方で表現する。

### 11.6 Send Interaction

1. Userが有効な送信ButtonをTapする
2. Frontendはその時点の元のContentをCaptureし、Logical Send用UUID v4を一度だけ生成する
3. ComposerのContentをPending Sendへ移し、User Messageを即時表示する
4. Text FieldをClearし、Keyboard Focusは維持する
5. 送信Buttonを無効にしてSSE開始を待つ
6. 同じLogical SendのTerminal Resultまで多重送信を許可しない

送信処理を開始できないClient-side Errorが発生した場合は、Capture済みContentをComposerへ復元してUser入力を失わない。Request結果が不明またはBackend処理中の場合は、ContentをPending Sendとして保持し、Error / Retry設計に従う。

### 11.7 Keyboard and Draft Behavior

- Return Keyは送信ではなく改行を挿入する
- Keyboardを閉じてもDraftを消去しない
- Screen外Tap等によるKeyboard Closeは許可する
- Phase 1ではHardware Keyboard Shortcutによる送信を必須にしない
- App Process終了後に未送信Draftを復元しない

送信をButton Tapへ限定することで、長文、Codeおよび複数行の技術質問を入力するときの誤送信を防ぐ。

### 11.8 Accessibility and Test Requirements

- Text Scale拡大時も入力欄と送信Buttonの主要操作を欠落させない
- VoiceOverで入力欄、送信Button、無効状態および文字数超過理由を理解できる
- 日本語、Emoji、結合文字、改行およびCodeを含む入力でCode Point判定をTestする
- 空文字、空白のみ、9,999、10,000および10,001 Code Pointを境界値Testする
- Pasteによる超過時にContentが切り詰められないことをTestする
- 送信中の連続Tapで2つ目のRequestが開始されないことをTestする
- Client-side開始失敗時にDraftが復元されることをTestする

### 11.9 Trade-off

Phase 1では送信中に次のMessageをQueueできないため、連続投稿の操作性は制限される。一方で、BackendのSingle Active Stream方針と整合し、Message順序、IdempotencyおよびFailure時の状態を単純に保てる。

未送信Draftを端末へ永続化しないためApp終了時には失われるが、Conversation Contentの端末残留を避け、Phase 1 Security Boundaryを小さくできる。

### 11.10 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-006 | Composer Structure | 1〜5行入力、Button送信、10,000 Code Point、単一Active Send | Accepted — 2026-08-16 JST |

---

## 12. Confirmed Empty State

### 12.1 Decision UI-007

Canonical Messageが1件も存在しないとき、Message AreaにAlice Coreと簡潔なWelcome Messageを表示する。

```text
何から始めましょうか？

相談したいことや、聞きたいことを入力してください。
```

普段のConversationでUser呼称を省略する人格方針に合わせ、Empty StateでもUser名を固定表示しない。Headerですでに`Alice`を示すため、本文で`Aliceです`という自己紹介を重ねず、Alice Coreの下へ`Alice`を重複表示しない。

### 12.2 Display Condition

Empty Stateを表示するのは、次のすべてを満たす場合だけとする。

1. Initial History Loadが正常に完了している
2. Canonical Messageが0件である
3. Pending User MessageまたはTemporary Alice Messageが存在しない
4. Initial Load Errorを表示していない

ConversationがBackend上で未作成の場合と、作成済みだがMessageが0件の場合は、Phase 1 UIでは同じEmpty Stateとして扱う。

### 12.3 State Boundary

| State | Message Area Behavior |
|---|---|
| Initial Loading | Empty Stateを表示せずLoading表示を使用する |
| Initial Load Failure | Empty Stateを表示せずError / Retry表示を使用する |
| Ready + 0 Messages | Empty Stateを表示する |
| Pending Send / Sending | Empty Stateを消し、Pending User Messageを表示する |
| Streaming | Temporary Alice Messageを表示する |
| Ready + History | Canonical Message Historyを表示する |

Empty StateはPresentation専用の案内であり、Assistant Message、Domain ModelまたはConversation Historyとして扱わない。Message ID、Timestamp、Roleを付与せず、Backendへ保存しない。

### 12.4 Layout

- Message Areaの利用可能領域内で中央寄せにする
- Alice CoreをWelcome Textより上へ配置し、一つのEmpty State Groupとして扱う
- Alice Coreは画面高とDynamic Typeに応じて縮小可能とし、ComposerまたはWelcome Textを押し出さない
- Composerとの間に十分な余白を確保し、入力操作を妨げない
- Text Scale拡大時は固定位置へ押し込まず、必要に応じてMessage Area内で自然にLayoutする
- Keyboard表示時は完全な中央位置を維持する必要はなく、Composerと重ならないことを優先する
- Empty State自体をTap Targetにしない

Alice Coreの描画に失敗した場合またはReduce Motion時も、Static表示またはWelcome TextだけでEmpty Stateが成立する構造とする。

### 12.5 Content Rules

- Phase 1で未実装のMemory、Tool、Voice、PC操作等を案内しない
- Provider名、Model名または内部Architectureを表示しない
- Suggestion Chip、Prompt TemplateおよびOnboarding Carouselを追加しない
- Marketing的な長文説明を表示しない
- 日時や時間帯に依存する挨拶を使用しない

時間帯で挨拶を変えないため、端末時刻、TimezoneまたはLocaleに依存せず、Testが安定する。将来Suggestion機能がRequirementになった場合は、実際に利用可能なCapabilityだけを表示する別Decisionとして設計する。

### 12.6 Transition

Userが最初のMessageを送信した時点でEmpty Stateを即座に消し、同じ領域へPending User Messageを表示する。Animationは必須とせず、状態切替によってMessageと案内が同時表示されないことを優先する。

送信開始前にText Fieldへ入力しているだけの状態ではEmpty Stateを維持する。入力内容はComposerが所有し、Empty Stateの表示条件へ影響させない。

### 12.7 Accessibility and Test Requirements

- VoiceOverではTitle、Description、Composerの順に自然に読み取れる
- TitleとDescriptionを一つの過剰に長いLabelへ結合しない
- Alice Coreが純粋な装飾として表示される場合はSemantics Treeから除外する
- Initial Loading中にEmpty Stateが一瞬表示されないことをTestする
- Initial Load FailureとEmpty Stateが同時表示されないことをTestする
- 0件Historyで表示され、1件以上で表示されないことをTestする
- 最初の送信時にPending User Messageへ置き換わることをTestする
- Empty StateがMessage ModelまたはPersistence Requestを生成しないことをTestする

### 12.8 Trade-off

Suggestion Chipを置かないため、初回Userへ具体的な質問例を提示する支援は少ない。一方で、Phase 1に存在しないCapabilityを誤認させず、画面を簡潔に保ち、Aliceとの会話をUser自身の言葉で開始できる。

### 12.9 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-007 | Empty State | Alice Core、短いWelcome Text、History非保存、Suggestionなし | Revised by UI-012 — Accepted 2026-08-17 JST |

---

## 13. Confirmed Loading and Streaming Presentation

### 13.1 Decision UI-008

Phase 1では、待機状態を一つのLoading表示にまとめず、Userが待っている対象に応じて次の3種類を表示する。

| UI State | Userが待っている対象 | Main Presentation |
|---|---|---|
| `initialLoading` | 保存済みConversation History | Message Area中央のProgress Indicatorと`会話を読み込んでいます` |
| `sending` | Request開始から最初の`assistant.delta` | Pending User MessageとAlice側の`考えています…` |
| `streaming` | Alice Responseの残り | 受信済みAlice Textと生成中Indicator |

この区別により、「過去の会話を読み込んでいるのか」「送信できたのか」「Aliceが回答を生成中なのか」を画面から判断できる。

### 13.2 Initial History Loading

- Headerは通常どおり表示する
- Message Area中央にiOSで自然なIndeterminate Progress Indicatorを表示する
- Indicatorの近くに`会話を読み込んでいます`と表示する
- Composerは表示するが入力・送信を無効にする
- Empty State、Message HistoryおよびInitial Load Errorを同時表示しない
- Skeleton Messageは使用しない

非常に短い読込でIndicatorが点滅して見えることを避けるため、Progress IndicatorはInitial Load開始から300 ms経過しても完了していない場合に表示する。300 ms未満で完了した場合は直接ReadyまたはEmpty Stateへ遷移する。

300 msは通信Timeoutではなく、表示のちらつきを抑えるためのUI Delayである。API Request自体を中断したり、遅延させたりしてはならない。

### 13.3 Sending Before First Delta

Userが送信ButtonをTapした直後は、Pending User MessageをMessage Areaへ即時表示する。その下にAlice Messageと同じ左側配置で生成開始表示を追加する。

```text
考えています…
```

- `stream.started`受信前でもPending Send開始後に表示してよい
- `stream.started`受信後も最初の`assistant.delta`まで同じ表示を維持する
- AliceのCanonical Messageとして扱わない
- Message ID、TimestampまたはCopy Actionを表示しない
- Provider名、Model名、Token数、Request IDまたは処理段階を表示しない
- Phase 1ではCancel Buttonを追加しない

`考えています…`は「Requestが成功した」という確定表示ではなく、送信処理が進行中であることを示す。最終結果はTerminal EventまたはErrorによって確定する。

### 13.4 Streaming After First Delta

最初の`assistant.delta`を受信した時点で`考えています…`をTemporary Alice Messageへ置き換える。

- 受信済みDeltaを到着順に連結して表示する
- Streaming Textへ人工的な1文字ずつのTypewriter Animationを追加しない
- Deltaに含まれない`...`、Cursor文字または仮TextをMessage Contentへ追加しない
- Message末尾付近にContentとは独立した小さな生成中Indicatorを表示する
- Temporary MessageにはCanonical Timestampを表示しない
- Safe Markdown Rendererを使用し、不完全なMarkdownでもCrashさせない
- Text Selectionを可能にする

生成中IndicatorはAlice Contentへ混入させず、Semantics上も本文と分離する。Motion削減設定が有効な場合は、激しいPulseまたは点滅を行わずStatic Indicatorへ切り替える。

### 13.5 Completion Transition

`assistant.completed`受信時は、一つのState Transitionで次を行う。

1. Temporary Alice Messageを削除する
2. Backendが返したCanonical User / Alice Messagesへ置き換える
3. 生成中Indicatorを削除する
4. Canonical JST `createdAt`をStateへ保持する。通常画面へは常時表示しない
5. Composerを再び送信可能なStateへ戻す

Temporary ContentとCanonical Alice Messageを同時表示せず、Messageが二重に見えるFrameを作らない。Canonical ContentがStreaming中の表示と異なる場合は、Backend ResponseをSource of TruthとしてCanonical Contentを表示する。

### 13.6 Unknown Duration and Progress

AI Responseの完了時刻や正確な進捗率をFrontendは把握できないため、次を表示しない。

- Percentage Progress
- 残り秒数
- Token生成数
- 固定の完了予定時刻
- 実際の処理と連動しないProgress Bar

一定時間経過後のTimeoutやFailureは、`api-design.md`、`ai-design.md`および次のError / Retry設計に従う。UI-008では独自のTimeout値を追加しない。

### 13.7 Scroll Boundary

生成開始表示とTemporary Alice Messageは、通常のMessage List内で最新Messageとして扱う。ただし、自動Scrollを継続する条件やUserが過去Messageを読んでいる場合の挙動は、後続のKeyboard / Scroll詳細Behaviorで決定する。

UI-008だけを根拠に、Streaming中に毎Deltaで強制的に最下部へScrollしてはならない。

### 13.8 Accessibility

- Initial Loading開始時は`会話を読み込んでいます`を一度だけ通知する
- Sending開始時は`Aliceが回答を作成中です`を一度だけ通知する
- DeltaごとにScreen Readerへ全文または差分を読み上げない
- Completion時にAliceの回答が完了したことを一度通知する
- Progressを色やAnimationだけで表現せず、理解可能なTextまたはSemantic Labelを持たせる
- iOSのReduce Motion設定を尊重する

### 13.9 Test Requirements

- 300 ms未満のInitial LoadでProgress Indicatorが表示されない
- 300 ms以上のInitial LoadでIndicatorとLabelが表示される
- Initial LoadingとEmpty / Error / Historyが同時表示されない
- Pending Send直後にUser Messageと`考えています…`が表示される
- 最初のDeltaで生成開始表示がTemporary Alice Messageへ置き換わる
- 複数Deltaが到着順どおり欠落なく連結される
- Streaming中にCanonical Timestampが存在せず、Completion後もMessageごとのTimestampが常時表示されない
- Completion時にTemporaryとCanonical Messageが重複しない
- Deltaごとの過剰なAccessibility Announcementが発生しない
- Reduce Motion時も生成中であることを理解できる
- Incomplete Markdown、Emojiおよび長文StreamingでCrashしない

### 13.10 Trade-off

最初のDelta到着前に`考えています…`を表示するため、Network接続中とAI生成中を細かく分けては表示しない。一方、Provider固有の内部状態を見せず、Userに必要な「送信処理が継続中」という情報を簡潔に伝えられる。

Cancel機能を設けないため長い生成をUIから中止できないが、Phase 1 BackendにCancel Contractを先行追加せず、実際に必要になった時点でAPIとAI処理を含めて設計できる。

### 13.11 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-008 | Loading / Streaming | 3状態を分離、`考えています…`、Temporary Text、偽Progressなし | Accepted — 2026-08-16 JST |

---

## 14. Confirmed Error and Retry Presentation

### 14.1 Decision UI-009

Phase 1のError UIは、Errorが発生した場所と安全に実行できるRecovery Actionに応じて表示を分ける。

| Error Scope | Presentation | Primary Action |
|---|---|---|
| Initial History Load | Message Area全体のError State | `再読み込み` |
| Composer Validation | 入力欄直下のInline Error | 入力内容を修正 |
| Pre-stream Send | Pending User Message直下のInline Error | Error Codeに応じたAction |
| Result Unknown | Pending User Message直下の確認State | `結果を確認` |
| Terminal `stream.failed` | 対象User Message直後のFailed Response | 明示的な新規送信または終了 |
| Protocol / Decode Error | Message Area下部の同期Error | `会話を再読み込み` |

Actionを伴うErrorは自動消去するSnackbarだけで表示せず、Userが内容を読みActionを選択するまでMessage AreaまたはComposer付近へ残す。

### 14.2 Initial Load Failure

Initial History Loadに失敗した場合、Empty Stateまたは古いHistoryを正常状態として表示せず、Message Area中央に次を表示する。

```text
会話を読み込めませんでした
接続を確認して、もう一度お試しください。

[ 再読み込み ]
```

- HeaderとComposerの外観は維持する
- Composerの入力・送信は再読込成功まで無効にする
- `再読み込み`はConversation基本情報と最新Message PageのInitial Load Flow全体を再実行する
- Retry中はButtonを無効化し、二重Requestを防ぐ
- Retry失敗時も同じ画面内で再試行可能にする
- `CONVERSATION_NOT_FOUND`はError表示せず、正常な未作成ConversationとしてEmpty Stateへ遷移する

### 14.3 Composer Validation Error

送信前に修正できるErrorはComposer直下へ具体的に表示する。

| Condition | UI Message |
|---|---|
| Empty / Whitespace only | `メッセージを入力してください。` |
| More than 10,000 Code Points | `メッセージは10,000文字以内で入力してください。` |
| Request Body too large | `メッセージのデータ量が大きすぎます。内容を短くしてください。` |

通常は無効な送信Buttonによって送信を防ぐ。Paste等で上限を超えた場合やBackendから`VALIDATION_ERROR` / `PAYLOAD_TOO_LARGE`を受信した場合は、元のContentをComposerへ保持してErrorを表示する。

BackendのField Error `message`またはProblem Details `detail`を条件分岐に使用せず、`code`とField-level Codeを既知のUI文言へMappingする。未知のValidation Codeは`入力内容を確認してください。`へFallbackする。

### 14.4 Safe Retry Categories

同じ見た目の`再試行`Buttonですべてを処理せず、Actionの意味をLabelで区別する。

| API / Client Condition | UI Message | Action | Idempotency Behavior |
|---|---|---|---|
| Network切断・Terminal Event未受信 | `送信結果を確認できませんでした。` | `結果を確認` | 同じContent・同じKey |
| `REQUEST_IN_PROGRESS` | `メッセージはまだ処理中です。` | `結果を確認` | `Retry-After`経過後に同じKey |
| `CONVERSATION_BUSY` | `Aliceは別の回答を作成中です。完了後にもう一度お試しください。` | `もう一度試す` | 同じLogical Send・同じKey |
| Pre-stream `SERVICE_UNAVAILABLE` | `Aliceを一時的に利用できません。` | `もう一度試す` | 同じLogical Send・同じKey |
| `IDEMPOTENCY_KEY_CONFLICT` | `送信状態に矛盾が見つかりました。会話を再読み込みしてください。` | `会話を再読み込み` | 再送しない |
| Protocol / Decode Error | `表示を同期できませんでした。` | `会話を再読み込み` | 再送しない |

`REQUEST_IN_PROGRESS`では`Retry-After`が経過するまでActionを無効にし、残り秒数を大きなCountdownとして表示する必要はない。Phase 1では汎用HTTP Clientの自動POST Retryを使用せず、Userの明示操作で結果確認を行う。

`INTERNAL_ERROR`等で処理開始有無が判断できない場合は、新しいKeyで再送せずResult Unknownとして同じKeyによる結果確認を優先する。

### 14.5 Terminal Stream Failure

`stream.failed`はそのLogical SendのTerminal Resultである。同じKeyを再送しても保存済みFailureがReplayされるため、`結果を確認`Actionは表示しない。

| SSE Code | UI Message |
|---|---|
| `RESPONSE_GENERATION_FAILED` | `Aliceの回答を生成できませんでした。` |
| `RESPONSE_TIMEOUT` | `Aliceの回答がタイムアウトしました。` |
| `MESSAGE_SAVE_FAILED` | `回答を会話履歴へ保存できませんでした。` |
| `REQUEST_INTERRUPTED` | `処理を最後まで確認できませんでした。` |
| `INTERNAL_ERROR` / Unknown | `予期しない問題が発生しました。` |

Database DesignによりUser MessageはConversation Historyへ残り、Partial Assistant Contentは保存されない。Frontendがすでに受信したPartial Contentは次のように扱う。

- Partial Contentが空ならFailed Responseだけを表示する
- Partial Contentが存在する場合は`途中までの回答`と明示して選択可能なまま表示する
- Canonical Alice Message、Timestampまたは正常完了として扱わない
- App再起動やHistory再取得後に復元されるとは案内しない

Failed Responseには`同じ内容でもう一度送る`をSecondary Actionとして表示できる。このActionは同じKeyのRetryではなく、Userが明示した新しいLogical Sendとして新しいUUIDを生成する。元のUser Messageは履歴へ残るため、Action Labelを単なる`再試行`にして重複しない処理だと誤解させてはならない。

### 14.6 Error Component Structure

Inline Error Componentは原則として次の順に構成する。

1. Errorを示すIcon
2. User向けTitleまたは短い説明
3. 必要な場合だけ補足説明
4. Primary Recovery Action
5. 必要な場合だけSecondary Action

- Errorを赤色だけで表現しない
- Stack Trace、Provider名、Model名、SDK Error、AWS情報またはInternal Endpointを表示しない
- `requestId`とError Codeは内部Traceへ保持するが、通常画面へ常時表示しない
- Error発生時にConversation ContentやIdempotency KeyをLogへ出力しない
- 同時に複数のPrimary Actionを配置しない

### 14.7 State and Content Preservation

- Result UnknownではPending Contentと同じIdempotency KeyをMemory内に保持する
- Retry / Result Check開始中は該当Actionを無効にして多重実行を防ぐ
- Initial Load RetryでComposer Draftを消去しない
- History再取得が必要なErrorでも、再取得成功まで現在表示中のCanonical Historyを不必要に消去しない
- Canonical History取得後はMessage IDで重複を除外し、Temporary / Failed Stateを整合させる
- Error解消前に別の新規Message Sendを開始させない

### 14.8 Accessibility

- Error発生時はTitleを一度だけLive Announcementする
- Error説明、対象Message、Recovery Actionの関係をSemantics順で理解できるようにする
- Retry Buttonへ`再読み込み`、`結果を確認`等の具体的なSemantic Labelを設定する
- Retry中の無効状態をVoiceOverへ伝える
- 同一Errorの再描画ごとにAnnouncementを繰り返さない
- IconだけでError種別を表現しない

### 14.9 Test Requirements

- Initial Load FailureでEmpty StateとHistoryを正常状態として同時表示しない
- `CONVERSATION_NOT_FOUND`がErrorではなくEmpty Stateになる
- Validation ErrorでOriginal Contentを変更・消去しない
- Result Unknownの`結果を確認`が同じContent・同じIdempotency Keyを使用する
- `REQUEST_IN_PROGRESS`で`Retry-After`前にActionを実行できない
- `IDEMPOTENCY_KEY_CONFLICT`でPOSTを再送せずHistoryを再取得する
- `stream.failed`後に同じKeyをRetry Actionとして使用しない
- `同じ内容でもう一度送る`がUser操作後に新しいKeyを一度だけ生成する
- Partial Assistant ContentがCanonical Messageとして扱われない
- Retry連打で複数Requestが開始されない
- 未知のHTTP / SSE Error Codeで安全なGeneric MessageへFallbackする
- Error UIへSecret、Stack TraceまたはProvider固有情報を表示しない
- VoiceOver Announcementが同一Errorで重複しない

### 14.10 Trade-off

ErrorごとにAction Labelを分けるため、単一の`再試行`Buttonより実装とTestは増える。一方、同じKeyで結果確認すべき処理と、新しい送信としてやり直す処理をUserと実装者の双方が区別でき、重複Messageや重複AI呼び出しを防止できる。

Partial Responseを一時的に残すことでUserは受信済み情報を確認できるが、Persistenceされない。そのため`途中までの回答`と明記し、Canonical Historyと同じ外観にしてはならない。

### 14.11 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-009 | Error / Retry | Scope別Error、Same-key結果確認、Terminal Failureの新規送信を分離 | Accepted — 2026-08-16 JST |

---

## 15. Confirmed History Pagination Interaction

### 15.1 Decision UI-010

Phase 1は最新50件をInitial Pageとして表示し、UserがMessage Area上端へ近づいたときにOlder Pageを取得する上方向Paginationを採用する。

```text
Older Messages
      ↑
上端へScroll
      ↑
Latest Messages
```

Conversation Historyの表示順は常に古いMessageから新しいMessageとし、追加取得したOlder Pageを既存Listの先頭へ結合する。

### 15.2 Load Trigger

次のすべてを満たした場合にOlder Page取得を開始する。

1. `hasMore = true`
2. `nextCursor`が存在する
3. Screen Stateが`ready`である
4. 別のPagination Requestを実行中ではない
5. Message Areaが上端から200 logical pixels以内へ入った

200 logical pixelsはAPI ContractではなくFrontend UI Constantである。実装時に異なる端末SizeでTestし、変更する場合は本Documentを更新する。

Scroll位置がThreshold外から内へ入った時だけTriggerする。取得完了後も同じ位置に留まっていることだけを理由に、次Pageを連続取得してはならない。次の自動取得にはUserによる新しい上方向Scrollを必要とする。

Message数が少なくList自体をScrollできず、かつ`hasMore = true`の場合は、上端に`以前のメッセージを読み込む`Buttonを表示して手動取得できるようにする。

### 15.3 Loading Presentation

Older Page取得中は、既存Historyを維持したままMessage Area上端に次を表示する。

```text
以前のメッセージを読み込んでいます
```

- 小さなIndeterminate Progress Indicatorを併記する
- Message Bubbleと誤認しない中央配置のStatus Rowとする
- Message List全体をSkeletonまたはFull-screen Loadingへ戻さない
- Loading RowへMessage ID、RoleまたはTimestampを付与しない
- 同じCursorに対する二重Requestを開始しない

Composerへの入力は継続できるが、既存のConcurrent Operation方針に従い、Pagination完了まで送信Buttonを無効にする。入力済みDraftは保持する。

### 15.4 Cursor Handling

- API Responseから受け取った`nextCursor`をOpaque Stringとして保持する
- Cursorを解析、生成、書換えまたは画面表示しない
- CursorをLog、AnalyticsまたはCrash Reportへ出力しない
- Retryでは失敗したPageと同じCursorを使用する
- Success Responseの`nextCursor`と`hasMore`だけで次Page有無を更新する
- `hasMore = false`の場合はCursorを保持せずPaginationを停止する

`hasMore = true`なのに`nextCursor = null`、または`hasMore = false`なのにCursorが存在するResponseはContract Errorとして扱い、勝手に補完しない。

### 15.5 Merge and Scroll Anchor

Page取得開始直前に、現在最上部で見えているCanonical MessageのIDとViewport上端からのOffsetをScroll AnchorとしてMemory内へ保持する。

取得成功時は次の順で更新する。

1. 取得Page内のMessage順が古い順から新しい順であることを前提にMappingする
2. 既存MessageとMessage IDが重複するItemを除外する
3. Older MessagesをCanonical List先頭へ追加する
4. 取得前のAnchor Messageを同じViewport Offsetへ戻す
5. 新しい`hasMore`と`nextCursor`をStateへ保存する

これにより、Message追加によって読んでいた箇所が突然下方向へ移動するのを防ぐ。Pagination完了時に最下部へScrollしてはならない。

重複除外後に新規Messageが0件でも、API Responseの`hasMore`と`nextCursor`を採用する。同じCursorを無限に再取得するLoopを作ってはならない。

### 15.6 Oldest Boundary

`hasMore = false`となり、UserがMessage Area上端まで到達した場合は、Message Bubbleではない小さな中央Labelとして次を表示する。

```text
会話の始まり
```

- Conversation HistoryやDomain Modelへ追加しない
- Timestampを付与しない
- Empty Conversationでは表示しない
- Message Listの先頭Decorationとして扱う

このLabelにより、通信失敗や未読込ではなく、保存済みHistoryの先頭まで到達したことをUserが判断できる。

### 15.7 Pagination Failure

Older Page取得に失敗しても、既に表示しているHistoryとScroll位置を維持する。上端のLoading Rowを次のInline Errorへ置き換える。

```text
以前のメッセージを読み込めませんでした
[ 再試行 ]
```

- `再試行`は失敗したPageと同じCursorを使用する
- Retry中はButtonを無効にして二重実行を防ぐ
- `INVALID_CURSOR`では同じCursorを繰り返し送らず、最新PageからConversation Historyを再同期するActionを表示する
- Unknown / Contract Errorでは安全なGeneric Messageを表示する
- Error Rowは既存Messageを隠さない
- Errorが解消するまで新しいOlder Pageの自動Triggerを停止する

### 15.8 Interaction with Send and Streaming

- `sending`または`streaming`中はOlder Page取得を開始しない
- `loadingOlder`中は新規Sendを開始しない
- Streaming中にUserが上端Thresholdへ到達してもRequestをQueueしない
- Screenが`ready`へ戻った後、Userによる新しい上方向ScrollでPaginationを開始する
- Pending Send、Temporary Alice MessageまたはFailed ResponseをPagination Mergeで削除しない

PaginationとSendを同時実行しないことで、一つのScreen State内でCanonical History、Temporary MessageおよびScroll Anchorが競合するのを防ぐ。

### 15.9 Accessibility

- Older Page取得開始を毎Frame通知せず、一度だけ`以前のメッセージを読み込んでいます`と通知する
- 完了時は追加件数を`以前のメッセージを読み込みました`として一度通知できる
- VoiceOver FocusをLoading Rowや新規先頭Messageへ強制移動しない
- Anchor Messageに対する現在のFocusと読書位置を可能な限り維持する
- Retry Buttonへ具体的なSemantic Labelを付ける
- `会話の始まり`をMessageとして読み上げない

### 15.10 Test Requirements

- `hasMore = false`でPagination Requestを送信しない
- Threshold外から内へ入った時だけ1回Requestする
- 同じ位置に留まるだけでPageを連続取得しない
- Non-scrollableかつ`hasMore = true`で手動Load Buttonを表示する
- Loading中も既存MessageとDraftを維持する
- 追加Pageを先頭へ古い順のまま結合する
- Message ID重複を除外する
- Merge後もAnchor MessageとViewport Offsetを維持する
- 完了時に最下部へ移動しない
- Failure時に既存Historyと同じCursorを保持する
- `INVALID_CURSOR`で同じCursorを再送し続けない
- `hasMore` / `nextCursor`不変条件違反をContract Errorとして扱う
- `sending` / `streaming`とPaginationを同時実行しない
- Empty Conversationで`会話の始まり`を表示しない
- CursorをLogまたはUIへ公開しない

### 15.11 Trade-off

上端近接による自動取得は専用Buttonだけの方式より自然に過去の会話を読める。一方、意図しない連続RequestやScroll Jumpを防ぐため、Threshold Crossing、Single-flightおよびScroll Anchorの実装とTestが必要になる。

Pagination中は送信Buttonを一時的に無効にするため、取得と送信を完全並行できない。ただし通常は短時間であり、Phase 1の状態管理とMessage順序を安全かつ単純に保てる。

### 15.12 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-010 | History Pagination | 上端自動取得、Scroll Anchor、Same-cursor Retry、Oldest Label | Accepted — 2026-08-16 JST |

---

## 16. Confirmed Keyboard and Scroll Detailed Behavior

### 16.1 Decision UI-011

Phase 1のKeyboard / Scroll Behaviorは、次の2つを両立させる。

- 最新のAlice Responseを見ているUserにはStreamingを自然に追従させる
- 過去Messageを読んでいるUserのScroll位置を勝手に移動しない

Message Areaは画面内のPrimary Scroll Regionとし、Composer内部Scroll、Code Block横ScrollおよびTable横Scrollを除き、独立した縦Scroll領域を追加しない。

### 16.2 Initial Position

- App起動時はComposerへ自動Focusしない
- Initial History Load完了後、Messageが1件以上あれば最新Messageが見える最下部を初期位置とする
- 初期位置決定はMessage Layout完了後に一度行い、最下部が一瞬表示される前に古い位置を見せない
- Empty StateではScroll位置調整を行わない
- App Processが継続したままBackgroundから復帰した場合は現在位置を維持する
- App Process再起動後は端末へScroll位置を永続化せず、最新Messageから開始する

Initial LoadとPaginationを区別し、Older Page追加時は最下部へ移動せずUI-010のScroll Anchorを維持する。

### 16.3 Keyboard Open and Close

| User Action | Behavior |
|---|---|
| ComposerをTap | Keyboardを表示し、ComposerをKeyboard直上へ配置する |
| Message Areaの空白をTap | Keyboardを閉じる |
| Message Areaを下方向へDrag | iOS標準のInteractive Keyboard Dismissを許可する |
| Return Key | 改行を挿入する |
| Send Button | Messageを送信し、Keyboard Focusを維持する |

- Selectable Textの選択、Code Block操作またはLink操作をKeyboard Dismiss Tapとして扱わない
- Keyboardを閉じてもDraft、Validation ErrorまたはCharacter Counterを消去しない
- Keyboard表示中もHeaderを固定し、Message Areaだけを縮小する
- Keyboard Height、Home IndicatorまたはAnimation DurationをHard Codeしない
- Flutter / iOS標準InsetとAnimationへ追従する

### 16.4 Keyboard Resize Anchor

Keyboard表示またはComposerの1〜5行拡張によってMessage Areaの高さが変わる場合、Resize直前の位置に応じてAnchorを選ぶ。

| Position Before Resize | Anchor Behavior |
|---|---|
| 最新から120 logical pixels以内 | 最下部をAnchorにして最新MessageをKeyboard上へ維持する |
| 最新から120 logical pixelsより上 | 最上部で見えているMessage IDとOffsetをAnchorにして読書位置を維持する |

この処理により、Keyboardを開いたことだけを理由に過去Messageから最新へ移動しない。

### 16.5 Follow-latest State

FrontendはScroll Behavior用に次の状態を持つ。

| State | Meaning |
|---|---|
| `followingLatest` | 最新MessageまたはStreaming末尾へ自動追従する |
| `readingHistory` | Userが過去Messageを読んでおり、自動追従しない |

初期History表示後は`followingLatest`とする。UserのScrollにより最下部から120 logical pixelsを超えて離れた場合は`readingHistory`へ移行する。

Userが最下部から120 logical pixels以内へ戻った場合、または`最新へ`ButtonをTapした場合は`followingLatest`へ戻す。

120 logical pixelsはFrontend UI Constantであり、Message ContentやDevice Heightから動的に推測しない。変更する場合は複数iPhone SizeでのTest後に本Documentを更新する。

### 16.6 Streaming Auto-follow

`followingLatest`では次の更新時に最新位置を維持する。

- Pending User Message追加
- `考えています…`表示
- Temporary Alice Messageの高さ変更
- `assistant.completed`によるCanonical Message置換
- Inline Send Error表示

Delta受信ごとにScroll Animationを新規開始しない。複数Deltaが同じUI Frame内で反映される場合はScroll更新も一度へまとめ、Animationの競合や揺れを防ぐ。

`readingHistory`ではStreamingが進んでも現在のMessage Anchorを維持し、最下部へ強制移動しない。Completion時も同様とする。

User自身がSend ButtonをTapした場合は、現在位置にかかわらず`followingLatest`へ移行し、Pending User Messageが見える最下部へ移動する。これはUserが新しい会話Turnを開始した明示操作だからである。

### 16.7 Latest Button

`readingHistory`かつ最新側に未表示の更新が存在する場合、Composer上部の右下へFloating Actionを表示する。

```text
[ ↓ 最新へ ]
```

- 最低44 x 44 pt相当のTouch Targetを確保する
- Message、ComposerまたはPagination Statusを隠さない
- Tapで最下部へ移動して`followingLatest`へ戻す
- Streaming Delta数やToken数をBadgeとして表示しない
- 未表示の更新がなくなった場合は非表示にする
- Semantic Labelは`最新のメッセージへ移動`とする

iOSのReduce Motionが無効なら短い標準Scroll Animationを使用できる。Reduce Motionが有効な場合は大きなAnimationを避け、即時または最小限の移動とする。

### 16.8 User Scroll Priority

- UserがDragを開始した時点で実行中の自動Scroll Animationを停止できる
- User操作中に新しいDeltaが到着しても自動Scrollを再開しない
- iOS標準のScroll PhysicsとOverscroll Behaviorを基本とする
- Status Bar Tap等で上端へ移動した場合もUser Scrollとして扱い、Pagination Ruleを適用する
- Programmatic ScrollとUser Scrollを区別し、Programmatic更新だけで`readingHistory`へ誤遷移しない

### 16.9 Interaction with Pagination

- Older Page Load開始時のMessage ID / Offset AnchorをKeyboard / Streaming更新より優先する
- `loadingOlder`中にKeyboard Resizeが発生してもAnchor Messageを失わない
- Page Merge完了後に`followingLatest`へ自動変更しない
- Pagination Error Row追加でも現在位置を維持する
- `会話の始まり`Label追加でMessage位置を飛ばさない

UI-010で決定したとおり、PaginationとSend / Streamingを同時開始しないため、複数のAnchor補正を同一State Transitionで競合させない。

### 16.10 Accessibility

- `最新へ`ButtonをVoiceOverの通常Navigationで到達可能にする
- Streaming DeltaごとにVoiceOver Focusを最新へ移動しない
- Text Selection中にScroll位置を強制変更しない
- Keyboard表示 / 非表示だけを過剰にAnnouncementしない
- Dynamic TypeでComposer Heightが変化してもFocus中の入力欄を画面内へ維持する
- Reduce Motion設定を自動Scroll Animationへ反映する

### 16.11 Test Requirements

- Initial History Load完了後に最新Messageが表示される
- App Process再起動後に古いScroll位置を復元しない
- Keyboard Open時、最新付近では最下部を維持する
- 過去Message閲覧中のKeyboard OpenでMessage ID / Offsetを維持する
- Keyboard CloseでDraftとValidation Stateを失わない
- Return Keyで送信せず改行する
- Send後もKeyboard Focusを維持する
- 120 logical pixels境界でFollow Stateが正しく切り替わる
- `readingHistory`中のDelta / Completionで強制Scrollしない
- `followingLatest`中のTemporary Message成長へ追従する
- User Dragで実行中の自動Scrollを中断できる
- `最新へ`Buttonで最下部へ移動し追従を再開する
- Reduce Motion時に大きなScroll Animationを使用しない
- Pagination MergeおよびKeyboard Resize後もAnchorを維持する
- Selectable Text操作を意図しないKeyboard Dismissとして扱わない

### 16.12 Trade-off

常に最下部へ強制Scrollする方式より状態管理は増えるが、長いAlice Responseや過去Historyを読んでいるUserの位置を守れる。Chat UIでは「新しい内容が来たこと」と「今読んでいる場所を奪わないこと」の両方が重要である。

120 logical pixelsの固定Thresholdは完全に端末依存をなくすものではないが、単純でTest可能であり、Phase 1の複数iPhone Widthで挙動を検証しやすい。

### 16.13 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-011 | Keyboard / Scroll | Anchor維持、120 logical pixels Follow判定、`最新へ`、Keyboard Focus継続 | Accepted — 2026-08-17 JST |

---

## 17. Confirmed Visual Direction

### 17.1 Decision UI-012

AliceのVisual Directionを`Ambient Intelligence Core`として正式採用する。

```text
Futuristic
+ Intelligent
+ Calm
+ Spacious
+ Personal
```

黒に近いDeep Navyを基調とし、Cyan、Electric BlueおよびVioletの光でAliceの存在を表現する。画面へ人物の顔を固定せず、流動的なParticle Mesh Sphereと同心円状のInterface Ringを組み合わせた抽象表現を`Alice Core`とする。

未来的で高揚する印象を持たせる一方、情報密度は上げず、広い余白と最小限の操作要素によって長時間利用できる落ち着きを維持する。

#### Accepted Visual Reference

![Project Alice UI-012 Accepted Visual — Mobile Ambient, Mobile Chat and Desktop Chat](frontend-ui-design-ui-012.png)

この画像をUI-012の正式なVisual Referenceとする。

| Position | View | Phase 1 Treatment |
|---|---|---|
| Left | Mobile Ambient / TOP | 将来Reference。Phase 1では実装しない |
| Center | Mobile Chat | Phase 1実装対象 |
| Right | Desktop Chat | 将来Reference。Phase 1では実装しない |

画像はVisual Tone、Alice Core、主要配置、余白、色およびComponentの関係を伝えるReferenceである。表示文言、状態条件、寸法、Accessibility、Error、Streaming、PaginationおよびScopeの正確なContractは本ドキュメントの文章・TableをSource of Truthとする。画像と文章が矛盾する場合、AI Coding Assistantは画像から推測せず、文章仕様を優先する。

### 17.2 Scope Boundary

採用Visualには、将来のCross-device展開を確認するため次の三つのReference Viewを含む。

| View | Purpose | Implementation Scope |
|---|---|---|
| Mobile Ambient / TOP | Aliceの存在を中心に示す待機・入口表現 | Phase 1では実装しない。後続PhaseのNavigation / Voice要件確定時に再確認 |
| Mobile Chat | iOS Smartphone上のText Conversation | Phase 1実装対象 |
| Desktop Chat | PC Applicationでの横長Layout | Phase 1では実装しない。Desktop対象Phaseで再確認 |

このReference View採用は、UI-001で確定したPhase 1の`ConversationScreen`一画面構成を変更しない。Phase 1の実装ではMobile Chatだけを作成し、Ambient / TOP、Desktop用Navigation、Voice操作またはPC機能を先行実装しない。

将来、Ambient / TOPを実Application Screenとして追加する場合は、対象PhaseのRequirement、Navigation、State OwnershipおよびAccessibilityを確定してから本ドキュメントまたは後続UI Designを更新する。

### 17.3 Alice Core

`Alice Core`はAliceのPresenceを示すVisual Componentであり、Domain Entity、AI ModelまたはBackendのAlice Core Architectureそのものを意味しない。

- 不定形で流動的なParticle Mesh Sphereを中心へ配置する
- SphereはCyan / Blue / Violetの点、細線、半透明の面および中心光で構成する
- 外周へ複数の細い同心円、目盛りおよび限定的なAmber Accentを配置できる
- 人物の顔、身体、Anime Characterまたは写実的Avatarを使用しない
- `A` MonogramをAlice Identityの主表現として使用しない
- 装飾だけで処理状態を伝えず、状態にはTextまたはAccessibility Semanticsを併用する
- Brand Logo、Buttonまたは入力操作として扱わない

Static Mockupの形状をPixel単位で固定せず、同じIdentityを保ったまま緩やかに形が変わることを許容する。Phase 1の描画方式、AnimationおよびPerformance BoundaryはUI-013、Asset ContractはUI-016をSource of Truthとする。Particle Mesh自体の流動変形はPhase 2〜4で再設計する。

### 17.4 Theme and Color Tokens

採用VisualはDark-firstとする。Phase 1ではReference Mockupと同じDark Themeを実装対象とし、Light Themeは追加しない。将来System Appearance追従を導入する場合は、Alice Coreの可読性、ContrastおよびBrand Identityを確認して別Decisionで追加する。

ColorはWidgetへ直接散在させずSemantic Tokenを経由する。

| Token | Baseline | Usage |
|---|---|---|
| `background` | `#020812` | Screen基底 |
| `backgroundElevated` | `#07111F` | Header、Desktop Pane、Composer周辺 |
| `surface` | `#0D1828` | Assistant Bubble、Input、Card |
| `surfaceStrong` | `#0D3A73` | User Bubble、Primary Action |
| `textPrimary` | `#F5F8FF` | Title、Message本文、Input Text |
| `textSecondary` | `#AEB9CA` | Placeholder、補足Text |
| `border` | `#1B304A` | Input Border、Desktop Divider |
| `primary` | `#1677E8` | Send Button、Focus、Active Accent |
| `coreCyan` | `#00D9FF` | Alice Core中心光・粒子 |
| `coreBlue` | `#176BFF` | Alice Core Ring・Particle |
| `coreViolet` | `#6C45FF` | Alice Core Mesh Accent |
| `coreAmber` | `#F6A623` | 外周Ringの限定Accent |
| `error` | `#FFB4AB` | Error Text / Icon |
| `errorSurface` | `#3B1D1C` | Inline Error背景 |

値はReference MockupをFlutter上で再現するためのBaselineである。実装時のContrast検証により微調整が必要な場合は、視覚的同一性を保つ範囲でToken値を更新し、Golden Testへ反映する。

### 17.5 Typography

iOS System Fontを使用し、Custom Font PackageまたはFont Assetを追加しない。Flutter側でSan Francisco等の具体的Font名をHard Codeしない。

| Token | Base Size | Weight | Usage |
|---|---:|---|---|
| `ambientTitle` | 40 | Light | Mobile AmbientでSphere下へ表示する`Alice` |
| `title` | 22 | Regular | Mobile / Desktop Chat Headerの`Alice` |
| `body` | 17 | Regular | User / Alice Message本文 |
| `bodyEmphasis` | 17 | Semibold | Markdown Strong、主要Action |
| `supporting` | 14 | Regular | Error補足、Loading Label |
| `caption` | 12 | Regular | Timestamp、Character Counter |
| `code` | 14 | Regular Monospace | Inline / Fenced Code |

- Product名は正確に`Alice`と表示し、`A.L.I.C.E`へ変換しない
- 呼称「楠瑛」は常時UI Labelへ表示しない
- Dynamic Typeを有効にし、固定HeightでTextを切らない
- Message本文のLine Heightは約1.45を基準とする
- 日本語と英数字が混在しても不自然なLetter Spacingを追加しない
- BoldまたはColorだけで意味を区別しない

### 17.6 Mobile Ambient / TOP Reference

Mobile Ambient / TOPは将来用Referenceとして次の構成を採用する。

- Headerへ`Alice`を表示しない
- 画面中央よりやや上へ大きなAlice Coreを配置する
- `Alice`をAlice Coreの下側中央へ表示する
- `待機中`、`オンライン`または同等の常時Status Textを表示しない
- Composer、Chat Bubble、MenuおよびNavigation Controlを表示しない
- Dark BackgroundをSafe Areaまで連続して描画する

このViewの位置関係は正式なVisual Directionである。ただしPhase 1の実装対象ではない。

### 17.7 Phase 1 Mobile Chat Layout

Phase 1の`ConversationScreen`は次のVisual Layoutを採用する。

- Top Safe Area下のHeader中央へ`Alice`を表示する
- Back Button、Hamburger Menu、Edit、Settings、Provider名およびOnline表示を追加しない
- Alice Coreを会話領域上半分の中央付近へ配置する
- Message HistoryをAlice Coreと共存するScrollable Message Areaへ表示する
- User Messageは右寄せ、Alice Messageは左寄せとする
- ComposerをBottom Safe Area直上へ固定する
- Reference Mockupの短い会話例はLayout確認用であり、固定Contentとして実装しない

Alice CoreはMessage Historyより前面へ重ならない。UI-014どおりCoreは非Scrollの`AliceCoreRegion`へ配置し、Historyが増えてもScroll Contentへ移動しない。利用可能Heightが減少した場合はCoreを段階的に縮小し、最小Boundaryを下回る場合は一時非表示としてMessage ViewportとComposerを優先する。

### 17.8 Desktop Chat Reference

Desktop Chatは将来用Referenceとして次の構成を採用する。

- Window上端中央へ`Alice`を表示する
- Contentを縦Dividerで左右二領域へ分ける
- 左Paneへ大きなAlice Coreを配置する
- 右PaneへConversation Historyと下部Composerを配置する
- Sidebar、Conversation List、Tool Menu、Settingsおよび`Alice Core` Labelを表示しない
- Responsive Width、Window Minimum SizeおよびKeyboard ShortcutはDesktop対象Phaseで確定する

Phase 1ではDesktop Widget、Desktop NavigationまたはDesktop用Stateを先行実装しない。

### 17.9 Message Styling

#### User Message

- 右寄せBubble
- 最大幅はMobile Message Areaの78%
- Backgroundは`surfaceStrong`
- Textは`textPrimary`
- Horizontal Padding 14、Vertical Padding 10
- Corner Radius 16〜18
- 強いShadowを使用しない

#### Alice Message

- 左寄せBubble
- Backgroundは`surface`
- Textは`textPrimary`
- Horizontal Padding 14、Vertical Padding 10
- Corner Radius 16〜18
- 人物Avatar、`A` MonogramおよびCore Thumbnailを各Messageへ繰り返し表示しない

Roleは位置、SurfaceおよびAccessibility Labelの組合せで識別する。Timestamp、Delivery CheckおよびRead Receiptを通常のPhase 1画面へ常時表示しない。Canonical `createdAt`はUI-004どおりStateへ保持し、日付境界の判定へ使用する。

### 17.10 Composer and Actions

- ComposerはDark Surface上のRounded Inputと円形Send Buttonで構成する
- Placeholderは`メッセージを入力`とする
- Input Borderは`border`、Focus時は`primary`を使用する
- Input Corner Radiusは14〜18とする
- Send Buttonは44 x 44 pt以上、Enabled時`primary`、Iconは`textPrimary`とする
- Send Iconは上向きArrowを使用する
- Disabled状態をOpacityだけで区別しない
- Phase 1ではMicrophone、Attachment、ToolまたはModel Selectionを追加しない

### 17.11 Code, Table, Link and Selection

- Inline Codeは`surface`、Corner Radius 4、Monospace 14とする
- Fenced Code BlockはDark Code Surface、Border `border`、Corner Radius 12とする
- Code BlockへSyntax Highlightを必須としない
- Table Headerは`surface`、Cell Borderは`border`とする
- Linkは明るいBlueとUnderlineで識別し、Colorだけに依存しない
- Horizontal Scrollbarは必要時のみ表示する

### 17.12 Spacing and Shape Tokens

| Token | Value | Typical Usage |
|---|---:|---|
| `space1` | 4 | Icon内部、密な補助要素 |
| `space2` | 8 | 関連要素 |
| `space3` | 12 | Message内、Status Row |
| `space4` | 16 | Screen Padding、Message間隔 |
| `space6` | 24 | Section間 |
| `space8` | 32 | Alice CoreとContent間 |
| `radiusSmall` | 4 | Inline Code |
| `radiusMedium` | 12 | Code Block、Error Surface |
| `radiusLarge` | 18 | Bubble、Input |
| `radiusPill` | 999 | Circular / Pill Action |

### 17.13 Motion and State Expression

- Alice Coreは将来的に穏やかで流動的なAnimationを持てる構造とする
- 常時Animationは会話内容の読解を妨げない強度とする
- Sending、Streaming、Listening、SpeakingおよびErrorをCoreの色や動きだけで伝えない
- Reduce Motion有効時はStatic Imageまたは低Motion表現へ切り替える
- Backgroundで動作するAnimationはApp Lifecycleに従って停止または負荷を下げる
- Phase 1のAnimation範囲、Frame PerformanceおよびLifecycle RuleはUI-013に従う

### 17.14 Visual Accessibility

- 通常Textは4.5:1以上、大きなTextと非Text UIは3:1以上を目標とする
- Error、Disabled、SelectedおよびStreamingを色だけで区別しない
- Touch Targetは最低44 x 44 pt相当とする
- Alice Coreには意味に応じたSemanticsを設定し、純粋な装飾状態では重複読み上げを避ける
- Dynamic Type拡大時はMessage幅とComposer高さを再計算する
- Increase Contrast、Reduce TransparencyおよびReduce Motionを可能な範囲で尊重する
- GlowやParticleがText Contrastを低下させないよう、Text背面へ十分な暗色Surfaceを確保する

### 17.15 Implementation Boundary

- Semantic Color、Typography、Spacing、RadiusをTheme / Token Classへ集約する
- Presentation WidgetへRaw Hex Colorを散在させない
- Domain / Application LayerへColor、TextStyle、Icon、AnimationまたはTheme Typeを漏らさない
- Alice Coreを独立したPresentation Componentとして扱い、Conversation Use Caseへ描画責務を持たせない
- Phase 1はUI-013どおりLocal Static PNGとFlutter標準`CustomPainter`を使用し、Shader、Rive、Videoまたは追加描画Dependencyを導入しない
- Reference Mockupを理由にPhase 1 Scope外のAmbient / TOPまたはDesktop Screenを作成しない

### 17.16 Test and Visual Review Requirements

- Mobile Chatを幅375、390、430 logical pixelsで確認する
- Dynamic Type標準・拡大でText切断とOverflowがない
- User / Alice RoleをColorなしでも位置とSemanticsから識別できる
- Primary Action、Secondary Text、ErrorおよびMessageのContrastを確認する
- Long Japanese Text、English、Emoji、Code、TableおよびLinkをFixture表示する
- Empty、History、Sending、Streaming、Error、PaginationおよびKeyboard OpenをGolden Test対象にする
- Alice CoreのGlow、ParticleまたはRingがMessage操作を妨げない
- Reduce Motion時の表示をTestする
- Mobile Ambient / TOPとDesktop ChatはPhase 1 Automated Test対象に含めない

### 17.17 Trade-off

Alice CoreによってAlice固有の存在感と将来のVoice / Agent状態表現へつながるVisual Anchorを得られる。一方で、単純なMonogramより描画、Animation、Battery、AccessibilityおよびGolden Testの複雑性が増える。

Dark-firstに限定することでPhase 1の実装とVisual Validationを集中できるが、Light Appearanceを好むUserへの選択肢は提供しない。Light ThemeがRequirementになった場合は、単純な色反転ではなくAlice Coreの視認性を含めて別途設計する。

### 17.18 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-012 | Visual Direction | `Ambient Intelligence Core`、Dark-first、Particle Mesh Sphere、Phase 1 Mobile Chat、将来Ambient / Desktop Reference | Accepted — 2026-08-17 JST |

---

## 18. Confirmed Alice Core Component

### 18.1 Decision UI-013

Phase 1のAlice Coreは、透過背景の静止画像Assetで表現するParticle Mesh Sphereと、Flutter標準`CustomPainter`で描画するRing / Glow Animationを組み合わせて実装する。

```text
AliceCoreView
└── RepaintBoundary
    └── Stack
        ├── RingPainter
        ├── Core Image Asset
        └── GlowPainter
```

Phase 1では第三者Animation Package、Rive、Fragment Shader、3D EngineまたはNetwork配信Assetを導入しない。

### 18.2 Responsibility

`AliceCoreView`はPresentation専用Componentとし、次だけを担当する。

- Alice Coreの描画
- Visual Stateに対応したRing、Glow、OpacityおよびScaleのAnimation
- Reduce Motion対応
- App LifecycleとVisibilityに応じたAnimation停止・再開
- Component境界内のRepaint分離

次を担当しない。

- SSE Eventの解析
- API / Backend Error Codeの判定
- Conversation、MessageまたはAI Providerの状態管理
- Alice Response Contentの表示
- Tool、VoiceまたはAgentのBusiness State判断

Conversation Presentation Stateを`AliceCoreVisualState`へ変換する責務は`ConversationScreen`側のPresentation Logicが持つ。

### 18.3 Phase 1 Rendering Composition

| Layer | Rendering | Phase 1 Rule |
|---|---|---|
| Core Base | Local transparent image | Particle Mesh Sphere本体。Particle位置とMesh形状は動かさない |
| Inner Glow | `CustomPainter` | Radial GradientのOpacity / Scaleを緩やかに変化させる |
| Interface Rings | `CustomPainter` | 同心円、短い目盛り、限定的なAmber Accentを描画する |
| Outer Glow | `CustomPainter` | Component外へ過度にはみ出さない弱い発光 |

Core Base AssetはRepository管理下のLocal Assetとし、RuntimeでNetworkから取得しない。Filename、Resolution、Format、配置および検証方法はUI-016をSource of Truthとする。Assetが存在しない状態をRuntime Fallbackで隠さず、Build / Testで検出する。

### 18.4 Component Input

概念Contractは次とする。

```text
AliceCoreView
├── visualState: AliceCoreVisualState
├── size: logical pixels
└── reduceMotion: boolean
```

`AliceCoreVisualState`はPresentation Modelであり、Phase 1では次を持つ。

| State | Meaning | Visual Behavior |
|---|---|---|
| `idle` | 送信可能な通常状態 | 通常発光、Ringを非常にゆっくり回転 |
| `thinking` | Send開始後から最初のDelta受信前 | Center GlowをPulse、Ringを少し速く回転 |
| `streaming` | Alice Response受信中 | 安定した発光、Ringを緩やかに回転 |
| `unavailable` | Initial Load Failure等で会話を開始できない | 明度を下げ、Animationを停止 |

Initial History LoadingはAliceが思考している状態ではないため`thinking`へMappingしない。Loading IndicatorとLabelをUI-008どおり別表示し、Alice CoreはAnimationを停止した`idle`のStatic表示とする。利用可能HeightがUI-014の最小Boundaryを下回る場合だけCoreを非表示にする。

### 18.5 Phase 1 Motion Baseline

| Motion | Baseline |
|---|---|
| Idle Ring Rotation | 12秒で1周 |
| Thinking Ring Rotation | 6秒で1周 |
| Streaming Ring Rotation | 8秒で1周 |
| Thinking Core Pulse | 2秒周期 |
| Pulse Scale | `0.98`〜`1.02` |
| Visual State Transition | 300 ms |
| Particle / Mesh Deformation | なし |

- Motionは線形回転だけにせず、視覚的に急な加速・停止が見えないCurveを使用する
- 状態切替時にAnimationを先頭から不自然にJumpさせず、現在値から次状態へTransitionする
- 毎Frameの乱数生成、Particle再配置またはPath再構築を行わない
- Interface Ringは3本とし、Core直径に対して約106%、116%、126%へ配置する
- Ring線幅は内側から1.0、0.75、0.75 logical pixelsをBaselineとする
- Tickは最外周Ringへ48本配置し、4本ごとに長いTickを使用する
- Amber Accentは最外周へ最大2区間、各12〜18度に限定し、常時点滅させない
- Outer Glowの最大Opacityは0.22、Thinking Core Pulseの最大Opacityは0.35をBaselineとする
- 上記値はGolden Testの基準値であり、ContrastまたはFrame Performance上の問題が確認された場合だけUI Design Reviewを通じて変更する

### 18.6 Reduce Motion and Accessibility

- iOSのReduce Motionが有効な場合、Ring Rotation、Pulseおよび継続的なScale変化を停止する
- Static状態でもAlice CoreのIdentityと各UI Stateを理解できるようにする
- 状態をAnimation、ColorまたはGlowだけで伝えず、`考えています…`、Streaming Indicator、Error Text等を併用する
- Alice Coreが装飾として使用される場合はSemantics Treeから除外する
- Screen ReaderへDeltaごとまたはAnimation FrameごとのAnnouncementを発生させない
- GlowとParticleをMessageまたは操作Textの背面へ重ねない

### 18.7 Performance Boundary

- `AliceCoreView`全体を`RepaintBoundary`で囲み、Core AnimationによってMessage List、HeaderまたはComposerを再描画しない
- `CustomPainter`はAnimationの`Listenable`から直接Repaintし、Animation Frameごとに画面全体のBuild / Layoutを要求しない
- Ring Geometry、Tick位置および固定PathはSize変更時に計算し、通常Frameごとに再生成しない
- `CustomPainter`の描画はComponent Bounds内へClipする
- AppがBackground / Inactiveになった場合、またはAlice Coreが画面外になった場合は継続Animationを停止する
- Minimum Support範囲の代表DeviceをProfile Modeで計測し、60 Hz表示でUI / Raster Frameのp95が16.7 ms以内となることを目標とする
- Performance目標を満たせない場合はParticle数を動的に変えるのではなく、Glow Layer、Ring DetailまたはAnimation FrequencyをDesign Reviewで段階的に削減する

### 18.8 Failure and Fallback

- Reduce Motion時は同じCore Base Assetと静止Ringを表示する
- Animation初期化に失敗してもConversation操作を停止させず、Static Alice CoreへFallbackする
- Alice Core描画失敗をAPI ErrorまたはAI生成失敗として扱わない
- Core Base Assetの欠落、Decode失敗または異常SizeはTestで検出し、通常運用時の無言Fallbackを前提にしない

### 18.9 Phase 2–4 Evolution

Particle Mesh Sphere本体の流動表示はPhase 1では実装せず、Phase 2〜4で段階的に対応する。

| Phase | Evolution Direction |
|---|---|
| Phase 1 | Static Particle Mesh Sphere + Ring / Glow Animation |
| Phase 2 | Sphere表面またはMeshの緩やかな流動・呼吸表現を追加 |
| Phase 3 | Tool待機、確認待ち、実行中、完了等の状態表現を必要に応じて追加 |
| Phase 4 | Listening、Thinking、Speaking、Agent Action等のVoice / Agent状態表現へ拡張 |

Phase 2開始時に、Rive、Fragment Shader、CustomPainter拡張または別Rendererを当時のFlutter、iOS、PerformanceおよびAsset制作要件に基づいて再比較する。

将来交換可能性だけを理由として、Phase 1で`AliceCoreRenderer` Interface、Plugin Architecture、Rive DependencyまたはShader Infrastructureを先行実装しない。`AliceCoreView`内部の描画責務をScreenから分離しておくことで、外部Contractを維持したまま後続Phaseで実装を置換できる構造とする。

### 18.10 Test Requirements

- 各`AliceCoreVisualState`のGolden Testを作成する
- Reduce Motion時に継続Animationが停止することをTestする
- State Transitionで例外、Layout Shiftまたは不自然なSize変更が発生しないことをTestする
- Alice CoreのRepaintによって親Screen全体が継続RepaintされないことをProfile / Debug Toolで確認する
- App LifecycleがInactive / Backgroundの間にAnimationを継続しないことをTestする
- Width 375、390、430 logical pixelsでCore、MessageおよびComposerが重ならないことを確認する
- Core Asset欠落をBuildまたはAutomated Testで検出する

### 18.11 Trade-off

Core Baseを静止画像にするため、Phase 1では採用Mockupの流動的なParticle Meshを完全には再現しない。一方、複雑な描画技術と第三者Dependencyを先行導入せず、RingとGlowによってAliceの存在感を保ちながら、実装・Performance・TestのRiskを制御できる。

Phase 2〜4でRendererを再評価する作業は残るが、将来要件が明確になってからVoice、ToolおよびAgent状態に適した技術を選択できる。

### 18.12 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-013 | Alice Core Component | Phase 1はStatic Core Asset + Flutter標準Ring / Glow Animation。Particle Meshの流動表示はPhase 2〜4で段階対応 | Accepted — 2026-08-17 JST |

---

## 19. Confirmed Phase 1 Component Structure

### 19.1 Decision UI-014

Phase 1の`ConversationScreen`は、固定Header、Responsiveな`AliceCoreRegion`、単一の縦Scroll領域である`MessageViewport`および固定`ComposerPanel`で構成する。

```text
ConversationScreen
├── AliceHeader
├── ConversationBody
│   ├── AliceCoreRegion
│   │   └── AliceCoreView
│   └── MessageViewport
│       ├── HistoryLoadingIndicator
│       ├── DateSeparator
│       ├── ConversationMessageItem
│       ├── StreamingMessage
│       └── InlineError
└── ComposerPanel
    ├── MessageTextField
    ├── ComposerValidationMessage
    └── SendButton
```

### 19.2 AliceHeader

- Top Safe Area直下へ固定する
- Content Heightは44 logical pixelsとする
- 中央へ`Alice`を表示する
- Back、Hamburger Menu、Edit、Settings、Provider名およびOnline表示を追加しない
- Sending、StreamingおよびError状態をHeaderへ重複表示しない
- Header自身はConversation StateまたはNavigationを所有しない

### 19.3 ConversationBody

`ConversationBody`はHeaderとComposerの間の利用可能領域を使用し、上側の`AliceCoreRegion`と下側の`MessageViewport`へ分割する。

通常時のAlice Core Region Heightは次をBaselineとする。

```text
clamp(ConversationBody Height × 0.34, 180, 260)
```

- `AliceCoreRegion`だけに固定Pixel値を適用し、`MessageViewport`は残りの高さを使用する
- Body全体を二つの独立した縦Scroll領域にしない
- Alice Core、Message、ErrorおよびComposerを重ねて表示しない
- Height計算に特定iPhoneの物理Pixel、Dynamic IslandまたはKeyboard HeightをHard Codeしない

### 19.4 Keyboard-responsive Alice Core

Keyboard表示中はConversation History、Focused Inputおよび最新MessageをAlice Coreより優先する。

- Alice Core Regionを96〜120 logical pixelsへ縮小する
- 縮小・復元Transitionは300 msをBaselineとする
- 縮小によってMessage ViewportのScroll Offsetを変更しない
- 利用可能なBody Heightが小さく、Message Viewportの最低表示領域を確保できない場合はAlice Core Regionを一時的に非表示にできる
- Keyboardを閉じた場合は、現在のScreen Stateを維持したまま通常Sizeへ戻す
- Core Size変更中もComposerのFocusを失わない

Alice Coreを非表示にする正確なHeight ThresholdはAccessibility / Responsive Test Fixtureで確定し、AI Coding AssistantがDevice名で条件分岐してはならない。

### 19.5 AliceCoreRegion

- UI-013の`AliceCoreView`だけを配置する
- Message Historyとは独立し、Scrollしない
- Tap、Long Press、DragまたはVoice起動操作を持たせない
- Alice Coreの描画範囲をRegion Bounds内へ制限する
- `AliceCoreVisualState`以外のConversation Domain Dataを受け取らない
- Error Text、Loading TextまたはProvider情報をCore上へ重ねない

### 19.6 MessageViewport

`MessageViewport`は画面内の唯一の主要な縦Scroll領域とする。Composer内部、Code BlockおよびTableの局所ScrollはUI-011の例外として維持する。

表示順は次とする。

```text
Older History Loading / Oldest Boundary
Date Separator
Oldest Message
...
Latest Message
Streaming / Send Error
```

- Canonical Messageを古い順から新しい順へ表示する
- UI-010の上端PaginationとScroll Anchorを維持する
- UI-011のFollow-latest、`最新へ`およびUser Scroll優先を維持する
- Alice Core RegionとMessage Contentを同じScroll Contentへ混在させない
- Streaming、Partial FailureおよびInline Errorも同じReading Order内へ配置する
- Message History件数によってAlice Core Regionを削除または再生成しない

### 19.7 Message Components

`ConversationMessageItem`はRoleとPresentation Stateに応じてUser / Alice表示を切り替える。

概念的なPresentation Modelは次を持つ。

```text
ConversationMessageViewData
├── messageId
├── role
├── content
├── createdAt
└── presentationState
```

`presentationState`はPresentation Layer固有とし、次を表現できるようにする。

| State | Meaning |
|---|---|
| `canonical` | Backendから取得した確定Message |
| `pending` | Send開始後の一時User Message |
| `streaming` | Deltaを結合中の一時Alice Message |
| `partialFailed` | Terminal Failure後に画面内だけへ残す途中回答 |

Domain EntityまたはAPI DTOへ上記UI Stateを追加しない。Presentation MappingでCanonical Modelと一時StateをView Dataへ変換する。

#### User Message Bubble

- 右寄せ
- Message Area有効幅の最大78%
- `surfaceStrong`背景
- User Label、Avatar、Delivery Check、Read ReceiptおよびTimestampを常時表示しない

#### Alice Message Bubble

- 左寄せ
- Message Area有効幅の最大88%
- `surface`背景
- Role Label、Avatar、`A` Monogram、Core ThumbnailおよびTimestampを常時表示しない
- UI-005のSafe Markdown Rendererを使用する
- Code / Table横ScrollをMessageViewportの縦Scrollから分離する

### 19.8 ComposerPanel

`ComposerPanel`はBottom Safe Area直上へ配置し、Keyboardへ追従する。

- `MessageTextField`は1〜5行
- Placeholderは`メッセージを入力`
- `SendButton`は44 x 44 logical pixels以上の円形Button
- Send Iconは上向きArrow
- `ComposerValidationMessage`はInputとの関係が分かる位置へ表示する
- Voice、Attachment、ToolおよびModel Selectionを追加しない
- Draft、Sending、StreamingおよびValidation StateはScreen Stateから受け取る
- Composer Component内部でUse Case、HTTP RequestまたはSSE接続を開始しない

概念Contractは次とする。

```text
ComposerPanel
├── draft
├── enabled
├── validationMessage
├── onDraftChanged
└── onSend
```

### 19.9 Error Components

Error ScopeごとにComponentを分ける。

| Component | Placement | Purpose |
|---|---|---|
| `InitialLoadErrorView` | Conversation Body | Initial History Load失敗 |
| `ComposerValidationMessage` | Text Field直下 | Userが送信前に修正できる入力Error |
| `MessageSendErrorView` | 対象User Message直後 | Send / Result UnknownとRecovery Action |
| `StreamingFailureView` | Partial Response直後 | Terminal Stream Failure |

すべてのError条件を一つの巨大な汎用Widgetへ集約しない。一方、Icon、Surface、Spacing、Action Row等の純粋なVisual部品は共通化してよい。

### 19.10 Dependency Boundary

UI Componentは次へ直接依存しない。

- HTTP / SSE Client
- OpenAIまたはその他AI Provider
- DynamoDB / Repository
- Application Use Case
- API DTO

UI ComponentはView Data、Primitiveな表示値およびUser Callbackを受け取る。状態取得、DTO Mapping、Use Case呼出しおよびNavigation判断はScreen / Presentation Logic側が担当する。

### 19.11 Test Requirements

- 各ComponentをBackendなしでWidget Testできる
- Width 375、390、430 logical pixelsでHorizontal Overflowがない
- 通常時のAlice Core Regionが計算Boundary内へ収まる
- Keyboard表示時にCoreが縮小し、Composer FocusとMessage Scroll Offsetを失わない
- 小さい利用可能HeightではCoreを非表示にしてもHeader、MessageおよびComposerを操作できる
- Message History件数が増えても主要Scroll領域が一つに保たれる
- User / Alice / Pending / Streaming / Partial FailureをFixtureで個別表示できる
- Error ComponentとRecovery Actionの関係をWidget Testできる
- Component TestがNetwork、OpenAIまたはDynamoDBを要求しない

### 19.12 Trade-off

Alice Coreを非Scroll領域として常時確保するため、一般的な全画面Chat UIよりMessage表示領域は小さくなる。一方で、Aliceの存在感を維持し、Userが意図した「上側にAlice、下側に会話」という構成を実現できる。

Keyboard表示時にCoreを縮小または非表示にするため、画面状態によってVisual Balanceは変化するが、入力、会話の確認およびAccessibilityを優先できる。

### 19.13 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-014 | Phase 1 Component Structure | 固定Header、Responsive Alice Core、単一Message Scroll、固定Composer。Keyboard時はCoreを縮小または一時非表示 | Accepted — 2026-08-17 JST |

---

## 20. Confirmed Phase 1 Accessibility

### 20.1 Decision UI-015

Phase 1では、Alice Coreの未来的なVisual表現が認識または操作の前提にならないAccessibility構造を採用する。Conversation、処理状態、ErrorおよびUser ActionはText、構造およびSemanticsによって理解可能にする。

対象はPhase 1 iOS Smartphone UIとし、Desktop、Voice Input / OutputおよびAgent固有のAccessibilityは各対象Phaseで追加設計する。

### 20.2 Reading and Focus Order

基本的なReading Orderは次とする。

1. Header `Alice`
2. Conversation History
3. 現在のStreaming / Error State
4. Message Input
5. Send Button

- Alice Coreは通常、操作を持たないDecorative ElementとしてSemantics Treeから除外する
- Visual配置とSemantics Reading Orderを不必要に逆転させない
- Message HistoryはCanonicalな古い順から新しい順で読む
- Inline Errorは対象MessageまたはInputの直後に配置する
- Floatingする`最新へ`ButtonがMessage本文のReading Orderへ割り込まないようにする

### 20.3 Message Semantics

Visual上はRole Labelを各Messageへ表示しないが、Assistive Technologyには発言者と状態を伝える。

| Message Type | Semantic Prefix Example |
|---|---|
| Canonical User | `あなた` |
| Canonical Alice | `Alice` |
| Pending User | `あなた、送信中` |
| Streaming Alice | `Alice、回答を作成中` |
| Partial Failure | `Alice、途中までの回答` |

- Role、StateおよびContentの関係を理解できるようにする
- Markdown全体を一つの巨大なLabelへ平坦化せず、Heading、List、LinkおよびCodeの理解可能な構造を可能な範囲で維持する
- Message ContentのText Selection / Copyを妨げない
- MessageごとのTimestampを常時表示しないDecisionを維持する
- Date Separatorは日付として理解可能なSemantic Labelを持つ

### 20.4 Streaming Announcement

- Send開始時に`Aliceが回答を作成中です`を一度通知する
- `assistant.delta`ごとにContentまたは差分を自動Announcementしない
- Completion時に`Aliceの回答が完了しました`を一度通知する
- Terminal Failure時は対応するUser向けErrorを一度通知する
- Rebuild、ScrollまたはTheme描画によって同一Announcementを繰り返さない
- Streaming開始・完了によって現在のAccessibility Focusを強制移動しない

### 20.5 Dynamic Type

- iOSのDynamic Typeを尊重し、Text Scaleを独自に小さくClampしない
- Header、Message、ErrorおよびComposer Textを固定Heightで切断しない
- Composerの1〜5行という入力Ruleを維持しつつ、拡大Textの1行Heightに応じてComponent Heightを再計算する
- Bubble Width、PaddingおよびCode / Table領域を利用可能幅に基づいて再計算する
- 利用可能Heightが不足した場合は、UI-014どおりAlice Coreを先に縮小または非表示にする
- Alice CoreのVisual維持を理由にMessage、Focused Input、ValidationまたはRecovery Actionを画面外へ押し出さない
- Text Scale拡大時もHorizontal Screen Scrollを追加しない

UI-003およびUI-006に記載したHeader / Composer Heightは標準Text ScaleでのBaselineとし、Accessibility Text Scale時の絶対上限として扱わない。

### 20.6 Touch Target and Interaction

Interactive Componentは44 x 44 logical pixels以上のTargetを持つ。

対象例：

- Send Button
- Retry / Result Check Button
- `最新へ`Button
- Link
- Code Copy Button

- IconのVisual Sizeが44未満でも、Hit Targetは44以上を確保する
- Alice Core、Date Separatorおよび通常Message Bubbleへ不要なTap Actionを追加しない
- Gestureだけで利用できる必須機能を作らず、理解可能なButtonまたはAccessibility Actionを提供する
- Disabled状態のButtonは操作不能であることをSemanticsへ反映する

### 20.7 Color and Contrast

- 通常TextのForeground / Background Contrastは4.5:1以上を目標とする
- 大きなTextおよび主要な非Text UIは3:1以上を目標とする
- Input Border、Focus表示、ButtonおよびError Iconを背景から識別可能にする
- User / Alice RoleをColorだけで区別しない
- Error、Disabled、SelectedおよびStreamingをColorまたはOpacityだけで区別しない
- Alice CoreのGlow、ParticleおよびRingをTextまたは操作要素の背面へ重ねない
- Increase Contrast有効時にBorder、TextおよびFocus Indicatorの識別性を低下させない

### 20.8 Reduce Motion

- UI-013どおりRing Rotation、Core Pulseおよび継続的なScale変化を停止する
- Core Sizeの縮小・復元は即時切替または短いCross-fadeへ変更できる
- Streaming Indicatorで強い点滅または反復移動を使用しない
- Programmatic Scrollを必要最小限とし、Userが読んでいる位置を奪わない
- Motionを停止してもIdle、Thinking、StreamingおよびUnavailableをTextとSemanticsから理解できる

### 20.9 Focus Management

| Event | Focus Behavior |
|---|---|
| Send | Composer Focusを維持する |
| Keyboard Open | Focused Inputを画面内へ維持する |
| Streaming Start | Focusを自動移動しない |
| Streaming Complete | Focusを自動移動しない |
| Validation Error | InputとErrorの関係を伝える |
| Retry Start | 対象Buttonを無効化し、処理中Stateを伝える |
| Initial Load Failure | Error説明からRecovery Actionの順で読める |
| Alice Core Resize / Hide | Focus順序と現在Focusへ影響させない |

新しいMessageまたはErrorが追加されても、Userが過去History、InputまたはRecovery Actionを操作中の場合はFocusを奪わない。

### 20.10 Accessible Labels

IconだけのButtonには目的を示すLabelを設定する。

| Component | Required Label |
|---|---|
| Send Button | `メッセージを送信` |
| Latest Button | `最新のメッセージへ移動` |
| Code Copy Button | `コードをコピー` |
| Initial Load Retry | `会話を再読み込み` |
| Result Check | `送信結果を確認` |

単なる`ボタン`、`矢印`または文脈のない`再試行`をPrimary Labelとして使用しない。Action完了後の通知は必要な場合だけ一度行い、同じStateのRebuildで繰り返さない。

### 20.11 Keyboard and Alternative Input

- iOS Software Keyboardだけを前提にせず、Hardware KeyboardでもInputとSendを操作できるようにする
- UI-006の送信Ruleを維持し、Enter単独による送信をPhase 1へ追加しない
- Tab / Accessibility Focusから主要Actionへ到達できるようにする
- Focus IndicatorをColorまたはGlowだけに依存させない
- Keyboard Dismiss操作によってDraftを消去しない

### 20.12 Test Requirements

Automated Testでは次を確認する。

- Semantics Reading Order
- Message Role / State Label
- Icon-only ButtonのLabel
- Enabled / Disabled State
- Dynamic Type拡大時のText切断、OverlapおよびOverflow
- Reduce Motion時の継続Animation停止
- InputとValidation Errorの関連
- Alice Coreが不要なFocus Targetにならない
- DeltaごとにAnnouncementが発生しない
- Core縮小・非表示時にFocusを失わない

実機またはiOS Simulatorで次を確認する。

- VoiceOver
- Dynamic Typeの標準・拡大・最大付近
- Reduce Motion
- Increase Contrast
- Hardware Keyboard
- Keyboard表示中のInput、Scroll、SendおよびRetry

Automated Semantics TestだけでVoiceOverの実利用品質を保証したと判断せず、Phase 1 UI ReviewにManual Accessibility Checkを含める。

### 20.13 Trade-off

Dynamic TypeまたはKeyboard表示中にAlice Coreを縮小・非表示にするため、通常時と同じVisual Balanceは維持できない。一方、Alice Coreは装飾であり、会話の読解、入力およびRecovery Actionを優先することで、Phase 1の主要機能を利用可能に保てる。

Streaming完了時にFocusを新しい回答へ強制移動しないため、回答へ自動的に移動したいUserには追加操作が必要になる場合がある。一方、入力中または過去Message確認中のFocusを奪わず、`最新へ`Actionで明示的に移動できる。

### 20.14 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-015 | Phase 1 Accessibility | Alice Coreを操作前提にせず、Text / Semantics、Dynamic Type、Reduce Motion、Focus維持および44 pt Targetで全主要機能を利用可能にする | Accepted — 2026-08-17 JST |

---

## 21. Confirmed Alice Core Asset Specification

### 21.1 Decision UI-016

Phase 1のAlice Core本体は、Project Alice専用に新規生成する1024 x 1024 pixels、sRGB、完全透過背景のPNG AssetとしてRepositoryで管理する。

画面Mockupまたは第三者画像から球体を直接切り抜かず、採用Visual Directionを基準に実装用Assetを生成する。AssetにはParticle Mesh Sphere本体だけを含め、Ring、背景、文字およびAnimationを含めない。

### 21.2 File Contract

| Item | Decision |
|---|---|
| Filename | `alice_core_base.png` |
| Repository Path | `frontend/assets/images/alice_core/alice_core_base.png` |
| Format | PNG |
| Canvas | 1024 x 1024 pixels |
| Aspect Ratio | 1:1 |
| Color Space | sRGB |
| Background | Full Transparency |
| Animation | None |
| Target Display Size | 96〜260 logical pixels |
| File Size Target | 2 MB以下 |

1024 pixelsは、最大260 logical pixelsを3倍程度のPixel Densityで表示する場合にも必要なDetailを保持するためのSource Resolutionとする。Flutter上で1024 logical pixelsのSizeへ固定表示しない。

### 21.3 Included Visual Elements

- 不定形で立体的なParticle Mesh Sphere
- Cyan、Electric BlueおよびVioletのParticle
- 細いMesh Lineと半透明の面
- Sphere中心のCyan Glow
- 小さなWhite Highlight
- 小さい表示Sizeでも認識できる主要Particleと輪郭

### 21.4 Excluded Visual Elements

- 同心円Ring
- Tick / Scale Mark
- Amber Accent
- Dark Backgroundまたは矩形Surface
- `Alice`、`A.L.I.C.E`、`待機中`その他すべてのText
- Status Icon、ButtonまたはControl
- 人物、顔、身体またはCharacter
- Logo、第三者Brand MarkまたはWatermark
- 画像端へ接触する強いOuter Glow

Ring、Tick、Amber Accent、Outer Glow、RotationおよびPulseはUI-013どおりFlutter側が描画・制御する。

### 21.5 Composition

- SphereをCanvas中央へ配置する
- Sphere本体の直径をCanvasの約78〜80%とする
- 全周へ10%以上の透明Marginを確保する
- Particle、Meshおよび必要なCore GlowをCanvas端で切らない
- 中心光をCanvas中央から大きくずらさない
- 左右または上下に方向性を持つShadowを追加しない

### 21.6 Flutter Registration

`pubspec.yaml`では次のLocal Assetを登録する。

```yaml
flutter:
  assets:
    - assets/images/alice_core/alice_core_base.png
```

- `AssetImage`等のFlutter標準機能で読み込む
- Networkから取得しない
- Base64 TextとしてSource Codeへ埋め込まない
- File PathをWidgetごとに重複Hard Codeせず、Presentation Asset定義へ集約してよい
- Asset DecodeまたはLoadをDomain / Application Layerへ持ち込まない

### 21.7 Quality Acceptance Criteria

次をすべて満たしたAssetだけを採用する。

- Alpha Channelが存在し、背景が完全透過である
- 黒または白の矩形背景が残っていない
- 96、180および260 logical pixelsでSphereを認識できる
- Cyan、BlueおよびVioletのBalanceが採用Mockupと整合する
- 中心Glowが白く潰れず、Particle Meshを判別できる
- 小さい表示でも主要Particleと輪郭が完全に消えない
- ParticleまたはGlowがCanvas端で切れていない
- Text、Logo、Watermarkおよび外周Ringを含まない
- Dark Background上で不自然なWhite / Black Haloが発生しない
- File Sizeが2 MBを超える場合は、Visual品質を確認したうえでLossless Optimizationを行う

### 21.8 Provenance and Security

- Project Alice専用に生成したAssetを使用する
- 添付されたJARVIS画像、素材SiteのPreview、ScreenshotまたはWatermark付き画像を直接使用しない
- Third-party URLをRuntime Asset Sourceにしない
- Prompt、生成日および採用判断を本DecisionとVersion Historyから追跡可能にする
- EXIF等の不要なMetadataをRelease Assetへ残さない

### 21.9 Phase Boundary

- Phase 1では`alice_core_base.png`一つだけをCore Base Assetとして作成する
- State別にほぼ同一のPNGを複数作成しない
- Phase 2〜4の流動表示用Rive、Shader、Videoまたは追加Frame Assetを先行作成しない
- 後続PhaseでRendererを変更する場合もPhase 1 Assetを無条件に上書きせず、FallbackまたはMigration要否を判断する

### 21.10 Test and Validation

- Assetの存在とDecode成功をAutomated Testで確認する
- Image Width / Heightが1024 x 1024であることを確認する
- Alpha Channelと透明Corner Pixelを確認する
- Light CheckerboardとProject Alice Dark Backgroundの両方でEdgeを目視確認する
- 96、180、260 logical pixelsでScreenshot / Golden Testを行う
- Ring / Glow Layerと合成した状態でClipping、Haloおよび過度な白飛びがないことを確認する

### 21.11 Generation and Validation Record

| Item | Result |
|---|---|
| Generated | 2026-08-17 JST |
| Source Type | User提供Visual Referenceを方向性として使用したProject Alice専用生成Asset |
| Final Filename | `alice_core_base.png` |
| Geometry | 1024 x 1024 pixels |
| Color Space | sRGB |
| Alpha | TrueColorAlpha、四隅完全透過 |
| File Size | 約1.15 MB |
| Visible Sphere Bounds | 約78〜80%（実質Alpha 5%以上の範囲） |
| Included | Particle Mesh Sphere、Cyan Center Glow |
| Excluded | Ring、Tick、Amber Accent、背景、文字、Logo、Watermark |

生成Promptは、User提供画像を形状・粒子密度・Mesh Texture・配色のVisual Referenceとして使用し、非対称で大きな凹凸を持つCyan / Electric Blue / VioletのParticle Mesh Sphere、中心Glow、完全透過背景、10%以上のMargin、文字・Ring・背景・第三者Brandの禁止を指定した。生成画像に含まれた格子背景は背景除去工程で実Alphaへ置換し、球体の形状と色を維持した。その後、1024 x 1024への高品質縮小、透明Margin調整、Metadata除去およびLossless PNG圧縮だけを実施した。

### 21.12 Trade-off

PNGは高度な流動表現を持たず、Decode後のMemory Sizeも圧縮File Sizeより大きくなる。一方、透過、細いParticle Detail、Flutter標準対応および再現性を優先でき、Phase 1のAssetが一枚だけであるためScopeを小さく保てる。

### 21.13 Decision Status

| Decision ID | Topic | Decision | Status |
|---|---|---|---|
| UI-016 | Alice Core Asset | 1024 x 1024、sRGB、透明PNGのSphere専用Asset。Ring、背景、文字、Animationは含めない | Accepted — 2026-08-17 JST |

---

## 22. Pending Design Topics

Phase 1 Frontend UI Detailed Designに、未確定の設計項目はない。

Phase 2〜4のAmbient / TOP、Desktop、Voiceおよび流動Particle Mesh Rendererは、各PhaseのRequirementを入力として再設計する。Phase 1では実装しない。

---

## 23. Requirements Traceability

| Requirement ID | UI Design Responsibility |
|---|---|
| `P1-FR-001` | Conversation Screen、Text Input、Send操作 |
| `P1-FR-002` | Single Conversation、一画面構成 |
| `P1-FR-004` | Alice IdentityをAlice Core、Visual / Content Presentationへ反映 |
| `P1-FR-005` | Streaming ResponseとCanonical Completion表示 |
| `P1-FR-006` | BackendをConversation HistoryのSource of Truthとし、Frontendへ会話内容を永続化しない |
| `P1-FR-007` | History表示とOlder Page読込 |
| `P1-FR-008` | Result Unknown、Retry、Replayの理解可能なUI |
| `NFR-003` | Secret / Internal DetailをUIへ公開しない |
| `NFR-004` | Phase 1に不要なScreen / Navigationを追加しない |
| `NFR-005` | `AliceCoreView`の描画責務をConversation StateおよびUse Caseから分離する |
| `NFR-006` | Retry、Partial Failure、Draft、ScrollおよびCanonical StateをFailure時にも保護する |
| `NFR-007` | Screen StateとInteractionをWidget Test可能にする |
| `NFR-008` | User向け状態表示と内部Observabilityを分離する |
| `NFR-009` | Flutter / Package Version、Visual TokenおよびAsset Contractを固定する |

---

## 24. AI Coding Assistant Rules

本ドキュメントが`Approved`になるまで、AI Coding AssistantはVisual Detailを独自に確定して実装してはならない。

承認後も次を独自変更してはならない。

- Screen数とNavigation
- Region構造
- Screen Stateと表示条件
- User Interaction
- Color / Typography / Spacing Token
- Accessibility Requirement
- Scope外機能の追加

局所的なWidget分割、private Method名および設計値をそのまま表現する標準的なFlutter実装は合理的に決定してよい。

---

## 25. Review Status

| Item | Value |
|---|---|
| Current Status | Approved |
| Review Result | Approved — No blocking findings |
| Review Date | 2026-08-17 JST |
| Confirmed Decisions | UI-001 Screen Structure / UI-002 Information Architecture / UI-003 Wireframe / UI-004 Message Presentation / UI-005 Markdown Renderer Dependency / UI-006 Composer Structure / UI-007 Empty State / UI-008 Loading / Streaming Presentation / UI-009 Error / Retry Presentation / UI-010 History Pagination Interaction / UI-011 Keyboard / Scroll Detailed Behavior / UI-012 Visual Direction / UI-013 Alice Core Component / UI-014 Phase 1 Component Structure / UI-015 Phase 1 Accessibility / UI-016 Alice Core Asset |
| Open Design Decision | None |
| Asset Status | `alice_core_base.png` generated and validated |
| Next Step | Phase 1 Frontend Implementation Preparation |

---

## 26. UI-XP-001 Cross-Phase Alice Experience Principle — 2026-09-04

**Status:** Accepted / Integrated
**Related ADR:** ADR-017

Hierarchy:

`UI-XP-001 → UI-012 → Phase-specific UI → Screen / Component / FIP`

- Primary Experience = Ambient / Voice + Conversation
- Contextual Capability = Memory usage, Tool result, Agent status, Approval, Permission request, Unknown Outcome
- Subordinate Management = Memory Management, Permission Management, Execution Detail, Trusted Devices, Safety, Settings, Audit / History
- Capability Growth ≠ Primary Navigation Growth
- Alice Core = Presence; not logo / button / loading spinner / backend entity
- `AliceCoreState → Presentation Contract → Renderer → Visual Expression`; Renderer is replaceable
- Current visual direction: Fluid / Organic / Particle Mesh / Cyan / Electric Blue / Violet / Internal Light / Soft Energy Flow
- Presentation patterns: Ambient / Voice, Conversation, Agent Running, Approval Required, Permission Required, Execution Detail, Unknown Outcome / Verifying, Emergency Stop
- These are states of one Alice Experience, not separate permanent modes
- Desktop may use Spatial Contextual Presentation around Alice Core; Permanent Capability Dashboard is not the default
- Short system semantics are English-first; meaning-critical / human communication is Japanese-first
- Visual Drift Gate is a formal review requirement

UI-XP-001 extends UI-012 without changing Phase 1 implementation scope. Phase 1 does not pre-implement Phase 4 routes, packages, screens, or renderer technology.


---

## Visual Source-of-Truth Integration Resolution — 2026-09-05

**Status:** Accepted / Integrated  
**Source:** UI-BL-001 / UI-VIS-SOT-001 / accepted Visual Detailed Design / CVD / Golden View decisions

Normative visual hierarchy for all implementation and review work:

`UI-BL-001 → UI-VIS-SOT-001 → accepted CVD / Golden View → UI-XP-001 / UI-012 → Phase-specific UI → Component / FIP → Implementation → Generated Mockup`

Rules:
- Conversation + Alice Core remain the primary Alice experience.
- Alice Core represents Presence, never execution authority.
- `AliceCoreState → Presentation Contract → AliceCoreRenderer → Visual Expression`.
- Renderer technology remains Presentation-only and replaceable.
- Application / Domain logic must not depend on particle, shader, asset, geometry, animation-controller, or canvas implementation details.
- Phase 1 does not pre-implement speculative Phase 2–4 renderer frameworks. Replaceability is achieved through Presentation isolation and dependency direction within the implemented scope.
- Capability Growth must not become permanent Dashboard / Primary Navigation Growth.
- Generated mockups never override the accepted visual Source of Truth.
- Any visual mismatch is Visual Drift and blocks UI approval until corrected or explicitly superseded by a new user-approved baseline decision.
