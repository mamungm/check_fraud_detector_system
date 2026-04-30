import json
import time
from dataclasses import dataclass
from typing import Any, Dict, Optional

from aiokafka import AIOKafkaProducer


@dataclass(frozen=True)
class ServiceCompletionEvent:
    """Message sent by downstream services back to api-spring.

    Keep this payload small and stable: api-spring typically only needs to know
    whether a service finished successfully (or failed) for a given eventId.
    """

    eventId: str
    service: str
    status: str  # SUCCESS | FAILED
    latencyMs: int
    ts: float
    error: Optional[str] = None
    details: Optional[Dict[str, Any]] = None


class CompletionReporter:
    def __init__(
        self,
        producer: AIOKafkaProducer,
        *,
        topic: str,
        service_name: str,
    ):
        self._producer = producer
        self._topic = topic
        self._service_name = service_name

    async def report_success(self, *, event_id: str, latency_ms: int, details: Optional[Dict[str, Any]] = None) -> None:
        await self._send(
            ServiceCompletionEvent(
                eventId=event_id,
                service=self._service_name,
                status="SUCCESS",
                latencyMs=latency_ms,
                ts=time.time(),
                details=details,
            )
        )

    async def report_failure(
        self,
        *,
        event_id: str,
        latency_ms: int,
        error: str,
        details: Optional[Dict[str, Any]] = None,
    ) -> None:
        await self._send(
            ServiceCompletionEvent(
                eventId=event_id,
                service=self._service_name,
                status="FAILED",
                latencyMs=latency_ms,
                ts=time.time(),
                error=error,
                details=details,
            )
        )

    async def _send(self, evt: ServiceCompletionEvent) -> None:
        payload = {
            "eventId": evt.eventId,
            "service": evt.service,
            "status": evt.status,
            "latencyMs": evt.latencyMs,
            "ts": evt.ts,
        }
        if evt.error is not None:
            payload["error"] = evt.error
        if evt.details is not None:
            payload["details"] = evt.details

        # key = eventId => nice partitioning for consumers
        await self._producer.send_and_wait(
            self._topic,
            key=evt.eventId.encode("utf-8"),
            value=json.dumps(payload).encode("utf-8"),
        )

