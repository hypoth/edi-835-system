package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.*;
import com.healthcare.edi835.model.dto.BucketingRuleDTO;
import com.healthcare.edi835.model.dto.CommitCriteriaDTO;
import com.healthcare.edi835.model.dto.GenerationThresholdDTO;
import com.healthcare.edi835.repository.*;
import com.healthcare.edi835.service.ConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for managing system configuration.
 * Provides endpoints for CRUD operations on payers, payees, rules, thresholds, etc.
 *
 * <p>Base Path: /api/v1/config</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config")
// CORS configured globally in WebConfig.java - do not override with wildcard
public class ConfigurationController {

    private final ConfigurationService configurationService;
    private final PayerRepository payerRepository;
    private final PayeeRepository payeeRepository;
    private final EdiBucketingRuleRepository bucketingRuleRepository;
    private final EdiGenerationThresholdRepository thresholdRepository;
    private final EdiCommitCriteriaRepository commitCriteriaRepository;
    private final EdiFileNamingTemplateRepository templateRepository;
    private final InsurancePlanRepository insurancePlanRepository;
    private final com.healthcare.edi835.service.EncryptionService encryptionService;

    public ConfigurationController(
            ConfigurationService configurationService,
            PayerRepository payerRepository,
            PayeeRepository payeeRepository,
            EdiBucketingRuleRepository bucketingRuleRepository,
            EdiGenerationThresholdRepository thresholdRepository,
            EdiCommitCriteriaRepository commitCriteriaRepository,
            EdiFileNamingTemplateRepository templateRepository,
            InsurancePlanRepository insurancePlanRepository,
            com.healthcare.edi835.service.EncryptionService encryptionService) {
        this.configurationService = configurationService;
        this.payerRepository = payerRepository;
        this.payeeRepository = payeeRepository;
        this.bucketingRuleRepository = bucketingRuleRepository;
        this.thresholdRepository = thresholdRepository;
        this.commitCriteriaRepository = commitCriteriaRepository;
        this.templateRepository = templateRepository;
        this.insurancePlanRepository = insurancePlanRepository;
        this.encryptionService = encryptionService;
    }

    // ==================== Payer Management ====================

    @GetMapping("/payers")
    public ResponseEntity<List<Payer>> getAllPayers() {
        log.debug("GET /api/v1/config/payers - Retrieving all payers");
        List<Payer> payers = payerRepository.findAll();
        return ResponseEntity.ok(payers);
    }

    @GetMapping("/payers/active")
    public ResponseEntity<List<Payer>> getActivePayers() {
        log.debug("GET /api/v1/config/payers/active - Retrieving active payers");
        List<Payer> payers = configurationService.getActivePayers();
        return ResponseEntity.ok(payers);
    }

