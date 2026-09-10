# Project Alice Autonomous Manufacturing Pipeline

## 正式設計および運用仕様

| 項目 | 内容 |
|---|---|
| 文書種別 | Project Alice 製造自動化の正式設計および運用仕様 |
| 対象 | Phase 1〜4、Cross-Phase Integration、最終製造完了ゲート |
| 版 | 1.0 |
| 作成日 | 2026-09-10 JST |
| 基準状態 | Phase 0 は `COMPLETE / Implementation Authorized`。Phase 1 は FIP-001〜004 完了、FIP-005 製造中、FIP-006〜012 未実装。Phase 2〜4 は設計完了・製造未着手。 |
| Source of Truth | 承認済みの正式設計、各FIP Implementation Plan、`project-alice-manufacturing-progress-register.md`。矛盾時は本書で推測せず、該当するSource of Truthへ差し戻す。 |

## 1. 目的

本仕様は、承認済みのFIPを単位として、次の工程を安全に連続実行するための製造運用を定める。

```text
Implementation → Automated Test → AI Review → Fix（明確な指摘のみ）
    → Re-test → Re-review → PASS → Commit / PR → 次の実行可能なFIP
```

目的は、PCを操作できない時間にもMac mini上で製造を継続しつつ、Project Aliceの既存設計、責務境界、Security/Safety Boundary、ApprovalおよびExecution Authorityを変更させないことである。

自律Pipelineは「承認済み仕様に対する実装・検証・局所修正」を行う。設計の再定義、仕様解釈が必要な変更、権限または安全境界に関する判断は行わず、Human Escalationへ停止する。

## 2. 適用範囲と非対象

| 含む | 含まない |
|---|---|
| FIP単位の実装、テスト、レビュー、明確な指摘の修正、再レビュー | Phase 0〜4の再設計、要件追加、承認済み仕様の書換え |
| PASS後の隔離Commit、明示的に有効化された場合のPR作成 | API、DB schema、依存関係、Security/Safety Boundaryの自動変更 |
| 進捗台帳、実行証跡、停止理由の記録 | mainへの直接push、force push、reset、履歴改変 |
| Phase品質ゲート、統合・最終ゲートの証跡集約 | Phase 2〜4の製造単位・順序・並行可否の推測 |

## 3. 基本原則

1. 承認済みの正式設計および当該FIP計画のみを作業根拠とする。
2. FIP、Phase、Architecture、層、権限の既存境界を変更しない。
3. FIP完了は実装完了ではない。必須テストとレビューの証跡が揃った時だけ完了とする。
4. 停止は失敗ではない。不明確・高リスクな状態を安全に人間へ引き渡す正常な遷移である。
5. 未決定の技術選定を、本書またはPipeline実装が既成事実化しない。

## 4. FIP 自律製造状態機械

| 状態 | 意味 | 次の遷移 |
|---|---|---|
| `READY` | 前提、承認状態、依存FIP、作業ツリー、実行環境を確認済み | `IMPLEMENTING` |
| `IMPLEMENTING` | 対象FIPだけを実装中 | 記録後に `TESTING` |
| `TESTING` | FIP計画に定義された必須テストを実行中 | PASSなら `REVIEW`、失敗なら `FIXING` または `ESCALATED` |
| `REVIEW` | 仕様、差分、テスト、境界を独立してレビュー中 | `QUALITY_PASS`、`FIXING`、または `ESCALATED` |
| `FIXING` | 明確で局所的な指摘だけを修正中 | 必ず `TESTING` へ戻る |
| `QUALITY_PASS` | テスト・再レビュー・証跡が揃った状態 | `COMMIT_PR` |
| `COMMIT_PR` | 許可されたGit運用に従ってCommitまたはPRを作成 | 成功時 `COMPLETE`、失敗時 `ESCALATED` |
| `ESCALATED` | 人間の判断待ち。新規変更を停止し、差分を保持 | 人間の明示決定後 `READY` または `CANCELLED` |
| `COMPLETE` | G1 FIP完了を記録済み | 後続FIPの `READY` 判定へ |

標準経路は `READY → IMPLEMENTING → TESTING → REVIEW → QUALITY_PASS → COMMIT_PR → COMPLETE` とする。修正経路は `REVIEW → FIXING → TESTING → REVIEW` に限定する。

