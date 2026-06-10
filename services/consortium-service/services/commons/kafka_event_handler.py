import asyncio
import json
import logging
import os
from typing import Optional

from aiokafka import AIOKafkaConsumer


class KafkaEventHandler:
    """Small reusable aiokafka consumer wrapper.

    Notes on consumer groups:
    - If TWO different services use the SAME `group_id` on the SAME topic, they will
      *share* work (Kafka assigns partitions across consumers). With a 1-partition topic,
      only one service will receive messages.
    - If you want BOTH services to receive ALL messages, give each service a DIFFERENT
      `group_id`.
    """

    def __init__(
        self,
        name: str,
        topics: str,
        bootstrap_servers: Optional[str] = None,
        group_id: Optional[str] = None,
        auto_offset_reset: str = "earliest",
        enable_auto_commit: bool = False,
        poll_timeout_ms: int = 1000,
        max_records: int = 100,
    ):
        self.name = name
        self.logger = logging.getLogger(name)
        self.topics = topics

        # Allow Docker-friendly configuration without changing code.
        self.bootstrap_servers = bootstrap_servers or os.getenv(
            "KAFKA_BOOTSTRAP_SERVERS", "host.docker.internal:9092"
        )

        # Keep backward-compatible default, but strongly prefer passing per-service IDs.
        self.group_id = group_id or os.getenv("KAFKA_GROUP_ID", "fraud-service")

        self.auto_offset_reset = auto_offset_reset
        self.enable_auto_commit = enable_auto_commit
        self.poll_timeout_ms = poll_timeout_ms
        self.max_records = max_records

    async def handle_event(self, event: dict):
        # Override in a subclass, or monkey-patch by composition.
        print(f"Received {self.name} event: {event}")

    async def run_consumer(self, stop_event: asyncio.Event):
        consumer = AIOKafkaConsumer(
            self.topics,
            bootstrap_servers=self.bootstrap_servers,
            group_id=self.group_id,
            auto_offset_reset=self.auto_offset_reset,
            enable_auto_commit=self.enable_auto_commit,
        )

        await consumer.start()
        self.logger.info(
            "Kafka consumer started name=%s topics=%s bootstrap=%s group_id=%s",
            self.name,
            self.topics,
            self.bootstrap_servers,
            self.group_id,
        )

        try:
            while not stop_event.is_set():
                try:
                    batches = await consumer.getmany(
                        timeout_ms=self.poll_timeout_ms,
                        max_records=self.max_records,
                    )

                    if not batches:
                        continue

                    for _tp, messages in batches.items():
                        for msg in messages:
                            try:
                                event = json.loads(msg.value.decode("utf-8"))
                                await self.handle_event(event)
                            except Exception:
                                # Important: current logic commits the batch even if one message fails.
                                # For at-least-once, consider committing per message after success.
                                self.logger.exception("Failed to process Kafka message")

                    if not self.enable_auto_commit:
                        await consumer.commit()

                except Exception:
                    self.logger.exception("Kafka consumer loop error")
                    await asyncio.sleep(2)

        finally:
            self.logger.info("Stopping Kafka consumer name=%s", self.name)
            await consumer.stop()
