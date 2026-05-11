from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from db.consortium_db_handler import get_db_session
from dtos.consortium_dtos import ConsortiumEventRequest, ConsortiumScoreResponse, FraudDisposition
from service.consortium_service import consortium_process_event, TOKEN_INDEX, RELATION_INDEX, BANK_FLOW_INDEX

router = APIRouter(prefix="/consortium", tags=["consortium"])

# -----------------------------
# API
# -----------------------------

@router.post("/match", response_model=ConsortiumScoreResponse)
def match(event: ConsortiumEventRequest, db: Session = Depends(get_db_session)):
    print("match api called with event:", event)
    return consortium_process_event(event, db)

@router.post("/fraud-decision")
def fraud_decision(event_id: str, disposition: str):
    # for e in EVENTS:
    #     if e.eventId == event_id:
    #         e.fraudDisposition = FraudDisposition(disposition)
    #         return {"status": "updated", "eventId": event_id, "disposition": disposition}
    return {"status": "not_found", "eventId": event_id}

@router.get("/health")
def health():
    return {"status": "ok"}


@router.get("/debug/stats")
def stats():
    return {
        "eventCount": 0,#len(EVENTS),
        "tokenIndexSize": len(TOKEN_INDEX),
        "relationshipIndexSize": len(RELATION_INDEX),
        "bankFlowIndexSize": len(BANK_FLOW_INDEX),
    }