# Project Alice — Personal Memory Design

## 1. Document Information

| Item | Value |
|---|---|
| Document | `memory-design.md` |
| Version | 50 |
| Status | Reviewed — Implementation Ready |
| Target Phase | Phase 2 — Personal Memory |
| Implementation | Not Started |
| Last Updated | 2026-09-02 JST |
| Review Required | No |

本ドキュメントは、Phase 2 Personal MemoryのArchitecture、責務、Boundary、主要Flow、Domain ModelおよびPortを定義するSource of Truthである。

Phase 2 Detailed Designは2026-09-02 JSTの最終横断レビューでImplementation Readyと判定された。Source Code実装は未着手であり、Accepted Decision、API ContractおよびTest GateをAIが独自変更してはならない。

### 1.1 Related Sources of Truth

| Concern | Source of Truth |
|---|---|
| Phase 2 Requirement | `requirements.md` Phase 2 — Approved |
| System Architecture | `alice-architecture.md` |
| Backend共通構造 | `backend-design.md` |
| Repository構造 | `repository-structure.md` |
| AI Capability | `ai-design.md` |
| API Contract | `api-design.md` |
| Persistence | `database-design.md` |
| Security | `security-design.md` |
| Frontend | `frontend-design.md` / `phase2-frontend-ui-design.md` |
| Testing | `test-design.md` |
| Architecture Decision | `decisions.md` |

本DocumentとAccepted ADRまたは上位Documentに矛盾が生じた場合、AIは独自解釈で解消しない。Implementationより先に関連Documentを整合させる。

---

## 2. Purpose

Conversationをまたいで再利用する価値があるユーザー固有情報を、Conversation Historyから独立したPersonal Memoryとして安全に管理する。

Personal Memoryによって、Aliceは次を実現する。

- ユーザーに関する長期的に有用な情報を保存する
- 現在の質問に関連するMemoryだけを取得する
- 必要最小限のMemoryを回答Contextへ利用する
- Memoryをユーザー自身が確認、登録、訂正、更新および削除する
- センシティブ情報と削除意思を尊重する
- 保存技術またはAI Providerを変更してもMemoryを継続利用する

---

## 3. Scope

### 3.1 In Scope

- 独立した`memory` Backend Feature
- Personal Memoryの登録、取得、検索、更新および削除
- 明示保存と通常Conversationからの自動保存候補処理
- 重複、補足、変更および矛盾の判定
- Memory利用設定と自動保存設定
- 削除後の自動再登録防止
- iOS Application内のMemory管理Capability
- Conversation回答ContextへのMemory統合
- 暗号化Archiveによる手動Backup / Restore
- Failure、Security、ObservabilityおよびTestabilityのBoundary

### 3.2 Out of Scope

- Conversation Historyの所有権移動
- Conversation History全文のPersonal Memory化
- Phase 3のTool / External Service実行
- 店舗営業時間等の現在情報取得
- Phase 4のAgent、PC、BrowserおよびVoice固有処理
- Desktop用Memory管理UIの先行実装
- 自動Backup、Cloud同期および複数端末同期
- 特定Vector Store、Embedding ModelまたはRAG Frameworkの先行採用

---

## 4. Architecture Principles

### MD-AP-001 Conversation History and Personal Memory Separation

Conversation HistoryとPersonal Memoryを別Feature、別Modelおよび別Persistence Boundaryとして扱う。

```text
Conversation History ≠ Personal Memory
```

`conversation`はConversationとMessageを所有し、`memory`は長期的に再利用するPersonal Memoryだけを所有する。

### MD-AP-002 Memory and Persistence Separation

Personal MemoryをDynamoDB、Vector Storeまたはその他の保存技術と同一概念として扱わない。

Alice CoreはMemory側が所有するPortを使用し、AWS SDK、DynamoDB Item、Embedding SDKまたは検索製品固有Modelへ直接依存しない。

### MD-AP-003 Memory Feature Ownership

Phase 2では独立した`memory` Featureを追加する。Engineering MemoryおよびProject Memoryは独立Featureにせず、Personal MemoryのCategoryとして`memory`が所有する。

### MD-AP-004 Explicit Application Boundary

他Featureは`memory.application`が公開するApplication Boundaryを通してMemory Capabilityを利用する。

他Featureが次へ直接依存することを禁止する。

- `memory.domain`の内部実装
- `memory.infrastructure`
- Memory用DynamoDB Item
- Memory検索EngineまたはSDK
- Memory Repository実装Class

### MD-AP-005 No Cyclic Feature Dependency

`conversation`と`memory`の相互直接依存を作らない。

Phase 2の基本方向は次とする。

```text
conversation.application
        │
        │ uses approved Memory Application Boundary
        ▼
memory.application
        │
        ▼
memory.domain
```

`memory`はConversation Repositoryを直接参照しない。Memory処理に必要なConversation情報は、呼出側からApplication用Modelとして必要最小限だけ渡す。

### MD-AP-006 AI Capability Separation

Memory候補抽出、意味的な関連性判断または矛盾判定でAIが必要な場合も、`memory`からOpenAI SDKを直接利用しない。

```text
memory.application
        │
        ▼
ai.application Capability Port
        ▲
        │
ai.infrastructure Provider Adapter
```

AIが提案した保存候補または判定結果を、検証なしにPersistenceへ保存しない。Alice固有の保存PolicyとDomain Ruleを`memory`側で適用する。

### MD-AP-007 Phase-based Implementation

設計はPhase 3〜4の拡張を阻害しない形にするが、Phase 2で不要なTool、Agent、Voice、共通ModuleまたはInfrastructureを先行実装しない。

---

## 5. Backend Feature Structure

Phase 2開始時のTarget Structureは次とする。

```text
com.projectalice.backend
├── conversation/
├── ai/
├── memory/
│   ├── presentation/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
└── shared/
```

空Packageを先行作成しない。各Layerには、その時点で承認済みのUse Caseに必要なClassだけを配置する。

### 5.1 Presentation

担当する。

- Memory HTTP Request / Response
- Request DTOとValidation
- Memory管理画面向けAPI
- HTTP StatusとError Mapping
- Backup ArchiveのUpload / Download境界

担当しない。

- 保存価値またはセンシティブ性の判断
- 重複・矛盾・更新判断
- DynamoDB直接アクセス
- AI Provider直接呼出し

### 5.2 Application

担当する。

- Memory Use Caseの実行順序
- 明示保存、自動保存候補、検索、更新、削除および設定変更の調整
- Domain Ruleの利用
- 他Featureへ公開するMemory Application Boundary
- Outbound Portの利用
- Failureと部分成功のApplication Level処理

### 5.3 Domain

担当する。

- Personal Memory Entity / Value Object
- Category、Source、Stateおよび日時の意味
- 保存禁止、削除、更新および再登録防止に関するDomain Rule
- Infrastructureから独立したDomain Validation

DomainはPlain Javaを基本とし、Spring Web、AWS SDK、DynamoDB SDK、OpenAI SDKおよびFlutter Modelへ依存しない。

### 5.4 Infrastructure

担当する。

- Memory Repository Portの実装
- DynamoDBとの通信
- 必要になった場合の検索Infrastructure
- Archive暗号化・復号の技術実装
- Infrastructure ModelとCore ModelのMapping

Infrastructure固有Exception、SDK Object、Persistence Keyおよび内部ScoreをApplication、DomainまたはAPIへ漏らさない。

---

## 6. Ownership Boundary

| Information / Decision | Owner |
|---|---|
| Conversation / Message / Message Order | `conversation` |
| Conversation History取得 | `conversation` |
| Personal Memory | `memory` |
| Memory Category / State / Source | `memory` |
| Memory保存・更新・削除Policy | `memory` |
| 自動保存設定 / Memory利用設定 | `memory` |
| 削除後の自動再登録防止情報 | `memory` |
| Memory Backup / Restore | `memory` |
| Provider-independent AI Capability | `ai.application` |
| OpenAI固有通信 | `ai.infrastructure` |
| DynamoDB固有Model / Mapping | 対応Featureの`infrastructure` |
| Conversation回答全体のOrchestration | `conversation.application` |

`conversation`は「いつMemory Capabilityを利用するか」をConversation Flowの一部として調整できるが、Memoryの保存価値、センシティブ性、重複、統合、矛盾および削除Policyを独自に実装しない。

`memory`はMemory判断を所有するが、ConversationまたはMessageのCanonical Dataを所有・変更しない。

---

## 7. Logical Component Flow

### 7.1 Answer-time Memory Use

```text
Flutter
   │
   ▼
conversation.presentation
   │
   ▼
conversation.application
   │
   ├── Conversation History取得
   │
   ├── memory.applicationへ関連Memory取得を要求
   │       │
   │       └── Memory利用設定・関連性・Security Policyを適用
   │
   ├── Conversation ContextとMemory Contextを区別して構築
   │
   └── ai.application Capability Portを利用
```

Memory利用がOFFの場合、通常回答Flowから`memory`へ関連Memory検索を要求せず、AI RequestへPersonal Memoryを追加しない。

### 7.2 Memory Capture

```text
Conversation処理
   │
   ├── 明示的な記憶要求
   │       └── memory.applicationへ明示保存Command
   │
   └── 通常Conversation
           └── 自動保存がONの場合だけMemory候補処理を要求
                    │
                    ├── 候補抽出
                    ├── 保存禁止・センシティブ性確認
                    ├── 重複・統合・矛盾判断
                    └── 保存または確認要求
```

Conversation処理とMemory保存の正確な実行順序、同期・非同期方式、失敗時整合性およびRetryは後続節で決定する。この概念図だけから実装順序を推測しない。

### 7.3 Management

```text
iOS Memory Management UI
   │
   ▼
memory.presentation
   │
   ▼
memory.application Use Case
   │
   ▼
memory.domain / Outbound Port
```

管理画面と自然言語操作は同じDomain Ruleを利用し、片方だけが削除、Securityまたは再登録防止Ruleを迂回できないようにする。

---

## 8. Application Boundaries

Memory Portは単一巨大Interfaceを意味しない。利用目的ごとのApplication Boundaryと、外部技術へ接続するOutbound Portの論理的な総称である。

### 8.1 Consumer-facing Capability Groups

| Capability Group | Purpose |
|---|---|
| Memory Query | 関連Memory、一覧、詳細およびCategoryを取得する |
| Memory Command | 明示保存、登録、訂正、更新および削除を行う |
| Memory Capture | Conversation情報から保存候補を処理する |
| Memory Settings | 自動保存と回答利用の設定を管理する |
| Memory Backup | ArchiveのExport、検証およびRestoreを行う |

これは責務Boundaryであり、現時点でInterface数、Class名またはMethod Signatureを確定しない。Use Case設計時に、過剰な分割を避けながら必要なContractだけを定義する。

### 8.2 Outbound Capability Groups

| Capability Group | Purpose |
|---|---|
| Persistence | Memory、設定および再登録防止情報を永続化する |
| Search | 現在の質問に関連するMemoryを検索する |
| AI Analysis | 必要な場合に候補抽出、関連性または矛盾判断を支援する |
| Archive Protection | Backup Archiveを暗号化、復号および完全性検証する |
| Clock / ID | Test可能な日時と識別子を提供する |

Persistence Repositoryと検索用Portは、Application上の利用目的が異なるため論理Contractを分離する。Infrastructure実装が同じDynamoDB Adapterまたは同じ内部Componentを共有することは妨げない。具体的なPort構成はSection 14で定義する。

---

## 9. Domain Model and Lifecycle

本Sectionは、Phase 2で扱うPersonal Memoryの最小Domain Modelを定義する。DynamoDB Item、API DTOおよびAI Provider ModelをDomain Modelとして使用しない。

### 9.1 Aggregate Overview

```text
PersonalMemory
├── MemoryId
├── MemoryContent
├── MemoryCategory
├── CaptureType
├── SensitivityLevel
├── MemoryState
├── Version
├── CreatedAt
├── UpdatedAt
└── ConfirmedAt（未確認の場合は空）
```

`PersonalMemory`を、保存中の一つの長期Memoryを表すAggregate Rootとする。

一つの`PersonalMemory`には、可能な限り一つの意味的な事実、Preference、関係または状態を保持する。Conversation全文、複数の無関係な事実または大量Documentを一つのMemoryへ詰め込まない。

### 9.2 PersonalMemory Fields

| Field | Required | Meaning |
|---|---:|---|
| `memoryId` | Yes | Backendが生成するMemory固有ID |
| `content` | Yes | ユーザーが理解できる正規化済みのMemory本文 |
| `category` | Yes | Profile、Preference、Person、Place、Engineering、Project等の分類 |
| `captureType` | Yes | 明示登録または自動保存のどちらで生成されたか |
| `sensitivityLevel` | Yes | 通常情報またはセンシティブ情報の分類 |
| `state` | Yes | 現在有効な情報か、解決済み等の過去状態か |
| `version` | Yes | 同じMemoryへの古い判定結果による上書きを防ぐ単調増加Version |
| `createdAt` | Yes | 最初に保存した日時 |
| `updatedAt` | Yes | 内容、Category、Stateその他の管理対象情報を最後に変更した日時 |
| `confirmedAt` | No | ユーザーが内容を最後に明示登録、編集または肯定した日時 |

### 9.3 Value Objects

#### MemoryId

- BackendがUUID v4を生成する
- 小文字・ハイフン付きCanonical形式を使用する
- APIではOpaque Stringとして扱い、ClientがUUID形式へ依存しない
- 更新しても同じMemoryのIdentityを維持する
- 削除後に明示的に再登録する場合は新しいIDを発行する

#### MemoryContent

- 空、空白だけまたは意味のない内容を許可しない
- ユーザーが管理画面で読んで理解できる表現にする
- Conversation Transcriptそのものを保存しない
- Credential、API Key、Authentication Tokenその他のSecretを許可しない
- 最大文字数とUTF-8 Byte上限はAPI / Database詳細設計で確定する

AIがContent候補を生成した場合も、AliceのDomain Validationを通過しなければ`PersonalMemory`を生成しない。

#### MemoryCategory

Categoryは「そのMemoryが主に何についての情報か」を表し、検索、管理画面表示および保存Policyへ利用する。

Phase 2では、階層を持たない次の8種類をTop-level Categoryとして使用する。

| Code | UI Label案 | 対象 | 含めないもの |
|---|---|---|---|
| `PROFILE` | プロフィール | ユーザー本人の比較的安定した属性、経歴、職業、言語、一般的な背景 | 好き嫌い、技術能力、Project固有情報 |
| `PREFERENCE` | 好み・趣味 | 趣味、好き嫌い、食事、飲み物、娯楽、Aliceとの会話Style等のPreference | 技術・開発方針のPreference、店舗自体の情報 |
| `PERSON` | 人物 | ユーザーが記憶を希望した友人、家族、著名人その他の人物との関係・情報 | ユーザー本人のProfile、人物に無関係な一般知識 |
| `LIFE_CONTEXT` | 生活・目標・相談 | 生活状況、習慣、長期目標、過去または現在の悩み・相談、その解決状態 | 店舗・場所、技術能力、Project固有Decision |
| `PLACE` | 場所・店舗 | 覚えておきたい店舗、施設、地域、場所およびユーザー固有のメモ | 営業時間、価格、営業状況等の変化する現在情報 |
| `ENGINEERING` | 技術・学習 | 技術経験、得意・苦手、学習履歴、Architectureまたは開発方針のPreference | Source Code・Design Document全文、特定Projectの正式Decision |
| `PROJECT` | プロジェクト | 継続的に扱うProjectの要点、方針、判断および背景 | Repository全文、常に変化する現在状態、正式資料の代替 |
| `OTHER` | その他 | Approved Memory保存基準を満たすユーザー固有の長期情報のうち、既存7 Categoryのどれとも主題が一致しない情報 | 内容が曖昧、分類判断が未完了、一時的、保存禁止、または複数事実が未分割の情報 |

##### Category Assignment Rules

1. 一つの`PersonalMemory`には一つのPrimary Categoryを必須とする。
2. Categoryは情報源ではなく、Memoryの主な対象と将来の利用目的で決める。
3. 複数の独立した事実が異なるCategoryに属する場合、原則として複数の`MemoryCandidate`へ分割する。
4. 一つの意味として分割できない場合、中心となる対象のCategoryを選択する。
5. Categoryを確定できない場合、自動的に`OTHER`へ保存せず、候補の修正、保存見送りまたは必要なUser Confirmationを行う。
6. 保存後にCategoryの誤りが分かった場合、同じ`memoryId`を維持してCategoryを訂正し、`updatedAt`を更新する。

Examples:

| Memory | Category | Additional Axis |
|---|---|---|
| 「Javaはまだ苦手」 | `ENGINEERING` | Sensitivityとは独立 |
| 「AliceではDynamoDBを採用する方針」 | `PROJECT` | 正式Documentが存在する場合はそちらがSource of Truth |
| 「友人Aはビールが好き」 | `PERSON` | 第三者の非公開情報なら`SENSITIVE`になり得る |
| 「特定食材にアレルギーがある」 | `LIFE_CONTEXT` | 健康情報なので`SENSITIVE` |
| 「この居酒屋を気に入った」 | `PLACE` | 営業時間はPhase 3 Toolで取得 |
| 「回答は形式ばりすぎない方がよい」 | `PREFERENCE` | Aliceの会話Styleへ利用可能 |
| 「予備の乾電池は廊下の収納箱にある」 | `OTHER` | 所有物の保管情報であり、店舗・施設としての`PLACE`ではない |
| 「リビングのテレビの型番は○○」 | `OTHER` | 個人所有物に関する長期情報で、既存Categoryに専用の主題がない |

`OTHER`へ保存する場合も、長期的有用性、ユーザーとの関連性、センシティブ性および保存禁止Ruleを通常どおり評価する。

次は`OTHER`へ分類しない。

| Case | Required Handling |
|---|---|
| 内容が曖昧で分類できない | User Confirmationまたは保存見送り |
| 一時的な予定・現在情報 | 保存しない、または対象PhaseのToolで取得 |
| Secretまたは保存禁止情報 | 保存を拒否 |
| 複数Categoryの独立した事実 | `MemoryCandidate`を分割 |
| 既存Categoryに該当する情報 | 適切なCategoryへ分類 |

同じ主題の`OTHER`が繰り返し保存される場合、Taxonomy不足の兆候として扱う。実際の検索・管理需要を確認し、新Category追加のDesign Changeを検討する。

##### Orthogonal Classification

Category、Sensitivity、Capture TypeおよびStateは別の分類軸とする。

```text
Category         = 何についてのMemoryか
SensitivityLevel = どの程度慎重に扱うか
CaptureType      = どの経路で生成されたか
MemoryState      = 現在どの状態か
```

例えば健康情報は`LIFE_CONTEXT + SENSITIVE + EXPLICIT + ACTIVE`のように表現できる。`LIFE_CONTEXT`だから自動的にSensitive、または`PERSON`だから自動的に保存禁止とは判断しない。実際の内容に対してSecurity Policyを適用する。

##### Taxonomy Complexity

Phase 2では次を導入しない。

- Categoryの親子階層
- 一つのMemoryへの複数Primary Category
- User定義Category
- 必須のFree-form Tag
- Categoryごとの独立FeatureまたはRepository

