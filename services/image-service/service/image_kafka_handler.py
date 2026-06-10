import asyncio
import json
import os
import time

from aiokafka import AIOKafkaProducer

from db.connection.image_db_connection import get_db_session_from_context
from dtos.image_dtos import ImageAnalysisRequest
from kafka_completion import CompletionReporter
from kafka_event_handler import KafkaEventHandler
from service.image_service import image_process_event


class ImageKafkaHandler(KafkaEventHandler):
    def __init__(self, name: str, topic: str, group_id: str, bootstrap_servers: str, reporter: CompletionReporter):
        super().__init__(name, topic, bootstrap_servers, group_id)
        self.reporter = reporter

    async def handle_event(self, event: dict):
        print(f"Received {self.name} event:", json.dumps(event, indent=4))
        start = time.time()
        if self.name == "image_kafka_Image_Service_CMD":
            event_id = str(event.get("eventId", ""))
            try:
                req = ImageAnalysisRequest.model_validate(event)
                with get_db_session_from_context() as db:
                    image_analysis_response = await asyncio.to_thread(image_process_event, req, db)
                latency_ms = int((time.time() - start) * 1000)
                print(f"Giving Response, latency= {latency_ms}ms :",
                      json.dumps(image_analysis_response.model_dump(), indent=4))
                await self.reporter.report_success(
                    event_id=event_id or "UNKNOWN",
                    latency_ms=latency_ms,
                    details=image_analysis_response.model_dump(),
                )
            except Exception as e:
                print(e)
                await self.reporter.report_failure(
                    event_id=event_id or "UNKNOWN",
                    latency_ms=int((time.time() - start) * 1000),
                    error=str(e),
                )


async def image_lifespan(stop_event: asyncio.Event):
    bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "host.docker.internal:9092")
    completion_topic = os.getenv("KAFKA_COMPLETION_TOPIC", "Image_Service_Response")

    producer = AIOKafkaProducer(bootstrap_servers=bootstrap)
    await producer.start()
    reporter = CompletionReporter(producer, topic=completion_topic, service_name="image")

    topics = ["Image_Service_CMD", "check.deposit.fraud.decision"]
    handlers = [
        ImageKafkaHandler(
            f"image_kafka_{topic}",
            topic,
            group_id="image-fraud-service",
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
