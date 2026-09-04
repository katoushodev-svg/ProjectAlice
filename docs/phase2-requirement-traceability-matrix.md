# Project Alice — Phase 2 Requirement Traceability Matrix

## 1. Matrix Information

| Item | Value |
|---|---|
| Version | 4 |
| Status | Accepted / Integrated — Phase 2 Final Review Passed |
| Date | 2026-09-02 JST |
| Requirements Baseline | `requirements.md` Version 11、Phase 2 Approved |
| Scope | P2-FR-001〜065、P2-NFR-001〜008（73件） |
| API Baseline | `api-design.md` Sections 31〜45、API2-001〜234 Accepted |
| Domain Baseline | `memory-design.md` MD-001〜MD-133 Accepted |
| Database Baseline | `database-design.md` Sections 29〜48、DB2-001〜DB2-243 Accepted |
| Persistence Test Baseline | `test-design.md` Section 43、P2-DB-TC-001〜017 Accepted |
| Security Baseline | `security-design.md` Sections 31〜44、SEC2-001〜038 Accepted |
| Security Test Baseline | `test-design.md` Section 44、P2-SEC-TC-001〜020 Accepted |
| Frontend UI Baseline | `phase2-frontend-ui-design.md` Version 2、FUI2-001〜030 Accepted |
| Frontend UI Test Baseline | `test-design.md` Section 45、P2-FUI-TC-001〜012 Accepted |
| Test ID Policy | RequirementごとにStable IDを一つ以上割当 |

本MatrixはPhase 2 RequirementからPublic API、Backend内部Boundary、非API設計およびTest IDへの詳細TraceabilityのSource of Truthである。空欄を許可せず、Public APIが不要な要件は`N/A — Internal only`、未設計箇所は`OPEN GATE`と明記する。

## 2. Status Rules

| Status | Meaning |
|---|---|
| `COVERED` | 承認済み設計へ追跡でき、対応Test IDがある |
| `OPEN GATE` | Requirementを満たす設計またはTest責務が不足 |
| `OUT OF SCOPE` | Approved Requirementの対象外。理由必須 |

Test IDはTest実装名を固定する論理IDであり、Unit / Contract / Integration / E2Eの具体的Class名から独立する。実装時は`test-design.md`の共通分類に従い、各IDを少なくとも一つの実行可能TestまたはEvaluationへBindingする。

## 3. Functional Requirement Matrix

