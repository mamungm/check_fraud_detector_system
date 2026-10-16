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
cd services/rules-service && ./gradlew clean bootJar
```

### Build jar for Fraud Detector orchestrator service:

```bash
cd services/fraud-detector && ./gradlew clean bootJar
```

### Start the system:
Check if the docker daemon is running (if not running, then run the Docker daemon). Then run the following command to start the system:
```bash
docker compose up --build
```

Example request:

```bash
curl -X POST http://localhost:8080/api/deposit_request \
  -H "Content-Type: application/json" \
  -d @examples/deposit_event_05_counterfeit_mobile.json
```

Posting a file directly reuses its tokens and hashes, so a second post of the same file looks like a repeat of the first.
To send fresh data every time (new tokens and hashes, timestamp set to now), use the scripts in `examples/`:

```bash
./examples/send_all.sh                  # all 10 examples, in order
./examples/collect_results.sh           # one example, then prints each service's stored result
```

The examples use real check images and amounts from `synthetic-data/out`, so the OCR amount matches the image.
Examples 01, 02 and 04 share an account (04 adds a new device), and 09 and 10 are the same check deposited twice.

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


### Logs

```bash
mkdir -p logs && docker compose logs --no-color --timestamps > logs/all.log 2>&1
```