package com.healthcare.edi835.changefeed;

import com.healthcare.edi835.entity.NcpdpRawClaim;
import com.healthcare.edi835.entity.NcpdpRawClaim.NcpdpStatus;
import com.healthcare.edi835.mapper.NcpdpToClaimMapper;
import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.model.ncpdp.NcpdpTransaction;
import com.healthcare.edi835.ncpdp.parser.NcpdpD0Parser;
import com.healthcare.edi835.ncpdp.parser.NcpdpParseException;
import com.healthcare.edi835.repository.NcpdpRawClaimRepository;
import com.healthcare.edi835.service.RemittanceProcessorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedicated change feed processor for NCPDP raw claims table.
 *
 * <p>This processor monitors the {@code ncpdp_raw_claims} table for claims with
 * status='PENDING' and automatically processes them through the EDI 835 pipeline.</p>
 *
 * <p><strong>Processing Flow:</strong></p>
 * <ol>
 *   <li>Poll database for PENDING claims (every N milliseconds)</li>
 *   <li>Mark claim as PROCESSING</li>
 *   <li>Parse raw NCPDP content using {@link NcpdpD0Parser}</li>
 *   <li>Map to standard Claim using {@link NcpdpToClaimMapper}</li>
 *   <li>Forward to {@link RemittanceProcessorService}</li>
 *   <li>Update status to PROCESSED or FAILED</li>
 * </ol>
 *
 * <p><strong>Configuration:</strong></p>
 * <pre>
 * changefeed:
 *   ncpdp:
 *     enabled: true
 *     poll-interval-ms: 5000
 *     batch-size: 50
 *     max-retries: 3
 * </pre>
 *
 * <p><strong>Monitoring:</strong></p>
 * <ul>
 *   <li>Total processed count</li>
 *   <li>Success/failure counts</li>
 *   <li>Last processing time</li>
 *   <li>Current processing status</li>
 * </ul>
 *
 * @see NcpdpRawClaim
 * @see NcpdpD0Parser
 * @see NcpdpToClaimMapper
 * @see RemittanceProcessorService
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "changefeed.ncpdp.enabled", havingValue = "true", matchIfMissing = false)
public class NcpdpChangeFeedProcessor {

    private final NcpdpRawClaimRepository ncpdpRepository;
    private final NcpdpD0Parser ncpdpParser;
    private final NcpdpToClaimMapper ncpdpMapper;
    private final RemittanceProcessorService remittanceProcessor;

    @Value("${changefeed.ncpdp.poll-interval-ms:5000}")
    private long pollIntervalMs;

    @Value("${changefeed.ncpdp.batch-size:50}")
    private int batchSize;

    @Value("${changefeed.ncpdp.max-retries:3}")
    private int maxRetries;

    @Value("${changefeed.ncpdp.stuck-threshold-minutes:30}")
    private int stuckThresholdMinutes;

    // Processing metrics
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private volatile LocalDateTime lastProcessingTime;

    public NcpdpChangeFeedProcessor(
            NcpdpRawClaimRepository ncpdpRepository,
            NcpdpD0Parser ncpdpParser,
            NcpdpToClaimMapper ncpdpMapper,
            RemittanceProcessorService remittanceProcessor) {
        this.ncpdpRepository = ncpdpRepository;
        this.ncpdpParser = ncpdpParser;
        this.ncpdpMapper = ncpdpMapper;
        this.remittanceProcessor = remittanceProcessor;
    }

    @PostConstruct
    public void init() {
        log.info("NCPDP Change Feed Processor initialized");
        log.info("Configuration: pollInterval={}ms, batchSize={}, maxRetries={}",
            pollIntervalMs, batchSize, maxRetries);
    }

