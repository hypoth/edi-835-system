package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.CheckReservation;
import com.healthcare.edi835.entity.Payer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CheckReservation entity.
 * Manages pre-allocated check number ranges for auto-approval workflow.
 */
@Repository
public interface CheckReservationRepository extends JpaRepository<CheckReservation, UUID> {

    /**
     * Finds active reservations by payer.
     *
     * @param payer The payer
     * @return List of active reservations for the payer
     */
    List<CheckReservation> findByPayerAndStatus(Payer payer, CheckReservation.ReservationStatus status);

    /**
     * Finds active reservations by payer ID.
     *
     * @param payerId Payer ID
     * @return List of active reservations
     */
    @Query("SELECT cr FROM CheckReservation cr WHERE cr.payer.id = :payerId AND cr.status = 'ACTIVE'")
    List<CheckReservation> findActiveByPayerId(@Param("payerId") UUID payerId);

    /**
     * Finds the first active reservation with available checks for a payer.
     * Orders by creation date (oldest first, FIFO).
     *
     * @param payerId Payer ID
     * @return Optional containing available reservation if found
     * @deprecated Use {@link #findAvailableForPayerOrderByCreatedAt(UUID)} instead to avoid
     *             non-unique result exceptions when multiple reservations exist.
     */
    @Deprecated
    @Query("SELECT cr FROM CheckReservation cr WHERE cr.payer.id = :payerId " +
           "AND cr.status = 'ACTIVE' AND cr.checksUsed < cr.totalChecks " +
           "ORDER BY cr.createdAt ASC")
    Optional<CheckReservation> findFirstAvailableForPayer(@Param("payerId") UUID payerId);

    /**
     * Finds all active reservations with available checks for a payer.
     * Returns a list ordered by creation date (oldest first, FIFO).
     * The caller should use the first element to implement FIFO ordering.
     *
     * <p>This method returns a List instead of Optional to avoid "non-unique result"
     * exceptions when multiple reservations exist for a payer.</p>
     *
     * @param payerId Payer ID
     * @return List of available reservations ordered by creation date (oldest first)
     */
    @Query("SELECT cr FROM CheckReservation cr WHERE cr.payer.id = :payerId " +
           "AND cr.status = 'ACTIVE' AND cr.checksUsed < cr.totalChecks " +
           "ORDER BY cr.createdAt ASC")
    List<CheckReservation> findAvailableForPayerOrderByCreatedAt(@Param("payerId") UUID payerId);

    /**
     * Finds reservations running low on checks.
     * Returns active reservations with remaining checks below threshold.
     *
     * @param threshold Alert threshold
     * @return List of low-stock reservations
     */
    @Query("SELECT cr FROM CheckReservation cr WHERE cr.status = 'ACTIVE' " +
           "AND (cr.totalChecks - cr.checksUsed) < :threshold " +
           "ORDER BY (cr.totalChecks - cr.checksUsed) ASC")
    List<CheckReservation> findLowStockReservations(@Param("threshold") int threshold);

    /**
     * Finds all reservations by status.
     *
     * @param status Reservation status
     * @return List of reservations with given status
     */
    List<CheckReservation> findByStatus(CheckReservation.ReservationStatus status);

    /**
     * Finds reservations by payment method.
     *
     * @param paymentMethodId Payment method ID
     * @return List of reservations
     */
    @Query("SELECT cr FROM CheckReservation cr WHERE cr.paymentMethod.id = :paymentMethodId")
    List<CheckReservation> findByPaymentMethodId(@Param("paymentMethodId") UUID paymentMethodId);

    /**
     * Finds all active reservations with available checks.
     *
     * @return List of active reservations with available capacity
     */
    @Query("SELECT cr FROM CheckReservation cr WHERE cr.status = 'ACTIVE' " +
           "AND cr.checksUsed < cr.totalChecks ORDER BY cr.createdAt ASC")
    List<CheckReservation> findAllActiveWithCapacity();

    /**
     * Counts total available checks across all active reservations for a payer.
     *
     * @param payerId Payer ID
     * @return Total number of available checks
     */
    @Query("SELECT SUM(cr.totalChecks - cr.checksUsed) FROM CheckReservation cr " +
           "WHERE cr.payer.id = :payerId AND cr.status = 'ACTIVE'")
    Long countTotalAvailableChecksForPayer(@Param("payerId") UUID payerId);

    /**
     * Finds exhausted reservations (all checks used).
     *
     * @return List of exhausted reservations
     */
    @Query("SELECT cr FROM CheckReservation cr WHERE cr.status = 'EXHAUSTED' " +
           "ORDER BY cr.updatedAt DESC")
    List<CheckReservation> findExhaustedReservations();

    /**
     * Checks if a check number range overlaps with existing reservations.
     * Used for validation when creating new reservations.
     *
     * @param payerId Payer ID
     * @param startNumber Start check number
     * @param endNumber End check number
     * @return true if range overlaps with existing reservations
     */
    @Query("SELECT COUNT(cr) > 0 FROM CheckReservation cr WHERE cr.payer.id = :payerId " +
           "AND cr.status != 'CANCELLED' " +
           "AND ((cr.checkNumberStart <= :startNumber AND cr.checkNumberEnd >= :startNumber) " +
           "OR (cr.checkNumberStart <= :endNumber AND cr.checkNumberEnd >= :endNumber) " +
           "OR (cr.checkNumberStart >= :startNumber AND cr.checkNumberEnd <= :endNumber))")
    boolean hasOverlappingRange(@Param("payerId") UUID payerId,
                                @Param("startNumber") String startNumber,
                                @Param("endNumber") String endNumber);
}
