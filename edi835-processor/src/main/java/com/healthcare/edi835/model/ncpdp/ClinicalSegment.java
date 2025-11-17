package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Clinical Segment (AM20)
 *
 * <p>Contains diagnosis codes and clinical information.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AM20*01*New therapy*</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalSegment {

    /**
     * Diagnosis code qualifier
     */
    private String diagnosisCodeQualifier;

    /**
     * Clinical information text
     */
    private String clinicalInformation;
}
