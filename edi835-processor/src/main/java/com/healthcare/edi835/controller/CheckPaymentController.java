package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.CheckPayment;
import com.healthcare.edi835.exception.CheckPaymentNotFoundException;
import com.healthcare.edi835.exception.InvalidCheckStateException;
import com.healthcare.edi835.model.dto.*;
import com.healthcare.edi835.service.CheckPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for check payment operations.
 * Provides endpoints for managing check payments throughout their lifecycle.
 *
 * <p>Base Path: /api/v1/check-payments</p>
 *
 * <p>Check Payment Lifecycle:</p>
 * <pre>
 * RESERVED → ASSIGNED → ACKNOWLEDGED → ISSUED
 *              ↓
 *          VOID/CANCELLED
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/check-payments")
public class CheckPaymentController {

    private final CheckPaymentService checkPaymentService;

    public CheckPaymentController(CheckPaymentService checkPaymentService) {
        this.checkPaymentService = checkPaymentService;
    }

    // ==================== Check Assignment ====================

    /**
     * Auto-assigns a check from available reservations to a bucket.
     * System automatically picks next available check from payer's reservation pool.
     *
     * POST /api/v1/check-payments/buckets/{bucketId}/assign-auto
     */
    @PostMapping("/buckets/{bucketId}/assign-auto")
    public ResponseEntity<?> assignCheckAutomatically(
            @PathVariable UUID bucketId,
            @RequestBody Map<String, String> request) {
        String assignedBy = request.get("assignedBy");
        log.info("POST /api/v1/check-payments/buckets/{}/assign-auto - Auto check assignment by {}",
                bucketId, assignedBy);

        try {
            // Need to get payer ID from bucket via service
            // The service method assignCheckAutomatically will extract it
            CheckPayment checkPayment = checkPaymentService.assignCheckAutomaticallyFromBucket(bucketId, assignedBy);
            CheckPaymentDTO dto = convertToDTO(checkPayment);

            log.info("Check {} auto-assigned to bucket {} by {}",
                    checkPayment.getCheckNumber(), bucketId, assignedBy);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(dto);

        } catch (IllegalArgumentException e) {
            log.error("Cannot auto-assign check: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error auto-assigning check to bucket {}", bucketId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to auto-assign check: " + e.getMessage()));
        }
    }

    /**
     * Assigns a check manually to a bucket during approval.
     * User enters all check details (number, date, bank info).
     *
     * POST /api/v1/check-payments/buckets/{bucketId}/assign-manual
     */
    @PostMapping("/buckets/{bucketId}/assign-manual")
    public ResponseEntity<?> assignCheckManually(
            @PathVariable UUID bucketId,
            @Valid @RequestBody ManualCheckAssignmentRequest request) {
        log.info("POST /api/v1/check-payments/buckets/{}/assign-manual - Manual check assignment by {}",
                bucketId, request.getAssignedBy());

        try {
            CheckPayment checkPayment = checkPaymentService.assignCheckManually(bucketId, request);
            CheckPaymentDTO dto = convertToDTO(checkPayment);

            log.info("Check {} manually assigned to bucket {} by {}",
                    checkPayment.getCheckNumber(), bucketId, request.getAssignedBy());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(dto);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request for manual check assignment: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error assigning check manually to bucket {}", bucketId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to assign check: " + e.getMessage()));
        }
    }

    /**
     * Replaces an existing check assignment with a new check.
     * Voids the old check and assigns a new one.
     *
     * Only allowed for buckets in PENDING_APPROVAL status with ASSIGNED payment.
     *
     * POST /api/v1/check-payments/buckets/{bucketId}/replace
     */
    @PostMapping("/buckets/{bucketId}/replace")
    public ResponseEntity<?> replaceCheck(
            @PathVariable UUID bucketId,
            @Valid @RequestBody ManualCheckAssignmentRequest request) {
        log.info("POST /api/v1/check-payments/buckets/{}/replace - Replacing check by {}",
                bucketId, request.getAssignedBy());

        try {
            CheckPayment newCheckPayment = checkPaymentService.replaceCheck(bucketId, request);
            CheckPaymentDTO dto = convertToDTO(newCheckPayment);

            log.info("Check replaced for bucket {}: new check {}",
                    bucketId, newCheckPayment.getCheckNumber());

            return ResponseEntity.ok(Map.of(
                    "message", "Check replaced successfully",
                    "checkPayment", dto
            ));

        } catch (IllegalStateException e) {
            log.warn("Cannot replace check for bucket {}: {}", bucketId, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.error("Invalid request for check replacement: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error replacing check for bucket {}", bucketId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to replace check: " + e.getMessage()));
        }
    }

    // ==================== Get Check Payments ====================

    /**
     * Gets all check payments.
     * Optionally filter by status using query parameter.
     *
     * GET /api/v1/check-payments
     * GET /api/v1/check-payments?status=ISSUED
     */
    @GetMapping
    public ResponseEntity<?> getAllCheckPayments(@RequestParam(required = false) String status) {
        log.debug("GET /api/v1/check-payments - Retrieving all check payments, status filter: {}", status);

        try {
            List<CheckPaymentDTO> checkPayments;
            if (status != null && !status.trim().isEmpty()) {
                checkPayments = checkPaymentService.getCheckPaymentsByStatus(status);
            } else {
                checkPayments = checkPaymentService.getAllCheckPayments();
            }
            return ResponseEntity.ok(checkPayments);

        } catch (IllegalArgumentException e) {
            log.error("Invalid status parameter: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Invalid status: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving check payments", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve check payments: " + e.getMessage()));
        }
    }

    /**
     * Gets check payment for a specific bucket.
     *
     * GET /api/v1/check-payments/buckets/{bucketId}
     */
    @GetMapping("/buckets/{bucketId}")
    public ResponseEntity<?> getCheckPaymentForBucket(@PathVariable UUID bucketId) {
        log.debug("GET /api/v1/check-payments/buckets/{} - Retrieving check payment", bucketId);

        try {
            return checkPaymentService.getCheckPaymentForBucket(bucketId)
                    .map(checkPayment -> {
                        CheckPaymentDTO dto = convertToDTO(checkPayment);
                        return ResponseEntity.ok(dto);
                    })
                    .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            log.error("Error retrieving check payment for bucket {}", bucketId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve check payment: " + e.getMessage()));
        }
    }

    /**
     * Gets check payments for a specific payer.
     *
     * GET /api/v1/check-payments/payer/{payerId}
     */
    @GetMapping("/payer/{payerId}")
    public ResponseEntity<?> getCheckPaymentsForPayer(@PathVariable UUID payerId) {
        log.debug("GET /api/v1/check-payments/payer/{} - Retrieving check payments", payerId);

        try {
            List<CheckPaymentDTO> checkPayments = checkPaymentService.getCheckPaymentsForPayer(payerId);
            return ResponseEntity.ok(checkPayments);

        } catch (Exception e) {
            log.error("Error retrieving check payments for payer {}", payerId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve check payments: " + e.getMessage()));
        }
    }

    /**
     * Gets check payment by ID.
     *
     * GET /api/v1/check-payments/{checkPaymentId}
     */
    @GetMapping("/{checkPaymentId}")
    public ResponseEntity<?> getCheckPayment(@PathVariable UUID checkPaymentId) {
        log.debug("GET /api/v1/check-payments/{} - Retrieving check payment", checkPaymentId);

        try {
            // This would need a getById method in service - using audit trail as workaround
            CheckAuditLogDTO firstLog = checkPaymentService.getCheckAuditTrail(checkPaymentId)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new CheckPaymentNotFoundException(checkPaymentId));

            // Note: In production, add getById() method to CheckPaymentService
            return ResponseEntity.ok(Map.of("message", "Check payment exists", "id", checkPaymentId));

        } catch (CheckPaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving check payment {}", checkPaymentId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve check payment: " + e.getMessage()));
        }
    }

    // ==================== Acknowledge Check ====================

    /**
     * Acknowledges a check payment amount.
     * User verifies the check amount before issuance.
     *
     * POST /api/v1/check-payments/{checkPaymentId}/acknowledge
     */
    @PostMapping("/{checkPaymentId}/acknowledge")
    public ResponseEntity<?> acknowledgeCheck(
            @PathVariable UUID checkPaymentId,
            @Valid @RequestBody AcknowledgeCheckRequest request) {
        log.info("POST /api/v1/check-payments/{}/acknowledge - Acknowledging check by {}",
                checkPaymentId, request.getAcknowledgedBy());

        try {
            CheckPayment checkPayment = checkPaymentService.acknowledgeCheck(
                    checkPaymentId,
                    request.getAcknowledgedBy());
            CheckPaymentDTO dto = convertToDTO(checkPayment);

            log.info("Check payment {} acknowledged by {}", checkPaymentId, request.getAcknowledgedBy());

            return ResponseEntity.ok(dto);

        } catch (CheckPaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (InvalidCheckStateException e) {
            log.error("Invalid check state for acknowledgment: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error acknowledging check payment {}", checkPaymentId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to acknowledge check: " + e.getMessage()));
        }
    }

    // ==================== Get Pending Acknowledgments ====================

    /**
     * Gets all pending acknowledgments.
     * Returns checks in ASSIGNED status awaiting user acknowledgment.
     *
     * GET /api/v1/check-payments/pending-acknowledgments
     */
    @GetMapping("/pending-acknowledgments")
    public ResponseEntity<?> getPendingAcknowledgments() {
        log.debug("GET /api/v1/check-payments/pending-acknowledgments - Retrieving pending acknowledgments");

        try {
            List<CheckPaymentDTO> pendingChecks = checkPaymentService.getPendingAcknowledgments();
            return ResponseEntity.ok(pendingChecks);

        } catch (Exception e) {
            log.error("Error retrieving pending acknowledgments", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve pending acknowledgments: " + e.getMessage()));
        }
    }

    // ==================== Mark Check as Issued ====================

    /**
     * Marks a check as issued (physically mailed/delivered).
     *
     * POST /api/v1/check-payments/{checkPaymentId}/issue
     */
    @PostMapping("/{checkPaymentId}/issue")
    public ResponseEntity<?> markCheckIssued(
            @PathVariable UUID checkPaymentId,
            @Valid @RequestBody IssueCheckRequest request) {
        log.info("POST /api/v1/check-payments/{}/issue - Marking check as issued by {}",
                checkPaymentId, request.getIssuedBy());

        try {
            CheckPayment checkPayment = checkPaymentService.markCheckIssued(
                    checkPaymentId,
                    request.getIssuedBy());
            CheckPaymentDTO dto = convertToDTO(checkPayment);

            log.info("Check payment {} marked as issued by {}", checkPaymentId, request.getIssuedBy());

            return ResponseEntity.ok(dto);

        } catch (CheckPaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (InvalidCheckStateException e) {
            log.error("Invalid check state for issuance: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error marking check payment {} as issued", checkPaymentId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to mark check as issued: " + e.getMessage()));
        }
    }

    // ==================== Void Check ====================

    /**
     * Voids a check payment.
     * Requires FINANCIAL_ADMIN role and must be within configured time limit.
     *
     * POST /api/v1/check-payments/{checkPaymentId}/void
     */
    @PostMapping("/{checkPaymentId}/void")
    public ResponseEntity<?> voidCheck(
            @PathVariable UUID checkPaymentId,
            @Valid @RequestBody VoidCheckRequest request) {
        log.info("POST /api/v1/check-payments/{}/void - Voiding check by {}: reason={}",
                checkPaymentId, request.getVoidedBy(), request.getReason());

        try {
            CheckPayment checkPayment = checkPaymentService.voidCheck(
                    checkPaymentId,
                    request.getReason(),
                    request.getVoidedBy());
            CheckPaymentDTO dto = convertToDTO(checkPayment);

            log.info("Check payment {} voided by {}", checkPaymentId, request.getVoidedBy());

            return ResponseEntity.ok(dto);

        } catch (CheckPaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (InvalidCheckStateException e) {
            log.error("Cannot void check: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error voiding check payment {}", checkPaymentId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to void check: " + e.getMessage()));
        }
    }

    // ==================== Get Audit Trail ====================

    /**
     * Gets audit trail for a check payment.
     * Returns complete history of all operations performed on the check.
     *
     * GET /api/v1/check-payments/{checkPaymentId}/audit-trail
     */
    @GetMapping("/{checkPaymentId}/audit-trail")
    public ResponseEntity<?> getAuditTrail(@PathVariable UUID checkPaymentId) {
        log.debug("GET /api/v1/check-payments/{}/audit-trail - Retrieving audit trail", checkPaymentId);

        try {
            List<CheckAuditLogDTO> auditTrail = checkPaymentService.getCheckAuditTrail(checkPaymentId);
            return ResponseEntity.ok(auditTrail);

        } catch (CheckPaymentNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving audit trail for check payment {}", checkPaymentId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve audit trail: " + e.getMessage()));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Converts CheckPayment entity to DTO.
     * This is a simplified version - the service already has toDTO method.
     */
    private CheckPaymentDTO convertToDTO(CheckPayment checkPayment) {
        // In production, move this to a mapper class or use the service's toDTO method
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
                .createdAt(checkPayment.getCreatedAt())
                .updatedAt(checkPayment.getUpdatedAt())
                .build();
    }
}
