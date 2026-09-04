# Alice Requirements

## 1. Document Information

| Item | Value |
|---|---|
| Document | `requirements.md` |
| Version | 12 |
| Status | Phase 1-3 Approved / Phase 4 Baseline |
| Last Updated | 2026-09-02 JST |
| Phase Source | `mvp.md`、Accepted ADR-014、採用済みPhase 2 Requirement DecisionおよびPhase 3 / 4要件整理引継ぎ資料 |
| Phase 2 Requirements Review | Approved — 2026-08-25 JST |
| Phase 3 Requirements Review | Approved — 2026-09-02 JST |

本ドキュメントは、Project Aliceが満たすべき主要な機能要件・非機能要件を定義する。

Requirement IDは、Detailed Design、Test CaseおよびImplementationのTraceabilityに使用する。IDの意味を実装都合で変更してはならない。

### 1.1 Section Status

| Section | Status |
|---|---|
| Phase 1 | Approved |
| Phase 2 | Approved — Requirements Review完了 |
| Phase 3 | Approved — Requirements Review完了 |
| Phase 4 | Phase 0 Architecture Baseline |

Phase 2 Sectionは、ユーザーとの要求整理で採用された内容を統合し、Phase 2 Personal Memory Requirements Reviewおよび指摘反映後の再Reviewを完了したApproved Requirementである。Phase 2のArchitecture、Class、Interface、API、DynamoDB Schemaまたは検索Technologyを本Documentだけから推測して実装してはならない。

Phase 3 Sectionは、Phase 3 / 4要件整理引継ぎ資料と`phase3-requirements-review.md`の一括承認を統合したApproved Requirementである。Tool Interface、Connector SDK、Permission / Approval Model、API、PersistenceまたはCredential方式はDetailed Designで確定し、本Documentだけから推測して実装してはならない。

## 2. Phase Definition

Accepted ADR-014に従い、Phase 0では次のPhase 1〜4を設計対象とする。

| Phase | Scope |
|---|---|
| Phase 1 | Conversation + AI + Conversation History |
| Phase 2 | Personal Memory |
| Phase 3 | Tools / External Services + Engineering Support |
| Phase 4 | Agent + PC / Browser Operation + Voice |

Phase 1は実装へ直接投入できる詳細度まで確定する。Phase 2〜4はArchitecture、責務、Boundary、主要Model、主要Flow、Port、SecurityおよびPermissionをPhase 0で確定し、変更可能性が高いTechnologyは実装時再確認としてよい。

---

## 3. Phase 1 — Conversation MVP

### 3.1 Purpose

Aliceと自然なテキスト会話ができ、Conversation Historyを継続利用できる基本基盤を構築する。

### 3.2 Functional Requirements

#### P1-FR-001 Text Chat

ユーザーはSmartphone ApplicationからテキストによってAliceと会話できること。

#### P1-FR-002 Single Conversation

Phase 1では単一Conversationを継続利用できること。Conversation一覧、Conversation切替および複数Conversation管理を要求しない。

#### P1-FR-003 AI Provider Integration

Alice CoreはProvider-independentなAI Capability Portを通してLLMを利用できること。OpenAI SDK ObjectをAlice Coreへ公開しないこと。

#### P1-FR-004 Alice Personality

Aliceとして一定の人格・行動方針を持った回答ができること。

#### P1-FR-005 Streaming Response

Aliceの回答を生成途中からSmartphone ApplicationへStreaming表示できること。完了したAssistant Messageは保存済みCanonical Resultとして確定できること。

#### P1-FR-006 Conversation History Persistence

ユーザーとAliceのConversation Historyを保存できること。

Conversation HistoryはPersonal Memoryではなく、Phase 1の`conversation` Featureが管理すること。

#### P1-FR-007 Conversation History Reference

保存したConversation Historyを後から取得し、時系列順に確認できること。

#### P1-FR-008 Safe Retry

通信切断等で結果を確認できない場合、同じLogical Message Sendを重複実行せず結果確認またはReplayできること。

### 3.3 Success Criteria

- P1-FR-001〜P1-FR-008がAutomated TestまたはAI EvaluationへTraceされている
- Aliceとして一貫した回答ができる
- 生成中の回答を表示できる
- Conversation Historyを保存・再取得できる
- App再起動後も保存済みHistoryを確認できる
- RetryによってUser Message、Assistant MessageまたはAI呼出しが重複しない

### 3.4 Out of Scope

- Personal Memory
- RAG / Vector Search
- Tool Calling / External Service Operation
- Voice
- PC / Browser Operation
- Autonomous Agent
- Multi User / Multi Conversation
- Public Internet Backend

---

## 4. Phase 2 — Personal Memory

### 4.1 Purpose

Conversationをまたいでも、Aliceがユーザーについて長期的に有用な情報を記憶・参照し、ユーザーに合わせた回答へ利用できる状態を作る。

Personal MemoryはConversation Historyとは異なる責務・Modelとして管理する。

```text
Conversation History
        │
        │ 必要な情報を抽出
        ▼
Personal Memory
        │
        │ 必要なときに検索
        ▼
AI Context
```

Personal Memoryは「過去の会話全文」ではなく、Conversationを越えて再利用する価値があるユーザー固有情報である。

### 4.2 Functional Requirements

#### 4.2.1 Memory Content

| ID | Requirement |
|---|---|
| P2-FR-001 | User ProfileをPersonal Memoryとして管理できること |
| P2-FR-002 | ユーザーの趣味、嗜好、Preferenceおよび価値観を保存できること |
| P2-FR-003 | ユーザー自身の得意分野、苦手分野、技術経験、開発方針、学習履歴および長期的に再利用する設計上のPreferenceを、Personal Memory内のEngineering Categoryとして管理できること |
| P2-FR-004 | Project Alice等、ユーザーが継続的に扱うProjectについて、ユーザーが明示的に記憶を希望した要点、方針、判断および長期Contextを、Personal Memory内のProject Categoryとして管理できること |
| P2-FR-005 | 現在の質問に関連するPersonal Memoryを取得し、必要な範囲で回答へ利用できること |
| P2-FR-006 | Conversation HistoryとPersonal Memoryを別の責務・Modelとして管理できること |
| P2-FR-007 | ユーザーが記憶を希望した友人、家族、著名人その他の人物に関する情報を管理できること |
| P2-FR-008 | Conversationをまたいで利用価値がある過去の悩み、相談およびその状態を管理できること |
| P2-FR-009 | ユーザーが登録を希望した店舗、場所その他の対象をMemoryとして管理できること |
| P2-FR-010 | その他、Conversationをまたいで再利用する価値があるユーザー固有情報を、既存Categoryへ無理に誤分類せず管理できること |

