package com.healthcare.edi835.service;

import com.healthcare.edi835.config.CheckReservationTransactionConfig;
import com.healthcare.edi835.entity.CheckReservation;
import com.healthcare.edi835.entity.Payer;
import com.healthcare.edi835.exception.NoAvailableChecksException;
import com.healthcare.edi835.model.dto.CheckReservationDTO;
import com.healthcare.edi835.model.dto.CreateCheckReservationRequest;
import com.healthcare.edi835.model.dto.ReservedCheckInfo;
import com.healthcare.edi835.repository.CheckPaymentConfigRepository;
import com.healthcare.edi835.repository.CheckReservationRepository;
import com.healthcare.edi835.repository.PayerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing check reservations (pre-allocated check number ranges).
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Create and manage check reservations</li>
 *   <li>Allocate check numbers from active reservations</li>
 *   <li>Track usage and exhaustion</li>
 *   <li>Monitor low stock and trigger alerts</li>
 *   <li>Cancel reservations</li>
 * </ul>
 *
 * <p>Reservation Lifecycle:</p>
 * <pre>
 * ACTIVE → EXHAUSTED (all checks used)
 *   ↓
 * CANCELLED (admin action)
 * </pre>
 */
@Slf4j
@Service
public class CheckReservationService {

    private final CheckReservationRepository reservationRepository;
    private final PayerRepository payerRepository;
    private final CheckPaymentConfigRepository configRepository;
    private final CheckReservationTransactionConfig transactionConfig;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public CheckReservationService(
            CheckReservationRepository reservationRepository,
            PayerRepository payerRepository,
            CheckPaymentConfigRepository configRepository,
            CheckReservationTransactionConfig transactionConfig,
            PlatformTransactionManager transactionManager) {
        this.reservationRepository = reservationRepository;
        this.payerRepository = payerRepository;
        this.configRepository = configRepository;
        this.transactionConfig = transactionConfig;

        // Create a TransactionTemplate configured for REQUIRES_NEW propagation
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Checks if separate transactions are enabled for check reservations.
     *
     * @return true if REQUIRES_NEW should be used (PostgreSQL), false otherwise (SQLite)
     */
    public boolean useSeparateTransaction() {
        return transactionConfig.isUseSeparateTransaction();
    }

    /**
     * Creates a new check reservation range.
     *
     * @param request Reservation creation request
     * @return Created reservation
     */
    @Transactional
    public CheckReservation createReservation(CreateCheckReservationRequest request) {
        log.info("Creating check reservation: start={}, end={}, payer={}",
                request.getCheckNumberStart(), request.getCheckNumberEnd(), request.getPayerId());

        // Validate payer exists
        UUID payerId = UUID.fromString(request.getPayerId());
        Payer payer = payerRepository.findById(payerId)
                .orElseThrow(() -> new IllegalArgumentException("Payer not found: " + payerId));

        // Calculate total checks
        int totalChecks = calculateTotalChecks(request.getCheckNumberStart(), request.getCheckNumberEnd());

        // Validate no overlapping ranges
        if (reservationRepository.hasOverlappingRange(
                payerId, request.getCheckNumberStart(), request.getCheckNumberEnd())) {
            throw new IllegalArgumentException(
                    "Check number range overlaps with existing reservation");
        }

        // Create reservation
        CheckReservation reservation = CheckReservation.builder()
                .checkNumberStart(request.getCheckNumberStart())
                .checkNumberEnd(request.getCheckNumberEnd())
                .totalChecks(totalChecks)
                .checksUsed(0)
                .bankName(request.getBankName())
                .routingNumber(request.getRoutingNumber())
                .accountNumberLast4(request.getAccountLast4())
                .payer(payer)
                .status(CheckReservation.ReservationStatus.ACTIVE)
                .createdBy(request.getCreatedBy())
                .build();

        reservation = reservationRepository.save(reservation);

        log.info("Check reservation created: id={}, totalChecks={}", reservation.getId(), totalChecks);

        return reservation;
    }

    /**
     * Gets next available check number from active reservations and reserves it.
     * Uses FIFO order (oldest reservation first).
     *
     * <p><b>Transaction Behavior:</b> This method's transaction behavior is configurable:</p>
     * <ul>
     *   <li><b>PostgreSQL (useSeparateTransaction=true):</b> Uses REQUIRES_NEW propagation
     *       to commit check reservation independently. Requires compensation if outer
     *       transaction fails.</li>
     *   <li><b>SQLite (useSeparateTransaction=false):</b> Participates in the calling
     *       transaction. No compensation needed as everything rolls back together.</li>
     * </ul>
     *
     * @param payerId Payer ID
     * @return Reserved check information
     * @throws NoAvailableChecksException if no checks available
     */
    public ReservedCheckInfo getAndReserveNextCheck(UUID payerId) {
        return getAndReserveNextCheck(payerId, null);
    }

    /**
     * Gets next available check number from active reservations and reserves it.
     * Uses FIFO order (oldest reservation first).
     *
     * <p><b>Transaction Behavior:</b> This method's transaction behavior is configurable:</p>
     * <ul>
     *   <li><b>PostgreSQL (useSeparateTransaction=true):</b> Uses REQUIRES_NEW propagation
     *       to commit check reservation independently. Requires compensation if outer
     *       transaction fails.</li>
     *   <li><b>SQLite (useSeparateTransaction=false):</b> Participates in the calling
     *       transaction. No compensation needed as everything rolls back together.</li>
     * </ul>
     *
     * @param payerId  Payer ID
     * @param bucketId Bucket ID for audit trail (nullable for backwards compatibility)
     * @return Reserved check information
     * @throws NoAvailableChecksException if no checks available
     */
    public ReservedCheckInfo getAndReserveNextCheck(UUID payerId, UUID bucketId) {
        if (transactionConfig.isUseSeparateTransaction()) {
            // PostgreSQL mode: Use REQUIRES_NEW for independent transaction
            log.debug("Using REQUIRES_NEW transaction for check reservation (PostgreSQL mode)");
            return requiresNewTransactionTemplate.execute(status ->
                    doReserveNextCheck(payerId, bucketId));
        } else {
            // SQLite mode: Participate in existing transaction
            log.debug("Using existing transaction for check reservation (SQLite mode)");
            return doReserveNextCheck(payerId, bucketId);
        }
    }

    /**
     * Internal method that performs the actual check reservation logic.
     * Transaction boundary is controlled by the calling method.
     *
     * @param payerId  Payer ID
     * @param bucketId Bucket ID for audit trail
     * @return Reserved check information
     */
    @Transactional
    protected ReservedCheckInfo doReserveNextCheck(UUID payerId, UUID bucketId) {
        log.debug("Getting next available check for payer {} (bucket: {})", payerId, bucketId);

        // Find first available reservation using explicit ordering to avoid non-unique result
        List<CheckReservation> availableReservations = reservationRepository
                .findAvailableForPayerOrderByCreatedAt(payerId);

        if (availableReservations.isEmpty()) {
            log.warn("No available check reservations for payer {} (bucket: {})", payerId, bucketId);
            throw new NoAvailableChecksException(payerId,
                    "No active reservations with available checks");
        }

        // Use the first (oldest) reservation
        CheckReservation reservation = availableReservations.get(0);

        // Get next check number
        String checkNumber = reservation.getNextCheckNumber();
        if (checkNumber == null) {
            throw new NoAvailableChecksException(payerId,
                    "Reservation exhausted unexpectedly");
        }

        // Save updated reservation
        reservationRepository.save(reservation);

        // Check if low stock alert needed
        int lowStockThreshold = configRepository.getLowStockAlertThreshold();
        if (reservation.isLowStock(lowStockThreshold)) {
            log.warn("LOW STOCK ALERT: Reservation {} for payer {} has only {} checks remaining",
                    reservation.getId(), payerId, reservation.getChecksRemaining());
            // TODO: Send email alert to configured recipients
        }

        log.info("RESERVED check {} from reservation {} for payer {} (bucket: {}) - {} checks remaining",
                checkNumber, reservation.getId(), payerId, bucketId, reservation.getChecksRemaining());

        return ReservedCheckInfo.builder()
                .checkNumber(checkNumber)
                .checkDate(LocalDate.now())  // Use current date for auto-assigned checks
                .bankName(reservation.getBankName())
                .routingNumber(reservation.getRoutingNumber())
                .accountLast4(reservation.getAccountNumberLast4())
                .reservationId(reservation.getId().toString())
                .build();
    }

    /**
     * Releases a previously reserved check back to the pool.
     * This is a compensation operation called when check assignment fails after reservation.
     *
     * <p><b>Transaction Behavior:</b> Uses REQUIRES_NEW when configured for PostgreSQL
     * to ensure the release commits even if the caller's transaction is rolling back.
     * For SQLite, this is a no-op since everything rolls back together.</p>
     *
     * @param checkNumber   The check number to release
     * @param reservationId The reservation ID the check came from
     * @param reason        Reason for releasing the check (for audit)
     */
    public void releaseReservedCheck(String checkNumber, UUID reservationId, String reason) {
        if (!transactionConfig.isUseSeparateTransaction()) {
            // SQLite mode: No compensation needed - transaction rolls back together
            log.debug("Skipping check release compensation (SQLite mode - transaction will rollback)");
            return;
        }

        // PostgreSQL mode: Use REQUIRES_NEW for independent compensation
        log.warn("RELEASING reserved check {} back to reservation {} - Reason: {}",
                checkNumber, reservationId, reason);

        requiresNewTransactionTemplate.execute(status -> {
            doReleaseReservedCheck(checkNumber, reservationId, reason);
            return null;
        });
    }

    /**
     * Internal method that performs the actual check release logic.
     */
    @Transactional
    protected void doReleaseReservedCheck(String checkNumber, UUID reservationId, String reason) {
        CheckReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> {
                    log.error("Cannot release check {} - reservation {} not found", checkNumber, reservationId);
                    return new IllegalArgumentException("Reservation not found: " + reservationId);
                });

        // Decrement checksUsed to return the check to the pool
        int currentUsed = reservation.getChecksUsed();
        if (currentUsed > 0) {
            reservation.setChecksUsed(currentUsed - 1);
            reservationRepository.save(reservation);
            log.info("RELEASED check {} back to reservation {} - checksUsed decremented from {} to {}",
                    checkNumber, reservationId, currentUsed, currentUsed - 1);
        } else {
            log.error("Cannot release check {} - reservation {} has checksUsed=0", checkNumber, reservationId);
        }
    }

