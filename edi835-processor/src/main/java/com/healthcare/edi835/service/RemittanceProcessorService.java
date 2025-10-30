package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.EdiBucketingRule;
import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.repository.EdiBucketingRuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Main orchestrator for remittance processing.
 * Receives claims from ChangeFeedHandler and routes them through the processing pipeline.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Receive claims from change feed</li>
 *   <li>Determine applicable bucketing rules</li>
 *   <li>Route claims to ClaimAggregationService</li>
 *   <li>Handle processing errors</li>
 * </ul>
 */
@Slf4j
@Service
public class RemittanceProcessorService {

    private final EdiBucketingRuleRepository bucketingRuleRepository;
    private final ClaimAggregationService claimAggregationService;
    private final ConfigurationService configurationService;

    public RemittanceProcessorService(
            EdiBucketingRuleRepository bucketingRuleRepository,
            ClaimAggregationService claimAggregationService,
            ConfigurationService configurationService) {
        this.bucketingRuleRepository = bucketingRuleRepository;
        this.claimAggregationService = claimAggregationService;
        this.configurationService = configurationService;
    }

    /**
     * Processes a single claim from the change feed.
     *
     * @param claim the claim to process
     */
    @Transactional
    public void processClaim(Claim claim) {
        if (claim == null) {
            log.warn("Received null claim, skipping processing");
            return;
        }

        log.debug("Processing claim: claimId={}, payerId={}, payeeId={}, amount={}",
                claim.getId(), claim.getPayerId(), claim.getPayeeId(), claim.getPaidAmount());

        try {
            // Determine applicable bucketing rule
            EdiBucketingRule rule = determineBucketingRule(claim);

            if (rule == null) {
                log.warn("No active bucketing rule found for claim: claimId={}, payerId={}, payeeId={}",
                        claim.getId(), claim.getPayerId(), claim.getPayeeId());
                return;
            }

            log.debug("Using bucketing rule: ruleName={}, ruleType={} for claim: {}",
                    rule.getRuleName(), rule.getRuleType(), claim.getId());

            // Aggregate claim into appropriate bucket
            claimAggregationService.aggregateClaim(claim, rule);

            log.info("Successfully processed claim: claimId={}, ruleName={}",
                    claim.getId(), rule.getRuleName());

        } catch (Exception e) {
            log.error("Error processing claim: claimId={}, error={}",
                    claim.getId(), e.getMessage(), e);
            // Note: Exception is logged but not rethrown to allow change feed to continue
            // Consider implementing a Dead Letter Queue (DLQ) for failed claims
        }
    }

    /**
     * Processes multiple claims in batch.
     *
     * @param claims list of claims to process
     */
    @Transactional
    public void processClaims(List<Claim> claims) {
        if (claims == null || claims.isEmpty()) {
            return;
        }

        log.info("Processing batch of {} claims", claims.size());

        int successCount = 0;
        int errorCount = 0;

        for (Claim claim : claims) {
            try {
                processClaim(claim);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to process claim in batch: claimId={}", claim.getId(), e);
            }
        }

        log.info("Batch processing complete: total={}, success={}, errors={}",
                claims.size(), successCount, errorCount);
    }

    /**
     * Determines the applicable bucketing rule for a claim.
     * Rules are evaluated by priority (highest first).
     *
     * @param claim the claim to evaluate
     * @return the applicable bucketing rule, or null if none found
     */
    private EdiBucketingRule determineBucketingRule(Claim claim) {
        // Get all active rules ordered by priority
        List<EdiBucketingRule> rules = bucketingRuleRepository.findActiveRulesByPriority();

        if (rules.isEmpty()) {
            log.warn("No active bucketing rules configured in the system");
            return null;
        }

        // Evaluate rules by priority
        for (EdiBucketingRule rule : rules) {
            if (isRuleApplicable(claim, rule)) {
                return rule;
            }
        }

        // If no specific rule matches, return the default rule (lowest priority)
        return rules.get(rules.size() - 1);
    }

    /**
     * Checks if a bucketing rule is applicable to a claim.
     *
     * @param claim the claim to check
     * @param rule the rule to evaluate
     * @return true if the rule applies to the claim
     */
    private boolean isRuleApplicable(Claim claim, EdiBucketingRule rule) {
        return switch (rule.getRuleType()) {
            case PAYER_PAYEE -> {
                // PAYER_PAYEE rule applies to all claims (default behavior)
                yield true;
            }
            case BIN_PCN -> {
                // BIN_PCN rule applies if claim has BIN/PCN information
                yield claim.getBinNumber() != null && !claim.getBinNumber().isEmpty();
            }
            case CUSTOM -> {
                // CUSTOM rule evaluation based on grouping expression
                yield evaluateCustomRule(claim, rule);
            }
        };
    }

    /**
     * Evaluates a custom bucketing rule expression.
     *
     * @param claim the claim to evaluate
     * @param rule the custom rule
     * @return true if the custom rule applies
     */
    private boolean evaluateCustomRule(Claim claim, EdiBucketingRule rule) {
        // TODO: Implement custom expression evaluation
        // This could use Spring Expression Language (SpEL) or a custom DSL
        // For now, return true to apply the rule
        log.debug("Custom rule evaluation not yet implemented for rule: {}",
                rule.getRuleName());
        return true;
    }

    /**
     * Gets processing statistics.
     *
     * @return processing statistics as a formatted string
     */
    public String getProcessingStatistics() {
        // TODO: Implement statistics tracking
        return "Statistics tracking not yet implemented";
    }
}
