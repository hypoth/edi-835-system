package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Compound Segment (AM14)
 *
 * <p>Contains ingredient details for compounded prescriptions.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AM14*01*00006020001234*50*ML*125.00*02*00008820004567*25*GM*85.00*03*BASE001*100*GM*40.00*</pre>
 *
 * <p>Each ingredient is represented by a sequence of fields:</p>
 * <ul>
 *   <li>Ingredient Sequence Number</li>
 *   <li>NDC or Product Code</li>
 *   <li>Quantity</li>
 *   <li>Quantity Unit</li>
 *   <li>Cost</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompoundSegment {

    /**
     * List of ingredients in the compound
     */
    private List<CompoundIngredient> ingredients;

    /**
     * Represents a single ingredient in a compound prescription
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompoundIngredient {
        /**
         * Ingredient sequence number
         */
        private Integer sequenceNumber;

        /**
         * NDC or product code
         */
        private String productCode;

        /**
         * Quantity of ingredient
         */
        private String quantity;

        /**
         * Quantity unit (ML=Milliliters, GM=Grams, etc.)
         */
        private String quantityUnit;

        /**
         * Cost of this ingredient
         */
        private String cost;
    }
}
