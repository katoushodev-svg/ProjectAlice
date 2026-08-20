# FIP-002 App Configuration - GitHub Copilot Task Prompt

## Usage

VS Codeで`/Users/shoei/dev/Project-Alice`を開き、GitHub Copilot ChatのAgent Modeへ「Task Prompt」Sectionをそのまま入力する。

## Task Prompt

```text
Task ID:
FIP-002 App Configuration

Purpose:
Project Alice Phase 1 Flutter Frontendへ、安全なBuild-time Backend URL Configuration、Application Lifecycle単位のHTTP Client Provider、およびDevelopment限定ATS設定を追加する。

Repository:
- Monorepo Root: /Users/shoei/dev/Project-Alice
- Flutter Project: frontend/
- Flutter Project Name: alice
- Target: iOS Simulator / iOS 15以上

Source of Truth:
- docs/frontend-design.md Section 4, 5, 12, 13, 16, 18
- docs/security-design.md Section 8, 10, 13, 21, 22
- docs/frontend-implementation-plan.md FIP-002
- docs/frontend-macos-setup-guide.md

Current State:
- FIP-001 Project ScaffoldはAPPROVED
- lib/main.dartはAliceAppを起動するEntry Pointのみ
- lib/app/alice_app.dartにAliceAppがある
- test/app/alice_app_test.dartが成功している
- Bundle Identifierはcom.projectalice.assistant
- Minimum iOSは15.0
- Portrait only
- XcodeからiPhone 16 Pro Simulatorで起動成功済み

Approved Dependencies to Add:
- flutter_riverpod: 3.4.2
- http: 1.6.0

Do not add any other dependency.

Required Files and Behavior:

1. frontend/lib/app/app_configuration.dart
- ImmutableなAppConfigurationを作成する
- Backend Base URLをUriとして保持する
- Exception名はAppConfigurationExceptionとする
- AppConfiguration.fromEnvironment({required bool allowLocalHttp})はString.fromEnvironment('ALICE_API_BASE_URL')から値を読む
- AppConfiguration.fromRaw({required String rawBaseUrl, required bool allowLocalHttp})をTest可能なValidation Entryとする
- 暗黙のDefault URLを持たない
- runAppより前にConfigurationを生成・検証できるようにする
- Test用にRaw StringとallowLocalHttpを明示できるFactoryを設ける
- Invalid時は専用Configuration Exceptionを送出する
- Exception MessageへRaw URL、Host、CredentialまたはSecretを含めない

Validation Rules:
- Missing、Empty、Whitespace onlyを拒否する
- 前後にWhitespaceを含む値を拒否し、暗黙Trimしない
- Absolute URI、Scheme、Hostを必須とする
- Schemeはhttpまたはhttpsだけ許可する
- httpはallowLocalHttp=trueかつHostが完全一致で127.0.0.1の場合だけ許可する
- Profile / Release相当ではallowLocalHttp=falseとしてhttpを拒否する
- User Info、Query、Fragmentを拒否する
- Pathは空または/だけ許可する
- 内部では末尾SlashなしのOriginへ正規化する
- Base URLに/api/v1等のAPI Pathを許可しない

2. frontend/lib/app/app_providers.dart
- appConfigurationProviderを定義し、未Overrideの利用は明示的に失敗させる
- main.dartで検証済みConfigurationをProvider Overrideする
- httpClientFactoryProviderを定義し、Defaultではhttp.Client.newを返す
- httpClientProviderはFactory ProviderからClientを一度生成する
- 同じProviderContainerではClient Instanceを一つだけ生成して再利用する
- ref.onDisposeでClient.close()を一度呼ぶ
- TestでTracking Client Factoryを注入できる最小の構造にする
- HTTP Request、API Endpoint、JSON、SSEまたはRetryは実装しない

3. frontend/lib/main.dart
- AppConfigurationをrunAppより前に生成する
- kDebugModeをallowLocalHttpへ渡し、Debug BuildだけLocal HTTPを許可する
- Profile / ReleaseはLocal HTTPを許可しない
- ProviderScopeをApplication Rootに一つだけ配置する
- 検証済みAppConfigurationをProvider Overrideで渡す
- Application Logicをmain.dartへ追加しない

4. Xcode Local Configuration
- frontend/ios/Flutter/Debug.xcconfigからLocal.xcconfigをOptional Includeする
- frontend/ios/Flutter/Local.xcconfigを.gitignoreへ追加する
- frontend/ios/Flutter/Local.xcconfig.exampleを作成する
- exampleには実Network AddressやBase64実値を含めず、Placeholderと作成方法だけを書く
- Local.xcconfigは作成・Commitしない
- Profile / ReleaseからLocal.xcconfigを読み込まない
- Local.xcconfigのDART_DEFINESは既存値を$(inherited)で保持する

5. Development-only ATS
- 現在のRunner Info.plistをRelease / Profile用として維持する
- frontend/ios/Runner/Info-Debug.plistを追加し、必要な既存Keyを維持する
- Debug用だけにNSAppTransportSecurity / NSAllowsLocalNetworking=trueを追加する
- Xcode Build SettingのINFOPLIST_FILEをConfiguration別に設定する
- DebugだけがDevelopment用Property Listを参照する
- Profile / Releaseは通常のInfo.plistを参照する
- NSAllowsArbitraryLoadsをどこにも追加しない

Required Tests:

frontend/test/app/app_configuration_test.dart
- Valid loopback HTTP in Development
- Valid HTTPS
- Missing / Empty / Whitespace / surrounding Whitespace rejection
- Relative URI / Unsupported Scheme rejection
- Non-loopback HTTP rejection
- Loopback HTTP rejection when allowLocalHttp=false
- User Info / Query / Fragment / API Path rejection
- Root Slash normalization
- Exceptionが入力URLを露出しないこと

frontend/test/app/app_providers_test.dart
- 同一ProviderContainerで同じClientが再利用されること
- Container dispose時にTracking Clientのcloseが一度呼ばれること
- 実Networkへ接続しないこと

Existing Test:
- test/app/alice_app_test.dartを壊さない

Prohibited Changes:
- Conversation Model、Gateway、API Client、Endpoint、JSONまたはSSEの実装
- POST RetryまたはRetry Middleware
- uuid、Markdown Packageまたはその他Dependencyの先行追加
- OpenAI API Key、AWS Credential、Authentication Tokenの追加
- Actual Base URLまたはBase64 DART_DEFINES ValueのCommit
- .envの追加
- Conversation Content、Configuration ValueまたはEnvironment DumpのLogging
- NSAllowsArbitraryLoads
- Release / ProfileへのLocal HTTP例外
- Backend Bind Addressの変更
- FIP-001の表示を最終UIへ変更すること
- assets/の変更

Required Verification Commands:
cd /Users/shoei/dev/Project-Alice/frontend
dart format lib test
flutter pub get
flutter analyze
flutter test
plutil -lint ios/Runner/Info.plist
plutil -lint ios/Runner/Info-Debug.plist

追加Static Check:
- DebugだけがDebug用Info Property Listを参照する
- Profile / ReleaseはInfo.plistを参照する
- NSAllowsArbitraryLoadsがRepository内に存在しない
- Local.xcconfigがGit管理対象外である
- Actual ALICE_API_BASE_URL値やCredentialがGit管理Fileへ追加されていない

Completion Report:
- 変更File一覧
- Configuration Validation Ruleの実装対応表
- Test一覧と結果
- flutter analyze結果
- flutter test結果
- plutil結果
- ATS Configuration別参照結果
- git diff --check結果
- Scope外変更がないこと

設計書と矛盾する点または未指定事項を発見した場合、推測で実装せず作業を停止して報告すること。
```

## Local Configuration After Copilot Work

Copilotの実装完了後、Git管理外の`frontend/ios/Flutter/Local.xcconfig`をDeveloper Machine上で作成する。実際のコマンドはCopilotのCompletion Reportと生成された`Local.xcconfig.example`を確認してから実行する。
