#!/bin/bash
# inspect-changefeed.sh
# Inspect change feed status and recent activity
#
# Prerequisites:
# - Database initialized with schema.sql and admin-schema.sql
# - Backend running (mvn spring-boot:run)
# - Test data loaded (sample-data.sql) if testing
#
# Expected Configuration (from admin-schema.sql):
# - 5 payers (PAYER001, BCBS001, UHC001, AETNA001, MEDICARE001)
# - 3 payees (PAYEE001, PAYEE002, PAYEE003)
# - 3 bucketing rules (priority 100 rule for BCBS-General Hospital)
# - 3 generation thresholds (CLAIM_COUNT=5, TIME=WEEKLY, AMOUNT=$5000)
# - 3 commit criteria (AUTO, MANUAL, HYBRID modes)
# - 2 file naming templates
#
# Note: Commit criteria field mapping:
# - auto_commit_threshold: Stores AMOUNT threshold ($)
# - manual_approval_threshold: Stores CLAIM COUNT threshold

DB="./edi835-processor/data/edi835-local.db"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

if [ ! -f "$DB" ]; then
    echo "Error: Database not found at $DB"
    exit 1
fi

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  Change Feed Status Inspector${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""

# Configuration Validation
echo -e "${BLUE}🔧 Configuration Validation:${NC}"
PAYER_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM payers;")
PAYEE_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM payees;")
RULE_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM edi_bucketing_rules;")
THRESHOLD_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM edi_generation_thresholds;")
CRITERIA_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM edi_commit_criteria;")

echo "  Payers configured: ${GREEN}$PAYER_COUNT${NC} (expected: 5 from admin-schema.sql)"
echo "  Payees configured: ${GREEN}$PAYEE_COUNT${NC} (expected: 3 from admin-schema.sql)"
echo "  Bucketing rules: ${GREEN}$RULE_COUNT${NC} (expected: 3 from admin-schema.sql)"
echo "  Generation thresholds: ${GREEN}$THRESHOLD_COUNT${NC} (expected: 3 from admin-schema.sql)"
echo "  Commit criteria: ${GREEN}$CRITERIA_COUNT${NC} (expected: 3 from admin-schema.sql)"

# Verify PAYER001/PAYEE001 exist (required for test-change-feed.sh)
TEST_PAYER=$(sqlite3 "$DB" "SELECT COUNT(*) FROM payers WHERE payer_id = 'PAYER001';")
TEST_PAYEE=$(sqlite3 "$DB" "SELECT COUNT(*) FROM payees WHERE payee_id = 'PAYEE001';")
if [ "$TEST_PAYER" -eq "1" ] && [ "$TEST_PAYEE" -eq "1" ]; then
    echo -e "  Test entities (PAYER001/PAYEE001): ${GREEN}Present${NC} ✓"
else
    echo -e "  Test entities (PAYER001/PAYEE001): ${YELLOW}Missing${NC} ⚠"
    echo "    Run: sqlite3 $DB < edi835-processor/src/main/resources/db/sqlite/admin-schema.sql"
fi
echo ""

# Checkpoint Status
echo -e "${BLUE}📍 Checkpoint Status:${NC}"
sqlite3 "$DB" <<EOF
.mode column
.headers on
.width 30 18 20 25 15
SELECT
  consumer_id,
  last_feed_version,
  last_sequence_number,
  datetime(last_checkpoint_at, 'localtime') as last_checkpoint,
  total_processed
FROM changefeed_checkpoint;
EOF
echo ""

# Feed Version Summary
echo -e "${BLUE}📊 Feed Versions:${NC}"
sqlite3 "$DB" <<EOF
.mode column
.headers on
.width 10 12 15 12 15 12
SELECT
  version_id,
  status,
  changes_count,
  processed_count,
  error_count,
  datetime(started_at, 'localtime') as started
FROM feed_versions
ORDER BY version_id DESC
LIMIT 5;
EOF
echo ""

# Pending Changes
PENDING=$(sqlite3 "$DB" "SELECT COUNT(*) FROM data_changes WHERE processed = 0;")
echo -e "${BLUE}⏳ Pending Changes: ${YELLOW}$PENDING${NC}"
if [ "$PENDING" -gt "0" ]; then
    echo ""
    sqlite3 "$DB" <<EOF
.mode column
.headers on
.width 10 12 10 25 25 10
SELECT
  change_id,
  operation,
  row_id,
  datetime(changed_at, 'localtime') as changed_at,
  sequence_number,
  processed
FROM data_changes
WHERE processed = 0
ORDER BY sequence_number
LIMIT 10;
EOF
fi
echo ""

# Recent Processed Changes
echo -e "${BLUE}✅ Recent Processed Changes:${NC}"
sqlite3 "$DB" <<EOF
.mode column
.headers on
.width 10 12 10 25 25 25
SELECT
  change_id,
  operation,
  row_id,
  datetime(changed_at, 'localtime') as changed_at,
  datetime(processed_at, 'localtime') as processed_at,
  sequence_number
FROM data_changes
WHERE processed = 1
ORDER BY processed_at DESC
LIMIT 10;
EOF
echo ""

# Processing Statistics
echo -e "${BLUE}📈 Processing Statistics:${NC}"
sqlite3 "$DB" <<EOF
.mode column
.headers on
SELECT
  COUNT(*) as total_changes,
  SUM(CASE WHEN processed = 1 THEN 1 ELSE 0 END) as processed,
  SUM(CASE WHEN processed = 0 THEN 1 ELSE 0 END) as pending,
  SUM(CASE WHEN error_message IS NOT NULL THEN 1 ELSE 0 END) as errors,
  printf('%.1f%%', 100.0 * SUM(CASE WHEN processed = 1 THEN 1 ELSE 0 END) / COUNT(*)) as success_rate
FROM data_changes;
EOF
echo ""

# Recent Claims
echo -e "${BLUE}🏥 Recent Claims:${NC}"
sqlite3 "$DB" <<EOF
.mode column
.headers on
.width 20 15 12 12 10 15
SELECT
  id,
  claim_number,
  payer_id,
  payee_id,
  status,
  datetime(updated_at, 'localtime') as updated
FROM claims
ORDER BY updated_at DESC
LIMIT 10;
EOF
echo ""

# Claim Processing Log
echo -e "${BLUE}📝 Recent Processing Log:${NC}"
PROCESSED_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM claim_processing_log;")
echo -e "Total logged: ${GREEN}$PROCESSED_COUNT${NC}"
echo ""
sqlite3 "$DB" <<EOF
.mode column
.headers on
.width 20 12 12 15 25
SELECT
  claim_id,
  status,
  printf('%.2f', claim_amount) as charge,
  printf('%.2f', paid_amount) as paid,
  datetime(processed_at, 'localtime') as processed
FROM claim_processing_log
ORDER BY processed_at DESC
LIMIT 10;
EOF
echo ""

# Active Buckets
echo -e "${BLUE}🗂️  Active Buckets:${NC}"
BUCKET_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM edi_file_buckets;")
echo -e "Total buckets: ${GREEN}$BUCKET_COUNT${NC}"
echo ""
sqlite3 "$DB" <<EOF
.mode column
.headers on
.width 38 10 10 12 15 15
SELECT
  bucket_id,
  payer_id,
  payee_id,
  claim_count,
  printf('%.2f', total_amount) as amount,
  status
FROM edi_file_buckets
ORDER BY created_at DESC
LIMIT 10;
EOF
echo ""

# Errors
ERROR_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM data_changes WHERE error_message IS NOT NULL;")
if [ "$ERROR_COUNT" -gt "0" ]; then
    echo -e "${YELLOW}⚠️  Errors Found: $ERROR_COUNT${NC}"
    echo ""
    sqlite3 "$DB" <<EOF
.mode column
.headers on
.width 10 25 40
SELECT
  change_id,
  row_id,
  error_message
FROM data_changes
WHERE error_message IS NOT NULL
ORDER BY changed_at DESC
LIMIT 5;
EOF
    echo ""
fi

# System Health
echo -e "${BLUE}🔍 Quick Health Check:${NC}"
echo -n "  Backend API: "
if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}RUNNING${NC}"
else
    echo -e "${YELLOW}NOT RESPONDING${NC}"
fi

echo -n "  Frontend: "
if curl -s -f http://localhost:3000 > /dev/null 2>&1; then
    echo -e "${GREEN}RUNNING${NC}"
else
    echo -e "${YELLOW}NOT RESPONDING${NC}"
fi
echo ""

echo -e "${BLUE}======================================${NC}"
echo ""
echo "Commands:"
echo "  View logs: tail -f edi835-processor/logs/edi835-processor.log"
echo "  Test claim: ./test-change-feed.sh"
echo "  Dashboard: http://localhost:3000/dashboard"
echo "  API: http://localhost:8080/api/v1/dashboard/summary"
echo ""
echo "Configuration:"
echo "  Load schema: sqlite3 $DB < edi835-processor/src/main/resources/db/sqlite/admin-schema.sql"
echo "  Load test data: sqlite3 $DB < edi835-processor/src/main/resources/db/sqlite/sample-data.sql"
echo "  Documentation: LOCAL_TESTING_GUIDE.md"
echo ""
