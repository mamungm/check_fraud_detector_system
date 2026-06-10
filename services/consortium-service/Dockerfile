FROM python:3.14.4-slim
WORKDIR /app
ENV PYTHONPATH=/app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY consortium_app.py .
COPY controller ./controller
COPY db ./db
COPY dtos ./dtos
COPY service ./service
COPY services ./services

CMD ["uvicorn","consortium_app:app","--host","0.0.0.0","--port","8003"]
