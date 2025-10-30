package com.healthcare.edi835.service;

import com.healthcare.edi835.config.SftpConfig;
import com.healthcare.edi835.entity.FileGenerationHistory;
import com.healthcare.edi835.entity.Payer;
import com.healthcare.edi835.model.projection.FileGenerationSummary;
import com.healthcare.edi835.repository.FileGenerationHistoryRepository;
import com.healthcare.edi835.repository.PayerRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for delivering EDI files to payers via SFTP.
 * Implements retry logic and delivery status tracking.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Upload files to payer SFTP servers</li>
 *   <li>Retry failed deliveries with exponential backoff</li>
 *   <li>Update delivery status and timestamps</li>
 *   <li>Log delivery attempts and errors</li>
 *   <li>Handle SFTP connection management</li>
 * </ul>
 *
 * <p>Note: This is a placeholder implementation. In production, you would integrate
 * with a proper SFTP library like Apache Commons VFS, JSch, or Spring Integration SFTP.</p>
 */
@Slf4j
@Service
public class FileDeliveryService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 5000; // 5 seconds

    private final FileGenerationHistoryRepository historyRepository;
    private final PayerRepository payerRepository;
    private final SftpConfig sftpConfig;

    @Value("${edi835.file-delivery.enabled:true}")
    private boolean deliveryEnabled;

    @Value("${edi835.file-delivery.auto-retry:true}")
    private boolean autoRetryEnabled;

    @Value("${edi835.file-delivery.max-retries:3}")
    private int maxRetries;

    @Value("${file-delivery.sftp.enabled:false}")
    private boolean sftpEnabled;

    public FileDeliveryService(
            FileGenerationHistoryRepository historyRepository,
            PayerRepository payerRepository,
            SftpConfig sftpConfig) {
        this.historyRepository = historyRepository;
        this.payerRepository = payerRepository;
        this.sftpConfig = sftpConfig;
    }

    /**
     * Delivers a file to the payer's SFTP server.
     *
     * @param fileId the file generation history ID
     * @throws DeliveryException if delivery fails after all retry attempts
     */
    @Transactional
    public void deliverFile(UUID fileId) throws DeliveryException {
        log.info("Starting file delivery for file ID: {}", fileId);

        if (!deliveryEnabled) {
            log.warn("File delivery is disabled. Skipping delivery for file: {}", fileId);
            return;
        }

        // Use findByIdWithContent to properly handle BLOB column with SQLite
        FileGenerationHistory file = historyRepository.findByIdWithContent(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        // Get payer SFTP configuration
        Payer payer = payerRepository.findByPayerId(file.getBucket().getPayerId())
                .orElseThrow(() -> new DeliveryException(
                        "Payer not found: " + file.getBucket().getPayerId()));

        if (!hasSftpConfig(payer)) {
            log.error("No SFTP configuration found for payer: {}", payer.getPayerId());
            historyRepository.updateDeliveryStatusToFailed(fileId.toString(), "No SFTP configuration");
            throw new DeliveryException("No SFTP configuration for payer: " + payer.getPayerId());
        }

        try {
            // Attempt delivery with retries
            deliverWithRetry(file, payer);

            // Mark as delivered using native query (avoids Hibernate detached entity issues)
            historyRepository.updateDeliveryStatusToDelivered(
                fileId.toString(),
                LocalDateTime.now()
            );

            log.info("File {} successfully delivered to payer {} at {}",
                    file.getFileName(), payer.getPayerId(), payer.getSftpHost());

        } catch (Exception e) {
            log.error("Failed to deliver file {} after {} attempts: {}",
                    file.getFileName(), file.getRetryCount(), e.getMessage(), e);

            // Mark as failed using native query (avoids Hibernate detached entity issues)
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 1000) {
                errorMsg = errorMsg.substring(0, 1000); // Truncate if too long
            }
            historyRepository.updateDeliveryStatusToFailed(fileId.toString(), errorMsg);

            throw new DeliveryException("File delivery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Attempts delivery with retry logic.
     *
     * @param file the file to deliver
     * @param payer the payer configuration
     * @throws DeliveryException if all retry attempts fail
     */
    private void deliverWithRetry(FileGenerationHistory file, Payer payer) throws DeliveryException {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            attempt++;

            // Increment retry count using native query (avoids detached entity issues)
            historyRepository.incrementDeliveryAttemptCount(file.getFileId().toString());

            try {
                log.debug("Delivery attempt {} of {} for file {}",
                        attempt, maxRetries, file.getFileName());

                // Perform actual SFTP upload
                uploadToSftp(file, payer);

                log.info("File {} delivered successfully on attempt {}", file.getFileName(), attempt);
                return; // Success!

            } catch (Exception e) {
                lastException = e;
                log.warn("Delivery attempt {} failed for file {}: {}",
                        attempt, file.getFileName(), e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        // Exponential backoff
                        long delay = RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
                        log.debug("Waiting {} ms before retry", delay);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new DeliveryException("Delivery interrupted", ie);
                    }
                }
            }
        }

        // All retries exhausted
        throw new DeliveryException(
                String.format("Failed to deliver file after %d attempts", maxRetries),
                lastException);
    }

    /**
     * Uploads file to SFTP server using Spring Integration SFTP.
     *
     * Implementation uses Spring Integration SFTP with JSch for secure file transfer.
     * Features:
     * - Connection pooling and caching
     * - Automatic session management
     * - Robust error handling
     * - Per-payer SFTP configuration
     *
     * @param file the file to upload
     * @param payer the payer with SFTP configuration
     * @throws DeliveryException if upload fails
     */
    private void uploadToSftp(FileGenerationHistory file, Payer payer) throws DeliveryException {
        log.info("Uploading file {} to SFTP: {}:{}/{}",
                file.getFileName(),
                payer.getSftpHost(),
                payer.getSftpPort(),
                payer.getSftpPath());

        // Validate file content
        if (file.getFileContent() == null || file.getFileContent().length == 0) {
            throw new DeliveryException("File content is empty");
        }

        // Check if SFTP is enabled
        if (!sftpEnabled) {
            log.warn("SFTP is disabled. Skipping actual upload for file: {}", file.getFileName());
            log.info("File {} would be uploaded to {}:{}{}/{}",
                    file.getFileName(),
                    payer.getSftpHost(),
                    payer.getSftpPort(),
                    payer.getSftpPath(),
                    file.getFileName());
            // In test mode, just return success
            return;
        }

        SessionFactory<SftpClient.DirEntry> sessionFactory = null;
        Session<SftpClient.DirEntry> session = null;

        try {
            // Create SFTP session factory for this payer
            sessionFactory = sftpConfig.createSessionFactory(payer);

            // Create remote file template for file operations
            SftpRemoteFileTemplate template = sftpConfig.createRemoteFileTemplate(sessionFactory);

            // Get session from factory
            session = sessionFactory.getSession();

            // Prepare remote path
            String remotePath = buildRemotePath(payer.getSftpPath(), file.getFileName());

            // Create input stream from file content
            InputStream inputStream = new ByteArrayInputStream(file.getFileContent());

            // Upload file using session
            log.debug("Writing file to remote path: {}", remotePath);
            session.write(inputStream, remotePath);

            log.info("File {} successfully uploaded to {}:{}{} ({} bytes)",
                    file.getFileName(),
                    payer.getSftpHost(),
                    payer.getSftpPort(),
                    remotePath,
                    file.getFileSizeBytes());

        } catch (Exception e) {
            String errorMsg = String.format("Failed to upload file %s to SFTP: %s",
                    file.getFileName(), e.getMessage());
            log.error(errorMsg, e);
            throw new DeliveryException(errorMsg, e);

        } finally {
            // Close session if opened
            if (session != null && session.isOpen()) {
                try {
                    session.close();
                    log.debug("SFTP session closed for file: {}", file.getFileName());
                } catch (Exception e) {
                    log.warn("Error closing SFTP session: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Builds the complete remote file path.
     *
     * @param basePath the base directory path (e.g., /edi/835)
     * @param fileName the file name
     * @return complete remote path
     */
    private String buildRemotePath(String basePath, String fileName) {
        // Ensure base path doesn't end with /
        String cleanPath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        return cleanPath + "/" + fileName;
    }

    /**
     * Retries delivery for all failed files.
     *
     * @return count of successfully delivered files
     */
    @Transactional
    public int retryFailedDeliveries() {
        log.info("Starting retry for failed file deliveries");

        if (!deliveryEnabled || !autoRetryEnabled) {
            log.warn("Auto-retry is disabled. Skipping failed delivery retry.");
            return 0;
        }

        List<FileGenerationHistory> failedFiles = historyRepository.findFailedDeliveries();

        if (failedFiles.isEmpty()) {
            log.debug("No failed deliveries found");
            return 0;
        }

        log.info("Found {} failed deliveries to retry", failedFiles.size());

        int successCount = 0;
        int errorCount = 0;

        for (FileGenerationHistory file : failedFiles) {
            // Only retry if not exceeded max attempts
            if (file.getRetryCount() >= maxRetries) {
                log.debug("File {} has exceeded max retry attempts ({}), skipping",
                        file.getFileName(), file.getRetryCount());
                continue;
            }

            try {
                deliverFile(file.getFileId());
                successCount++;
            } catch (DeliveryException e) {
                errorCount++;
                log.error("Retry failed for file {}: {}", file.getFileName(), e.getMessage());
            }
        }

        log.info("Retry complete: {} succeeded, {} failed", successCount, errorCount);
        return successCount;
    }

    /**
     * Gets delivery status for a file.
     *
     * @param fileId the file generation history ID
     * @return the delivery status
     */
    public Optional<FileGenerationHistory.DeliveryStatus> getDeliveryStatus(UUID fileId) {
        // Use summary projection to avoid BLOB issues with SQLite
        return historyRepository.findSummaryById(fileId)
                .map(FileGenerationSummary::getDeliveryStatus);
    }

    /**
     * Gets all pending deliveries.
     *
     * @return list of files pending delivery
     */
    public List<FileGenerationHistory> getPendingDeliveries() {
        return historyRepository.findPendingDeliveries();
    }

    /**
     * Gets delivery statistics.
     *
     * @return statistics as object array
     */
    public List<Object[]> getDeliveryStatistics() {
        return historyRepository.getGenerationStatistics();
    }

    /**
     * Manually marks a file as delivered.
     * Used when file was delivered through alternate means.
     *
     * @param fileId the file ID
     * @param deliveredBy the user who confirmed delivery
     */
    @Transactional
    public void markAsDelivered(UUID fileId, String deliveredBy) {
        log.info("Manually marking file {} as delivered by {}", fileId, deliveredBy);

        // Update using native query to avoid detached entity issues
        int updated = historyRepository.updateDeliveryStatusToDeliveredManual(
            fileId.toString(),
            LocalDateTime.now(),
            deliveredBy + " (manual)"
        );

        if (updated == 0) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }

        log.info("File {} marked as delivered by {}", fileId, deliveredBy);
    }

    /**
     * Validates SFTP configuration for a payer.
     *
     * @param payerId the payer ID
     * @return true if SFTP is properly configured
     */
    public boolean validateSftpConfig(String payerId) {
        Optional<Payer> payerOpt = payerRepository.findByPayerId(payerId);

        if (payerOpt.isEmpty()) {
            log.warn("Payer not found: {}", payerId);
            return false;
        }

        Payer payer = payerOpt.get();
        boolean isValid = hasSftpConfig(payer);

        if (!isValid) {
            log.warn("Incomplete SFTP configuration for payer: {}", payerId);
        }

        return isValid;
    }

    /**
     * Checks if payer has complete SFTP configuration.
     *
     * @param payer the payer entity
     * @return true if SFTP is configured
     */
    private boolean hasSftpConfig(Payer payer) {
        return payer.getSftpHost() != null && !payer.getSftpHost().isEmpty() &&
               payer.getSftpPort() != null &&
               payer.getSftpUsername() != null && !payer.getSftpUsername().isEmpty() &&
               payer.getSftpPath() != null && !payer.getSftpPath().isEmpty();
    }

    /**
     * Exception thrown when file delivery fails.
     */
    public static class DeliveryException extends Exception {
        public DeliveryException(String message) {
            super(message);
        }

        public DeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
