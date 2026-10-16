import json
import logging
import time
from pathlib import Path
from typing import List, Dict, Any

import joblib
import numpy as np
import pandas as pd
import xgboost as xgb

from dtos.ml_dtos import CombinedScoreResponse, CombinedScoreRequest

BASE_DIR = Path(__file__).resolve().parent.parent
MODEL_DIR = BASE_DIR / "models"

DEPOSIT_MODEL_PATH = MODEL_DIR / "deposit_fraud_model.joblib"
IN_CLEARING_MODEL_PATH = MODEL_DIR / "in_clearing_fraud_model.joblib"
DEPOSIT_METRICS_PATH = MODEL_DIR / "deposit_fraud_metrics.json"
IN_CLEARING_METRICS_PATH = MODEL_DIR / "in_clearing_fraud_metrics.json"

log = logging.getLogger("ml_service")

# Models are loaded once at import. If a file is missing, the service still starts and /ml/health reports it,
# instead of the container crashing at import time. Scoring then fails with a clear error.
MODEL_LOAD_ERRORS: Dict[str, str] = {}


def _load_model(path: Path, key: str):
    try:
        return joblib.load(path)
    except Exception as e:  # noqa: BLE001
        MODEL_LOAD_ERRORS[key] = f"{type(e).__name__}: {e}"
        log.error("could not load %s model from %s: %s", key, path, e)
        return None


def _load_metadata(path: Path, default_name: str) -> Dict[str, str]:
    try:
        m = json.loads(path.read_text())
        return {"modelName": m.get("model_name", default_name), "modelVersion": str(m.get("model_version", "unknown"))}
    except Exception:  # noqa: BLE001
        return {"modelName": default_name, "modelVersion": "unknown"}


deposit_model = _load_model(DEPOSIT_MODEL_PATH, "deposit")
in_clearing_model = _load_model(IN_CLEARING_MODEL_PATH, "in_clearing")
DEPOSIT_META = _load_metadata(DEPOSIT_METRICS_PATH, "deposit_fraud_xgboost")
IN_CLEARING_META = _load_metadata(IN_CLEARING_METRICS_PATH, "in_clearing_fraud_xgboost")


def models_status() -> Dict[str, Any]:
    return {
        "depositModelLoaded": deposit_model is not None,
        "inClearingModelLoaded": in_clearing_model is not None,
        "errors": MODEL_LOAD_ERRORS,
    }


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
    values = {f: row.get(f, 0) for f in features}
    # The models were trained on lowercase channels (atm, branch, mobile, online, mail).
    if "channel" in values:
        values["channel"] = str(values["channel"] or "branch").strip().lower()
    return pd.DataFrame([values])


def score_deposit_model(row: Dict[str, Any]) -> float:
    return score_deposit_model_with_features(deposit_model, row, DEPOSIT_FEATURES)


def _require(model, name: str):
    if model is None:
        raise RuntimeError(f"{name} model is not loaded: {MODEL_LOAD_ERRORS.get(name)}")


def score_deposit_model_with_features(model, row: Dict[str, Any], features: List[str]) -> float:
    _require(model, "deposit")
    x = make_dataframe(row, features)
    return float(model.predict_proba(x)[0, 1])

def score_inclearing_model(row: Dict[str, Any]) -> float:
    return score_inclearing_model_with_features(in_clearing_model, row, IN_CLEARING_FEATURES)


def score_inclearing_model_with_features(model, row: Dict[str, Any], features: List[str]) -> float:
    _require(model, "in_clearing")
    x = make_dataframe(row, features)
    return float(model.predict_proba(x)[0, 1])


def model_feature_contributions(model, row: Dict[str, Any], features: List[str], top_k: int = 8
                                ) -> List[Dict[str, Any]]:
    """Per-feature contributions from XGBoost itself (TreeSHAP via pred_contribs), averaged over the calibration
    folds. Values are in log-odds of the uncalibrated model: positive pushes towards fraud, negative away.
    One-hot channel columns are summed back into a single `channel` feature."""
    x = make_dataframe(row, features)
    totals: Dict[str, float] = {}
    n = 0
    for cc in model.calibrated_classifiers_:
        pipe = cc.estimator
        pre, clf = pipe[:-1], pipe[-1]
        names = [str(c).split("__", 1)[-1] for c in pre.get_feature_names_out()]
        xt = np.asarray(pre.transform(x), dtype=float)
        contribs = clf.get_booster().predict(xgb.DMatrix(xt), pred_contribs=True)[0]
        for name, c in zip(names, contribs[:-1]):  # last column is the bias term
            key = "channel" if name.startswith("channel_") else name
            totals[key] = totals.get(key, 0.0) + float(c)
        n += 1
    out = []
    for f in features:
        if f in totals and abs(totals[f] / n) >= 0.01:
            v = x.iloc[0][f]
            out.append({"feature": f, "value": v if isinstance(v, str) else float(v),
                        "contribution": round(totals[f] / n, 4)})
    return sorted(out, key=lambda c: abs(c["contribution"]), reverse=True)[:top_k]


def deposit_feature_contributions(row: Dict[str, Any], top_k: int = 8) -> List[Dict[str, Any]]:
    try:
        _require(deposit_model, "deposit")
        return model_feature_contributions(deposit_model, row, DEPOSIT_FEATURES, top_k)
    except Exception as e:  # noqa: BLE001
        log.warning("model contributions failed, using approximation: %s", e)
        return approximate_deposit_feature_contributions(row, top_k)


def in_clearing_feature_contributions(row: Dict[str, Any], top_k: int = 8) -> List[Dict[str, Any]]:
    try:
        _require(in_clearing_model, "in_clearing")
        return model_feature_contributions(in_clearing_model, row, IN_CLEARING_FEATURES, top_k)
    except Exception as e:  # noqa: BLE001
        log.warning("model contributions failed, using approximation: %s", e)
        return approximate_in_clearing_feature_contributions(row, top_k)


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
    # Only features that push the score towards fraud are listed as reasons.
    return [
        f"{c['feature']}={c['value']}"
        for c in contributions
        if c.get("contribution", c.get("approxContribution", 0)) > 0
    ][:8]


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

    deposit_contrib = deposit_feature_contributions(row, top_k=8)
    clearing_contrib = in_clearing_feature_contributions(in_clearing_row, top_k=8)

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
            "contributionUnit": "log-odds (TreeSHAP of the uncalibrated XGBoost model); positive = towards fraud",
            "depositFeatureContributions": deposit_contrib,
            "inClearingFeatureContributions": clearing_contrib
        }
    )