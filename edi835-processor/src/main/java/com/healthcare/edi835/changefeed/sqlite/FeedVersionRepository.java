package com.healthcare.edi835.changefeed.sqlite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing FeedVersion entities.
 *
 * <p>Provides queries for tracking change feed processing versions and their status.</p>
 */
@Repository
public interface FeedVersionRepository extends JpaRepository<FeedVersion, Long> {

    /**
     * Finds the latest feed version.
     *
     * @return the latest feed version, if any
     */
    @Query("SELECT fv FROM FeedVersion fv ORDER BY fv.versionId DESC LIMIT 1")
    Optional<FeedVersion> findLatestVersion();

    /**
     * Finds all running feed versions.
     *
     * @return list of running feed versions
     */
    List<FeedVersion> findByStatus(FeedVersion.Status status);

    /**
     * Finds the maximum version ID.
     *
     * @return the maximum version ID, or 0 if no versions exist
     */
    @Query("SELECT COALESCE(MAX(fv.versionId), 0) FROM FeedVersion fv")
    Long findMaxVersionId();

    /**
     * Finds recent feed versions.
     *
     * @param limit maximum number of versions to return
     * @return list of recent feed versions
     */
    @Query("SELECT fv FROM FeedVersion fv ORDER BY fv.versionId DESC LIMIT :limit")
    List<FeedVersion> findRecentVersions(int limit);
}
