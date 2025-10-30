package com.healthcare.edi835.event;

import com.healthcare.edi835.entity.EdiFileBucket;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a bucket's status changes.
 * Used to trigger EDI file generation and other workflows.
 */
@Getter
public class BucketStatusChangeEvent extends ApplicationEvent {

    private final EdiFileBucket bucket;
    private final EdiFileBucket.BucketStatus previousStatus;
    private final EdiFileBucket.BucketStatus newStatus;

    public BucketStatusChangeEvent(Object source, EdiFileBucket bucket,
                                   EdiFileBucket.BucketStatus previousStatus,
                                   EdiFileBucket.BucketStatus newStatus) {
        super(source);
        this.bucket = bucket;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }

    /**
     * Checks if this is a transition to GENERATING status.
     */
    public boolean isTransitionToGenerating() {
        return newStatus == EdiFileBucket.BucketStatus.GENERATING;
    }

    /**
     * Checks if this is a transition to PENDING_APPROVAL status.
     */
    public boolean isTransitionToPendingApproval() {
        return newStatus == EdiFileBucket.BucketStatus.PENDING_APPROVAL;
    }

    /**
     * Checks if this is a transition to COMPLETED status.
     */
    public boolean isTransitionToCompleted() {
        return newStatus == EdiFileBucket.BucketStatus.COMPLETED;
    }

    @Override
    public String toString() {
        return String.format("BucketStatusChangeEvent[bucketId=%s, %s -> %s]",
                bucket.getBucketId(), previousStatus, newStatus);
    }
}
