#!/bin/bash
# Reinitialize SQLite database for local testing
# This script drops and recreates the SQLite database with fresh schema

set -e

DB_PATH="./data/edi835-local.db"
SCHEMA_DIR="src/main/resources/db/sqlite"

echo "==================================="
echo "SQLite Database Reinitialization"
echo "==================================="

# Backup existing database if it exists
if [ -f "$DB_PATH" ]; then
    BACKUP_PATH="${DB_PATH}.backup.$(date +%Y%m%d_%H%M%S)"
    echo "Backing up existing database to: $BACKUP_PATH"
    cp "$DB_PATH" "$BACKUP_PATH"
    rm "$DB_PATH"
fi

# Create data directory if it doesn't exist
mkdir -p ./data

echo "Creating fresh SQLite database..."

# Initialize schema in correct order
echo "1. Loading change feed schema (schema.sql)..."
sqlite3 "$DB_PATH" < "$SCHEMA_DIR/schema.sql"

echo "2. Loading admin portal schema (admin-schema.sql)..."
sqlite3 "$DB_PATH" < "$SCHEMA_DIR/admin-schema.sql"

echo "3. Loading sample data (sample-data.sql)..."
sqlite3 "$DB_PATH" < "$SCHEMA_DIR/sample-data.sql"

echo ""
echo "✅ Database initialized successfully!"
echo ""

# Show table count
echo "Tables created:"
sqlite3 "$DB_PATH" ".tables"

echo ""
echo "NCPDP claims count:"
sqlite3 "$DB_PATH" "SELECT COUNT(*) as ncpdp_claims FROM ncpdp_raw_claims;"

echo ""
echo "NCPDP claims by status:"
sqlite3 "$DB_PATH" "SELECT status, COUNT(*) as count FROM ncpdp_raw_claims GROUP BY status;"

echo ""
echo "Ready to run: mvn test"
