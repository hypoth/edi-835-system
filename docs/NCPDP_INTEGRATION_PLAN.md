# NCPDP D.0 Rx Claims Integration Plan

**Date:** 2025-10-30
**Status:** In Progress
**Approach:** Script-Based Ingestion with Separate Table Change Feed

---

## Executive Summary

This plan describes the integration of NCPDP D.0 pharmacy prescription claims into the EDI 835 processing system. The solution uses a script-based ingestion approach with a dedicated change feed processor that automatically parses, maps, and processes NCPDP claims through the existing EDI 835 remittance pipeline.

---

## Architecture Overview

### Data Flow

```
┌─────────────┐
│   Script    │
│  (Generate  │
│  or Load)   │
└──────┬──────┘
       │
       ▼
┌─────────────────────┐
│ ncpdp_raw_claims    │ ← Separate table for raw NCPDP data
│ (status='PENDING')  │
└──────┬──────────────┘
       │
       │ (Polling every 5s)
       ▼
┌──────────────────────────┐
│ NcpdpChangeFeedProcessor │ ← Dedicated change feed
└──────┬───────────────────┘
       │
       ▼
┌─────────────────┐
│ NcpdpD0Parser   │ ← Parse NCPDP text
└──────┬──────────┘
       │
       ▼
┌────────────────────┐
│ NcpdpToClaimMapper │ ← Map NCPDP → Claim
└──────┬─────────────┘
       │
       ▼
┌───────────────────────────┐
│ RemittanceProcessorService │ ← Standard processing
└──────┬────────────────────┘
       │
       ▼
┌─────────────────┐
│ Bucketing       │ → EDI 835 Generation
│ & Thresholds    │
└─────────────────┘
```

### Why Separate Table (Option B)

**Benefits:**
- ✅ Clear separation between raw NCPDP data and processed claims
- ✅ Enables re-processing if parsing logic changes
- ✅ Audit trail of original NCPDP transactions
- ✅ Better debugging (can see raw vs processed)
- ✅ Independent change feed for NCPDP vs standard claims
- ✅ No sourceType field needed in claims table

**Status Flow:**
1. Script inserts into `ncpdp_raw_claims` (status='PENDING')
2. `NcpdpChangeFeedProcessor` detects new rows
3. Parser converts NCPDP text → NcpdpTransaction object
4. Mapper converts NcpdpTransaction → Claim object
5. Claim forwarded to RemittanceProcessorService
6. Update `ncpdp_raw_claims.status='PROCESSED'`
7. Standard change feed on claims table continues normal flow

---

## Components to Build

### 1. Database Schema

**Table: `ncpdp_raw_claims`**

```sql
CREATE TABLE ncpdp_raw_claims (
    id VARCHAR(50) PRIMARY KEY,
    payer_id VARCHAR(50) NOT NULL,
    pharmacy_id VARCHAR(50),
    transaction_id VARCHAR(50),
    raw_content TEXT NOT NULL,              -- Complete NCPDP transaction
    transaction_type VARCHAR(10),
    service_date DATE,
    patient_id VARCHAR(50),
    prescription_number VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING',   -- PENDING, PROCESSING, PROCESSED, FAILED
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_date TIMESTAMP,
    claim_id VARCHAR(50),                   -- Links to processed Claim.id
    error_message TEXT,
    retry_count INTEGER DEFAULT 0
);
```

**Indexes:**
- `idx_ncpdp_status` - Fast lookup by status
- `idx_ncpdp_payer` - Query by payer
- `idx_ncpdp_pharmacy` - Query by pharmacy
- `idx_ncpdp_pending_created` - Composite for change feed queries

**Logging Table: `ncpdp_processing_log`**
- Tracks processing stages (PARSE, MAP, PROCESS, COMPLETE)
- Stores error details and processing metrics

### 2. Entity & Repository

