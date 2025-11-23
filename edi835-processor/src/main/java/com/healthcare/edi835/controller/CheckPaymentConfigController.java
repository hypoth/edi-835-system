package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.CheckPaymentConfig;
import com.healthcare.edi835.model.dto.CheckPaymentConfigDTO;
import com.healthcare.edi835.model.dto.UpdateCheckPaymentConfigRequest;
import com.healthcare.edi835.repository.CheckPaymentConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for managing check payment configurations.
 * Provides CRUD operations for system-wide check payment settings.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/check-payment-config")
@RequiredArgsConstructor
public class CheckPaymentConfigController {

    private final CheckPaymentConfigRepository configRepository;

    /**
     * Display name mapping for config keys.
     */
    private static final Map<String, String> CONFIG_DISPLAY_NAMES = Map.of(
            CheckPaymentConfig.Keys.VOID_TIME_LIMIT_HOURS, "Void Time Limit (Hours)",
            CheckPaymentConfig.Keys.LOW_STOCK_ALERT_THRESHOLD, "Low Stock Alert Threshold",
            CheckPaymentConfig.Keys.LOW_STOCK_ALERT_EMAILS, "Low Stock Alert Emails",
            CheckPaymentConfig.Keys.VOID_AUTHORIZED_ROLES, "Void Authorized Roles",
            CheckPaymentConfig.Keys.DEFAULT_CHECK_RANGE_SIZE, "Default Check Range Size",
            CheckPaymentConfig.Keys.REQUIRE_ACKNOWLEDGMENT_BEFORE_EDI, "Require Acknowledgment Before EDI"
    );

