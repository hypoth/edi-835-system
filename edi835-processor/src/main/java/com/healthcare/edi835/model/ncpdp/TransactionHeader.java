package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transaction Header Segment (AM01)
 *
 * <p>Contains basic transaction identification and timestamp information.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AM01*1234567*PHARMACY001*20241014*143025*1*</pre>
 *
 * <p><strong>Field Mapping:</strong></p>
 * <ul>
 *   <li>Field 1: Service Provider ID</li>
 *   <li>Field 2: Pharmacy ID</li>
 *   <li>Field 3: Date (YYYYMMDD)</li>
 *   <li>Field 4: Time (HHmmss)</li>
 *   <li>Field 5: Transaction Count</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHeader {

    /**
     * Service Provider ID (typically same as pharmacy ID)
     */
    private String serviceProviderId;

    /**
     * Pharmacy ID (e.g., "PHARMACY001")
     */
    private String pharmacyId;

    /**
     * Transaction date in YYYYMMDD format (e.g., "20241014")
     */
    private String date;

    /**
     * Transaction time in HHmmss format (e.g., "143025")
     */
    private String time;

    /**
     * Transaction count
     */
    private String transactionCount;
}