**Entity:** `NcpdpRawClaim.java`
- JPA entity mapping to `ncpdp_raw_claims` table
- Enum `NcpdpStatus` {PENDING, PROCESSING, PROCESSED, FAILED}
- Helper methods: `markAsProcessing()`, `markAsProcessed()`, `markAsFailed()`, `canRetry()`

**Repository:** `NcpdpRawClaimRepository.java`
- `findByStatusOrderByCreatedDateAsc(NcpdpStatus)` - FIFO processing
- `findFailedClaimsForRetry(int maxRetries)` - Retry failed claims
- `countByStatus(NcpdpStatus)` - Status summary
- `findStuckProcessingClaims(LocalDateTime)` - Find hung processes

### 3. NCPDP Models

**Package:** `com.healthcare.edi835.model.ncpdp`

**Main Model:** `NcpdpTransaction`
- Contains all NCPDP segments
- Methods: `hasResponse()`, `isApproved()`, `isRejected()`, `isCompound()`

**Segment Models:**
- `TransactionHeader` (AM01) - Service provider ID, date/time
- `InsuranceSegment` (AM04) - Cardholder ID, prescription origin, fill number
- `PatientSegment` (AM07) - Patient demographics, payer ID, BIN/PCN
- `PrescriberSegment` (AM11) - Prescriber NPI, name, contact
- `ClaimSegment` (AM13) - Prescription details, NDC, quantity, days supply
- `PricingSegment` (AM15, AM17) - Costs, fees, totals
- `CompoundSegment` (AM14) - Ingredient details for compounds
- `PriorAuthorizationSegment` (AM19) - Prior auth info
- `ClinicalSegment` (AM20) - Diagnosis codes
- `AdditionalDocumentationSegment` (AM21) - DEA, state license, PA number
- `ResponseStatusSegment` (AN02) - Approval/rejection status
- `ResponsePaymentSegment` (AN23) - Paid amounts
- `ResponseMessageSegment` (AN25) - Message and auth number

### 4. NCPDP Parser

**Class:** `NcpdpD0Parser.java`

**Purpose:** Parse raw NCPDP D.0 text format into Java objects

**Key Methods:**
- `parse(String rawContent) → NcpdpTransaction` - Main parse method
- `parseAM01(String[] fields) → TransactionHeader`
- `parseAM07(String[] fields) → PatientSegment`
- `parseAM13(String[] fields) → ClaimSegment`
- `parseAM17(String[] fields) → PricingSegment`
- `parseAN02(String[] fields) → ResponseStatusSegment`

**Parsing Logic:**
- Split by `*` delimiter
- Handle multi-line transactions (STX → SE blocks)
- Extract segment ID from first field
- Map fields by position to model properties
- Handle optional segments (AM14, AM19, AM20, AM21)

**Example:**
```java
String rawTx = "STX*D0*...\nAM01*1234567*...\n...SE*15*...";
NcpdpTransaction tx = parser.parse(rawTx);
// tx.getPatient().getCarrierId() → "BCBSIL"
// tx.getPricing().getGrossAmountDue() → BigDecimal("250.00")
```

### 5. NCPDP to Claim Mapper

**Class:** `NcpdpToClaimMapper.java`

**Purpose:** Convert NCPDP transaction to standard Claim model

**Mapping Rules:**

| NCPDP Field | Claim Field | Notes |
|-------------|-------------|-------|
| AM07 - Carrier ID | payerId | e.g., "BCBSIL", "CIGNA" |
| AM01 - Pharmacy ID | payeeId | e.g., "PHARMACY001" |
| AM07 - Patient name | patientName | Combined first/last |
| AM17-11 | totalChargeAmount | Gross amount due |
| AN23-01 + AN23-02 | paidAmount | Ingredient + dispensing fee |
| AN23-03 | patientResponsibilityAmount | Patient copay |
| AM13 - Service date | serviceDate | YYYYMMDD → LocalDate |
| AM15 - NDC | procedureCode | National Drug Code |
| AM13 - Quantity | units | Quantity dispensed |
| AM07 - BIN | binNumber | BIN number |
| AN02 - Status | status | A=PAID, R=DENIED |

