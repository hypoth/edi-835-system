package com.healthcare.edi835.model.ncpdp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response Message Segment (AN25)
 *
 * <p>Contains response message text and authorization number.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>AN25*CLAIM APPROVED*AUTH123456*</pre>
 *
 * <p><strong>Field Mapping:</strong></p>
 * <ul>
 *   <li>Field 1: Message Text</li>
 *   <li>Field 2: Authorization Number</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseMessageSegment {

    /**
     * Response message text (e.g., "CLAIM APPROVED", "CLAIM REJECTED - INVALID BIN")
     */
    private String messageText;

    /**
     * Authorization number assigned by payer
     */
    private String authorizationNumber;
}
