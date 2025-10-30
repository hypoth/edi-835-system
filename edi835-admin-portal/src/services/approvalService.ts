import apiClient from './apiClient';
import { Bucket, ApprovalLog, ApprovalRequest } from '../types/models';

export const approvalService = {
  // Get pending approvals
  getPendingApprovals: async (): Promise<Bucket[]> => {
    const response = await apiClient.get('/approvals/pending');
    return response.data;
  },

  // Get approval history
  getApprovalHistory: async (limit?: number): Promise<ApprovalLog[]> => {
    const params = limit ? `?limit=${limit}` : '';
    const response = await apiClient.get(`/approvals/history${params}`);
    return response.data;
  },

  // Get approvals by bucket
  getApprovalsByBucket: async (bucketId: string): Promise<ApprovalLog[]> => {
    const response = await apiClient.get(`/approvals/history/${bucketId}`);
    return response.data;
  },

  // Approve bucket
  approveBucket: async (bucketId: string, request: ApprovalRequest): Promise<void> => {
    await apiClient.post(`/approvals/approve/${bucketId}`, {
      approvedBy: request.actionBy,
      comments: request.comments,
    });
  },

  // Reject bucket
  rejectBucket: async (bucketId: string, request: ApprovalRequest): Promise<void> => {
    await apiClient.post(`/approvals/reject/${bucketId}`, {
      approvedBy: request.actionBy,
      rejectionReason: request.rejectionReason,
      comments: request.comments,
    });
  },

  // Bulk approve buckets
  bulkApproveBuckets: async (bucketIds: string[], request: ApprovalRequest): Promise<void> => {
    await apiClient.post('/approvals/approve/bulk', {
      bucketIds,
      approvedBy: request.actionBy,
      comments: request.comments,
    });
  },

  // Get approval statistics
  getApprovalStatistics: async () => {
    const response = await apiClient.get('/approvals/statistics');
    return response.data;
  },

  // Get approvals by user
  getApprovalsByUser: async (userId: string): Promise<ApprovalLog[]> => {
    const response = await apiClient.get(`/approvals/history/user/${userId}`);
    return response.data;
  },
};
