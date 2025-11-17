package com.healthcare.edi835.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for NCPDP processing status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NcpdpStatusResponse {

    /**
     * Number of claims with PENDING status
     */
    private long pending;

    /**
     * Number of claims with PROCESSING status
     */
    private long processing;

    /**
     * Number of claims with PROCESSED status
     */
    private long processed;

    /**
     * Number of claims with FAILED status
     */
    private long failed;

    /**
     * Total number of claims
     */
    public long getTotal() {
        return pending + processing + processed + failed;
    }

    /**
     * Success rate percentage
     */
    public double getSuccessRate() {
        long total = getTotal();
        if (total == 0) {
            return 0.0;
        }
        return (double) processed / total * 100.0;
    }
}