## 5. Ready Gate

Orchestratorは、次をすべて確認できる場合だけFIPを開始する。いずれか一つでも満たさない場合は開始せず、理由を記録する。

- 対象FIPが `Approved / Implementation Ready` であり、対象範囲、禁止事項、受入条件、テスト計画、関連するSource of Truthを参照できる。
- 前提FIPおよび依存ゲートを通過している。Phase 1は現行台帳の順序を基準とし、個別の承認済みFIP計画に異なる依存関係があればそちらを優先する。
- 作業ツリーに未知の未コミット変更、未解決コンフリクト、別作業の差分がない。
- 実行環境、必要な認証、テスト用依存サービス、ツール版が承認済み実行プロファイルを満たす。
- 同じFIPを処理中の実行がなく、run IDと実行ロックを作成できる。
- 自動CommitまたはPR作成を有効にする場合は、その権限とブランチ保護を事前に確認できる。

## 6. 実装 テスト レビュー 修正

### 6.1 Implementation

実装Agentへの入力は、対象FIP計画、関連設計、前提FIPの完了証跡、許可パス、禁止事項、必須テスト、run budgetで構成する。AgentはFIP外の型、依存、API、設定、リファクタリング、将来用の実装を追加しない。

### 6.2 Automated Test

必須テストは各FIP計画をSource of Truthとする。一般にformat、静的解析、focused test、必要な回帰テストを実行するが、FIPに定義されていないテストをPASS根拠として推測しない。例えばFIP-003にある `dart format`、`flutter analyze`、focused test、full `flutter test` はFIP-003の明示要件であり、他FIPへの自動的な横展開ではない。

テスト不能、認証不能、環境異常は製品不具合と区別して記録する。再試行は一度までとし、解決しなければ停止する。

### 6.3 AI Review

レビューは実装Agentと可能な限り独立したコンテキストまたは役割で実施する。入力は、FIP受入条件、関連設計、差分、テスト結果、既知の制約とする。レビューは以下を確認する。

- FIPのIn Scope、Out of Scope、受入条件との整合
- 正式設計、前提FIP、Phase間境界との整合
- テスト結果と変更の対応関係
- API、DB、依存、Security/Safety、Approval、Execution Authorityへの影響
- 機密情報、会話内容、識別子の安全な取扱い

レビュー結論は必ず次の三値に正規化する。

| 結論 | 意味 | Pipelineの行動 |
|---|---|---|
| `PASS` | 既知の必須要件を満たし、重大・修正必須の問題がない | `QUALITY_PASS` へ進む |
| `FIXABLE_FINDINGS` | 根拠、対象、期待結果が明確で、既存境界内の局所変更で解決できる | `FIXING` へ進む |
| `ESCALATE` | 仕様が曖昧・矛盾、または境界・安全性・技術決定に触れる | 即時停止 |

根拠のない一般論や任意の改善提案は、FAILまたは自動修正の根拠にしない。

### 6.4 自動修正の可否

| 分類 | 自動修正 | 例 |
|---|---|---|
| 実装と明示仕様の不一致 | 可 | 指定されたValidation、状態遷移、null処理、表示分岐の不足 |
| テスト可能な品質不備 | 可 | 既存責務内のテスト失敗、format/analyze違反、明確な回帰 |
| 境界を変えない局所欠陥 | 可 | 不変性違反、安全なエラー取扱い漏れ |
| 仕様の曖昧さ・矛盾 | 不可 | 二つの正式資料が異なる挙動を要求する |
| API、DB、依存関係の追加・変更 | 不可 | endpoint、schema migration、新package |
| Security、Safety、権限境界 | 不可 | 認可、Approval、Execution Authorityの解釈変更 |
| PhaseまたはArchitecture Boundary | 不可 | 層の責務移動、Phase 2〜4の先行実装 |

## 7. 無限ループ防止と停止条件

既定の保守的なrun budgetは、同一FIPにつき自動修正最大2回、レビュー最大3回（初回を含む）とする。FIP固有またはPhase固有の承認済み制約がある場合はそちらを優先する。上限超過時は成功見込みを推測して継続せず `ESCALATED` へ遷移する。

