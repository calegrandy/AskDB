#!/bin/sh
# Loads the Pagila sample database (https://github.com/devrimgunduz/pagila) on the container's first start.
# Pinned to one commit so the data, and any eval answers based on it, never change.
set -e

PAGILA_COMMIT=e0e35a666f31a786b9d3c06cb83799fc46db25d4
BASE_URL="https://raw.githubusercontent.com/devrimgunduz/pagila/$PAGILA_COMMIT"

cd /tmp
wget -q -O pagila-schema.sql "$BASE_URL/pagila-schema.sql"
wget -q -O pagila-data.sql "$BASE_URL/pagila-data.sql"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f pagila-schema.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f pagila-data.sql

rm pagila-schema.sql pagila-data.sql

# A least-privileged user for AskDB to connect as: it can read the sample data and nothing else.
# (Superusers can read server files with SQL functions, which a read-only transaction doesn't prevent.)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE askdb_reader LOGIN PASSWORD 'askdb_reader';
GRANT CONNECT ON DATABASE pagila TO askdb_reader;
GRANT USAGE ON SCHEMA public TO askdb_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO askdb_reader;
SQL