| Requirement | Requirement Summary | Public API / Contract | Internal Boundary | Non-API Design | Test ID | Status |
|---|---|---|---|---|---|---|
| P2-FR-001 | User Profile管理 | Sections 31〜34: list/search/register/get/update | Memory Category / CRUD Use Cases | Memory Sections 7〜10、Frontend Memory管理 | P2-TC-FR-001 | COVERED |
| P2-FR-002 | 趣味・嗜好・価値観管理 | Sections 31〜34: Memory CRUD | Candidate Validation / Category | Memory Sections 7〜11 | P2-TC-FR-002 | COVERED |
| P2-FR-003 | Engineering Category管理 | Sections 32〜34: Category / Resource / CRUD | Category Taxonomy | Memory Sections 7、9 | P2-TC-FR-003 | COVERED |
| P2-FR-004 | Project Category管理 | Sections 32〜34: Category / Resource / CRUD | Category Taxonomy | Memory Sections 7、9 | P2-TC-FR-004 | COVERED |
| P2-FR-005 | 関連Memoryを回答利用 | Section 36: memory-usage transparency | AnswerMemorySearch / Context Assembly | Memory Section 14、AI Context Boundary | P2-TC-FR-005 | COVERED |
| P2-FR-006 | Conversation HistoryとMemory分離 | N/A — Internal only | conversation.application → memory.application | Memory Sections 3〜6、禁止依存 | P2-TC-FR-006 | COVERED |
| P2-FR-007 | 人物情報管理 | Sections 32〜34: PERSON Category CRUD | Category / Candidate Validation | Memory Sections 7、9 | P2-TC-FR-007 | COVERED |
| P2-FR-008 | 悩み・相談と状態管理 | Sections 32〜34: LIFE_CONTEXT / state update | Lifecycle / Currentness | Memory Sections 8、9、14 | P2-TC-FR-008 | COVERED |
| P2-FR-009 | 店舗・場所管理 | Sections 32〜34: PLACE Category CRUD | Category / Candidate Validation | Memory Sections 7、9 | P2-TC-FR-009 | COVERED |
| P2-FR-010 | OTHERの安全な分類 | Sections 32〜34: Category validation | Taxonomy / OTHER gate | Memory Section 9 | P2-TC-FR-010 | COVERED |
| P2-FR-011 | 明示的な記憶要求 | POST /memories、既存Conversation API | Explicit Capture / Save Decision | Memory Sections 10〜11、AI intent boundary | P2-TC-FR-011 | COVERED |
| P2-FR-012 | 通常Conversationから候補抽出 | N/A — Internal only | ExtractMemoryCandidatesUseCase | Memory Section 11、AI Capability | P2-TC-FR-012 | COVERED |
| P2-FR-013 | 自動保存判断の評価軸 | N/A — Internal only | Automatic Save Policy | Memory Section 11 | P2-TC-FR-013 | COVERED |
| P2-FR-014 | 一時情報を自動保存しない | N/A — Internal only | Long-term Value Gate | Memory Section 11 | P2-TC-FR-014 | COVERED |
| P2-FR-015 | 通常Memoryの自動保存 | Section 35 Preferences、Conversation内部Flow | Automatic Capture / Commit Preflight | Memory Sections 10〜11 | P2-TC-FR-015 | COVERED |
| P2-FR-016 | 曖昧時は確認または見送り | Relation ReviewはSections 38、45 | Save Decision / Confirmation | Memory Sections 11〜12、23 | P2-TC-FR-016 | COVERED |
| P2-FR-017 | Sensitiveを自動保存しない | Sections 34、38 Domain Problems | Sensitivity Gate / Explicit Consent | Memory Section 11、Security Design | P2-TC-FR-017 | COVERED |
| P2-FR-018 | 関連なしは新規保存 | POST /memories | Relation Decision `NONE` | Memory Section 12 | P2-TC-FR-018 | COVERED |
| P2-FR-019 | 同一内容を重複保存しない | POST /memories outcome `NO_CHANGE` / `CONFIRMED` | `EQUIVALENT` Decision | Memory Section 12 | P2-TC-FR-019 | COVERED |
| P2-FR-020 | 補足情報を意味保持して統合 | Sections 34、38、45 Relation Review | ResolveMemoryRelationUseCase | Memory Sections 12、23 | P2-TC-FR-020 | COVERED |
| P2-FR-021 | 明確な変更を更新 | PATCH /memories、Relation Resolve | Update / `SUPERSEDES` Decision | Memory Sections 12、23 | P2-TC-FR-021 | COVERED |
| P2-FR-022 | 判断不能な矛盾を確認 | Sections 38、45 Relation Review | `CONFLICT` / `UNCERTAIN` | Memory Sections 12、23 | P2-TC-FR-022 | COVERED |
| P2-FR-023 | 単語一致だけで判断しない | N/A — Internal only | Structured Relation Decision | Memory Section 12 | P2-TC-FR-023 | COVERED |
| P2-FR-024 | 自然言語で個別削除 | DELETE /memories、既存Conversation API | DeleteMemoryUseCase | Memory Section 13、AI intent boundary | P2-TC-FR-024 | COVERED |
| P2-FR-025 | 削除対象曖昧時に確認 | Sections 37 Deletion Plan / Preview | Resolve Delete Targets | Memory Section 13 | P2-TC-FR-025 | COVERED |
| P2-FR-026 | 削除内容の自動再登録防止 | Sections 34、37、40 Guards | Re-registration Guard / Reset Point | Memory Sections 13、20、Security / DB | P2-TC-FR-026 | COVERED |
| P2-FR-027 | 明示希望時だけ再登録 | Sections 34、40 Restore reintroduction | Explicit Re-registration Use Case | Memory Sections 13、20 | P2-TC-FR-027 | COVERED |
| P2-FR-028 | 確認後の全Memory削除 | Section 37 `scope=ALL` / confirm / execute | Delete-all / Reset Point | Memory Section 13、Frontend Plan UX | P2-TC-FR-028 | COVERED |
| P2-FR-029 | 不完全結果を成功表示しない | Sections 34、37、38、40 Result Model | Partial / Unknown Reconciliation | Memory Sections 10、13、20 | P2-TC-FR-029 | COVERED |
| P2-FR-030 | 通常会話へ自然に利用 | N/A — Internal answer flow | Context Assembly | Memory Section 14、AI Design | P2-TC-FR-030 | COVERED |
| P2-FR-031 | 大きく影響した前提を示す | Section 36 Memory Usage | Explainability / Response Composition | Memory Section 14、Frontend | P2-TC-FR-031 | COVERED |
| P2-FR-032 | Sensitive利用を必要最小限にする | Section 36 transparency | Sensitive Eligibility / Context Budget | Memory Section 14、Security Design | P2-TC-FR-032 | COVERED |
| P2-FR-033 | 利用Memoryを説明 | GET message memory-usage | ExplainMemoryUsageUseCase | Memory Section 14、Frontend | P2-TC-FR-033 | COVERED |
| P2-FR-034 | 利用Memoryを訂正・更新・削除 | memory-usage + Sections 34 CRUD | Usage-to-Management Navigation | Frontend Design、Memory Sections 10、14 | P2-TC-FR-034 | COVERED |
| P2-FR-035 | 現在性不明を断定しない | Section 36 temporal role / current resource | Currentness Evaluation | Memory Sections 8、14 | P2-TC-FR-035 | COVERED |
| P2-FR-036 | Flutter本体に管理機能 | Sections 31〜40 Backend Contract | Frontend Repository / Use Cases | Frontend Section 25、Phase 2 UI Sections 3〜4 | P2-TC-FR-036、P2-FUI-TC-001 | COVERED |
| P2-FR-037 | iOS一覧・分類・検索・詳細とPrivate LAN | Sections 31〜33、41 Security | Secure API Client | Frontend Sections 20、25、Phase 2 UI Sections 5〜7 | P2-TC-FR-037、P2-FUI-TC-001〜003 | COVERED |
| P2-FR-038 | 管理画面で登録・編集・個別削除 | Sections 34、45 | Memory Mutation / Relation Resolution | Frontend Sections 20、24〜25、Phase 2 UI Sections 7〜8、13 | P2-TC-FR-038、P2-FUI-TC-003〜006、009 | COVERED |
| P2-FR-039 | 全削除と部分結果確認 | Section 37 Plan API | Delete-all / Reconciliation | Frontend Sections 21、25、Phase 2 UI Section 11 | P2-TC-FR-039、P2-FUI-TC-010 | COVERED |
| P2-FR-040 | 詳細Fieldと未確認表示 | Section 32 MemoryResource | GetMemoryUseCase | Frontend Section 25、Phase 2 UI Section 7 | P2-TC-FR-040、P2-FUI-TC-003 | COVERED |
| P2-FR-041 | 内部Score等を非表示 | Sections 32、33、36 Security non-fields | Presentation DTO Mapping | Frontend Section 25、Phase 2 UI Sections 6、10、Security Design | P2-TC-FR-041、P2-FUI-TC-001、008 | COVERED |
| P2-FR-042 | 編集・削除を回答へ反映 | Sections 34 Mutation、36 current status | Projection Repair / Final Validation | Memory Sections 13〜14、Phase 2 UI Sections 7〜10、DB Design | P2-TC-FR-042、P2-FUI-TC-003〜005、008〜009 | COVERED |
| P2-FR-043 | 将来Desktop共通Capability | 同一`/api/v1` Contract | Platform-neutral Application Boundary | Frontend Sections 20〜25、Phase 2 UI Section 15 | P2-TC-FR-043、P2-FUI-TC-001〜012 | COVERED |
| P2-FR-044 | 自然言語で登録・確認・訂正・更新・削除 | 既存Conversation API + Memory APIs | Intent → Memory Use Cases | AI Section 20、Memory Section 10 | P2-TC-FR-044 | COVERED |
| P2-FR-045 | 覚えている内容を自然言語確認 | 既存Conversation API、Sections 32〜33 | List / Search Memory Capability | Memory Section 10、AI Design | P2-TC-FR-045 | COVERED |
| P2-FR-046 | 自動保存ON/OFF・初期ON | Section 35 Preferences | MemoryPreferences | Memory Sections 8、10 | P2-TC-FR-046 | COVERED |
| P2-FR-047 | 自動保存OFFでも明示保存可 | Sections 34〜35 | Explicit vs Automatic Capture | Memory Sections 10〜11 | P2-TC-FR-047 | COVERED |
| P2-FR-048 | 回答利用ON/OFF・初期ON | Section 35 Preferences | Answer Use Gate | Memory Sections 8、14 | P2-TC-FR-048 | COVERED |
| P2-FR-049 | 利用OFF時にContext送信停止 | Section 35 Preferences | Provider直前Preference Recheck | Memory Section 14、AI Design | P2-TC-FR-049 | COVERED |
| P2-FR-050 | UIと自然言語で設定変更 | Section 35 + Conversation API | UpdateMemoryPreferencesUseCase | Frontend Section 25、Phase 2 UI Section 9、AI Design | P2-TC-FR-050、P2-FUI-TC-007 | COVERED |
| P2-FR-051 | 一律自動期限なし | N/A — Internal persistence policy | PersonalMemory Lifecycle | Memory Section 8、DB Design | P2-TC-FR-051 | COVERED |
| P2-FR-052 | 未使用だけで自動削除しない | N/A — Internal lifecycle policy | Retention / Deletion Boundary | Memory Sections 8、13 | P2-TC-FR-052 | COVERED |
| P2-FR-053 | 古い可能性を考慮 | Section 36 currentness fields | Currentness Evaluation | Memory Sections 8、14 | P2-TC-FR-053 | COVERED |
| P2-FR-054 | 現在性確認と日時更新 | POST /memories/{id}/confirm、PATCH | Confirm / Update Use Cases | Memory Sections 8、10 | P2-TC-FR-054 | COVERED |
| P2-FR-055 | 解決済み状態へ更新 | PATCH /memories state | Lifecycle Transition | Memory Section 8 | P2-TC-FR-055 | COVERED |
| P2-FR-056 | 取得障害時も会話継続 | N/A — Internal answer flow | Graceful Degradation | Memory Section 14、AI / Test Design | P2-TC-FR-056 | COVERED |
| P2-FR-057 | 参照できたふりをしない | Section 36 `UNAVAILABLE` | Answer Composition / Trace | Memory Section 14、AI Design | P2-TC-FR-057 | COVERED |
| P2-FR-058 | 変更失敗・不明・部分成功と再試行 | Sections 34、37、38、40、45 | Idempotency / Reconciliation | Frontend / DB / Test Design | P2-TC-FR-058 | COVERED |
| P2-FR-059 | 自動保存失敗で回答を失敗させない | N/A — Internal post-answer flow | Automatic Capture Failure Isolation | Memory Sections 10〜11、AI Design | P2-TC-FR-059 | COVERED |
| P2-FR-060 | 復旧後に無断再実行しない | Mutation Contract / Idempotency | Pending Operation Policy | Memory Sections 10、13、20、23 | P2-TC-FR-060 | COVERED |
| P2-FR-061 | 管理画面読込失敗を成功表示しない | Sections 33、34、38 Errors | Frontend Load State | Frontend Section 25、Phase 2 UI Sections 5、14 | P2-TC-FR-061、P2-FUI-TC-001〜005 | COVERED |
| P2-FR-062 | Memoryと設定をArchive Export | POST /memory-backups/export | ExportMemoryBackupUseCase | Memory Section 20、Phase 2 UI Section 12、Security / Frontend | P2-TC-FR-062、P2-FUI-TC-011 | COVERED |
| P2-FR-063 | 検証・影響確認後Restore | Sections 40〜44 Restore Plan | Inspect / Resolve / Execute Restore | Memory Sections 20〜22、Phase 2 UI Section 12 | P2-TC-FR-063、P2-FUI-TC-012 | COVERED |
| P2-FR-064 | 形式・Version・完全性・Size検証 | Sections 39〜44 Archive Boundaries | Archive Inspector | Memory Sections 20〜22、Phase 2 UI Section 12、Security / Test | P2-TC-FR-064、P2-FUI-TC-011〜012 | COVERED |
| P2-FR-065 | Restore影響の明示確認 | Section 40 Plan / confirm / execute | ResolveMemoryRestorePlanUseCase | Memory Sections 20〜22、Phase 2 UI Section 12 | P2-TC-FR-065、P2-FUI-TC-012 | COVERED |