    /**
     * Gets all active reservations.
     *
     * @return List of active reservation DTOs
     */
    public List<CheckReservationDTO> getActiveReservations() {
        List<CheckReservation> reservations = reservationRepository
                .findAllActiveWithCapacity();

        return reservations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gets reservations for a specific payer.
     *
     * @param payerId Payer ID
     * @return List of reservation DTOs
     */
    public List<CheckReservationDTO> getReservationsForPayer(UUID payerId) {
        List<CheckReservation> reservations = reservationRepository
                .findActiveByPayerId(payerId);

        return reservations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gets low stock reservations.
     *
     * @return List of reservations running low on checks
     */
    public List<CheckReservationDTO> getLowStockReservations() {
        int threshold = configRepository.getLowStockAlertThreshold();
        List<CheckReservation> reservations = reservationRepository
                .findLowStockReservations(threshold);

        return reservations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Cancels a reservation.
     * Cannot cancel if reservation has been used.
     *
     * @param reservationId Reservation ID
     * @param cancelledBy   User cancelling the reservation
     */
    @Transactional
    public void cancelReservation(UUID reservationId, String cancelledBy) {
        log.info("Cancelling check reservation {}", reservationId);

        CheckReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        // Validate not used
        if (reservation.getChecksUsed() > 0) {
            throw new IllegalStateException(
                    String.format("Cannot cancel reservation - %d checks already used",
                            reservation.getChecksUsed()));
        }

        reservation.cancel();
        reservation.setUpdatedBy(cancelledBy);
        reservationRepository.save(reservation);

        log.info("Check reservation {} cancelled by {}", reservationId, cancelledBy);
    }

    /**
     * Gets all check reservations (all statuses).
     *
     * @return List of all reservation DTOs
     */
    public List<CheckReservationDTO> getAllReservations() {
        List<CheckReservation> reservations = reservationRepository.findAll();
        return reservations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gets a specific reservation by ID.
     *
     * @param reservationId Reservation ID
     * @return Reservation entity
     */
    public CheckReservation getReservation(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));
    }

    /**
     * Updates a check reservation.
     * Can only update certain fields like bank details.
     *
     * @param reservationId Reservation ID
     * @param request       Update request
     * @return Updated reservation
     */
    @Transactional
    public CheckReservation updateReservation(UUID reservationId, CreateCheckReservationRequest request) {
        log.info("Updating check reservation {}", reservationId);

        CheckReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        // Only allow updating bank details, not check number ranges
        reservation.setBankName(request.getBankName());
        reservation.setRoutingNumber(request.getRoutingNumber());
        reservation.setAccountNumberLast4(request.getAccountLast4());
        reservation.setUpdatedBy(request.getCreatedBy());

        reservation = reservationRepository.save(reservation);

        log.info("Check reservation {} updated successfully", reservationId);
        return reservation;
    }

    /**
     * Deletes a check reservation.
     * Can only delete unused reservations (checksUsed = 0).
     *
     * @param reservationId Reservation ID
     */
    @Transactional
    public void deleteReservation(UUID reservationId) {
        log.info("Deleting check reservation {}", reservationId);

        CheckReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        // Validate not used
        if (reservation.getChecksUsed() > 0) {
            throw new IllegalStateException(
                    String.format("Cannot delete reservation - %d checks already used",
                            reservation.getChecksUsed()));
        }

        reservationRepository.delete(reservation);

        log.info("Check reservation {} deleted successfully", reservationId);
    }

    /**
     * Gets total available checks across all active reservations for a payer.
     *
     * @param payerId Payer ID
     * @return Total available check count
     */
    public long getTotalAvailableChecks(UUID payerId) {
        Long count = reservationRepository.countTotalAvailableChecksForPayer(payerId);
        return count != null ? count : 0L;
    }

    /**
     * Calculates total checks in a range.
     * Supports alphanumeric check numbers.
     *
     * @param startNumber Start check number
     * @param endNumber   End check number
     * @return Total checks in range
     */
    private int calculateTotalChecks(String startNumber, String endNumber) {
        // Extract numeric parts
        int start = extractNumericPart(startNumber);
        int end = extractNumericPart(endNumber);

        if (end < start) {
            throw new IllegalArgumentException(
                    "End check number must be >= start check number");
        }

        return (end - start) + 1;
    }

    /**
     * Extracts numeric part from check number.
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
     * Converts CheckReservation entity to DTO.
     */
    private CheckReservationDTO toDTO(CheckReservation reservation) {
        int lowStockThreshold = configRepository.getLowStockAlertThreshold();

        return CheckReservationDTO.builder()
                .id(reservation.getId() != null ? reservation.getId().toString() : null)
                .checkNumberStart(reservation.getCheckNumberStart())
                .checkNumberEnd(reservation.getCheckNumberEnd())
                .totalChecks(reservation.getTotalChecks())
                .checksUsed(reservation.getChecksUsed())
                .checksRemaining(reservation.getChecksRemaining())
                .usagePercentage(reservation.getUsagePercentage())
                .bankName(reservation.getBankName())
                .routingNumber(reservation.getRoutingNumber())
                .accountNumberLast4(reservation.getAccountNumberLast4())
                .paymentMethodId(reservation.getPaymentMethod() != null ?
                        reservation.getPaymentMethod().getId().toString() : null)
                .payerId(reservation.getPayer() != null ?
                        reservation.getPayer().getId().toString() : null)
                .payerName(reservation.getPayer() != null ?
                        reservation.getPayer().getPayerName() : null)
                .status(reservation.getStatus() != null ? reservation.getStatus().name() : null)
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .createdBy(reservation.getCreatedBy())
                .isLowStock(reservation.isLowStock(lowStockThreshold))
                .isExhausted(reservation.getStatus() == CheckReservation.ReservationStatus.EXHAUSTED)
                .build();
    }
}
