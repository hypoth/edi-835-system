package com.healthcare.edi835.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * SQLite-specific data source configuration.
 *
 * <p>This configuration disables Hibernate's schema validation and metadata
 * extraction for SQLite, which has limited JDBC metadata support.</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "changefeed.sqlite.enabled", havingValue = "true", matchIfMissing = false)
public class SQLiteDataSourceConfig {

    /**
     * Customizes Hibernate properties to work with SQLite.
     * Disables metadata access that causes NoSuchElementException.
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return (Map<String, Object> hibernateProperties) -> {
            log.info("Customizing Hibernate properties for SQLite");

            // Disable all schema management
            hibernateProperties.put("hibernate.hbm2ddl.auto", "none");

            // Disable JDBC metadata access (this is the key fix)
            hibernateProperties.put("hibernate.boot.allow_jdbc_metadata_access", "false");

            // Disable schema validation
            hibernateProperties.put("hibernate.validator.apply_to_ddl", "false");

            // Other optimizations for SQLite
            hibernateProperties.put("hibernate.temp.use_jdbc_metadata_defaults", "false");
            hibernateProperties.put("hibernate.globally_quoted_identifiers", "true");
            hibernateProperties.put("hibernate.id.new_generator_mappings", "false");

            // Configure UUID to use VARCHAR/CHAR mapping for SQLite
            // This tells Hibernate to store UUIDs as strings in the database
            hibernateProperties.put("hibernate.type.preferred_uuid_jdbc_type", "CHAR");

            log.info("Hibernate properties customized for SQLite compatibility");
        };
    }
}
