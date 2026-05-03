# Consortium Risk Service
This implementation represents a privacy-preserving consortium fraud intelligence service for check fraud detection. The main idea is that one financial institution can assess whether a check deposit is risky by comparing tokenized signals against a shared network history, without exposing raw personally identifiable information.

Fraud is often not visible from one institution alone. One bank may see only one suspicious deposit. But across a consortium, the same payee, image, device, account, or payor relationship may appear repeatedly.
So the system turns isolated events into network intelligence.
```text
single-bank view → network-level fraud view
```
This aligns well with real financial-crime platforms because it combines:
```text
rules
recency windows
cross-institution evidence
explainable scoring
privacy-preserving tokens
event-driven architecture
```

The service receives check deposit events through Kafka event stream from the `check.deposit.created` topic. Each event contains:
```python
eventId: str
institutionId: str
clearingInstitutionId: Optional[str] = None
channel: str
depositTimestamp: datetime
accountToken: Optional[str] = None
payeeToken: Optional[str] = None
payorToken: Optional[str] = None
deviceToken: Optional[str] = None
imageFingerprint: Optional[str] = None
region: Optional[str] = None
checkSerial: str
micrRoutingHash: Optional[str] = None
micrAccountHash: Optional[str] = None
imageFrontUri: Optional[str] = None
imageBackUri: Optional[str] = None
status: str
createdAt: datetime
amount: float
currency: str
confirmedFraud: Optional[bool] = False
```

### 1. Privacy-Preserving Tokenization
Instead of storing raw sensitive data, the system uses tokens such as:
```python
accountToken
payeeToken
payorToken
deviceToken
imageFingerprint
```
These tokens are expected to be generated using HMAC or salted hashing by the caller. The service only stores and compares these tokens.
For explanation purposes, it returns only short preview hashes using:
```python
token_preview(token)
```
This supports the privacy principle:
```text
Raw identity is not shared.
Only stable, comparable, pseudonymous identifiers are used.
```

### 2. Consortium Event Indexing
The `TokenLinkService` stores incoming events in several in-memory indexes:
```python
TOKEN_INDEX
RELATION_INDEX
BANK_FLOW_INDEX
EVENTS
```
Conceptually, these indexes answer different fraud questions.

#### Token index
This tracks how often a token appears across institutions.

Example:
```text
Has the same payee appeared at many banks recently?
Has the same device been linked to previous fraud?
Has the same check image fingerprint appeared at multiple institutions?
```
#### Relationship index
This tracks payor-payee relationships.

Example:
```text
Has this payor paid this payee before?
Is this a new relationship?
Has this relationship been involved in fraud?
```
#### Bank-flow index
This tracks flows between deposit banks and clearing banks.

Example:
```text
Is this deposit-bank-to-clearing-bank path showing elevated fraud activity?
```
In production, these indexes would normally be replaced by PostgreSQL, DynamoDB, Redis, Elasticsearch, or a feature store.

### 3. Network Risk Aggregation
The `NetworkRiskAggregator` calculates risk from three evidence categories.

#### Token Frequency Risk
For each token type, the system checks appearances over 7-day, 14-day, and 30-day windows.
It evaluates signals such as:
```text
Payee seen in many institutions
Same image hash seen at multiple institutions
Device linked to prior confirmed fraud
Token has multiple prior frauds
Token spread across institutions
```
Example rule:
```python
if token_type == "payee" and len(inst14) >= 6:
    score += 0.45
```
The theory is that fraud patterns often repeat across institutions. A mule payee, reused device, reused check image, or repeated token appearing at many banks can indicate coordinated activity.

#### Payor-Payee Relationship Risk
The system checks whether the payor-payee pair is new or historically risky.

A new relationship gets some risk:
```python
if len(e30) == 0:
    score += 0.25
```
Prior fraud increases risk:
```python
if fraud30 > 0:
    score += 0.45
```
The theory is that legitimate financial relationships often have historical continuity, while fraud attempts may introduce new or unusual relationships.

#### Bank Flow Risk
The system analyzes fraud rates between a deposit institution and a clearing institution.
Example:
```python
fraud_rate = fraud30 / total30
```
If a bank flow has many recent events and a high fraud rate, it becomes risky.

The theory is that some fraud campaigns concentrate around specific bank corridors or institution pairs.

### 4. Feature Computation Layer
`ConsortiumFeatureService` acts as the feature extraction layer.
It collects:
```python
tokenRiskResults
relationshipRisk
bankFlowRisk
```

This creates a structured feature object that can later be consumed by scoring, ML models, or investigation tools.
This separation is important because the system distinguishes between:
```text
raw event data → derived risk features → final consortium score
```

### 5. Cross-Institution Evidence Scoring
`CrossInstitutionEvidenceService` combines the evidence into a final risk score. The final score is calculated as:
```python
score = 0.55 * max_token_score
      + 0.25 * relationship_score
      + 0.20 * bank_flow_score
```
The highest token risk receives the largest weight because reused identity, image, device, or payee signals are strong consortium indicators.
The final response includes:
```text
score
topReasons
supportingLinkedCounts
recencyWindows
explanation
```
This makes the result explainable rather than just returning a black-box score.

### 6. Example Interpretation
A high score could mean:
```text
The same check image was seen at multiple institutions.
The payee token appeared at many banks in the last 14 days.
The device was previously linked to confirmed fraud.
The payor-payee relationship is new.
The deposit-to-clearing bank flow has elevated fraud rate.
```
A low score could mean:
```text
No meaningful consortium evidence was found.
The tokens do not appear risky.
No prior fraud links were detected.
The bank flow does not show elevated fraud.
```

### 9. Production Limitation
The current implementation is theoretical/MVP-level because it uses in-memory storage:
```python
EVENTS = []
TOKEN_INDEX = defaultdict(list)
```
That means data disappears when the service restarts and cannot scale across multiple service instances.

For production, this should be replaced with:
```text
PostgreSQL for persistent events
Redis for fast lookup
Feature store for model features
Kafka for event streaming
Audit logs for investigation
Access control for consortium data
```