import time

from fastapi import APIRouter

from dtos.ml_dtos import FraudScoreResponse, DepositScoreRequest, InClearingScoreRequest, CombinedScoreResponse, \
    CombinedScoreRequest
from service.ml_service import risk_band, recommended_action, score_inclearing_model, \
    score_deposit_model, deposit_feature_contributions, in_clearing_feature_contributions, \
    combined_process_event, models_status, DEPOSIT_META, IN_CLEARING_META

router = APIRouter(prefix="/ml", tags=["ml"])


@router.post("/score/deposit", response_model=FraudScoreResponse)
def score_deposit(req: DepositScoreRequest):
    start = time.time()

    row = req.model_dump()
    prob = score_deposit_model(row)
    contributions = deposit_feature_contributions(row)

    return FraudScoreResponse(
        eventId=req.eventId,
        fraudProbability=round(prob, 4),
        calibratedRiskBand=risk_band(prob),
        recommendedAction=recommended_action(prob),
        topFeatureContributions=contributions,
        latencyMs=int((time.time() - start) * 1000),
        modelName=DEPOSIT_META["modelName"],
        modelVersion=DEPOSIT_META["modelVersion"]
    )


@router.post("/score/in-clearing", response_model=FraudScoreResponse)
def score_in_clearing(req: InClearingScoreRequest):
    start = time.time()

    row = req.model_dump()
    prob = score_inclearing_model(row)
    contributions = in_clearing_feature_contributions(row)

    return FraudScoreResponse(
        eventId=req.eventId,
        fraudProbability=round(prob, 4),
        calibratedRiskBand=risk_band(prob),
        recommendedAction=recommended_action(prob),
        topFeatureContributions=contributions,
        latencyMs=int((time.time() - start) * 1000),
        modelName=IN_CLEARING_META["modelName"],
        modelVersion=IN_CLEARING_META["modelVersion"]
    )


@router.post("/score/combined", response_model=CombinedScoreResponse)
def score_combined(req: CombinedScoreRequest):
    return combined_process_event(req)


@router.get("/health")
def health():
    status = models_status()
    ok = status["depositModelLoaded"] and status["inClearingModelLoaded"]
    return {"status": "ok" if ok else "degraded", **status}
