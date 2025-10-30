package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.FileGenerationHistory;
import com.healthcare.edi835.model.dto.FileDownloadDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Custom repository implementation for file download operations.
 * Uses JdbcTemplate to directly query the database and avoid Hibernate's LOB handling.
 * This approach is SQLite-compatible as it uses basic JDBC ResultSet.getBytes() method.
 */
@Slf4j
@Repository
public class FileGenerationHistoryRepositoryImpl implements FileGenerationHistoryRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    public FileGenerationHistoryRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<FileDownloadDTO> findFileForDownloadById(UUID fileId) {
        String sql = "SELECT generated_file_name, file_size_bytes, file_content " +
                     "FROM file_generation_history WHERE id = ?";

        try {
            FileDownloadDTO result = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                FileDownloadDTO dto = new FileDownloadDTO();
                dto.setFileName(rs.getString("generated_file_name"));
                dto.setFileSizeBytes(rs.getLong("file_size_bytes"));
                dto.setFileContent(rs.getBytes("file_content")); // Uses basic JDBC method, not LOB
                return dto;
            }, fileId.toString());

            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.debug("File not found for download: {}", fileId);
            return Optional.empty();
        }
    }

    @Override
    public Optional<FileDownloadDTO> findFileForDownloadByFileName(String fileName) {
        String sql = "SELECT generated_file_name, file_size_bytes, file_content " +
                     "FROM file_generation_history WHERE generated_file_name = ?";

        try {
            FileDownloadDTO result = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                FileDownloadDTO dto = new FileDownloadDTO();
                dto.setFileName(rs.getString("generated_file_name"));
                dto.setFileSizeBytes(rs.getLong("file_size_bytes"));
                dto.setFileContent(rs.getBytes("file_content")); // Uses basic JDBC method, not LOB
                return dto;
            }, fileName);

            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.debug("File not found for download: {}", fileName);
            return Optional.empty();
        }
    }

    @Override
    public Optional<FileGenerationHistory> findEntityWithContentById(UUID fileId) {
        // Join with bucket table to get payerId needed for delivery
        String sql = "SELECT f.id, f.bucket_id, f.generated_file_name, f.file_path, f.file_size_bytes, " +
                     "f.claim_count, f.total_amount, f.generated_at, f.generated_by, f.delivery_status, " +
                     "f.delivered_at, f.delivery_attempt_count, f.error_message, f.file_content, " +
                     "b.payer_id, b.payee_id " +
                     "FROM file_generation_history f " +
                     "LEFT JOIN edi_file_buckets b ON f.bucket_id = b.bucket_id " +
                     "WHERE f.id = ?";

        try {
            FileGenerationHistory result = jdbcTemplate.queryForObject(sql,
                (rs, rowNum) -> mapResultSetToEntity(rs),
                fileId.toString());

            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.debug("File generation history not found: {}", fileId, e);
            return Optional.empty();
        }
    }

    /**
     * Maps ResultSet to FileGenerationHistory entity.
     * Populates bucket stub with payer_id and payee_id if available from JOIN.
     */
    private FileGenerationHistory mapResultSetToEntity(ResultSet rs) throws SQLException {
        FileGenerationHistory entity = new FileGenerationHistory();

        // Primary key
        entity.setId(UUID.fromString(rs.getString("id")));

        // Bucket relationship (create stub with payerId/payeeId from JOIN if available)
        String bucketIdStr = rs.getString("bucket_id");
        if (bucketIdStr != null) {
            EdiFileBucket bucket = new EdiFileBucket();
            bucket.setBucketId(UUID.fromString(bucketIdStr));

            // Populate payer_id and payee_id if present (from JOIN)
            try {
                String payerId = rs.getString("payer_id");
                if (payerId != null) {
                    bucket.setPayerId(payerId);
                }
            } catch (SQLException e) {
                // Column not present in this query - that's ok
            }

            try {
                String payeeId = rs.getString("payee_id");
                if (payeeId != null) {
                    bucket.setPayeeId(payeeId);
                }
            } catch (SQLException e) {
                // Column not present in this query - that's ok
            }

            entity.setBucket(bucket);
        }

        // File metadata
        entity.setGeneratedFileName(rs.getString("generated_file_name"));
        entity.setFilePath(rs.getString("file_path"));
        entity.setFileSizeBytes(rs.getLong("file_size_bytes"));
        entity.setClaimCount(rs.getInt("claim_count"));

        // Amount
        java.math.BigDecimal totalAmount = rs.getBigDecimal("total_amount");
        if (totalAmount != null) {
            entity.setTotalAmount(totalAmount);
        }

        // Timestamps
        Timestamp generatedAt = rs.getTimestamp("generated_at");
        if (generatedAt != null) {
            entity.setGeneratedAt(generatedAt.toLocalDateTime());
        }

        Timestamp deliveredAt = rs.getTimestamp("delivered_at");
        if (deliveredAt != null) {
            entity.setDeliveredAt(deliveredAt.toLocalDateTime());
        }

        // Audit fields
        entity.setGeneratedBy(rs.getString("generated_by"));

        // Delivery status
        String statusStr = rs.getString("delivery_status");
        if (statusStr != null) {
            entity.setDeliveryStatus(FileGenerationHistory.DeliveryStatus.valueOf(statusStr));
        }

        entity.setDeliveryAttemptCount(rs.getInt("delivery_attempt_count"));
        entity.setErrorMessage(rs.getString("error_message"));

        // File content (BLOB) - uses basic JDBC method
        entity.setFileContent(rs.getBytes("file_content"));

        return entity;
    }
}
