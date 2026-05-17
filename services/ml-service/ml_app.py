from fastapi import FastAPI

import asyncio
from contextlib import asynccontextmanager

from controller.ml_controller import router
from service.ml_kafka_handler import ml_lifespan


@asynccontextmanager
async def lifespan(app: FastAPI):
    # create_all_schema()

    stop_event = asyncio.Event()

    producer, tasks = await ml_lifespan(stop_event)

    try:
        yield

    finally:
        stop_event.set()
        await asyncio.gather(*tasks)
        await producer.stop()


app = FastAPI(title="ML service", lifespan=lifespan)
app.include_router(router=router)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("ml_app:app", host="0.0.0.0", port=8083, reload=True)