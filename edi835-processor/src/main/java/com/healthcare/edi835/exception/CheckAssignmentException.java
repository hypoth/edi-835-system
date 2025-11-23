package com.healthcare.edi835.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Exception thrown when check assignment fails during bucket approval.
 * This exception propagates up to trigger transaction rollback, ensuring
 * the entire approval process is atomic - either everything succeeds or
 * everything is rolled back.
 *
 * <p>Carries context about the failure including bucket ID and the underlying cause.</p>
 */
@Getter
public class CheckAssignmentException extends RuntimeException {

    private final UUID bucketId;
    private final String assignmentMode;

    public CheckAssignmentException(String message) {
        super(message);
        this.bucketId = null;
        this.assignmentMode = null;
    }

    public CheckAssignmentException(UUID bucketId, String message) {
        super(String.format("Check assignment failed for bucket %s: %s", bucketId, message));
        this.bucketId = bucketId;
        this.assignmentMode = null;
    }

    public CheckAssignmentException(UUID bucketId, String assignmentMode, String message) {
        super(String.format("Check assignment (%s) failed for bucket %s: %s",
                assignmentMode, bucketId, message));
        this.bucketId = bucketId;
        this.assignmentMode = assignmentMode;
    }

    public CheckAssignmentException(UUID bucketId, String assignmentMode, Throwable cause) {
        super(String.format("Check assignment (%s) failed for bucket %s: %s",
                assignmentMode, bucketId, cause.getMessage()), cause);
        this.bucketId = bucketId;
        this.assignmentMode = assignmentMode;
    }

    public CheckAssignmentException(UUID bucketId, Throwable cause) {
        super(String.format("Check assignment failed for bucket %s: %s",
                bucketId, cause.getMessage()), cause);
        this.bucketId = bucketId;
        this.assignmentMode = null;
    }
}