これは分類を単純に保ち、AIによる分類揺れ、管理画面の複雑化および検索条件の過剰化を防ぐためである。将来、実際のMemory件数と検索要件から必要性が確認された場合に追加設計する。

##### Stable Code and Evolution

- Domain / API / Archiveでは英大文字のStable Codeを使用する。
- 日本語等のUI Labelは表示責務としてCodeから分離する。
- 一度保存に使用したCodeの意味を別用途へ変更または再利用しない。
- 新しいCategoryを追加する場合、既存Memory、API Client、検索、Backup ArchiveおよびMigrationへの影響を確認する。
- 未対応のCategory Codeを受信またはRestoreした場合、暗黙に`OTHER`へ変換しない。対応Versionの確認、明示的なMigrationまたは安全な拒否を行う。

`OTHER`は有効な拡張余地として残すが、分類判断を省略するためのDefault値として使用しない。

#### CaptureType

Phase 2では次の論理値を使用する。

| Value | Meaning |
|---|---|
| `EXPLICIT` | Conversationでの明示要求または管理画面からユーザーが登録した |
| `AUTOMATIC` | 通常ConversationからAliceが保存判断した |

自動保存されたMemoryをユーザーが後から編集または確認しても、生成経路を示す`captureType`は`AUTOMATIC`のままとする。ユーザーの確認実績は`confirmedAt`で表現する。

BackupからRestoreした場合も、Archiveに記録された元の`captureType`を維持する。Restore操作そのものを生成経路として上書きしない。

#### SensitivityLevel

Phase 2では少なくとも次を区別する。

| Value | Meaning |
|---|---|
| `NORMAL` | 通常の保存Policyを満たす情報 |
| `SENSITIVE` | 明示的な保存意思と追加の利用制約が必要な情報 |

センシティブ性を判定できない候補は、保存前の一時的な`MemoryCandidate`として扱い、確定済み`PersonalMemory`として保存しない。SecretはSensitivity Levelに関係なく保存禁止とする。

#### MemoryState

Phase 2では最小限、次を区別する。

| Value | Meaning |
|---|---|
| `ACTIVE` | 現在も適用される情報として利用可能 |
| `RESOLVED` | 解決・終了済みであり、現在進行中の事実として扱わない |

`RESOLVED`は削除ではない。過去の相談や状態を理解するため、現在の質問に明確に関連する場合だけ歴史的Contextとして利用できる。

Memory利用設定のON / OFFはMemory単位の`state`ではなく、別の`MemoryPreferences`で管理する。

削除済みMemoryを`DELETED`状態の通常Entityとして検索可能な場所へ残さない。削除および再登録防止はSection 9.7の別Boundaryで扱う。

#### MemoryTimestamps

すべての業務日時はProject共通方針に従い、JST Offset付き、ミリ秒精度の値として扱う。

```text
2026-08-25T21:30:00.000+09:00
```

Java Domain / ApplicationではJSTへ正規化した`OffsetDateTime`相当の値を使用し、`Clock`を注入してTest可能にする。

| Timestamp | Update Rule |
|---|---|
| `createdAt` | Memoryを最初に保存した時だけ設定し、更新しない |
| `updatedAt` | Content、Category、Stateその他の管理対象情報を変更した時に更新する |
| `confirmedAt` | ユーザーが明示登録、編集または肯定した時に更新する。未確認なら空 |

検索、AI Contextへの追加、回答での利用、Aliceによる推測またはユーザーが否定しなかったことだけでは`confirmedAt`を更新しない。

### 9.4 MemoryCandidate

`MemoryCandidate`はConversationから抽出した保存検討中の一時的なApplication Modelであり、確定済み`PersonalMemory`ではない。

```text
Conversation Information
        │
        ▼
MemoryCandidate
        │
        ├── 保存禁止確認
        ├── センシティブ性判断
        ├── 長期価値判断
        ├── 重複・統合・矛盾判断
        └── 必要なUser Confirmation
                │
                ▼
        PersonalMemoryとして保存
```

候補抽出に失敗した場合、判定不能の場合または保存条件を満たさない場合、`PersonalMemory`を作成しない。

`MemoryCandidate`を長期保存するか、処理中だけ保持するかは、失敗時整合性と再試行方式を設計する際に決定する。候補を保持する場合も、有効なPersonal Memory、通常検索結果または回答Contextとして扱わない。

### 9.5 MemoryPreferences

自動保存と通常回答へのMemory利用は、個々の`PersonalMemory`ではなく、独立したユーザー設定`MemoryPreferences`として管理する。

```text
MemoryPreferences
├── autoSaveEnabled
├── answerUseEnabled
├── version
└── updatedAt
```

| Field | Initial Value | Meaning |
|---|---:|---|
| `autoSaveEnabled` | `true` | 通常Conversationから自動保存候補を処理するか |
| `answerUseEnabled` | `true` | 通常回答でPersonal Memoryを検索・利用するか |
| `version` | `1` | 設定変更の競合を防ぐPreferences固有の単調増加Version |
| `updatedAt` | Initial creation time | 設定を最後に変更した日時 |

両設定は独立して変更できる。`answerUseEnabled = false`でも、明示的なMemory管理要求および管理画面からの操作は可能とする。

設定値が一つでも実際に変わる場合だけ`version`と`updatedAt`を更新する。APIのETagはこのVersionからPresentation層で生成するOpaque Tokenであり、Domain ModelへHTTP ETagを持ち込まない。

### 9.6 Lifecycle

```text
MemoryCandidate
      │
      │ 保存条件を満たす
      ▼
   ACTIVE
      │  内容・Category等の更新
      ├──────────────────┐
      │                  │
      │ 解決・終了       │ 明示削除
      ▼                  ▼
  RESOLVED          Removed from active Memory
      │                  │
      │ 再び有効         └── Re-registration Guard
      ▼
   ACTIVE
```

Lifecycle Rule:

1. 保存前の候補は`PersonalMemory`ではない。
2. 新規保存されたMemoryは原則`ACTIVE`とする。
3. 内容、CategoryまたはStateの変更では同じ`memoryId`を維持する。
4. 一時的な悩み等が終了した場合、削除せず`RESOLVED`へ変更できる。
5. `RESOLVED`を現在進行中の事実として回答へ利用しない。
6. ユーザーが削除した場合、通常の有効Memory集合から除外する。
7. 削除済み内容の再登録は、ユーザーによる新しい明示意思がある場合だけ許可し、新しい`memoryId`を発行する。

自動的なState変更は行わない。Aliceが解決・変更を推測しただけの場合は、必要に応じてユーザーへ確認する。

### 9.7 Deletion Boundary

削除済みMemory本文を通常の`PersonalMemory`として保持しない。一方、Conversation Historyからの自動再登録を防止するため、`memory` Featureは有効Memoryとは分離した論理的な`Re-registration Guard`を所有する。

Guardが満たす条件:

- 削除本文をそのまま復元できる形式で保持しない
- 通常のMemory検索結果へ含めない
- AI ContextまたはPersonalizationへ利用しない
- 自動再登録防止以外へ利用しない
- ユーザーの明示的な再登録を妨げない

Fingerprint、Scope、Memory Reset Point、保存期間およびPersistence形式は削除・再登録防止の詳細設計で決定する。

### 9.8 Model Separation

```text
API DTO
   ↕ Mapper
Application / Domain Model
   ↕ Mapper
Infrastructure Item
```

次を同一Classとして共用しない。

- Flutter ModelとBackend Domain Model
- HTTP Request / Response DTOと`PersonalMemory`
- DynamoDB Itemと`PersonalMemory`
- AI Candidate Modelと`PersonalMemory`
- Backup Archive Recordと`PersonalMemory`

単純なMappingのためだけに過剰なMapper Classを作らないが、Infrastructure AttributeまたはAI固有FieldをDomainへ漏らさない。

---

## 10. Use Cases and Application Flow

本Sectionは、Phase 2で必要となるMemory Application Use Caseと、その概念的な処理Flowを定義する。

Use Caseはユーザーまたは他Featureが達成したい目的を表す。HTTP Endpoint、Controller、DynamoDB操作またはAI Provider RequestそのものをUse Caseとしない。

### 10.1 Use Case Groups

| Group | Purpose | Main Caller |
|---|---|---|
| Answer Context | 通常回答に関連Memoryを提供する | `conversation.application` |
| Capture | 明示要求または通常ConversationからMemoryを保存する | `conversation.application` |
| Management | Memoryの一覧、詳細、登録、確認、更新および削除 | `memory.presentation` / `conversation.application` |
| Preferences | 自動保存と回答利用の設定を管理する | `memory.presentation` / `conversation.application` |
| Transparency | 回答へ利用したMemoryを説明可能にする | `conversation.application` |
| Backup | 暗号化ArchiveをExport、検証およびRestoreする | `memory.presentation` |

### 10.2 Required Use Cases

以下は概念Class名である。最終的なPackage、Input / Output ModelおよびMethod SignatureはApplication詳細設計で確定する。

| ID | Conceptual Use Case | Responsibility |
|---|---|---|
| `MEM-UC-001` | `FindRelevantMemoriesUseCase` | 現在の質問に関連し、利用可能なMemoryを必要最小限で返す |
| `MEM-UC-002` | `ProcessConversationMemoryUseCase` | 通常Conversationから自動保存候補を抽出・評価する |
| `MEM-UC-003` | `RegisterMemoryUseCase` | 明示要求または管理画面から新しいMemoryを登録する |
| `MEM-UC-004` | `ListMemoriesUseCase` | 全体、Category、Stateおよび検索条件からMemory一覧を取得する |
| `MEM-UC-005` | `GetMemoryUseCase` | 指定Memoryの管理用詳細を取得する |
| `MEM-UC-006` | `UpdateMemoryUseCase` | Content、Category、SensitivityまたはStateを訂正・更新する |
| `MEM-UC-007` | `ConfirmMemoryUseCase` | 内容を変更せず、ユーザーの明示確認として`confirmedAt`を更新する |
| `MEM-UC-008` | `DeleteMemoryUseCase` | 対象Memoryを削除し、自動再登録防止Boundaryへ反映する |
| `MEM-UC-009` | `DeleteAllMemoriesUseCase` | 明示確認後に全有効Memoryを削除し、Memory Reset Pointを設定する |
| `MEM-UC-010` | `GetMemoryPreferencesUseCase` | 自動保存と回答利用の現在設定を取得する |
| `MEM-UC-011` | `UpdateMemoryPreferencesUseCase` | 自動保存と回答利用を独立して変更する |
| `MEM-UC-012` | `ExplainMemoryUsageUseCase` | 指定回答へ影響したMemoryを理解可能な形で説明する |
| `MEM-UC-013` | `ExportMemoryBackupUseCase` | 有効MemoryとMemory設定を暗号化ArchiveとしてExportする |
| `MEM-UC-014` | `InspectMemoryBackupUseCase` | Archiveの形式、Version、完全性、Size、内容および影響を検証する |
| `MEM-UC-015` | `RestoreMemoryBackupUseCase` | 検証済みArchiveを明示確認後にRestoreする |
| `MEM-UC-016` | `DeleteMemoriesUseCase` | ユーザーが確認した複数の対象MemoryをScope付きで削除する |

`ListMemoriesUseCase`は一覧と検索の共通Application Boundaryとし、Phase 2で検索専用Use Caseを理由なく重複作成しない。Access PatternまたはAPI Contract上の合理的理由が確認された場合に分離する。

### 10.3 Answer-time Memory Flow

```text
SendMessageUseCase
      │
      ├── MemoryPreferences確認
      │       └── answerUseEnabled = false → Memory検索を行わない
      │
      ├── FindRelevantMemoriesUseCase
      │       ├── Question / Conversation Contextを必要最小限で受領
      │       ├── ACTIVE / 関連するRESOLVED Memoryを検索
      │       ├── Currentness / Sensitivity / Relevance Ruleを適用
      │       └── Memory Context Itemを返す
      │
      ├── Conversation HistoryとMemory Contextを区別してContext構築
      └── AI Capability呼出し
```

`FindRelevantMemoriesUseCase`は、DynamoDB Itemまたは`PersonalMemory` AggregateをそのままConversation Featureへ返さない。回答Contextへ利用できる最小限のApplication Modelを返す。

Memory取得に失敗した場合、可能な限りMemoryなしでConversationを継続する。AliceはMemoryを取得・利用できたように振る舞わない。

### 10.4 Explicit Registration Flow

```text
User: 「覚えておいて」 / Management UI Register
      │
      ▼
RegisterMemoryUseCase
      │
      ├── Input Validation
      ├── Secret / 保存禁止確認
      ├── Content正規化
      ├── Category / Sensitivity判定
      ├── Related Memory取得
      ├── New / Duplicate / Merge / Update / Conflict判定
      ├── 必要な場合だけUser Confirmation要求
      └── 実際の保存結果を返す
```

センシティブ情報について、ユーザーの明示的な記憶要求は保存意思として扱う。ただしSecretまたは保存禁止対象は、明示要求があっても保存しない。

同一内容の場合は重複保存せず、`NO_CHANGE`相当の結果を返せること。判断不能な矛盾の場合は保存せず、確認に必要な候補情報を返す。

### 10.5 Automatic Capture Flow

```text
Canonical Conversation Information
      │
      ├── autoSaveEnabled = false → 終了
      │
      ▼
ProcessConversationMemoryUseCase
      │
      ├── MemoryCandidate抽出
      ├── 長期価値 / 再利用性評価
      ├── Secret / Sensitive / Category判定
      ├── Related Memory取得
      ├── Duplicate / Merge / Update / Conflict判定
      └── Save / Skip / Confirmation Required
```

通常Conversationからセンシティブ情報を自動保存しない。センシティブまたは判定不能な候補は、保存せず必要に応じてUser Confirmationへ移行する。

自動保存処理の失敗によって、完了可能なConversation回答を不必要に失敗させない。Conversation回答とMemory保存の正確な実行順序はSection 10.12の未決定事項とする。

### 10.6 Management Flow

#### List and Detail

```text
Memory Management UI
      │
      ├── ListMemoriesUseCase
      │       └── Category / State / Search / Pagination
      │
      └── GetMemoryUseCase
              └── Content / Category / CaptureType / State / Timestamps
```

通常の管理画面へ、内部Score、Embedding、Persistence Key、再登録防止情報またはInfrastructure Metadataを表示しない。

#### Update and Confirm

- Content、Category、SensitivityまたはStateを変更する場合は`UpdateMemoryUseCase`を使用する。
- 内容を変更せず「現在も正しい」と確認する場合は`ConfirmMemoryUseCase`を使用する。
- 更新では`updatedAt`を、明示確認では`confirmedAt`をAccepted Timestamp Ruleに従って更新する。
- Aliceの推測だけで`ConfirmMemoryUseCase`を実行しない。

#### Individual Delete

```text
Delete Request
      │
      ├── Targetを一意に特定できるか
      │       └── No → 候補提示・User Confirmation
      │
      ├── DeleteMemoryUseCase
      ├── 有効Memory集合から除外
      ├── Re-registration Guardへ反映
      └── 実際の結果を返す
```

自然言語操作と管理画面操作は同じ`DeleteMemoryUseCase`とDomain Ruleを利用する。

#### Scoped Multiple Delete

「Javaについて覚えていることを削除して」のように複数Memoryが対象になり得る場合は、`DeleteMemoriesUseCase`を利用する。

```text
Scoped Delete Request
      │
      ├── 対象候補を検索
      ├── 削除対象一覧と件数を提示
      ├── Explicit Confirmation
      ├── DeleteMemoriesUseCase
      └── Complete / Partial / Unknown / Failedを返す
```

対象集合を確定できないまま、検索結果の全件を削除しない。

### 10.7 Delete-all Flow

```text
Delete All Request
      │
      ├── 対象範囲と影響を表示
      ├── Explicit Confirmation
      ├── DeleteAllMemoriesUseCase
      ├── 全有効Memoryを削除
      ├── Memory Reset Pointを設定
      └── Complete / Partial / Unknown / Failedを返す
```

一部成功または結果不明を全件成功として表示しない。削除済み範囲と残存Memoryを再確認できること。全件成功または部分成功を許可する具体的なTransaction方式はDatabase詳細設計で決定する。

### 10.8 Preferences Flow

`MemoryPreferences`は次の4状態をすべて許可する。

| autoSaveEnabled | answerUseEnabled | Behavior |
|---:|---:|---|
| `true` | `true` | 自動保存と通常回答でのMemory利用を行う |
| `false` | `true` | 自動保存せず、保存済みMemoryは通常回答へ利用できる |
| `true` | `false` | 自動保存は行うが、通常回答へMemoryを利用しない |
| `false` | `false` | 自動保存も通常回答への利用も行わない |

設定変更は既存Memoryを削除しない。`answerUseEnabled = false`の場合も、明示的なMemory管理要求と管理画面操作は利用できる。

### 10.9 Transparency Flow

回答ContextへMemoryを渡した際は、少なくとも利用した`memoryId`と、説明に必要な非Infrastructure情報をRequest Scopeで追跡できること。

```text
Answer Request
      │
      ├── Used Memory References
      └── Answer Result
              │
              ▼
      ExplainMemoryUsageUseCase
```

追跡情報の保存期間、Persistence要否およびConversation Messageとの関連付け方法はObservability / Database詳細設計で決定する。Memory本文を無条件にLogへ出力しない。

### 10.10 Backup and Restore Flow

```text
ExportMemoryBackupUseCase
      ├── 有効Memoryと設定を取得
      ├── Archive ModelへMapping
      ├── Version / Integrity情報を付与
      └── 暗号化してExport

User selects Archive
      │
      ▼
InspectMemoryBackupUseCase
      ├── 復号
      ├── Format / Version / Integrity / Size検証
      ├── Content Validation
      └── 追加・更新・置換・削除の影響を提示
              │
              ├── User Confirmationなし → Restoreしない
              ▼
      RestoreMemoryBackupUseCase
```

`RestoreMemoryBackupUseCase`は`InspectMemoryBackupUseCase`による検証済み結果と明示確認を必要とする。無効、破損、未対応または安全でないArchiveを読み込まない。

Phase 2 RestoreはSection 20で定義する非破壊の`MERGE_SAFE`を使用する。検証済み結果はArchive Digest、対象Versionおよび有効期限へBindingしたRestore Planとして実行へ受け渡す。

### 10.11 Application Result Rules

Application Use Caseは、少なくとも次の論理結果を区別できる設計とする。これは共通巨大Enumの先行実装を要求するものではない。

| Result | Meaning |
|---|---|
| `SUCCESS` | 要求した処理が確認済みの状態で完了した |
| `NO_CHANGE` | 重複等により変更は不要だった |
| `CONFIRMATION_REQUIRED` | 対象、矛盾、Sensitiveまたは影響についてUser Confirmationが必要 |
| `PARTIAL_SUCCESS` | 複数対象の一部だけ成功した |
| `UNKNOWN` | External / Persistence結果を確定できない |
| `FAILED` | 処理が失敗した |

Mutationの`PARTIAL_SUCCESS`、`UNKNOWN`または`FAILED`を`SUCCESS`としてPresentationへ返さない。

