package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Additional Documentation Segment (AM21)
 *
 * <p>Contains additional documentation like DEA number, state license, prior authorization numbers.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AM21*01*20241014*STATE123456*TX*1234567*</pre>
 * <pre>AM21*03*PA123456789*</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdditionalDocumentationSegment {

    /**
     * Documentation type
     * 01 = Prescriber DEA number
     * 03 = Prior authorization number
     */
    private String documentationType;

    /**
     * Documentation date (if applicable)
     */
    private String documentationDate;

    /**
     * DEA number (if type=01)
     */
    private String deaNumber;

    /**
     * State (if type=01)
     */
    private String state;

    /**
     * State license number (if type=01)
     */
    private String stateLicenseNumber;

    /**
     * Prior authorization number (if type=03)
     */
    private String priorAuthorizationNumber;
}
