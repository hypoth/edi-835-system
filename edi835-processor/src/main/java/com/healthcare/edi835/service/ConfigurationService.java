package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.*;
import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for retrieving and managing system configuration.
 * Provides cached access to configuration entities.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Retrieve bucketing rules</li>
 *   <li>Retrieve thresholds</li>
 *   <li>Retrieve commit criteria</li>
 *   <li>Retrieve file naming templates</li>
 *   <li>Cache configuration for performance</li>
 * </ul>
 */
@Slf4j
@Service
public class ConfigurationService {

    private final EdiBucketingRuleRepository bucketingRuleRepository;
    private final EdiGenerationThresholdRepository thresholdRepository;
    private final EdiCommitCriteriaRepository commitCriteriaRepository;
    private final EdiFileNamingTemplateRepository templateRepository;
    private final PayerRepository payerRepository;
    private final PayeeRepository payeeRepository;
    private final InsurancePlanRepository insurancePlanRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    public ConfigurationService(
            EdiBucketingRuleRepository bucketingRuleRepository,
            EdiGenerationThresholdRepository thresholdRepository,
            EdiCommitCriteriaRepository commitCriteriaRepository,
            EdiFileNamingTemplateRepository templateRepository,
            PayerRepository payerRepository,
            PayeeRepository payeeRepository,
            InsurancePlanRepository insurancePlanRepository,
            PaymentMethodRepository paymentMethodRepository) {
        this.bucketingRuleRepository = bucketingRuleRepository;
        this.thresholdRepository = thresholdRepository;
        this.commitCriteriaRepository = commitCriteriaRepository;
        this.templateRepository = templateRepository;
        this.payerRepository = payerRepository;
        this.payeeRepository = payeeRepository;
        this.insurancePlanRepository = insurancePlanRepository;
        this.paymentMethodRepository = paymentMethodRepository;
    }

    /**
     * Gets all active bucketing rules ordered by priority.
     * Results are cached for performance.
     *
     * @return list of active bucketing rules
     */
    @Cacheable("bucketingRules")
    public List<EdiBucketingRule> getActiveBucketingRules() {
        log.debug("Retrieving active bucketing rules");
        return bucketingRuleRepository.findActiveRulesByPriority();
    }

    /**
     * Gets a bucketing rule by ID.
     *
     * @param ruleId the rule ID
     * @return the bucketing rule
     */
    public Optional<EdiBucketingRule> getBucketingRuleById(UUID ruleId) {
        log.debug("Retrieving bucketing rule: {}", ruleId);
        return bucketingRuleRepository.findById(ruleId);
    }

    /**
     * Gets bucketing rule by name.
     *
     * @param ruleName the rule name
     * @return the bucketing rule
     */
    public Optional<EdiBucketingRule> getBucketingRuleByName(String ruleName) {
        log.debug("Retrieving bucketing rule by name: {}", ruleName);
        return bucketingRuleRepository.findByRuleName(ruleName);
    }

    /**
     * Gets thresholds for a specific bucketing rule.
     * Results are cached for performance.
     *
     * @param rule the bucketing rule
     * @return list of generation thresholds
     */
    @Cacheable(value = "generationThresholds", key = "#rule.ruleId")
    public List<EdiGenerationThreshold> getThresholdsForRule(EdiBucketingRule rule) {
        log.debug("Retrieving thresholds for rule: {}", rule.getRuleName());
        return thresholdRepository.findByLinkedBucketingRuleAndIsActiveTrue(rule);
    }

    /**
     * Gets commit criteria for a specific bucketing rule.
     * Results are cached for performance.
     *
     * @param rule the bucketing rule
     * @return the commit criteria, or empty if not configured
     */
    @Cacheable(value = "commitCriteria", key = "#rule.ruleId")
    public Optional<EdiCommitCriteria> getCommitCriteriaForRule(EdiBucketingRule rule) {
        log.debug("Retrieving commit criteria for rule: {}", rule.getRuleName());
        return commitCriteriaRepository.findByLinkedBucketingRuleAndIsActiveTrue(rule);
    }

    /**
     * Gets file naming template for a bucketing rule.
     * Falls back to default template if no rule-specific template exists.
     *
     * @param rule the bucketing rule
     * @return the file naming template
     */
    public Optional<EdiFileNamingTemplate> getFileNamingTemplate(EdiBucketingRule rule) {
        log.debug("Retrieving file naming template for rule: {}", rule.getRuleName());

        List<EdiFileNamingTemplate> templates = templateRepository.findByLinkedBucketingRule(rule);

        if (!templates.isEmpty()) {
            return Optional.of(templates.get(0));
        }

        // Fall back to default template
        log.debug("No rule-specific template found, using default template");
        return templateRepository.findDefaultTemplate();
    }

    /**
     * Gets default file naming template.
     *
     * @return the default template
     */
    public Optional<EdiFileNamingTemplate> getDefaultFileNamingTemplate() {
        log.debug("Retrieving default file naming template");
        return templateRepository.findDefaultTemplate();
    }

    /**
     * Gets payer information by payer ID.
     *
     * @param payerId the payer ID
     * @return the payer entity
     */
    @Cacheable(value = "payers", key = "#payerId")
    public Optional<Payer> getPayerById(String payerId) {
        log.debug("Retrieving payer: {}", payerId);
        return payerRepository.findByPayerId(payerId);
    }

    /**
     * Gets payee information by payee ID.
     *
     * @param payeeId the payee ID
     * @return the payee entity
     */
    @Cacheable(value = "payees", key = "#payeeId")
    public Optional<Payee> getPayeeById(String payeeId) {
        log.debug("Retrieving payee: {}", payeeId);
        return payeeRepository.findByPayeeId(payeeId);
    }

