import apiClient from './apiClient';
import { DashboardSummary, SystemHealth, Alert } from '../types/models';

export const dashboardService = {
  // Get dashboard summary
  getSummary: async (): Promise<DashboardSummary> => {
    const response = await apiClient.get('/dashboard/summary');
    return response.data;
  },

  // Get system health
  getSystemHealth: async (): Promise<SystemHealth> => {
    const response = await apiClient.get('/dashboard/health');
    return response.data;
  },

  // Get system alerts
  getAlerts: async (): Promise<Alert[]> => {
    const response = await apiClient.get('/dashboard/alerts');
    return response.data;
  },

  // Get bucket statistics
  getBucketStatistics: async () => {
    const response = await apiClient.get('/dashboard/bucket-statistics');
    return response.data;
  },

  // Get file statistics
  getFileStatistics: async () => {
    const response = await apiClient.get('/dashboard/file-statistics');
    return response.data;
  },

  // Get delivery statistics
  getDeliveryStatistics: async () => {
    const response = await apiClient.get('/dashboard/delivery-statistics');
    return response.data;
  },

  // Get processing trends
  getProcessingTrends: async (days: number = 30) => {
    const response = await apiClient.get(`/dashboard/trends?days=${days}`);
    return response.data;
  },

  // Get active buckets
  getActiveBuckets: async () => {
    const response = await apiClient.get('/buckets/active');
    return response.data;
  },

  // Get pending approvals
  getPendingApprovals: async () => {
    const response = await apiClient.get('/buckets/pending-approval');
    return response.data;
  },

  // Get rejection analytics
  getRejectionAnalytics: async () => {
    const response = await apiClient.get('/dashboard/rejection-analytics');
    return response.data;
  },
};