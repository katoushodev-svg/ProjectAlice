# Project Alice - DynamoDB Local Setup

## 1. Document Information

| Item | Value |
|---|---|
| Document | `dynamodb-local-setup.md` |
| Project | Project Alice |
| Target | Phase 1 Local Development |
| Status | Draft |
| Last Updated | 2026-08-14 |

本ドキュメントは、Project Alice Phase 1でDynamoDB Local 3.3.0を導入・操作する手順を定義する。

Database Contractは`database-design.md`をSource of Truthとし、本ドキュメントでKey、Item Schema、TransactionまたはConsistency Ruleを変更しない。

---

## 2. When to Provide This Document to AI

本ドキュメントは以下の作業でAI Coding Assistantへ渡す。

- DynamoDB Local導入
- Docker Compose構成
- Local Table Setup Script作成
- Local起動・停止手順の実装
- DynamoDB Localの障害調査

Conversation Repository、Item Mapping、TransactionまたはPaginationの実装だけを依頼する場合、本ドキュメントは必須Inputではない。

---

## 3. Prerequisites

以下がDeveloper Machineで利用できることを確認する。

- Docker DesktopまたはDocker Engine
- Docker Compose v2
- Port `8000`が未使用
- Table Setup Commandを実行する場合はAWS CLI v2

```bash
docker --version
docker compose version
aws --version
```

AWS CLIはDynamoDB LocalのContainer起動には不要だが、Table作成・確認に使用する。

---

## 4. Planned Repository File

Implementation時に以下を作成する。

```text
backend/
└── compose.yaml
```

`backend/compose.yaml`:

```yaml
services:
  dynamodb-local:
    image: amazon/dynamodb-local:3.3.0
    container_name: project-alice-dynamodb-local
    command:
      - -jar
      - DynamoDBLocal.jar
      - -sharedDb
      - -dbPath
      - ./data
      - -disableTelemetry
    working_dir: /home/dynamodblocal
    ports:
      - "127.0.0.1:8000:8000"
    volumes:
      - project-alice-dynamodb-data:/home/dynamodblocal/data

volumes:
  project-alice-dynamodb-data:
```

`latest`へ変更してはならない。Version更新時は本ドキュメント、`database-design.md`およびTest結果を同時に更新する。

---

## 5. Start DynamoDB Local

Repository Rootから実行する場合:

```bash
docker compose -f backend/compose.yaml up -d dynamodb-local
```

Status確認:

```bash
docker compose -f backend/compose.yaml ps
```

Log確認:

```bash
docker compose -f backend/compose.yaml logs dynamodb-local
```

ContainerのVersion確認:

```bash
docker compose -f backend/compose.yaml exec dynamodb-local \
  java -jar DynamoDBLocal.jar -version
```

---

## 6. Local Environment Variables

Local Backend起動前に以下を設定する。

```bash
export ALICE_DYNAMODB_TABLE_NAME=project-alice-conversation-local
export ALICE_DYNAMODB_ENDPOINT=http://localhost:8000
export AWS_REGION=ap-northeast-1
export AWS_ACCESS_KEY_ID=local
export AWS_SECRET_ACCESS_KEY=local
export ALICE_PROCESSING_LEASE_SECONDS=300
export ALICE_FAILED_RETENTION_SECONDS=86400
```

`ALICE_CURSOR_SIGNING_KEY`には最低32 ByteのRandom ValueをBase64化した値を設定する。

macOSでの生成例:

```bash
export ALICE_CURSOR_SIGNING_KEY="$(openssl rand -base64 32)"
```

実際のKeyをDocument、Source Code、Git RepositoryまたはLogへ記載しない。

---

## 7. Create Local Table

本手順はDynamoDB Localへのみ実行する。`--endpoint-url`を省略してはならない。

```bash
AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local \
aws dynamodb create-table \
  --table-name project-alice-conversation-local \
  --attribute-definitions \
    AttributeName=pk,AttributeType=S \
    AttributeName=sk,AttributeType=S \
  --key-schema \
    AttributeName=pk,KeyType=HASH \
    AttributeName=sk,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --region ap-northeast-1 \
  --endpoint-url http://localhost:8000
```

Tableが既に存在する場合は作成し直さず、次SectionのCommandでSchemaを確認する。

