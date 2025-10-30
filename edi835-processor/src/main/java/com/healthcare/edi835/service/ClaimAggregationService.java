package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.*;
import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Service for aggregating claims into buckets based on bucketing rules.
 * Implements the three bucketing strategies: PAYER_PAYEE, BIN_PCN, and CUSTOM.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Find or create appropriate bucket for claim</li>
 *   <li>Add claim to bucket</li>
 *   <li>Update bucket statistics</li>
 *   <li>Log claim processing</li>
 *   <li>Handle rejections</li>
 * </ul>
 */
@Slf4j
@Service
public class ClaimAggregationService {

    private final EdiFileBucketRepository bucketRepository;
    private final ClaimProcessingLogRepository processingLogRepository;
    private final PayerRepository payerRepository;
    private final PayeeRepository payeeRepository;
    private final EdiFileNamingTemplateRepository templateRepository;
    private final BucketManagerService bucketManagerService;

    public ClaimAggregationService(
            EdiFileBucketRepository bucketRepository,
            ClaimProcessingLogRepository processingLogRepository,
            PayerRepository payerRepository,
            PayeeRepository payeeRepository,
            EdiFileNamingTemplateRepository templateRepository,
            BucketManagerService bucketManagerService) {
        this.bucketRepository = bucketRepository;
        this.processingLogRepository = processingLogRepository;
        this.payerRepository = payerRepository;
        this.payeeRepository = payeeRepository;
        this.templateRepository = templateRepository;
        this.bucketManagerService = bucketManagerService;
    }

    /**
     * Aggregates a claim into the appropriate bucket based on the bucketing rule.
     *
     * @param claim the claim to aggregate
     * @param rule the bucketing rule to apply
     */
    @Transactional
    public void aggregateClaim(Claim claim, EdiBucketingRule rule) {
        log.debug("Aggregating claim {} using rule {} (type: {})",
                claim.getId(), rule.getRuleName(), rule.getRuleType());

        try {
            // Validate claim data
            if (!isValidClaim(claim)) {
                handleRejectedClaim(claim, "Invalid or incomplete claim data");
                return;
            }

            // Find or create bucket based on rule type
            EdiFileBucket bucket = findOrCreateBucket(claim, rule);

            // Add claim to bucket
            addClaimToBucket(claim, bucket);

            // Log successful processing
            logClaimProcessing(claim, bucket);

            // Check if bucket needs threshold evaluation
            bucketManagerService.evaluateBucketThresholds(bucket);

            log.info("Successfully aggregated claim {} into bucket {}",
                    claim.getId(), bucket.getBucketId());

        } catch (Exception e) {
            log.error("Error aggregating claim {}: {}", claim.getId(), e.getMessage(), e);
            handleRejectedClaim(claim, "Error during aggregation: " + e.getMessage());
        }
    }

    /**
     * Finds an existing bucket or creates a new one based on the bucketing rule.
     *
     * @param claim the claim being aggregated
     * @param rule the bucketing rule
     * @return the bucket for this claim
     */
    private EdiFileBucket findOrCreateBucket(Claim claim, EdiBucketingRule rule) {
        return switch (rule.getRuleType()) {
            case PAYER_PAYEE -> findOrCreatePayerPayeeBucket(claim, rule);
            case BIN_PCN -> findOrCreateBinPcnBucket(claim, rule);
            case CUSTOM -> findOrCreateCustomBucket(claim, rule);
        };
    }

    /**
     * Finds or creates a bucket for PAYER_PAYEE bucketing strategy.
     *
     * @param claim the claim
     * @param rule the bucketing rule
     * @return the bucket
     */
    private EdiFileBucket findOrCreatePayerPayeeBucket(Claim claim, EdiBucketingRule rule) {
        // Try to find existing accumulating bucket
        Optional<EdiFileBucket> existingBucket = bucketRepository
                .findAccumulatingBucketForPayerPayee(claim.getPayerId(), claim.getPayeeId());

        if (existingBucket.isPresent()) {
            log.debug("Found existing PAYER_PAYEE bucket: {}", existingBucket.get().getBucketId());
            return existingBucket.get();
        }

        // Create new bucket
        log.info("Creating new PAYER_PAYEE bucket for payer={}, payee={}",
                claim.getPayerId(), claim.getPayeeId());

        return createNewBucket(claim, rule, null, null);
    }

