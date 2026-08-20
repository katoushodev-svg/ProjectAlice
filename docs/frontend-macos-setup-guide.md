# Project Alice Frontend macOS Setup Guide

最終更新: 2026-08-17 JST

## 1. Purpose

本書は、新しいMacでProject Alice Phase 1 Frontendの開発環境を再構築し、iOS SimulatorでAliceを起動するための手順書である。

本書はmacOS向けとし、Phase 1の対象PlatformはiOSのみとする。Android SDKの導入は対象外である。

## 2. Verified Baseline

現在の開発環境で動作確認済みのBaselineは次のとおり。

| Item | Version / Decision |
|---|---|
| Host Architecture | Apple Silicon `arm64` |
| macOS | `15.2` |
| Flutter | `3.47.0` Stable |
| Dart | `3.13.0` Stable |
| DevTools | `2.60.0` |
| Xcode | `16.3`（Build `16E140`） |
| CocoaPods | `1.17.0` |
| iOS Simulator Runtime | `iOS 18.4` |
| Standard Simulator | `iPhone 16 Pro` |
| Flutter Project Name | `alice` |
| iOS Display Name | `Alice` |
| Bundle Identifier | `com.projectalice.assistant` |
| Minimum iOS Version | `iOS 15.0` |
| Orientation | Portrait only |

Versionを変更する場合は、AIや開発者が独自判断で更新せず、`development-environment.md`等のSource of Truthを先に更新する。

## 3. Directory Policy

Flutter SDKとProject Alice Repositoryは分離する。

例:

```text
~/develop/
└── flutter/              # Flutter SDK

~/dev/
└── Project-Alice/        # Project Alice Repository
    ├── README.md
    ├── docs/
    └── frontend/
```

配置先は上記と完全に同じでなくてもよい。ただし、次を守る。

- Project AliceをFlutter SDK配下へ作成しない
- 空白や日本語を含むPathを避ける
- iCloud Drive等、自動同期による競合が起きやすい場所を避ける
- Git Repository Rootを`Project-Alice/`とする

## 4. Install Xcode

1. App StoreまたはApple Developer SiteからXcodeをインストールする。
2. Xcodeを一度起動し、初回Setupを完了する。
3. TerminalでCommand Line Toolsを設定する。

```bash
sudo sh -c 'xcode-select -s /Applications/Xcode.app/Contents/Developer && xcodebuild -runFirstLaunch'
```

必要に応じてLicenseを確認し、同意する。

```bash
sudo xcodebuild -license
```

Xcode Versionを確認する。

```bash
xcodebuild -version
```

## 5. Install iOS Simulator Runtime

Xcodeの`Settings`からPlatform / Component管理画面を開き、設計Baselineに対応するiOS Simulator Runtimeを導入する。

現在のBaselineは`iOS 18.4`である。Commandで利用可能なRuntimeとDeviceを確認できる。

```bash
xcrun simctl list runtimes
xcrun simctl list devices available
```

`iPhone 16 Pro`が存在しない場合は、XcodeのDevice / Simulator管理画面から追加する。

## 6. Install Flutter SDK

### 6.1 Download

Flutter公式SDK Archiveから、Apple Silicon用のFlutter `3.47.0` Stableを取得する。

新しいVersionへ無条件で置き換えない。指定Versionが取得できない場合は、実装を開始する前にTechnology Baselineを再確認する。

### 6.2 Extract

例として`~/develop/`へ展開する。

```bash
mkdir -p ~/develop
unzip ~/Downloads/<flutter-sdk-archive>.zip -d ~/develop
```

Flutter SDKは管理者権限が不要な場所へ配置する。Flutter Commandに`sudo`を付けない。

### 6.3 Add PATH

Zshの`~/.zprofile`へFlutter SDKを追加する。

```bash
echo 'export PATH="$HOME/develop/flutter/bin:$PATH"' >> ~/.zprofile
source ~/.zprofile
```

確認する。

```bash
flutter --version
dart --version
```

期待値:

```text
Flutter 3.47.0
Dart 3.13.0
```

## 7. Install Homebrew

Homebrewが未導入の場合、公式Install Commandを実行する。

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Apple Silicon Macで`brew`が見つからない場合、次を実行する。

