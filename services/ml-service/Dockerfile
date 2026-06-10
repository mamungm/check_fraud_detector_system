FROM python:3.11-slim
WORKDIR /app
ENV PYTHONPATH=/app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ml_app.py .
COPY controller ./controller
COPY dtos ./dtos
COPY service ./service
COPY models ./models

CMD ["uvicorn","ml_app:app","--host","0.0.0.0","--port","8001"]
