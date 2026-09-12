# Project Alice — 全体製造進捗管理表

## 1. 文書管理

| 項目 | 内容 |
|---|---|
| 文書名 | `project-alice-manufacturing-progress-register.md` |
| 目的 | Phase 1〜4の製造、全体統合、最終検証を一元管理し、Project Alice製造完了までの残工程を可視化する。 |
| 管理対象の完了条件 | Phase 1〜4の製造完了 ＋ 全体統合 ＋ 最終検証の全ゲート通過 |
| 基準日 | 2026-09-09 JST |
| 設計上の前提 | Phase 0は `COMPLETE / Implementation Authorized`。Phase 2〜4の設計、Phase間整合性確認およびFinal Design Reviewは完了済み。 |
| 更新原則 | 実装・レビュー・テストの完了根拠が確認できた時だけ状態を更新する。資料にない実装項目・FIP・工数は追加しない。 |

### ステータス記号

| 記号 | 状態 | 定義 |
|---|---|---|
| ✅ | 完了 | 実装、必要なレビューおよび当該工程のテストが完了し、後続工程へ引き渡せる。 |
| 🔵 | 実装中 | 実装作業が開始済みで、完了判定前。 |
| ⚪ | 未着手 | 承認済みの範囲だが、実装を開始していない。 |
| ⏳ | 待機 | 前提工程または正式な製造計画の確定待ち。 |
| — | 未定義 | 現在参照できる正式資料に、実装単位の内訳がない。 |

## 2. エグゼクティブ・サマリー

| 観点 | 現在地 | 製造完了までの残り |
|---|---|---|
| Phase 1 Conversation | FIP-001〜005完了、FIP-006〜012未実装 | FIP-006〜012、Phase 1レビュー・テスト・統合判定 |
| Phase 2 Personal Memory | 設計完了、製造未着手 | 正式な製造単位に沿う実装、レビュー、テスト、Phase統合判定 |
| Phase 3 Tools / External Services | 設計完了、製造未着手 | 正式な製造単位に沿う実装、レビュー、テスト、Phase統合判定 |
| Phase 4 Agent / PC / Browser / Application / Voice | 設計完了、製造未着手 | 正式な製造単位に沿う実装、レビュー、テスト、Phase統合判定 |
| 全体統合・最終検証 | 未着手 | Cross-Phase Integration、E2E、安全性、アクセシビリティ、性能および最終完了承認 |

**全体進捗率（概算）:** **数値未設定**。現在の正式資料にはPhase 2〜4および統合・最終検証の製造単位数・工数・重みがないため、FIP数だけで算出する数値はProject Alice全体の進捗を誤って表す。数値を設定する場合は、各Phaseと統合・最終検証を含む重みを承認してから「概算」として記録する。

参考指標として、**Phase 1のFIP着手済み比率は 5/12（約42%）**、**完了比率は 5/12（約42%）**である。これは全体進捗率ではない。

## 3. マスタースケジュール／進捗管理表

| 順序 | 管理対象 | 目的・完了条件（資料記載の範囲） | 現在状態 | 依存関係・開始条件 | 次の判定／残工程 |
|---:|---|---|---|---|---|
| 0 | Phase 0 設計・基盤構築 | 設計、Phase間整合性確認、Final Design Reviewを完了し、実装を承認する。 | ✅ 完了 | — | Phase 1〜4の製造は承認済み設計に従う。 |
| 1 | Phase 1 Conversation | 自然で一貫した会話、AI Provider連携、Conversation Historyの保存・参照、Alice人格設定、基本応答を成立させる。 | 🔵 製造中 | Phase 0完了。FIPは後述の順序で管理する。 | FIP-006〜012を順次実施し、Phase 1品質ゲートへ進む。 |
| 2 | Phase 2 Personal Memory | ユーザー管理可能なMemoryの保存・更新・参照・削除を通じ、ユーザーに合わせた回答を可能にする。 | ⚪ 設計完了／製造未着手 | Phase 1のConversation Historyとの責務分離を維持する。MemoryはPermission、Approval、Execution Authorityの代替にしない。 | 正式な製造計画に基づく実装開始、レビュー、テスト、Phase統合判定。 |
| 3 | Phase 3 Tools / External Services | 外部サービスの情報取得・利用を安全に拡張し、初期優先ConnectorとしてApple Calendarを利用可能にする。 | ⚪ 設計完了／製造未着手 | Tool SelectionとApprovalを分離する。外部サービス連携は限定的で明確なConnector/APIを利用する。 | 正式な製造計画に基づく実装開始、レビュー、テスト、Phase統合判定。 |
| 4 | Phase 4 Agent / PC / Browser / Application / Voice | Goal理解からPlan、Execute、Observe、EvaluateまでのAgent Cycleと、PC・Browser・Application・Voiceの一貫した体験を実現する。 | ⚪ 設計完了／製造未着手 | Goal／AI ProposalはExecution Authorityではない。Memory、Permission、Approval、Risk、Execution Authorityを分離する。 | 正式な製造計画に基づく実装開始、レビュー、テスト、Phase統合判定。 |
| 5 | 全体統合 | Phase間の責務境界および連携が、正式設計に対して整合することを確認する。 | ⏳ 未着手 | Phase 1〜4の製造完了と各Phaseの品質ゲート通過。 | Cross-Phase Integration判定。 |
| 6 | 最終検証・製造完了 | E2E、安全性、アクセシビリティ、性能を含む最終品質ゲートを通過し、製造完了を承認する。 | ⏳ 未着手 | 全体統合完了。 | 最終完了承認。 |

