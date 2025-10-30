package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.FileGenerationHistory;
import com.healthcare.edi835.model.dto.FileGenerationRequestDTO;
import com.healthcare.edi835.model.projection.FileGenerationSummary;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import com.healthcare.edi835.repository.FileGenerationHistoryRepository;
import com.healthcare.edi835.service.Edi835GeneratorService;
import com.healthcare.edi835.service.FileNamingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for EDI file generation operations.
 * Provides endpoints for triggering file generation and viewing generation history.
 *
 * <p>Base Path: /api/v1/files</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
// CORS configured globally in WebConfig.java - do not override with wildcard
public class FileGenerationController {

    private final Edi835GeneratorService generatorService;
    private final FileGenerationHistoryRepository historyRepository;
    private final EdiFileBucketRepository bucketRepository;
    private final FileNamingService fileNamingService;

    public FileGenerationController(
            Edi835GeneratorService generatorService,
            FileGenerationHistoryRepository historyRepository,
            EdiFileBucketRepository bucketRepository,
            FileNamingService fileNamingService) {
        this.generatorService = generatorService;
        this.historyRepository = historyRepository;
        this.bucketRepository = bucketRepository;
        this.fileNamingService = fileNamingService;
    }

    // ==================== Root Endpoint ====================

    /**
     * GET /api/v1/files - Retrieve all file generation history records.
     * Returns a list of recently generated EDI files with optional limit.
     * Uses projection to exclude file_content for SQLite compatibility.
     */
    @GetMapping
    public ResponseEntity<List<FileGenerationSummary>> getAllFiles(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        log.debug("GET /api/v1/files - Retrieving files (limit: {})", limit);

        List<FileGenerationSummary> files = historyRepository.findRecentSummaries(limit);
        return ResponseEntity.ok(files);
    }

    // ==================== File Generation ====================

    @PostMapping("/generate/{bucketId}")
    public ResponseEntity<Map<String, Object>> generateEdiFile(@PathVariable UUID bucketId) {
        log.info("POST /api/v1/files/generate/{} - Generating EDI file", bucketId);

        try {
            EdiFileBucket bucket = bucketRepository.findById(bucketId)
                    .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketId));

            FileGenerationHistory history = generatorService.generateEdi835File(bucket);

