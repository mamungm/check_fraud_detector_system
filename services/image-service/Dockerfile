FROM python:3.14.4-slim
WORKDIR /app
ENV PYTHONPATH=/app

COPY image-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY image-service/image_app.py .
COPY image-service/controller ./controller
COPY image-service/db ./db
COPY image-service/dtos ./dtos
COPY image-service/service ./service
COPY commons/*.py ./services/commons/

CMD ["uvicorn","image_app:app","--host","0.0.0.0","--port","8002"]
