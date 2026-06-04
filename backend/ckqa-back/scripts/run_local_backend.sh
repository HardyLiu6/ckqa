#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${APP_DIR}/../.." && pwd)"

ENV_FILE="${CKQA_BACK_ENV_FILE:-${APP_DIR}/.env}"
SERVER_PORT_OVERRIDE=""
MAILER_TYPE_OVERRIDE=""
DRY_RUN=false
MVNW_ARGS=()

usage() {
    cat <<'EOF'
Usage: scripts/run_local_backend.sh [options] [-- mvnw-args...]

Options:
  --env-file PATH       Load env file after stripping CRLF line endings.
  --port PORT           Override SERVER_PORT for the Spring Boot process.
  --mailer-type TYPE    Override CKQA_EMAIL_MAILER_TYPE, e.g. log or smtp.
  --dry-run             Print sanitized startup variables and exit.
  -h, --help            Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file)
            ENV_FILE="${2:?--env-file requires a path}"
            shift 2
            ;;
        --port)
            SERVER_PORT_OVERRIDE="${2:?--port requires a value}"
            shift 2
            ;;
        --mailer-type)
            MAILER_TYPE_OVERRIDE="${2:?--mailer-type requires a value}"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --)
            shift
            MVNW_ARGS+=("$@")
            break
            ;;
        *)
            MVNW_ARGS+=("$1")
            shift
            ;;
    esac
done

load_env_file() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        return 0
    fi

    local line key value
    # 兼容 CRLF 与包含空格/中文的未引用值；不执行 .env 内容，避免把值当 shell 命令。
    while IFS= read -r line || [[ -n "$line" ]]; do
        line="${line%$'\r'}"
        [[ -z "$line" || "$line" == \#* || "$line" != *=* ]] && continue
        key="${line%%=*}"
        value="${line#*=}"
        key="${key//[[:space:]]/}"
        [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
        value="${value%$'\r'}"
        if [[ "$value" == \"*\" && "$value" == *\" ]]; then
            value="${value:1:${#value}-2}"
        elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
            value="${value:1:${#value}-2}"
        fi
        export "$key=$value"
    done < "$file"
}

load_env_file "$ENV_FILE"

if [[ -n "$SERVER_PORT_OVERRIDE" ]]; then
    export SERVER_PORT="$SERVER_PORT_OVERRIDE"
else
    export SERVER_PORT="${SERVER_PORT:-8080}"
fi

if [[ -n "$MAILER_TYPE_OVERRIDE" ]]; then
    export CKQA_EMAIL_MAILER_TYPE="$MAILER_TYPE_OVERRIDE"
else
    export CKQA_EMAIL_MAILER_TYPE="${CKQA_EMAIL_MAILER_TYPE:-log}"
fi

export PDF_INGEST_ROOT="${PDF_INGEST_ROOT:-${REPO_ROOT}/pdf_ingest}"
export GRAPHRAG_ROOT="${GRAPHRAG_ROOT:-${REPO_ROOT}/graphrag_pipeline}"
export GRAPHRAG_OUTPUT_DIR="${GRAPHRAG_OUTPUT_DIR:-${REPO_ROOT}/graphrag_pipeline/output}"
export GRAPHRAG_STORAGE_DIR="${GRAPHRAG_STORAGE_DIR:-${GRAPHRAG_OUTPUT_DIR}}"
export GRAPHRAG_LANCEDB_URI="${GRAPHRAG_LANCEDB_URI:-${REPO_ROOT}/graphrag_pipeline/output/lancedb}"
export GRAPHRAG_BUILD_RUNS_ROOT="${GRAPHRAG_BUILD_RUNS_ROOT:-${REPO_ROOT}/graphrag_pipeline/runtime/kb-build-runs}"
export GRAPHRAG_API_BASE_URL="${GRAPHRAG_API_BASE_URL:-http://127.0.0.1:8012}"
export CKQA_NEO4J_TOPIC_BINDING_TIMEOUT_MS="${CKQA_NEO4J_TOPIC_BINDING_TIMEOUT_MS:-3000}"

if [[ "$DRY_RUN" == true ]]; then
    printf 'ENV_FILE=%q\n' "$ENV_FILE"
    printf 'SERVER_PORT=%q\n' "$SERVER_PORT"
    printf 'CKQA_EMAIL_MAILER_TYPE=%q\n' "$CKQA_EMAIL_MAILER_TYPE"
    printf 'PDF_INGEST_ROOT=%q\n' "$PDF_INGEST_ROOT"
    printf 'GRAPHRAG_ROOT=%q\n' "$GRAPHRAG_ROOT"
    printf 'GRAPHRAG_API_BASE_URL=%q\n' "$GRAPHRAG_API_BASE_URL"
    printf 'CKQA_NEO4J_TOPIC_BINDING_TIMEOUT_MS=%q\n' "$CKQA_NEO4J_TOPIC_BINDING_TIMEOUT_MS"
    exit 0
fi

cd "$APP_DIR"
exec ./mvnw spring-boot:run "${MVNW_ARGS[@]}"
