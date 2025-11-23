package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CheckPaymentConfig entity - stores configurable parameters for check payment system.
 * Maps to 'check_payment_config' table.
 *
 * Supports multiple value types:
 * - STRING: Plain text values
 * - INTEGER: Numeric values (thresholds, limits)
 * - BOOLEAN: True/false flags
 * - EMAIL: Comma-separated email addresses
 *
 * Default configurations:
 * - void_time_limit_hours: Time window for voiding checks (default: 72 hours)
 * - low_stock_alert_threshold: Alert when reservations run low (default: 50 checks)
 * - low_stock_alert_emails: Recipients for alerts
 * - void_authorized_roles: Roles permitted to void checks
 * - default_check_range_size: Standard reservation size (default: 100)
 * - require_acknowledgment_before_edi: Whether acknowledgment blocks EDI generation
 */
@Entity
@Table(name = "check_payment_config", indexes = {
        @Index(name = "idx_check_config_key", columnList = "config_key", unique = true),
        @Index(name = "idx_check_config_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckPaymentConfig {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "config_key", unique = true, nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private ConfigValueType valueType;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // Audit fields
    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Configuration value types.
     */
    public enum ConfigValueType {
        STRING,   // Plain text
        INTEGER,  // Numeric values
        BOOLEAN,  // true/false
        EMAIL     // Comma-separated email addresses
    }

    /**
     * Standard configuration keys.
     */
    public static class Keys {
        public static final String VOID_TIME_LIMIT_HOURS = "void_time_limit_hours";
        public static final String LOW_STOCK_ALERT_THRESHOLD = "low_stock_alert_threshold";
        public static final String LOW_STOCK_ALERT_EMAILS = "low_stock_alert_emails";
        public static final String VOID_AUTHORIZED_ROLES = "void_authorized_roles";
        public static final String DEFAULT_CHECK_RANGE_SIZE = "default_check_range_size";
        public static final String REQUIRE_ACKNOWLEDGMENT_BEFORE_EDI = "require_acknowledgment_before_edi";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Gets config value as an integer.
     * Throws exception if value type is not INTEGER or value cannot be parsed.
     *
     * @return Integer value
     * @throws IllegalStateException if value type is not INTEGER
     * @throws NumberFormatException if value cannot be parsed
     */
    public Integer getIntValue() {
        if (valueType != ConfigValueType.INTEGER) {
            throw new IllegalStateException(
                    String.format("Config key '%s' is not of type INTEGER (actual: %s)", configKey, valueType));
        }
        try {
            return Integer.parseInt(configValue.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(
                    String.format("Invalid integer value for config key '%s': %s", configKey, configValue));
        }
    }

    /**
     * Gets config value as a boolean.
     * Accepts: "true", "false", "1", "0", "yes", "no" (case-insensitive).
     *
     * @return Boolean value
     * @throws IllegalStateException if value type is not BOOLEAN
     */
    public Boolean getBooleanValue() {
        if (valueType != ConfigValueType.BOOLEAN) {
            throw new IllegalStateException(
                    String.format("Config key '%s' is not of type BOOLEAN (actual: %s)", configKey, valueType));
        }
        String normalized = configValue.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
    }

    /**
     * Gets config value as a string.
     *
     * @return String value
     */
    public String getStringValue() {
        return configValue;
    }

    /**
     * Gets config value as a list of email addresses.
     * Splits on comma and trims whitespace.
     *
     * @return List of email addresses
     * @throws IllegalStateException if value type is not EMAIL
     */
    public List<String> getEmailList() {
        if (valueType != ConfigValueType.EMAIL) {
            throw new IllegalStateException(
                    String.format("Config key '%s' is not of type EMAIL (actual: %s)", configKey, valueType));
        }
        return Arrays.stream(configValue.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Gets config value as a list of strings (comma-separated).
     * Useful for role lists, category lists, etc.
     *
     * @return List of strings
     */
    public List<String> getStringList() {
        return Arrays.stream(configValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Updates the config value and marks as updated.
     *
     * @param newValue New configuration value
     * @param updatedBy User making the update
     */
    public void updateValue(String newValue, String updatedBy) {
        this.configValue = newValue;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Validates the config value against its declared type.
     *
     * @return true if value is valid for its type
     */
    public boolean isValueValid() {
        try {
            switch (valueType) {
                case INTEGER:
                    Integer.parseInt(configValue.trim());
                    return true;
                case BOOLEAN:
                    String normalized = configValue.trim().toLowerCase();
                    return normalized.equals("true") || normalized.equals("false") ||
                           normalized.equals("1") || normalized.equals("0") ||
                           normalized.equals("yes") || normalized.equals("no");
                case EMAIL:
                    // Basic validation - contains @ and no spaces
                    List<String> emails = getEmailList();
                    return emails.stream().allMatch(email -> email.contains("@") && !email.contains(" "));
                case STRING:
                    return true; // Strings always valid
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Factory method to create a new config entry.
     *
     * @param key Config key
     * @param value Config value
     * @param description Description
     * @param type Value type
     * @param createdBy User creating the config
     * @return CheckPaymentConfig instance
     */
    public static CheckPaymentConfig create(String key, String value, String description,
                                           ConfigValueType type, String createdBy) {
        return CheckPaymentConfig.builder()
                .configKey(key)
                .configValue(value)
                .description(description)
                .valueType(type)
                .isActive(true)
                .createdBy(createdBy)
                .build();
    }
}
