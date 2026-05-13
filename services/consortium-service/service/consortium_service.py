from typing import Optional, Dict, Any
from datetime import datetime, timedelta, timezone
import hashlib

from io import BytesIO
from pathlib import Path
from urllib.parse import urlparse

import requests
import imagehash
from PIL import Image, ImageOps
from sqlalchemy.orm import Session

from db.entity.consortium_entities import ConsortiumEventEntity, TokenIndexEntity, TokenType, RelationIndexEntity, \
    BankFlowIndexEntity
from db.repo.consortium_repositories import add_consortium_event, add_token_index, add_relation_index, get_token_events, \
    get_relation_events, get_bank_flow_events, add_bank_flow_index
from dtos.consortium_dtos import FraudDispositionEventRequest, ConsortiumEventRequest, ConsortiumScoreResponse
from typing import List


# -----------------------------
# Utility
# -----------------------------

def token_preview(token: Optional[str]) -> Optional[str]:
    if not token:
        return None
    return hashlib.sha256(token.encode()).hexdigest()[:12]


def days_ago(ts: datetime, days: int) -> datetime:
    if ts.tzinfo is None:
        ts = ts.replace(tzinfo=timezone.utc)
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
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)

    cutoff = days_ago(now, days)
    return [
        e for e in events
        if (
            e.depositTimestamp.replace(tzinfo=timezone.utc)
            if e.depositTimestamp.tzinfo is None
            else e.depositTimestamp
        ) >= cutoff
    ]


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
        if event.eventId is None:
            return

        token_fields = {
            TokenType.ACCOUNT: event.accountToken,
            TokenType.PAYEE: event.payeeToken,
            TokenType.PAYOR: event.payorToken,
            TokenType.DEVICE: event.deviceToken,
            TokenType.IMAGE: event.imageFingerprint,
        }

        for token_type, token in token_fields.items():
            if token:
                add_token_index(
                    TokenIndexEntity(
                        token_type=token_type,
                        token_value=token,
                        event_id=event.eventId,
                        institution_id=event.institutionId,
                        deposit_timestamp=event.depositTimestamp,
                        fraud_disposition=event.fraudDisposition.value if hasattr(event.fraudDisposition, "value") else str(event.fraudDisposition),
                    ), db)

        if event.payorToken and event.payeeToken:
            add_relation_index(
                RelationIndexEntity(
                    payor_token=event.payorToken,
                    payee_token=event.payeeToken,
                    event_id=event.eventId,
                    institution_id=event.institutionId,
                    deposit_timestamp=event.depositTimestamp,
                    fraud_disposition=event.fraudDisposition.value if hasattr(event.fraudDisposition, "value") else str(
                        event.fraudDisposition),
                ),
                db,
            )

        if event.clearingInstitutionId:
            add_bank_flow_index(
                BankFlowIndexEntity(
                    deposit_institution=event.institutionId,
                    clearing_institution=event.clearingInstitutionId,
                    event_id=event.eventId,
                    deposit_timestamp=event.depositTimestamp,
                    fraud_disposition=event.fraudDisposition.value if hasattr(event.fraudDisposition, "value") else str(
                        event.fraudDisposition),
                ),
                db,
            )

        add_consortium_event(ConsortiumEventEntity(**event.model_dump()), db)


# -----------------------------
# NetworkRiskAggregator
# -----------------------------

class NetworkRiskAggregator:

    @staticmethod
    def token_frequency_risk(token_type: TokenType, token: Optional[str], now: datetime, db: Session) -> Dict[str, Any]:
        events = get_token_events(token_type, token, db)

        e7 = recent(events, now, 7)
        e14 = recent(events, now, 14)
        e30 = recent(events, now, 30)

        inst14 = unique_institutions(e14)
        fraud30 = count_frauds(e30)

        score = 0.0
        reasons = []

        if token_type == TokenType.PAYEE and len(inst14) >= 6:
            score += 0.45
            reasons.append(f"PAYEE_SEEN_IN_{len(inst14)}_INSTITUTIONS_14D")

        if token_type == TokenType.IMAGE and len(unique_institutions(e30)) >= 2:
            score += 0.65
            reasons.append("SAME_IMAGE_HASH_SEEN_AT_MULTIPLE_INSTITUTIONS")

        if token_type == TokenType.DEVICE and fraud30 > 0:
            score += 0.60
            reasons.append("DEVICE_LINKED_TO_PRIOR_CONFIRMED_FRAUD")

        if fraud30 >= 2:
            score += 0.35
            reasons.append(f"{token_type.value}_TOKEN_HAS_{fraud30}_PRIOR_FRAUDS_30D")

        if len(inst14) >= 3:
            score += 0.20
            reasons.append(f"{token_type.value}_CROSS_INSTITUTION_SPREAD")

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
        now: datetime,
        db: Session
    ) -> Dict[str, Any]:

        if not payor_token or not payee_token:
            return {
                "score": 0.0,
                "reasons": [],
                "counts": {}
            }

        events = get_relation_events(payor_token, payee_token, db=db)
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
        now: datetime,
        db: Session
    ) -> Dict[str, Any]:

        if not clearing_bank:
            return {
                "score": 0.0,
                "reasons": [],
                "counts": {}
            }

        events = get_bank_flow_events(deposit_bank, clearing_bank, db=db)
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
    def compute_features(event: ConsortiumEventRequest, db: Session) -> Dict[str, Any]:
        now = event.depositTimestamp

        token_results = []

        token_inputs = [
            (TokenType.PAYEE, event.payeeToken),
            (TokenType.PAYOR, event.payorToken),
            (TokenType.ACCOUNT, event.accountToken),
            (TokenType.DEVICE, event.deviceToken),
            (TokenType.IMAGE, event.imageFingerprint),
        ]

        for token_type, token in token_inputs:
            if token:
                token_results.append(
                    NetworkRiskAggregator.token_frequency_risk(token_type, token, now, db)
                )

        relationship_result = NetworkRiskAggregator.payor_payee_relationship_risk(
            event.payorToken,
            event.payeeToken,
            now,
            db
        )

        bank_flow_result = NetworkRiskAggregator.bank_flow_risk(
            event.institutionId,
            event.clearingInstitutionId,
            now,
            db
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
    features = ConsortiumFeatureService.compute_features(event, db)
    result = CrossInstitutionEvidenceService.explain(features)

    TokenLinkService.index_event(event, db)

    return ConsortiumScoreResponse(**result)