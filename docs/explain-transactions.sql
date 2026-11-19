EXPLAIN (ANALYZE, BUFFERS)
SELECT id, account_id, amount, type, transaction_date
FROM transactions
WHERE account_id = (
    SELECT id FROM accounts WHERE account_number = 'BENCH-001'
)
  AND transaction_date >= TIMESTAMP '2025-06-01 02:05:00'
  AND transaction_date <  TIMESTAMP '2025-06-01 02:07:00'
ORDER BY transaction_date;