## 4. Phase 1 — FIP進捗台帳

> 注記：この台帳は、現在の正式状態として指定されたFIP-001〜012の実装状態を管理する。各FIPの詳細スコープ・個別テスト内容は、対応する承認済みFIP計画をSource of Truthとする。

| FIP | 状態 | 実装状況 | 前提／順序 | 次の管理アクション |
|---|---|---|---|---|
| FIP-001 | ✅ 完了 | 完了 | Phase 1 FIPの先行工程 | 完了根拠を維持し、後続への影響がないかPhase 1統合時に確認する。 |
| FIP-002 | ✅ 完了 | 完了 | FIP-001後の先行工程 | 同上。 |
| FIP-003 | ✅ 完了 | 完了 | FIP-001・002を前提とするDomain Foundation | Domain境界が後続実装で崩れないことを継続確認する。 |
| FIP-004 | ✅ 完了 | 完了 | FIP-003後の後続工程 | 完了根拠を維持し、後続への影響がないかPhase 1統合時に確認する。 |
| FIP-005 | ✅ 完了 | 完了 | FIP-001〜004完了後 | 完了根拠を維持し、後続への影響がないかPhase 1統合時に確認する。 |
| FIP-006 | ⚪ 未実装 | 未着手 | FIP-005完了後に開始判定 | FIP-005の完了判定後、承認済み計画に従い開始する。 |
| FIP-007 | ⚪ 未実装 | 未着手 | FIP-006を含む先行FIPの完了後に開始判定 | 先行FIPの完了判定後に開始する。 |
| FIP-008 | ⚪ 未実装 | 未着手 | FIP-007を含む先行FIPの完了後に開始判定 | 先行FIPの完了判定後に開始する。 |
| FIP-009 | ⚪ 未実装 | 未着手 | FIP-008を含む先行FIPの完了後に開始判定 | 先行FIPの完了判定後に開始する。 |
| FIP-010 | ⚪ 未実装 | 未着手 | FIP-009を含む先行FIPの完了後に開始判定 | 先行FIPの完了判定後に開始する。 |
| FIP-011 | ⚪ 未実装 | 未着手 | FIP-010を含む先行FIPの完了後に開始判定 | 先行FIPの完了判定後に開始する。 |
| FIP-012 | ⚪ 未実装 | 未着手 | FIP-011を含む先行FIPの完了後に開始判定 | 先行FIPの完了判定後、Phase 1品質ゲートへ進む。 |

### Phase 1の製造順序と依存関係

```text
FIP-001 ✅ → FIP-002 ✅ → FIP-003 ✅ → FIP-004 ✅ → FIP-005 ✅
                                                        ↓
FIP-006 ⚪ → FIP-007 ⚪ → FIP-008 ⚪ → FIP-009 ⚪ → FIP-010 ⚪ → FIP-011 ⚪ → FIP-012 ⚪
                                                                                              ↓
                                                                           Phase 1 レビュー／テスト／統合判定
```

この順序は、現時点で管理対象として与えられたFIPの連番と、FIP-003計画に明記されたFIP-001・002を前提とする関係に基づく。各FIPの詳細な依存関係が別の承認済み計画に明記されている場合は、そちらを優先して本表を更新する。

## 5. Phase 2〜4 — 製造準備・進捗台帳

