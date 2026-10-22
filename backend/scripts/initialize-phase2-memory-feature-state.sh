#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  initialize-phase2-memory-feature-state.sh \
    --endpoint-url http://localhost:8000 \
    [--table-name project-alice-memory-local]

This script initializes only the Phase 2 Memory Feature State item
in DynamoDB Local. It never creates Memory Preferences defaults.
USAGE
}

ENDPOINT=""
TABLE="project-alice-memory-local"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --endpoint-url)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      ENDPOINT="$2"
      shift 2
      ;;
    --table-name)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      TABLE="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ -z "$ENDPOINT" ]]; then
  echo "ERROR: --endpoint-url is required." >&2
  usage
  exit 2
fi

# This setup script is strictly for local development.
if ! python3 - "$ENDPOINT" <<'PY'
import sys
from urllib.parse import urlparse

url = urlparse(sys.argv[1])
valid = (
    url.scheme == "http"
    and url.hostname in ("localhost", "127.0.0.1")
    and url.port == 8000
    and not url.username
    and not url.password
    and not url.query
    and not url.fragment
    and url.path in ("", "/")
)
sys.exit(0 if valid else 1)
PY
then
  echo "ERROR: Endpoint must be http://localhost:8000 or http://127.0.0.1:8000." >&2
  exit 2
fi

if [[ -z "$TABLE" ]]; then
  echo "ERROR: Table name must not be empty." >&2
  exit 2
fi

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-local}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-local}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-1}"

AWS_ARGS=(
  --endpoint-url "$ENDPOINT"
  --region "$AWS_DEFAULT_REGION"
)

echo "===== 1. Verify DynamoDB Local table ====="

TABLE_STATUS="$(
  aws dynamodb describe-table \
    --table-name "$TABLE" \
    "${AWS_ARGS[@]}" \
    --query 'Table.TableStatus' \
    --output text
)"

if [[ "$TABLE_STATUS" != "ACTIVE" ]]; then
  echo "ERROR: Table is not ACTIVE: $TABLE_STATUS" >&2
  exit 1
fi

echo "Table: $TABLE"
echo "Status: $TABLE_STATUS"

echo
echo "===== 2. Create Feature State if absent ====="

CREATED_AT="$(
  python3 - <<'PY'
from datetime import datetime, timezone, timedelta

jst = timezone(timedelta(hours=9))
print(datetime.now(jst).isoformat(timespec="milliseconds"))
PY
)"

if aws dynamodb put-item \
  --table-name "$TABLE" \
  --item "{
    \"pk\": {\"S\": \"MEMORY_SETTINGS#GLOBAL\"},
    \"sk\": {\"S\": \"FEATURE_STATE\"},
    \"itemType\": {\"S\": \"MEMORY_FEATURE_STATE\"},
    \"schemaVersion\": {\"N\": \"1\"},
    \"preferencesInitialized\": {\"BOOL\": false},
    \"createdAt\": {\"S\": \"$CREATED_AT\"}
  }" \
  --condition-expression \
    "attribute_not_exists(#pk) AND attribute_not_exists(#sk)" \
  --expression-attribute-names \
    '{"#pk":"pk","#sk":"sk"}' \
  "${AWS_ARGS[@]}"; then
  echo "Feature State created."
else
  echo "ERROR: Feature State was not created." >&2
  echo "It may already exist. No existing item was overwritten." >&2
  echo "Inspect the current item before deciding what to do next." >&2
  exit 1
fi

echo
echo "===== 3. Verify created item ====="

aws dynamodb get-item \
  --table-name "$TABLE" \
  --key '{
    "pk": {"S": "MEMORY_SETTINGS#GLOBAL"},
    "sk": {"S": "FEATURE_STATE"}
  }' \
  --consistent-read \
  "${AWS_ARGS[@]}" \
  --output json

echo
echo "Initialization complete. Memory Preferences defaults were not created."
