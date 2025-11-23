package com.healthcare.edi835.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when payment is required for a bucket but not assigned.
 * Prevents EDI file generation from proceeding without required payment information.
 */
@Getter
public class PaymentRequiredException extends RuntimeException {

    private final UUID bucketId;

    public PaymentRequiredException(String message) {
        super(message);
        this.bucketId = null;
    }

    public PaymentRequiredException(UUID bucketId, String message) {
        super(String.format("Payment required for bucket %s: %s", bucketId, message));
        this.bucketId = bucketId;
    }

    public PaymentRequiredException(UUID bucketId) {
        super(String.format("Payment must be assigned to bucket %s before EDI generation", bucketId));
        this.bucketId = bucketId;
    }
}
