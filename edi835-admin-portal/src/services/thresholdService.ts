import apiClient from './apiClient';
import { Threshold } from '../types/models';

/**
 * Service for threshold operations.
 */
export const thresholdService = {
  /**
   * Gets all thresholds.
   */
  getAllThresholds: async (): Promise<Threshold[]> => {
    const response = await apiClient.get('/config/thresholds');
    return response.data;
  },

  /**
   * Gets threshold by ID.
   */
  getThreshold: async (id: string): Promise<Threshold> => {
    const response = await apiClient.get(`/config/thresholds/${id}`);
    return response.data;
  },

  /**
   * Creates a new threshold.
   */
  createThreshold: async (threshold: any): Promise<Threshold> => {
    const response = await apiClient.post('/config/thresholds', threshold);
    return response.data;
  },

  /**
   * Updates a threshold.
   */
  updateThreshold: async (id: string, threshold: any): Promise<Threshold> => {
    const response = await apiClient.put(`/config/thresholds/${id}`, threshold);
    return response.data;
  },

  /**
   * Deletes a threshold.
   */
  deleteThreshold: async (id: string): Promise<void> => {
    await apiClient.delete(`/config/thresholds/${id}`);
  },
};
