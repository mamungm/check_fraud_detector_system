from typing import Optional, Dict, Any, Tuple
from collections import defaultdict
from datetime import datetime, timedelta
import hashlib

from io import BytesIO
from pathlib import Path
from urllib.parse import urlparse

import requests
import imagehash
from PIL import Image, ImageOps
from sqlalchemy.orm import Session

from db.consortium_db_handler import add_consortium_event
from db.entities.consortium_entities import ConsortiumEventEntity
from dtos.consortium_dtos import FraudDispositionEventRequest, ConsortiumEventRequest, ConsortiumScoreResponse

# -----------------------------
# In-memory consortium store
# Replace with PostgreSQL / DynamoDB / feature store later
# -----------------------------
from typing import List

TOKEN_INDEX: Dict[Tuple[str, str], List[ConsortiumEventRequest]] = defaultdict(list)
RELATION_INDEX: Dict[Tuple[str, str], List[ConsortiumEventRequest]] = defaultdict(list)
BANK_FLOW_INDEX: Dict[Tuple[str, str], List[ConsortiumEventRequest]] = defaultdict(list)


# -----------------------------
# Utility
# -----------------------------

def token_preview(token: Optional[str]) -> Optional[str]:
    if not token:
        return None
    return hashlib.sha256(token.encode()).hexdigest()[:12]


def days_ago(ts: datetime, days: int) -> datetime:
    return ts - timedelta(days=days)


def safe_ratio(n: float, d: float) -> float:
    return n / d if d else 0.0


def clamp(v: float, low: float = 0.0, high: float = 1.0) -> float:
    return max(low, min(high, v))


def unique_institutions(events: List[ConsortiumEventRequest]) -> set[str]:
    return {e.institutionId for e in events}


def count_frauds(events: List[ConsortiumEventRequest]) -> int:
    return sum(
        1
        for e in events
        if e.fraudDisposition == "CONFIRMED_FRAUD"
    )


def recent(events: List[ConsortiumEventRequest], now: datetime, days: int) -> List[ConsortiumEventRequest]:
    cutoff = days_ago(now, days)
    return [e for e in events if e.depositTimestamp >= cutoff]


def set_fraud_disposition(event_id: str, fraud_disposition: FraudDispositionEventRequest, db: Session):
    # for e in EVENTS:
    #     if e.eventId == event_id:
    #         e.fraudDisposition = fraud_disposition.fraudDisposition
    #         print(f"Updated event {event_id} with fraud disposition {fraud_disposition.fraudDisposition}")
    #         return
    print(f"Event {event_id} not found to update fraud disposition")


# -----------------------------
# TokenLinkService
# -----------------------------

class TokenLinkService:

    @staticmethod
    def index_event(event: ConsortiumEventRequest, db: Session):
        token_fields = {
            "account": event.accountToken,
            "payee": event.payeeToken,
            "payor": event.payorToken,
            "device": event.deviceToken,
            "image": event.imageFingerprint,
        }

        for token_type, token in token_fields.items():
            if token:
                TOKEN_INDEX[(token_type, token)].append(event)

        if event.payorToken and event.payeeToken:
            RELATION_INDEX[(event.payorToken, event.payeeToken)].append(event)

        if event.clearingInstitutionId:
            BANK_FLOW_INDEX[(event.institutionId, event.clearingInstitutionId)].append(event)

        add_consortium_event(ConsortiumEventEntity(**event.model_dump()), db)

    @staticmethod
    def get_token_events(token_type: str, token: Optional[str]) -> List[ConsortiumEventRequest]:
        if not token:
            return []
        return TOKEN_INDEX.get((token_type, token), [])


# -----------------------------
# NetworkRiskAggregator
# -----------------------------