    /**
     * Finds or creates a bucket for BIN_PCN bucketing strategy.
     *
     * @param claim the claim
     * @param rule the bucketing rule
     * @return the bucket
     */
    private EdiFileBucket findOrCreateBinPcnBucket(Claim claim, EdiBucketingRule rule) {
        String binNumber = claim.getBinNumber();
        String pcnNumber = claim.getPcnNumber();

        if (binNumber == null || binNumber.isEmpty()) {
            log.warn("BIN_PCN rule applied but claim {} has no BIN number, falling back to PAYER_PAYEE",
                    claim.getId());
            return findOrCreatePayerPayeeBucket(claim, rule);
        }

        // Try to find existing accumulating bucket
        Optional<EdiFileBucket> existingBucket = bucketRepository
                .findAccumulatingBucketForBinPcn(claim.getPayerId(), claim.getPayeeId(),
                        binNumber, pcnNumber);

        if (existingBucket.isPresent()) {
            log.debug("Found existing BIN_PCN bucket: {}", existingBucket.get().getBucketId());
            return existingBucket.get();
        }

        // Create new bucket
        log.info("Creating new BIN_PCN bucket for payer={}, payee={}, BIN={}, PCN={}",
                claim.getPayerId(), claim.getPayeeId(), binNumber, pcnNumber);

        return createNewBucket(claim, rule, binNumber, pcnNumber);
    }

    /**
     * Finds or creates a bucket for CUSTOM bucketing strategy.
     *
     * @param claim the claim
     * @param rule the bucketing rule
     * @return the bucket
     */
    private EdiFileBucket findOrCreateCustomBucket(Claim claim, EdiBucketingRule rule) {
        // TODO: Implement custom bucketing logic based on rule.getGroupingExpression()
        log.debug("Custom bucketing not yet fully implemented, falling back to PAYER_PAYEE");
        return findOrCreatePayerPayeeBucket(claim, rule);
    }

    /**
     * Creates a new bucket.
     *
     * @param claim the first claim in the bucket
     * @param rule the bucketing rule
     * @param binNumber optional BIN number
     * @param pcnNumber optional PCN number
     * @return the newly created bucket
     */
    private EdiFileBucket createNewBucket(Claim claim, EdiBucketingRule rule,
                                          String binNumber, String pcnNumber) {
        // Get payer and payee names
        String payerName = payerRepository.findByPayerId(claim.getPayerId())
                .map(Payer::getPayerName)
                .orElse(claim.getPayerId());

        String payeeName = payeeRepository.findByPayeeId(claim.getPayeeId())
                .map(Payee::getPayeeName)
                .orElse(claim.getPayeeId());

        // Get file naming template
        EdiFileNamingTemplate template = templateRepository
                .findByLinkedBucketingRule(rule)
                .stream()
                .findFirst()
                .orElse(templateRepository.findDefaultTemplate().orElse(null));

        // Create bucket
        EdiFileBucket bucket = EdiFileBucket.builder()
                .bucketingRule(rule)
                .bucketingRuleName(rule.getRuleName())
                .payerId(claim.getPayerId())
                .payerName(payerName)
                .payeeId(claim.getPayeeId())
                .payeeName(payeeName)
                .binNumber(binNumber)
                .pcnNumber(pcnNumber)
                .fileNamingTemplate(template)
                .build();

        return bucketRepository.save(bucket);
    }

    /**
     * Adds a claim to a bucket and updates bucket statistics.
     *
     * @param claim the claim to add
     * @param bucket the bucket to add to
     */
    private void addClaimToBucket(Claim claim, EdiFileBucket bucket) {
        BigDecimal claimAmount = claim.getPaidAmount() != null
                ? claim.getPaidAmount()
                : BigDecimal.ZERO;

        bucket.addClaim(claimAmount);

        bucketRepository.save(bucket);

        log.debug("Added claim {} to bucket {}, new count: {}, new amount: {}",
                claim.getId(), bucket.getBucketId(),
                bucket.getClaimCount(), bucket.getTotalAmount());
    }

    /**
     * Logs claim processing to audit trail.
     *
     * @param claim the processed claim
     * @param bucket the bucket it was added to
     */
    private void logClaimProcessing(Claim claim, EdiFileBucket bucket) {
        ClaimProcessingLog log = ClaimProcessingLog.forProcessedClaim(
                claim.getId(),
                bucket,
                claim.getPayerId(),
                claim.getPayeeId(),
                claim.getTotalChargeAmount(),
                claim.getPaidAmount()
        );

        processingLogRepository.save(log);
    }

    /**
     * Handles a rejected claim.
     *
     * @param claim the rejected claim
     * @param reason the rejection reason
     */
    private void handleRejectedClaim(Claim claim, String reason) {
        log.warn("Claim {} rejected: {}", claim.getId(), reason);

        ClaimProcessingLog log = ClaimProcessingLog.forRejectedClaim(
                claim.getId(),
                claim.getPayerId(),
                claim.getPayeeId(),
                reason
        );

        processingLogRepository.save(log);
    }

    /**
     * Validates claim data.
     *
     * @param claim the claim to validate
     * @return true if claim is valid
     */
    private boolean isValidClaim(Claim claim) {
        if (claim.getPayerId() == null || claim.getPayerId().isEmpty()) {
            log.warn("Claim {} has no payer ID", claim.getId());
            return false;
        }

        if (claim.getPayeeId() == null || claim.getPayeeId().isEmpty()) {
            log.warn("Claim {} has no payee ID", claim.getId());
            return false;
        }

        if (claim.getPaidAmount() == null || claim.getPaidAmount().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Claim {} has invalid paid amount", claim.getId());
            return false;
        }

        return true;
    }
}
