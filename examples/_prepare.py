#!/usr/bin/env python3
"""Prints an example deposit JSON with a fresh timestamp and run-unique tokens/hashes.

Usage: _prepare.py <file.json> <run_id>
All files prepared with the same run_id stay linked (same account, device, duplicate hashes), but never collide
with data from earlier runs.
"""
import json
import sys
from datetime import datetime, timedelta, timezone

path, run = sys.argv[1], sys.argv[2]
doc = json.load(open(path))
for key in ("accountToken", "payeeToken", "payorToken", "deviceToken",
            "checkSerialHash", "micrRoutingHash", "micrAccountHash"):
    if doc.get(key):
        doc[key] = f"{doc[key]}_{run}"
doc["depositTimestamp"] = datetime.now(timezone(timedelta(hours=-2, minutes=-30))).replace(microsecond=0).isoformat()
print(json.dumps(doc))
