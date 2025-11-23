import apiClient from './apiClient';
import {
  CheckPaymentWorkflowConfig,
  CheckPaymentWorkflowConfigRequest,
} from '../types/models';

/**
 * Service for check payment workflow configuration operations.
 * Manages workflow settings linked to thresholds/bucketing rules.
 */
export const checkPaymentWorkflowService = {
  /**
   * Creates a new workflow configuration.
   */
  createWorkflowConfig: async (
    request: CheckPaymentWorkflowConfigRequest
  ): Promise<CheckPaymentWorkflowConfig> => {
    const response = await apiClient.post('/config/check-payment-workflow', request);
    return response.data;
  },

  /**
   * Updates an existing workflow configuration.
   */
  updateWorkflowConfig: async (
    id: string,
    request: CheckPaymentWorkflowConfigRequest
  ): Promise<CheckPaymentWorkflowConfig> => {
    const response = await apiClient.put(`/config/check-payment-workflow/${id}`, request);
    return response.data;
  },

  /**
   * Gets workflow configuration by ID.
   */
  getWorkflowConfig: async (id: string): Promise<CheckPaymentWorkflowConfig> => {
    const response = await apiClient.get(`/config/check-payment-workflow/${id}`);
    return response.data;
  },

  /**
   * Gets workflow configuration by threshold ID.
   */
  getWorkflowConfigByThreshold: async (
    thresholdId: string
  ): Promise<CheckPaymentWorkflowConfig | null> => {
    try {
      const response = await apiClient.get(
        `/config/check-payment-workflow/threshold/${thresholdId}`
      );
      return response.data;
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  },

  /**
   * Gets all active workflow configurations.
   */
  getActiveWorkflowConfigs: async (): Promise<CheckPaymentWorkflowConfig[]> => {
    const response = await apiClient.get('/config/check-payment-workflow/active');
    return response.data;
  },

  /**
   * Gets all workflow configurations.
   */
  getAllWorkflowConfigs: async (): Promise<CheckPaymentWorkflowConfig[]> => {
    const response = await apiClient.get('/config/check-payment-workflow');
    return response.data;
  },

  /**
   * Deletes a workflow configuration.
   */
  deleteWorkflowConfig: async (id: string): Promise<void> => {
    await apiClient.delete(`/config/check-payment-workflow/${id}`);
  },
};
