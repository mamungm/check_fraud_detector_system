from typing import Optional, List, Dict, Any

from pydantic import BaseModel


class ImageAnalysisRequest(BaseModel):
    eventId: str
    amount: float
    imageFrontUri: str
    imageBackUri: Optional[str] = None


class ImageAnalysisResponse(BaseModel):
    image_duplicate_score: float
    ocr_amount_match: bool
    layout_anomaly_score: float
    font_anomaly_score: float
    signature_presence_score: float
    endorsement_score: float
    reasonCodes: List[str]
    explanation: Dict[str, Any]
    latencyMs: int