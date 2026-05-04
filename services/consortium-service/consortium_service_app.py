from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional, Dict, Any, List, Tuple
from collections import defaultdict
from datetime import datetime, timedelta
import hashlib

import asyncio
import os
import time
from contextlib import asynccontextmanager
from services.commons.kafka_event_handler import KafkaEventHandler
from services.commons.kafka_completion import CompletionReporter
from aiokafka import AIOKafkaProducer

@asynccontextmanager
async def lifespan(app: FastAPI):
    stop_event = asyncio.Event()

    bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    completion_topic = os.getenv("KAFKA_COMPLETION_TOPIC", "check.deposit.service.completed")

    producer = AIOKafkaProducer(bootstrap_servers=bootstrap)
    await producer.start()
    reporter = CompletionReporter(producer, topic=completion_topic, service_name="consortium")

    class ConsortiumKafkaHandler(KafkaEventHandler):
        async def handle_event(self, event: dict):
            print(f"Received {self.name} event: {event}")
            start = time.time()
            event_id = str(event.get("eventId", ""))
            try:
                consortium_event = ConsortiumEventRequest.model_validate(event)
                consortiumScoreResponse = consortium_process_event(consortium_event)
                await reporter.report_success(
                    event_id=event_id or "UNKNOWN",
                    latency_ms=int((time.time() - start) * 1000),
                    details=consortiumScoreResponse.model_dump(),
                )
            except Exception as e:
                print(e)
                await reporter.report_failure(
                    event_id=event_id or "UNKNOWN",
                    latency_ms=int((time.time() - start) * 1000),
                    error=str(e),
                )

    consortium_kafka_event_handler = ConsortiumKafkaHandler(
        "consortium_kafka",
        "check.deposit.created",
        group_id="consortium-fraud-service",
        bootstrap_servers=bootstrap,
    )
    consortium_kafka_task = asyncio.create_task(
        consortium_kafka_event_handler.run_consumer(stop_event)
    )

    try:
        yield

    finally:
        stop_event.set()
        await consortium_kafka_task
        await producer.stop()

app = FastAPI(title="Consortium Risk Service", lifespan=lifespan)


# -----------------------------
# DTOs
# -----------------------------

class ConsortiumEventRequest(BaseModel):
    eventId: str
    institutionId: str
    clearingInstitutionId: Optional[str] = None

    channel: str

    depositTimestamp: datetime

    accountToken: Optional[str] = None
    payeeToken: Optional[str] = None
    payorToken: Optional[str] = None
    deviceToken: Optional[str] = None
    # IMGFPR_v1:
    # front_phash = ff8e1c3a7b92d441 |
    # back_phash = 7
    # ac91ef034bc9122 |
    # micr_hash = 1
    # d8ab2... |
    # serial_hash = 7e11...
    # Then hash full string: c4a9d7b21e8f0c99a8f7d123456789abcdef0123456789fedcba9876543210
    imageFingerprint: Optional[str] = None


    region: Optional[str] = None
    checkSerial: str

    micrRoutingHash: Optional[str] = None
    micrAccountHash: Optional[str] = None

    imageFrontUri: Optional[str] = None
    imageBackUri: Optional[str] = None

    status: str
    createdAt: datetime

    amount: float
    currency: str
    confirmedFraud: Optional[bool] = False


class ConsortiumScoreResponse(BaseModel):
    score: float
    topReasons: List[str]
    supportingLinkedCounts: Dict[str, Any]
    recencyWindows: Dict[str, Any]
    explanation: Dict[str, Any]


# -----------------------------
# In-memory consortium store
# Replace with PostgreSQL / DynamoDB / feature store later
# -----------------------------

EVENTS: List[ConsortiumEventRequest] = []

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
    return sum(1 for e in events if e.confirmedFraud)


def recent(events: List[ConsortiumEventRequest], now: datetime, days: int) -> List[ConsortiumEventRequest]:
    cutoff = days_ago(now, days)
    return [e for e in events if e.depositTimestamp >= cutoff]


# -----------------------------
# TokenLinkService
# -----------------------------

class TokenLinkService:

    @staticmethod
    def index_event(event: ConsortiumEventRequest):
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

        EVENTS.append(event)

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

def consortium_process_event(event: ConsortiumEventRequest) -> ConsortiumScoreResponse:
    features = ConsortiumFeatureService.compute_features(event)
    result = CrossInstitutionEvidenceService.explain(features)

    TokenLinkService.index_event(event)

    return ConsortiumScoreResponse(**result)

# -----------------------------
# API
# -----------------------------

@app.post("/match", response_model=ConsortiumScoreResponse)
def match(event: ConsortiumEventRequest):
    print("match api called with event:", event)
    return consortium_process_event(event)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/debug/stats")
def stats():
    return {
        "eventCount": len(EVENTS),
        "tokenIndexSize": len(TOKEN_INDEX),
        "relationshipIndexSize": len(RELATION_INDEX),
        "bankFlowIndexSize": len(BANK_FLOW_INDEX),
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("consortium_service_app:app", host="0.0.0.0", port=8081)