店舗の営業時間、営業状況、価格等の現在情報はPersonal Memoryとして断定しない。Phase 2は「ユーザーがその店舗を記憶すること」を担当し、現在情報の取得はPhase 3のTool責務とする。

Engineering MemoryおよびProject Memoryは、独立したFeature、ModuleまたはPersistence Boundaryではなく、Phase 2の`memory` Featureが管理するPersonal MemoryのCategoryとする。

Engineering / Project Categoryには、使用経験のある技術、得意・苦手な技術、好みのArchitectureまたは開発方針、ユーザーが明示的に記憶を希望したProject上の要点、および継続的に利用する判断や背景等の簡潔な長期情報を保存できる。一方、Source Code、Design Document全文、GitHub Repository全体、Issue / Pull Request全文、Build Log、大量のProject資料または常に変化するRepositoryの現在状態をPersonal Memoryとして丸ごと保存しない。

Projectに正式なDesign Document、Accepted ADR、RepositoryまたはExternal Service上の情報が存在する場合、それらをProject情報のSource of Truthとする。Personal Memoryはユーザー向けの長期Contextとして利用し、正式資料を置き換えたり、矛盾するMemoryによって正式Decisionを上書きしたりしない。

#### 4.2.2 Memory Capture and Saving Decision

| ID | Requirement |
|---|---|
| P2-FR-011 | ユーザーが「覚えておいて」等で明示的に記憶を要求した場合、保存禁止対象を除き、原則としてPersonal Memoryへ保存できること。センシティブ情報については、この明示的な記憶要求をユーザーの保存意思として扱うこと |
| P2-FR-012 | 通常Conversationから、長期的な利用価値を持つMemory候補をAliceが抽出できること |
| P2-FR-013 | 自動保存判断で長期的有用性、将来の再利用可能性、ユーザーとの関連性、センシティブ性および既存Memoryとの整合性を考慮すること |
| P2-FR-014 | 一時的、偶発的または長期利用価値が低い情報を原則として自動保存しないこと |
| P2-FR-015 | センシティブ情報に該当しない通常の有用なMemoryは、会話を毎回中断する確認を行わず自動保存できること |
| P2-FR-016 | 保存判断が曖昧な場合、Aliceが推測で保存せず必要に応じてユーザーへ確認できること |
| P2-FR-017 | ユーザー本人または第三者に関するセンシティブ情報を、通常Conversationから自動保存しないこと。保存するには、ユーザーによる明示的な記憶要求、または保存対象を示したうえでの明示的な確認を必要とすること。センシティブ性を判断できない場合は、保存せずユーザーへ確認すること |

保存判断では、次の優先順位を適用する。

1. Credential、API Key、Authentication Tokenその他のSecretは、ユーザーが明示的に依頼してもPersonal Memoryへ保存しない。
2. センシティブ情報は通常Conversationから自動保存せず、ユーザーによる明示的な記憶要求、または保存前の明示的な確認がある場合だけ保存できる。
3. センシティブ情報に該当しない通常情報は、保存基準を満たす場合、保存のたびに確認せず自動保存できる。

センシティブ情報には、少なくとも健康・医療・服薬情報、精神状態または深刻な悩み、金融・資産情報、本人確認または識別に関する情報、正確な住所・現在位置、私生活または親密な人間関係、法的問題、および友人・家族その他の第三者に関する非公開情報を含む。詳細な分類基準、確認文言および利用制約はPhase 2のSecurity / Memory詳細設計で確定する。

#### 4.2.3 Duplicate, Update and Conflict

| ID | Requirement |
|---|---|
| P2-FR-018 | 関連Memoryが存在しない場合、新規Memoryとして保存できること |
| P2-FR-019 | 同一内容のMemoryが存在する場合、重複保存しないこと |
| P2-FR-020 | 新しい情報が既存Memoryの追加・補足である場合、意味を失わない形で統合できること |
| P2-FR-021 | ユーザーが明確な変更を伝えた場合、最新情報へ更新できること |
| P2-FR-022 | 新旧情報が矛盾するか判断できない場合、勝手に上書きせずユーザーへ確認すること |
| P2-FR-023 | 単純な単語一致だけで同一、矛盾、更新またはユーザーの能力を判断しないこと |

例えば「Javaは苦手」と「Javaを仕事で使っている」は両立可能であり、後者だけから「Javaが得意」へ変更してはならない。

#### 4.2.4 Deletion and Forgetting

| ID | Requirement |
|---|---|
| P2-FR-024 | ユーザーは「これを忘れて」等の自然言語によってPersonal Memoryを削除できること。削除後は対象Memoryを検索、管理画面表示、回答ContextまたはPersonalizationへ利用しないこと |
| P2-FR-025 | 削除対象を一意に特定できない場合、Aliceは推測で削除せず候補を示して確認すること |
| P2-FR-026 | ユーザーが明示的に削除したMemoryまたは実質的に同じ内容を、保存済みConversation Historyまたは通常Conversationだけを根拠として自動再登録しないこと |
| P2-FR-027 | 削除済みの内容は、ユーザーが後から明示的に再登録を希望した場合に限り、新しい保存意思として再登録できること |
| P2-FR-028 | ユーザーは確認操作を経て、すべての有効なPersonal Memoryを一括削除できること。一括削除時はMemory Reset Pointを設定し、それ以前のConversation HistoryからPersonal Memoryを自動的に再生成しないこと |
| P2-FR-029 | 保存、更新または削除が成功していない場合、結果不明または一部成功の場合を含め、Aliceは操作全体が成功したものとして案内しないこと。確認できた実際の結果をユーザーへ表示すること |

