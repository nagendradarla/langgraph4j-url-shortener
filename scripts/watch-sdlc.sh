#!/usr/bin/env bash
# watch-sdlc.sh — monitor the latest (or given) SDLC run in a second terminal.
#
#   Terminal A:  ./scripts/sdlc.sh -f requirements/feature-bulk-shorten.txt
#   Terminal B:  ./scripts/watch-sdlc.sh
#   Terminal B:  ./scripts/watch-sdlc.sh -f          # follow audit.jsonl only
#   Terminal B:  ./scripts/watch-sdlc.sh runs/<id>
#
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FOLLOW=0
RUN_ARG=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      cat <<'EOF'
Usage:
  ./scripts/watch-sdlc.sh              Dashboard of the latest run (refreshes)
  ./scripts/watch-sdlc.sh -f           Tail audit.jsonl of the latest run
  ./scripts/watch-sdlc.sh <runDir>     Same, for a specific runs/<threadId>

Start this in a second terminal while sdlc.sh is running. It does not
resume the graph or approve HITL.
EOF
      exit 0
      ;;
    -f|--follow)
      FOLLOW=1
      shift
      ;;
    *)
      RUN_ARG="$1"
      shift
      ;;
  esac
done

latest_run() {
  ls -1dt runs/*/ 2>/dev/null | head -1 || true
}

wait_for_run() {
  local found=""
  if [[ -n "$RUN_ARG" ]]; then
    found="$RUN_ARG"
  else
    echo "Waiting for runs/<threadId>/ ..." >&2
    while true; do
      found="$(latest_run)"
      if [[ -n "$found" && -d "$found" ]]; then
        break
      fi
      sleep 1
    done
  fi
  if [[ ! -d "$found" ]]; then
    echo "Run directory not found: $found" >&2
    exit 1
  fi
  (cd "$found" && pwd)
}

marker() {
  local path="$1"
  if [[ -e "$path" ]]; then
    echo "yes"
  else
    echo "—"
  fi
}

dashboard() {
  local run="$1"
  printf '\033[2J\033[H'
  echo "======== SDLC watch ========"
  echo "runDir: $run"
  echo "time:   $(date '+%H:%M:%S')"
  echo
  echo "artifacts:"
  printf "  spec.json      %s\n" "$(marker "$run/spec.json")"
  printf "  tasks.json     %s\n" "$(marker "$run/tasks.json")"
  printf "  HITL_GATE.md   %s\n" "$(marker "$run/HITL_GATE.md")"
  printf "  PR.md          %s\n" "$(marker "$run/PR.md")"
  printf "  metrics.json   %s\n" "$(marker "$run/metrics.json")"
  echo
  echo "latest node:"
  if [[ -s "$run/audit.jsonl" ]]; then
    tail -n 1 "$run/audit.jsonl"
  else
    echo "  (waiting for first audit line — ingest)"
  fi
  echo
  echo "audit (last 16):"
  if [[ -s "$run/audit.jsonl" ]]; then
    tail -n 16 "$run/audit.jsonl" | sed 's/^/  /'
  else
    echo "  (empty)"
  fi
  echo
  echo "workspace Java:"
  if [[ -d "$run/workspace/com/example/shortener" ]]; then
    ls -1 "$run/workspace/com/example/shortener"/*.java 2>/dev/null \
      | xargs -n1 basename 2>/dev/null | sed 's/^/  /' || echo "  (none yet)"
  else
    echo "  (none yet)"
  fi
  echo
  echo "Ctrl-C to stop. Orchestrator stdout still has [sdlc] lines."
}

RUN="$(wait_for_run)"
mkdir -p "$RUN"
: >> "$RUN/audit.jsonl"

if [[ "$FOLLOW" -eq 1 ]]; then
  echo "======== SDLC watch ========"
  echo "runDir: $RUN"
  echo "----- audit.jsonl -----"
  exec tail -n +1 -f "$RUN/audit.jsonl"
fi

trap 'echo; exit 0' INT
while true; do
  dashboard "$RUN"
  sleep 2
done