    /**
     * Gets all check payment configurations.
     *
     * @return List of all configurations
     */
    @GetMapping
    public ResponseEntity<List<CheckPaymentConfigDTO>> getAllConfigs() {
        log.info("Fetching all check payment configurations");
        List<CheckPaymentConfig> configs = configRepository.findAllByOrderByConfigKeyAsc();
        List<CheckPaymentConfigDTO> dtos = configs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Gets only active check payment configurations.
     *
     * @return List of active configurations
     */
    @GetMapping("/active")
    public ResponseEntity<List<CheckPaymentConfigDTO>> getActiveConfigs() {
        log.info("Fetching active check payment configurations");
        List<CheckPaymentConfig> configs = configRepository.findByIsActiveTrueOrderByConfigKeyAsc();
        List<CheckPaymentConfigDTO> dtos = configs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Gets a specific configuration by key.
     *
     * @param configKey The configuration key
     * @return Configuration if found
     */
    @GetMapping("/key/{configKey}")
    public ResponseEntity<CheckPaymentConfigDTO> getConfigByKey(@PathVariable String configKey) {
        log.info("Fetching check payment configuration: {}", configKey);
        return configRepository.findByConfigKey(configKey)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Gets a specific configuration by ID.
     *
     * @param id The configuration ID
     * @return Configuration if found
     */
    @GetMapping("/{id}")
    public ResponseEntity<CheckPaymentConfigDTO> getConfigById(@PathVariable String id) {
        log.info("Fetching check payment configuration by ID: {}", id);
        return configRepository.findById(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Updates a configuration by ID.
     *
     * @param id      Configuration ID
     * @param request Update request
     * @return Updated configuration
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateConfig(
            @PathVariable String id,
            @RequestBody UpdateCheckPaymentConfigRequest request) {
        log.info("Updating check payment configuration {}: value={}", id, request.getConfigValue());

        return configRepository.findById(id)
                .map(config -> {
                    // Validate new value against type
                    String newValue = request.getConfigValue();
                    if (newValue != null) {
                        config.setConfigValue(newValue);
                        if (!config.isValueValid()) {
                            return ResponseEntity.badRequest().body(Map.of(
                                    "error", "Invalid value for type " + config.getValueType(),
                                    "configKey", config.getConfigKey(),
                                    "valueType", config.getValueType().name()
                            ));
                        }
                    }

                    if (request.getDescription() != null) {
                        config.setDescription(request.getDescription());
                    }
                    if (request.getIsActive() != null) {
                        config.setIsActive(request.getIsActive());
                    }
                    config.setUpdatedBy(request.getUpdatedBy() != null ? request.getUpdatedBy() : "admin");
                    config.setUpdatedAt(LocalDateTime.now());

                    CheckPaymentConfig saved = configRepository.save(config);
                    log.info("Updated check payment configuration {}: {}", config.getConfigKey(), newValue);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Updates a configuration by key.
     *
     * @param configKey Configuration key
     * @param request   Update request
     * @return Updated configuration
     */
    @PutMapping("/key/{configKey}")
    public ResponseEntity<?> updateConfigByKey(
            @PathVariable String configKey,
            @RequestBody UpdateCheckPaymentConfigRequest request) {
        log.info("Updating check payment configuration by key {}: value={}", configKey, request.getConfigValue());

        return configRepository.findByConfigKey(configKey)
                .map(config -> {
                    // Validate new value against type
                    String newValue = request.getConfigValue();
                    if (newValue != null) {
                        config.setConfigValue(newValue);
                        if (!config.isValueValid()) {
                            return ResponseEntity.badRequest().body(Map.of(
                                    "error", "Invalid value for type " + config.getValueType(),
                                    "configKey", config.getConfigKey(),
                                    "valueType", config.getValueType().name()
                            ));
                        }
                    }

                    if (request.getDescription() != null) {
                        config.setDescription(request.getDescription());
                    }
                    if (request.getIsActive() != null) {
                        config.setIsActive(request.getIsActive());
                    }
                    config.setUpdatedBy(request.getUpdatedBy() != null ? request.getUpdatedBy() : "admin");
                    config.setUpdatedAt(LocalDateTime.now());

                    CheckPaymentConfig saved = configRepository.save(config);
                    log.info("Updated check payment configuration {}: {}", config.getConfigKey(), newValue);
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new configuration (admin use only).
     *
     * @param dto New configuration data
     * @return Created configuration
     */
    @PostMapping
    public ResponseEntity<?> createConfig(@RequestBody CheckPaymentConfigDTO dto) {
        log.info("Creating check payment configuration: {}", dto.getConfigKey());

        // Check if key already exists
        if (configRepository.existsByConfigKey(dto.getConfigKey())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Configuration key already exists",
                    "configKey", dto.getConfigKey()
            ));
        }

        CheckPaymentConfig config = CheckPaymentConfig.builder()
                .configKey(dto.getConfigKey())
                .configValue(dto.getConfigValue())
                .description(dto.getDescription())
                .valueType(CheckPaymentConfig.ConfigValueType.valueOf(dto.getValueType()))
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .createdBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : "admin")
                .build();

        // Validate value
        if (!config.isValueValid()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid value for type " + config.getValueType(),
                    "configKey", config.getConfigKey(),
                    "valueType", config.getValueType().name()
            ));
        }

        CheckPaymentConfig saved = configRepository.save(config);
        log.info("Created check payment configuration: {}", saved.getConfigKey());
        return ResponseEntity.ok(toDTO(saved));
    }

    /**
     * Toggles the active state of a configuration.
     *
     * @param id Configuration ID
     * @return Updated configuration
     */
    @PostMapping("/{id}/toggle-active")
    public ResponseEntity<CheckPaymentConfigDTO> toggleActive(@PathVariable String id) {
        log.info("Toggling active state for check payment configuration {}", id);

        return configRepository.findById(id)
                .map(config -> {
                    config.setIsActive(!config.getIsActive());
                    config.setUpdatedAt(LocalDateTime.now());
                    CheckPaymentConfig saved = configRepository.save(config);
                    log.info("Toggled configuration {} active state to {}", config.getConfigKey(), saved.getIsActive());
                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Gets configurations by value type.
     *
     * @param valueType The value type (STRING, INTEGER, BOOLEAN, EMAIL)
     * @return List of configurations with matching type
     */
    @GetMapping("/type/{valueType}")
    public ResponseEntity<List<CheckPaymentConfigDTO>> getConfigsByType(@PathVariable String valueType) {
        log.info("Fetching check payment configurations by type: {}", valueType);

        try {
            CheckPaymentConfig.ConfigValueType type = CheckPaymentConfig.ConfigValueType.valueOf(valueType.toUpperCase());
            List<CheckPaymentConfig> configs = configRepository.findByValueTypeAndIsActiveTrue(type);
            List<CheckPaymentConfigDTO> dtos = configs.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Converts entity to DTO.
     */
    private CheckPaymentConfigDTO toDTO(CheckPaymentConfig config) {
        return CheckPaymentConfigDTO.builder()
                .id(config.getId())
                .configKey(config.getConfigKey())
                .configValue(config.getConfigValue())
                .description(config.getDescription())
                .valueType(config.getValueType() != null ? config.getValueType().name() : null)
                .isActive(config.getIsActive())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .createdBy(config.getCreatedBy())
                .updatedBy(config.getUpdatedBy())
                .displayName(CONFIG_DISPLAY_NAMES.getOrDefault(config.getConfigKey(), config.getConfigKey()))
                .isValid(config.isValueValid())
                .build();
    }
}