Personal Memoryの削除は、Aliceが管理する有効なMemoryとその後の利用を対象とし、Conversation History自体の削除とは別の操作とする。削除した情報がConversation Historyに残っている場合も、その情報をPersonal Memoryとして再利用、回答Contextへ追加または自動再登録してはならない。

再登録防止のための内部情報を保持する場合、削除したMemory本文をそのまま保持せず、再登録防止以外の目的で利用してはならない。当該情報を有効なPersonal Memory、回答ContextまたはPersonalizationへ利用しない。具体的な再登録防止方式とMemory Reset Pointの表現方法はPhase 2詳細設計で確定する。

Personal Memoryの削除は、過去にAI Providerへ送信済みのRequestまたはAliceの管理外にあるProvider側保持Dataを遡って削除したことを意味しない。削除後の新しいRequestでは、削除済みMemoryをPersonal MemoryとしてAI Providerへ送信しない。

#### 4.2.5 Memory Use and Transparency

| ID | Requirement |
|---|---|
| P2-FR-030 | AliceはPersonal Memoryを通常Conversationへ自然に利用し、利用のたびに機械的な通知を表示しないこと |
| P2-FR-031 | Memoryが回答、提案または判断へ大きく影響した場合、利用した前提をユーザーが自然に理解できる表現を使用すること |
| P2-FR-032 | センシティブなMemoryは、現在の要求へ明確に関連し、利用する必要がある場合に限って必要最小限の範囲で使用すること。回答へ大きく影響した場合は、利用した前提または理由をユーザーが自然に理解できるようにすること |
| P2-FR-033 | ユーザーから求められた場合、Aliceは回答に利用したMemoryを説明できること |
| P2-FR-034 | ユーザーは回答に利用されたMemoryを訂正、更新または削除できること |
| P2-FR-035 | Memoryが現在も正しいか不明な場合、現在の事実として断定せず必要に応じて確認すること |

#### 4.2.6 Memory Management

| ID | Requirement |
|---|---|
| P2-FR-036 | Alice本体のFlutter Application内にPersonal Memory管理機能を提供すること。別の管理専用Applicationを作成しないこと |
| P2-FR-037 | Phase 2ではiOS ApplicationからMemory一覧、Category別表示、検索および詳細確認を行えること。iOS Simulatorに加え、Security要件を満たした個人所有iPhoneからPrivate LAN経由で利用できること |
| P2-FR-038 | Memory管理画面からMemoryを新規登録、編集および個別削除できること |
| P2-FR-039 | Memory管理画面から確認操作を経て全Memoryを一括削除できること。一括削除が一部だけ成功した場合は、完了していないこと、削除済み範囲および残っているMemoryを確認できること |
| P2-FR-040 | Memory詳細で少なくとも内容、Category、明示登録か自動保存か、登録日時、最終更新日時および最終確認日時を確認できること。ユーザーによる確認実績がない場合は、最終確認日時を「未確認」として識別できること |
| P2-FR-041 | 技術的な内部Score、Embedding、Persistence Keyその他のInfrastructure情報を通常の管理画面へ表示しないこと |
| P2-FR-042 | Memoryの編集または削除結果を、その後のAliceの回答へ反映できること |
| P2-FR-043 | 将来のDesktop Applicationで同じ管理Capabilityを画面幅に適したLayoutで提供できる設計とし、Phase 2でDesktop UIを先行実装しないこと |

#### 4.2.7 Natural-language Management and Controls

| ID | Requirement |
|---|---|
| P2-FR-044 | ユーザーはMemory管理画面だけでなく、Aliceとの自然言語ConversationからMemoryを登録、確認、訂正、更新および削除できること |
| P2-FR-045 | ユーザーは「私について何を覚えているか」または特定Categoryについて質問し、保存中のMemoryを理解可能な形で確認できること |
| P2-FR-046 | 自動保存をON / OFFできること。初期状態はONとすること |
| P2-FR-047 | 自動保存がOFFの場合、通常Conversationから新しいMemoryを自動保存せず、ユーザーの明示的な記憶要求は保存可能とすること |
| P2-FR-048 | 通常回答へのPersonal Memory利用をON / OFFできること。初期状態はONとすること |
| P2-FR-049 | Memory利用がOFFの場合、保存済みMemoryを削除せず、通常ConversationへのPersonalization、関連Memoryの取得および回答Contextへの追加を停止すること。通常回答用のAI RequestへPersonal Memoryを送信しないこと |
| P2-FR-050 | ユーザーは自然言語Conversationと管理画面の両方から、自動保存と回答へのMemory利用をそれぞれ独立して変更できること |

自動保存設定と回答へのMemory利用設定は独立した設定とし、両方ON、自動保存だけOFF、Memory利用だけOFF、および両方OFFのすべてを許可する。

Memory利用がOFFの場合も、Memory管理画面と、ユーザーが明示的に要求したMemoryの登録、確認、訂正、更新および削除操作は利用可能とする。「私について何を覚えているか」等の明示的なMemory管理要求では、操作に必要な範囲に限ってMemoryを取得できる。ただし、取得したMemoryを当該管理要求と関係のない回答のPersonalizationへ流用してはならない。

#### 4.2.8 Currentness and Lifetime

| ID | Requirement |
|---|---|
| P2-FR-051 | Personal Memoryへ一律の自動有効期限を設定しないこと |
| P2-FR-052 | 長期間利用されていないことだけを理由としてMemoryを自動削除しないこと |
| P2-FR-053 | Memoryの種類、登録・更新・最終確認時期および新しいConversation情報を考慮し、古い可能性があるMemoryを現在の事実として断定しないこと |
| P2-FR-054 | 必要に応じてユーザーへMemoryの現在性を確認できること。ユーザーが現在も正しいと確認した場合は最終確認日時を更新し、内容が変化している場合はMemory内容と最終更新日時を更新すること |
| P2-FR-055 | 一時的な悩み等が解決した場合、削除だけでなく解決済み等の現在状態へ更新できること |

