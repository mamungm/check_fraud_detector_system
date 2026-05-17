import time

from fastapi import APIRouter

from dtos.ml_dtos import FraudScoreResponse, DepositScoreRequest, InClearingScoreRequest, CombinedScoreResponse, \
    CombinedScoreRequest
from service.ml_service import risk_band, recommended_action, score_inclearing_model, \
    score_deposit_model, approximate_deposit_feature_contributions, approximate_in_clearing_feature_contributions, \
    combined_process_event, DEPOSIT_MODEL_PATH, IN_CLEARING_MODEL_PATH

router = APIRouter(prefix="/ml", tags=["ml"])


@router.post("/score/deposit", response_model=FraudScoreResponse)
def score_deposit(req: DepositScoreRequest):
    start = time.time()

    row = req.model_dump()
    prob = score_deposit_model(row)
    contributions = approximate_deposit_feature_contributions(row)

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


@router.post("/score/in-clearing", response_model=FraudScoreResponse)
def score_in_clearing(req: InClearingScoreRequest):
    start = time.time()

    row = req.model_dump()
    prob = score_inclearing_model(row)
    contributions = approximate_in_clearing_feature_contributions(row)

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


@router.post("/score/combined", response_model=CombinedScoreResponse)
def score_combined(req: CombinedScoreRequest):
    print("score/combined api called with event:", req)
    return combined_process_event(req)


@router.get("/health")
def health():
    return {
        "status": "ok",
        "depositModelLoaded": DEPOSIT_MODEL_PATH.exists(),
        "inClearingModelLoaded": IN_CLEARING_MODEL_PATH.exists()
    }