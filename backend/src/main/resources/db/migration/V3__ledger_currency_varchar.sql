-- Match the JPA mapping: Hibernate expects VARCHAR for String columns.
-- CHAR(3) (bpchar) fails PostgreSQL schema validation; V2 used CHAR(3).
ALTER TABLE ledger_entries ALTER COLUMN currency TYPE VARCHAR(3);