**Example:**
```java
NcpdpTransaction ncpdpTx = ...;
Claim claim = mapper.mapToClaim(ncpdpTx);
// claim.getPayerId() → "BCBSIL"
// claim.getTotalChargeAmount() → BigDecimal("250.00")
// claim.getStatus() → ClaimStatus.PAID
```

### 6. Change Feed Processor

**Class:** `NcpdpChangeFeedProcessor.java`

**Purpose:** Poll `ncpdp_raw_claims` table and process PENDING claims

**Configuration:**
```yaml
changefeed:
  ncpdp:
    enabled: true
    poll-interval-ms: 5000
    batch-size: 50
    max-retries: 3
```

**Processing Flow:**
```java
@Scheduled(fixedDelay = "${changefeed.ncpdp.poll-interval-ms}")
public void processPendingClaims() {
    List<NcpdpRawClaim> pending = repository
        .findByStatusOrderByCreatedDateAsc(PENDING)
        .stream()
        .limit(batchSize)
        .toList();

    for (NcpdpRawClaim raw : pending) {
        processNcpdpClaim(raw);
    }
}

private void processNcpdpClaim(NcpdpRawClaim raw) {
    raw.markAsProcessing();
    repository.save(raw);

    NcpdpTransaction tx = parser.parse(raw.getRawContent());
    Claim claim = mapper.mapToClaim(tx);
    remittanceProcessor.processClaim(claim);

    raw.markAsProcessed(claim.getId());
    repository.save(raw);
}
```

**Error Handling:**
- Failed claims marked as `FAILED` with error message
- Retry count incremented
- Can be retried up to `max-retries` times
- Stuck claims (PROCESSING > threshold) can be detected and reset

### 7. Ingestion Service

**Class:** `NcpdpIngestionService.java`

**Purpose:** Handle file reading and database insertion

**Methods:**
- `ingestFromFile(String filePath) → IngestionResult`
- `generateClaims(int count) → IngestionResult`
- `createRawClaim(String rawContent) → NcpdpRawClaim`
- `readTransactionsFromFile(String filePath) → List<String>`

**File Reading Logic:**
```java
// Read file and split into transactions (STX → SE blocks)
List<String> transactions = new ArrayList<>();
StringBuilder currentTx = new StringBuilder();

try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
    String line;
    while ((line = reader.readLine()) != null) {
        if (line.startsWith("STX")) {
            currentTx = new StringBuilder();
        }
        currentTx.append(line).append("\n");
        if (line.startsWith("SE")) {
            transactions.add(currentTx.toString());
        }
    }
}
```

### 8. REST API Controller

**Class:** `NcpdpIngestionController.java`

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/ncpdp/ingest` | Load from file |
| POST | `/api/v1/ncpdp/generate?count=N` | Generate N synthetic claims |
| GET | `/api/v1/ncpdp/status` | Get processing status |
| GET | `/api/v1/ncpdp/claims/pending` | List pending claims |
| GET | `/api/v1/ncpdp/claims/failed` | List failed claims |

**Example Responses:**
```json
// POST /api/v1/ncpdp/generate?count=10
{
  "totalProcessed": 10,
  "totalFailed": 0,
  "status": "SUCCESS"
}

// GET /api/v1/ncpdp/status
{
  "pending": 5,
  "processing": 2,
  "processed": 1243,
  "failed": 7
}
```

### 9. Ingestion Script

**File:** `edi835-processor/scripts/ingest-ncpdp-claims.sh`

**Usage:**
```bash
# Generate 10 synthetic claims
./ingest-ncpdp-claims.sh generate 10

# Load from file
./ingest-ncpdp-claims.sh load ../d0-samples/ncpdp_rx_claims.txt

