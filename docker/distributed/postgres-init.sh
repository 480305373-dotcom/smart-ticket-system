#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  --set=core_password="$CORE_DB_PASSWORD" --set=knowledge_password="$KNOWLEDGE_DB_PASSWORD" <<'SQL'
CREATE ROLE core LOGIN PASSWORD :'core_password';
CREATE ROLE knowledge LOGIN PASSWORD :'knowledge_password';
CREATE DATABASE intelligent_ticket OWNER core;
CREATE DATABASE smartticket_knowledge OWNER knowledge;
REVOKE CONNECT ON DATABASE intelligent_ticket FROM PUBLIC;
REVOKE CONNECT ON DATABASE smartticket_knowledge FROM PUBLIC;
GRANT CONNECT ON DATABASE intelligent_ticket TO core;
GRANT CONNECT ON DATABASE smartticket_knowledge TO knowledge;
SQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname intelligent_ticket -c 'CREATE EXTENSION IF NOT EXISTS vector;'
