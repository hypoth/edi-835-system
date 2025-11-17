package com.healthcare.edi835.service;

import com.healthcare.edi835.dto.IngestionResult;
import com.healthcare.edi835.dto.NcpdpStatusResponse;
import com.healthcare.edi835.entity.NcpdpRawClaim;
import com.healthcare.edi835.entity.NcpdpRawClaim.NcpdpStatus;
import com.healthcare.edi835.repository.NcpdpRawClaimRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for ingesting NCPDP D.0 claims from files.
 *
 * <p>This service handles reading NCPDP claim files and inserting them into the
 * {@code ncpdp_raw_claims} table. Once inserted with status='PENDING', the
 * {@link com.healthcare.edi835.changefeed.NcpdpChangeFeedProcessor} will automatically
 * detect and process them.</p>
 *
 * <p><strong>Supported Operations:</strong></p>
 * <ul>
 *   <li>Read NCPDP file and split into transactions (STX → SE blocks)</li>
 *   <li>Extract basic metadata for indexing (payer, pharmacy, date)</li>
 *   <li>Insert raw transactions into database</li>
 *   <li>Generate synthetic test data (optional)</li>
 * </ul>
 *
 * <p><strong>File Format:</strong></p>
 * <pre>
 * STX*D0*...
 * AM01*...
 * ...
 * SE*...
 * (Next transaction)
 * STX*D0*...
 * </pre>
 *
 * @see NcpdpRawClaim
 * @see com.healthcare.edi835.changefeed.NcpdpChangeFeedProcessor
 */
@Service
@Slf4j
public class NcpdpIngestionService {

    private final NcpdpRawClaimRepository repository;

    @Value("${ncpdp.ingestion.default-file-path:d0-samples/ncpdp_rx_claims.txt}")
    private String defaultFilePath;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern PAYER_PATTERN = Pattern.compile("AM07\\*([^\\*]+)\\*");
    private static final Pattern PHARMACY_PATTERN = Pattern.compile("AM01\\*[^\\*]*\\*([^\\*]+)\\*");
    private static final Pattern DATE_PATTERN = Pattern.compile("AM13\\*([^\\*]+)\\*");
    private static final Pattern PATIENT_PATTERN = Pattern.compile("AM07\\*[^\\*]*\\*[^\\*]*\\*([^\\*]+)\\*");
    private static final Pattern RX_PATTERN = Pattern.compile("AM13\\*[^\\*]*\\*([^\\*]+)\\*");

    public NcpdpIngestionService(NcpdpRawClaimRepository repository) {
        this.repository = repository;
    }

    /**
     * Ingests NCPDP claims from a file
     *
     * @param filePath path to the NCPDP file
     * @return ingestion result with counts and status
     */
    public IngestionResult ingestFromFile(String filePath) {
        return ingestFromFile(filePath, false);
    }

    /**
     * Ingests NCPDP claims from a file
     *
     * @param filePath path to the NCPDP file
     * @param stopOnError whether to stop processing on first error
     * @return ingestion result with counts and status
     */
    public IngestionResult ingestFromFile(String filePath, boolean stopOnError) {
        log.info("Ingesting NCPDP claims from file: {}", filePath);

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            log.error("File not found: {}", filePath);
            return IngestionResult.failure("File not found: " + filePath);
        }

        if (!Files.isReadable(path)) {
            log.error("File not readable: {}", filePath);
            return IngestionResult.failure("File not readable: " + filePath);
        }

