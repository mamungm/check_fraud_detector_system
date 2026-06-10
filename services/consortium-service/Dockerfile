FROM python:3.14.4-slim
WORKDIR /app
ENV PYTHONPATH=/app

COPY consortium-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY consortium-service/consortium_app.py .
COPY consortium-service/controller ./controller
COPY consortium-service/db ./db
COPY consortium-service/dtos ./dtos
COPY consortium-service/service ./service
COPY commons/*.py ./services/commons/

CMD ["uvicorn","consortium_app:app","--host","0.0.0.0","--port","8003"]
