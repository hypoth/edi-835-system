package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Pricing Segment (AM15, AM17)
 *
 * <p>Contains financial information including costs, fees, and totals.</p>
 *
 * <p><strong>Example AM17:</strong></p>
 * <pre>AM17*01*250.00*02*225.00*03*20.00*04*5.00*05*0.00*06*0.00*07*0.00*11*250.00*</pre>
 *
 * <p><strong>Amount Codes:</strong></p>
 * <ul>
 *   <li>01 = Ingredient Cost Submitted</li>
 *   <li>02 = Ingredient Cost Paid</li>
 *   <li>03 = Dispensing Fee Submitted</li>
 *   <li>04 = Dispensing Fee Paid</li>
 *   <li>05 = Tax Amount</li>
 *   <li>06 = Usual and Customary Charge</li>
 *   <li>07 = Flat Sales Tax Amount</li>
 *   <li>11 = Gross Amount Due</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingSegment {

    /**
     * Ingredient cost submitted (code 01)
     */
    private BigDecimal ingredientCostSubmitted;

    /**
     * Ingredient cost paid (code 02)
     */
    private BigDecimal ingredientCostPaid;

    /**
     * Dispensing fee submitted (code 03)
     */
    private BigDecimal dispensingFeeSubmitted;

    /**
     * Dispensing fee paid (code 04)
     */
    private BigDecimal dispensingFeePaid;

    /**
     * Tax amount (code 05)
     */
    private BigDecimal taxAmount;

    /**
     * Usual and customary charge (code 06)
     */
    private BigDecimal usualAndCustomaryCharge;

    /**
     * Flat sales tax amount (code 07)
     */
    private BigDecimal flatSalesTaxAmount;

    /**
     * Gross amount due - total charge (code 11)
     */
    private BigDecimal grossAmountDue;

    /**
     * Calculates total submitted amount
     *
     * @return ingredient cost + dispensing fee + tax
     */
    public BigDecimal getTotalSubmitted() {
        BigDecimal total = BigDecimal.ZERO;
        if (ingredientCostSubmitted != null) {
            total = total.add(ingredientCostSubmitted);
        }
        if (dispensingFeeSubmitted != null) {
            total = total.add(dispensingFeeSubmitted);
        }
        if (taxAmount != null) {
            total = total.add(taxAmount);
        }
        return total;
    }

    /**
     * Calculates total paid amount
     *
     * @return ingredient cost paid + dispensing fee paid
     */
    public BigDecimal getTotalPaid() {
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
