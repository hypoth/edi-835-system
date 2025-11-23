package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.*;
import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.repository.*;
import com.healthcare.edi835.util.EdiIdentifierNormalizer;
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

            // Store error in bucket for UI visibility (if bucket exists)
            try {
                EdiFileBucket bucket = findOrCreateBucket(claim, rule);
                bucket.setLastError(e.getClass().getSimpleName() + ": " + e.getMessage());
                bucketRepository.save(bucket);
            } catch (Exception ignored) {
                // Bucket lookup failed, error already logged
            }
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
        // Normalize IDs for lookup
        String normalizedPayerId = EdiIdentifierNormalizer.normalizePayerId(claim.getPayerId());
        String normalizedPayeeId = EdiIdentifierNormalizer.normalizePayeeId(claim.getPayeeId());

        // Try to find existing accumulating bucket using normalized IDs
        Optional<EdiFileBucket> existingBucket = bucketRepository
                .findAccumulatingBucketForPayerPayee(normalizedPayerId, normalizedPayeeId);

        if (existingBucket.isPresent()) {
            log.debug("Found existing PAYER_PAYEE bucket: {}", existingBucket.get().getBucketId());
            return existingBucket.get();
        }

        // Create new bucket
        log.info("Creating new PAYER_PAYEE bucket for payer={} (normalized: {}), payee={} (normalized: {})",
                claim.getPayerId(), normalizedPayerId, claim.getPayeeId(), normalizedPayeeId);

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

        // Normalize IDs for lookup
        String normalizedPayerId = EdiIdentifierNormalizer.normalizePayerId(claim.getPayerId());
        String normalizedPayeeId = EdiIdentifierNormalizer.normalizePayeeId(claim.getPayeeId());

        // Try to find existing accumulating bucket using normalized IDs
        Optional<EdiFileBucket> existingBucket = bucketRepository
                .findAccumulatingBucketForBinPcn(normalizedPayerId, normalizedPayeeId,
                        binNumber, pcnNumber);

        if (existingBucket.isPresent()) {
            log.debug("Found existing BIN_PCN bucket: {}", existingBucket.get().getBucketId());
            return existingBucket.get();
        }

        // Create new bucket
        log.info("Creating new BIN_PCN bucket for payer={} (normalized: {}), payee={} (normalized: {}), BIN={}, PCN={}",
                claim.getPayerId(), normalizedPayerId, claim.getPayeeId(), normalizedPayeeId, binNumber, pcnNumber);

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
        // Normalize IDs from D0 claims to conform to EDI validation rules
        String normalizedPayerId = EdiIdentifierNormalizer.normalizePayerId(claim.getPayerId());
        String normalizedPayeeId = EdiIdentifierNormalizer.normalizePayeeId(claim.getPayeeId());

        log.debug("Creating bucket with normalized IDs - Payer: '{}' -> '{}', Payee: '{}' -> '{}'",
                claim.getPayerId(), normalizedPayerId, claim.getPayeeId(), normalizedPayeeId);

        // Get payer and payee names (try both raw and normalized IDs)
        String payerName = payerRepository.findByPayerId(normalizedPayerId)
                .or(() -> payerRepository.findByPayerId(claim.getPayerId()))
                .map(Payer::getPayerName)
                .orElseGet(() -> {
                    // If payer doesn't exist, auto-create it
                    log.info("Payer '{}' (normalized: '{}') not found in master data. Auto-creating payer record.",
                            claim.getPayerId(), normalizedPayerId);
                    return autoCreatePayer(claim.getPayerId(), normalizedPayerId);
                });

        String payeeName = payeeRepository.findByPayeeId(normalizedPayeeId)
                .or(() -> payeeRepository.findByPayeeId(claim.getPayeeId()))
                .map(Payee::getPayeeName)
                .orElseGet(() -> {
                    // If payee doesn't exist, auto-create it
                    log.info("Payee '{}' (normalized: '{}') not found in master data. Auto-creating payee record.",
                            claim.getPayeeId(), normalizedPayeeId);
                    return autoCreatePayee(claim.getPayeeId(), normalizedPayeeId);
                });

        // Get file naming template
        EdiFileNamingTemplate template = templateRepository
                .findByLinkedBucketingRule(rule)
                .stream()
                .findFirst()
                .orElse(templateRepository.findDefaultTemplate().orElse(null));

        // Create bucket with normalized IDs
        EdiFileBucket bucket = EdiFileBucket.builder()
                .bucketingRule(rule)
                .bucketingRuleName(rule.getRuleName())
                .payerId(normalizedPayerId)
                .payerName(payerName)
                .payeeId(normalizedPayeeId)
                .payeeName(payeeName)
                .binNumber(binNumber)
                .pcnNumber(pcnNumber)
                .fileNamingTemplate(template)
                .build();

        return bucketRepository.save(bucket);
    }

    /**
     * Auto-creates a payer record when master data doesn't exist.
     *
     * @param rawPayerId the raw payer ID from D0 claims
     * @param normalizedPayerId the normalized payer ID
     * @return the payer name
     */
    private String autoCreatePayer(String rawPayerId, String normalizedPayerId) {
        try {
            // Create payer entity with generated values
            Payer payer = new Payer();
            payer.setPayerId(normalizedPayerId);
            payer.setPayerName(generateFriendlyName(rawPayerId, "Payer"));
            payer.setIsaQualifier("ZZ");
            payer.setIsaSenderId(EdiIdentifierNormalizer.generateIsaSenderId(normalizedPayerId));
            payer.setGsApplicationSenderId(EdiIdentifierNormalizer.generateGsApplicationSenderId(normalizedPayerId));
            payer.setIsActive(true);
            payer.setCreatedBy("SYSTEM_AUTO");

            Payer savedPayer = payerRepository.save(payer);

            log.info("Auto-created payer: ID='{}', Name='{}', ISA Sender ID='{}'",
                    savedPayer.getPayerId(), savedPayer.getPayerName(), savedPayer.getIsaSenderId());

            return savedPayer.getPayerName();

        } catch (Exception e) {
            log.error("Failed to auto-create payer '{}': {}", normalizedPayerId, e.getMessage(), e);
            // Return a friendly name even if creation fails
            return generateFriendlyName(rawPayerId, "Payer");
        }
    }

    /**
     * Auto-creates a payee record when master data doesn't exist.
     *
     * @param rawPayeeId the raw payee ID from D0 claims
     * @param normalizedPayeeId the normalized payee ID
     * @return the payee name
     */
    private String autoCreatePayee(String rawPayeeId, String normalizedPayeeId) {
        try {
            // Create payee entity
            Payee payee = new Payee();
            payee.setPayeeId(normalizedPayeeId);
            payee.setPayeeName(generateFriendlyName(rawPayeeId, "Payee"));
            payee.setIsActive(true);
            payee.setCreatedBy("SYSTEM_AUTO");

            Payee savedPayee = payeeRepository.save(payee);

            log.info("Auto-created payee: ID='{}', Name='{}'",
                    savedPayee.getPayeeId(), savedPayee.getPayeeName());

            return savedPayee.getPayeeName();

        } catch (Exception e) {
            log.error("Failed to auto-create payee '{}': {}", normalizedPayeeId, e.getMessage(), e);
            // Return a friendly name even if creation fails
            return generateFriendlyName(rawPayeeId, "Payee");
        }
    }

    /**
     * Generates a friendly display name from a raw ID.
     *
     * @param rawId the raw ID
     * @param prefix the prefix (e.g., "Payer", "Payee")
     * @return friendly name
     */
    private String generateFriendlyName(String rawId, String prefix) {
        if (rawId == null || rawId.isEmpty()) {
            return prefix + " (Unknown)";
        }

        // Convert to title case and clean up
        String friendly = rawId.replaceAll("[_-]", " ");
        friendly = friendly.substring(0, 1).toUpperCase() +
                   friendly.substring(1).toLowerCase();

        return friendly;
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
