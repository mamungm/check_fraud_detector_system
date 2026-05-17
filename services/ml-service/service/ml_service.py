import time
from pathlib import Path
from typing import List, Dict, Any

import joblib
import pandas as pd

from dtos.ml_dtos import CombinedScoreResponse, CombinedScoreRequest

BASE_DIR = Path(__file__).resolve().parent.parent
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


def score_deposit_model(row: Dict[str, Any]) -> float:
    return score_deposit_model_with_features(deposit_model, row, DEPOSIT_FEATURES)


def score_deposit_model_with_features(model, row: Dict[str, Any], features: List[str]) -> float:
    x = make_dataframe(row, features)
    return float(model.predict_proba(x)[0, 1])

def score_inclearing_model(row: Dict[str, Any]) -> float:
    return score_inclearing_model_with_features(in_clearing_model, row, IN_CLEARING_FEATURES)


def score_inclearing_model_with_features(model, row: Dict[str, Any], features: List[str]) -> float:
    x = make_dataframe(row, features)
    return float(model.predict_proba(x)[0, 1])


def approximate_in_clearing_feature_contributions(
    row: Dict[str, Any],
    top_k: int = 8
) -> List[Dict[str, Any]]:
    return approximate_feature_contributions(row, IN_CLEARING_EXPLANATION_WEIGHTS, top_k)


def approximate_deposit_feature_contributions(
    row: Dict[str, Any],
    top_k: int = 8
) -> List[Dict[str, Any]]:
    return approximate_feature_contributions(row, DEPOSIT_EXPLANATION_WEIGHTS, top_k)


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


def top_reason_strings(contributions: List[Dict[str, Any]]) -> List[str]:
    return [
        f"{c['feature']}={c['value']}"
        for c in contributions[:8]
    ]


def combined_process_event(event: CombinedScoreRequest) -> CombinedScoreResponse:
    start = time.time()

    row = event.model_dump()

    deposit_prob = score_deposit_model(row)

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

    in_clearing_prob = score_inclearing_model(
        in_clearing_row
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
        eventId=event.eventId,
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