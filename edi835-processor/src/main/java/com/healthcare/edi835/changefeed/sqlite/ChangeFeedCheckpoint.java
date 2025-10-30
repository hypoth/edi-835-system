package com.healthcare.edi835.changefeed.sqlite;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entity representing the checkpoint for a change feed consumer.
 *
 * <p>Tracks the last processed version and sequence number for each consumer,
 * enabling resume capability and preventing duplicate processing.</p>
 */
@Entity
@Table(name = "changefeed_checkpoint")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeFeedCheckpoint {

    @Id
    @Column(name = "consumer_id", length = 255)
    private String consumerId;

    @Column(name = "last_feed_version", nullable = false)
    @Builder.Default
    private Long lastFeedVersion = 0L;

    @Column(name = "last_sequence_number", nullable = false)
    @Builder.Default
    private Long lastSequenceNumber = 0L;

    @Column(name = "last_checkpoint_at")
    @Builder.Default
    private LocalDateTime lastCheckpointAt = LocalDateTime.now();

    @Column(name = "total_processed")
    @Builder.Default
    private Long totalProcessed = 0L;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Updates the checkpoint to a new position.
     *
     * @param feedVersion the new feed version
     * @param sequenceNumber the new sequence number
     */
    public void updateCheckpoint(Long feedVersion, Long sequenceNumber) {
        this.lastFeedVersion = feedVersion;
        this.lastSequenceNumber = sequenceNumber;
        this.lastCheckpointAt = LocalDateTime.now();
        this.totalProcessed++;
    }
}
