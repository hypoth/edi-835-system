package com.healthcare.edi835.mapper;

import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.model.ncpdp.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mapper that converts NCPDP D.0 pharmacy claims to standard EDI Claim objects.
 *
 * <p>This mapper performs the following conversions:</p>
 * <ul>
 *   <li>NCPDP payer ID → Claim payerId</li>
 *   <li>NCPDP pharmacy → Claim payeeId</li>
 *   <li>NCPDP pricing → Claim financial amounts</li>
 *   <li>NCPDP response status → Claim status</li>
 *   <li>NDC code → Service line procedure code</li>
 * </ul>
 *
 * <p><strong>Field Mapping Reference:</strong></p>
 * <table border="1">
 *   <tr><th>NCPDP Field</th><th>Claim Field</th><th>Notes</th></tr>
 *   <tr><td>AM07 - Carrier ID</td><td>payerId</td><td>BCBSIL, CIGNA, etc.</td></tr>
 *   <tr><td>AM01 - Pharmacy ID</td><td>payeeId</td><td>PHARMACY001, etc.</td></tr>
 *   <tr><td>AM13 - Prescription #</td><td>claimNumber</td><td>Unique claim identifier</td></tr>
 *   <tr><td>AM07 - Cardholder ID</td><td>patientId</td><td>Member ID</td></tr>
 *   <tr><td>AM07 - Patient Name</td><td>patientName</td><td>Combined first/last</td></tr>
 *   <tr><td>AM07 - BIN</td><td>binNumber</td><td>Bank Identification Number</td></tr>
 *   <tr><td>AM13 - Service Date</td><td>serviceDate</td><td>YYYYMMDD → LocalDate</td></tr>
 *   <tr><td>AM17-11</td><td>totalChargeAmount</td><td>Gross amount due</td></tr>
 *   <tr><td>AN23-05 or AN23-01+02</td><td>paidAmount</td><td>Total paid by payer</td></tr>
 *   <tr><td>AN23-03</td><td>patientResponsibilityAmount</td><td>Patient copay</td></tr>
 *   <tr><td>AN02 - Status</td><td>status</td><td>A=PAID, R=DENIED</td></tr>
 * </table>
 *
 * @see NcpdpTransaction
 * @see Claim
 */
