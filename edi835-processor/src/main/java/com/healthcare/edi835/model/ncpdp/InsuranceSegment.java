package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Insurance Segment (AM04)
 *
 * <p>Contains insurance-related information including cardholder ID and prescription origin.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AM04*01*R*1*</pre>
 *
 * <p><strong>Field Mapping:</strong></p>
 * <ul>
 *   <li>Field 1: Cardholder ID Qualifier (01=Person Code)</li>
 *   <li>Field 2: Prescription Origin Code (R=Written, C=Compound, E=Electronic)</li>
 *   <li>Field 3: Fill Number (1=New, 2+ =Refill)</li>
 * </ul>
 *
 * <p><strong>Prescription Origin Codes:</strong></p>
 * <ul>
 *   <li>1 = Written</li>
 *   <li>2 = Telephone</li>
 *   <li>3 = Electronic</li>
 *   <li>4 = Facsimile</li>
 *   <li>5 = Pharmacy</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceSegment {

    /**
     * Cardholder ID qualifier (typically "01" for person code)
     */
    private String cardholderIdQualifier;

    /**
     * Prescription origin code (R=Written, C=Compound, E=Electronic, etc.)
     */
    private String prescriptionOriginCode;

    /**
     * Fill number (1=New prescription, 2+=Refill)
     */
    private Integer fillNumber;
}
