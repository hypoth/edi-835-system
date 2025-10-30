package com.healthcare.edi835.changefeed.sqlite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for managing DataChange entities.
 *
 * <p>Provides queries for retrieving unprocessed changes, changes by version,
 * and statistics about the change feed.</p>
 */
@Repository
public interface DataChangeRepository extends JpaRepository<DataChange, Long> {

    /**
     * Finds all unprocessed changes ordered by version and sequence.
     *
     * @return list of unprocessed changes
     */
    @Query("SELECT dc FROM DataChange dc WHERE dc.processed = false " +
           "ORDER BY dc.feedVersion ASC, dc.sequenceNumber ASC")
    List<DataChange> findUnprocessedChanges();

    /**
     * Finds unprocessed changes for a specific version.
     *
     * @param feedVersion the feed version
     * @return list of unprocessed changes for the version
     */
    @Query("SELECT dc FROM DataChange dc WHERE dc.feedVersion = :feedVersion " +
           "AND dc.processed = false ORDER BY dc.sequenceNumber ASC")
    List<DataChange> findUnprocessedChangesByVersion(@Param("feedVersion") Integer feedVersion);

    /**
     * Finds changes for a specific version (processed or not).
     *
     * @param feedVersion the feed version
     * @return list of all changes for the version
     */
    List<DataChange> findByFeedVersionOrderBySequenceNumberAsc(Integer feedVersion);

    /**
     * Finds changes after a specific checkpoint.
     *
     * @param feedVersion the feed version to start from
     * @param sequenceNumber the sequence number to start from
     * @param limit maximum number of changes to return
     * @return list of changes after the checkpoint
     */
    @Query("SELECT dc FROM DataChange dc WHERE " +
           "(dc.feedVersion > :feedVersion) OR " +
           "(dc.feedVersion = :feedVersion AND dc.sequenceNumber > :sequenceNumber) " +
           "ORDER BY dc.feedVersion ASC, dc.sequenceNumber ASC " +
           "LIMIT :limit")
    List<DataChange> findChangesAfterCheckpoint(
            @Param("feedVersion") Integer feedVersion,
            @Param("sequenceNumber") Long sequenceNumber,
            @Param("limit") int limit);

    /**
     * Counts unprocessed changes.
     *
     * @return count of unprocessed changes
     */
    long countByProcessedFalse();

    /**
     * Counts changes for a specific version.
     *
     * @param feedVersion the feed version
     * @return count of changes for the version
     */
    long countByFeedVersion(Integer feedVersion);

    /**
     * Finds changes for a specific table and row ID.
     *
     * @param tableName the table name
     * @param rowId the row ID
     * @return list of changes for the specific row
     */
    List<DataChange> findByTableNameAndRowIdOrderByChangedAtDesc(String tableName, String rowId);
}
