package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.FileGenerationHistory;
import com.healthcare.edi835.model.projection.FileGenerationSummary;
import com.healthcare.edi835.repository.FileGenerationHistoryRepository;
import com.healthcare.edi835.service.FileDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for file delivery operations.
 * Provides endpoints for managing SFTP file delivery and retry logic.
 *
 * <p>Base Path: /api/v1/delivery</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/delivery")
// CORS configured globally in WebConfig.java - do not override with wildcard
public class DeliveryController {

    private final FileDeliveryService deliveryService;
    private final FileGenerationHistoryRepository historyRepository;

    public DeliveryController(
            FileDeliveryService deliveryService,
            FileGenerationHistoryRepository historyRepository) {
        this.deliveryService = deliveryService;
        this.historyRepository = historyRepository;
    }

    // ==================== File Delivery ====================

    @PostMapping("/deliver/{fileId}")
    public ResponseEntity<Map<String, String>> deliverFile(@PathVariable UUID fileId) {
        log.info("POST /api/v1/delivery/deliver/{} - Delivering file", fileId);

        try {
            deliveryService.deliverFile(fileId);

            return ResponseEntity.ok(Map.of(
                    "message", "File delivered successfully",
                    "fileId", fileId.toString()
            ));

        } catch (FileDeliveryService.DeliveryException e) {
            log.error("Delivery failed for file {}: {}", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error delivering file {}: {}", fileId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Delivery failed: " + e.getMessage()));
        }
    }

    @PostMapping("/deliver/batch")
    public ResponseEntity<Map<String, Object>> deliverBatch(@RequestBody Map<String, List<String>> request) {
        log.info("POST /api/v1/delivery/deliver/batch - Batch delivery requested");

        List<String> fileIdStrings = request.get("fileIds");
        int successCount = 0;
        int errorCount = 0;

        for (String fileIdStr : fileIdStrings) {
            try {
                UUID fileId = UUID.fromString(fileIdStr);
                deliveryService.deliverFile(fileId);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to deliver file {}: {}", fileIdStr, e.getMessage());
            }
        }

        Map<String, Object> response = Map.of(
                "message", "Batch delivery completed",
                "total", fileIdStrings.size(),
                "success", successCount,
                "failed", errorCount
        );

        return ResponseEntity.ok(response);
    }

    // ==================== Delivery Status ====================

    @GetMapping("/status/{fileId}")
    public ResponseEntity<Map<String, Object>> getDeliveryStatus(@PathVariable UUID fileId) {
        log.debug("GET /api/v1/delivery/status/{} - Retrieving delivery status", fileId);

        return deliveryService.getDeliveryStatus(fileId)
                .map(status -> {
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("fileId", fileId);
                    result.put("status", status.name());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<FileGenerationSummary>> getPendingDeliveries() {
        log.debug("GET /api/v1/delivery/pending - Retrieving pending deliveries");
        List<FileGenerationSummary> files = historyRepository.findPendingDeliveriesSummary();
        return ResponseEntity.ok(files);
    }

    @GetMapping("/failed")
    public ResponseEntity<List<FileGenerationSummary>> getFailedDeliveries() {
        log.debug("GET /api/v1/delivery/failed - Retrieving failed deliveries");
        List<FileGenerationSummary> files = historyRepository.findFailedDeliveriesSummary();
        return ResponseEntity.ok(files);
    }

    @GetMapping("/delivered")
    public ResponseEntity<List<FileGenerationSummary>> getDeliveredFiles() {
        log.debug("GET /api/v1/delivery/delivered - Retrieving delivered files");
        List<FileGenerationSummary> files = historyRepository.findSummariesByDeliveryStatus(
                FileGenerationHistory.DeliveryStatus.DELIVERED);
        return ResponseEntity.ok(files);
    }

    // ==================== Retry Operations ====================

    @PostMapping("/retry/{fileId}")
    public ResponseEntity<Map<String, String>> retryDelivery(@PathVariable UUID fileId) {
        log.info("POST /api/v1/delivery/retry/{} - Retrying delivery", fileId);

        try {
            deliveryService.deliverFile(fileId);

            return ResponseEntity.ok(Map.of(
                    "message", "Delivery retry successful",
                    "fileId", fileId.toString()
            ));

        } catch (FileDeliveryService.DeliveryException e) {
            log.error("Retry failed for file {}: {}", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error retrying delivery for file {}: {}", fileId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Retry failed: " + e.getMessage()));
        }
    }

    @PostMapping("/retry-all-failed")
    public ResponseEntity<Map<String, Object>> retryAllFailed() {
        log.info("POST /api/v1/delivery/retry-all-failed - Retrying all failed deliveries");

        int successCount = deliveryService.retryFailedDeliveries();

        Map<String, Object> response = Map.of(
                "message", "Retry operation completed",
                "successCount", successCount
        );

        return ResponseEntity.ok(response);
    }

    // ==================== Manual Operations ====================

    @PostMapping("/mark-delivered/{fileId}")
    public ResponseEntity<Map<String, String>> markAsDelivered(
            @PathVariable UUID fileId,
            @RequestParam String deliveredBy) {
        log.info("POST /api/v1/delivery/mark-delivered/{}?deliveredBy={}", fileId, deliveredBy);

        try {
            deliveryService.markAsDelivered(fileId, deliveredBy);

            return ResponseEntity.ok(Map.of(
                    "message", "File marked as delivered",
                    "fileId", fileId.toString(),
                    "deliveredBy", deliveredBy
            ));

        } catch (IllegalArgumentException e) {
            log.error("File not found: {}", fileId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "File not found"));

        } catch (Exception e) {
            log.error("Error marking file as delivered: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Operation failed: " + e.getMessage()));
        }
    }

    // ==================== SFTP Configuration ====================

    @GetMapping("/validate-sftp/{payerId}")
    public ResponseEntity<Map<String, Object>> validateSftpConfig(@PathVariable String payerId) {
        log.debug("GET /api/v1/delivery/validate-sftp/{} - Validating SFTP config", payerId);

        boolean isValid = deliveryService.validateSftpConfig(payerId);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("payerId", payerId);
        response.put("valid", isValid);
        return ResponseEntity.ok(response);
    }

    // ==================== Delivery Statistics ====================

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getDeliveryStatistics() {
        log.debug("GET /api/v1/delivery/statistics - Retrieving delivery statistics");

        List<Object[]> stats = deliveryService.getDeliveryStatistics();

        Map<String, Object> response = stats.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> ((FileGenerationHistory.DeliveryStatus) row[0]).name(),
                        row -> Map.of(
                                "count", row[1],
                                "totalClaims", row[2] != null ? row[2] : 0,
                                "totalAmount", row[3] != null ? row[3] : 0
                        )
                ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics/summary")
    public ResponseEntity<Map<String, Object>> getDeliverySummary() {
        log.debug("GET /api/v1/delivery/statistics/summary");

        int pendingCount = historyRepository.findPendingDeliveriesSummary().size();
        int failedCount = historyRepository.findFailedDeliveriesSummary().size();
        int deliveredCount = historyRepository.findSummariesByDeliveryStatus(
                FileGenerationHistory.DeliveryStatus.DELIVERED).size();

        int totalCount = pendingCount + failedCount + deliveredCount;

        Map<String, Object> summary = Map.of(
                "pending", pendingCount,
                "failed", failedCount,
                "delivered", deliveredCount,
                "total", totalCount,
                "deliveryRate", totalCount > 0
                        ? String.format("%.2f%%", (double) deliveredCount / totalCount * 100)
                        : "0%",
                "failureRate", totalCount > 0
                        ? String.format("%.2f%%", (double) failedCount / totalCount * 100)
                        : "0%"
        );

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/statistics/retry-metrics")
    public ResponseEntity<Map<String, Object>> getRetryMetrics() {
        log.debug("GET /api/v1/delivery/statistics/retry-metrics");

        List<FileGenerationSummary> allFiles = historyRepository.findAllSummaries();

        long filesWithRetries = allFiles.stream()
                .filter(f -> f.getRetryCount() != null && f.getRetryCount() > 0)
                .count();

        double avgRetries = allFiles.stream()
                .filter(f -> f.getRetryCount() != null)
                .mapToInt(FileGenerationSummary::getRetryCount)
                .average()
                .orElse(0);

        int maxRetries = allFiles.stream()
                .filter(f -> f.getRetryCount() != null)
                .mapToInt(FileGenerationSummary::getRetryCount)
                .max()
                .orElse(0);

        Map<String, Object> metrics = Map.of(
                "filesWithRetries", filesWithRetries,
                "averageRetries", String.format("%.2f", avgRetries),
                "maxRetries", maxRetries
        );

        return ResponseEntity.ok(metrics);
    }

    // ==================== Delivery Queue Management ====================

    @GetMapping("/queue/size")
    public ResponseEntity<Map<String, Integer>> getQueueSize() {
        log.debug("GET /api/v1/delivery/queue/size - Getting queue size");

        int queueSize = historyRepository.findPendingDeliveriesSummary().size();

        return ResponseEntity.ok(Map.of("queueSize", queueSize));
    }

    @GetMapping("/queue/details")
    public ResponseEntity<List<Map<String, Object>>> getQueueDetails() {
        log.debug("GET /api/v1/delivery/queue/details - Getting queue details");

        List<FileGenerationSummary> pendingFiles = historyRepository.findPendingDeliveriesSummary();

        List<Map<String, Object>> queueDetails = pendingFiles.stream()
                .map(file -> {
                    Map<String, Object> detail = new java.util.HashMap<>();
                    detail.put("fileId", file.getFileId());
                    detail.put("fileName", file.getFileName());
                    detail.put("payerId", file.getBucket().getPayerId());
                    detail.put("retryCount", file.getRetryCount() != null ? file.getRetryCount() : 0);
                    detail.put("createdAt", file.getGeneratedAt());
                    return detail;
                })
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(queueDetails);
    }

    // ==================== Error Tracking ====================

    @GetMapping("/errors")
    public ResponseEntity<List<Map<String, Object>>> getDeliveryErrors() {
        log.debug("GET /api/v1/delivery/errors - Retrieving delivery errors");

        List<FileGenerationSummary> failedFiles = historyRepository.findFailedDeliveriesSummary();

        List<Map<String, Object>> errors = failedFiles.stream()
                .filter(file -> file.getErrorMessage() != null)
                .map(file -> {
                    Map<String, Object> error = new java.util.HashMap<>();
                    error.put("fileId", file.getFileId());
                    error.put("fileName", file.getFileName());
                    error.put("payerId", file.getBucket().getPayerId());
                    error.put("errorMessage", file.getErrorMessage());
                    error.put("retryCount", file.getRetryCount() != null ? file.getRetryCount() : 0);
                    error.put("lastAttempt", file.getGeneratedAt());
                    return error;
                })
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(errors);
    }
}
