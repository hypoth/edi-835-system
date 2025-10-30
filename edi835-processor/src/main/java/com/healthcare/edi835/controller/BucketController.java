package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.model.dto.BucketSummaryDTO;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import com.healthcare.edi835.service.BucketManagerService;
import com.healthcare.edi835.service.ThresholdMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for bucket operations.
 * Provides endpoints for viewing, searching, and managing EDI file buckets.
 *
 * <p>Base Path: /api/v1/buckets</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/buckets")
// CORS configured globally in WebConfig.java - do not override with wildcard
public class BucketController {

    private final EdiFileBucketRepository bucketRepository;
    private final BucketManagerService bucketManagerService;
    private final ThresholdMonitorService thresholdMonitorService;

    public BucketController(
            EdiFileBucketRepository bucketRepository,
            BucketManagerService bucketManagerService,
            ThresholdMonitorService thresholdMonitorService) {
        this.bucketRepository = bucketRepository;
        this.bucketManagerService = bucketManagerService;
        this.thresholdMonitorService = thresholdMonitorService;
    }

    // ==================== Bucket Retrieval ====================

    @GetMapping
    public ResponseEntity<List<EdiFileBucket>> getAllBuckets(
            @RequestParam(required = false) EdiFileBucket.BucketStatus status) {
        log.debug("GET /api/v1/buckets - Retrieving buckets (status: {})", status);

        List<EdiFileBucket> buckets;
        if (status != null) {
            buckets = bucketRepository.findByStatus(status);
        } else {
            buckets = bucketRepository.findAll();
        }

        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/active")
    public ResponseEntity<List<EdiFileBucket>> getActiveBuckets() {
        log.debug("GET /api/v1/buckets/active - Retrieving active buckets");
        List<EdiFileBucket> buckets = bucketManagerService.getActiveBuckets();
        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/{bucketId}")
    public ResponseEntity<EdiFileBucket> getBucketById(@PathVariable UUID bucketId) {
        log.debug("GET /api/v1/buckets/{} - Retrieving bucket", bucketId);

        return bucketRepository.findById(bucketId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/payer/{payerId}")
    public ResponseEntity<List<EdiFileBucket>> getBucketsByPayer(@PathVariable String payerId) {
        log.debug("GET /api/v1/buckets/payer/{} - Retrieving buckets", payerId);
        List<EdiFileBucket> buckets = bucketRepository.findByPayerId(payerId);
        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/payee/{payeeId}")
    public ResponseEntity<List<EdiFileBucket>> getBucketsByPayee(@PathVariable String payeeId) {
        log.debug("GET /api/v1/buckets/payee/{} - Retrieving buckets", payeeId);
        List<EdiFileBucket> buckets = bucketRepository.findByPayeeId(payeeId);
        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/payer/{payerId}/payee/{payeeId}")
    public ResponseEntity<List<EdiFileBucket>> getBucketsByPayerAndPayee(
            @PathVariable String payerId,
            @PathVariable String payeeId) {
        log.debug("GET /api/v1/buckets/payer/{}/payee/{} - Retrieving buckets", payerId, payeeId);
        List<EdiFileBucket> buckets = bucketRepository.findByPayerIdAndPayeeId(payerId, payeeId);
        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/bin/{binNumber}")
    public ResponseEntity<List<EdiFileBucket>> getBucketsByBin(
            @PathVariable String binNumber,
            @RequestParam(required = false) String pcnNumber) {
        log.debug("GET /api/v1/buckets/bin/{} - Retrieving buckets (PCN: {})", binNumber, pcnNumber);

        List<EdiFileBucket> buckets = bucketRepository.findByBinNumber(binNumber);

        if (pcnNumber != null) {
            buckets = buckets.stream()
                    .filter(b -> pcnNumber.equals(b.getPcnNumber()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(buckets);
    }

    // ==================== Bucket Statistics ====================

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getBucketStatistics() {
        log.debug("GET /api/v1/buckets/statistics - Retrieving bucket statistics");

        List<Object[]> stats = bucketRepository.getBucketStatistics();

        Map<String, Object> response = stats.stream()
                .collect(Collectors.toMap(
                        row -> ((EdiFileBucket.BucketStatus) row[0]).name(),
                        row -> Map.of(
                                "count", row[1],
                                "totalClaims", row[2] != null ? row[2] : 0,
                                "totalAmount", row[3] != null ? row[3] : 0
                        )
                ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    public ResponseEntity<List<BucketSummaryDTO>> getBucketSummary() {
        log.debug("GET /api/v1/buckets/summary - Retrieving bucket summary");

        List<EdiFileBucket> buckets = bucketRepository.findAll();

        List<BucketSummaryDTO> summary = buckets.stream()
                .map(bucket -> BucketSummaryDTO.builder()
                        .bucketId(bucket.getBucketId() != null ? bucket.getBucketId().toString() : null)
                        .payerId(bucket.getPayerId())
                        .payerName(bucket.getPayerName())
                        .payeeId(bucket.getPayeeId())
                        .payeeName(bucket.getPayeeName())
                        .status(bucket.getStatus() != null ? bucket.getStatus().name() : null)
                        .claimCount(bucket.getClaimCount())
                        .totalAmount(bucket.getTotalAmount())
                        .createdAt(bucket.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getBucketCounts() {
        log.debug("GET /api/v1/buckets/count - Retrieving bucket counts");

        Map<String, Long> counts = Map.of(
                "total", bucketRepository.count(),
                "active", (long) bucketManagerService.getActiveBuckets().size(),
                "pendingApproval", (long) bucketManagerService.getPendingApprovals().size()
        );

        return ResponseEntity.ok(counts);
    }

    // ==================== Bucket Operations ====================

    @PostMapping("/{bucketId}/transition-to-generation")
    public ResponseEntity<Void> transitionToGeneration(@PathVariable UUID bucketId) {
        log.info("POST /api/v1/buckets/{}/transition-to-generation", bucketId);

        return bucketRepository.findById(bucketId)
                .map(bucket -> {
                    bucketManagerService.transitionToGeneration(bucket);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{bucketId}/transition-to-approval")
    public ResponseEntity<Void> transitionToPendingApproval(@PathVariable UUID bucketId) {
        log.info("POST /api/v1/buckets/{}/transition-to-approval", bucketId);

        return bucketRepository.findById(bucketId)
                .map(bucket -> {
                    bucketManagerService.transitionToPendingApproval(bucket);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{bucketId}/mark-completed")
    public ResponseEntity<Void> markCompleted(@PathVariable UUID bucketId) {
        log.info("POST /api/v1/buckets/{}/mark-completed", bucketId);

        return bucketRepository.findById(bucketId)
                .map(bucket -> {
                    bucketManagerService.markCompleted(bucket);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{bucketId}/mark-failed")
    public ResponseEntity<Void> markFailed(@PathVariable UUID bucketId) {
        log.info("POST /api/v1/buckets/{}/mark-failed", bucketId);

        return bucketRepository.findById(bucketId)
                .map(bucket -> {
                    bucketManagerService.markFailed(bucket);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{bucketId}/evaluate-thresholds")
    public ResponseEntity<Void> evaluateThresholds(@PathVariable UUID bucketId) {
        log.info("POST /api/v1/buckets/{}/evaluate-thresholds - Manual evaluation", bucketId);

        return bucketRepository.findById(bucketId)
                .map(bucket -> {
                    bucketManagerService.evaluateBucketThresholds(bucket);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Monitoring ====================

    @PostMapping("/evaluate-all-thresholds")
    public ResponseEntity<Map<String, String>> evaluateAllThresholds() {
        log.info("POST /api/v1/buckets/evaluate-all-thresholds - Manual trigger");

        thresholdMonitorService.evaluateAllBucketThresholds();

        Map<String, String> response = Map.of(
                "message", "Threshold evaluation triggered successfully",
                "statistics", thresholdMonitorService.getMonitoringStatistics()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/monitoring/statistics")
    public ResponseEntity<String> getMonitoringStatistics() {
        log.debug("GET /api/v1/buckets/monitoring/statistics");
        String stats = thresholdMonitorService.getMonitoringStatistics();
        return ResponseEntity.ok(stats);
    }

    // ==================== Search and Filter ====================

    @GetMapping("/search")
    public ResponseEntity<List<EdiFileBucket>> searchBuckets(
            @RequestParam(required = false) String payerId,
            @RequestParam(required = false) String payeeId,
            @RequestParam(required = false) EdiFileBucket.BucketStatus status,
            @RequestParam(required = false) String binNumber) {
        log.debug("GET /api/v1/buckets/search - Searching buckets");

        List<EdiFileBucket> buckets = bucketRepository.findAll();

        if (payerId != null) {
            buckets = buckets.stream()
                    .filter(b -> payerId.equalsIgnoreCase(b.getPayerId()))
                    .collect(Collectors.toList());
        }

        if (payeeId != null) {
            buckets = buckets.stream()
                    .filter(b -> payeeId.equalsIgnoreCase(b.getPayeeId()))
                    .collect(Collectors.toList());
        }

        if (status != null) {
            buckets = buckets.stream()
                    .filter(b -> status == b.getStatus())
                    .collect(Collectors.toList());
        }

        if (binNumber != null) {
            buckets = buckets.stream()
                    .filter(b -> binNumber.equals(b.getBinNumber()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<EdiFileBucket>> getRecentBuckets(
            @RequestParam(defaultValue = "10") int limit) {
        log.debug("GET /api/v1/buckets/recent?limit={}", limit);

        List<EdiFileBucket> buckets = bucketRepository.findRecentBuckets(limit);
        return ResponseEntity.ok(buckets);
    }

    // ==================== Bucket Details ====================

    @GetMapping("/{bucketId}/details")
    public ResponseEntity<Map<String, Object>> getBucketDetails(@PathVariable UUID bucketId) {
        log.debug("GET /api/v1/buckets/{}/details", bucketId);

        return bucketRepository.findById(bucketId)
                .map(bucket -> {
                    Map<String, Object> details = Map.of(
                            "bucket", bucket,
                            "ageDays", bucket.getAgeDays(),
                            "awaitingApprovalDuration",
                            bucket.getAwaitingApprovalSince() != null
                                    ? java.time.Duration.between(
                                    bucket.getAwaitingApprovalSince(),
                                    java.time.LocalDateTime.now()).toHours() + " hours"
                                    : "N/A",
                            "generationDuration",
                            bucket.getGenerationStartedAt() != null && bucket.getGenerationCompletedAt() != null
                                    ? java.time.Duration.between(
                                    bucket.getGenerationStartedAt(),
                                    bucket.getGenerationCompletedAt()).toSeconds() + " seconds"
                                    : "N/A"
                    );
                    return ResponseEntity.ok(details);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
