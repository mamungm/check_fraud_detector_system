import asyncio
import os

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from db.connection.image_db_connection import get_db_session_from_context
from dtos.image_dtos import ImageAnalysisResponse, ImageAnalysisRequest
from service.image_service import image_process_event, to_local_path

router = APIRouter(prefix="/image", tags=["image"])

# Folder the check images are served from (/app/images in the container).
IMAGE_DIR = os.path.realpath(
    os.getenv("IMAGE_DIR", os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "images"))
)
ALLOWED_EXTENSIONS = {".png", ".jpg", ".jpeg"}

@router.post("/analyze", response_model=ImageAnalysisResponse)
async def handle(req: ImageAnalysisRequest):
    with get_db_session_from_context() as db:
        return await asyncio.to_thread(image_process_event, req, db)

@router.get("/health")
def health():
    return {"status": "ok"}


@router.get("/file")
def get_image(uri: str):
    """Serves a check image so the UI can display it. Only files inside IMAGE_DIR are served: just the
    file name of `uri` is used, so paths like ../../etc/passwd cannot escape the folder."""
    name = os.path.basename(to_local_path(uri))
    path = os.path.realpath(os.path.join(IMAGE_DIR, name))
    if (os.path.splitext(name)[1].lower() not in ALLOWED_EXTENSIONS
            or os.path.dirname(path) != IMAGE_DIR
            or not os.path.isfile(path)):
        raise HTTPException(status_code=404, detail="Image not found")
    return FileResponse(path)