成功が確認済みのMutationを再試行で重複実行しない。失敗したMemory変更を復旧後にユーザー意思なしで自動再実行しない。

### 10.12 Deferred Execution Details

本SectionのFlowは責務と分岐を示す概念Flowであり、次をまだ確定しない。

- Conversation回答と自動保存の正確な実行順序
- 同期処理、非同期処理またはBackground処理の採否
- MemoryCandidateの一時保存方式
- AI Analysisの呼出回数とBatch単位
- Transaction Boundary
- Retry回数、TimeoutおよびBackoff
- Partial SuccessのPersistence表現
- Used Memory Referenceの保存期間

これらは保存判断、Database、AI、SecurityおよびTest詳細設計で決定する。

---

## 11. Memory Candidate Extraction and Save Decision

本Sectionは、Conversationまたは管理画面入力から`MemoryCandidate`を作り、保存処理へ進めるかを判断する責務とRuleを定義する。

初心者向けに整理すると、保存判断は次の5問を順番に確認する処理である。

1. その情報はユーザーに関する事実またはユーザーが覚えてほしい情報か。
2. Conversationをまたいで将来利用する価値があるか。
3. Secretや保存禁止情報ではなく、安全に保存できるか。
4. 内容、Categoryおよびセンシティブ性を十分明確に判断できるか。
5. 既存Memoryと同じ、補足、変更または矛盾ではないか。

1〜4を本Sectionで定義し、5の重複・統合・更新・矛盾判定は次の詳細設計で確定する。

### 11.1 Separation of Responsibilities

```text
Canonical Input
      │
      ▼
Candidate Extractor
      │  構造化された候補を提案
      ▼
Alice Save Policy
      │  明示Ruleで保存適格性を判断
      ▼
Related Memory Evaluation
      │  New / Duplicate / Merge / Update / Conflict
      ▼
Persistence Command
```

| Component | Responsibility | Must Not Do |
|---|---|---|
| Caller | 明示要求、通常Conversationまたは管理画面入力をCanonical Inputとして渡す | 独自の保存価値・Sensitive Ruleを実装する |
| Candidate Extractor | 入力からAtomicな候補と判定材料を構造化する | AI出力だけで保存を確定する |
| Alice Save Policy | 保存禁止、Grounding、長期価値、再利用性、Sensitivityおよび明確性を評価する | Provider固有Scoreへ最終判断を委ねる |
| Related Memory Evaluation | 既存Memoryとの関係を評価する | 新規・更新・矛盾を単語一致だけで決める |
| Repository Port | 確定したMutationを永続化する | 保存価値を判断する |

Candidate ExtractorにAI Capabilityを利用できるが、その出力は提案である。Alice Save PolicyとDomain Validationを通過していないCandidateを`PersonalMemory`として保存しない。

### 11.2 Canonical Input

Candidate抽出は、Conversation Infrastructure ModelやOpenAI Requestを直接受け取らず、Application側で作成したProvider非依存のCanonical Inputを受け取る。

概念上、少なくとも次を区別する。

| Input | Purpose |
|---|---|
| Current User Content | 今回ユーザーが入力した本文 |
| Minimal Conversation Context | 代名詞や対象を解決するための必要最小限の直近Context |
| Explicit Memory Intent | 「覚えておいて」等の明示的な保存意思の有無 |
| Capture Type | `EXPLICIT`または`AUTOMATIC` |
| Source References | 追跡に必要なConversation / MessageのOpaque ID |

保存候補は原則としてユーザーが提供した情報にGroundingする。Aliceの過去回答は文脈解決には使用できるが、Alice自身が生成した推測または提案だけを根拠にユーザーのMemoryを作らない。

Conversation全文を候補抽出のために無制限に渡さない。必要なContext範囲、文字数・Byte数、Candidate件数の上限はAI詳細設計で確定する。

### 11.3 MemoryCandidate Model

`MemoryCandidate`は少なくとも次の論理情報を保持できる設計とする。これは永続化Schemaを規定しない。

| Field | Required | Meaning |
|---|---:|---|
| `candidateId` | Yes | 一つの処理内で候補を識別する一時ID |
| `contentProposal` | Yes | ユーザーが理解可能な正規化済み本文案 |
| `categoryProposal` | Yes | Primary Category案 |
| `sensitivityProposal` | Yes | `NORMAL`または`SENSITIVE`の案。判断不能も結果として区別する |
| `captureType` | Yes | `EXPLICIT`または`AUTOMATIC` |
| `sourceReferences` | Yes | 根拠となるUser Message等のOpaque参照 |
| `groundingSummary` | Yes | どのユーザー提供情報に基づくかを検証するための要約 |
| `longTermValueReason` | Yes | Conversationをまたぐ価値があると考えた理由 |
| `reuseExamples` | No | 将来どの場面で役立つかの例 |
| `uncertainties` | No | 内容、Category、Sensitivity等の未確定点 |

AI Provider固有のResponse Object、Token情報、EmbeddingまたはModel名を`MemoryCandidate`へ含めない。AIが返すConfidence値は診断材料として利用できるが、Aliceの最終保存条件にはしない。

一つのCandidateには一つの意味的事実だけを含める。例えば「Javaは苦手で、友人Aはビールが好き」は、`ENGINEERING`と`PERSON`の二つへ分割する。

### 11.4 Extraction Rules

Candidate Extractorは次のRuleを満たす候補だけを返す。

1. ユーザー発言またはユーザーの明示登録入力に根拠がある。
2. 意味を変えず、管理画面で単独表示して理解できる本文へ正規化する。
3. 複数の独立事実をAtomic Candidateへ分割する。
4. 推測を事実へ昇格しない。
5. Aliceの返答、一般知識またはTool取得結果だけからユーザー固有Memoryを生成しない。
6. 相対表現を根拠なく絶対表現へ変換しない。
7. Secretらしい文字列を本文案へ複製・要約しない。
8. 抽出不能、意味不明または根拠不足の場合は空の候補集合を返せる。

例：

| Input | Candidate Result | Reason |
|---|---|---|
| 「私はJavaがまだ苦手」 | 「ユーザーはJavaを苦手分野と認識している」 | User-groundedで長期利用可能 |
| 「明日の会議は10時」 | 自動保存候補なし | 一時的な予定 |
| Aliceが「Pythonが得意そう」と推測した | 候補なし | ユーザー提供情報ではない |
| 「これ覚えて。予備電池は廊下の箱」 | 「予備の乾電池は廊下の収納箱にある」 | 明示要求、`OTHER`候補 |
| 「API Keyは○○。覚えて」 | 保存可能な本文候補を作らない | Secretは明示要求でも保存禁止 |

### 11.5 Save Eligibility Gates

各Candidateを次の順番で評価する。前段で保存不可が確定した場合、不要なAI呼出しやRelated Memory検索を行わない。

| Order | Gate | Pass Condition | Failure Result |
|---:|---|---|---|
| 1 | Input and Grounding | User-groundedで意味が明確 | `SKIPPED`または`CONFIRMATION_REQUIRED` |
| 2 | Prohibited Content | Secretその他の保存禁止情報ではない | `REJECTED` |
| 3 | User Relevance | ユーザー固有情報または明示的に覚える対象 | `SKIPPED` |
| 4 | Long-term Value | Conversationをまたいで有用 | `SKIPPED` |
| 5 | Reuse Potential | 将来利用できる具体的場面がある | `SKIPPED` |
| 6 | Sensitivity | 保存意思とPolicyを満たす | `SKIPPED`または`CONFIRMATION_REQUIRED` |
| 7 | Content Clarity | 本文、対象、Categoryが確定可能 | `CONFIRMATION_REQUIRED`または`SKIPPED` |
| 8 | Re-registration Guard | 削除済み情報の無断再登録ではない | `SKIPPED`または`CONFIRMATION_REQUIRED` |
| 9 | Related Memory | 既存Memoryとの関係を評価できる | 次設計のRelation Decisionへ進む |

長期価値は「重要そう」という印象だけで判断しない。少なくとも、将来の回答Personalization、継続相談、ユーザーの目標支援、人物・場所の再参照、技術支援またはProject継続性のいずれかに具体的に役立つ必要がある。

一時的な予定、現在価格、営業時間、天気、単発の雑談、挨拶および一般知識は、ユーザーが明示的に保存を求めた場合を除き自動保存しない。明示要求があっても、変化しやすい情報であることにより将来誤認を生む場合は確認または安全な保存表現への修正を行う。

### 11.6 Explicit and Automatic Decision Policy

明示保存と自動保存は同じValidation Pipelineを利用するが、保存意思と判断基準が異なる。

| Condition | Explicit Request | Automatic Capture |
|---|---|---|
| `autoSaveEnabled = false` | 処理する | 候補抽出を行わない |
| 通常情報・明確・保存可能 | Related Memory評価へ進む | 長期価値と再利用性も満たす場合だけ進む |
| Sensitive | 明示要求を保存意思として扱い、追加Policyを満たせば進む | 自動保存しない |
| Secret / 保存禁止 | 拒否する | 拒否または安全にSkipする |
| 対象・意味が曖昧 | User Confirmation | 原則Skip。重要性が高く自然な場合だけConfirmation候補 |
| 長期価値が低い | 明示意思を尊重するが、誤認リスクがあれば確認する | Skip |
| 削除済み内容と一致 | 新しい明示意思を確認して再登録可能 | 自動再登録しない |

通常ConversationでSensitive Candidateを検出した場合、勝手に保存しない。長期利用価値が高く、確認が会話を不自然に中断しない場合だけ`CONFIRMATION_REQUIRED`を返せる。それ以外は`SKIPPED`とする。

`CONFIRMATION_REQUIRED`は「判断できないものをすべてユーザーへ聞く」ためのDefaultではない。確認しなくても安全に見送れる候補は`SKIPPED`とし、Conversationを頻繁に中断しない。

### 11.7 Rule-based Decision, Not a Single Score

Phase 2では、長期価値、センシティブ性および明確性を一つの総合数値Scoreへ合算し、閾値だけで保存を決める方式を採用しない。

理由：

- Secret禁止のようなHard Ruleを高い有用性Scoreで上書きさせないため
- AI Model変更によるScoreの揺れをAliceの保存Policyへ直接持ち込まないため
- ユーザーへ保存・見送り理由を説明可能にするため
- Testで各Ruleを独立して検証できるようにするため

AI Confidenceや補助ScoreをObservabilityまたは調整材料として使用することはできるが、単独で保存可否を確定しない。

### 11.8 Candidate Decision Result

保存判断はCandidateごとに結果を返す。複数Candidateの一部が失敗しても、他の結果を失わない。

| Result | Meaning |
|---|---|
| `ELIGIBLE` | 保存適格性を満たし、Related Memory評価へ進める |
| `SKIPPED` | 安全に保存を見送った |
| `REJECTED` | Secretまたは保存禁止Ruleにより拒否した |
| `CONFIRMATION_REQUIRED` | ユーザー意思または内容の確認がなければ進められない |
| `FAILED` | 抽出・評価処理自体が失敗した |

Resultは少なくとも、`candidateId`、Result種別および機密情報を含まないReason Codeを持てる設計とする。`CONFIRMATION_REQUIRED`の場合は、ユーザーが理解できる質問と選択対象を構築するための非Infrastructure情報を返す。

Reason Codeの概念例：

- `NOT_USER_GROUNDED`
- `TEMPORARY_INFORMATION`
- `LOW_LONG_TERM_VALUE`
- `NO_REUSE_SCENARIO`
- `PROHIBITED_SECRET`
- `SENSITIVE_WITHOUT_EXPLICIT_INTENT`
- `AMBIGUOUS_CONTENT`
- `CATEGORY_UNRESOLVED`
- `RE_REGISTRATION_BLOCKED`

Reason CodeはAPI Error Codeと同一ではない。最終的なCode一覧と外部公開範囲はApplication / API詳細設計で確定する。

### 11.9 Failure and Privacy Rules

- Candidate Extractorが失敗した場合、推測で空でないMemoryを生成しない。
- Automatic Captureの失敗は、完了可能なConversation回答を失敗させない。
- Explicit Requestの失敗は、保存できたように返答せず実際の結果を示す。
- Candidate本文、User Message全文、Secret候補およびSensitive情報を無条件にLogへ出力しない。
- Provider Error、Prompt、Raw ResponseまたはInternal Scoreをユーザー向けResultへ漏らさない。
- Timeout、Retry、Candidate Batch上限および一時Candidate保持方式はAI / Database / Security詳細設計で確定する。

### 11.10 Deferred Relation Decisions

本Sectionで`ELIGIBLE`となったCandidateは、まだ新規保存確定ではない。次の詳細設計で既存Memoryと比較し、次を決定する。

- New
- Duplicate / No Change
- Merge
- Update
- Conflict / User Confirmation

この分離により、「保存する価値があるか」と「既存Memoryをどう変更するか」を混同しない。

---

## 12. Duplicate, Merge, Update and Conflict Decision

本Sectionは、保存適格性を満たした`MemoryCandidate`と既存`PersonalMemory`の関係を判定し、どのMutationへ進めるかを定義する。

初心者向けに整理すると、次の違いを判定する処理である。

| Situation | Example | Expected Handling |
|---|---|---|
| New | 既存Memoryに関連情報がない | 新規保存 |
| Duplicate | 「Javaが苦手」／「Javaは得意ではない」 | 重複保存しない |
| Complement | 「友人Aはビールが好き」／「友人AはIPAが特に好き」 | 一つの事実に統合できる場合だけ更新 |
| Related but Independent | 「Javaが苦手」／「仕事でJavaを使っている」 | 両方を保持 |
| Clear Change | 「ビールが好き」／「今はビールを飲まない」 | 最新状態へ更新可能 |
| Conflict | 「辛い物が好き」／「辛い物が嫌い」だが時点不明 | 確認するまで変更しない |

### 12.1 Relation Decision Flow

```text
Eligible MemoryCandidate
      │
      ├── 同一Batch内のCandidateを確認
      ├── Related Memoryを限定取得
      ├── Subject / Attribute / Scope / Timeを比較
      ├── Relation Typeを判定
      ├── Mutation Planを作成
      └── Policy Validation
              │
              ├── 確定可能 → Mutation実行
              └── 判断不能 → User Confirmation
```

Relation判定とPersistence Mutationを分離する。AIまたは検索結果がRelationを提案しても、Application / Domain Ruleが許可したMutationだけをRepository Portへ渡す。

### 12.2 Comparison Dimensions

単語の一致率またはCategoryだけでRelationを決めない。少なくとも次を比較する。

| Dimension | Meaning | Example |
|---|---|---|
| Subject | 誰・何についての情報か | ユーザー、友人A、Project Alice、店舗B |
| Attribute / Predicate | 対象のどの性質・関係か | 好き、苦手、勤務先、採用技術 |
| Value | 属性に対する内容 | ビール、Java、札幌、DynamoDB |
| Scope | どの場面・範囲で成立するか | 仕事、趣味、特定Project、食事 |
| Temporal Applicability | 現在、過去、期間限定等の適用時点 | 現在は、以前は、2026年時点 |
| Polarity / Qualification | 肯定・否定・程度・条件 | 好き、嫌い、少し苦手、場合による |
| State | 現在有効か、解決済みか | `ACTIVE`、`RESOLVED` |

Categoryが同じでもSubjectやAttributeが異なればDuplicateではない。Categoryが異なっていても同じ事実を表す可能性があるため、Category不一致だけで比較対象から除外しない。

### 12.3 Relation Types

Phase 2では次の論理Relationを区別する。

| Relation | Meaning |
|---|---|
| `NONE` | 関連する既存Memoryがない |
| `EQUIVALENT` | 表現は異なるが意味的に同一 |
| `COMPLEMENTARY_SAME_FACT` | 同じ事実へ追加情報を加え、一つのAtomic Memoryに統合可能 |
| `RELATED_INDEPENDENT` | 関連するが、別々に成立する独立情報 |
| `SUPERSEDES` | 新情報が同じ事実の明確な現在状態を置き換える |
| `CONFLICT` | 同時に成立しない可能性があるが、どちらを正とするか確定不能 |
| `UNCERTAIN` | 対象、属性、時点または意味を安全に判定できない |

Relation Typeは保存結果ではない。Relation TypeをPolicyへ適用し、次のMutation Planへ変換する。

### 12.4 Mutation Plans

| Relation | Default Mutation Plan | Result |
|---|---|---|
| `NONE` | `CREATE_NEW` | 新しい`memoryId`で保存 |
| `EQUIVALENT` | `NO_CHANGE` | 重複保存しない |
| `COMPLEMENTARY_SAME_FACT` | `UPDATE_EXISTING` | 同じ`memoryId`のContentを統合 |
| `RELATED_INDEPENDENT` | `CREATE_SEPARATE` | 既存Memoryを維持して別Memoryを保存 |
| `SUPERSEDES` | `UPDATE_EXISTING` | 同じ`memoryId`を最新状態へ更新 |
| `CONFLICT` | `CONFIRMATION_REQUIRED` | 確認までMutationしない |
| `UNCERTAIN` | `CONFIRMATION_REQUIRED`または`SKIP` | 安全性と重要性に応じる |

`MERGE`という独立した曖昧な永続化操作は定義しない。本設計でのMergeは、同じAtomic Memoryへ情報を補足する`UPDATE_EXISTING`を意味する。

### 12.5 Duplicate Rules

`EQUIVALENT`は、表記や語順ではなく意味が同一の場合に成立する。

例：

- 「Javaが苦手」
- 「Javaは得意ではない」

これらは通常、一つのMemoryとして扱える。ただし「少し苦手」と「非常に苦手」のように程度が変わる場合は、単純Duplicateではなく補足または更新の可能性を評価する。

Duplicateの場合：

- 新しい`PersonalMemory`を作らない。
- 既存Memoryの`updatedAt`を変更しない。
- Automatic Captureだけでは`confirmedAt`を変更しない。
- ユーザーが明示的に同じ内容を再登録・肯定した場合だけ、`ConfirmMemoryUseCase`により`confirmedAt`を更新できる。
- API / Conversation Resultは`NO_CHANGE`を返し、保存したと偽らない。

### 12.6 Complement and Independent Information

補足情報を一つのMemoryへ統合するのは、統合後も一つの意味的事実として読める場合だけとする。

| Existing | Candidate | Decision |
|---|---|---|
| 「友人Aはビールが好き」 | 「友人AはIPAが特に好き」 | 一つのPreferenceとして統合可能 |
| 「Javaが苦手」 | 「Spring Bootは未経験」 | 技術領域は関連するが別Memory |
| 「札幌に住んでいる」 | 「札幌に長く住みたい」 | 現在地と希望は別Memory |
| 「店舗Bを気に入った」 | 「店舗Bは金曜に混みやすかった」 | 恒常Preferenceと一時的観察を分離し、後者は保存価値を再評価 |

統合によって元の意味、条件、時点またはセンシティブ性が失われる場合は`UPDATE_EXISTING`にしない。過度に長いContentや複数事実の詰め込みを避け、必要なら`CREATE_SEPARATE`とする。

### 12.7 Clear Update and Supersession

