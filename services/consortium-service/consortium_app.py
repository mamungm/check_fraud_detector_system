import asyncio
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from service.consortium_kafka_handler import consortium_lifespan
from controller.consortium_controller import router
from db.consortium_db_handler import create_all_schema

@asynccontextmanager
async def lifespan(app: FastAPI):
    create_all_schema()

    stop_event = asyncio.Event()

    producer, tasks = await consortium_lifespan(stop_event)

    try:
        yield

    finally:
        stop_event.set()
        await asyncio.gather(*tasks)
        await producer.stop()

app = FastAPI(title="Consortium Risk Service", lifespan=lifespan)
app.include_router(router=router)


if __name__ == "__main__":
    uvicorn.run("consortium_app:app", host="0.0.0.0", port=8081)
