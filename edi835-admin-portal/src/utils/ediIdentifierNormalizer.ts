/**
 * Utility functions for normalizing EDI identifiers to conform to X12 835 validation rules.
 */

/**
 * Normalizes a payer or payee ID to conform to validation rules.
 * - Converts to uppercase
 * - Replaces hyphens, spaces, and dots with underscores
 * - Removes all other special characters
 * - Ensures only A-Z, 0-9, and _ remain
 */
export const normalizePayerPayeeId = (rawId: string): string => {
  if (!rawId) {
    return rawId;
  }

  // Convert to uppercase
  let normalized = rawId.toUpperCase();

  // Replace hyphens, spaces, dots with underscores
  normalized = normalized.replace(/[-\s.]/g, '_');

  // Remove all characters except A-Z, 0-9, and underscore
  normalized = normalized.replace(/[^A-Z0-9_]/g, '');

  // Remove consecutive underscores
  normalized = normalized.replace(/_{2,}/g, '_');

  // Remove leading/trailing underscores
  normalized = normalized.replace(/^_+|_+$/g, '');

  return normalized;
};

/**
 * Generates an ISA Sender ID from a payer ID.
 * - Normalizes to uppercase alphanumeric only (A-Z, 0-9)
 * - Truncates to 15 characters maximum
 * - Removes underscores (not allowed in ISA Sender ID)
 */
export const generateIsaSenderId = (payerId: string): string => {
  if (!payerId) {
    return 'DEFAULT';
  }

  // Start with normalized payer ID
  let isaSenderId = normalizePayerPayeeId(payerId);

  // Remove underscores (not allowed in ISA Sender ID)
  isaSenderId = isaSenderId.replace(/_/g, '');

  // Ensure alphanumeric only
  isaSenderId = isaSenderId.replace(/[^A-Z0-9]/g, '');

  // Truncate to 15 characters
  if (isaSenderId.length > 15) {
    isaSenderId = isaSenderId.substring(0, 15);
  }

  // If empty after normalization, use default
  if (!isaSenderId) {
    isaSenderId = `PAYER${Date.now() % 10000}`;
  }

  return isaSenderId;
};

/**
 * Generates a GS Application Sender ID from a payer ID.
 * Same rules as ISA Sender ID.
 */
export const generateGsApplicationSenderId = (payerId: string): string => {
  return generateIsaSenderId(payerId);
};

/**
 * Normalizes a payee ID (same rules as payer ID).
 */
export const normalizePayeeId = (rawPayeeId: string): string => {
  return normalizePayerPayeeId(rawPayeeId);
};

/**
 * Normalizes a payer ID (convenience method).
 */
export const normalizePayerId = (rawPayerId: string): string => {
  return normalizePayerPayeeId(rawPayerId);
};

/**
 * Validates if a string conforms to payer/payee ID rules.
 */
export const isValidPayerPayeeId = (id: string): boolean => {
  if (!id) {
    return false;
  }
  return /^[A-Z0-9_]+$/.test(id);
};

/**
 * Validates if a string conforms to ISA Sender ID rules.
 */
export const isValidIsaSenderId = (id: string): boolean => {
  if (!id) {
    return false;
  }
  return /^[A-Z0-9]+$/.test(id) && id.length <= 15;
};
