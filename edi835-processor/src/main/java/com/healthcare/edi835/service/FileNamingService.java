package com.healthcare.edi835.service;

import com.healthcare.edi835.entity.*;
import com.healthcare.edi835.repository.FileNamingSequenceRepository;
import com.healthcare.edi835.repository.PayerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for generating file names from templates.
 * Parses templates like "{payerId}_{date}_{sequenceNumber}.835"
 * and substitutes variables with actual values.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Parse file naming templates</li>
 *   <li>Substitute template variables</li>
 *   <li>Manage sequence numbers with thread-safe increment</li>
 *   <li>Handle sequence reset (DAILY, MONTHLY, YEARLY)</li>
 *   <li>Apply case conversions (UPPER, LOWER, CAPITALIZE)</li>
 * </ul>
 *
 * <p>Supported Variables:</p>
 * <ul>
 *   <li>{payerId} - Payer identifier</li>
 *   <li>{payerName} - Payer name</li>
 *   <li>{payeeId} - Payee identifier</li>
 *   <li>{payeeName} - Payee name</li>
 *   <li>{binNumber} - BIN number (if applicable)</li>
 *   <li>{pcnNumber} - PCN number (if applicable)</li>
 *   <li>{date} - Current date (yyyyMMdd)</li>
 *   <li>{date:format} - Custom date format</li>
 *   <li>{timestamp} - Current timestamp (yyyyMMddHHmmss)</li>
 *   <li>{sequenceNumber} - Auto-incrementing sequence</li>
 *   <li>{sequenceNumber:width} - Padded sequence (e.g., {sequenceNumber:6} = 000123)</li>
 *   <li>{bucketId} - Bucket UUID</li>
 * </ul>
 */
