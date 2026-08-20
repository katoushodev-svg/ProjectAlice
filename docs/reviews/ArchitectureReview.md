Architecture Review の結論は 「条件付き承認（Approve with changes）」 です。

根本的なアーキテクチャ変更は不要です。むしろ現在のProject Aliceは、Phase 1実装へ向けた骨格としてかなり良い状態です。ただし、AI Coding Assistantを本格的に使い始める前に修正した方がよい設計書間の不整合が3点、将来の曖昧さが数点あります。

総合評価

観点	評価
アーキテクチャ思想	★★★★★
責務分離	★★★★★
Phase分割	★★★★★
拡張性	★★★★☆
過剰設計の回避	★★★★★
設計書間の整合性	★★★★☆
AI実装への適性	★★★★☆
Phase 1実装準備度	85〜90%

大きな作り直しは不要です。

⸻

1. 最優先：repository-structure.md に確定済み事項がOpen Decisionとして残っている

これは明確な不整合です。

decisions.md、backend-design.md、development-environment.md では、Backend環境はすでに以下で正式決定されています。

* Java / JDK 25
* Spring Boot 4.1.0
* Maven 3.9.16
* Maven Wrapper

ところが repository-structure.md の Open Decisions には、

* Backend Build Tool
* Java / JDK Version

が未決定として残っています。 repository-structure.md

これは特にProject Aliceでは問題です。

Aliceでは「設計書をAI実装のSource of Truthにする」こと自体が正式な方針なので、Copilotが repository-structure.md だけを強く参照すると、

Java Versionはまだ未決定だから決める必要がある

と判断する可能性があります。

修正

repository-structure.md の Open Decisions から、

* Backend Build Tool
* Java / JDK Version

を削除してください。

さらにRepository側にも短く、

Backend Runtime / Framework / Build Toolについては ADR-013 および development-environment.md にて決定済み。

と入れておくのが安全です。

重要度：Critical

⸻

2. Phase 1の「Conversation Memory」と「Conversation History」の用語を統一した方がよい

今回一番アーキテクチャ的に気になる部分です。

backend-design.md ではかなり明確に、

Conversation History ≠ Personal Memory

としており、Phase 1では、

conversation
 ├ Conversation
 ├ Message
 └ Conversation History

とする方針です。memory/ FeatureはPhase 2から追加します。 backend-design.md

これは非常に良い判断です。

しかし alice-architecture.md では、

Memory Categories
Conversation Memory
Personal Memory
Engineering Memory
Project Memory

となっており、Phase 1 Architectureでも、

Conversation Memory Port
    │
    └── DynamoDB Infrastructure

となっています。 alice-architecture.md

さらに repository-structure.md にもPhase 1 Backend責務として、

Conversation Memory

という名称が残っています。 repository-structure.md

概念的には間違いではありません。

しかしAI実装では、

Phase 1にもMemoryがある
→ memory/ packageを作ろう

という誤解を生みやすいです。

これは今回決定した、

Phase 1の会話履歴は conversation に含め、Phase 2から memory Featureを追加する

という方針を弱めています。

推奨

Phase 1に関する用語は完全に、

Conversation History

へ統一することを勧めます。

例えばArchitectureを、

Alice Core
    │
    ├── AI Provider Port
    │
    └── Conversation Repository Port
             │
             └── DynamoDB Infrastructure

とする。

Memoryの説明では、

Alice Memory（広義）
├── Conversation History
├── Personal Memory
├── Engineering Memory
└── Project Memory

としても構いません。

ただし、

Phase 1ではConversation HistoryをMemory Featureとして実装しない。

を明記します。

これなら概念レベルと実装レベルを両立できます。

重要度：Critical

⸻

3. Source of Truthが複数あるため「文書間で衝突した場合」のルールが必要

Project Aliceでは設計書をAI Coding AssistantのSource of Truthにしています。 decisions.md

これはAliceの開発方針として非常に重要です。

ただ、すでに文書が、