| 停止トリガー | 必須処置 |
|---|---|
| 同じ原因・同じ失敗の再発 | 即時停止。全試行の差分、結果要約、再現手順を添える。 |
| レビューが曖昧、矛盾、根拠不足 | 自動修正しない。判定不能として停止する。 |
| Source of Truthとの矛盾 | 設計を推測して補正しない。矛盾箇所・影響を示して停止する。 |
| API、DB、依存、Security/Safety、Phase Boundaryへの影響 | 差分を拡大せず、必要な意思決定を明示して停止する。 |
| Git競合、未知の変更、push拒否 | 既存変更を保護して停止。rebase、force push、resetは自律実行しない。 |
| 実行ロックまたは監査記録への書込み失敗 | 状態の信頼性を失うため停止。完了扱いにしない。 |

## 8. Human Escalation と再開

エスカレーションは「人間に何を判断してほしいか」を一つずつ明確にする。通知・記録に秘密情報、会話本文、認証情報を含めない。

| 記録項目 | 内容 |
|---|---|
| 識別 | run ID、Phase、FIP、ブランチ、開始・停止時刻、実行環境 |
| 停止理由 | 停止トリガー、影響する設計・FIP・ファイルへの参照 |
| 事実 | 実行済みコマンド、結果要約、レビュー結論、試行回数。機密はマスクする。 |
| 保持状態 | 未コミット差分、Commit hash、PR URLまたは未作成、再現手順 |
| 必要な決定 | 承認、仕様解釈、設計修正、環境復旧、取消のいずれか |
| 再開条件 | 誰が何を承認・修正した後に、どのReady Gateから確認するか |

再開承認後も、PipelineはReady Gateから再検証する。設計資料が更新された場合、旧レビューをそのままPASS根拠にせず、影響範囲を再レビューする。

## 9. Git ブランチ Commit PR 方針

既存資料で具体的なブランチ名・マージ方法は確定していないため、ここでは原則だけを正式化する。

| 項目 | 方針 |
|---|---|
| 作業単位 | 1 FIPにつき1つの隔離ブランチまたは同等の隔離作業領域。複数FIPを一つのCommitに混在させない。 |
| 命名 | 具体形式は未決定。FIP番号とrun IDを追跡できる規約を別途承認する。 |
| Commit | `QUALITY_PASS` と証跡記録後のみ作成。内容は当該FIPと必要な記録に限定する。 |
| PR | 明示的に有効化された運用モードでのみ作成する。FIP、根拠、テスト、レビュー、未解決事項を記載する。 |
| 禁止 | mainへの直接push、force push、reset、履歴改変、無関係な整形、秘密情報のCommit。 |
| マージ | 人間または既に承認されたブランチ保護ルールによる。Pipelineは自律マージを前提にしない。 |
| Git失敗 | CommitまたはPR作成失敗時は完了扱いにせず、差分を保持して `ESCALATED` へ進む。 |

## 10. Phase と最終統合への適用

| 対象 | Pipelineの扱い |
|---|---|
| Phase 1 | 現行台帳ではFIP-005完了後にFIP-006〜012を順次開始判定する。ただし個別計画が未承認・未Readyなら停止する。FIP-012後はG2 Phase 1品質ゲートへ進む。 |
| Phase 2 | Conversation HistoryとPersonal Memoryの責務分離、MemoryがPermission・Approval・Execution Authorityを代替しないことを必須ゲートとする。製造単位・順序は未決定。 |
| Phase 3 | Tool SelectionとApprovalの分離を必須ゲートとする。Apple Calendarは初期優先Connectorだが、実装順・技術選定・権限処理の詳細は承認済み計画に従う。 |
| Phase 4 | GoalまたはAI ProposalをExecution Authorityにしない。Memory、Permission、Approval、Risk、Execution Authorityの分離を必須ゲートとする。 |
| 最終統合 | 全PhaseのG2後にG3 Cross-Phase Integration、G4 E2E、G5 Security、G6 Accessibility、G7 Performanceを通過し、G8 Manufacturing Completeは人間の最終承認でのみ成立する。 |

Phase 2〜4のFIP名、件数、順序、工数、並行可否を、資料がない状態でPipelineが補完してはならない。

