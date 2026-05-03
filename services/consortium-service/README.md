# Consortium Risk Service
This implementation represents a privacy-preserving consortium fraud intelligence service for check fraud detection. The main idea is that one financial institution can assess whether a check deposit is risky by comparing tokenized signals against a shared network history, without exposing raw personally identifiable information.

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

## Quick start

```bash
docker compose up --build
```