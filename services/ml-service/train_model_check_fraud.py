import os
import json
import time
import joblib
import numpy as np
import pandas as pd

from xgboost import XGBClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    average_precision_score,
    precision_score,
    recall_score,
    confusion_matrix,
    brier_score_loss
)
from sklearn.calibration import CalibratedClassifierCV
from sklearn.preprocessing import OneHotEncoder
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline


DATA_PATH = "../../training-data/check_fraud_training.csv"
MODEL_DIR = "models"
MODEL_PATH = f"{MODEL_DIR}/deposit_fraud_model.joblib"
METRICS_PATH = f"{MODEL_DIR}/deposit_fraud_metrics.json"


NUMERIC_FEATURES = [
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
]

CATEGORICAL_FEATURES = [
    "channel"
]

TARGET = "label"


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


def precision_at_k(y_true, y_prob, k_ratio=0.05):
    k = max(1, int(len(y_true) * k_ratio))
    order = np.argsort(y_prob)[::-1]
    top_k = order[:k]
    return float(np.mean(y_true.iloc[top_k]))


def recall_at_k(y_true, y_prob, k_ratio=0.05):
    k = max(1, int(len(y_true) * k_ratio))
    order = np.argsort(y_prob)[::-1]
    top_k = order[:k]

    total_fraud = y_true.sum()
    if total_fraud == 0:
        return 0.0

    return float(y_true.iloc[top_k].sum() / total_fraud)


def false_positive_rate_by_institution(df_eval, y_true, y_prob, threshold=0.60):
    rows = []

    eval_df = df_eval.copy()
    eval_df["y_true"] = y_true.values
    eval_df["y_prob"] = y_prob
    eval_df["pred"] = (eval_df["y_prob"] >= threshold).astype(int)

    for institution_id, g in eval_df.groupby("institution_id"):
        tn, fp, fn, tp = confusion_matrix(
            g["y_true"],
            g["pred"],
            labels=[0, 1]
        ).ravel()

        fpr = fp / (fp + tn) if (fp + tn) > 0 else 0.0

        rows.append({
            "institution_id": institution_id,
            "false_positive_rate": round(float(fpr), 4),
            "false_positives": int(fp),
            "true_negatives": int(tn),
            "sample_count": int(len(g))
        })

    return rows


def main():
    os.makedirs(MODEL_DIR, exist_ok=True)

    df = pd.read_csv(DATA_PATH)

    required = ["event_id", "institution_id", TARGET] + NUMERIC_FEATURES + CATEGORICAL_FEATURES
    missing = [c for c in required if c not in df.columns]

    if missing:
        raise ValueError(f"Missing columns: {missing}")

    df = df.copy()

    for c in NUMERIC_FEATURES:
        df[c] = pd.to_numeric(df[c], errors="coerce").fillna(0)

    df["channel"] = df["channel"].fillna("unknown").astype(str)

    X = df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    y = df[TARGET].astype(int)

    train_df, test_df, y_train, y_test = train_test_split(
        df,
        y,
        test_size=0.25,
        random_state=42,
        stratify=y
    )

    X_train = train_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    X_test = test_df[NUMERIC_FEATURES + CATEGORICAL_FEATURES]

    preprocessor = ColumnTransformer(
        transformers=[
            ("cat", OneHotEncoder(handle_unknown="ignore"), CATEGORICAL_FEATURES),
            ("num", "passthrough", NUMERIC_FEATURES),
        ]
    )

    base_model = XGBClassifier(
        n_estimators=300,
        max_depth=4,
        learning_rate=0.05,
        subsample=0.9,
        colsample_bytree=0.9,
        objective="binary:logistic",
        eval_metric="aucpr",
        tree_method="hist",
        random_state=42
    )

    pipeline = Pipeline(
        steps=[
            ("preprocessor", preprocessor),
            ("model", base_model)
        ]
    )

    calibrated_model = CalibratedClassifierCV(
        estimator=pipeline,
        method="isotonic",
        cv=3
    )

    start = time.time()
    calibrated_model.fit(X_train, y_train)
    train_latency = time.time() - start

    start = time.time()
    y_prob = calibrated_model.predict_proba(X_test)[:, 1]
    scoring_latency_ms = ((time.time() - start) / len(X_test)) * 1000

    y_pred_60 = (y_prob >= 0.60).astype(int)

    pr_auc = average_precision_score(y_test, y_prob)
    precision_60 = precision_score(y_test, y_pred_60, zero_division=0)
    recall_60 = recall_score(y_test, y_pred_60, zero_division=0)
    brier = brier_score_loss(y_test, y_prob)

    metrics = {
        "model_name": "deposit_fraud_xgboost",
        "model_version": "0.1.0",
        "train_rows": int(len(X_train)),
        "test_rows": int(len(X_test)),
        "positive_rate_train": float(y_train.mean()),
        "positive_rate_test": float(y_test.mean()),
        "precision_at_1pct": precision_at_k(y_test, y_prob, 0.01),
        "precision_at_5pct": precision_at_k(y_test, y_prob, 0.05),
        "recall_at_1pct": recall_at_k(y_test, y_prob, 0.01),
        "recall_at_5pct": recall_at_k(y_test, y_prob, 0.05),
        "pr_auc": round(float(pr_auc), 4),
        "precision_threshold_0_60": round(float(precision_60), 4),
        "recall_threshold_0_60": round(float(recall_60), 4),
        "brier_score": round(float(brier), 4),
        "avg_scoring_latency_ms": round(float(scoring_latency_ms), 4),
        "training_time_seconds": round(float(train_latency), 2),
        "false_positive_rate_by_institution": false_positive_rate_by_institution(
            test_df,
            y_test,
            y_prob,
            threshold=0.60
        ),
        "features": {
            "numeric": NUMERIC_FEATURES,
            "categorical": CATEGORICAL_FEATURES
        },
        "risk_bands": {
            "HIGH": "probability >= 0.85",
            "MEDIUM": "0.60 <= probability < 0.85",
            "LOW": "0.30 <= probability < 0.60",
            "MINIMAL": "probability < 0.30"
        }
    }

    joblib.dump(calibrated_model, MODEL_PATH)

    with open(METRICS_PATH, "w") as f:
        json.dump(metrics, f, indent=2)

    print("Saved model:", MODEL_PATH)
    print("Saved metrics:", METRICS_PATH)
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()