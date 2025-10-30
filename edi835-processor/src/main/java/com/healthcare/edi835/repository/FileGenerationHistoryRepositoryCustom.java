package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.FileGenerationHistory;
import com.healthcare.edi835.model.dto.FileDownloadDTO;

import java.util.Optional;
import java.util.UUID;

/**
 * Custom repository interface for file download operations.
 * Uses JdbcTemplate to bypass Hibernate's LOB handling for SQLite compatibility.
 */
public interface FileGenerationHistoryRepositoryCustom {

    /**
     * Finds file download data by ID using JdbcTemplate.
     * Avoids Hibernate entity mapping to prevent SQLite JDBC LOB issues.
     */
    Optional<FileDownloadDTO> findFileForDownloadById(UUID fileId);

    /**
     * Finds file download data by filename using JdbcTemplate.
     * Avoids Hibernate entity mapping to prevent SQLite JDBC LOB issues.
     */
    Optional<FileDownloadDTO> findFileForDownloadByFileName(String fileName);

    /**
     * Finds full FileGenerationHistory entity with file content by ID.
     * Uses JdbcTemplate to properly handle BLOB for SQLite compatibility.
     * Note: Does NOT load bucket relationship - use repository.findById() and then this method
     * to populate file_content separately if needed.
     *
     * @param fileId the file ID
     * @return Optional containing entity with file_content loaded
     */
    Optional<FileGenerationHistory> findEntityWithContentById(UUID fileId);
}
