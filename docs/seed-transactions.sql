WITH demo_client AS (
INSERT INTO clients (name, email)
VALUES ('Demo Client', 'explain-demo@example.test')
    RETURNING id
    ),
    demo_accounts AS (
INSERT INTO accounts (client_id, account_number)
SELECT demo_client.id, 'BENCH-' || lpad(n::text, 3, '0')
FROM demo_client
    CROSS JOIN generate_series(1, 100) AS accounts(n)
    RETURNING id
    )
INSERT INTO transactions (account_id, amount, type, transaction_date)
SELECT demo_accounts.id,
       n + 1,
       'TRANSFER_IN',
       TIMESTAMP '2025-06-01 00:00:00' + n * INTERVAL '1 minute'
FROM demo_accounts
    CROSS JOIN generate_series(0, 599) AS tx(n);

ANALYZE accounts;
ANALYZE transactions;

SELECT count(*) AS transaction_count FROM transactions;