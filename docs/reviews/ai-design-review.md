# Project Alice - AI Design Review

## 1. Review Information

| Item | Value |
|---|---|
| Review Target | `ai-design.md` Version 11 |
| Review Date | 2026-08-16 JST |
| Review Scope | Phase 1 Detailed Design / Phase 2〜4 AI Architecture |
| Review Result | **CHANGES REQUIRED** |
| Required Action | 指摘修正後に再レビュー |

本レビューは、承認済みのProject Alice Architecture Principlesを再検討するものではない。

Phase 1についてAI Coding Assistantが設計判断を推測せず実装できるか、Phase 2〜4の拡張によってPhase 1の大幅な再設計が必要にならないかを確認した。

---

## 2. Review Summary

AI Designの全体構造は妥当である。

特に次は維持できる。

- Alice CoreとOpenAI Providerの分離
- Responses APIと`store=false`の採用
- DynamoDB Conversation Historyを会話状態のSource of Truthとする方針
- Capability-specific Portへの分割
- Prompt、Conversation ContextおよびUntrusted Dataの分離
- Phase 1 Text StreamingとCanonical Assistant Messageの分離
- Phase別のSecurity / Permission Boundary
- Software TestとAI Evaluationの分離
- Provider-independentなEvaluation Assetの保持

ただし、Phase 1の実装結果を分岐させる矛盾またはContract不足があるため、現時点ではImplementation-readyとして承認しない。

必須修正は5件、軽微修正は3件である。

---

## 3. Required Findings

### AI-REV-001: AiProvider Ownership変更のAccepted ADRを確認できない

**Severity: High**

`ai-design.md` Section 6は、AI Provider Boundaryを従来の`conversation`所有から独立した`ai` Feature所有へ変更している。

`ai-design-phase0-reevaluation.md` Section 4.1およびSection 8は、この変更について次を実装前必須としている。

- Accepted ADR
- `alice-architecture.md`の更新
- `repository-structure.md`の更新
- `backend-design.md`の更新
- `ai-design.md`の更新

今回確認できた資料にはAccepted ADRと関連3文書の更新結果が含まれていない。

**Risk**

AI Coding Assistantが、旧Decisionの`conversation.application.port.AiProvider`と、新Decisionの`ai.application.port.TextGenerationProvider`のどちらを採用すべきか判断できない。

**Required Fix**

既存のADR番号を確認し、次を正式なAccepted ADRとして追加する。

1. AI Capability Boundaryを独立した`ai` Featureへ昇格する
2. Provider ContractをCapability-specificに分割する
3. Phase 1ではText GenerationとInput Token Countingだけを実装する

その後、Architecture / Repository / Backend Designを同じDecisionへ同期する。

---

### AI-REV-002: Client Disconnect時の生成継続とCancellation Testが矛盾する

**Severity: High**

`ai-design.md` Section 13.8は、FlutterのSSE Connectionが切断されてもBackend Generationを継続し、成功結果を保存すると定義している。

一方、Section 17には次の異なる要求がある。

- Section 17.4: Client Disconnect時に不要なGenerationをCancelする
- Section 17.5: Client DisconnectでSubscriber CancelとProvider Cancellationを確認する

**Risk**

実装者によって次の2種類のBackendが生成される。

- Disconnect後も生成・保存し、Idempotency Replayを可能にするBackend
- Disconnect時にProvider CallをCancelし、Assistant Messageを保存しないBackend

これはUser Experience、Cost、PersistenceおよびIdempotencyに直接影響する。

**Required Fix**

Section 13.8をSource of TruthとしてTest章を修正する。

- Client DisconnectではSSE Delta送信だけを停止する
- Delta HandlerをNo-opへ切り替える
- Provider Generationは継続する
- 成功時はAssistant MessageとIdempotency Resultを保存する
- Provider Cancellation Testは、Use Case DeadlineまたはBackend Shutdown等の別Scenarioへ限定する

---

### AI-REV-003: Absolute Deadline / CancellationをProvider Portへ伝えるContractがない

**Severity: High**

Section 13.2は、`SendMessageUseCase`がAbsolute Deadlineを生成し、各External Callが「固有上限と残り時間の短い方」を使用すると定義している。

しかしPhase 1 Contractは次だけである。

```java
TextGenerationResult generate(
    TextGenerationRequest request,
    TextDeltaHandler deltaHandler
);

long count(TextGenerationRequest request);
```

`TextGenerationRequest`はDate-Timeを明示的に除外しており、Cancellation SignalまたはExecution Contextも存在しない。

またHistory Budget超過時はInput Token Countを複数回実行できるが、15秒が「各Count Request」なのか「Token Count Stage全体」なのかが一意でない。

**Risk**

- Use Case残り時間をOpenAI Adapterへ伝えられない
- 170秒のUse Case Deadlineを超えてProvider Callが継続し得る
- Timeout後に別ThreadでGenerationが継続し、保存やMetricが競合し得る
- Token Countの複数回実行でDeadline Reserveを消費し得る

**Required Fix**

Provider-independentなExecution Control Contractを定義する。例:

```java
public record AiExecutionContext(
    Instant deadline,
    CancellationSignal cancellationSignal
) {}
```

名称は変更可能だが、次を一意にする。

- Use Case Absolute Deadlineの伝達方法
- Provider Callの中断方法
- Cancellationの発生源
- Client DisconnectはCancellation Sourceではないこと
- 15秒をToken Count Stage全体に適用するか、各Callへ適用するか
- Thread Interrupt / Future Cancellation / SDK Stream Closeの責務

