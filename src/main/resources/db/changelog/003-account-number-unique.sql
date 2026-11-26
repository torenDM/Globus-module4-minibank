--liquibase formatted sql
--changeset minibank:003-account-number-unique

ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_account_number UNIQUE (account_number);