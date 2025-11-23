package com.healthcare.edi835.config;

import com.healthcare.edi835.entity.Payer;
import com.healthcare.edi835.service.EncryptionService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Integration SFTP Configuration.
 *
 * Provides SFTP session factory and file transfer capabilities for EDI file delivery.
 * Uses JSch library for SFTP protocol implementation.
 *
 * Features:
 * - Connection pooling with caching session factory
 * - Per-payer SFTP configuration with factory caching
 * - Configurable timeouts and connection settings
 * - Support for password-based authentication with encryption
 *
 * <p>Session factories are cached per payer to avoid resource exhaustion.
 * Each payer gets a single CachingSessionFactory that is reused across
 * multiple file deliveries.</p>
 *
 * @see FileDeliveryService
 */
@Slf4j
@Configuration
public class SftpConfig {

    private final EncryptionService encryptionService;

    /**
     * Cache of SFTP session factories per payer ID.
     * Prevents resource exhaustion by reusing factories instead of creating new ones.
     */
    private final Map<String, CachingSessionFactory<SftpClient.DirEntry>> sessionFactoryCache =
            new ConcurrentHashMap<>();

    @Value("${file-delivery.sftp.connection-timeout-ms:30000}")
    private int connectionTimeoutMs;

    @Value("${file-delivery.sftp.session-timeout-ms:300000}")
    private int sessionTimeoutMs;

    @Value("${file-delivery.sftp.pool-size:5}")
    private int poolSize;

