# Alice MVP

## Overview

Project Aliceは、最初から万能なAIアシスタントを目指さない。

小さな機能から段階的に成長させ、実際の利用を通じて得た情報を適切に蓄積・活用することで、徐々にユーザー専用AIへ進化させる。

本ドキュメントでは、Aliceの開発フェーズと各段階の目標をMVPレベルで定義する。各機能の詳細な要件、データ構造、権限モデル、実装方式は正式設計文書で定義する。

---

# Phase 0：設計・基盤構築

## 目的

Aliceの方向性、設計思想、正式な責務境界、開発基盤を整える。

## 成果物

- Alice概要
- アーキテクチャ設計
- 要件定義
- 技術方針
- リポジトリ構成
- Phase間の整合性確認
- Final Design Review

## 状態

**COMPLETE / Implementation Authorized**

Phase 0の設計および最終レビューは完了しており、正式設計に基づく実装が承認されている。

---

# Phase 1：Conversation

## 目的

Aliceと自然で一貫した会話ができる基盤を作る。

## 機能

- テキストチャット
- AI Provider連携
- Conversation Historyの保存と参照
- Alice人格設定
- 基本的な質問応答

## 完成イメージ

```text
ユーザー
  ↓
Alice Interface
  ↓
Conversation
  ↓
AI Provider
  ↓
回答
```

## Scope Boundary

Conversation Historyは会話を継続するための履歴であり、Personal Memoryではない。会話履歴を自動的に長期記憶や権限情報として扱わない。

## 成功条件

- Aliceとして一貫した回答ができる
- 必要な過去会話を参照できる
- Conversation Historyが保存される

---

# Phase 2：Personal Memory

## 目的

ユーザーが管理できるPersonal Memoryを導入し、Aliceをユーザーに合わせて成長させる。

## 機能

- ユーザープロフィール管理
- 好み、価値観、生活情報の管理
- 技術経験や開発方針の管理
- プロジェクト情報の管理
- Memoryの保存、更新、参照、削除

## 保存対象例

### Personal Memory

- 好きな店
- 食事の好み
- 趣味
- 生活スタイル

### Engineering Memory

- 技術経験
- 開発方針
- 設計判断
- 学習履歴

### Project Memory

- Project Alice
- その他の開発案件

## Scope Boundary

- Conversation HistoryとPersonal Memoryは別の責務として扱う
- MemoryはPermission、Approval、Execution Authorityの代わりにならない
- MemoryをSystem InstructionやCredential Storeとして扱わない

## 成功条件

ユーザーが管理可能なMemoryに基づき、Aliceが一般的な回答ではなく、ユーザーに合わせた回答を返せる。

---

# Phase 3：Tools / External Services

## 目的

Aliceが外部サービスから情報を取得し、許可された範囲でサービスを利用できる状態にする。

## 機能候補

- Apple Calendar連携
- 店舗情報検索
- 天気情報取得
- GitHub連携
- Web検索
- その他の外部サービス連携

初期Connectorは、ユーザー環境との親和性を踏まえて**Apple Calendarを優先**する。

## 基本方針

外部サービスとの連携は、可能な限り限定的で明確なConnectorまたはAPIを利用する。

Tool Selectionは、実行手段の選択であってApprovalではない。ツールが選択されたことだけを根拠に、許可が必要な操作を実行しない。

## 利用イメージ

```text
ユーザーの依頼
  ↓
Conversation
  ↓
Tool Selection
  ↓
Connector / API
  ↓
取得結果または操作結果
  ↓
Aliceの回答
```

## 成功条件

- Apple Calendarを初期優先Connectorとして利用できる
- 外部サービス連携をConnector単位で安全に拡張できる
- Tool SelectionとApprovalが分離されている

---

# Phase 4：Agent / PC / Browser / Application / Voice

## 目的

AliceがユーザーのGoalを理解し、安全な権限境界の中で計画、実行、観測、評価を行える状態にする。PC、Browser、Application、Voiceを統合し、会話から現実の作業支援へつなげる。

## 機能候補

- Goalの理解とPlanの作成
- タスクの実行、結果の観測、評価、再計画
- PC操作
- Browser操作
- Application操作
- ファイル操作
- 開発作業支援
- 音声入力と音声回答
- 状況に応じた提案や継続的な作業支援

## Agent Cycle

```text
Goal
  ↓
Plan
  ↓
Execute
  ↓
Observe
  ↓
Evaluate
  └─ 必要に応じて再計画
```

## Scope Boundary

- GoalやAI ProposalはExecution Authorityではない
- ユーザーが目的を示したことだけを、すべての操作への包括的な許可として扱わない
- Memory、Permission、Approval、Risk、Execution Authorityを分離する
- 操作の影響とリスクに応じて、必要な確認や承認を行う

## 成功条件

- AliceがGoalに対して計画から評価までのAgent Cycleを実行できる
- PC、Browser、Application、Voiceを一貫した体験として利用できる
- 権限やリスクを伴う操作が正式なApprovalとExecution Authorityに基づいて実行される

---

# Development Principle

Alice開発では以下を重視する。

- 小さく作る
- 実際に利用する
- 利用結果を、ユーザーが管理可能な形で必要に応じてMemoryへ反映する
- 必要な機能だけ拡張する

Aliceは一度に完成させる製品ではなく、正式な責務境界と安全性を維持しながら、利用を通じて段階的に成長するシステムである。
