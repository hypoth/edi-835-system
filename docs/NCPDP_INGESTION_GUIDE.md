# NCPDP D.0 Claim Ingestion Guide

This guide explains how to ingest pharmacy claims using the NCPDP D.0 telecommunications standard format into the EDI 835 processing system.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
- [Command Line Tools](#command-line-tools)
- [Sample Data Generation](#sample-data-generation)
- [Monitoring & Troubleshooting](#monitoring--troubleshooting)
- [Configuration](#configuration)
- [Advanced Usage](#advanced-usage)

---

## Overview

The NCPDP ingestion system processes pharmacy prescription claims in NCPDP D.0 format and converts them into standardized claims for EDI 835 remittance processing.

### What is NCPDP D.0?

NCPDP (National Council for Prescription Drug Programs) D.0 is a telecommunications standard used for real-time transmission of pharmacy claims between pharmacies and payers. Each transaction contains segments with specific information about the prescription, patient, pharmacy, and pricing.

### Key Features

- **File-based ingestion**: Read NCPDP claim files and insert into database
- **Change feed processing**: Automatic polling and processing of pending claims
- **Status tracking**: Monitor claims through PENDING → PROCESSING → PROCESSED/FAILED lifecycle
- **Retry logic**: Automatic retry for failed claims with configurable max attempts
- **REST API**: Comprehensive API for ingestion, monitoring, and management
- **Sample data generation**: Built-in utility to create synthetic test data

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   NCPDP Ingestion Flow                          │
└─────────────────────────────────────────────────────────────────┘

1. File Ingestion
   ├── Read NCPDP file (STX → SE blocks)
   ├── Extract metadata (payer, pharmacy, patient, etc.)
   └── Insert into ncpdp_raw_claims table (status=PENDING)
            ↓
2. Change Feed Processor (polls every 5 seconds)
   ├── Find claims with status=PENDING
   ├── Mark as PROCESSING
   ├── Parse NCPDP transaction (NcpdpD0Parser)
   ├── Map to standard Claim (NcpdpToClaimMapper)
   ├── Process through RemittanceProcessor
   └── Update status (PROCESSED or FAILED)
            ↓
3. EDI 835 Generation
   ├── Claims aggregated into buckets
   ├── Threshold monitoring
   └── Generate EDI 835 files
```

### Database Schema

**ncpdp_raw_claims** table:
```sql
id                      VARCHAR(50) PRIMARY KEY
payer_id               VARCHAR(50) NOT NULL
pharmacy_id            VARCHAR(50)
patient_id             VARCHAR(50)
prescription_number    VARCHAR(50)
service_date           DATE
raw_content            TEXT NOT NULL
status                 VARCHAR(20) DEFAULT 'PENDING'
claim_id               VARCHAR(50)  -- Links to processed Claim
error_message          TEXT
retry_count            INTEGER DEFAULT 0
processing_started_date TIMESTAMP
processed_date         TIMESTAMP
created_date           TIMESTAMP NOT NULL
created_by             VARCHAR(100)
```

**Status Values**:
- `PENDING`: Waiting to be processed by change feed
- `PROCESSING`: Currently being processed
- `PROCESSED`: Successfully converted to claim
- `FAILED`: Processing failed (may retry if retry_count < max)

---

## Quick Start

### 1. Start the Application

```bash
cd edi835-processor
mvn spring-boot:run
```

Wait for the message:
```
Started Edi835ProcessorApplication in X.XXX seconds
```

### 2. Generate Sample Data

```bash
# Generate 50 sample claims
mvn exec:java -Dexec.mainClass="com.healthcare.edi835.util.NcpdpSampleGenerator" \
  -Dexec.args="50 d0-samples/ncpdp_rx_claims.txt"
```

### 3. Ingest Claims

```bash
# Using the ingestion script (recommended)
./ingest-ncpdp-claims.sh -f d0-samples/ncpdp_rx_claims.txt

# Or using curl directly
curl -X POST http://localhost:8080/api/v1/ncpdp/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "/absolute/path/to/d0-samples/ncpdp_rx_claims.txt",
    "stopOnError": false
  }'
```

### 4. Monitor Processing

```bash
# Check status
curl http://localhost:8080/api/v1/ncpdp/status

# View pending claims
curl http://localhost:8080/api/v1/ncpdp/claims/pending

# View failed claims
curl http://localhost:8080/api/v1/ncpdp/claims/failed
```

---

## API Reference

Base URL: `http://localhost:8080/api/v1/ncpdp`

### Ingestion Endpoints

#### POST /ingest
Ingest NCPDP claims from a file.

**Request Body**:
```json
{
  "filePath": "/path/to/ncpdp_claims.txt",
  "stopOnError": false
}
```

**Response** (200 OK / 207 MULTI_STATUS / 400 BAD_REQUEST):
```json
{
  "totalProcessed": 50,
  "totalSuccess": 48,
  "totalFailed": 2,
  "status": "PARTIAL",
  "errors": [
    "Failed to insert transaction: Invalid payer ID"
  ],
  "timestamp": "2024-10-30T23:45:12.123"
}
```

#### POST /ingest/default
Ingest from the default file path (configured in `application.yml`).

**Response**: Same as `/ingest`

---

### Status & Monitoring Endpoints

#### GET /status
Get current processing status summary.

**Response** (200 OK):
```json
{
  "pending": 15,
  "processing": 3,
  "processed": 182,
  "failed": 8,
  "total": 208,
  "successRate": 87.5
}
```

#### GET /metrics
Get detailed processing metrics.

**Response** (200 OK):
```json
{
  "statusBreakdown": {
    "pending": 15,
    "processing": 3,
    "processed": 182,
    "failed": 8
  },
  "total": 208,
  "successRate": "87.50%",
  "timestamp": "2024-10-30T23:45:12"
}
```

---

### Claim Retrieval Endpoints

#### GET /claims/pending
Get pending claims (waiting to be processed).

**Query Parameters**:
- `limit` (optional, default: 100): Maximum number of results

**Response** (200 OK):
```json
[
  {
    "id": "uuid-123",
    "payerId": "EXPRESS-SCRIPTS",
    "pharmacyId": "CVS-001",
    "patientId": "PAT123456789",
    "prescriptionNumber": "RX12345678",
    "serviceDate": "2024-10-15",
    "rawContent": "STX*D0*...",
    "status": "PENDING",
    "retryCount": 0,
    "createdDate": "2024-10-30T23:45:12",
    "createdBy": "INGESTION_SERVICE"
  }
]
```

#### GET /claims/failed
Get failed claims.

**Query Parameters**:
- `limit` (optional, default: 100): Maximum number of results

**Response**: Same structure as `/claims/pending`

#### GET /claims/processing
Get claims currently being processed.

**Response**: Same structure as `/claims/pending`

#### GET /claims/retryable
Get claims eligible for retry.

**Query Parameters**:
- `maxRetries` (optional, default: 3): Max retry threshold

**Response**: Same structure as `/claims/pending`

#### GET /claims/{claimId}
Get a specific claim by ID.

**Response** (200 OK or 404 NOT FOUND): Single claim object

#### GET /claims/by-payer/{payerId}
Get all claims for a specific payer.

**Response**: Array of claim objects

#### GET /claims/stuck
Get claims stuck in PROCESSING status.

**Query Parameters**:
- `thresholdMinutes` (optional, default: 30): Time threshold

**Response**: Array of stuck claims

---

### Admin Endpoints

#### DELETE /claims/by-status/{status}
Delete all claims with a specific status.

**⚠️ WARNING**: This is a destructive operation.

**Path Parameters**:
- `status`: PENDING, PROCESSING, PROCESSED, or FAILED

**Response** (200 OK):
```json
{
  "status": "SUCCESS",
  "deletedCount": 25,
  "message": "Deleted 25 claims with status PENDING"
}
```

---

## Command Line Tools

### Ingestion Script

`ingest-ncpdp-claims.sh` - Full-featured bash script for ingesting claims with progress monitoring.

**Usage**:
```bash
./ingest-ncpdp-claims.sh [OPTIONS]
```

**Options**:
- `-f, --file FILE`: Path to NCPDP file (default: `d0-samples/ncpdp_rx_claims.txt`)
- `-d, --default`: Use default file from application.yml
- `-s, --stop-on-error`: Stop on first error
- `-u, --url URL`: API base URL (default: `http://localhost:8080/api/v1/ncpdp`)
- `-h, --help`: Show help

**Examples**:
```bash
# Ingest from default file
./ingest-ncpdp-claims.sh

# Ingest from custom file
./ingest-ncpdp-claims.sh -f /path/to/claims.txt

# Stop on first error
./ingest-ncpdp-claims.sh -f claims.txt -s

# Use production API
./ingest-ncpdp-claims.sh -u http://prod:8080/api/v1/ncpdp
```

**Output**:
```
=========================================
NCPDP D.0 Claim Ingestion
=========================================

Step 1: Checking API availability...
✓ API is available

Step 2: Getting current status...
Current status:
  Pending:   0
  Processed: 0
  Failed:    0

Step 3: Ingesting from file: d0-samples/ncpdp_rx_claims.txt
Absolute path: /home/user/edi835-processor/d0-samples/ncpdp_rx_claims.txt

✓ Ingestion completed: SUCCESS

Ingestion Results:
  Total Processed: 50
  Successful:      50
  Failed:          0

Step 4: Waiting for change feed processing...
Monitoring status (updates every 2 seconds, max 60 seconds)...

  Pending: 0 | Processing: 0 | Processed: +50 | Failed: 0 | Elapsed: 12s

✓ All claims processed!

Step 5: Final status summary...
Final Status:
  Total:        50
  Pending:      0
  Processing:   0
  Processed:    50 (from 0)
  Failed:       0 (from 0)
  Success Rate: 100.0%

=========================================
✓ INGESTION COMPLETE
=========================================

Next steps:
  1. View dashboard: http://localhost:8080/api/v1/dashboard/summary
  2. Check active buckets: http://localhost:8080/api/v1/dashboard/buckets/metrics
  3. View failed claims: curl http://localhost:8080/api/v1/ncpdp/claims/failed
```

---

## Sample Data Generation

### Using NcpdpSampleGenerator

The `NcpdpSampleGenerator` utility creates realistic synthetic NCPDP claims for testing.

**Via Maven**:
```bash
mvn exec:java \
  -Dexec.mainClass="com.healthcare.edi835.util.NcpdpSampleGenerator" \
  -Dexec.args="[count] [outputFile]"
```

**Examples**:
```bash
# Generate 100 claims (default)
mvn exec:java -Dexec.mainClass="com.healthcare.edi835.util.NcpdpSampleGenerator"

# Generate 500 claims
mvn exec:java -Dexec.mainClass="com.healthcare.edi835.util.NcpdpSampleGenerator" \
  -Dexec.args="500"

# Generate to custom file
mvn exec:java -Dexec.mainClass="com.healthcare.edi835.util.NcpdpSampleGenerator" \
  -Dexec.args="200 custom-claims.txt"
```

**Claim Type Distribution**:
- 40% Brand name prescriptions
- 30% Generic refills
- 10% Compound medications
- 10% Controlled substances (with DEA numbers)
- 10% Rejected claims

**Sample Output File Format**:
```
# NCPDP D.0 Sample Claims
# Generated: 2024-10-30
# Total claims: 50

STX*D0*TXN1730332799123-456*
AM01*01*CVS-001*1234 MAIN ST*ANYTOWN*CA*90210*
AM04*610020*RX001*
AM07*EXPRESS-SCRIPTS*MEMBER001*PAT123456789*01*
AM11*1234567890*SMITH*JOHN*MD*
AM13*20241015*RX12345678*00002-7510-02*30*0*
AM15*125.50*2.50*
AM17*128.00*25.00*0.00*
AN02*APPROVED*A*
AN23*103.00*25.00*
SE*TXN1730332799123-456*

(... more transactions ...)
```

---

## Monitoring & Troubleshooting

### Monitoring Tools

#### 1. Real-time Status
```bash
# Watch status every 2 seconds
watch -n 2 'curl -s http://localhost:8080/api/v1/ncpdp/status | jq'
```

#### 2. Check Logs
```bash
# Application logs
tail -f logs/edi835-processor.log | grep NCPDP

# Filter for errors
tail -f logs/edi835-processor.log | grep -i "error\|exception"
```

#### 3. Database Queries
```bash
# Connect to SQLite database
sqlite3 data/edi835-local.db

# Count by status
SELECT status, COUNT(*) FROM ncpdp_raw_claims GROUP BY status;

# Find failed claims
SELECT id, payer_id, error_message, retry_count
FROM ncpdp_raw_claims
WHERE status = 'FAILED';

# Find stuck claims
SELECT id, payer_id, processing_started_date
FROM ncpdp_raw_claims
WHERE status = 'PROCESSING'
  AND processing_started_date < datetime('now', '-30 minutes');
```

### Common Issues

#### Issue: Claims stuck in PENDING status

**Cause**: Change feed processor not running or disabled.

**Solution**:
1. Check configuration in `application.yml`:
   ```yaml
   changefeed:
     ncpdp:
       enabled: true
       poll-interval-ms: 5000
   ```
2. Verify in logs: `NcpdpChangeFeedProcessor initialized`
3. Restart application if needed

---

#### Issue: Claims stuck in PROCESSING status

**Cause**: Processing failed without updating status, or processing is genuinely taking long.

**Solution**:
1. Check for stuck claims:
   ```bash
   curl http://localhost:8080/api/v1/ncpdp/claims/stuck?thresholdMinutes=30
   ```
2. The change feed processor automatically resets stuck claims (configurable via `stuck-threshold-minutes`)
3. Manual reset via database:
   ```sql
   UPDATE ncpdp_raw_claims
   SET status = 'PENDING', processing_started_date = NULL
   WHERE status = 'PROCESSING'
     AND processing_started_date < datetime('now', '-30 minutes');
   ```

---

#### Issue: High failure rate

**Cause**: Invalid data, mapping errors, or downstream processing issues.

**Solution**:
1. Check error messages:
   ```bash
   curl http://localhost:8080/api/v1/ncpdp/claims/failed | jq '.[].errorMessage'
   ```
2. Review logs for specific errors
3. Validate source data format
4. Check parser configuration:
   ```yaml
   ncpdp:
     parser:
       strict-validation: false  # Set to true for strict mode
       skip-invalid-segments: true
   ```

---

#### Issue: File not found error

**Cause**: Relative path vs. absolute path mismatch.

**Solution**:
Always use absolute paths in API requests:
```bash
# Get absolute path
realpath d0-samples/ncpdp_rx_claims.txt

# Use in request
curl -X POST http://localhost:8080/api/v1/ncpdp/ingest \
  -H "Content-Type: application/json" \
  -d "{\"filePath\": \"$(realpath d0-samples/ncpdp_rx_claims.txt)\"}"
```

---

## Configuration

### application.yml Settings

```yaml
# NCPDP Change Feed Configuration
changefeed:
  ncpdp:
    enabled: true                     # Enable/disable NCPDP change feed
    poll-interval-ms: 5000            # How often to poll for pending claims (ms)
    batch-size: 50                    # Max claims to process per batch
    max-retries: 3                    # Max retry attempts for failed claims
    stuck-threshold-minutes: 30       # Reset stuck claims after X minutes

# NCPDP Ingestion Configuration
ncpdp:
  ingestion:
    default-file-path: d0-samples/ncpdp_rx_claims.txt
    auto-process: true                # Auto-process when inserted
  parser:
    strict-validation: false          # Allow missing optional fields
    skip-invalid-segments: true       # Skip unknown segments vs. failing
```

### Environment Variables

Override configuration via environment variables:

```bash
# Change feed settings
export NCPDP_CHANGEFEED_ENABLED=true
export NCPDP_POLL_INTERVAL=3000
export NCPDP_BATCH_SIZE=100

# Start application
mvn spring-boot:run
```

---

## Advanced Usage

### Custom Integration

#### Programmatic Ingestion

```java
@Autowired
private NcpdpIngestionService ingestionService;

public void ingestClaims(String filePath) {
    IngestionResult result = ingestionService.ingestFromFile(filePath, false);

    if ("SUCCESS".equals(result.getStatus())) {
        log.info("Ingested {} claims", result.getTotalSuccess());
    } else {
        log.error("Failed: {}", result.getErrors());
    }
}
```

#### Direct Database Insertion

```java
@Autowired
private NcpdpRawClaimRepository repository;

public void insertRawClaim(String rawContent) {
    NcpdpRawClaim claim = NcpdpRawClaim.builder()
        .id(UUID.randomUUID().toString())
        .payerId("EXPRESS-SCRIPTS")
        .rawContent(rawContent)
        .status(NcpdpStatus.PENDING)
        .createdDate(LocalDateTime.now())
        .createdBy("CUSTOM_INTEGRATION")
        .build();

    repository.save(claim);
    // Change feed will automatically process
}
```

### Performance Tuning

#### High-Volume Ingestion

For ingesting large files (>10,000 claims):

1. Increase batch size:
   ```yaml
   changefeed:
     ncpdp:
       batch-size: 200
   ```

2. Reduce poll interval for faster processing:
   ```yaml
   changefeed:
     ncpdp:
       poll-interval-ms: 1000
   ```

3. Use database connection pooling:
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
   ```

4. Consider parallel processing by running multiple instances

#### Memory Optimization

The ingestion service uses streaming for file reading, so memory usage is constant regardless of file size. However, for very large batches:

```yaml
changefeed:
  ncpdp:
    batch-size: 50  # Smaller batches = less memory
```

---

## Testing

### Unit Tests

Run parser and mapper tests:
```bash
mvn test -Dtest=NcpdpD0ParserTest
mvn test -Dtest=NcpdpToClaimMapperTest
```

### Integration Tests

Run full end-to-end tests:
```bash
mvn test -Dtest=NcpdpIngestionIntegrationTest
```

### Manual Testing Workflow

1. **Generate sample data**:
   ```bash
   mvn exec:java -Dexec.mainClass="com.healthcare.edi835.util.NcpdpSampleGenerator" \
     -Dexec.args="100 test-claims.txt"
   ```

2. **Clean database**:
   ```bash
   sqlite3 data/edi835-local.db "DELETE FROM ncpdp_raw_claims;"
   ```

3. **Ingest**:
   ```bash
   ./ingest-ncpdp-claims.sh -f test-claims.txt
   ```

4. **Verify**:
   ```bash
   curl http://localhost:8080/api/v1/ncpdp/status
   ```

---

## Appendix

### NCPDP D.0 Segment Reference

Common segments used in pharmacy claims:

| Segment | Description | Example |
|---------|-------------|---------|
| STX | Transaction Header | `STX*D0*TXN123*` |
| AM01 | Transmission Header | `AM01*1234567*PHARMACY001*...` |
| AM04 | Insurance Segment | `AM04*01*R*1*` |
| AM07 | Patient Segment | `AM07*BCBSIL*60054*123456789*...` |
| AM11 | Prescriber Segment | `AM11*1234567890*1*...` |
| AM13 | Claim Segment | `AM13*20241014*RX12345*...` |
| AM15 | Pricing Segment | `AM15*59762-0123-03*` |
| AM17 | Pricing Details | `AM17*01*250.00*02*225.00*...` |
| AN02 | Response Status | `AN02*APPROVED*A*` |
| SE | Segment Trailer | `SE*15*1234567*` |

### HTTP Status Codes

| Code | Status | Description |
|------|--------|-------------|
| 200 | OK | Ingestion successful |
| 207 | Multi-Status | Partial success (some claims failed) |
| 400 | Bad Request | Validation error or file not found |
| 404 | Not Found | Claim not found |
| 500 | Internal Server Error | Server error |

### File Format Specification

**File Structure**:
```
[Comment lines starting with #]
STX*...*
AM01*...*
...
SE*...*

STX*...*  [Next transaction]
AM01*...*
...
SE*...*
```

**Requirements**:
- Each transaction starts with `STX` or `STX*D0*`
- Each transaction ends with `SE` or `ANC1`
- Segments are separated by newlines
- Fields within segments are separated by `*`
- Empty lines and comments (starting with `#`) outside transactions are ignored

---

## Support & Resources

- **Project Documentation**: `/docs/NCPDP_INTEGRATION_PLAN.md`
- **API Documentation**: `http://localhost:8080/api/v1/swagger-ui.html` (if Swagger enabled)
- **Application Logs**: `logs/edi835-processor.log`
- **Database Schema**: `database/migrations/002_create_ncpdp_raw_claims.sql`

---

**Version**: 1.0
**Last Updated**: October 30, 2024
**Maintained by**: EDI 835 Development Team
