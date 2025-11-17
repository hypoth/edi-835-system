package com.healthcare.edi835.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for normalizing identifiers to conform to EDI X12 835 validation rules.
 *
 * <p>Normalization Rules:</p>
 * <ul>
 *   <li>Payer/Payee IDs: Uppercase letters, numbers, and underscores only (A-Z, 0-9, _)</li>
 *   <li>ISA Sender ID: Uppercase letters and numbers only (A-Z, 0-9), max 15 chars</li>
 *   <li>GS Application Sender ID: Uppercase letters and numbers only (A-Z, 0-9), max 15 chars</li>
 * </ul>
 */
@Slf4j
public class EdiIdentifierNormalizer {

    /**
     * Normalizes a payer or payee ID to conform to validation rules.
     * - Converts to uppercase
     * - Replaces hyphens, spaces, and dots with underscores
     * - Removes all other special characters
     * - Ensures only A-Z, 0-9, and _ remain
     *
     * @param rawId the raw ID from D0 claims
     * @return normalized ID conforming to validation rules
     */
    public static String normalizePayerPayeeId(String rawId) {
        if (rawId == null || rawId.isEmpty()) {
            return rawId;
        }

        // Convert to uppercase
        String normalized = rawId.toUpperCase();

        // Replace hyphens, spaces, dots with underscores
        normalized = normalized.replaceAll("[-\\s.]", "_");

        // Remove all characters except A-Z, 0-9, and underscore
        normalized = normalized.replaceAll("[^A-Z0-9_]", "");

        // Remove consecutive underscores
        normalized = normalized.replaceAll("_{2,}", "_");

        // Remove leading/trailing underscores
        normalized = normalized.replaceAll("^_+|_+$", "");

        if (!normalized.equals(rawId)) {
            log.debug("Normalized ID: '{}' -> '{}'", rawId, normalized);
        }

        return normalized;
    }

    /**
     * Generates an ISA Sender ID from a payer ID.
     * - Normalizes to uppercase alphanumeric only (A-Z, 0-9)
     * - Truncates to 15 characters maximum
     * - Removes underscores (not allowed in ISA Sender ID)
     *
     * @param payerId the payer ID
     * @return ISA Sender ID (max 15 chars, alphanumeric only)
     */
    public static String generateIsaSenderId(String payerId) {
        if (payerId == null || payerId.isEmpty()) {
            return "DEFAULT";
        }

        // Start with normalized payer ID
        String isaSenderId = normalizePayerPayeeId(payerId);

        // Remove underscores (not allowed in ISA Sender ID)
        isaSenderId = isaSenderId.replaceAll("_", "");

        // Ensure alphanumeric only
        isaSenderId = isaSenderId.replaceAll("[^A-Z0-9]", "");

        // Truncate to 15 characters
        if (isaSenderId.length() > 15) {
            isaSenderId = isaSenderId.substring(0, 15);
        }

        // If empty after normalization, use default
        if (isaSenderId.isEmpty()) {
            isaSenderId = "PAYER" + System.currentTimeMillis() % 10000;
        }

        log.debug("Generated ISA Sender ID for payer '{}': '{}'", payerId, isaSenderId);

        return isaSenderId;
    }

    /**
     * Generates a GS Application Sender ID from a payer ID.
     * Same rules as ISA Sender ID.
     *
     * @param payerId the payer ID
     * @return GS Application Sender ID (max 15 chars, alphanumeric only)
     */
    public static String generateGsApplicationSenderId(String payerId) {
        // Same logic as ISA Sender ID
        return generateIsaSenderId(payerId);
    }

    /**
     * Normalizes a payee ID (same rules as payer ID).
     *
     * @param rawPayeeId the raw payee ID from D0 claims
     * @return normalized payee ID
     */
    public static String normalizePayeeId(String rawPayeeId) {
        return normalizePayerPayeeId(rawPayeeId);
    }

    /**
     * Normalizes a payer ID (convenience method).
     *
     * @param rawPayerId the raw payer ID from D0 claims
     * @return normalized payer ID
     */
    public static String normalizePayerId(String rawPayerId) {
        return normalizePayerPayeeId(rawPayerId);
    }

    /**
     * Validates if a string conforms to payer/payee ID rules.
     *
     * @param id the ID to validate
     * @return true if valid
     */
    public static boolean isValidPayerPayeeId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return id.matches("^[A-Z0-9_]+$");
    }

    /**
     * Validates if a string conforms to ISA Sender ID rules.
     *
     * @param id the ID to validate
     * @return true if valid
     */
    public static boolean isValidIsaSenderId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return id.matches("^[A-Z0-9]+$") && id.length() <= 15;
    }
}
