import asyncio
import os
import time

import json

from db.connection.consortium_db_connection import get_db_session_from_context
from service.consortium_service import consortium_process_event, set_fraud_disposition

from services.commons.kafka_event_handler import KafkaEventHandler
from services.commons.kafka_completion import CompletionReporter
from aiokafka import AIOKafkaProducer

from dtos.consortium_dtos import FraudDispositionEventRequest, ConsortiumEventRequest


class ConsortiumKafkaHandler(KafkaEventHandler):
    def __init__(self, name: str, topic: str, group_id: str, bootstrap_servers: str, reporter: CompletionReporter):
        super().__init__(name, topic, bootstrap_servers, group_id)
        self.reporter = reporter


    async def handle_event(self, event: dict):
        print(f"Received {self.name} event:", json.dumps(event, indent=4))
        start = time.time()
        if self.name == "consortium_kafka_check.deposit.fraud.decision":
            event_id = str(event.get("eventId", ""))
            try:
                req = FraudDispositionEventRequest.model_validate(event)
                with get_db_session_from_context() as db:
                    await asyncio.to_thread(set_fraud_disposition, event_id, req, db)
            except Exception as e:
                await self.reporter.report_failure(
                    event_id=event_id or "UNKNOWN",
                    latency_ms=int((time.time() - start) * 1000),
                    error=str(e),
                )

        elif self.name == "consortium_kafka_check.deposit.created":
            event_id = str(event.get("eventId", ""))
            try:
                req = ConsortiumEventRequest.model_validate(event)
                with get_db_session_from_context() as db:
                    consortium_score_response = await asyncio.to_thread(consortium_process_event, req, db)
                latency_ms = int((time.time() - start) * 1000)
                print(f"Giving Response, latency= {latency_ms}ms :",
                      json.dumps(consortium_score_response.model_dump(), indent=4))
                await self.reporter.report_success(
                    event_id=event_id or "UNKNOWN",
                    latency_ms=latency_ms,
                    details=consortium_score_response.model_dump(),
                )
            except Exception as e:
                print(e)
                await self.reporter.report_failure(
                    event_id=event_id or "UNKNOWN",
                    latency_ms=int((time.time() - start) * 1000),
                    error=str(e),
                )


async def consortium_lifespan(stop_event: asyncio.Event):
    bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    completion_topic = os.getenv("KAFKA_COMPLETION_TOPIC", "check.deposit.service.completed")

    producer = AIOKafkaProducer(bootstrap_servers=bootstrap)
    await producer.start()
    reporter = CompletionReporter(producer, topic=completion_topic, service_name="consortium")

    topics = ["check.deposit.created", "check.deposit.fraud.decision"]
    handlers = [
        ConsortiumKafkaHandler(
            f"consortium_kafka_{topic}",
            topic,
            group_id="consortium-fraud-service",
            bootstrap_servers=bootstrap,
            reporter=reporter,
        )
        for topic in topics
    ]
    tasks = [
        asyncio.create_task(handler.run_consumer(stop_event))
        for handler in handlers
    ]

    return producer, tasks