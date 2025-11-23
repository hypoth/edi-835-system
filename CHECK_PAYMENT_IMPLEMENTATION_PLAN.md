# Check Payment Implementation Plan - Phase 1

## Overview

This document outlines the implementation plan for adding check payment support to the EDI 835 system. The system will support two workflows:
1. **Manual Approval**: User enters check details during approval
2. **Auto Approval**: System uses pre-reserved check numbers

## Current State Analysis

### Existing Components
- ✅ `PaymentMethod` entity with `CHECK` and `EFT` types
- ✅ `PaymentInfo` model for EDI 835 BPR segment
- ✅ `EdiFileBucket` entity for claim aggregation
- ✅ Approval workflow (manual/auto/hybrid)

### Missing Components
- ❌ Check number management/reservation
- ❌ Payment assignment to buckets
- ❌ Check acknowledgment workflow
- ❌ Audit trail for check usage
- ❌ Payment details in EDI file generation

---

## Implementation Phases

### Phase 1: Database Schema & Entities

#### 1.1 New Tables

**`check_payments` Table**
```sql
CREATE TABLE IF NOT EXISTS check_payments (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    bucket_id TEXT NOT NULL REFERENCES edi_file_buckets(bucket_id) ON DELETE CASCADE,
    check_number TEXT UNIQUE NOT NULL,
    check_amount REAL NOT NULL,
    check_date DATE NOT NULL,
    bank_name TEXT,
    routing_number TEXT,
    account_number_last4 TEXT,  -- Last 4 digits for reference

    -- Check status
    status TEXT NOT NULL DEFAULT 'RESERVED'
        CHECK (status IN ('RESERVED', 'ASSIGNED', 'ACKNOWLEDGED', 'ISSUED', 'VOID', 'CANCELLED')),

    -- Assignment tracking
    assigned_by TEXT,  -- User who assigned (manual) or 'SYSTEM' (auto)
    assigned_at TIMESTAMP,
    acknowledged_by TEXT,  -- User who acknowledged the amount
    acknowledged_at TIMESTAMP,

    -- Payment method reference
    payment_method_id TEXT REFERENCES payment_methods(id),

    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,
    updated_by TEXT,

    -- Constraints
    UNIQUE(bucket_id)  -- One check per bucket
);

CREATE INDEX IF NOT EXISTS idx_check_payments_bucket ON check_payments(bucket_id);
CREATE INDEX IF NOT EXISTS idx_check_payments_status ON check_payments(status);
CREATE INDEX IF NOT EXISTS idx_check_payments_check_number ON check_payments(check_number);
```

**`check_reservations` Table** (for auto-approval pre-allocated checks)
```sql
CREATE TABLE IF NOT EXISTS check_reservations (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    check_number_start TEXT NOT NULL,
    check_number_end TEXT NOT NULL,
    total_checks INTEGER NOT NULL,
    checks_used INTEGER DEFAULT 0,

    -- Bank details
    bank_name TEXT NOT NULL,
    routing_number TEXT,
    account_number_last4 TEXT,
    payment_method_id TEXT REFERENCES payment_methods(id),

    -- Range status
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'EXHAUSTED', 'CANCELLED')),

    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,

    -- Ensure no overlapping ranges
    UNIQUE(check_number_start, check_number_end)
);

CREATE INDEX IF NOT EXISTS idx_check_reservations_status ON check_reservations(status);
```

**`check_audit_log` Table** (for full traceability)
```sql
CREATE TABLE IF NOT EXISTS check_audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    check_payment_id TEXT REFERENCES check_payments(id) ON DELETE CASCADE,
    check_number TEXT NOT NULL,
    action TEXT NOT NULL,  -- RESERVED, ASSIGNED, ACKNOWLEDGED, ISSUED, VOID, CANCELLED
    bucket_id TEXT,
    amount REAL,
    performed_by TEXT,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_check_audit_check ON check_audit_log(check_payment_id);
CREATE INDEX IF NOT EXISTS idx_check_audit_bucket ON check_audit_log(bucket_id);
CREATE INDEX IF NOT EXISTS idx_check_audit_date ON check_audit_log(created_at DESC);
```