    /**
     * Gets all active payers.
     *
     * @return list of active payers
     */
    public List<Payer> getActivePayers() {
        log.debug("Retrieving all active payers");
        return payerRepository.findActivePayersWithSftpConfig();
    }

    /**
     * Gets all active payees.
     *
     * @return list of active payees
     */
    public List<Payee> getActivePayees() {
        log.debug("Retrieving all active payees");
        return payeeRepository.findActivePayees();
    }

    /**
     * Gets insurance plan by BIN and PCN.
     *
     * @param binNumber the BIN number
     * @param pcnNumber the PCN number (optional)
     * @return the insurance plan
     */
    public Optional<InsurancePlan> getInsurancePlanByBinPcn(String binNumber, String pcnNumber) {
        log.debug("Retrieving insurance plan: BIN={}, PCN={}", binNumber, pcnNumber);
        return insurancePlanRepository.findByBinAndPcn(binNumber, pcnNumber);
    }

    /**
     * Gets payment method configuration by method type.
     *
     * @param methodType the payment method type (EFT or CHECK)
     * @return the payment method
     */
    public Optional<PaymentMethod> getPaymentMethod(PaymentMethod.MethodType methodType) {
        log.debug("Retrieving payment method: {}", methodType);
        List<PaymentMethod> methods = paymentMethodRepository.findByMethodTypeAndIsActiveTrue(methodType);
        return methods.isEmpty() ? Optional.empty() : Optional.of(methods.get(0));
    }

    /**
     * Gets payment method configuration by code string.
     * Converts code to MethodType enum.
     *
     * @param paymentMethodCode the payment method code (e.g., "EFT", "CHECK")
     * @return the payment method
     */
    public Optional<PaymentMethod> getPaymentMethodByCode(String paymentMethodCode) {
        log.debug("Retrieving payment method by code: {}", paymentMethodCode);
        try {
            PaymentMethod.MethodType methodType = PaymentMethod.MethodType.valueOf(paymentMethodCode.toUpperCase());
            return getPaymentMethod(methodType);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid payment method code: {}", paymentMethodCode);
            return Optional.empty();
        }
    }

    /**
     * Determines if a claim requires special handling based on configuration.
     *
     * @param claim the claim to evaluate
     * @return true if special handling is required
     */
    public boolean requiresSpecialHandling(Claim claim) {
        // Check if payer has special requirements
        Optional<Payer> payer = getPayerById(claim.getPayerId());
        if (payer.isPresent() && payer.get().getRequiresSpecialHandling() != null &&
            payer.get().getRequiresSpecialHandling()) {
            log.debug("Claim {} requires special handling due to payer configuration", claim.getId());
            return true;
        }

        // Check if payee has special requirements
        Optional<Payee> payee = getPayeeById(claim.getPayeeId());
        if (payee.isPresent() && payee.get().getRequiresSpecialHandling() != null &&
            payee.get().getRequiresSpecialHandling()) {
            log.debug("Claim {} requires special handling due to payee configuration", claim.getId());
            return true;
        }

        return false;
    }

    /**
     * Gets SFTP configuration for a payer.
     *
     * @param payerId the payer ID
     * @return SFTP configuration details
     */
    public Optional<SftpConfig> getSftpConfigForPayer(String payerId) {
        log.debug("Retrieving SFTP config for payer: {}", payerId);

        Optional<Payer> payer = getPayerById(payerId);
        if (payer.isEmpty()) {
            return Optional.empty();
        }

        Payer p = payer.get();

        // Check if SFTP is configured
        if (p.getSftpHost() == null || p.getSftpHost().isEmpty()) {
            log.debug("No SFTP configuration found for payer: {}", payerId);
            return Optional.empty();
        }

        SftpConfig config = new SftpConfig(
                p.getSftpHost(),
                p.getSftpPort(),
                p.getSftpUsername(),
                p.getSftpPath()
        );

        return Optional.of(config);
    }

    /**
     * Validates system configuration.
     *
     * @return validation result with any errors
     */
    public ConfigurationValidationResult validateConfiguration() {
        log.info("Validating system configuration");

        ConfigurationValidationResult result = new ConfigurationValidationResult();

        // Check if at least one bucketing rule exists
        List<EdiBucketingRule> rules = getActiveBucketingRules();
        if (rules.isEmpty()) {
            result.addError("No active bucketing rules configured");
        }

        // Check if default file naming template exists
        if (getDefaultFileNamingTemplate().isEmpty()) {
            result.addWarning("No default file naming template configured");
        }

        // Check if at least one payer exists
        List<Payer> payers = getActivePayers();
        if (payers.isEmpty()) {
            result.addWarning("No active payers configured");
        }

        // Validate each rule has thresholds
        for (EdiBucketingRule rule : rules) {
            List<EdiGenerationThreshold> thresholds = getThresholdsForRule(rule);
            if (thresholds.isEmpty()) {
                result.addWarning("Rule '" + rule.getRuleName() + "' has no generation thresholds");
            }
        }

        log.info("Configuration validation complete: {} errors, {} warnings",
                result.getErrors().size(), result.getWarnings().size());

        return result;
    }

    /**
     * SFTP configuration DTO.
     */
    public record SftpConfig(String host, Integer port, String username, String path) {}

    /**
     * Configuration validation result.
     */
    public static class ConfigurationValidationResult {
        private final List<String> errors = new java.util.ArrayList<>();
        private final List<String> warnings = new java.util.ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}
