import asyncio

from fastapi import APIRouter

from db.connection.image_db_connection import get_db_session_from_context
from dtos.image_dtos import ImageAnalysisResponse, ImageAnalysisRequest
from service.image_service import image_process_event

router = APIRouter(prefix="/image", tags=["image"])

@router.post("/analyze", response_model=ImageAnalysisResponse)
async def handle(req: ImageAnalysisRequest):
    with get_db_session_from_context() as db:
        return await asyncio.to_thread(image_process_event, req, db)

@router.get("/health")
def health():
    return {"status": "ok"}