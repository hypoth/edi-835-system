package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.CheckAuditLog;
import com.healthcare.edi835.entity.CheckPayment;
import com.healthcare.edi835.entity.CheckPaymentConfig;
import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.Payer;
import com.healthcare.edi835.exception.CheckPaymentNotFoundException;
import com.healthcare.edi835.exception.InvalidCheckStateException;
import com.healthcare.edi835.model.dto.CheckAuditLogDTO;
import com.healthcare.edi835.model.dto.CheckPaymentDTO;
import com.healthcare.edi835.model.dto.ManualCheckAssignmentRequest;
import com.healthcare.edi835.repository.CheckAuditLogRepository;
import com.healthcare.edi835.repository.CheckPaymentConfigRepository;
import com.healthcare.edi835.repository.CheckPaymentRepository;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import com.healthcare.edi835.repository.PayerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing check payment lifecycle.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Manual check assignment (user-entered details)</li>
 *   <li>Automatic check assignment (from pre-reserved ranges)</li>
 *   <li>Check acknowledgment workflow</li>
 *   <li>Check issuance tracking</li>
 *   <li>Check void operations</li>
 *   <li>Audit trail management</li>
 * </ul>
 *
 * <p>Check Lifecycle:</p>
 * <pre>
 * RESERVED → ASSIGNED → ACKNOWLEDGED → ISSUED
 *              ↓
 *          VOID/CANCELLED
 * </pre>
 */
@Slf4j
@Service
public class CheckPaymentService {

    private final CheckPaymentRepository checkPaymentRepository;
    private final CheckAuditLogRepository auditLogRepository;
    private final CheckPaymentConfigRepository configRepository;
    private final EdiFileBucketRepository bucketRepository;
    private final PayerRepository payerRepository;
    private final CheckReservationService reservationService;
    private final BucketManagerService bucketManagerService;

    public CheckPaymentService(
            CheckPaymentRepository checkPaymentRepository,
            CheckAuditLogRepository auditLogRepository,
            CheckPaymentConfigRepository configRepository,
            EdiFileBucketRepository bucketRepository,
            PayerRepository payerRepository,
            CheckReservationService reservationService,
            BucketManagerService bucketManagerService) {
        this.checkPaymentRepository = checkPaymentRepository;
        this.auditLogRepository = auditLogRepository;
        this.configRepository = configRepository;
        this.bucketRepository = bucketRepository;
        this.payerRepository = payerRepository;
        this.reservationService = reservationService;
        this.bucketManagerService = bucketManagerService;
    }

    /**
     * Assigns a check manually during bucket approval.
     * User enters all check details.
     *
     * @param bucketId Bucket ID to assign check to
     * @param request  Manual check assignment request
     * @return Created check payment
     */
    @Transactional
    public CheckPayment assignCheckManually(UUID bucketId, ManualCheckAssignmentRequest request) {
        log.info("Assigning check manually to bucket {}: checkNumber={}", bucketId, request.getCheckNumber());

        // Validate bucket exists
        EdiFileBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

        // Validate check number is unique
        if (checkPaymentRepository.existsByCheckNumber(request.getCheckNumber())) {
            throw new IllegalArgumentException("Check number already exists: " + request.getCheckNumber());
        }

        // Create check payment
        CheckPayment checkPayment = CheckPayment.builder()
                .bucket(bucket)
                .checkNumber(request.getCheckNumber())
                .checkAmount(bucket.getTotalAmount())  // Use bucket's total amount
                .checkDate(request.getCheckDate())
                .bankName(request.getBankName())
                .routingNumber(request.getRoutingNumber())
                .accountNumberLast4(request.getAccountLast4())
                .status(CheckPayment.CheckStatus.ASSIGNED)
                .assignedBy(request.getAssignedBy())
                .assignedAt(LocalDateTime.now())
                .createdBy(request.getAssignedBy())
                .build();

        checkPayment = checkPaymentRepository.save(checkPayment);

        // Update bucket with check payment
        bucket.assignCheckPayment(checkPayment);
        bucketRepository.save(bucket);

        // Create audit log
        CheckAuditLog auditLog = CheckAuditLog.logAssignment(
                checkPayment,
                bucket.getBucketId().toString(),
                request.getAssignedBy(),
                false  // Manual assignment
        );
        auditLogRepository.save(auditLog);

        log.info("Check {} manually assigned to bucket {} by {}",
                checkPayment.getCheckNumber(), bucketId, request.getAssignedBy());

        // If bucket is approved and awaiting payment, trigger EDI generation
        if (bucket.getStatus() == EdiFileBucket.BucketStatus.PENDING_APPROVAL &&
                bucket.getApprovedBy() != null) {
            log.info("Bucket {} is approved and payment assigned, triggering EDI generation", bucketId);
            bucketManagerService.transitionToGeneration(bucket);
        }

        return checkPayment;
    }

