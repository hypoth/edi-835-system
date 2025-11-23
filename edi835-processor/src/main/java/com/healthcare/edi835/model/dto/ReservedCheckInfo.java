package com.healthcare.edi835.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Internal DTO for reserved check information.
 * Used when retrieving next available check from reservation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservedCheckInfo {

    private String checkNumber;
    private LocalDate checkDate;
    private String bankName;
    private String routingNumber;
    private String accountLast4;
    private String reservationId;
}
