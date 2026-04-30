import time
from pathlib import Path
from typing import Any, Dict, List
import os

import joblib
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel

import asyncio
from contextlib import asynccontextmanager
from services.commons.kafka_event_handler import KafkaEventHandler
from services.commons.kafka_completion import CompletionReporter
from aiokafka import AIOKafkaProducer

@asynccontextmanager
async def lifespan(app: FastAPI):
    stop_event = asyncio.Event()

    bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    completion_topic = os.getenv("KAFKA_COMPLETION_TOPIC", "check.deposit.service.completed")

    producer = AIOKafkaProducer(bootstrap_servers=bootstrap)
    await producer.start()
    reporter = CompletionReporter(producer, topic=completion_topic, service_name="ml")

    class MlKafkaHandler(KafkaEventHandler):
        async def handle_event(self, event: dict):
            start = time.time()
            event_id = str(event.get("eventId", ""))
            try:
                req = DepositScoreRequest.model_validate(event)
                resp = await asyncio.to_thread(score_deposit, req)
                await reporter.report_success(
                    event_id=event_id or "UNKNOWN",
                    latency_ms=int((time.time() - start) * 1000),
                    details={
                        "fraudProbability": resp.fraudProbability,
                        "recommendedAction": resp.recommendedAction,
                        "riskBand": resp.calibratedRiskBand,
                    },
                )
            except Exception as e:
                await reporter.report_failure(
                    event_id=event_id or "UNKNOWN",
                    latency_ms=int((time.time() - start) * 1000),
                    error=str(e),
                )

    ml_kafka_event_handler = MlKafkaHandler(
        "ml_kafka",
        "check.deposit.created",
        group_id="ml-fraud-service",
        bootstrap_servers=bootstrap,
    )
    ml_kafka_task = asyncio.create_task(
        ml_kafka_event_handler.run_consumer(stop_event)
    )

    try:
        yield

    finally:
        stop_event.set()
        await ml_kafka_task
        await producer.stop()

app = FastAPI(title="ml-service", lifespan=lifespan)


BASE_DIR = Path(__file__).resolve().parent
MODEL_DIR = BASE_DIR / "models"

DEPOSIT_MODEL_PATH = MODEL_DIR / "deposit_fraud_model.joblib"
IN_CLEARING_MODEL_PATH = MODEL_DIR / "in_clearing_fraud_model.joblib"


deposit_model = joblib.load(DEPOSIT_MODEL_PATH)
in_clearing_model = joblib.load(IN_CLEARING_MODEL_PATH)


DEPOSIT_FEATURES = [
    "amount",
    "account_deposit_count_7d",
    "account_avg_amount_30d",
    "account_new_device",
    "payee_seen_institutions_14d",
    "payee_fraud_count_30d",
    "device_fraud_count_30d",
    "image_duplicate_score",
    "ocr_amount_match",
    "layout_anomaly_score",
    "font_anomaly_score",
    "signature_presence_score",
    "endorsement_score",
    "rule_hit_count",
    "critical_rule_hit_count",
    "return_code_present",
    "chargeback_present",
    "loss_amount",
    "channel",
]


IN_CLEARING_FEATURES = [
    "deposit_score",
    "days_since_deposit",
    "clearing_amount_matches_deposit",
    "drawee_bank_prior_fraud_30d",
    "deposit_to_clearing_bank_risk",
    "return_code_present",
    "chargeback_present",
    "loss_amount",
    "channel",
]


class DepositScoreRequest(BaseModel):
    eventId: str
    institutionId: str

    amount: float
    channel: str

    account_deposit_count_7d: float = 0
    account_avg_amount_30d: float = 0
    account_new_device: float = 0

    payee_seen_institutions_14d: float = 0
    payee_fraud_count_30d: float = 0
    device_fraud_count_30d: float = 0

    image_duplicate_score: float = 0
    ocr_amount_match: float = 1
    layout_anomaly_score: float = 0
    font_anomaly_score: float = 0
    signature_presence_score: float = 1
    endorsement_score: float = 1

    rule_hit_count: float = 0
    critical_rule_hit_count: float = 0

    return_code_present: float = 0
    chargeback_present: float = 0
    loss_amount: float = 0