    /**
     * Replaces an existing check assignment with a new check.
     * Updates the existing check record with new details (due to UNIQUE bucket_id constraint).
     * Maintains audit trail of both the void and new assignment.
     *
     * Only allowed for buckets in PENDING_APPROVAL status with ASSIGNED payment.
     *
     * @param bucketId Bucket ID to replace check for
     * @param request New check details
     * @return The updated check payment with new details
     */
    @Transactional
    public CheckPayment replaceCheck(UUID bucketId, ManualCheckAssignmentRequest request) {
        log.info("Replacing check for bucket {}: newCheckNumber={}, replacedBy={}",
                bucketId, request.getCheckNumber(), request.getAssignedBy());

        // Validate bucket exists
        EdiFileBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

        // Validate bucket state
        if (bucket.getStatus() != EdiFileBucket.BucketStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Can only replace check for buckets in PENDING_APPROVAL status. Current: " + bucket.getStatus());
        }

        if (bucket.getPaymentStatus() != EdiFileBucket.PaymentStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Can only replace check when payment status is ASSIGNED. Current: " + bucket.getPaymentStatus());
        }

        // Get existing check payment
        CheckPayment existingCheck = checkPaymentRepository.findByBucket(bucket)
                .orElseThrow(() -> new IllegalStateException("No check payment found for bucket: " + bucketId));

        // Validate new check number is unique (unless it's the same as current)
        if (!existingCheck.getCheckNumber().equals(request.getCheckNumber()) &&
                checkPaymentRepository.existsByCheckNumber(request.getCheckNumber())) {
            throw new IllegalArgumentException("Check number already exists: " + request.getCheckNumber());
        }

        String oldCheckNumber = existingCheck.getCheckNumber();

        // Create audit log for void of old check details
        String voidReason = String.format("Replaced with check %s by %s",
                request.getCheckNumber(), request.getAssignedBy());
        CheckAuditLog voidAuditLog = CheckAuditLog.builder()
                .checkPayment(existingCheck)
                .checkNumber(oldCheckNumber)
                .action(CheckAuditLog.Actions.VOIDED)
                .bucketId(bucketId.toString())
                .performedBy(request.getAssignedBy())
                .notes(voidReason)
                .amount(existingCheck.getCheckAmount())
                .build();
        auditLogRepository.save(voidAuditLog);

        log.info("Voiding old check {} for bucket {}", oldCheckNumber, bucketId);

        // Update existing check with new details (reuse same record due to UNIQUE constraint)
        existingCheck.setCheckNumber(request.getCheckNumber());
        existingCheck.setCheckDate(request.getCheckDate());
        existingCheck.setBankName(request.getBankName());
        existingCheck.setRoutingNumber(request.getRoutingNumber());
        existingCheck.setAccountNumberLast4(request.getAccountLast4());
        existingCheck.setCheckAmount(bucket.getTotalAmount());
        existingCheck.setAssignedBy(request.getAssignedBy());
        existingCheck.setAssignedAt(LocalDateTime.now());
        existingCheck.setStatus(CheckPayment.CheckStatus.ASSIGNED);
        // Clear any previous acknowledgment/issuance/void data
        existingCheck.setAcknowledgedBy(null);
        existingCheck.setAcknowledgedAt(null);
        existingCheck.setIssuedBy(null);
        existingCheck.setIssuedAt(null);
        existingCheck.setVoidReason(null);
        existingCheck.setVoidedBy(null);
        existingCheck.setVoidedAt(null);
        existingCheck.setUpdatedBy(request.getAssignedBy());

        existingCheck = checkPaymentRepository.save(existingCheck);

        // Reset bucket payment status back to ASSIGNED
        bucket.setPaymentStatus(EdiFileBucket.PaymentStatus.ASSIGNED);
        bucket.setCheckPayment(existingCheck);
        bucketRepository.save(bucket);

        // Create audit log for new assignment
        CheckAuditLog assignAuditLog = CheckAuditLog.logAssignment(
                existingCheck,
                bucket.getBucketId().toString(),
                request.getAssignedBy(),
                false  // Manual assignment
        );
        auditLogRepository.save(assignAuditLog);

        log.info("Replaced check for bucket {}: {} -> {}",
                bucketId, oldCheckNumber, existingCheck.getCheckNumber());

        return existingCheck;
    }

