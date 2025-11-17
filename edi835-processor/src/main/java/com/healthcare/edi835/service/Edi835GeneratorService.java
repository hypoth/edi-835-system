package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.ClaimProcessingLog;
import com.healthcare.edi835.entity.EdiFileBucket;
import com.healthcare.edi835.entity.FileGenerationHistory;
import com.healthcare.edi835.entity.Payer;
import com.healthcare.edi835.entity.Payee;
import com.healthcare.edi835.exception.MissingConfigurationException;
import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.model.PaymentInfo;
import com.healthcare.edi835.model.RemittanceAdvice;
import com.healthcare.edi835.repository.ClaimProcessingLogRepository;
import com.healthcare.edi835.repository.PayerRepository;
import com.healthcare.edi835.repository.PayeeRepository;
import io.xlate.edi.schema.Schema;
import io.xlate.edi.schema.SchemaFactory;
import io.xlate.edi.stream.EDIOutputFactory;
import io.xlate.edi.stream.EDIStreamWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to generate EDI 835 files using StAEDI
 */
@Slf4j
@Service
public class Edi835GeneratorService {

    private static final DateTimeFormatter DATE_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = 
        DateTimeFormatter.ofPattern("HHmm");

    @Value("${edi.schema-directory}")
    private String schemaDirectory;

    @Value("${edi.default-schema}")
    private String defaultSchema;

    @Value("${edi.output-directory:#{systemProperties['java.io.tmpdir']}}")
    private String outputDirectory;

    private final EDIOutputFactory outputFactory;
    private final ClaimProcessingLogRepository claimLogRepository;
    private final PayerRepository payerRepository;
    private final PayeeRepository payeeRepository;
    private Schema schema;

    public Edi835GeneratorService(
            ClaimProcessingLogRepository claimLogRepository,
            PayerRepository payerRepository,
            PayeeRepository payeeRepository) {
        this.outputFactory = EDIOutputFactory.newFactory();
        this.claimLogRepository = claimLogRepository;
        this.payerRepository = payerRepository;
        this.payeeRepository = payeeRepository;
    }

    @PostConstruct
    public void init() {
        this.schema = loadSchema();
    }

    /**
     * Generate EDI 835 file from remittance advice
     */
    public Path generateEdi835File(RemittanceAdvice remittance, Path outputPath) {
        log.info("Generating EDI 835 file: {}", outputPath);

        try (OutputStream output = new FileOutputStream(outputPath.toFile())) {
            EDIStreamWriter writer = outputFactory.createEDIStreamWriter(output);
            // Note: setSchema() not available in StAEDI 1.25.3
            // writer.setSchema(schema);

            writeInterchangeEnvelope(writer, remittance);
            writeFunctionalGroup(writer, remittance);
            writeTransaction(writer, remittance);

            writer.close();
            log.info("Successfully generated EDI 835 file: {}", outputPath);
            return outputPath;

        } catch (Exception e) {
            log.error("Error generating EDI 835 file", e);
            throw new RuntimeException("Failed to generate EDI 835", e);
        }
    }

