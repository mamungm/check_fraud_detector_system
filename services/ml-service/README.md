# ML-Based Check Fraud Scoring Service
This implementation represents two-stage machine-learning fraud scoring service of a check-fraud detection system. It loads trained fraud models, receives deposit or in-clearing events, converts event attributes into model features, predicts fraud probability, assigns a risk band, recommends an action, and returns explainable feature-level reasons.

### 1. Overall Purpose
The service is designed to answer one core question:
```text
How risky is this check event?
```
It supports three scoring modes:
```text
Deposit fraud scoring
In-clearing fraud scoring
Combined fraud scoring
```
The deposit model estimates early fraud risk using transaction, behavioral, image, rule, and outcome features

The in-clearing model refines the risk using clearing-stage and inter-bank signals.

The combined score merges both probabilities into a final fraud probability, maps it into risk bands, recommends an action, and returns approximate explanations for analyst review.

### 2. Model Training Theory
The training scripts build two supervised binary-classification models using XGBoost:
```text
label = 0 → legitimate
label = 1 → fraud
```
The deposit model is trained using account behavior, payee/device risk, image analysis signals, rule-engine signals, and transaction outcome signals.

The in-clearing model is trained using clearing-stage features such as deposit score, days since deposit, amount match, drawee-bank prior fraud, cross-bank risk, return-code presence, chargeback presence, and loss amount.

Both models use:
```text
XGBoost classifier
One-hot encoding for channel
numeric passthrough for numerical features
isotonic calibration
train/test split with stratification
```

### 3. Why XGBoost Is Used
XGBoost is suitable here because fraud data is usually:
```text
tabular
imbalanced
nonlinear
feature-interaction heavy
```
For example, a new device alone may not be highly suspicious. But this combination may be suspicious:
```text
new device
high amount
duplicate image
payee fraud history
multiple institutions recently seen
```
Tree-based boosting models are strong at learning these interactions.

### 4. Probability Calibration
The implementation wraps the model using:
```python
CalibratedClassifierCV(method="isotonic", cv=3)
```
The theoretical purpose is to make the model probability more meaningful.

Without calibration, a model score of 0.80 may only mean “high model confidence.” With calibration, the goal is closer to:
```text
Among similar events scored near 0.80, around 80% are expected to be fraud.
```
This matters because the service uses probability thresholds to trigger operational actions.

### 5. Deposit Fraud Model
The deposit model uses features from multiple fraud dimensions:
```text
transaction amount
account deposit velocity
account average amount
new device indicator
payee cross-institution activity
payee fraud history
device fraud history
image duplicate score
OCR amount match
layout anomaly
font anomaly
signature score
endorsement score
rule hit count
critical rule hit count
return code
chargeback
loss amount
channel
```
The theory is that fraud risk is stronger when multiple weak signals combine.

Example:
```text
high amount + new device + duplicate image + OCR mismatch
```
is much more suspicious than only a high amount.

### 6. In-Clearing Fraud Model
The in-clearing model is a second-stage model.

It uses features that are more relevant after the check enters the clearing process:
```text
deposit_score
days_since_deposit
clearing_amount_matches_deposit
drawee_bank_prior_fraud_30d
deposit_to_clearing_bank_risk
return_code_present
chargeback_present
loss_amount
channel
```
This model acts as a downstream fraud-risk refinement layer.

The key idea is:
```text
deposit model = early risk signal
in-clearing model = clearing-stage confirmation signal
```

### 7. Combined Scoring Theory
The combined endpoint computes both probabilities:
```text
deposit_prob
in_clearing_prob
```
Then it calculates:
```python
final_prob = max(deposit_prob, 0.45 * deposit_prob + 0.55 * in_clearing_prob)
```
This design has two important theoretical properties:
```text
1. The final score never goes below the original deposit risk.
2. The in-clearing score has slightly higher influence when available.
```
So if the deposit model already sees high risk, the final score preserves it. If the clearing model finds stronger evidence later, the final score can increase.

### 8. Risk Bands and Actions
The service maps probability into operational categories:
```text
HIGH      → probability >= 0.85
MEDIUM    → probability >= 0.60
LOW       → probability >= 0.30
MINIMAL   → probability < 0.30
```
Recommended actions are:
```text
HIGH   → HOLD
MEDIUM → MANUAL_REVIEW
LOW/MINIMAL → PASS
```
This converts raw ML output into decisions that a fraud operations team can use.

### 9. Explainability Layer
The implementation includes approximate feature contributions.

This is not true SHAP explainability. Instead, it multiplies feature values by predefined weights:
```python
approxContribution = feature_value × explanation_weight
```
The purpose is to produce analyst-friendly reasons such as:
```text
image_duplicate_score=0.9
critical_rule_hit_count=1
payee_fraud_count_30d=2
deposit_to_clearing_bank_risk=0.7
```
This gives a quick explanation of why the model considered an event risky.

### 10. Evaluation Metrics
The training scripts evaluate the models using:
```text
PR-AUC
precision at 1%
precision at 5%
recall at 1%
recall at 5%
precision at threshold 0.60
recall at threshold 0.60
Brier score
false positive rate by institution
average scoring latency
```
These metrics are appropriate because fraud detection usually has class imbalance. PR-AUC and precision-at-k are often more useful than accuracy.

False-positive rate by institution is also important because one institution should not be unfairly or disproportionately over-flagged.

