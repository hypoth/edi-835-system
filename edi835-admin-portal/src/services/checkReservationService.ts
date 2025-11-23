import apiClient from './apiClient';
import {
  CheckReservation,
  CreateCheckReservationRequest,
  ReservationSummary,
} from '../types/models';

/**
 * Service for check reservation operations.
 * Handles pre-allocated check number ranges for auto-approval workflow.
 */
export const checkReservationService = {
  /**
   * Creates a new check reservation range.
   */
  createReservation: async (
    request: CreateCheckReservationRequest
  ): Promise<CheckReservation> => {
    const response = await apiClient.post('/check-reservations', request);
    return response.data;
  },

  /**
   * Gets all active reservations.
   * Returns reservations with available checks.
   */
  getActiveReservations: async (): Promise<CheckReservation[]> => {
    const response = await apiClient.get('/check-reservations/active');
    return response.data;
  },

  /**
   * Gets reservations for a specific payer.
   */
  getReservationsForPayer: async (payerId: string): Promise<CheckReservation[]> => {
    const response = await apiClient.get(`/check-reservations/payer/${payerId}`);
    return response.data;
  },

  /**
   * Gets low stock reservations.
   * Returns reservations below configured threshold.
   */
  getLowStockReservations: async (): Promise<CheckReservation[]> => {
    const response = await apiClient.get('/check-reservations/low-stock');
    return response.data;
  },

  /**
   * Gets total available checks for a payer.
   */
  getAvailableChecksForPayer: async (
    payerId: string
  ): Promise<{ payerId: string; availableChecks: number }> => {
    const response = await apiClient.get(`/check-reservations/payer/${payerId}/available-count`);
    return response.data;
  },

  /**
   * Gets reservation summary statistics.
   */
  getReservationSummary: async (): Promise<ReservationSummary> => {
    const response = await apiClient.get('/check-reservations/summary');
    return response.data;
  },

  /**
   * Gets all check reservations (all statuses).
   */
  getAllReservations: async (): Promise<CheckReservation[]> => {
    const response = await apiClient.get('/check-reservations');
    return response.data;
  },

  /**
   * Gets a specific reservation by ID.
   */
  getReservation: async (reservationId: string): Promise<CheckReservation> => {
    const response = await apiClient.get(`/check-reservations/${reservationId}`);
    return response.data;
  },

  /**
   * Updates a check reservation.
   */
  updateReservation: async (
    reservationId: string,
    request: Partial<CreateCheckReservationRequest>
  ): Promise<CheckReservation> => {
    const response = await apiClient.put(`/check-reservations/${reservationId}`, request);
    return response.data;
  },

  /**
   * Cancels a check reservation.
   * Can only cancel unused reservations (checksUsed = 0).
   */
  cancelReservation: async (reservationId: string, cancelledBy: string): Promise<void> => {
    await apiClient.post(`/check-reservations/${reservationId}/cancel`, null, {
      params: { cancelledBy },
    });
  },

  /**
   * Deletes a check reservation.
   */
  deleteReservation: async (reservationId: string): Promise<void> => {
    await apiClient.delete(`/check-reservations/${reservationId}`);
  },
};
