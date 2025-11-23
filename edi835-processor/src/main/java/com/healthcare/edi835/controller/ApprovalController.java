package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.BucketApprovalLog;
import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.exception.CheckAssignmentException;
import com.healthcare.edi835.exception.NoAvailableChecksException;
import com.healthcare.edi835.model.dto.ApprovalRequestDTO;
import com.healthcare.edi835.service.ApprovalWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for approval workflow operations.
 * Provides endpoints for managing bucket approvals and rejections.
 *
 * <p>Base Path: /api/v1/approvals</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/approvals")
// CORS configured globally in WebConfig.java - do not override with wildcard
public class ApprovalController {

    private final ApprovalWorkflowService approvalWorkflowService;

    public ApprovalController(ApprovalWorkflowService approvalWorkflowService) {
        this.approvalWorkflowService = approvalWorkflowService;
    }

    // ==================== Approval Queue ====================

    @GetMapping("/pending")
    public ResponseEntity<List<EdiFileBucket>> getPendingApprovals() {
        log.debug("GET /api/v1/approvals/pending - Retrieving pending approvals");
        List<EdiFileBucket> buckets = approvalWorkflowService.getPendingApprovals();
        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/pending/payer/{payerId}")
    public ResponseEntity<List<EdiFileBucket>> getPendingApprovalsForPayer(@PathVariable String payerId) {
        log.debug("GET /api/v1/approvals/pending/payer/{} - Retrieving pending approvals", payerId);
        List<EdiFileBucket> buckets = approvalWorkflowService.getPendingApprovalsForPayer(payerId);
        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/pending/payee/{payeeId}")
    public ResponseEntity<List<EdiFileBucket>> getPendingApprovalsForPayee(@PathVariable String payeeId) {
        log.debug("GET /api/v1/approvals/pending/payee/{} - Retrieving pending approvals", payeeId);
        List<EdiFileBucket> buckets = approvalWorkflowService.getPendingApprovalsForPayee(payeeId);
        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/pending/count")
    public ResponseEntity<Map<String, Long>> getPendingApprovalCount() {
        log.debug("GET /api/v1/approvals/pending/count - Counting pending approvals");
        long count = approvalWorkflowService.getPendingApprovals().size();
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ==================== Approve/Reject Operations ====================

    @PostMapping("/approve/{bucketId}")
    public ResponseEntity<Map<String, String>> approveBucket(
            @PathVariable UUID bucketId,
            @Valid @RequestBody ApprovalRequestDTO request) {
        log.info("POST /api/v1/approvals/approve/{} - Approving bucket by {}",
                bucketId, request.getActionBy());

        try {
            approvalWorkflowService.approveBucket(
                    bucketId,
                    request.getActionBy(),
                    request.getComments());

            Map<String, String> response = Map.of(
                    "message", "Bucket approved successfully",
                    "bucketId", bucketId.toString(),
                    "approvedBy", request.getActionBy()
            );

            return ResponseEntity.ok(response);

        } catch (CheckAssignmentException e) {
            // Check assignment failed - approval has been rolled back
            log.error("Check assignment failed for bucket {}, approval rolled back: {}",
                    bucketId, e.getMessage(), e);

            // Determine if it's due to no available checks
            String errorCode = "CHECK_ASSIGNMENT_FAILED";
            String userMessage = "Approval failed: Check assignment could not be completed. " +
                    "The approval has been rolled back. Please retry or assign check manually.";

            if (e.getCause() instanceof NoAvailableChecksException) {
                errorCode = "NO_AVAILABLE_CHECKS";
                userMessage = "Approval failed: No check numbers available for auto-assignment. " +
                        "Please add more check reservations or assign check manually.";
            }

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", userMessage,
                            "errorCode", errorCode,
                            "bucketId", bucketId.toString(),
                            "cause", e.getCause() != null ? e.getCause().getMessage() : e.getMessage()
                    ));

        } catch (IllegalStateException e) {
            log.warn("Failed to approve bucket {}: {}", bucketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.error("Bucket not found: {}", bucketId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Bucket not found"));

        } catch (Exception e) {
            log.error("Error approving bucket {}: {}", bucketId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @PostMapping("/reject/{bucketId}")
    public ResponseEntity<Map<String, String>> rejectBucket(
            @PathVariable UUID bucketId,
            @Valid @RequestBody ApprovalRequestDTO request) {
        log.info("POST /api/v1/approvals/reject/{} - Rejecting bucket by {}",
                bucketId, request.getActionBy());

        try {
            if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Rejection reason is required"));
            }

            approvalWorkflowService.rejectBucket(
                    bucketId,
                    request.getActionBy(),
                    request.getRejectionReason());

            Map<String, String> response = Map.of(
                    "message", "Bucket rejected successfully",
                    "bucketId", bucketId.toString(),
                    "rejectedBy", request.getActionBy(),
                    "reason", request.getRejectionReason()
            );

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.warn("Failed to reject bucket {}: {}", bucketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.error("Bucket not found or invalid reason: {}", bucketId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error rejecting bucket {}: {}", bucketId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    // ==================== Bulk Operations ====================

    @PostMapping("/approve/bulk")
    public ResponseEntity<Map<String, Object>> bulkApproveBuckets(
            @RequestBody Map<String, Object> request) {
        log.info("POST /api/v1/approvals/approve/bulk - Bulk approval requested");

        try {
            @SuppressWarnings("unchecked")
            List<String> bucketIdStrings = (List<String>) request.get("bucketIds");
            String approvedBy = (String) request.get("approvedBy");
            String comments = (String) request.get("comments");

            List<UUID> bucketIds = bucketIdStrings.stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());

            int approvedCount = approvalWorkflowService.bulkApproveBuckets(
                    bucketIds, approvedBy, comments);

            Map<String, Object> response = Map.of(
                    "message", "Bulk approval completed",
                    "total", bucketIds.size(),
                    "approved", approvedCount,
                    "failed", bucketIds.size() - approvedCount
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in bulk approval: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Bulk approval failed: " + e.getMessage()));
        }
    }

    // ==================== Approval History ====================

    @GetMapping("/history/{bucketId}")
    public ResponseEntity<List<BucketApprovalLog>> getApprovalHistory(@PathVariable UUID bucketId) {
        log.debug("GET /api/v1/approvals/history/{} - Retrieving approval history", bucketId);

        try {
            List<BucketApprovalLog> history = approvalWorkflowService.getApprovalHistory(bucketId);
            return ResponseEntity.ok(history);

        } catch (IllegalArgumentException e) {
            log.error("Bucket not found: {}", bucketId);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/history/user/{username}")
    public ResponseEntity<List<BucketApprovalLog>> getApprovalsByUser(@PathVariable String username) {
        log.debug("GET /api/v1/approvals/history/user/{} - Retrieving user approvals", username);
        List<BucketApprovalLog> approvals = approvalWorkflowService.getApprovalsByUser(username);
        return ResponseEntity.ok(approvals);
    }

    // ==================== Approval Statistics ====================

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getApprovalStatistics() {
        log.debug("GET /api/v1/approvals/statistics - Retrieving approval statistics");

        List<Object[]> stats = approvalWorkflowService.getApprovalStatistics();

        Map<String, Object> response = stats.stream()
                .collect(Collectors.toMap(
                        row -> ((BucketApprovalLog.ApprovalAction) row[0]).name(),
                        row -> Map.of(
                                "count", row[1],
                                "totalAmount", row[2] != null ? row[2] : 0
                        )
                ));

        return ResponseEntity.ok(response);
    }

    // ==================== Authorization Check ====================

    @GetMapping("/authorize/{bucketId}")
    public ResponseEntity<Map<String, Boolean>> checkAuthorization(
            @PathVariable UUID bucketId,
            @RequestParam String username,
            @RequestParam String roles) {
        log.debug("GET /api/v1/approvals/authorize/{}?username={}&roles={}",
                bucketId, username, roles);

        boolean isAuthorized = approvalWorkflowService.isAuthorizedToApprove(
                bucketId, username, roles);

        return ResponseEntity.ok(Map.of("authorized", isAuthorized));
    }

    // ==================== Reset Failed Bucket ====================

    @PostMapping("/reset/{bucketId}")
    public ResponseEntity<Map<String, String>> resetFailedBucket(
            @PathVariable UUID bucketId,
            @RequestBody Map<String, String> request) {
        log.info("POST /api/v1/approvals/reset/{} - Resetting failed bucket", bucketId);

        try {
            String resetBy = request.get("resetBy");
            String reason = request.get("reason");

            if (resetBy == null || reason == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "resetBy and reason are required"));
            }

            approvalWorkflowService.resetFailedBucket(bucketId, resetBy, reason);

            Map<String, String> response = Map.of(
                    "message", "Bucket reset successfully",
                    "bucketId", bucketId.toString(),
                    "resetBy", resetBy
            );

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.warn("Failed to reset bucket {}: {}", bucketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.error("Bucket not found: {}", bucketId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Bucket not found"));

        } catch (Exception e) {
            log.error("Error resetting bucket {}: {}", bucketId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    // ==================== Quick Actions ====================

    @GetMapping("/quick-approve/{bucketId}")
    public ResponseEntity<Map<String, String>> quickApprove(
            @PathVariable UUID bucketId,
            @RequestParam String approvedBy) {
        log.info("GET /api/v1/approvals/quick-approve/{}?approvedBy={}", bucketId, approvedBy);

        try {
            approvalWorkflowService.approveBucket(bucketId, approvedBy, "Quick approval via API");

            return ResponseEntity.ok(Map.of(
                    "message", "Bucket approved",
                    "bucketId", bucketId.toString()
            ));

        } catch (CheckAssignmentException e) {
            log.error("Quick approval failed for bucket {} - check assignment failed, rolled back: {}",
                    bucketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Check assignment failed - approval rolled back",
                            "errorCode", e.getCause() instanceof NoAvailableChecksException
                                    ? "NO_AVAILABLE_CHECKS" : "CHECK_ASSIGNMENT_FAILED",
                            "cause", e.getCause() != null ? e.getCause().getMessage() : e.getMessage()
                    ));

        } catch (Exception e) {
            log.error("Quick approval failed for bucket {}: {}", bucketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== Approval Metrics ====================

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getApprovalMetrics() {
        log.debug("GET /api/v1/approvals/metrics - Retrieving approval metrics");

        int pendingCount = approvalWorkflowService.getPendingApprovals().size();
        List<Object[]> stats = approvalWorkflowService.getApprovalStatistics();

        long approvalCount = stats.stream()
                .filter(row -> row[0] == BucketApprovalLog.ApprovalAction.APPROVE)
                .mapToLong(row -> (Long) row[1])
                .sum();

        long rejectionCount = stats.stream()
                .filter(row -> row[0] == BucketApprovalLog.ApprovalAction.REJECT)
                .mapToLong(row -> (Long) row[1])
                .sum();

        double approvalRate = (approvalCount + rejectionCount) > 0
                ? (double) approvalCount / (approvalCount + rejectionCount) * 100
                : 0;

        Map<String, Object> metrics = Map.of(
                "pendingCount", pendingCount,
                "totalApprovals", approvalCount,
                "totalRejections", rejectionCount,
                "approvalRate", String.format("%.2f%%", approvalRate)
        );

        return ResponseEntity.ok(metrics);
    }
}
