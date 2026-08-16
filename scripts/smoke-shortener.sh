#!/usr/bin/env bash
# Quick manual smoke of the shortener. Server must already be running:
#   mvn -q exec:java -Dexec.args="serve 8080"

set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

echo "== health =="
curl -sS "$BASE_URL/health"
echo

echo "== ready =="
curl -sS -w "\nHTTP %{http_code}\n" "$BASE_URL/ready"

echo "== shorten =="
CODE="$(curl -sS -X POST --data "https://example.com/long" "$BASE_URL/shorten")"
echo "code=$CODE"

echo "== resolve (headers, no follow) =="
curl -sS -D - -o /dev/null "$BASE_URL/$CODE"

echo "== stats after one follow =="
curl -sS "$BASE_URL/stats/$CODE"
echo

echo "== reject dangerous scheme =="
curl -sS -w "\nHTTP %{http_code}\n" -X POST --data "javascript:alert(1)" "$BASE_URL/shorten"