`SUPERSEDES`は、次のすべてを満たす場合に限りUser Confirmationなしで`UPDATE_EXISTING`へ進められる。

1. SubjectとAttributeが同じである。
2. 新情報が現在状態または変更を明確に表している。
3. ユーザー提供情報へGroundingしている。
4. 旧情報と新情報の時系列が明確である。
5. 保存禁止、Sensitivityおよび再登録防止Ruleを満たす。
6. 更新対象の既存Memoryを一意に特定できる。

例：

| Existing | New User Information | Handling |
|---|---|---|
| 「ビールが好き」 | 「最近はビールを飲まなくなった」 | 現在のPreferenceへ更新可能。ただし単なる一時休止なら確認 |
| 「Javaが苦手」 | 「今はJavaを問題なく使える」 | 能力認識の明確な変更として更新可能 |
| 「札幌に住んでいる」 | 「東京へ引っ越した」 | 現在居住地を更新可能 |
| 「悩みXを抱えている」 | 「悩みXは解決した」 | ContentまたはStateを`RESOLVED`へ更新可能 |

Aliceが会話から「おそらく変わった」と推測しただけでは更新しない。時点または継続性が曖昧なら`CONFIRMATION_REQUIRED`とする。

### 12.8 Conflict Rules

次を満たす場合に`CONFLICT`候補とする。

- SubjectとAttributeが同じである。
- ScopeとTemporal Applicabilityを考慮しても同時成立しにくい。
- 新情報が明確な変更を表していない。
- 既存と新規のどちらを現在の正しい情報とすべきか確定できない。

次は自動的にConflictとしない。

| Existing | Candidate | Why Coexistence Is Possible |
|---|---|---|
| 「Javaが苦手」 | 「Javaを仕事で使っている」 | 使用経験と得意不得意は別属性 |
| 「ビールが好き」 | 「今日はビールを飲みたくない」 | 長期Preferenceと一時的気分 |
| 「札幌に住んでいる」 | 「東京で働いている」 | 居住地と勤務地は別属性 |
| 「店舗Bを気に入った」 | 「店舗Bで一度不満があった」 | 全体Preferenceと個別体験 |

ConflictまたはUncertainの場合、既存Memoryを上書きせず、Candidateも有効Memoryとして保存しない。確認では少なくとも次の選択肢を表現できるようにする。

- 既存Memoryを維持する
- 新情報へ更新する
- 両方を別の条件・時点付きで保持する
- 今回は保存しない

### 12.9 Multiple Match and Intra-batch Rules

一つのCandidateに対して複数の更新対象が見つかり、一意に選べない場合は自動更新しない。

同じ入力から複数Candidateを抽出した場合、既存Memoryとの比較前にCandidate同士も比較する。これにより、同一Batch内の重複、矛盾または同じ対象への複数更新を、処理順序だけで異なる結果にしない。

同一Batch内で同じMemoryへ複数Mutationが必要な場合は、次のいずれかとする。

- 一つの安全なMutation Planへ統合する
- Candidateを独立したMemoryとして保存する
- 判断不能ならUser Confirmationへ移す

単純に先頭Candidateから順番に保存し、後続Candidateで上書きしない。

### 12.10 Source and Currentness Rules

新しい情報だからという理由だけで古いMemoryを上書きしない。次をCurrentness判断へ使用する。

- ユーザーが変更を明示しているか
- 「今は」「以前は」等のTemporal表現があるか
- 明示登録、管理画面編集または通常ConversationのどのSourceか
- 既存Memoryの`confirmedAt`と更新経緯
- 情報のScopeと条件が同じか

管理画面編集、明示的な記憶要求および明確な訂正は強いユーザー意思として扱う。ただしSecret禁止や正式DocumentのSource of Truthを上書きしない。

Project CategoryのPersonal Memoryは、Projectの正式Design DocumentまたはAccepted ADRを置き換えない。正式Decisionを変更する内容の場合、Memory更新だけでArchitecture変更を確定せず、対象Document / ADRを先に更新する。

### 12.11 Mutation Identity and Timestamp Rules

`UPDATE_EXISTING`では次を維持する。

| Field | Rule |
|---|---|
| `memoryId` | 維持する |
| `createdAt` | 維持する |
| `captureType` | 最初の生成経路を維持する |
| `updatedAt` | Mutation成功時刻へ更新する |
| `confirmedAt` | 明示登録、管理画面編集または明示確認の場合だけ更新する |

通常ConversationからのAutomatic Captureにより明確な変更を検出して更新しても、ユーザーがMemory内容を明示的に確認したわけではないため`confirmedAt`を自動更新しない。

同じMemoryへの並行更新や古い判定結果による上書きを防ぐため、`PersonalMemory`へ単調増加する論理`version`を追加する。Mutationは判定時に参照したExpected Versionと一致する場合だけ成功させる。

具体的なDynamoDB Conditional Write、API ETagまたはVersion Field表現はDatabase / API詳細設計で確定する。

### 12.12 Memory Revision History

ユーザーの嗜好・能力・生活状況等の変化をAliceが理解し、管理操作を追跡できるよう、Phase 2ではMemory更新履歴を保持する。

概念Model：

```text
MemoryRevision
├── memoryId
├── revisionNumber
├── changeType
├── beforeSnapshot
├── afterSnapshot
├── changedAt
├── changeSource
├── sourceReferences
└── reasonCode
```

Rules:

- `MemoryRevision`は`PersonalMemory`と同じ`memory` Featureが所有する。
- Content、Category、SensitivityまたはStateの変更ごとにRevisionを追加する。
- Revision NumberはMemory単位で単調増加させる。
- Conversation Transcript全文、Provider Request / ResponseまたはInfrastructure Metadataを保存しない。
- 通常回答Contextでは最新の`PersonalMemory`を使用し、Revision全文を無条件にAIへ渡さない。
- 過去状態が質問に関連する場合だけ、専用Application Boundaryを通じて必要最小限を利用する。
- Revisionの保存技術とAccess PatternはDatabase詳細設計で確定する。

Memory削除時に、Revisionへ残る過去Contentを保持し続けて削除要求を無効化してはならない。削除時のRevision消去、Content除去および再登録防止情報との分離は次の削除詳細設計で確定する。

### 12.13 Confirmation Binding

「はい」だけで別のMemoryを誤更新しないよう、確認要求は対象Decisionへ結び付ける。

概念上、Confirmation Contextは次を保持できる必要がある。

- Opaque `confirmationId`
- Candidate ID
- 対象Memory IDとExpected Version
- 提案するMutation Plan
- ユーザーへ提示した安全な選択肢
- 有効期限

確認後もExpected Versionが変わっていた場合、古い確認内容で上書きせず再評価する。期限切れ、対象不明または内容不一致の場合もMutationしない。

Confirmation ContextのPersistence、TTL、API ContractおよびConversationとの関連付けはApplication / Database / API詳細設計で確定する。

### 12.14 Relation Decision Result

Relation判定はCandidateごとに少なくとも次を返せる設計とする。

| Field | Meaning |
|---|---|
| `candidateId` | 入力Candidateとの対応 |
| `relationType` | Section 12.3のRelation |
| `targetMemoryIds` | 比較・Mutation対象となるMemory ID |
| `mutationPlan` | Create、No Change、Update、Create Separate、Confirmation等 |
| `expectedVersions` | 判定時に参照したVersion |
| `reasonCodes` | 機密情報を含まない判定理由 |
| `confirmationProposal` | 必要な場合だけ提示内容を構築する材料 |

AI固有Response、DynamoDB ItemまたはRaw Contentの無制限な複製をResultへ含めない。

### 12.15 Failure, Retry and Idempotency

- Related Memory取得に失敗した場合、`NONE`とみなして新規保存しない。
- Relation判定に失敗した場合、推測でUpdateまたはCreateしない。
- Version不一致は上書きせず、最新Memoryを取得して再評価する。
- 同じ操作のRetryでDuplicate Memoryを作らないよう、Mutation単位のIdempotency Boundaryを設ける。
- 複数Candidateの一部だけ成功した場合、Candidateごとの結果を保持し、全件成功として扱わない。
- Automatic Captureの失敗はConversation回答から分離するが、保存成功を偽らない。
- Explicit Mutationの結果が不明な場合、確認なしに同じMutationを再実行しない。

Idempotency Key、Retry回数、Transaction範囲および部分成功のPersistence表現はDatabase / API詳細設計で確定する。

---

## 13. Deletion, Memory Reset Point and Re-registration Prevention

本Sectionは、ユーザーが削除したPersonal Memoryを利用対象から除外し、Conversation Historyその他の残存情報から自動再登録されないようにするRuleを定義する。

削除には、次の二つの目的がある。

1. AliceがそのMemoryを回答、検索、Personalizationおよび管理画面へ利用しなくなること。
2. 過去Conversationを再処理して、同じ内容をAliceが勝手に覚え直さないこと。

この二つ目の目的があるため、単純なItem削除だけでは要件を満たさない。

### 13.1 Deletion Scope

Phase 2では次の3種類を区別する。

| Scope | Use Case | Example |
|---|---|---|
| Individual | `DeleteMemoryUseCase` | 「このMemoryを削除」 |
| Scoped Multiple | `DeleteMemoriesUseCase` | 「Javaについて覚えていることを削除」 |
| All Memories | `DeleteAllMemoriesUseCase` | 「AliceのMemoryをすべて削除」 |

`RESOLVED`を含むPersonal Memoryも削除対象にできる。State変更と削除を混同せず、「解決済みにする」は`RESOLVED`への更新、「忘れて」は削除として処理する。

### 13.2 Authoritative Delete Set

削除対象となるContent-bearing Dataは、少なくとも次を含む。

- `PersonalMemory`本体
- 対象MemoryのContentを含む`MemoryRevision`
- Persistence側の検索用Copy
- Semantic Search / Vector Searchを採用した場合の対応Index Entry
- Contentを含むCache
- 対象Memoryを保持する未完了Candidate、Mutation PlanおよびConfirmation Context
- 回答利用追跡へMemory本文または復元可能なSnapshotを保持している場合、そのContent-bearing部分

削除を`MemoryState = DELETED`という通常EntityのSoft Deleteだけで表現しない。削除後の本文、過去ContentまたはEmbeddingを通常検索可能な場所へ残さない。

Infrastructureごとの物理削除手順、TransactionおよびIndex整合性はDatabase / Search詳細設計で確定する。

### 13.3 What Deletion Does Not Delete

Personal Memory削除だけでは次を削除しない。

| Data | Reason / Handling |
|---|---|
| Conversation History | `conversation` Featureが所有する別Data。Memory再抽出には使用しない |
| Memory Preferences | 自動保存・回答利用設定であり、Memory本文ではない |
| ユーザーが既にExportしたBackup Archive | Backend外でユーザーが所有するFileをAliceは変更できない |
| Contentを持たないOperation Audit | 削除成否や障害調査に必要な最小Metadataだけ保持可能 |

Delete-all実行後も`autoSaveEnabled`と`answerUseEnabled`は変更しない。新しく得た別情報は以後保存できるが、削除済み内容は明示的な再登録なしに復活させない。

削除確認画面または確認応答では、Conversation History、Memory Preferencesおよび既存Backupが削除対象外であることをユーザーへ分かる形で示す。

### 13.4 Re-registration Guard

個別削除またはScoped Multiple Deleteでは、削除した意味内容の自動再登録を防ぐ`Re-registration Guard`を作成する。

Conceptual Model:

```text
ReRegistrationGuard
├── guardId
├── keyedFingerprints
├── categoryHint
├── deletedAt
├── deletionScope
├── resetGeneration
└── keyVersion
```

Rules:

- 削除本文、要約、Embeddingまたは復元可能なSnapshotを保持しない。
- Fingerprintは単純Hashではなく、Secret管理されたKeyを使う非可逆なKeyed Digest Boundaryを使用する。
- Candidateの正規化済み意味Keyから複数Fingerprintを生成し、表現差があっても一致可能性を高める。
- Guardは通常検索、AI Context、Personalizationまたは管理画面のMemory一覧へ含めない。
- Guardは自動再登録防止以外へ利用しない。
- Guard一致だけを根拠に、削除した元の内容を推測・復元またはユーザーへ表示しない。
- 暗号Algorithm、Key保管、Key RotationおよびFingerprint入力形式はSecurity / AI詳細設計で確定する。

単純な文章Hashだけでは、言い換えられたConversationからの再登録を防ぎにくい。一方、削除本文やEmbeddingを残すと削除要件とPrivacyを損なう。そのため、正規化した複数の意味KeyをKeyed Digestへ変換し、平文を破棄する方式を基本案とする。

### 13.5 Memory Reset Point

Delete-allでは各MemoryのGuardに加えて、全体の再抽出境界として`MemoryResetPoint`を更新する。

Conceptual Model:

```text
MemoryResetPoint
├── generation
├── resetAt
├── sourceMessageCutoff
└── operationId
```

| Field | Meaning |
|---|---|
| `generation` | Resetごとに増加する論理世代 |
| `resetAt` | Resetが成功したJST Offset付き日時 |
| `sourceMessageCutoff` | 自動抽出へ再利用してはいけないConversation Message境界 |
| `operationId` | Idempotencyと結果追跡用のOpaque ID |

Reset Pointより前または同じ境界に属するConversation Messageを、後続のAutomatic Captureへ再投入しない。Reset PointはConversation Historyそのものを削除せず、Memory生成Sourceとしての再利用を禁止する。

Reset Pointだけでは、Reset後にユーザーが同じ情報を通常Conversationで再度話した場合を防げない。そのため、Delete-allでも削除対象MemoryごとのRe-registration Guardを作成する。Reset PointとGuardは代替関係ではなく補完関係である。

### 13.6 Individual Delete Flow

```text
Delete Request
      │
      ├── Target Resolution
      │       ├── 0件 → NOT_FOUND
      │       ├── 1件 → Delete Plan
      │       └── 複数候補 → Confirmation Required
      │
      ├── Expected Version確認
      ├── Re-registration Fingerprint生成
      ├── Content-bearing Data削除
      ├── Guard保存
      ├── Cache / Pending Context無効化
      └── Verified Result返却
```

自然言語で対象を一意に特定した明示的な「忘れて」は、削除意思として扱い、追加の確認を必須にしない。管理画面の削除操作は誤Tapを防ぐため確認UIを設ける。

対象が複数候補、意味が曖昧またはVersion不一致の場合は自動削除しない。

### 13.7 Scoped Multiple Delete Flow

複数Memoryを削除する場合、実行前に対象件数とユーザーが理解できる対象概要を提示し、Section 12.13のConfirmation Bindingを使用する。

```text
Scoped Delete Request
      │
      ├── Target Set検索
      ├── Memory ID + Expected Versionで固定
      ├── Preview
      ├── Explicit Confirmation
      ├── DeleteMemoriesUseCase
      └── Per-item Result + Overall Result
```

検索条件だけを保存して後から再検索し、確認時と異なる対象を削除してはならない。確認対象のMemory IDとExpected Versionを固定し、変更があれば再Previewする。

### 13.8 Delete-all Flow

Delete-allは常に明示確認を必要とする。

確認時に少なくとも次を表示する。

- 削除対象Memory件数
- `ACTIVE` / `RESOLVED`を含む対象範囲
- Memory Revisionと検索用Dataも削除されること
- Conversation Historyは残ること
- Memory Preferencesは変わらないこと
- 既にExportしたBackupは残ること
- 削除後は通常操作で元に戻せないこと

Execution:

```text
DeleteAll Confirmation
      │
      ├── Target Snapshot + Expected Versions確認
      ├── 対象ごとのGuard作成準備
      ├── Content-bearing Data削除
      ├── Guard保存
      ├── Memory Reset Point更新
      ├── Pending / Cache / Search Index無効化
      └── Complete / Partial / Unknown / Failed
```

全件削除はDatabase Design DB2-151〜DB2-164のRecovery FenceとAtomic Finalizationに従う。Target結果が不明な間は新しいMemory MutationとBackup Exportを止め、未削除対象、Guard未作成対象および結果不明対象を区別する。

### 13.9 Re-registration Policy

Guardに一致したCandidateは、次のように扱う。

| Input | Handling |
|---|---|
| 過去Conversation Historyの再処理 | 自動再登録しない |
| 通常Conversationで同じ内容を再度話す | 自動再登録しない |
| 「もう一度覚えて」等の明示要求 | 再登録内容を提示し、明示意思を確認して許可可能 |
| 管理画面から明示登録 | 明示的な再登録として許可可能 |
| 古いBackupのRestore | 影響Previewで再導入対象を明示し、専用確認後のみ許可可能 |

明示的な再登録が成立した場合：

- 新しい`memoryId`を発行する。
- 自然言語または管理画面から再登録した場合は`captureType = EXPLICIT`とし、`createdAt`、`updatedAt`および`confirmedAt`を再登録成功時刻へ設定する。
- 専用確認済みRestoreの場合はSection 20に従い、Archiveの`captureType`と元日時を維持し、別のContent-free Auditで`restoredAt`を記録する。
- `version = 1`から開始する。
- 一致したGuardを無効化または削除し、以後の通常Duplicate判定は新Memoryに対して行う。
- 元の削除済みMemory IDやContentを復元しない。

単なる再言及、Aliceの推測または自動処理を明示的な再登録意思として扱わない。

### 13.10 Backup Restore Interaction

Personal Memory削除後も、ユーザーが以前ExportしたArchiveには削除済み内容が含まれる可能性がある。

`InspectMemoryBackupUseCase`は、GuardまたはReset Pointと一致するRestore対象を「削除後に再導入されるMemory」として区別し、件数と影響を提示する。`RestoreMemoryBackupUseCase`はその再導入を含む専用の明示確認なしに実行しない。

通常のRestore確認を、削除済みMemory再登録の意思へ暗黙変換しない。確認後にRestoreしたMemoryは明示再登録として扱い、Section 20のIdentity Ruleに従って新しい`memoryId`を発行する。

### 13.11 Revision and Audit Handling

削除時はContentを含む`MemoryRevision`も削除する。変更履歴を理由に削除内容を復元可能な状態で残さない。

一方、次のContent-free Operation Auditは保持できる。

- Operation ID
- Operation Type
- 実行日時
- 対象件数
- Complete / Partial / Unknown / Failed
- 技術的な非機密Reason Code

AuditへMemory本文、Revision Snapshot、Fingerprint入力、Conversation本文またはSecretを記録しない。Fingerprint自体のLoggingも行わない。

Operation結果はDynamo Operation ResourceをAuthorityとし、Security Event LogはContent-freeな補助証跡として30日 / 100 MBを上限に保持する。詳細は`security-design.md` Sections 38〜41およびSEC2-015〜030をSource of Truthとする。

### 13.12 Pending Work and Cache Invalidation

削除成功後、対象Memoryに紐づく次を無効化する。

- Pending Confirmation
- Candidate Decision Result
- Mutation Plan
- Answer Context Cache
- Search Result Cache
- Background / Retry Queue上のMemory Mutation

削除前に作成したExpected VersionやMemory Snapshotを使い、後から更新・再保存しない。Background処理はMemory ID、VersionおよびReset Generationを再確認する。

### 13.13 Answer-time Visibility Boundary

