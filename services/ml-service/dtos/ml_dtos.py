from typing import List, Dict, Any

from pydantic import BaseModel


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


class CombinedScoreMLRequest(BaseModel):
    eventId: str            # Query consortium_events table with eventId

    institutionId: str      # From consortium_events table -> institutionId

    amount: float           # From consortium_events table -> amount
    channel: str            # From consortium_events table -> channel

    account_deposit_count_7d: float = 0     # Query consortium_events where accountToken & depositTimestamp in last 7d
    account_avg_amount_30d: float = 0       # Query consortium_events where accountToken & depositTimestamp in last 30d, avg(amount)
    account_new_device: float = 0           # Query consortium_events to check if the deviceToken is new (first appearance in DB)

    payee_seen_institutions_14d: float = 0   # Query consortium_events where payeeToken & depositTimestamp in last 14d, count(distinct institutionId)
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