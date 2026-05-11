from datetime import datetime
from typing import Optional, List, Dict, Any
import uuid

from pydantic import BaseModel
from enum import Enum


# -----------------------------
# DTOs
# -----------------------------
class FraudDisposition(str, Enum):
    UNKNOWN = "UNKNOWN"
    CONFIRMED_FRAUD = "CONFIRMED_FRAUD"
    CLEARED = "CLEARED"

class FraudDispositionEventRequest(BaseModel):
    eventId: str
    fraudDisposition: FraudDisposition

class ConsortiumEventRequest(BaseModel):
    eventId: Optional[str] = str(uuid.uuid4())
    institutionId: str
    clearingInstitutionId: Optional[str] = None

    channel: str

    depositTimestamp: datetime

    accountToken: Optional[str] = None
    payeeToken: Optional[str] = None
    payorToken: Optional[str] = None
    deviceToken: Optional[str] = None

    region: Optional[str] = None
    checkSerialHash: str

    micrRoutingHash: Optional[str] = None
    micrAccountHash: Optional[str] = None

    imageFrontUri: Optional[str] = None
    imageBackUri: Optional[str] = None

    # status: str
    # createdAt: datetime

    amount: float
    currency: str
    fraudDisposition: FraudDisposition = FraudDisposition.UNKNOWN

    #Computed features
    # IMGFPR_v1:
    # front_phash = ff8e1c3a7b92d441 |
    # back_phash = 7
    # ac91ef034bc9122 |
    # micr_hash = 1
    # d8ab2... |
    # serial_hash = 7e11...
    # Then hash full string: c4a9d7b21e8f0c99a8f7d123456789abcdef0123456789fedcba9876543210
    imageFingerprint: Optional[str] = None

class ConsortiumScoreResponse(BaseModel):
    score: float
    topReasons: List[str]
    supportingLinkedCounts: Dict[str, Any]
    recencyWindows: Dict[str, Any]
    explanation: Dict[str, Any]