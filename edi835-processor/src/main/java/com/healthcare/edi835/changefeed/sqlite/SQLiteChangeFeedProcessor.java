package com.healthcare.edi835.changefeed.sqlite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.edi835.changefeed.ChangeFeedHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-based change feed processor using version-based approach.
 *
 * <p>This processor polls the data_changes table for new changes, processes them
 * using the same ChangeFeedHandler as Cosmos DB, and maintains checkpoints for
 * resumability.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Version-based change tracking for replay capability</li>
 *   <li>Checkpoint management for reliable processing</li>
 *   <li>Batch processing with configurable batch size</li>
 *   <li>Automatic retry and error handling</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "changefeed.sqlite.enabled", havingValue = "true")
public class SQLiteChangeFeedProcessor {

    private final DataChangeRepository dataChangeRepository;
    private final FeedVersionRepository feedVersionRepository;
    private final ChangeFeedCheckpointRepository checkpointRepository;
    private final ChangeFeedHandler changeFeedHandler;
    private final ObjectMapper objectMapper;

    @Value("${changefeed.sqlite.poll-interval-ms:5000}")
    private long pollIntervalMs;

    @Value("${changefeed.sqlite.batch-size:100}")
    private int batchSize;

    @Value("${changefeed.sqlite.auto-version:true}")
    private boolean autoVersion;

    @Value("${changefeed.sqlite.consumer-id:edi835-processor-default}")
    private String consumerId;

    private String hostName;

    public SQLiteChangeFeedProcessor(
            DataChangeRepository dataChangeRepository,
            FeedVersionRepository feedVersionRepository,
            ChangeFeedCheckpointRepository checkpointRepository,
            ChangeFeedHandler changeFeedHandler,
            ObjectMapper objectMapper) {
        this.dataChangeRepository = dataChangeRepository;
        this.feedVersionRepository = feedVersionRepository;
        this.checkpointRepository = checkpointRepository;
        this.changeFeedHandler = changeFeedHandler;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            this.hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            this.hostName = "unknown-host";
            log.warn("Could not determine hostname, using 'unknown-host'", e);
        }

        log.info("SQLite Change Feed Processor initialized: consumerId={}, batchSize={}, pollInterval={}ms",
                consumerId, batchSize, pollIntervalMs);