@Component
@Slf4j
public class NcpdpToClaimMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Maps NCPDP transaction to standard Claim object
     *
     * @param ncpdpTx the NCPDP transaction to map
     * @return mapped Claim object
     * @throws IllegalArgumentException if required fields are missing
     */
    public Claim mapToClaim(NcpdpTransaction ncpdpTx) {
        if (ncpdpTx == null) {
            throw new IllegalArgumentException("NCPDP transaction cannot be null");
        }

        log.debug("Mapping NCPDP transaction to Claim");

        validateRequiredFields(ncpdpTx);

        return Claim.builder()
            .id(generateClaimId(ncpdpTx))
            .payerId(extractPayerId(ncpdpTx))
            .payeeId(extractPayeeId(ncpdpTx))
            .claimNumber(extractClaimNumber(ncpdpTx))
            .patientId(extractPatientId(ncpdpTx))
            .patientName(buildPatientName(ncpdpTx.getPatient()))
            .binNumber(extractBinNumber(ncpdpTx))
            .pcnNumber(extractPcnNumber(ncpdpTx))
            .serviceDate(parseServiceDate(ncpdpTx))
            .statementFromDate(parseServiceDate(ncpdpTx))
            .statementToDate(parseServiceDate(ncpdpTx))
            .totalChargeAmount(extractTotalChargeAmount(ncpdpTx))
            .paidAmount(extractPaidAmount(ncpdpTx))
            .patientResponsibilityAmount(extractPatientResponsibility(ncpdpTx))
            .adjustmentAmount(calculateAdjustmentAmount(ncpdpTx))
            .status(mapStatus(ncpdpTx))
            .statusReason(extractStatusReason(ncpdpTx))
            .serviceLines(createServiceLines(ncpdpTx))
            .adjustments(createAdjustments(ncpdpTx))
            .processedDate(LocalDateTime.now())
            .createdDate(LocalDateTime.now())
            .lastModifiedDate(LocalDateTime.now())
            .processedBy("NCPDP_D0_MAPPER")
            .build();
    }

    /**
     * Validates that required fields are present in NCPDP transaction
     */
    private void validateRequiredFields(NcpdpTransaction tx) {
        if (tx.getPatient() == null) {
            throw new IllegalArgumentException("Patient segment (AM07) is required");
        }
        if (tx.getHeader() == null) {
            throw new IllegalArgumentException("Header segment (AM01) is required");
        }
        if (tx.getClaim() == null) {
            throw new IllegalArgumentException("Claim segment (AM13) is required");
        }
        if (tx.getPricing() == null) {
            throw new IllegalArgumentException("Pricing segment (AM17) is required");
        }
    }

    /**
     * Generates unique claim ID based on NCPDP data
     */
    private String generateClaimId(NcpdpTransaction tx) {
        // Use prescription number + pharmacy + date for uniqueness
        String base = String.format("NCPDP-%s-%s-%s",
            tx.getHeader().getPharmacyId(),
            tx.getClaim().getPrescriptionNumber(),
            tx.getHeader().getDate());

        // Add UUID suffix to ensure uniqueness
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Extracts payer ID from NCPDP patient segment
     * Normalizes to uppercase for consistency
     */
    private String extractPayerId(NcpdpTransaction tx) {
        String carrierId = tx.getPatient().getCarrierId();
        return carrierId != null ? carrierId.toUpperCase() : "UNKNOWN";
    }

    /**
     * Extracts payee (pharmacy) ID from NCPDP header
     */
    private String extractPayeeId(NcpdpTransaction tx) {
        return tx.getHeader().getPharmacyId();
    }

    /**
     * Extracts claim number (prescription reference number)
     */
    private String extractClaimNumber(NcpdpTransaction tx) {
        return tx.getClaim().getPrescriptionNumber();
    }

    /**
     * Extracts patient ID (cardholder ID)
     */
    private String extractPatientId(NcpdpTransaction tx) {
        return tx.getPatient().getCardholderIdNumber();
    }

    /**
     * Builds full patient name from NCPDP patient segment
     */
    private String buildPatientName(PatientSegment patient) {
        if (patient == null) {
            return null;
        }
        return patient.getFullName();
    }

    /**
     * Extracts BIN (Bank Identification Number) from patient segment
     */
    private String extractBinNumber(NcpdpTransaction tx) {
        return tx.getPatient().getBinNumber();
    }

    /**
     * Extracts PCN (Processor Control Number) - not always present in NCPDP D.0
     * Returns null if not available
     */
    private String extractPcnNumber(NcpdpTransaction tx) {
        // PCN is not a standard field in D.0, would be in extended fields
        // For now, return null - can be enhanced if needed
        return null;
    }

    /**
     * Parses service date from NCPDP format (YYYYMMDD) to LocalDate
     */
    private LocalDate parseServiceDate(NcpdpTransaction tx) {
        String dateStr = tx.getClaim().getDateOfService();
        if (dateStr == null || dateStr.isEmpty()) {
            log.warn("Service date is missing, using current date");
            return LocalDate.now();
        }

        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.error("Failed to parse service date: {}", dateStr, e);
            return LocalDate.now();
        }
    }

    /**
     * Extracts total charge amount from NCPDP pricing
     * Uses gross amount due (AM17-11)
     */
    private BigDecimal extractTotalChargeAmount(NcpdpTransaction tx) {
        PricingSegment pricing = tx.getPricing();

        if (pricing.getGrossAmountDue() != null) {
            return pricing.getGrossAmountDue();
        }

        // Fallback: calculate from components
        return pricing.getTotalSubmitted();
    }

    /**
     * Extracts paid amount from NCPDP response
     * If response present, uses AN23 payment amounts
     * Otherwise, uses submitted amounts from AM17
     */
    private BigDecimal extractPaidAmount(NcpdpTransaction tx) {
        // If response payment present, use it
        if (tx.getResponsePayment() != null) {
            return tx.getResponsePayment().getTotalPaid();
        }

        // If approved but no payment segment, use pricing
        if (tx.isApproved() && tx.getPricing() != null) {
            return tx.getPricing().getTotalPaid();
        }

        // If rejected, paid amount is zero
        if (tx.isRejected()) {
            return BigDecimal.ZERO;
        }

        // Default to submitted amount for pending claims
        return tx.getPricing() != null ? tx.getPricing().getTotalPaid() : BigDecimal.ZERO;
    }

    /**
     * Extracts patient responsibility amount (copay/coinsurance)
     */
    private BigDecimal extractPatientResponsibility(NcpdpTransaction tx) {
        if (tx.getResponsePayment() != null && tx.getResponsePayment().getPatientPayAmount() != null) {
            return tx.getResponsePayment().getPatientPayAmount();
        }

        // If no response, calculate as difference between submitted and paid
        BigDecimal totalCharge = extractTotalChargeAmount(tx);
        BigDecimal paidAmount = extractPaidAmount(tx);

        if (totalCharge != null && paidAmount != null) {
            BigDecimal patientPay = totalCharge.subtract(paidAmount);
            return patientPay.compareTo(BigDecimal.ZERO) > 0 ? patientPay : BigDecimal.ZERO;
        }

        return BigDecimal.ZERO;
    }

    /**
     * Calculates adjustment amount (difference between submitted and paid)
     */
    private BigDecimal calculateAdjustmentAmount(NcpdpTransaction tx) {
        BigDecimal submitted = extractTotalChargeAmount(tx);
        BigDecimal paid = extractPaidAmount(tx);
        BigDecimal patientPay = extractPatientResponsibility(tx);

        if (submitted != null && paid != null && patientPay != null) {
            BigDecimal adjustment = submitted.subtract(paid).subtract(patientPay);
            return adjustment.compareTo(BigDecimal.ZERO) > 0 ? adjustment : BigDecimal.ZERO;
        }

        return BigDecimal.ZERO;
    }

    /**
     * Maps NCPDP response status to Claim status
     * A (Approved) → PAID
     * R (Rejected) → DENIED
     * No response → PROCESSED
     */
    private Claim.ClaimStatus mapStatus(NcpdpTransaction tx) {
        if (tx.getResponseStatus() != null) {
            String status = tx.getResponseStatus().getResponseStatus();

            return switch (status) {
                case "A" -> Claim.ClaimStatus.PAID;
                case "R" -> Claim.ClaimStatus.DENIED;
                case "P" -> Claim.ClaimStatus.PAID;
                default -> Claim.ClaimStatus.PROCESSED;
            };
        }

        // No response means claim is processed but not adjudicated
        return Claim.ClaimStatus.PROCESSED;
    }

    /**
     * Extracts status reason from response message
     */
    private String extractStatusReason(NcpdpTransaction tx) {
        if (tx.getResponseStatus() != null) {
            return tx.getResponseStatus().getResponseMessage();
        }
        if (tx.getResponseMessage() != null) {
            return tx.getResponseMessage().getMessageText();
        }
        return null;
    }

    /**
     * Creates service lines from NCPDP prescription data
     * Each prescription creates one service line with NDC as procedure code
     */
    private List<Claim.ServiceLine> createServiceLines(NcpdpTransaction tx) {
        List<Claim.ServiceLine> serviceLines = new ArrayList<>();

        ClaimSegment claim = tx.getClaim();
        PricingSegment pricing = tx.getPricing();

        // Create service line for the prescription
        Claim.ServiceLine serviceLine = Claim.ServiceLine.builder()
            .procedureCode(extractNdcCode(tx))
            .modifier(claim.getDosageForm()) // Use dosage form as modifier
            .chargedAmount(pricing.getGrossAmountDue())
            .paidAmount(extractPaidAmount(tx))
            .adjustmentAmount(calculateAdjustmentAmount(tx))
            .units(claim.getQuantityDispensed() != null ? claim.getQuantityDispensed().intValue() : 0)
            .serviceDate(parseServiceDate(tx))
            .adjustments(new ArrayList<>()) // Could be populated with CAS adjustments
            .build();

        serviceLines.add(serviceLine);

        // If compound, could add additional service lines for ingredients
        if (tx.isCompound() && tx.getCompound() != null) {
            log.debug("Compound prescription detected with {} ingredients",
                tx.getCompound().getIngredients().size());
            // Could create separate service lines for compound ingredients
            // For now, treating as single service line
        }

        return serviceLines;
    }

    /**
     * Extracts NDC code from either AM15 or AM13 segment
     */
    private String extractNdcCode(NcpdpTransaction tx) {
        // Prefer AM15 NDC if present
        if (tx.getNdcCode() != null && !tx.getNdcCode().isEmpty()) {
            return tx.getNdcCode();
        }

        // Fallback to AM13 NDC
        if (tx.getClaim() != null && tx.getClaim().getNdc() != null) {
            return tx.getClaim().getNdc();
        }

        return "UNKNOWN";
    }

    /**
     * Creates claim-level adjustments if claim was denied or partially paid
     */
    private List<Claim.ClaimAdjustment> createAdjustments(NcpdpTransaction tx) {
        List<Claim.ClaimAdjustment> adjustments = new ArrayList<>();

        // If rejected, create denial adjustment
        if (tx.isRejected()) {
            Claim.ClaimAdjustment adjustment = Claim.ClaimAdjustment.builder()
                .groupCode("PR") // Patient Responsibility
                .reasonCode("REJECTED")
                .amount(extractTotalChargeAmount(tx))
                .quantity(null)
                .build();
            adjustments.add(adjustment);
        }

        // If partially paid, create contractual adjustment
        BigDecimal adjustmentAmount = calculateAdjustmentAmount(tx);
        if (adjustmentAmount.compareTo(BigDecimal.ZERO) > 0) {
            Claim.ClaimAdjustment adjustment = Claim.ClaimAdjustment.builder()
                .groupCode("CO") // Contractual Obligation
                .reasonCode("45") // CARC 45: Charge exceeds fee schedule
                .amount(adjustmentAmount)
                .quantity(null)
                .build();
            adjustments.add(adjustment);
        }

        return adjustments;
    }

    /**
     * Maps multiple NCPDP transactions to claims
     *
     * @param transactions list of NCPDP transactions
     * @return list of mapped claims
     */
    public List<Claim> mapToClaims(List<NcpdpTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return new ArrayList<>();
        }

        List<Claim> claims = new ArrayList<>();
        for (NcpdpTransaction tx : transactions) {
            try {
                Claim claim = mapToClaim(tx);
                claims.add(claim);
            } catch (Exception e) {
                log.error("Failed to map NCPDP transaction: {}", e.getMessage(), e);
                // Continue processing other transactions
            }
        }

        log.info("Mapped {} NCPDP transactions to claims", claims.size());
        return claims;
    }
}
