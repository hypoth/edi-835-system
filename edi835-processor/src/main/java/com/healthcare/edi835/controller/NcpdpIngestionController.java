package com.healthcare.edi835.controller;

import com.healthcare.edi835.dto.IngestRequest;
import com.healthcare.edi835.dto.IngestionResult;
import com.healthcare.edi835.dto.NcpdpStatusResponse;
import com.healthcare.edi835.entity.NcpdpRawClaim;
import com.healthcare.edi835.entity.NcpdpRawClaim.NcpdpStatus;
import com.healthcare.edi835.repository.NcpdpRawClaimRepository;
import com.healthcare.edi835.service.NcpdpIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for NCPDP D.0 claim ingestion.
 * Provides endpoints for ingesting pharmacy claims from files and monitoring processing status.
 *
 * <p>Base Path: /api/v1/ncpdp</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>File-based ingestion of NCPDP D.0 claims</li>
 *   <li>Real-time processing status monitoring</li>
 *   <li>Pending and failed claim retrieval</li>
 *   <li>Integration with change feed processor</li>
 * </ul>
 *
 * <p>Note: CORS is configured globally in WebConfig.java to allow specific origins
 * (localhost:3000, localhost:5173 for dev). Do not use @CrossOrigin(origins = "*")
 * as it conflicts with allowCredentials=true.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ncpdp")
public class NcpdpIngestionController {

    private final NcpdpIngestionService ingestionService;
    private final NcpdpRawClaimRepository repository;

    public NcpdpIngestionController(
            NcpdpIngestionService ingestionService,
            NcpdpRawClaimRepository repository) {
        this.ingestionService = ingestionService;
        this.repository = repository;
    }

    // ==================== Ingestion Endpoints ====================

