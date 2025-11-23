package com.healthcare.edi835.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when a check payment cannot be found.
 */
@Getter
public class CheckPaymentNotFoundException extends RuntimeException {

    private final UUID checkPaymentId;
    private final String checkNumber;

    public CheckPaymentNotFoundException(UUID checkPaymentId) {
        super(String.format("Check payment not found: %s", checkPaymentId));
        this.checkPaymentId = checkPaymentId;
        this.checkNumber = null;
    }

    public CheckPaymentNotFoundException(String checkNumber) {
        super(String.format("Check payment not found for check number: %s", checkNumber));
        this.checkPaymentId = null;
        this.checkNumber = checkNumber;
    }

    public CheckPaymentNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.checkPaymentId = null;
        this.checkNumber = null;
    }
}
