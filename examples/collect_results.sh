#!/usr/bin/env bash
# Posts one example deposit and prints every service's stored result from the database.
# Usage: ./examples/collect_results.sh [examples/deposit_event_05_counterfeit_mobile.json]
set -euo pipefail
cd "$(dirname "$0")/.."
FILE="${1:-examples/deposit_event_05_counterfeit_mobile.json}"
PSQL=(docker exec -i postgres psql -U fraud -d frauddb -At)

RUN="${RUN_ID:-$(date +%m%d%H%M%S)}"
RESP=$(python3 examples/_prepare.py "$FILE" "$RUN" | curl -sS -X POST http://localhost:8080/api/deposit_request \
  -H "Content-Type: application/json" -d @-)
echo "== POST response =="; echo "$RESP"
EVENT_ID=$(echo "$RESP" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["depositEventList"][0]["eventId"])' 2>/dev/null || true)
[ -n "$EVENT_ID" ] || { echo "no eventId found in response"; exit 1; }
echo "eventId = $EVENT_ID"

echo "waiting for all services (up to 90s)..."
for _ in $(seq 1 45); do
  DONE=$("${PSQL[@]}" -c "select image_completed and consortium_completed and ml_completed and rule_completed from tbl_fraud_detection_workflow where event_id='$EVENT_ID'" || true)
  [ "$DONE" = "t" ] && break
  sleep 2
done

q() { "${PSQL[@]}" -c "$1"; }
pretty() { # column of JSON text -> indented JSON
  q "select jsonb_pretty(${1}::jsonb) from tbl_fraud_detection_workflow where event_id='$EVENT_ID'" || q "select $1 from tbl_fraud_detection_workflow where event_id='$EVENT_ID'"
}

echo; echo "== workflow flags =="
q "select 'image='||image_completed||' consortium='||consortium_completed||' ml='||ml_completed||' rule='||rule_completed from tbl_fraud_detection_workflow where event_id='$EVENT_ID'"
echo; echo "== deposit state =="
q "select workflow from deposit_event where event_id='$EVENT_ID'"
echo; echo "== consortium result =="; pretty consortium_result
echo; echo "== image result ==";      pretty image_result
echo; echo "== rule result ==";       pretty rule_result
echo; echo "== ml result ==";         pretty ml_result
