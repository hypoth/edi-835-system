package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new check reservation.
 * Used by admins to pre-allocate check number ranges.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCheckReservationRequest {

    private String payerId;              // Required: Payer ID
    private String checkNumberStart;     // Required: Starting check number (e.g., "CHK000001")
    private String checkNumberEnd;       // Required: Ending check number (e.g., "CHK000100")
    private String bankName;             // Required: Bank name
    private String routingNumber;        // Optional: Bank routing number
    private String accountLast4;         // Optional: Last 4 digits of account
    private String paymentMethodId;      // Optional: Payment method ID
    private String createdBy;            // Required: User creating the reservation
}