Memoryの日時は次の意味で扱う。

| 日時 | 更新条件 |
|---|---|
| 登録日時 | Memoryを最初に保存した時 |
| 最終更新日時 | Memoryの内容、Category、状態その他の管理対象情報を変更した時 |
| 最終確認日時 | ユーザーが保存内容を明示的に登録、編集または肯定した時 |

AliceがMemoryを検索、回答Contextへ追加または回答で使用しただけでは、最終確認日時を更新しない。自動保存しただけの場合、Aliceが正しいと推測した場合、またはユーザーが否定しなかっただけの場合も更新しない。ユーザーによる確認実績がないMemoryの最終確認日時は未設定とし、管理画面では「未確認」として表示する。

#### 4.2.9 Failure Behavior

| ID | Requirement |
|---|---|
| P2-FR-056 | Personal Memoryの取得障害時、可能な限りMemoryなしでConversationを継続できること |
| P2-FR-057 | Memoryを取得できない場合、AliceはMemoryを参照できたふりをしないこと |
| P2-FR-058 | 明示的な保存、更新または削除が失敗、結果不明または一部成功となった場合、ユーザーへ理解可能な状態表示と明示的な再試行手段を提供すること。再試行では、成功が確認済みの操作を重複実行しないこと |
| P2-FR-059 | 自動保存失敗によってConversation回答そのものを不必要に失敗させないこと |
| P2-FR-060 | Memory機能復旧後、失敗した変更操作をユーザーの意思確認なしに勝手に再実行しないこと |
| P2-FR-061 | Memory管理画面の読込失敗時、既知の成功状態として表示せず再読み込み手段を提供すること |

#### 4.2.10 Backup and Restore

| ID | Requirement |
|---|---|
| P2-FR-062 | ユーザーは、保存中の有効なPersonal MemoryおよびMemory設定を、ユーザー操作によってBackup ArchiveとしてExportできること |
| P2-FR-063 | ユーザーは、AliceがExportした対応Archiveを選択し、内容と影響を確認したうえでPersonal MemoryをRestoreできること |
| P2-FR-064 | Restore前にArchiveの形式、Version、完全性、Sizeおよび内容を検証し、無効、破損、未対応または安全でないArchiveを読み込まないこと。検証失敗をRestore成功として扱わないこと |
| P2-FR-065 | Restoreによって既存Memoryが追加、更新、置換または削除される可能性がある場合、実行前にユーザーへ影響を示して明示確認を要求すること |

### 4.3 User Use Cases

| ID | User Goal | Expected Outcome |
|---|---|---|
| P2-UC-001 | 「これを覚えておいて」と依頼する | 明示Memoryとして保存され、成功または失敗を確認できる |
| P2-UC-002 | 通常会話を行う | 長期価値がある非センシティブ情報だけが必要に応じて自動保存される |
| P2-UC-003 | Aliceから自分に合った回答を得る | 現在の質問に関連するMemoryだけが必要な範囲で利用される |
| P2-UC-004 | Aliceがなぜその回答をしたか確認する | 回答へ影響したMemoryを理解可能な形で説明される |
| P2-UC-005 | Aliceが覚えている内容を確認する | Conversationまたは管理画面で全体・Category別に確認できる |
| P2-UC-006 | 間違ったMemoryを訂正する | 対象を特定し、更新後の内容が以後の回答へ反映される |
| P2-UC-007 | 不要なMemoryを忘れさせる | 対象が削除され、Conversation Historyから勝手に再登録されない |
| P2-UC-008 | 削除済み内容をもう一度覚えさせる | 明示的な新しい意思として再登録される |
| P2-UC-009 | Memoryの自動保存だけを止める | 既存Memoryと明示保存を維持したまま自動保存だけが停止する |
| P2-UC-010 | Memoryを回答に使わせない | 保存済みMemoryを残したまま回答Contextへの利用が停止する |
| P2-UC-011 | Memoryの古さを確認・更新する | 日時情報を確認し、必要に応じて現在情報へ更新できる |
| P2-UC-012 | Memory障害中もConversationを続ける | Memoryなしで回答し、変更失敗を成功として案内しない |
| P2-UC-013 | Personal MemoryをBackup・Restoreする | 暗号化されたAlice対応ArchiveとしてExportし、検証と明示確認を経てRestoreできる |

### 4.4 Non-functional Requirements Specific to Phase 2

#### P2-NFR-001 Privacy and Sensitive Data

Personal Memoryを個人データとして扱い、センシティブなMemoryの保存・利用・表示を必要最小限にすること。Secretを保存しないこと。

#### P2-NFR-002 User Control

ユーザーが保存内容、利用状態、自動保存状態、訂正および削除を自分で確認・制御できること。

#### P2-NFR-003 Explainability

Personal Memoryが重要な回答へ影響した場合、ユーザーが求めれば利用したMemoryを説明できること。

#### P2-NFR-004 Reliability

Memory障害を可能な限りConversationから分離し、保存・更新・削除の失敗、結果不明または部分成功を、操作全体の成功として扱わないこと。

#### P2-NFR-005 Currentness

古い可能性があるMemoryとExternal Serviceから取得した現在情報を区別し、Memoryだけを根拠に変化し得る現在情報を断定しないこと。

#### P2-NFR-006 Testability

明示保存、自動保存、確認分岐、重複、統合、矛盾、削除、再登録防止、利用停止、障害時縮退、現在性判断、Backup ArchiveのExport、検証、Restore確認およびRestore失敗を再現可能にTestできること。

#### P2-NFR-007 Network and Access Boundary

Phase 2はSingle UserおよびLocal Developmentを維持すること。iOS SimulatorはLoopback接続を利用できる。実機iPhoneから接続する場合は、Private LAN限定の専用構成とし、許可端末のAuthentication、暗号化通信、Bind Address制限およびFirewall制限を必須とすること。AuthenticationなしでPersonal MemoryへLAN接続させないこと。

