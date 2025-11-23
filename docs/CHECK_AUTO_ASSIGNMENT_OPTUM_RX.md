# Check Payment Auto-Assignment Flow for Optum_Rx

## Executive Summary

This document provides a detailed technical explanation of how check payments are automatically assigned to EDI 835 buckets for the payer **Optum_Rx**. The system supports three workflow modes and three assignment modes, with auto-assignment being a key feature for high-volume payers.

---

## Table of Contents

1. [Prerequisites & Configuration](#1-prerequisites--configuration)
2. [Database Configuration Required](#2-database-configuration-required)
3. [Workflow Modes Explained](#3-workflow-modes-explained)
4. [Assignment Modes Explained](#4-assignment-modes-explained)
5. [Complete Auto-Assignment Flow](#5-complete-auto-assignment-flow)
6. [Code Flow & Key Methods](#6-code-flow--key-methods)
7. [Database Tables Involved](#7-database-tables-involved)
8. [Status Transitions](#8-status-transitions)
9. [Example Configuration for Optum_Rx](#9-example-configuration-for-optum_rx)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Prerequisites & Configuration

For auto-assignment to work for **Optum_Rx**, the following must be configured:

### 1.1 Payer Record
A payer record must exist in the `payers` table with:
- `payer_id`: "OPTUM_RX" (business identifier)
- `id`: UUID (system identifier used for check reservations)

### 1.2 Check Reservations
Pre-allocated check number ranges must exist in `check_reservations` table linked to the Optum_Rx payer's UUID.

### 1.3 Workflow Configuration
A `check_payment_workflow_config` record with:
- `workflow_mode`: 'SEPARATE' or 'COMBINED'
- `assignment_mode`: 'AUTO' or 'BOTH'
- `linked_threshold_id`: Links to the threshold for Optum_Rx buckets

---

## 2. Database Configuration Required

### 2.1 Step 1: Ensure Payer Exists

```sql
-- Check if Optum_Rx payer exists
SELECT id, payer_id, payer_name FROM payers WHERE payer_id = 'OPTUM_RX';

-- If not exists, create it:
INSERT INTO payers (payer_id, payer_name, isa_sender_id, is_active)
VALUES ('OPTUM_RX', 'Optum Rx Services', 'OPTUMRX', true);
```

### 2.2 Step 2: Create Bucketing Rule (if needed)

```sql
-- Create a bucketing rule for Optum_Rx
INSERT INTO edi_bucketing_rules (rule_name, rule_type, linked_payer_id, is_active)
SELECT 'Optum_Rx_Rule', 'PAYER_PAYEE', id, true
FROM payers WHERE payer_id = 'OPTUM_RX';
```

### 2.3 Step 3: Create Generation Threshold

```sql
-- Create threshold linked to the bucketing rule
INSERT INTO edi_generation_thresholds (
    threshold_name,
    threshold_type,
    max_claims,
    max_amount,
    linked_bucketing_rule_id,
    is_active
)
SELECT
    'Optum_Rx_Threshold',
    'HYBRID',
    100,           -- Max 100 claims
    50000.00,      -- Max $50,000
    id,
    true
FROM edi_bucketing_rules WHERE rule_name = 'Optum_Rx_Rule';
```

### 2.4 Step 4: Create Check Payment Workflow Config

```sql
-- Create workflow config with AUTO assignment mode
INSERT INTO check_payment_workflow_config (
    config_name,
    workflow_mode,
    assignment_mode,
    require_acknowledgment,
    linked_threshold_id,
    description,
    is_active
)
SELECT
    'Optum_Rx_Check_Workflow',
    'SEPARATE',     -- Approve first, then assign check
    'AUTO',         -- Auto-assign from reservations
    false,          -- No acknowledgment required before EDI
    id,
    'Auto-assignment workflow for Optum_Rx check payments',
    true
FROM edi_generation_thresholds WHERE threshold_name = 'Optum_Rx_Threshold';
```

### 2.5 Step 5: Create Check Reservations (Pre-allocate Check Numbers)

```sql
-- Get Optum_Rx payer UUID
-- Then create check reservation range

INSERT INTO check_reservations (
    check_number_start,
    check_number_end,
    total_checks,
    checks_used,
    bank_name,
    routing_number,
    account_number_last4,
    payer_id,
    status,
    created_by
)
SELECT
    'CHK100001',           -- Starting check number
    'CHK100500',           -- Ending check number
    500,                   -- Total checks in range
    0,                     -- None used yet
    'First National Bank',
    '021000021',
    '4567',
    id,                    -- Optum_Rx payer UUID
    'ACTIVE',
    'admin'
FROM payers WHERE payer_id = 'OPTUM_RX';
```

---

## 3. Workflow Modes Explained

The system supports three workflow modes configured in `check_payment_workflow_config.workflow_mode`:

### 3.1 NONE Mode
- **Description**: No check payment required
- **Use Case**: EFT payments, other payment methods
- **Flow**: Bucket approval → EDI generation immediately
- **Check Assignment**: Not applicable

### 3.2 SEPARATE Mode (Recommended for Optum_Rx)
- **Description**: Two-step process - approve first, then assign check
- **Use Case**: When approval and check assignment are handled by different teams
- **Flow**:
  1. Bucket reaches threshold → PENDING_APPROVAL
  2. Admin approves bucket → Bucket remains PENDING_APPROVAL (awaiting check)
  3. Check assigned (auto or manual) → EDI generation triggered

### 3.3 COMBINED Mode
- **Description**: Single-step - approve and assign check together
- **Use Case**: When same person handles both approval and check
- **Flow**:
  1. Bucket reaches threshold → PENDING_APPROVAL
  2. Admin approves + assigns check in one dialog → EDI generation triggered

---

## 4. Assignment Modes Explained

The system supports three assignment modes in `check_payment_workflow_config.assignment_mode`:

### 4.1 MANUAL Mode
- User manually enters check number, date, and bank details
- No pre-reserved check numbers used
- UI shows input form for check details

### 4.2 AUTO Mode (Recommended for High-Volume Payers like Optum_Rx)
- System automatically assigns next available check from reservations
- Uses FIFO order (oldest reservation first)
- No user input required for check details
- Check number, date, bank info pulled from reservation

### 4.3 BOTH Mode
- User can choose between manual entry or auto-assignment
- UI shows dropdown to select method
- Flexible for different scenarios

---

## 5. Complete Auto-Assignment Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTO-ASSIGNMENT FLOW FOR OPTUM_RX                        │
└─────────────────────────────────────────────────────────────────────────────┘

                              CLAIMS ARRIVE
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  1. CLAIM AGGREGATION                                                       │
│     - ClaimAggregationService groups claims                                 │
│     - Creates bucket for Optum_Rx payer/payee combination                   │
│     - Bucket status: ACCUMULATING                                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  2. THRESHOLD CHECK                                                         │
│     - ThresholdMonitorService monitors bucket                               │
│     - Checks: claim_count >= 100 OR total_amount >= $50,000                 │
│     - When threshold met → BucketManagerService.handleThresholdMet()        │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  3. PENDING APPROVAL                                                        │
│     - BucketManagerService.transitionToPendingApproval()                    │
│     - Bucket status: PENDING_APPROVAL                                       │
│     - awaitingApprovalSince = current timestamp                             │
│     - Appears in Admin Portal approval queue                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  4. ADMIN APPROVES BUCKET                                                   │
│     - POST /api/v1/buckets/{bucketId}/approve                               │
│     - ApprovalWorkflowService.approveBucket()                               │
│     - Creates BucketApprovalLog record                                      │
│     - Sets approvedBy, approvedAt timestamps                                │
│     - Checks: bucket.isPaymentRequired() → TRUE (workflow_mode = SEPARATE)  │
│     - Bucket remains PENDING_APPROVAL, awaiting check assignment            │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  5. AUTO-ASSIGN CHECK (assignment_mode = AUTO)                              │
│     - POST /api/v1/check-payments/bucket/{bucketId}/assign-auto             │
│     - CheckPaymentService.assignCheckAutomatically()                        │
│                                                                             │
│     5a. Get Next Check from Reservation                                     │
│         - CheckReservationService.getAndReserveNextCheck(payerId)           │
│         - Finds first ACTIVE reservation for Optum_Rx with available checks │
│         - Calls reservation.getNextCheckNumber()                            │
│         - Increments checksUsed counter                                     │
│         - Returns: checkNumber, checkDate, bankName, routing, accountLast4  │
│                                                                             │
│     5b. Create CheckPayment Record                                          │
│         - checkNumber: "CHK100001" (from reservation)                       │
│         - checkAmount: bucket.totalAmount                                   │
│         - checkDate: LocalDate.now()                                        │
│         - status: ASSIGNED                                                  │
│         - assignedBy: "SYSTEM"                                              │
│         - assignedAt: current timestamp                                     │
│                                                                             │
│     5c. Update Bucket                                                       │
│         - bucket.assignCheckPayment(checkPayment)                           │
│         - bucket.paymentStatus: PENDING → ASSIGNED                          │
│         - bucket.paymentDate: check date                                    │
│                                                                             │
│     5d. Create Audit Log                                                    │
│         - CheckAuditLog with action: "AUTO_ASSIGNED"                        │
│         - notes: "Automatically assigned from reservation"                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  6. TRIGGER EDI GENERATION                                                  │
│     - CheckPaymentService detects: bucket.approvedBy != null                │
│     - Calls: BucketManagerService.transitionToGeneration()                  │
│                                                                             │
│     6a. Validate Payment Readiness                                          │
│         - validatePaymentReadiness(bucket)                                  │
│         - Checks: isPaymentRequired() → TRUE                                │
│         - Checks: hasPaymentAssigned() → TRUE                               │
│         - Checks: requireAcknowledgmentBeforeEdi → FALSE (config)           │
│         - Result: ASSIGNED status is sufficient → PASS                      │
│                                                                             │
│     6b. Transition to Generation                                            │
│         - Bucket status: PENDING_APPROVAL → GENERATING                      │
│         - generationStartedAt = current timestamp                           │
│         - Publishes BucketStatusChangeEvent                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  7. EDI 835 FILE GENERATION                                                 │
│     - Edi835GeneratorService listens for BucketStatusChangeEvent            │
│     - Generates EDI 835 file with check payment information                 │
│     - Includes BPR segment with check details                               │
│     - Bucket status: GENERATING → COMPLETED                                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Code Flow & Key Methods

### 6.1 Check Reservation Service
**File**: `edi835-processor/src/main/java/com/healthcare/edi835/service/CheckReservationService.java`

```java
/**
 * Gets next available check number from active reservations.
 * Uses FIFO order (oldest reservation first).
 */
public ReservedCheckInfo getAndReserveNextCheck(UUID payerId) {
    // 1. Find first available reservation for payer
    Optional<CheckReservation> reservationOpt = reservationRepository
            .findFirstAvailableForPayer(payerId);

    // 2. Get next check number (increments checksUsed)
    String checkNumber = reservation.getNextCheckNumber();

    // 3. Save updated reservation
    reservationRepository.save(reservation);

    // 4. Check low stock alert
    if (reservation.isLowStock(lowStockThreshold)) {
        log.warn("Low stock alert: Reservation has {} checks remaining",
                reservation.getChecksRemaining());
    }

    // 5. Return check info
    return ReservedCheckInfo.builder()
            .checkNumber(checkNumber)
            .checkDate(LocalDate.now())
            .bankName(reservation.getBankName())
            .routingNumber(reservation.getRoutingNumber())
            .accountLast4(reservation.getAccountNumberLast4())
            .reservationId(reservation.getId().toString())
            .build();
}
```

### 6.2 Check Payment Service
**File**: `edi835-processor/src/main/java/com/healthcare/edi835/service/CheckPaymentService.java`

```java
/**
 * Assigns check automatically using pre-reserved check numbers.
 */
public CheckPayment assignCheckAutomatically(UUID bucketId, UUID payerId, String systemUser) {
    // 1. Validate bucket exists
    EdiFileBucket bucket = bucketRepository.findById(bucketId).orElseThrow();

    // 2. Get next available check from reservation
    var checkInfo = reservationService.getAndReserveNextCheck(payerId);

    // 3. Create check payment
    CheckPayment checkPayment = CheckPayment.builder()
            .bucket(bucket)
            .checkNumber(checkInfo.getCheckNumber())
            .checkAmount(bucket.getTotalAmount())
            .checkDate(checkInfo.getCheckDate())
            .bankName(checkInfo.getBankName())
            .status(CheckPayment.CheckStatus.ASSIGNED)
            .assignedBy("SYSTEM")
            .assignedAt(LocalDateTime.now())
            .build();

    checkPayment = checkPaymentRepository.save(checkPayment);

    // 4. Update bucket
    bucket.assignCheckPayment(checkPayment);
    bucketRepository.save(bucket);

    // 5. Create audit log
    CheckAuditLog auditLog = CheckAuditLog.logAssignment(checkPayment, bucketId, "SYSTEM", true);
    auditLogRepository.save(auditLog);

    // 6. Trigger EDI generation if bucket is approved
    if (bucket.getApprovedBy() != null) {
        bucketManagerService.transitionToGeneration(bucket);
    }

    return checkPayment;
}
```

### 6.3 Approval Workflow Service
**File**: `edi835-processor/src/main/java/com/healthcare/edi835/service/ApprovalWorkflowService.java`

```java
/**
 * Approves a bucket for EDI generation.
 */
public void approveBucket(UUID bucketId, String approvedBy, String comments) {
    // 1. Validate bucket is pending approval
    EdiFileBucket bucket = bucketRepository.findById(bucketId).orElseThrow();

    // 2. Create approval log
    BucketApprovalLog approvalLog = new BucketApprovalLog(bucket, "APPROVE", approvedBy, comments);
    approvalLogRepository.save(approvalLog);

    // 3. Update bucket approval metadata
    bucket.setApprovedBy(approvedBy);
    bucket.setApprovedAt(LocalDateTime.now());

    // 4. Check if payment is required
    if (!bucket.isPaymentRequired()) {
        // NONE workflow mode - generate EDI immediately
        bucketManagerService.transitionToGeneration(bucket);
    } else {
        // SEPARATE or COMBINED mode - save and wait for check assignment
        bucketRepository.save(bucket);
        log.info("Bucket {} approved, awaiting check payment assignment", bucketId);
    }
}
```

### 6.4 Bucket Manager Service
**File**: `edi835-processor/src/main/java/com/healthcare/edi835/service/BucketManagerService.java`

```java
/**
 * Transitions bucket to GENERATING status.
 */
public void transitionToGeneration(EdiFileBucket bucket) {
    // 1. Validate payment readiness
    validatePaymentReadiness(bucket);

    // 2. Update status
    bucket.markGenerating();
    bucketRepository.save(bucket);

    // 3. Trigger EDI generation
    eventPublisher.publishEvent(new BucketStatusChangeEvent(bucket));
}

/**
 * Validates payment is ready for EDI generation.
 */
private void validatePaymentReadiness(EdiFileBucket bucket) {
    if (!bucket.isPaymentRequired()) {
        return; // No payment needed
    }

    if (!bucket.hasPaymentAssigned()) {
        throw new PaymentRequiredException("Check payment not assigned");
    }

    boolean requireAck = configRepository.getRequireAcknowledgmentBeforeEdi();
    if (!bucket.isPaymentReadyForEdi(requireAck)) {
        throw new PaymentRequiredException("Check payment not acknowledged");
    }
}
```

---

## 7. Database Tables Involved

### 7.1 Configuration Tables

| Table | Purpose |
|-------|---------|
| `payers` | Stores Optum_Rx payer record with UUID |
| `edi_bucketing_rules` | Defines how claims are grouped |
| `edi_generation_thresholds` | Defines when to trigger EDI generation |
| `check_payment_workflow_config` | Defines workflow and assignment mode |
| `check_reservations` | Pre-allocated check number ranges |

### 7.2 Operational Tables

| Table | Purpose |
|-------|---------|
| `edi_file_buckets` | Active claim accumulations |
| `check_payments` | Individual check assignments |
| `check_audit_log` | Audit trail for compliance |
| `bucket_approval_log` | Approval history |

### 7.3 Configuration Settings

| Table | Key | Value | Description |
|-------|-----|-------|-------------|
| `check_payment_config` | `low_stock_alert_threshold` | `50` | Alert when < 50 checks remaining |
| `check_payment_config` | `require_acknowledgment_before_edi` | `false` | ASSIGNED status sufficient |

---

## 8. Status Transitions

### 8.1 Bucket Status Flow

```
ACCUMULATING → PENDING_APPROVAL → GENERATING → COMPLETED
                                      ↓
                                   FAILED
```

### 8.2 Payment Status Flow (on Bucket)

```
PENDING → ASSIGNED → ACKNOWLEDGED → ISSUED
```

### 8.3 Check Payment Status Flow

```
RESERVED → ASSIGNED → ACKNOWLEDGED → ISSUED
              ↓
         VOID / CANCELLED
```

### 8.4 Check Reservation Status Flow

```
ACTIVE → EXHAUSTED (all checks used)
   ↓
CANCELLED (admin action)
```

---

## 9. Example Configuration for Optum_Rx

### 9.1 Complete SQL Setup

```sql
-- ============================================
-- COMPLETE SETUP FOR OPTUM_RX AUTO-ASSIGNMENT
-- ============================================

-- 1. Create Payer
INSERT INTO payers (
    payer_id,
    payer_name,
    isa_sender_id,
    isa_qualifier,
    is_active,
    created_by
) VALUES (
    'OPTUM_RX',
    'Optum Rx Services',
    'OPTUMRX123',
    'ZZ',
    true,
    'admin'
);

-- 2. Create Bucketing Rule
INSERT INTO edi_bucketing_rules (
    rule_name,
    rule_type,
    linked_payer_id,
    description,
    is_active,
    created_by
)
SELECT
    'Optum_Rx_Payer_Payee_Rule',
    'PAYER_PAYEE',
    id,
    'Bucketing rule for Optum Rx claims by payer/payee',
    true,
    'admin'
FROM payers WHERE payer_id = 'OPTUM_RX';

-- 3. Create Generation Threshold
INSERT INTO edi_generation_thresholds (
    threshold_name,
    threshold_type,
    max_claims,
    max_amount,
    time_duration,
    linked_bucketing_rule_id,
    is_active,
    created_by
)
SELECT
    'Optum_Rx_Hybrid_Threshold',
    'HYBRID',
    100,           -- Trigger at 100 claims
    50000.00,      -- OR trigger at $50,000
    'DAILY',       -- OR trigger daily
    id,
    true,
    'admin'
FROM edi_bucketing_rules WHERE rule_name = 'Optum_Rx_Payer_Payee_Rule';

-- 4. Create Check Payment Workflow Config
INSERT INTO check_payment_workflow_config (
    config_name,
    workflow_mode,
    assignment_mode,
    require_acknowledgment,
    linked_threshold_id,
    description,
    is_active,
    created_by
)
SELECT
    'Optum_Rx_Auto_Check_Workflow',
    'SEPARATE',     -- Approve first, then auto-assign check
    'AUTO',         -- System auto-assigns from reservations
    false,          -- No acknowledgment required
    id,
    'Automatic check assignment workflow for Optum Rx high-volume processing',
    true,
    'admin'
FROM edi_generation_thresholds WHERE threshold_name = 'Optum_Rx_Hybrid_Threshold';

-- 5. Create Check Reservations (Multiple Ranges)
-- Range 1: CHK100001 - CHK100500
INSERT INTO check_reservations (
    check_number_start,
    check_number_end,
    total_checks,
    checks_used,
    bank_name,
    routing_number,
    account_number_last4,
    payer_id,
    status,
    created_by
)
SELECT
    'CHK100001',
    'CHK100500',
    500,
    0,
    'First National Bank',
    '021000021',
    '4567',
    id,
    'ACTIVE',
    'admin'
FROM payers WHERE payer_id = 'OPTUM_RX';

-- Range 2: CHK100501 - CHK101000 (backup range)
INSERT INTO check_reservations (
    check_number_start,
    check_number_end,
    total_checks,
    checks_used,
    bank_name,
    routing_number,
    account_number_last4,
    payer_id,
    status,
    created_by
)
SELECT
    'CHK100501',
    'CHK101000',
    500,
    0,
    'First National Bank',
    '021000021',
    '4567',
    id,
    'ACTIVE',
    'admin'
FROM payers WHERE payer_id = 'OPTUM_RX';
```

### 9.2 Verify Configuration

```sql
-- Verify complete setup
SELECT
    p.payer_id,
    p.payer_name,
    br.rule_name,
    gt.threshold_name,
    gt.max_claims,
    gt.max_amount,
    cpwc.workflow_mode,
    cpwc.assignment_mode,
    cpwc.require_acknowledgment,
    cr.check_number_start,
    cr.check_number_end,
    cr.total_checks,
    cr.checks_used,
    cr.status as reservation_status
FROM payers p
LEFT JOIN edi_bucketing_rules br ON br.linked_payer_id = p.id
LEFT JOIN edi_generation_thresholds gt ON gt.linked_bucketing_rule_id = br.id
LEFT JOIN check_payment_workflow_config cpwc ON cpwc.linked_threshold_id = gt.id
LEFT JOIN check_reservations cr ON cr.payer_id = p.id
WHERE p.payer_id = 'OPTUM_RX';
```

---

## 10. Troubleshooting

### 10.1 "No active reservations with available checks"

**Cause**: No ACTIVE reservations exist for the payer, or all reservations are EXHAUSTED.

**Solution**:
```sql
-- Check reservation status
SELECT * FROM check_reservations
WHERE payer_id = (SELECT id FROM payers WHERE payer_id = 'OPTUM_RX');

-- Add new reservation if needed
INSERT INTO check_reservations (...) VALUES (...);
```

### 10.2 "Payer not found" error

**Cause**: The payer UUID doesn't exist or is incorrect.

**Solution**:
```sql
-- Verify payer exists
SELECT id, payer_id FROM payers WHERE payer_id = 'OPTUM_RX';

-- Ensure reservation uses correct payer UUID
UPDATE check_reservations
SET payer_id = (SELECT id FROM payers WHERE payer_id = 'OPTUM_RX')
WHERE check_number_start = 'CHK100001';
```

### 10.3 Check not auto-assigning after approval

**Cause**: Workflow config may be missing or set to NONE mode.

**Solution**:
```sql
-- Check workflow config
SELECT * FROM check_payment_workflow_config cpwc
JOIN edi_generation_thresholds gt ON cpwc.linked_threshold_id = gt.id
JOIN edi_bucketing_rules br ON gt.linked_bucketing_rule_id = br.id
WHERE br.rule_name LIKE '%Optum%';

-- Verify workflow_mode is SEPARATE or COMBINED
-- Verify assignment_mode is AUTO or BOTH
```

### 10.4 Low Stock Alert Configuration

```sql
-- Set low stock alert threshold
UPDATE check_payment_config
SET config_value = '100'  -- Alert when < 100 checks remaining
WHERE config_key = 'low_stock_alert_threshold';

-- Configure alert emails
UPDATE check_payment_config
SET config_value = 'finance@company.com,admin@company.com'
WHERE config_key = 'low_stock_alert_emails';
```

---

## API Endpoints Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/buckets/{id}/approve` | POST | Approve bucket for generation |
| `/api/v1/check-payments/bucket/{id}/assign-auto` | POST | Auto-assign check to bucket |
| `/api/v1/check-payments/bucket/{id}/assign` | POST | Manually assign check |
| `/api/v1/check-reservations` | GET | List all reservations |
| `/api/v1/check-reservations` | POST | Create new reservation |
| `/api/v1/check-reservations/payer/{id}` | GET | Get reservations for payer |
| `/api/v1/check-reservations/low-stock` | GET | Get low stock reservations |

---

## Summary

For **Optum_Rx** auto-assignment to work:

1. **Payer record** must exist with a valid UUID
2. **Bucketing rule** must be linked to the payer
3. **Generation threshold** must be linked to the bucketing rule
4. **Workflow config** must have:
   - `workflow_mode`: SEPARATE or COMBINED
   - `assignment_mode`: AUTO or BOTH
   - `linked_threshold_id`: Points to the threshold
5. **Check reservations** must exist with:
   - `payer_id`: Optum_Rx UUID
   - `status`: ACTIVE
   - Available checks (checks_used < total_checks)

When all configured correctly, the flow is:
1. Claims accumulate → Bucket created
2. Threshold met → PENDING_APPROVAL
3. Admin approves → Awaits check
4. System auto-assigns check from reservation → EDI generation triggered
5. EDI 835 file generated with check payment info
