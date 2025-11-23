package com.healthcare.edi835.exception;

import com.healthcare.edi835.entity.CheckPayment;
import lombok.Getter;

/**
 * Exception thrown when a check payment operation is attempted in an invalid state.
 * For example, trying to acknowledge a check that's not in ASSIGNED status.
 */
@Getter
public class InvalidCheckStateException extends RuntimeException {

    private final CheckPayment.CheckStatus currentStatus;
    private final CheckPayment.CheckStatus requiredStatus;
    private final String operation;

    public InvalidCheckStateException(String message) {
        super(message);
        this.currentStatus = null;
        this.requiredStatus = null;
        this.operation = null;
    }

    public InvalidCheckStateException(String operation, CheckPayment.CheckStatus currentStatus,
                                     CheckPayment.CheckStatus requiredStatus) {
        super(String.format("Cannot %s - check is in %s status, must be %s",
                operation, currentStatus, requiredStatus));
        this.operation = operation;
        this.currentStatus = currentStatus;
        this.requiredStatus = requiredStatus;
    }

    public InvalidCheckStateException(String operation, CheckPayment.CheckStatus currentStatus) {
        super(String.format("Cannot %s - check is in %s status", operation, currentStatus));
        this.operation = operation;
        this.currentStatus = currentStatus;
        this.requiredStatus = null;
    }
}
