--liquibase formatted sql
--changeset minibank:002-transactions-index

CREATE INDEX idx_transactions_account_date
    ON transactions (account_id, transaction_date);