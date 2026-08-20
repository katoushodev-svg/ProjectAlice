# Project Alice - API Design Review

## 1. Review Information

| Item | Value |
|---|---|
| Review Target | `api-design.md` |
| Target Version | Library version 0 |
| Review Date | 2026-08-14 |
| Reviewer Role | Tech Lead / System Architect |
| Verdict | CHANGES REQUIRED |

本レビューでは、Project Alice Phase 1のRequirements、MVP、承認済みBackend ArchitectureおよびAPI Contract内部の整合性を確認した。

本レビューは設計レビューであり、`api-design.md`自体の修正は実施していない。

---

## 2. Executive Summary

APIの主要方針はPhase 1 Requirementsおよび承認済みArchitectureと整合している。

以下は適切に設計されている。

- 単一Conversation Scope
- 初回Message送信時のConversation自動作成
- 3 Endpointへの限定
- Conversation HistoryとPersonal Memoryの分離
- OpenAI / DynamoDB固有Modelの隔離
- SSEによるProvider-independent Streaming
- Cursor Pagination
- RFC 9457 Problem Details
- API DTOとDomain / Infrastructure Modelの分離
- Phase 1 Authenticationなし・Public Internet公開禁止
- Requirements Traceability

Architecture変更または新規ADRを必要とする問題は確認されなかった。

一方、実装前に修正すべきMust Fixが2件、明確化を推奨するShould Fixが7件ある。

---

## 3. Findings Summary

| ID | Severity | Finding | Result |
|---|---|---|---|
| API-R-001 | Must Fix | Idempotencyの保証期間と放置されたProcessing Stateの扱いが未定義 | Open |
| API-R-002 | Must Fix | Terminal Eventを「必ず送信」とする記述がConnection Loss規定と矛盾 | Open |
| API-R-003 | Should Fix | `requestId`をUUIDと定義しているがExampleがUUIDではない | Open |
| API-R-004 | Should Fix | Request Optional Field追加と未知Field拒否のVersioning Ruleが不明確 | Open |
| API-R-005 | Should Fix | `413 PAYLOAD_TOO_LARGE`の判定Thresholdが未定義 | Open |
| API-R-006 | Should Fix | Conversation `updatedAt`の更新Semanticsが未定義 | Open |
| API-R-007 | Should Fix | 10,000 Unicode CharacterのCounting Unitが曖昧 | Open |
| API-R-008 | Should Fix | Idempotency Replay時に使用する`requestId`が未定義 | Open |
| API-R-009 | Should Fix | SSEとPhase 1 Non-ScopeのComplex Async Processingの関係が未説明 | Open |

---

## 4. Must Fix Findings

### API-R-001: Idempotency Lifecycle Contract

Severity: Must Fix

該当箇所:

- Section 18: Idempotency
- Section 20: Connection Loss and Retry
- Section 27.1: Database Designへの委譲

現在のContractでは、Connection Loss後に同じ`Idempotency-Key`で再試行することを要求している。

しかし以下がAPI Contractとして未定義である。

- Idempotencyが保証される最低期間
- Retention終了後に同じKeyを受信した場合の動作
- Backend Crash等で`Processing`のまま残ったRecordの回復方法
- `REQUEST_IN_PROGRESS`をいつまで返すか
- Clientが再確認する際の待機指示

RetentionをDatabase実装詳細として委譲すること自体は可能だが、Clientから見える保証期間はAPI Contractで定義する必要がある。

未修正の場合、古いKeyが新規処理として再実行され、MessageおよびAI API呼び出しが重複する可能性がある。また、Crash後に永続的に`REQUEST_IN_PROGRESS`となる可能性がある。

Recommended Resolution:

- Idempotency Guarantee WindowをAPI Contractへ追加する
- `Processing`にLease / Expirationを持たせることをDatabase Designへ要求する
- `REQUEST_IN_PROGRESS`へ`Retry-After` Headerを付与する
- Abandoned Processing Stateを`Failed`等へ収束させるObservable Ruleを定義する

具体的なDynamoDB AttributeおよびState Transition実装は`database-design.md`へ委譲してよい。

---

### API-R-002: Terminal Event Guarantee

Severity: Must Fix

該当箇所:

- Section 15.3: Terminal Events
- Section 20.1: Connection Loss Detection

Section 15.3では、SSEが必ず`assistant.completed`または`stream.failed`を送信して終了すると定義している。

一方、Section 20.1ではTerminal Eventを受信せずConnectionが終了する場合を定義している。

Network Loss、Client Disconnect、Process Crash等では、BackendがTerminal Eventを送信またはClientが受信することを保証できない。

Recommended Resolution:

以下のように保証範囲を修正する。

> Backendが制御可能な正常終了またはApplication Error終了では、Exactly OneのTerminal Event送信を試みる。Transport Loss等によりTerminal EventがClientへ到達しない場合がある。ClientがTerminal Eventを受信しなかった場合、結果不明としてIdempotency Contractに従う。

Test Designも「すべてのConnectionでTerminal Event受信」ではなく、「Application-controlled completionではExactly One」とする。

---

## 5. Should Fix Findings

### API-R-003: requestId Example Format

Section 9では`requestId`をUUID形式と定義しているが、SSEおよびProblem Details Exampleでは`req_001`等を使用している。

AI Coding AssistantがPrefix形式を正式Formatとして実装する可能性がある。

Recommended Resolution:

- すべてのExampleをUUIDへ統一する
- または`requestId`をOpaque Stringへ変更する

