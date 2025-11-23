import apiClient from './apiClient';
import {
  CheckPayment,
  CheckAuditLog,
  ManualCheckAssignmentRequest,
  AcknowledgeCheckRequest,
  IssueCheckRequest,
  VoidCheckRequest,
} from '../types/models';

/**
 * Service for check payment operations.
 * Handles manual/auto check assignment, acknowledgment, issuance, and voiding.
 */
export const checkPaymentService = {
  /**
   * Assigns a check manually to a bucket during approval.
   * User enters all check details.
   */
  assignCheckManually: async (
    bucketId: string,
    request: ManualCheckAssignmentRequest
  ): Promise<CheckPayment> => {
    const response = await apiClient.post(
      `/check-payments/buckets/${bucketId}/assign-manual`,
      request
    );
    return response.data;
  },

  /**
   * Auto-assigns a check from available reservations to a bucket.
   * System automatically picks next available check from reservation pool.
   */
  assignCheckAutomatically: async (
    bucketId: string,
    assignedBy: string
  ): Promise<CheckPayment> => {
    const response = await apiClient.post(
      `/check-payments/buckets/${bucketId}/assign-auto`,
      { assignedBy }
    );
    return response.data;
  },

  /**
   * Gets check payment for a specific bucket.
   */
  getCheckPaymentForBucket: async (bucketId: string): Promise<CheckPayment | null> => {
    try {
      const response = await apiClient.get(`/check-payments/buckets/${bucketId}`);
      return response.data;
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  },

  /**
   * Gets check payment by ID.
   */
  getCheckPayment: async (checkPaymentId: string): Promise<CheckPayment> => {
    const response = await apiClient.get(`/check-payments/${checkPaymentId}`);
    return response.data;
  },

  /**
   * Gets all pending acknowledgments.
   * Returns checks in ASSIGNED status awaiting user acknowledgment.
   */
  getPendingAcknowledgments: async (): Promise<CheckPayment[]> => {
    const response = await apiClient.get('/check-payments/pending-acknowledgments');
    return response.data;
  },

  /**
   * Acknowledges a check payment amount.
   * User verifies the check amount before issuance.
   */
  acknowledgeCheck: async (
    checkPaymentId: string,
    request: AcknowledgeCheckRequest
  ): Promise<CheckPayment> => {
    const response = await apiClient.post(
      `/check-payments/${checkPaymentId}/acknowledge`,
      request
    );
    return response.data;
  },

  /**
   * Marks a check as issued (physically mailed/delivered).
   */
  markCheckIssued: async (
    checkPaymentId: string,
    request: IssueCheckRequest
  ): Promise<CheckPayment> => {
    const response = await apiClient.post(
      `/check-payments/${checkPaymentId}/issue`,
      request
    );
    return response.data;
  },

  /**
   * Voids a check payment.
   * Requires FINANCIAL_ADMIN role and must be within configured time limit.
   */
  voidCheck: async (
    checkPaymentId: string,
    request: VoidCheckRequest
  ): Promise<CheckPayment> => {
    const response = await apiClient.post(
      `/check-payments/${checkPaymentId}/void`,
      request
    );
    return response.data;
  },

  /**
   * Gets audit trail for a check payment.
   * Returns complete history of all operations performed on the check.
   */
  getCheckAuditTrail: async (checkPaymentId: string): Promise<CheckAuditLog[]> => {
    const response = await apiClient.get(`/check-payments/${checkPaymentId}/audit-trail`);
    return response.data;
  },

  /**
   * Gets all check payments.
   * Returns all checks regardless of status.
   */
  getAllCheckPayments: async (): Promise<CheckPayment[]> => {
    const response = await apiClient.get('/check-payments');
    return response.data;
  },

  /**
   * Gets check payments by status.
   */
  getCheckPaymentsByStatus: async (status: string): Promise<CheckPayment[]> => {
    const response = await apiClient.get('/check-payments', {
      params: { status },
    });
    return response.data;
  },

  /**
   * Gets check payments for a specific payer.
   */
  getCheckPaymentsForPayer: async (payerId: string): Promise<CheckPayment[]> => {
    const response = await apiClient.get(`/check-payments/payer/${payerId}`);
    return response.data;
  },

  /**
   * Replaces an existing check assignment with a new check.
   * Voids the old check and assigns a new one.
   * Only allowed for buckets in PENDING_APPROVAL status with ASSIGNED payment.
   */
  replaceCheck: async (
    bucketId: string,
    request: ManualCheckAssignmentRequest
  ): Promise<{ message: string; checkPayment: CheckPayment }> => {
    const response = await apiClient.post(
      `/check-payments/buckets/${bucketId}/replace`,
      request
    );
    return response.data;
  },
};
