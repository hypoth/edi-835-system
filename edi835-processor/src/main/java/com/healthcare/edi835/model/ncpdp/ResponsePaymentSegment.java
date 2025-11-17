package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response Payment Segment (AN23)
 *
 * <p>Contains the financial response including paid amounts and patient responsibility.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AN23*01*225.00*02*20.00*03*5.00*05*250.00*</pre>
 *
 * <p><strong>Amount Codes:</strong></p>
 * <ul>
 *   <li>01 = Ingredient Cost Paid</li>
 *   <li>02 = Dispensing Fee Paid</li>
 *   <li>03 = Patient Pay Amount (copay/coinsurance)</li>
 *   <li>05 = Total Amount Paid</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponsePaymentSegment {

    /**
     * Ingredient cost paid by payer (code 01)
     */
    private BigDecimal ingredientCostPaid;

    /**
     * Dispensing fee paid by payer (code 02)
     */
    private BigDecimal dispensingFeePaid;

    /**
     * Patient pay amount - copay/coinsurance (code 03)
     */
    private BigDecimal patientPayAmount;

    /**
     * Total amount paid by payer (code 05)
     */
    private BigDecimal totalAmountPaid;

    /**
     * Gets total amount paid by payer
     *
     * @return total amount or calculated from ingredients + dispensing fee
     */
    public BigDecimal getTotalPaid() {
        if (totalAmountPaid != null) {
            return totalAmountPaid;
        }

        // Calculate from components if total not provided
        BigDecimal total = BigDecimal.ZERO;
        if (ingredientCostPaid != null) {
            total = total.add(ingredientCostPaid);
        }
        if (dispensingFeePaid != null) {
            total = total.add(dispensingFeePaid);
        }
        return total;
    }
}
