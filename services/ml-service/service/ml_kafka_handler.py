import asyncio
import json
import os
import time

from aiokafka import AIOKafkaProducer
from dtos.ml_dtos import CombinedScoreRequest
from kafka_completion import CompletionReporter
from kafka_event_handler import KafkaEventHandler
from service.ml_service import combined_process_event


class MLKafkaHandler(KafkaEventHandler):
    def __init__(self, name: str, topic: str, group_id: str, bootstrap_servers: str, reporter: CompletionReporter):
        super().__init__(name, topic, bootstrap_servers, group_id)
        self.reporter = reporter

    async def handle_event(self, event: dict):
        print(f"Received {self.name} event: {event}")
        start = time.time()
        event_id = str(event.get("eventId", ""))
        try:
            combined_score_request_event = CombinedScoreRequest.model_validate(event)
            combinedScoreResponse = combined_process_event(combined_score_request_event)
            await self.reporter.report_success(
                event_id=event_id or "UNKNOWN",
                latency_ms=int((time.time() - start) * 1000),
                details=combinedScoreResponse.model_dump(),
            )
            print(f"Processed {self.name} event {event_id} successfully in {int((time.time() - start) * 1000)} ms. "
                  f"response = {json.dumps(combinedScoreResponse.model_dump(), indent=4)}")
        except Exception as e:
            await self.reporter.report_failure(
                event_id=event_id or "UNKNOWN",
                latency_ms=int((time.time() - start) * 1000),
                error=str(e),
            )


async def ml_lifespan(stop_event: asyncio.Event):
    bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    completion_topic = os.getenv("KAFKA_COMPLETION_TOPIC", "check.deposit.ml.service.completed")

    producer = AIOKafkaProducer(bootstrap_servers=bootstrap)
    await producer.start()
    reporter = CompletionReporter(producer, topic=completion_topic, service_name="image")

    topics = ["check.deposit.ml.ready"]
    handlers = [
        MLKafkaHandler(
            f"ml_kafka_{topic}",
            topic,
            group_id="ml-fraud-service",
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