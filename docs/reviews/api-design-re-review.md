# Project Alice - API Design Re-review

## 1. Review Information

| Item | Value |
|---|---|
| Review Target | `api-design.md` |
| Review Date | 2026-08-14 |
| Review Type | Review Finding Fix Verification / Regression Review |
| Reviewer Role | Tech Lead / System Architect |
| Verdict | APPROVED |

本再レビューでは、初回API Design Reviewの9件の指摘に対する修正と、修正後のAPI Contract全体の回帰を確認した。

---

## 2. Executive Summary

初回レビューのMust Fix 2件およびShould Fix 7件は、すべて解消済みと判定する。

再レビューで新たなMust FixまたはShould Fixは検出されなかった。

修正後のAPI Designは、Phase 1 Requirements、MVP Scope、承認済みArchitecture、Backend Layer責務およびSecurity Boundaryと整合する。

既存Architecture Decisionの変更および新規ADRは必要ない。

---

## 3. Finding Resolution

| ID | Original Severity | Resolution | Result |
|---|---|---|---|
| API-R-001 | Must Fix | Completed / Failed Keyの保証期間、Processing Lease、失効後のFailed収束、`Retry-After`、保証期間終了後のClient Ruleを定義 | Closed |
| API-R-002 | Must Fix | Terminal Eventの保証範囲をApplication-controlled completionに限定し、Transport Loss時の結果不明とRetry Ruleを定義 | Closed |
| API-R-003 | Should Fix | すべての`requestId` ExampleをUUIDへ統一 | Closed |
| API-R-004 | Should Fix | Request Optional Field追加時のCompatibility確認とDeploy順序を定義 | Closed |
| API-R-005 | Should Fix | HTTP Request Body上限を128 KiBと定義 | Closed |
| API-R-006 | Should Fix | `updatedAt`を最後に正常保存されたMessageの`createdAt`と定義 | Closed |
| API-R-007 | Should Fix | 10,000文字の判定単位をUnicode Code Pointと定義 | Closed |
| API-R-008 | Should Fix | Replayごとに現在のHTTP Request用の新しいUUID `requestId`を使用するRuleを定義 | Closed |
| API-R-009 | Should Fix | SSEを単一HTTP Request Lifecycle内のStreamingと定義し、Complex Async Processingとの境界を明確化 | Closed |

---

## 4. Regression Review

### 4.1 Requirements and Scope

- 単一Conversation Scopeを維持している
- Phase 1の3 Endpoint以外を追加していない
- Conversation HistoryとPersonal Memoryを混同していない
- Personal Memory、Tool、Voice、AgentおよびComplex Async Processingを先行導入していない

### 4.2 Contract Consistency

- Endpoint、HTTP MethodおよびSuccess Statusは一貫している
- JSON Exampleは構文上有効である
- `requestId` ExampleはすべてUUIDである
- JST Offset付きDate-Time Contractを維持している
- HTTP ErrorとSSE ErrorのBoundaryは一貫している
- Application-controlled completionのTerminal EventはExactly Oneである

### 4.3 Architecture and Security

- Application / DomainはHTTP、OpenAI SDKおよびDynamoDB SDKに直接依存しない
- AiProviderはPhase 1でconversation Featureが所有する
- SSEのためだけにQueueまたはBackground Jobを追加しない
- Authenticationなし、Local Development、Public Internet非公開のBoundaryを維持している
- Secret、Stack TraceおよびProvider固有ErrorをAPIへ公開しない

---

## 5. Final Decision

`api-design.md`はPhase 1 APIの詳細設計として承認する。

次の推奨作業は`database-design.md`である。特に、API Contractで定義した以下の保証をDynamoDBのData ModelおよびState Transitionに接続する必要がある。

- Conversation / Messageの保存順序とFailure Consistency
- Cursor EncodingとIntegrity Validation
- Idempotency RecordのState、Lease、Retention
- Conversation Busy LockのAtomicity
- `updatedAt`の更新方式