TTLを有効化する。

```bash
AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local \
aws dynamodb update-time-to-live \
  --table-name project-alice-conversation-local \
  --time-to-live-specification \
    Enabled=true,AttributeName=expiresAtEpochSeconds \
  --region ap-northeast-1 \
  --endpoint-url http://localhost:8000
```

TTLの物理削除完了をLocal Testで待機しない。Failed Idempotencyの期限判定はApplicationのLogical Expirationでテストする。

---

## 8. Verify Local Table

Table一覧:

```bash
AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local \
aws dynamodb list-tables \
  --region ap-northeast-1 \
  --endpoint-url http://localhost:8000
```

Table Schema:

```bash
AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local \
aws dynamodb describe-table \
  --table-name project-alice-conversation-local \
  --region ap-northeast-1 \
  --endpoint-url http://localhost:8000
```

TTL Configuration:

```bash
AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local \
aws dynamodb describe-time-to-live \
  --table-name project-alice-conversation-local \
  --region ap-northeast-1 \
  --endpoint-url http://localhost:8000
```

確認対象:

- Table Nameが`project-alice-conversation-local`
- Partition Keyが`pk` / String
- Sort Keyが`sk` / String
- GSI / LSIが存在しない
- TTL Attributeが`expiresAtEpochSeconds`

---

## 9. Stop and Restart

Container停止:

```bash
docker compose -f backend/compose.yaml stop dynamodb-local
```

再起動:

```bash
docker compose -f backend/compose.yaml start dynamodb-local
```

Containerを削除してもVolumeは維持する:

```bash
docker compose -f backend/compose.yaml down
```

---

## 10. Reset Local Data

以下はLocal Conversation Historyを含むDynamoDB Localの全データを削除する。必要な場合だけ実行する。

```bash
docker compose -f backend/compose.yaml down -v
```

削除後はContainer起動とTable作成をやり直す。

---

## 11. Troubleshooting

### 11.1 Port 8000 Is Already in Use

```bash
lsof -nP -iTCP:8000 -sTCP:LISTEN
```

別ProcessがPort 8000を使用している場合、対象Processを確認してから停止する。独自判断でProcessを強制終了しない。

### 11.2 Container Does Not Start

```bash
docker compose -f backend/compose.yaml ps
docker compose -f backend/compose.yaml logs dynamodb-local
```

以下を確認する。

- Docker Engineが起動している
- Image Tagが`3.3.0`
- Port 8000が空いている
- Docker Volumeを書き込める

### 11.3 Backend Cannot Connect

以下を確認する。

- `ALICE_DYNAMODB_ENDPOINT=http://localhost:8000`
- DynamoDB Local Containerが起動中
- Local Profileが有効
- Dummy CredentialとRegionが設定済み
- Tableが作成済み

### 11.4 ResourceNotFoundException

`describe-table`でTable NameとEndpointを確認する。

Spring BootのTable NameとLocal Table Nameが一致していない場合、Tableを勝手に再作成せずConfigurationを修正する。

---

## 12. DynamoDB Local Limitations

DynamoDB LocalはDevelopment / Test用であり、AWS DynamoDBのすべてのBehaviorを完全に再現しない。

特に以下をLocal Testだけで保証しない。

- Transaction Conflict
- AWS実環境のLatency / Throttling
- IAM Permission
- PITR
- Cloud Monitoring
- TTLの物理削除Timing

Transaction ConflictはFake / Mock Testで補い、AWS固有Behaviorは必要になった段階で限定的なSmoke Testを実行する。

---

## 13. Security Rules

- DynamoDB Localは`127.0.0.1`にだけBindする
- Public Internetへ公開しない
- Local ProfileでLoopback以外のEndpointを拒否する
- 実AWS CredentialをCompose Fileへ記載しない
- Cursor Signing KeyをCommitしない
- Conversation ContentをSetup Logへ出力しない
- Table Setup Commandで`--endpoint-url http://localhost:8000`を省略しない

---

## 14. References

- AWS DynamoDB Local Documentation
- AWS DynamoDB Local Usage Notes
- Amazon DynamoDB Local Docker Image
- `database-design.md`
- `development-environment.md`
- `security-design.md`