#### P2-NFR-008 Data Portability and Backup Security

Backup ArchiveはAI Provider、DynamoDB Itemおよび内部Persistence Keyへ依存しないVersion付き形式とし、センシティブなPersonal Memoryを含む可能性があるためDefaultで暗号化すること。SecretをArchiveへ含めず、Memory本文をLogへ出力しないこと。

### 4.5 Success Criteria

- 明示的な記憶要求を保存でき、成功・失敗をユーザーが確認できる
- 通常Conversationから長期利用価値のあるMemoryを抽出でき、一時的な情報を原則として保存しない
- センシティブな情報を安易に自動保存しない
- 同一Memoryを重複保存せず、補足・明確な変更・判断不能な矛盾を区別できる
- 関連するPersonal Memoryを必要な範囲で利用し、ユーザーに合わせた回答を返せる
- 重要な利用について、ユーザーが求めれば使用したMemoryを説明できる
- 自然言語とiOS管理画面の両方からMemoryを確認、登録、訂正、更新および削除できる
- 明示削除したMemoryをConversation Historyから勝手に再登録しない
- 自動保存と回答へのMemory利用を個別に停止・再開できる
- 古い可能性があるMemoryを現在の事実として断定しない
- Memory取得障害時も可能な限りConversationを継続できる
- 保存、更新または削除の失敗を成功として案内しない
- 有効なPersonal MemoryとMemory設定を暗号化Archiveとして手動Exportし、検証と明示確認を経てRestoreできる
- Conversation HistoryとPersonal Memoryが責務・Model・管理操作上で混同されていない

### 4.6 Out of Scope

- Conversation HistoryをPersonal Memoryとしてそのまま複製すること
- Phase 2での店舗営業時間、営業状況、価格その他の現在情報取得
- Calendar、Web、GitHub、AWSその他のExternal Tool実行
- Desktop用Memory管理UIの実装
- Voice専用Memory管理Interaction
- Multi User向けMemory共有
- Public InternetからのPersonal Memory Access
- AWS / Cloudへ公開したBackendからのPersonal Memory Access
- LAN外からのRemote Access
- Multi User Authentication
- 自動または定期Backup
- Cloud Storageへの自動Upload
- 複数端末間のMemory同期
- 第三者Service形式からの汎用Import
- DynamoDB TableまたはItemの直接Export / Import
- Memoryを利用したAutonomous Agent Action
- Source Code、Design Document、Repository、Issue、Pull RequestまたはBuild Logの全文をPersonal Memoryとして複製すること
- Personal Memoryを正式なProject Document、Accepted ADRまたはRepositoryのSource of Truthの代替とすること
- 特定のVector Store、Embedding ModelまたはRAG Frameworkの先行採用
- Architecture、API Contract、DynamoDB SchemaまたはClass構造の本Requirement内での確定

### 4.7 Phase Boundary

| Concern | Phase 2 Responsibility | Later Phase Responsibility |
|---|---|---|
| 店舗 | ユーザーが覚えたい店舗・Preferenceを保存 | Phase 3 Toolで営業時間・営業状況等を取得 |
| 人物 | ユーザーが記憶を希望した関係・情報を保存 | External Serviceから人物情報を取得する場合はPhase 3 |
| Engineering | ユーザーの技術経験、得意・苦手、Architectureまたは開発方針のPreferenceを保存 | Phase 3 Engineering ToolでCodeや外部資料を取得・レビュー |
| Project | ユーザーが記憶を希望した長期的な要点、方針、判断および背景を保存 | Phase 3 Engineering ToolでGitHub、Project資料等の現在情報を取得 |
| 大量Document | Personal Memoryとして全文を保存しない | 必要なPhaseでRAG、Semantic SearchまたはDocument検索基盤を検討 |
| Memory管理UI | iOS Alice App内で提供 | Desktop Layoutは対象Phaseで実装 |
| Voice | Text Conversationと管理画面でMemoryを管理 | Phase 4でVoice固有Interactionを設計・実装 |
| Agent | Agentが将来参照できるMemoryの基盤を提供 | Phase 4で利用範囲・Permissionを設計 |

### 4.8 Detailed Design Follow-ups

次はRequirementでは方向性を固定するが、具体方式をPhase 2 Detailed Designで決定する。

- Memory Category TaxonomyとCategory間の関係
- センシティブ情報の分類基準、確認文言および利用制約
- Memory候補抽出、保存判断、関連性判断および矛盾判定の責務
- Memory変更履歴の保持範囲とユーザーへの表示範囲
- 明示削除したMemoryの自動再登録防止方式と保持情報
- 回答へ利用するMemory数、優先順位およびContext Budget
- Memory利用説明と詳細な利用履歴の範囲
- Memory管理画面のNavigation、LayoutおよびAccessibility
- Natural Language Operationの確認・取消・再試行Flow
- 一括変更操作を全件成功または全件失敗とするか、部分成功を許可して個別管理するかの整合性方式
- Memory保存・検索TechnologyおよびDynamoDBとのBoundary
- Semantic Search / Vector Searchの必要性
- API Endpoint、Request / Response、Error ContractおよびPagination
- Security Boundary、端末・Network・Cloud公開時の保護
- Test Dataset、AI EvaluationおよびAcceptance Threshold
- Backup Archive FormatとVersioning
- Archive暗号化方式とKey / Passphrase管理
- Restoreを全置換とするかMerge可能とするか
- Restore部分失敗時の整合性
- Archiveの最大SizeとMemory件数
- 削除・再登録防止情報をBackupへ含める範囲
- iOS上のArchive保存先と共有方法

### 4.9 Requirements Review Record

| Review | Date | Result |
|---|---|---|
| Phase 2 Personal Memory Requirements Review | 2026-08-25 JST | Approved — 初回Reviewの全指摘を反映し、再Reviewで修正必須事項なし |

---

## 5. Phase 3 — Tools / External Services and Engineering Support

### 5.1 Purpose

AliceがConversation上の自然言語要求から必要なToolを判断し、ユーザーが許可したExternal Serviceから情報を取得または操作し、その結果をConversationへ安全に統合できる状態を作る。

