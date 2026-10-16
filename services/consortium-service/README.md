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

The service scores deposit events and learns from fraud decisions. It exposes both a REST API and Kafka consumers (see [Interfaces](#interfaces)). A deposit event (`ConsortiumEventRequest`) contains:
```python
eventId: str                          # generated per event if omitted
institutionId: str
clearingInstitutionId: Optional[str] = None
channel: str
depositTimestamp: datetime
accountToken: Optional[str] = None
payeeToken: Optional[str] = None
payorToken: Optional[str] = None
deviceToken: Optional[str] = None
region: Optional[str] = None
checkSerialHash: str
micrRoutingHash: Optional[str] = None
micrAccountHash: Optional[str] = None
imageFrontUri: Optional[str] = None
imageBackUri: Optional[str] = None
amount: float
currency: str
fraudDisposition: UNKNOWN | CONFIRMED_FRAUD | CLEARED = UNKNOWN
imageFingerprint: Optional[str] = None   # computed by this service, see section 2
```

## Interfaces
| Interface | Purpose |
|---|---|
| `POST /consortium/match` | Score one deposit event and index it. Returns a `ConsortiumScoreResponse`. |
| `POST /consortium/fraud-decision?event_id=...&disposition=CONFIRMED_FRAUD\|CLEARED\|UNKNOWN` | Record the outcome for an event. Returns `{"status": "updated"}` or `{"status": "not_found"}`. |
| `GET /consortium/health` | Liveness check. |
| `GET /consortium/debug/stats` | Row counts of the event table and the three indexes. |
| Kafka `Consortium_Service_CMD` (in) | Same as `/match`. The score is reported on `Consortium_Service_Response` (`details` holds the response). |
| Kafka `check.deposit.fraud.decision` (in) | `{"eventId", "fraudDisposition"}`, same as `/fraud-decision`. |

### 1. Privacy-Preserving Tokenization
Instead of storing raw sensitive data, the system uses tokens such as:
```python
accountToken
payeeToken
payorToken
deviceToken
imageFingerprint
```
The account, payee, payor and device tokens are expected to be generated using HMAC or salted hashing by the caller. `imageFingerprint` is the exception: the service computes it itself (see section 2). The service only stores and compares these tokens.
For explanation purposes, it returns only short preview hashes using:
```python
token_preview(token)
```
This supports the privacy principle:
```text
Raw identity is not shared.
Only stable, comparable, pseudonymous identifiers are used.
```

### 2. Image Fingerprint and Consortium Event Indexing
#### Image fingerprint
`compute_image_fingerprint` builds one SHA-256 fingerprint of the physical check, so the same check presented twice produces the same value:
```text
front image perceptual hash (phash)
back image perceptual hash (phash)
MICR hash   = sha256(micrRoutingHash | micrAccountHash | checkSerialHash)
serial      = checkSerialHash
amount hash = sha256(amount to 2 decimals | currency)
```
It deliberately excludes the depositor (`accountToken`), device, timestamp, channel and payee, because those differ between presentments of the same check. Image URIs can be a local path or an http(s) URL. A missing or unreadable image makes the request fail.

#### Event indexing
The `TokenLinkService` persists each scored event in PostgreSQL (tables created at startup):
```python
consortium_events   # one row per event, including fraudDisposition
token_index         # (token type, token value) -> event
relation_index      # (payor, payee) -> event
bank_flow_index     # (deposit bank, clearing bank) -> event
```
An event is scored against history first and indexed afterwards, so it never matches itself. Conceptually, these indexes answer different fraud questions.

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
At larger scale, these lookups would normally move to a dedicated store such as Redis, Elasticsearch, or a feature store.

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
Because the event being scored is not indexed yet, the image rule counts the current event's institution as well. The same image seen earlier at one other bank is therefore already "multiple institutions".

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

#### Rule Reference
Every rule adds to the score of its own category. Each category score is capped at 1.0 (`clamp`). Windows are counted back from the event's `depositTimestamp`.

**Token rules**, computed per token (payee, payor, account, device, image). Only the highest token score goes into the final blend.

| Rule | Applies to | Condition | Adds | Reason code |
|---|---|---|---|---|
| Image reuse | image | seen at 2 or more institutions in 30d (including the current one) | +0.65 | `SAME_IMAGE_HASH_SEEN_AT_MULTIPLE_INSTITUTIONS` |
| Device fraud link | device | 1 or more `CONFIRMED_FRAUD` events in 30d | +0.60 | `DEVICE_LINKED_TO_PRIOR_CONFIRMED_FRAUD` |
| Payee spread | payee | seen at 6 or more institutions in 14d | +0.45 | `PAYEE_SEEN_IN_N_INSTITUTIONS_14D` |
| Repeat fraud | any token | 2 or more `CONFIRMED_FRAUD` events in 30d | +0.35 | `<TYPE>_TOKEN_HAS_N_PRIOR_FRAUDS_30D` |
| Cross-institution spread | any token | seen at 3 or more institutions in 14d | +0.20 | `<TYPE>_CROSS_INSTITUTION_SPREAD` |

**Payor-payee relationship rules**

| Rule | Condition | Adds | Reason code |
|---|---|---|---|
| New relationship | pair not seen in 30d | +0.25 | `NEW_PAYOR_PAYEE_RELATIONSHIP` |
| Prior fraud | 1 or more `CONFIRMED_FRAUD` events for the pair in 30d | +0.45 | `PAYOR_PAYEE_RELATIONSHIP_HAS_PRIOR_FRAUD` |
| Multi-institution | pair seen at 3 or more institutions in 30d | +0.25 | `PAYOR_PAYEE_RELATIONSHIP_SPANS_MULTIPLE_INSTITUTIONS` |

**Bank-flow rules** (need `clearingInstitutionId`)

| Rule | Condition | Adds | Reason code |
|---|---|---|---|
| Elevated fraud rate | 5 or more events and a fraud rate of 25% or more in 30d | +0.55 | `ELEVATED_DEPOSIT_TO_CLEARING_BANK_FRAUD_RATE` |
| Repeated fraud | 3 or more `CONFIRMED_FRAUD` events in 30d | +0.30 | `BANK_FLOW_HAS_MULTIPLE_PRIOR_FRAUDS_30D` |

Notes:
- The device, repeat-fraud, relationship-fraud and bank-flow rules depend on fraud decisions (see section 6).
- Only the single worst token counts, so one very risky token outweighs several mildly risky ones.
- `topReasons` returns all triggered codes sorted alphabetically (not by severity) and limited to 8.
- Because of the 0.55 / 0.25 / 0.20 blend, each category contributes at most 0.55, 0.25 and 0.20 to the final score. A same-image duplicate with nothing else risky scores 0.55 × 0.65 = 0.3575.

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

### 6. Fraud Decisions (Feedback Loop)
Fraud-related rules only count events whose `fraudDisposition` is `CONFIRMED_FRAUD`. All new events start as `UNKNOWN`, so the device, repeat-fraud, relationship-fraud and bank-flow rules have nothing to fire on until decisions are recorded.

`set_fraud_disposition` (called by `/consortium/fraud-decision` and by the `check.deposit.fraud.decision` Kafka topic) updates the event row and its copies in the three index tables. The next event that shares a token, relationship or bank flow with it is scored with that outcome. An unknown `eventId` is logged and reported as `not_found`.

### 7. Example Interpretation
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

### 8. Production Limitations
Events and indexes are persisted in PostgreSQL, so data survives restarts and can be shared by several service instances. Remaining MVP limitations:
```text
Scoring thresholds and weights are hand-set, not calibrated on real data
Image fingerprint uses simple perceptual hashes and is sensitive to scan/photo differences
No access control or audit log for consortium data
Lookups join the full event table, with no cache or feature store
Scoring and indexing happen in one request, with no deduplication of retried Kafka messages
```
For production, consider:
```text
Redis or a feature store for fast lookup
Audit logs for investigation
Access control for consortium data
Calibrated thresholds validated against labelled fraud outcomes
```