## 4. Non-functional Requirement Matrix

| Requirement | Requirement Summary | Public API / Contract | Internal Boundary | Non-API Design | Test ID | Status |
|---|---|---|---|---|---|---|
| P2-NFR-001 | Privacy / Sensitive Data | Sections 31、38〜45 security and non-fields | Sensitivity / Secret / Data minimization gates | Security Sections 31〜44、Memory全体、P2-SEC-TC-001〜009 / 015〜019 | P2-TC-NFR-001 | COVERED |
| P2-NFR-002 | User Control | Sections 33〜35、37、40、45 | Memory CRUD / Preferences / Plan Use Cases | Frontend Sections 20〜24 | P2-TC-NFR-002 | COVERED |
| P2-NFR-003 | Explainability | Section 36 Memory Usage | ExplainMemoryUsageUseCase | Memory Section 14、Frontend | P2-TC-NFR-003 | COVERED |
| P2-NFR-004 | Reliability | Sections 34、37〜45 Error / Idempotency / Result | Graceful Degradation / Reconciliation | DB Sections 30〜48、Security Sections 37〜41、Test Sections 35〜44 | P2-TC-NFR-004 | COVERED |
| P2-NFR-005 | Currentness | Sections 32、34、36 | Currentness / Final Validation | Memory Sections 8、14 | P2-TC-NFR-005 | COVERED |
| P2-NFR-006 | Testability | 全Phase 2 Contract Section | Capability Port / Fake Clock / Fault Injection | Test Sections 35〜44、本Matrix、P2-SEC-TC-001〜020 | P2-TC-NFR-006 | COVERED |
| P2-NFR-007 | Network / Access Boundary | Section 41、全`/api/v1/**` | Security Profile / Allowed Device Auth | Security Sections 31 / 39 / 42〜43、Frontend Section 20、DB Sections 29 / 38、Test Sections 31 / 43〜44 | P2-TC-NFR-007 | COVERED |
| P2-NFR-008 | Portability / Backup Security | Sections 39〜44 | Versioned Archive / Restore Plan | Memory Sections 20〜22、Security Sections 32 / 35 / 37 / 41、P2-SEC-TC-015〜016 | P2-TC-NFR-008 | COVERED |