Aliceの中心はConversationである。External InformationまたはSide Effectが不要な日常会話、雑談、相談および愚痴ではToolを使用せず、ユーザーにTool Mode切替やTool名指定を要求しない。

Phase 3はConnector / APIによるExternal Service操作を所有する。PC、OS、Browser UI、Desktop Application UIおよびSmartphone UIの直接操作はPhase 4が所有する。

### 5.2 Functional Requirements

| ID | Requirement |
|---|---|
| P3-FR-001 | Calendarから予定を取得・検索できること |
| P3-FR-002 | PermissionとApprovalに従ってCalendar Eventを登録・変更・削除できること |
| P3-FR-003 | 現在位置、時刻、営業状況、好み、履歴および予算を必要に応じて考慮し、店舗情報を提案できること |
| P3-FR-004 | 必要な現在情報をWeb / Search Connectorから取得できること |
| P3-FR-005 | GitHub上の開発情報を取得し、許可された操作を実行できること |
| P3-FR-006 | AWS等の許可されたExternal Serviceから情報を取得できること |
| P3-FR-007 | Code、Architecture、RequirementおよびTechnology Selectionをレビューできること |
| P3-FR-008 | ToolのRead / Write、Impact、Reversibility、AmbiguityおよびThird-party Effectを評価し、必要なApprovalを要求できること |
| P3-FR-009 | Current User RequestとConversation Contextから必要なToolを自動選択できること |
| P3-FR-010 | External InformationまたはSide Effectが不要な場合、Toolを呼び出さず通常Conversationを継続すること |
| P3-FR-011 | Current External Stateが必要な場合、推測や古いMemoryより責任を持つConnectorからの取得を優先すること |
| P3-FR-012 | ユーザーがServiceまたはToolを明示指定した場合、そのIntentをSelectionへ反映し、利用不能なら明示すること |
| P3-FR-013 | 一つのGoalに必要な複数Toolを順序付けて組み合わせられること |
| P3-FR-014 | 同じGoalを複数手段で達成できる場合、User Preference、Dedicated Connector、Generic Web / Search、Phase 4 Agent Candidateの順を基本にしつつRiskと実現性を評価すること |
| P3-FR-015 | ConnectorからPhase 4 AgentへCapability、PermissionまたはRiskが増えるFallbackを黙って実行せず、新しい判断と必要なApprovalを要求すること |
| P3-FR-016 | Tool Definition、Input Contract、Risk Metadata、EnablementおよびConnector Bindingを登録・解決できるRegistryを持つこと |
| P3-FR-017 | Alice CoreおよびAI ProviderからService固有SDK型を分離し、Tool Boundaryの背後へConnectorを配置すること |
| P3-FR-018 | 新しいConnector追加時にConversation、MemoryおよびAlice Core全体の大幅変更を必要としないこと |
| P3-FR-019 | 対応済みだが未接続のConnectorをUser Goalから発見し、必要性とPermission Scopeを説明してEnableを提案できること |
| P3-FR-020 | Connector EnableはRequired Permission提示、User Scope選択、Authorization / Authentication、Enable完了確認の順で行うこと |
| P3-FR-021 | 未対応Connectorについて、AliceがConnector Codeを自律生成、Security Review、Deploy、InstallまたはEnableしたと報告しないこと |
| P3-FR-022 | ユーザーがConnectorをDisableまたはDisconnectでき、以後の新規実行を停止できること |
| P3-FR-023 | 最初のCalendar ConnectorとしてApple Calendarを優先すること |
| P3-FR-024 | Apple固有のModelとAuthentication方式を共通Calendar Tool Boundaryへ漏らさないこと |
| P3-FR-025 | 指定期間、Keyword、Event属性およびCalendar Scopeにより予定を取得・検索できること |
| P3-FR-026 | 指定期間内のBusy Intervalを考慮し、要求された長さのFree Time候補を提示できること |
| P3-FR-027 | Personal、Workその他の複数Calendarを識別し、Read / Write ScopeをCalendar単位で制御できること |
| P3-FR-028 | Event Title、開始・終了、Time Zone、Calendarおよび必要な補助情報を用いてEventを登録できること |
| P3-FR-029 | 対象Eventを一意に解決し、Current StateとExpected Versionを確認してEventを変更できること |
| P3-FR-030 | 対象Eventを一意に解決し、削除影響を提示してEventを削除できること |
| P3-FR-031 | 対象Event、Calendar、日時またはTime Zoneが曖昧な場合、推測実行せず必要な情報を確認すること |
| P3-FR-032 | Default Calendarを将来設定可能な構造とし、未設定または複数候補時の安全な選択規則を持つこと |
| P3-FR-033 | Web / Search Resultの取得時刻、Sourceおよび対象Queryを追跡できること |
| P3-FR-034 | 店舗の営業時間、営業状況、所在地その他のCurrent Stateを必要なConnectorから取得し、取得不能または古い場合は断定しないこと |
| P3-FR-035 | GitHub Repository、Issue、Pull Request、Commitその他の許可された開発情報を取得できること |
| P3-FR-036 | GitHubへのComment、Issue更新その他のWriteを、対象RepositoryとResource Scopeに従って実行できること |
| P3-FR-037 | AWS等のConnectorはAccount、Environment、Region、ServiceおよびOperation Scopeを必要に応じて区別できること |
| P3-FR-038 | Code、Architecture、RequirementおよびTechnology Reviewで、Repositoryや正式DocumentをSource of Truthとして参照できること |
| P3-FR-039 | Permissionを最低でもConnector × Operation × Scopeで表現できること |
| P3-FR-040 | Read、Create、Update、DeleteおよびExternal CommitのPermissionを独立して許可・拒否・確認要求へ設定できること |
| P3-FR-041 | Alice、LLM、ConnectorまたはTool ResultがPermission Scopeを自己拡張できないこと |
| P3-FR-042 | 新しいPermissionが必要な場合、理由、Access対象、Operationおよび必要Scopeを提示し、User Authorizationを得ること |
| P3-FR-043 | ユーザーがPermissionを確認、変更、Revoke、DisableおよびConnector単位でDisconnectできること |
| P3-FR-044 | OAuth Token、Refresh Token、API Key、Password、Session CredentialおよびRecovery MaterialをConversation History / Personal Memoryから分離したCredential Boundaryで管理すること |
| P3-FR-045 | CredentialをPrompt、Tool Arguments、一般Application Log、Audit本文またはClientの通常Storageへ露出しないこと |
| P3-FR-046 | Authentication Expired時は実行済みと扱わず、再Authenticationの必要性と対象Connectorを説明すること |
| P3-FR-047 | Permissionと今回の具体的Operationに対するApproval / Execution Intentを別Conceptとして扱うこと |
| P3-FR-048 | Approval要否をImpact、Reversibility、Ambiguity、Third-party EffectおよびUser IntentからRisk Basedで決定すること |
| P3-FR-049 | 対象、Action、主要な影響が一意な低Risk Explicit Commandは、そのCommand自体を当該OperationへBindingされたApprovalとして扱えること |
| P3-FR-050 | P3-FR-049をStanding Permission、別Target、変更後Arguments、Aliceからの自発提案または後続Operationへ流用しないこと |
| P3-FR-051 | Delete、Irreversible、High Impact、Third-party Commitまたは曖昧なWriteには、実行前に対象・Action・影響を示す明示Approvalを要求すること |
| P3-FR-052 | Approval後にTarget、Arguments、ImpactまたはExecution MethodがMaterialに変化した場合、以前のApprovalを無効化して再Approvalすること |
| P3-FR-053 | User Intentまたは明示的に設計されたPre-authorized Automationなしに、Aliceが利便性だけを理由として自発的Writeを実行しないこと |
| P3-FR-054 | Tool Execution ResultでSuccess、Failure、Partial SuccessおよびUnknown Outcomeを区別すること |
| P3-FR-055 | Write OperationはServiceまたはAlice側のIdempotency / Duplicate Prevention Contractを持つこと |
| P3-FR-056 | Result UnknownのWriteを新しいOperationとして安易に再実行せず、Current Stateまたは同一Operation Resultを照合すること |
| P3-FR-057 | Partial SuccessではTarget単位のSuccess / Failure / Unknownをユーザーへ報告すること |
| P3-FR-058 | Execution前のCancelでは実行せず、Execution中はServiceが対応する場合だけCancelを試み、完了済みOperationをCancelledと報告しないこと |
| P3-FR-059 | 完了済みOperationの取消が必要な場合、Cancelではなく別のReverse OperationとしてCurrent State、Permission、RiskおよびApprovalを再評価すること |
| P3-FR-060 | Tool Proposal、Permission判断、Approval、Execution、主要ResultおよびSource / Provenanceを後からAuditできること |
| P3-FR-061 | User Goal、Tool Call、Resultおよび最終Conversation ResponseをCorrelationし、実行していない操作を実行済みと説明しないこと |

