package com.healthcare.edi835.config;

import com.healthcare.edi835.entity.Payer;
import com.healthcare.edi835.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;

/**
 * Spring Integration SFTP Configuration.
 *
 * Provides SFTP session factory and file transfer capabilities for EDI file delivery.
 * Uses JSch library for SFTP protocol implementation.
 *
 * Features:
 * - Connection pooling with caching session factory
 * - Per-payer SFTP configuration
 * - Configurable timeouts and connection settings
 * - Support for password-based authentication with encryption
 *
 * @see FileDeliveryService
 */
@Slf4j
@Configuration
public class SftpConfig {

    private final EncryptionService encryptionService;

    @Value("${file-delivery.sftp.connection-timeout-ms:30000}")
    private int connectionTimeoutMs;

    @Value("${file-delivery.sftp.session-timeout-ms:300000}")
    private int sessionTimeoutMs;

    public SftpConfig(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    /**
     * Creates an SFTP session factory for a specific payer.
     *
     * @param payer the payer with SFTP configuration
     * @return SessionFactory configured for the payer's SFTP server
     * @throws IllegalArgumentException if payer SFTP configuration is incomplete
     */
    public SessionFactory<SftpClient.DirEntry> createSessionFactory(Payer payer) {
        if (payer == null) {
            throw new IllegalArgumentException("Payer cannot be null");
        }

        validatePayerSftpConfig(payer);

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

        // Use caching to reuse connections
        CachingSessionFactory<SftpClient.DirEntry> cachingFactory =
                new CachingSessionFactory<>(factory, 10); // Pool size: 10

        cachingFactory.setSessionWaitTimeout(sessionTimeoutMs);
        cachingFactory.setTestSession(true); // Test connections before use

        log.info("SFTP session factory created for payer: {} at {}:{}",
                payer.getPayerId(), payer.getSftpHost(), payer.getSftpPort());

        return cachingFactory;
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
