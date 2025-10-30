package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.FileGenerationHistory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled service for automatic SFTP file delivery.
 *
 * <p>Automatically delivers pending EDI files to payer SFTP servers at configured intervals.
 * Runs as a scheduled job using Spring's @Scheduled annotation.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Configurable schedule via cron expression</li>
 *   <li>Batch processing with configurable batch size</li>
 *   <li>Automatic retry for transient failures</li>
 *   <li>Comprehensive logging for monitoring</li>
 *   <li>Can be enabled/disabled via configuration</li>
 * </ul>
 *
 * <p>Configuration:</p>
 * <pre>
 * file-delivery:
 *   scheduler:
 *     enabled: true
 *     cron: "0 *\/5 * * * ?"  # Every 5 minutes
 *     batch-size: 10
 * </pre>
 *
 * @see FileDeliveryService
 * @see FileGenerationHistory
 */
@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "file-delivery.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ScheduledDeliveryService {

    private final FileDeliveryService deliveryService;

    @Value("${file-delivery.scheduler.batch-size:10}")
    private int batchSize;

    @Value("${file-delivery.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    public ScheduledDeliveryService(FileDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * Scheduled job to automatically deliver pending files.
     *
     * <p>Runs at configured intervals (default: every 5 minutes).
     * Processes up to batch-size files per execution.</p>
     *
     * <p>Execution Flow:</p>
     * <ol>
     *   <li>Fetch pending files from database</li>
     *   <li>Limit to batch-size to avoid overload</li>
     *   <li>Attempt delivery for each file</li>
     *   <li>Log success/failure for monitoring</li>
     *   <li>Continue on individual failures</li>
     * </ol>
     */
    @Scheduled(cron = "${file-delivery.scheduler.cron:0 */5 * * * ?}")
    public void autoDeliverPendingFiles() {
        if (!schedulerEnabled) {
            log.debug("Scheduled delivery is disabled, skipping execution");
            return;
        }

        log.info("Starting scheduled file delivery job...");

        try {
            // Get pending files
            List<FileGenerationHistory> pendingFiles = deliveryService.getPendingDeliveries();

            if (pendingFiles.isEmpty()) {
                log.debug("No pending files found for delivery");
                return;
            }

            // Limit to batch size
            int filesToProcess = Math.min(pendingFiles.size(), batchSize);
            List<FileGenerationHistory> batch = pendingFiles.subList(0, filesToProcess);

            log.info("Found {} pending files, processing batch of {}",
                    pendingFiles.size(), filesToProcess);

            // Track success and failure counts
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // Process each file in batch
            for (FileGenerationHistory file : batch) {
                try {
                    log.debug("Attempting delivery for file: {} ({})",
                            file.getFileName(), file.getFileId());

                    deliveryService.deliverFile(file.getFileId());
                    successCount.incrementAndGet();

                    log.info("File {} delivered successfully", file.getFileName());

                } catch (FileDeliveryService.DeliveryException e) {
                    failureCount.incrementAndGet();
                    log.error("Failed to deliver file {}: {}",
                            file.getFileName(), e.getMessage());

                    // Continue processing other files despite individual failures
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("Unexpected error delivering file {}: {}",
                            file.getFileName(), e.getMessage(), e);
                }
            }

            // Log summary
            log.info("Scheduled delivery completed: {} succeeded, {} failed, {} remaining",
                    successCount.get(),
                    failureCount.get(),
                    pendingFiles.size() - filesToProcess);

        } catch (Exception e) {
            log.error("Error in scheduled delivery job: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled job to retry failed deliveries.
     *
     * <p>Runs less frequently than the main delivery job (default: hourly).
     * Attempts to re-deliver files that have failed but haven't exceeded max retries.</p>
     */
    @Scheduled(cron = "${file-delivery.scheduler.retry-cron:0 0 * * * ?}")
    public void autoRetryFailedDeliveries() {
        if (!schedulerEnabled) {
            log.debug("Scheduled delivery is disabled, skipping retry execution");
            return;
        }

        log.info("Starting scheduled retry of failed deliveries...");

        try {
            int successCount = deliveryService.retryFailedDeliveries();

            if (successCount > 0) {
                log.info("Retry completed: {} files successfully delivered", successCount);
            } else {
                log.debug("No failed files successfully retried");
            }

        } catch (Exception e) {
            log.error("Error in scheduled retry job: {}", e.getMessage(), e);
        }
    }

    /**
     * Provides status information about the scheduler.
     *
     * @return Status summary string
     */
    public String getSchedulerStatus() {
        return String.format("Scheduler: %s, Batch Size: %d",
                schedulerEnabled ? "ENABLED" : "DISABLED",
                batchSize);
    }

    /**
     * Manually triggers the delivery job (for testing/admin purposes).
     *
     * @return Summary of execution
     */
    public String triggerManualDelivery() {
        log.info("Manual delivery triggered");
        autoDeliverPendingFiles();
        return "Manual delivery triggered successfully";
    }

    /**
     * Manually triggers the retry job (for testing/admin purposes).
     *
     * @return Summary of execution
     */
    public String triggerManualRetry() {
        log.info("Manual retry triggered");
        autoRetryFailedDeliveries();
        return "Manual retry triggered successfully";
    }
}
