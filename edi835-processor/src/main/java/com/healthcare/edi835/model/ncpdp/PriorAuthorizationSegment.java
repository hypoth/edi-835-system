package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prior Authorization Segment (AM19)
 *
 * <p>Contains prior prescription data and prior authorization information.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AM19*20*20240714*</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorAuthorizationSegment {

    /**
     * Prior authorization type
     */
    private String authorizationType;

    /**
     * Prior prescription date (YYYYMMDD)
     */
    private String priorPrescriptionDate;
}