    public SftpConfig(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    /**
     * Gets or creates a cached SFTP session factory for a specific payer.
     * This method is thread-safe and ensures only one factory exists per payer.
     *
     * @param payer the payer with SFTP configuration
     * @return SessionFactory configured for the payer's SFTP server (cached)
     * @throws IllegalArgumentException if payer SFTP configuration is incomplete
     */
    public SessionFactory<SftpClient.DirEntry> getOrCreateSessionFactory(Payer payer) {
        if (payer == null) {
            throw new IllegalArgumentException("Payer cannot be null");
        }

        validatePayerSftpConfig(payer);

        String cacheKey = buildCacheKey(payer);

        return sessionFactoryCache.computeIfAbsent(cacheKey, key -> {
            log.info("Creating new cached SFTP session factory for payer: {} ({}:{})",
                    payer.getPayerId(), payer.getSftpHost(), payer.getSftpPort());
            return createCachingSessionFactory(payer);
        });
    }

    /**
     * Builds a cache key for a payer's SFTP configuration.
     * Key includes host, port, and username to handle config changes.
     */
    private String buildCacheKey(Payer payer) {
        return String.format("%s_%s_%d_%s",
                payer.getPayerId(),
                payer.getSftpHost(),
                payer.getSftpPort() != null ? payer.getSftpPort() : 22,
                payer.getSftpUsername());
    }

    /**
     * Removes a payer's session factory from cache and destroys it.
     * Call this when payer SFTP configuration changes.
     *
     * @param payerId the payer ID
     */
    public void evictSessionFactory(String payerId) {
        sessionFactoryCache.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(payerId + "_")) {
                log.info("Evicting cached SFTP session factory for payer: {}", payerId);
                try {
                    entry.getValue().destroy();
                } catch (Exception e) {
                    log.warn("Error destroying session factory for payer {}: {}", payerId, e.getMessage());
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Cleans up all cached session factories on application shutdown.
     */
    @PreDestroy
    public void destroyAllSessionFactories() {
        log.info("Destroying {} cached SFTP session factories", sessionFactoryCache.size());
        sessionFactoryCache.forEach((key, factory) -> {
            try {
                factory.destroy();
                log.debug("Destroyed session factory: {}", key);
            } catch (Exception e) {
                log.warn("Error destroying session factory {}: {}", key, e.getMessage());
            }
        });
        sessionFactoryCache.clear();
    }

    /**
     * Creates a new CachingSessionFactory for a payer.
     * This is called internally by getOrCreateSessionFactory when cache miss occurs.
     *
     * @param payer the payer with SFTP configuration
     * @return CachingSessionFactory configured for the payer's SFTP server
     */
    private CachingSessionFactory<SftpClient.DirEntry> createCachingSessionFactory(Payer payer) {
        log.debug("Creating SFTP session factory for payer: {} ({}:{})",
                payer.getPayerId(), payer.getSftpHost(), payer.getSftpPort());

        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);

        // Connection settings
        factory.setHost(payer.getSftpHost());
        factory.setPort(payer.getSftpPort() != null ? payer.getSftpPort() : 22);
        factory.setUser(payer.getSftpUsername());

        // Authentication - password based (decrypt encrypted password)
        if (payer.getSftpPassword() != null && !payer.getSftpPassword().isEmpty()) {
            try {
                String decryptedPassword = encryptionService.decrypt(payer.getSftpPassword());
                factory.setPassword(decryptedPassword);
                log.debug("SFTP password decrypted successfully for payer: {}", payer.getPayerId());
            } catch (Exception e) {
                log.error("Failed to decrypt SFTP password for payer: {}. " +
                         "Password may not be encrypted or encryption configuration changed.",
                         payer.getPayerId(), e);
                throw new IllegalStateException("Failed to decrypt SFTP password for payer " +
                                              payer.getPayerId(), e);
            }
        }

        // Security settings
        factory.setAllowUnknownKeys(true); // In production, use known_hosts file

        // Timeout settings
        factory.setTimeout(connectionTimeoutMs);

        // Use caching to reuse connections - pool size from configuration
        CachingSessionFactory<SftpClient.DirEntry> cachingFactory =
                new CachingSessionFactory<>(factory, poolSize);

        cachingFactory.setSessionWaitTimeout(sessionTimeoutMs);
        cachingFactory.setTestSession(true); // Test connections before use

        log.info("SFTP session factory created for payer: {} at {}:{} (pool size: {})",
                payer.getPayerId(), payer.getSftpHost(), payer.getSftpPort(), poolSize);

        return cachingFactory;
    }

    /**
     * @deprecated Use {@link #getOrCreateSessionFactory(Payer)} instead to avoid resource exhaustion.
     * This method creates a new factory each time which can leak resources.
     */
    @Deprecated
    public SessionFactory<SftpClient.DirEntry> createSessionFactory(Payer payer) {
        log.warn("Using deprecated createSessionFactory() - use getOrCreateSessionFactory() instead");
        return getOrCreateSessionFactory(payer);
    }

    /**
     * Creates SFTP remote file template for file operations.
     *
     * @param sessionFactory the SFTP session factory
     * @return SftpRemoteFileTemplate for file upload/download operations
     */
    public SftpRemoteFileTemplate createRemoteFileTemplate(
            SessionFactory<SftpClient.DirEntry> sessionFactory) {

        SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(sessionFactory);
        template.setAutoCreateDirectory(false); // Don't create directories automatically
        template.setUseTemporaryFileName(false); // Write directly with final name

        return template;
    }

    /**
     * Validates that payer has complete SFTP configuration.
     *
     * @param payer the payer to validate
     * @throws IllegalArgumentException if configuration is incomplete
     */
    private void validatePayerSftpConfig(Payer payer) {
        if (payer.getSftpHost() == null || payer.getSftpHost().isEmpty()) {
            throw new IllegalArgumentException(
                    "Payer " + payer.getPayerId() + " missing SFTP host");
        }

        if (payer.getSftpPort() == null) {
            log.warn("Payer {} missing SFTP port, using default: 22", payer.getPayerId());
        }

        if (payer.getSftpUsername() == null || payer.getSftpUsername().isEmpty()) {
            throw new IllegalArgumentException(
                    "Payer " + payer.getPayerId() + " missing SFTP username");
        }

        if (payer.getSftpPath() == null || payer.getSftpPath().isEmpty()) {
            throw new IllegalArgumentException(
                    "Payer " + payer.getPayerId() + " missing SFTP path");
        }
    }

}