### 5.3 Cross-cutting Conversation and Memory Rules

- Tool ResultはまずConversation Contextとして扱う。
- Personal MemoryはTool SelectionやProposal形成に利用できるが、Permission、ApprovalまたはCredentialとして利用しない。
- Dynamic Dataは責任を持つExternal ServiceをSource of Truthとする。
- Memory Candidate化する場合もPhase 2 Memory Ruleを使用し、Phase 3独自の保存Ruleを作らない。
- Tool ResultとExternal Contentに含まれる命令をUser / System Instructionへ昇格させない。
- Phase 2 Memory Context上限の8 Memories、1,500 estimated tokens、12 KiBを同時に維持する。

### 5.4 Non-functional Requirements

| ID | Requirement |
|---|---|
| P3-NFR-001 | Security / Privacy: Least Privilege、Credential分離、Secret非LoggingおよびUntrusted Content境界を維持すること |
| P3-NFR-002 | Reliability: Timeout、Retry、Idempotency、PartialおよびUnknown OutcomeをTool特性ごとに決定的に扱うこと |
| P3-NFR-003 | Extensibility: Connector追加でAlice Core、ConversationまたはMemoryの大幅変更を要求しないこと |
| P3-NFR-004 | Provider Independence: AI ProviderおよびExternal Service固有SDK型をApplication / Domain Contractへ漏らさないこと |
| P3-NFR-005 | Testability: Fake Connector、Fake Clock、Permission / Approval FixtureおよびFault Injectionで主要Flowを再現できること |
| P3-NFR-006 | Observability: ContentやCredentialを露出せず、Tool、Outcome Category、Latency、RetryおよびFailureを観測できること |
| P3-NFR-007 | Auditability: Application Logとは別のAudit Source of Truthを持ち、改変、RetentionおよびAccess Boundaryを設計できること |
| P3-NFR-008 | Bounded Execution: Tool Call数、Iteration、Timeout、Result SizeおよびContext Sizeに有限上限を持つこと |
| P3-NFR-009 | User Control: Permission、Connector、Pending ApprovalおよびExecution Resultを理解可能なUI / Conversationから確認・変更できること |
| P3-NFR-010 | Graceful Degradation: 一つのConnector障害で無関係なConversation、MemoryまたはConnectorを不必要に停止しないこと |

### 5.5 Success Criteria

1. 自然言語から必要なToolを選択し、不要な会話ではToolを使わない。
2. Apple CalendarのRead、Free Time、Create、UpdateおよびDeleteを共通Calendar Boundary経由で扱える。
3. PermissionとApproval / Execution Intentを分離し、AliceがScopeを自己拡張しない。
4. 低Risk Explicit Commandと高Risk / Delete / Third-party OperationのApproval差を一貫して適用できる。
5. CredentialをConversation、Memory、Prompt、通常Logおよび不適切なClient Storageへ露出しない。
6. Success、Failure、PartialおよびUnknownを区別し、Writeの重複実行を防げる。
7. Tool ResultのSource / Freshnessを追跡し、Dynamic DataをMemoryで置換しない。
8. Connector追加でAlice Coreを大幅変更せず、Phase 4へのSilent High-risk Fallbackを行わない。
9. Phase 3 Cross ReviewでOpen Critical / High Findingが0件になる。