# Check status
./ingest-ncpdp-claims.sh status
```

**Implementation:**
```bash
#!/bin/bash
API_BASE_URL=${API_BASE_URL:-http://localhost:8080/api/v1}
MODE=$1
ARG=$2

case $MODE in
    generate)
        curl -X POST "$API_BASE_URL/ncpdp/generate?count=$ARG"
        ;;
    load)
        curl -X POST "$API_BASE_URL/ncpdp/ingest" \
             -H "Content-Type: application/json" \
             -d "{\"filePath\": \"$ARG\"}"
        ;;
    status)
        curl -s "$API_BASE_URL/ncpdp/status" | jq
        ;;
esac
```

### 10. Sample Generator

**Class:** `NcpdpSampleGenerator.java`

**Purpose:** Generate realistic NCPDP test data

**Features:**
- Random payers (BCBSIL, CIGNA, AETNA, UNITEDHC, etc.)
- Realistic drug names and NDC codes
- Varying amounts ($10 - $6500)
- Both approved and rejected claims
- New prescriptions and refills
- Compound prescriptions (10% of samples)

### 11. Configuration

**File:** `application.yml`

```yaml
# NCPDP Configuration
changefeed:
  ncpdp:
    enabled: ${NCPDP_CHANGEFEED_ENABLED:true}
    poll-interval-ms: ${NCPDP_POLL_INTERVAL:5000}
    batch-size: ${NCPDP_BATCH_SIZE:50}
    max-retries: 3

ncpdp:
  ingestion:
    default-file-path: d0-samples/ncpdp_rx_claims.txt
    auto-process: true
  parser:
    strict-validation: false
    skip-invalid-segments: true
```

---

## Implementation Phases

### Phase 1: Database Foundation ✅
- [x] Create `ncpdp_raw_claims` table migration
- [x] Create `NcpdpRawClaim` entity
- [x] Create `NcpdpRawClaimRepository`

### Phase 2: NCPDP Models & Parser ✅
- [x] Create NCPDP model classes
- [ ] Implement `NcpdpD0Parser`
- [ ] Unit tests for parser

### Phase 3: Mapper
- [ ] Create `NcpdpToClaimMapper`
- [ ] Implement field mappings
- [ ] Unit tests for mappings

### Phase 4: Dedicated Change Feed
- [ ] Create `NcpdpChangeFeedProcessor`
- [ ] Implement polling logic
- [ ] Integrate parser + mapper
- [ ] Test auto-processing

### Phase 5: Ingestion Service + API
- [ ] Create `NcpdpIngestionService`
- [ ] Implement file reading
- [ ] Create `NcpdpIngestionController`
- [ ] Test endpoints

### Phase 6: Script
- [ ] Create `ingest-ncpdp-claims.sh`
- [ ] Test generate command
- [ ] Test load command

### Phase 7: Integration Testing
- [ ] End-to-end: Script → DB → Change Feed → Bucket → EDI 835
- [ ] Test with all 6 samples
- [ ] Load test (1000 claims)

### Phase 8: Documentation
- [ ] Create `NCPDP_INGESTION_GUIDE.md`
- [ ] Update CLAUDE.md

---

## NCPDP D.0 Format Reference

### Transaction Structure

```
STX*D0*          *          *
AM01*<pharmacy-id>*<date>*<time>*
AM04*<qualifier>*<origin>*<fill-num>*
AM07*<payer>*<bin>*<member-id>*...*<patient-name>*...*<address>*
AM11*<prescriber-npi>*...*<prescriber-name>*
AM13*<service-date>*<rx-num>*...*<ndc>*<drug>*<qty>*<days-supply>*
AM15*<ndc>*
AM17*01*<ing-cost-submitted>*02*<ing-cost-paid>*03*<disp-fee-submitted>*04*<disp-fee-paid>*11*<gross-amount>*
SE*<count>*<id>*

