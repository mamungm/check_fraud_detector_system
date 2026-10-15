import asyncio

from fastapi import APIRouter

from db.connection.consortium_db_connection import get_db_session_from_context
from dtos.consortium_dtos import ConsortiumEventRequest, ConsortiumScoreResponse, FraudDisposition, \
    FraudDispositionEventRequest
from db.repo.consortium_repositories import get_index_counts
from service.consortium_service import consortium_process_event, set_fraud_disposition

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
def fraud_decision(event_id: str, disposition: FraudDisposition):
    req = FraudDispositionEventRequest(eventId=event_id, fraudDisposition=disposition)
    with get_db_session_from_context() as db:
        updated = set_fraud_disposition(event_id, req, db)
    if updated:
        return {"status": "updated", "eventId": event_id, "disposition": disposition.value}
    return {"status": "not_found", "eventId": event_id}

@router.get("/health")
def health():
    return {"status": "ok"}


@router.get("/debug/stats")
def stats():
    with get_db_session_from_context() as db:
        return get_index_counts(db)
