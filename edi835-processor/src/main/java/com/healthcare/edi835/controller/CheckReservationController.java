package com.healthcare.edi835.controller;

import com.healthcare.edi835.entity.CheckReservation;
import com.healthcare.edi835.model.dto.CheckReservationDTO;
import com.healthcare.edi835.model.dto.CreateCheckReservationRequest;
import com.healthcare.edi835.service.CheckReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for check reservation operations.
 * Provides endpoints for managing pre-allocated check number ranges.
 *
 * <p>Base Path: /api/v1/check-reservations</p>
 *
 * <p>Reservation Lifecycle:</p>
 * <pre>
 * ACTIVE → EXHAUSTED (all checks used)
 *   ↓
 * CANCELLED (admin action)
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/check-reservations")
public class CheckReservationController {

    private final CheckReservationService reservationService;

    public CheckReservationController(CheckReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // ==================== Create Reservation ====================

    /**
     * Creates a new check reservation range.
     * Pre-allocates a range of check numbers for auto-approval workflow.
     *
     * POST /api/v1/check-reservations
     */
    @PostMapping
    public ResponseEntity<?> createReservation(@Valid @RequestBody CreateCheckReservationRequest request) {
        log.info("POST /api/v1/check-reservations - Creating check reservation: start={}, end={}, payer={}",
                request.getCheckNumberStart(), request.getCheckNumberEnd(), request.getPayerId());

        try {
            CheckReservation reservation = reservationService.createReservation(request);
            CheckReservationDTO dto = convertToDTO(reservation);

            log.info("Check reservation created: id={}, totalChecks={}",
                    reservation.getId(), reservation.getTotalChecks());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(dto);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request for check reservation: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating check reservation", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create reservation: " + e.getMessage()));
        }
    }

    // ==================== Get Reservations ====================

    /**
     * Gets all check reservations (all statuses).
     *
     * GET /api/v1/check-reservations
     */
    @GetMapping
    public ResponseEntity<?> getAllReservations() {
        log.debug("GET /api/v1/check-reservations - Retrieving all reservations");

        try {
            List<CheckReservationDTO> reservations = reservationService.getAllReservations();
            return ResponseEntity.ok(reservations);

        } catch (Exception e) {
            log.error("Error retrieving all reservations", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve reservations: " + e.getMessage()));
        }
    }

    /**
     * Gets a specific reservation by ID.
     *
     * GET /api/v1/check-reservations/{reservationId}
     */
    @GetMapping("/{reservationId}")
    public ResponseEntity<?> getReservation(@PathVariable UUID reservationId) {
        log.debug("GET /api/v1/check-reservations/{} - Retrieving reservation", reservationId);

        try {
            CheckReservation reservation = reservationService.getReservation(reservationId);
            CheckReservationDTO dto = convertToDTO(reservation);
            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            log.error("Reservation not found: {}", reservationId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Reservation not found"));
        } catch (Exception e) {
            log.error("Error retrieving reservation {}", reservationId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve reservation: " + e.getMessage()));
        }
    }

    /**
     * Gets all active reservations.
     * Returns reservations with available checks (ACTIVE status, checks remaining > 0).
     *
     * GET /api/v1/check-reservations/active
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActiveReservations() {
        log.debug("GET /api/v1/check-reservations/active - Retrieving active reservations");

        try {
            List<CheckReservationDTO> reservations = reservationService.getActiveReservations();
            return ResponseEntity.ok(reservations);

        } catch (Exception e) {
            log.error("Error retrieving active reservations", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve reservations: " + e.getMessage()));
        }
    }

    /**
     * Gets reservations for a specific payer.
     *
     * GET /api/v1/check-reservations/payer/{payerId}
     */
    @GetMapping("/payer/{payerId}")
    public ResponseEntity<?> getReservationsForPayer(@PathVariable UUID payerId) {
        log.debug("GET /api/v1/check-reservations/payer/{} - Retrieving reservations", payerId);

        try {
            List<CheckReservationDTO> reservations = reservationService.getReservationsForPayer(payerId);
            return ResponseEntity.ok(reservations);

        } catch (Exception e) {
            log.error("Error retrieving reservations for payer {}", payerId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve reservations: " + e.getMessage()));
        }
    }

    /**
     * Gets low stock reservations.
     * Returns reservations below configured threshold.
     *
     * GET /api/v1/check-reservations/low-stock
     */
    @GetMapping("/low-stock")
    public ResponseEntity<?> getLowStockReservations() {
        log.debug("GET /api/v1/check-reservations/low-stock - Retrieving low stock reservations");

        try {
            List<CheckReservationDTO> reservations = reservationService.getLowStockReservations();
            return ResponseEntity.ok(reservations);

        } catch (Exception e) {
            log.error("Error retrieving low stock reservations", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve low stock reservations: " + e.getMessage()));
        }
    }

    // ==================== Reservation Statistics ====================

    /**
     * Gets total available checks for a payer.
     * Sum of available checks across all active reservations.
     *
     * GET /api/v1/check-reservations/payer/{payerId}/available-count
     */
    @GetMapping("/payer/{payerId}/available-count")
    public ResponseEntity<?> getAvailableChecksForPayer(@PathVariable UUID payerId) {
        log.debug("GET /api/v1/check-reservations/payer/{}/available-count - Counting available checks", payerId);

        try {
            long availableChecks = reservationService.getTotalAvailableChecks(payerId);
            return ResponseEntity.ok(Map.of(
                    "payerId", payerId.toString(),
                    "availableChecks", availableChecks
            ));

        } catch (Exception e) {
            log.error("Error counting available checks for payer {}", payerId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to count available checks: " + e.getMessage()));
        }
    }

    /**
     * Gets reservation summary statistics.
     * Provides overview of all reservations.
     *
     * GET /api/v1/check-reservations/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getReservationSummary() {
        log.debug("GET /api/v1/check-reservations/summary - Getting reservation summary");

        try {
            List<CheckReservationDTO> allReservations = reservationService.getActiveReservations();
            List<CheckReservationDTO> lowStockReservations = reservationService.getLowStockReservations();

            long totalReservations = allReservations.size();
            long totalAvailableChecks = allReservations.stream()
                    .mapToLong(r -> r.getChecksRemaining() != null ? r.getChecksRemaining() : 0)
                    .sum();
            long totalUsedChecks = allReservations.stream()
                    .mapToLong(r -> r.getChecksUsed() != null ? r.getChecksUsed() : 0)
                    .sum();
            long lowStockCount = lowStockReservations.size();

            return ResponseEntity.ok(Map.of(
                    "totalActiveReservations", totalReservations,
                    "totalAvailableChecks", totalAvailableChecks,
                    "totalUsedChecks", totalUsedChecks,
                    "lowStockReservations", lowStockCount,
                    "needsAttention", lowStockCount > 0
            ));

        } catch (Exception e) {
            log.error("Error generating reservation summary", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate summary: " + e.getMessage()));
        }
    }

    // ==================== Update Reservation ====================

    /**
     * Updates a check reservation.
     * Can only update certain fields like bank details.
     *
     * PUT /api/v1/check-reservations/{reservationId}
     */
    @PutMapping("/{reservationId}")
    public ResponseEntity<?> updateReservation(
            @PathVariable UUID reservationId,
            @Valid @RequestBody CreateCheckReservationRequest request) {
        log.info("PUT /api/v1/check-reservations/{} - Updating reservation", reservationId);

        try {
            CheckReservation reservation = reservationService.updateReservation(reservationId, request);
            CheckReservationDTO dto = convertToDTO(reservation);

            log.info("Check reservation {} updated successfully", reservationId);

            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            log.error("Cannot update reservation: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating reservation {}", reservationId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update reservation: " + e.getMessage()));
        }
    }

    // ==================== Delete Reservation ====================

    /**
     * Deletes a check reservation.
     * Can only delete unused reservations (checksUsed = 0).
     *
     * DELETE /api/v1/check-reservations/{reservationId}
     */
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<?> deleteReservation(@PathVariable UUID reservationId) {
        log.info("DELETE /api/v1/check-reservations/{} - Deleting reservation", reservationId);

        try {
            reservationService.deleteReservation(reservationId);

            log.info("Check reservation {} deleted successfully", reservationId);

            return ResponseEntity.ok(Map.of(
                    "message", "Reservation deleted successfully",
                    "reservationId", reservationId.toString()
            ));

        } catch (IllegalArgumentException e) {
            log.error("Cannot delete reservation: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("Cannot delete reservation: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting reservation {}", reservationId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete reservation: " + e.getMessage()));
        }
    }

    // ==================== Cancel Reservation ====================

    /**
     * Cancels a check reservation.
     * Can only cancel reservations that haven't been used (checksUsed = 0).
     *
     * POST /api/v1/check-reservations/{reservationId}/cancel
     */
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<?> cancelReservation(
            @PathVariable UUID reservationId,
            @RequestParam String cancelledBy) {
        log.info("POST /api/v1/check-reservations/{}/cancel - Cancelling reservation by {}",
                reservationId, cancelledBy);

        try {
            reservationService.cancelReservation(reservationId, cancelledBy);

            log.info("Check reservation {} cancelled by {}", reservationId, cancelledBy);

            return ResponseEntity.ok(Map.of(
                    "message", "Reservation cancelled successfully",
                    "reservationId", reservationId.toString()
            ));

        } catch (IllegalArgumentException e) {
            log.error("Cannot cancel reservation: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("Cannot cancel reservation: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error cancelling reservation {}", reservationId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel reservation: " + e.getMessage()));
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Converts CheckReservation entity to DTO.
     */
    private CheckReservationDTO convertToDTO(CheckReservation reservation) {
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
                .isLowStock(false)  // Calculated in service
                .isExhausted(reservation.getStatus() == CheckReservation.ReservationStatus.EXHAUSTED)
                .build();
    }
}
