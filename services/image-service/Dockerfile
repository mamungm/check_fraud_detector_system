FROM python:3.11-slim
WORKDIR /app
ENV PYTHONPATH=/app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY image_app.py .
COPY controller ./controller
COPY db ./db
COPY dtos ./dtos
COPY service ./service

CMD ["uvicorn","image_app:app","--host","0.0.0.0","--port","8002"]
