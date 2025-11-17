package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.Payer;
import com.healthcare.edi835.entity.Payee;
import com.healthcare.edi835.model.dto.BucketConfigurationCheckDTO;
import com.healthcare.edi835.model.dto.BucketSummaryDTO;
import com.healthcare.edi835.model.dto.CreatePayerFromBucketDTO;
import com.healthcare.edi835.model.dto.CreatePayeeFromBucketDTO;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import com.healthcare.edi835.repository.PayerRepository;
import com.healthcare.edi835.repository.PayeeRepository;
import com.healthcare.edi835.service.BucketManagerService;
import com.healthcare.edi835.service.EncryptionService;
import com.healthcare.edi835.service.ThresholdMonitorService;
import com.healthcare.edi835.util.EdiIdentifierNormalizer;
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
    private final PayerRepository payerRepository;
    private final PayeeRepository payeeRepository;
    private final EncryptionService encryptionService;

    public BucketController(
            EdiFileBucketRepository bucketRepository,
            BucketManagerService bucketManagerService,
            ThresholdMonitorService thresholdMonitorService,
            PayerRepository payerRepository,
            PayeeRepository payeeRepository,
            EncryptionService encryptionService) {
        this.bucketRepository = bucketRepository;
        this.bucketManagerService = bucketManagerService;
        this.thresholdMonitorService = thresholdMonitorService;
        this.payerRepository = payerRepository;
        this.payeeRepository = payeeRepository;
        this.encryptionService = encryptionService;
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

    // ==================== Configuration Management ====================

    /**
     * Check bucket configuration status (missing payer/payee).
     *
     * GET /api/v1/buckets/{bucketId}/configuration-check
     */
    @GetMapping("/{bucketId}/configuration-check")
    public ResponseEntity<BucketConfigurationCheckDTO> checkBucketConfiguration(@PathVariable UUID bucketId) {
        log.debug("GET /api/v1/buckets/{}/configuration-check", bucketId);

        return bucketRepository.findById(bucketId)
                .map(bucket -> {
                    boolean payerExists = payerRepository.findByPayerId(bucket.getPayerId()).isPresent();
                    boolean payeeExists = payeeRepository.findByPayeeId(bucket.getPayeeId()).isPresent();
                    boolean hasAllConfiguration = payerExists && payeeExists;

                    String actionRequired;
                    if (!payerExists && !payeeExists) {
                        actionRequired = "CREATE_BOTH";
                    } else if (!payerExists) {
                        actionRequired = "CREATE_PAYER";
                    } else if (!payeeExists) {
                        actionRequired = "CREATE_PAYEE";
                    } else {
                        actionRequired = "NONE";
                    }

                    // Normalize IDs/names to ensure they conform to EDI validation rules
                    String normalizedPayerId = !payerExists ?
                            EdiIdentifierNormalizer.normalizePayerId(bucket.getPayerId()) : null;
                    String normalizedPayeeId = !payeeExists ?
                            EdiIdentifierNormalizer.normalizePayeeId(bucket.getPayeeId()) : null;

                    BucketConfigurationCheckDTO check = BucketConfigurationCheckDTO.builder()
                            .bucketId(bucketId.toString())
                            .hasAllConfiguration(hasAllConfiguration)
                            .payerExists(payerExists)
                            .payeeExists(payeeExists)
                            .missingPayerId(normalizedPayerId)
                            .missingPayerName(!payerExists ? bucket.getPayerName() : null)
                            .missingPayeeId(normalizedPayeeId)
                            .missingPayeeName(!payeeExists ? bucket.getPayeeName() : null)
                            .actionRequired(actionRequired)
                            .build();

                    return ResponseEntity.ok(check);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create payer from bucket information.
     *
     * POST /api/v1/buckets/{bucketId}/create-payer
     */
    @PostMapping("/{bucketId}/create-payer")
    public ResponseEntity<?> createPayerFromBucket(
            @PathVariable UUID bucketId,
            @RequestBody CreatePayerFromBucketDTO request) {
        log.info("POST /api/v1/buckets/{}/create-payer", bucketId);

        try {
            // Validate bucket exists
            EdiFileBucket bucket = bucketRepository.findById(bucketId)
                    .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

            // IMPORTANT: Normalize payer ID to conform to EDI validation rules
            String originalPayerId = request.getPayerId();
            String normalizedPayerId = EdiIdentifierNormalizer.normalizePayerId(originalPayerId);

            log.info("Normalizing payer ID: '{}' -> '{}'", originalPayerId, normalizedPayerId);

            // Check if payer already exists (check both original and normalized)
            if (payerRepository.findByPayerId(normalizedPayerId).isPresent() ||
                payerRepository.findByPayerId(originalPayerId).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Payer already exists: " + normalizedPayerId));
            }

            // Auto-generate ISA Sender ID if not provided or invalid
            String isaSenderId = request.getIsaSenderId();
            if (isaSenderId == null || isaSenderId.isEmpty() ||
                !EdiIdentifierNormalizer.isValidIsaSenderId(isaSenderId)) {
                isaSenderId = EdiIdentifierNormalizer.generateIsaSenderId(normalizedPayerId);
                log.info("Auto-generated ISA Sender ID: '{}'", isaSenderId);
            } else {
                // Normalize provided ISA Sender ID
                String normalizedIsaSenderId = isaSenderId.toUpperCase().replaceAll("[^A-Z0-9]", "");
                if (normalizedIsaSenderId.length() > 15) {
                    normalizedIsaSenderId = normalizedIsaSenderId.substring(0, 15);
                }
                isaSenderId = normalizedIsaSenderId;
                log.info("Normalized provided ISA Sender ID: '{}' -> '{}'", request.getIsaSenderId(), isaSenderId);
            }

            // Auto-generate GS Application Sender ID if not provided or invalid
            String gsApplicationSenderId = request.getGsApplicationSenderId();
            if (gsApplicationSenderId == null || gsApplicationSenderId.isEmpty() ||
                !EdiIdentifierNormalizer.isValidIsaSenderId(gsApplicationSenderId)) {
                gsApplicationSenderId = EdiIdentifierNormalizer.generateGsApplicationSenderId(normalizedPayerId);
                log.info("Auto-generated GS Application Sender ID: '{}'", gsApplicationSenderId);
            } else {
                // Normalize provided GS Application Sender ID
                String normalizedGsSenderId = gsApplicationSenderId.toUpperCase().replaceAll("[^A-Z0-9]", "");
                if (normalizedGsSenderId.length() > 15) {
                    normalizedGsSenderId = normalizedGsSenderId.substring(0, 15);
                }
                gsApplicationSenderId = normalizedGsSenderId;
            }

            // Set default ISA Qualifier if not provided
            String isaQualifier = request.getIsaQualifier();
            if (isaQualifier == null || isaQualifier.isEmpty()) {
                isaQualifier = "ZZ";
            }

            // Encrypt SFTP password if provided
            String encryptedPassword = null;
            if (request.getSftpPassword() != null && !request.getSftpPassword().isEmpty()) {
                encryptedPassword = encryptionService.encrypt(request.getSftpPassword());
                log.debug("SFTP password encrypted for payer: {}", normalizedPayerId);
            }

            // Create payer entity with normalized values
            Payer payer = new Payer();
            payer.setPayerId(normalizedPayerId);  // Use normalized ID
            payer.setPayerName(request.getPayerName());
            payer.setIsaQualifier(isaQualifier);
            payer.setIsaSenderId(isaSenderId);  // Use generated/normalized ISA Sender ID
            payer.setGsApplicationSenderId(gsApplicationSenderId);  // Use generated/normalized GS ID
            payer.setAddressStreet(request.getAddressStreet());
            payer.setAddressCity(request.getAddressCity());
            payer.setAddressState(request.getAddressState());
            payer.setAddressZip(request.getAddressZip());
            payer.setSftpHost(request.getSftpHost());
            payer.setSftpPort(request.getSftpPort());
            payer.setSftpUsername(request.getSftpUsername());
            payer.setSftpPassword(encryptedPassword);  // Use encrypted password
            payer.setSftpPath(request.getSftpPath());
            payer.setRequiresSpecialHandling(request.getRequiresSpecialHandling());
            payer.setIsActive(request.getIsActive());
            payer.setCreatedBy(request.getCreatedBy() != null ? request.getCreatedBy() : "SYSTEM_AUTO");

            Payer savedPayer = payerRepository.save(payer);

            log.info("Payer created successfully for bucket {}: ID='{}', ISA Sender ID='{}'",
                    bucketId, savedPayer.getPayerId(), savedPayer.getIsaSenderId());

            // Update bucket's payer ID to normalized version
            if (!normalizedPayerId.equals(bucket.getPayerId())) {
                bucket.setPayerId(normalizedPayerId);
                bucketRepository.save(bucket);
                log.info("Updated bucket {} payer ID to normalized version: '{}'", bucketId, normalizedPayerId);
            }

            // If bucket is in MISSING_CONFIGURATION status, check if we can now generate
            if (bucket.getStatus() == EdiFileBucket.BucketStatus.MISSING_CONFIGURATION) {
                boolean payeeExists = payeeRepository.findByPayeeId(bucket.getPayeeId()).isPresent();
                if (payeeExists) {
                    // Both payer and payee now exist, transition back to PENDING_APPROVAL
                    bucket.markPendingApproval();
                    bucketRepository.save(bucket);
                    log.info("Bucket {} transitioned back to PENDING_APPROVAL", bucketId);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Payer created successfully",
                    "payer", savedPayer,
                    "normalizations", Map.of(
                            "originalPayerId", originalPayerId,
                            "normalizedPayerId", normalizedPayerId,
                            "isaSenderId", isaSenderId,
                            "gsApplicationSenderId", gsApplicationSenderId
                    )
            ));

        } catch (Exception e) {
            log.error("Failed to create payer from bucket {}", bucketId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to create payer: " + e.getMessage()));
        }
    }

    /**
     * Create payee from bucket information.
     *
     * POST /api/v1/buckets/{bucketId}/create-payee
     */
    @PostMapping("/{bucketId}/create-payee")
    public ResponseEntity<?> createPayeeFromBucket(
            @PathVariable UUID bucketId,
            @RequestBody CreatePayeeFromBucketDTO request) {
        log.info("POST /api/v1/buckets/{}/create-payee", bucketId);

        try {
            // Validate bucket exists
            EdiFileBucket bucket = bucketRepository.findById(bucketId)
                    .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

            // IMPORTANT: Normalize payee ID to conform to EDI validation rules
            String originalPayeeId = request.getPayeeId();
            String normalizedPayeeId = EdiIdentifierNormalizer.normalizePayeeId(originalPayeeId);

            log.info("Normalizing payee ID: '{}' -> '{}'", originalPayeeId, normalizedPayeeId);

            // Check if payee already exists (check both original and normalized)
            if (payeeRepository.findByPayeeId(normalizedPayeeId).isPresent() ||
                payeeRepository.findByPayeeId(originalPayeeId).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Payee already exists: " + normalizedPayeeId));
            }

            // Create payee entity with normalized values
            Payee payee = new Payee();
            payee.setPayeeId(normalizedPayeeId);  // Use normalized ID
            payee.setPayeeName(request.getPayeeName());
            payee.setNpi(request.getNpi());
            payee.setTaxId(request.getTaxId());
            payee.setAddressStreet(request.getAddressStreet());
            payee.setAddressCity(request.getAddressCity());
            payee.setAddressState(request.getAddressState());
            payee.setAddressZip(request.getAddressZip());
            payee.setRequiresSpecialHandling(request.getRequiresSpecialHandling());
            payee.setIsActive(request.getIsActive());
            payee.setCreatedBy(request.getCreatedBy() != null ? request.getCreatedBy() : "SYSTEM_AUTO");

            Payee savedPayee = payeeRepository.save(payee);

            log.info("Payee created successfully for bucket {}: ID='{}'", bucketId, savedPayee.getPayeeId());

            // Update bucket's payee ID to normalized version
            if (!normalizedPayeeId.equals(bucket.getPayeeId())) {
                bucket.setPayeeId(normalizedPayeeId);
                bucketRepository.save(bucket);
                log.info("Updated bucket {} payee ID to normalized version: '{}'", bucketId, normalizedPayeeId);
            }

            // If bucket is in MISSING_CONFIGURATION status, check if we can now generate
            if (bucket.getStatus() == EdiFileBucket.BucketStatus.MISSING_CONFIGURATION) {
                // Check with normalized payer ID
                String normalizedPayerId = EdiIdentifierNormalizer.normalizePayerId(bucket.getPayerId());
                boolean payerExists = payerRepository.findByPayerId(normalizedPayerId).isPresent() ||
                                     payerRepository.findByPayerId(bucket.getPayerId()).isPresent();
                if (payerExists) {
                    // Both payer and payee now exist, transition back to PENDING_APPROVAL
                    bucket.markPendingApproval();
                    bucketRepository.save(bucket);
                    log.info("Bucket {} transitioned back to PENDING_APPROVAL", bucketId);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Payee created successfully",
                    "payee", savedPayee,
                    "normalizations", Map.of(
                            "originalPayeeId", originalPayeeId,
                            "normalizedPayeeId", normalizedPayeeId
                    )
            ));

        } catch (Exception e) {
            log.error("Failed to create payee from bucket {}", bucketId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to create payee: " + e.getMessage()));
        }
    }
}
