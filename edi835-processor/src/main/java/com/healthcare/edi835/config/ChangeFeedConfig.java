package com.healthcare.edi835.config;

import com.azure.cosmos.ChangeFeedProcessor;
import com.azure.cosmos.ChangeFeedProcessorBuilder;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.ChangeFeedProcessorOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.edi835.changefeed.ChangeFeedHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for Azure Cosmos DB Change Feed Processor.
 * 
 * <p>This configuration sets up the Change Feed processor to listen for real-time
 * changes in the Cosmos DB claims container and forward them to the remittance
 * processing pipeline.</p>
 * 
 * <p>Key Features:</p>
 * <ul>
 *   <li>Automatic lease management for distributed processing</li>
 *   <li>Configurable polling intervals and batch sizes</li>
 *   <li>Checkpoint management for reliability</li>
 *   <li>Partition-based parallel processing</li>
 * </ul>
 * 
 * @see ChangeFeedHandler
 * @see CosmosDbConfig
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "changefeed.cosmos.enabled", havingValue = "true", matchIfMissing = false)
public class ChangeFeedConfig {

    private final CosmosAsyncClient cosmosClient;
    private final ChangeFeedHandler changeFeedHandler;

    @Value("${spring.cloud.azure.cosmos.database}")
    private String databaseName;

    @Value("${cosmos.changefeed.container}")
    private String feedContainerName;

    @Value("${cosmos.changefeed.lease-container}")
    private String leaseContainerName;

    @Value("${cosmos.changefeed.host-name}")
    private String hostName;

    @Value("${cosmos.changefeed.max-items-count:100}")
    private int maxItemsCount;

    @Value("${cosmos.changefeed.poll-interval-ms:5000}")
    private long pollIntervalMs;

    @Value("${cosmos.changefeed.start-from-beginning:false}")
    private boolean startFromBeginning;

    public ChangeFeedConfig(
            CosmosAsyncClient cosmosClient,
            ChangeFeedHandler changeFeedHandler) {
        this.cosmosClient = cosmosClient;
        this.changeFeedHandler = changeFeedHandler;
    }

    /**
     * Creates and configures the Change Feed Processor bean.
     * 
     * <p>The processor listens to changes in the feed container and delegates
     * processing to the ChangeFeedHandler. It manages leases automatically
     * to coordinate processing across multiple instances.</p>
     * 
     * @return configured ChangeFeedProcessor instance
     * @throws IllegalStateException if containers cannot be accessed
     */
    @Bean
    public ChangeFeedProcessor changeFeedProcessor() {
        log.info("Initializing Change Feed Processor for container: {}", feedContainerName);

        // Get feed container reference
        CosmosAsyncContainer feedContainer = cosmosClient
                .getDatabase(databaseName)
                .getContainer(feedContainerName);

        // Get lease container reference
        CosmosAsyncContainer leaseContainer = cosmosClient
                .getDatabase(databaseName)
                .getContainer(leaseContainerName);

        // Verify containers exist
        verifyContainerExists(feedContainer, feedContainerName);
        verifyContainerExists(leaseContainer, leaseContainerName);

        // Configure change feed processor options
        ChangeFeedProcessorOptions options = buildChangeFeedOptions();

        // Build and return the processor
        return new ChangeFeedProcessorBuilder()
                .hostName(hostName)
                .feedContainer(feedContainer)
                .leaseContainer(leaseContainer)
                .options(options)
                .handleChanges(this::handleChanges)
                .buildChangeFeedProcessor();
    }

    /**
     * Builds Change Feed Processor options from configuration properties.
     * 
     * @return configured ChangeFeedProcessorOptions
     */
    private ChangeFeedProcessorOptions buildChangeFeedOptions() {
        ChangeFeedProcessorOptions options = new ChangeFeedProcessorOptions();
        options.setFeedPollDelay(Duration.ofMillis(pollIntervalMs));
        options.setMaxItemCount(maxItemsCount);
        options.setStartFromBeginning(startFromBeginning);
        
        log.debug("Change Feed Options - Poll Interval: {}ms, Max Items: {}, Start From Beginning: {}",
                pollIntervalMs, maxItemsCount, startFromBeginning);
        
        return options;
    }

    /**
     * Handles changes from the change feed by delegating to ChangeFeedHandler.
     * 
     * <p>This method is called automatically by the Change Feed Processor when
     * new changes are detected. It provides a clean separation between the
     * infrastructure concern (Change Feed setup) and business logic (claim processing).</p>
     * 
     * @param docs list of changed documents from Cosmos DB
     */
    private void handleChanges(List<JsonNode> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }

        log.debug("Received {} documents from change feed", docs.size());

        try {
            changeFeedHandler.handleChanges(docs);
        } catch (Exception e) {
            log.error("Error processing change feed batch of {} documents", docs.size(), e);
            // Note: Exception is logged but not rethrown to allow processor to continue
            // Consider implementing a DLQ (Dead Letter Queue) for failed items
        }
    }

    /**
     * Verifies that a Cosmos DB container exists and is accessible.
     * 
     * @param container the container to verify
     * @param containerName the name of the container (for logging)
     * @throws IllegalStateException if container cannot be accessed
     */
    private void verifyContainerExists(CosmosAsyncContainer container, String containerName) {
        try {
            container.read()
                    .doOnSuccess(response -> 
                            log.info("Successfully verified container: {}", containerName))
                    .doOnError(error -> 
                            log.error("Failed to access container: {}", containerName, error))
                    .block();
        } catch (Exception e) {
            String message = String.format(
                    "Cannot access Cosmos DB container '%s'. Ensure it exists and credentials are correct.",
                    containerName);
            throw new IllegalStateException(message, e);
        }
    }

    /**
     * Provides configuration summary for monitoring and debugging.
     * 
     * @return configuration summary string
     */
    @Override
    public String toString() {
        return String.format(
                "ChangeFeedConfig{database='%s', container='%s', leaseContainer='%s', " +
                "hostName='%s', maxItems=%d, pollIntervalMs=%d}",
                databaseName, feedContainerName, leaseContainerName, 
                hostName, maxItemsCount, pollIntervalMs);
    }
}