    /**
     * Generate EDI 835 file from bucket
     */
    public FileGenerationHistory generateEdi835File(EdiFileBucket bucket) {
        log.info("Generating EDI 835 file for bucket: {} (Claims: {}, Amount: {})",
                bucket.getBucketId(), bucket.getClaimCount(), bucket.getTotalAmount());

        try {
            // Build RemittanceAdvice from bucket
            RemittanceAdvice remittance = buildRemittanceAdvice(bucket);

            // Generate output path
            String fileName = generateFileName(bucket);
            Path outputPath = Paths.get(outputDirectory, fileName);

            // Ensure output directory exists
            Files.createDirectories(outputPath.getParent());

            // Generate the EDI file
            generateEdi835File(remittance, outputPath);

            // Get file size and read content for database storage
            long fileSize = Files.size(outputPath);
            byte[] fileContent = Files.readAllBytes(outputPath);

            // Create history record with file content
            FileGenerationHistory history = FileGenerationHistory.builder()
                    .bucket(bucket)
                    .generatedFileName(fileName)
                    .filePath(outputPath.toString())
                    .fileSizeBytes(fileSize)
                    .claimCount(bucket.getClaimCount())
                    .totalAmount(bucket.getTotalAmount())
                    .deliveryStatus(FileGenerationHistory.DeliveryStatus.PENDING)
                    .generatedBy("system")
                    .fileContent(fileContent)  // Store file content for database download
                    .build();

            log.info("EDI 835 file generated successfully: {} ({} bytes, stored in database)",
                fileName, fileSize);

            return history;

        } catch (Exception e) {
            log.error("Failed to generate EDI 835 file for bucket: {}", bucket.getBucketId(), e);
            throw new RuntimeException("EDI 835 generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build RemittanceAdvice from bucket and its claims
     */
    private RemittanceAdvice buildRemittanceAdvice(EdiFileBucket bucket) {
        log.debug("Building RemittanceAdvice for bucket: {}", bucket.getBucketId());

        // Get payer and payee information
        Payer payer = payerRepository.findByPayerId(bucket.getPayerId())
                .orElseThrow(() -> new MissingConfigurationException(
                        MissingConfigurationException.ConfigurationType.PAYER,
                        bucket.getPayerId()));

        Payee payee = payeeRepository.findByPayeeId(bucket.getPayeeId())
                .orElseThrow(() -> new MissingConfigurationException(
                        MissingConfigurationException.ConfigurationType.PAYEE,
                        bucket.getPayeeId()));

        // Get claims for this bucket
        List<ClaimProcessingLog> claimLogs = claimLogRepository.findByBucketId(bucket.getBucketId());

        if (claimLogs.isEmpty()) {
            log.warn("No claims found for bucket: {}", bucket.getBucketId());
        }

        // Build payer address
        RemittanceAdvice.Address payerAddress = RemittanceAdvice.Address.builder()
                .addressLine1(payer.getAddressStreet() != null ? payer.getAddressStreet() : "")
                .city(payer.getAddressCity() != null ? payer.getAddressCity() : "")
                .state(payer.getAddressState() != null ? payer.getAddressState() : "")
                .postalCode(payer.getAddressZip() != null ? payer.getAddressZip() : "")
                .build();

        // Build payee address
        RemittanceAdvice.Address payeeAddress = RemittanceAdvice.Address.builder()
                .addressLine1(payee.getAddressStreet() != null ? payee.getAddressStreet() : "")
                .city(payee.getAddressCity() != null ? payee.getAddressCity() : "")
                .state(payee.getAddressState() != null ? payee.getAddressState() : "")
                .postalCode(payee.getAddressZip() != null ? payee.getAddressZip() : "")
                .build();

        // Build payer identification
        RemittanceAdvice.PartyIdentification payerParty = RemittanceAdvice.PartyIdentification.builder()
                .entityIdentifierCode("PR")
                .name(payer.getPayerName())
                .identificationCode(payer.getPayerId())
                .identificationCodeQualifier("XX")
                .address(payerAddress)
                .build();

        // Build payee identification
        RemittanceAdvice.PartyIdentification payeeParty = RemittanceAdvice.PartyIdentification.builder()
                .entityIdentifierCode("PE")
                .name(payee.getPayeeName())
                .identificationCode(payee.getPayeeId())
                .identificationCodeQualifier("XX")
                .address(payeeAddress)
                .build();

        // Build payment information
        PaymentInfo paymentInfo = PaymentInfo.builder()
                .transactionHandlingCode("I") // I = Information only, C = Payment
                .totalActualProviderPaymentAmount(bucket.getTotalAmount())
                .creditDebitFlag("C") // C = Credit
                .paymentMethodCode("ACH") // ACH = Automated Clearing House
                .originatingCompanyIdentifier(payer.getPayerId())
                .checkOrEftTraceNumber(bucket.getBucketId().toString())
                .payerIdentifier(payer.getPayerId())
                .payeeIdentifier(payee.getPayeeId())
                .paymentEffectiveDate(LocalDate.now())
                .build();

        // Convert claims to ClaimPayment objects
        List<RemittanceAdvice.ClaimPayment> claimPayments = new ArrayList<>();
        for (ClaimProcessingLog claimLog : claimLogs) {
            RemittanceAdvice.ClaimPayment claimPayment = RemittanceAdvice.ClaimPayment.builder()
                    .claimId(claimLog.getClaimId())
                    .claimStatusCode("1") // 1 = Processed as primary
                    .totalClaimChargeAmount(claimLog.getClaimAmount() != null ? claimLog.getClaimAmount() : BigDecimal.ZERO)
                    .claimPaymentAmount(claimLog.getPaidAmount() != null ? claimLog.getPaidAmount() : BigDecimal.ZERO)
                    .patientResponsibilityAmount(BigDecimal.ZERO)
                    .claimFilingIndicatorCode("12") // 12 = Preferred Provider Organization (PPO)
                    .payerClaimControlNumber(claimLog.getClaimId())
                    .patientControlNumber(claimLog.getClaimId())
                    .build();

            claimPayments.add(claimPayment);
        }

        // Build RemittanceAdvice using builder pattern
        RemittanceAdvice remittance = RemittanceAdvice.builder()
                .bucketId(bucket.getBucketId().toString())
                .payerId(payer.getPayerId())
                .payerName(payer.getPayerName())
                .payeeId(payee.getPayeeId())
                .payeeName(payee.getPayeeName())
                .paymentInfo(paymentInfo)
                .payer(payerParty)
                .payee(payeeParty)
                .claims(claimPayments)
                .productionDate(LocalDate.now())
                .transactionSetControlNumber(String.format("%04d", 1))
                .interchangeControlNumber(String.format("%09d", generateControlNumber()))
                .build();

        log.debug("Built RemittanceAdvice with {} claims", claimPayments.size());

        return remittance;
    }

    /**
     * Generate file name based on bucket information
     */
    private String generateFileName(EdiFileBucket bucket) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("EDI835_%s_%s_%s.txt",
                bucket.getPayerId(),
                bucket.getPayeeId(),
                timestamp);
    }

    /**
     * Generate a unique control number
     */
    private int generateControlNumber() {
        // Simple implementation: use current time
        return (int) (System.currentTimeMillis() % 1000000000);
    }

    /**
     * Calculate segment count for SE segment
     */
    private int calculateSegmentCount(RemittanceAdvice remittance) {
        // Rough estimate: ST + BPR + TRN + N1*2 + CLP*claims + SE
        int baseSegments = 6; // ST, BPR, TRN, N1(PR), N1(PE), SE
        int claimSegments = remittance.getClaims() != null ? remittance.getClaims().size() * 2 : 0; // CLP + NM1 per claim
        return baseSegments + claimSegments;
    }

    /**
     * Write ISA/IEA envelope
     */
    private void writeInterchangeEnvelope(
            EDIStreamWriter writer, 
            RemittanceAdvice remittance) throws Exception {
        
        writer.startInterchange();
        
        // ISA segment
        writer.writeStartSegment("ISA");
        writer.writeElement("00"); // ISA01 - Authorization qualifier
        writer.writeElement("          "); // ISA02 - Authorization info
        writer.writeElement("00"); // ISA03 - Security qualifier
        writer.writeElement("          "); // ISA04 - Security info
        writer.writeElement("ZZ"); // ISA05 - Sender qualifier
        writer.writeElement(padRight(remittance.getSenderId(), 15)); // ISA06
        writer.writeElement("ZZ"); // ISA07 - Receiver qualifier
        writer.writeElement(padRight(remittance.getReceiverId(), 15)); // ISA08
        writer.writeElement(LocalDate.now().format(DATE_FORMAT).substring(2)); // ISA09 - Date
        writer.writeElement(java.time.LocalTime.now().format(TIME_FORMAT)); // ISA10 - Time
        writer.writeElement("U"); // ISA11 - Standards ID
        writer.writeElement("00501"); // ISA12 - Version
        writer.writeElement(remittance.getControlNumber()); // ISA13 - already formatted as 9-digit string
        writer.writeElement("0"); // ISA14 - Ack requested
        writer.writeElement(remittance.isProduction() ? "P" : "T"); // ISA15
        writer.writeElement(">"); // ISA16 - Subelement separator
        writer.writeEndSegment();
    }

    /**
     * Write GS/GE functional group
     */
    private void writeFunctionalGroup(
            EDIStreamWriter writer,
            RemittanceAdvice remittance) throws Exception {
        
        writer.writeStartSegment("GS");
        writer.writeElement("HP"); // GS01 - Functional ID (HP = 835)
        writer.writeElement(remittance.getSenderId()); // GS02
        writer.writeElement(remittance.getReceiverId()); // GS03
        writer.writeElement(LocalDate.now().format(DATE_FORMAT)); // GS04
        writer.writeElement(java.time.LocalTime.now().format(TIME_FORMAT)); // GS05
        writer.writeElement(String.valueOf(remittance.getGroupControlNumber())); // GS06
        writer.writeElement("X"); // GS07 - Responsible agency
        writer.writeElement("005010X221A1"); // GS08 - Version
        writer.writeEndSegment();
    }

    /**
     * Write ST/SE transaction
     */
    private void writeTransaction(
            EDIStreamWriter writer,
            RemittanceAdvice remittance) throws Exception {

        // ST segment
        writer.writeStartSegment("ST");
        writer.writeElement("835"); // ST01
        writer.writeElement(remittance.getTransactionSetNumber()); // ST02 - already formatted as 4-digit string
        writer.writeElement("005010X221A1"); // ST03
        writer.writeEndSegment();

        // BPR segment - Financial Information
        writeBPRSegment(writer, remittance);

        // TRN segment - Reassociation Trace Number
        writeTRNSegment(writer, remittance);

        // 1000A Loop - Payer Identification
        writePayerLoop(writer, remittance);

        // 1000B Loop - Payee Identification
        writePayeeLoop(writer, remittance);

        // 2000 Loop - Claim Payment Information
        if (remittance.getClaims() != null) {
            for (RemittanceAdvice.ClaimPayment claim : remittance.getClaims()) {
                writeClaimLoop(writer, claim);
            }
        }

        // SE segment - Transaction Set Trailer
        writer.writeStartSegment("SE");
        writer.writeElement(String.valueOf(remittance.getSegmentCount())); // SE01
        writer.writeElement(remittance.getTransactionSetNumber()); // SE02 - already formatted as 4-digit string
        writer.writeEndSegment();

        // GE segment
        writer.writeStartSegment("GE");
        writer.writeElement("1"); // GE01 - Number of transaction sets
        writer.writeElement(String.valueOf(remittance.getGroupControlNumber())); // GE02
        writer.writeEndSegment();

        // IEA segment
        writer.writeStartSegment("IEA");
        writer.writeElement("1"); // IEA01 - Number of functional groups
        writer.writeElement(remittance.getControlNumber()); // IEA02 - already formatted as 9-digit string
        writer.writeEndSegment();

        writer.endInterchange();
    }

    private void writeBPRSegment(EDIStreamWriter writer, RemittanceAdvice remittance) 
            throws Exception {
        writer.writeStartSegment("BPR");
        writer.writeElement("I"); // BPR01 - Transaction handling (I=Info only, C=Payment)
        writer.writeElement(formatAmount(remittance.getTotalPaidAmount())); // BPR02
        writer.writeElement("C"); // BPR03 - Credit/Debit (C=Credit)
        writer.writeElement("ACH"); // BPR04 - Payment method (ACH)
        // ... additional BPR elements
        writer.writeEndSegment();
    }

    private void writeTRNSegment(EDIStreamWriter writer, RemittanceAdvice remittance) 
            throws Exception {
        writer.writeStartSegment("TRN");
        writer.writeElement("1"); // TRN01 - Trace type (1=Current transaction)
        writer.writeElement(remittance.getPaymentTraceNumber()); // TRN02
        writer.writeElement(remittance.getOriginatingCompanyId()); // TRN03
        writer.writeEndSegment();
    }

    private void writePayerLoop(EDIStreamWriter writer, RemittanceAdvice remittance) 
            throws Exception {
        // N1 segment - Payer identification
        writer.writeStartSegment("N1");
        writer.writeElement("PR"); // N101 - Entity identifier (PR=Payer)
        writer.writeElement(remittance.getPayerName()); // N102
        writer.writeElement("XX"); // N103 - ID code qualifier
        writer.writeElement(remittance.getPayerId()); // N104
        writer.writeEndSegment();

        // N3 segment - Payer address
        if (remittance.getPayerAddress() != null) {
            writer.writeStartSegment("N3");
            writer.writeElement(remittance.getPayerAddress().getStreet()); // N301
            writer.writeEndSegment();

            // N4 segment - Payer city/state/zip
            writer.writeStartSegment("N4");
            writer.writeElement(remittance.getPayerAddress().getCity()); // N401
            writer.writeElement(remittance.getPayerAddress().getState()); // N402
            writer.writeElement(remittance.getPayerAddress().getZip()); // N403
            writer.writeEndSegment();
        }
    }

    private void writePayeeLoop(EDIStreamWriter writer, RemittanceAdvice remittance) 
            throws Exception {
        // N1 segment - Payee identification
        writer.writeStartSegment("N1");
        writer.writeElement("PE"); // N101 - Entity identifier (PE=Payee)
        writer.writeElement(remittance.getPayeeName()); // N102
        writer.writeElement("XX"); // N103 - ID code qualifier
        writer.writeElement(remittance.getPayeeId()); // N104
        writer.writeEndSegment();

        // N3 segment - Payee address (if present)
        if (remittance.getPayeeAddress() != null) {
            writer.writeStartSegment("N3");
            writer.writeElement(remittance.getPayeeAddress().getStreet());
            writer.writeEndSegment();

            writer.writeStartSegment("N4");
            writer.writeElement(remittance.getPayeeAddress().getCity());
            writer.writeElement(remittance.getPayeeAddress().getState());
            writer.writeElement(remittance.getPayeeAddress().getZip());
            writer.writeEndSegment();
        }
    }

    private void writeClaimLoop(EDIStreamWriter writer, RemittanceAdvice.ClaimPayment claim) throws Exception {
        // TODO: Implement full CLP loop with ClaimPayment structure
        // CLP segment - Claim payment information
        writer.writeStartSegment("CLP");
        writer.writeElement(claim.getClaimId() != null ? claim.getClaimId() : ""); // CLP01
        writer.writeElement(claim.getClaimStatusCode() != null ? claim.getClaimStatusCode() : "1"); // CLP02
        writer.writeElement(formatAmount(claim.getTotalClaimChargeAmount())); // CLP03
        writer.writeElement(formatAmount(claim.getClaimPaymentAmount())); // CLP04
        writer.writeElement(formatAmount(claim.getPatientResponsibilityAmount())); // CLP05
        writer.writeElement(claim.getClaimFilingIndicatorCode() != null ? claim.getClaimFilingIndicatorCode() : "12"); // CLP06
        writer.writeElement(claim.getPayerClaimControlNumber() != null ? claim.getPayerClaimControlNumber() : claim.getClaimId()); // CLP07
        writer.writeEndSegment();

        // CAS segment - Claim level adjustments
        if (claim.getClaimAdjustments() != null && !claim.getClaimAdjustments().isEmpty()) {
            for (RemittanceAdvice.ClaimLevelAdjustment adj : claim.getClaimAdjustments()) {
                writer.writeStartSegment("CAS");
                writer.writeElement(adj.getAdjustmentGroupCode() != null ? adj.getAdjustmentGroupCode() : "CO"); // CAS01
                writer.writeElement(adj.getAdjustmentReasonCode() != null ? adj.getAdjustmentReasonCode() : "1"); // CAS02
                writer.writeElement(formatAmount(adj.getAdjustmentAmount())); // CAS03
                if (adj.getAdjustmentQuantity() != null) {
                    writer.writeElement(formatAmount(adj.getAdjustmentQuantity())); // CAS04
                }
                writer.writeEndSegment();
            }
        }

        // NM1 segment - Patient information
        writer.writeStartSegment("NM1");
        writer.writeElement("QC"); // NM101 - Entity identifier (QC=Patient)
        writer.writeElement("1"); // NM102 - Entity type (1=Person)
        writer.writeElement(claim.getPatientControlNumber() != null ? claim.getPatientControlNumber() : ""); // NM103
        writer.writeEndSegment();

        // Service line loops
        if (claim.getServicePayments() != null) {
            for (RemittanceAdvice.ServicePayment serviceLine : claim.getServicePayments()) {
                writeServiceLineLoop(writer, serviceLine);
            }
        }
    }

    private void writeServiceLineLoop(
            EDIStreamWriter writer,
            RemittanceAdvice.ServicePayment serviceLine) throws Exception {

        // TODO: Implement full SVC loop with ServicePayment structure
        // SVC segment - Service payment information
        writer.writeStartSegment("SVC");

        // SVC01 - Composite medical procedure
        writer.startComponent();
        writer.writeElement("HC"); // Qualifier
        writer.writeElement(serviceLine.getProcedureCode() != null ? serviceLine.getProcedureCode() : ""); // Procedure code
        writer.endComponent();

        writer.writeElement(formatAmount(serviceLine.getLineItemChargeAmount())); // SVC02
        writer.writeElement(formatAmount(serviceLine.getLineItemProviderPaymentAmount())); // SVC03
        writer.writeElement("UN"); // SVC04 - Unit basis
        writer.writeElement(serviceLine.getQuantity() != null ? formatAmount(serviceLine.getQuantity()) : "1"); // SVC05
        writer.writeEndSegment();

        // CAS segment - Service line adjustments
        if (serviceLine.getAdjustments() != null && !serviceLine.getAdjustments().isEmpty()) {
            for (RemittanceAdvice.ServiceAdjustment adj : serviceLine.getAdjustments()) {
                writer.writeStartSegment("CAS");
                writer.writeElement(adj.getAdjustmentGroupCode() != null ? adj.getAdjustmentGroupCode() : "CO");
                writer.writeElement(adj.getAdjustmentReasonCode() != null ? adj.getAdjustmentReasonCode() : "1");
                writer.writeElement(formatAmount(adj.getAdjustmentAmount()));
                writer.writeEndSegment();
            }
        }
    }

    /**
     * Load EDI 835 schema for validation.
     * Returns null if schema file is not found (allows application to start for testing).
     */
    private Schema loadSchema() {
        // If schema is not configured or empty, skip loading
        if (defaultSchema == null || defaultSchema.trim().isEmpty()) {
            log.info("EDI schema validation is disabled (no schema configured)");
            return null;
        }

        try {
            SchemaFactory schemaFactory = SchemaFactory.newFactory();
            var schemaStream = getClass().getResourceAsStream("/edi-schemas/" + defaultSchema);

            if (schemaStream == null) {
                log.warn("EDI schema file not found: /edi-schemas/{}. Schema validation will be disabled.", defaultSchema);
                log.warn("To enable schema validation, add a valid StAEDI X12 835 schema file to src/main/resources/edi-schemas/");
                return null;
            }

            Schema schema = schemaFactory.createSchema(schemaStream);
            log.info("Successfully loaded EDI schema: {}", defaultSchema);
            return schema;
        } catch (Exception e) {
            log.warn("Failed to load EDI schema: {}. Schema validation will be disabled.", e.getMessage());
            log.debug("Schema load error details", e);
            return null;  // Don't fail startup, just disable schema validation
        }
    }

    /**
     * Format amount for EDI (remove decimal, pad if needed)
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0";
        return amount.multiply(BigDecimal.valueOf(100))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .toString();
    }


    /**
     * Pad string to right with spaces
     */
    private String padRight(String s, int length) {
        return String.format("%-" + length + "s", s);
    }
}