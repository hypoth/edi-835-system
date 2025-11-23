package com.healthcare.edi835.event;

import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.FileGenerationHistory;
import com.healthcare.edi835.exception.MissingConfigurationException;
import com.healthcare.edi835.repository.EdiFileBucketRepository;
import com.healthcare.edi835.repository.FileGenerationHistoryRepository;
import com.healthcare.edi835.service.BucketManagerService;
import com.healthcare.edi835.service.Edi835GeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listener for bucket status change events.
 * Triggers EDI file generation when buckets transition to GENERATING status.
 */
@Slf4j
@Component
public class EdiGenerationEventListener {

    private final Edi835GeneratorService edi835GeneratorService;
    private final BucketManagerService bucketManagerService;
    private final EdiFileBucketRepository bucketRepository;
    private final FileGenerationHistoryRepository fileHistoryRepository;

    public EdiGenerationEventListener(
            Edi835GeneratorService edi835GeneratorService,
            BucketManagerService bucketManagerService,
            EdiFileBucketRepository bucketRepository,
            FileGenerationHistoryRepository fileHistoryRepository) {
        this.edi835GeneratorService = edi835GeneratorService;
        this.bucketManagerService = bucketManagerService;
        this.bucketRepository = bucketRepository;
        this.fileHistoryRepository = fileHistoryRepository;
    }

    /**
     * Handles bucket status change events.
     * Triggers EDI generation asynchronously when bucket transitions to GENERATING.
     */
    @Async("taskExecutor")
    @EventListener
    @Transactional
    public void handleBucketStatusChange(BucketStatusChangeEvent event) {
        log.debug("Received bucket status change event: {}", event);

        if (event.isTransitionToGenerating()) {
            handleGenerationRequest(event.getBucket());
        }
    }

    /**
     * Handles EDI file generation for a bucket.
     */
    private void handleGenerationRequest(EdiFileBucket bucket) {
        log.info("Starting EDI file generation for bucket: {} (Claims: {}, Amount: {})",
                bucket.getBucketId(), bucket.getClaimCount(), bucket.getTotalAmount());

        try {
            // Refresh bucket to get latest state
            EdiFileBucket freshBucket = bucketRepository.findById(bucket.getBucketId())
                    .orElseThrow(() -> new IllegalStateException("Bucket not found: " + bucket.getBucketId()));

            // Double-check status (in case it changed)
            if (freshBucket.getStatus() != EdiFileBucket.BucketStatus.GENERATING) {
                log.warn("Bucket {} is no longer in GENERATING status. Current: {}. Skipping generation.",
                        freshBucket.getBucketId(), freshBucket.getStatus());
                return;
            }

            // Generate the EDI file
            FileGenerationHistory history = edi835GeneratorService.generateEdi835File(freshBucket);

            // Save the generation history
            fileHistoryRepository.save(history);

            // Mark bucket as completed
            bucketManagerService.markCompleted(freshBucket);

            log.info("EDI file generation completed successfully for bucket: {}. File: {}",
                    freshBucket.getBucketId(), history.getGeneratedFileName());

        } catch (MissingConfigurationException e) {
            log.warn("EDI file generation blocked due to missing configuration for bucket: {}. Type: {}, ID: {}",
                    bucket.getBucketId(), e.getConfigurationType(), e.getMissingId());

            // Mark bucket as MISSING_CONFIGURATION
            try {
                EdiFileBucket configBucket = bucketRepository.findById(bucket.getBucketId())
                        .orElseThrow(() -> new IllegalStateException("Bucket not found: " + bucket.getBucketId()));
                bucketManagerService.markMissingConfiguration(configBucket);
                log.info("Bucket {} marked as MISSING_CONFIGURATION. User action required to create {} with ID: {}",
                        configBucket.getBucketId(), e.getConfigurationType(), e.getMissingId());
            } catch (Exception markConfigError) {
                log.error("Failed to mark bucket as MISSING_CONFIGURATION: {}", bucket.getBucketId(), markConfigError);
            }

        } catch (Exception e) {
            log.error("EDI file generation failed for bucket: {}", bucket.getBucketId(), e);

            // Mark bucket as failed with error details
            try {
                EdiFileBucket failedBucket = bucketRepository.findById(bucket.getBucketId())
                        .orElseThrow(() -> new IllegalStateException("Bucket not found: " + bucket.getBucketId()));
                bucketManagerService.markFailed(failedBucket, e);
            } catch (Exception markFailedError) {
                log.error("Failed to mark bucket as FAILED: {}", bucket.getBucketId(), markFailedError);
            }
        }
    }
}