@Slf4j
@Service
public class FileNamingService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([a-zA-Z]+)(?::([^}]+))?\\}");
    private static final DateTimeFormatter DEFAULT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final FileNamingSequenceRepository sequenceRepository;
    private final PayerRepository payerRepository;

    public FileNamingService(
            FileNamingSequenceRepository sequenceRepository,
            PayerRepository payerRepository) {
        this.sequenceRepository = sequenceRepository;
        this.payerRepository = payerRepository;
    }

    /**
     * Generates a file name from a bucket and its naming template.
     *
     * @param bucket the bucket to generate file name for
     * @return the generated file name
     */
    @Transactional
    public String generateFileName(EdiFileBucket bucket) {
        EdiFileNamingTemplate template = bucket.getFileNamingTemplate();

        if (template == null) {
            log.warn("No file naming template configured for bucket {}, using default",
                    bucket.getBucketId());
            return generateDefaultFileName(bucket);
        }

        log.debug("Generating file name for bucket {} using template: {}",
                bucket.getBucketId(), template.getTemplatePattern());

        try {
            // Get payer for sequence management
            Payer payer = payerRepository.findByPayerId(bucket.getPayerId())
                    .orElse(null);

            // Build variable map
            Map<String, String> variables = buildVariableMap(bucket, template, payer);

            // Parse template and substitute variables
            String fileName = parseTemplate(template.getTemplatePattern(), variables);

            // Apply case conversion
            fileName = applyCaseConversion(fileName, template);

            // Add file extension if not present
            if (!fileName.endsWith(".835")) {
                fileName += ".835";
            }

            log.info("Generated file name for bucket {}: {}", bucket.getBucketId(), fileName);
            return fileName;

        } catch (Exception e) {
            log.error("Error generating file name for bucket {}: {}",
                    bucket.getBucketId(), e.getMessage(), e);
            return generateDefaultFileName(bucket);
        }
    }

    /**
     * Builds a map of template variables and their values.
     *
     * @param bucket the bucket
     * @param template the naming template
     * @param payer the payer entity
     * @return map of variable names to values
     */
    private Map<String, String> buildVariableMap(EdiFileBucket bucket,
                                                   EdiFileNamingTemplate template,
                                                   Payer payer) {
        Map<String, String> variables = new HashMap<>();

        // Payer/Payee identifiers
        variables.put("payerId", sanitize(bucket.getPayerId()));
        variables.put("payerName", sanitize(bucket.getPayerName()));
        variables.put("payeeId", sanitize(bucket.getPayeeId()));
        variables.put("payeeName", sanitize(bucket.getPayeeName()));

        // BIN/PCN (if applicable)
        if (bucket.getBinNumber() != null) {
            variables.put("binNumber", sanitize(bucket.getBinNumber()));
        }
        if (bucket.getPcnNumber() != null) {
            variables.put("pcnNumber", sanitize(bucket.getPcnNumber()));
        }

        // Date/time
        LocalDate now = LocalDate.now();
        variables.put("date", now.format(DEFAULT_DATE_FORMAT));
        variables.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));

        // Bucket ID
        variables.put("bucketId", bucket.getBucketId().toString());

        // Sequence number (requires special handling)
        if (template.getTemplatePattern().contains("{sequenceNumber")) {
            Integer sequence = getNextSequenceNumber(template, payer);
            variables.put("sequenceNumber", String.valueOf(sequence));
        }

        return variables;
    }

    /**
     * Parses template and substitutes variables.
     *
     * @param templatePattern the template pattern
     * @param variables map of variable values
     * @return the parsed string with variables substituted
     */
    private String parseTemplate(String templatePattern, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(templatePattern);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String varOption = matcher.group(2);

            String value = substituteVariable(varName, varOption, variables);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Substitutes a single variable with its value.
     *
     * @param varName the variable name
     * @param varOption optional formatting option
     * @param variables map of variable values
     * @return the substituted value
     */
    private String substituteVariable(String varName, String varOption,
                                       Map<String, String> variables) {
        String value = variables.get(varName);

        if (value == null) {
            log.warn("Variable {} not found in variable map, using empty string", varName);
            value = "";
        }

        // Handle special formatting options
        if (varOption != null) {
            value = applyVariableOption(varName, value, varOption);
        }

        return value;
    }

    /**
     * Applies formatting option to a variable value.
     *
     * @param varName the variable name
     * @param value the variable value
     * @param option the formatting option
     * @return the formatted value
     */
    private String applyVariableOption(String varName, String value, String option) {
        return switch (varName) {
            case "date" -> {
                // Custom date format: {date:MM-dd-yyyy}
                try {
                    LocalDate now = LocalDate.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(option);
                    yield now.format(formatter);
                } catch (Exception e) {
                    log.warn("Invalid date format '{}', using default", option);
                    yield value;
                }
            }
            case "sequenceNumber" -> {
                // Padded sequence: {sequenceNumber:6} = 000123
                try {
                    int width = Integer.parseInt(option);
                    yield String.format("%0" + width + "d", Integer.parseInt(value));
                } catch (Exception e) {
                    log.warn("Invalid sequence width '{}', using default", option);
                    yield value;
                }
            }
            default -> value;
        };
    }

    /**
     * Gets next sequence number with thread-safe increment.
     * Handles sequence reset based on reset frequency.
     *
     * @param template the naming template
     * @param payer the payer entity
     * @return the next sequence number
     */
    @Transactional
    private Integer getNextSequenceNumber(EdiFileNamingTemplate template, Payer payer) {
        // Find or create sequence
        Optional<FileNamingSequence> sequenceOpt = sequenceRepository
                .findByTemplateAndPayerForUpdate(template, payer);

        FileNamingSequence sequence;

        if (sequenceOpt.isPresent()) {
            sequence = sequenceOpt.get();

            // Check if sequence needs reset
            if (sequence.shouldReset()) {
                log.info("Resetting sequence for template {} and payer {} (frequency: {})",
                        template.getTemplateName(), payer != null ? payer.getPayerId() : "DEFAULT",
                        sequence.getResetFrequency());
                sequence.reset();
            }

            // Increment sequence
            Integer nextValue = sequence.incrementAndGet();
            sequenceRepository.save(sequence);

            log.debug("Generated sequence number {} for template {} and payer {}",
                    nextValue, template.getTemplateName(),
                    payer != null ? payer.getPayerId() : "DEFAULT");

            return nextValue;

        } else {
            // Create new sequence
            sequence = FileNamingSequence.builder()
                    .template(template)
                    .payer(payer)
                    .currentSequence(1)
                    .resetFrequency(FileNamingSequence.ResetFrequency.DAILY)
                    .build();

            sequenceRepository.save(sequence);

            log.info("Created new sequence for template {} and payer {}",
                    template.getTemplateName(), payer != null ? payer.getPayerId() : "DEFAULT");

            return 1;
        }
    }

    /**
     * Applies case conversion to file name.
     *
     * @param fileName the file name
     * @param template the naming template
     * @return the case-converted file name
     */
    private String applyCaseConversion(String fileName, EdiFileNamingTemplate template) {
        if (template.getCaseConversion() == null) {
            return fileName;
        }

        return switch (template.getCaseConversion()) {
            case UPPER -> fileName.toUpperCase();
            case LOWER -> fileName.toLowerCase();
            case NONE -> fileName;
        };
    }

    /**
     * Capitalizes first letter of each word.
     *
     * @param str the string to capitalize
     * @return capitalized string
     */
    private String capitalizeWords(String str) {
        String[] words = str.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
            if (i < words.length - 1) {
                result.append("_");
            }
        }

        return result.toString();
    }

    /**
     * Sanitizes a string for use in file names.
     * Removes invalid characters and replaces spaces with underscores.
     *
     * @param str the string to sanitize
     * @return sanitized string
     */
    private String sanitize(String str) {
        if (str == null) {
            return "";
        }

        // Replace spaces with underscores
        str = str.replace(" ", "_");

        // Remove invalid file name characters
        str = str.replaceAll("[^a-zA-Z0-9_.-]", "");

        // Trim leading/trailing underscores
        str = str.replaceAll("^_+|_+$", "");

        return str;
    }

    /**
     * Generates a default file name when template is not available.
     *
     * @param bucket the bucket
     * @return default file name
     */
    private String generateDefaultFileName(EdiFileBucket bucket) {
        String date = LocalDate.now().format(DEFAULT_DATE_FORMAT);
        String sanitizedPayerId = sanitize(bucket.getPayerId());
        String sanitizedPayeeId = sanitize(bucket.getPayeeId());
        String bucketIdShort = bucket.getBucketId().toString().substring(0, 8);

        return String.format("%s_%s_%s_%s.835",
                sanitizedPayerId, sanitizedPayeeId, date, bucketIdShort);
    }

    /**
     * Validates a file naming template pattern.
     *
     * @param templatePattern the template pattern to validate
     * @return validation result with any errors
     */
    public TemplateValidationResult validateTemplate(String templatePattern) {
        TemplateValidationResult result = new TemplateValidationResult();

        if (templatePattern == null || templatePattern.trim().isEmpty()) {
            result.addError("Template pattern cannot be empty");
            return result;
        }

        // Check for balanced braces
        int openBraces = templatePattern.length() - templatePattern.replace("{", "").length();
        int closeBraces = templatePattern.length() - templatePattern.replace("}", "").length();

        if (openBraces != closeBraces) {
            result.addError("Unbalanced braces in template pattern");
        }

        // Check for invalid characters in file name
        String testFileName = templatePattern.replaceAll("\\{[^}]+\\}", "X");
        if (testFileName.matches(".*[<>:\"/\\\\|?*].*")) {
            result.addError("Template contains invalid file name characters");
        }

        // Validate variable names
        Matcher matcher = VARIABLE_PATTERN.matcher(templatePattern);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!isValidVariableName(varName)) {
                result.addWarning("Unknown variable: " + varName);
            }
        }

        return result;
    }

    /**
     * Checks if a variable name is valid.
     *
     * @param varName the variable name
     * @return true if valid
     */
    private boolean isValidVariableName(String varName) {
        return switch (varName) {
            case "payerId", "payerName", "payeeId", "payeeName",
                 "binNumber", "pcnNumber", "date", "timestamp",
                 "sequenceNumber", "bucketId" -> true;
            default -> false;
        };
    }

    /**
     * Template validation result.
     */
    public static class TemplateValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public java.util.List<String> getErrors() {
            return errors;
        }

        public java.util.List<String> getWarnings() {
            return warnings;
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}