**`check_payment_config` Table** (configuration settings)
```sql
CREATE TABLE IF NOT EXISTS check_payment_config (
    id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
    config_key TEXT UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    value_type TEXT DEFAULT 'STRING' CHECK (value_type IN ('STRING', 'INTEGER', 'BOOLEAN', 'EMAIL')),
    is_active INTEGER DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by TEXT
);

-- Default configuration values
INSERT INTO check_payment_config (config_key, config_value, description, value_type) VALUES
('void_time_limit_hours', '72', 'Time limit in hours for voiding a check after issuance', 'INTEGER'),
('low_stock_alert_threshold', '50', 'Alert when check reservation has fewer than this many checks remaining', 'INTEGER'),
('low_stock_alert_emails', 'finance@example.com,admin@example.com', 'Comma-separated email addresses for low stock alerts', 'EMAIL'),
('void_authorized_roles', 'FINANCIAL_ADMIN,SYSTEM_ADMIN', 'Roles authorized to void checks', 'STRING'),
('default_check_range_size', '100', 'Default number of checks in a reservation range', 'INTEGER'),
('require_acknowledgment_before_edi', 'false', 'Whether check acknowledgment is required before EDI generation', 'BOOLEAN');

CREATE INDEX IF NOT EXISTS idx_check_payment_config_key ON check_payment_config(config_key);
```

#### 1.2 Schema Modifications

**Add to `edi_file_buckets` table**:
```sql
ALTER TABLE edi_file_buckets ADD COLUMN payment_status TEXT DEFAULT 'PENDING'
    CHECK (payment_status IN ('PENDING', 'ASSIGNED', 'ACKNOWLEDGED', 'ISSUED'));
ALTER TABLE edi_file_buckets ADD COLUMN payment_required INTEGER DEFAULT 1 CHECK(payment_required IN (0, 1));
ALTER TABLE edi_file_buckets ADD COLUMN check_number TEXT;
ALTER TABLE edi_file_buckets ADD COLUMN payment_date DATE;
```

---

### Phase 2: Backend Entities & Models

#### 2.1 New Entity Classes

**`CheckPayment.java`**
```java
@Entity
@Table(name = "check_payments")
public class CheckPayment {
    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bucket_id", nullable = false)
    private EdiFileBucket bucket;

    @Column(name = "check_number", unique = true, nullable = false)
    private String checkNumber;

    @Column(name = "check_amount", nullable = false)
    private BigDecimal checkAmount;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "routing_number")
    private String routingNumber;

    @Column(name = "account_number_last4")
    private String accountNumberLast4;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CheckStatus status = CheckStatus.RESERVED;

    @Column(name = "assigned_by")
    private String assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public enum CheckStatus {
        RESERVED,      // Check number reserved but not assigned
        ASSIGNED,      // Assigned to a bucket
        ACKNOWLEDGED,  // Amount acknowledged by user
        ISSUED,        // Check physically issued/mailed
        VOID,          // Check voided
        CANCELLED      // Check cancelled
    }
}
```

**`CheckReservation.java`**
```java
@Entity
@Table(name = "check_reservations")
public class CheckReservation {
    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "check_number_start", nullable = false)
    private String checkNumberStart;

    @Column(name = "check_number_end", nullable = false)
    private String checkNumberEnd;

    @Column(name = "total_checks", nullable = false)
    private Integer totalChecks;

    @Column(name = "checks_used")
    private Integer checksUsed = 0;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "routing_number")
    private String routingNumber;

    @Column(name = "account_number_last4")
    private String accountNumberLast4;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;

    public enum ReservationStatus {
        ACTIVE,     // Available for use
        EXHAUSTED,  // All checks used
        CANCELLED   // Reservation cancelled
    }

    // Helper methods
    public String getNextCheckNumber() {
        // Logic to generate next sequential check number
    }

    public boolean hasAvailableChecks() {
        return checksUsed < totalChecks && status == ReservationStatus.ACTIVE;
    }
}
```

