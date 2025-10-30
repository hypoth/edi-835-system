package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.util.UUID;

/**
 * File Naming Sequence entity - tracks sequence numbers for file naming.
 * Maps to 'file_naming_sequence' table in PostgreSQL.
 */
@Entity
@Table(name = "file_naming_sequence",
       uniqueConstraints = @UniqueConstraint(columnNames = {"templateId", "payerId"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileNamingSequence {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private EdiFileNamingTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id")
    private Payer payer;

    @Column(name = "current_sequence")
    @Builder.Default
    private Integer currentSequence = 0;

    @Column(name = "last_reset_date")
    @Builder.Default
    private LocalDate lastResetDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "reset_frequency", length = 20)
    private ResetFrequency resetFrequency;

    public enum ResetFrequency {
        DAILY,
        MONTHLY,
        YEARLY,
        NEVER
    }

    /**
     * Increments the sequence number and returns the new value.
     * Thread-safe when used with proper locking.
     */
    public synchronized Integer incrementAndGet() {
        this.currentSequence++;
        return this.currentSequence;
    }

    /**
     * Checks if sequence should be reset based on frequency.
     */
    public boolean shouldReset() {
        if (resetFrequency == null || resetFrequency == ResetFrequency.NEVER) {
            return false;
        }

        LocalDate now = LocalDate.now();

        return switch (resetFrequency) {
            case DAILY -> !lastResetDate.equals(now);
            case MONTHLY -> lastResetDate.getMonth() != now.getMonth() ||
                           lastResetDate.getYear() != now.getYear();
            case YEARLY -> lastResetDate.getYear() != now.getYear();
            case NEVER -> false;
        };
    }

    /**
     * Resets the sequence to zero.
     */
    public void reset() {
        this.currentSequence = 0;
        this.lastResetDate = LocalDate.now();
    }
}