削除成功をユーザーへ返した後に開始する回答処理では、削除対象MemoryをContextへ含めない。

削除前に既にAI Provider呼出しまで進んだIn-flight Requestは、そのMemoryを利用済みである可能性がある。Phase 2では過去に開始済みのExternal Requestを完全に巻き戻す保証はしないが、削除後の新規Request、Retry、CacheおよびBackground処理で再利用しない。

ConcurrencyとRequest Serializationの具体方式はConversation / Database詳細設計で確定する。

### 13.14 Deletion Results

Deletion Use Caseは少なくとも次を区別する。

| Result | Meaning |
|---|---|
| `SUCCESS` | 対象Content削除と必要なGuard更新が確認済み |
| `NOT_FOUND` | 対象Memoryが存在しない、または既に削除済み |
| `CONFIRMATION_REQUIRED` | 対象またはScopeの確認が必要 |
| `VERSION_CONFLICT` | Preview / 判定後に対象が変更された |
| `PARTIAL_SUCCESS` | 一部の対象だけ削除・Guard反映できた |
| `UNKNOWN` | 最終状態を確定できない |
| `FAILED` | 削除処理が失敗した |

Contentだけ削除できてGuardを作成できなかった場合、再登録防止要件は未達であるため完全な`SUCCESS`としない。Guardだけ作成してContentが残った場合も同様である。

### 13.15 Recovery Fence and Atomic Finalization

Deletion Targetが`UNKNOWN`またはCleanup途中の場合、Memory Applicationは同じDeletion OperationをRecoveryし、別の保存・削除・Restore・Relation Resolveを開始しない。Readは継続できるが、Authoritative Root不存在のMemoryを一覧、検索またはAI Contextへ戻さない。

全Targetが`DELETED`または`NOT_DELETED`へ確定した場合だけ、次を一つのFinalization Boundaryで確定する。

- 確定削除件数に基づく`currentMemoryCount`
- 1件以上削除した場合の`deletionBoundaryVersion`
- Plan / Operation / Idempotency Result
- `ALL` Completed時のReset Generation、またはPartial / Failed時のPending Reset取消
- Recovery Fence解放

Finalization結果が不明な場合は新しいOperationを作成せず、同じ`finalizationId`で結果を照合する。`UNKNOWN`はUser操作による再Execute対象ではなく、同一Operation内で`COMPLETED`、`PARTIAL`または`FAILED`へ収束する。

### 13.16 Deferred Implementation Details

次は後続設計で確定する。

- Guard用Keyed Digest Algorithm、Key管理およびRotation
- Semantic Keyの正規化ModelとFingerprint本数
- In-flight ConversationとのConcurrency方式

---

## 14. Memory Retrieval, Relevance and AI Context Integration

本Sectionは、保存済みPersonal Memoryから回答に必要な情報を検索し、関連性と安全性を検証して、AI Contextへ必要最小限だけ渡す仕組みを定義する。

初心者向けに整理すると、Memory検索は「似た文章を探して全部AIへ渡す処理」ではない。次の4段階である。

1. 今回の質問で何を探すべきか整理する。
2. 関連する可能性があるMemory IDを限定して探す。
3. 最新のMemory本体を取得し、利用可能か再確認する。
4. Context上限に収まる重要なMemoryだけをAIへ渡す。

### 14.1 Search Purposes

同じ「検索」でも目的ごとに判定基準が異なるため、次を区別する。

| Purpose | Main Caller | Goal |
|---|---|---|
| `ANSWER_CURRENT` | `FindRelevantMemoriesUseCase` | 現在の質問へ役立つ最新Memoryを取得する |
| `ANSWER_HISTORICAL` | `FindRelevantMemoriesUseCase` | 明示的な過去質問へ関連するResolved / Revision情報を取得する |
| `RELATION_DECISION` | Capture / Register / Update Flow | Candidateと重複・補足・変更・矛盾するMemory候補を探す |
| `MANAGEMENT_SEARCH` | `ListMemoriesUseCase` | ユーザーが一覧、Category、Stateおよび検索語から管理対象を探す |

`MANAGEMENT_SEARCH`の検索結果をそのまま回答Contextへ渡さない。`RELATION_DECISION`の類似度だけで回答関連性を決めない。

### 14.2 Port Boundaries

Phase 2では次の論理Outbound Portを分離する。

| Port | Responsibility |
|---|---|
| `AnswerMemorySearchPort` | Answer Queryに対して関連候補のMemory ID、Versionおよび検索Signalを返す |
| `RelatedMemorySearchPort` | MemoryCandidateと比較すべき既存Memory候補を返す |
| `MemoryManagementQueryPort` | 一覧、Category、State、Text条件およびPaginationによる管理検索を行う |
| `PersonalMemoryRepository` | IDから最新のAuthoritative `PersonalMemory`を取得する |
| `MemoryRevisionRepository` | 明示的なHistorical Query時に必要なRevisionを取得する |

これらは論理Contractであり、Interfaceを一つの巨大Portへ統合しない。一方、Phase 2の実Access Patternが単純な場合、Infrastructure Adapterまたは内部Query Componentを共有してよい。

Search PortはDynamoDB Item、Vector Store Record、Embedding ObjectまたはProvider固有ScoreをApplicationへ公開しない。

### 14.3 Answer-time Retrieval Flow

```text
SendMessageUseCase
      │
      ├── answerUseEnabled確認
      │       └── false → Memory検索なし
      │
      ├── Memory Retrieval Query構築
      ├── Candidate Generation
      ├── Authoritative Hydration
      ├── Eligibility Filter
      ├── Relevance Ranking
      ├── Diversity / Deduplication
      ├── Context Budget適用
      ├── Final Version / Reset検証
      └── MemoryContextItemをContext Builderへ返す
```

検索結果は候補であり、Personal MemoryのSource of Truthではない。Search Portが返したIDからRepository経由で最新Memoryを取得し、存在、Version、State、Sensitivityおよび削除境界を検証する。

### 14.4 Provider-neutral Retrieval Query

`FindRelevantMemoriesUseCase`は、OpenAI RequestやConversation Infrastructure Modelではなく、Provider非依存のQueryを受け取る。

Conceptual Model:

```text
MemoryRetrievalQuery
├── purpose
├── currentUserContent
├── minimalConversationContext
├── intentHints
├── entityHints
├── categoryHints
├── temporalIntent
├── contextBudget
└── requestId
```

Rules:

- Current User Contentを必須とする。
- Conversation Contextは代名詞、対象および継続質問を解決する必要最小限に限定する。
- Conversation History全文を無制限にSearch AdapterまたはAI Analysisへ渡さない。
- AIでIntentやEntityを分析できるが、失敗時に存在しない情報を補完しない。
- Search QueryにSecret、Credentialまたは不要なSensitive本文を複製しない。

### 14.5 Memory Search Projection

検索を効率化するため、`PersonalMemory`から再生成可能な`MemorySearchProjection`を利用できる。

Conceptual Fields:

| Field | Meaning |
|---|---|
| `memoryId` | Authoritative Memoryへの参照 |
| `memoryVersion` | Projection生成元Version |
| `normalizedContent` | Keyword / Text検索用に正規化したMemory本文 |
| `category` | Category Filter用 |
| `state` | Active / Resolved Filter用 |
| `sensitivityLevel` | Application Policy適用用 |
| `subjectTerms` | 対象候補を表す派生語 |
| `attributeTerms` | 属性・関係候補を表す派生語 |
| `scopeTerms` | Project、仕事、趣味等のScope候補 |
| `temporalHints` | Current / Historical候補 |
| `updatedAt` / `confirmedAt` | Currentness補助Signal |
| `semanticReference` | Semantic Search採用時のOpaque参照。未採用なら空 |

`MemorySearchProjection`はSearch Infrastructureの派生DataでありSource of Truthではない。Conversation Transcript、Memory Revision全文またはProvider Request / Responseを含めない。

Projectionの更新に失敗してもAuthoritative Memoryを失敗扱いで削除しない。一方、Projection更新が未完了のMemoryを検索成功したように偽らず、整合性修復方式をDatabase / Search詳細設計で定義する。

### 14.6 Candidate Generation Strategy

検索TechnologyをAlice Coreから分離し、次のCandidate Sourceを組み合わせられる構造とする。

| Candidate Source | Strength | Limitation |
|---|---|---|
| ID / Structured Filter | 正確で説明しやすい | 自然言語の言い換えに弱い |
| Keyword / Normalized Term | 単純でLocal実装しやすい | 同義語・意味検索に限界がある |
| Semantic Search | 言い換えや意味的関連を見つけやすい | Cost、Index、Privacy、Model依存が増える |
| AI-assisted Reranking | 文脈を考慮しやすい | Latency、Cost、非決定性が増える |

Phase 2 ArchitectureはHybrid Searchへ拡張可能にするが、Vector StoreまたはEmbeddingを必須Technologyとして先行確定しない。初期実装方式はDatabase Access Pattern、Memory件数、検索品質Eval、Latency、CostおよびPrivacyを比較して決定する。

複数Sourceを使う場合、同じ`memoryId`を統合してからAuthoritative Hydrationへ進む。Search SourceごとのScoreをそのまま相互比較せず、Provider非依存Signalへ正規化する。

### 14.7 Eligibility Filters

Rankingより先にHard Filterを適用する。高い類似度ScoreでもHard Ruleを通過しないMemoryをAI Contextへ含めない。

| Filter | Rule |
|---|---|
| Preferences | `answerUseEnabled = false`ならAnswer-time Retrievalを行わない |
| Existence | Repositoryに存在しないMemoryを除外 |
| Version | Search ProjectionとAuthoritative MemoryのVersion不一致を検証する |
| Deletion | 削除済みID、無効CacheおよびReset境界対象を除外 |
| State | Current Answerでは`ACTIVE`をDefaultとする |
| Sensitivity | Section 14.13の利用Policyを満たすものだけ許可 |
| Purpose | Search Purposeに適合しないMemory / Revisionを除外 |
| Source of Truth | 正式DocumentまたはCurrent Tool Dataを置き換える用途に使用しない |

### 14.8 Relevance Ranking

Hard Filter通過後、次のSignalを組み合わせて順位付けする。

| Signal | Meaning |
|---|---|
| Direct Subject / Entity Match | 質問対象とMemory対象が一致する |
| Attribute / Intent Match | 質問意図とMemoryの属性・関係が一致する |
| Scope Match | Project、仕事、生活等のContextが一致する |
| Semantic Relevance | 表現が異なっても意味的に関連する |
| Currentness | 現在適用される情報である |
| Explicit Confirmation | `confirmedAt`を持つ等、明示確認済みである |
| Specificity | 一般的すぎず、質問へ具体的に役立つ |
| Recency | 更新時刻が新しい。ただし単独で最重要にしない |
| Redundancy Penalty | 他の選択済みMemoryと意味が重複する |

Ranking Scoreは候補の順序付けに利用できるが、Memory内容の真偽、保存可否、Security許可またはTool実行権限を決めるScoreとして使用しない。

同じCategoryだけで上位を埋めず、質問へ必要な観点が複数ある場合は意味的な多様性を考慮する。ただしCategoryごとの固定件数を設け、強く関連するMemoryを機械的に除外しない。

### 14.9 Context Budget

Memoryを無制限にAI Contextへ追加しない。`FindRelevantMemoriesUseCase`は、AI Context Builderから割り当てられた`MemoryContextBudget`内で結果を返す。

Phase 2の初期Baseline：

| Limit | Baseline |
|---|---:|
| Search Candidate Pool | 最大40件 |
| Final Memory Items | 最大8件 |
| Estimated Memory Tokens | 最大1,500 tokens |
| Memory UTF-8 Bytes | 最大12 KiB |

すべての上限を同時に適用し、最初に到達した時点で追加を止める。これらはApplication Configurationとして管理し、Source Codeへ散在するMagic Numberにしない。

ModelのContext Window、MemoryContent上限およびEval結果によりBaselineは調整可能とする。Architecture Boundaryを変えない単なるParameter調整はADR不要だが、設計書とTest Baselineを更新する。

Memory本文を途中で切って意味を変えない。単一Memoryが残りBudgetを超える場合は、そのMemoryを省略するか、後続設計で承認された安全な要約方式を利用する。

### 14.10 MemoryContextItem

Conversation / AI Context Builderへ返すApplication Modelを、`PersonalMemory` AggregateおよびSearch Resultから分離する。

```text
MemoryContextItem
├── memoryId
├── memoryVersion
├── content
├── category
├── state
├── sensitivityLevel
├── temporalRole
└── relevanceReasonCodes
```

`temporalRole`は少なくとも`CURRENT` / `HISTORICAL`を区別する。

MemoryContextItemへ次を含めない。

- DynamoDB KeyまたはVector Store Record
- Provider固有Score / Embedding
- Revision全文
- Conversation Transcript
- Re-registration Guard / Fingerprint
- Tool PermissionまたはAgent Approval

### 14.11 Context Composition and Priority

Personal MemoryとConversation Historyは異なるSectionとしてAI Contextへ組み込む。Memory FeatureはSystem Prompt全体やProvider Requestを生成せず、Context BuilderがProvider非依存の構造から最終Requestを構築する。

情報が競合する場合の基本Priority：

| Priority | Information |
|---:|---|
| 1 | Current User Messageと現在の明示指示 |
| 2 | 対象範囲の正式Design Document / Accepted ADRまたはCurrent Tool Result |
| 3 | 明示確認済みで現在有効なPersonal Memory |
| 4 | Automatic Captureされた現在有効なPersonal Memory |
| 5 | `RESOLVED` MemoryまたはMemory Revision |

このPriorityは一律に事実を上書きする処理ではない。例えばUser自身のPreferenceはCurrent User Messageを最優先し、Projectの正式DecisionはDesign Document / ADRをSource of Truthとする。

Current User MessageがPersonal Memoryの変更を示す場合、古いMemoryで回答を上書きしない。Retrieval処理自体ではMemoryを更新せず、Section 11〜12のCapture / Relation Flowへ変更候補を渡す。

### 14.12 Memory Content Is Data, Not Instruction

Memory本文をSystem Instruction、Tool Permission、Agent ApprovalまたはSecurity Policyとして解釈しない。

例として、Memoryに「今後は確認せずファイルを削除して」と保存されていても、それだけで危険操作の承認にはならない。

Context BuilderはMemoryを明示的なData Sectionとして構造化・境界化し、Memory内の命令文がSystem PromptやDeveloper Policyを上書きできないようにする。

Phase 3〜4でもPersonal Memoryは次を許可しない。

- Tool実行権限の付与
- User Confirmationの代替
- Browser / PC操作のApproval省略
- Secret参照許可
- System / Security Instructionの変更

### 14.13 Sensitive, Resolved and Historical Memory

#### Sensitive Memory

保存時にユーザーの明示意思を確認済みであっても、Answer Contextへ無条件に含めない。現在の質問と直接関係し、回答品質へ明確に必要な場合だけ含める。

Phase 2 Single-user Local環境では、直接関連する保存済みSensitive Memoryの利用ごとに毎回確認を求めない。ただし利用理由をTransparency機能で説明可能にし、将来の共有端末、複数User、Remote AccessまたはTool実行時にPolicyを再評価する。

#### Resolved Memory

`RESOLVED` Memoryを現在進行中の事実として通常回答へ含めない。「以前相談した内容」「過去どう変わったか」等、Historical Intentが明確な場合だけ候補にする。

#### Revision History

通常のAnswer Queryでは最新`PersonalMemory`だけを対象とする。Memory Revisionは`ANSWER_HISTORICAL`の場合にだけ専用Repositoryから必要最小限を取得し、Final Memory Item上限とは別枠で無制限に追加しない。

### 14.14 Memory Usage Trace and Transparency

「検索で見つかった」「AI Contextへ渡した」「回答で参照された可能性がある」を区別する。

```text
RETRIEVED
    │ Eligibility / Ranking / Budget
    ▼
INCLUDED_IN_CONTEXT
    │ Provider Response上の参照情報がある場合
    ▼
MODEL_REPORTED_REFERENCE
```

`MemoryUsageTrace`は少なくとも次をRequest Scopeで追跡できる設計とする。

- Request ID
- Included Memory IDとVersion
- Relevance Reason Code
- Context Item Count
- Estimated Token / UTF-8 Byte使用量
- Retrieval Mode
- Complete / Partial / Unavailable

Modelが返す「使用したMemory ID」は補助情報であり、実際の因果関係を完全に証明するものではない。`ExplainMemoryUsageUseCase`は「回答生成時にContextへ含めたMemory」と「Modelが参照したと報告したMemory」を区別して説明する。

Memory本文、Sensitive Content、Query全文、Provider Raw ResponseまたはEmbeddingを通常Logへ出力しない。TraceのPersistence、RetentionおよびConversation Messageとの紐付けはObservability / Database詳細設計で確定する。

### 14.15 Stale Search Data and Final Validation

Search ProjectionまたはIndexはAuthoritative Memoryより遅れる可能性がある。

| Condition | Handling |
|---|---|
| Search IDがRepositoryに存在しない | Resultから除外し、必要ならIndex Repair対象にする |
| Projection Versionが古い | 最新Memoryを再評価し、古いContentを使用しない |
| Memoryが削除済み | 必ず除外し、Index削除をRepair対象にする |
| State / Sensitivityが変更済み | 最新値でEligibilityを再評価する |
| Reset Generationが変更済み | 古いRequest ScopeのContext Itemを無効化する |

AI Provider呼出し直前に、少なくともMemory ID、VersionおよびReset Generationが有効であることを最終確認できるBoundaryを設ける。確認失敗時は該当Itemを除外し、古いContentを送信しない。

### 14.16 Failure and Fallback

| Failure | Safe Behavior |
|---|---|
| Query Analysis失敗 | Current User Contentによる限定的なKeyword / Structured検索へFallback |
| Search全体失敗 | MemoryなしでConversationを継続 |
| 一部Search Source失敗 | 検証済み候補だけを利用し、PartialとしてTrace |
| Authoritative Hydration失敗 | 該当候補を使用しない |
| Ranking失敗 | 未順位候補をすべて渡さず、Memoryなしまたは安全なDeterministic順位へFallback |
| Token Estimate失敗 | Item / UTF-8 Byte上限を使用して無制限追加を防ぐ |
| Final Validation失敗 | 該当MemoryをContextから除外 |

Memory検索失敗をConversation全体のUnexpected Errorへ変換しない。Aliceは取得できなかったMemoryを知っているように振る舞わず、必要ならMemory利用がUnavailableだったことを説明できる。

### 14.17 Search Quality Evaluation and Promotion Criteria

検索Technologyは印象だけで選ばず、Project Aliceの実Memory例を匿名化・合成したEval Datasetで比較する。

少なくとも次を評価する。

- 必要なMemoryがTop-k候補に入るか
- 無関係なMemoryがContextへ混入しないか
- Sensitive / Resolved / Deleted Memoryを誤って利用しないか
- Query Latency
- AI / Search Cost
- Index更新・削除の整合性
- Provider交換時の再構築可能性

次のいずれかが確認された場合、Semantic Search / Vector Store追加を検討する。