**`CheckAuditLog.java`**
```java
@Entity
@Table(name = "check_audit_log")
public class CheckAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_payment_id")
    private CheckPayment checkPayment;

    @Column(name = "check_number", nullable = false)
    private String checkNumber;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "bucket_id")
    private String bucketId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

#### 2.2 DTO Classes

**`CheckPaymentDTO.java`**
```java
@Data
@Builder
public class CheckPaymentDTO {
    private String id;
    private String bucketId;
    private String checkNumber;
    private BigDecimal checkAmount;
    private LocalDate checkDate;
    private String bankName;
    private String routingNumber;
    private String accountNumberLast4;
    private String status;
    private String assignedBy;
    private LocalDateTime assignedAt;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
}
```

**`CheckReservationDTO.java`**
```java
@Data
@Builder
public class CheckReservationDTO {
    private String id;
    private String checkNumberStart;
    private String checkNumberEnd;
    private Integer totalChecks;
    private Integer checksUsed;
    private Integer checksRemaining;
    private String bankName;
    private String routingNumber;
    private String accountNumberLast4;
    private String status;
    private LocalDateTime createdAt;
    private String createdBy;
}
```

---

### Phase 3: Service Layer

#### 3.1 CheckPaymentService

```java
@Service
public class CheckPaymentService {

    /**
     * Manual check assignment - user enters check details during approval
     */
    public CheckPayment assignCheckManually(
        UUID bucketId,
        String checkNumber,
        LocalDate checkDate,
        String bankName,
        String assignedBy
    );

    /**
     * Auto check assignment - system uses pre-reserved check number
     */
    public CheckPayment assignCheckAutomatically(UUID bucketId);

    /**
     * User acknowledges check amount before issuance
     */
    public CheckPayment acknowledgeCheck(UUID checkPaymentId, String acknowledgedBy);

    /**
     * Mark check as issued (physically mailed/delivered)
     */
    public CheckPayment markCheckIssued(UUID checkPaymentId);

    /**
     * Void a check
     */
    public CheckPayment voidCheck(UUID checkPaymentId, String reason, String voidedBy);

    /**
     * Get check payment for a bucket
     */
    public Optional<CheckPayment> getCheckPaymentForBucket(UUID bucketId);

    /**
     * Get all pending acknowledgments
     */
    public List<CheckPaymentDTO> getPendingAcknowledgments();

    /**
     * Get check audit trail
     */
    public List<CheckAuditLog> getCheckAuditTrail(UUID checkPaymentId);
}
```

#### 3.2 CheckReservationService

```java
@Service
public class CheckReservationService {

    /**
     * Create new check reservation range
     */
    public CheckReservation createReservation(
        String checkNumberStart,
        String checkNumberEnd,
        String bankName,
        String routingNumber,
        String accountLast4,
        String createdBy
    );

    /**
     * Get next available check number from active reservations
     */
    public Optional<String> getNextAvailableCheckNumber();

    /**
     * Mark check as used in reservation
     */
    public void markCheckUsed(String checkNumber);

    /**
     * Get all active reservations
     */
    public List<CheckReservationDTO> getActiveReservations();

    /**
     * Cancel a reservation
     */
    public void cancelReservation(UUID reservationId, String cancelledBy);

    /**
     * Get reservation statistics
     */
    public ReservationStats getReservationStats();
}
```

#### 3.3 Update Existing Services

**`BucketManagerService.java` - Add payment checks**:
```java
// Before moving to GENERATING, ensure payment is assigned
public void transitionToGenerating(UUID bucketId) {
    EdiFileBucket bucket = getBucket(bucketId);

    // Check if payment is required and assigned
    if (bucket.getPaymentRequired() && bucket.getCheckNumber() == null) {
        throw new PaymentRequiredException("Payment must be assigned before generation");
    }

    bucket.markGenerating();
    save(bucket);
}
```

**`Edi835GeneratorService.java` - Include check info**:
```java
private PaymentInfo createPaymentInfo(EdiFileBucket bucket) {
    CheckPayment checkPayment = checkPaymentService.getCheckPaymentForBucket(bucket.getBucketId())
        .orElseThrow(() -> new PaymentNotFoundException("No payment found for bucket"));

    return PaymentInfo.builder()
        .transactionHandlingCode("C")  // Payment
        .totalActualProviderPaymentAmount(bucket.getTotalAmount())
        .creditDebitFlag("C")  // Credit
        .paymentMethodCode("CHK")  // Check
        .paymentFormatCode("CCP")
        .checkOrEftTraceNumber(checkPayment.getCheckNumber())
        .paymentEffectiveDate(checkPayment.getCheckDate())
        .originatingCompanyIdentifier(bucket.getPayerId())
        .build();
}
```

---

### Phase 4: REST API Endpoints

#### 4.1 Check Payment APIs

**`CheckPaymentController.java`**
```java
@RestController
@RequestMapping("/api/v1/check-payments")
public class CheckPaymentController {