### 5.6 Out of Scope

- PC / OS、Browser UI、Desktop / Smartphone Application UIの直接操作
- Voice Agent基盤
- 未対応Connector Codeの自律生成、Security Review、Deploy、Install
- Alice自身のPermission / Safety Policy改変またはPrivilege Escalation
- User Intent / Pre-authorized Automationなしの自発的Write
- Connector失敗からPhase 4 AgentへのSilent Fallback
- Payment、Transfer、Purchase、Contractその他のCritical External Commitの初期実装
- CAPTCHA / MFAその他の本人確認回避
- Phase 4 Agent Runtime、Plan、Observation、Replan、PC / Browser Executor
- 将来利用だけを理由にした空Common Module、空Featureまたは空Connector Packageの先行実装

### 5.7 Requirements Review Record

| Review | Date | Result |
|---|---|---|
| Phase 3 Requirements Formalization Review | 2026-09-02 JST | Approved — P3-FR-001〜061、P3-NFR-001〜010を一括採用 |

---

## 6. Phase 4 — Agent, PC / Browser Operation and Voice

### 6.1 Purpose

Aliceがユーザーの目的を安全なTaskへ分解し、必要なTool、PC、BrowserおよびVoice Capabilityを組み合わせて支援できる状態を作る。

### 6.2 Functional Requirements

| ID | Requirement |
|---|---|
| P4-FR-001 | User Goalを複数のTask / Stepへ分解できる |
| P4-FR-002 | Taskに必要なToolを選択できる |
| P4-FR-003 | Permission、Approval、DeadlineおよびStep上限に従ってToolを実行できる |
| P4-FR-004 | Tool実行結果を評価し、次の行動または停止を判断できる |
| P4-FR-005 | 重要・危険・取り消し困難な操作前にUser Confirmationを要求できる |
| P4-FR-006 | 許可されたApplication、FileおよびOS OperationをPC Agent経由で実行できる |
| P4-FR-007 | 許可されたBrowser Operationを実行できる |
| P4-FR-008 | Voice InputをAliceへの指示として処理できる |
| P4-FR-009 | Aliceの回答をVoice Outputできる |
| P4-FR-010 | VoiceだけをAuthenticationまたは危険操作Approvalの根拠にしない |
| P4-FR-011 | Agent / Tool / PC / Browser Operationを後からAuditできる |

### 6.3 Success Criteria

単純な一命令一操作だけではなく、ユーザーの目的から必要なStepをAliceが提案し、User Controlを維持したまま安全に実行できること。

---

## 7. Non-functional Requirements

### NFR-001 AI Provider Independence

Alice Coreは特定のLLM SDKへ直接依存せず、Capability-specific Portを通してAI Modelを利用すること。Provider変更時にAlice固有の判断やMemoryを失わないこと。

### NFR-002 Memory Portability

Conversation HistoryおよびPersonal MemoryをAI Providerから分離して管理し、Provider変更後も継続利用できること。

### NFR-003 Security and Privacy

- Aliceが扱うUser Dataを個人データとして扱う
- CredentialまたはAPI KeyをConversation History / Personal Memoryへ保存しない
- Secret、Authentication TokenおよびCredentialをSource Code、Repository、API ResponseまたはLogへ出力しない
- External Providerへ送信するDataを必要最小限にする
- Phase 1 BackendをPublic Internetへ公開しない

### NFR-004 Maintainability

個人開発として継続的に保守できる複雑さに抑えること。将来必要になる可能性だけを理由として不要なFeature、Port、Package、FrameworkまたはInfrastructureを先行実装しないこと。

### NFR-005 Extensibility

新しいAI Capability、ToolまたはProviderの追加時にAlice Core全体の大幅変更を必要としない責務・依存方向を維持すること。

### NFR-006 Reliability

Timeout、Retry、Partial Failure、Client DisconnectおよびDuplicate RequestでConversation Historyを不整合にしないこと。External SDKの暗黙RetryとAlice Retryを重複させないこと。

### NFR-007 Testability

Domain、Application、Port、Adapter、HTTP / SSEおよびPersistence Contractを自動Test可能にすること。Clock、ID、Delay、ProviderおよびRepositoryを制御可能にすること。

### NFR-008 Observability

処理内容、利用Capability、成否およびError Categoryを必要に応じて追跡できること。ただしConversation Content、Prompt、SSE Delta、SecretまたはProvider Error Bodyを無条件に記録しないこと。

### NFR-009 Reproducibility

JDK、Spring Boot、Maven、OpenAI SDK、Flutter SDK、Dartおよび主要Test ToolのVersionを設計書またはLock Fileで再現可能にすること。

## 8. Traceability Rule

- Detailed Designは対応するRequirement IDをTraceability Tableへ記載する
- Test CaseまたはTest Metadataは検証対象Requirement IDを記載する
- 一つのRequirementを複数Layerで検証してよい
- Requirementに存在しないScopeをDetailed DesignまたはImplementationが独自に追加しない
- Requirement変更が必要な場合はImplementationより先に本Documentと関連Designを更新する

## 9. Summary

Phase 1は、単一Conversation、Text Chat、Alice Personality、Streaming、Conversation HistoryおよびSafe Retryを実装する。

Phase 2は、Conversation Historyから独立したPersonal Memoryについて、保存判断、更新・矛盾、削除、利用、透明性、管理画面、設定、現在性、障害時Behavior、および暗号化Archiveによる手動Backup / Restoreを定義する。

Phase 3は、Conversationから必要なTool / Connectorを選択し、Permission、Approval、Credential、Result、ProvenanceおよびAudit Boundaryの中でExternal Serviceを利用するApproved Requirementを定義する。最初のCalendar ConnectorはApple Calendarを優先する。

Phase 4のArchitectureはPhase 0で設計するが、実装は各Phaseに必要な最小範囲だけを追加する。
