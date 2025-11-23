import apiClient from './apiClient';
import {
  Bucket,
  BucketStatus,
  BucketConfigurationCheck,
  CreatePayerFromBucketRequest,
  CreatePayeeFromBucketRequest,
  Payer,
  Payee
} from '../types/models';

export const bucketService = {
  // Get all buckets
  getAllBuckets: async (status?: BucketStatus): Promise<Bucket[]> => {
    const params = status ? `?status=${status}` : '';
    const response = await apiClient.get(`/buckets${params}`);
    return response.data;
  },

  // Get active buckets
  getActiveBuckets: async (): Promise<Bucket[]> => {
    const response = await apiClient.get('/buckets/active');
    return response.data;
  },

  // Get bucket by ID
  getBucketById: async (bucketId: string): Promise<Bucket> => {
    const response = await apiClient.get(`/buckets/${bucketId}`);
    return response.data;
  },

  // Get buckets by payer
  getBucketsByPayer: async (payerId: string): Promise<Bucket[]> => {
    const response = await apiClient.get(`/buckets/payer/${payerId}`);
    return response.data;
  },

  // Search buckets
  searchBuckets: async (params: {
    payerId?: string;
    payeeId?: string;
    status?: BucketStatus;
    binNumber?: string;
  }): Promise<Bucket[]> => {
    const queryParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value) queryParams.append(key, value);
    });
    const response = await apiClient.get(`/buckets/search?${queryParams.toString()}`);
    return response.data;
  },

  // Get bucket statistics
  getBucketStatistics: async () => {
    const response = await apiClient.get('/buckets/statistics');
    return response.data;
  },

  // Get bucket counts
  getBucketCounts: async () => {
    const response = await apiClient.get('/buckets/count');
    return response.data;
  },

  // Evaluate thresholds
  evaluateThresholds: async (bucketId: string): Promise<void> => {
    await apiClient.post(`/buckets/${bucketId}/evaluate-thresholds`);
  },

  // Transition to generation
  transitionToGeneration: async (bucketId: string): Promise<void> => {
    await apiClient.post(`/buckets/${bucketId}/transition-to-generation`);
  },

  // Transition to pending approval
  transitionToPendingApproval: async (bucketId: string): Promise<void> => {
    await apiClient.post(`/buckets/${bucketId}/transition-to-approval`);
  },

  // Mark completed
  markCompleted: async (bucketId: string): Promise<void> => {
    await apiClient.post(`/buckets/${bucketId}/mark-completed`);
  },

  // Mark failed
  markFailed: async (bucketId: string): Promise<void> => {
    await apiClient.post(`/buckets/${bucketId}/mark-failed`);
  },

  // Evaluate all thresholds
  evaluateAllThresholds: async () => {
    const response = await apiClient.post('/buckets/evaluate-all-thresholds');
    return response.data;
  },

  // Get recent buckets
  getRecentBuckets: async (limit: number = 10): Promise<Bucket[]> => {
    const response = await apiClient.get(`/buckets/recent?limit=${limit}`);
    return response.data;
  },

  // Configuration management
  checkConfiguration: async (bucketId: string): Promise<BucketConfigurationCheck> => {
    const response = await apiClient.get(`/buckets/${bucketId}/configuration-check`);
    return response.data;
  },

  createPayerFromBucket: async (request: CreatePayerFromBucketRequest): Promise<{ message: string; payer: Payer }> => {
    const response = await apiClient.post(`/buckets/${request.bucketId}/create-payer`, request);
    return response.data;
  },

  createPayeeFromBucket: async (request: CreatePayeeFromBucketRequest): Promise<{ message: string; payee: Payee }> => {
    const response = await apiClient.post(`/buckets/${request.bucketId}/create-payee`, request);
    return response.data;
  },

  // Get bucket configuration (threshold, workflow config, commit criteria)
  getBucketConfiguration: async (bucketId: string): Promise<BucketConfiguration> => {
    const response = await apiClient.get(`/buckets/${bucketId}/configuration`);
    return response.data;
  },
};

// Type for bucket configuration response
export interface BucketConfiguration {
  threshold?: {
    thresholdId: string;
    thresholdName: string;
    thresholdType: string;
    maxClaims?: number;
    maxAmount?: number;
    timeDuration?: string;
    isActive: boolean;
  };
  workflowConfig?: {
    id: string;
    configName: string;
    workflowMode: string;
    assignmentMode: string;
    requireAcknowledgment: boolean;
    description?: string;
    isActive: boolean;
  };
  commitCriteria?: {
    id: string;
    criteriaName: string;
    commitMode: string;
    autoCommitThreshold?: number;
    manualApprovalThreshold?: number;
    approvalRequiredRoles?: string[];
    isActive: boolean;
  };
}
