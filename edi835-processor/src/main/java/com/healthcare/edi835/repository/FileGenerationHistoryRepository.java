package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.FileGenerationHistory;
import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.model.projection.FileGenerationSummary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for FileGenerationHistory entity.
 * Extends custom repository for SQLite-compatible file download operations.
 */
@Repository
public interface FileGenerationHistoryRepository extends JpaRepository<FileGenerationHistory, UUID>,
        FileGenerationHistoryRepositoryCustom {

    /**
     * Finds generation history by bucket.
     */
    List<FileGenerationHistory> findByBucket(EdiFileBucket bucket);

    /**
     * Finds files by delivery status.
     */
    List<FileGenerationHistory> findByDeliveryStatus(FileGenerationHistory.DeliveryStatus status);

    /**
     * Finds files pending delivery (basic info only, no LOB).
     * Returns file IDs and names for processing. Load full content separately via findByIdWithContent().
     */
    default List<FileGenerationHistory> findPendingDeliveries() {
        return findPendingDeliveriesSummary().stream()
                .map(summary -> {
                    FileGenerationHistory file = new FileGenerationHistory();
                    file.setId(summary.getFileId());
                    file.setGeneratedFileName(summary.getFileName());
                    file.setFilePath(summary.getFilePath());
                    file.setDeliveryStatus(FileGenerationHistory.DeliveryStatus.PENDING);
                    return file;
                })
                .toList();
    }

    /**
     * Finds files pending delivery as summaries (for display).
     */
    @Query("SELECT f.id as fileId, f.generatedFileName as fileName, f.filePath as filePath, " +
           "f.fileSizeBytes as fileSizeBytes, f.claimCount as claimCount, f.totalAmount as totalAmount, " +
           "f.generatedAt as generatedAt, f.generatedBy as generatedBy, f.deliveryStatus as deliveryStatus, " +
           "f.deliveredAt as deliveredAt, f.deliveryAttemptCount as retryCount, " +
           "f.errorMessage as errorMessage, b as bucket " +
           "FROM FileGenerationHistory f LEFT JOIN f.bucket b WHERE f.deliveryStatus = 'PENDING' ORDER BY f.generatedAt ASC")
    List<FileGenerationSummary> findPendingDeliveriesSummary();

    /**
     * Finds files that need retry (basic info only, no LOB).
     * Returns file IDs and names for processing. Load full content separately via findByIdWithContent().
     */
    @Query("SELECT f.id as fileId, f.generatedFileName as fileName, f.filePath as filePath, " +
           "f.fileSizeBytes as fileSizeBytes, f.claimCount as claimCount, f.totalAmount as totalAmount, " +
           "f.generatedAt as generatedAt, f.generatedBy as generatedBy, f.deliveryStatus as deliveryStatus, " +
           "f.deliveredAt as deliveredAt, f.deliveryAttemptCount as retryCount, " +
           "f.errorMessage as errorMessage, b as bucket " +
           "FROM FileGenerationHistory f LEFT JOIN f.bucket b " +
           "WHERE f.deliveryStatus = 'RETRY' AND f.deliveryAttemptCount < :maxAttempts " +
           "ORDER BY f.generatedAt ASC")
    List<FileGenerationSummary> findFilesForRetrySummary(@Param("maxAttempts") int maxAttempts);

    /**
     * Finds files that need retry as entities (compatibility method).
     * Delegates to summary method and converts to minimal entities without LOB.
     */
    default List<FileGenerationHistory> findFilesForRetry(int maxAttempts) {
        return findFilesForRetrySummary(maxAttempts).stream()
                .map(summary -> {
                    FileGenerationHistory file = new FileGenerationHistory();
                    file.setId(summary.getFileId());
                    file.setGeneratedFileName(summary.getFileName());
                    file.setFilePath(summary.getFilePath());
                    file.setDeliveryStatus(FileGenerationHistory.DeliveryStatus.RETRY);
                    file.setDeliveryAttemptCount(summary.getRetryCount());
                    return file;
                })
                .toList();
    }

    /**
     * Finds recent file generation history.
     */
    @Query("SELECT f FROM FileGenerationHistory f WHERE f.generatedAt >= :since ORDER BY f.generatedAt DESC")
    List<FileGenerationHistory> findRecentHistory(@Param("since") LocalDateTime since);

    /**
     * Finds files generated today.
     */
    @Query("SELECT f FROM FileGenerationHistory f WHERE f.generatedAt >= :startOfDay")
    List<FileGenerationHistory> findFilesGeneratedToday(@Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Counts files by delivery status.
     */
    long countByDeliveryStatus(FileGenerationHistory.DeliveryStatus status);

    /**
     * Gets generation statistics.
     */
    @Query("SELECT f.deliveryStatus, COUNT(f), SUM(f.claimCount), SUM(f.totalAmount) " +
           "FROM FileGenerationHistory f GROUP BY f.deliveryStatus")
    List<Object[]> getGenerationStatistics();

    /**
     * Finds failed deliveries (basic info only, no LOB).
     * Returns file IDs and names for processing. Load full content separately via findByIdWithContent().
     */
    default List<FileGenerationHistory> findFailedDeliveries() {
        return findFailedDeliveriesSummary().stream()
                .map(summary -> {
                    FileGenerationHistory file = new FileGenerationHistory();
                    file.setId(summary.getFileId());
                    file.setGeneratedFileName(summary.getFileName());
                    file.setFilePath(summary.getFilePath());
                    file.setDeliveryStatus(FileGenerationHistory.DeliveryStatus.FAILED);
                    file.setDeliveryAttemptCount(summary.getRetryCount());
                    file.setErrorMessage(summary.getErrorMessage());
                    return file;
                })
                .toList();
    }

    /**
     * Finds failed deliveries as summaries (for display).
     */
    @Query("SELECT f.id as fileId, f.generatedFileName as fileName, f.filePath as filePath, " +
           "f.fileSizeBytes as fileSizeBytes, f.claimCount as claimCount, f.totalAmount as totalAmount, " +
           "f.generatedAt as generatedAt, f.generatedBy as generatedBy, f.deliveryStatus as deliveryStatus, " +
           "f.deliveredAt as deliveredAt, f.deliveryAttemptCount as retryCount, " +
           "f.errorMessage as errorMessage, b as bucket " +
           "FROM FileGenerationHistory f LEFT JOIN f.bucket b WHERE f.deliveryStatus = 'FAILED' ORDER BY f.generatedAt DESC")
    List<FileGenerationSummary> findFailedDeliveriesSummary();

    /**
     * Finds recent files (limit by generation date).
     */
    @Query("SELECT f FROM FileGenerationHistory f ORDER BY f.generatedAt DESC")
    List<FileGenerationHistory> findRecentFiles();

    /**
     * Finds recent files with limit.
     */
    default List<FileGenerationHistory> findRecentFiles(int limit) {
        return findRecentFiles().stream().limit(limit).toList();
    }

    /**
     * Finds files by bucket ID.
     */
    @Query("SELECT f FROM FileGenerationHistory f WHERE f.bucket.bucketId = :bucketId ORDER BY f.generatedAt DESC")
    List<FileGenerationHistory> findByBucketId(@Param("bucketId") UUID bucketId);

    /**
     * Finds files by payer ID (through bucket relationship).
     */
    @Query("SELECT f FROM FileGenerationHistory f WHERE f.bucket.payerId = :payerId ORDER BY f.generatedAt DESC")
    List<FileGenerationHistory> findByPayerId(@Param("payerId") String payerId);

    // ==================== Projection Methods (WITHOUT file_content for SQLite compatibility) ====================

    /**
     * Finds recent files as summary projection (excludes file_content).
     * Use this for list endpoints to avoid SQLite JDBC LOB issues.
     */
    @Query("SELECT f.id as fileId, f.generatedFileName as fileName, f.filePath as filePath, " +
           "f.fileSizeBytes as fileSizeBytes, f.claimCount as claimCount, f.totalAmount as totalAmount, " +
           "f.generatedAt as generatedAt, f.generatedBy as generatedBy, f.deliveryStatus as deliveryStatus, " +
           "f.deliveredAt as deliveredAt, f.deliveryAttemptCount as retryCount, " +
           "f.errorMessage as errorMessage, b as bucket " +
           "FROM FileGenerationHistory f LEFT JOIN f.bucket b ORDER BY f.generatedAt DESC")
    List<FileGenerationSummary> findAllSummaries();

    /**
     * Finds recent files as summary with limit.
     */
    default List<FileGenerationSummary> findRecentSummaries(int limit) {
        return findAllSummaries().stream().limit(limit).toList();
    }

    /**
     * Finds file summary by ID (excludes file_content).
     */
    @Query("SELECT f.id as fileId, f.generatedFileName as fileName, f.filePath as filePath, " +
           "f.fileSizeBytes as fileSizeBytes, f.claimCount as claimCount, f.totalAmount as totalAmount, " +
           "f.generatedAt as generatedAt, f.generatedBy as generatedBy, f.deliveryStatus as deliveryStatus, " +
           "f.deliveredAt as deliveredAt, f.deliveryAttemptCount as retryCount, " +
           "f.errorMessage as errorMessage, b as bucket " +
           "FROM FileGenerationHistory f LEFT JOIN f.bucket b WHERE f.id = :fileId")
    Optional<FileGenerationSummary> findSummaryById(@Param("fileId") UUID fileId);

    /**
     * Finds files by bucket as summaries.
     */
    @Query("SELECT f.id as fileId, f.generatedFileName as fileName, f.filePath as filePath, " +
           "f.fileSizeBytes as fileSizeBytes, f.claimCount as claimCount, f.totalAmount as totalAmount, " +
           "f.generatedAt as generatedAt, f.generatedBy as generatedBy, f.deliveryStatus as deliveryStatus, " +
           "f.deliveredAt as deliveredAt, f.deliveryAttemptCount as retryCount, " +
           "f.errorMessage as errorMessage, b as bucket " +
           "FROM FileGenerationHistory f LEFT JOIN f.bucket b WHERE b = :bucket ORDER BY f.generatedAt DESC")
    List<FileGenerationSummary> findSummariesByBucket(@Param("bucket") EdiFileBucket bucket);

    /**
     * Finds files by payer as summaries.
     */
    @Query("SELECT f.id as fileId, f.generatedFileName as fileName, f.filePath as filePath, " +
           "f.fileSizeBytes as fileSizeBytes, f.claimCount as claimCount, f.totalAmount as totalAmount, " +
           "f.generatedAt as generatedAt, f.generatedBy as generatedBy, f.deliveryStatus as deliveryStatus, " +
           "f.deliveredAt as deliveredAt, f.deliveryAttemptCount as retryCount, " +
           "f.errorMessage as errorMessage, b as bucket " +
           "FROM FileGenerationHistory f LEFT JOIN f.bucket b WHERE b.payerId = :payerId ORDER BY f.generatedAt DESC")
    List<FileGenerationSummary> findSummariesByPayerId(@Param("payerId") String payerId);

    /**
     * Finds files by delivery status as summaries.
     */
    @Query("SELECT f.id as fileId, f.generatedFileName as fileName, f.filePath as filePath, " +
           "f.fileSizeBytes as fileSizeBytes, f.claimCount as claimCount, f.totalAmount as totalAmount, " +
           "f.generatedAt as generatedAt, f.generatedBy as generatedBy, f.deliveryStatus as deliveryStatus, " +
           "f.deliveredAt as deliveredAt, f.deliveryAttemptCount as retryCount, " +
           "f.errorMessage as errorMessage, b as bucket " +
           "FROM FileGenerationHistory f LEFT JOIN f.bucket b WHERE f.deliveryStatus = :status ORDER BY f.generatedAt DESC")
    List<FileGenerationSummary> findSummariesByDeliveryStatus(@Param("status") FileGenerationHistory.DeliveryStatus status);

    // ==================== Methods WITH file_content (for download/delivery) ====================
    // NOTE: Native queries with SELECT * still trigger Hibernate entity mapping which fails with SQLite LOB.
    // Use custom repository methods (findFileForDownloadById, findFileForDownloadByFileName) instead.
    // These use JdbcTemplate to bypass Hibernate and work with both SQLite and PostgreSQL.

    /**
     * Finds file by ID WITH file_content for delivery operations.
     * Uses custom repository implementation to handle SQLite BLOB properly.
     *
     * @param fileId the file ID
     * @return Optional containing the file with content, or empty if not found
     */
    default Optional<FileGenerationHistory> findByIdWithContent(UUID fileId) {
        return findEntityWithContentById(fileId);
    }

    /**
     * Updates delivery status to DELIVERED using native query.
     * Avoids Hibernate cascade issues with detached entities.
     *
     * @param fileId the file ID
     * @return number of rows updated
     */
    @Modifying
    @Query(value = "UPDATE file_generation_history SET delivery_status = 'DELIVERED', " +
                   "delivered_at = :deliveredAt WHERE id = :fileId", nativeQuery = true)
    int updateDeliveryStatusToDelivered(@Param("fileId") String fileId, @Param("deliveredAt") LocalDateTime deliveredAt);

    /**
     * Updates delivery status to FAILED using native query.
     * Avoids Hibernate cascade issues with detached entities.
     *
     * @param fileId the file ID
     * @param errorMessage the error message
     * @return number of rows updated
     */
    @Modifying
    @Query(value = "UPDATE file_generation_history SET delivery_status = 'FAILED', " +
                   "error_message = :errorMessage WHERE id = :fileId", nativeQuery = true)
    int updateDeliveryStatusToFailed(@Param("fileId") String fileId, @Param("errorMessage") String errorMessage);

    /**
     * Increments delivery attempt count using native query.
     * Avoids Hibernate cascade issues with detached entities.
     *
     * @param fileId the file ID
     * @return number of rows updated
     */
    @Modifying
    @Query(value = "UPDATE file_generation_history SET delivery_attempt_count = delivery_attempt_count + 1, " +
                   "delivery_status = 'RETRY' WHERE id = :fileId", nativeQuery = true)
    int incrementDeliveryAttemptCount(@Param("fileId") String fileId);

    /**
     * Manually marks a file as delivered with custom generated_by.
     * Avoids Hibernate cascade issues with detached entities.
     *
     * @param fileId the file ID
     * @param deliveredAt the delivery timestamp
     * @param generatedBy the user who marked it as delivered
     * @return number of rows updated
     */
    @Modifying
    @Query(value = "UPDATE file_generation_history SET delivery_status = 'DELIVERED', " +
                   "delivered_at = :deliveredAt, generated_by = :generatedBy WHERE id = :fileId", nativeQuery = true)
    int updateDeliveryStatusToDeliveredManual(@Param("fileId") String fileId,
                                               @Param("deliveredAt") LocalDateTime deliveredAt,
                                               @Param("generatedBy") String generatedBy);
}

