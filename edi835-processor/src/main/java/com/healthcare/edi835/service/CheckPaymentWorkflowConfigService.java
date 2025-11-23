package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.CheckPaymentWorkflowConfig;
import com.healthcare.edi835.entity.EdiGenerationThreshold;
import com.healthcare.edi835.model.dto.CheckPaymentWorkflowConfigDTO;
import com.healthcare.edi835.model.dto.CheckPaymentWorkflowConfigRequest;
import com.healthcare.edi835.repository.CheckPaymentWorkflowConfigRepository;
import com.healthcare.edi835.repository.EdiGenerationThresholdRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing check payment workflow configurations.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Create and update workflow configurations</li>
 *   <li>Link configurations to thresholds/bucketing rules</li>
 *   <li>Retrieve workflow config for approval operations</li>
 *   <li>Validate workflow configuration consistency</li>
 * </ul>
 */
@Slf4j
@Service
public class CheckPaymentWorkflowConfigService {

    private final CheckPaymentWorkflowConfigRepository workflowConfigRepository;
    private final EdiGenerationThresholdRepository thresholdRepository;

    public CheckPaymentWorkflowConfigService(
            CheckPaymentWorkflowConfigRepository workflowConfigRepository,
            EdiGenerationThresholdRepository thresholdRepository) {
        this.workflowConfigRepository = workflowConfigRepository;
        this.thresholdRepository = thresholdRepository;
    }

    /**
     * Creates a new workflow configuration.
     *
     * @param request the configuration request
     * @return created configuration DTO
     */
    @Transactional
    public CheckPaymentWorkflowConfigDTO createWorkflowConfig(CheckPaymentWorkflowConfigRequest request) {
        log.info("Creating workflow configuration: {}", request.getConfigName());

        // Validate threshold exists
        UUID thresholdId = UUID.fromString(request.getLinkedThresholdId());
        EdiGenerationThreshold threshold = thresholdRepository.findById(thresholdId)
                .orElseThrow(() -> new IllegalArgumentException("Threshold not found: " + thresholdId));

        // Check if threshold already has configuration
        if (workflowConfigRepository.existsByThresholdId(thresholdId)) {
            throw new IllegalStateException("Threshold already has workflow configuration: " + thresholdId);
        }

        // Create configuration
        CheckPaymentWorkflowConfig config = CheckPaymentWorkflowConfig.builder()
                .configName(request.getConfigName())
                .workflowMode(CheckPaymentWorkflowConfig.WorkflowMode.valueOf(request.getWorkflowMode()))
                .assignmentMode(CheckPaymentWorkflowConfig.AssignmentMode.valueOf(request.getAssignmentMode()))
                .requireAcknowledgment(request.getRequireAcknowledgment())
                .linkedThreshold(threshold)
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .createdBy(request.getCreatedBy())
                .build();

        config = workflowConfigRepository.save(config);

        log.info("Workflow configuration created: ID={}, Mode={}, Assignment={}",
                config.getId(), config.getWorkflowMode(), config.getAssignmentMode());

        return toDTO(config);
    }

    /**
     * Updates an existing workflow configuration.
     *
     * @param id the configuration ID
     * @param request the update request
     * @return updated configuration DTO
     */
    @Transactional
    public CheckPaymentWorkflowConfigDTO updateWorkflowConfig(UUID id, CheckPaymentWorkflowConfigRequest request) {
        log.info("Updating workflow configuration: {}", id);

        CheckPaymentWorkflowConfig config = workflowConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow configuration not found: " + id));

        // Update fields
        config.setConfigName(request.getConfigName());
        config.setWorkflowMode(CheckPaymentWorkflowConfig.WorkflowMode.valueOf(request.getWorkflowMode()));
        config.setAssignmentMode(CheckPaymentWorkflowConfig.AssignmentMode.valueOf(request.getAssignmentMode()));
        config.setRequireAcknowledgment(request.getRequireAcknowledgment());
        config.setDescription(request.getDescription());
        config.setIsActive(request.getIsActive() != null ? request.getIsActive() : config.getIsActive());
        config.setUpdatedBy(request.getUpdatedBy());
        config.setUpdatedAt(LocalDateTime.now());

        config = workflowConfigRepository.save(config);

        log.info("Workflow configuration updated: ID={}, Mode={}, Assignment={}",
                config.getId(), config.getWorkflowMode(), config.getAssignmentMode());

        return toDTO(config);
    }

    /**
     * Gets workflow configuration by ID.
     *
     * @param id the configuration ID
     * @return configuration DTO
     */
    public CheckPaymentWorkflowConfigDTO getWorkflowConfig(UUID id) {
        CheckPaymentWorkflowConfig config = workflowConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow configuration not found: " + id));
        return toDTO(config);
    }

    /**
     * Gets workflow configuration by threshold ID.
     *
     * @param thresholdId the threshold ID
     * @return Optional configuration DTO
     */
    public Optional<CheckPaymentWorkflowConfigDTO> getWorkflowConfigByThreshold(UUID thresholdId) {
        return workflowConfigRepository.findByThresholdId(thresholdId)
                .map(this::toDTO);
    }

    /**
     * Gets all active workflow configurations.
     *
     * @return List of active configurations
     */
    public List<CheckPaymentWorkflowConfigDTO> getAllActiveConfigurations() {
        return workflowConfigRepository.findByIsActiveTrue().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gets all workflow configurations.
     *
     * @return List of all configurations
     */
    public List<CheckPaymentWorkflowConfigDTO> getAllConfigurations() {
        return workflowConfigRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a workflow configuration.
     *
     * @param id the configuration ID
     */
    @Transactional
    public void deleteWorkflowConfig(UUID id) {
        log.info("Deleting workflow configuration: {}", id);

        if (!workflowConfigRepository.existsById(id)) {
            throw new IllegalArgumentException("Workflow configuration not found: " + id);
        }

        workflowConfigRepository.deleteById(id);
        log.info("Workflow configuration deleted: {}", id);
    }

    /**
     * Converts entity to DTO.
     */
    private CheckPaymentWorkflowConfigDTO toDTO(CheckPaymentWorkflowConfig config) {
        return CheckPaymentWorkflowConfigDTO.builder()
                .id(config.getId().toString())
                .configName(config.getConfigName())
                .workflowMode(config.getWorkflowMode().name())
                .assignmentMode(config.getAssignmentMode().name())
                .requireAcknowledgment(config.getRequireAcknowledgment())
                .linkedThresholdId(config.getLinkedThreshold() != null ?
                        config.getLinkedThreshold().getId().toString() : null)
                .linkedThresholdName(config.getLinkedThreshold() != null ?
                        config.getLinkedThreshold().getThresholdName() : null)
                .linkedBucketingRuleName(config.getLinkedThreshold() != null &&
                        config.getLinkedThreshold().getLinkedBucketingRule() != null ?
                        config.getLinkedThreshold().getLinkedBucketingRule().getRuleName() : null)
                .description(config.getDescription())
                .isActive(config.getIsActive())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .createdBy(config.getCreatedBy())
                .updatedBy(config.getUpdatedBy())
                .build();
    }
}
