package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prescriber Segment (AM11)
 *
 * <p>Contains prescriber identification and contact information.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AM11*00123456789*1*1234567890*JONES*ROBERT*D*555-123-4567*</pre>
 *
 * <p><strong>Field Mapping:</strong></p>
 * <ul>
 *   <li>Field 1: Prescriber ID (NPI)</li>
 *   <li>Field 2: Prescriber ID Qualifier (1=NPI)</li>
 *   <li>Field 3: Prescriber License Number</li>
 *   <li>Field 4: Last Name</li>
 *   <li>Field 5: First Name</li>
 *   <li>Field 6: Middle Initial</li>
 *   <li>Field 7: Phone Number</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriberSegment {

    /**
     * Prescriber ID (typically NPI)
     */
    private String prescriberId;

    /**
     * Prescriber ID qualifier (1=NPI, 2=State License, etc.)
     */
    private String prescriberIdQualifier;

    /**
     * Prescriber license number
     */
    private String licenseNumber;

    /**
     * Prescriber last name
     */
    private String lastName;

    /**
     * Prescriber first name
     */
    private String firstName;

    /**
     * Prescriber middle initial
     */
    private String middleInitial;

    /**
     * Prescriber phone number
     */
    private String phoneNumber;

    /**
     * Gets full prescriber name
     *
     * @return formatted full name
     */
    public String getFullName() {
        StringBuilder name = new StringBuilder();
        if (firstName != null) {
            name.append(firstName);
        }
        if (middleInitial != null && !middleInitial.isEmpty()) {
            name.append(" ").append(middleInitial);
        }
        if (lastName != null) {
            if (name.length() > 0) {
                name.append(" ");
            }
            name.append(lastName);
        }
        return name.toString();
    }
}
