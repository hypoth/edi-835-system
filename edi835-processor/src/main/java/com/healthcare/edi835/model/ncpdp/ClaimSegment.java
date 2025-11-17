package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Claim Segment (AM13)
 *
 * <p>Contains prescription/service details including NDC, quantity, and days supply.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AM13*20241014*12345*1*00002012345678*LIPITOR*20MG*TAB*30*EA*1*0*0*3*</pre>
 *
 * <p><strong>Field Mapping:</strong></p>
 * <ul>
 *   <li>Field 1: Date of Service (YYYYMMDD)</li>
 *   <li>Field 2: Prescription/Service Reference Number</li>
 *   <li>Field 3: Fill Number</li>
 *   <li>Field 4: Product/Service ID (NDC)</li>
 *   <li>Field 5: Product Description</li>
 *   <li>Field 6: Strength</li>
 *   <li>Field 7: Dosage Form</li>
 *   <li>Field 8: Quantity Dispensed</li>
 *   <li>Field 9: Quantity Unit (EA=Each, ML=Milliliters, etc.)</li>
 *   <li>Field 10: Fill Number</li>
 *   <li>Field 11: Refills Authorized</li>
 *   <li>Field 12: Origin Code</li>
 *   <li>Field 13: Days Supply</li>
 * </ul>
 *
 * <p><strong>DAW (Dispense As Written) Codes:</strong></p>
 * <ul>
 *   <li>0 = No product selection indicated</li>
 *   <li>1 = Substitution not allowed by prescriber</li>
 *   <li>2 = Substitution allowed - patient requested</li>
 *   <li>3 = Substitution allowed - pharmacist selected</li>
 *   <li>5 = Brand dispensed as generic</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimSegment {

    /**
     * Date of service in YYYYMMDD format
     */
    private String dateOfService;

    /**
     * Prescription/Service reference number
     */
    private String prescriptionNumber;

    /**
     * Fill number (1=New, 2+=Refill)
     */
    private Integer fillNumber;

    /**
     * Product/Service ID (NDC - National Drug Code)
     */
    private String ndc;

    /**
     * Product description (drug name)
     */
    private String productDescription;

    /**
     * Drug strength (e.g., "20MG", "100U/ML")
     */
    private String strength;

    /**
     * Dosage form (TAB=Tablet, CAP=Capsule, INJ=Injection, etc.)
     */
    private String dosageForm;

    /**
     * Quantity dispensed
     */
    private BigDecimal quantityDispensed;

    /**
     * Quantity unit (EA=Each, ML=Milliliters, GM=Grams)
     */
    private String quantityUnit;

    /**
     * Number of refills authorized
     */
    private Integer refillsAuthorized;

    /**
     * Prescription origin code (0-5)
     */
    private Integer originCode;

    /**
     * Days supply
     */
    private Integer daysSupply;

    /**
     * DAW (Dispense As Written) code
     */
    private Integer dawCode;

    /**
     * Checks if this is a new prescription (not a refill)
     *
     * @return true if fill number is 1
     */
    public boolean isNewPrescription() {
        return fillNumber != null && fillNumber == 1;
    }

    /**
     * Checks if this is a refill
     *
     * @return true if fill number is greater than 1
     */
    public boolean isRefill() {
        return fillNumber != null && fillNumber > 1;
    }
}