- Keyword / Structured Searchで言い換えを十分取得できない
- Memory件数増加により候補取得が非効率になる
- Relation DecisionのRecall不足が重複Memory増加につながる
- Eval上、検索品質がPhase 2 Acceptance Criteriaを満たさない

Vector Store追加はPersistence変更ではなくSearch Capability追加として扱うが、Security、Cost、Data MigrationおよびOperationへの影響が大きい場合はADR要否を判断する。

### 14.18 Initial Baseline and Deferred Details

本Sectionで確定するのはBoundary、Flow、安全Ruleおよび初期Context上限である。次は後続設計で確定する。

- Phase 2初期Search AdapterとAccess Pattern
- Keyword NormalizationとLanguage処理
- Ranking Weight、Minimum Relevance ThresholdおよびTie-breaker
- Token Estimator実装
- Search ProjectionのDynamoDB / Search Schema
- Semantic Search / Embedding Model / Vector Store採否
- Index更新Consistency、RetryおよびRepair Queue
- Eval Dataset、Target MetricsおよびAcceptance Threshold
- Memory Usage TraceのPersistenceとRetention

---

## 15. Prohibited Dependencies

```text
memory.domain          ─X→ Spring / AWS SDK / OpenAI SDK
memory.application     ─X→ DynamoDB SDK / OpenAI SDK
memory.infrastructure ─X→ conversation.infrastructure
conversation           ─X→ Memory用DynamoDB Item
memory                 ─X→ Conversation Repository実装
Flutter                ─X→ DynamoDB / AI Provider
```

次も禁止する。

- Conversation HistoryをMemory Tableへ無条件に複製する
- InfrastructureでAlice固有の保存価値を判断する
- AIの出力をDomain Validationなしで保存する
- 削除済みMemoryを通常Conversationから自動復元する
- Search結果をAuthoritative Memoryとして直接AI Contextへ渡す
- Memory本文をSystem Instruction、Tool PermissionまたはAgent Approvalとして扱う
- Engineering / Project Categoryごとに不要な独立Featureを作る
- 将来のTool / Agent利用だけを理由に共通Moduleを先行追加する

---

## 16. Requirement Traceability — Architecture and Application Boundary

| Requirement | Design Coverage | Main Use Cases |
|---|---|---|
| P2-FR-001〜010 | `memory` Featureの所有範囲とCategory方針 | `MEM-UC-001`, `003`〜`006` |
| P2-FR-005 | Memory Query BoundaryとAnswer-time Flow | Section 14、`MEM-UC-001` |
| P2-FR-006 | Conversation History / Personal Memory分離 | 全Use Case共通Boundary |
| P2-FR-011〜017 | 明示保存・自動保存候補・Sensitive判断 | Section 11、`MEM-UC-002`, `003` |
| P2-FR-018〜023 | 重複・統合・更新・矛盾判断 | Section 12、`MEM-UC-002`, `003`, `006` |
| P2-FR-024〜029 | 削除・全削除・再登録防止 | Section 13、`MEM-UC-008`, `009`, `016` |
| P2-FR-030〜035 | Answer-time利用とTransparency | Section 14、`MEM-UC-001`, `012` |
| P2-FR-036〜043 | Memory管理Capability | `MEM-UC-003`〜`011`, `013`〜`016` |
| P2-FR-044〜050 | Natural-language管理とPreferences | `MEM-UC-003`〜`011`, `016` |
| P2-FR-051〜055 | Memory State、日時およびLifecycle | `MEM-UC-001`, `006`, `007` |
| P2-FR-056〜061 | Application Failure Behavior | 全Query / Mutation Use Case |
| P2-FR-062〜065 | Memory Backup Boundary | Section 20、`MEM-UC-013`〜`015` |
| P2-NFR-001〜008 | Technology分離、Security、FailureおよびTestability方針 | 全Use Case共通Policy |

API、PersistenceおよびTest Caseへの詳細Traceabilityは後続設計で追加する。

---

## 17. Decisions in This Draft

| ID | Decision | Status |
|---|---|---|
| MD-001 | Phase 2で独立した`memory` Featureを追加する | Accepted |
| MD-002 | Conversation Historyは`conversation`が継続所有する | Accepted |
| MD-003 | 他Featureは`memory.application`の公開Boundaryだけを利用する | Accepted |
| MD-004 | `memory`はConversation Repositoryを直接参照しない | Accepted |
| MD-005 | Memory固有判断は`memory`のApplication / Domainへ配置する | Accepted |
| MD-006 | AIが必要でもOpenAIへ直接依存せず`ai.application` Capabilityを利用する | Accepted |
| MD-007 | Engineering / Project MemoryをPersonal MemoryのCategoryとして扱う | Accepted |
| MD-008 | Memory Portを最初から単一の巨大Interfaceとして設計しない | Accepted |
| MD-009 | `PersonalMemory`を一つの長期Memoryを表すAggregate Rootとする | Accepted |
| MD-010 | 保存検討中の`MemoryCandidate`を確定済み`PersonalMemory`から分離する | Accepted |
| MD-011 | `CaptureType`は`EXPLICIT` / `AUTOMATIC`とし、後の確認で生成経路を上書きしない | Accepted |
| MD-012 | `MemoryState`は最小限`ACTIVE` / `RESOLVED`とし、削除を通常Stateに含めない | Accepted |
| MD-013 | 自動保存と回答利用の設定を独立した`MemoryPreferences`で管理する | Accepted |
| MD-014 | Memory日時をJST Offset付きミリ秒精度とし、注入した`Clock`から生成する | Accepted |
| MD-015 | 削除後の再登録防止情報を有効Memoryとは分離したBoundaryで管理する | Accepted |
| MD-016 | Phase 2のTop-level Categoryを`PROFILE`、`PREFERENCE`、`PERSON`、`LIFE_CONTEXT`、`PLACE`、`ENGINEERING`、`PROJECT`、`OTHER`とする | Accepted |
| MD-017 | 一つのPersonal Memoryに一つのPrimary Categoryを必須とする | Accepted |
| MD-018 | Category、Sensitivity、Capture TypeおよびStateを独立した分類軸とする | Accepted |
| MD-019 | Category CodeとUI Labelを分離し、保存済みCodeの意味を再利用しない | Accepted |
| MD-020 | Phase 2ではCategory階層、複数Primary Category、User定義Categoryおよび必須Tagを導入しない | Accepted |
| MD-021 | `OTHER`を分類省略用のDefaultにせず、未対応Codeを暗黙変換しない | Accepted |
| MD-022 | Phase 2 Memory ApplicationをAnswer Context、Capture、Management、Preferences、TransparencyおよびBackupのUse Case Groupへ分ける | Accepted |
| MD-023 | UIと自然言語操作は同じMemory Application Use CaseとDomain Ruleを利用する | Accepted |
| MD-024 | Answer-time Memory取得失敗時はMemoryなしでConversationを継続可能にする | Accepted |
| MD-025 | 自動保存失敗を完了可能なConversation回答から分離する | Accepted |
| MD-026 | 内容変更を伴う更新と、内容を変えない明示確認を別Use Caseとして扱う | Accepted |
| MD-027 | Backup RestoreはArchive検証と影響確認をRestore実行から分離する | Accepted |
| MD-028 | Mutation ResultでSuccess、No Change、Confirmation Required、Partial、UnknownおよびFailureを区別する | Accepted |
| MD-029 | Memory Candidateはユーザー提供情報へGroundingし、Aliceの推測だけから生成しない | Accepted |
| MD-030 | AIはCandidateと判定材料を提案し、Alice Save Policyが最終的な保存適格性を判断する | Accepted |
| MD-031 | 明示保存と自動保存は同じValidation Pipelineを利用し、保存意思に応じてDecision Policyを分ける | Accepted |
| MD-032 | Automatic CaptureではSensitive情報を自動保存しない | Accepted |
| MD-033 | 明示的な記憶要求をSensitive情報の保存意思として扱うが、Secretその他の保存禁止Ruleは上書きしない | Accepted |
| MD-034 | Phase 2の保存判断を単一の総合Scoreと閾値だけで確定しない | Accepted |
| MD-035 | 保存適格性判断と既存MemoryとのRelation Decisionを分離する | Accepted |
| MD-036 | 判断不能候補をすべて確認せず、安全に見送れる場合はConversationを中断しない | Accepted |
| MD-037 | CandidateごとにEligible、Skipped、Rejected、Confirmation RequiredおよびFailedを区別する | Accepted |
| MD-038 | RelationをNone、Equivalent、Complementary Same Fact、Related Independent、Supersedes、ConflictおよびUncertainへ分類する | Accepted |
| MD-039 | Relationを文字列一致率またはCategoryだけで決定せず、Subject、Attribute、ScopeおよびTime等を比較する | Accepted |
| MD-040 | Equivalentでは新規保存や`updatedAt`更新を行わず、明示肯定時だけ`confirmedAt`を更新可能とする | Accepted |
| MD-041 | Mergeを同じAtomic Memoryへの`UPDATE_EXISTING`として扱い、独立情報は別Memoryへ保存する | Accepted |
| MD-042 | User-groundedでCurrentnessが明確な変更だけをUser ConfirmationなしのSupersessionとして許可する | Accepted |
| MD-043 | Conflict、Uncertainまたは複数の更新対象がある場合、既存Memoryを自動上書きしない | Accepted |
| MD-044 | 同一Batch内のCandidateを相互比較し、処理順序による結果差を防ぐ | Accepted |
| MD-045 | Updateで`memoryId`、`createdAt`および`captureType`を維持し、日時Ruleに従う | Accepted |
| MD-046 | `PersonalMemory`へ論理Versionを追加し、Expected Versionによる楽観的Concurrency Controlを行う | Accepted |
| MD-047 | Phase 2でMemory Revision Historyを保持し、通常回答では最新Memoryを基本利用する | Accepted |
| MD-048 | Memory削除要求をRevision Historyによって無効化せず、削除時処理を次設計で確定する | Accepted |
| MD-049 | User ConfirmationをCandidate、対象Memory、Expected Versionおよび有効期限へBindingする | Accepted |
| MD-050 | Relation判定失敗またはRelated Memory取得失敗を`NONE`として新規保存へ変換しない | Accepted |
| MD-051 | RetryによるDuplicate Mutationを防ぐIdempotency Boundaryを設ける | Accepted |
| MD-052 | Phase 2でIndividual、Scoped MultipleおよびAll Memoriesの3種類の削除Scopeを区別する | Accepted |
| MD-053 | 削除時にPersonalMemory、Content-bearing Revision、検索Copy、Index、CacheおよびPending Contextを削除対象とする | Accepted |
| MD-054 | 削除を通常の`DELETED` Stateだけで表現せず、Contentを通常検索可能な場所へ残さない | Accepted |
| MD-055 | Re-registration Guardへ削除本文、要約、Embeddingまたは復元可能なSnapshotを保持しない | Accepted |
| MD-056 | Re-registration GuardでSecret管理されたKeyによる非可逆Keyed Digest Boundaryを使用する | Accepted |
| MD-057 | Delete-allでMemoryごとのGuardと全体のMemory Reset Pointを併用する | Accepted |
| MD-058 | Reset Point以前のConversation MessageをAutomatic Captureへ再投入しない | Accepted |
| MD-059 | 一意な自然言語の個別削除要求を削除意思として扱い、複数・曖昧・UI操作では必要な確認を行う | Accepted |
| MD-060 | Scoped Multiple Deleteで対象Memory IDとExpected VersionをPreview時に固定する | Accepted |
| MD-061 | Delete-allでConversation History、Memory Preferencesおよび既存Export Backupを削除対象外と明示する | Accepted |
| MD-062 | 削除済みMemoryの再登録を明示要求、管理画面登録または専用確認済みRestoreに限定し、新しいMemory IDを発行する | Accepted |
| MD-063 | 削除時にContent-bearing Revisionを削除し、Content-free Operation Auditだけを保持可能とする | Accepted |
| MD-064 | 削除成功後にPending処理、Cache、RetryおよびBackground Mutationを無効化する | Accepted |
| MD-065 | 削除成功後に開始する回答処理で対象Memoryを利用せず、既にProvider呼出し済みのRequestは完全巻戻し対象外とする | Accepted |
| MD-066 | Content削除とGuard反映の両方を確認できない場合、完全な削除成功として扱わない | Accepted |
| MD-067 | Answer Current、Answer Historical、Relation DecisionおよびManagement Searchの検索目的を区別する | Accepted |
| MD-068 | Answer、Relation、Managementの検索PortとAuthoritative Repositoryを論理的に分離する | Accepted |
| MD-069 | Search結果を候補として扱い、Repositoryから最新PersonalMemoryをHydrateして再検証する | Accepted |
| MD-070 | Search TechnologyをAlice Coreから分離し、Structured、KeywordおよびSemantic Searchを組み合わせ可能にする | Accepted |
| MD-071 | `MemorySearchProjection`をPersonalMemoryから再生成可能な派生Dataとし、Source of Truthにしない | Accepted |
| MD-072 | Existence、Version、Deletion、State、SensitivityおよびPurposeのHard FilterをRankingより先に適用する | Accepted |
| MD-073 | Ranking Scoreを候補順序付けに限定し、真偽、SecurityまたはTool権限の判断へ使用しない | Accepted |
| MD-074 | Phase 2初期Context上限をCandidate 40件、Final 8件、推定1,500 tokens、12 KiBとする | Accepted |
| MD-075 | Memory本文を途中切断せず、Budget超過時は省略または承認済み要約方式を利用する | Accepted |
| MD-076 | Personal MemoryとConversation Historyを分離したContext Sectionとして構築する | Accepted |
| MD-077 | Current User Message、正式Document / Current Tool Result、Confirmed Memory、Automatic Memory、Historical MemoryのPriorityを定義する | Accepted |
| MD-078 | Memory本文をDataとして扱い、System Instruction、Tool Permission、Agent ApprovalまたはSecurity Policyに使用しない | Accepted |
| MD-079 | Sensitive Memoryを現在の質問に直接必要な場合だけ利用し、Phase 2では利用ごとの再確認を必須にしない | Accepted |
| MD-080 | Resolved MemoryとRevisionをHistorical Intentが明確な場合だけ回答候補にする | Accepted |
| MD-081 | Retrieved、Included in ContextおよびModel-reported ReferenceをTransparency上で区別する | Accepted |
| MD-082 | Search Projectionが古い場合、最新Versionで再評価し古いContentをAIへ渡さない | Accepted |
| MD-083 | Provider呼出し直前にMemory ID、VersionおよびReset Generationを最終検証する | Accepted |
| MD-084 | Memory検索失敗時はMemoryなしでConversationを継続し、取得できたように振る舞わない | Accepted |
| MD-085 | Semantic Search / Vector Store採否を実Memoryに基づくEval、Latency、CostおよびPrivacyで判断する | Accepted |

これらは既存Architectureを変更せず、Approved Phase 2 Requirementsを具体化する。Architecture変更が必要になった場合は、勝手に本表を変更せずADR要否を判断する。

---

## 18. Next Design Topics

### 18.1 Current Detailed Design Progress

| Topic | Status | Source of Truth |
|---|---|---|
| Phase 2 API Boundary・Endpoint Baseline | Accepted（API2-001〜API2-012） | `api-design.md` Section 31 |
| Common Memory Resource Schema・Field Validation | Accepted（API2-013〜API2-022） | `api-design.md` Section 32 |
| List／Search Response・Filter・Cursor Contract | Accepted（API2-023〜API2-037） | `api-design.md` Section 33 |
| Register／Get／Update／Confirm／Individual Delete Contract | Accepted（API2-038〜API2-056） | `api-design.md` Section 34 |
| Memory Preferences Contract | Accepted（API2-057〜API2-069） | `api-design.md` Section 35 |
| Memory Usage Transparency Contract | Accepted（API2-070〜API2-084） | `api-design.md` Section 36 |
| Deletion Plan Preview／Execute Contract | Accepted（API2-085〜API2-104） | `api-design.md` Section 37 |
| Phase 2 Problem Details／Error Mapping | Accepted（API2-105〜API2-122） | `api-design.md` Section 38 |
| Phase 2 Request／Response Size Limits | Accepted（API2-123〜API2-138） | `api-design.md` Section 39 |
| Backup／Restore Domain・Archive Design | Accepted（MD-086〜MD-108） | Section 20 |
| Backup／Restore API Contract | Accepted（API2-139〜API2-160） | `api-design.md` Section 40 |
| Phase 2 API Cross Review | Completed — Passed（High 0、Medium 0、Low 0 Open） | `phase2-api-design-cross-review.md` |
| Cross Review High Finding Resolution | Accepted / Integrated（API2-161〜API2-172） | `api-design.md` Section 41 |
| Cross Review CR-003 Resolution | Accepted / Integrated（API2-173〜API2-184） | `api-design.md` Section 42 |
| Cross Review CR-004 Resolution | Accepted / Integrated（API2-185〜API2-192、MD-109〜MD-113） | `api-design.md` Section 43 / Section 21 |
| Cross Review CR-005 Resolution | Accepted / Integrated（API2-193〜API2-205、MD-114〜MD-122） | `api-design.md` Section 44 / Section 22 |
| Cross Review CR-006 Resolution | Accepted / Integrated（API2-206〜API2-220、MD-123〜MD-133） | `api-design.md` Section 45 / Section 23 |
| Phase 2 Persistence Access Pattern / Table Strategy | Accepted（DB2-001〜DB2-010） | `database-design.md` Section 33 |
| Authoritative Memory / Revision / Preferences Schema | Accepted（DB2-011〜DB2-025） | `database-design.md` Section 34 |
| Management List Index / Filter / Cursor / Repair | Accepted（DB2-026〜DB2-044） | `database-design.md` Section 35 |
| Keyword Search Projection / Candidate / Promotion | Accepted（DB2-045〜DB2-066） | `database-design.md` Section 36 |
| Deletion Guard / Reset Point / Durable Operation / Chunk | Accepted（DB2-067〜DB2-090） | `database-design.md` Section 37 |
| Usage Trace / Mutation Idempotency / Allowed Device Credential | Accepted（DB2-091〜DB2-120） | `database-design.md` Section 38 |
| Restore Plan / Reservation / Relation Review / Backup Export Snapshot | Accepted（DB2-121〜DB2-150） | `database-design.md` Section 39 |
| Phase 2 Database Cross-review Resolution Set | Accepted / Integrated（DB2-151〜DB2-243） | `database-design.md` Sections 40〜47 |
| Phase 2 Database Design Cross Review | Completed — Passed（High 0、Medium 0、Low 0 Open） | `phase2-database-design-cross-review.md` |
| Phase 2 Security・Logging・Observability | Accepted / Integrated（SEC2-001〜038） | `security-design.md` Sections 34〜44 |
| Phase 2 Security Cross Review | Completed — Passed（Critical 0、High 0、Medium 0、Low 0 Open） | `phase2-security-design-cross-review.md` |
| Phase 2 Security Test Registry | Accepted / Integrated（P2-SEC-TC-001〜020） | `test-design.md` Section 44 |
| Phase 2 Standard Memory Management UI | Accepted / Integrated（FUI2-001〜030） | `phase2-frontend-ui-design.md` / `frontend-design.md` Section 25 |
| Phase 2 Frontend UI Test Registry | Accepted / Integrated（P2-FUI-TC-001〜012） | `test-design.md` Section 45 |
| Phase 2 Detailed Design Final Review | Completed — Passed（Critical / High Open 0） | `phase2-detailed-design-final-review.md` Version 2 |