## 5. Test ID Binding Rules

| Test ID Range | Required Test Level |
|---|---|
| P2-TC-FR-001〜010 | Domain Unit + API Resource / Category Contract |
| P2-TC-FR-011〜023 | Domain Unit + Application Contract + Relation E2E |
| P2-TC-FR-024〜029 | Deletion Contract + Fault-injection Integration + UI E2E |
| P2-TC-FR-030〜035 | Context / Transparency Contract + AI Evaluation |
| P2-TC-FR-036〜050 | API Client Contract + Widget / Mobile E2E + Natural-language E2E |
| P2-TC-FR-051〜061 | Lifecycle / Currentness / Failure Contract + Fake Clock |
| P2-TC-FR-062〜065 | Archive Boundary + Security + Restore E2E |
| P2-TC-NFR-001〜008 | Cross-cutting Security / Reliability / Testability Gate |
| P2-SEC-TC-001〜020 | Security・Logging・Observability Contract + Negative / Fault-injection Gate |

一つのTestが複数Requirementを検証してもよいが、Test Report / Case Metadataへ全該当IDを付ける。Requirement変更時は同じChange Setで本Matrix、関連DesignおよびTest Metadataを更新する。

## 6. Coverage Result and Gate

| Metric | Result |
|---|---:|
| Approved Requirements | 73 |
| Individually Mapped | 73 |
| Stable Test IDs | 73 |
| Security Test IDs | 20 |
| Persistence Access-pattern Test IDs | 17 |
| Frontend UI Test IDs | 12 |
| `OPEN GATE` | 0 |
| Blank Responsibility Cells | 0 |

