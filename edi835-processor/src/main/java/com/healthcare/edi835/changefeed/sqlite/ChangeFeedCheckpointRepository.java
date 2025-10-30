package com.healthcare.edi835.changefeed.sqlite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing ChangeFeedCheckpoint entities.
 *
 * <p>Provides methods for storing and retrieving checkpoint information
 * for change feed consumers.</p>
 */
@Repository
public interface ChangeFeedCheckpointRepository extends JpaRepository<ChangeFeedCheckpoint, String> {

    /**
     * Finds a checkpoint by consumer ID.
     *
     * @param consumerId the consumer ID
     * @return the checkpoint, if it exists
     */
    Optional<ChangeFeedCheckpoint> findByConsumerId(String consumerId);
}
