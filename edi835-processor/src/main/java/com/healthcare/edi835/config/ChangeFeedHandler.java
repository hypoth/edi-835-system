package com.healthcare.edi835.changefeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.service.RemittanceProcessorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handler for processing Cosmos DB Change Feed events.
 * 
 * <p>This component processes changes from the Cosmos DB change feed,
 * filtering for relevant claim documents and forwarding them to the
 * remittance processor.</p>
 * 
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Filter documents for processed claims only</li>
 *   <li>Parse JSON documents to Claim objects</li>
 *   <li>Forward valid claims to RemittanceProcessorService</li>
 *   <li>Handle parsing errors gracefully</li>
 *   <li>Log processing metrics</li>
 * </ul>
 * 
 * @see ChangeFeedConfig
 * @see RemittanceProcessorService
 */
@Slf4j
@Component
public class ChangeFeedHandler {

    private final RemittanceProcessorService remittanceProcessor;
    private final ObjectMapper objectMapper;

    public ChangeFeedHandler(
            RemittanceProcessorService remittanceProcessor,
            ObjectMapper objectMapper) {
        this.remittanceProcessor = remittanceProcessor;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles a batch of changes from the change feed.
     * 
     * <p>Processes each document in the batch, filtering for processed claims
     * and forwarding them to the remittance processor.</p>
     * 
     * @param docs list of changed documents from Cosmos DB
     */
    public void handleChanges(List<JsonNode> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }

        log.debug("Processing {} documents from change feed", docs.size());

        int processedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        for (JsonNode doc : docs) {
            try {
                if (isProcessedClaim(doc)) {
                    Claim claim = parseClaim(doc);
                    remittanceProcessor.processClaim(claim);
                    processedCount++;
                } else {
                    skippedCount++;
                    log.trace("Skipped non-processed claim: {}", 
                            doc.has("id") ? doc.get("id").asText() : "unknown");
                }
            } catch (Exception e) {
                errorCount++;
                String docId = doc.has("id") ? doc.get("id").asText() : "unknown";
                log.error("Error processing document from change feed: id={}", docId, e);
                // Consider implementing DLQ (Dead Letter Queue) here
            }
        }

        log.info("Change feed batch processed: {} processed, {} skipped, {} errors",
                processedCount, skippedCount, errorCount);
    }

    /**
     * Checks if the document represents a processed claim.
     * 
     * <p>Filters for claims that have been processed by the D0 engine
     * and are ready for remittance processing.</p>
     * 
     * @param doc the document to check
     * @return true if this is a processed claim
     */
    private boolean isProcessedClaim(JsonNode doc) {
        if (doc == null || !doc.has("status")) {
            return false;
        }

        String status = doc.get("status").asText();
        return "PROCESSED".equalsIgnoreCase(status) || 
               "PAID".equalsIgnoreCase(status);
    }

    /**
     * Parses a JSON document to a Claim object.
     * 
     * @param doc the JSON document to parse
     * @return parsed Claim object
     * @throws com.fasterxml.jackson.core.JsonProcessingException if parsing fails
     */
    private Claim parseClaim(JsonNode doc) throws com.fasterxml.jackson.core.JsonProcessingException {
        return objectMapper.treeToValue(doc, Claim.class);
    }
}
