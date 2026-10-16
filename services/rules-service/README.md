# Rules Service

Deterministic fraud rules for a deposit. It reads the same `deposit_events` table as fraud-detector and counts earlier
deposits. It runs in parallel with the consortium and image services.

## Interfaces
| | |
|---|---|
| Input | Kafka `Rule_Service_CMD`: the saved `DepositEvent` as JSON |
| Output | Kafka `Rule_Service_Response`: `{eventId, service: "rule", status, latencyMs, ts, error, details}` |
| Port | 8004 |

`status` is `SUCCESS` (with `details` = the rule hits, empty when nothing fired) or `FAILED` (with `error`).
A failure is always reported, so the workflow never waits forever for this service.

## Rules
| Key | Code | Severity | Fires when |
|---|---|---|---|
| `rule_duplicate_presentment` | `DUPLICATE_PRESENTMENT` | CRITICAL | Another deposit has the same MICR routing, MICR account and serial hash within the lookback (365 days). Skipped when any hash is missing |
| `rule_high_deposit_velocity_7d` | `HIGH_DEPOSIT_VELOCITY_7D` | HIGH | More than 10 earlier deposits for the account in 7 days |
| `rule_new_device_for_account` | `NEW_DEVICE_FOR_ACCOUNT` | MEDIUM | No earlier deposit from this account and device in 90 days |
| `rule_high_amount_check` | `HIGH_AMOUNT_CHECK` | MEDIUM | Amount is 5000 or more |

Counts never include the deposit being evaluated. Each hit is `{ruleCode, severity, evidence}`.

## Configuration (`application.yaml`)
```yaml
rules:
  velocity-max-deposits7d: 10
  new-device-lookback-days: 90
  high-amount-threshold: 5000.0
  duplicate-lookback-days: 365
```

## Notes
- fraud-detector sends the command after its transaction commits, so the deposit is visible when the rules run.
- Rule hits are stored and shown by fraud-detector. They do not change the ML score or the workflow outcome.
