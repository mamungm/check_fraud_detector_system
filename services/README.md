# System Architecture
Full Cheque fraud detection system follows a microservices architecture with the following components:
1. **Fraud Detector(Orchestrator)**: This service acts as an orchestrator between all the other services. Receives deposit event ingestion, publishes kafka events to other services. Tracks the results from consortium and image services, publishes kafka event to ml service. Provides error or success response to the application through websocket.
2. **Consortium Service**: Implements privacy-preserving techniques to enable cross-institution fraud detection without sharing raw PII data. It stores HMAC tokens, aggregate counts, risk scores, and fraud metadata to identify potential fraud patterns across multiple banks while maintaining data privacy.
3. **Image Service**: Analyzes cheque images using computer vision techniques to extract features such as handwriting analysis, signature verification, and cheque alteration detection. It provides additional insights and risk factors for the ML scoring service.
4. **ML Service**: Receives events from the fraud-detector, processes the data using a machine learning model to generate a fraud risk score. The model is trained on historical data and incorporates features from the consortium and image services to improve accuracy.
5. **Rule based Service**: Implements a rules engine that evaluates the fraud risk score and other factors to determine the appropriate action (e.g., hold, manual review, pass). It allows for dynamic rule updates without redeploying the entire system.

<img src="resources/image/system_architecture.png" alt="System Architecture" width="1920">

## Sequence Diagram

<img src="resources/image/sequence_diagram.png" alt="Sequence Diagram" width="1920">