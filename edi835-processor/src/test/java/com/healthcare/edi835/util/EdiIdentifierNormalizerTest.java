package com.healthcare.edi835.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EdiIdentifierNormalizer.
 */
class EdiIdentifierNormalizerTest {

    @Test
    void testNormalizePayerId_WithHyphens() {
        String input = "BCBS-CA";
        String expected = "BCBS_CA";

        String result = EdiIdentifierNormalizer.normalizePayerId(input);

        assertEquals(expected, result);
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId(result));
    }

    @Test
    void testNormalizePayerId_WithLowercase() {
        String input = "bcbs-ca";
        String expected = "BCBS_CA";

        String result = EdiIdentifierNormalizer.normalizePayerId(input);

        assertEquals(expected, result);
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId(result));
    }

    @Test
    void testNormalizePayerId_WithSpaces() {
        String input = "Blue Cross Blue Shield";
        String expected = "BLUE_CROSS_BLUE_SHIELD";

        String result = EdiIdentifierNormalizer.normalizePayerId(input);

        assertEquals(expected, result);
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId(result));
    }

    @Test
    void testNormalizePayerId_WithSpecialCharacters() {
        String input = "BCBS@CA#2024";
        String expected = "BCBSCA2024";

        String result = EdiIdentifierNormalizer.normalizePayerId(input);

        assertEquals(expected, result);
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId(result));
    }

    @Test
    void testNormalizePayerId_WithDots() {
        String input = "United.Health.Care";
        String expected = "UNITED_HEALTH_CARE";

        String result = EdiIdentifierNormalizer.normalizePayerId(input);

        assertEquals(expected, result);
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId(result));
    }

    @Test
    void testNormalizePayerId_AlreadyValid() {
        String input = "UHC_001";

        String result = EdiIdentifierNormalizer.normalizePayerId(input);

        assertEquals(input, result);
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId(result));
    }

    @Test
    void testGenerateIsaSenderId_FromPayerId() {
        String payerId = "BCBS-CA";

        String result = EdiIdentifierNormalizer.generateIsaSenderId(payerId);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.length() <= 15);
        assertTrue(EdiIdentifierNormalizer.isValidIsaSenderId(result));
        // Should not contain underscores
        assertFalse(result.contains("_"));
    }

    @Test
    void testGenerateIsaSenderId_LongPayerId() {
        String payerId = "VERY_LONG_PAYER_IDENTIFIER_EXCEEDING_LIMIT";

        String result = EdiIdentifierNormalizer.generateIsaSenderId(payerId);

        assertNotNull(result);
        assertTrue(result.length() <= 15);
        assertTrue(EdiIdentifierNormalizer.isValidIsaSenderId(result));
        assertEquals("VERYLONGPAYERID", result);
    }

    @Test
    void testGenerateIsaSenderId_WithUnderscores() {
        String payerId = "UHC_WEST_001";

        String result = EdiIdentifierNormalizer.generateIsaSenderId(payerId);

        assertNotNull(result);
        assertTrue(result.length() <= 15);
        assertTrue(EdiIdentifierNormalizer.isValidIsaSenderId(result));
        // Underscores should be removed
        assertEquals("UHCWEST001", result);
    }

    @Test
    void testIsValidPayerPayeeId_Valid() {
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId("BCBS_CA"));
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId("UHC001"));
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId("PAYER_123"));
    }

    @Test
    void testIsValidPayerPayeeId_Invalid() {
        assertFalse(EdiIdentifierNormalizer.isValidPayerPayeeId("bcbs-ca")); // lowercase and hyphen
        assertFalse(EdiIdentifierNormalizer.isValidPayerPayeeId("BCBS-CA")); // hyphen
        assertFalse(EdiIdentifierNormalizer.isValidPayerPayeeId("BCBS CA")); // space
        assertFalse(EdiIdentifierNormalizer.isValidPayerPayeeId("BCBS@CA")); // special char
        assertFalse(EdiIdentifierNormalizer.isValidPayerPayeeId(null)); // null
        assertFalse(EdiIdentifierNormalizer.isValidPayerPayeeId("")); // empty
    }

    @Test
    void testIsValidIsaSenderId_Valid() {
        assertTrue(EdiIdentifierNormalizer.isValidIsaSenderId("BCBSCA"));
        assertTrue(EdiIdentifierNormalizer.isValidIsaSenderId("UHC001"));
        assertTrue(EdiIdentifierNormalizer.isValidIsaSenderId("PAYER123"));
        assertTrue(EdiIdentifierNormalizer.isValidIsaSenderId("A123456789BCDEF")); // 15 chars
    }

    @Test
    void testIsValidIsaSenderId_Invalid() {
        assertFalse(EdiIdentifierNormalizer.isValidIsaSenderId("BCBS_CA")); // underscore
        assertFalse(EdiIdentifierNormalizer.isValidIsaSenderId("bcbsca")); // lowercase
        assertFalse(EdiIdentifierNormalizer.isValidIsaSenderId("BCBS-CA")); // hyphen
        assertFalse(EdiIdentifierNormalizer.isValidIsaSenderId("1234567890123456")); // 16 chars (too long)
        assertFalse(EdiIdentifierNormalizer.isValidIsaSenderId(null)); // null
        assertFalse(EdiIdentifierNormalizer.isValidIsaSenderId("")); // empty
    }

    @Test
    void testNormalizePayeeId() {
        // Payee ID uses same rules as payer ID
        String input = "provider-npi-12345";
        String expected = "PROVIDER_NPI_12345";

        String result = EdiIdentifierNormalizer.normalizePayeeId(input);

        assertEquals(expected, result);
        assertTrue(EdiIdentifierNormalizer.isValidPayerPayeeId(result));
    }

    @Test
    void testGenerateGsApplicationSenderId() {
        String payerId = "BCBS-CA";

        String result = EdiIdentifierNormalizer.generateGsApplicationSenderId(payerId);

        assertNotNull(result);
        assertTrue(result.length() <= 15);
        assertTrue(EdiIdentifierNormalizer.isValidIsaSenderId(result));
    }
}