    // Manual check assignment
    @PostMapping("/buckets/{bucketId}/assign-manual")
    public ResponseEntity<CheckPaymentDTO> assignCheckManually(
        @PathVariable UUID bucketId,
        @RequestBody ManualCheckRequest request
    );

    // Get check payment for bucket
    @GetMapping("/buckets/{bucketId}")
    public ResponseEntity<CheckPaymentDTO> getCheckPaymentForBucket(@PathVariable UUID bucketId);

    // Acknowledge check amount
    @PostMapping("/{checkPaymentId}/acknowledge")
    public ResponseEntity<CheckPaymentDTO> acknowledgeCheck(
        @PathVariable UUID checkPaymentId,
        @RequestBody AcknowledgeCheckRequest request
    );

    // Get pending acknowledgments
    @GetMapping("/pending-acknowledgments")
    public ResponseEntity<List<CheckPaymentDTO>> getPendingAcknowledgments();

    // Mark check as issued
    @PostMapping("/{checkPaymentId}/issue")
    public ResponseEntity<CheckPaymentDTO> markCheckIssued(@PathVariable UUID checkPaymentId);

    // Void check
    @PostMapping("/{checkPaymentId}/void")
    public ResponseEntity<CheckPaymentDTO> voidCheck(
        @PathVariable UUID checkPaymentId,
        @RequestBody VoidCheckRequest request
    );

    // Get audit trail
    @GetMapping("/{checkPaymentId}/audit-trail")
    public ResponseEntity<List<CheckAuditLogDTO>> getAuditTrail(@PathVariable UUID checkPaymentId);
}
```

#### 4.2 Check Reservation APIs

**`CheckReservationController.java`**
```java
@RestController
@RequestMapping("/api/v1/check-reservations")
public class CheckReservationController {

    // Create new reservation
    @PostMapping
    public ResponseEntity<CheckReservationDTO> createReservation(
        @RequestBody CreateReservationRequest request
    );

    // Get all reservations
    @GetMapping
    public ResponseEntity<List<CheckReservationDTO>> getAllReservations(
        @RequestParam(required = false) String status
    );

    // Get reservation stats
    @GetMapping("/stats")
    public ResponseEntity<ReservationStats> getReservationStats();