class InClearingScoreRequest(BaseModel):
    eventId: str
    institutionId: str

    deposit_score: float = 0
    days_since_deposit: float = 0
    clearing_amount_matches_deposit: float = 1
    drawee_bank_prior_fraud_30d: float = 0
    deposit_to_clearing_bank_risk: float = 0

    return_code_present: float = 0
    chargeback_present: float = 0
    loss_amount: float = 0


class CombinedScoreRequest(DepositScoreRequest):
    days_since_deposit: float = 0
    clearing_amount_matches_deposit: float = 1
    drawee_bank_prior_fraud_30d: float = 0
    deposit_to_clearing_bank_risk: float = 0


class FraudScoreResponse(BaseModel):
    eventId: str
    fraudProbability: float
    calibratedRiskBand: str
    recommendedAction: str
    topFeatureContributions: List[Dict[str, Any]]
    latencyMs: int
    modelName: str
    modelVersion: str


class CombinedScoreResponse(BaseModel):
    eventId: str
    depositFraudProbability: float
    inClearingFraudProbability: float
    finalFraudProbability: float
    calibratedRiskBand: str
    recommendedAction: str
    topReasons: List[str]
    latencyMs: int
    explanation: Dict[str, Any]


def risk_band(prob: float) -> str:
    if prob >= 0.85:
        return "HIGH"
    if prob >= 0.60:
        return "MEDIUM"
    if prob >= 0.30:
        return "LOW"
    return "MINIMAL"


def recommended_action(prob: float) -> str:
    if prob >= 0.85:
        return "HOLD"
    if prob >= 0.60:
        return "MANUAL_REVIEW"
    return "PASS"


def make_dataframe(row: Dict[str, Any], features: List[str]) -> pd.DataFrame:
    return pd.DataFrame([{f: row.get(f, 0) for f in features}])


def score_model(model, row: Dict[str, Any], features: List[str]) -> float:
    x = make_dataframe(row, features)
    return float(model.predict_proba(x)[0, 1])


def approximate_feature_contributions(
    row: Dict[str, Any],
    feature_weights: Dict[str, float],
    top_k: int = 8
) -> List[Dict[str, Any]]:
    contributions = []

    for feature, weight in feature_weights.items():
        value = row.get(feature, 0)

        try:
            value = float(value)
        except Exception:
            continue

        if value > 0:
            contributions.append({
                "feature": feature,
                "value": value,
                "approxContribution": round(value * weight, 4)
            })

    return sorted(
        contributions,
        key=lambda x: x["approxContribution"],
        reverse=True
    )[:top_k]


DEPOSIT_EXPLANATION_WEIGHTS = {
    "critical_rule_hit_count": 0.30,
    "image_duplicate_score": 0.25,
    "payee_fraud_count_30d": 0.20,
    "device_fraud_count_30d": 0.20,
    "layout_anomaly_score": 0.15,
    "font_anomaly_score": 0.12,
    "account_new_device": 0.10,
    "account_deposit_count_7d": 0.08,
    "payee_seen_institutions_14d": 0.08,
    "return_code_present": 0.25,
    "chargeback_present": 0.25,
    "loss_amount": 0.15,
}


IN_CLEARING_EXPLANATION_WEIGHTS = {
    "deposit_score": 0.35,
    "clearing_amount_matches_deposit": -0.15,
    "days_since_deposit": 0.03,
    "drawee_bank_prior_fraud_30d": 0.20,
    "deposit_to_clearing_bank_risk": 0.30,
    "return_code_present": 0.35,
    "chargeback_present": 0.35,
    "loss_amount": 0.20,
}


def top_reason_strings(contributions: List[Dict[str, Any]]) -> List[str]:
    return [
        f"{c['feature']}={c['value']}"
        for c in contributions[:8]
    ]