CR-007とP2-FINAL-CR-001の設計上の欠落は本Matrixで解消した。最終統合確認で73件の個別Mapping、Stable Requirement Test ID 73件、Security Test ID 20件、Persistence Test ID 17件、Frontend UI Test ID 12件、空欄0件および`OPEN GATE` 0件を確認した。Test実装自体はPhase 2 Implementation時に行い、実装前Readiness Gateでは各Stable Test IDに実行可能Test計画が割り当てられていることを確認する。

## 7. Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| TM-001 | 73件のApproved Requirementを一行ずつ追跡する | Accepted |
| TM-002 | Public API不要時も`N/A — Internal only`と内部責務を明示する | Accepted |
| TM-003 | 未対応を空欄でなく`OPEN GATE`として扱う | Accepted |
| TM-004 | RequirementごとにStable Test IDを最低一つ割り当てる | Accepted |
| TM-005 | Requirement / Design / Test変更を同じChange Setで更新する | Accepted |
| TM-006 | Open Gateが一件でもあればImplementation Readyへ昇格しない | Accepted |
| TM-007 | SEC2-001〜038をP2-SEC-TC-001〜020へBindingしSecurity Review Open Finding 0件を維持する | Accepted |
| TM-008 | FUI2-001〜030をP2-FUI-TC-001〜012へBindingしFinal Review Open Critical / High 0件を維持する | Accepted |
