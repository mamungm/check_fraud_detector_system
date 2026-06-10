FROM python:3.14.4-slim
WORKDIR /app
ENV PYTHONPATH=/app

RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

COPY services/image-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY services/image-service/image_app.py .
COPY services/image-service/controller ./controller
COPY services/image-service/db ./db
COPY services/image-service/dtos ./dtos
COPY services/image-service/service ./service
COPY services/commons/*.py ./services/commons/

COPY synthetic-data/out/images/check_1_normal.png ./images/check_1_normal.png
COPY synthetic-data/out/images/back_check_1_normal.png ./images/back_check_1_normal.png

CMD ["uvicorn","image_app:app","--host","0.0.0.0","--port","8002"]
