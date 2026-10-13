#!/usr/bin/env bash
# Recreates the golden-dataset database and starts the ERP backend against it on port 8081.
#
# Isolation: the normal ERP (port 8080, database "erpai") is not touched. The golden ERP uses the same
# local PostgreSQL (port 5432, user vacos — as in backend application.yml) but its own database
# "erpai_golden", dropped and recreated on every start -> same empty state, same ids each run.
# Flyway creates the schema (V1..V6) when the backend starts. Stop with Ctrl+C.
#
# Usage (Git Bash, not PowerShell's WSL bash):  bash eval/scripts/golden-erp.sh
set -euo pipefail                  # stop on the first error, on unset variables and on failed pipes
cd "$(dirname "$0")/../.."         # repo root (this file lives in eval/scripts/)

# Connection settings; each can be overridden from outside (VAR=value bash eval/scripts/golden-erp.sh).
DB_HOST="${GOLDEN_DB_HOST:-localhost}"
DB_PORT="${GOLDEN_DB_PORT:-5432}"
DB_USER="${GOLDEN_DB_USER:-vacos}"
DB_PASSWORD="${GOLDEN_DB_PASSWORD:-vacos}"
DB_NAME=erpai_golden
# psql from PATH if available, otherwise the local PostgreSQL 16 install.
PSQL="${GOLDEN_PSQL:-$(command -v psql || echo "/c/Program Files/PostgreSQL/16/bin/psql.exe")}"

# 1. Fresh, empty database. We connect to the existing "erpai" database to run the commands,
#    because a database cannot be dropped while you are connected to it.
#    WITH (FORCE) also kicks out connections left over from a previous run.
export PGPASSWORD="$DB_PASSWORD"
"$PSQL" -q -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d erpai -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE)"
"$PSQL" -q -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d erpai -c "CREATE DATABASE $DB_NAME"
echo "Database $DB_NAME recreated on $DB_HOST:$DB_PORT. Starting backend on port 8081 (first start may take a minute)..."

# 2. Point Spring Boot at that database. Environment variables override application.yml
#    (Spring relaxed binding: SPRING_DATASOURCE_URL -> spring.datasource.url).
#    The backend needs Java 22; override the JDK path with GOLDEN_JAVA_HOME if yours differs.
export JAVA_HOME="${GOLDEN_JAVA_HOME:-$HOME/.jdks/openjdk-22.0.2}"
export SPRING_DATASOURCE_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME"
export SPRING_DATASOURCE_USERNAME="$DB_USER"
export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"

# 3. Run the backend on port 8081 with readable (dev) logs. `exec` replaces this shell,
#    so Ctrl+C goes straight to Maven/Spring.
cd backend
exec ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=8081
