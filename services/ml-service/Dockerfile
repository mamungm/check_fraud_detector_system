FROM python:3.14.4-slim
WORKDIR /app
ENV PYTHONPATH=/app

COPY ml-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ml-service/ml_app.py .
COPY ml-service/controller ./controller
COPY ml-service/dtos ./dtos
COPY ml-service/service ./service
COPY ml-service/models ./models
COPY commons/kafka_event_handler.py .
COPY commons/kafka_completion.py .

CMD ["uvicorn","ml_app:app","--host","0.0.0.0","--port","8001"]
