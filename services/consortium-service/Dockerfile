FROM python:3.14.4-slim
WORKDIR /app
ENV PYTHONPATH=/app

COPY services/consortium-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY services/consortium-service/consortium_app.py .
COPY services/consortium-service/controller ./controller
COPY services/consortium-service/db ./db
COPY services/consortium-service/dtos ./dtos
COPY services/consortium-service/service ./service
COPY services/commons/*.py ./services/commons/

COPY synthetic-data/out/images/*.png ./images/

CMD ["uvicorn","consortium_app:app","--host","0.0.0.0","--port","8003"]
