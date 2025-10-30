# Local Testing Guide - EDI 835 System

This guide provides step-by-step instructions for testing the EDI 835 system locally using SQLite change feed functionality.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [System Architecture Overview](#system-architecture-overview)
3. [Quick Start](#quick-start)
4. [Detailed Setup](#detailed-setup)
5. [Triggering Change Feed Events](#triggering-change-feed-events)
6. [End-to-End Testing Scenarios](#end-to-end-testing-scenarios)
7. [Monitoring & Verification](#monitoring--verification)
8. [Troubleshooting](#troubleshooting)
9. [Advanced Testing](#advanced-testing)
10. [Cleaning Up](#cleaning-up)

---

## Prerequisites

### Required Software

- **Java 17+**: Check version with `java -version`
- **Maven 3.8+**: Check version with `mvn -version`
- **SQLite 3.x**: Check version with `sqlite3 --version`
- **Node.js 18+** and **npm**: For frontend (check with `node -v` and `npm -v`)
- **curl**: For API testing (check with `curl --version`)

### Optional Tools

- **jq**: For pretty-printing JSON responses (`sudo apt install jq` or `brew install jq`)
- **SQLite Browser**: GUI tool for database inspection
- **Postman**: For API testing

---

## System Architecture Overview

### Change Feed Flow

```
┌───────────────────────────────────────────────────────────────────┐
│                     LOCAL TESTING ARCHITECTURE                     │
└───────────────────────────────────────────────────────────────────┘

 ┌─────────────────┐
 │  SQLite DB      │
 │  - claims       │      ┌──────────────────────────────────┐
 │  - data_changes │◄─────┤ INSERT/UPDATE Claims (Manual)    │
 │  - triggers     │      └──────────────────────────────────┘
 └────────┬────────┘
          │
          │ Triggers automatically create entries
          ▼
 ┌────────────────────┐
 │  data_changes      │
 │  table             │
 │  (unprocessed=0)   │
 └────────┬───────────┘
          │
          │ Polls every 5 seconds
          ▼
 ┌──────────────────────────────┐
 │ SQLiteChangeFeedProcessor    │
 │ - Fetches unprocessed changes│
 │ - Batches JSON documents     │
 │ - Maintains checkpoint       │
 └──────────┬───────────────────┘
            │
            │ handleChanges(List<JsonNode>)
            ▼
 ┌──────────────────────────┐
 │  ChangeFeedHandler       │
 │  - Filter by status      │
 │  - Parse to Claim object │
 └──────────┬───────────────┘
            │
            │ processClaim(Claim)
            ▼
 ┌────────────────────────────────┐
 │  RemittanceProcessorService    │
 │  - Determine bucketing rule    │
 │  - Call aggregation service    │
 └──────────┬───────────────────────┘
            │
            │ aggregateClaim(claim, rule)
            ▼
 ┌──────────────────────────────┐
 │  ClaimAggregationService     │
 │  - Find/Create bucket        │
 │  - Add claim to bucket       │
 │  - Log to processing log     │
 │  - Evaluate thresholds       │
 └──────────┬───────────────────┘
            │
            ▼
 ┌──────────────────────────────┐
 │  Database Tables Updated:    │
 │  - edi_file_buckets          │
 │  - claim_processing_log      │
 │  - data_changes (processed=1)│
 └──────────────────────────────┘
            │
            │ API: GET /api/v1/dashboard/summary
            ▼
 ┌──────────────────────────────┐
 │  React Dashboard             │
 │  - Total Claims              │
 │  - Active Buckets            │
 │  - Processing Metrics        │
 └──────────────────────────────┘
```

### Key Components

1. **SQLite Database**: Local database with triggers that automatically track changes
2. **SQLiteChangeFeedProcessor**: Scheduled service that polls for changes every 5 seconds
3. **ChangeFeedHandler**: Filters and parses claim documents
4. **RemittanceProcessorService**: Routes claims to aggregation
5. **ClaimAggregationService**: Groups claims into buckets based on rules
6. **Dashboard**: Displays real-time metrics

### Important: Commit Criteria Field Mapping

The `edi_commit_criteria` table has **only 2 integer fields** for thresholds:
- `auto_commit_threshold`: Stores **AMOUNT** threshold (in dollars)
- `manual_approval_threshold`: Stores **CLAIM COUNT** threshold

Frontend field mapping:
- `approvalAmountThreshold` → maps to `auto_commit_threshold`
- `approvalClaimCountThreshold` → maps to `manual_approval_threshold`

Example configurations in admin-schema.sql:
- **AUTO mode**: `auto_commit_threshold=500` ($500 amount), `manual_approval_threshold=10` (10 claims)
- **HYBRID mode**: `auto_commit_threshold=5000` ($5000 amount), `manual_approval_threshold=10` (10 claims)

---

## Quick Start

### 1. Initialize Database

```bash
cd edi835-processor

# Create data directory if it doesn't exist
mkdir -p data

# Initialize all schemas
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/schema.sql
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/admin-schema.sql

# Load sample data (optional)
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/sample-data.sql
```

### 2. Start Backend

```bash
# Option 1: Using Maven
mvn spring-boot:run

# Option 2: Using Makefile
make run

# Backend will start on http://localhost:8080
```

### 3. Start Frontend

```bash
cd ../edi835-admin-portal

# Install dependencies (first time only)
npm install

# Start development server
npm run dev

# Frontend will start on http://localhost:3000
```

### 4. Trigger Change Feed

Open a new terminal and insert a test claim:

```bash
cd edi835-processor

sqlite3 ./data/edi835-local.db <<EOF
-- Insert a new claim
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
VALUES (
  'claim-test-001',
  'CLM-TEST-001',
  'Test Patient',
  'PAYER001',
  'PAYEE001',
  date('now'),
  1000.00,
  0.00,
  'PENDING'
);

-- Process the claim (this triggers the change feed)
UPDATE claims
SET status = 'PROCESSED', total_paid = 900.00, version = version + 1
WHERE id = 'claim-test-001';

-- Verify the change was tracked
SELECT * FROM data_changes ORDER BY changed_at DESC LIMIT 1;
EOF
```

### 5. Verify Results

**Check Dashboard**: Navigate to `http://localhost:3000/dashboard` and verify:
- Total Claims increased by 1
- Active Buckets shows new bucket
- Recent activity shows the new claim

**Check API**:
```bash
curl http://localhost:8080/api/v1/dashboard/summary | jq
```

---

## Detailed Setup

### Step 1: Database Initialization

#### 1.1 Create Database Directory

```bash
cd edi835-processor
mkdir -p data
```

#### 1.2 Initialize Change Feed Schema

```bash
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/schema.sql
```

**What this creates**:
- `claims` table: Stores claim data (simulates Cosmos DB)
- `data_changes` table: Tracks all INSERT/UPDATE/DELETE operations
- `feed_versions` table: Tracks processing runs
- `changefeed_checkpoint` table: Maintains processor state
- `claim_status_history` table: Audit trail for status changes
- **Triggers**: Automatically capture changes to `claims` table

#### 1.3 Initialize Admin Schema

```bash
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/admin-schema.sql
```

**What this creates**:
- `edi_file_buckets`: Active claim buckets
- `claim_processing_log`: Audit trail for processed claims
- `file_generation_history`: Generated EDI file tracking
- `bucket_approval_log`: Approval workflow audit
- Configuration tables: payers, payees, rules, thresholds, etc.

#### 1.4 Verify Schema

```bash
sqlite3 ./data/edi835-local.db <<EOF
.tables
.schema claims
.schema data_changes
EOF
```

Expected output:
```
claim_processing_log        claims                      data_changes
edi_file_buckets           feed_versions               changefeed_checkpoint
...
```

### Step 2: Configure Application

#### 2.1 Check Configuration

**File**: `src/main/resources/application-sqlite.yml`

Key settings:
```yaml
spring:
  profiles:
    active: sqlite

  datasource:
    url: jdbc:sqlite:./data/edi835-local.db
    driver-class-name: org.sqlite.JDBC

changefeed:
  type: sqlite
  sqlite:
    enabled: true
    poll-interval-ms: 5000  # Poll every 5 seconds
    batch-size: 100
    auto-version: true

edi:
  output-directory: ./data/edi/output
  temp-directory: ./data/edi/temp
  default-schema: ""  # Schema validation disabled

logging:
  level:
    com.healthcare.edi835: DEBUG
```

#### 2.2 Create Output Directories

```bash
mkdir -p data/edi/output
mkdir -p data/edi/temp
```

### Step 3: Start Backend

#### Option A: Maven

```bash
# Clean build
mvn clean compile

# Run with SQLite profile (default)
mvn spring-boot:run
```

#### Option B: Makefile

```bash
make clean build
make run
```

#### Option C: JAR

```bash
# Package
mvn clean package -DskipTests

# Run JAR
java -jar target/edi835-processor-1.0.0-SNAPSHOT.jar
```

#### 3.1 Verify Backend Started

**Check logs for**:
```
SQLite Change Feed Configuration initialized
SQLite Change Feed Processor initialized: consumerId=edi835-processor-default
Started Edi835ProcessorApplication
```

**Test health endpoint**:
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### Step 4: Start Frontend

```bash
cd ../edi835-admin-portal

# First time setup
npm install

# Create .env file if it doesn't exist
cat > .env <<EOF
VITE_API_BASE_URL=http://localhost:8080/api/v1
EOF

# Start dev server
npm run dev
```

**Frontend will be available at**: `http://localhost:3000`

---

## Triggering Change Feed Events

### Understanding the Trigger Mechanism

The SQLite change feed uses **database triggers** to automatically track changes:

1. **INSERT on `claims`**: Trigger creates entry in `data_changes` with operation='INSERT'
2. **UPDATE on `claims`**: Trigger creates entry in `data_changes` with operation='UPDATE'
3. **DELETE on `claims`**: Trigger creates entry in `data_changes` with operation='DELETE'

The `SQLiteChangeFeedProcessor` polls `data_changes` every 5 seconds for unprocessed entries.

### Method 1: Insert New Claim (Recommended)

```bash
sqlite3 ./data/edi835-local.db <<EOF
-- Insert a new claim with PROCESSED status
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
  'claim-test-$(date +%s)',  -- Unique ID using timestamp
  'CLM-TEST-$(date +%Y%m%d%H%M%S)',
  'John Test Patient',
  'PAYER001',
  'PAYEE001',
  date('now'),
  1500.00,
  1350.00,
  'PROCESSED'
);

-- Verify the change was recorded
SELECT
  change_id,
  operation,
  row_id,
  processed,
  changed_at
FROM data_changes
ORDER BY changed_at DESC
LIMIT 1;
EOF
```

### Method 2: Update Existing Claim

```bash
sqlite3 ./data/edi835-local.db <<EOF
-- Update an existing claim to PROCESSED status
UPDATE claims
SET
  status = 'PROCESSED',
  total_paid = total_charge * 0.90,  -- 90% payment
  updated_at = datetime('now'),
  version = version + 1
WHERE id = 'claim-001';

-- Check the change
SELECT * FROM data_changes WHERE row_id = 'claim-001' ORDER BY changed_at DESC LIMIT 1;
EOF
```

### Method 3: Batch Insert Multiple Claims

```bash
sqlite3 ./data/edi835-local.db <<EOF
-- Insert multiple claims for the same payer/payee (will create one bucket)
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
VALUES
  ('claim-batch-001', 'CLM-BATCH-001', 'Patient A', 'PAYER001', 'PAYEE001', date('now'), 1000.00, 900.00, 'PROCESSED'),
  ('claim-batch-002', 'CLM-BATCH-002', 'Patient B', 'PAYER001', 'PAYEE001', date('now'), 1500.00, 1350.00, 'PROCESSED'),
  ('claim-batch-003', 'CLM-BATCH-003', 'Patient C', 'PAYER001', 'PAYEE001', date('now'), 2000.00, 1800.00, 'PROCESSED'),
  ('claim-batch-004', 'CLM-BATCH-004', 'Patient D', 'PAYER001', 'PAYEE001', date('now'), 1200.00, 1080.00, 'PROCESSED');

-- Verify all changes tracked
SELECT COUNT(*) as new_changes FROM data_changes WHERE processed = 0;
EOF
```

### Method 4: Using SQL Script File

Create a file `test-claims.sql`:

```sql
-- Test Claims Insertion Script
-- Usage: sqlite3 ./data/edi835-local.db < test-claims.sql

BEGIN TRANSACTION;

-- Insert claims for different payer/payee combinations
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
VALUES
  -- Bucket 1: PAYER001 -> PAYEE001
  ('test-p1-pe1-001', 'CLM-001', 'Patient 1', 'PAYER001', 'PAYEE001', date('now'), 1000.00, 900.00, 'PROCESSED'),
  ('test-p1-pe1-002', 'CLM-002', 'Patient 2', 'PAYER001', 'PAYEE001', date('now'), 1500.00, 1350.00, 'PROCESSED'),

  -- Bucket 2: PAYER002 -> PAYEE001
  ('test-p2-pe1-001', 'CLM-003', 'Patient 3', 'PAYER002', 'PAYEE001', date('now'), 2000.00, 1800.00, 'PROCESSED'),
  ('test-p2-pe1-002', 'CLM-004', 'Patient 4', 'PAYER002', 'PAYEE001', date('now'), 1200.00, 1080.00, 'PROCESSED'),

  -- Bucket 3: PAYER001 -> PAYEE002
  ('test-p1-pe2-001', 'CLM-005', 'Patient 5', 'PAYER001', 'PAYEE002', date('now'), 1800.00, 1620.00, 'PROCESSED'),

  -- Rejected claim
  ('test-rejected-001', 'CLM-006', 'Patient 6', 'PAYER003', 'PAYEE003', date('now'), 5000.00, 0.00, 'REJECTED');

COMMIT;

-- Display summary
SELECT
  'Total claims inserted' as description,
  COUNT(*) as count
FROM claims
WHERE id LIKE 'test-%'
UNION ALL
SELECT
  'Tracked changes',
  COUNT(*)
FROM data_changes
WHERE row_id LIKE 'test-%' AND processed = 0;
```

Execute:
```bash
sqlite3 ./data/edi835-local.db < test-claims.sql
```

### Method 5: Interactive SQLite Session

```bash
# Start interactive session
sqlite3 ./data/edi835-local.db

-- Enable headers and column mode for better output
.headers on
.mode column

-- Insert claim
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
VALUES ('claim-interactive-001', 'CLM-INT-001', 'Interactive Patient', 'PAYER001', 'PAYEE001', date('now'), 1000.00, 900.00, 'PROCESSED');

-- Check unprocessed changes
SELECT * FROM data_changes WHERE processed = 0;

-- Wait 5 seconds for processor to pick up changes
.timer on

-- Check if processed
SELECT * FROM data_changes WHERE row_id = 'claim-interactive-001';

-- Exit
.quit
```

---

## End-to-End Testing Scenarios

### Scenario 1: Single Claim Processing

**Objective**: Verify complete flow from claim insertion to dashboard display

#### Step 1: Get Baseline Metrics

```bash
curl -s http://localhost:8080/api/v1/dashboard/summary | jq '.totalClaims'
# Note the current count (e.g., 10)
```

#### Step 2: Insert Test Claim

```bash
sqlite3 ./data/edi835-local.db <<EOF
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
VALUES ('e2e-test-001', 'E2E-001', 'End-to-End Test', 'PAYER001', 'PAYEE001', date('now'), 2500.00, 2250.00, 'PROCESSED');
EOF
```

#### Step 3: Wait for Processing

```bash
# Wait for change feed poll (5 seconds)
sleep 6
```

#### Step 4: Verify in Database

```bash
sqlite3 ./data/edi835-local.db <<EOF
-- Check data_changes was processed
SELECT processed, processed_at FROM data_changes WHERE row_id = 'e2e-test-001';

-- Check claim_processing_log
SELECT claim_id, status, claim_amount, paid_amount FROM claim_processing_log WHERE claim_id = 'e2e-test-001';

-- Check bucket created
SELECT bucket_id, payer_id, payee_id, claim_count, total_amount FROM edi_file_buckets WHERE payer_id = 'PAYER001' AND payee_id = 'PAYEE001';
EOF
```

Expected output:
```
processed = 1
processed_at = 2025-10-15 ...

claim_id = e2e-test-001
status = PROCESSED
claim_amount = 2500.00
paid_amount = 2250.00

bucket_id = <UUID>
payer_id = PAYER001
payee_id = PAYEE001
claim_count = 1 (or more if bucket existed)
total_amount = 2250.00 (or more)
```

#### Step 5: Verify API

```bash
# Check summary increased
curl -s http://localhost:8080/api/v1/dashboard/summary | jq '{totalClaims, processedClaims}'

# Check active buckets
curl -s http://localhost:8080/api/v1/dashboard/buckets/metrics | jq
```

#### Step 6: Verify Dashboard UI

1. Open `http://localhost:3000/dashboard`
2. Verify "Total Claims" increased by 1
3. Verify "Processed Claims" increased by 1
4. Check "Recent Buckets" section for new or updated bucket

### Scenario 2: Multi-Payer Bucketing

**Objective**: Verify claims are grouped by payer/payee combination

#### Insert Claims for Multiple Payers

```bash
sqlite3 ./data/edi835-local.db <<EOF
-- Three claims for PAYER001 -> PAYEE001 (should go to bucket 1)
INSERT INTO claims VALUES
  ('multi-p1-1', 'MULTI-P1-1', 'Patient 1', 'PAYER001', 'PAYEE001', date('now'), 1000, 900, 'PROCESSED', NULL, datetime('now'), datetime('now'), 1),
  ('multi-p1-2', 'MULTI-P1-2', 'Patient 2', 'PAYER001', 'PAYEE001', date('now'), 1500, 1350, 'PROCESSED', NULL, datetime('now'), datetime('now'), 1),
  ('multi-p1-3', 'MULTI-P1-3', 'Patient 3', 'PAYER001', 'PAYEE001', date('now'), 2000, 1800, 'PROCESSED', NULL, datetime('now'), datetime('now'), 1);

-- Two claims for PAYER002 -> PAYEE001 (should go to bucket 2)
INSERT INTO claims VALUES
  ('multi-p2-1', 'MULTI-P2-1', 'Patient 4', 'PAYER002', 'PAYEE001', date('now'), 1200, 1080, 'PROCESSED', NULL, datetime('now'), datetime('now'), 1),
  ('multi-p2-2', 'MULTI-P2-2', 'Patient 5', 'PAYER002', 'PAYEE001', date('now'), 1800, 1620, 'PROCESSED', NULL, datetime('now'), datetime('now'), 1);
EOF

# Wait for processing
sleep 6
```

#### Verify Bucketing

```bash
sqlite3 ./data/edi835-local.db <<EOF
.headers on
.mode column

-- Should show 2 buckets with correct counts
SELECT
  bucket_id,
  payer_id,
  payee_id,
  claim_count,
  total_amount,
  status
FROM edi_file_buckets
WHERE payer_id IN ('PAYER001', 'PAYER002')
ORDER BY payer_id;
EOF
```

Expected output:
```
bucket_id          payer_id  payee_id  claim_count  total_amount  status
----------------   --------  --------  -----------  ------------  ------------
<uuid-1>           PAYER001  PAYEE001  3            4050.00       ACCUMULATING
<uuid-2>           PAYER002  PAYEE001  2            2700.00       ACCUMULATING
```

### Scenario 3: Claim Rejection

**Objective**: Verify rejected claims are logged but not added to buckets

#### Insert Rejected Claim

```bash
sqlite3 ./data/edi835-local.db <<EOF
-- Insert with invalid data (missing payer_id)
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
VALUES ('reject-test-001', 'REJECT-001', 'Rejection Test', NULL, 'PAYEE001', date('now'), 1000.00, 0.00, 'PROCESSED');

-- Wait and verify
EOF

sleep 6

sqlite3 ./data/edi835-local.db <<EOF
-- Check claim_processing_log
SELECT claim_id, status, rejection_reason FROM claim_processing_log WHERE claim_id = 'reject-test-001';

-- Verify NOT in any bucket
SELECT COUNT(*) as claims_in_bucket FROM claim_processing_log WHERE claim_id = 'reject-test-001' AND bucket_id IS NOT NULL;
EOF
```

Expected output:
```
claim_id = reject-test-001
status = REJECTED
rejection_reason = Invalid or incomplete claim data

claims_in_bucket = 0
```

### Scenario 4: Status Transition

**Objective**: Verify claim status changes are tracked

#### Insert and Update Claim

```bash
sqlite3 ./data/edi835-local.db <<EOF
-- Insert as PENDING
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
VALUES ('status-test-001', 'STATUS-001', 'Status Test', 'PAYER001', 'PAYEE001', date('now'), 1000.00, 0.00, 'PENDING');
EOF

sleep 1

```bash
# Update to PROCESSING
sqlite3 ./data/edi835-local.db "UPDATE claims SET status = 'PROCESSING', version = version + 1 WHERE id = 'status-test-001';"
sleep 1

# Update to PROCESSED
sqlite3 ./data/edi835-local.db "UPDATE claims SET status = 'PROCESSED', total_paid = 900.00, version = version + 1 WHERE id = 'status-test-001';"
sleep 6

# Check status history
sqlite3 ./data/edi835-local.db <<EOF
.headers on
.mode column
SELECT old_status, new_status, changed_at FROM claim_status_history WHERE claim_id = 'status-test-001' ORDER BY changed_at;
EOF
```

Expected output:
```
old_status  new_status   changed_at
----------  -----------  -------------------
PENDING     PROCESSING   2025-10-15 ...
PROCESSING  PROCESSED    2025-10-15 ...
```

---

## Monitoring & Verification

### Backend Logs

#### View Logs in Real-Time

```bash
# Follow application log
tail -f edi835-processor/logs/edi835-processor.log

# Or using Makefile
cd edi835-processor
make logs
```

#### Key Log Messages to Watch

**Change Feed Polling**:
```
DEBUG c.h.e.c.s.SQLiteChangeFeedProcessor - Polling for changes...
INFO  c.h.e.c.s.SQLiteChangeFeedProcessor - Found 1 unprocessed changes to process
INFO  c.h.e.c.s.SQLiteChangeFeedProcessor - Processed 1 changes (1 successful, 0 errors) for version 1
```

**Claim Processing**:
```
INFO  c.h.e.c.ChangeFeedHandler - Processing 1 documents from change feed
DEBUG c.h.e.s.ClaimAggregationService - Aggregating claim claim-test-001 using rule Default Bucketing Rule
INFO  c.h.e.s.ClaimAggregationService - Successfully aggregated claim claim-test-001 into bucket <uuid>
```

**Bucket Creation**:
```
INFO  c.h.e.s.ClaimAggregationService - Creating new PAYER_PAYEE bucket for payer=PAYER001, payee=PAYEE001
```

### Database Queries

#### Check Unprocessed Changes

```bash
sqlite3 ./data/edi835-local.db <<EOF
.headers on
.mode column

SELECT
  change_id,
  operation,
  row_id,
  changed_at,
  processed
FROM data_changes
WHERE processed = 0
ORDER BY changed_at DESC
LIMIT 10;
EOF
```

#### View Recent Processing

```bash
sqlite3 ./data/edi835-local.db <<EOF
SELECT
  claim_id,
  status,
  claim_amount,
  paid_amount,
  processed_at
FROM claim_processing_log
ORDER BY processed_at DESC
LIMIT 10;
EOF
```

#### Check Buckets

```bash
sqlite3 ./data/edi835-local.db <<EOF
.headers on
.mode column

SELECT
  bucket_id,
  payer_id,
  payee_id,
  claim_count,
  printf('%.2f', total_amount) as total_amount,
  status,
  created_at
FROM edi_file_buckets
ORDER BY created_at DESC;
EOF
```

#### View Change Feed Statistics

```bash
sqlite3 ./data/edi835-local.db <<EOF
SELECT * FROM v_feed_version_summary;
EOF
```

### API Endpoints for Monitoring

#### Dashboard Summary

```bash
curl -s http://localhost:8080/api/v1/dashboard/summary | jq
```

Example response:
```json
{
  "totalBuckets": 3,
  "activeBuckets": 3,
  "pendingApprovalBuckets": 0,
  "totalFiles": 0,
  "pendingDeliveryFiles": 0,
  "failedDeliveryFiles": 0,
  "totalClaims": 15,
  "processedClaims": 13,
  "rejectedClaims": 2
}
```

#### Bucket Metrics

```bash
curl -s http://localhost:8080/api/v1/dashboard/buckets/metrics | jq
```

#### Claim Metrics

```bash
curl -s http://localhost:8080/api/v1/dashboard/claims/metrics | jq
```

#### System Health

```bash
curl -s http://localhost:8080/api/v1/dashboard/health | jq
```

### Frontend Dashboard

Navigate to `http://localhost:3000/dashboard` and monitor:

1. **Metric Cards** (top row):
   - Total Buckets
   - Pending Approvals
   - Total Files
   - Total Claims ← **Primary metric**

2. **Secondary Metrics**:
   - Failed Deliveries
   - Rejected Claims
   - Processing Rate

3. **Recent Buckets**: Shows live bucket activity

4. **System Health Alert**: Green (HEALTHY), Yellow (WARNING), Red (CRITICAL)


### Local sftp server (for linux)
1. Ensure OpenSSH Server is Installed:
If not already installed, install the OpenSSH server package:
Code
```bash
sudo apt update
sudo apt install openssh-server
```
2. Start the SFTP Server (by starting SSH service):
The SFTP server runs as part of the SSH daemon. To start it, you start the SSH service:
Code
```bash
sudo systemctl start ssh
```
3. Restart the SFTP Server (by restarting SSH service):
If you have made changes to the SSH configuration file (/etc/ssh/sshd_config) related to SFTP, you need to restart the SSH service for those changes to take effect:
Code
```bash
sudo systemctl restart ssh
```
4. Check the Status of the SFTP Server:
To verify that the SSH service (and thus the SFTP server) is running, you can check its status:
Code
```bash
sudo systemctl status ssh
```


5. Configure SSH for SFTP:
Edit the SSH daemon configuration file to enable and configure SFTP.
Code
```bash
sudo nano /etc/ssh/sshd_config
```
    Ensure SFTP Subsystem: Locate or add the line: 

Code
```bash
    Subsystem sftp /usr/lib/openssh/sftp-server
```
    Restrict Users to SFTP (Optional but Recommended): To prevent SFTP users from having full shell access, add a Match block at the end of the file. This example creates a group named sftpusers and restricts users belonging to it. 

Code
```bash
    Match Group sftpusers
        ForceCommand internal-sftp
        ChrootDirectory %h
        PermitTunnel no
        AllowAgentForwarding no
        AllowTcpForwarding no
        X11Forwarding no
```
    ChrootDirectory %h: This jails the user to their home directory.
    Restart SSH Service: Apply the changes by restarting the SSH service. 

Code
```bash
    sudo systemctl restart ssh
```
6. Create SFTP Users and Group:

    Create an SFTP Group (if using the Match Group configuration): 

Code
```bash
    sudo groupadd sftpusers
```
    Create a new SFTP user and add them to the group: 

Code
```bash
    sudo adduser sftpuser1
    sudo usermod -aG sftpusers sftpuser1
```
Replace sftpuser1 with your desired username and Set a password for the user.
Code
```bash
    sudo passwd sftpuser1
```
7. Configure User Home Directory and Permissions:
If you are using ChrootDirectory %h, the user's home directory will serve as their SFTP root. Ensure correct permissions:

    Set ownership of the user's home directory: 

Code
```bash
    sudo chown root:root /home/sftpuser1
```
    Set permissions for the user's home directory: 

Code
```bash
    sudo chmod 755 /home/sftpuser1
```
    Create a directory within the user's home directory for uploads (optional): 

Code
```bash
    sudo mkdir /home/sftpuser1/uploads
    sudo chown sftpuser1:sftpusers /home/sftpuser1/uploads
```
8. Test SFTP Connection:
From a client machine, attempt to connect using an SFTP client (e.g., sftp command line, FileZilla, WinSCP):
Code
```bash
sftp sftpuser1@your_server_ip
```
Enter the password when prompted. You should be connected and restricted to the configured directory.

---

## Troubleshooting

### Problem: Claims Not Processing

#### Symptom
Claims inserted into database but not appearing in dashboard.

#### Diagnosis

1. **Check if change feed is enabled**:
   ```bash
   grep "changefeed.sqlite.enabled" src/main/resources/application-sqlite.yml
   # Should be: enabled: true
   ```

2. **Check if change was tracked**:
   ```bash
   sqlite3 ./data/edi835-local.db "SELECT COUNT(*) FROM data_changes WHERE processed = 0;"
   # Should be > 0 if changes are pending
   ```

3. **Check processor logs**:
   ```bash
   tail -100 logs/edi835-processor.log | grep SQLiteChangeFeedProcessor
   ```

4. **Check claim status**:
   ```bash
   sqlite3 ./data/edi835-local.db "SELECT id, status FROM claims WHERE id = 'your-claim-id';"
   # Status must be 'PROCESSED' or 'PAID' to be processed
   ```

#### Solution

**If no changes tracked**:
- Verify triggers exist: `sqlite3 ./data/edi835-local.db ".schema claims_insert_trigger"`
- Re-run schema: `sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/schema.sql`

**If changes tracked but not processed**:
- Check processor is running: `curl http://localhost:8080/actuator/health`
- Check logs for errors: `grep ERROR logs/edi835-processor.log`
- Manually trigger poll (if endpoint exists): `curl -X POST http://localhost:8080/api/v1/changefeed/trigger`

**If claim status is wrong**:
```bash
# Update to PROCESSED
sqlite3 ./data/edi835-local.db "UPDATE claims SET status = 'PROCESSED', version = version + 1 WHERE id = 'your-claim-id';"
```

### Problem: Backend Won't Start

#### Symptom
`mvn spring-boot:run` fails with errors.

#### Common Causes

1. **Database file locked**:
   ```
   Error: database is locked
   ```
   **Solution**:
   ```bash
   # Stop any running instances
   pkill -f edi835-processor
   # Delete lock file
   rm ./data/edi835-local.db-journal
   ```

2. **Port 8080 already in use**:
   ```
   Error: Port 8080 is already in use
   ```
   **Solution**:
   ```bash
   # Find process using port 8080
   lsof -i :8080
   # Kill the process
   kill -9 <PID>
   # Or change port in application.yml
   ```

3. **Database schema outdated**:
   ```
   Error: no such table: claims
   ```
   **Solution**:
   ```bash
   # Re-initialize database
   rm ./data/edi835-local.db
   sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/schema.sql
   sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/admin-schema.sql
   ```

### Problem: Dashboard Shows Zero Claims

#### Symptom
Dashboard loads but shows 0 for all metrics.

#### Diagnosis

1. **Check API directly**:
   ```bash
   curl http://localhost:8080/api/v1/dashboard/summary
   ```

2. **Check CORS errors** (browser console):
   ```
   Access to XMLHttpRequest at 'http://localhost:8080/api/v1/dashboard/summary'
   from origin 'http://localhost:3000' has been blocked by CORS policy
   ```

3. **Check database has data**:
   ```bash
   sqlite3 ./data/edi835-local.db "SELECT COUNT(*) FROM claim_processing_log;"
   ```

#### Solution

**If API returns data**:
- Frontend issue: Check browser console for errors
- Check `.env` file has correct API URL
- Clear browser cache and reload

**If API returns zero**:
- Database is empty: Load sample data
  ```bash
  sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/sample-data.sql
  ```

**If CORS error**:
- Verify `WebConfig.java` allows `http://localhost:3000`
- Restart backend after configuration changes

### Problem: Changes Not Being Picked Up

#### Symptom
New claims inserted but change feed processor doesn't process them.

#### Diagnosis

```bash
# Check checkpoint
sqlite3 ./data/edi835-local.db "SELECT * FROM changefeed_checkpoint;"

# Check unprocessed changes
sqlite3 ./data/edi835-local.db "SELECT COUNT(*) FROM data_changes WHERE processed = 0;"

# Check latest feed version
sqlite3 ./data/edi835-local.db "SELECT * FROM feed_versions ORDER BY version_id DESC LIMIT 1;"
```

#### Solution

**Reset checkpoint** (will reprocess all changes):
```bash
sqlite3 ./data/edi835-local.db <<EOF
UPDATE changefeed_checkpoint
SET last_feed_version = 0,
    last_sequence_number = 0,
    last_checkpoint_at = datetime('now')
WHERE consumer_id = 'edi835-processor-default';
EOF

# Restart backend to trigger reprocessing
```

---

## Advanced Testing

### Performance Testing

#### Test with Large Batch

```bash
# Generate 1000 claims
sqlite3 ./data/edi835-local.db <<EOF
BEGIN TRANSACTION;

-- Use a loop to insert 1000 claims (SQLite doesn't have FOR loops, so we use WITH RECURSIVE)
WITH RECURSIVE cnt(x) AS (
  SELECT 1
  UNION ALL
  SELECT x+1 FROM cnt
  LIMIT 1000
)
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
SELECT
  'perf-test-' || printf('%04d', x),
  'PERF-' || printf('%04d', x),
  'Patient ' || x,
  'PAYER' || printf('%03d', (x % 10) + 1),  -- 10 different payers
  'PAYEE' || printf('%03d', (x % 5) + 1),   -- 5 different payees
  date('now', '-' || (x % 30) || ' days'),
  1000.00 + (x * 10),
  900.00 + (x * 9),
  'PROCESSED'
FROM cnt;

COMMIT;

-- Show summary
SELECT
  COUNT(*) as total_claims,
  COUNT(DISTINCT payer_id) as unique_payers,
  COUNT(DISTINCT payee_id) as unique_payees,
  printf('%.2f', SUM(total_paid)) as total_amount
FROM claims
WHERE id LIKE 'perf-test-%';
EOF
```

**Monitor processing time**:
```bash
# Watch logs for processing messages
tail -f logs/edi835-processor.log | grep "Processed.*changes"
```

Expected: 1000 claims processed in batches of 100 over ~50 seconds.

#### Stress Test Change Feed

```bash
# Rapid insertions
for i in {1..100}; do
  sqlite3 ./data/edi835-local.db "INSERT INTO claims VALUES ('stress-$i', 'STR-$i', 'Patient $i', 'PAYER001', 'PAYEE001', date('now'), 1000, 900, 'PROCESSED', NULL, datetime('now'), datetime('now'), 1);"
  sleep 0.1
done

# Monitor system
curl -s http://localhost:8080/api/v1/dashboard/summary | jq '.totalClaims'
```

### Integration Testing

#### Test Entire Workflow

```bash
#!/bin/bash
# complete-workflow-test.sh

set -e

echo "=== EDI 835 Complete Workflow Test ==="

DB="./data/edi835-local.db"
TEST_ID="workflow-$(date +%s)"

echo "1. Insert claim..."
sqlite3 $DB <<EOF
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
VALUES ('$TEST_ID', 'WF-TEST', 'Workflow Test', 'PAYER001', 'PAYEE001', date('now'), 5000.00, 4500.00, 'PROCESSED');
EOF

echo "2. Wait for change feed processing (6 seconds)..."
sleep 6

echo "3. Verify in claim_processing_log..."
COUNT=$(sqlite3 $DB "SELECT COUNT(*) FROM claim_processing_log WHERE claim_id = '$TEST_ID';")
if [ "$COUNT" -eq "1" ]; then
  echo "✓ Claim logged successfully"
else
  echo "✗ Claim NOT logged (count: $COUNT)"
  exit 1
fi

echo "4. Verify in bucket..."
BUCKET_ID=$(sqlite3 $DB "SELECT bucket_id FROM claim_processing_log WHERE claim_id = '$TEST_ID';")
if [ -n "$BUCKET_ID" ]; then
  echo "✓ Claim added to bucket: $BUCKET_ID"
else
  echo "✗ Claim NOT in any bucket"
  exit 1
fi

echo "5. Check API response..."
TOTAL_CLAIMS=$(curl -s http://localhost:8080/api/v1/dashboard/summary | jq '.totalClaims')
echo "✓ Total claims in system: $TOTAL_CLAIMS"

echo ""
echo "=== All Tests Passed ==="
```

Save and run:
```bash
chmod +x complete-workflow-test.sh
./complete-workflow-test.sh
```

### Manual Checkpoint Management

#### View Current Checkpoint

```bash
sqlite3 ./data/edi835-local.db <<EOF
.headers on
.mode column

SELECT
  consumer_id,
  last_feed_version,
  last_sequence_number,
  last_checkpoint_at,
  total_processed
FROM changefeed_checkpoint;
EOF
```

#### Reset to Specific Position

```bash
# Reset to reprocess from sequence 100
sqlite3 ./data/edi835-local.db <<EOF
UPDATE changefeed_checkpoint
SET last_sequence_number = 100
WHERE consumer_id = 'edi835-processor-default';
EOF

# Restart backend to trigger reprocessing
```

#### Reprocess All Changes

```bash
sqlite3 ./data/edi835-local.db <<EOF
-- Mark all changes as unprocessed
UPDATE data_changes SET processed = 0, processed_at = NULL;

-- Reset checkpoint
UPDATE changefeed_checkpoint
SET last_feed_version = 0,
    last_sequence_number = 0,
    total_processed = 0;
EOF

# Restart backend
```

---

## Cleaning Up

### Remove Test Data

```bash
sqlite3 ./data/edi835-local.db <<EOF
-- Delete test claims
DELETE FROM claims WHERE id LIKE 'test-%' OR id LIKE 'claim-test-%' OR id LIKE 'e2e-%';

-- Delete test processing logs
DELETE FROM claim_processing_log WHERE claim_id LIKE 'test-%' OR claim_id LIKE 'claim-test-%';

-- Delete associated data_changes
DELETE FROM data_changes WHERE row_id LIKE 'test-%';

-- Reset checkpoint
UPDATE changefeed_checkpoint SET last_checkpoint_at = datetime('now');

-- Vacuum to reclaim space
VACUUM;
EOF
```

### Reset Database

```bash
# Backup current database (optional)
cp ./data/edi835-local.db ./data/edi835-local.db.backup

# Drop and recreate
rm ./data/edi835-local.db

# Reinitialize
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/schema.sql
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/admin-schema.sql

# Optionally load sample data
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/sample-data.sql
```

### Stop Services

```bash
# Stop backend (if running with mvn)
# Press Ctrl+C in terminal running mvn spring-boot:run

# Kill any lingering processes
pkill -f edi835-processor

# Stop frontend (if running)
# Press Ctrl+C in terminal running npm run dev
```

---

## Quick Reference Commands

### Database Operations

```bash
# Connect to database
sqlite3 ./data/edi835-local.db

# View schema
.schema <table_name>

# List tables
.tables

# Check unprocessed changes
SELECT COUNT(*) FROM data_changes WHERE processed = 0;

# Insert test claim
INSERT INTO claims (id, claim_number, patient_name, payer_id, payee_id, service_date, total_charge, total_paid, status)
VALUES ('test-quick', 'TST-QUICK', 'Quick Test', 'PAYER001', 'PAYEE001', date('now'), 1000, 900, 'PROCESSED');

# View recent buckets
SELECT bucket_id, payer_id, payee_id, claim_count, total_amount FROM edi_file_buckets ORDER BY created_at DESC LIMIT 5;
```

### API Testing

```bash
# Health check
curl http://localhost:8080/actuator/health

# Dashboard summary
curl -s http://localhost:8080/api/v1/dashboard/summary | jq

# Active buckets
curl -s http://localhost:8080/api/v1/dashboard/buckets/metrics | jq

# System health
curl -s http://localhost:8080/api/v1/dashboard/health | jq
```

### Build & Run

```bash
# Backend
cd edi835-processor
make clean build
make run

# Frontend
cd edi835-admin-portal
npm install
npm run dev
```

---

## Appendix

### A. Sample Data Details

The `sample-data.sql` file creates:
- **5 sample claims** with different statuses
- **Multiple status transitions** tracked in `claim_status_history`
- **Data changes** automatically tracked by triggers
- **Feed version** initialized for processing

### B. Configuration Reference

| Configuration | Default | Description |
|---------------|---------|-------------|
| `changefeed.sqlite.enabled` | `true` | Enable SQLite change feed |
| `changefeed.sqlite.poll-interval-ms` | `5000` | Poll every 5 seconds |
| `changefeed.sqlite.batch-size` | `100` | Process 100 changes per batch |
| `changefeed.sqlite.consumer-id` | `edi835-processor-default` | Unique consumer identifier |

### C. Useful SQL Queries

**Change Feed Statistics**:
```sql
SELECT
  SUM(CASE WHEN processed = 1 THEN 1 ELSE 0 END) as processed,
  SUM(CASE WHEN processed = 0 THEN 1 ELSE 0 END) as pending,
  COUNT(*) as total
FROM data_changes;
```

**Bucket Summary**:
```sql
SELECT
  status,
  COUNT(*) as bucket_count,
  SUM(claim_count) as total_claims,
  printf('%.2f', SUM(total_amount)) as total_amount
FROM edi_file_buckets
GROUP BY status;
```

**Processing Rate**:
```sql
SELECT
  date(processed_at) as date,
  COUNT(*) as claims_processed,
  SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) as rejected,
  printf('%.2f%%',
    100.0 * SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) / COUNT(*)
  ) as rejection_rate
FROM claim_processing_log
GROUP BY date(processed_at)
ORDER BY date DESC;
```

---

## Support

For issues or questions:
- Check `TROUBLESHOOTING.md` for common problems
- Review `DASHBOARD_TOTAL_CLAIMS_CALCULATION.md` for understanding data flow
- Check application logs in `logs/edi835-processor.log`
- Review SQLite database directly with `sqlite3 ./data/edi835-local.db`

---

**Last Updated**: 2025-10-15
**Version**: 1.0
