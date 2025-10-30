package com.healthcare.edi835.changefeed;

import com.azure.cosmos.ChangeFeedProcessor;
import com.azure.cosmos.ChangeFeedProcessorBuilder;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.ChangeFeedProcessorOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.service.RemittanceProcessorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Cosmos DB Change Feed processor that listens to claim changes
 * and forwards them to the remittance processor.
 * Only enabled when changefeed.cosmos.enabled=true
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "changefeed.cosmos.enabled", havingValue = "true", matchIfMissing = false)
public class ClaimChangeFeedProcessor {

    private final CosmosAsyncClient cosmosClient;
    private final RemittanceProcessorService remittanceProcessor;
    private ChangeFeedProcessor changeFeedProcessor;

    @Value("${spring.cloud.azure.cosmos.database}")
    private String databaseName;

    @Value("${cosmos.changefeed.container}")
    private String containerName;

    @Value("${cosmos.changefeed.lease-container}")
    private String leaseContainerName;

    @Value("${cosmos.changefeed.host-name}")
    private String hostName;

    @Value("${cosmos.changefeed.max-items-count}")
    private int maxItemsCount;

    @Value("${cosmos.changefeed.poll-interval-ms}")
    private long pollIntervalMs;

    public ClaimChangeFeedProcessor(
            CosmosAsyncClient cosmosClient,
            RemittanceProcessorService remittanceProcessor) {
        this.cosmosClient = cosmosClient;
        this.remittanceProcessor = remittanceProcessor;
    }

    @PostConstruct
    public void start() {
        log.info("Initializing Change Feed Processor for container: {}", containerName);

        CosmosAsyncContainer feedContainer = cosmosClient
            .getDatabase(databaseName)
            .getContainer(containerName);

        CosmosAsyncContainer leaseContainer = cosmosClient
            .getDatabase(databaseName)
            .getContainer(leaseContainerName);

        ChangeFeedProcessorOptions options = new ChangeFeedProcessorOptions();
        options.setFeedPollDelay(Duration.ofMillis(pollIntervalMs));
        options.setMaxItemCount(maxItemsCount);
        options.setStartFromBeginning(false);

        changeFeedProcessor = new ChangeFeedProcessorBuilder()
            .hostName(hostName)
            .feedContainer(feedContainer)
            .leaseContainer(leaseContainer)
            .options(options)
            .handleChanges(this::handleChanges)
            .buildChangeFeedProcessor();

        changeFeedProcessor.start()
            .doOnSuccess(aVoid -> log.info("Change Feed Processor started successfully"))
            .doOnError(throwable -> log.error("Failed to start Change Feed Processor", throwable))
            .subscribe();
    }

    @PreDestroy
    public void stop() {
        if (changeFeedProcessor != null) {
            log.info("Stopping Change Feed Processor");
            changeFeedProcessor.stop()
                .doOnSuccess(aVoid -> log.info("Change Feed Processor stopped"))
                .subscribe();
        }
    }

    /**
     * Handles changes from the change feed
     */
    private void handleChanges(List<JsonNode> docs) {
        log.debug("Processing {} documents from change feed", docs.size());

        for (JsonNode doc : docs) {
            try {
                // Check if this is a processed claim
                if (isProcessedClaim(doc)) {
                    Claim claim = parseClaim(doc);
                    log.info("Processing claim: {} for payer: {}", 
                        claim.getClaimNumber(), claim.getPayerId());
                    
                    // Forward to remittance processor
                    remittanceProcessor.processClaim(claim);
                }
            } catch (Exception e) {
                log.error("Error processing document from change feed: {}", 
                    doc.get("id"), e);
                // Consider implementing dead-letter queue for failed items
            }
        }
    }

    /**
     * Checks if the document represents a processed claim
     */
    private boolean isProcessedClaim(JsonNode doc) {
        // Filter logic - only process claims with PROCESSED status
        return doc.has("status") && 
               "PROCESSED".equals(doc.get("status").asText());
    }

    /**
     * Parses JSON document to Claim object
     */
    private Claim parseClaim(JsonNode doc) {
        // Implement JSON to Claim conversion
        // This is simplified - use ObjectMapper in production
        return Claim.builder()
            .id(doc.get("id").asText())
            .payerId(doc.get("payerId").asText())
            .payeeId(doc.get("payeeId").asText())
            .claimNumber(doc.get("claimNumber").asText())
            // ... map other fields
            .build();
    }
}