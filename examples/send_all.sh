#!/usr/bin/env bash
# Posts every examples/deposit_event_*.json to the fraud-detector API.
cd "$(dirname "$0")"
for f in deposit_event_*.json; do
  echo "== $f"
  curl -s -X POST http://localhost:8080/api/deposit_request -H "Content-Type: application/json" -d @"$f"
  echo
done