        try {
            List<String> transactions = readTransactionsFromFile(filePath);
            log.info("Read {} transactions from file", transactions.size());

            return processTransactions(transactions, stopOnError);

        } catch (IOException e) {
            log.error("Failed to read NCPDP file: {}", filePath, e);
            return IngestionResult.failure("Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Ingests NCPDP claims from the default file path
     *
     * @return ingestion result
     */
    public IngestionResult ingestFromDefaultFile() {
        log.info("Ingesting from default file: {}", defaultFilePath);
        return ingestFromFile(defaultFilePath);
    }

    /**
     * Processes list of raw NCPDP transactions and inserts into database
     *
     * @param transactions list of raw transaction strings
     * @param stopOnError whether to stop on first error
     * @return ingestion result
     */
    @Transactional
    protected IngestionResult processTransactions(List<String> transactions, boolean stopOnError) {
        IngestionResult.IngestionResultBuilder resultBuilder = IngestionResult.builder()
            .totalProcessed(0)
            .totalSuccess(0)
            .totalFailed(0);

        List<String> errors = new ArrayList<>();

        for (String rawTransaction : transactions) {
            try {
                NcpdpRawClaim rawClaim = createRawClaim(rawTransaction);
                repository.save(rawClaim);

                resultBuilder.totalSuccess(resultBuilder.build().getTotalSuccess() + 1);
                log.debug("Inserted NCPDP claim: id={}, payer={}", rawClaim.getId(), rawClaim.getPayerId());

            } catch (Exception e) {
                resultBuilder.totalFailed(resultBuilder.build().getTotalFailed() + 1);
                String error = "Failed to insert transaction: " + e.getMessage();
                errors.add(error);
                log.error(error, e);

                if (stopOnError) {
                    break;
                }
            }

            resultBuilder.totalProcessed(resultBuilder.build().getTotalProcessed() + 1);
        }

        IngestionResult result = resultBuilder.errors(errors).build();

        // Determine overall status
        if (result.getTotalFailed() == 0) {
            result.setStatus("SUCCESS");
        } else if (result.getTotalSuccess() == 0) {
            result.setStatus("FAILED");
        } else {
            result.setStatus("PARTIAL");
        }

        log.info("Ingestion complete: success={}, failed={}, status={}",
            result.getTotalSuccess(), result.getTotalFailed(), result.getStatus());

        return result;
    }

    /**
     * Creates a NcpdpRawClaim entity from raw transaction content
     *
     * @param rawContent the raw NCPDP transaction text
     * @return NcpdpRawClaim entity
     */
    private NcpdpRawClaim createRawClaim(String rawContent) {
        // Extract basic metadata for indexing
        String payerId = extractPayerId(rawContent);
        String pharmacyId = extractPharmacyId(rawContent);
        String patientId = extractPatientId(rawContent);
        String prescriptionNumber = extractPrescriptionNumber(rawContent);
        LocalDate serviceDate = extractServiceDate(rawContent);

        return NcpdpRawClaim.builder()
            .id(UUID.randomUUID().toString())
            .payerId(payerId)
            .pharmacyId(pharmacyId)
            .patientId(patientId)
            .prescriptionNumber(prescriptionNumber)
            .serviceDate(serviceDate)
            .rawContent(rawContent)
            .status(NcpdpStatus.PENDING)
            .createdDate(LocalDateTime.now())
            .createdBy("INGESTION_SERVICE")
            .build();
    }

    /**
     * Reads NCPDP transactions from file (STX → SE blocks)
     *
     * @param filePath path to file
     * @return list of transaction strings
     * @throws IOException if file reading fails
     */
    private List<String> readTransactionsFromFile(String filePath) throws IOException {
        List<String> transactions = new ArrayList<>();
        StringBuilder currentTx = new StringBuilder();
        boolean inTransaction = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments outside transactions
                if (!inTransaction && (line.isEmpty() || line.startsWith("#"))) {
                    continue;
                }

                // Start of transaction
                if (line.startsWith("STX")) {
                    currentTx = new StringBuilder();
                    inTransaction = true;
                }

                // Add line to current transaction
                if (inTransaction) {
                    currentTx.append(line).append("\n");
                }

                // End of transaction
                if (line.startsWith("SE") || line.startsWith("ANC1")) {
                    if (inTransaction) {
                        transactions.add(currentTx.toString());
                        inTransaction = false;
                    }
                }
            }
        }

        return transactions;
    }

    /**
     * Extracts payer ID from raw NCPDP content (AM07 segment)
     */
    private String extractPayerId(String rawContent) {
        Matcher matcher = PAYER_PATTERN.matcher(rawContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "UNKNOWN";
    }

    /**
     * Extracts pharmacy ID from raw NCPDP content (AM01 segment)
     */
    private String extractPharmacyId(String rawContent) {
        Matcher matcher = PHARMACY_PATTERN.matcher(rawContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Extracts patient ID from raw NCPDP content (AM07 segment)
     */
    private String extractPatientId(String rawContent) {
        Matcher matcher = PATIENT_PATTERN.matcher(rawContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Extracts prescription number from raw NCPDP content (AM13 segment)
     */
    private String extractPrescriptionNumber(String rawContent) {
        Matcher matcher = RX_PATTERN.matcher(rawContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Extracts service date from raw NCPDP content (AM13 segment)
     */
    private LocalDate extractServiceDate(String rawContent) {
        Matcher matcher = DATE_PATTERN.matcher(rawContent);
        if (matcher.find()) {
            try {
                String dateStr = matcher.group(1).trim();
                return LocalDate.parse(dateStr, DATE_FORMATTER);
            } catch (Exception e) {
                log.debug("Failed to parse service date", e);
            }
        }
        return null;
    }

    /**
     * Gets ingestion statistics
     *
     * @return status summary
     */
    public NcpdpStatusResponse getStatus() {
        return NcpdpStatusResponse.builder()
            .pending(repository.countByStatus(NcpdpStatus.PENDING))
            .processing(repository.countByStatus(NcpdpStatus.PROCESSING))
            .processed(repository.countByStatus(NcpdpStatus.PROCESSED))
            .failed(repository.countByStatus(NcpdpStatus.FAILED))
            .build();
    }
}
