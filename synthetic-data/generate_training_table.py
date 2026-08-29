"""
Generates the flat ML training table consumed by both
services/ml-service/train_model_check_fraud.py and
services/ml-service/train_model_in_clearing.py.

Both scripts read the same file (DATA_PATH = "../../training-data/check_fraud_training.csv")
and each selects its own subset of feature columns from it, so this single generator
produces the union of both scripts' required columns:

  event_id, institution_id, label, channel,
  amount, account_deposit_count_7d, account_avg_amount_30d, account_new_device,
  payee_seen_institutions_14d, payee_fraud_count_30d, device_fraud_count_30d,
  image_duplicate_score, ocr_amount_match, layout_anomaly_score, font_anomaly_score,
  signature_presence_score, endorsement_score, rule_hit_count, critical_rule_hit_count,
  return_code_present, chargeback_present, loss_amount,
  deposit_score, days_since_deposit, clearing_amount_matches_deposit,
  drawee_bank_prior_fraud_30d, deposit_to_clearing_bank_risk

This file is intentionally gitignored (see /training-data/check_fraud_training.csv in
.gitignore) -- it is meant to be generated locally, same as the other synthetic-data outputs.

Usage:
    python generate_training_table.py --out ../training-data --rows 8000
"""
import argparse
import csv
import random
import uuid
from pathlib import Path


def write_csv(path, rows):
    if not rows:
        return
    with open(path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def clamp01(x):
    return max(0.0, min(1.0, x))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="../training-data")
    ap.add_argument("--rows", type=int, default=8000)
    ap.add_argument("--institutions", type=int, default=5)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    random.seed(args.seed)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    institution_ids = [str(uuid.uuid4()) for _ in range(args.institutions)]
    channels = ["mobile", "online", "branch", "atm", "mail"]

    rows = []
    for _ in range(args.rows):
        institution_id = random.choice(institution_ids)
        channel = random.choices(channels, weights=[0.45, 0.2, 0.2, 0.1, 0.05])[0]

        # same fraud-scenario mix as synthetic-data/generate_synthetic_data.py,
        # so the two generators tell a consistent fraud story
        is_dup = random.random() < 0.07
        is_altered = random.random() < 0.04
        is_counterfeit = random.random() < 0.03
        is_mule = random.random() < 0.05
        is_fraud_scenario = is_dup or is_altered or is_counterfeit or is_mule
        # small residual rate of "organic" fraud not captured by any single heuristic
        label = 1 if (is_fraud_scenario or random.random() < 0.01) else 0

        amount = round(random.lognormvariate(6.8, 0.65), 2)
        if is_altered or is_counterfeit:
            amount *= random.uniform(1.5, 4.0)
        amount = min(amount, 50000.00)

        account_deposit_count_7d = random.randint(3, 10) if is_mule else random.randint(0, 3)
        account_avg_amount_30d = round(random.lognormvariate(6.5, 0.6), 2)
        account_new_device = 1 if random.random() < (0.5 if is_fraud_scenario else 0.1) else 0

        payee_seen_institutions_14d = random.randint(3, 8) if is_mule else random.randint(0, 1)
        payee_fraud_count_30d = random.randint(1, 4) if is_mule else (1 if random.random() < 0.03 else 0)
        device_fraud_count_30d = random.randint(1, 2) if (is_mule or is_dup) and random.random() < 0.4 else 0

        image_duplicate_score = round(random.uniform(0.75, 0.99) if is_dup else random.uniform(0.0, 0.3), 3)
        ocr_amount_match = 0 if (is_altered and random.random() < 0.85) else 1
        layout_anomaly_score = round(random.uniform(0.6, 0.95) if is_counterfeit else random.uniform(0.0, 0.25), 3)
        font_anomaly_score = round(random.uniform(0.55, 0.9) if is_altered else random.uniform(0.0, 0.2), 3)

        missing_signature = random.random() < 0.03
        signature_presence_score = round(random.uniform(0.0, 0.3) if missing_signature else random.uniform(0.7, 1.0), 3)
        missing_endorsement = random.random() < 0.03
        endorsement_score = round(random.uniform(0.0, 0.3) if missing_endorsement else random.uniform(0.7, 1.0), 3)

        rule_hit_count = random.randint(1, 4) if is_fraud_scenario else random.randint(0, 1)
        critical_rule_hit_count = min(rule_hit_count, random.randint(1, 2) if (is_dup or is_altered or is_counterfeit) and random.random() < 0.5 else 0)

        return_code_present = 1 if random.random() < (0.15 if is_fraud_scenario else 0.03) else 0
        chargeback_present = 1 if random.random() < (0.08 if is_fraud_scenario else 0.015) else 0
        loss_amount = round(amount * random.uniform(0.4, 1.0), 2) if chargeback_present else 0.0

        # simulated upstream deposit-stage risk (input feature for the in-clearing model)
        base_risk = 0.35 if is_fraud_scenario else 0.04
        deposit_score = round(clamp01(random.gauss(base_risk, 0.12)), 3)

        days_since_deposit = random.randint(0, 10)
        clearing_amount_matches_deposit = 0 if (is_altered and random.random() < 0.6) else 1
        drawee_bank_prior_fraud_30d = random.randint(1, 5) if is_fraud_scenario and random.random() < 0.3 else 0
        deposit_to_clearing_bank_risk = round(random.uniform(0.4, 0.9) if is_mule else random.uniform(0.0, 0.3), 3)

        rows.append({
            "event_id": str(uuid.uuid4()),
            "institution_id": institution_id,
            "label": label,
            "channel": channel,
            "amount": amount,
            "account_deposit_count_7d": account_deposit_count_7d,
            "account_avg_amount_30d": account_avg_amount_30d,
            "account_new_device": account_new_device,
            "payee_seen_institutions_14d": payee_seen_institutions_14d,
            "payee_fraud_count_30d": payee_fraud_count_30d,
            "device_fraud_count_30d": device_fraud_count_30d,
            "image_duplicate_score": image_duplicate_score,
            "ocr_amount_match": ocr_amount_match,
            "layout_anomaly_score": layout_anomaly_score,
            "font_anomaly_score": font_anomaly_score,
            "signature_presence_score": signature_presence_score,
            "endorsement_score": endorsement_score,
            "rule_hit_count": rule_hit_count,
            "critical_rule_hit_count": critical_rule_hit_count,
            "return_code_present": return_code_present,
            "chargeback_present": chargeback_present,
            "loss_amount": loss_amount,
            "deposit_score": deposit_score,
            "days_since_deposit": days_since_deposit,
            "clearing_amount_matches_deposit": clearing_amount_matches_deposit,
            "drawee_bank_prior_fraud_30d": drawee_bank_prior_fraud_30d,
            "deposit_to_clearing_bank_risk": deposit_to_clearing_bank_risk,
        })

    out_path = out / "check_fraud_training.csv"
    write_csv(out_path, rows)
    fraud_rate = sum(r["label"] for r in rows) / len(rows)
    print(f"Wrote {len(rows)} rows to {out_path.resolve()}")
    print(f"Fraud rate: {fraud_rate:.2%}")


if __name__ == "__main__":
    main()