    /**
     * Main processing loop - scheduled to run at fixed intervals.
     * Polls for PENDING claims and processes them in batches.
     */
    @Scheduled(fixedDelayString = "${changefeed.ncpdp.poll-interval-ms:5000}")
    public void processPendingClaims() {
        // Skip if already processing
        if (!isProcessing.compareAndSet(false, true)) {
            log.debug("Previous batch still processing, skipping this poll");
            return;
        }

        try {
            lastProcessingTime = LocalDateTime.now();

            // Fetch pending claims in FIFO order
            List<NcpdpRawClaim> pendingClaims = ncpdpRepository
                .findByStatusOrderByCreatedDateAsc(NcpdpStatus.PENDING)
                .stream()
                .limit(batchSize)
                .toList();

            if (pendingClaims.isEmpty()) {
                log.trace("No pending NCPDP claims to process");
                return;
            }

            log.info("Processing {} pending NCPDP claims", pendingClaims.size());

            int batchSuccessCount = 0;
            int batchFailureCount = 0;

            for (NcpdpRawClaim rawClaim : pendingClaims) {
                try {
                    processNcpdpClaim(rawClaim);
                    batchSuccessCount++;
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    batchFailureCount++;
                    failureCount.incrementAndGet();
                    log.error("Failed to process NCPDP claim: id={}", rawClaim.getId(), e);
                }
                totalProcessed.incrementAndGet();
            }

            log.info("Batch complete: success={}, failures={}, total processed={}",
                batchSuccessCount, batchFailureCount, totalProcessed.get());

        } catch (Exception e) {
            log.error("Error in NCPDP change feed processing loop", e);
        } finally {
            isProcessing.set(false);
        }
    }

    /**
     * Processes individual NCPDP claim through the complete pipeline.
     *
     * @param rawClaim the raw NCPDP claim to process
     */
    @Transactional
    protected void processNcpdpClaim(NcpdpRawClaim rawClaim) {
        log.debug("Processing NCPDP claim: id={}, pharmacy={}, payer={}",
            rawClaim.getId(), rawClaim.getPharmacyId(), rawClaim.getPayerId());

        try {
            // Step 1: Mark as processing
            rawClaim.markAsProcessing();
            ncpdpRepository.save(rawClaim);

            // Step 2: Parse NCPDP format
            NcpdpTransaction ncpdpTx = ncpdpParser.parse(rawClaim.getRawContent());
            log.debug("Parsed NCPDP transaction successfully");

            // Step 3: Map to standard Claim
            Claim claim = ncpdpMapper.mapToClaim(ncpdpTx);
            log.debug("Mapped to Claim: claimId={}, payerId={}, amount={}",
                claim.getId(), claim.getPayerId(), claim.getTotalChargeAmount());

            // Step 4: Process through remittance pipeline
            remittanceProcessor.processClaim(claim);
            log.debug("Forwarded to remittance processor");

            // Step 5: Mark as processed
            rawClaim.markAsProcessed(claim.getId());
            ncpdpRepository.save(rawClaim);

            log.info("Successfully processed NCPDP claim: id={}, claimId={}, payer={}",
                rawClaim.getId(), claim.getId(), claim.getPayerId());

        } catch (NcpdpParseException e) {
            handleParseError(rawClaim, e);
        } catch (IllegalArgumentException e) {
            handleValidationError(rawClaim, e);
        } catch (Exception e) {
            handleProcessingError(rawClaim, e);
        }
    }

    /**
     * Handles parsing errors
     */
    private void handleParseError(NcpdpRawClaim rawClaim, NcpdpParseException e) {
        String errorMsg = String.format("Parse error: %s (Segment: %s, Line: %d)",
            e.getMessage(), e.getSegmentId(), e.getLineNumber());

        log.error("NCPDP parsing failed for claim {}: {}", rawClaim.getId(), errorMsg);

        rawClaim.markAsFailed(errorMsg);
        ncpdpRepository.save(rawClaim);
    }

