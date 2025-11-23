package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.CheckPaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CheckPaymentConfig entity.
 * Manages system-wide configuration parameters for check payment operations.
 */
@Repository
public interface CheckPaymentConfigRepository extends JpaRepository<CheckPaymentConfig, String> {

    /**
     * Finds configuration by key.
     *
     * @param configKey The configuration key
     * @return Optional containing config if found
     */
    Optional<CheckPaymentConfig> findByConfigKey(String configKey);

    /**
     * Finds active configuration by key.
     * Only returns if configuration is active.
     *
     * @param configKey The configuration key
     * @return Optional containing active config if found
     */
    Optional<CheckPaymentConfig> findByConfigKeyAndIsActiveTrue(String configKey);

    /**
     * Finds all active configurations.
     *
     * @return List of active configurations
     */
    List<CheckPaymentConfig> findByIsActiveTrueOrderByConfigKeyAsc();

    /**
     * Finds all configurations ordered by key.
     *
     * @return List of all configurations
     */
    List<CheckPaymentConfig> findAllByOrderByConfigKeyAsc();

    /**
     * Finds configurations by value type.
     *
     * @param valueType The configuration value type
     * @return List of configurations with given type
     */
    List<CheckPaymentConfig> findByValueTypeAndIsActiveTrue(CheckPaymentConfig.ConfigValueType valueType);

    /**
     * Checks if a configuration key exists.
     *
     * @param configKey The configuration key
     * @return true if key exists, false otherwise
     */
    boolean existsByConfigKey(String configKey);

    /**
     * Gets integer configuration value by key.
     * Returns null if not found or not active.
     *
     * @param key Configuration key
     * @return Configuration value as integer, or null
     */
    @Query("SELECT c.configValue FROM CheckPaymentConfig c WHERE c.configKey = :key " +
           "AND c.isActive = true AND c.valueType = 'INTEGER'")
    Optional<String> getIntegerValue(@Param("key") String key);

    /**
     * Gets boolean configuration value by key.
     * Returns null if not found or not active.
     *
     * @param key Configuration key
     * @return Configuration value as string (to be parsed as boolean), or null
     */
    @Query("SELECT c.configValue FROM CheckPaymentConfig c WHERE c.configKey = :key " +
           "AND c.isActive = true AND c.valueType = 'BOOLEAN'")
    Optional<String> getBooleanValue(@Param("key") String key);

    /**
     * Gets string configuration value by key.
     * Returns null if not found or not active.
     *
     * @param key Configuration key
     * @return Configuration value, or null
     */
    @Query("SELECT c.configValue FROM CheckPaymentConfig c WHERE c.configKey = :key " +
           "AND c.isActive = true")
    Optional<String> getStringValue(@Param("key") String key);

    /**
     * Gets email configuration value by key.
     * Returns null if not found or not active.
     *
     * @param key Configuration key
     * @return Configuration value (comma-separated emails), or null
     */
    @Query("SELECT c.configValue FROM CheckPaymentConfig c WHERE c.configKey = :key " +
           "AND c.isActive = true AND c.valueType = 'EMAIL'")
    Optional<String> getEmailValue(@Param("key") String key);

    /**
     * Helper method to get void time limit hours config.
     *
     * @return Void time limit in hours, or null if not configured
     */
    default Integer getVoidTimeLimitHours() {
        return findByConfigKeyAndIsActiveTrue(CheckPaymentConfig.Keys.VOID_TIME_LIMIT_HOURS)
                .map(CheckPaymentConfig::getIntValue)
                .orElse(72); // Default 72 hours
    }

    /**
     * Helper method to get low stock alert threshold.
     *
     * @return Low stock alert threshold, or null if not configured
     */
    default Integer getLowStockAlertThreshold() {
        return findByConfigKeyAndIsActiveTrue(CheckPaymentConfig.Keys.LOW_STOCK_ALERT_THRESHOLD)
                .map(CheckPaymentConfig::getIntValue)
                .orElse(50); // Default 50 checks
    }

    /**
     * Helper method to get low stock alert emails.
     *
     * @return List of email addresses, or empty list if not configured
     */
    default List<String> getLowStockAlertEmails() {
        return findByConfigKeyAndIsActiveTrue(CheckPaymentConfig.Keys.LOW_STOCK_ALERT_EMAILS)
                .map(CheckPaymentConfig::getEmailList)
                .orElse(List.of());
    }

    /**
     * Helper method to get default check range size.
     *
     * @return Default check range size, or 100 if not configured
     */
    default Integer getDefaultCheckRangeSize() {
        return findByConfigKeyAndIsActiveTrue(CheckPaymentConfig.Keys.DEFAULT_CHECK_RANGE_SIZE)
                .map(CheckPaymentConfig::getIntValue)
                .orElse(100); // Default 100 checks
    }

    /**
     * Helper method to check if acknowledgment is required before EDI generation.
     * Uses direct query to avoid entity mapping issues with config values.
     *
     * @return true if acknowledgment required, false otherwise
     */
    default Boolean requireAcknowledgmentBeforeEdi() {
        // Use the @Query method that filters by valueType='BOOLEAN' for safety
        return getBooleanValue(CheckPaymentConfig.Keys.REQUIRE_ACKNOWLEDGMENT_BEFORE_EDI)
                .map(value -> {
                    String normalized = value.trim().toLowerCase();
                    return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes");
                })
                .orElse(false); // Default: not required
    }

    /**
     * Helper method to get authorized roles for voiding checks.
     *
     * @return List of authorized role names
     */
    default List<String> getVoidAuthorizedRoles() {
        return findByConfigKeyAndIsActiveTrue(CheckPaymentConfig.Keys.VOID_AUTHORIZED_ROLES)
                .map(CheckPaymentConfig::getStringList)
                .orElse(List.of("FINANCIAL_ADMIN"));
    }
}
