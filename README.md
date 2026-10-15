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

### Generate synthetic CSVs:

```bash
pip install -r synthetic-data/requirements.txt
python synthetic-data/generate_synthetic_data.py --out synthetic-data/out --banks 5 --events 2000
```

### Generate training data for ML model:

```bash
python synthetic-data/generate_training_table.py --out synthetic-data/out
```

### Train ML model:

```bash
cd services/ml-service
pip install -r requirements.txt
python train_model_check_fraud.py
python train_model_in_clearing.py
```

The trained models will be saved in `services/ml-service/models/`. There will be one model for check fraud and one model for in-clearing risk scoring.
For details on the models, see the [ML service README](services/ml-service/README.md).

### Build jar for Rule Service:

```bash
cd services/rules-service && ./gradlew bootJar
cd ../fraud-detector && ./gradlew bootJar
cd ../.. && docker compose up --build
```

### Build jar for Fraud Detector orchestrator service:

```bash
cd services/fraud-detector && ./gradlew bootJar
cd ../.. && docker compose up --build
```

### Start the system:
Check if the docker daemon is running (if not running, then run the Docker daemon). Then run the following command to start the system:
```bash
docker compose up --build
```

Example request:

```bash
curl -X POST http://localhost:8080/api/deposits \
  -H "Content-Type: application/json" \
  -d @examples/deposit_event.json
```

### Down the containers and remove locally built images:

```bash
docker compose down -v --remove-orphans
docker volume prune -f
docker volume ls
```

## Decision policy

```text
score >= 0.85 OR duplicate presentment => HOLD
score >= 0.60 OR consortium high risk => MANUAL_REVIEW
else PASS
```

## Privacy model

The consortium layer stores HMAC tokens, aggregate counts, risk scores, and fraud metadata. It does not store raw cross-institution PII.