API、Persistence / Search、Backup / Restore、Security / Logging / Observability、FrontendおよびTest Strategyの詳細化と最終横断レビューは完了した。Phase 2は`Reviewed — Implementation Ready`であり、Phase 3 / 4設計への引継ぎは`phase2-to-phase3-4-handoff.md`を使用する。

---

## 19. Implementation-start Confirmations

Phase 2 Detailed Designを阻害するOpen Decisionはない。次は実装時の局所選択または検証であり、Accepted Architectureを変更しない限りPhase 2横断レビューを再開しない。

- Consumer-facing Interfaceの局所的な型名とMethod名
- Accepted Flowを実現するJob / Transaction内の実装分割
- OpenAI Adapter固有のRequest / Response Mapping
- Semantic Searchを追加するか判断するための実Memory Evaluation
- 初期Keyword Ranking WeightとRelevance ThresholdのEvaluation調整
- Java Argon2id Library、Version、Dependency SecurityおよびBenchmark
- Flutter Secure Storage / File Picker Packageの対応Version確認
- Certificate、Key、Rotation、Environment分離のProvisioning
- 実機iPhoneでのPrivacy、Background、InterruptionおよびArchive Test
- 負荷・Fault Injection結果に基づくAlert Threshold調整

これらの選択でApproved Requirement、Public API、Persistence Schema、Security Boundary、削除・RestoreのAtomicityまたはCritical / High Safety Boundaryを変更する場合だけ、影響範囲を限定したChange Reviewを行う。

---

## 20. Backup / Restore Domain and Archive Design

### 20.1 Purpose and Design Boundary

Phase 2では、ユーザーが明示操作によってPersonal MemoryとMemory Preferencesを暗号化ArchiveへExportし、対応Archiveを検証・Preview・明示確認した後にRestoreできるようにする。

Backup / Restoreは次を目的とする。

- DynamoDB、AI Providerおよび内部Persistence Keyに依存しないData Portability
- Local Data消失時のPersonal Memory復旧
- Restore前に内容と影響を確認できるUser Control
- Sensitive Memoryを平文Fileとして持ち出さないDefault Protection

Phase 2で自動・定期Backup、Cloud同期、複数端末同期およびConversation History Backupは導入しない。

### 20.2 Export Scope

Archive v1へ含めるDataは次に限定する。

| Data | Included | Rule |
|---|---:|---|
| `ACTIVE` Personal Memory | Yes | Export時点で有効なCurrent Resource |
| `RESOLVED` Personal Memory | Yes | 保存中の有効Resourceとして含める |
| Memory Preferences | Yes | `autoSaveEnabled`と`answerUseEnabled`の現在値 |
| Memory Revision History | No | 過去本文を持ち出す範囲を最小化する |
| Deleted Memory Content | No | 削除済み本文を復元しない |
| Re-registration Guard / Fingerprint | No | Key依存・内部Security Data |
| Memory Reset Point | No | 現在Installation固有の削除境界 |
| Conversation History | No | `conversation` Featureが所有する別Data |
| Usage Trace | No | Conversation関連Data |
| Search Projection / Embedding | No | 再生成可能な派生Data |
| Pending Candidate / Confirmation | No | 未確定・一時Data |
| Deletion / Restore Plan | No | Operation固有の一時Data |
| Idempotency / Audit Record | No | Runtime Operation Data |
| Provider / DynamoDB Metadata | No | Portability対象外 |

一つでもExport対象MemoryがArchive Schema、Secret禁止、SizeまたはDomain Validationを満たさない場合、該当Recordだけを黙って省略せずExport全体を失敗させる。Userへ対象件数と修正手段を示すが、ErrorへMemory本文またはSecret検出値を含めない。

### 20.3 Archive v1 Logical Structure

Archive v1は、暗号化用Binary Envelopeと、その内部で暗号化されるUTF-8 JSON Payloadから構成する。

```text
Alice Memory Backup File
├── Public Binary Envelope Header
│   ├── Magic / Envelope Version
│   ├── KDF Algorithm and bounded parameters
│   ├── Random Salt
│   ├── Cipher Algorithm
│   ├── Random Nonce
│   └── Ciphertext Length
└── AEAD Ciphertext
    └── Encrypted Archive Payload JSON + Authentication Tag
```

File Extensionは`.alice-memory-backup`とする。Extensionだけで形式を信用せず、Magic、Version、LengthおよびAEAD認証を検証する。

Envelope v1のMagicは8 ASCII Byteの`ALICEMB1`とする。Headerは最大4 KiBのLength-prefixed UTF-8 JSONとし、HeaderのExact Byte列をAEAD Additional Authenticated Data（AAD）として認証する。

Public HeaderへMemory件数、Category、日時、本文、設定値、Installation IDまたはUser情報を含めない。

### 20.4 Archive Protection v1

Archive v1では次を固定する。

| Purpose | Algorithm / Parameter |
|---|---|
| Passphrase KDF | Argon2id |
| Salt | CSPRNG生成16 Byte。Archiveごとに新規生成 |
| Memory Cost | 64 MiB |
| Iterations | 3 |
| Parallelism | 1 |
| Derived Key | 32 Byte |
| AEAD | AES-256-GCM |
| Nonce | CSPRNG生成12 Byte。Archiveごとに新規生成 |
| Authentication Tag | 16 Byte |
| Compression | v1では使用しない |

同じPayloadとPassphraseを再Exportしても、SaltとNonceを毎回生成するため同一Archive Byte列にならない。

EnvelopeのAlgorithm IDまたはParameterがv1の許可値と一致しない場合、推測で別AlgorithmへFallbackしない。将来Algorithmを変更する場合は新しいEnvelope VersionとしてSecurity Review、Compatibility TestおよびMigration方針を追加する。

Argon2id Library、Provider、実測時間およびMemory使用量はSecurity / Implementation Designで固定する。採用Libraryが利用できないことを理由に、設計変更なしでPBKDF2、固定Key、Hash単体または独自暗号へ置換しない。

### 20.5 Passphrase Policy

Archive暗号化KeyはUserがExport時に入力したPassphraseから導出する。AliceはPassphrase Recovery、Server-side EscrowまたはDefault共通Passphraseを提供しない。

| Constraint | Rule |
|---|---|
| Minimum | 12 Unicode Code Point |
| Maximum | 128 Unicode Code Pointかつ512 UTF-8 Byte |
| Canonicalization | Trim、Unicode Normalization、Case変換を行わず入力Byteを維持 |
| Export | Flutter上で2回入力が完全一致した場合だけ開始し、Backend APIへは一致確認後の1値だけを送る |
| Restore / Inspect | 1回入力。Archive認証成功後だけPayloadを扱う |

Spaceを含むPassphraseは許可するが、Whitespaceのみは拒否する。複雑性Ruleによる特定記号・大文字・数字の強制は行わず、十分な長さとUserへの保存案内を優先する。

Passphraseは次のBoundaryを守る。

- Application Log、Access Log、Metric、TraceおよびErrorへ出力しない。
- DynamoDB、Idempotency Record、Restore PlanまたはArchiveへ保存しない。
- AI Provider、Search ProviderまたはToolへ送信しない。
- Request処理を超えてCacheしない。
- 使用後は可能な範囲でMutable Bufferを上書きし、GCだけを消去保証として説明しない。
- Passphraseを忘れたArchiveはAliceから復号できないことをExport前に明示する。

### 20.6 Encrypted Payload Schema v1

復号後Payloadの論理Schemaは次とする。

```json
{
  "schemaVersion": 1,
  "archiveId": "b13d5e5a-70db-4f54-8b64-123456789abc",
  "producer": "PROJECT_ALICE",
  "exportedAt": "2026-08-29T21:00:00.000+09:00",
  "memories": [
    {
      "recordId": "c24e6f6b-81ec-4056-9c75-23456789abcd",
      "content": "仕事ではJavaを使用している。",
      "category": "ENGINEERING",
      "captureType": "EXPLICIT",
      "sensitivityLevel": "NORMAL",
      "state": "ACTIVE",
      "createdAt": "2026-08-20T20:00:00.000+09:00",
      "updatedAt": "2026-08-28T20:30:00.000+09:00",
      "confirmedAt": "2026-08-28T20:30:00.000+09:00"
    }
  ],
  "preferences": {
    "autoSaveEnabled": true,
    "answerUseEnabled": true,
    "updatedAt": "2026-08-28T19:00:00.000+09:00"
  }
}
```

Payload Field Rule:

| Field | Rule |
|---|---|
| `schemaVersion` | v1ではInteger `1`固定 |
| `archiveId` | Exportごとに生成するCanonical UUID v4 |
| `producer` | `PROJECT_ALICE`固定。暗号学的な発行者証明とは扱わない |
| `exportedAt` | JST Offset付き・ミリ秒精度 |
| `memories` | 0〜10,000件。`recordId`重複不可 |
| `preferences` | v1で必須。値2つとArchive上の更新日時 |

JSONはUTF-8、重複Key禁止、Unknown Field禁止、最大Nesting Depth 16とする。Enum、Contentおよび日時は通常Domain / APIと同じRuleで検証し、未知Categoryを`OTHER`へ変換しない。

### 20.7 Archive Identity and Runtime Identity

Archiveの`recordId`はArchive内参照専用であり、Runtimeの`memoryId`ではない。次をArchiveへ含めない。

- Runtime `memoryId`
- Runtime `version`とETag
- DynamoDB Key
- Revision ID
- Installation / Device / User ID

Restore ActionごとのIdentity Rule:

| Action | Runtime Identity |
|---|---|
| 新規追加 | 新しい`memoryId`、`version = 1` |
| Equivalent / No Change | 現在の`memoryId`を維持 |
| 既存Memory更新 | 現在の`memoryId`を維持しVersionを進める |
| 削除済み内容の明示再導入 | 新しい`memoryId`、`version = 1` |

新規追加ではArchiveの`captureType`、`createdAt`、`updatedAt`および`confirmedAt`を維持し、Content-free Restore Auditへ実際の`restoredAt`を記録できる。これにより生成経路と元の時系列を失わず、Restore実行日時も区別できる。

既存Memoryを更新する場合、現在の`captureType`と`createdAt`を維持し、`updatedAt`と`confirmedAt`をRestore成功時刻へ更新する。Archiveの過去値は新しいRevisionの判断Sourceとして扱うが、Runtime Versionを巻き戻さない。

### 20.8 Export Consistency

Exportは一つの論理Snapshotとして扱う。

```text
Manual Export Request
      ↓
Current Memory + Preferences読込
      ↓
Schema / Secret / Size Validation
      ↓
Source Versions再確認
      ↓
Payload生成
      ↓
Encrypt + Authenticate
      ↓
Completed Archiveを返却
```

Export中にMemoryまたはPreferencesが変更された場合、変更前後を混在させたArchiveを成功として返さない。Database Adapterは安定SnapshotまたはVersion再確認を提供し、不一致時はBounded Retryまたは`SOURCE_CHANGED`として失敗させる。

一部Memoryだけを含むPartial Archive、認証Tag未確定ArchiveまたはSize超過ArchiveをUserへ返さない。Export成功はArchive全Byteの生成と最終Size検証を確認した場合だけ成立する。

### 20.9 Size and Resource Limits

Archive v1は次を同時に適用する。

| Constraint | Maximum |
|---|---:|
| Encrypted Archive File | 256 MiB（268,435,456 Byte） |
| Public Header | 4 KiB（4,096 Byte） |
| Decrypted JSON Payload | 255 MiB（267,386,880 Byte） |
| Memory Records | 10,000件 |
| JSON Nesting Depth | 16 |
| Single Memory Content | 2,000 Code Pointかつ8 KiB UTF-8 |

Archive v1はCompressionを使用しないため、Decompression RatioおよびEntry展開は存在しない。ZIP、TAR、Directory Entry、Symbolic Linkまたは任意File Pathを解釈しない。

Length Fieldは信用せず、実読込Byteにも上限を適用する。KDF実行前にMagic、Header Length、File SizeおよびParameter上限を検証し、過大値によるMemory / CPU消費を防ぐ。

### 20.10 Inspection Validation Order

`InspectMemoryBackupUseCase`は次の順で検証し、Mutationを行わない。

1. File SizeとReadable Regular File / Upload Boundary
2. Magic、Envelope Version、Header LengthおよびHeader JSON
3. Algorithm ID、KDF Parameter、Salt、NonceおよびCiphertext Length
4. Passphrase KDFとAEAD Authentication
5. Decrypted Payload Size、UTF-8、JSON Syntax、Duplicate KeyおよびDepth
6. Payload Schema Version、Required / Unknown FieldおよびCount
7. RecordごとのContent、Enum、日時、SecretおよびDomain Validation
8. Archive内Duplicate / Relation Validation
9. Current Memory、Preferences、GuardおよびReset Pointとの比較
10. Restore Actionと影響Previewの生成

AEAD認証が失敗した場合、Wrong Passphraseと破損・改変Archiveを区別せず`INVALID_PASSPHRASE_OR_ARCHIVE`とする。Provider Exception、Tag、Salt、Nonce、Digestまたは復号途中DataをErrorへ含めない。

Unsupported Version、Unsafe Parameter、Invalid Schema、Prohibited ContentおよびSize超過は別の安全なResult Codeで区別できるが、Memory本文をEchoしない。

### 20.11 Restore Strategy: `MERGE_SAFE`

Phase 2のRestore Strategyは非破壊の`MERGE_SAFE`だけを提供する。

- Archiveに存在しない現在Memoryを削除しない。
- Archiveを理由に全Memoryを置換しない。
- Current MemoryをRuntime Versionごと巻き戻さない。
- 新規追加、Equivalent、明確な更新候補、Review Requiredを区別する。
- 削除・置換を伴うSnapshot RestoreはPhase 2 Scope外とする。

Restore Relation Action:

| Relation / Condition | Proposed Action |
|---|---|
| `NONE` | `ADD` |
| `EQUIVALENT` | `NO_CHANGE` |
| `COMPLEMENTARY_SAME_FACT` | `REVIEW_REQUIRED` |
| Archiveが明確にCurrent Factを更新する | `UPDATE_EXISTING` |
| Current Memoryの方が新しい可能性がある | `REVIEW_REQUIRED` |
| `CONFLICT` | `REVIEW_REQUIRED` |
| `UNCERTAIN` | `REVIEW_REQUIRED` |
| Guard / Reset Pointと一致 | `REINTRODUCE_DELETED` |
| Record自体がInvalid | Planを作成せずInspection Failure |

TimestampだけでArchiveとCurrentの真偽を決定しない。`UPDATE_EXISTING`はSubject、Attribute、Scopeおよび内容の変化が明確で、Previewへ具体的影響を固定できる場合だけ使用する。

`REVIEW_REQUIRED`をUser選択なしで`ADD`、`UPDATE`または`NO_CHANGE`へ変換しない。

### 20.12 Preferences Restore

ArchiveにはMemory Preferencesを必ず含めるが、Restore時に自動適用しない。

Previewで現在値とArchive値を比較し、次をUserが明示選択する。

| Choice | Behavior |
|---|---|
| `KEEP_CURRENT` | 現在Preferencesを変更しない |
| `APPLY_ARCHIVE` | Archive値を一つのAtomic Preference Mutationとして適用 |

Defaultは`KEEP_CURRENT`とする。Archiveが`autoSaveEnabled = true`または`answerUseEnabled = true`でも、Restore選択だけを根拠に暗黙で再有効化しない。

`APPLY_ARCHIVE`実行前にPreview時のPreferences Versionを再確認する。不一致時は古いPreviewで上書きせずPlanをInvalidatedとする。

### 20.13 Deleted Memory Reintroduction

Re-registration GuardまたはReset PointはArchiveへExportしない。Restore Inspectionでは、現在Installationに存在するGuardとReset Pointを使用してArchive Recordとの一致を確認する。

`REINTRODUCE_DELETED`が1件以上ある場合、通常のRestore確認とは別に次を明示する。

- 削除後に再導入される件数
- 対象の理解可能なPreview
- 新しい`memoryId`で再登録されること
- 過去の削除意思を今回の明示Restore意思で上書きすること

専用確認がないRecordはRestoreしない。専用確認後の再導入は`captureType = EXPLICIT`へ上書きせずArchiveの元Capture Typeを維持し、操作自体をContent-free Auditで明示Restoreとして記録する。

新しいInstallation等でGuard / Reset Pointが存在しない場合、Archiveだけから過去の削除履歴を推測できない。Previewで「この環境には削除履歴がないため、古いArchiveに削除済み内容が含まれるか判定できない」ことを示す。Archive Restoreを削除履歴の完全復元として案内しない。

### 20.14 Restore Plan and Confirmation Binding

Inspection成功後、次へBindingした`MemoryRestorePlan`を作成する。

```text
MemoryRestorePlan
├── restorePlanId
├── archiveDigest
├── archiveSchemaVersion
├── proposedActions
├── currentMemoryExpectedVersions
├── preferencesExpectedVersion
├── resetGeneration
├── userResolutionSelections
├── reintroductionConfirmation
├── actionSetVersion
├── createdAt
└── reviewExpiresAt
```

`archiveDigest`はEncrypted ArchiveのExact Byte列に対するSHA-256とし、Plan BindingとIdempotencyに使用する。Digest、PassphraseまたはArchive IDを通常Logへ記録しない。

Plan Review期限は作成から固定4時間とし、Accessで延長しない。Confirmation Tokenは専用Confirm処理でだけ発行し、発行から15分以内かつReview期限まで有効とする。Token期限切れだけではPlanを失効させず、Review期限内なら再確認できる。Action Pageは不変`actionSetVersion`へBindingし、User ResolutionによるPlan Version変更だけではCursorを無効化しない。Execute時にArchive Digest、全対象Version、Preferences Version、Reset GenerationおよびUser選択を再確認する。一つでも変化した場合、Mutation開始前にPlanをInvalidatedとし再Inspectionを要求する。

User ResolutionはDeltaとして保持できるが、Active Deltaは128 Items / 2 MiBを上限とする。96件でSelection Compactionを開始し、Hard Limit到達後はPlanを破棄せず新しい変更だけをRetryableに停止する。Compaction中も既存Selectionを完全に復元できる場合だけReview / Confirm / Executeを継続し、Generation、DigestまたはPointer不整合時は古い選択を推測して使用しない。

Planは復号済みMemory本文またはPassphraseを永続化しない。実行に必要なEncrypted Archiveは期限内だけ厳格なTemporary Boundaryで保持するか、Execute時に同じArchiveを再提示してDigest一致を確認する。具体方式はAPI / Security Designで確定する。

### 20.15 Restore Execution and Result

Restore実行は固定済みPlanだけを使用し、実行時にRelationを別結果へ再判定して対象を増減させない。

```text
Confirmed Restore Plan
      ↓
Preflight Validation
      ↓
Durable Restore Intent
      ↓
Idempotent Per-action Mutation
      ↓
Preferences Mutation（選択時）
      ↓
Search Projection再生成 / 無効化
      ↓
Complete / Partial / Failed / Unknown
```

