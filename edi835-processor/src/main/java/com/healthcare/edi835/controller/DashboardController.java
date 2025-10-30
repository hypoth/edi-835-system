package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.FileGenerationHistory;
import com.healthcare.edi835.model.dto.DashboardSummaryDTO;
import com.healthcare.edi835.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for dashboard and metrics.
 * Provides aggregated statistics and real-time metrics for Operations Manager dashboard.
 *
 * <p>Base Path: /api/v1/dashboard</p>
 *
 * <p>Note: CORS is configured globally in WebConfig.java to allow specific origins
 * (localhost:3000, localhost:5173 for dev). Do not use @CrossOrigin(origins = "*")
 * as it conflicts with allowCredentials=true.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final EdiFileBucketRepository bucketRepository;
    private final FileGenerationHistoryRepository fileHistoryRepository;
    private final ClaimProcessingLogRepository claimLogRepository;
    private final BucketApprovalLogRepository approvalLogRepository;

    public DashboardController(
            EdiFileBucketRepository bucketRepository,
            FileGenerationHistoryRepository fileHistoryRepository,
            ClaimProcessingLogRepository claimLogRepository,
            BucketApprovalLogRepository approvalLogRepository) {
        this.bucketRepository = bucketRepository;
        this.fileHistoryRepository = fileHistoryRepository;
        this.claimLogRepository = claimLogRepository;
        this.approvalLogRepository = approvalLogRepository;
    }

    // ==================== Main Dashboard Summary ====================

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDTO> getDashboardSummary() {
        log.debug("GET /api/v1/dashboard/summary - Retrieving dashboard summary");

        // Bucket statistics
        List<Object[]> bucketStats = bucketRepository.getBucketStatistics();
        long totalBuckets = bucketRepository.count();
        long activeBuckets = bucketStats.stream()
                .filter(row -> row[0] == EdiFileBucket.BucketStatus.ACCUMULATING)
                .mapToLong(row -> (Long) row[1])
                .sum();
        long pendingApproval = bucketStats.stream()
                .filter(row -> row[0] == EdiFileBucket.BucketStatus.PENDING_APPROVAL)
                .mapToLong(row -> (Long) row[1])
                .sum();

        // File statistics
        List<Object[]> fileStats = fileHistoryRepository.getGenerationStatistics();
        long totalFiles = fileHistoryRepository.count();
        long pendingDelivery = fileStats.stream()
                .filter(row -> row[0] == FileGenerationHistory.DeliveryStatus.PENDING)
                .mapToLong(row -> (Long) row[1])
                .sum();
        long failedDelivery = fileStats.stream()
                .filter(row -> row[0] == FileGenerationHistory.DeliveryStatus.FAILED)
                .mapToLong(row -> (Long) row[1])
                .sum();

        // Claim statistics
        long totalClaims = claimLogRepository.count();
        long processedClaims = claimLogRepository.countProcessedClaims();
        long rejectedClaims = claimLogRepository.countRejectedClaims();

        DashboardSummaryDTO summary = DashboardSummaryDTO.builder()
                .totalBuckets(totalBuckets)
                .activeBuckets(activeBuckets)
                .pendingApprovalBuckets(pendingApproval)
                .totalFiles(totalFiles)
                .pendingDeliveryFiles(pendingDelivery)
                .failedDeliveryFiles(failedDelivery)
                .totalClaims(totalClaims)
                .processedClaims(processedClaims)
                .rejectedClaims(rejectedClaims)
                .build();

        return ResponseEntity.ok(summary);
    }

    // ==================== Bucket Metrics ====================

    @GetMapping("/buckets/metrics")
    public ResponseEntity<Map<String, Object>> getBucketMetrics() {
        log.debug("GET /api/v1/dashboard/buckets/metrics");

        List<Object[]> stats = bucketRepository.getBucketStatistics();

        Map<String, Map<String, Object>> byStatus = new HashMap<>();

        for (Object[] row : stats) {
            EdiFileBucket.BucketStatus status = (EdiFileBucket.BucketStatus) row[0];
            Long count = (Long) row[1];
            Long totalClaims = row[2] != null ? (Long) row[2] : 0L;
            BigDecimal totalAmount = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

            byStatus.put(status.name(), Map.of(
                    "count", count,
                    "totalClaims", totalClaims,
                    "totalAmount", totalAmount
            ));
        }

        Map<String, Object> response = Map.of(
                "byStatus", byStatus,
                "totalBuckets", bucketRepository.count()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/buckets/by-payer")
    public ResponseEntity<Map<String, Long>> getBucketsByPayer() {
        log.debug("GET /api/v1/dashboard/buckets/by-payer");

        List<EdiFileBucket> buckets = bucketRepository.findAll();

        Map<String, Long> byPayer = buckets.stream()
                .collect(Collectors.groupingBy(
                        EdiFileBucket::getPayerId,
                        Collectors.counting()
                ));

        return ResponseEntity.ok(byPayer);
    }

    @GetMapping("/buckets/top-payers")
    public ResponseEntity<List<Map<String, Object>>> getTopPayers(
            @RequestParam(defaultValue = "10") int limit) {
        log.debug("GET /api/v1/dashboard/buckets/top-payers?limit={}", limit);

        List<EdiFileBucket> buckets = bucketRepository.findAll();

        List<Map<String, Object>> topPayers = buckets.stream()
                .collect(Collectors.groupingBy(
                        b -> Map.of("payerId", b.getPayerId(), "payerName", b.getPayerName()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> Map.of(
                                        "count", (long) list.size(),
                                        "totalClaims", list.stream()
                                                .mapToInt(EdiFileBucket::getClaimCount)
                                                .sum(),
                                        "totalAmount", list.stream()
                                                .map(EdiFileBucket::getTotalAmount)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                )
                        )
                ))
                .entrySet().stream()
                .map(e -> {
                    Map<String, Object> result = new HashMap<>(e.getKey());
                    result.putAll(e.getValue());
                    return result;
                })
                .sorted((a, b) -> Long.compare(
                        (Long) b.get("count"),
                        (Long) a.get("count")
                ))
                .limit(limit)
                .collect(Collectors.toList());

        return ResponseEntity.ok(topPayers);
    }

    // ==================== File Metrics ====================

    @GetMapping("/files/metrics")
    public ResponseEntity<Map<String, Object>> getFileMetrics() {
        log.debug("GET /api/v1/dashboard/files/metrics");

        List<Object[]> stats = fileHistoryRepository.getGenerationStatistics();

        Map<String, Map<String, Object>> byStatus = new HashMap<>();

        for (Object[] row : stats) {
            FileGenerationHistory.DeliveryStatus status =
                    (FileGenerationHistory.DeliveryStatus) row[0];
            Long count = (Long) row[1];
            Long totalClaims = row[2] != null ? (Long) row[2] : 0L;
            BigDecimal totalAmount = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

            byStatus.put(status.name(), Map.of(
                    "count", count,
                    "totalClaims", totalClaims,
                    "totalAmount", totalAmount
            ));
        }

        long totalFiles = fileHistoryRepository.count();
        long delivered = byStatus.getOrDefault("DELIVERED",
                Map.of("count", 0L)).get("count") instanceof Long
                ? (Long) byStatus.getOrDefault("DELIVERED", Map.of("count", 0L)).get("count")
                : 0L;

        Map<String, Object> response = Map.of(
                "byStatus", byStatus,
                "totalFiles", totalFiles,
                "deliveryRate", totalFiles > 0
                        ? String.format("%.2f%%", (double) delivered / totalFiles * 100)
                        : "0%"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/files/recent-activity")
    public ResponseEntity<List<FileGenerationHistory>> getRecentFileActivity(
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("GET /api/v1/dashboard/files/recent-activity?limit={}", limit);

        List<FileGenerationHistory> recentFiles = fileHistoryRepository.findRecentFiles(limit);
        return ResponseEntity.ok(recentFiles);
    }

    // ==================== Claim Metrics ====================

    @GetMapping("/claims/metrics")
    public ResponseEntity<Map<String, Object>> getClaimMetrics() {
        log.debug("GET /api/v1/dashboard/claims/metrics");

        long totalClaims = claimLogRepository.count();
        long processedClaims = claimLogRepository.countProcessedClaims();
        long rejectedClaims = claimLogRepository.countRejectedClaims();

        double processingRate = totalClaims > 0
                ? (double) processedClaims / totalClaims * 100
                : 0;

        double rejectionRate = totalClaims > 0
                ? (double) rejectedClaims / totalClaims * 100
                : 0;

        Map<String, Object> metrics = Map.of(
                "totalClaims", totalClaims,
                "processed", processedClaims,
                "rejected", rejectedClaims,
                "processingRate", String.format("%.2f%%", processingRate),
                "rejectionRate", String.format("%.2f%%", rejectionRate)
        );

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/claims/rejection-reasons")
    public ResponseEntity<List<Map<String, Object>>> getRejectionReasons() {
        log.debug("GET /api/v1/dashboard/claims/rejection-reasons");

        List<Object[]> reasons = claimLogRepository.getTopRejectionReasons();

        List<Map<String, Object>> rejectionReasons = reasons.stream()
                .map(row -> Map.of(
                        "reason", row[0] != null ? row[0] : "Unknown",
                        "count", row[1]
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(rejectionReasons);
    }

    // ==================== Approval Metrics ====================

    @GetMapping("/approvals/metrics")
    public ResponseEntity<Map<String, Object>> getApprovalMetrics() {
        log.debug("GET /api/v1/dashboard/approvals/metrics");

        List<Object[]> stats = approvalLogRepository.getApprovalStatistics();

        long approvals = stats.stream()
                .filter(row -> row[0] == com.healthcare.edi835.entity.BucketApprovalLog.ApprovalAction.APPROVE)
                .mapToLong(row -> (Long) row[1])
                .sum();

        long rejections = stats.stream()
                .filter(row -> row[0] == com.healthcare.edi835.entity.BucketApprovalLog.ApprovalAction.REJECT)
                .mapToLong(row -> (Long) row[1])
                .sum();

        double approvalRate = (approvals + rejections) > 0
                ? (double) approvals / (approvals + rejections) * 100
                : 0;

        Map<String, Object> metrics = Map.of(
                "totalApprovals", approvals,
                "totalRejections", rejections,
                "approvalRate", String.format("%.2f%%", approvalRate),
                "pendingApprovals", bucketRepository.findPendingApprovals().size()
        );

        return ResponseEntity.ok(metrics);
    }

    // ==================== System Health ====================

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        log.debug("GET /api/v1/dashboard/health");

        // Calculate health indicators
        long staleBuckets = bucketRepository.findStaleBuckets(7).size();
        long failedDeliveries = fileHistoryRepository.findFailedDeliveries().size();
        long pendingApprovals = bucketRepository.findPendingApprovals().size();

        String healthStatus;
        if (staleBuckets > 10 || failedDeliveries > 20) {
            healthStatus = "CRITICAL";
        } else if (staleBuckets > 5 || failedDeliveries > 10 || pendingApprovals > 50) {
            healthStatus = "WARNING";
        } else {
            healthStatus = "HEALTHY";
        }

        Map<String, Object> health = Map.of(
                "status", healthStatus,
                "staleBuckets", staleBuckets,
                "failedDeliveries", failedDeliveries,
                "pendingApprovals", pendingApprovals,
                "timestamp", java.time.LocalDateTime.now()
        );

        return ResponseEntity.ok(health);
    }

    // ==================== Trends and Analytics ====================

    @GetMapping("/trends/daily")
    public ResponseEntity<Map<String, Object>> getDailyTrends() {
        log.debug("GET /api/v1/dashboard/trends/daily");

        // Get buckets created today
        java.time.LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
        List<EdiFileBucket> todayBuckets = bucketRepository.findAll().stream()
                .filter(b -> b.getCreatedAt().isAfter(startOfDay))
                .collect(Collectors.toList());

        // Get files generated today
        List<FileGenerationHistory> todayFiles = fileHistoryRepository.findAll().stream()
                .filter(f -> f.getGeneratedAt() != null && f.getGeneratedAt().isAfter(startOfDay))
                .collect(Collectors.toList());

        Map<String, Object> trends = Map.of(
                "bucketsCreatedToday", todayBuckets.size(),
                "filesGeneratedToday", todayFiles.size(),
                "claimsProcessedToday", todayBuckets.stream()
                        .mapToInt(EdiFileBucket::getClaimCount)
                        .sum(),
                "totalAmountToday", todayBuckets.stream()
                        .map(EdiFileBucket::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        return ResponseEntity.ok(trends);
    }

    // ==================== Alerts and Notifications ====================

    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getAlerts() {
        log.debug("GET /api/v1/dashboard/alerts");

        List<Map<String, Object>> alerts = new java.util.ArrayList<>();

        // Check for stale buckets
        long staleBuckets = bucketRepository.findStaleBuckets(7).size();
        if (staleBuckets > 0) {
            alerts.add(Map.of(
                    "severity", "WARNING",
                    "message", staleBuckets + " bucket(s) have been accumulating for over 7 days",
                    "type", "STALE_BUCKETS",
                    "count", staleBuckets
            ));
        }

        // Check for failed deliveries
        long failedDeliveries = fileHistoryRepository.findFailedDeliveries().size();
        if (failedDeliveries > 0) {
            alerts.add(Map.of(
                    "severity", "ERROR",
                    "message", failedDeliveries + " file(s) failed to deliver",
                    "type", "FAILED_DELIVERY",
                    "count", failedDeliveries
            ));
        }

        // Check for pending approvals
        long pendingApprovals = bucketRepository.findPendingApprovals().size();
        if (pendingApprovals > 20) {
            alerts.add(Map.of(
                    "severity", "INFO",
                    "message", pendingApprovals + " bucket(s) awaiting approval",
                    "type", "PENDING_APPROVALS",
                    "count", pendingApprovals
            ));
        }

        return ResponseEntity.ok(alerts);
    }
}
