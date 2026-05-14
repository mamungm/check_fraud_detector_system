import asyncio
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI

from controller.image_controller import router
from db.connection.image_db_connection import create_all_schema
from service.image_kafka_handler import image_lifespan


@asynccontextmanager
async def lifespan(app: FastAPI):
    create_all_schema()

    stop_event = asyncio.Event()

    producer, tasks = await image_lifespan(stop_event)

    try:
        yield

    finally:
        stop_event.set()
        await asyncio.gather(*tasks)
        await producer.stop()

app = FastAPI(title="Image Analysis Service", lifespan=lifespan)
app.include_router(router=router)


if __name__ == "__main__":
    uvicorn.run("image_app:app", host="0.0.0.0", port=8082)