    /**
     * Assigns a check automatically by extracting payer ID from bucket.
     * Convenience method for controller that extracts payerId from bucket.
     *
     * @param bucketId  Bucket ID to assign check to
     * @param systemUser System user for audit
     * @return Created check payment
     */
    @Transactional
    public CheckPayment assignCheckAutomaticallyFromBucket(UUID bucketId, String systemUser) {
        log.info("Assigning check automatically to bucket {} (extracting payer from bucket)", bucketId);

        // Validate bucket exists and extract payer
        EdiFileBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

        if (bucket.getPayerId() == null) {
            throw new IllegalArgumentException("Bucket has no associated payer for auto-assignment");
        }

        // Look up Payer entity by business ID to get the UUID
        String payerBusinessId = bucket.getPayerId();
        Payer payer = payerRepository.findByPayerId(payerBusinessId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payer not found for business ID: " + payerBusinessId +
                        ". Please ensure the payer is configured in the system."));

        log.debug("Found payer UUID {} for business ID {}", payer.getId(), payerBusinessId);
        return assignCheckAutomatically(bucketId, payer.getId(), systemUser);
    }

    /**
     * Assigns a check automatically using pre-reserved check numbers.
     * System retrieves next available check from active reservations.
     *
     * <p><b>Transaction Safety:</b> This method implements compensation logic. If the check
     * is successfully reserved but subsequent operations fail, the reserved check is
     * automatically released back to the pool.</p>
     *
     * @param bucketId  Bucket ID to assign check to
     * @param payerId   Payer ID to find reservations
     * @param systemUser System user for audit
     * @return Created check payment
     */
    @Transactional
    public CheckPayment assignCheckAutomatically(UUID bucketId, UUID payerId, String systemUser) {
        log.info("Assigning check automatically to bucket {} for payer {}", bucketId, payerId);

        // Validate bucket exists
        EdiFileBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

        // Get next available check from reservation (runs in REQUIRES_NEW transaction)
        // This commits immediately, so we need compensation if later steps fail
        var checkInfo = reservationService.getAndReserveNextCheck(payerId, bucketId);

        try {
            // Create check payment
            CheckPayment checkPayment = CheckPayment.builder()
                    .bucket(bucket)
                    .checkNumber(checkInfo.getCheckNumber())
                    .checkAmount(bucket.getTotalAmount())
                    .checkDate(checkInfo.getCheckDate())
                    .bankName(checkInfo.getBankName())
                    .routingNumber(checkInfo.getRoutingNumber())
                    .accountNumberLast4(checkInfo.getAccountLast4())
                    .status(CheckPayment.CheckStatus.ASSIGNED)
                    .assignedBy("SYSTEM")
                    .assignedAt(LocalDateTime.now())
                    .createdBy(systemUser)
                    .build();

            checkPayment = checkPaymentRepository.save(checkPayment);

            // Update bucket with check payment
            bucket.assignCheckPayment(checkPayment);
            bucketRepository.save(bucket);

            // Create audit log
            CheckAuditLog auditLog = CheckAuditLog.logAssignment(
                    checkPayment,
                    bucket.getBucketId().toString(),
                    "SYSTEM",
                    true  // Automatic assignment
            );
            auditLogRepository.save(auditLog);

            log.info("Check {} automatically assigned to bucket {} from reservation",
                    checkPayment.getCheckNumber(), bucketId);

            // If bucket is approved and awaiting payment, trigger EDI generation
            if (bucket.getStatus() == EdiFileBucket.BucketStatus.PENDING_APPROVAL &&
                    bucket.getApprovedBy() != null) {
                log.info("Bucket {} is approved and payment assigned, triggering EDI generation", bucketId);
                bucketManagerService.transitionToGeneration(bucket);
            }

            return checkPayment;

        } catch (Exception e) {
            // COMPENSATION: Release the reserved check back to the pool (if using separate transactions)
            // For SQLite mode, this is handled by transaction rollback automatically
            if (reservationService.useSeparateTransaction()) {
                log.error("Check assignment failed for bucket {} after reserving check {}. " +
                          "Initiating compensation to release check (PostgreSQL mode).",
                        bucketId, checkInfo.getCheckNumber(), e);

                try {
                    UUID reservationId = UUID.fromString(checkInfo.getReservationId());
                    reservationService.releaseReservedCheck(
                            checkInfo.getCheckNumber(),
                            reservationId,
                            "Assignment failed: " + e.getMessage()
                    );
                    log.info("Compensation successful: Check {} released back to reservation {}",
                            checkInfo.getCheckNumber(), reservationId);
                } catch (Exception compensationError) {
                    // Log compensation failure - this is a critical error that needs manual intervention
                    log.error("CRITICAL: Compensation failed! Check {} may be orphaned. " +
                              "Manual intervention required to release check from reservation {}",
                            checkInfo.getCheckNumber(), checkInfo.getReservationId(), compensationError);
                }
            } else {
                log.error("Check assignment failed for bucket {} after reserving check {}. " +
                          "Transaction will rollback automatically (SQLite mode).",
                        bucketId, checkInfo.getCheckNumber(), e);
            }

            // Re-throw the original exception
            throw e;
        }
    }

    /**
     * Acknowledges a check payment amount.
     * User verifies the check amount before issuance.
     *
     * @param checkPaymentId Check payment ID
     * @param acknowledgedBy User acknowledging
     * @return Updated check payment
     */
    @Transactional
    public CheckPayment acknowledgeCheck(UUID checkPaymentId, String acknowledgedBy) {
        log.info("Acknowledging check payment {}", checkPaymentId);

        CheckPayment checkPayment = checkPaymentRepository.findById(checkPaymentId)
                .orElseThrow(() -> new CheckPaymentNotFoundException(checkPaymentId));

        // Validate state
        if (checkPayment.getStatus() != CheckPayment.CheckStatus.ASSIGNED) {
            throw new InvalidCheckStateException(
                    "acknowledge", checkPayment.getStatus(), CheckPayment.CheckStatus.ASSIGNED);
        }

        // Mark as acknowledged
        checkPayment.markAcknowledged(acknowledgedBy);
        checkPayment = checkPaymentRepository.save(checkPayment);

        // Update bucket payment status
        if (checkPayment.getBucket() != null) {
            EdiFileBucket bucket = checkPayment.getBucket();
            bucket.markPaymentAcknowledged();
            bucketRepository.save(bucket);
        }

        // Create audit log
        CheckAuditLog auditLog = CheckAuditLog.logAcknowledgment(checkPayment, acknowledgedBy);
        auditLogRepository.save(auditLog);

        log.info("Check payment {} acknowledged by {}", checkPaymentId, acknowledgedBy);

        return checkPayment;
    }

    /**
     * Marks a check as issued (physically mailed/delivered).
     *
     * @param checkPaymentId Check payment ID
     * @param issuedBy       User issuing the check
     * @return Updated check payment
     */
    @Transactional
    public CheckPayment markCheckIssued(UUID checkPaymentId, String issuedBy) {
        log.info("Marking check payment {} as issued", checkPaymentId);

        CheckPayment checkPayment = checkPaymentRepository.findById(checkPaymentId)
                .orElseThrow(() -> new CheckPaymentNotFoundException(checkPaymentId));

        // Validate state
        if (checkPayment.getStatus() != CheckPayment.CheckStatus.ACKNOWLEDGED) {
            throw new InvalidCheckStateException(
                    "issue", checkPayment.getStatus(), CheckPayment.CheckStatus.ACKNOWLEDGED);
        }

        // Mark as issued
        checkPayment.markIssued(issuedBy);
        checkPayment = checkPaymentRepository.save(checkPayment);

        // Update bucket payment status
        if (checkPayment.getBucket() != null) {
            EdiFileBucket bucket = checkPayment.getBucket();
            bucket.markPaymentIssued();
            bucketRepository.save(bucket);
        }

        // Create audit log
        CheckAuditLog auditLog = CheckAuditLog.logIssuance(checkPayment, issuedBy);
        auditLogRepository.save(auditLog);

        log.info("Check payment {} marked as issued by {}", checkPaymentId, issuedBy);

        return checkPayment;
    }

    /**
     * Voids a check payment.
     * Only allowed within configured time limit and requires FINANCIAL_ADMIN role.
     *
     * @param checkPaymentId Check payment ID
     * @param reason         Reason for voiding
     * @param voidedBy       User voiding (must have FINANCIAL_ADMIN role)
     * @return Updated check payment
     */
    @Transactional
    public CheckPayment voidCheck(UUID checkPaymentId, String reason, String voidedBy) {
        log.info("Voiding check payment {}: reason={}", checkPaymentId, reason);

        CheckPayment checkPayment = checkPaymentRepository.findById(checkPaymentId)
                .orElseThrow(() -> new CheckPaymentNotFoundException(checkPaymentId));

        // Validate check can be voided
        int voidTimeLimitHours = configRepository.getVoidTimeLimitHours();
        if (!checkPayment.canBeVoided(voidTimeLimitHours)) {
            throw new InvalidCheckStateException(
                    String.format("Cannot void check - either not issued or outside %d hour void window",
                            voidTimeLimitHours));
        }

        // Mark as voided
        checkPayment.markVoid(reason, voidedBy);
        checkPayment = checkPaymentRepository.save(checkPayment);

        // Create audit log
        CheckAuditLog auditLog = CheckAuditLog.logVoid(checkPayment, reason, voidedBy);
        auditLogRepository.save(auditLog);

        log.info("Check payment {} voided by {}", checkPaymentId, voidedBy);

        return checkPayment;
    }

    /**
     * Gets check payment for a bucket.
     *
     * @param bucketId Bucket ID
     * @return Optional check payment
     */
    public Optional<CheckPayment> getCheckPaymentForBucket(UUID bucketId) {
        EdiFileBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));
        return checkPaymentRepository.findByBucket(bucket);
    }

    /**
     * Gets all check payments.
     *
     * @return List of all check payment DTOs
     */
    public List<CheckPaymentDTO> getAllCheckPayments() {
        List<CheckPayment> checkPayments = checkPaymentRepository.findAll();
        return checkPayments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gets check payments by status.
     *
     * @param status Check status
     * @return List of check payment DTOs with matching status
     */
    public List<CheckPaymentDTO> getCheckPaymentsByStatus(String status) {
        CheckPayment.CheckStatus checkStatus;
        try {
            checkStatus = CheckPayment.CheckStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid check status: " + status);
        }

        List<CheckPayment> checkPayments = checkPaymentRepository.findByStatus(checkStatus);
        return checkPayments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gets check payments for a specific payer.
     *
     * @param payerId Payer ID
     * @return List of check payment DTOs for the payer
     */
    public List<CheckPaymentDTO> getCheckPaymentsForPayer(UUID payerId) {
        // Get all check payments and filter by payer ID from bucket
        List<CheckPayment> allCheckPayments = checkPaymentRepository.findAll();
        List<CheckPayment> checkPayments = allCheckPayments.stream()
                .filter(cp -> cp.getBucket() != null && payerId.toString().equals(cp.getBucket().getPayerId()))
                .collect(Collectors.toList());

        return checkPayments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gets all pending acknowledgments.
     * Returns checks in ASSIGNED status awaiting user acknowledgment.
     *
     * @return List of check payment DTOs
     */
    public List<CheckPaymentDTO> getPendingAcknowledgments() {
        List<CheckPayment> pendingChecks = checkPaymentRepository.findAwaitingAcknowledgment();
        return pendingChecks.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gets audit trail for a check payment.
     *
     * @param checkPaymentId Check payment ID
     * @return List of audit log entries
     */
    public List<CheckAuditLogDTO> getCheckAuditTrail(UUID checkPaymentId) {
        CheckPayment checkPayment = checkPaymentRepository.findById(checkPaymentId)
                .orElseThrow(() -> new CheckPaymentNotFoundException(checkPaymentId));

        List<CheckAuditLog> auditLogs = auditLogRepository
                .findByCheckPaymentOrderByCreatedAtAsc(checkPayment);

        return auditLogs.stream()
                .map(this::toAuditDTO)
                .collect(Collectors.toList());
    }

    /**
     * Converts CheckPayment entity to DTO.
     */
    private CheckPaymentDTO toDTO(CheckPayment checkPayment) {
        int voidTimeLimitHours = configRepository.getVoidTimeLimitHours();
        boolean canVoid = checkPayment.canBeVoided(voidTimeLimitHours);
        Integer hoursUntilDeadline = null;

        if (checkPayment.getStatus() == CheckPayment.CheckStatus.ISSUED && checkPayment.getIssuedAt() != null) {
            LocalDateTime deadline = checkPayment.getIssuedAt().plusHours(voidTimeLimitHours);
            hoursUntilDeadline = (int) ChronoUnit.HOURS.between(LocalDateTime.now(), deadline);
        }

        return CheckPaymentDTO.builder()
                .id(checkPayment.getId() != null ? checkPayment.getId().toString() : null)
                .bucketId(checkPayment.getBucket() != null ?
                        checkPayment.getBucket().getBucketId().toString() : null)
                .checkNumber(checkPayment.getCheckNumber())
                .checkAmount(checkPayment.getCheckAmount())
                .checkDate(checkPayment.getCheckDate())
                .bankName(checkPayment.getBankName())
                .routingNumber(checkPayment.getRoutingNumber())
                .accountNumberLast4(checkPayment.getAccountNumberLast4())
                .status(checkPayment.getStatus() != null ? checkPayment.getStatus().name() : null)
                .assignedBy(checkPayment.getAssignedBy())
                .assignedAt(checkPayment.getAssignedAt())
                .acknowledgedBy(checkPayment.getAcknowledgedBy())
                .acknowledgedAt(checkPayment.getAcknowledgedAt())
                .issuedBy(checkPayment.getIssuedBy())
                .issuedAt(checkPayment.getIssuedAt())
                .voidReason(checkPayment.getVoidReason())
                .voidedBy(checkPayment.getVoidedBy())
                .voidedAt(checkPayment.getVoidedAt())
                .paymentMethodId(checkPayment.getPaymentMethod() != null ?
                        checkPayment.getPaymentMethod().getId().toString() : null)
                .createdAt(checkPayment.getCreatedAt())
                .updatedAt(checkPayment.getUpdatedAt())
                .canBeVoided(canVoid)
                .hoursUntilVoidDeadline(hoursUntilDeadline)
                .build();
    }

    /**
     * Converts CheckAuditLog entity to DTO.
     */
    private CheckAuditLogDTO toAuditDTO(CheckAuditLog auditLog) {
        return CheckAuditLogDTO.builder()
                .id(auditLog.getId())
                .checkPaymentId(auditLog.getCheckPayment() != null ?
                        auditLog.getCheckPayment().getId().toString() : null)
                .checkNumber(auditLog.getCheckNumber())
                .action(auditLog.getAction())
                .bucketId(auditLog.getBucketId())
                .amount(auditLog.getAmount())
                .performedBy(auditLog.getPerformedBy())
                .notes(auditLog.getNotes())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
