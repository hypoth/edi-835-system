package com.healthcare.edi835.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CheckReservation entity - represents a pre-allocated range of check numbers.
 * Maps to 'check_reservations' table.
 *
 * Used for auto-approval workflow where check numbers are pre-reserved
 * and automatically assigned when buckets reach thresholds.
 */
@Entity
@Table(name = "check_reservations", indexes = {
        @Index(name = "idx_check_reservations_status", columnList = "status"),
        @Index(name = "idx_check_reservations_payer", columnList = "payer_id"),
        @Index(name = "idx_check_reservations_payment_method", columnList = "payment_method_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckReservation {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "check_number_start", nullable = false)
    private String checkNumberStart;  // Starting check number (alphanumeric, e.g., CHK001)

    @Column(name = "check_number_end", nullable = false)
    private String checkNumberEnd;    // Ending check number (alphanumeric, e.g., CHK100)

    @Column(name = "total_checks", nullable = false)
    private Integer totalChecks;      // Total checks in range (default: 100)

    @Column(name = "checks_used")
    @Builder.Default
    private Integer checksUsed = 0;   // Number of checks used from this range

    // Bank details (multiple accounts per payer supported)
    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "routing_number")
    private String routingNumber;

    @Column(name = "account_number_last4")
    private String accountNumberLast4;  // Last 4 digits for reference

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id")
    private Payer payer;  // Which payer this reservation belongs to

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.ACTIVE;

    // Audit fields
    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Reservation status.
     */
    public enum ReservationStatus {
        ACTIVE,     // Available for use
        EXHAUSTED,  // All checks used
        CANCELLED   // Reservation cancelled
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Gets the next available check number from this reservation.
     * Increments the checksUsed counter.
     *
     * @return Next check number, or null if exhausted
     */
    public String getNextCheckNumber() {
        if (!hasAvailableChecks()) {
            return null;
        }

        // Parse the numeric part and increment
        String nextNumber = generateCheckNumber(checksUsed);
        checksUsed++;

        // Mark as exhausted if all checks used
        if (checksUsed >= totalChecks) {
            this.status = ReservationStatus.EXHAUSTED;
        }

        return nextNumber;
    }

    /**
     * Generates check number based on the range pattern and index.
     * Supports alphanumeric format (e.g., CHK000001, CHK000002).
     *
     * @param index Index within the range (0-based)
     * @return Formatted check number
     */
    private String generateCheckNumber(int index) {
        // Extract prefix and numeric part from start number
        String prefix = extractPrefix(checkNumberStart);
        int startNumeric = extractNumericPart(checkNumberStart);
        int nextNumeric = startNumeric + index;

        // Determine padding based on start number format
        int padding = getNumericPartLength(checkNumberStart);

        // Format with padding
        return prefix + String.format("%0" + padding + "d", nextNumeric);
    }

    /**
     * Extracts alphabetic prefix from check number.
     * E.g., "CHK000123" → "CHK"
     */
    private String extractPrefix(String checkNumber) {
        if (checkNumber == null || checkNumber.isEmpty()) {
            return "";
        }
        int i = 0;
        while (i < checkNumber.length() && !Character.isDigit(checkNumber.charAt(i))) {
            i++;
        }
        return checkNumber.substring(0, i);
    }

    /**
     * Extracts numeric part from check number.
     * E.g., "CHK000123" → 123
     */
    private int extractNumericPart(String checkNumber) {
        if (checkNumber == null || checkNumber.isEmpty()) {
            return 0;
        }
        int i = 0;
        while (i < checkNumber.length() && !Character.isDigit(checkNumber.charAt(i))) {
            i++;
        }
        if (i >= checkNumber.length()) {
            return 0;
        }
        return Integer.parseInt(checkNumber.substring(i));
    }

    /**
     * Gets the length of numeric part (including leading zeros).
     * E.g., "CHK000123" → 6
     */
    private int getNumericPartLength(String checkNumber) {
        if (checkNumber == null || checkNumber.isEmpty()) {
            return 0;
        }
        int i = 0;
        while (i < checkNumber.length() && !Character.isDigit(checkNumber.charAt(i))) {
            i++;
        }
        return checkNumber.length() - i;
    }

    /**
     * Checks if reservation has available checks.
     */
    public boolean hasAvailableChecks() {
        return checksUsed < totalChecks && status == ReservationStatus.ACTIVE;
    }

    /**
     * Gets number of remaining checks.
     */
    public int getChecksRemaining() {
        return Math.max(0, totalChecks - checksUsed);
    }

    /**
     * Checks if reservation is running low (below threshold).
     *
     * @param threshold Alert threshold (e.g., 50)
     * @return true if remaining checks < threshold
     */
    public boolean isLowStock(int threshold) {
        return getChecksRemaining() < threshold && status == ReservationStatus.ACTIVE;
    }

    /**
     * Cancels the reservation.
     */
    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    /**
     * Gets usage percentage.
     */
    public double getUsagePercentage() {
        if (totalChecks == 0) {
            return 0.0;
        }
        return (double) checksUsed / totalChecks * 100.0;
    }
}