現在の設計意図ではUUIDへの統一を推奨する。

---

### API-R-004: Versioning and Unknown Request Fields

Section 6では、RequestへのOptional Field追加を`v1`内で許可する一方、Backendは未知のRequest Fieldを`400 Bad Request`とする。

新しいFlutterがOptional Fieldを送信し、古いBackendへ接続すると互換性が失われる。

Recommended Resolution:

以下のいずれかを明記する。

- Flutter / Backendを常に同時DeployするためBackward Compatibilityを保証しない
- Request Optional Field追加もVersion変更対象とする
- 未知のRequest Fieldを無視する

Phase 1のLocal Monorepo前提では「同時Deployを前提とし、Request追加時はCompatibilityを個別確認する」が現実的である。

---

### API-R-005: Request Body Size Threshold

`413 PAYLOAD_TOO_LARGE`が定義されているが、最大HTTP Request Body Sizeが定義されていない。

`content`最大10,000文字とは別に、JSON Encodingを含むByte上限が必要である。

Recommended Resolution:

- Request Body最大Byte数をAPIまたはDevelopment Configurationで定義する
- 値をConfiguration可能にする
- Spring Container LevelのErrorも共通Problem Detailsへ変換する

---

### API-R-006: updatedAt Semantics

Conversation Responseの`updatedAt`が「Conversation最終更新日時」とだけ定義されている。

以下のどの時点で更新するかが不明である。

- User Message保存時
- Assistant Message保存時
- Completed Pair確定時
- Metadata変更時

Recommended Resolution:

Phase 1では「Conversation Historyへ最後にMessageが正常保存された日時」等、Observableな意味を定義する。

Persistence更新方法は`database-design.md`へ委譲してよい。

---

### API-R-007: Unicode Character Counting

10,000文字の判定について、UTF-8 ByteおよびUTF-16 Code Unitではないとされているが、Unicode Code PointとGrapheme Clusterのどちらかが明示されていない。

Emojiや結合文字で実装差が生じる。

Recommended Resolution:

- Phase 1ではUnicode Code Point Countと明記する
- Java実装もCode Point Countに合わせる

---

### API-R-008: requestId on Idempotency Replay

`requestId`はOne HTTP RequestごとにBackendが生成すると定義されている。

一方、Completed / Failed ResultのReplayでは保存済みSSE Resultを返すとされており、元Requestの`requestId`を返すか、Retry Requestの新しい`requestId`へ置き換えるかが未定義である。

Recommended Resolution:

- Replay時は現在のHTTP Requestで生成した新しい`requestId`をHeaderおよび全SSE Eventへ設定する
- 保存済みMessage / Error Payloadを再構築し、元Requestの`requestId`をReplayしない

---

### API-R-009: SSE and Complex Async Processing

`backend-design.md`ではPhase 1 Non-ScopeとしてComplex Async Processingを挙げている。

`api-design.md`ではSSE Streamingを採用しているため、AI Coding AssistantがArchitecture矛盾と判断したり、逆にMessage Queue / Background Job等を導入したりする可能性がある。

Recommended Resolution:

以下を明記する。

> Phase 1のSSEは一つのHTTP Request Lifecycle内で行うResponse Streamingであり、Non-ScopeであるBackground Job、Message Queue、Autonomous Async Workflow等のComplex Async Processingには該当しない。

この明確化はArchitecture変更ではなく、用語境界の補足である。

---

## 6. Passed Review Items

以下は修正不要と判断した。

### Requirements / MVP Alignment

- Text Chat Requirementと`SendMessageUseCase`が接続されている
- Conversation History保存・参照RequirementがAPIへ接続されている
- Personal MemoryはPhase 1 Scope外である
- Phase 1へ将来Featureを先行追加していない

### Architecture Alignment

- Feature-based Structureと矛盾しない
- AiProviderはconversation Feature所有のままである
- Application / DomainへOpenAI SDKまたはDynamoDB SDKを漏らしていない
- API DTOとDomain / Infrastructure Modelを分離している
- Conversation HistoryとPersonal Memoryを混同していない
- Accepted Architecture Decisionの変更を必要としない

### API Contract

- 3 Endpointの責務が明確である
- 初回Message送信時のConversation作成方式が確定している
- Conversation未作成時のGET動作が明確である
- Pagination方向とResponse内Sort Orderが明確である
- `nextCursor` / `hasMore` Invariantが明確である
- Message SchemaがProvider-independentである
- JST Offset付きDate-Timeが明確である
- HTTP ErrorとSSE ErrorのBoundaryが定義されている
- `assistant.completed`がCanonical Persistence Resultである

### Security / Reliability

- Public Internet Accessを禁止している
- Cacheを禁止している
- Secret、Stack TraceおよびRaw SDK Errorを返さない
- Concurrent SendをBackendでも拒否する
- New Idempotency Keyによる自動Retryを禁止している

---

## 7. Final Verdict

Verdict: CHANGES REQUIRED

Architecture変更: 不要

ADR追加: 不要

Must Fix 2件を修正するまで、API DesignをApprovedとはしない。

Should Fix 7件もAIによる実装差を防ぐため、Database Design開始前に同時修正することを推奨する。

Recommended Next Step:

1. API-R-001からAPI-R-009を`api-design.md`へ反映する
2. Review Checklistを実行する
3. API Design Re-reviewを行う
4. Approved後に`database-design.md`へ進む
