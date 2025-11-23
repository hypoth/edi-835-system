package com.healthcare.edi835.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for check reservation transaction behavior.
 *
 * <p>This configuration controls whether check reservations use a separate transaction
 * (REQUIRES_NEW) or participate in the calling transaction.</p>
 *
 * <h3>Database-Specific Behavior:</h3>
 * <ul>
 *   <li><b>PostgreSQL:</b> Use separate transactions (use-separate-transaction=true)
 *       to ensure check numbers are not lost if the outer transaction fails.
 *       Requires adequate connection pool size (minimum 3).</li>
 *   <li><b>SQLite:</b> Use single transaction (use-separate-transaction=false)
 *       because SQLite only supports one writer at a time and has limited
 *       connection pooling. REQUIRES_NEW would cause connection pool exhaustion.</li>
 * </ul>
 *
 * <h3>Trade-offs:</h3>
 * <ul>
 *   <li><b>Separate Transaction (true):</b> Check reservation commits immediately,
 *       requires compensation logic if outer transaction fails, but guarantees
 *       no lost check numbers.</li>
 *   <li><b>Single Transaction (false):</b> Check reservation rolls back with
 *       outer transaction, simpler but check numbers are only "reserved" temporarily
 *       until the full transaction commits.</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "check-reservation")
@Getter
@Setter
public class CheckReservationTransactionConfig {

    /**
     * Whether to use a separate transaction (REQUIRES_NEW) for check reservations.
     *
     * <p>Default: false (safer for SQLite)</p>
     *
     * <p>Set to true for PostgreSQL to enable compensation-based transaction safety.</p>
     */
    private boolean useSeparateTransaction = false;

    /**
     * Whether compensation logic should be enabled when using separate transactions.
     * Only applicable when useSeparateTransaction=true.
     *
     * <p>Default: true</p>
     */
    private boolean enableCompensation = true;
}
