package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Patient Segment (AM07)
 *
 * <p>Contains cardholder information, patient demographics, and address.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AM07*BCBSIL*60054*123456789*01*SMITH*JOHN*A*19850515*M*456 PATIENT AVE*CHICAGO*IL*60602*</pre>
 *
 * <p><strong>Field Mapping:</strong></p>
 * <ul>
 *   <li>Field 1: Carrier ID (Payer: BCBSIL, CIGNA, AETNA, etc.)</li>
 *   <li>Field 2: BIN Number</li>
 *   <li>Field 3: Cardholder ID Number (Member ID)</li>
 *   <li>Field 4: Cardholder ID Qualifier</li>
 *   <li>Field 5: Last Name</li>
 *   <li>Field 6: First Name</li>
 *   <li>Field 7: Middle Initial</li>
 *   <li>Field 8: Date of Birth (YYYYMMDD)</li>
 *   <li>Field 9: Gender (M/F)</li>
 *   <li>Field 10: Address</li>
 *   <li>Field 11: City</li>
 *   <li>Field 12: State</li>
 *   <li>Field 13: ZIP Code</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientSegment {

    /**
     * Carrier ID / Payer ID (e.g., "BCBSIL", "CIGNA", "AETNA")
     */
    private String carrierId;

    /**
     * BIN Number (Bank Identification Number for pharmacy benefit)
     */
    private String binNumber;

    /**
     * Cardholder ID Number (Member ID)
     */
    private String cardholderIdNumber;

    /**
     * Cardholder ID Qualifier (typically "01")
     */
    private String cardholderIdQualifier;

    /**
     * Patient last name
     */
    private String lastName;

    /**
     * Patient first name
     */
    private String firstName;

    /**
     * Patient middle initial
     */
    private String middleInitial;

    /**
     * Date of birth in YYYYMMDD format
     */
    private String dateOfBirth;

    /**
     * Gender (M=Male, F=Female)
     */
    private String gender;

    /**
     * Street address
     */
    private String address;

    /**
     * City
     */
    private String city;

    /**
     * State abbreviation (2 characters)
     */
    private String state;

    /**
     * ZIP code
     */
    private String zip;

    /**
     * Gets full patient name
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
