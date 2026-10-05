#!/usr/bin/env bash
# Runs the three demo scenarios against the API and saves the evidence in docs/scenarios/.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE=http://localhost:8080
OUT=docs/scenarios
mkdir -p "$OUT"

pretty()    { python3 -m json.tool 2>/dev/null || cat; }
run_id()    { grep -o '"id":"[0-9a-f-]*"' | head -1 | cut -d'"' -f4; }
status_of() { grep -o '"status":"[A-Z_]*"' | head -1 | cut -d'"' -f4; }
stages()    { grep -o '"stage":"[A-Z_]*","status":"[A-Z_]*"' \
              | sed -E 's/"stage":"([A-Z_]+)","status":"([A-Z_]+)"/\1=\2/' | tr '\n' ' '; }

post() {
  local body="${2:-}"
  if [ -z "$body" ]; then body='{}'; fi
  curl -sS -X POST "$BASE$1" -H 'Content-Type: application/json' -d "$body"
}

save() {
  { echo '{"run":'; curl -sS "$BASE/runs/$2"; echo ',"audit":'; curl -sS "$BASE/runs/$2/audit"; echo '}'; } \
    | pretty > "$OUT/$1.json"
  echo "  evidence saved: $OUT/$1.json"
}

# ---- start the app if it is not already running
STARTED=0
if ! curl -sf "$BASE/actuator/health" >/dev/null 2>&1; then
  echo "Building and starting the app..."
  ./mvnw -q -DskipTests package
  JAR=$(ls target/*.jar | grep -v '\.original$' | head -1)
  java -jar "$JAR" > target/app.log 2>&1 &
  APP_PID=$!
  STARTED=1
  trap '[ "$STARTED" = 1 ] && kill "$APP_PID" 2>/dev/null || true' EXIT
  for _ in $(seq 1 60); do
    curl -sf "$BASE/actuator/health" >/dev/null 2>&1 && break
    sleep 1
  done
  curl -sf "$BASE/actuator/health" >/dev/null 2>&1 || { echo "App did not start, see target/app.log"; exit 1; }
fi

APPROVE='{"actor":"leticia","note":"approved after review"}'

echo
echo "=== Scenario 1: greenfield ==="
R=$(post /runs '{"requirement":"Build a URL shortener service with create, redirect and click analytics","type":"GREENFIELD"}')
ID=$(echo "$R" | run_id)
echo "  run $ID started -> $(echo "$R" | status_of)"
echo "  stages: $(echo "$R" | stages)"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/runs/$ID/stages/DESIGN_APPROVAL/approve" \
  -H 'Content-Type: application/json' -d '{"actor":"leticia","note":"password = hunter2hunter2"}')
echo "  approval note containing a secret -> HTTP $CODE (policy guard)"
R=$(post "/runs/$ID/stages/DESIGN_APPROVAL/approve" "$APPROVE")
echo "  design approved -> $(echo "$R" | status_of)"
R=$(post "/runs/$ID/stages/RELEASE_APPROVAL/approve" "$APPROVE")
echo "  release approved -> $(echo "$R" | status_of)"
save 01-greenfield "$ID"

echo
echo "=== Scenario 2: brownfield ==="
R=$(post /runs '{"requirement":"Add expiration to short links so they stop working after a given date","type":"BROWNFIELD"}')
ID=$(echo "$R" | run_id)
echo "  run $ID started -> $(echo "$R" | status_of)"
echo "  stages: $(echo "$R" | stages)"
post "/runs/$ID/stages/DESIGN_APPROVAL/approve" "$APPROVE" >/dev/null
R=$(post "/runs/$ID/stages/RELEASE_APPROVAL/approve" "$APPROVE")
echo "  finished -> $(echo "$R" | status_of)"
save 02-brownfield "$ID"

echo
echo "=== Scenario 3: ambiguous requirement ==="
R=$(post /runs '{"requirement":"Make it more secure","type":"GREENFIELD"}')
ID=$(echo "$R" | run_id)
echo "  run $ID started -> $(echo "$R" | status_of)"
echo "  stages: $(echo "$R" | stages)"
R=$(post "/runs/$ID/stages/CLARIFY/approve" '{"actor":"leticia","note":"Focus on input validation, SSRF protection and rate limiting"}')
echo "  human clarified the scope -> $(echo "$R" | status_of)"
R=$(post "/runs/$ID/stages/DESIGN_APPROVAL/reject" '{"actor":"leticia","note":"Design must also cover abuse of expired links"}')
echo "  design rejected, re-planned -> $(echo "$R" | status_of)"
echo "  stages: $(echo "$R" | stages)"
post "/runs/$ID/stages/DESIGN_APPROVAL/approve" "$APPROVE" >/dev/null
R=$(post "/runs/$ID/stages/RELEASE_APPROVAL/approve" "$APPROVE")
echo "  finished -> $(echo "$R" | status_of)"
save 03-ambiguous "$ID"

echo
echo "=== Extra: safe-stop ==="
R=$(post /runs '{"requirement":"Build a URL shortener service with create and redirect","type":"GREENFIELD"}')
ID=$(echo "$R" | run_id)
R=$(post "/runs/$ID/stop" '{"actor":"leticia"}')
echo "  stop requested -> $(echo "$R" | status_of)"
save 04-safe-stop "$ID"

echo
curl -sS "$BASE/metrics" | pretty > "$OUT/metrics.json"
echo "Metrics saved: $OUT/metrics.json"
cat "$OUT/metrics.json"
