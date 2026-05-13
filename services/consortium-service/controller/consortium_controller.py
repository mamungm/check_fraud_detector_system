import asyncio

from fastapi import APIRouter

from db.connection.consortium_db_connection import get_db_session_from_context
from dtos.consortium_dtos import ConsortiumEventRequest, ConsortiumScoreResponse
from service.consortium_service import consortium_process_event

router = APIRouter(prefix="/consortium", tags=["consortium"])

# -----------------------------
# API
# -----------------------------

@router.post("/match", response_model=ConsortiumScoreResponse)
async def match(event: ConsortiumEventRequest):
    print("match api called with event:", event)
    with get_db_session_from_context() as db:
        return await asyncio.to_thread(consortium_process_event, event, db)

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
        "tokenIndexSize": 0,#len(TOKEN_INDEX),
        "relationshipIndexSize": 0,#len(RELATION_INDEX),
        "bankFlowIndexSize": 0,#len(BANK_FLOW_INDEX),
    }