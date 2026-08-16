#!/usr/bin/env bash
# Exercise UrlShortenerServer APIs.
#
# Start the server in another terminal:
#   mvn -q exec:java -Dexec.args="serve 8080"
#
# Then:
#   ./scripts/test-shortener-api.sh
#   BASE_URL=http://127.0.0.1:9090 ./scripts/test-shortener-api.sh

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
PASS=0
FAIL=0

# Writes headers to $1, body to $2; prints HTTP status.
http() {
  local headers_file="$1"
  local body_file="$2"
  shift 2
  curl -sS --max-time 5 -D "$headers_file" -o "$body_file" "$@"
  awk 'NR==1 {print $2}' "$headers_file"
}

assert() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  local extra="${4-}"
  if [[ "$actual" == "$expected" ]]; then
    printf 'PASS  %s\n' "$name"
    [[ -n "$extra" ]] && printf '      %s\n' "$extra"
    PASS=$((PASS + 1))
  else
    printf 'FAIL  %s  expected %s, got %s\n' "$name" "$expected" "$actual"
    FAIL=$((FAIL + 1))
  fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
H="$TMP/headers"
B="$TMP/body"

echo "Base URL: $BASE_URL"
echo

status="$(http "$H" "$B" "$BASE_URL/health")"
assert "GET /health → 200" "200" "$status" "$(cat "$B")"

status="$(http "$H" "$B" "$BASE_URL/ready")"
assert "GET /ready → 200" "200" "$status" "$(cat "$B")"

status="$(http "$H" "$B" -X POST --data "https://example.com/long/path" "$BASE_URL/shorten")"
CODE="$(cat "$B")"
assert "POST /shorten valid https → 200" "200" "$status" "code=$CODE"

status="$(http "$H" "$B" -X POST --data "https://example.com/long/path" "$BASE_URL/shorten")"
CODE2="$(cat "$B")"
assert "POST /shorten same URL is idempotent → 200" "200" "$status"
assert "idempotent code matches" "$CODE" "$CODE2"

status="$(http "$H" "$B" "$BASE_URL/stats/$CODE")"
assert "GET /stats/{code} before follow → 200 / 0" "200" "$status"
assert "clicks before follow" "0" "$(cat "$B")"

status="$(http "$H" "$B" "$BASE_URL/$CODE")"
LOC="$(awk 'tolower($1)=="location:" {print $2}' "$H" | tr -d '\r')"
assert "GET /{code} → 302" "302" "$status" "Location=$LOC"
assert "Location is original URL" "https://example.com/long/path" "$LOC"

status="$(http "$H" "$B" "$BASE_URL/stats/$CODE")"
assert "GET /stats/{code} after follow → 1" "200" "$status"
assert "clicks after follow" "1" "$(cat "$B")"

status="$(http "$H" "$B" -X POST --data "javascript:alert(1)" "$BASE_URL/shorten")"
assert "POST /shorten javascript: → 400" "400" "$status" "$(cat "$B")"

status="$(http "$H" "$B" -X POST --data "file:///etc/passwd" "$BASE_URL/shorten")"
assert "POST /shorten file: → 400" "400" "$status"

status="$(http "$H" "$B" -X POST --data "not-a-url" "$BASE_URL/shorten")"
assert "POST /shorten invalid → 400" "400" "$status"

status="$(http "$H" "$B" "$BASE_URL/shorten")"
assert "GET /shorten → 405" "405" "$status"

status="$(http "$H" "$B" "$BASE_URL/does-not-exist")"
assert "GET /unknown-code → 404" "404" "$status"

status="$(http "$H" "$B" "$BASE_URL/stats/does-not-exist")"
assert "GET /stats/unknown → 404 (not 0)" "404" "$status"

status="$(http "$H" "$B" "$BASE_URL/")"
assert "GET / missing code → 400" "400" "$status"

echo
echo "Passed: $PASS  Failed: $FAIL"
[[ "$FAIL" -eq 0 ]]