* product-definition
* requirements
* mvp
* alice-architecture
* repository-structure
* backend-design
* development-environment
* decisions

まで増えています。 

今後さらに、

* api-design
* database-design
* ai-design
* security-design
* test-design

が加わります。

この状態では必ず、

ArchitectureにはA
Backend DesignにはB
ADRにはC

という状況が発生します。

現在は「設計とADRに従え」とありますが、設計書同士で矛盾した場合の優先順位がありません。

推奨

docsの管理ルールとして、例えば以下を定義します。

Requirement / Scope
    requirements.md
    mvp.md
Architecture
    alice-architecture.md
Accepted Decisions
    decisions.md
Detailed Design
    backend-design.md
    api-design.md
    database-design.md
    ai-design.md
    ...
Implementation
    Source Code

そして、

Accepted ADRは、そのDecisionの対象範囲について既存設計より優先する。
Detailed DesignはArchitectureおよびAccepted ADRと矛盾してはならない。
Requirements / MVPのScopeを下位設計が独自に変更してはならない。
矛盾を発見した場合、AIは独自解釈で実装せず設計書を修正する。

というルールを設けるとかなり強固になります。

これはAI実装主体のAliceだからこそ重要です。

重要度：High

⸻

4. Phase 1のRequest Flowに「保存順序」が書かれている一方、Backend Designでは未決定になっている

ArchitectureのExample Request Flowでは、

AI Response取得
↓
User Message / Alice Response保存
↓
DynamoDB

という順番です。 alice-architecture.md

一方 backend-design.md では、

実際の保存順序、Failure時の整合性制御等はDatabase / API / Backend詳細設計で具体化する

さらにOpen Decisionsでも、

Message保存順序・Failure時の整合性戦略

が未決定です。 backend-design.md

Backend Design側の方が適切です。

なぜなら例えば、

User Message保存
↓
OpenAI
↓
Assistant Message保存

とするのか、

OpenAI
↓
TransactWriteItems
 User + Assistantを同時保存

とするのかで障害時の挙動が変わるからです。

推奨

ArchitectureのRequest Flowに、

以下は概念フローであり、Messageの永続化タイミングおよびFailure時の整合性戦略を規定するものではない。

を1行追加してください。

これでdatabase-design.md側が自由に正しい方式を決定できます。

重要度：High

⸻

5. Phase 1の成功条件とBackend Use Caseがまだ完全には接続されていない

RequirementsではPhase 1成功条件として、

保存した会話履歴を確認できる

ことが要求されています。 requirements.md

MVPでも、

過去会話を参照できる
会話履歴が保存される

となっています。 mvp.md

一方Backend Designで具体的に示されている主要Use Caseは現在、

SendMessageUseCase

です。 backend-design.md

もちろんAPI Design前なので問題ではありません。

ただし今後 api-design.md を作る際には最低限、

SendMessage
GetConversation
GetConversationMessages

あるいはそれと同等のUse Caseが必要になります。

場合によっては、

CreateConversation
ListConversations

も必要です。

「AI Context用に内部で履歴取得できる」ことと、

「ユーザーが過去履歴を確認できる」

は別Requirementなので、API Designで取りこぼさないよう注意が必要です。

重要度：Medium

⸻

6. AiProvider の所有Featureは将来的に問題になる可能性がある

Phase 1では、

conversation/
└── application/
    └── port/
        └── AiProvider

が候補になっています。 backend-design.md

Phase 1だけならこれで問題ありません。

しかし将来的には、

conversation
memory
tool
engineering support
agent

など複数FeatureがAIを使う可能性があります。

そのとき、

memory.application
    ↓
conversation.application.port.AiProvider

となるとFeature Boundaryがおかしくなります。

ただし、今は変更しなくてよい

Aliceには、

必要になるまで抽象化しない

という非常に良い原則があります。 alice-architecture.md

したがってPhase 1から ai/ Featureを作る必要はありません。

代わりにbackend-designへ、

Phase 1ではAiProvider Portをconversation Featureが所有する。
将来、複数Featureから共通利用する必要が発生した場合、Portの所有Boundaryを再設計しADRとして決定する。

