package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Complete NCPDP D.0 transaction containing all segments.
 *
 * <p>Represents a full NCPDP telecommunications standard transaction
 * from STX (Start of Transaction) to SE (Transaction Trailer).</p>
 *
 * <p><strong>NCPDP D.0 Transaction Structure:</strong></p>
 * <pre>
 * STX*D0*          *          *
 * AM01* - Transaction Header Segment
 * AM04* - Insurance Segment
 * AM07* - Patient Segment
 * AM11* - Prescriber Segment
 * AM13* - Claim Segment
 * AM14* - Compound Segment (if compound prescription)
 * AM15* - Pricing Segment (NDC)
 * AM17* - Pricing Segment (Amounts)
 * AM19* - Prior Authorization Segment (optional)
 * AM20* - Clinical Segment (optional)
 * AM21* - Additional Documentation Segment (optional)
 * AMC1* - Claim Trailer
 * SE* - Transaction End
 *
 * Response Segments (if present):
 * AN01* - Response Header
 * AN02* - Response Status
 * AN23* - Response Payment
 * AN25* - Response Message
 * </pre>
 *
 * @see TransactionHeader
 * @see PatientSegment
 * @see ClaimSegment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NcpdpTransaction {

    /**
     * NCPDP version (typically "D0")
     */
    private String version;

    /**
     * Transaction type: B1 (Billing), B2 (Reversal), B3 (Rebill)
     */
    private String transactionType;

    /**
     * Transaction Header Segment (AM01)
     * Contains service provider ID, date/time, transaction count
     */
    private TransactionHeader header;

    /**
     * Insurance Segment (AM04)
     * Contains cardholder ID prefix, prescription origin code, fill number
     */
    private InsuranceSegment insurance;

    /**
     * Patient Segment (AM07)
     * Contains cardholder information, patient demographics, address
     */
    private PatientSegment patient;

    /**
     * Prescriber Segment (AM11)
     * Contains prescriber ID (NPI), prescriber information, contact details
     */
    private PrescriberSegment prescriber;

    /**
     * Claim Segment (AM13)
     * Contains prescription details, NDC, quantity, days supply
     */
    private ClaimSegment claim;

    /**
     * Compound Segment (AM14) - only present for compound prescriptions
     * Contains ingredient details for compounded medications
     */
    private CompoundSegment compound;

    /**
     * NDC code from AM15 segment
     */
    private String ndcCode;

    /**
     * Pricing Segment (AM15, AM17)
     * Contains ingredient cost, dispensing fee, tax, totals
     */
    private PricingSegment pricing;

    /**
     * Prior Authorization Segment (AM19) - optional
     */
    private PriorAuthorizationSegment priorAuthorization;

    /**
     * Clinical Segment (AM20) - optional
     */
    private ClinicalSegment clinical;

    /**
     * Additional Documentation Segment (AM21) - optional
     */
    private AdditionalDocumentationSegment additionalDocumentation;

    /**
     * Claim Trailer (AMC1)
     */
    private String claimTrailer;

    /**
     * Response Status Segment (AN02)
     * Contains approval/rejection status
     */
    private ResponseStatusSegment responseStatus;

    /**
     * Response Payment Segment (AN23)
     * Contains paid amounts
     */
    private ResponsePaymentSegment responsePayment;

    /**
     * Response Message Segment (AN25)
     * Contains message and authorization number
     */
    private ResponseMessageSegment responseMessage;

    /**
     * Complete raw transaction content (STX → SE)
     */
    private String rawContent;

    /**
     * Checks if transaction has a response (typically means it's approved or rejected)
     *
     * @return true if response segments are present
     */
    public boolean hasResponse() {
        return responseStatus != null;
    }

    /**
     * Checks if transaction was approved
     *
     * @return true if response status is 'A' (Approved)
     */
    public boolean isApproved() {
        return hasResponse() &&
               responseStatus != null &&
               "A".equals(responseStatus.getResponseStatus());
    }

    /**
     * Checks if transaction was rejected
     *
     * @return true if response status is 'R' (Rejected)
     */
    public boolean isRejected() {
        return hasResponse() &&
               responseStatus != null &&
               "R".equals(responseStatus.getResponseStatus());
    }

    /**
     * Checks if this is a compound prescription
     *
     * @return true if compound segment is present
     */
    public boolean isCompound() {
        return compound != null;
    }
}
