package com.healthcare.edi835.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when no check reservations are available for auto-assignment.
 * Indicates that all check reservation ranges are exhausted or no active reservations exist.
 */
@Getter
public class NoAvailableChecksException extends RuntimeException {

    private final UUID payerId;

    public NoAvailableChecksException(String message) {
        super(message);
        this.payerId = null;
    }

    public NoAvailableChecksException(UUID payerId) {
        super(String.format("No available checks for payer %s - all reservations exhausted", payerId));
        this.payerId = payerId;
    }

    public NoAvailableChecksException(UUID payerId, String message) {
        super(String.format("No available checks for payer %s: %s", payerId, message));
        this.payerId = payerId;
    }
}
