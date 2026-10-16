#!/usr/bin/env bash
# Posts every examples/deposit_event_*.json (in order) to the fraud-detector API.
# Tokens and hashes get a per-run suffix and the timestamp is set to now, so reruns never collide with earlier data.
# Examples 01, 02 and 04 share an account; 09 and 10 are the same check deposited twice.
cd "$(dirname "$0")"
RUN="${RUN_ID:-$(date +%m%d%H%M%S)}"
echo "run id: $RUN"
for f in deposit_event_*.json; do
  echo "== $f"
  python3 _prepare.py "$f" "$RUN" | curl -s -X POST http://localhost:8080/api/deposit_request \
    -H "Content-Type: application/json" -d @-
  echo
  sleep 3   # let each event finish so the next one sees it in the database
done
