package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for manual check assignment during approval.
 * Used when user enters check details manually.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualCheckAssignmentRequest {

    private String checkNumber;      // Required: Check number
    private LocalDate checkDate;     // Required: Check date
    private String bankName;         // Optional: Bank name
    private String routingNumber;    // Optional: Routing number
    private String accountLast4;     // Optional: Last 4 digits of account
    private String assignedBy;       // Required: User assigning the check
}