            Map<String, Object> response = Map.of(
                    "message", "EDI file generated successfully",
                    "fileId", history.getFileId(),
                    "fileName", history.getFileName(),
                    "fileSizeBytes", history.getFileSizeBytes(),
                    "claimCount", history.getClaimCount(),
                    "totalAmount", history.getTotalAmount()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Bucket not found: {}", bucketId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Bucket not found"));

        } catch (IllegalStateException e) {
            log.warn("Invalid bucket state for generation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error generating EDI file for bucket {}: {}", bucketId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File generation failed: " + e.getMessage()));
        }
    }

    @PostMapping("/generate/batch")
    public ResponseEntity<Map<String, Object>> generateBatch(
            @Valid @RequestBody FileGenerationRequestDTO request) {
        log.info("POST /api/v1/files/generate/batch - Batch generation for {} buckets",
                request.getBucketIds().size());

        int successCount = 0;
        int errorCount = 0;

        for (UUID bucketId : request.getBucketIds()) {
            try {
                bucketRepository.findById(bucketId)
                        .ifPresent(generatorService::generateEdi835File);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to generate file for bucket {}: {}", bucketId, e.getMessage());
            }
        }

        Map<String, Object> response = Map.of(
                "message", "Batch generation completed",
                "total", request.getBucketIds().size(),
                "success", successCount,
                "failed", errorCount
        );

        return ResponseEntity.ok(response);
    }

    // ==================== File History ====================

    @GetMapping("/history")
    public ResponseEntity<List<FileGenerationSummary>> getFileHistory(
            @RequestParam(required = false) FileGenerationHistory.DeliveryStatus status) {
        log.debug("GET /api/v1/files/history - Retrieving file history (status: {})", status);

        List<FileGenerationSummary> history;
        if (status != null) {
            history = historyRepository.findSummariesByDeliveryStatus(status);
        } else {
            history = historyRepository.findAllSummaries();
        }

        return ResponseEntity.ok(history);
    }

    @GetMapping("/history/{fileId}")
    public ResponseEntity<FileGenerationSummary> getFileById(@PathVariable UUID fileId) {
        log.debug("GET /api/v1/files/history/{} - Retrieving file", fileId);

        return historyRepository.findSummaryById(fileId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history/bucket/{bucketId}")
    public ResponseEntity<List<FileGenerationSummary>> getFilesByBucket(@PathVariable UUID bucketId) {
        log.debug("GET /api/v1/files/history/bucket/{} - Retrieving files", bucketId);

        return bucketRepository.findById(bucketId)
                .map(bucket -> {
                    List<FileGenerationSummary> files = historyRepository.findSummariesByBucket(bucket);
                    return ResponseEntity.ok(files);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history/payer/{payerId}")
    public ResponseEntity<List<FileGenerationSummary>> getFilesByPayer(@PathVariable String payerId) {
        log.debug("GET /api/v1/files/history/payer/{} - Retrieving files", payerId);
        List<FileGenerationSummary> files = historyRepository.findSummariesByPayerId(payerId);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/history/recent")
    public ResponseEntity<List<FileGenerationSummary>> getRecentFiles(
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("GET /api/v1/files/history/recent?limit={}", limit);
        List<FileGenerationSummary> files = historyRepository.findRecentSummaries(limit);
        return ResponseEntity.ok(files);
    }

    // ==================== File Download ====================

    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable UUID fileId) {
        log.info("GET /api/v1/files/download/{} - Downloading file by ID", fileId);

        // Use JdbcTemplate-based method to avoid SQLite JDBC LOB issues
        return historyRepository.findFileForDownloadById(fileId)
                .map(fileData -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType("application/edi-x12"));
                    headers.setContentDispositionFormData("attachment", fileData.getFileName());
                    headers.setContentLength(fileData.getFileSizeBytes());

                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(fileData.getFileContent());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFileByName(@RequestParam String fileName) {
        log.info("GET /api/v1/files/download?fileName={} - Downloading file by name", fileName);

        // Use JdbcTemplate-based method to avoid SQLite JDBC LOB issues
        return historyRepository.findFileForDownloadByFileName(fileName)
                .map(fileData -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType("application/edi-x12"));
                    headers.setContentDispositionFormData("attachment", fileData.getFileName());
                    headers.setContentLength(fileData.getFileSizeBytes());

                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(fileData.getFileContent());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/preview/{fileId}")
    public ResponseEntity<String> previewFile(@PathVariable UUID fileId) {
        log.debug("GET /api/v1/files/preview/{} - Previewing file", fileId);

        // Use JdbcTemplate-based method to avoid SQLite JDBC LOB issues
        return historyRepository.findFileForDownloadById(fileId)
                .map(fileData -> {
                    String content = new String(fileData.getFileContent());
                    // Limit preview to first 5000 characters
                    if (content.length() > 5000) {
                        content = content.substring(0, 5000) + "\n... (truncated)";
                    }
                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_PLAIN)
                            .body(content);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== File Statistics ====================

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getFileStatistics() {
        log.debug("GET /api/v1/files/statistics - Retrieving file statistics");

        List<Object[]> stats = historyRepository.getGenerationStatistics();

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
    public ResponseEntity<Map<String, Object>> getFileSummary() {
        log.debug("GET /api/v1/files/statistics/summary");

        long totalFiles = historyRepository.count();
        long pendingDelivery = historyRepository.findPendingDeliveries().size();
        long failedDeliveries = historyRepository.findFailedDeliveries().size();
        long delivered = historyRepository.findByDeliveryStatus(
                FileGenerationHistory.DeliveryStatus.DELIVERED).size();

        Map<String, Object> summary = Map.of(
                "totalFiles", totalFiles,
                "pending", pendingDelivery,
                "delivered", delivered,
                "failed", failedDeliveries,
                "deliveryRate", totalFiles > 0
                        ? String.format("%.2f%%", (double) delivered / totalFiles * 100)
                        : "0%"
        );

        return ResponseEntity.ok(summary);
    }

    // ==================== File Naming ====================

    @PostMapping("/preview-name/{bucketId}")
    public ResponseEntity<Map<String, String>> previewFileName(@PathVariable UUID bucketId) {
        log.debug("POST /api/v1/files/preview-name/{} - Previewing file name", bucketId);

        return bucketRepository.findById(bucketId)
                .map(bucket -> {
                    String fileName = fileNamingService.generateFileName(bucket);
                    return ResponseEntity.ok(Map.of("fileName", fileName));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/validate-template")
    public ResponseEntity<Map<String, Object>> validateTemplate(
            @RequestBody Map<String, String> request) {
        log.debug("POST /api/v1/files/validate-template");

        String templatePattern = request.get("templatePattern");

        if (templatePattern == null || templatePattern.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Template pattern is required"));
        }

        FileNamingService.TemplateValidationResult result =
                fileNamingService.validateTemplate(templatePattern);

        Map<String, Object> response = Map.of(
                "valid", result.isValid(),
                "errors", result.getErrors(),
                "warnings", result.getWarnings()
        );

        return ResponseEntity.ok(response);
    }

    // ==================== File Search ====================

    @GetMapping("/search")
    public ResponseEntity<List<FileGenerationSummary>> searchFiles(
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String payerId,
            @RequestParam(required = false) FileGenerationHistory.DeliveryStatus status) {
        log.debug("GET /api/v1/files/search - Searching files");

        List<FileGenerationSummary> files = historyRepository.findAllSummaries();

        if (fileName != null) {
            files = files.stream()
                    .filter(f -> f.getFileName().toLowerCase().contains(fileName.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
        }

        if (payerId != null) {
            files = files.stream()
                    .filter(f -> payerId.equalsIgnoreCase(f.getBucket().getPayerId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        if (status != null) {
            files = files.stream()
                    .filter(f -> status == f.getDeliveryStatus())
                    .collect(java.util.stream.Collectors.toList());
        }

        return ResponseEntity.ok(files);
    }

    // ==================== File Details ====================

    @GetMapping("/details/{fileId}")
    public ResponseEntity<Map<String, Object>> getFileDetails(@PathVariable UUID fileId) {
        log.debug("GET /api/v1/files/details/{}", fileId);

        return historyRepository.findSummaryById(fileId)
                .map(file -> {
                    Map<String, Object> details = Map.of(
                            "file", file,
                            "bucket", file.getBucket(),
                            "generationDuration",
                            file.getGeneratedAt() != null && file.getDeliveredAt() != null
                                    ? java.time.Duration.between(
                                    file.getGeneratedAt(),
                                    file.getDeliveredAt()).toMinutes() + " minutes"
                                    : "N/A",
                            "retryAttempts", file.getRetryCount(),
                            "errorMessage", file.getErrorMessage() != null ? file.getErrorMessage() : "None"
                    );
                    return ResponseEntity.ok(details);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== File Count ====================

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getFileCounts() {
        log.debug("GET /api/v1/files/count - Retrieving file counts");

        Map<String, Long> counts = Map.of(
                "total", historyRepository.count(),
                "pending", (long) historyRepository.findPendingDeliveries().size(),
                "delivered", (long) historyRepository.findByDeliveryStatus(
                        FileGenerationHistory.DeliveryStatus.DELIVERED).size(),
                "failed", (long) historyRepository.findFailedDeliveries().size()
        );

        return ResponseEntity.ok(counts);
    }
}