Response (if present):
AN02*<status>*<code>*<message>*
AN23*01*<ing-cost-paid>*02*<disp-fee-paid>*03*<patient-pay>*05*<total>*
AN25*<message>*<auth-number>*
```

### Common Field Values

**Payers:**
- BCBSIL - Blue Cross Blue Shield Illinois
- CIGNA - Cigna Health
- AETNA - Aetna
- UNITEDHC - UnitedHealthcare
- EXPRESSCR - Express Scripts
- ANTHEM - Anthem

**Response Status:**
- A = Approved
- R = Rejected

**Prescription Origin:**
- R = Written
- C = Compound
- E = Electronic

---

## Testing Strategy

### Unit Tests
- Parser tests with sample segments
- Mapper tests for each field
- Service method tests

### Integration Tests
- File → Database insertion
- Change feed processing
- End-to-end claim flow

### Test Data
- 6 sample claims in `d0-samples/ncpdp_rx_claims.txt`:
  1. Brand name prescription (Lipitor) - $250
  2. Generic refill (Lisinopril) - $45
  3. Compound prescription - $250
  4. Controlled substance (Hydrocodone) - $85
  5. Insulin prescription - $450
  6. Specialty drug with prior auth (Humira) - $6500

---

## Success Criteria

✅ **Database:**
- [x] `ncpdp_raw_claims` table created
- [x] Entity and repository working

✅ **Parser:**
- [x] NCPDP models created
- [ ] Parser successfully parses all 6 samples
- [ ] Handles optional segments

✅ **Mapper:**
- [ ] All NCPDP fields map to Claim
- [ ] Financial amounts accurate
- [ ] Status mapping correct (A→PAID, R→DENIED)

✅ **Change Feed:**
- [ ] Detects new PENDING claims automatically
- [ ] Processes in FIFO order
- [ ] Updates status correctly
- [ ] Handles errors gracefully

✅ **Ingestion:**
- [ ] Script loads from file
- [ ] Script generates synthetic claims
- [ ] REST API works

✅ **Integration:**
- [ ] NCPDP claims flow through bucketing
- [ ] EDI 835 files generated correctly
- [ ] Dashboard shows NCPDP claims

✅ **Documentation:**
- [ ] User guide complete
- [ ] API documented
- [ ] Field mappings documented

---

## Performance Considerations

**Change Feed Polling:**
- Poll interval: 5 seconds (configurable)
- Batch size: 50 claims per poll
- ~600 claims/minute throughput

**Database:**
- Indexes on status, payer_id, created_date
- Composite index for pending claims query
- Cleanup old processed claims (retention policy)

**Parser:**
- Streaming approach for large files
- Line-by-line reading
- Transaction buffering

**Error Handling:**
- Retry failed claims (max 3 attempts)
- Exponential backoff
- Dead-letter queue for permanent failures

---

## Monitoring & Alerts

**Metrics to Track:**
- Pending claim count
- Processing throughput (claims/minute)
- Error rate
- Average processing time
- Stuck processing claims

**Alerts:**
- Pending claims > threshold for > 5 minutes
- Error rate > 5%
- Stuck processing claims detected
- Failed claims exceeding max retries

---

## Future Enhancements

1. **Real-time Change Feed:** Use database triggers instead of polling
2. **Async Processing:** Use message queue (RabbitMQ, Kafka) for scalability
3. **Advanced Retry Logic:** Exponential backoff, circuit breaker
4. **Synthetic Data Generator:** More realistic claim generation
5. **NCPDP Validation:** Schema validation against NCPDP standard
6. **Multi-format Support:** Support NCPDP versions beyond D.0

---

## References

- **NCPDP Standard:** [NCPDP Telecommunications Standard](https://standards.ncpdp.org/)
- **Sample File:** `d0-samples/ncpdp_rx_claims.txt`
- **EDI 835 Spec:** HIPAA 5010 X12 835
- **Architecture Doc:** `ARCHITECTURE-GUIDE.txt`

---

**Document Version:** 1.0
**Last Updated:** 2025-10-30
**Author:** Claude Code Integration Team