## 11. Mac mini 常時製造と MacBook Air 監視

| 役割 | Mac mini 製造ホスト | MacBook Air 監視端末 |
|---|---|---|
| 主責務 | 隔離作業領域、実装Agent、テスト、レビュー、実行ロック、証跡保管、停止通知 | 状態確認、PR確認、人間ゲート判断、停止理由の解消、再開承認 |
| 保護 | 画面ロック、最小権限の資格情報、安全な秘密情報保管、電源・ネットワーク復旧、ログのマスク | 安全なリモート接続。通知には秘密情報や会話本文を含めない。 |
| 可観測性 | 現在FIP、状態、試行回数、最終テスト、停止理由、次の必要判断を表示 | 日次要約と停止通知を確認し、必要な場合だけ介入 |

無人時に対話入力を要求する処理を成功経路に置かない。停電・ネットワーク断・ホスト再起動後の挙動、ジョブの重複防止、停止状態の永続化を運用開始前に検証する。

## 12. 技術選定の状態

次の技術は候補または未決定であり、本仕様によって正式採用されない。

| 領域 | 候補または未決定事項 | 採用前の確認 |
|---|---|---|
| Orchestrator | GitHub Actions、Mac mini上のローカルスケジューラ、その他CI/ジョブ基盤 | 無人実行、秘密情報、macOS/Flutter要件、再試行、費用、監査性 |
| Coding Agent | GitHub Copilot等の承認済みAI Coding Assistant | 非対話実行、プロンプト固定、権限制御、差分取得、利用規約・費用 |
| Review Agent | 独立コンテキストのAIレビューまたは既存レビュー手段 | 根拠出力、再現性、機密取扱い、誤検知時の停止 |
| Git連携 | GitHub CLI、API、その他の承認済み連携 | PR権限、ブランチ保護、最小権限トークン、監査ログ |
| 監視通知 | Tailscale、メール、チャット、ダッシュボード等 | 外部公開範囲、通知漏れ、秘密情報マスク、到達確認 |

## 13. 実装着手の最小構成

全FIPの無人連続実行を最初から有効化しない。次の段階を順番に検証する。

| 段階 | 有効化する能力 | 完了条件 |
|---|---|---|
| A | FIP manifest、Ready Gate、run record、実行ロック、停止テンプレート | FIPを変更せず、開始可否と停止理由を再現可能に記録できる。 |
| B | 1 FIP品質ループ | FIP-005など一件で、成功・修正・エスカレーションを安全に再現できる。 |
| C | 品質PASS後の隔離Commitと任意PR | ブランチ保護を侵害せず、失敗時に差分を失わない。 |
| D | 後続FIPのReady判定 | 未承認・未準備のFIPを開始せず、依存関係を守る。 |
| E | G2〜G8の証跡集約 | 実装完了とPhase・全体製造完了を混同しない。 |

最小実装物は以下とする。

- FIP manifest: FIP ID、承認済み計画への参照、前提、許可パス、禁止事項、テスト一覧、review policy、run budget。
- Run record: 状態遷移、Agent入力の版、コマンド結果、レビュー判定、修正回数、Commit/PR、停止理由。
- Review schema: `PASS`、`FIXABLE_FINDINGS`、`ESCALATE`、根拠、重大度、変更許可範囲、再テスト要求。
- Escalation record: 第8章の必須項目を満たす人間判断用の記録。
- Progress register updater: G1〜G8の根拠確認後だけ進捗台帳を更新する処理。

## 14. 受入基準

- 承認済みFIPが、既存の設計・Phase・Security/Safety・権限境界を変更せずに一件ずつ処理される。
- テストとレビューのPASS証跡なしに、FIPは完了にも次FIP開始可能にもならない。
- 明確かつ局所的な指摘だけが自動修正され、上限超過・曖昧・設計変更・依存追加・境界影響はHuman Escalationへ停止する。
- Git操作は隔離・追跡可能・非破壊であり、mainへの直接反映や履歴改変をしない。
- Mac miniが無人時に動作しても、停止・失敗・判断待ちが観測でき、MacBook Airから安全に判断・再開できる。
- Phase 2〜4および実行基盤の未決定事項を勝手に確定しない。
