package com.healthcare.edi835.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Remittance Advice data structure for EDI 835 generation.
 * Represents the complete remittance information for a bucket of claims.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemittanceAdvice {

    private String bucketId;
    private String payerId;
    private String payerName;
    private String payeeId;
    private String payeeName;

    // Payment information (BPR segment)
    private PaymentInfo paymentInfo;

    // Payer identification (N1 loop)
    private PartyIdentification payer;

    // Payee identification (N1 loop)
    private PartyIdentification payee;

    // Claims included in this remittance
    private List<ClaimPayment> claims;

    // Metadata
    private LocalDate productionDate;
    private String transactionSetControlNumber;
    private String interchangeControlNumber;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimPayment {
        private String claimId;
        private String patientControlNumber;
        private String claimStatusCode;
        private BigDecimal totalClaimChargeAmount;
        private BigDecimal claimPaymentAmount;
        private BigDecimal patientResponsibilityAmount;
        private String claimFilingIndicatorCode;
        private String payerClaimControlNumber;
        private List<ServicePayment> servicePayments;
        private List<ClaimLevelAdjustment> claimAdjustments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServicePayment {
        private String procedureCode;
        private BigDecimal lineItemChargeAmount;
        private BigDecimal lineItemProviderPaymentAmount;
        private String revenueCode;
        private BigDecimal quantity;
        private List<ServiceAdjustment> adjustments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimLevelAdjustment {
        private String adjustmentGroupCode;
        private String adjustmentReasonCode;
        private BigDecimal adjustmentAmount;
        private BigDecimal adjustmentQuantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceAdjustment {
        private String adjustmentGroupCode;
        private String adjustmentReasonCode;
        private BigDecimal adjustmentAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartyIdentification {
        private String entityIdentifierCode;  // PR (Payer) or PE (Payee)
        private String name;
        private String identificationCode;
        private String identificationCodeQualifier;
        private Address address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String addressLine1;
        private String city;
        private String state;
        private String postalCode;

        // Alias methods for backward compatibility
        public String getStreet() {
            return addressLine1;
        }

        public String getZip() {
            return postalCode;
        }
    }

    // Wrapper methods for backward compatibility
    public Address getPayerAddress() {
        return payer != null ? payer.getAddress() : null;
    }

    public Address getPayeeAddress() {
        return payee != null ? payee.getAddress() : null;
    }

    // Additional getters that might be referenced
    public String getTransactionSetNumber() {
        return transactionSetControlNumber;
    }

    public String getGroupControlNumber() {
        return interchangeControlNumber;
    }

    public String getControlNumber() {
        return interchangeControlNumber;
    }

    public BigDecimal getTotalPaidAmount() {
        return paymentInfo != null ? paymentInfo.getTotalPaidAmount() : BigDecimal.ZERO;
    }

    public String getPaymentTraceNumber() {
        return paymentInfo != null ? paymentInfo.getTraceNumber() : null;
    }

    public String getOriginatingCompanyId() {
        return paymentInfo != null ? paymentInfo.getOriginatingCompanyId() : null;
    }

    public boolean isProduction() {
        // Default to production mode
        return true;
    }

    public String getSenderId() {
        return payerId;
    }

    public String getReceiverId() {
        return payeeId;
    }

    public int getSegmentCount() {
        // Calculate approximate segment count
        // ISA + GS + ST + BPR + TRN + DTM + N1 loops + CLP loops + SE + GE + IEA
        int baseSegments = 11; // ISA, GS, ST, BPR, TRN, DTM, SE, GE, IEA, and 2 N1 loops
        int claimSegments = claims != null ? claims.size() * 3 : 0; // CLP, CAS, SVC per claim (approximate)
        return baseSegments + claimSegments;
    }
}