class NetworkRiskAggregator:

    @staticmethod
    def token_frequency_risk(token_type: str, token: Optional[str], now: datetime) -> Dict[str, Any]:
        events = TokenLinkService.get_token_events(token_type, token)

        e7 = recent(events, now, 7)
        e14 = recent(events, now, 14)
        e30 = recent(events, now, 30)

        inst14 = unique_institutions(e14)
        fraud30 = count_frauds(e30)

        score = 0.0
        reasons = []

        if token_type == "payee" and len(inst14) >= 6:
            score += 0.45
            reasons.append(f"PAYEE_SEEN_IN_{len(inst14)}_INSTITUTIONS_14D")

        if token_type == "image" and len(unique_institutions(e30)) >= 2:
            score += 0.65
            reasons.append("SAME_IMAGE_HASH_SEEN_AT_MULTIPLE_INSTITUTIONS")

        if token_type == "device" and fraud30 > 0:
            score += 0.60
            reasons.append("DEVICE_LINKED_TO_PRIOR_CONFIRMED_FRAUD")

        if fraud30 >= 2:
            score += 0.35
            reasons.append(f"{token_type.upper()}_TOKEN_HAS_{fraud30}_PRIOR_FRAUDS_30D")

        if len(inst14) >= 3:
            score += 0.20
            reasons.append(f"{token_type.upper()}_CROSS_INSTITUTION_SPREAD")

        return {
            "score": clamp(score),
            "reasons": reasons,
            "counts": {
                "tokenType": token_type,
                "tokenPreview": token_preview(token),
                "totalAppearances": len(events),
                "appearances7d": len(e7),
                "appearances14d": len(e14),
                "appearances30d": len(e30),
                "institutionCount14d": len(inst14),
                "institutionCount30d": len(unique_institutions(e30)),
                "fraudCount30d": fraud30,
            }
        }

    @staticmethod
    def payor_payee_relationship_risk(
        payor_token: Optional[str],
        payee_token: Optional[str],
        now: datetime
    ) -> Dict[str, Any]:

        if not payor_token or not payee_token:
            return {
                "score": 0.0,
                "reasons": [],
                "counts": {}
            }

        events = RELATION_INDEX.get((payor_token, payee_token), [])
        e30 = recent(events, now, 30)

        fraud30 = count_frauds(e30)
        inst30 = unique_institutions(e30)

        score = 0.0
        reasons = []

        if len(e30) == 0:
            score += 0.25
            reasons.append("NEW_PAYOR_PAYEE_RELATIONSHIP")

        if fraud30 > 0:
            score += 0.45
            reasons.append("PAYOR_PAYEE_RELATIONSHIP_HAS_PRIOR_FRAUD")

        if len(inst30) >= 3:
            score += 0.25
            reasons.append("PAYOR_PAYEE_RELATIONSHIP_SPANS_MULTIPLE_INSTITUTIONS")

        return {
            "score": clamp(score),
            "reasons": reasons,
            "counts": {
                "relationshipAppearances30d": len(e30),
                "relationshipFraudCount30d": fraud30,
                "relationshipInstitutionCount30d": len(inst30),
                "payorPreview": token_preview(payor_token),
                "payeePreview": token_preview(payee_token),
            }
        }

    @staticmethod
    def bank_flow_risk(
        deposit_bank: str,
        clearing_bank: Optional[str],
        now: datetime
    ) -> Dict[str, Any]:

        if not clearing_bank:
            return {
                "score": 0.0,
                "reasons": [],
                "counts": {}
            }

        events = BANK_FLOW_INDEX.get((deposit_bank, clearing_bank), [])
        e30 = recent(events, now, 30)

        total30 = len(e30)
        fraud30 = count_frauds(e30)
        fraud_rate = safe_ratio(fraud30, total30)

        score = 0.0
        reasons = []

        if total30 >= 5 and fraud_rate >= 0.25:
            score += 0.55
            reasons.append("ELEVATED_DEPOSIT_TO_CLEARING_BANK_FRAUD_RATE")

        if fraud30 >= 3:
            score += 0.30
            reasons.append("BANK_FLOW_HAS_MULTIPLE_PRIOR_FRAUDS_30D")

        return {
            "score": clamp(score),
            "reasons": reasons,
            "counts": {
                "depositInstitution": deposit_bank,
                "clearingInstitution": clearing_bank,
                "bankFlowAppearances30d": total30,
                "bankFlowFraudCount30d": fraud30,
                "bankFlowFraudRate30d": round(fraud_rate, 4),
            }
        }


# -----------------------------
# ConsortiumFeatureService
# -----------------------------

class ConsortiumFeatureService:

    @staticmethod
    def compute_features(event: ConsortiumEventRequest) -> Dict[str, Any]:
        now = event.depositTimestamp

        token_results = []

        token_inputs = [
            ("payee", event.payeeToken),
            ("payor", event.payorToken),
            ("account", event.accountToken),
            ("device", event.deviceToken),
            ("image", event.imageFingerprint),
        ]

        for token_type, token in token_inputs:
            if token:
                token_results.append(
                    NetworkRiskAggregator.token_frequency_risk(token_type, token, now)
                )

        relationship_result = NetworkRiskAggregator.payor_payee_relationship_risk(
            event.payorToken,
            event.payeeToken,
            now
        )

        bank_flow_result = NetworkRiskAggregator.bank_flow_risk(
            event.institutionId,
            event.clearingInstitutionId,
            now
        )

        return {
            "tokenRiskResults": token_results,
            "relationshipRisk": relationship_result,
            "bankFlowRisk": bank_flow_result,
        }