@app.post("/score/deposit", response_model=FraudScoreResponse)
def score_deposit(req: DepositScoreRequest):
    start = time.time()

    row = req.model_dump()
    prob = score_model(deposit_model, row, DEPOSIT_FEATURES)
    contributions = approximate_feature_contributions(
        row,
        DEPOSIT_EXPLANATION_WEIGHTS
    )

    return FraudScoreResponse(
        eventId=req.eventId,
        fraudProbability=round(prob, 4),
        calibratedRiskBand=risk_band(prob),
        recommendedAction=recommended_action(prob),
        topFeatureContributions=contributions,
        latencyMs=int((time.time() - start) * 1000),
        modelName="deposit_fraud_xgboost",
        modelVersion="0.1.0"
    )


@app.post("/score/in-clearing", response_model=FraudScoreResponse)
def score_in_clearing(req: InClearingScoreRequest):
    start = time.time()

    row = req.model_dump()
    prob = score_model(in_clearing_model, row, IN_CLEARING_FEATURES)
    contributions = approximate_feature_contributions(
        row,
        IN_CLEARING_EXPLANATION_WEIGHTS
    )

    return FraudScoreResponse(
        eventId=req.eventId,
        fraudProbability=round(prob, 4),
        calibratedRiskBand=risk_band(prob),
        recommendedAction=recommended_action(prob),
        topFeatureContributions=contributions,
        latencyMs=int((time.time() - start) * 1000),
        modelName="in_clearing_fraud_xgboost",
        modelVersion="0.1.0"
    )


@app.post("/score/combined", response_model=CombinedScoreResponse)
def score_combined(req: CombinedScoreRequest):
    start = time.time()

    row = req.model_dump()

    deposit_prob = score_model(deposit_model, row, DEPOSIT_FEATURES)

    in_clearing_row = {
        "deposit_score": row.get("deposit_score", deposit_prob),
        "days_since_deposit": row.get("days_since_deposit", 0),
        "clearing_amount_matches_deposit": row.get("clearing_amount_matches_deposit", 1),
        "drawee_bank_prior_fraud_30d": row.get("drawee_bank_prior_fraud_30d", 0),
        "deposit_to_clearing_bank_risk": row.get("deposit_to_clearing_bank_risk", 0),
        "return_code_present": row.get("return_code_present", 0),
        "chargeback_present": row.get("chargeback_present", 0),
        "loss_amount": row.get("loss_amount", 0),
        "channel": row.get("channel", "branch"),
    }

    in_clearing_prob = score_model(
        in_clearing_model,
        in_clearing_row,
        IN_CLEARING_FEATURES
    )

    final_prob = max(
        deposit_prob,
        0.45 * deposit_prob + 0.55 * in_clearing_prob
    )

    deposit_contrib = approximate_feature_contributions(
        row,
        DEPOSIT_EXPLANATION_WEIGHTS,
        top_k=5
    )

    clearing_contrib = approximate_feature_contributions(
        in_clearing_row,
        IN_CLEARING_EXPLANATION_WEIGHTS,
        top_k=5
    )

    top_reasons = (
        ["deposit:" + r for r in top_reason_strings(deposit_contrib)]
        + ["in_clearing:" + r for r in top_reason_strings(clearing_contrib)]
    )[:8]

    return CombinedScoreResponse(
        eventId=req.eventId,
        depositFraudProbability=round(deposit_prob, 4),
        inClearingFraudProbability=round(in_clearing_prob, 4),
        finalFraudProbability=round(final_prob, 4),
        calibratedRiskBand=risk_band(final_prob),
        recommendedAction=recommended_action(final_prob),
        topReasons=top_reasons or ["NO_MAJOR_MODEL_SIGNAL"],
        latencyMs=int((time.time() - start) * 1000),
        explanation={
            "scoreBlend": {
                "depositProbability": deposit_prob,
                "inClearingProbability": in_clearing_prob,
                "finalFormula": "max(deposit_prob, 0.45 * deposit_prob + 0.55 * in_clearing_prob)"
            },
            "depositFeatureContributions": deposit_contrib,
            "inClearingFeatureContributions": clearing_contrib
        }
    )


@app.get("/health")
def health():
    return {
        "status": "ok",
        "depositModelLoaded": DEPOSIT_MODEL_PATH.exists(),
        "inClearingModelLoaded": IN_CLEARING_MODEL_PATH.exists()
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("ml_service_app:app", host="0.0.0.0", port=8083, reload=True)