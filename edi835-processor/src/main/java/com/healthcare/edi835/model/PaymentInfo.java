package com.healthcare.edi835.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payment Information for BPR segment in EDI 835.
 * Contains financial transaction details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInfo {

    // BPR01 - Transaction Handling Code
    private String transactionHandlingCode;  // I=Information, C=Payment, D=Debit, etc.

    // BPR02 - Monetary Amount
    private BigDecimal totalActualProviderPaymentAmount;

    // BPR03 - Credit/Debit Flag
    private String creditDebitFlag;  // C=Credit, D=Debit

    // BPR04 - Payment Method Code
    private String paymentMethodCode;  // CHK=Check, ACH=ACH, NON=Non-Payment Data

    // BPR05 - Payment Format Code
    private String paymentFormatCode;  // CCP=Cash Concentration/Disbursement plus Addenda

    // BPR10 - Originating Company Identifier (Payer's Company ID)
    private String originatingCompanyIdentifier;

    // BPR16 - Check/EFT Issue Date
    private LocalDate paymentEffectiveDate;

    // TRN02 - Trace Number
    private String checkOrEftTraceNumber;

    // Additional information
    private String payerIdentifier;
    private String payeeIdentifier;

    // Wrapper methods for backward compatibility
    public BigDecimal getTotalPaidAmount() {
        return totalActualProviderPaymentAmount;
    }

    public String getTraceNumber() {
        return checkOrEftTraceNumber;
    }

    public String getOriginatingCompanyId() {
        return originatingCompanyIdentifier;
    }
}
