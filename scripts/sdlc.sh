#!/usr/bin/env bash
# sdlc.sh — run a feature or bugfix through the gated LangGraph4j SDLC
# (understand → decompose → implement → SAST/tests → HITL).
# Cursor agents write code; this script never git-commits.
#
#   ./scripts/sdlc.sh -r "Add GET /metrics for links and capacity"
#   ./scripts/sdlc.sh -f requirements/feature-bulk-shorten.txt
#   ./scripts/sdlc.sh requirements/feature-optional-ttl.txt
#   ./scripts/sdlc.sh --auto-approve -f requirements/feature-bulk-shorten.txt
#   ./scripts/sdlc.sh --apply -r "Fix unknown stats returning 0 instead of 404"

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/sdlc.sh -r|--requirement <text>     Feature or bugfix as text
  ./scripts/sdlc.sh -f|--file <path>            Feature or bugfix from a text file
  ./scripts/sdlc.sh <path.txt>                  Same as --file if the path exists

Options:
  --interactive     Pause for clarify/HITL (default)
  --auto-approve    Auto-answer clarify=memory and HITL=approve
  --apply           After HITL approve, copy workspace Java into src/main/java
  --clarify <text>  Used with --auto-approve when the graph asks to clarify

Put CURSOR_API_KEY in .env (see .env.example).
EOF
}

REQUIREMENT=""
REQUIREMENT_FILE=""
HITL_MODE="--interactive"
APPLY=""
CLARIFY="memory"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    -r|--requirement)
      if [[ $# -lt 2 || -z "${2:-}" || "$2" == -* ]]; then
        echo "$1 requires requirement text." >&2
        exit 1
      fi
      REQUIREMENT="$2"
      shift 2
      ;;
    -f|--file)
      if [[ $# -lt 2 || -z "${2:-}" || "$2" == -* ]]; then
        echo "$1 requires a file path." >&2
        exit 1
      fi
      REQUIREMENT_FILE="$2"
      shift 2
      ;;
    --interactive)
      HITL_MODE="--interactive"
      shift
      ;;
    --auto-approve)
      HITL_MODE="--auto-approve"
      shift
      ;;
    --apply)
      APPLY="--apply"
      shift
      ;;
    --clarify)
      CLARIFY="${2:-memory}"
      shift 2
      ;;
    --)
      shift
      break
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
    *)
      if [[ -z "$REQUIREMENT_FILE" && -z "$REQUIREMENT" && -f "$1" ]]; then
        REQUIREMENT_FILE="$1"
        shift
      else
        echo "Unexpected argument: $1" >&2
        usage
        exit 1
      fi
      ;;
  esac
done

if [[ -z "${CURSOR_API_KEY:-}" ]]; then
  echo "CURSOR_API_KEY is not set. Add it to .env (see .env.example)" >&2
  exit 1
fi

if [[ -n "$REQUIREMENT_FILE" && -n "$REQUIREMENT" ]]; then
  echo "Use either --requirement or --file, not both." >&2
  exit 1
fi

if [[ -n "$REQUIREMENT_FILE" ]]; then
  if [[ ! -f "$REQUIREMENT_FILE" ]]; then
    echo "Requirement file not found: $REQUIREMENT_FILE" >&2
    exit 1
  fi
  REQUIREMENT_FILE="$(cd "$(dirname "$REQUIREMENT_FILE")" && pwd)/$(basename "$REQUIREMENT_FILE")"
elif [[ -n "$REQUIREMENT" ]]; then
  # Maven splits exec.args on spaces; always pass a file so punctuation stays intact.
  REQUIREMENT_FILE="$(mktemp "${TMPDIR:-/tmp}/sdlc-req.XXXXXX")"
  printf '%s\n' "$REQUIREMENT" > "$REQUIREMENT_FILE"
  trap 'rm -f "$REQUIREMENT_FILE"' EXIT
else
  usage
  exit 1
fi

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null || true)}"
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}/opt/homebrew/bin:/usr/local/bin:$PATH"

if [[ ! -d scripts/cursor-worker/node_modules/@cursor/sdk ]]; then
  (cd scripts/cursor-worker && npm install)
fi

echo "======== SDLC change ========"
if [[ -n "$REQUIREMENT" ]]; then
  echo "requirement: $REQUIREMENT"
else
  echo "requirement-file: $REQUIREMENT_FILE"
fi
echo "-----"
cat "$REQUIREMENT_FILE"
echo "-----"
echo "mode: $HITL_MODE ${APPLY}"
echo

# exec:java skips compile; pass the file as a system property so Maven cannot drop it.
EXEC_ARGS="run live --requirement-file ${REQUIREMENT_FILE} ${HITL_MODE} --clarify ${CLARIFY} ${APPLY}"
mvn -q compile exec:java \
  "-Dagentic.requirementFile=${REQUIREMENT_FILE}" \
  "-Dexec.args=${EXEC_ARGS}"
