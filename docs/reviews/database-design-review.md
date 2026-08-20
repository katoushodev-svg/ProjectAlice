# Project Alice - Database Design Review

## 1. Document Information

| Item | Value |
|---|---|
| Document | `database-design-review.md` |
| Review Target | `database-design.md` |
| Target Phase | Phase 1 |
| Review Date | 2026-08-14 |
| Result | Approved |

---

## 2. Review Scope

以下を横断的に確認した。

- Approved API Contractとの整合
- Phase 1 Architecture Boundaryとの整合
- Conversation HistoryとPersonal Memoryの分離
- Single Conversation Access Pattern
- Table、KeyおよびItem Schema
- TransactionとFailure Consistency
- Idempotency、LeaseおよびBusy Lock
- Cursor PaginationとStrongly Consistent Read
- DynamoDB Local 3.3.0
- LoggingとSecurity
- Database Test Requirements
- AI Coding Assistantが推測せず実装できる具体性

---

## 3. Findings and Corrections

### DBR-001: Processing Lease Description Typo

Severity: Low

`300秒と5る`という誤記が2か所存在した。

Resolution:

`300秒とする`へ修正した。

Status: Resolved

### DBR-002: Missing TTL Inspection Permission

Severity: Medium

Backend起動時にTTL設定を検証する一方、将来AWS Runtimeに必要なOperationへ`dynamodb:DescribeTimeToLive`が含まれていなかった。

Resolution:

起動時検証用Operationとして`dynamodb:DescribeTimeToLive`を追加した。Table変更用の`dynamodb:UpdateTimeToLive`はRuntimeへ付与せず、Setup / Infrastructure Identityへ分離する方針を維持した。

Status: Resolved

### DBR-003: Message Item Size Safety

Severity: Medium

DynamoDBの1 Itemあたり400 KB上限に対し、Assistant Message ContentのPersistence上限が未定義だった。

Resolution:

Message `content`をUTF-8で300,000 Byte以下とするSafety Limitを追加した。`ai-design.md`はこの上限以下で、より小さい生成上限を定義できる。上限超過時の無言の切り詰めは禁止した。

Status: Resolved

---

## 4. Cross-document Consistency

| Review Point | Result |
|---|---|
| 単一Conversationを固定Partitionで解決できる | Pass |
| Conversationは最初のSend Messageで作成される | Pass |
| User MessageをAI呼び出し前に保存する | Pass |
| `assistant.completed`前にCompletion Transactionが完了する | Pass |
| APIのIdempotency保証期間を満たす | Pass |
| `requestId`と`Idempotency-Key`を混同しない | Pass |
| API Message順序とDynamoDB Sequenceが一致する | Pass |
| API CursorへDynamoDB Keyを直接公開しない | Pass |
| API日時をJST Offset付きで返せる | Pass |
| Phase 1でPersonal Memoryを保存しない | Pass |
| Phase 1 Security Boundaryを変更していない | Pass |

---

## 5. DynamoDB Behavior Review

| Review Point | Result |
|---|---|
| TransactionはAll-or-Nothingを前提とする | Pass |
| Transaction結果不明時にStateを再確認する | Pass |
| 直前Write確認にStrongly Consistent Readを使用する | Pass |
| TTL物理削除の即時性へ依存しない | Pass |
| DynamoDB Localで再現できないFailureをFake / Mockで補う | Pass |
| Item Size Limitへ安全余裕を持たせる | Pass |

---

## 6. AI Implementation Readiness

以下が明示されているため、AI Coding Assistantが主要なPersistence Decisionを推測する必要はない。

- Table数とIndex有無
- PK / SKと各Item Schema
- ID、日時、Sequence
- Start / Completion / Failure Transaction
- Idempotency StateとRetention
- Lease、Busy Lock、Recovery
- Cursor FormatとValidation
- Read Consistency
- Local EnvironmentとConfiguration
- Logging禁止Data
- AWS Permission Boundary
- Unit / Integration / Failure Test要件

Test Framework、AI ModelおよびAI Response生成上限の具体値は、それぞれのSource of Truthである`test-design.md`と`ai-design.md`へ委譲されており、Database Designの未完成事項ではない。

---

## 7. Final Result

```text
APPROVED
```

修正必須の未解決事項はない。

`database-design.md`はPhase 1 Database Detailed DesignのSource of Truthとして実装準備可能である。

次は`ai-design.md`の詳細設計へ進む。