    /**
     * Handles validation errors during mapping
     */
    private void handleValidationError(NcpdpRawClaim rawClaim, IllegalArgumentException e) {
        String errorMsg = "Validation error: " + e.getMessage();
        log.error("NCPDP validation failed for claim {}: {}", rawClaim.getId(), errorMsg);

        rawClaim.markAsFailed(errorMsg);
        ncpdpRepository.save(rawClaim);
    }

    /**
     * Handles general processing errors
     */
    private void handleProcessingError(NcpdpRawClaim rawClaim, Exception e) {
        String errorMsg = "Processing error: " + e.getMessage();
        log.error("NCPDP processing failed for claim {}: {}", rawClaim.getId(), errorMsg, e);

        rawClaim.markAsFailed(errorMsg);
        ncpdpRepository.save(rawClaim);
    }

    /**
     * Scheduled task to retry failed claims (runs every 5 minutes)
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void retryFailedClaims() {
        try {
            List<NcpdpRawClaim> retriableClaims = ncpdpRepository
                .findFailedClaimsForRetry(maxRetries);

            if (retriableClaims.isEmpty()) {
                return;
            }

            log.info("Retrying {} failed NCPDP claims", retriableClaims.size());

            for (NcpdpRawClaim claim : retriableClaims) {
                try {
                    log.info("Retrying NCPDP claim: id={}, attempt={}",
                        claim.getId(), claim.getRetryCount() + 1);

                    // Reset to pending for reprocessing
                    claim.setStatus(NcpdpStatus.PENDING);
                    claim.setErrorMessage(null);
                    ncpdpRepository.save(claim);

                } catch (Exception e) {
                    log.error("Failed to reset claim for retry: id={}", claim.getId(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in retry failed claims task", e);
        }
    }

    /**
     * Scheduled task to detect and reset stuck claims (runs every 10 minutes)
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    public void detectStuckClaims() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(stuckThresholdMinutes);
            List<NcpdpRawClaim> stuckClaims = ncpdpRepository
                .findStuckProcessingClaims(cutoffTime);

            if (stuckClaims.isEmpty()) {
                return;
            }

            log.warn("Detected {} stuck NCPDP claims (PROCESSING > {} minutes)",
                stuckClaims.size(), stuckThresholdMinutes);

            for (NcpdpRawClaim claim : stuckClaims) {
                log.warn("Resetting stuck claim: id={}, createdDate={}",
                    claim.getId(), claim.getCreatedDate());

                claim.setStatus(NcpdpStatus.PENDING);
                claim.setErrorMessage("Reset from stuck PROCESSING state");
                ncpdpRepository.save(claim);
            }

        } catch (Exception e) {
            log.error("Error in detect stuck claims task", e);
        }
    }

    /**
     * Gets current processing metrics
     *
     * @return processing metrics as formatted string
     */
    public String getMetrics() {
        return String.format(
            "NCPDP Processor Metrics: total=%d, success=%d, failures=%d, lastRun=%s, isProcessing=%s",
            totalProcessed.get(),
            successCount.get(),
            failureCount.get(),
            lastProcessingTime,
            isProcessing.get()
        );
    }

    /**
     * Gets total number of claims processed
     */
    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    /**
     * Gets number of successfully processed claims
     */
    public long getSuccessCount() {
        return successCount.get();
    }

    /**
     * Gets number of failed claims
     */
    public long getFailureCount() {
        return failureCount.get();
    }

    /**
     * Checks if processor is currently processing
     */
    public boolean isProcessing() {
        return isProcessing.get();
    }

    /**
     * Gets last processing time
     */
    public LocalDateTime getLastProcessingTime() {
        return lastProcessingTime;
    }

    /**
     * Resets metrics (useful for testing)
     */
    public void resetMetrics() {
        totalProcessed.set(0);
        successCount.set(0);
        failureCount.set(0);
        lastProcessingTime = null;
    }

    @PreDestroy
    public void shutdown() {
        log.info("NCPDP Change Feed Processor shutting down");
        log.info("Final metrics: {}", getMetrics());
    }
}
