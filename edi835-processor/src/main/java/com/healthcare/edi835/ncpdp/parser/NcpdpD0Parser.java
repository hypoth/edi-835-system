package com.healthcare.edi835.ncpdp.parser;

import com.healthcare.edi835.model.ncpdp.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for NCPDP D.0 pharmacy prescription claim transactions.
 *
 * <p>Parses raw NCPDP telecommunications standard format into structured Java objects.</p>
 *
 * <p><strong>NCPDP Format:</strong></p>
 * <ul>
 *   <li>Delimiter: * (asterisk)</li>
 *   <li>Segment separator: newline</li>
 *   <li>Transaction start: STX*D0*</li>
 *   <li>Transaction end: SE*{count}*{id}*</li>
 * </ul>
 *
 * <p><strong>Example Usage:</strong></p>
 * <pre>
 * String rawContent = "STX*D0*...\nAM01*...\n...SE*15*...";
 * NcpdpTransaction transaction = parser.parse(rawContent);
 * </pre>
 *
 * @see NcpdpTransaction
 */
@Component
@Slf4j
public class NcpdpD0Parser {

    private static final String DELIMITER = "\\*";
    private static final String SEGMENT_DELIMITER = "\\n";

    /**
     * Parses complete NCPDP D.0 transaction from raw text
     *
     * @param rawContent the raw NCPDP transaction text
     * @return parsed NcpdpTransaction object
     * @throws NcpdpParseException if parsing fails
     */
    public NcpdpTransaction parse(String rawContent) throws NcpdpParseException {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            throw new NcpdpParseException("Raw content is empty or null");
        }

        log.debug("Parsing NCPDP transaction, length: {} bytes", rawContent.length());