Resultは少なくとも次を区別する。

| Result | Meaning |
|---|---|
| `COMPLETED` | 全選択Actionの確定結果を確認済み |
| `NO_CHANGE` | 全RecordとPreferencesに変更不要 |
| `PARTIAL` | 一部Actionだけ成功 |
| `FAILED` | 変更成功を確認できず失敗が確定 |
| `UNKNOWN` | 1件以上の最終状態を確定できない |
| `INVALIDATED` | 実行前にSource / Target差分を検出 |

`PARTIAL`または`UNKNOWN`を全体成功として表示しない。成功済みActionを同じRestore OperationのRetryで重複実行せず、未完了・不明Actionを個別にReconcileする。

Restoreで追加・更新したMemoryは通常の検索・回答Contextへ反映する前にAuthoritative ResourceとProjection Versionを整合させる。Projection生成失敗をMemory Mutation成功と混同せず、Repair可能な状態として区別する。

### 20.16 Temporary Data and Cleanup

Archive処理では次を守る。

- Upload / Export中のEncrypted ArchiveだけをPermission制限されたTemporary Fileへ保持できる。
- 復号済みPayloadを通常File、Cache、DynamoDBまたはLogへ保存しない。
- 復号済みPayloadはBounded Memory内で扱い、Use Case終了後に参照を破棄する。
- Temporary FilenameへUser入力、Archive ID、CategoryまたはMemory情報を含めない。
- Success、Failure、Cancel、TimeoutおよびProcess Recoveryの全経路でTemporary FileをCleanupする。
- Cleanup FailureはSecurity EventとしてContent-freeに記録し、次回Startup Cleanup対象にする。
- OS、SSD、Userが保存したExport先Fileからの物理的完全消去をApplicationだけで保証しない。

Export済みArchiveはUser所有Fileであり、Memory削除またはDelete-allによって自動変更・削除できない。

### 20.17 Logging and Audit

通常Logへ次を出力しない。

- PassphraseまたはDerived Key
- Archive Byte列、Salt、Nonce、TagまたはDigest
- Memory本文、Record JSONまたはPreferences値
- Secret検出値またはFingerprint
- Restore PlanのTarget一覧
- Temporary FileのUser-visible Path

Content-free Operation Auditへ次を記録できる。

- Operation IDとType（Export / Inspect / Restore）
- 開始・完了日時
- Archive Schema / Envelope Version
- Memory件数とAction件数
- `COMPLETED` / `PARTIAL` / `FAILED` / `UNKNOWN`
- 安全なReason Code

Wrong PassphraseとCorrupted ArchiveをAuditでも同じReason Familyとして扱い、Passphrase推測Oracleを作らない。

### 20.18 Deferred Technical Details

後続設計で次を確定する。

- Binary Envelopeの厳密なByte LayoutとParser Test Vector
- Java Argon2id Library、Version、Dependency SecurityおよびBenchmark
- CSPRNG SourceとKey Material Zeroizationの実装範囲
- Stable Export SnapshotのDynamoDB Access Pattern
- Restore Intent、Per-action ResultおよびRecoveryのPersistence Model
- Encrypted Temporary FileのDirectory、Permission、TTLおよびStartup Cleanup
- iOS File Picker、保存先、Share SheetおよびPassphrase UX
- Export / Inspect / Restore API Endpoint、Media TypeおよびStreaming Contract
- Archive Compatibility FixtureとMigration Tooling

### 20.19 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| MD-086 | Archive v1へ`ACTIVE` / `RESOLVED` MemoryとMemory Preferencesだけを含める | Accepted |
| MD-087 | Revision、削除済みContent、Guard、Reset Point、Conversation、Trace、ProjectionおよびRuntime DataをArchiveから除外する | Accepted |
| MD-088 | Archive v1をPublic Binary Envelopeと暗号化UTF-8 JSON Payloadで構成する | Accepted |
| MD-089 | Argon2id（64 MiB、3回、並列度1）とAES-256-GCMをArchive v1のProtection Contractとする | Accepted |
| MD-090 | Archiveごとに16 Byte Saltと12 Byte NonceをCSPRNGで新規生成し、HeaderをAADとして認証する | Accepted |
| MD-091 | Passphraseを12〜128 Code Pointかつ最大512 Byteとし、正規化・保存・Recoveryを行わない | Accepted |
| MD-092 | Archive v1でCompression、ZIP、TARおよび任意File Entryを使用しない | Accepted |
| MD-093 | Payload Schema v1をStrict JSONとし、Unknown / Duplicate Field、未知Enumおよび暗黙Migrationを拒否する | Accepted |
| MD-094 | Archive Record IDをRuntime Memory IDから分離し、Runtime Version、ETagおよびPersistence KeyをExportしない | Accepted |
| MD-095 | 新規・再導入Memoryへ新しいIDを発行し、Equivalent / Updateでは現在IDを維持する | Accepted |
| MD-096 | Exportを整合した論理Snapshotとし、Source変更・Invalid Record・Size超過時にPartial Archiveを返さない | Accepted |
| MD-097 | Archive上限を256 MiB、Decrypted Payload上限を255 MiB、Memory上限を10,000件、JSON Depth上限を16とする | Accepted |
| MD-098 | InspectionをEnvelope、KDF / AEAD、Payload、Domain、Current Stateの順で実行し、Mutationと分離する | Accepted |
| MD-099 | Wrong Passphraseと破損・改変Archiveを`INVALID_PASSPHRASE_OR_ARCHIVE`として外部上区別しない | Accepted |
| MD-100 | Phase 2 Restoreを非破壊`MERGE_SAFE`に限定し、Archive不存在を理由とする削除・全置換を行わない | Accepted |
| MD-101 | Restore Actionを`ADD`、`NO_CHANGE`、`UPDATE_EXISTING`、`REVIEW_REQUIRED`、`REINTRODUCE_DELETED`へ分類する | Accepted |
| MD-102 | Preferences RestoreをDefault `KEEP_CURRENT`とし、`APPLY_ARCHIVE`を明示選択した場合だけ適用する | Accepted |
| MD-103 | Current Guard / Reset Pointに一致するArchive Recordを通常Restoreから分離し、専用再導入確認を要求する | Accepted |
| MD-104 | 新環境で削除履歴が存在しない場合、古いArchiveの再導入可能性を判定不能として明示する | Accepted |
| MD-105 | Restore PlanをArchive Digest、Target Version、Preferences Version、Reset Generation、User選択、固定4時間Review期限および不変Action SetへBindingし、15分Tokenを分離する | Accepted |
| MD-106 | Restore結果を`COMPLETED`、`NO_CHANGE`、`PARTIAL`、`FAILED`、`UNKNOWN`、`INVALIDATED`で区別する | Accepted |
| MD-107 | Passphraseと復号済みPayloadを永続化せず、Encrypted Temporary Fileを全終了経路でCleanupする | Accepted |
| MD-108 | Phase 2 Backupを手動Export / Restoreに限定し、自動Backup、Cloud SyncおよびBase64 JSON埋込みを導入しない | Accepted |

### 20.20 Next Backup Design Topics

MD-086〜MD-108は承認済みである。次の順序で詳細化する。

1. Backup / Restore API Contract（`api-design.md`）
2. Archive Security / Key Material / Temporary Data（`security-design.md`）
3. Export Snapshot / Restore Recovery Persistence（`database-design.md`）
4. Flutter Backup / Restore UX（`frontend-design.md`）
5. Backup / Restore Test Strategy（`test-design.md`）

---

## 21. Phase 2 Cross Review CR-004 Domain Resolution

### 21.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-004 |
| Superseded Decision after Approval | MD-097のPayload上限部分 |

Encrypted Archive FileとDecrypted JSON Payloadの上限を分離し、Archive Envelopeの必須Overheadを明示的に確保する。Archive v1のFile上限、暗号方式、Media TypeおよびMemory件数上限は変更しない。

### 21.2 Accepted Domain Limits

| Constraint | Maximum |
|---|---:|
| Encrypted Archive File | 256 MiB（268,435,456 Byte） |
| Decrypted JSON Payload | 255 MiB（267,386,880 Byte） |
| Envelope Reserve | 1 MiB（1,048,576 Byte） |
| Public Header | 4 KiB（4,096 Byte） |
| AES-GCM Authentication Tag | 16 Byte |
| Memory Records | 10,000件 |
| JSON Nesting Depth | 16 |

PayloadはUTF-8 JSONのExact Byte数で測定する。ArchiveはMagic、Length Prefix、Public Header、CiphertextおよびAuthentication Tagを含むFile全体のExact Byte数で測定する。どちらも上限へ合わせて切断、Record省略または再Encodingしない。

Envelope v1の全Overheadは1 MiB Reserve内に収まらなければならない。将来Headerまたは暗号Materialを拡張してReserveを超える場合、同一VersionのままPayload上限を暗黙変更せず、新しいEnvelope VersionとCompatibility Reviewを行う。

### 21.3 Validation Invariants

- ExportはPayload上限を暗号化前、Archive上限を完成後に独立検証する。
- RestoreはArchive上限をKDF前、Payload上限をAEAD認証後かつJSON解析前に独立検証する。
- Length計算はOverflow-safeとし、Header宣言値を実読込Byteより優先しない。
- Size超過は`BACKUP_ARCHIVE_TOO_LARGE`、不正な構造Parameterは`UNSAFE_ARCHIVE_PARAMETERS`として区別する。
- Mobile / Desktop Clientの事前検証に関係なくBackendをAuthoritative Boundaryとする。

### 21.4 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| MD-109 | Archive v1のEncrypted File上限を256 MiB、Decrypted JSON Payload上限を255 MiBへ分離する | Accepted |
| MD-110 | ArchiveとPayloadの差1 MiBをEnvelope Reserveとし、同一VersionのOverhead上限とする | Accepted |
| MD-111 | PayloadとArchiveを異なる測定点で独立検証し、切断・Record省略・暗黙再Encodingを禁止する | Accepted |
| MD-112 | Size計算をExact Byte・Overflow-safeとし、Declared Lengthだけを信用しない | Accepted |
| MD-113 | Platformに依存せず同じByte定数を使用し、BackendをAuthoritative Boundaryとする | Accepted |

MD-109〜MD-113は承認済みであり、Section 20.9とMD-097を更新してAPI2-185〜API2-192と同じ変更単位で確定した。

---

## 22. Phase 2 Cross Review CR-005 Domain Resolution

### 22.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-005 |
| API Source | `api-design.md` Section 44、API2-193〜API2-205 |

Restore PlanはSingle Userあたり一つだけActiveにする。ActiveとはInspection中のLease、`NEEDS_REVIEW`、`READY`および`EXECUTING`を含み、Clientや端末ごとの別枠を設けない。

### 22.2 Domain Invariants

- 新Planは既存Planを自動失効させない。
- Userは既存Planを再開するか、`NEEDS_REVIEW` / `READY`で明示Cancelする。
- Restore Plan Create RetryはIdempotency-Keyで同じOperationへ収束する。
- Restore Active Slot、Temporary Byte、Backup Crypto Slot、Inspection LeaseおよびBindingをUpload前にAtomic Reservationする。
- Restore SlotまたはCrypto Slotだけを保持した待機を行わない。
- KDF / AEAD完了後のCrypto Material破棄とCrypto Slot解放を同じPhase遷移で確定する。
- Cancel、期限切れ、失敗またはRestartでSensitive Temporary Stateを再利用不能にする。
- Cleanup確認前にQuotaを解放せず、新Planを許可しない。
- `EXECUTING` PlanはCancelせず、Durable Resultを照合する。

### 22.3 Resource Limits

| Resource | Maximum |
|---|---:|
| Active Plan / Inspection Lease | 1 |
| Retained Temporary State | 544 MiB（570,425,344 Byte） |
| Working Temporary Directory | 768 MiB（805,306,368 Byte） |
| Inspection Lease | 固定30分 |
| Backup Crypto Slot | Export / Inspect合計1 |

### 22.4 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| MD-114 | Active Restore PlanとInspection LeaseをSingle User全体で一つに制限する | Accepted |
| MD-115 | Retained 544 MiB、Working 768 MiBのTemporary Byte Hard Limitを設ける | Accepted |
| MD-116 | 新Planによる既存Planの自動失効を禁止し、再開または明示Cancelを要求する | Accepted |
| MD-117 | Restore Inspection前にActive SlotとTemporary ByteをAtomic Reservationする | Accepted |
| MD-118 | Restore Plan CreateとCancelをIdempotentなOperationとして扱う | Accepted |
| MD-119 | `CANCELLED`をTerminal Statusへ追加し、`EXECUTING`のCancelを禁止する | Accepted |
| MD-120 | Cleanup確認後だけQuotaを解放し、失敗時はCLEANUP_PENDINGとして保持する | Accepted |
| MD-121 | Failure、Disconnect、Cancel、Expiry、Startupおよび周期Sweeperで同じCleanup Ruleを使用する | Accepted |
| MD-122 | Temporary StorageのMetric / Auditから本文、Path、Archive情報およびSecretを除外する | Accepted |

MD-114〜MD-122は承認済みであり、API2-193〜API2-205と同じ変更単位で確定した。

DB2-165〜DB2-177との統合により、Restore / Crypto両ControlのAtomic Reservation、Inspection Phase、Crypto Material破棄後のSlot解放およびCleanup Pending Recoveryを本SectionのPersistence Contractとして適用する。

---

## 23. Phase 2 Cross Review CR-006 Domain Resolution

### 23.1 Purpose and Status

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-006 |
| API Source | `api-design.md` Section 45、API2-206〜API2-220 |

`CONFIRMATION_REQUIRED`を単なる会話表示で終わらせず、Candidate、Relation判定、Expected VersionおよびUser ResolutionへBindingした`MemoryRelationReview`として扱う。

### 23.2 Aggregate

```text
MemoryRelationReview
├── relationReviewId
├── sourceOperation
├── sourceMemoryId / ExpectedVersion
├── protectedCandidate
├── relationType
├── relatedMemoryExpectedVersions
├── allowedResolutionPlans
├── guardGeneration
├── status / version
├── createdAt / reviewExpiresAt
└── result
```

Candidate本文とMutation PreviewはSensitive Temporary Stateであり、Content-free Metadataと分離する。固定Review期限は30分、Active ReviewはSingle User全体で20件、関連Memoryは一Review100件を上限とする。

### 23.3 Resolution Semantics

| Resolution | Domain Meaning |
|---|---|
| `UPDATE_TARGET` | Reviewが生成したAllowed TargetとMutation PreviewをExpected Version付きで適用 |
| `ADD_AS_NEW` | Candidateが既存Memoryとは独立して成立するとUserが明示確認し、新IDで保存 |
| `SKIP` | Candidateを保存・更新せず終了 |

全Relation Typeで三つの選択肢を提示できるが、Recommended Actionを自動選択しない。`COMPLEMENTARY_SAME_FACT`と`SUPERSEDES`は`UPDATE_TARGET`を推奨でき、`CONFLICT`と`UNCERTAIN`はDefaultなしとする。

User Resolutionが上書きできるのはReviewへBindingされたRelation判断だけである。Secret、Sensitivity、Category、Atomicity、削除Guard、未知Target、Version ConflictまたはReview後に発生した新Relationは上書きできない。

### 23.4 Lifecycle

```text
OPEN
├── Resolve → PROCESSING → COMPLETED / SKIPPED / FAILED / UNKNOWN
├── 30分経過 → EXPIRED
└── Version / Guard / Relation変化 → INVALIDATED
```

Terminal後はCandidate / PreviewをCleanupし、Content-free Resultだけを最低24時間保持する。`UNKNOWN`は新Operationとして再実行せず、同じResolution Operationを照合する。

Reviewの保護Stateは作成時に固定した単一のRelation State Key VersionへBindingする。Key VersionはDomain上の非公開保護属性であり、Client選択、表示、検索またはAI Contextへ使用しない。参照Key欠落、未知Version、AAD不一致またはAEAD検証失敗では別Keyや平文へFallbackせず、Reviewを`INVALIDATED`へ遷移してCandidate / PreviewをCleanupする。Relation Key Startup Gate失敗はRelation Review Capabilityだけを閉じ、無関係なMemory ReadとConversation Historyを継続する。

### 23.5 Application Boundary

Management UIと自然言語操作は同じ`ResolveMemoryRelationUseCase`を使用する。Application Commandは`relationReviewId`、Resolution、Allowed Target、Expected Review VersionおよびOperation Identityを受け取り、Presentation種別をDomainへ持ち込まない。

複数のActive Reviewがある場合、会話上の「はい」「それで」だけから対象Reviewを選ばない。UserがReviewとResolutionを一意に指定できない場合はMutationせず確認する。

### 23.6 Accepted Decisions

| ID | Decision | Status |
|---|---|---|
| MD-123 | CONFIRMATION_REQUIREDをMemoryRelationReview Aggregateとして表現する | Accepted |
| MD-124 | ReviewをCandidate、Relation、Expected Version、Guard、Allowed Planおよび固定30分期限へBindingする | Accepted |
| MD-125 | User ResolutionをUPDATE_TARGET、ADD_AS_NEW、SKIPへ限定する | Accepted |
| MD-126 | UPDATE_TARGETをAllowed Targetと固定Mutation Previewへ限定する | Accepted |
| MD-127 | ADD_AS_NEWを独立事実としてのUser明示確認に限定する | Accepted |
| MD-128 | Relation OverrideからSecret、Sensitivity、Category、削除Guard、Versionおよび新Relationを除外する | Accepted |
| MD-129 | Review LifecycleでCompleted、Skipped、Failed、Unknown、Expired、Invalidatedを区別する | Accepted |
| MD-130 | Candidate / Previewを短命保護StateとしTerminal後にCleanupする | Accepted |
| MD-131 | Active Review 20件、関連Memory 100件、固定30分期限をHard Limitとする | Accepted |
| MD-132 | Management UIと自然言語操作で同じResolveMemoryRelationUseCaseを使用する | Accepted |
| MD-133 | 複数Review時の曖昧な肯定から対象ReviewまたはResolutionを推測しない | Accepted |

MD-123〜MD-133はAPI2-206〜API2-220と同じ承認単位で確定し、Database、Security、Frontend、AI / ConversationおよびTest Designへ統合した。

---

## 24. Phase 2 Requirement Traceability Boundary

| Item | Value |
|---|---|
| Status | Accepted / Integrated |
| Review Finding | CR-007 |
| Detailed Source | `phase2-requirement-traceability-matrix.md` |

Section 16のRequirement Group対応はArchitecture-level Summaryとして維持し、73件の個別Requirement、Public API / Internal Boundary、非API共同責務およびStable Test IDは専用MatrixをAuthoritativeとする。内部処理だけで完結するRequirementへHTTP Endpointを作ることをTraceabilityの条件にせず、`N/A — Internal only`とUse Case / Portを明記する。

専用Matrixの`OPEN GATE`が一件でも残る場合、Phase 2 Detailed DesignをImplementation Readyへ昇格しない。Domain Decision、Use CaseまたはRequirementを変更する場合は、同じChange SetでMatrixとTest Metadataを更新する。
