# EDI 835 Payment Integration - Implementation Plan

## Document Information
- **Project**: EDI 835 Remittance Processing System - Payment Integration
- **Version**: 1.0
- **Last Updated**: 2025
- **Author**: Development Team

---

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Phase 1: Check Payment Implementation](#phase-1-check-payment-implementation)
4. [Phase 2: NACH Payment Implementation](#phase-2-nach-payment-implementation)
5. [Testing Strategy](#testing-strategy)
6. [Deployment Plan](#deployment-plan)
7. [Risk Mitigation](#risk-mitigation)

---

## Overview

### Project Goals
Integrate payment processing with EDI 835 remittance generation to ensure:
1. Payment references (check numbers/NACH TRN) are allocated before EDI generation
2. EDI 835 files contain accurate payment reference information
3. Payments are processed and tracked through to settlement
4. Complete audit trail from claim to payment to reconciliation

### Implementation Approach
- **Phase 1**: Check Payment Implementation (6-8 weeks)
- **Phase 2**: NACH Payment Implementation (4-6 weeks)

### Success Criteria
- ✅ EDI 835 files contain correct check numbers before printing
- ✅ Checks printed with pre-allocated numbers matching EDI
- ✅ 100% reconciliation between EDI files and physical checks
- ✅ Complete audit trail for all payment transactions
- ✅ Automated status tracking from printing to encashment

---

## Prerequisites

### Technical Requirements
- [x] Java 17+ installed
- [x] Spring Boot 3.x application running
- [x] PostgreSQL 14+ database
- [x] Azure Cosmos DB configured
- [x] React 18+ frontend deployed
- [x] StAEDI library integrated for EDI generation

### Business Requirements
- [x] Pharmacy payment configurations defined
- [x] Check printing service/vendor identified
- [x] Courier service for check delivery selected
- [x] Check stock inventory management process established
- [x] Bank account(s) for issuing checks configured

### Access & Permissions
- [x] Database admin access for schema changes
- [x] Check printing service API credentials
- [x] Courier service API credentials
- [x] SFTP/AS2 credentials for EDI delivery

---

# Phase 1: Check Payment Implementation

**Duration**: 6-8 weeks  
**Priority**: High  
**Dependencies**: Core EDI 835 generation system operational

---

## Week 1-2: Database Schema & Foundation

### Task 1.1: Database Schema Implementation
**Owner**: Backend Team  
**Duration**: 3 days  
**Priority**: Critical

#### Deliverables
1. Create database migration scripts
2. Implement all tables with proper indexes
3. Set up foreign key constraints
4. Create database documentation

#### SQL Scripts to Implement

```sql
-- File: V1.1__payment_foundation.sql

-- Payment configuration per pharmacy
CREATE TABLE pharmacy_payment_config (
    id BIGSERIAL PRIMARY KEY,
    pharmacy_id VARCHAR(50) NOT NULL UNIQUE,
    pharmacy_name VARCHAR(200) NOT NULL,
    payment_method VARCHAR(20) NOT NULL DEFAULT 'CHECK',
    
    -- Check Details
    check_enabled BOOLEAN DEFAULT true,
    check_payee_name VARCHAR(200),
    check_delivery_address TEXT,
    check_delivery_city VARCHAR(100),
    check_delivery_state VARCHAR(50),
    check_delivery_pincode VARCHAR(10),
    check_bank_account_id VARCHAR(50),
    
    -- Thresholds
    min_payment_amount DECIMAL(15,2) DEFAULT 0.00,
    
    -- Scheduling
    payment_schedule VARCHAR(20) DEFAULT 'IMMEDIATE',
    
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pharmacy_payment_config_pharmacy_id 
    ON pharmacy_payment_config(pharmacy_id);
CREATE INDEX idx_pharmacy_payment_config_active 
    ON pharmacy_payment_config(is_active);

-- Check number sequence management
CREATE TABLE check_number_sequences (
    id BIGSERIAL PRIMARY KEY,
    bank_account_id VARCHAR(50) NOT NULL,
    current_check_number BIGINT NOT NULL,
    check_prefix VARCHAR(10),
    check_suffix VARCHAR(10),
    last_issued_at TIMESTAMP,
    
    CONSTRAINT uq_bank_account UNIQUE(bank_account_id)
);

-- Payment reference allocation
CREATE TABLE payment_reference_allocation (
    id BIGSERIAL PRIMARY KEY,
    reference_type VARCHAR(20) NOT NULL,
    reference_number VARCHAR(50) UNIQUE NOT NULL,
    
    allocated_to_bucket_id BIGINT REFERENCES edi_file_buckets(bucket_id),
    allocated_to_pharmacy_id VARCHAR(50),
    allocation_status VARCHAR(30) NOT NULL,
    
    payment_transaction_id BIGINT,
    edi_file_id BIGINT,
    
    reserved_at TIMESTAMP,
    allocated_at TIMESTAMP,
    used_at TIMESTAMP,
    expires_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_reference_type CHECK (reference_type IN ('CHECK_NUMBER', 'NACH_TRN')),
    CONSTRAINT chk_allocation_status CHECK (allocation_status IN 
        ('RESERVED', 'ALLOCATED', 'USED', 'CANCELLED'))
);

CREATE INDEX idx_payment_ref_status ON payment_reference_allocation(allocation_status);
CREATE INDEX idx_payment_ref_bucket ON payment_reference_allocation(allocated_to_bucket_id);
CREATE INDEX idx_payment_ref_number ON payment_reference_allocation(reference_number);

-- Payment batches
CREATE TABLE payment_batches (
    id BIGSERIAL PRIMARY KEY,
    batch_number VARCHAR(50) UNIQUE NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    total_claims INT NOT NULL,
    pharmacy_count INT NOT NULL,
    
    edi_bucket_id BIGINT REFERENCES edi_file_buckets(bucket_id),
    edi_file_id BIGINT,
    
    status VARCHAR(30) NOT NULL,
    
    gateway_batch_id VARCHAR(100),
    submission_timestamp TIMESTAMP,
    completion_timestamp TIMESTAMP,
    
    expected_settlement_date DATE,
    actual_settlement_date DATE,
    reconciliation_status VARCHAR(30),
    
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('CHECK', 'NACH')),
    CONSTRAINT chk_batch_status CHECK (status IN 
        ('PENDING', 'SUBMITTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'PARTIALLY_FAILED'))
);

CREATE INDEX idx_payment_batch_status ON payment_batches(status);
CREATE INDEX idx_payment_batch_bucket ON payment_batches(edi_bucket_id);

-- Payment transactions
CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_number VARCHAR(50) UNIQUE NOT NULL,
    batch_id BIGINT REFERENCES payment_batches(id),
    
    pharmacy_id VARCHAR(50) NOT NULL,
    pharmacy_name VARCHAR(200),
    
    payment_method VARCHAR(20) NOT NULL,
    payment_amount DECIMAL(15,2) NOT NULL,
    claim_count INT NOT NULL,
    
    -- Check specific fields
    check_number VARCHAR(50),
    check_date DATE,
    check_amount DECIMAL(15,2),
    check_status VARCHAR(30),
    check_tracking_number VARCHAR(100),
    check_payee_name VARCHAR(200),
    check_delivery_address TEXT,
    
    status VARCHAR(30) NOT NULL,
    failure_reason TEXT,
    retry_count INT DEFAULT 0,
    
    settlement_date DATE,
    settlement_status VARCHAR(30),
    
    edi_file_id BIGINT,
    edi_bucket_id BIGINT,
    remittance_reference VARCHAR(100),
    
    payment_reference_allocated_id BIGINT REFERENCES payment_reference_allocation(id),
    payment_reference_number VARCHAR(50),
    payment_reference_reserved_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_txn_payment_method CHECK (payment_method IN ('CHECK', 'NACH')),
    CONSTRAINT chk_txn_status CHECK (status IN 
        ('PENDING', 'INITIATED', 'SUCCESS', 'FAILED', 'REVERSED')),
    CONSTRAINT chk_check_status CHECK (check_status IS NULL OR check_status IN 
        ('PRINTED', 'DISPATCHED', 'DELIVERED', 'ENCASHED', 'CANCELLED', 'STOPPED'))
);

CREATE INDEX idx_payment_txn_batch ON payment_transactions(batch_id);
CREATE INDEX idx_payment_txn_pharmacy ON payment_transactions(pharmacy_id);
CREATE INDEX idx_payment_txn_status ON payment_transactions(status);
CREATE INDEX idx_payment_txn_check_number ON payment_transactions(check_number);

-- Payment to claims mapping
CREATE TABLE payment_claim_mapping (
    id BIGSERIAL PRIMARY KEY,
    payment_transaction_id BIGINT REFERENCES payment_transactions(id),
    claim_id VARCHAR(100) NOT NULL,
    claim_amount DECIMAL(15,2) NOT NULL,
    paid_amount DECIMAL(15,2) NOT NULL,
    adjustment_amount DECIMAL(15,2),
    patient_responsibility DECIMAL(15,2)
);

CREATE INDEX idx_payment_claim_mapping_txn ON payment_claim_mapping(payment_transaction_id);
CREATE INDEX idx_payment_claim_mapping_claim ON payment_claim_mapping(claim_id);

-- Payment status events (event sourcing)
CREATE TABLE payment_status_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    payment_transaction_id BIGINT REFERENCES payment_transactions(id),
    batch_id BIGINT REFERENCES payment_batches(id),
    
    event_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(30),
    new_status VARCHAR(30),
    
    event_data JSONB,
    error_details TEXT,
    
    triggered_by VARCHAR(100),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_events_txn ON payment_status_events(payment_transaction_id);
CREATE INDEX idx_payment_events_type ON payment_status_events(event_type);
CREATE INDEX idx_payment_events_timestamp ON payment_status_events(timestamp);

-- Bucket to payment allocation tracking
CREATE TABLE bucket_payment_allocation (
    id BIGSERIAL PRIMARY KEY,
    bucket_id BIGINT REFERENCES edi_file_buckets(bucket_id),
    payment_batch_id BIGINT REFERENCES payment_batches(id),
    allocation_status VARCHAR(30) NOT NULL,
    
    references_reserved_at TIMESTAMP,
    edi_generated_at TIMESTAMP,
    payments_submitted_at TIMESTAMP,
    completed_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_bucket_allocation_status CHECK (allocation_status IN 
        ('REFERENCES_RESERVED', 'EDI_GENERATED', 'PAYMENTS_SUBMITTED', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_bucket_payment_bucket ON bucket_payment_allocation(bucket_id);
CREATE INDEX idx_bucket_payment_status ON bucket_payment_allocation(allocation_status);

-- Payment reconciliation
CREATE TABLE payment_reconciliation (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_date DATE NOT NULL,
    batch_id BIGINT REFERENCES payment_batches(id),
    
    expected_amount DECIMAL(15,2) NOT NULL,
    actual_amount DECIMAL(15,2),
    difference DECIMAL(15,2),
    
    status VARCHAR(30) NOT NULL,
    
    bank_statement_ref VARCHAR(100),
    bank_statement_date DATE,
    bank_statement_amount DECIMAL(15,2),
    
    remarks TEXT,
    reconciled_by VARCHAR(100),
    reconciled_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_reconciliation_status CHECK (status IN 
        ('MATCHED', 'MISMATCHED', 'PENDING_REVIEW'))
);

CREATE INDEX idx_payment_reconciliation_batch ON payment_reconciliation(batch_id);
CREATE INDEX idx_payment_reconciliation_date ON payment_reconciliation(reconciliation_date);
```

#### Testing Checklist
- [ ] All tables created successfully
- [ ] Foreign keys properly established
- [ ] Indexes created and optimized
- [ ] Check constraints validated
- [ ] Sample data insertion successful
- [ ] Rollback script tested

---

### Task 1.2: Entity Classes & Repositories
**Owner**: Backend Team  
**Duration**: 3 days  
**Priority**: Critical

#### Deliverables
Create JPA entity classes and repositories for all new tables.

#### Files to Create

```
src/main/java/com/healthcare/edi835/
├── model/
│   ├── PharmacyPaymentConfig.java
│   ├── CheckNumberSequence.java
│   ├── PaymentReferenceAllocation.java
│   ├── PaymentBatch.java
│   ├── PaymentTransaction.java
│   ├── PaymentClaimMapping.java
│   ├── PaymentStatusEvent.java
│   ├── BucketPaymentAllocation.java
│   └── PaymentReconciliation.java
├── model/enums/
│   ├── PaymentMethod.java
│   ├── AllocationStatus.java
│   ├── PaymentBatchStatus.java
│   ├── PaymentTransactionStatus.java
│   ├── CheckStatus.java
│   └── ReferenceType.java
└── repository/
    ├── PharmacyPaymentConfigRepository.java
    ├── CheckNumberSequenceRepository.java
    ├── PaymentReferenceAllocationRepository.java
    ├── PaymentBatchRepository.java
    ├── PaymentTransactionRepository.java
    ├── PaymentClaimMappingRepository.java
    ├── PaymentStatusEventRepository.java
    ├── BucketPaymentAllocationRepository.java
    └── PaymentReconciliationRepository.java
```

#### Example Entity Implementation

```java
// File: src/main/java/com/healthcare/edi835/model/PaymentTransaction.java

package com.healthcare.edi835.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_number", unique = true, nullable = false)
    private String transactionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private PaymentBatch batch;

    @Column(name = "pharmacy_id", nullable = false)
    private String pharmacyId;

    @Column(name = "pharmacy_name")
    private String pharmacyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "claim_count", nullable = false)
    private Integer claimCount;

    // Check-specific fields
    @Column(name = "check_number")
    private String checkNumber;

    @Column(name = "check_date")
    private LocalDate checkDate;

    @Column(name = "check_amount", precision = 15, scale = 2)
    private BigDecimal checkAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_status")
    private CheckStatus checkStatus;

    @Column(name = "check_tracking_number")
    private String checkTrackingNumber;

    @Column(name = "check_payee_name")
    private String checkPayeeName;

    @Column(name = "check_delivery_address", columnDefinition = "TEXT")
    private String checkDeliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentTransactionStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "settlement_status")
    private String settlementStatus;

    @Column(name = "edi_file_id")
    private Long ediFileId;

    @Column(name = "edi_bucket_id")
    private Long ediBucketId;

    @Column(name = "remittance_reference")
    private String remittanceReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_reference_allocated_id")
    private PaymentReferenceAllocation paymentReferenceAllocation;

    @Column(name = "payment_reference_number")
    private String paymentReferenceNumber;

    @Column(name = "payment_reference_reserved_at")
    private LocalDateTime paymentReferenceReservedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

#### Testing Checklist
- [ ] All entity classes compile successfully
- [ ] JPA annotations correct
- [ ] Repositories extend correct interfaces
- [ ] Basic CRUD operations tested
- [ ] Relationships properly mapped

---

## Week 3-4: Core Payment Services

### Task 2.1: Payment Reference Allocation Service
**Owner**: Backend Team  
**Duration**: 4 days  
**Priority**: Critical

#### Implementation

```java
// File: src/main/java/com/healthcare/edi835/service/PaymentReferenceAllocationService.java

package com.healthcare.edi835.service;

import com.healthcare.edi835.model.*;
import com.healthcare.edi835.model.enums.*;
import com.healthcare.edi835.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class PaymentReferenceAllocationService {

    private final PaymentReferenceAllocationRepository referenceRepo;
    private final CheckNumberSequenceRepository checkSeqRepo;
    private final PharmacyPaymentConfigRepository configRepo;

    public PaymentReferenceAllocationService(
            PaymentReferenceAllocationRepository referenceRepo,
            CheckNumberSequenceRepository checkSeqRepo,
            PharmacyPaymentConfigRepository configRepo) {
        this.referenceRepo = referenceRepo;
        this.checkSeqRepo = checkSeqRepo;
        this.configRepo = configRepo;
    }

    /**
     * Allocates check numbers for all pharmacies in a bucket.
     */
    @Transactional
    public Map<String, PaymentReference> allocateCheckNumbersForBucket(
            Long bucketId,
            Map<String, PharmacyPaymentGroup> paymentGroups) {
        
        log.info("Allocating check numbers for bucket: {}", bucketId);
        
        Map<String, PaymentReference> allocatedReferences = new HashMap<>();
        
        for (Map.Entry<String, PharmacyPaymentGroup> entry : paymentGroups.entrySet()) {
            String pharmacyId = entry.getKey();
            PharmacyPaymentGroup group = entry.getValue();
            
            PaymentReference reference = allocateCheckNumber(bucketId, pharmacyId, group);
            allocatedReferences.put(pharmacyId, reference);
        }
        
        log.info("Allocated {} check numbers for bucket: {}", 
            allocatedReferences.size(), bucketId);
        
        return allocatedReferences;
    }

    @Transactional
    public PaymentReference allocateCheckNumber(
            Long bucketId, 
            String pharmacyId,
            PharmacyPaymentGroup group) {
        
        log.debug("Allocating check number for pharmacy: {}", pharmacyId);
        
        // Get pharmacy config
        PharmacyPaymentConfig config = configRepo.findByPharmacyId(pharmacyId)
            .orElseThrow(() -> new IllegalStateException(
                "Payment config not found for pharmacy: " + pharmacyId));
        
        String bankAccountId = config.getCheckBankAccountId();
        
        // Get or create sequence
        CheckNumberSequence sequence = checkSeqRepo
            .findByBankAccountId(bankAccountId)
            .orElseGet(() -> createCheckSequence(bankAccountId));
        
        // Increment and get next check number
        long nextCheckNumber = sequence.getCurrentCheckNumber() + 1;
        sequence.setCurrentCheckNumber(nextCheckNumber);
        sequence.setLastIssuedAt(LocalDateTime.now());
        checkSeqRepo.save(sequence);
        
        // Format check number
        String formattedCheckNumber = formatCheckNumber(
            sequence.getCheckPrefix(),
            nextCheckNumber,
            sequence.getCheckSuffix()
        );
        
        // Create allocation record
        PaymentReferenceAllocation allocation = PaymentReferenceAllocation.builder()
            .referenceType(ReferenceType.CHECK_NUMBER)
            .referenceNumber(formattedCheckNumber)
            .allocatedToBucketId(bucketId)
            .allocatedToPharmacyId(pharmacyId)
            .allocationStatus(AllocationStatus.RESERVED)
            .reservedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();
        
        referenceRepo.save(allocation);
        
        PaymentReference reference = PaymentReference.builder()
            .allocationId(allocation.getId())
            .referenceType(ReferenceType.CHECK_NUMBER)
            .referenceNumber(formattedCheckNumber)
            .pharmacyId(pharmacyId)
            .paymentAmount(group.getTotalAmount())
            .build();
        
        log.info("Allocated check number: {} for pharmacy: {}", 
            formattedCheckNumber, pharmacyId);
        
        return reference;
    }

    private CheckNumberSequence createCheckSequence(String bankAccountId) {
        CheckNumberSequence sequence = CheckNumberSequence.builder()
            .bankAccountId(bankAccountId)
            .currentCheckNumber(100000L) // Starting number
            .checkPrefix("CHK")
            .build();
        return checkSeqRepo.save(sequence);
    }

    private String formatCheckNumber(String prefix, long number, String suffix) {
        StringBuilder formatted = new StringBuilder();
        
        if (prefix != null && !prefix.isEmpty()) {
            formatted.append(prefix).append("-");
        }
        
        formatted.append(String.format("%06d", number));
        
        if (suffix != null && !suffix.isEmpty()) {
            formatted.append("-").append(suffix);
        }
        
        return formatted.toString();
    }

    @Transactional
    public void markReferenceAsAllocated(Long allocationId, Long ediFileId) {
        PaymentReferenceAllocation allocation = referenceRepo
            .findById(allocationId)
            .orElseThrow(() -> new IllegalStateException(
                "Allocation not found: " + allocationId));
        
        allocation.setAllocationStatus(AllocationStatus.ALLOCATED);
        allocation.setAllocatedAt(LocalDateTime.now());
        allocation.setEdiFileId(ediFileId);
        
        referenceRepo.save(allocation);
    }

    @Transactional
    public void markReferenceAsUsed(Long allocationId, Long paymentTransactionId) {
        PaymentReferenceAllocation allocation = referenceRepo
            .findById(allocationId)
            .orElseThrow(() -> new IllegalStateException(
                "Allocation not found: " + allocationId));
        
        allocation.setAllocationStatus(AllocationStatus.USED);
        allocation.setUsedAt(LocalDateTime.now());
        allocation.setPaymentTransactionId(paymentTransactionId);
        
        referenceRepo.save(allocation);
    }

    @Transactional
    public void cancelReservedReferences(Long bucketId) {
        List<PaymentReferenceAllocation> allocations = referenceRepo
            .findByAllocatedToBucketIdAndAllocationStatus(
                bucketId, AllocationStatus.RESERVED);
        
        for (PaymentReferenceAllocation allocation : allocations) {
            allocation.setAllocationStatus(AllocationStatus.CANCELLED);
            referenceRepo.save(allocation);
        }
        
        log.info("Cancelled {} reserved references for bucket: {}", 
            allocations.size(), bucketId);
    }
}
```

#### Testing Checklist
- [ ] Check number allocation works correctly
- [ ] Sequential numbering maintained
- [ ] Concurrent allocation handles properly
- [ ] Reference status transitions correct
- [ ] Cancellation works as expected

---

### Task 2.2: Enhanced EDI 835 Generator with Check References
**Owner**: Backend Team  
**Duration**: 5 days  
**Priority**: Critical

#### Files to Modify

```
src/main/java/com/healthcare/edi835/service/
└── Edi835GeneratorService.java  (enhance existing)
```

#### Key Changes to Implement

1. **Add method to generate EDI with payment references**
2. **Update BPR segment for check payments**
3. **Update TRN segment with check reference**
4. **Add REF segment per claim with check number**

#### Code Implementation

```java
// File: src/main/java/com/healthcare/edi835/service/Edi835GeneratorService.java
// Add these methods to existing service

/**
 * NEW METHOD: Generate EDI 835 with payment references.
 */
public Path generateEdi835WithPaymentReferences(
        BucketDetails bucket,
        Map<String, PharmacyPaymentGroup> paymentGroups,
        Map<String, PaymentReference> paymentReferences) {
    
    log.info("Generating EDI 835 with payment references for bucket: {}", 
        bucket.getBucketId());
    
    RemittanceAdvice remittance = buildRemittanceAdviceWithReferences(
        bucket, paymentGroups, paymentReferences);
    
    Path outputPath = determineOutputPath(bucket, paymentReferences);
    
    try (OutputStream output = new FileOutputStream(outputPath.toFile())) {
        EDIStreamWriter writer = outputFactory.createEDIStreamWriter(output);
        writer.setSchema(schema);

        writeInterchangeEnvelope(writer, remittance);
        writeFunctionalGroup(writer, remittance);
        writeTransactionWithPaymentReferences(writer, remittance, paymentReferences);
        
        writer.close();
        
        log.info("Successfully generated EDI 835 file with payment references: {}", 
            outputPath);
        
        return outputPath;

    } catch (Exception e) {
        log.error("Error generating EDI 835 file", e);
        throw new RuntimeException("Failed to generate EDI 835", e);
    }
}

/**
 * MODIFIED: Write BPR segment for check payment.
 */
private void writeBPRSegmentForCheckPayment(
        EDIStreamWriter writer,
        RemittanceAdvice remittance,
        Map<String, PaymentReference> paymentReferences) throws Exception {
    
    writer.writeStartSegment("BPR");
    writer.writeElement("I"); // BPR01 - Transaction handling
    writer.writeElement(formatAmount(remittance.getTotalPaidAmount())); // BPR02
    writer.writeElement("C"); // BPR03 - Credit
    writer.writeElement("CHK"); // BPR04 - Check payment method
    
    // BPR05-15 - Not used for checks
    for (int i = 0; i < 11; i++) {
        writer.writeElement("");
    }
    
    // BPR16 - Check date
    writer.writeElement(LocalDate.now().format(DATE_FORMAT));
    
    writer.writeEndSegment();
}

/**
 * MODIFIED: Write TRN segment with primary check number.
 */
private void writeTRNSegmentWithCheckReference(
        EDIStreamWriter writer,
        RemittanceAdvice remittance,
        Map<String, PaymentReference> paymentReferences) throws Exception {
    
    writer.writeStartSegment("TRN");
    writer.writeElement("1"); // TRN01 - Trace type
    
    // Use first check number as primary reference
    String primaryReference = paymentReferences.values().iterator().next()
        .getReferenceNumber();
    
    writer.writeElement(primaryReference); // TRN02 - Check reference
    writer.writeElement(remittance.getPayerId()); // TRN03
    writer.writeElement(String.format("BATCH-%d", remittance.getBatchId())); // TRN04
    
    writer.writeEndSegment();
}

/**
 * MODIFIED: Write claim loop with check number reference.
 */
private void writeClaimLoopWithCheckReference(
        EDIStreamWriter writer,
        Claim claim,
        PaymentReference reference) throws Exception {
    
    // CLP segment (standard)
    writeClPSegment(writer, claim);
    
    // CAS segments for adjustments
    if (claim.getAdjustments() != null && !claim.getAdjustments().isEmpty()) {
        writeClaimAdjustments(writer, claim.getAdjustments());
    }

    // NM1 segment - Patient
    writePatientSegment(writer, claim);

    // REF segment - Check number reference
    writer.writeStartSegment("REF");
    writer.writeElement("0K"); // REF01 - Check number qualifier
    writer.writeElement(reference.getReferenceNumber()); // REF02 - Check number
    writer.writeEndSegment();

    // Service line loops
    if (claim.getServiceLines() != null) {
        for (Claim.ServiceLine serviceLine : claim.getServiceLines()) {
            writeServiceLineLoop(writer, serviceLine);
        }
    }
}
```

#### Testing Checklist
- [ ] EDI file generated with check numbers
- [ ] BPR segment correct for check payment
- [ ] TRN segment contains check reference
- [ ] REF segment present per claim
- [ ] File validates against HIPAA 5010 schema
- [ ] Check numbers match allocation records

---

### Task 2.3: Payment Orchestration Service
**Owner**: Backend Team  
**Duration**: 5 days  
**Priority**: Critical

#### Implementation

```java
// File: src/main/java/com/healthcare/edi835/service/PaymentOrchestrationService.java

package com.healthcare.edi835.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentOrchestrationService {

    private final PaymentReferenceAllocationService referenceAllocationService;
    private final Edi835GeneratorService edi835Generator;
    private final CheckPaymentService checkPaymentService;
    private final PaymentConfigService paymentConfigService;
    private final BucketPaymentAllocationRepository bucketPaymentRepo;
    private final PaymentBatchRepository batchRepo;
    private final PaymentTransactionRepository transactionRepo;

    @Transactional
    public BucketPaymentResult processApprovedBucketForCheckPayment(
            Long bucketId, 
            String approvedBy) {
        
        log.info("Processing bucket {} for check payment by {}", bucketId, approvedBy);
        
        BucketPaymentResult result = new BucketPaymentResult();
        
        try {
            // Step 1: Get bucket details
            BucketDetails bucket = getBucketDetails(bucketId);
            
            // Step 2: Group claims by pharmacy
            Map<String, PharmacyPaymentGroup> paymentGroups = 
                groupClaimsByPharmacy(bucket.getClaims());
            
            // Step 3: Allocate check numbers
            Map<String, PaymentReference> paymentReferences = 
                referenceAllocationService.allocateCheckNumbersForBucket(
                    bucketId, paymentGroups);
            
            result.setPaymentReferences(paymentReferences);
            
            // Step 4: Create bucket allocation record
            BucketPaymentAllocation allocation = createBucketAllocation(bucketId);
            allocation.setAllocationStatus(AllocationStatus.REFERENCES_RESERVED);
            allocation.setReferencesReservedAt(LocalDateTime.now());
            bucketPaymentRepo.save(allocation);
            
            // Step 5: Generate EDI 835 with check numbers
            Path ediFile = edi835Generator.generateEdi835WithPaymentReferences(
                bucket, paymentGroups, paymentReferences);
            
            Long ediFileId = saveEdiFileRecord(ediFile, bucketId);
            result.setEdiFileId(ediFileId);
            
            // Update allocation status
            allocation.setAllocationStatus(AllocationStatus.EDI_GENERATED);
            allocation.setEdiGeneratedAt(LocalDateTime.now());
            bucketPaymentRepo.save(allocation);
            
            // Mark references as allocated
            for (PaymentReference ref : paymentReferences.values()) {
                referenceAllocationService.markReferenceAsAllocated(
                    ref.getAllocationId(), ediFileId);
            }
            
            // Step 6: Create payment batch
            PaymentBatch batch = createPaymentBatch(
                bucket, ediFileId, paymentGroups, paymentReferences);
            
            List<PaymentTransaction> transactions = 
                createPaymentTransactions(batch, paymentGroups, paymentReferences);
            
            result.setPaymentBatch(batch);
            result.setPaymentTransactions(transactions);
            
            // Step 7: Print checks
            checkPaymentService.printChecksWithPreAllocatedNumbers(
                batch, transactions, paymentReferences);
            
            // Update allocation status
            allocation.setAllocationStatus(AllocationStatus.PAYMENTS_SUBMITTED);
            allocation.setPaymentsSubmittedAt(LocalDateTime.now());
            allocation.setPaymentBatchId(batch.getId());
            bucketPaymentRepo.save(allocation);
            
            // Mark references as used
            for (PaymentReference ref : paymentReferences.values()) {
                PaymentTransaction txn = transactions.stream()
                    .filter(t -> t.getPharmacyId().equals(ref.getPharmacyId()))
                    .findFirst()
                    .orElseThrow();
                
                referenceAllocationService.markReferenceAsUsed(
                    ref.getAllocationId(), txn.getId());
            }
            
            // Step 8: Update bucket status
            updateBucketStatus(bucketId, BucketStatus.COMPLETED);
            
            allocation.setAllocationStatus(AllocationStatus.COMPLETED);
            allocation.setCompletedAt(LocalDateTime.now());
            bucketPaymentRepo.save(allocation);
            
            result.setSuccess(true);
            log.info("Successfully processed bucket: {}", bucketId);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to process bucket: {}", bucketId, e);
            referenceAllocationService.cancelReservedReferences(bucketId);
            
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            throw new BucketProcessingException("Failed to process bucket", e);
        }
    }

    private Map<String, PharmacyPaymentGroup> groupClaimsByPharmacy(
            List<Claim> claims) {
        
        Map<String, List<Claim>> claimsByPharmacy = claims.stream()
            .collect(Collectors.groupingBy(Claim::getPharmacyId));
        
        Map<String, PharmacyPaymentGroup> paymentGroups = new HashMap<>();
        
        for (Map.Entry<String, List<Claim>> entry : claimsByPharmacy.entrySet()) {
            String pharmacyId = entry.getKey();
            List<Claim> pharmacyClaims = entry.getValue();
            
            PharmacyPaymentConfig config = paymentConfigService.getConfig(pharmacyId);
            
            BigDecimal totalAmount = pharmacyClaims.stream()
                .map(Claim::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            PharmacyPaymentGroup group = PharmacyPaymentGroup.builder()
                .pharmacyId(pharmacyId)
                .pharmacyName(config.getPharmacyName())
                .claims(pharmacyClaims)
                .paymentMethod(PaymentMethod.CHECK)
                .totalAmount(totalAmount)
                .config(config)
                .build();
            
            paymentGroups.put(pharmacyId, group);
        }
        
        return paymentGroups;
    }

    private List<PaymentTransaction> createPaymentTransactions(
            PaymentBatch batch,
            Map<String, PharmacyPaymentGroup> paymentGroups,
            Map<String, PaymentReference> paymentReferences) {
        
        List<PaymentTransaction> transactions = new ArrayList<>();
        
        for (Map.Entry<String, PharmacyPaymentGroup> entry : paymentGroups.entrySet()) {
            String pharmacyId = entry.getKey();
            PharmacyPaymentGroup group = entry.getValue();
            PaymentReference reference = paymentReferences.get(pharmacyId);
            
            PaymentTransaction txn = PaymentTransaction.builder()
                .transactionNumber(generateTransactionNumber())
                .batch(batch)
                .pharmacyId(pharmacyId)
                .pharmacyName(group.getPharmacyName())
                .paymentMethod(PaymentMethod.CHECK)
                .paymentAmount(group.getTotalAmount())
                .claimCount(group.getClaims().size())
                .status(PaymentTransactionStatus.PENDING)
                .ediBucketId(batch.getEdiBucketId())
                .ediFileId(batch.getEdiFileId())
                .remittanceReference(reference.getReferenceNumber())
                .paymentReferenceAllocation(
                    referenceAllocationService.findById(reference.getAllocationId()))
                .paymentReferenceNumber(reference.getReferenceNumber())
                .checkNumber(reference.getReferenceNumber())
                .checkPayeeName(group.getConfig().getCheckPayeeName())
                .checkDeliveryAddress(group.getConfig().getCheckDeliveryAddress())
                .paymentReferenceReservedAt(LocalDateTime.now())
                .build();
            
            transactions.add(txn);
        }
        
        return transactionRepo.saveAll(transactions);
    }
}
```

#### Testing Checklist
- [ ] End-to-end flow works correctly
- [ ] Check numbers allocated before EDI
- [ ] EDI contains correct check numbers
- [ ] Transactions created with references
- [ ] Error rollback works properly
- [ ] Status transitions tracked

---

## Week 5: Check Printing & Delivery

### Task 3.1: Check Printing Service Integration
**Owner**: Backend Team  
**Duration**: 4 days  
**Priority**: High

#### Files to Create

```
src/main/java/com/healthcare/edi835/
├── service/
│   ├── CheckPaymentService.java
│   └── CheckPrintingService.java
├── integration/
│   └── CheckPrintingClient.java
└── model/
    ├── CheckDetails.java
    └── CheckPrintJob.java
```

#### Implementation

```java
// File: src/main/java/com/healthcare/edi835/service/CheckPaymentService.java

package com.healthcare.edi835.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CheckPaymentService {

    private final CheckPrintingService checkPrintingService;
    private final CourierService courierService;
    private final PaymentTransactionRepository transactionRepo;
    private final PaymentStatusEventRepository eventRepo;

    @Transactional
    public void printChecksWithPreAllocatedNumbers(
            PaymentBatch batch,
            List<PaymentTransaction> transactions,
            Map<String, PaymentReference> paymentReferences) {
        
        log.info("Printing checks for batch: {} with {} transactions", 
            batch.getBatchNumber(), transactions.size());

        for (PaymentTransaction txn : transactions) {
            try {
                PaymentReference reference = paymentReferences.get(txn.getPharmacyId());
                String checkNumber = reference.getReferenceNumber();
                
                CheckDetails check = CheckDetails.builder()
                    .checkNumber(checkNumber)
                    .checkDate(LocalDate.now())
                    .payeeName(txn.getCheckPayeeName())
                    .amount(txn.getPaymentAmount())
                    .amountInWords(convertAmountToWords(txn.getPaymentAmount()))
                    .memoLine(String.format("Claim Payment - Ref: %s", checkNumber))
                    .pharmacyId(txn.getPharmacyId())
                    .transactionId(txn.getId())
                    .deliveryAddress(txn.getCheckDeliveryAddress())
                    .build();
                
                // Print check
                CheckPrintJob printJob = checkPrintingService.printCheck(check);
                
                // Update transaction
                txn.setCheckStatus(CheckStatus.PRINTED);
                txn.setStatus(PaymentTransactionStatus.INITIATED);
                txn.setCheckDate(LocalDate.now());
                transactionRepo.save(txn);
                
                // Record event
                recordStatusEvent(txn, PaymentTransactionStatus.PENDING, 
                    PaymentTransactionStatus.INITIATED, "Check printed");
                
                log.info("Printed check: {} for pharmacy: {}", 
                    checkNumber, txn.getPharmacyId());
                
                // Schedule dispatch
                scheduleCheckDispatch(txn, printJob);
                
            } catch (Exception e) {
                log.error("Failed to print check for transaction: {}", 
                    txn.getTransactionNumber(), e);
                handleCheckPrintingFailure(txn, e);
            }
        }
    }

    private void scheduleCheckDispatch(
            PaymentTransaction txn, 
            CheckPrintJob printJob) {
        
        CourierDispatch dispatch = CourierDispatch.builder()
            .transactionId(txn.getId())
            .checkNumber(txn.getCheckNumber())
            .recipientName(txn.getCheckPayeeName())
            .deliveryAddress(txn.getCheckDeliveryAddress())
            .packageContents("Payment Check")
            .build();
        
        CourierBooking booking = courierService.bookCourier(dispatch);
        
        txn.setCheckTrackingNumber(booking.getTrackingNumber());
        txn.setCheckStatus(CheckStatus.DISPATCHED);
        transactionRepo.save(txn);
        
        recordStatusEvent(txn, null, null, "Check dispatched via courier");
    }

    private String convertAmountToWords(BigDecimal amount) {
        // Implementation to convert number to words
        // e.g., 1234.56 -> "One Thousand Two Hundred Thirty Four and 56/100"
        return AmountToWordsConverter.convert(amount);
    }

    private void recordStatusEvent(
            PaymentTransaction txn,
            PaymentTransactionStatus previousStatus,
            PaymentTransactionStatus newStatus,
            String message) {
        
        PaymentStatusEvent event = PaymentStatusEvent.builder()
            .paymentTransaction(txn)
            .eventType("STATUS_CHANGE")
            .previousStatus(previousStatus != null ? previousStatus.name() : null)
            .newStatus(newStatus != null ? newStatus.name() : null)
            .eventData(Map.of("message", message))
            .triggeredBy("SYSTEM")
            .build();
        
        eventRepo.save(event);
    }

    @Transactional
    public void updateCheckStatus(
            String checkNumber, 
            CheckStatus newStatus, 
            LocalDate date,
            String details) {
        
        PaymentTransaction txn = transactionRepo
            .findByCheckNumber(checkNumber)
            .orElseThrow(() -> new IllegalStateException(
                "Transaction not found for check: " + checkNumber));

        CheckStatus previousStatus = txn.getCheckStatus();
        txn.setCheckStatus(newStatus);
        
        switch (newStatus) {
            case DELIVERED -> {
                recordStatusEvent(txn, null, null, 
                    "Check delivered to pharmacy");
            }
            case ENCASHED -> {
                txn.setStatus(PaymentTransactionStatus.SUCCESS);
                txn.setSettlementDate(date);
                txn.setSettlementStatus("SETTLED");
                recordStatusEvent(txn, PaymentTransactionStatus.INITIATED,
                    PaymentTransactionStatus.SUCCESS, "Check encashed");
            }
            case CANCELLED, STOPPED -> {
                txn.setStatus(PaymentTransactionStatus.FAILED);
                txn.setFailureReason(details);
                recordStatusEvent(txn, txn.getStatus(),
                    PaymentTransactionStatus.FAILED, "Check cancelled/stopped");
            }
        }
        
        transactionRepo.save(txn);
        
        log.info("Updated check {} status from {} to {}", 
            checkNumber, previousStatus, newStatus);
    }
}
```

#### Testing Checklist
- [ ] Check printing integration works
- [ ] Pre-allocated numbers used correctly
- [ ] Check details accurate
- [ ] Courier dispatch scheduled
- [ ] Status events recorded
- [ ] Error handling works

---

### Task 3.2: Check Printing Client (Mock/Real)
**Owner**: Backend Team  
**Duration**: 3 days  
**Priority**: High

#### Implementation

```java
// File: src/main/java/com/healthcare/edi835/service/CheckPrintingService.java

package com.healthcare.edi835.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CheckPrintingService {

    @Value("${check-printing.mode:MOCK}")
    private String mode;

    @Value("${check-printing.api-url}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    public CheckPrintJob printCheck(CheckDetails check) {
        if ("MOCK".equals(mode)) {
            return printCheckMock(check);
        } else {
            return printCheckReal(check);
        }
    }

    /**
     * Mock implementation for testing.
     */
    private CheckPrintJob printCheckMock(CheckDetails check) {
        log.info("MOCK: Printing check number: {}", check.getCheckNumber());
        
        CheckPrintJob job = CheckPrintJob.builder()
            .jobId("MOCK-" + UUID.randomUUID().toString())
            .checkNumber(check.getCheckNumber())
            .status("PRINTED")
            .printedAt(LocalDateTime.now())
            .build();
        
        // Simulate printing delay
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("MOCK: Check {} printed successfully", check.getCheckNumber());
        return job;
    }

    /**
     * Real implementation calling check printing API.
     */
    private CheckPrintJob printCheckReal(CheckDetails check) {
        log.info("Sending check to printing service: {}", check.getCheckNumber());
        
        try {
            CheckPrintRequest request = CheckPrintRequest.builder()
                .checkNumber(check.getCheckNumber())
                .date(check.getCheckDate())
                .payee(check.getPayeeName())
                .amount(check.getAmount())
                .amountInWords(check.getAmountInWords())
                .memo(check.getMemoLine())
                .build();
            
            CheckPrintResponse response = restTemplate.postForObject(
                apiUrl + "/print",
                request,
                CheckPrintResponse.class
            );
            
            CheckPrintJob job = CheckPrintJob.builder()
                .jobId(response.getJobId())
                .checkNumber(check.getCheckNumber())
                .status(response.getStatus())
                .printedAt(LocalDateTime.now())
                .build();
            
            log.info("Check {} sent to printer, job ID: {}", 
                check.getCheckNumber(), job.getJobId());
            
            return job;
            
        } catch (Exception e) {
            log.error("Failed to print check: {}", check.getCheckNumber(), e);
            throw new CheckPrintingException("Failed to print check", e);
        }
    }
}
```

#### Configuration

```yaml
# File: src/main/resources/application.yml

check-printing:
  mode: MOCK  # MOCK or REAL
  api-url: ${CHECK_PRINTING_API_URL:http://localhost:9000/api/checks}
  api-key: ${CHECK_PRINTING_API_KEY}
  timeout-ms: 30000

courier:
  provider: FEDEX  # FEDEX, UPS, DHL, etc.
  api-url: ${COURIER_API_URL}
  api-key: ${COURIER_API_KEY}
  default-service-type: OVERNIGHT
```

#### Testing Checklist
- [ ] Mock mode works for testing
- [ ] Real API integration configured
- [ ] Error handling robust
- [ ] Timeout handling works
- [ ] Retry logic implemented

---

## Week 6: Frontend & API Integration

### Task 4.1: Payment Configuration UI
**Owner**: Frontend Team  
**Duration**: 4 days  
**Priority**: High

#### Files to Create

```
edi835-admin-portal/src/
├── components/
│   └── payments/
│       ├── PaymentConfigList.tsx
│       ├── PaymentConfigForm.tsx
│       ├── CheckConfigSection.tsx
│       └── PaymentScheduleSelector.tsx
├── services/
│   └── paymentConfigService.ts
└── types/
    └── PaymentConfig.ts
```

#### Implementation

```typescript
// File: src/types/PaymentConfig.ts

export interface PaymentConfig {
  id?: number;
  pharmacyId: string;
  pharmacyName: string;
  paymentMethod: 'CHECK' | 'NACH' | 'BOTH';
  
  // Check configuration
  checkEnabled: boolean;
  checkPayeeName?: string;
  checkDeliveryAddress?: string;
  checkDeliveryCity?: string;
  checkDeliveryState?: string;
  checkDeliveryPincode?: string;
  checkBankAccountId?: string;
  
  // Thresholds
  minPaymentAmount: number;
  
  // Scheduling
  paymentSchedule: 'IMMEDIATE' | 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
  
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
}
```

```typescript
// File: src/services/paymentConfigService.ts

import axios from 'axios';
import { PaymentConfig } from '../types/PaymentConfig';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export const paymentConfigService = {
  async getAll(): Promise<PaymentConfig[]> {
    const response = await axios.get(`${API_BASE_URL}/payments/config/pharmacies`);
    return response.data;
  },

  async getById(id: number): Promise<PaymentConfig> {
    const response = await axios.get(`${API_BASE_URL}/payments/config/pharmacies/${id}`);
    return response.data;
  },

  async create(config: PaymentConfig): Promise<PaymentConfig> {
    const response = await axios.post(`${API_BASE_URL}/payments/config/pharmacies`, config);
    return response.data;
  },

  async update(id: number, config: PaymentConfig): Promise<PaymentConfig> {
    const response = await axios.put(
      `${API_BASE_URL}/payments/config/pharmacies/${id}`,
      config
    );
    return response.data;
  },

  async delete(id: number): Promise<void> {
    await axios.delete(`${API_BASE_URL}/payments/config/pharmacies/${id}`);
  },
};
```

```tsx
// File: src/components/payments/PaymentConfigForm.tsx

import React, { useState } from 'react';
import {
  Box,
  TextField,
  FormControl,
  FormLabel,
  RadioGroup,
  FormControlLabel,
  Radio,
  Switch,
  Button,
  Grid,
  Paper,
  Typography,
} from '@mui/material';
import { useFormik } from 'formik';
import * as yup from 'yup';
import { PaymentConfig } from '../../types/PaymentConfig';
import { paymentConfigService } from '../../services/paymentConfigService';

const validationSchema = yup.object({
  pharmacyId: yup.string().required('Pharmacy ID is required'),
  pharmacyName: yup.string().required('Pharmacy name is required'),
  checkPayeeName: yup.string().when('checkEnabled', {
    is: true,
    then: yup.string().required('Payee name is required'),
  }),
  checkDeliveryAddress: yup.string().when('checkEnabled', {
    is: true,
    then: yup.string().required('Delivery address is required'),
  }),
  minPaymentAmount: yup.number().min(0, 'Must be non-negative'),
});

interface Props {
  config?: PaymentConfig;
  onSave: (config: PaymentConfig) => void;
  onCancel: () => void;
}

export const PaymentConfigForm: React.FC<Props> = ({ config, onSave, onCancel }) => {
  const [loading, setLoading] = useState(false);

  const formik = useFormik({
    initialValues: config || {
      pharmacyId: '',
      pharmacyName: '',
      paymentMethod: 'CHECK',
      checkEnabled: true,
      checkPayeeName: '',
      checkDeliveryAddress: '',
      checkDeliveryCity: '',
      checkDeliveryState: '',
      checkDeliveryPincode: '',
      checkBankAccountId: '',
      minPaymentAmount: 0,
      paymentSchedule: 'IMMEDIATE',
      isActive: true,
    },
    validationSchema,
    onSubmit: async (values) => {
      setLoading(true);
      try {
        if (config?.id) {
          await paymentConfigService.update(config.id, values);
        } else {
          await paymentConfigService.create(values);
        }
        onSave(values);
      } catch (error) {
        console.error('Failed to save payment config:', error);
      } finally {
        setLoading(false);
      }
    },
  });

  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>
        {config ? 'Edit' : 'Create'} Payment Configuration
      </Typography>

      <form onSubmit={formik.handleSubmit}>
        <Grid container spacing={3}>
          {/* Basic Information */}
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Pharmacy ID"
              name="pharmacyId"
              value={formik.values.pharmacyId}
              onChange={formik.handleChange}
              error={formik.touched.pharmacyId && Boolean(formik.errors.pharmacyId)}
              helperText={formik.touched.pharmacyId && formik.errors.pharmacyId}
              disabled={!!config}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Pharmacy Name"
              name="pharmacyName"
              value={formik.values.pharmacyName}
              onChange={formik.handleChange}
              error={formik.touched.pharmacyName && Boolean(formik.errors.pharmacyName)}
              helperText={formik.touched.pharmacyName && formik.errors.pharmacyName}
            />
          </Grid>

          {/* Payment Method */}
          <Grid item xs={12}>
            <FormControl component="fieldset">
              <FormLabel>Payment Method</FormLabel>
              <RadioGroup
                name="paymentMethod"
                value={formik.values.paymentMethod}
                onChange={formik.handleChange}
                row
              >
                <FormControlLabel value="CHECK" control={<Radio />} label="Check Only" />
                <FormControlLabel value="NACH" control={<Radio />} label="NACH Only" disabled />
                <FormControlLabel value="BOTH" control={<Radio />} label="Both" disabled />
              </RadioGroup>
            </FormControl>
          </Grid>

          {/* Check Configuration */}
          <Grid item xs={12}>
            <FormControlLabel
              control={
                <Switch
                  checked={formik.values.checkEnabled}
                  onChange={(e) => formik.setFieldValue('checkEnabled', e.target.checked)}
                  name="checkEnabled"
                />
              }
              label="Enable Check Payments"
            />
          </Grid>

          {formik.values.checkEnabled && (
            <>
              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Check Payee Name"
                  name="checkPayeeName"
                  value={formik.values.checkPayeeName}
                  onChange={formik.handleChange}
                  error={formik.touched.checkPayeeName && Boolean(formik.errors.checkPayeeName)}
                  helperText={formik.touched.checkPayeeName && formik.errors.checkPayeeName}
                />
              </Grid>

              <Grid item xs={12} md={6}>
                <TextField
                  fullWidth
                  label="Bank Account ID"
                  name="checkBankAccountId"
                  value={formik.values.checkBankAccountId}
                  onChange={formik.handleChange}
                />
              </Grid>

              <Grid item xs={12}>
                <TextField
                  fullWidth
                  label="Delivery Address"
                  name="checkDeliveryAddress"
                  value={formik.values.checkDeliveryAddress}
                  onChange={formik.handleChange}
                  multiline
                  rows={2}
                  error={
                    formik.touched.checkDeliveryAddress &&
                    Boolean(formik.errors.checkDeliveryAddress)
                  }
                  helperText={
                    formik.touched.checkDeliveryAddress && formik.errors.checkDeliveryAddress
                  }
                />
              </Grid>

              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth
                  label="City"
                  name="checkDeliveryCity"
                  value={formik.values.checkDeliveryCity}
                  onChange={formik.handleChange}
                />
              </Grid>

              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth
                  label="State"
                  name="checkDeliveryState"
                  value={formik.values.checkDeliveryState}
                  onChange={formik.handleChange}
                />
              </Grid>

              <Grid item xs={12} md={4}>
                <TextField
                  fullWidth
                  label="Pincode"
                  name="checkDeliveryPincode"
                  value={formik.values.checkDeliveryPincode}
                  onChange={formik.handleChange}
                />
              </Grid>
            </>
          )}

          {/* Thresholds */}
          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              type="number"
              label="Minimum Payment Amount"
              name="minPaymentAmount"
              value={formik.values.minPaymentAmount}
              onChange={formik.handleChange}
              error={
                formik.touched.minPaymentAmount && Boolean(formik.errors.minPaymentAmount)
              }
              helperText={formik.touched.minPaymentAmount && formik.errors.minPaymentAmount}
            />
          </Grid>

          {/* Payment Schedule */}
          <Grid item xs={12} md={6}>
            <FormControl fullWidth>
              <FormLabel>Payment Schedule</FormLabel>
              <RadioGroup
                name="paymentSchedule"
                value={formik.values.paymentSchedule}
                onChange={formik.handleChange}
              >
                <FormControlLabel
                  value="IMMEDIATE"
                  control={<Radio />}
                  label="Immediate"
                />
                <FormControlLabel value="DAILY" control={<Radio />} label="Daily" />
                <FormControlLabel value="WEEKLY" control={<Radio />} label="Weekly" />
                <FormControlLabel value="MONTHLY" control={<Radio />} label="Monthly" />
              </RadioGroup>
            </FormControl>
          </Grid>

          {/* Actions */}
          <Grid item xs={12}>
            <Box display="flex" justifyContent="flex-end" gap={2}>
              <Button onClick={onCancel} disabled={loading}>
                Cancel
              </Button>
              <Button type="submit" variant="contained" disabled={loading}>
                {loading ? 'Saving...' : 'Save'}
              </Button>
            </Box>
          </Grid>
        </Grid>
      </form>
    </Paper>
  );
};
```

#### Testing Checklist
- [ ] Form validation works
- [ ] Create/edit/delete operations work
- [ ] UI responsive and user-friendly
- [ ] Error handling displays properly
- [ ] Data persists correctly

---

### Task 4.2: Payment Monitoring Dashboard
**Owner**: Frontend Team  
**Duration**: 4 days  
**Priority**: High

#### Files to Create

```
edi835-admin-portal/src/
├── components/
│   └── payments/
│       ├── PaymentDashboard.tsx
│       ├── PaymentBatchList.tsx
│       ├── PaymentTransactionList.tsx
│       └── CheckStatusTracker.tsx
└── services/
    └── paymentMonitoringService.ts
```

#### Implementation

```tsx
// File: src/components/payments/PaymentDashboard.tsx

import React from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
} from '@mui/material';
import {
  CheckCircle,
  Pending,
  Error,
  LocalShipping,
} from '@mui/icons-material';
import { useQuery } from '@tanstack/react-query';
import { paymentMonitoringService } from '../../services/paymentMonitoringService';

export const PaymentDashboard: React.FC = () => {
  const { data: summary, isLoading } = useQuery({
    queryKey: ['paymentSummary'],
    queryFn: paymentMonitoringService.getSummary,
    refetchInterval: 30000,
  });

  if (isLoading) return <div>Loading...</div>;

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" gutterBottom>
        Payment Monitoring
      </Typography>

      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <CheckCircle color="success" fontSize="large" />
                <Box>
                  <Typography variant="h6">
                    {summary?.completedPayments || 0}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Completed Payments
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Pending color="warning" fontSize="large" />
                <Box>
                  <Typography variant="h6">
                    {summary?.pendingPayments || 0}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Pending Payments
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <LocalShipping color="info" fontSize="large" />
                <Box>
                  <Typography variant="h6">
                    {summary?.checksInTransit || 0}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Checks in Transit
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Error color="error" fontSize="large" />
                <Box>
                  <Typography variant="h6">
                    {summary?.failedPayments || 0}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Failed Payments
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              Recent Payment Batches
            </Typography>
            {/* PaymentBatchList component */}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};
```

#### Testing Checklist
- [ ] Dashboard displays correctly
- [ ] Real-time updates work
- [ ] Metrics accurate
- [ ] Navigation functional
- [ ] Performance acceptable

---

## Week 7-8: Testing & Refinement

### Task 5.1: Unit Tests
**Owner**: Backend Team  
**Duration**: 5 days  
**Priority**: High

#### Test Coverage Requirements
- Payment reference allocation: >90%
- Payment orchestration: >85%
- Check payment service: >85%
- EDI generator enhancements: >85%

#### Files to Create

```
src/test/java/com/healthcare/edi835/
├── service/
│   ├── PaymentReferenceAllocationServiceTest.java
│   ├── PaymentOrchestrationServiceTest.java
│   ├── CheckPaymentServiceTest.java
│   └── Edi835GeneratorServiceTest.java
└── integration/
    └── CheckPaymentIntegrationTest.java
```

#### Example Test

```java
// File: src/test/java/com/healthcare/edi835/service/PaymentReferenceAllocationServiceTest.java

@ExtendWith(MockitoExtension.class)
class PaymentReferenceAllocationServiceTest {

    @Mock
    private PaymentReferenceAllocationRepository referenceRepo;
    
    @Mock
    private CheckNumberSequenceRepository checkSeqRepo;
    
    @Mock
    private PharmacyPaymentConfigRepository configRepo;
    
    @InjectMocks
    private PaymentReferenceAllocationService service;

    @Test
    @DisplayName("Should allocate check number successfully")
    void shouldAllocateCheckNumberSuccessfully() {
        // Given
        Long bucketId = 1L;
        String pharmacyId = "PHARM001";
        
        PharmacyPaymentConfig config = PharmacyPaymentConfig.builder()
            .pharmacyId(pharmacyId)
            .checkBankAccountId("BANK001")
            .build();
        
        CheckNumberSequence sequence = CheckNumberSequence.builder()
            .bankAccountId("BANK001")
            .currentCheckNumber(100000L)
            .checkPrefix("CHK")
            .build();
        
        when(configRepo.findByPharmacyId(pharmacyId))
            .thenReturn(Optional.of(config));
        when(checkSeqRepo.findByBankAccountId("BANK001"))
            .thenReturn(Optional.of(sequence));
        when(referenceRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        
        PharmacyPaymentGroup group = PharmacyPaymentGroup.builder()
            .totalAmount(new BigDecimal("1000.00"))
            .build();
        
        // When
        PaymentReference reference = service.allocateCheckNumber(
            bucketId, pharmacyId, group);
        
        // Then
        assertThat(reference).isNotNull();
        assertThat(reference.getReferenceNumber()).startsWith("CHK-");
        assertThat(reference.getReferenceType()).isEqualTo(ReferenceType.CHECK_NUMBER);
        
        verify(checkSeqRepo).save(argThat(seq -> 
            seq.getCurrentCheckNumber() == 100001L));
        verify(referenceRepo).save(argThat(alloc ->
            alloc.getAllocationStatus() == AllocationStatus.RESERVED));
    }

    @Test
    @DisplayName("Should handle concurrent allocation correctly")
    void shouldHandleConcurrentAllocationCorrectly() {
        // Test implementation for concurrent scenarios
    }
}
```

#### Testing Checklist
- [ ] All unit tests pass
- [ ] Code coverage meets requirements
- [ ] Edge cases covered
- [ ] Concurrent scenarios tested
- [ ] Mock dependencies correctly

---

### Task 5.2: Integration Tests
**Owner**: Backend Team + QA  
**Duration**: 5 days  
**Priority**: High

#### Test Scenarios

1. **End-to-End Check Payment Flow**
   - Bucket approval triggers check allocation
   - Check numbers allocated before EDI
   - EDI contains correct check numbers
   - Checks printed with allocated numbers
   - Status tracking works end-to-end

2. **Error Recovery Scenarios**
   - Check allocation failure rolls back
   - EDI generation failure cancels references
   - Check printing failure handled
   - Retry logic works correctly

3. **Concurrent Processing**
   - Multiple buckets processed simultaneously
   - Check number uniqueness maintained
   - No race conditions in allocation

#### Implementation

```java
// File: src/test/java/com/healthcare/edi835/integration/CheckPaymentIntegrationTest.java

@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
class CheckPaymentIntegrationTest {

    @Autowired
    private PaymentOrchestrationService orchestrationService;
    
    @Autowired
    private PaymentTransactionRepository transactionRepo;
    
    @Autowired
    private PaymentReferenceAllocationRepository referenceRepo;

    @Test
    @DisplayName("End-to-end check payment flow")
    void testEndToEndCheckPaymentFlow() {
        // Setup test data
        Long bucketId = createTestBucket();
        
        // Execute
        BucketPaymentResult result = orchestrationService
            .processApprovedBucketForCheckPayment(bucketId, "test-user");
        
        // Verify
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPaymentReferences()).isNotEmpty();
        
        // Verify check numbers allocated
        List<PaymentReferenceAllocation> allocations = referenceRepo
            .findByAllocatedToBucketId(bucketId);
        assertThat(allocations).allMatch(a -> 
            a.getAllocationStatus() == AllocationStatus.USED);
        
        // Verify transactions created
        List<PaymentTransaction> transactions = transactionRepo
            .findByBatchId(result.getPaymentBatch().getId());
        assertThat(transactions).allMatch(t -> 
            t.getCheckNumber() != null && 
            t.getStatus() == PaymentTransactionStatus.INITIATED);
    }
}
```

#### Testing Checklist
- [ ] All integration tests pass
- [ ] Database transactions work correctly
- [ ] Error scenarios handled
- [ ] Performance acceptable
- [ ] No data leaks or corruption

---

### Task 5.3: User Acceptance Testing (UAT)
**Owner**: QA + Business Users  
**Duration**: 5 days  
**Priority**: High

#### UAT Test Cases

**Test Case 1: Configure Pharmacy for Check Payment**
1. Navigate to Payment Configuration
2. Click "Add New Pharmacy"
3. Enter pharmacy details
4. Enable check payments
5. Enter check delivery address
6. Save configuration
7. Verify saved successfully

**Test Case 2: Process Bucket with Check Payment**
1. Navigate to Bucket Approval Queue
2. Select a bucket ready for approval
3. Review bucket details
4. Click "Approve & Generate Payment"
5. Verify check numbers allocated
6. Verify EDI file generated
7. Download and review EDI file
8. Verify check numbers in EDI match allocation

**Test Case 3: Monitor Check Status**
1. Navigate to Payment Monitoring Dashboard
2. View recent check payments
3. Click on specific check
4. View check details and tracking
5. Update check status to "Dispatched"
6. Verify status updated correctly
7. Update to "Delivered"
8. Update to "Encashed"
9. Verify payment marked as complete

#### Testing Checklist
- [ ] All UAT test cases pass
- [ ] Business users approve
- [ ] Documentation complete
- [ ] Known issues documented
- [ ] Training materials ready

---

## Week 8: REST API Implementation

### Task 6.1: Payment REST Controllers
**Owner**: Backend Team  
**Duration**: 3 days  
**Priority**: High

#### Files to Create

```
src/main/java/com/healthcare/edi835/controller/
├── PaymentConfigController.java
├── PaymentBatchController.java
├── PaymentTransactionController.java
└── PaymentMonitoringController.java
```

#### Implementation

```java
// File: src/main/java/com/healthcare/edi835/controller/PaymentConfigController.java

package com.healthcare.edi835.controller;

import com.healthcare.edi835.model.PharmacyPaymentConfig;
import com.healthcare.edi835.service.PaymentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments/config")
public class PaymentConfigController {

    private final PaymentConfigService configService;

    public PaymentConfigController(PaymentConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/pharmacies")
    public ResponseEntity<List<PharmacyPaymentConfig>> getAllConfigs() {
        List<PharmacyPaymentConfig> configs = configService.getAllConfigs();
        return ResponseEntity.ok(configs);
    }

    @GetMapping("/pharmacies/{id}")
    public ResponseEntity<PharmacyPaymentConfig> getConfig(@PathVariable Long id) {
        return configService.getConfigById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/pharmacies")
    public ResponseEntity<PharmacyPaymentConfig> createConfig(
            @Valid @RequestBody PharmacyPaymentConfig config) {
        PharmacyPaymentConfig created = configService.createConfig(config);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/pharmacies/{id}")
    public ResponseEntity<PharmacyPaymentConfig> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody PharmacyPaymentConfig config) {
        PharmacyPaymentConfig updated = configService.updateConfig(id, config);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/pharmacies/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        configService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }
}
```

```java
// File: src/main/java/com/healthcare/edi835/controller/PaymentMonitoringController.java

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentMonitoringController {

    private final PaymentMonitoringService monitoringService;

    @GetMapping("/dashboard/summary")
    public ResponseEntity<PaymentSummary> getSummary() {
        PaymentSummary summary = monitoringService.getSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/batches")
    public ResponseEntity<Page<PaymentBatch>> getBatches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PaymentBatch> batches = monitoringService.getBatches(status, pageable);
        return ResponseEntity.ok(batches);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<PaymentTransaction>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String pharmacyId,
            @RequestParam(required = false) String status) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PaymentTransaction> transactions = 
            monitoringService.getTransactions(pharmacyId, status, pageable);
        return ResponseEntity.ok(transactions);
    }

    @PostMapping("/transactions/{id}/status")
    public ResponseEntity<Void> updateCheckStatus(
            @PathVariable Long id,
            @RequestBody CheckStatusUpdate statusUpdate) {
        
        monitoringService.updateCheckStatus(
            id,
            statusUpdate.getStatus(),
            statusUpdate.getDate(),
            statusUpdate.getDetails()
        );
        return ResponseEntity.ok().build();
    }
}
```

#### Testing Checklist
- [ ] All endpoints work correctly
- [ ] Validation works
- [ ] Error responses appropriate
- [ ] API documentation generated
- [ ] Postman collection created

---

## Phase 1 Completion Checklist

### Database
- [ ] All tables created and verified
- [ ] Indexes optimized
- [ ] Foreign keys working
- [ ] Sample data loaded for testing

### Backend Services
- [ ] Payment reference allocation service complete
- [ ] Payment orchestration service complete
- [ ] Check payment service complete
- [ ] EDI generator enhanced
- [ ] REST APIs implemented
- [ ] Unit tests >85% coverage
- [ ] Integration tests passing

### Frontend
- [ ] Payment configuration UI complete
- [ ] Payment monitoring dashboard complete
- [ ] API integration working
- [ ] Error handling robust
- [ ] Responsive design verified

### Testing
- [ ] Unit tests passing
- [ ] Integration tests passing
- [ ] UAT completed
- [ ] Performance acceptable
- [ ] Security review done

### Documentation
- [ ] API documentation complete
- [ ] User guides written
- [ ] Admin guides written
- [ ] Runbooks created
- [ ] Training materials ready

### Deployment
- [ ] Dev environment deployed
- [ ] QA environment deployed
- [ ] UAT environment deployed
- [ ] Production deployment plan ready
- [ ] Rollback plan documented

---

# Phase 2: NACH Payment Implementation

**Duration**: 4-6 weeks  
**Priority**: Medium  
**Dependencies**: Phase 1 complete

*Note: Phase 2 implementation plan will be detailed separately once Phase 1 is successfully deployed and stabilized.*

---

## Phase 2 Overview (High-Level)

### Key Components to Implement

1. **NACH Gateway Integration**
   - NPCI API integration
   - Bank NACH API integration
   - File format handling (NACH format)
   - Mandate management

2. **Database Enhancements**
   - NACH-specific fields
   - Mandate tracking tables
   - Settlement tracking

3. **Service Layer**
   - NACH transaction reference allocation
   - NACH payment service
   - Mandate validation service
   - Settlement reconciliation service

4. **EDI Generator Updates**
   - BPR segment for ACH payments
   - TRN segment for NACH reference
   - ACH-specific loops and segments

5. **Frontend Updates**
   - NACH configuration UI
   - Mandate management UI
   - NACH payment monitoring
   - Settlement reconciliation UI

6. **Testing**
   - NACH gateway integration tests
   - End-to-end NACH payment flow
   - Reconciliation testing
   - UAT with bank systems

---

## Testing Strategy

### Unit Testing
- Target: >85% code coverage
- Focus: Business logic, service layer
- Tools: JUnit 5, Mockito, AssertJ

### Integration Testing
- Database integration
- API integration
- External service mocking
- End-to-end workflows

### UAT Testing
- Business user validation
- Real-world scenarios
- Edge case handling
- User acceptance sign-off

### Performance Testing
- Load testing for concurrent bucket processing
- Check allocation under load
- Database query optimization
- API response times

---

## Deployment Plan

### Phase 1 Deployment

#### Pre-Deployment
1. Database backup
2. Code freeze
3. Final testing
4. Documentation review
5. Stakeholder communication

#### Deployment Steps
1. Deploy database migrations
2. Deploy backend services
3. Deploy frontend updates
4. Configure check printing integration
5. Configure courier service
6. Smoke testing
7. Monitoring setup

#### Post-Deployment
1. Monitor error logs
2. Check payment processing
3. Verify EDI generation
4. User feedback collection
5. Issue tracking and resolution

### Rollback Plan
- Database rollback scripts ready
- Previous version containers available
- Feature flags for gradual rollout
- Communication plan for rollback

---

## Risk Mitigation

### Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Check number collision | High | Low | Implement database-level unique constraint + transaction isolation |
| EDI generation failure | High | Medium | Implement retry logic + transaction rollback |
| Check printing service downtime | High | Medium | Implement circuit breaker + fallback queue |
| Concurrent allocation issues | Medium | Low | Use database locks + optimistic locking |
| Data corruption | High | Low | Implement transaction boundaries + audit logging |

### Business Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Pharmacy rejects check delivery | Medium | Medium | Implement delivery confirmation + re-issue process |
| Check lost in transit | Medium | Low | Implement tracking + insurance + re-issue process |
| Incorrect payment amounts | High | Low | Implement multi-level validation + approval workflow |
| Delayed check delivery | Medium | Medium | Implement expedited shipping option + alerts |

---

## Success Metrics

### Technical Metrics
- Check allocation success rate: >99.9%
- EDI generation success rate: >99.5%
- Check printing success rate: >98%
- Average processing time: <5 minutes per bucket
- API response time: <2 seconds (95th percentile)

### Business Metrics
- Payment processing time: <24 hours
- Check delivery time: <3 business days
- Payment reconciliation accuracy: >99%
- Pharmacy satisfaction: >90%
- Audit trail completeness: 100%

---

## Support & Maintenance

### Production Support
- 24/7 monitoring
- On-call rotation
- Incident response procedures
- Escalation matrix

### Maintenance Windows
- Weekly: Minor updates, bug fixes
- Monthly: Feature releases
- Quarterly: Major updates, infrastructure changes

---

## Appendix

### A. Database Schema Reference
See Week 1-2 database scripts

### B. API Documentation
Generated via Swagger/OpenAPI

### C. Configuration Reference
See application.yml files

### D. Troubleshooting Guide
To be created during implementation

### E. Glossary
- **BPR**: Financial Information segment in EDI 835
- **TRN**: Reassociation Trace Number segment
- **CLP**: Claim Payment Information segment
- **NACH**: National Automated Clearing House
- **UAT**: User Acceptance Testing

---

**End of Phase 1 Implementation Plan**


# Phase 2: NACH Payment Integration - Implementation Plan

## Document Information
- **Project**: EDI 835 Remittance Processing System - NACH Integration
- **Version**: 1.0
- **Phase**: 2
- **Duration**: 6-8 weeks
- **Prerequisites**: Phase 1 (Check Payment) completed and stable

---

## Table of Contents
1. [NACH Overview](#nach-overview)
2. [Week 1-2: NACH Foundation & Database](#week-1-2-nach-foundation--database)
3. [Week 3-4: NACH Services & Gateway Integration](#week-3-4-nach-services--gateway-integration)
4. [Week 5: EDI 835 Enhancement for NACH](#week-5-edi-835-enhancement-for-nach)
5. [Week 6: Frontend & Mandate Management](#week-6-frontend--mandate-management)
6. [Week 7-8: Testing & Deployment](#week-7-8-testing--deployment)
7. [NACH Reconciliation & Settlement](#nach-reconciliation--settlement)
8. [Production Considerations](#production-considerations)

---

## NACH Overview

### What is NACH?
National Automated Clearing House (NACH) is a centralized system launched by NPCI (National Payments Corporation of India) for bulk transactions. It's used for:
- Credit transactions: Salary, dividend, pension payments
- Debit transactions: Bill payments, loan EMI collection

For this project, we'll use **NACH Credit** for pharmacy payments.

### Key NACH Concepts

#### 1. NACH Mandate
- Authorization from pharmacy (beneficiary) to receive payments
- Contains: Bank account details, IFSC code, account holder name
- Status: PENDING → ACTIVE → EXPIRED/CANCELLED
- Validity period (typically 1-5 years)

#### 2. NACH File Format
Standard format for bulk payment instructions:
```
H (Header) - Batch details
D (Detail) - Individual transaction records
T (Trailer) - Batch summary
```

#### 3. Payment Flow
```
Generate NACH File → Submit to Bank/NPCI → Processing → Settlement → Status Updates
```

#### 4. UTR (Unique Transaction Reference)
- Bank-assigned reference for each transaction
- Used for reconciliation and tracking
- Format: NEFT/RTGS style (e.g., HDFC20241120123456789)

#### 5. Settlement Cycle
- T+0 to T+2 days (depending on bank)
- Status updates via:
  - File-based reports from bank
  - API callbacks/webhooks
  - Manual bank statement reconciliation

---

## Week 1-2: NACH Foundation & Database

### Task 1.1: Enhanced Database Schema for NACH
**Owner**: Backend Team  
**Duration**: 4 days  
**Priority**: Critical

#### Deliverables
NACH-specific database tables and enhancements

#### SQL Implementation

```sql
-- File: V2.1__nach_foundation.sql

-- NACH Mandate Management
CREATE TABLE nach_mandates (
    id BIGSERIAL PRIMARY KEY,
    mandate_id VARCHAR(50) UNIQUE NOT NULL,
    
    -- Pharmacy/Beneficiary Details
    pharmacy_id VARCHAR(50) NOT NULL,
    pharmacy_name VARCHAR(200) NOT NULL,
    
    -- Bank Account Details
    bank_account_number VARCHAR(50) NOT NULL,
    bank_account_holder_name VARCHAR(200) NOT NULL,
    bank_ifsc_code VARCHAR(11) NOT NULL,
    bank_name VARCHAR(100),
    bank_branch VARCHAR(100),
    account_type VARCHAR(20), -- 'SAVINGS', 'CURRENT'
    
    -- Mandate Details
    mandate_type VARCHAR(20) DEFAULT 'CREDIT', -- 'CREDIT', 'DEBIT'
    mandate_status VARCHAR(30) NOT NULL, -- 'PENDING', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELLED', 'REJECTED'
    
    -- Limits
    max_amount_per_transaction DECIMAL(15,2),
    max_transactions_per_month INT,
    
    -- Validity
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    
    -- NPCI/Bank References
    sponsor_bank_code VARCHAR(10),
    destination_bank_code VARCHAR(10),
    umrn VARCHAR(50), -- Unique Mandate Reference Number (from NPCI)
    
    -- Registration
    registration_date DATE,
    registered_by VARCHAR(100),
    registration_reference VARCHAR(100),
    
    -- Activation
    activation_date DATE,
    activated_by VARCHAR(100),
    
    -- Rejection/Cancellation
    rejection_reason TEXT,
    rejection_date DATE,
    cancellation_reason TEXT,
    cancellation_date DATE,
    cancelled_by VARCHAR(100),
    
    -- Verification
    verification_status VARCHAR(30), -- 'PENDING', 'VERIFIED', 'FAILED'
    verification_date DATE,
    verification_method VARCHAR(50), -- 'PENNY_DROP', 'DOCUMENT', 'MANUAL'
    
    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_mandate_status CHECK (mandate_status IN 
        ('PENDING', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELLED', 'REJECTED')),
    CONSTRAINT chk_account_type CHECK (account_type IS NULL OR account_type IN 
        ('SAVINGS', 'CURRENT', 'NRE', 'NRO'))
);

CREATE INDEX idx_nach_mandates_pharmacy ON nach_mandates(pharmacy_id);
CREATE INDEX idx_nach_mandates_status ON nach_mandates(mandate_status);
CREATE INDEX idx_nach_mandates_umrn ON nach_mandates(umrn);
CREATE INDEX idx_nach_mandates_validity ON nach_mandates(valid_from, valid_until);

-- NACH Transaction Sequence (for generating transaction references)
CREATE TABLE nach_transaction_sequences (
    id BIGSERIAL PRIMARY KEY,
    sequence_date DATE NOT NULL,
    current_sequence BIGINT NOT NULL,
    prefix VARCHAR(10) NOT NULL,
    last_issued_at TIMESTAMP,
    
    CONSTRAINT uq_nach_seq_date_prefix UNIQUE(sequence_date, prefix)
);

-- NACH Batch Files
CREATE TABLE nach_batch_files (
    id BIGSERIAL PRIMARY KEY,
    batch_file_number VARCHAR(50) UNIQUE NOT NULL,
    payment_batch_id BIGINT REFERENCES payment_batches(id),
    
    -- File Details
    file_name VARCHAR(200) NOT NULL,
    file_path TEXT,
    file_format VARCHAR(20) DEFAULT 'NACH_TEXT', -- 'NACH_TEXT', 'NACH_XML'
    file_size_bytes BIGINT,
    
    -- Batch Metadata
    total_transactions INT NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    settlement_date DATE NOT NULL,
    
    -- Sponsor Bank
    sponsor_bank_code VARCHAR(10),
    sponsor_bank_name VARCHAR(100),
    
    -- Status
    status VARCHAR(30) NOT NULL, -- 'GENERATED', 'SUBMITTED', 'PROCESSING', 'COMPLETED', 'FAILED'
    
    -- Submission
    submitted_to VARCHAR(100), -- Bank/NPCI identifier
    submission_timestamp TIMESTAMP,
    submission_reference VARCHAR(100),
    
    -- Processing
    ack_received_at TIMESTAMP,
    ack_reference VARCHAR(100),
    processing_started_at TIMESTAMP,
    processing_completed_at TIMESTAMP,
    
    -- Results
    success_count INT DEFAULT 0,
    failure_count INT DEFAULT 0,
    pending_count INT DEFAULT 0,
    
    -- Error Details
    error_message TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_nach_batch_status CHECK (status IN 
        ('GENERATED', 'SUBMITTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'PARTIALLY_FAILED'))
);

CREATE INDEX idx_nach_batch_files_payment_batch ON nach_batch_files(payment_batch_id);
CREATE INDEX idx_nach_batch_files_status ON nach_batch_files(status);
CREATE INDEX idx_nach_batch_files_settlement ON nach_batch_files(settlement_date);

-- NACH Transaction Records (individual payment tracking)
CREATE TABLE nach_transactions (
    id BIGSERIAL PRIMARY KEY,
    nach_transaction_id VARCHAR(50) UNIQUE NOT NULL,
    
    -- Links
    payment_transaction_id BIGINT REFERENCES payment_transactions(id),
    nach_batch_file_id BIGINT REFERENCES nach_batch_files(id),
    mandate_id BIGINT REFERENCES nach_mandates(id),
    
    -- Transaction Details
    transaction_reference VARCHAR(50) NOT NULL, -- Our internal reference
    transaction_amount DECIMAL(15,2) NOT NULL,
    transaction_date DATE NOT NULL,
    settlement_date DATE,
    
    -- Beneficiary (Pharmacy)
    beneficiary_name VARCHAR(200) NOT NULL,
    beneficiary_account_number VARCHAR(50) NOT NULL,
    beneficiary_ifsc_code VARCHAR(11) NOT NULL,
    
    -- Status
    status VARCHAR(30) NOT NULL, -- 'PENDING', 'SUBMITTED', 'SUCCESS', 'FAILED', 'REVERSED', 'RETURNED'
    
    -- NPCI/Bank References
    utr_number VARCHAR(50), -- Unique Transaction Reference from bank
    npci_transaction_id VARCHAR(50),
    bank_reference_number VARCHAR(50),
    
    -- Settlement
    settlement_status VARCHAR(30), -- 'PENDING', 'SETTLED', 'FAILED'
    actual_settlement_date DATE,
    settlement_amount DECIMAL(15,2),
    
    -- Failure/Return Details
    failure_code VARCHAR(10),
    failure_reason TEXT,
    return_reason_code VARCHAR(10),
    return_reason TEXT,
    failed_at TIMESTAMP,
    
    -- Retry Management
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    next_retry_at TIMESTAMP,
    original_transaction_id BIGINT REFERENCES nach_transactions(id),
    
    -- Reconciliation
    reconciliation_status VARCHAR(30) DEFAULT 'PENDING', -- 'PENDING', 'MATCHED', 'MISMATCHED'
    reconciled_at TIMESTAMP,
    reconciled_by VARCHAR(100),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_nach_txn_status CHECK (status IN 
        ('PENDING', 'SUBMITTED', 'SUCCESS', 'FAILED', 'REVERSED', 'RETURNED'))
);

CREATE INDEX idx_nach_txn_payment ON nach_transactions(payment_transaction_id);
CREATE INDEX idx_nach_txn_batch ON nach_transactions(nach_batch_file_id);
CREATE INDEX idx_nach_txn_status ON nach_transactions(status);
CREATE INDEX idx_nach_txn_utr ON nach_transactions(utr_number);
CREATE INDEX idx_nach_txn_settlement ON nach_transactions(settlement_date);
CREATE INDEX idx_nach_txn_reconciliation ON nach_transactions(reconciliation_status);

-- NACH Status Updates (event tracking)
CREATE TABLE nach_status_updates (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
    
    nach_transaction_id BIGINT REFERENCES nach_transactions(id),
    nach_batch_file_id BIGINT REFERENCES nach_batch_files(id),
    
    -- Status Change
    event_type VARCHAR(50) NOT NULL, -- 'STATUS_UPDATE', 'UTR_RECEIVED', 'SETTLEMENT', 'FAILURE', 'RETURN'
    previous_status VARCHAR(30),
    new_status VARCHAR(30),
    
    -- Event Details
    event_timestamp TIMESTAMP NOT NULL,
    event_source VARCHAR(50), -- 'BANK_FILE', 'API_CALLBACK', 'WEBHOOK', 'MANUAL'
    
    -- Data
    event_data JSONB,
    utr_number VARCHAR(50),
    bank_reference VARCHAR(100),
    failure_code VARCHAR(10),
    failure_reason TEXT,
    
    -- Processing
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_by VARCHAR(100),
    
    INDEX idx_nach_status_txn (nach_transaction_id),
    INDEX idx_nach_status_batch (nach_batch_file_id),
    INDEX idx_nach_status_event_type (event_type),
    INDEX idx_nach_status_timestamp (event_timestamp)
);

-- NACH Settlement Reports (from bank)
CREATE TABLE nach_settlement_reports (
    id BIGSERIAL PRIMARY KEY,
    report_id VARCHAR(50) UNIQUE NOT NULL,
    
    -- Report Details
    report_date DATE NOT NULL,
    report_type VARCHAR(30), -- 'DAILY', 'INTRADAY', 'MONTHLY'
    settlement_date DATE NOT NULL,
    
    -- Source
    source_bank VARCHAR(100),
    source_file_name VARCHAR(200),
    source_file_path TEXT,
    received_at TIMESTAMP,
    
    -- Summary
    total_transactions INT,
    total_amount DECIMAL(15,2),
    success_count INT,
    failure_count INT,
    
    -- Processing
    processing_status VARCHAR(30), -- 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'
    processed_at TIMESTAMP,
    processed_by VARCHAR(100),
    
    -- Reconciliation
    reconciliation_status VARCHAR(30), -- 'PENDING', 'COMPLETED', 'PARTIAL', 'FAILED'
    matched_transactions INT,
    unmatched_transactions INT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nach_settlement_date ON nach_settlement_reports(settlement_date);
CREATE INDEX idx_nach_settlement_status ON nach_settlement_reports(processing_status);

-- NACH Reconciliation Details
CREATE TABLE nach_reconciliation_details (
    id BIGSERIAL PRIMARY KEY,
    
    settlement_report_id BIGINT REFERENCES nach_settlement_reports(id),
    nach_transaction_id BIGINT REFERENCES nach_transactions(id),
    
    -- Match Status
    match_status VARCHAR(30) NOT NULL, -- 'MATCHED', 'AMOUNT_MISMATCH', 'UTR_MISSING', 'NOT_FOUND'
    
    -- Expected vs Actual
    expected_amount DECIMAL(15,2),
    actual_amount DECIMAL(15,2),
    difference_amount DECIMAL(15,2),
    
    expected_settlement_date DATE,
    actual_settlement_date DATE,
    
    -- Details
    mismatch_reason TEXT,
    resolution_status VARCHAR(30), -- 'PENDING', 'RESOLVED', 'ESCALATED'
    resolution_notes TEXT,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nach_recon_report ON nach_reconciliation_details(settlement_report_id);
CREATE INDEX idx_nach_recon_txn ON nach_reconciliation_details(nach_transaction_id);
CREATE INDEX idx_nach_recon_status ON nach_reconciliation_details(match_status);

-- Update payment_transactions table for NACH
ALTER TABLE payment_transactions
    ADD COLUMN nach_mandate_id BIGINT REFERENCES nach_mandates(id),
    ADD COLUMN nach_transaction_id BIGINT REFERENCES nach_transactions(id),
    ADD COLUMN nach_utr_number VARCHAR(50);

CREATE INDEX idx_payment_txn_nach_mandate ON payment_transactions(nach_mandate_id);
CREATE INDEX idx_payment_txn_nach_utr ON payment_transactions(nach_utr_number);

-- Update pharmacy_payment_config for NACH
ALTER TABLE pharmacy_payment_config
    ADD COLUMN nach_enabled BOOLEAN DEFAULT false,
    ADD COLUMN nach_mandate_id BIGINT REFERENCES nach_mandates(id),
    ADD COLUMN nach_mandate_status VARCHAR(30),
    ADD COLUMN nach_max_amount DECIMAL(15,2),
    ADD COLUMN preferred_method VARCHAR(20) DEFAULT 'CHECK',
    ADD COLUMN payment_threshold_for_nach DECIMAL(15,2),
    ADD COLUMN payment_threshold_for_check DECIMAL(15,2);

ALTER TABLE pharmacy_payment_config
    ADD CONSTRAINT chk_preferred_method CHECK (preferred_method IN ('CHECK', 'NACH'));

-- NACH Return/Rejection Codes Reference (NPCI standard codes)
CREATE TABLE nach_return_codes (
    code VARCHAR(10) PRIMARY KEY,
    description TEXT NOT NULL,
    category VARCHAR(50), -- 'TECHNICAL', 'ACCOUNT_RELATED', 'AUTHORIZATION', 'OTHER'
    is_retryable BOOLEAN DEFAULT false,
    recommended_action TEXT
);

-- Insert standard NACH return codes
INSERT INTO nach_return_codes (code, description, category, is_retryable, recommended_action) VALUES
('01', 'Account Closed', 'ACCOUNT_RELATED', false, 'Update bank details'),
('02', 'No Account/Invalid Account', 'ACCOUNT_RELATED', false, 'Verify and update account number'),
('03', 'Account Blocked/Frozen', 'ACCOUNT_RELATED', false, 'Contact pharmacy to unfreeze account'),
('04', 'Invalid Beneficiary Details', 'ACCOUNT_RELATED', false, 'Verify beneficiary name and details'),
('05', 'Insufficient Funds', 'ACCOUNT_RELATED', true, 'Retry after sufficient balance'),
('06', 'Mandate Cancelled', 'AUTHORIZATION', false, 'Re-register mandate'),
('07', 'Mandate Expired', 'AUTHORIZATION', false, 'Renew mandate'),
('08', 'IFSC Code Invalid', 'TECHNICAL', false, 'Correct IFSC code'),
('09', 'Duplicate Transaction', 'TECHNICAL', false, 'Check for duplicate submission'),
('10', 'Payment Stopped by Customer', 'AUTHORIZATION', false, 'Contact pharmacy'),
('C1', 'Cut-off time exceeded', 'TECHNICAL', true, 'Retry next cycle'),
('T1', 'Technical Error at Bank', 'TECHNICAL', true, 'Retry'),
('T2', 'Timeout', 'TECHNICAL', true, 'Retry');
```

#### Testing Checklist
- [ ] All NACH tables created successfully
- [ ] Foreign keys and constraints working
- [ ] Indexes created and optimized
- [ ] Sample mandate data inserted
- [ ] Return codes table populated

---

### Task 1.2: NACH Entity Classes & Repositories
**Owner**: Backend Team  
**Duration**: 3 days  
**Priority**: Critical

#### Files to Create

```
src/main/java/com/healthcare/edi835/
├── model/
│   ├── NachMandate.java
│   ├── NachTransactionSequence.java
│   ├── NachBatchFile.java
│   ├── NachTransaction.java
│   ├── NachStatusUpdate.java
│   ├── NachSettlementReport.java
│   ├── NachReconciliationDetail.java
│   └── NachReturnCode.java
├── model/enums/
│   ├── MandateStatus.java
│   ├── NachTransactionStatus.java
│   ├── NachBatchStatus.java
│   └── ReconciliationStatus.java
└── repository/
    ├── NachMandateRepository.java
    ├── NachTransactionSequenceRepository.java
    ├── NachBatchFileRepository.java
    ├── NachTransactionRepository.java
    ├── NachStatusUpdateRepository.java
    ├── NachSettlementReportRepository.java
    ├── NachReconciliationDetailRepository.java
    └── NachReturnCodeRepository.java
```

#### Example Entity Implementation

```java
// File: src/main/java/com/healthcare/edi835/model/NachMandate.java

package com.healthcare.edi835.model;

import com.healthcare.edi835.model.enums.MandateStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "nach_mandates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NachMandate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mandate_id", unique = true, nullable = false)
    private String mandateId;

    @Column(name = "pharmacy_id", nullable = false)
    private String pharmacyId;

    @Column(name = "pharmacy_name", nullable = false)
    private String pharmacyName;

    // Bank Account Details
    @Column(name = "bank_account_number", nullable = false)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder_name", nullable = false)
    private String bankAccountHolderName;

    @Column(name = "bank_ifsc_code", nullable = false, length = 11)
    private String bankIfscCode;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "account_type")
    private String accountType;

    // Mandate Details
    @Column(name = "mandate_type")
    private String mandateType = "CREDIT";

    @Enumerated(EnumType.STRING)
    @Column(name = "mandate_status", nullable = false)
    private MandateStatus mandateStatus;

    // Limits
    @Column(name = "max_amount_per_transaction", precision = 15, scale = 2)
    private BigDecimal maxAmountPerTransaction;

    @Column(name = "max_transactions_per_month")
    private Integer maxTransactionsPerMonth;

    // Validity
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    // NPCI/Bank References
    @Column(name = "sponsor_bank_code")
    private String sponsorBankCode;

    @Column(name = "destination_bank_code")
    private String destinationBankCode;

    @Column(name = "umrn")
    private String umrn; // Unique Mandate Reference Number

    // Registration
    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "registered_by")
    private String registeredBy;

    @Column(name = "registration_reference")
    private String registrationReference;

    // Activation
    @Column(name = "activation_date")
    private LocalDate activationDate;

    @Column(name = "activated_by")
    private String activatedBy;

    // Rejection/Cancellation
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "rejection_date")
    private LocalDate rejectionDate;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancellation_date")
    private LocalDate cancellationDate;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    // Verification
    @Column(name = "verification_status")
    private String verificationStatus;

    @Column(name = "verification_date")
    private LocalDate verificationDate;

    @Column(name = "verification_method")
    private String verificationMethod;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if mandate is currently valid and active.
     */
    public boolean isValid() {
        if (mandateStatus != MandateStatus.ACTIVE) {
            return false;
        }
        
        LocalDate now = LocalDate.now();
        return !now.isBefore(validFrom) && !now.isAfter(validUntil);
    }

    /**
     * Checks if transaction amount is within mandate limits.
     */
    public boolean isWithinLimit(BigDecimal amount) {
        if (maxAmountPerTransaction == null) {
            return true;
        }
        return amount.compareTo(maxAmountPerTransaction) <= 0;
    }
}
```

```java
// File: src/main/java/com/healthcare/edi835/model/NachTransaction.java

package com.healthcare.edi835.model;

import com.healthcare.edi835.model.enums.NachTransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "nach_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NachTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nach_transaction_id", unique = true, nullable = false)
    private String nachTransactionId;

    // Links
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private PaymentTransaction paymentTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nach_batch_file_id")
    private NachBatchFile nachBatchFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mandate_id")
    private NachMandate mandate;

    // Transaction Details
    @Column(name = "transaction_reference", nullable = false)
    private String transactionReference;

    @Column(name = "transaction_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    // Beneficiary
    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "beneficiary_account_number", nullable = false)
    private String beneficiaryAccountNumber;

    @Column(name = "beneficiary_ifsc_code", nullable = false)
    private String beneficiaryIfscCode;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NachTransactionStatus status;

    // NPCI/Bank References
    @Column(name = "utr_number")
    private String utrNumber;

    @Column(name = "npci_transaction_id")
    private String npciTransactionId;

    @Column(name = "bank_reference_number")
    private String bankReferenceNumber;

    // Settlement
    @Column(name = "settlement_status")
    private String settlementStatus;

    @Column(name = "actual_settlement_date")
    private LocalDate actualSettlementDate;

    @Column(name = "settlement_amount", precision = 15, scale = 2)
    private BigDecimal settlementAmount;

    // Failure/Return
    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "return_reason_code")
    private String returnReasonCode;

    @Column(name = "return_reason", columnDefinition = "TEXT")
    private String returnReason;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    // Retry Management
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id")
    private NachTransaction originalTransaction;

    // Reconciliation
    @Column(name = "reconciliation_status")
    private String reconciliationStatus = "PENDING";

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "reconciled_by")
    private String reconciledBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if transaction can be retried.
     */
    public boolean canRetry() {
        return status == NachTransactionStatus.FAILED 
            && retryCount < maxRetries
            && isRetryableFailure();
    }

    private boolean isRetryableFailure() {
        // Check if failure code allows retry (e.g., technical errors, timeouts)
        if (failureCode == null) return false;
        return failureCode.matches("C1|T1|T2|05"); // Retryable codes
    }
}
```

```java
// File: src/main/java/com/healthcare/edi835/model/enums/MandateStatus.java

package com.healthcare.edi835.model.enums;

public enum MandateStatus {
    PENDING,      // Awaiting registration
    ACTIVE,       // Active and valid
    SUSPENDED,    // Temporarily suspended
    EXPIRED,      // Validity period ended
    CANCELLED,    // Cancelled by user
    REJECTED      // Rejected by bank
}
```

```java
// File: src/main/java/com/healthcare/edi835/model/enums/NachTransactionStatus.java

package com.healthcare.edi835.model.enums;

public enum NachTransactionStatus {
    PENDING,      // Created but not submitted
    SUBMITTED,    // Submitted to bank/NPCI
    SUCCESS,      // Successfully settled
    FAILED,       // Failed permanently
    REVERSED,     // Reversed after success
    RETURNED      // Returned by bank
}
```

#### Repository Examples

```java
// File: src/main/java/com/healthcare/edi835/repository/NachMandateRepository.java

package com.healthcare.edi835.repository;

import com.healthcare.edi835.model.NachMandate;
import com.healthcare.edi835.model.enums.MandateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NachMandateRepository extends JpaRepository<NachMandate, Long> {

    Optional<NachMandate> findByPharmacyId(String pharmacyId);
    
    Optional<NachMandate> findByMandateId(String mandateId);
    
    Optional<NachMandate> findByUmrn(String umrn);
    
    List<NachMandate> findByMandateStatus(MandateStatus status);
    
    @Query("SELECT m FROM NachMandate m WHERE m.pharmacyId = :pharmacyId " +
           "AND m.mandateStatus = 'ACTIVE' " +
           "AND m.validFrom <= :currentDate " +
           "AND m.validUntil >= :currentDate")
    Optional<NachMandate> findActiveMandate(
        @Param("pharmacyId") String pharmacyId,
        @Param("currentDate") LocalDate currentDate
    );
    
    @Query("SELECT m FROM NachMandate m WHERE m.mandateStatus = 'ACTIVE' " +
           "AND m.validUntil BETWEEN :startDate AND :endDate")
    List<NachMandate> findMandatesExpiringBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT COUNT(m) FROM NachMandate m WHERE m.pharmacyId = :pharmacyId " +
           "AND m.mandateStatus = 'ACTIVE'")
    long countActiveMandatesByPharmacy(@Param("pharmacyId") String pharmacyId);
}
```

```java
// File: src/main/java/com/healthcare/edi835/repository/NachTransactionRepository.java

package com.healthcare.edi835.repository;

import com.healthcare.edi835.model.NachTransaction;
import com.healthcare.edi835.model.enums.NachTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NachTransactionRepository extends JpaRepository<NachTransaction, Long> {

    Optional<NachTransaction> findByNachTransactionId(String nachTransactionId);
    
    Optional<NachTransaction> findByUtrNumber(String utrNumber);
    
    Optional<NachTransaction> findByTransactionReference(String transactionReference);
    
    List<NachTransaction> findByStatus(NachTransactionStatus status);
    
    List<NachTransaction> findByNachBatchFileId(Long batchFileId);
    
    @Query("SELECT t FROM NachTransaction t WHERE t.status = :status " +
           "AND t.retryCount < t.maxRetries " +
           "AND (t.nextRetryAt IS NULL OR t.nextRetryAt <= :now)")
    List<NachTransaction> findRetryableFailed(
        @Param("status") NachTransactionStatus status,
        @Param("now") LocalDateTime now
    );
    
    @Query("SELECT t FROM NachTransaction t WHERE t.settlementDate = :settlementDate " +
           "AND t.reconciliationStatus = 'PENDING'")
    List<NachTransaction> findUnreconciledBySettlementDate(
        @Param("settlementDate") LocalDate settlementDate
    );
    
    @Query("SELECT COUNT(t), SUM(t.transactionAmount) " +
           "FROM NachTransaction t WHERE t.status = :status " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    Object[] getCountAndSumByStatusAndDateRange(
        @Param("status") NachTransactionStatus status,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
```

#### Testing Checklist
- [ ] All entity classes compile
- [ ] JPA mappings correct
- [ ] Repositories work correctly
- [ ] Queries tested
- [ ] Relationships validated

---

## Week 3-4: NACH Services & Gateway Integration

### Task 2.1: NACH Mandate Management Service
**Owner**: Backend Team  
**Duration**: 4 days  
**Priority**: Critical

#### Implementation

```java
// File: src/main/java/com/healthcare/edi835/service/NachMandateService.java

package com.healthcare.edi835.service;

import com.healthcare.edi835.model.NachMandate;
import com.healthcare.edi835.model.enums.MandateStatus;
import com.healthcare.edi835.repository.NachMandateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing NACH mandates.
 * 
 * Handles mandate registration, activation, validation, and lifecycle management.
 */
@Slf4j
@Service
public class NachMandateService {

    private final NachMandateRepository mandateRepo;
    private final BankAccountVerificationService accountVerificationService;
    private final NachGatewayClient nachGateway;

    public NachMandateService(
            NachMandateRepository mandateRepo,
            BankAccountVerificationService accountVerificationService,
            NachGatewayClient nachGateway) {
        this.mandateRepo = mandateRepo;
        this.accountVerificationService = accountVerificationService;
        this.nachGateway = nachGateway;
    }

    /**
     * Registers a new NACH mandate for a pharmacy.
     */
    @Transactional
    public NachMandate registerMandate(NachMandateRequest request) {
        log.info("Registering NACH mandate for pharmacy: {}", request.getPharmacyId());

        // Check if active mandate already exists
        Optional<NachMandate> existing = mandateRepo.findActiveMandate(
            request.getPharmacyId(), LocalDate.now());
        
        if (existing.isPresent()) {
            throw new IllegalStateException(
                "Active mandate already exists for pharmacy: " + request.getPharmacyId());
        }

        // Verify bank account (penny drop or document verification)
        BankAccountVerificationResult verification = 
            accountVerificationService.verifyAccount(
                request.getBankAccountNumber(),
                request.getBankIfscCode(),
                request.getBankAccountHolderName()
            );

        if (!verification.isVerified()) {
            throw new BankAccountVerificationException(
                "Bank account verification failed: " + verification.getFailureReason());
        }

        // Create mandate
        NachMandate mandate = NachMandate.builder()
            .mandateId(generateMandateId())
            .pharmacyId(request.getPharmacyId())
            .pharmacyName(request.getPharmacyName())
            .bankAccountNumber(request.getBankAccountNumber())
            .bankAccountHolderName(verification.getAccountHolderName()) // Use verified name
            .bankIfscCode(request.getBankIfscCode())
            .bankName(verification.getBankName())
            .bankBranch(verification.getBranchName())
            .accountType(request.getAccountType())
            .mandateType("CREDIT")
            .mandateStatus(MandateStatus.PENDING)
            .maxAmountPerTransaction(request.getMaxAmountPerTransaction())
            .maxTransactionsPerMonth(request.getMaxTransactionsPerMonth())
            .validFrom(request.getValidFrom())
            .validUntil(request.getValidUntil())
            .registrationDate(LocalDate.now())
            .registeredBy(request.getRegisteredBy())
            .verificationStatus("VERIFIED")
            .verificationDate(LocalDate.now())
            .verificationMethod(verification.getMethod())
            .build();

        mandate = mandateRepo.save(mandate);

        // Submit to NPCI/Bank for registration
        try {
            MandateRegistrationResponse response = nachGateway.registerMandate(mandate);
            
            mandate.setUmrn(response.getUmrn());
            mandate.setRegistrationReference(response.getRegistrationReference());
            mandate.setSponsorBankCode(response.getSponsorBankCode());
            mandate.setDestinationBankCode(response.getDestinationBankCode());
            
            mandateRepo.save(mandate);
            
            log.info("Mandate registered with NPCI: {} (UMRN: {})", 
                mandate.getMandateId(), mandate.getUmrn());
            
        } catch (Exception e) {
            log.error("Failed to register mandate with NPCI: {}", mandate.getMandateId(), e);
            mandate.setMandateStatus(MandateStatus.REJECTED);
            mandate.setRejectionReason("NPCI registration failed: " + e.getMessage());
            mandate.setRejectionDate(LocalDate.now());
            mandateRepo.save(mandate);
            throw new MandateRegistrationException("Failed to register mandate", e);
        }

        return mandate;
    }

    /**
     * Activates a pending mandate after bank approval.
     */
    @Transactional
    public NachMandate activateMandate(String mandateId, String activatedBy) {
        log.info("Activating mandate: {}", mandateId);

        NachMandate mandate = mandateRepo.findByMandateId(mandateId)
            .orElseThrow(() -> new IllegalStateException(
                "Mandate not found: " + mandateId));

        if (mandate.getMandateStatus() != MandateStatus.PENDING) {
            throw new IllegalStateException(
                "Mandate not in PENDING status: " + mandate.getMandateStatus());
        }

        if (mandate.getUmrn() == null || mandate.getUmrn().isEmpty()) {
            throw new IllegalStateException(
                "Mandate does not have UMRN assigned");
        }

        mandate.setMandateStatus(MandateStatus.ACTIVE);
        mandate.setActivationDate(LocalDate.now());
        mandate.setActivatedBy(activatedBy);

        mandateRepo.save(mandate);

        log.info("Mandate activated: {} (UMRN: {})", mandateId, mandate.getUmrn());

        return mandate;
    }

    /**
     * Validates if a mandate can be used for a transaction.
     */
    public MandateValidationResult validateMandateForTransaction(
            String pharmacyId, 
            BigDecimal amount) {
        
        Optional<NachMandate> mandateOpt = mandateRepo.findActiveMandate(
            pharmacyId, LocalDate.now());

        if (mandateOpt.isEmpty()) {
            return MandateValidationResult.failure(
                "No active mandate found for pharmacy: " + pharmacyId);
        }

        NachMandate mandate = mandateOpt.get();

        // Check validity period
        if (!mandate.isValid()) {
            return MandateValidationResult.failure(
                "Mandate is not valid or has expired");
        }

        // Check amount limit
        if (!mandate.isWithinLimit(amount)) {
            return MandateValidationResult.failure(
                String.format("Amount %.2f exceeds mandate limit %.2f",
                    amount, mandate.getMaxAmountPerTransaction()));
        }

        // Check monthly transaction limit
        // (would need to query transaction history)

        return MandateValidationResult.success(mandate);
    }

    /**
     * Cancels a mandate.
     */
    @Transactional
    public void cancelMandate(String mandateId, String reason, String cancelledBy) {
        log.info("Cancelling mandate: {}", mandateId);

        NachMandate mandate = mandateRepo.findByMandateId(mandateId)
            .orElseThrow(() -> new IllegalStateException(
                "Mandate not found: " + mandateId));

        // Notify NPCI/Bank of cancellation
        try {
            nachGateway.cancelMandate(mandate.getUmrn());
        } catch (Exception e) {
            log.warn("Failed to notify NPCI of cancellation for mandate: {}", 
                mandateId, e);
            // Continue with local cancellation
        }

        mandate.setMandateStatus(MandateStatus.CANCELLED);
        mandate.setCancellationReason(reason);
        mandate.setCancellationDate(LocalDate.now());
        mandate.setCancelledBy(cancelledBy);

        mandateRepo.save(mandate);

        log.info("Mandate cancelled: {}", mandateId);
    }

    /**
     * Finds mandates expiring soon for renewal alerts.
     */
    public List<NachMandate> findMandatesExpiringSoon(int daysAhead) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);
        
        return mandateRepo.findMandatesExpiringBetween(startDate, endDate);
    }

    private String generateMandateId() {
        return "MDT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
```

#### Testing Checklist
- [ ] Mandate registration works
- [ ] Bank account verification integrated
- [ ] Mandate activation successful
- [ ] Validation logic correct
- [ ] Cancellation works

---

### Task 2.2: NACH Transaction Reference Allocation
**Owner**: Backend Team  
**Duration**: 3 days  
**Priority**: Critical

#### Implementation

```java
// File: Enhance PaymentReferenceAllocationService with NACH support

/**
 * Allocates NACH transaction reference for a pharmacy payment.
 */
@Transactional
public PaymentReference allocateNachTransactionReference(
        Long bucketId,
        String pharmacyId,
        PharmacyPaymentGroup group) {
    
    log.debug("Allocating NACH transaction reference for pharmacy: {}", pharmacyId);
    
    LocalDate today = LocalDate.now();
    String prefix = "NACH";
    
    // Get or create sequence for today
    NachTransactionSequence sequence = nachSeqRepo
        .findBySequenceDateAndPrefix(today, prefix)
        .orElseGet(() -> createNachSequence(today, prefix));
    
    // Increment and get next sequence number
    long nextSequence = sequence.getCurrentSequence() + 1;
    sequence.setCurrentSequence(nextSequence);
    sequence.setLastIssuedAt(LocalDateTime.now());
    nachSeqRepo.save(sequence);
    
    // Format transaction reference: NACH-YYYYMMDD-NNNNNN
    String transactionReference = String.format("%s-%s-%06d",
        prefix,
        today.format(DateTimeFormatter.BASIC_ISO_DATE),
        nextSequence
    );
    
    // Create allocation record
    PaymentReferenceAllocation allocation = PaymentReferenceAllocation.builder()
        .referenceType(ReferenceType.NACH_TRN)
        .referenceNumber(transactionReference)
        .allocatedToBucketId(bucketId)
        .allocatedToPharmacyId(pharmacyId)
        .allocationStatus(AllocationStatus.RESERVED)
        .reservedAt(LocalDateTime.now())
        .expiresAt(LocalDateTime.now().plusHours(24))
        .build();
    
    referenceRepo.save(allocation);
    
    PaymentReference reference = PaymentReference.builder()
        .allocationId(allocation.getId())
        .referenceType(ReferenceType.NACH_TRN)
        .referenceNumber(transactionReference)
        .pharmacyId(pharmacyId)
        .paymentAmount(group.getTotalAmount())
        .build();
    
    log.info("Allocated NACH reference: {} for pharmacy: {}", 
        transactionReference, pharmacyId);
    
    return reference;
}

private NachTransactionSequence createNachSequence(LocalDate date, String prefix) {
    NachTransactionSequence sequence = NachTransactionSequence.builder()
        .sequenceDate(date)
        .currentSequence(0L)
        .prefix(prefix)
        .build();
    return nachSeqRepo.save(sequence);
}
```

---

### Task 2.3: NACH Payment Service
**Owner**: Backend Team  
**Duration**: 5 days  
**Priority**: Critical

#### Implementation

```java
// File: src/main/java/com/healthcare/edi835/service/NachPaymentService.java

package com.healthcare.edi835.service;

import com.healthcare.edi835.model.*;
import com.healthcare.edi835.model.enums.*;
import com.healthcare.edi835.integration.NachGatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for processing NACH payments.
 * 
 * Handles:
 * - NACH file generation
 * - Submission to bank/NPCI
 * - Status tracking
 * - Retry management
 */
@Slf4j
@Service
public class NachPaymentService {

    private final NachGatewayClient nachGateway;
    private final NachTransactionRepository nachTxnRepo;
    private final NachBatchFileRepository batchFileRepo;
    private final PaymentTransactionRepository paymentTxnRepo;
    private final NachMandateService mandateService;
    private final NachFileGenerator nachFileGenerator;
    private final PaymentStatusEventRepository eventRepo;

    /**
     * Submits NACH batch with pre-allocated transaction references.
     */
    @Transactional
    public NachBatchFile submitBatchWithReferences(
            PaymentBatch paymentBatch,
            List<PaymentTransaction> transactions,
            Map<String, PaymentReference> paymentReferences) {
        
        log.info("Submitting NACH batch for payment batch: {}", 
            paymentBatch.getBatchNumber());

        // Validate all mandates before proceeding
        validateMandatesForTransactions(transactions);

        // Create NACH transactions
        List<NachTransaction> nachTransactions = 
            createNachTransactions(paymentBatch, transactions, paymentReferences);

        // Generate NACH file
        NachBatchFile batchFile = generateNachBatchFile(
            paymentBatch, nachTransactions);

        // Submit to bank/NPCI
        try {
            NachSubmissionResponse response = nachGateway.submitBatch(batchFile);
            
            // Update batch file status
            batchFile.setStatus(NachBatchStatus.SUBMITTED);
            batchFile.setSubmittedTo(response.getSubmittedTo());
            batchFile.setSubmissionTimestamp(LocalDateTime.now());
            batchFile.setSubmissionReference(response.getSubmissionReference());
            batchFile.setAckReference(response.getAckReference());
            batchFileRepo.save(batchFile);
            
            // Update payment batch
            paymentBatch.setGatewayBatchId(response.getBatchId());
            paymentBatch.setSubmissionTimestamp(LocalDateTime.now());
            paymentBatch.setStatus(PaymentBatchStatus.SUBMITTED);
            
            // Update NACH transactions
            for (NachTransaction nachTxn : nachTransactions) {
                nachTxn.setStatus(NachTransactionStatus.SUBMITTED);
                nachTxnRepo.save(nachTxn);
                
                // Update payment transaction
                PaymentTransaction paymentTxn = nachTxn.getPaymentTransaction();
                paymentTxn.setStatus(PaymentTransactionStatus.INITIATED);
                paymentTxn.setNachTransactionId(nachTxn.getId());
                paymentTxnRepo.save(paymentTxn);
                
                recordStatusEvent(paymentTxn, "NACH_SUBMITTED", 
                    "NACH transaction submitted to bank");
            }
            
            log.info("NACH batch submitted successfully: {} with {} transactions",
                batchFile.getBatchFileNumber(), nachTransactions.size());
            
            return batchFile;
            
        } catch (Exception e) {
            log.error("Failed to submit NACH batch: {}", 
                batchFile.getBatchFileNumber(), e);
            
            batchFile.setStatus(NachBatchStatus.FAILED);
            batchFile.setErrorMessage(e.getMessage());
            batchFileRepo.save(batchFile);
            
            throw new NachSubmissionException("Failed to submit NACH batch", e);
        }
    }

    /**
     * Validates that all transactions have valid mandates.
     */
    private void validateMandatesForTransactions(List<PaymentTransaction> transactions) {
        for (PaymentTransaction txn : transactions) {
            MandateValidationResult validation = mandateService
                .validateMandateForTransaction(txn.getPharmacyId(), txn.getPaymentAmount());
            
            if (!validation.isValid()) {
                throw new InvalidMandateException(
                    String.format("Invalid mandate for pharmacy %s: %s",
                        txn.getPharmacyId(), validation.getFailureReason()));
            }
        }
    }

    /**
     * Creates NACH transaction records from payment transactions.
     */
    private List<NachTransaction> createNachTransactions(
            PaymentBatch paymentBatch,
            List<PaymentTransaction> paymentTransactions,
            Map<String, PaymentReference> paymentReferences) {
        
        List<NachTransaction> nachTransactions = new ArrayList<>();
        
        for (PaymentTransaction paymentTxn : paymentTransactions) {
            PaymentReference reference = paymentReferences.get(paymentTxn.getPharmacyId());
            
            // Get mandate
            NachMandate mandate = mandateService.getActiveMandateByPharmacy(
                paymentTxn.getPharmacyId());
            
            NachTransaction nachTxn = NachTransaction.builder()
                .nachTransactionId(UUID.randomUUID().toString())
                .paymentTransaction(paymentTxn)
                .mandate(mandate)
                .transactionReference(reference.getReferenceNumber())
                .transactionAmount(paymentTxn.getPaymentAmount())
                .transactionDate(LocalDate.now())
                .settlementDate(calculateSettlementDate())
                .beneficiaryName(paymentTxn.getPharmacyName())
                .beneficiaryAccountNumber(mandate.getBankAccountNumber())
                .beneficiaryIfscCode(mandate.getBankIfscCode())
                .status(NachTransactionStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .build();
            
            nachTransactions.add(nachTxnRepo.save(nachTxn));
        }
        
        return nachTransactions;
    }

    /**
     * Generates NACH batch file.
     */
    private NachBatchFile generateNachBatchFile(
            PaymentBatch paymentBatch,
            List<NachTransaction> transactions) {
        
        String batchFileNumber = generateBatchFileNumber();
        LocalDate settlementDate = calculateSettlementDate();
        
        // Create batch file record
        NachBatchFile batchFile = NachBatchFile.builder()
            .batchFileNumber(batchFileNumber)
            .paymentBatch(paymentBatch)
            .totalTransactions(transactions.size())
            .totalAmount(calculateTotalAmount(transactions))
            .settlementDate(settlementDate)
            .status(NachBatchStatus.GENERATED)
            .build();
        
        batchFile = batchFileRepo.save(batchFile);
        
        // Link transactions to batch file
        for (NachTransaction txn : transactions) {
            txn.setNachBatchFile(batchFile);
            nachTxnRepo.save(txn);
        }
        
        // Generate physical file
        try {
            NachFileContent fileContent = nachFileGenerator.generateFile(
                batchFile, transactions);
            
            batchFile.setFileName(fileContent.getFileName());
            batchFile.setFilePath(fileContent.getFilePath());
            batchFile.setFileSize(fileContent.getFileSize());
            batchFile.setFileFormat(fileContent.getFormat());
            
            batchFileRepo.save(batchFile);
            
            log.info("Generated NACH file: {} with {} transactions",
                fileContent.getFileName(), transactions.size());
            
        } catch (Exception e) {
            log.error("Failed to generate NACH file for batch: {}", 
                batchFileNumber, e);
            throw new NachFileGenerationException("Failed to generate NACH file", e);
        }
        
        return batchFile;
    }

    /**
     * Handles status update callback from bank/NPCI.
     */
    @Transactional
    public void handleStatusUpdate(NachStatusUpdateRequest update) {
        log.info("Received NACH status update for transaction: {}", 
            update.getTransactionReference());

        NachTransaction nachTxn = nachTxnRepo
            .findByTransactionReference(update.getTransactionReference())
            .orElseThrow(() -> new IllegalStateException(
                "NACH transaction not found: " + update.getTransactionReference()));

        NachTransactionStatus previousStatus = nachTxn.getStatus();
        
        switch (update.getStatus()) {
            case "SUCCESS" -> handleSuccessfulTransaction(nachTxn, update);
            case "FAILED" -> handleFailedTransaction(nachTxn, update);
            case "RETURNED" -> handleReturnedTransaction(nachTxn, update);
            case "REVERSED" -> handleReversedTransaction(nachTxn, update);
            default -> log.warn("Unknown status received: {}", update.getStatus());
        }
        
        // Record status update event
        recordNachStatusEvent(nachTxn, previousStatus, update);
    }

    private void handleSuccessfulTransaction(
            NachTransaction nachTxn,
            NachStatusUpdateRequest update) {
        
        nachTxn.setStatus(NachTransactionStatus.SUCCESS);
        nachTxn.setUtrNumber(update.getUtrNumber());
        nachTxn.setNpciTransactionId(update.getNpciTransactionId());
        nachTxn.setBankReferenceNumber(update.getBankReference());
        nachTxn.setActualSettlementDate(update.getSettlementDate());
        nachTxn.setSettlementAmount(update.getSettlementAmount());
        nachTxn.setSettlementStatus("SETTLED");
        
        nachTxnRepo.save(nachTxn);
        
        // Update payment transaction
        PaymentTransaction paymentTxn = nachTxn.getPaymentTransaction();
        paymentTxn.setStatus(PaymentTransactionStatus.SUCCESS);
        paymentTxn.setNachUtrNumber(update.getUtrNumber());
        paymentTxn.setSettlementDate(update.getSettlementDate());
        paymentTxn.setSettlementStatus("SETTLED");
        paymentTxnRepo.save(paymentTxn);
        
        recordStatusEvent(paymentTxn, "NACH_SUCCESS", 
            "NACH payment successful, UTR: " + update.getUtrNumber());
        
        log.info("NACH transaction successful: {} (UTR: {})",
            nachTxn.getTransactionReference(), update.getUtrNumber());
    }

    private void handleFailedTransaction(
            NachTransaction nachTxn,
            NachStatusUpdateRequest update) {
        
        nachTxn.setStatus(NachTransactionStatus.FAILED);
        nachTxn.setFailureCode(update.getFailureCode());
        nachTxn.setFailureReason(update.getFailureReason());
        nachTxn.setFailedAt(LocalDateTime.now());
        
        // Check if retryable
        if (nachTxn.canRetry()) {
            nachTxn.setRetryCount(nachTxn.getRetryCount() + 1);
            nachTxn.setNextRetryAt(calculateNextRetryTime(nachTxn.getRetryCount()));
            
            log.info("NACH transaction failed, will retry: {} (Attempt {}/{})",
                nachTxn.getTransactionReference(), 
                nachTxn.getRetryCount(), 
                nachTxn.getMaxRetries());
        } else {
            // Permanent failure
            PaymentTransaction paymentTxn = nachTxn.getPaymentTransaction();
            paymentTxn.setStatus(PaymentTransactionStatus.FAILED);
            paymentTxn.setFailureReason(update.getFailureReason());
            paymentTxnRepo.save(paymentTxn);
            
            recordStatusEvent(paymentTxn, "NACH_FAILED", 
                "NACH payment failed: " + update.getFailureReason());
            
            log.error("NACH transaction permanently failed: {} - {}",
                nachTxn.getTransactionReference(), update.getFailureReason());
        }
        
        nachTxnRepo.save(nachTxn);
    }

    private void handleReturnedTransaction(
            NachTransaction nachTxn,
            NachStatusUpdateRequest update) {
        
        nachTxn.setStatus(NachTransactionStatus.RETURNED);
        nachTxn.setReturnReasonCode(update.getReturnCode());
        nachTxn.setReturnReason(update.getReturnReason());
        nachTxnRepo.save(nachTxn);
        
        PaymentTransaction paymentTxn = nachTxn.getPaymentTransaction();
        paymentTxn.setStatus(PaymentTransactionStatus.FAILED);
        paymentTxn.setFailureReason("Returned by bank: " + update.getReturnReason());
        paymentTxnRepo.save(paymentTxn);
        
        recordStatusEvent(paymentTxn, "NACH_RETURNED", 
            "NACH payment returned: " + update.getReturnReason());
        
        log.warn("NACH transaction returned: {} - {}",
            nachTxn.getTransactionReference(), update.getReturnReason());
    }

    private LocalDate calculateSettlementDate() {
        // T+1 or T+2 depending on cut-off time and bank
        LocalDate today = LocalDate.now();
        // Simple logic: T+1 for now
        return today.plusDays(1);
    }

    private LocalDateTime calculateNextRetryTime(int retryCount) {
        // Exponential backoff: 1 hour, 4 hours, 24 hours
        long hours = (long) Math.pow(2, retryCount);
        return LocalDateTime.now().plusHours(Math.min(hours, 24));
    }

    private String generateBatchFileNumber() {
        return String.format("NACH-%s-%06d",
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
            new Random().nextInt(999999));
    }

    private BigDecimal calculateTotalAmount(List<NachTransaction> transactions) {
        return transactions.stream()
            .map(NachTransaction::getTransactionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void recordStatusEvent(
            PaymentTransaction txn,
            String eventType,
            String message) {
        
        PaymentStatusEvent event = PaymentStatusEvent.builder()
            .paymentTransaction(txn)
            .eventType(eventType)
            .eventData(Map.of("message", message))
            .triggeredBy("SYSTEM")
            .build();
        
        eventRepo.save(event);
    }

    private void recordNachStatusEvent(
            NachTransaction nachTxn,
            NachTransactionStatus previousStatus,
            NachStatusUpdateRequest update) {
        
        NachStatusUpdate event = NachStatusUpdate.builder()
            .nachTransaction(nachTxn)
            .eventType("STATUS_UPDATE")
            .previousStatus(previousStatus != null ? previousStatus.name() : null)
            .newStatus(nachTxn.getStatus().name())
            .eventTimestamp(LocalDateTime.now())
            .eventSource(update.getSource())
            .utrNumber(update.getUtrNumber())
            .bankReference(update.getBankReference())
            .failureCode(update.getFailureCode())
            .failureReason(update.getFailureReason())
            .processedBy("SYSTEM")
            .build();
        
        // Save to nach_status_updates table
    }
}
```

#### Testing Checklist
- [ ] NACH file generation works
- [ ] Submission to gateway successful
- [ ] Status updates handled correctly
- [ ] Retry logic works
- [ ] Error handling robust

---

### Task 2.4: NACH File Generator
**Owner**: Backend Team  
**Duration**: 4 days  
**Priority**: Critical

#### Implementation

```java
// File: src/main/java/com/healthcare/edi835/service/NachFileGenerator.java

package com.healthcare.edi835.service;

import com.healthcare.edi835.model.NachBatchFile;
import com.healthcare.edi835.model.NachTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates NACH format files for submission to bank/NPCI.
 * 
 * NACH file format:
 * - H Record: Header
 * - D Records: Detail (one per transaction)
 * - T Record: Trailer
 */
@Slf4j
@Service
public class NachFileGenerator {

    private static final DateTimeFormatter DATE_FORMAT = 
        DateTimeFormatter.ofPattern("ddMMyyyy");
    
    @Value("${nach.output-directory}")
    private String outputDirectory;
    
    @Value("${nach.sponsor-bank-code}")
    private String sponsorBankCode;
    
    @Value("${nach.sponsor-user-number}")
    private String sponsorUserNumber;

    /**
     * Generates NACH text file.
     */
    public NachFileContent generateFile(
            NachBatchFile batchFile,
            List<NachTransaction> transactions) throws IOException {
        
        log.info("Generating NACH file for batch: {}", batchFile.getBatchFileNumber());
        
        String fileName = generateFileName(batchFile);
        Path filePath = Paths.get(outputDirectory, fileName);
        
        Files.createDirectories(filePath.getParent());
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            // Write header record
            writeHeaderRecord(writer, batchFile, transactions);
            
            // Write detail records
            for (NachTransaction txn : transactions) {
                writeDetailRecord(writer, txn);
            }
            
            // Write trailer record
            writeTrailerRecord(writer, batchFile, transactions);
        }
        
        long fileSize = Files.size(filePath);
        
        log.info("NACH file generated: {} (size: {} bytes)", fileName, fileSize);
        
        return NachFileContent.builder()
            .fileName(fileName)
            .filePath(filePath.toString())
            .fileSize(fileSize)
            .format("NACH_TEXT")
            .build();
    }

    /**
     * Writes NACH header record (H).
     * 
     * Format: H|SponsorBankCode|UserNumber|Date|FileSequence|...|
     */
    private void writeHeaderRecord(
            BufferedWriter writer,
            NachBatchFile batchFile,
            List<NachTransaction> transactions) throws IOException {
        
        StringBuilder header = new StringBuilder("H");
        
        // Sponsor bank code
        header.append("|").append(padRight(sponsorBankCode, 10));
        
        // User number
        header.append("|").append(padRight(sponsorUserNumber, 10));
        
        // File date
        header.append("|").append(LocalDate.now().format(DATE_FORMAT));
        
        // Settlement date
        header.append("|").append(batchFile.getSettlementDate().format(DATE_FORMAT));
        
        // Total transactions
        header.append("|").append(String.format("%07d", transactions.size()));
        
        // Total amount (in paise)
        long totalAmountPaise = batchFile.getTotalAmount()
            .multiply(new BigDecimal("100"))
            .longValue();
        header.append("|").append(String.format("%015d", totalAmountPaise));
        
        // Transaction type (Credit)
        header.append("|").append("C");
        
        // Product type (NACH Credit)
        header.append("|").append("CREDIT");
        
        writer.write(header.toString());
        writer.newLine();
    }

    /**
     * Writes NACH detail record (D).
     * 
     * Format: D|TransactionRef|BeneficiaryName|AccountNo|IFSC|Amount|...|
     */
    private void writeDetailRecord(
            BufferedWriter writer,
            NachTransaction txn) throws IOException {
        
        StringBuilder detail = new StringBuilder("D");
        
        // Transaction reference
        detail.append("|").append(padRight(txn.getTransactionReference(), 30));
        
        // Beneficiary name
        detail.append("|").append(padRight(txn.getBeneficiaryName(), 40));
        
        // Account number
        detail.append("|").append(padRight(txn.getBeneficiaryAccountNumber(), 20));
        
        // IFSC code
        detail.append("|").append(padRight(txn.getBeneficiaryIfscCode(), 11));
        
        // Amount (in paise)
        long amountPaise = txn.getTransactionAmount()
            .multiply(new BigDecimal("100"))
            .longValue();
        detail.append("|").append(String.format("%013d", amountPaise));
        
        // Transaction date
        detail.append("|").append(txn.getTransactionDate().format(DATE_FORMAT));
        
        // UMRN (Unique Mandate Reference Number)
        String umrn = txn.getMandate() != null ? txn.getMandate().getUmrn() : "";
        detail.append("|").append(padRight(umrn, 20));
        
        // Purpose code (for claim payment)
        detail.append("|").append("CLMPMT");
        
        writer.write(detail.toString());
        writer.newLine();
    }

    /**
     * Writes NACH trailer record (T).
     * 
     * Format: T|TotalCount|TotalAmount|Hash|
     */
    private void writeTrailerRecord(
            BufferedWriter writer,
            NachBatchFile batchFile,
            List<NachTransaction> transactions) throws IOException {
        
        StringBuilder trailer = new StringBuilder("T");
        
        // Total transaction count
        trailer.append("|").append(String.format("%07d", transactions.size()));
        
        // Total amount (in paise)
        long totalAmountPaise = batchFile.getTotalAmount()
            .multiply(new BigDecimal("100"))
            .longValue();
        trailer.append("|").append(String.format("%015d", totalAmountPaise));
        
        // Hash/checksum (simple sum of all amounts for validation)
        long hash = transactions.stream()
            .mapToLong(t -> t.getTransactionAmount()
                .multiply(new BigDecimal("100"))
                .longValue())
            .sum();
        trailer.append("|").append(String.format("%015d", hash));
        
        writer.write(trailer.toString());
        writer.newLine();
    }

    private String generateFileName(NachBatchFile batchFile) {
        return String.format("NACH_%s_%s.txt",
            batchFile.getBatchFileNumber(),
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    private String padRight(String str, int length) {
        if (str == null) str = "";
        return String.format("%-" + length + "s", str).substring(0, length);
    }
}
```

#### Configuration

```yaml
# File: application.yml

nach:
  output-directory: ${NACH_OUTPUT_DIR:/data/nach/output}
  sponsor-bank-code: ${NACH_SPONSOR_BANK:HDFC0000001}
  sponsor-user-number: ${NACH_USER_NUMBER:USER001}
  gateway:
    url: ${NACH_GATEWAY_URL:https://nach-gateway.example.com/api}
    api-key: ${NACH_API_KEY}
    timeout-ms: 60000
  reconciliation:
    enabled: true
    report-directory: ${NACH_REPORT_DIR:/data/nach/reports}
    auto-process: true
```

#### Testing Checklist
- [ ] NACH file format correct
- [ ] Header/Detail/Trailer records accurate
- [ ] File naming convention followed
- [ ] File validation passes
- [ ] Compatible with bank systems

---

## Week 5: EDI 835 Enhancement for NACH

### Task 3.1: Update EDI Generator for NACH Payments
**Owner**: Backend Team  
**Duration**: 4 days  
**Priority**: Critical

#### Enhancements Needed

1. **BPR Segment for ACH/NACH**
2. **TRN Segment with NACH Reference**
3. **REF Segment with NACH Transaction ID**

#### Implementation

```java
// File: Enhance Edi835GeneratorService.java

/**
 * Writes BPR segment for NACH/ACH payment.
 */
private void writeBPRSegmentForNachPayment(
        EDIStreamWriter writer,
        RemittanceAdvice remittance,
        Map<String, PaymentReference> paymentReferences) throws Exception {
    
    writer.writeStartSegment("BPR");
    
    // BPR01 - Transaction handling code
    writer.writeElement("I"); // I = Information only (for NACH credits)
    
    // BPR02 - Total payment amount
    writer.writeElement(formatAmount(remittance.getTotalPaidAmount()));
    
    // BPR03 - Credit/Debit flag
    writer.writeElement("C"); // C = Credit
    
    // BPR04 - Payment method code
    writer.writeElement("ACH"); // ACH for NACH payments
    
    // BPR05 - Payment format code
    writer.writeElement("CCP"); // CCP = Cash Concentration/Disbursement Plus
    
    // BPR06 - DFI ID Number Qualifier (Sender)
    writer.writeElement("01"); // 01 = ABA routing number
    
    // BPR07 - Sender DFI Identifier (Bank routing number)
    writer.writeElement(remittance.getPayerRoutingNumber());
    
    // BPR08 - Account Number Qualifier (Sender)
    writer.writeElement("DA"); // DA = Demand Deposit
    
    // BPR09 - Sender Account Number
    writer.writeElement(remittance.getPayerAccountNumber());
    
    // BPR10 - Originating Company Identifier
    writer.writeElement(remittance.getPayerId());
    
    // BPR11 - Originating Company Supplemental Code
    writer.writeElement(""); // Optional
    
    // BPR12 - DFI ID Number Qualifier (Receiver)
    writer.writeElement("01");
    
    // BPR13 - Receiver DFI Identifier
    // For multiple pharmacies, this is left blank or uses first pharmacy
    writer.writeElement("");
    
    // BPR14 - Account Number Qualifier (Receiver)
    writer.writeElement("DA");
    
    // BPR15 - Receiver Account Number
    // For multiple pharmacies, individual accounts in REF segments
    writer.writeElement("");
    
    // BPR16 - Payment effective date
    writer.writeElement(remittance.getPaymentEffectiveDate().format(DATE_FORMAT));
    
    writer.writeEndSegment();
}

/**
 * Writes TRN segment with NACH transaction reference.
 */
private void writeTRNSegmentWithNachReference(
        EDIStreamWriter writer,
        RemittanceAdvice remittance,
        Map<String, PaymentReference> paymentReferences) throws Exception {
    
    writer.writeStartSegment("TRN");
    
    // TRN01 - Trace type code
    writer.writeElement("1"); // 1 = Current Transaction Trace Numbers
    
    // TRN02 - Reference Identification
    // Use primary NACH reference or batch reference
    String primaryReference = getPrimaryPaymentReference(paymentReferences);
    writer.writeElement(primaryReference);
    
    // TRN03 - Originating Company Identifier
    writer.writeElement(remittance.getPayerId());
    
    // TRN04 - Reference Identification (Additional)
    writer.writeElement(String.format("BATCH-%d", remittance.getBatchId()));
    
    writer.writeEndSegment();
}

/**
 * Writes claim loop with NACH payment reference.
 */
private void writeClaimLoopWithNachReference(
        EDIStreamWriter writer,
        Claim claim,
        PaymentReference reference) throws Exception {
    
    // CLP segment (standard claim payment information)
    writeClPSegment(writer, claim);
    
    // CAS segments for adjustments
    if (claim.getAdjustments() != null && !claim.getAdjustments().isEmpty()) {
        writeClaimAdjustments(writer, claim.getAdjustments());
    }

    // NM1 segment - Patient information
    writePatientSegment(writer, claim);

    // REF segment - NACH payment reference
    writer.writeStartSegment("REF");
    writer.writeElement("EV"); // EV = Receiver Identification Number (for electronic payments)
    writer.writeElement(reference.getReferenceNumber()); // NACH transaction reference
    writer.writeEndSegment();
    
    // Additional REF segment for UTR (if available at time of EDI generation)
    // This would be added later via EDI update if UTR received after initial generation
    
    // REF segment - NACH beneficiary account (optional, for pharmacy reference)
    if (reference.getBeneficiaryAccount() != null) {
        writer.writeStartSegment("REF");
        writer.writeElement("1L"); // 1L = Receiver's Account Number
        writer.writeElement(reference.getBeneficiaryAccount());
        writer.writeEndSegment();
    }

    // Service line loops
    if (claim.getServiceLines() != null) {
        for (Claim.ServiceLine serviceLine : claim.getServiceLines()) {
            writeServiceLineLoop(writer, serviceLine);
        }
    }
}

/**
 * Gets the primary payment reference for TRN segment.
 * For NACH, uses the first NACH transaction reference or batch identifier.
 */
private String getPrimaryPaymentReference(Map<String, PaymentReference> references) {
    if (references.isEmpty()) {
        throw new IllegalStateException("No payment references available");
    }
    
    // For NACH, all references are transaction references
    // Use first one as primary, or create batch-level reference
    PaymentReference firstRef = references.values().iterator().next();
    
    if (references.size() == 1) {
        return firstRef.getReferenceNumber();
    } else {
        // Multiple references - create batch reference
        return String.format("NACH-BATCH-%s",
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}
```

#### Testing Checklist
- [ ] BPR segment correct for ACH
- [ ] TRN segment contains NACH reference
- [ ] REF segment per claim with NACH TRN
- [ ] EDI validates against HIPAA 5010
- [ ] Compatible with pharmacy systems

---

## Week 6: Frontend & Mandate Management

### Task 4.1: NACH Mandate Management UI
**Owner**: Frontend Team  
**Duration**: 5 days  
**Priority**: High

#### Files to Create

```
edi835-admin-portal/src/
├── components/
│   └── nach/
│       ├── MandateList.tsx
│       ├── MandateForm.tsx
│       ├── MandateDetails.tsx
│       ├── MandateActivation.tsx
│       └── BankAccountVerification.tsx
├── services/
│   └── nachMandateService.ts
└── types/
    └── NachMandate.ts
```

#### Implementation

```typescript
// File: src/types/NachMandate.ts

export interface NachMandate {
  id?: number;
  mandateId: string;
  pharmacyId: string;
  pharmacyName: string;
  
  // Bank details
  bankAccountNumber: string;
  bankAccountHolderName: string;
  bankIfscCode: string;
  bankName?: string;
  bankBranch?: string;
  accountType?: 'SAVINGS' | 'CURRENT' | 'NRE' | 'NRO';
  
  // Mandate details
  mandateType: 'CREDIT' | 'DEBIT';
  mandateStatus: 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'EXPIRED' | 'CANCELLED' | 'REJECTED';
  
  // Limits
  maxAmountPerTransaction?: number;
  maxTransactionsPerMonth?: number;
  
  // Validity
  validFrom: string;
  validUntil: string;
  
  // NPCI references
  umrn?: string;
  sponsorBankCode?: string;
  
  // Dates
  registrationDate?: string;
  activationDate?: string;
  rejectionDate?: string;
  
  // Reasons
  rejectionReason?: string;
  cancellationReason?: string;
  
  // Verification
  verificationStatus?: 'PENDING' | 'VERIFIED' | 'FAILED';
  verificationMethod?: string;
}
```

```tsx
// File: src/components/nach/MandateForm.tsx

import React, { useState } from 'react';
import {
  Box,
  Button,
  TextField,
  Grid,
  Paper,
  Typography,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  CircularProgress,
} from '@mui/material';
import { useFormik } from 'formik';
import * as yup from 'yup';
import { NachMandate } from '../../types/NachMandate';
import { nachMandateService } from '../../services/nachMandateService';
import { BankAccountVerification } from './BankAccountVerification';

const validationSchema = yup.object({
  pharmacyId: yup.string().required('Pharmacy ID is required'),
  pharmacyName: yup.string().required('Pharmacy name is required'),
  bankAccountNumber: yup
    .string()
    .required('Bank account number is required')
    .matches(/^[0-9]+$/, 'Must be only digits')
    .min(9, 'Must be at least 9 digits')
    .max(18, 'Must be at most 18 digits'),
  bankAccountHolderName: yup.string().required('Account holder name is required'),
  bankIfscCode: yup
    .string()
    .required('IFSC code is required')
    .matches(/^[A-Z]{4}0[A-Z0-9]{6}$/, 'Invalid IFSC code format'),
  accountType: yup.string().required('Account type is required'),
  validFrom: yup.date().required('Valid from date is required'),
  validUntil: yup
    .date()
    .required('Valid until date is required')
    .min(yup.ref('validFrom'), 'Must be after valid from date'),
  maxAmountPerTransaction: yup
    .number()
    .min(0, 'Must be non-negative')
    .required('Maximum amount is required'),
});

interface Props {
  mandate?: NachMandate;
  onSave: (mandate: NachMandate) => void;
  onCancel: () => void;
}

export const MandateForm: React.FC<Props> = ({ mandate, onSave, onCancel }) => {
  const [loading, setLoading] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [verificationResult, setVerificationResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  const formik = useFormik({
    initialValues: mandate || {
      pharmacyId: '',
      pharmacyName: '',
      bankAccountNumber: '',
      bankAccountHolderName: '',
      bankIfscCode: '',
      accountType: 'SAVINGS',
      mandateType: 'CREDIT',
      validFrom: new Date().toISOString().split('T')[0],
      validUntil: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000)
        .toISOString()
        .split('T')[0],
      maxAmountPerTransaction: 100000,
      maxTransactionsPerMonth: 50,
    },
    validationSchema,
    onSubmit: async (values) => {
      if (!verificationResult || !verificationResult.verified) {
        setError('Please verify bank account before submitting');
        return;
      }

      setLoading(true);
      setError(null);

      try {
        if (mandate?.id) {
          await nachMandateService.update(mandate.id, values);
        } else {
          await nachMandateService.create(values);
        }
        onSave(values);
      } catch (err: any) {
        setError(err.response?.data?.message || 'Failed to save mandate');
      } finally {
        setLoading(false);
      }
    },
  });

  const handleVerifyAccount = async () => {
    setVerifying(true);
    setError(null);

    try {
      const result = await nachMandateService.verifyBankAccount({
        accountNumber: formik.values.bankAccountNumber,
        ifscCode: formik.values.bankIfscCode,
        accountHolderName: formik.values.bankAccountHolderName,
      });

      setVerificationResult(result);

      if (result.verified) {
        // Update form with verified details
        formik.setFieldValue('bankName', result.bankName);
        formik.setFieldValue('bankBranch', result.branchName);
        formik.setFieldValue('bankAccountHolderName', result.accountHolderName);
      } else {
        setError(result.failureReason || 'Verification failed');
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Verification failed');
    } finally {
      setVerifying(false);
    }
  };

  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h6" gutterBottom>
        {mandate ? 'Edit' : 'Register'} NACH Mandate
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <form onSubmit={formik.handleSubmit}>
        <Grid container spacing={3}>
          {/* Pharmacy Information */}
          <Grid item xs={12}>
            <Typography variant="subtitle2" color="primary" gutterBottom>
              Pharmacy Information
            </Typography>
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Pharmacy ID"
              name="pharmacyId"
              value={formik.values.pharmacyId}
              onChange={formik.handleChange}
              error={formik.touched.pharmacyId && Boolean(formik.errors.pharmacyId)}
              helperText={formik.touched.pharmacyId && formik.errors.pharmacyId}
              disabled={!!mandate}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Pharmacy Name"
              name="pharmacyName"
              value={formik.values.pharmacyName}
              onChange={formik.handleChange}
              error={formik.touched.pharmacyName && Boolean(formik.errors.pharmacyName)}
              helperText={formik.touched.pharmacyName && formik.errors.pharmacyName}
            />
          </Grid>

          {/* Bank Account Details */}
          <Grid item xs={12}>
            <Typography variant="subtitle2" color="primary" gutterBottom>
              Bank Account Details
            </Typography>
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Bank Account Number"
              name="bankAccountNumber"
              value={formik.values.bankAccountNumber}
              onChange={formik.handleChange}
              error={
                formik.touched.bankAccountNumber && Boolean(formik.errors.bankAccountNumber)
              }
              helperText={formik.touched.bankAccountNumber && formik.errors.bankAccountNumber}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="IFSC Code"
              name="bankIfscCode"
              value={formik.values.bankIfscCode}
              onChange={formik.handleChange}
              error={formik.touched.bankIfscCode && Boolean(formik.errors.bankIfscCode)}
              helperText={formik.touched.bankIfscCode && formik.errors.bankIfscCode}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              label="Account Holder Name"
              name="bankAccountHolderName"
              value={formik.values.bankAccountHolderName}
              onChange={formik.handleChange}
              error={
                formik.touched.bankAccountHolderName &&
                Boolean(formik.errors.bankAccountHolderName)
              }
              helperText={
                formik.touched.bankAccountHolderName && formik.errors.bankAccountHolderName
              }
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <FormControl fullWidth>
              <InputLabel>Account Type</InputLabel>
              <Select
                name="accountType"
                value={formik.values.accountType}
                onChange={formik.handleChange}
                error={formik.touched.accountType && Boolean(formik.errors.accountType)}
              >
                <MenuItem value="SAVINGS">Savings</MenuItem>
                <MenuItem value="CURRENT">Current</MenuItem>
                <MenuItem value="NRE">NRE</MenuItem>
                <MenuItem value="NRO">NRO</MenuItem>
              </Select>
            </FormControl>
          </Grid>

          {/* Verify Button */}
          <Grid item xs={12}>
            <Button
              variant="outlined"
              onClick={handleVerifyAccount}
              disabled={
                verifying ||
                !formik.values.bankAccountNumber ||
                !formik.values.bankIfscCode ||
                !formik.values.bankAccountHolderName
              }
              startIcon={verifying && <CircularProgress size={20} />}
            >
              {verifying ? 'Verifying...' : 'Verify Bank Account'}
            </Button>

            {verificationResult && (
              <Box mt={2}>
                <BankAccountVerification result={verificationResult} />
              </Box>
            )}
          </Grid>

          {/* Mandate Limits */}
          <Grid item xs={12}>
            <Typography variant="subtitle2" color="primary" gutterBottom>
              Mandate Limits
            </Typography>
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              type="number"
              label="Maximum Amount Per Transaction"
              name="maxAmountPerTransaction"
              value={formik.values.maxAmountPerTransaction}
              onChange={formik.handleChange}
              error={
                formik.touched.maxAmountPerTransaction &&
                Boolean(formik.errors.maxAmountPerTransaction)
              }
              helperText={
                formik.touched.maxAmountPerTransaction &&
                formik.errors.maxAmountPerTransaction
              }
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              type="number"
              label="Maximum Transactions Per Month"
              name="maxTransactionsPerMonth"
              value={formik.values.maxTransactionsPerMonth}
              onChange={formik.handleChange}
            />
          </Grid>

          {/* Validity Period */}
          <Grid item xs={12}>
            <Typography variant="subtitle2" color="primary" gutterBottom>
              Validity Period
            </Typography>
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              type="date"
              label="Valid From"
              name="validFrom"
              value={formik.values.validFrom}
              onChange={formik.handleChange}
              error={formik.touched.validFrom && Boolean(formik.errors.validFrom)}
              helperText={formik.touched.validFrom && formik.errors.validFrom}
              InputLabelProps={{ shrink: true }}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              fullWidth
              type="date"
              label="Valid Until"
              name="validUntil"
              value={formik.values.validUntil}
              onChange={formik.handleChange}
              error={formik.touched.validUntil && Boolean(formik.errors.validUntil)}
              helperText={formik.touched.validUntil && formik.errors.validUntil}
              InputLabelProps={{ shrink: true }}
            />
          </Grid>

          {/* Actions */}
          <Grid item xs={12}>
            <Box display="flex" justifyContent="flex-end" gap={2}>
              <Button onClick={onCancel} disabled={loading}>
                Cancel
              </Button>
              <Button
                type="submit"
                variant="contained"
                disabled={loading || !verificationResult?.verified}
              >
                {loading ? 'Saving...' : mandate ? 'Update' : 'Register'}
              </Button>
            </Box>
          </Grid>
        </Grid>
      </form>
    </Paper>
  );
};
```

#### Testing Checklist
- [ ] Mandate registration UI works
- [ ] Bank account verification integrated
- [ ] Form validation correct
- [ ] Mandate list displays properly
- [ ] Activation workflow functional

---

### Task 4.2: NACH Payment Monitoring Dashboard
**Owner**: Frontend Team  
**Duration**: 4 days  
**Priority**: High

```tsx
// File: src/components/nach/NachPaymentDashboard.tsx

import React from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  Chip,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { nachMonitoringService } from '../../services/nachMonitoringService';

export const NachPaymentDashboard: React.FC = () => {
  const { data: summary, isLoading } = useQuery({
    queryKey: ['nachSummary'],
    queryFn: nachMonitoringService.getSummary,
    refetchInterval: 30000,
  });

  if (isLoading) return <div>Loading...</div>;

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" gutterBottom>
        NACH Payment Monitoring
      </Typography>

      <Grid container spacing={3} mb={3}>
        {/* Summary Cards */}
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6">{summary?.successfulPayments || 0}</Typography>
              <Typography variant="body2" color="text.secondary">
                Successful Payments
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6">{summary?.pendingPayments || 0}</Typography>
              <Typography variant="body2" color="text.secondary">
                Pending Settlement
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6">{summary?.failedPayments || 0}</Typography>
              <Typography variant="body2" color="text.secondary">
                Failed Payments
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Typography variant="h6">{summary?.activeMandates || 0}</Typography>
              <Typography variant="body2" color="text.secondary">
                Active Mandates
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Grid container spacing={3}>
        {/* Recent NACH Transactions */}
        <Grid item xs={12}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              Recent NACH Transactions
            </Typography>
            {/* Transaction list component */}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};
```

---

## Week 7-8: Testing & Deployment

### Task 5.1: NACH Integration Testing
**Owner**: Backend Team + QA  
**Duration**: 5 days  
**Priority**: Critical

#### Test Scenarios

1. **End-to-End NACH Payment Flow**
   - Mandate validation
   - NACH reference allocation
   - EDI generation with NACH references
   - NACH file generation
   - Submission to gateway (mock)
   - Status update handling
   - Settlement tracking

2. **Mandate Lifecycle**
   - Registration
   - Verification
   - Activation
   - Usage validation
   - Expiry handling
   - Cancellation

3. **Error Scenarios**
   - Invalid mandate
   - Mandate expired
   - Amount exceeds limit
   - NACH file generation failure
   - Submission failure
   - Failed transactions
   - Return handling

4. **Reconciliation**
   - Settlement report processing
   - UTR matching
   - Amount reconciliation
   - Mismatch detection

#### Testing Checklist
- [ ] All integration tests pass
- [ ] Mandate workflows tested
- [ ] NACH file format validated
- [ ] Error handling verified
- [ ] Reconciliation accurate

---

### Task 5.2: UAT & Production Readiness
**Owner**: QA + Business Users  
**Duration**: 5 days  
**Priority**: Critical

#### UAT Test Cases

**Test Case 1: Register NACH Mandate**
1. Navigate to NACH Mandate Management
2. Register new mandate with valid bank details
3. Verify bank account
4. Submit mandate
5. Verify NPCI registration initiated
6. Activate mandate after bank approval

**Test Case 2: Process Bucket with NACH Payment**
1. Approve bucket with pharmacies having NACH mandates
2. Verify NACH references allocated
3. Verify EDI contains NACH references
4. Verify NACH file generated
5. Download and review NACH file format
6. Verify submission to bank

**Test Case 3: Track NACH Payment Status**
1. View NACH payment dashboard
2. View specific transaction
3. Simulate status update from bank
4. Verify UTR recorded
5. Verify settlement status

#### Production Readiness Checklist
- [ ] All tests passed
- [ ] Performance benchmarks met
- [ ] Security review completed
- [ ] Documentation finalized
- [ ] Runbooks prepared
- [ ] Monitoring configured
- [ ] Backup/recovery tested
- [ ] Rollback plan ready

---

## NACH Reconciliation & Settlement

### Daily Reconciliation Process

```java
// File: src/main/java/com/healthcare/edi835/service/NachReconciliationService.java

package com.healthcare.edi835.service;

/**
 * Service for reconciling NACH payments with bank settlement reports.
 */
@Slf4j
@Service
public class NachReconciliationService {

    private final NachTransactionRepository nachTxnRepo;
    private final NachSettlementReportRepository settlementRepo;
    private final NachReconciliationDetailRepository reconDetailRepo;

    /**
     * Processes daily settlement report from bank.
     */
    @Transactional
    public NachSettlementReport processDailySettlementReport(
            LocalDate settlementDate,
            Path reportFilePath) {
        
        log.info("Processing NACH settlement report for date: {}", settlementDate);

        // Create settlement report record
        NachSettlementReport report = NachSettlementReport.builder()
            .reportId(generateReportId(settlementDate))
            .reportDate(LocalDate.now())
            .reportType("DAILY")
            .settlementDate(settlementDate)
            .sourceFileName(reportFilePath.getFileName().toString())
            .sourceFilePath(reportFilePath.toString())
            .receivedAt(LocalDateTime.now())
            .processingStatus("PENDING")
            .build();
        
        report = settlementRepo.save(report);

        try {
            // Parse settlement report file
            List<SettlementRecord> records = parseSettlementFile(reportFilePath);
            
            report.setTotalTransactions(records.size());
            report.setProcessingStatus("PROCESSING");
            settlementRepo.save(report);

            // Reconcile each record
            int matched = 0;
            int unmatched = 0;
            BigDecimal totalAmount = BigDecimal.ZERO;

            for (SettlementRecord record : records) {
                ReconciliationResult result = reconcileTransaction(report, record);
                
                if (result.isMatched()) {
                    matched++;
                } else {
                    unmatched++;
                }
                
                totalAmount = totalAmount.add(record.getAmount());
            }

            // Update report summary
            report.setTotalAmount(totalAmount);
            report.setSuccessCount(matched);
            report.setFailureCount(unmatched);
            report.setMatchedTransactions(matched);
            report.setUnmatchedTransactions(unmatched);
            report.setProcessingStatus("COMPLETED");
            report.setProcessedAt(LocalDateTime.now());
            report.setReconciliationStatus(
                unmatched == 0 ? "COMPLETED" : "PARTIAL");
            
            settlementRepo.save(report);

            log.info("Settlement report processed: {} matched, {} unmatched",
                matched, unmatched);

            return report;

        } catch (Exception e) {
            log.error("Failed to process settlement report", e);
            report.setProcessingStatus("FAILED");
            settlementRepo.save(report);
            throw new SettlementProcessingException(
                "Failed to process settlement report", e);
        }
    }

    /**
     * Reconciles a single transaction record.
     */
    private ReconciliationResult reconcileTransaction(
            NachSettlementReport report,
            SettlementRecord record) {
        
        // Find NACH transaction by UTR or transaction reference
        Optional<NachTransaction> nachTxnOpt = record.getUtrNumber() != null
            ? nachTxnRepo.findByUtrNumber(record.getUtrNumber())
            : nachTxnRepo.findByTransactionReference(record.getTransactionReference());

        NachReconciliationDetail detail = NachReconciliationDetail.builder()
            .settlementReport(report)
            .build();

        if (nachTxnOpt.isEmpty()) {
            // Transaction not found in our system
            detail.setMatchStatus("NOT_FOUND");
            detail.setMismatchReason("Transaction not found in system");
            detail.setResolutionStatus("PENDING");
            reconDetailRepo.save(detail);
            
            return ReconciliationResult.unmatched("Transaction not found");
        }

        NachTransaction nachTxn = nachTxnOpt.get();
        detail.setNachTransaction(nachTxn);
        detail.setExpectedAmount(nachTxn.getTransactionAmount());
        detail.setActualAmount(record.getAmount());

        // Check amount match
        if (nachTxn.getTransactionAmount().compareTo(record.getAmount()) != 0) {
            detail.setMatchStatus("AMOUNT_MISMATCH");
            detail.setDifferenceAmount(
                record.getAmount().subtract(nachTxn.getTransactionAmount()));
            detail.setMismatchReason(String.format(
                "Amount mismatch: Expected %.2f, Received %.2f",
                nachTxn.getTransactionAmount(), record.getAmount()));
            detail.setResolutionStatus("PENDING");
            reconDetailRepo.save(detail);
            
            return ReconciliationResult.unmatched("Amount mismatch");
        }

        // Check settlement date
        if (record.getSettlementDate() != null) {
            detail.setExpectedSettlementDate(nachTxn.getSettlementDate());
            detail.setActualSettlementDate(record.getSettlementDate());
        }

        // Update NACH transaction with UTR and settlement info
        if (record.getUtrNumber() != null && nachTxn.getUtrNumber() == null) {
            nachTxn.setUtrNumber(record.getUtrNumber());
        }
        
        nachTxn.setActualSettlementDate(record.getSettlementDate());
        nachTxn.setSettlementAmount(record.getAmount());
        nachTxn.setReconciliationStatus("MATCHED");
        nachTxn.setReconciledAt(LocalDateTime.now());
        nachTxn.setReconciledBy("SYSTEM");
        nachTxnRepo.save(nachTxn);

        detail.setMatchStatus("MATCHED");
        detail.setResolutionStatus("RESOLVED");
        reconDetailRepo.save(detail);

        return ReconciliationResult.matched();
    }

    private List<SettlementRecord> parseSettlementFile(Path filePath) 
            throws IOException {
        // Parse bank settlement file format
        // Format varies by bank - implement parser based on bank specs
        return Collections.emptyList();
    }

    private String generateReportId(LocalDate date) {
        return String.format("SETTLE-%s-%d",
            date.format(DateTimeFormatter.BASIC_ISO_DATE),
            System.currentTimeMillis() % 100000);
    }
}
```

---

## Production Considerations

### Security

1. **Mandate Data Protection**
   - Encrypt bank account numbers at rest
   - Mask account numbers in logs and UI
   - Audit all mandate access

2. **NACH File Security**
   - Encrypt files during transmission
   - Secure SFTP/API credentials
   - File integrity validation

3. **API Security**
   - Authenticate all gateway calls
   - Validate webhook signatures
   - Rate limiting on status updates

### Monitoring

1. **Key Metrics**
   - NACH submission success rate
   - Settlement time (T+X)
   - Reconciliation accuracy
   - Mandate activation rate
   - Failed transaction rate

2. **Alerts**
   - NACH submission failures
   - Settlement delays
   - Reconciliation mismatches
   - Mandate expiry approaching
   - High failure rates

### Performance

1. **Batch Processing**
   - Process mandates in parallel
   - Async status updates
   - Bulk reconciliation

2. **Database Optimization**
   - Index on UTR, transaction reference
   - Partition large tables
   - Archive old transactions

---

## Phase 2 Completion Checklist

### Database
- [ ] NACH tables created
- [ ] Indexes optimized
- [ ] Sample data loaded

### Backend
- [ ] NACH mandate service complete
- [ ] NACH payment service complete
- [ ] NACH file generator working
- [ ] Gateway integration complete
- [ ] Reconciliation service functional
- [ ] Unit tests >85% coverage
- [ ] Integration tests passing

### Frontend
- [ ] Mandate management UI complete
- [ ] NACH monitoring dashboard complete
- [ ] Bank verification integrated
- [ ] Error handling robust

### Integration
- [ ] EDI generator enhanced for NACH
- [ ] Payment orchestration updated
- [ ] Dual payment method support (Check + NACH)

### Testing
- [ ] End-to-end NACH flow tested
- [ ] UAT completed
- [ ] Performance benchmarks met
- [ ] Security review done

### Documentation
- [ ] API documentation updated
- [ ] NACH integration guide written
- [ ] Reconciliation procedures documented
- [ ] Troubleshooting guide complete

### Deployment
- [ ] All environments deployed
- [ ] Gateway credentials configured
- [ ] Monitoring enabled
- [ ] Production deployment plan ready

---

**End of Phase 2 Implementation Plan**

Would you like me to elaborate on any specific section, or shall we proceed with creating specific code files or configuration details?