        String[] lines = rawContent.split(SEGMENT_DELIMITER);
        NcpdpTransaction.NcpdpTransactionBuilder builder = NcpdpTransaction.builder();
        builder.rawContent(rawContent);

        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            line = line.trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            try {
                String segmentId = extractSegmentId(line);
                String[] fields = line.split(DELIMITER, -1); // -1 to keep trailing empty strings

                parseSegment(segmentId, fields, builder, lineNumber);

            } catch (Exception e) {
                log.error("Error parsing line {}: {}", lineNumber, line, e);
                throw new NcpdpParseException(
                    "Failed to parse line: " + e.getMessage(),
                    extractSegmentId(line),
                    lineNumber
                );
            }
        }

        NcpdpTransaction transaction = builder.build();
        log.debug("Successfully parsed NCPDP transaction");
        return transaction;
    }

    /**
     * Routes segment to appropriate parser based on segment ID
     */
    private void parseSegment(String segmentId, String[] fields,
                              NcpdpTransaction.NcpdpTransactionBuilder builder,
                              int lineNumber) throws NcpdpParseException {

        switch (segmentId) {
            case "STX" -> parseSTX(fields, builder);
            case "AM01" -> builder.header(parseAM01(fields));
            case "AM04" -> builder.insurance(parseAM04(fields));
            case "AM07" -> builder.patient(parseAM07(fields));
            case "AM11" -> builder.prescriber(parseAM11(fields));
            case "AM13" -> builder.claim(parseAM13(fields));
            case "AM14" -> builder.compound(parseAM14(fields));
            case "AM15" -> builder.ndcCode(parseAM15(fields));
            case "AM17" -> builder.pricing(parseAM17(fields));
            case "AM19" -> builder.priorAuthorization(parseAM19(fields));
            case "AM20" -> builder.clinical(parseAM20(fields));
            case "AM21" -> builder.additionalDocumentation(parseAM21(fields));
            case "AMC1" -> builder.claimTrailer(parseAMC1(fields));
            case "AN01" -> parseAN01(fields, builder);
            case "AN02" -> builder.responseStatus(parseAN02(fields));
            case "AN04" -> parseAN04(fields, builder);
            case "AN07" -> parseAN07(fields, builder);
            case "AN23" -> builder.responsePayment(parseAN23(fields));
            case "AN25" -> builder.responseMessage(parseAN25(fields));
            case "ANC1" -> parseANC1(fields, builder);
            case "SE" -> parseSE(fields, builder);
            default -> log.debug("Skipping unknown segment: {}", segmentId);
        }
    }

    /**
     * Extracts segment ID from line (first field before *)
     */
    private String extractSegmentId(String line) {
        int delimiterIndex = line.indexOf('*');
        if (delimiterIndex > 0) {
            return line.substring(0, delimiterIndex);
        }
        return line;
    }

    /**
     * Parse STX segment (Start of Transaction)
     * Format: STX*D0*          *          *
     */
    private void parseSTX(String[] fields, NcpdpTransaction.NcpdpTransactionBuilder builder) {
        if (fields.length > 1) {
            builder.version(fields[1].trim());
        }
    }

    /**
     * Parse AM01 segment (Transaction Header)
     * Format: AM01*1234567*PHARMACY001*20241014*143025*1*
     */
    private TransactionHeader parseAM01(String[] fields) throws NcpdpParseException {
        validateMinFields(fields, 6, "AM01");

        return TransactionHeader.builder()
            .serviceProviderId(getField(fields, 1))
            .pharmacyId(getField(fields, 2))
            .date(getField(fields, 3))
            .time(getField(fields, 4))
            .transactionCount(getField(fields, 5))
            .build();
    }

    /**
     * Parse AM04 segment (Insurance)
     * Format: AM04*01*R*1*
     */
    private InsuranceSegment parseAM04(String[] fields) throws NcpdpParseException {
        validateMinFields(fields, 4, "AM04");

        return InsuranceSegment.builder()
            .cardholderIdQualifier(getField(fields, 1))
            .prescriptionOriginCode(getField(fields, 2))
            .fillNumber(parseInteger(getField(fields, 3)))
            .build();
    }

    /**
     * Parse AM07 segment (Patient)
     * Format: AM07*BCBSIL*60054*123456789*01*SMITH*JOHN*A*19850515*M*456 PATIENT AVE*CHICAGO*IL*60602*
     */
    private PatientSegment parseAM07(String[] fields) throws NcpdpParseException {
        validateMinFields(fields, 14, "AM07");

        return PatientSegment.builder()
            .carrierId(getField(fields, 1))
            .binNumber(getField(fields, 2))
            .cardholderIdNumber(getField(fields, 3))
            .cardholderIdQualifier(getField(fields, 4))
            .lastName(getField(fields, 5))
            .firstName(getField(fields, 6))
            .middleInitial(getField(fields, 7))
            .dateOfBirth(getField(fields, 8))
            .gender(getField(fields, 9))
            .address(getField(fields, 10))
            .city(getField(fields, 11))
            .state(getField(fields, 12))
            .zip(getField(fields, 13))
            .build();
    }

    /**
     * Parse AM11 segment (Prescriber)
     * Format: AM11*00123456789*1*1234567890*JONES*ROBERT*D*555-123-4567*
     */
    private PrescriberSegment parseAM11(String[] fields) throws NcpdpParseException {
        validateMinFields(fields, 8, "AM11");

        return PrescriberSegment.builder()
            .prescriberId(getField(fields, 1))
            .prescriberIdQualifier(getField(fields, 2))
            .licenseNumber(getField(fields, 3))
            .lastName(getField(fields, 4))
            .firstName(getField(fields, 5))
            .middleInitial(getField(fields, 6))
            .phoneNumber(getField(fields, 7))
            .build();
    }

    /**
     * Parse AM13 segment (Claim)
     * Format: AM13*20241014*12345*1*00002012345678*LIPITOR*20MG*TAB*30*EA*1*0*0*3*
     */
    private ClaimSegment parseAM13(String[] fields) throws NcpdpParseException {
        validateMinFields(fields, 14, "AM13");

        return ClaimSegment.builder()
            .dateOfService(getField(fields, 1))
            .prescriptionNumber(getField(fields, 2))
            .fillNumber(parseInteger(getField(fields, 3)))
            .ndc(getField(fields, 4))
            .productDescription(getField(fields, 5))
            .strength(getField(fields, 6))
            .dosageForm(getField(fields, 7))
            .quantityDispensed(parseBigDecimal(getField(fields, 8)))
            .quantityUnit(getField(fields, 9))
            .dawCode(parseInteger(getField(fields, 10)))  // DAW (Dispense As Written) code
            .refillsAuthorized(parseInteger(getField(fields, 11)))
            .originCode(parseInteger(getField(fields, 12)))
            .daysSupply(parseInteger(getField(fields, 13)))
            .build();
    }

    /**
     * Parse AM14 segment (Compound)
     * Format: AM14*01*00006020001234*50*ML*125.00*02*00008820004567*25*GM*85.00*03*BASE001*100*GM*40.00*
     */
    private CompoundSegment parseAM14(String[] fields) {
        List<CompoundSegment.CompoundIngredient> ingredients = new ArrayList<>();

        // Parse ingredients in groups of 5 (sequence, code, qty, unit, cost)
        for (int i = 1; i < fields.length; i += 5) {
            if (i + 4 < fields.length) {
                ingredients.add(CompoundSegment.CompoundIngredient.builder()
                    .sequenceNumber(parseInteger(getField(fields, i)))
                    .productCode(getField(fields, i + 1))
                    .quantity(getField(fields, i + 2))
                    .quantityUnit(getField(fields, i + 3))
                    .cost(getField(fields, i + 4))
                    .build());
            }
        }

        return CompoundSegment.builder()
            .ingredients(ingredients)
            .build();
    }

    /**
     * Parse AM15 segment (NDC)
     * Format: AM15*59762-0123-03*
     */
    private String parseAM15(String[] fields) {
        if (fields.length > 1) {
            return getField(fields, 1);
        }
        return null;
    }

    /**
     * Parse AM17 segment (Pricing)
     * Format: AM17*01*250.00*02*225.00*03*20.00*04*5.00*05*0.00*06*0.00*07*0.00*11*250.00*
     */
    private PricingSegment parseAM17(String[] fields) {
        Map<String, BigDecimal> amounts = new HashMap<>();

        // Parse paired values: code*amount
        for (int i = 1; i < fields.length; i += 2) {
            if (i + 1 < fields.length) {
                String code = getField(fields, i);
                BigDecimal amount = parseBigDecimal(getField(fields, i + 1));
                amounts.put(code, amount);
            }
        }

        return PricingSegment.builder()
            .ingredientCostSubmitted(amounts.get("01"))
            .ingredientCostPaid(amounts.get("02"))
            .dispensingFeeSubmitted(amounts.get("03"))
            .dispensingFeePaid(amounts.get("04"))
            .taxAmount(amounts.get("05"))
            .usualAndCustomaryCharge(amounts.get("06"))
            .flatSalesTaxAmount(amounts.get("07"))
            .grossAmountDue(amounts.get("11"))
            .build();
    }

    /**
     * Parse AM19 segment (Prior Authorization)
     * Format: AM19*20*20240714*
     */
    private PriorAuthorizationSegment parseAM19(String[] fields) {
        return PriorAuthorizationSegment.builder()
            .authorizationType(getField(fields, 1))
            .priorPrescriptionDate(getField(fields, 2))
            .build();
    }

    /**
     * Parse AM20 segment (Clinical)
     * Format: AM20*01*New therapy*
     */
    private ClinicalSegment parseAM20(String[] fields) {
        return ClinicalSegment.builder()
            .diagnosisCodeQualifier(getField(fields, 1))
            .clinicalInformation(getField(fields, 2))
            .build();
    }

    /**
     * Parse AM21 segment (Additional Documentation)
     * Format: AM21*01*20241014*STATE123456*TX*1234567* or AM21*03*PA123456789*
     */
    private AdditionalDocumentationSegment parseAM21(String[] fields) {
        String docType = getField(fields, 1);

        AdditionalDocumentationSegment.AdditionalDocumentationSegmentBuilder builder =
            AdditionalDocumentationSegment.builder()
                .documentationType(docType);

        if ("01".equals(docType) && fields.length >= 6) {
            // DEA number
            builder.documentationDate(getField(fields, 2))
                   .deaNumber(getField(fields, 3))
                   .state(getField(fields, 4))
                   .stateLicenseNumber(getField(fields, 5));
        } else if ("03".equals(docType) && fields.length >= 3) {
            // Prior authorization
            builder.priorAuthorizationNumber(getField(fields, 2));
        }

        return builder.build();
    }

    /**
     * Parse AMC1 segment (Claim Trailer)
     * Format: AMC1*123456789012345*
     */
    private String parseAMC1(String[] fields) {
        if (fields.length > 1) {
            return getField(fields, 1);
        }
        return null;
    }

    /**
     * Parse AN01 segment (Response Header)
     */
    private void parseAN01(String[] fields, NcpdpTransaction.NcpdpTransactionBuilder builder) {
        // Response header - typically mirrors request header
        log.debug("Parsed AN01 response header");
    }

    /**
     * Parse AN02 segment (Response Status)
     * Format: AN02*A*00*APPROVED*
     */
    private ResponseStatusSegment parseAN02(String[] fields) throws NcpdpParseException {
        validateMinFields(fields, 4, "AN02");

        return ResponseStatusSegment.builder()
            .responseStatus(getField(fields, 1))
            .responseCode(getField(fields, 2))
            .responseMessage(getField(fields, 3))
            .build();
    }

    /**
     * Parse AN04 segment (Response Insurance)
     */
    private void parseAN04(String[] fields, NcpdpTransaction.NcpdpTransactionBuilder builder) {
        // Response insurance - typically mirrors request
        log.debug("Parsed AN04 response insurance");
    }

    /**
     * Parse AN07 segment (Response Patient)
     */
    private void parseAN07(String[] fields, NcpdpTransaction.NcpdpTransactionBuilder builder) {
        // Response patient - typically mirrors request
        log.debug("Parsed AN07 response patient");
    }

    /**
     * Parse AN23 segment (Response Payment)
     * Format: AN23*01*225.00*02*20.00*03*5.00*05*250.00*
     */
    private ResponsePaymentSegment parseAN23(String[] fields) {
        Map<String, BigDecimal> amounts = new HashMap<>();

        // Parse paired values: code*amount
        for (int i = 1; i < fields.length; i += 2) {
            if (i + 1 < fields.length) {
                String code = getField(fields, i);
                BigDecimal amount = parseBigDecimal(getField(fields, i + 1));
                amounts.put(code, amount);
            }
        }

        return ResponsePaymentSegment.builder()
            .ingredientCostPaid(amounts.get("01"))
            .dispensingFeePaid(amounts.get("02"))
            .patientPayAmount(amounts.get("03"))
            .totalAmountPaid(amounts.get("05"))
            .build();
    }

    /**
     * Parse AN25 segment (Response Message)
     * Format: AN25*CLAIM APPROVED*AUTH123456*
     */
    private ResponseMessageSegment parseAN25(String[] fields) {
        return ResponseMessageSegment.builder()
            .messageText(getField(fields, 1))
            .authorizationNumber(getField(fields, 2))
            .build();
    }

    /**
     * Parse ANC1 segment (Response Claim Trailer)
     */
    private void parseANC1(String[] fields, NcpdpTransaction.NcpdpTransactionBuilder builder) {
        // Response trailer
        log.debug("Parsed ANC1 response trailer");
    }

    /**
     * Parse SE segment (Transaction End)
     * Format: SE*15*1234567*
     */
    private void parseSE(String[] fields, NcpdpTransaction.NcpdpTransactionBuilder builder) {
        log.debug("Parsed SE transaction end");
    }

    // ========== Utility Methods ==========

    /**
     * Gets field at index, returns null if not present or empty
     */
    private String getField(String[] fields, int index) {
        if (index < fields.length) {
            String value = fields[index].trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    /**
     * Parses string to Integer, returns null if empty or invalid
     */
    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse integer: {}", value);
            return null;
        }
    }

    /**
     * Parses string to BigDecimal, returns null if empty or invalid
     */
    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse decimal: {}", value);
            return null;
        }
    }

    /**
     * Validates minimum number of fields in segment
     */
    private void validateMinFields(String[] fields, int minFields, String segmentId)
        throws NcpdpParseException {
        if (fields.length < minFields) {
            throw new NcpdpParseException(
                String.format("Segment %s requires at least %d fields, found %d",
                    segmentId, minFields, fields.length)
            );
        }
    }
}