# -----------------------------
# CrossInstitutionEvidenceService
# -----------------------------

class CrossInstitutionEvidenceService:

    @staticmethod
    def explain(features: Dict[str, Any]) -> Dict[str, Any]:
        reasons = []
        linked_counts = {}

        max_token_score = 0.0

        for r in features["tokenRiskResults"]:
            max_token_score = max(max_token_score, r["score"])
            reasons.extend(r["reasons"])

            token_type = r["counts"].get("tokenType")
            if token_type:
                linked_counts[token_type] = r["counts"]

        relationship = features["relationshipRisk"]
        bank_flow = features["bankFlowRisk"]

        reasons.extend(relationship["reasons"])
        reasons.extend(bank_flow["reasons"])

        linked_counts["payorPayeeRelationship"] = relationship["counts"]
        linked_counts["bankFlow"] = bank_flow["counts"]

        score = clamp(
            0.55 * max_token_score
            + 0.25 * relationship["score"]
            + 0.20 * bank_flow["score"]
        )

        top_reasons = sorted(set(reasons))[:8]

        return {
            "score": round(score, 4),
            "topReasons": top_reasons or ["NO_CONSORTIUM_RISK"],
            "supportingLinkedCounts": linked_counts,
            "recencyWindows": {
                "shortWindow": "7d",
                "mediumWindow": "14d",
                "longWindow": "30d",
            },
            "explanation": {
                "scoreBlend": {
                    "maxTokenRiskWeight": 0.55,
                    "relationshipRiskWeight": 0.25,
                    "bankFlowRiskWeight": 0.20,
                    "maxTokenRisk": max_token_score,
                    "relationshipRisk": relationship["score"],
                    "bankFlowRisk": bank_flow["score"],
                },
                "privacy": {
                    "rawPIIStored": False,
                    "tokenization": "HMAC or salted hash expected upstream",
                    "returnedTokens": "preview hashes only"
                }
            }
        }

def compute_phash(image_uri: str) -> str:
    """
    Supports:
    - local filesystem paths
    - S3 HTTPS URLs
    - presigned URLs
    - MinIO URLs
    """

    parsed = urlparse(image_uri)

    # -----------------------------
    # Remote URL (http / https)
    # -----------------------------
    if parsed.scheme in ("http", "https"):

        response = requests.get(image_uri, timeout=15)
        response.raise_for_status()

        with Image.open(BytesIO(response.content)) as img:
            img = ImageOps.exif_transpose(img)
            img = img.convert("L")

            return str(imagehash.phash(img))

    # -----------------------------
    # Local filesystem path
    # -----------------------------
    else:
        path = Path(image_uri)

        if not path.exists():
            raise FileNotFoundError(f"Image not found: {image_uri}")

        with Image.open(path) as img:
            img = ImageOps.exif_transpose(img)
            img = img.convert("L")

            return str(imagehash.phash(img))

def compute_image_fingerprint(event: ConsortiumEventRequest):
    front_phash = compute_phash(event.imageFrontUri) if event.imageFrontUri else None

    back_phash = compute_phash(event.imageBackUri) if event.imageBackUri else None

    micr_string = f"{event.micrRoutingHash}|{event.accountToken}|{event.checkSerialHash}"
    micr_hash = hashlib.sha256(micr_string.encode()).hexdigest()

    normalized = f"PAYEE:{(event.payeeToken or "").upper().strip()}AMOUNT:{event.amount:.2f}DATE:{event.depositTimestamp}"
    ocr_hash = hashlib.sha256(normalized.encode()).hexdigest()

    fingerprint_string = "|".join([
        f"front={front_phash}",
        f"back={back_phash}",
        f"micr={micr_hash}",
        f"serial={micr_hash}",
        f"ocr={ocr_hash}",
    ])

    image_fingerprint = hashlib.sha256(fingerprint_string.encode()).hexdigest()

    event.imageFingerprint = image_fingerprint


def consortium_process_event(event: ConsortiumEventRequest, db: Session) -> ConsortiumScoreResponse:
    compute_image_fingerprint(event)
    features = ConsortiumFeatureService.compute_features(event)
    result = CrossInstitutionEvidenceService.explain(features)

    TokenLinkService.index_event(event, db)

    return ConsortiumScoreResponse(**result)