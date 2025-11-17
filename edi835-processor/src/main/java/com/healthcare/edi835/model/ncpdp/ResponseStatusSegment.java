package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response Status Segment (AN02)
 *
 * <p>Contains the transaction response status (approved or rejected).</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AN02*A*00*APPROVED*</pre>
 *
 * <p><strong>Field Mapping:</strong></p>
 * <ul>
 *   <li>Field 1: Response Status (A=Approved, R=Rejected)</li>
 *   <li>Field 2: Response Code (00=Approved, non-zero=Error/Rejection codes)</li>
 *   <li>Field 3: Response Message</li>
 * </ul>
 *
 * <p><strong>Response Status Codes:</strong></p>
 * <ul>
 *   <li>A = Approved</li>
 *   <li>R = Rejected</li>
 *   <li>P = Paid (for reversals)</li>
 *   <li>C = Captured (for electronic claims)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseStatusSegment {

    /**
     * Response status (A=Approved, R=Rejected, P=Paid, C=Captured)
     */
    private String responseStatus;

    /**
     * Response code (00=Approved, other codes indicate specific rejection reasons)
     */
    private String responseCode;

    /**
     * Response message (e.g., "APPROVED", "REJECTED - INVALID BIN")
     */
    private String responseMessage;

    /**
     * Checks if the transaction was approved
     *
     * @return true if status is 'A' (Approved)
     */
    public boolean isApproved() {
        return "A".equals(responseStatus);
    }

    /**
     * Checks if the transaction was rejected
     *
     * @return true if status is 'R' (Rejected)
     */
    public boolean isRejected() {
        return "R".equals(responseStatus);
    }
}