    /**
     * Ingests NCPDP claims from a file.
     *
     * <p>Reads NCPDP D.0 transactions from the specified file and inserts them
     * into the database with PENDING status. The change feed processor will
     * automatically detect and process these claims.</p>
     *
     * <p><strong>Request Body:</strong></p>
     * <pre>
     * {
     *   "filePath": "d0-samples/ncpdp_rx_claims.txt",
     *   "stopOnError": false
     * }
     * </pre>
     *
     * @param request ingestion request with file path and options
     * @return ingestion result with success/failure counts
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> ingestFromFile(@RequestBody IngestRequest request) {
        log.info("POST /api/v1/ncpdp/ingest - Ingesting from file: {}", request.getFilePath());

        if (request.getFilePath() == null || request.getFilePath().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(IngestionResult.failure("File path is required"));
        }

        IngestionResult result = ingestionService.ingestFromFile(
            request.getFilePath(),
            request.isStopOnError()
        );

        HttpStatus status = switch (result.getStatus()) {
            case "SUCCESS" -> HttpStatus.OK;
            case "PARTIAL" -> HttpStatus.MULTI_STATUS;
            case "FAILED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        log.info("Ingestion completed: status={}, success={}, failed={}",
            result.getStatus(), result.getTotalSuccess(), result.getTotalFailed());

        return ResponseEntity.status(status).body(result);
    }

    /**
     * Ingests NCPDP claims from the default file path.
     *
     * <p>Uses the default file path configured in application.yml:
     * {@code ncpdp.ingestion.default-file-path}</p>
     *
     * @return ingestion result
     */
    @PostMapping("/ingest/default")
    public ResponseEntity<IngestionResult> ingestFromDefaultFile() {
        log.info("POST /api/v1/ncpdp/ingest/default - Ingesting from default file");

        IngestionResult result = ingestionService.ingestFromDefaultFile();

        HttpStatus status = switch (result.getStatus()) {
            case "SUCCESS" -> HttpStatus.OK;
            case "PARTIAL" -> HttpStatus.MULTI_STATUS;
            case "FAILED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status).body(result);
    }

    // ==================== Status and Monitoring Endpoints ====================

    /**
     * Gets the current NCPDP processing status.
     *
     * <p>Returns counts by status (PENDING, PROCESSING, PROCESSED, FAILED)
     * along with total count and success rate.</p>
     *
     * <p><strong>Response Example:</strong></p>
     * <pre>
     * {
     *   "pending": 15,
     *   "processing": 3,
     *   "processed": 182,
     *   "failed": 8,
     *   "total": 208,
     *   "successRate": 87.5
     * }
     * </pre>
     *
     * @return status response with counts and metrics
     */
    @GetMapping("/status")
    public ResponseEntity<NcpdpStatusResponse> getStatus() {
        log.debug("GET /api/v1/ncpdp/status - Retrieving NCPDP processing status");

        NcpdpStatusResponse status = ingestionService.getStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Gets detailed metrics for NCPDP claim processing.
     *
     * <p>Includes status breakdown, average processing time, and throughput metrics.</p>
     *
     * @return detailed metrics map
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        log.debug("GET /api/v1/ncpdp/metrics - Retrieving NCPDP metrics");

        NcpdpStatusResponse status = ingestionService.getStatus();

        Map<String, Object> metrics = Map.of(
            "statusBreakdown", Map.of(
                "pending", status.getPending(),
                "processing", status.getProcessing(),
                "processed", status.getProcessed(),
                "failed", status.getFailed()
            ),
            "total", status.getTotal(),
            "successRate", String.format("%.2f%%", status.getSuccessRate()),
            "timestamp", java.time.LocalDateTime.now()
        );

        return ResponseEntity.ok(metrics);
    }

    // ==================== Claim Retrieval Endpoints ====================

    /**
     * Gets all pending claims (status = PENDING).
     *
     * <p>These claims are waiting to be processed by the change feed processor.</p>
     *
     * @param limit maximum number of results to return (default: 100)
     * @return list of pending claims
     */
    @GetMapping("/claims/pending")
    public ResponseEntity<List<NcpdpRawClaim>> getPendingClaims(
            @RequestParam(defaultValue = "100") int limit) {
        log.debug("GET /api/v1/ncpdp/claims/pending?limit={}", limit);

        List<NcpdpRawClaim> pending = repository
            .findByStatusOrderByCreatedDateAsc(NcpdpStatus.PENDING)
            .stream()
            .limit(limit)
            .toList();

        return ResponseEntity.ok(pending);
    }

    /**
     * Gets all failed claims (status = FAILED).
     *
     * <p>These claims failed during processing and may require manual review.</p>
     *
     * @param limit maximum number of results to return (default: 100)
     * @return list of failed claims
     */
    @GetMapping("/claims/failed")
    public ResponseEntity<List<NcpdpRawClaim>> getFailedClaims(
            @RequestParam(defaultValue = "100") int limit) {
        log.debug("GET /api/v1/ncpdp/claims/failed?limit={}", limit);

        List<NcpdpRawClaim> failed = repository
            .findByStatus(NcpdpStatus.FAILED)
            .stream()
            .limit(limit)
            .toList();

        return ResponseEntity.ok(failed);
    }

    /**
     * Gets all processing claims (status = PROCESSING).
     *
     * <p>These claims are currently being processed by the change feed.</p>
     *
     * @return list of processing claims
     */
    @GetMapping("/claims/processing")
    public ResponseEntity<List<NcpdpRawClaim>> getProcessingClaims() {
        log.debug("GET /api/v1/ncpdp/claims/processing");

        List<NcpdpRawClaim> processing = repository.findByStatus(NcpdpStatus.PROCESSING);
        return ResponseEntity.ok(processing);
    }

    /**
     * Gets claims eligible for retry (failed with retry count < max).
     *
     * <p>These claims can be retried by the change feed processor.</p>
     *
     * @param maxRetries maximum retry count threshold (default: 3)
     * @return list of retryable claims
     */
    @GetMapping("/claims/retryable")
    public ResponseEntity<List<NcpdpRawClaim>> getRetryableClaims(
            @RequestParam(defaultValue = "3") int maxRetries) {
        log.debug("GET /api/v1/ncpdp/claims/retryable?maxRetries={}", maxRetries);

        List<NcpdpRawClaim> retryable = repository.findFailedClaimsForRetry(maxRetries);
        return ResponseEntity.ok(retryable);
    }

    /**
     * Gets a specific claim by ID.
     *
     * @param claimId the claim ID
     * @return the claim, or 404 if not found
     */
    @GetMapping("/claims/{claimId}")
    public ResponseEntity<NcpdpRawClaim> getClaimById(@PathVariable String claimId) {
        log.debug("GET /api/v1/ncpdp/claims/{}", claimId);

        return repository.findById(claimId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Admin/Utility Endpoints ====================

    /**
     * Gets claims by payer ID.
     *
     * @param payerId the payer identifier
     * @return list of claims for the specified payer
     */
    @GetMapping("/claims/by-payer/{payerId}")
    public ResponseEntity<List<NcpdpRawClaim>> getClaimsByPayer(@PathVariable String payerId) {
        log.debug("GET /api/v1/ncpdp/claims/by-payer/{}", payerId);

        List<NcpdpRawClaim> claims = repository.findByPayerId(payerId);
        return ResponseEntity.ok(claims);
    }

    /**
     * Gets stuck claims (stuck in PROCESSING for too long).
     *
     * @param thresholdMinutes time threshold in minutes (default: 30)
     * @return list of stuck claims
     */
    @GetMapping("/claims/stuck")
    public ResponseEntity<List<NcpdpRawClaim>> getStuckClaims(
            @RequestParam(defaultValue = "30") int thresholdMinutes) {
        log.debug("GET /api/v1/ncpdp/claims/stuck?thresholdMinutes={}", thresholdMinutes);

        java.time.LocalDateTime threshold = java.time.LocalDateTime.now()
            .minusMinutes(thresholdMinutes);

        List<NcpdpRawClaim> stuck = repository.findStuckProcessingClaims(threshold);
        return ResponseEntity.ok(stuck);
    }

    /**
     * Deletes all claims with a specific status.
     *
     * <p><strong>WARNING:</strong> This is a destructive operation. Use with caution.</p>
     *
     * @param status the status to delete (PENDING, PROCESSING, PROCESSED, FAILED)
     * @return number of deleted claims
     */
    @DeleteMapping("/claims/by-status/{status}")
    public ResponseEntity<Map<String, Object>> deleteClaimsByStatus(@PathVariable String status) {
        log.warn("DELETE /api/v1/ncpdp/claims/by-status/{} - Deleting claims", status);

        try {
            NcpdpStatus ncpdpStatus = NcpdpStatus.valueOf(status.toUpperCase());
            List<NcpdpRawClaim> claims = repository.findByStatus(ncpdpStatus);
            int count = claims.size();

            repository.deleteAll(claims);

            log.info("Deleted {} claims with status {}", count, status);

            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "deletedCount", count,
                "message", "Deleted " + count + " claims with status " + status
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "FAILED",
                "message", "Invalid status: " + status + ". Valid values: PENDING, PROCESSING, PROCESSED, FAILED"
            ));
        }
    }
}
