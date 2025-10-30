package com.healthcare.edi835.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for SQLite-based change feed processing.
 *
 * <p>This configuration is activated when the 'sqlite' profile is active
 * and enables scheduled polling for the SQLite change feed processor.</p>
 *
 * <p>Schema initialization is handled by Spring SQL Init
 * (spring.sql.init in application-sqlite.yml)</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "changefeed.sqlite.enabled", havingValue = "true", matchIfMissing = false)
@EnableScheduling
public class SQLiteChangeFeedConfig {

    public SQLiteChangeFeedConfig() {
        log.info("SQLite Change Feed Configuration initialized (schema managed by Spring SQL Init)");
    }
}