    // Cancel reservation
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<Void> cancelReservation(@PathVariable UUID reservationId);
}
```

---

### Phase 5: Frontend Implementation

#### 5.1 Manual Check Entry Form (during approval)

**Location**: `edi835-admin-portal/src/components/approvals/CheckPaymentForm.tsx`

**Features**:
- Check number input
- Check date picker
- Bank name/routing number
- Validation:
  - Check number uniqueness
  - Valid date format
  - Required fields

**Integration**:
- Embedded in approval dialog
- Appears when approving bucket
- Required before approval can proceed

#### 5.2 Check Reservation Management

**Location**: `edi835-admin-portal/src/pages/config/CheckReservations.tsx`

**Features**:
- Add new check range
  - Start check number
  - End check number
  - Bank details
  - Calculate total checks
- View active reservations
  - Show used vs remaining
  - Progress bar
  - Status indicators
- Cancel reservations
- Statistics dashboard

#### 5.3 Check Acknowledgment Screen

**Location**: `edi835-admin-portal/src/pages/checks/CheckAcknowledgments.tsx`

**Features**:
- List of pending acknowledgments
- For each check:
  - Bucket ID and details
  - Check number
  - Amount to be written
  - Payee information
- Bulk acknowledgment
- Individual acknowledgment
- Print check list for reference

#### 5.4 Check Audit Trail

**Location**: `edi835-admin-portal/src/pages/checks/CheckAuditTrail.tsx`

**Features**:
- Search by:
  - Check number
  - Bucket ID
  - Date range
  - User
- Timeline view of check lifecycle
- Export to CSV/PDF
- Filter by status/action

---

### Phase 6: EDI File Integration

#### 6.1 BPR Segment (Payment Information)

```
BPR*C*[AMOUNT]*C*CHK*CCP*[ROUTING]*DA*[ACCOUNT]*[PAYER_ID]***[PAYER_COMPANY_ID]*******[CHECK_DATE]*[CHECK_NUMBER]*
```

Example:
```
BPR*C*1234.56*C*CHK*CCP*123456789*DA*9876*BCBS001***BLUECROSS*******20251120*000123456*
```

#### 6.2 TRN Segment (Trace Number)

```
TRN*1*[CHECK_NUMBER]*[PAYER_TAX_ID]*
```

Example:
```
TRN*1*000123456*123456789*
```

---

## Implementation Timeline

### Week 1: Database & Entities
- [ ] Create new tables (check_payments, check_reservations, check_audit_log)
- [ ] Modify edi_file_buckets table
- [ ] Create entity classes
- [ ] Create repositories
- [ ] Write unit tests

### Week 2: Service Layer
- [ ] Implement CheckPaymentService
- [ ] Implement CheckReservationService
- [ ] Update BucketManagerService
- [ ] Update Edi835GeneratorService
- [ ] Write service tests

### Week 3: Backend APIs
- [ ] Implement CheckPaymentController
- [ ] Implement CheckReservationController
- [ ] API documentation (Swagger)
- [ ] Integration tests

### Week 4: Frontend - Manual Flow
- [ ] CheckPaymentForm component
- [ ] Integrate with approval workflow
- [ ] Validation and error handling
- [ ] Testing

### Week 5: Frontend - Auto Flow
- [ ] CheckReservations management page
- [ ] CheckAcknowledgments page
- [ ] Testing

### Week 6: Frontend - Audit & Polish
- [ ] CheckAuditTrail page
- [ ] Dashboard widgets
- [ ] E2E testing
- [ ] Documentation

---

## Testing Strategy

### Unit Tests
- Entity validation
- Service logic (manual/auto assignment)
- Check number sequencing
- Audit log creation

### Integration Tests
- API endpoints
- Database transactions
- EDI file generation with check info

### E2E Tests
- Manual approval with check entry
- Auto approval with reserved checks
- Check acknowledgment workflow
- Audit trail verification

### Test Scenarios
1. Happy path: Manual check assignment
2. Happy path: Auto check assignment with reservations
3. Error: Duplicate check number
4. Error: Exhausted reservations
5. Error: Missing payment for generation
6. Check void/cancellation
7. Multi-user concurrent assignments

---

## Security Considerations

1. **Access Control**:
   - Only authorized users can assign checks
   - Role-based permissions for reservations
   - Audit all check-related actions

2. **Data Protection**:
   - Store only last 4 digits of account numbers
   - Encrypt sensitive banking information
   - Secure API endpoints

3. **Validation**:
   - Check number uniqueness across system
   - Prevent duplicate assignments
   - Validate check number format

---

## Rollback Plan

If issues arise:
1. Disable check payment requirement temporarily
2. Roll back database migrations
3. Revert to payment-optional mode
4. Fix issues in lower environment
5. Re-deploy with fixes

---

## Success Metrics

- [ ] 100% of approved buckets have payment assigned
- [ ] Zero duplicate check numbers
- [ ] Full audit trail for all checks
- [ ] < 2 minutes to assign check (manual)
- [ ] < 10 seconds to assign check (auto)
- [ ] 100% EDI files include correct check info

---

## Future Enhancements (Phase 2+)

- EFT/ACH payment support
- Check printing integration
- Automated check mailing
- Check reconciliation
- Payment status tracking (cleared, bounced)
- Multi-bank support
- Check stock management alerts

---

## Questions & Decisions - ANSWERED ✅

1. **Check Number Format**: ✅ Alphanumeric, variable length (e.g., CHK000123, CHK0001)
2. **Bank Account Access**: ✅ Multiple bank accounts (per payer)
3. **Check Void Policy**: ✅ FINANCIAL_ADMIN role, configurable time limit
4. **Reservation Strategy**: ✅ Manual ranges (typical size: 100 checks)
5. **Acknowledgment Requirement**: ✅ Post-generation acknowledgment allowed (not mandatory before generation)
6. **Check Stock Alerts**: ✅ Configurable threshold (default: 50), configurable email recipients

---

**Document Version**: 1.0
**Last Updated**: 2025-11-20
**Author**: EDI 835 Development Team