```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

確認する。

```bash
brew --version
```

## 8. Install CocoaPods

macOS標準のsystem Rubyを直接変更せず、HomebrewからCocoaPodsを導入する。

```bash
brew install cocoapods
```

確認する。

```bash
pod --version
```

検証済みVersionは`1.17.0`である。

## 9. Verify Toolchain

```bash
flutter doctor -v
```

Phase 1で必要な項目:

- Flutter: `[✓]`
- Xcode: `[✓]`
- CocoaPods Versionが表示される
- Network Resources: `[✓]`

Android Toolchainが`[✗]`でも、Phase 1はiOS ScopeのためBlockerではない。

## 10. Get Project Alice on Another Mac

通常、新しいMacではFlutter Projectを再生成しない。Git Repositoryから既存のProject Aliceを取得する。

```bash
cd ~/dev
git clone <project-alice-repository-url> Project-Alice
cd Project-Alice/frontend
```

Repository URLと配置先は実際の環境に合わせる。

重要:

- 既存Repositoryへ`flutter create`を再実行しない
- `ios/`、`lib/`、`pubspec.yaml`および`assets/`はGit管理された内容を使用する
- Bundle Identifier、iOS 15.0およびPortrait設定はRepositoryの設定を正とする

## 11. Restore Dependencies

Frontend DirectoryでDart / Flutter Dependenciesを取得する。

```bash
flutter pub get
```

iOS Native Dependenciesに問題がある場合は、次を実行する。

```bash
cd ios
pod install
cd ..
```

通常は`flutter run`またはXcode Build時にも必要な処理が実行されるため、問題がない状態で毎回`pod install`を実行する必要はない。

## 12. Start Alice with Xcode UI

Frontend DirectoryでWorkspaceを開く。

```bash
open ios/Runner.xcworkspace
```

Xcodeで次を選択する。

1. Scheme: `Runner`
2. Device: `iPhone 16 Pro`
3. 左上のRun Button、または`Command + R`

成功条件:

- iPhone 16 Pro Simulatorが起動する
- Alice AppがSimulatorへInstallされる
- Alice Appが自動起動する
- Build Errorが発生しない

停止するときはXcode左上のStop Buttonを押す。

`Runner.xcodeproj`ではなく、CocoaPodsを含む`Runner.xcworkspace`を開く。

## 13. Verify Project

```bash
cd ~/dev/Project-Alice/frontend
flutter analyze
flutter test
```

期待結果:

- `flutter analyze`: No issues found
- `flutter test`: All tests passed

初期Scaffold作成直後で`test/`がまだ存在しない場合、`Test directory "test" not found.`は環境構築失敗ではない。FIP-001で初期Widget Testを追加した後は、`flutter test`成功を必須とする。

## 14. Initial Project Creation Reference

このSectionはRepositoryを新規作成し直す場合の復旧用情報であり、別PCで通常開発を開始するときには実行しない。

```bash
cd /path/to/Project-Alice/frontend

flutter create \
  --platforms=ios \
  --project-name=alice \
  --org=com.projectalice \
  --empty \
  .
```

生成直後のBundle Identifierは`com.projectalice.alice`になるため、Xcodeの`Runner` Target設定で正式値`com.projectalice.assistant`へ変更する。

あわせて次を設定する。

- Minimum Deployment Target: iOS 15.0
- Device Orientation: Portrait only
- Display Name: Alice

## 15. Troubleshooting

### 15.1 `flutter: command not found`

```bash
source ~/.zprofile
which flutter
flutter --version
```

解決しない場合、`~/.zprofile`のFlutter SDK Pathと実際の配置先が一致しているか確認する。

### 15.2 `pod: command not found`

```bash
brew install cocoapods
pod --version
```

`brew`も見つからない場合は、Section 7のHomebrew PATH設定を行う。

### 15.3 Simulatorが`flutter devices`に表示されない

```bash
open -a Simulator
flutter devices
```

Simulatorの起動完了後に再確認する。

### 15.4 Xcode BuildでPod関連Errorが出る

```bash
cd /path/to/Project-Alice/frontend
flutter pub get
cd ios
pod install
cd ..
```

その後、`ios/Runner.xcworkspace`を開き直す。

### 15.5 Android SDK Error

Phase 1では対応しない。iOSのFlutter / Xcode項目が正常であれば開発を継続できる。

## 16. Security Notes

- OpenAI API KeyやCredentialをFrontendへ保存しない
- SecretをSource Code、`pubspec.yaml`、Git RepositoryへCommitしない
- 新しいMacでSecretが必要になった場合は`security-design.md`の正式手順に従う
- Phase 1 BackendをPublic Internetへ公開しない

## 17. Completion Checklist

- [ ] XcodeをInstall・初回起動済み
- [ ] Xcode Command Line Tools設定済み
- [ ] iOS 18.4 Simulator Runtime導入済み
- [ ] iPhone 16 Pro Simulator利用可能
- [ ] Flutter 3.47.0利用可能
- [ ] Dart 3.13.0利用可能
- [ ] Homebrew利用可能
- [ ] CocoaPods 1.17.0利用可能
- [ ] `flutter doctor -v`のiOS関連項目が正常
- [ ] Project Alice Repository取得済み
- [ ] `flutter pub get`成功
- [ ] `flutter analyze`成功
- [ ] `flutter test`成功
- [ ] XcodeからAliceをSimulatorで起動成功

## 18. References

- Flutter Manual Installation: https://docs.flutter.dev/install/manual
- Flutter iOS Setup: https://docs.flutter.dev/platform-integration/ios/setup
- Homebrew: https://brew.sh/
- CocoaPods Formula: https://formulae.brew.sh/formula/cocoapods
- CocoaPods Getting Started: https://guides.cocoapods.org/using/getting-started.html

