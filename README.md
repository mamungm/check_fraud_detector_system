# Check Fraud Detector System
Event-driven check fraud detection system with microservices, feature pipelines, and ML models for deposit and in-clearing risk scoring.

## Included

- Spring Boot API: deposit ingest, alert/case workflow, rules engine, Kafka publish
- PostgreSQL schema: events, scores, rule hits, alerts, cases, audit
- Python FastAPI services:
  - ML scoring
  - image analysis placeholder
  - privacy-preserving consortium matching
- Synthetic data generator for 3–5 banks and fraud scenarios
- Docker Compose for PostgreSQL, Kafka, API, ML, image, consortium, Prometheus, Grafana

## Quick start

```bash
docker compose up --build
```

Generate synthetic CSVs:

```bash
python synthetic-data/generate_synthetic_data.py --out synthetic-data/out --banks 5 --events 2000
```

Example request:

```bash
curl -X POST http://localhost:8080/api/deposits \
  -H "Content-Type: application/json" \
  -d @examples/deposit_event.json
```

## Decision policy

```text
score >= 0.85 OR duplicate presentment => HOLD
score >= 0.60 OR consortium high risk => MANUAL_REVIEW
else PASS
```

## Privacy model

The consortium layer stores HMAC tokens, aggregate counts, risk scores, and fraud metadata. It does not store raw cross-institution PII.