| Phase | 設計状態 | 製造状態 | 製造順序／依存関係として管理する事項 | スコープ境界（必須確認） | 次の正式アクション |
|---|---|---|---|---|---|
| Phase 2 Personal Memory | ✅ 設計完了 | ⚪ 未着手 | Phase 1 Conversationと独立した責務として実装・統合する。Phase 2内の製造単位と順序は、承認済み実装計画で確定したものを登録する。 | Conversation HistoryとPersonal Memoryを混同しない。MemoryをPermission、Approval、Execution Authorityに転用しない。 | 製造開始時に承認済み実装計画の単位、依存関係、テスト根拠を台帳へ登録する。 |
| Phase 3 Tools / External Services | ✅ 設計完了 | ⚪ 未着手 | Conversationを起点とするTool Selection、Connector/API、結果の流れを、Approvalと分離して実装・統合する。初期優先ConnectorはApple Calendar。Phase内の製造単位と順序は、承認済み実装計画で確定したものを登録する。 | Tool SelectionはApprovalではない。選択のみを根拠に許可が必要な操作を実行しない。 | 製造開始時に承認済み実装計画の単位、依存関係、テスト根拠を台帳へ登録する。 |
| Phase 4 Agent / PC / Browser / Application / Voice | ✅ 設計完了 | ⚪ 未着手 | Goal → Plan → Execute → Observe → Evaluate（必要に応じ再計画）を、正式な権限境界の下で実装・統合する。Phase内の製造単位と順序は、承認済み実装計画で確定したものを登録する。 | GoalやAI ProposalをExecution Authorityと扱わない。Memory、Permission、Approval、Risk、Execution Authorityを分離する。 | 製造開始時に承認済み実装計画の単位、依存関係、テスト根拠を台帳へ登録する。 |

## 6. レビュー・テスト・統合・最終ゲート台帳

| ゲート | 対象 | 通過条件 | 現在状態 | 前提 | 証跡／判定記録 |
|---|---|---|---|---|---|
| G1: FIP完了判定 | 各FIP | 承認済み計画のスコープを実装し、必要なレビューとテストを完了する。 | Phase 1はFIP-001〜005通過。Phase 2〜4は未着手。 | 当該FIPの実装完了 | 対応FIP計画、レビュー結果、テスト結果 |
| G2: Phase品質ゲート | Phase 1〜4 | 当該Phaseの製造項目を完了し、要件・責務境界・必要テストを満たす。 | Phase 1〜4とも未通過 | 当該Phaseの全FIP／正式製造単位のG1通過 | Phaseレビュー記録、テスト結果、残課題一覧 |
| G3: Cross-Phase Integration | Phase 1〜4間 | Conversation、Memory、Tools、Agentの境界と連携が正式設計と整合する。 | ⏳ 未着手 | G2を全Phaseで通過 | 統合レビュー記録、統合テスト結果 |
| G4: E2E | Project Alice全体 | ユーザー体験として各Phaseを通る主要な一連の動作を確認する。 | ⏳ 未着手 | G3通過 | E2Eテスト結果 |
| G5: Security | Project Alice全体 | 権限・承認・実行権限の分離、および会話・識別子等の安全な取扱いを確認する。 | ⏳ 未着手 | G3通過 | セキュリティレビュー／テスト結果 |
| G6: Accessibility | Project Alice全体 | アクセシビリティの最終検証を完了する。 | ⏳ 未着手 | G3通過 | 検証結果 |
| G7: Performance | Project Alice全体 | 性能の最終検証を完了する。 | ⏳ 未着手 | G3通過 | 検証結果 |
| G8: Manufacturing Complete | Project Alice全体 | G4〜G7を通過し、未解決の製造上の阻害事項がないことを確認して最終承認する。 | ⏳ 未着手 | G4〜G7通過 | 最終承認記録 |

## 7. 全体完成までのクリティカル・パス

```text
Phase 1: FIP-005完了 → FIP-006〜012完了 → Phase 1品質ゲート
Phase 2: 正式な製造計画に従う実装完了 → Phase 2品質ゲート
Phase 3: 正式な製造計画に従う実装完了 → Phase 3品質ゲート
Phase 4: 正式な製造計画に従う実装完了 → Phase 4品質ゲート
                                                        ↓（全PhaseのG2通過）
Cross-Phase Integration → E2E / Security / Accessibility / Performance → Manufacturing Complete
```

Phase 2〜4は設計完了・製造未着手である。Phase 1〜4を直列に製造することや、Phase 2〜4相互の実装順を、現時点の資料からは確定しない。実装の並行可否や開始順は、各Phaseの承認済み実装計画に明記された依存関係を優先する。本台帳は、設計を再設計する場ではなく、正式に承認済みの製造計画と実績を追跡する場とする。

## 8. 更新ルール

1. FIPを「✅ 完了」に変えるのは、実装だけでなくG1の根拠がそろった後とする。
2. Phaseを「✅ 製造完了」に変えるのは、当該PhaseのG2通過後とする。
3. Phase 2〜4のFIP名、件数、順序、工数を、資料がない状態で本書へ追加しない。
4. 全体進捗率を数値化する場合は、Phase 1〜4、全体統合、最終検証を含む重みと集計基準を承認し、「概算」と明記する。
5. 設計との矛盾・未解決事項が見つかった場合は、実装で推測して解消せず、該当するSource of Truthへ差し戻して判定する。
