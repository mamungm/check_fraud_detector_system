# Implementation Notes

## Use cases covered

| Use case | Implementation |
|---|---|
| Duplicate presentment | Rule engine checks MICR routing + MICR account + serial; consortium service checks cross-institution token appearances |
| Altered check suspicion | Image service placeholder flags URI markers; replace with OCR amount mismatch, tamper segmentation, visual embeddings |
| Counterfeit/forged suspicion | Image service placeholder; replace with layout similarity, signature verification, stock/template detection |
| Cross-institution risk escalation | Consortium service aggregates hashed token appearances and institution count |
| Behavioral anomaly | Rule engine + ML service use velocity, new device, geography, amount/channel |
| Hold/review/pass | Final decision policy in Spring `DepositService` |

## Replace demo ML with real ML

1. Start with interpretable Logistic Regression or XGBoost on tabular features.
2. Add image embeddings: perceptual hash, OCR legal/courtesy amount mismatch, signature/endorsement presence, layout anomaly.
3. Add graph/network features: account/payee/device/check-token degree, fraud-neighbor counts, connected-component risk.
4. Calibrate scores with Platt scaling or isotonic regression.
5. Track precision, recall, false-positive rate, alert volume, latency, and explanation coverage.

## Production hardening

- KMS-backed HMAC keys
- per-institution token namespace
- key rotation
- row-level security
- immutable audit logs
- dead-letter Kafka topics
- idempotent event ingestion
- async score aggregation
- model registry and champion/challenger deployment
- data drift and feature drift monitoring