    @GetMapping("/payers/{payerId}")
    public ResponseEntity<Payer> getPayerById(@PathVariable String payerId) {
        log.debug("GET /api/v1/config/payers/{} - Retrieving payer", payerId);
        return configurationService.getPayerById(payerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/payers")
    public ResponseEntity<Payer> createPayer(@Valid @RequestBody Payer payer) {
        log.info("POST /api/v1/config/payers - Creating payer: {}", payer.getPayerId());

        // Encrypt SFTP password if provided
        if (payer.getSftpPassword() != null && !payer.getSftpPassword().isEmpty()) {
            String encryptedPassword = encryptionService.encrypt(payer.getSftpPassword());
            payer.setSftpPassword(encryptedPassword);
            log.debug("SFTP password encrypted for payer: {}", payer.getPayerId());
        }

        Payer savedPayer = payerRepository.save(payer);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPayer);
    }

    @PutMapping("/payers/{id}")
    public ResponseEntity<Payer> updatePayer(@PathVariable UUID id, @Valid @RequestBody Payer payer) {
        log.info("PUT /api/v1/config/payers/{} - Updating payer", id);

        return payerRepository.findById(id)
                .map(existingPayer -> {
                    payer.setId(id);

                    // Encrypt SFTP password if provided and changed
                    if (payer.getSftpPassword() != null && !payer.getSftpPassword().isEmpty()) {
                        // Only encrypt if it's different from existing (not already encrypted)
                        // Check if password looks like it's already encrypted (contains non-printable chars or is hex)
                        if (!payer.getSftpPassword().equals(existingPayer.getSftpPassword())) {
                            String encryptedPassword = encryptionService.encrypt(payer.getSftpPassword());
                            payer.setSftpPassword(encryptedPassword);
                            log.debug("SFTP password encrypted for payer: {}", payer.getPayerId());
                        }
                    }

                    Payer updatedPayer = payerRepository.save(payer);
                    return ResponseEntity.ok(updatedPayer);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/payers/{id}")
    public ResponseEntity<Void> deletePayer(@PathVariable UUID id) {
        log.info("DELETE /api/v1/config/payers/{} - Deleting payer", id);

        if (payerRepository.existsById(id)) {
            payerRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/payers/search")
    public ResponseEntity<List<Payer>> searchPayers(@RequestParam String query) {
        log.debug("GET /api/v1/config/payers/search?query={}", query);
        List<Payer> payers = payerRepository.searchPayers(query);
        return ResponseEntity.ok(payers);
    }

    // ==================== Payee Management ====================

    @GetMapping("/payees")
    public ResponseEntity<List<Payee>> getAllPayees() {
        log.debug("GET /api/v1/config/payees - Retrieving all payees");
        List<Payee> payees = payeeRepository.findAll();
        return ResponseEntity.ok(payees);
    }

    @GetMapping("/payees/active")
    public ResponseEntity<List<Payee>> getActivePayees() {
        log.debug("GET /api/v1/config/payees/active - Retrieving active payees");
        List<Payee> payees = configurationService.getActivePayees();
        return ResponseEntity.ok(payees);
    }

    @GetMapping("/payees/{payeeId}")
    public ResponseEntity<Payee> getPayeeById(@PathVariable String payeeId) {
        log.debug("GET /api/v1/config/payees/{} - Retrieving payee", payeeId);
        return configurationService.getPayeeById(payeeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/payees")
    public ResponseEntity<Payee> createPayee(@Valid @RequestBody Payee payee) {
        log.info("POST /api/v1/config/payees - Creating payee: {}", payee.getPayeeId());
        Payee savedPayee = payeeRepository.save(payee);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPayee);
    }

    @PutMapping("/payees/{id}")
    public ResponseEntity<Payee> updatePayee(@PathVariable UUID id, @Valid @RequestBody Payee payee) {
        log.info("PUT /api/v1/config/payees/{} - Updating payee", id);

        return payeeRepository.findById(id)
                .map(existingPayee -> {
                    payee.setId(id);
                    Payee updatedPayee = payeeRepository.save(payee);
                    return ResponseEntity.ok(updatedPayee);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/payees/{id}")
    public ResponseEntity<Void> deletePayee(@PathVariable UUID id) {
        log.info("DELETE /api/v1/config/payees/{} - Deleting payee", id);

        if (payeeRepository.existsById(id)) {
            payeeRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ==================== Bucketing Rules ====================

    /**
     * Convert EdiBucketingRule entity to DTO to avoid Hibernate proxy serialization issues.
     */
    private BucketingRuleDTO convertToDTO(EdiBucketingRule rule) {
        return BucketingRuleDTO.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .ruleType(rule.getRuleType() != null ? rule.getRuleType().name() : null)
                .groupingExpression(rule.getGroupingExpression())
                .priority(rule.getPriority())
                .linkedPayerId(rule.getLinkedPayer() != null ? rule.getLinkedPayer().getId() : null)
                .linkedPayerName(rule.getLinkedPayer() != null ? rule.getLinkedPayer().getPayerName() : null)
                .linkedPayeeId(rule.getLinkedPayee() != null ? rule.getLinkedPayee().getId() : null)
                .linkedPayeeName(rule.getLinkedPayee() != null ? rule.getLinkedPayee().getPayeeName() : null)
                .description(rule.getDescription())
                .isActive(rule.getIsActive())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .createdBy(rule.getCreatedBy())
                .updatedBy(rule.getUpdatedBy())
                .build();
    }

    /**
     * Convert EdiCommitCriteria entity to DTO to avoid array serialization issues with SQLite.
     */
    private CommitCriteriaDTO convertCommitCriteriaToDTO(EdiCommitCriteria criteria) {
        return CommitCriteriaDTO.builder()
                .id(criteria.getId())
                .criteriaName(criteria.getCriteriaName())
                .commitMode(criteria.getCommitMode() != null ? criteria.getCommitMode().name() : null)
                .autoCommitThreshold(criteria.getAutoCommitThreshold())
                .manualApprovalThreshold(criteria.getManualApprovalThreshold())
                // Frontend-expected field names (aliases)
                .approvalClaimCountThreshold(criteria.getManualApprovalThreshold())
                .approvalAmountThreshold(criteria.getAutoCommitThreshold() != null ?
                        criteria.getAutoCommitThreshold().doubleValue() : null)
                .approvalRequiredRoles(criteria.getApprovalRequiredRoles() != null ?
                        Arrays.asList(criteria.getApprovalRequiredRoles()) : null)
                .overridePermissions(criteria.getOverridePermissions() != null ?
                        Arrays.asList(criteria.getOverridePermissions()) : null)
                .linkedBucketingRuleId(criteria.getLinkedBucketingRule() != null ?
                        criteria.getLinkedBucketingRule().getId() : null)
                .linkedBucketingRuleName(criteria.getLinkedBucketingRule() != null ?
                        criteria.getLinkedBucketingRule().getRuleName() : null)
                .isActive(criteria.getIsActive())
                .createdAt(criteria.getCreatedAt())
                .updatedAt(criteria.getUpdatedAt())
                .createdBy(criteria.getCreatedBy())
                .updatedBy(criteria.getUpdatedBy())
                .build();
    }

    /**
     * Convert EdiGenerationThreshold entity to DTO to avoid Hibernate proxy serialization issues.
     */
    private GenerationThresholdDTO convertThresholdToDTO(EdiGenerationThreshold threshold) {
        // Build nested bucketing rule summary if linked rule exists
        GenerationThresholdDTO.BucketingRuleSummary ruleSummary = null;
        if (threshold.getLinkedBucketingRule() != null) {
            EdiBucketingRule rule = threshold.getLinkedBucketingRule();
            ruleSummary = GenerationThresholdDTO.BucketingRuleSummary.builder()
                    .id(rule.getId())
                    .ruleId(rule.getId().toString())
                    .ruleName(rule.getRuleName())
                    .ruleType(rule.getRuleType() != null ? rule.getRuleType().name() : null)
                    .priority(rule.getPriority())
                    .isActive(rule.getIsActive())
                    .build();
        }

        return GenerationThresholdDTO.builder()
                .id(threshold.getId())
                .thresholdName(threshold.getThresholdName())
                .thresholdType(threshold.getThresholdType() != null ? threshold.getThresholdType().name() : null)
                .maxClaims(threshold.getMaxClaims())
                .maxAmount(threshold.getMaxAmount())
                .timeDuration(threshold.getTimeDuration() != null ? threshold.getTimeDuration().name() : null)
                .generationSchedule(threshold.getGenerationSchedule())
                .linkedBucketingRule(ruleSummary)
                .linkedBucketingRuleId(threshold.getLinkedBucketingRule() != null ?
                        threshold.getLinkedBucketingRule().getId() : null)
                .linkedBucketingRuleName(threshold.getLinkedBucketingRule() != null ?
                        threshold.getLinkedBucketingRule().getRuleName() : null)
                .isActive(threshold.getIsActive())
                .createdAt(threshold.getCreatedAt())
                .updatedAt(threshold.getUpdatedAt())
                .createdBy(threshold.getCreatedBy())
                .updatedBy(threshold.getUpdatedBy())
                .build();
    }

    @GetMapping("/rules")
    public ResponseEntity<List<BucketingRuleDTO>> getAllRules() {
        log.debug("GET /api/v1/config/rules - Retrieving all bucketing rules");
        List<EdiBucketingRule> rules = bucketingRuleRepository.findAll();
        List<BucketingRuleDTO> dtos = rules.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/rules/active")
    public ResponseEntity<List<BucketingRuleDTO>> getActiveRules() {
        log.debug("GET /api/v1/config/rules/active - Retrieving active rules");
        List<EdiBucketingRule> rules = configurationService.getActiveBucketingRules();
        List<BucketingRuleDTO> dtos = rules.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<BucketingRuleDTO> getRuleById(@PathVariable UUID id) {
        log.debug("GET /api/v1/config/rules/{} - Retrieving rule", id);
        return configurationService.getBucketingRuleById(id)
                .map(this::convertToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/rules")
    public ResponseEntity<EdiBucketingRule> createRule(@Valid @RequestBody EdiBucketingRule rule) {
        log.info("POST /api/v1/config/rules - Creating rule: {}", rule.getRuleName());
        EdiBucketingRule savedRule = bucketingRuleRepository.save(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRule);
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<EdiBucketingRule> updateRule(@PathVariable UUID id,
                                                        @Valid @RequestBody EdiBucketingRule rule) {
        log.info("PUT /api/v1/config/rules/{} - Updating rule", id);

        return bucketingRuleRepository.findById(id)
                .map(existingRule -> {
                    rule.setRuleId(id);
                    EdiBucketingRule updatedRule = bucketingRuleRepository.save(rule);
                    return ResponseEntity.ok(updatedRule);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        log.info("DELETE /api/v1/config/rules/{} - Deleting rule", id);

        if (bucketingRuleRepository.existsById(id)) {
            bucketingRuleRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ==================== Generation Thresholds ====================

    @GetMapping("/thresholds")
    public ResponseEntity<List<GenerationThresholdDTO>> getAllThresholds() {
        log.debug("GET /api/v1/config/thresholds - Retrieving all thresholds");
        List<EdiGenerationThreshold> thresholds = thresholdRepository.findAll();
        List<GenerationThresholdDTO> dtos = thresholds.stream()
                .map(this::convertThresholdToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/thresholds/rule/{ruleId}")
    public ResponseEntity<List<GenerationThresholdDTO>> getThresholdsByRule(@PathVariable UUID ruleId) {
        log.debug("GET /api/v1/config/thresholds/rule/{} - Retrieving thresholds", ruleId);

        return configurationService.getBucketingRuleById(ruleId)
                .map(rule -> {
                    List<EdiGenerationThreshold> thresholds =
                            configurationService.getThresholdsForRule(rule);
                    List<GenerationThresholdDTO> dtos = thresholds.stream()
                            .map(this::convertThresholdToDTO)
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(dtos);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/thresholds/{id}")
    public ResponseEntity<GenerationThresholdDTO> getThresholdById(@PathVariable UUID id) {
        log.debug("GET /api/v1/config/thresholds/{} - Retrieving threshold", id);
        return thresholdRepository.findById(id)
                .map(this::convertThresholdToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/thresholds")
    public ResponseEntity<GenerationThresholdDTO> createThreshold(
            @Valid @RequestBody java.util.Map<String, Object> thresholdData) {
        log.info("POST /api/v1/config/thresholds - Creating threshold");

        EdiGenerationThreshold threshold = new EdiGenerationThreshold();

        // Map basic fields
        if (thresholdData.containsKey("thresholdName")) {
            threshold.setThresholdName((String) thresholdData.get("thresholdName"));
        }
        if (thresholdData.containsKey("thresholdType")) {
            threshold.setThresholdType(EdiGenerationThreshold.ThresholdType.valueOf((String) thresholdData.get("thresholdType")));
        }
        if (thresholdData.containsKey("maxClaims")) {
            threshold.setMaxClaims((Integer) thresholdData.get("maxClaims"));
        }
        if (thresholdData.containsKey("maxAmount")) {
            Object amountObj = thresholdData.get("maxAmount");
            if (amountObj instanceof Number) {
                threshold.setMaxAmount(new java.math.BigDecimal(amountObj.toString()));
            }
        }
        if (thresholdData.containsKey("timeDuration")) {
            threshold.setTimeDuration(EdiGenerationThreshold.TimeDuration.valueOf((String) thresholdData.get("timeDuration")));
        }
        if (thresholdData.containsKey("generationSchedule")) {
            threshold.setGenerationSchedule((String) thresholdData.get("generationSchedule"));
        }
        if (thresholdData.containsKey("isActive")) {
            threshold.setIsActive((Boolean) thresholdData.get("isActive"));
        }

        // Handle linked bucketing rule (can be nested object or just ID)
        Object linkedRuleObj = thresholdData.get("linkedBucketingRule");
        if (linkedRuleObj != null) {
            UUID ruleId = null;
            if (linkedRuleObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> ruleMap = (java.util.Map<String, Object>) linkedRuleObj;
                Object idObj = ruleMap.get("id");
                if (idObj != null) {
                    ruleId = UUID.fromString(idObj.toString());
                }
            } else if (linkedRuleObj instanceof String && !linkedRuleObj.toString().isEmpty()) {
                ruleId = UUID.fromString(linkedRuleObj.toString());
            }

            if (ruleId != null) {
                bucketingRuleRepository.findById(ruleId).ifPresent(threshold::setLinkedBucketingRule);
            }
        }

        EdiGenerationThreshold savedThreshold = thresholdRepository.save(threshold);
        GenerationThresholdDTO dto = convertThresholdToDTO(savedThreshold);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/thresholds/{id}")
    public ResponseEntity<GenerationThresholdDTO> updateThreshold(
            @PathVariable UUID id, @Valid @RequestBody java.util.Map<String, Object> thresholdData) {
        log.info("PUT /api/v1/config/thresholds/{} - Updating threshold", id);

        return thresholdRepository.findById(id)
                .map(existingThreshold -> {
                    // Update basic fields
                    if (thresholdData.containsKey("thresholdName")) {
                        existingThreshold.setThresholdName((String) thresholdData.get("thresholdName"));
                    }
                    if (thresholdData.containsKey("thresholdType")) {
                        existingThreshold.setThresholdType(EdiGenerationThreshold.ThresholdType.valueOf((String) thresholdData.get("thresholdType")));
                    }
                    if (thresholdData.containsKey("maxClaims")) {
                        existingThreshold.setMaxClaims((Integer) thresholdData.get("maxClaims"));
                    }
                    if (thresholdData.containsKey("maxAmount")) {
                        Object amountObj = thresholdData.get("maxAmount");
                        if (amountObj instanceof Number) {
                            existingThreshold.setMaxAmount(new java.math.BigDecimal(amountObj.toString()));
                        }
                    }
                    if (thresholdData.containsKey("timeDuration")) {
                        String timeDuration = (String) thresholdData.get("timeDuration");
                        existingThreshold.setTimeDuration(timeDuration != null && !timeDuration.isEmpty() ?
                            EdiGenerationThreshold.TimeDuration.valueOf(timeDuration) : null);
                    }
                    if (thresholdData.containsKey("generationSchedule")) {
                        existingThreshold.setGenerationSchedule((String) thresholdData.get("generationSchedule"));
                    }
                    if (thresholdData.containsKey("isActive")) {
                        existingThreshold.setIsActive((Boolean) thresholdData.get("isActive"));
                    }

                    // Handle linked bucketing rule (can be nested object or just ID)
                    if (thresholdData.containsKey("linkedBucketingRule")) {
                        Object linkedRuleObj = thresholdData.get("linkedBucketingRule");
                        if (linkedRuleObj != null) {
                            UUID ruleId = null;
                            if (linkedRuleObj instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> ruleMap = (java.util.Map<String, Object>) linkedRuleObj;
                                Object idObj = ruleMap.get("id");
                                if (idObj == null) {
                                    idObj = ruleMap.get("ruleId");
                                }
                                if (idObj != null) {
                                    ruleId = UUID.fromString(idObj.toString());
                                }
                            } else if (linkedRuleObj instanceof String && !linkedRuleObj.toString().isEmpty()) {
                                ruleId = UUID.fromString(linkedRuleObj.toString());
                            }

                            if (ruleId != null) {
                                bucketingRuleRepository.findById(ruleId).ifPresent(existingThreshold::setLinkedBucketingRule);
                            } else {
                                existingThreshold.setLinkedBucketingRule(null);
                            }
                        } else {
                            existingThreshold.setLinkedBucketingRule(null);
                        }
                    }

                    EdiGenerationThreshold updatedThreshold = thresholdRepository.save(existingThreshold);
                    GenerationThresholdDTO dto = convertThresholdToDTO(updatedThreshold);
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/thresholds/{id}")
    public ResponseEntity<Void> deleteThreshold(@PathVariable UUID id) {
        log.info("DELETE /api/v1/config/thresholds/{} - Deleting threshold", id);

        if (thresholdRepository.existsById(id)) {
            thresholdRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Test threshold with simulated scenario
     */
    @PostMapping("/thresholds/test")
    public ResponseEntity<java.util.Map<String, Object>> testThreshold(@RequestBody java.util.Map<String, Object> request) {
        log.info("POST /api/v1/config/thresholds/test - Testing threshold");

        // Extract threshold data and scenario from request
        // For now, return a simple mock response
        // TODO: Implement actual threshold evaluation logic

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("wouldTrigger", true);
        result.put("reason", "Threshold conditions met");
        result.put("details", Arrays.asList(
            "Claim Count: PASS",
            "Amount: PASS"
        ));

        return ResponseEntity.ok(result);
    }

    /**
     * Get analytics for a specific threshold
     */
    @GetMapping("/thresholds/{id}/analytics")
    public ResponseEntity<java.util.Map<String, Object>> getThresholdAnalytics(@PathVariable UUID id) {
        log.info("GET /api/v1/config/thresholds/{}/analytics - Retrieving threshold analytics", id);

        if (!thresholdRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        // TODO: Implement actual analytics calculation from database
        // For now, return mock analytics data

        java.util.Map<String, Object> analytics = new java.util.HashMap<>();
        analytics.put("thresholdId", id.toString());
        analytics.put("triggerCount", 42);
        analytics.put("averageClaimsAtTrigger", 85.5);
        analytics.put("averageAmountAtTrigger", 42500.0);
        analytics.put("averageTimeToTrigger", 18.5);
        analytics.put("failureRate", 2.5);
        analytics.put("lastTriggered", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(analytics);
    }

    /**
     * Get buckets affected by a threshold configuration
     */
    @PostMapping("/thresholds/affected-buckets")
    public ResponseEntity<List<java.util.Map<String, Object>>> getAffectedBuckets(
            @RequestBody java.util.Map<String, Object> thresholdData) {
        log.info("POST /api/v1/config/thresholds/affected-buckets - Getting affected buckets");

        // TODO: Implement actual bucket filtering logic
        // For now, return empty list

        List<java.util.Map<String, Object>> affectedBuckets = new java.util.ArrayList<>();
        return ResponseEntity.ok(affectedBuckets);
    }

    /**
     * Bulk update threshold status (activate/deactivate)
     */
    @PostMapping("/thresholds/bulk-update-status")
    public ResponseEntity<Void> bulkUpdateThresholdStatus(@RequestBody java.util.Map<String, Object> request) {
        log.info("POST /api/v1/config/thresholds/bulk-update-status - Bulk updating threshold status");

        @SuppressWarnings("unchecked")
        List<String> thresholdIds = (List<String>) request.get("thresholdIds");
        Boolean isActive = (Boolean) request.get("isActive");

        if (thresholdIds == null || isActive == null) {
            return ResponseEntity.badRequest().build();
        }

        // Update all specified thresholds
        for (String idStr : thresholdIds) {
            try {
                UUID id = UUID.fromString(idStr);
                thresholdRepository.findById(id).ifPresent(threshold -> {
                    threshold.setIsActive(isActive);
                    thresholdRepository.save(threshold);
                });
            } catch (IllegalArgumentException e) {
                log.error("Invalid UUID: {}", idStr);
            }
        }

        return ResponseEntity.ok().build();
    }

    // ==================== Commit Criteria ====================

    @GetMapping("/commit-criteria")
    public ResponseEntity<List<CommitCriteriaDTO>> getAllCommitCriteria() {
        log.debug("GET /api/v1/config/commit-criteria - Retrieving all commit criteria");
        List<EdiCommitCriteria> criteria = commitCriteriaRepository.findAll();
        List<CommitCriteriaDTO> dtos = criteria.stream()
                .map(this::convertCommitCriteriaToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/commit-criteria/rule/{ruleId}")
    public ResponseEntity<CommitCriteriaDTO> getCommitCriteriaByRule(@PathVariable UUID ruleId) {
        log.debug("GET /api/v1/config/commit-criteria/rule/{} - Retrieving criteria", ruleId);

        return configurationService.getBucketingRuleById(ruleId)
                .flatMap(configurationService::getCommitCriteriaForRule)
                .map(this::convertCommitCriteriaToDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/commit-criteria")
    public ResponseEntity<CommitCriteriaDTO> createCommitCriteria(
            @Valid @RequestBody java.util.Map<String, Object> criteriaData) {
        log.info("POST /api/v1/config/commit-criteria - Creating criteria");

        EdiCommitCriteria criteria = new EdiCommitCriteria();

        // Map fields from request
        if (criteriaData.containsKey("criteriaName")) {
            criteria.setCriteriaName((String) criteriaData.get("criteriaName"));
        }
        if (criteriaData.containsKey("commitMode")) {
            criteria.setCommitMode(EdiCommitCriteria.CommitMode.valueOf((String) criteriaData.get("commitMode")));
        }
        if (criteriaData.containsKey("autoCommitThreshold")) {
            criteria.setAutoCommitThreshold((Integer) criteriaData.get("autoCommitThreshold"));
        }
        if (criteriaData.containsKey("manualApprovalThreshold")) {
            criteria.setManualApprovalThreshold((Integer) criteriaData.get("manualApprovalThreshold"));
        }
        // Support frontend field names for hybrid mode
        if (criteriaData.containsKey("approvalClaimCountThreshold")) {
            criteria.setManualApprovalThreshold((Integer) criteriaData.get("approvalClaimCountThreshold"));
        }
        if (criteriaData.containsKey("approvalAmountThreshold")) {
            Object amountObj = criteriaData.get("approvalAmountThreshold");
            if (amountObj instanceof Double) {
                criteria.setAutoCommitThreshold(((Double) amountObj).intValue());
            } else if (amountObj instanceof Integer) {
                criteria.setAutoCommitThreshold((Integer) amountObj);
            }
        }
        if (criteriaData.containsKey("approvalRequiredRoles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) criteriaData.get("approvalRequiredRoles");
            criteria.setApprovalRequiredRoles(roles.toArray(new String[0]));
        }
        if (criteriaData.containsKey("isActive")) {
            criteria.setIsActive((Boolean) criteriaData.get("isActive"));
        }

        // Handle linked bucketing rule
        if (criteriaData.containsKey("linkedBucketingRuleId") && criteriaData.get("linkedBucketingRuleId") != null) {
            String ruleIdStr = criteriaData.get("linkedBucketingRuleId").toString();
            if (!ruleIdStr.isEmpty()) {
                UUID ruleId = UUID.fromString(ruleIdStr);
                bucketingRuleRepository.findById(ruleId).ifPresent(criteria::setLinkedBucketingRule);
            }
        }

        EdiCommitCriteria savedCriteria = commitCriteriaRepository.save(criteria);
        CommitCriteriaDTO dto = convertCommitCriteriaToDTO(savedCriteria);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/commit-criteria/{id}")
    public ResponseEntity<CommitCriteriaDTO> updateCommitCriteria(
            @PathVariable UUID id, @Valid @RequestBody java.util.Map<String, Object> criteriaData) {
        log.info("PUT /api/v1/config/commit-criteria/{} - Updating criteria", id);

        return commitCriteriaRepository.findById(id)
                .map(existingCriteria -> {
                    // Update fields from request
                    if (criteriaData.containsKey("criteriaName")) {
                        existingCriteria.setCriteriaName((String) criteriaData.get("criteriaName"));
                    }
                    if (criteriaData.containsKey("commitMode")) {
                        existingCriteria.setCommitMode(EdiCommitCriteria.CommitMode.valueOf((String) criteriaData.get("commitMode")));
                    }
                    if (criteriaData.containsKey("autoCommitThreshold")) {
                        existingCriteria.setAutoCommitThreshold((Integer) criteriaData.get("autoCommitThreshold"));
                    }
                    if (criteriaData.containsKey("manualApprovalThreshold")) {
                        existingCriteria.setManualApprovalThreshold((Integer) criteriaData.get("manualApprovalThreshold"));
                    }
                    // Support frontend field names for hybrid mode
                    if (criteriaData.containsKey("approvalClaimCountThreshold")) {
                        existingCriteria.setManualApprovalThreshold((Integer) criteriaData.get("approvalClaimCountThreshold"));
                    }
                    if (criteriaData.containsKey("approvalAmountThreshold")) {
                        Object amountObj = criteriaData.get("approvalAmountThreshold");
                        if (amountObj instanceof Double) {
                            existingCriteria.setAutoCommitThreshold(((Double) amountObj).intValue());
                        } else if (amountObj instanceof Integer) {
                            existingCriteria.setAutoCommitThreshold((Integer) amountObj);
                        }
                    }
                    if (criteriaData.containsKey("approvalRequiredRoles")) {
                        @SuppressWarnings("unchecked")
                        List<String> roles = (List<String>) criteriaData.get("approvalRequiredRoles");
                        existingCriteria.setApprovalRequiredRoles(roles.toArray(new String[0]));
                    }
                    if (criteriaData.containsKey("isActive")) {
                        existingCriteria.setIsActive((Boolean) criteriaData.get("isActive"));
                    }

                    // Handle linked bucketing rule
                    if (criteriaData.containsKey("linkedBucketingRuleId")) {
                        Object ruleIdObj = criteriaData.get("linkedBucketingRuleId");
                        if (ruleIdObj != null && !ruleIdObj.toString().isEmpty()) {
                            UUID ruleId = UUID.fromString(ruleIdObj.toString());
                            bucketingRuleRepository.findById(ruleId).ifPresent(existingCriteria::setLinkedBucketingRule);
                        } else {
                            existingCriteria.setLinkedBucketingRule(null);
                        }
                    }

                    EdiCommitCriteria updatedCriteria = commitCriteriaRepository.save(existingCriteria);
                    CommitCriteriaDTO dto = convertCommitCriteriaToDTO(updatedCriteria);
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/commit-criteria/{id}")
    public ResponseEntity<Void> deleteCommitCriteria(@PathVariable UUID id) {
        log.info("DELETE /api/v1/config/commit-criteria/{} - Deleting commit criteria", id);

        if (commitCriteriaRepository.existsById(id)) {
            commitCriteriaRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ==================== File Naming Templates ====================

    @GetMapping("/templates")
    public ResponseEntity<List<EdiFileNamingTemplate>> getAllTemplates() {
        log.debug("GET /api/v1/config/templates - Retrieving all templates");
        List<EdiFileNamingTemplate> templates = templateRepository.findAll();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/templates/default")
    public ResponseEntity<EdiFileNamingTemplate> getDefaultTemplate() {
        log.debug("GET /api/v1/config/templates/default - Retrieving default template");
        return configurationService.getDefaultFileNamingTemplate()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/templates")
    public ResponseEntity<EdiFileNamingTemplate> createTemplate(
            @Valid @RequestBody EdiFileNamingTemplate template) {
        log.info("POST /api/v1/config/templates - Creating template: {}",
                template.getTemplateName());
        EdiFileNamingTemplate savedTemplate = templateRepository.save(template);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTemplate);
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<EdiFileNamingTemplate> updateTemplate(
            @PathVariable UUID id, @Valid @RequestBody EdiFileNamingTemplate template) {
        log.info("PUT /api/v1/config/templates/{} - Updating template", id);

        return templateRepository.findById(id)
                .map(existingTemplate -> {
                    template.setTemplateId(id);
                    EdiFileNamingTemplate updatedTemplate = templateRepository.save(template);
                    return ResponseEntity.ok(updatedTemplate);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Insurance Plans ====================

    @GetMapping("/insurance-plans")
    public ResponseEntity<List<InsurancePlan>> getAllInsurancePlans() {
        log.debug("GET /api/v1/config/insurance-plans - Retrieving all plans");
        List<InsurancePlan> plans = insurancePlanRepository.findAll();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/insurance-plans/bin/{binNumber}")
    public ResponseEntity<List<InsurancePlan>> getPlansByBin(@PathVariable String binNumber) {
        log.debug("GET /api/v1/config/insurance-plans/bin/{} - Retrieving plans", binNumber);
        List<InsurancePlan> plans = insurancePlanRepository.findByBinNumber(binNumber);
        return ResponseEntity.ok(plans);
    }

    @PostMapping("/insurance-plans")
    public ResponseEntity<InsurancePlan> createInsurancePlan(
            @Valid @RequestBody InsurancePlan plan) {
        log.info("POST /api/v1/config/insurance-plans - Creating plan: {}", plan.getPlanName());
        InsurancePlan savedPlan = insurancePlanRepository.save(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPlan);
    }

    // ==================== Configuration Validation ====================

    @GetMapping("/validate")
    public ResponseEntity<ConfigurationService.ConfigurationValidationResult> validateConfiguration() {
        log.info("GET /api/v1/config/validate - Validating system configuration");
        ConfigurationService.ConfigurationValidationResult result =
                configurationService.validateConfiguration();

        if (result.isValid()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }
}
