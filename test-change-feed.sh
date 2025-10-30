#!/bin/bash
# test-change-feed.sh
# Quick script to test EDI 835 change feed processing
#
# This script tests the complete change feed flow:
# 1. Inserts a test claim (PAYER001 -> PAYEE001)
# 2. Verifies trigger creates entry in data_changes table
# 3. Waits for SQLiteChangeFeedProcessor to process the change
# 4. Checks claim appears in claim_processing_log
# 5. Verifies claim is assigned to correct bucket
# 6. Validates API metrics updated
#
# Prerequisites:
# - Database initialized with schema.sql
# - Configuration loaded from admin-schema.sql (includes PAYER001/PAYEE001)
# - Backend running: cd edi835-processor && mvn spring-boot:run
#
# Expected Bucketing (from admin-schema.sql):
# - PAYER001 + PAYEE001 -> Uses "PAYER_PAYEE Default" rule (priority 10)
# - Threshold: 5 claims (from "Daily Claim Count" threshold)
# - Commit mode: Check edi_commit_criteria for linked rule behavior
#
# Note: This script uses PAYER001/PAYEE001 which are maintained in
# admin-schema.sql specifically for test script compatibility.

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

DB="./edi835-processor/data/edi835-local.db"
API_URL="http://localhost:8080"

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  EDI 835 Change Feed Test Script${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""

# Check if database exists
if [ ! -f "$DB" ]; then
    echo -e "${RED}Error: Database not found at $DB${NC}"
    echo "Please initialize the database first:"
    echo "  cd edi835-processor"
    echo "  sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/schema.sql"
    echo "  sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/admin-schema.sql"
    exit 1
fi

# Validate required test entities exist (from admin-schema.sql)
echo -e "${BLUE}Validating Configuration...${NC}"
PAYER_EXISTS=$(sqlite3 "$DB" "SELECT COUNT(*) FROM payers WHERE payer_id = 'PAYER001';")
PAYEE_EXISTS=$(sqlite3 "$DB" "SELECT COUNT(*) FROM payees WHERE payee_id = 'PAYEE001';")

if [ "$PAYER_EXISTS" -eq "0" ] || [ "$PAYEE_EXISTS" -eq "0" ]; then
    echo -e "${RED}Error: Required test entities (PAYER001/PAYEE001) not found${NC}"
    echo "Please load configuration data:"
    echo "  cd edi835-processor"
    echo "  sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/admin-schema.sql"
    echo ""
    echo "Configuration check:"
    echo "  PAYER001 exists: $([ "$PAYER_EXISTS" -eq "1" ] && echo "✓" || echo "✗")"
    echo "  PAYEE001 exists: $([ "$PAYEE_EXISTS" -eq "1" ] && echo "✓" || echo "✗")"
    exit 1
fi

echo -e "${GREEN}✓ Configuration validated (PAYER001/PAYEE001 found)${NC}"
echo ""

# Check if backend is running
if ! curl -s -f "$API_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${YELLOW}Warning: Backend not responding at $API_URL${NC}"
    echo "Please start the backend first:"
    echo "  cd edi835-processor"
    echo "  mvn spring-boot:run"
    echo ""
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Generate unique test ID
TEST_ID="test-$(date +%Y%m%d-%H%M%S)"
CLAIM_NUMBER="CLM-$TEST_ID"

echo -e "${BLUE}Test Configuration:${NC}"
echo "  Test ID: $TEST_ID"
echo "  Claim Number: $CLAIM_NUMBER"
echo "  Database: $DB"
echo ""

# Get baseline metrics
echo -e "${BLUE}Step 1: Get Baseline Metrics${NC}"
if curl -s -f "$API_URL/api/v1/dashboard/summary" > /dev/null 2>&1; then
    BASELINE=$(curl -s "$API_URL/api/v1/dashboard/summary" | grep -o '"totalClaims":[0-9]*' | cut -d':' -f2)
    echo -e "${GREEN}✓ Current total claims: $BASELINE${NC}"
else
    echo -e "${YELLOW}! Could not fetch baseline metrics (backend might not be running)${NC}"
    BASELINE="unknown"
fi
echo ""

# Insert test claim
echo -e "${BLUE}Step 2: Insert Test Claim${NC}"
sqlite3 "$DB" <<EOF
INSERT INTO claims (
    id,
    claim_number,
    patient_name,
    payer_id,
    payee_id,
    service_date,
    total_charge,
    total_paid,
    status
) VALUES (
    '$TEST_ID',
    '$CLAIM_NUMBER',
    'Test Patient $(date +%H:%M:%S)',
    'PAYER001',
    'PAYEE001',
    date('now'),
    2500.00,
    2250.00,
    'PROCESSED'
);
EOF

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Test claim inserted successfully${NC}"
else
    echo -e "${RED}✗ Failed to insert test claim${NC}"
    exit 1
fi
echo ""

# Verify change was tracked
echo -e "${BLUE}Step 3: Verify Change Tracked${NC}"
CHANGE_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM data_changes WHERE row_id = '$TEST_ID';")
if [ "$CHANGE_COUNT" -gt "0" ]; then
    echo -e "${GREEN}✓ Change tracked in data_changes table${NC}"
    echo "  Changes recorded: $CHANGE_COUNT"
else
    echo -e "${RED}✗ Change NOT tracked (triggers may not be working)${NC}"
    exit 1
fi
echo ""

# Wait for processing
echo -e "${BLUE}Step 4: Wait for Change Feed Processing${NC}"
echo -n "  Waiting for processor (5 seconds)"
for i in {1..5}; do
    sleep 1
    echo -n "."
done
echo ""
sleep 1  # Extra second for safety
echo -e "${GREEN}✓ Wait complete${NC}"
echo ""

# Check if processed
echo -e "${BLUE}Step 5: Verify Processing${NC}"

# Check data_changes processed flag
PROCESSED=$(sqlite3 "$DB" "SELECT processed FROM data_changes WHERE row_id = '$TEST_ID' ORDER BY changed_at DESC LIMIT 1;")
if [ "$PROCESSED" = "1" ]; then
    echo -e "${GREEN}✓ Change marked as processed${NC}"
else
    echo -e "${YELLOW}! Change not yet processed (processed=$PROCESSED)${NC}"
    echo "  The change feed processor may be slow or not running"
fi

# Check claim_processing_log
LOG_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM claim_processing_log WHERE claim_id = '$TEST_ID';")
if [ "$LOG_COUNT" -gt "0" ]; then
    echo -e "${GREEN}✓ Claim logged in claim_processing_log${NC}"

    # Get log details
    sqlite3 "$DB" <<EOF
.mode column
.headers on
SELECT claim_id, status, claim_amount, paid_amount FROM claim_processing_log WHERE claim_id = '$TEST_ID';
EOF
else
    echo -e "${YELLOW}! Claim NOT in claim_processing_log yet${NC}"
fi

# Check bucket assignment
BUCKET_ID=$(sqlite3 "$DB" "SELECT bucket_id FROM claim_processing_log WHERE claim_id = '$TEST_ID' LIMIT 1;")
if [ -n "$BUCKET_ID" ] && [ "$BUCKET_ID" != "" ]; then
    echo -e "${GREEN}✓ Claim assigned to bucket: $BUCKET_ID${NC}"

    # Show bucket details
    echo ""
    echo "  Bucket Details:"
    sqlite3 "$DB" <<EOF
.mode column
.headers on
SELECT payer_id, payee_id, claim_count, printf('%.2f', total_amount) as total_amount, status
FROM edi_file_buckets WHERE bucket_id = '$BUCKET_ID';
EOF
else
    echo -e "${YELLOW}! Claim not assigned to bucket yet${NC}"
fi
echo ""

# Check API metrics
echo -e "${BLUE}Step 6: Verify API Metrics${NC}"
if curl -s -f "$API_URL/api/v1/dashboard/summary" > /dev/null 2>&1; then
    CURRENT=$(curl -s "$API_URL/api/v1/dashboard/summary" | grep -o '"totalClaims":[0-9]*' | cut -d':' -f2)
    echo -e "${GREEN}✓ Current total claims: $CURRENT${NC}"

    if [ "$BASELINE" != "unknown" ]; then
        DIFF=$((CURRENT - BASELINE))
        if [ $DIFF -ge 1 ]; then
            echo -e "${GREEN}✓ Total claims increased by $DIFF${NC}"
        else
            echo -e "${YELLOW}! Total claims did not increase (may need more time)${NC}"
        fi
    fi
else
    echo -e "${YELLOW}! Could not fetch current metrics${NC}"
fi
echo ""

# Summary
echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  Test Summary${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""
echo "Test Claim Details:"
echo "  ID: $TEST_ID"
echo "  Claim Number: $CLAIM_NUMBER"
echo "  Status: PROCESSED"
echo "  Charge: \$2,500.00"
echo "  Paid: \$2,250.00"
echo ""

if [ "$PROCESSED" = "1" ] && [ "$LOG_COUNT" -gt "0" ]; then
    echo -e "${GREEN}✓ Test PASSED - Claim processed successfully${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. View dashboard: http://localhost:3000/dashboard"
    echo "  2. Check logs: tail -f edi835-processor/logs/edi835-processor.log"
    echo "  3. Inspect change feed: ./inspect-changefeed.sh"
    echo "  4. Query database: sqlite3 $DB"
    echo ""
    echo "Configuration:"
    echo "  Bucketing rule: PAYER_PAYEE (PAYER001 + PAYEE001)"
    echo "  Threshold: 5 claims (from admin-schema.sql)"
    echo "  View config: http://localhost:3000/config/rules"
    echo ""
    exit 0
else
    echo -e "${YELLOW}⚠ Test INCOMPLETE - Claim may still be processing${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check backend logs: tail -f edi835-processor/logs/edi835-processor.log"
    echo "  2. Verify change feed is enabled in application-sqlite.yml"
    echo "  3. Check if SQLiteChangeFeedProcessor is running"
    echo "  4. Inspect change feed status: ./inspect-changefeed.sh"
    echo "  5. Wait 10 more seconds and check again:"
    echo "     sqlite3 $DB \"SELECT processed FROM data_changes WHERE row_id = '$TEST_ID';\""
    echo "  6. Verify configuration loaded:"
    echo "     sqlite3 $DB \"SELECT COUNT(*) FROM payers;\""
    echo "     sqlite3 $DB \"SELECT COUNT(*) FROM edi_bucketing_rules;\""
    echo ""
    echo "Documentation: LOCAL_TESTING_GUIDE.md"
    echo ""
    exit 1
fi
