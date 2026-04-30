"""
Batch feature recomputation placeholder.

Production version:
- reads deposit, clearing, alert, and history tables
- computes 7d and 30d velocity features
- writes feature tables or online feature store
- calculates model-observability drift metrics
"""
import os
import psycopg

DSN = os.getenv("DSN", "postgresql://fraud:fraud@localhost:5432/frauddb")

SQL = """
CREATE TABLE IF NOT EXISTS account_features_daily AS
SELECT
  account_token,
  date_trunc('day', deposit_timestamp) AS feature_day,
  count(*) AS deposit_count,
  avg(amount) AS avg_amount,
  max(amount) AS max_amount
FROM deposit_event
GROUP BY 1,2;
"""

if __name__ == "__main__":
    with psycopg.connect(DSN) as conn:
        conn.execute(SQL)
    print("feature recomputation complete")