Spring、ReactorまたはOpenAI SDK型をCore Contractへ公開してはならない。

---

### AI-REV-004: Provider-independent Error ModelがSDK Exceptionを漏らし得る

**Severity: High**

Section 13.11はApplicationへOpenAI SDK Exceptionを公開しないと定義しているが、直後の`AiGenerationFailure`は`Throwable cause`を保持する。

OpenAI AdapterがSDK Exceptionを`cause`へ設定した場合、Application側Modelを通じてProvider固有Exception ObjectがCoreへ漏れる。

**Risk**

- Alice CoreがOpenAI SDKへ間接依存する
- Provider交換時にException Handlingが変化する
- Provider Error BodyやRequest情報が上位Logへ漏れる可能性がある

**Required Fix**

Applicationへ渡すError Contractから`Throwable cause`を除外する。

Applicationへ渡す情報は、少なくとも次に限定する。

- Provider-independent Category
- Safe MessageまたはSafe Error Identifier
- Retryable Flag

SDK ExceptionとProvider Request IDはInfrastructure内で安全にLogging / Telemetry処理する。

---

### AI-REV-005: Port Contract TestのUsage要件を観測できない

**Severity: Medium**

Section 8.6はProvider Token Usageを`TextGenerationResult`等から明示的に除外している。これはAlice CoreとProvider Metadataを分離する適切な方針である。

一方、Section 17.6の共通Port Contract Testは、Usageを取得できない場合に`0`と偽装しないことを要求している。

現在のPort ContractからUsageは観測できないため、このTest Requirementは実装不能である。

**Required Fix**

Usageの確認を共通Port Contract Testから削除し、OpenAI Adapterと`AiInvocationTelemetry`のInfrastructure Testへ移す。

Core ContractへUsageを追加して解決してはならない。

---

## 4. Minor Findings

### AI-REV-006: 未定義のIdle TimeoutがTest項目に存在する

**Severity: Low**

Section 17.4はGeneration Deadline、Idle Timeout、Overall Request Deadlineの区別をTestするとしているが、Section 13.2にIdle TimeoutのDefinitionまたはDefault値がない。

Phase 1でIdle Timeoutを採用しないならTest項目から削除する。採用するならOwner、Definition、Default、SSE Heartbeatとの違いをSection 13と16へ追加する。

### AI-REV-007: Phase 1 Out-of-Scope項目が重複する

**Severity: Low**

Section 4.3の`Provider自動切替`が2回記載されている。片方を削除する。

### AI-REV-008: Review完了後のDocument Status更新が必要

**Severity: Low**

現在の`ai-design.md`は`Status: Draft`で、Section 18にAI Design Reviewが未完了として残っている。

必須修正と再レビュー完了後に次を更新する。

- Statusを`Approved`へ変更
- Section 18をReview Result / Remaining Implementation-time Reconfirmationへ置き換える
- Review DateとReview Referenceを追加する

---

## 5. Cross-document Review Limitation

今回の作業環境で完全な内容を確認できた関連資料は次である。

- `ai-design.md` Version 11
- `ai-design-phase0-reevaluation.md`

`api-design.md`は利用可能なCopyが途中で終了しており、`database-design.md`、`backend-design.md`、`decisions.md`の最新版は確認できなかった。

そのため、次は再レビューの必須確認対象とする。

- SSE Event / Timeout / Replay Contract
- User / Assistant Message保存順序とFailure State
- AI Capability BoundaryのPackage Structure
- Accepted ADRとの整合

これは`ai-design.md`単体レビューの指摘を無効にしないが、Project横断の最終承認は関連Document確認後に行う。

---

## 6. OpenAI Specification Verification

2026-08-16 JST時点の公式OpenAI Documentationで次を確認した。

| Item | Verification |
|---|---|
| Responses API | Phase 1 Text Generationに使用可能 |
| `store=false` | Provider-side Response Storageを無効化する明示設定として有効 |
| `POST /v1/responses/input_tokens` | Input Token Count Endpointとして存在 |
| `gpt-5.6-terra` | Responses API、StreamingおよびReasoning Effort `low`をSupport |
| Java SDK | 公式Java SDKが提供されている |

Reference:

- <https://developers.openai.com/api/docs/guides/migrate-to-responses>
- <https://developers.openai.com/api/docs/models/gpt-5.6-terra>
- <https://developers.openai.com/api/reference/resources/responses/subresources/input_tokens/methods/count/>
- <https://developers.openai.com/api/reference/java/>

公式Java SDK Documentationの掲載VersionはReview時点で`4.51.0`である。`ai-design.md`が固定する`4.50.0`を自動変更する必要はないが、実装開始前に`4.50.0`でResponses Streaming、Input Token Count、Retry無効化およびRequest ID取得が成立することをAdapter Testで確認する。

---

## 7. Final Decision

**Review Result: CHANGES REQUIRED**

Architectureの方向性は承認可能であり、大規模な再設計は不要である。

次の順序で修正する。

1. AI-REV-001: ADRと関連Architecture Documentを同期する
2. AI-REV-002〜006: `ai-design.md`の矛盾とContract不足を修正する
3. AI-REV-007〜008: 軽微事項とStatusを整理する
4. 最新のAPI / Database / Backend / ADRと横断整合を確認する
5. AI Design Re-reviewを実施する

必須修正完了後、Phase 1 AI DesignはImplementation-readyとして再判定できる。
