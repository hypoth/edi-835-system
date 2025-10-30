import apiClient from './apiClient';
import { FileHistory, DeliveryStatus } from '../types/models';

export const fileService = {
  // Get all files
  getAllFiles: async (status?: DeliveryStatus): Promise<FileHistory[]> => {
    const params = status ? `?status=${status}` : '';
    const response = await apiClient.get(`/files${params}`);
    return response.data;
  },

  // Get file by ID
  getFileById: async (fileId: string): Promise<FileHistory> => {
    const response = await apiClient.get(`/files/history/${fileId}`);
    return response.data;
  },

  // Get files by bucket
  getFilesByBucket: async (bucketId: string): Promise<FileHistory[]> => {
    const response = await apiClient.get(`/files/history/bucket/${bucketId}`);
    return response.data;
  },

  // Get pending delivery files
  getPendingDeliveryFiles: async (): Promise<FileHistory[]> => {
    const response = await apiClient.get('/delivery/pending');
    return response.data;
  },

  // Get failed delivery files
  getFailedDeliveryFiles: async (): Promise<FileHistory[]> => {
    const response = await apiClient.get('/delivery/failed');
    return response.data;
  },

  // Search files
  searchFiles: async (params: {
    payerId?: string;
    payeeId?: string;
    status?: DeliveryStatus;
    startDate?: string;
    endDate?: string;
  }): Promise<FileHistory[]> => {
    const queryParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value) queryParams.append(key, value);
    });
    const response = await apiClient.get(`/files/search?${queryParams.toString()}`);
    return response.data;
  },

  // Download file
  downloadFile: async (fileId: string): Promise<Blob> => {
    const response = await apiClient.get(`/files/download/${fileId}`, {
      responseType: 'blob',
    });
    return response.data;
  },

  // Deliver file (for pending files)
  deliverFile: async (fileId: string): Promise<void> => {
    await apiClient.post(`/delivery/deliver/${fileId}`);
  },

  // Retry delivery (for failed files)
  retryDelivery: async (fileId: string): Promise<void> => {
    await apiClient.post(`/delivery/retry/${fileId}`);
  },

  // Get file statistics
  getFileStatistics: async () => {
    const response = await apiClient.get('/files/statistics');
    return response.data;
  },

  // Get recent files
  getRecentFiles: async (limit: number = 10): Promise<FileHistory[]> => {
    const response = await apiClient.get(`/files/history/recent?limit=${limit}`);
    return response.data;
  },
};
