package com.healthcare.edi835.repository;

import com.healthcare.edi835.entity.CheckPayment;
import com.healthcare.edi835.entity.EdiFileBucket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CheckPayment entity.
 * Manages CRUD operations and queries for check payment records.
 */
@Repository
public interface CheckPaymentRepository extends JpaRepository<CheckPayment, UUID> {

    /**
     * Finds check payment by check number.
     *
     * @param checkNumber The check number
     * @return Optional containing check payment if found
     */
    Optional<CheckPayment> findByCheckNumber(String checkNumber);

    /**
     * Finds check payment by bucket.
     *
     * @param bucket The EDI file bucket
     * @return Optional containing check payment if found
     */
    Optional<CheckPayment> findByBucket(EdiFileBucket bucket);

    /**
     * Finds all check payments by status.
     *
     * @param status The check status
     * @return List of check payments with given status
     */
    List<CheckPayment> findByStatus(CheckPayment.CheckStatus status);

    /**
     * Finds all check payments by status ordered by check date.
     *
     * @param status The check status
     * @return List of check payments ordered by check date
     */
    List<CheckPayment> findByStatusOrderByCheckDateDesc(CheckPayment.CheckStatus status);

    /**
     * Finds check payments awaiting acknowledgment.
     * Returns checks in ASSIGNED status.
     *
     * @return List of checks awaiting acknowledgment
     */
    @Query("SELECT cp FROM CheckPayment cp WHERE cp.status = 'ASSIGNED' ORDER BY cp.assignedAt ASC")
    List<CheckPayment> findAwaitingAcknowledgment();

    /**
     * Finds check payments by payment method.
     *
     * @param paymentMethodId Payment method ID
     * @return List of check payments
     */
    @Query("SELECT cp FROM CheckPayment cp WHERE cp.paymentMethod.id = :paymentMethodId")
    List<CheckPayment> findByPaymentMethodId(@Param("paymentMethodId") UUID paymentMethodId);

    /**
     * Finds check payments that can be voided based on time limit.
     * Returns checks in ISSUED status within the void time window.
     *
     * @param cutoffTime The cutoff time (issued after this time can be voided)
     * @return List of voidable checks
     */
    @Query("SELECT cp FROM CheckPayment cp WHERE cp.status = 'ISSUED' " +
           "AND cp.issuedAt >= :cutoffTime ORDER BY cp.issuedAt DESC")
    List<CheckPayment> findVoidableChecks(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Counts checks by status.
     *
     * @param status The check status
     * @return Count of checks with given status
     */
    long countByStatus(CheckPayment.CheckStatus status);

    /**
     * Finds recent check payments for audit/reporting.
     *
     * @param since Timestamp to filter from
     * @return List of recent check payments
     */
    @Query("SELECT cp FROM CheckPayment cp WHERE cp.createdAt >= :since ORDER BY cp.createdAt DESC")
    List<CheckPayment> findRecentCheckPayments(@Param("since") LocalDateTime since);

    /**
     * Checks if a check number already exists.
     *
     * @param checkNumber The check number to check
     * @return true if exists, false otherwise
     */
    boolean existsByCheckNumber(String checkNumber);
}