        // Initialize checkpoint if it doesn't exist
        initializeCheckpoint();
    }

    /**
     * Scheduled method that polls for changes and processes them.
     * Runs at fixed delay based on poll-interval-ms configuration.
     */
    @Scheduled(fixedDelayString = "${changefeed.sqlite.poll-interval-ms:5000}")
    public void pollAndProcessChanges() {
        try {
            // log.debug("Polling for changes..."); // disabling as too many logs are getting printed

            // Get current checkpoint
            ChangeFeedCheckpoint checkpoint = getOrCreateCheckpoint();

            // Get current feed version
            Long currentVersion = getCurrentFeedVersion();

            // Fetch unprocessed changes
            List<DataChange> changes = fetchUnprocessedChanges(checkpoint);

            if (changes.isEmpty()) {
                log.trace("No unprocessed changes found");
                return;
            }

            log.info("Found {} unprocessed changes to process", changes.size());

            // Process changes
            processChanges(changes, checkpoint, currentVersion);

        } catch (Exception e) {
            log.error("Error during change feed poll cycle", e);
        }
    }

    /**
     * Processes a batch of changes.
     *
     * @param changes the changes to process
     * @param checkpoint the current checkpoint
     * @param currentVersion the current feed version
     */
    @Transactional
    protected void processChanges(List<DataChange> changes, ChangeFeedCheckpoint checkpoint, Long currentVersion) {
        FeedVersion feedVersion = getOrCreateFeedVersion(currentVersion);

        int processedCount = 0;
        int errorCount = 0;

        // Group changes into batches for the handler
        List<JsonNode> batch = new ArrayList<>();

        for (DataChange change : changes) {
            try {
                // Convert new_values JSON to JsonNode
                if (change.getNewValues() != null && !change.getNewValues().isEmpty()) {
                    JsonNode jsonNode = objectMapper.readTree(change.getNewValues());
                    batch.add(jsonNode);

                    // Process batch when it reaches batch size
                    if (batch.size() >= batchSize) {
                        processJsonBatch(batch);
                        markChangesAsProcessed(changes.subList(processedCount, processedCount + batch.size()));
                        processedCount += batch.size();
                        batch.clear();
                    }
                }

            } catch (Exception e) {
                log.error("Error processing change: changeId={}", change.getChangeId(), e);
                change.markAsFailed(e.getMessage());
                dataChangeRepository.save(change);
                errorCount++;
            }
        }

        // Process remaining batch
        if (!batch.isEmpty()) {
            try {
                processJsonBatch(batch);
                markChangesAsProcessed(changes.subList(processedCount, processedCount + batch.size()));
                processedCount += batch.size();
            } catch (Exception e) {
                log.error("Error processing final batch", e);
                errorCount += batch.size();
            }
        }

        // Update checkpoint
        if (!changes.isEmpty()) {
            DataChange lastChange = changes.get(changes.size() - 1);
            checkpoint.updateCheckpoint(
                    lastChange.getFeedVersion().longValue(),
                    lastChange.getSequenceNumber()
            );
            checkpointRepository.save(checkpoint);
        }

        // Update feed version statistics
        feedVersion.setChangesCount(feedVersion.getChangesCount() + changes.size());
        feedVersion.setProcessedCount(feedVersion.getProcessedCount() + processedCount);
        feedVersion.setErrorCount(feedVersion.getErrorCount() + errorCount);
        feedVersionRepository.save(feedVersion);

        log.info("Processed {} changes ({} successful, {} errors) for version {}",
                changes.size(), processedCount, errorCount, currentVersion);
    }

    /**
     * Processes a batch of JSON nodes using the ChangeFeedHandler.
     *
     * @param batch the batch of JSON nodes
     */
    private void processJsonBatch(List<JsonNode> batch) {
        if (batch.isEmpty()) {
            return;
        }

        log.debug("Processing batch of {} changes", batch.size());
        changeFeedHandler.handleChanges(batch);
    }

    /**
     * Marks a list of changes as processed.
     *
     * @param changes the changes to mark as processed
     */
    private void markChangesAsProcessed(List<DataChange> changes) {
        for (DataChange change : changes) {
            change.markAsProcessed();
        }
        dataChangeRepository.saveAll(changes);
    }

    /**
     * Fetches unprocessed changes after the current checkpoint.
     *
     * @param checkpoint the current checkpoint
     * @return list of unprocessed changes
     */
    private List<DataChange> fetchUnprocessedChanges(ChangeFeedCheckpoint checkpoint) {
        // Find changes after the last checkpoint
        return dataChangeRepository.findChangesAfterCheckpoint(
                checkpoint.getLastFeedVersion().intValue(),
                checkpoint.getLastSequenceNumber(),
                batchSize
        );
    }

    /**
     * Gets or creates the checkpoint for this consumer.
     *
     * @return the checkpoint
     */
    private ChangeFeedCheckpoint getOrCreateCheckpoint() {
        return checkpointRepository.findByConsumerId(consumerId)
                .orElseGet(() -> {
                    log.info("Creating new checkpoint for consumer: {}", consumerId);
                    ChangeFeedCheckpoint newCheckpoint = ChangeFeedCheckpoint.builder()
                            .consumerId(consumerId)
                            .lastFeedVersion(0L)
                            .lastSequenceNumber(0L)
                            .totalProcessed(0L)
                            .build();
                    return checkpointRepository.save(newCheckpoint);
                });
    }

    /**
     * Initializes the checkpoint if it doesn't exist.
     */
    private void initializeCheckpoint() {
        if (!checkpointRepository.existsById(consumerId)) {
            ChangeFeedCheckpoint checkpoint = ChangeFeedCheckpoint.builder()
                    .consumerId(consumerId)
                    .lastFeedVersion(0L)
                    .lastSequenceNumber(0L)
                    .totalProcessed(0L)
                    .build();
            checkpointRepository.save(checkpoint);
            log.info("Initialized checkpoint for consumer: {}", consumerId);
        }
    }

    /**
     * Gets the current feed version ID.
     *
     * @return the current feed version ID
     */
    private Long getCurrentFeedVersion() {
        return feedVersionRepository.findMaxVersionId();
    }

    /**
     * Gets or creates a feed version for the current version ID.
     *
     * @param versionId the version ID
     * @return the feed version
     */
    private FeedVersion getOrCreateFeedVersion(Long versionId) {
        return feedVersionRepository.findById(versionId)
                .orElseGet(() -> {
                    log.info("Creating new feed version: {}", versionId);
                    FeedVersion newVersion = FeedVersion.builder()
                            .versionId(versionId)
                            .hostName(hostName)
                            .status(FeedVersion.Status.RUNNING)
                            .build();
                    return feedVersionRepository.save(newVersion);
                });
    }

    /**
     * Manually triggers a change feed poll (for testing/admin purposes).
     */
    public void triggerPoll() {
        log.info("Manually triggering change feed poll");
        pollAndProcessChanges();
    }

    /**
     * Gets the current checkpoint status.
     *
     * @return the current checkpoint
     */
    public ChangeFeedCheckpoint getCheckpointStatus() {
        return getOrCreateCheckpoint();
    }

    /**
     * Resets the checkpoint to start processing from the beginning.
     */
    @Transactional
    public void resetCheckpoint() {
        log.warn("Resetting checkpoint for consumer: {}", consumerId);
        ChangeFeedCheckpoint checkpoint = getOrCreateCheckpoint();
        checkpoint.setLastFeedVersion(0L);
        checkpoint.setLastSequenceNumber(0L);
        checkpoint.setTotalProcessed(0L);
        checkpointRepository.save(checkpoint);
    }
}
