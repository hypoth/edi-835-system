package com.healthcare.edi835.changefeed.sqlite;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entity representing a change feed processing version/run.
 *
 * <p>Each version tracks a complete run of the change feed processor,
 * allowing for replay scenarios and monitoring of processing history.</p>
 */
@Entity
@Table(name = "feed_versions", indexes = {
    @Index(name = "idx_feed_versions_status", columnList = "status,started_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "started_at")
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private Status status = Status.RUNNING;

    @Column(name = "changes_count")
    @Builder.Default
    private Integer changesCount = 0;

    @Column(name = "processed_count")
    @Builder.Default
    private Integer processedCount = 0;

    @Column(name = "error_count")
    @Builder.Default
    private Integer errorCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "host_name", length = 255)
    private String hostName;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Status enum for feed version processing.
     */
    public enum Status {
        RUNNING,
        COMPLETED,
        FAILED
    }

    /**
     * Marks this version as completed.
     */
    public void complete() {
        this.status = Status.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks this version as failed with an error message.
     *
     * @param errorMessage the error message
     */
    public void fail(String errorMessage) {
        this.status = Status.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    /**
     * Increments the processed count.
     */
    public void incrementProcessed() {
        this.processedCount++;
    }

    /**
     * Increments the error count.
     */
    public void incrementErrors() {
        this.errorCount++;
    }
}