程度を記載しておくのが最適です。

今作らないが、移動条件だけ決めるという設計です。

重要度：Medium

⸻

7. 将来の tool/, github/, aws/ の境界が少し曖昧

Backend DesignのTarget/Future構造には、

tool/
github/
aws/

が並ぶ可能性が示されています。 backend-design.md

Architectureでは、

Tool Port
 ├ GitHub Tool
 ├ Calendar Tool
 ├ AWS Tool
 └ PC Control Tool

というモデルです。 alice-architecture.md

ここは将来的に、

tool/github

なのか、

github/

なのか、

tool/
github/

の両方なのかで迷う可能性があります。

ただしPhase 3以降の問題なので、Phase 1のために決める必要はありません。

Backend DesignのFuture Structureを「確定Package構成」に見えないよう、

Feature名およびTool BoundaryはPhase 3開始時にRequirementに基づき決定する。

という現在の記述を維持し、Phase 3でADR化すれば十分です。

重要度：Low / Future

⸻

8. Phase 1のSecurity Boundaryを実装前に決める必要がある

ArchitectureではAuthentication方式はOpen Decisionです。 alice-architecture.md

Development EnvironmentではPhase 1を、

Flutter
 ↓
Local Spring Boot
 ↓
OpenAI / DynamoDB

としています。 development-environment.md

この構成自体は良いです。

ただしSecurity Designでは、

Phase 1のBackendを誰からアクセス可能にするか

を最低限決める必要があります。

例えば完全ローカルなら、

127.0.0.1 bind
+
Single User
+
Authenticationなし

という判断も十分あり得ます。

逆にLANや外部端末からアクセスするならAuthenticationが必要になります。

これは現在のArchitectureの欠陥というより、security-design.md作成時の必須Decisionです。

特にAliceは将来、個人情報・Memory・PC Agentを扱うため、SecurityをArchitecture Goalとして明示している現在の方向性は正しいです。 alice-architecture.md

重要度：High / 次工程

⸻

良かった部分

今回かなり高く評価できるのが、「Clean Architectureを使うこと」が目的化していない点です。

例えばBackend Designでは、

Phase 1で過剰なDomain Modelを作らない
単純なMappingのためだけにMapper Classを大量に作らない
空Featureや将来用Interfaceを先行作成しない

というルールがあります。 backend-design.md

これはかなり重要です。

典型的なAI生成コードでは、

Controller
Service
UseCase
Port
Adapter
Repository
Mapper
DTO
Domain
Entity
Factory

を必要もないのに大量生成しがちです。

Aliceではそれを設計書側で明確に防げています。

また、

Alice ≠ Spring Boot
Alice ≠ OpenAI
Memory ≠ DynamoDB
Tool ≠ PC Agent
Conversation History ≠ Personal Memory

という重要な境界もかなり明確です。 

この部分は長期プロジェクトとして非常に良い設計思想です。

⸻

Architecture Review 最終判定

Approve with changes

Phase 1実装前に最低限直したいのは次の3点です。

1. repository-structure.md の古いOpen Decisionを削除する
2. Phase 1の「Conversation Memory」を「Conversation History」へ統一する
3. 設計書間のSource of Truth / Conflict Resolution Ruleを定義する

その次に、

4. ArchitectureのRequest Flowを「保存順序を規定しない概念例」にする
5. API DesignでConversation History参照Use Caseを忘れない
6. AiProviderの将来的な昇格条件を記載
7. Security DesignでPhase 1のアクセス境界を確定

まで対応すれば、Architecture / Repository / Backendの基礎設計はPhase 1実装に入れる品質だと判断します。

特に現段階では新しいArchitecture PatternやFrameworkを追加するより、この設計の曖昧さを減らしてAIが誤解できない状態へ仕上げることの方が重要です。 product-definition.md

次の設計工程としては、このレビュー修正後に api-design.md → database-design.md → ai-design.md の順で進めるのが自然です。