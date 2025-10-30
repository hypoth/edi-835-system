import apiClient from './apiClient';
import {
  Payer,
  Payee,
  BucketingRule,
  GenerationThreshold,
  CommitCriteria,
  FileNamingTemplate,
} from '../types/models';

export const configurationService = {
  // ===== Payers =====
  getAllPayers: async (): Promise<Payer[]> => {
    const response = await apiClient.get('/config/payers');
    return response.data;
  },

  getPayerById: async (id: string): Promise<Payer> => {
    const response = await apiClient.get(`/config/payers/${id}`);
    return response.data;
  },

  createPayer: async (payer: Partial<Payer>): Promise<Payer> => {
    const response = await apiClient.post('/config/payers', payer);
    return response.data;
  },

  updatePayer: async (id: string, payer: Partial<Payer>): Promise<Payer> => {
    const response = await apiClient.put(`/config/payers/${id}`, payer);
    return response.data;
  },

  deletePayer: async (id: string): Promise<void> => {
    await apiClient.delete(`/config/payers/${id}`);
  },

  // ===== Payees =====
  getAllPayees: async (): Promise<Payee[]> => {
    const response = await apiClient.get('/config/payees');
    return response.data;
  },

  getPayeeById: async (id: string): Promise<Payee> => {
    const response = await apiClient.get(`/config/payees/${id}`);
    return response.data;
  },

  createPayee: async (payee: Partial<Payee>): Promise<Payee> => {
    const response = await apiClient.post('/config/payees', payee);
    return response.data;
  },

  updatePayee: async (id: string, payee: Partial<Payee>): Promise<Payee> => {
    const response = await apiClient.put(`/config/payees/${id}`, payee);
    return response.data;
  },

  deletePayee: async (id: string): Promise<void> => {
    await apiClient.delete(`/config/payees/${id}`);
  },

  // ===== Bucketing Rules =====
  getAllBucketingRules: async (): Promise<BucketingRule[]> => {
    const response = await apiClient.get('/config/rules');
    return response.data;
  },

  getBucketingRuleById: async (id: string): Promise<BucketingRule> => {
    const response = await apiClient.get(`/config/rules/${id}`);
    return response.data;
  },

  createBucketingRule: async (rule: Partial<BucketingRule>): Promise<BucketingRule> => {
    const response = await apiClient.post('/config/rules', rule);
    return response.data;
  },

  updateBucketingRule: async (id: string, rule: Partial<BucketingRule>): Promise<BucketingRule> => {
    const response = await apiClient.put(`/config/rules/${id}`, rule);
    return response.data;
  },

  deleteBucketingRule: async (id: string): Promise<void> => {
    await apiClient.delete(`/config/rules/${id}`);
  },

  // ===== Generation Thresholds =====
  getAllThresholds: async (): Promise<GenerationThreshold[]> => {
    const response = await apiClient.get('/config/thresholds');
    return response.data;
  },

  getThresholdById: async (id: string): Promise<GenerationThreshold> => {
    const response = await apiClient.get(`/config/thresholds/${id}`);
    return response.data;
  },

  createThreshold: async (threshold: Partial<GenerationThreshold>): Promise<GenerationThreshold> => {
    const response = await apiClient.post('/config/thresholds', threshold);
    return response.data;
  },

  updateThreshold: async (id: string, threshold: Partial<GenerationThreshold>): Promise<GenerationThreshold> => {
    const response = await apiClient.put(`/config/thresholds/${id}`, threshold);
    return response.data;
  },

  deleteThreshold: async (id: string): Promise<void> => {
    await apiClient.delete(`/config/thresholds/${id}`);
  },

  // Test threshold with simulated scenario
  testThreshold: async (thresholdData: Partial<GenerationThreshold>, scenario: any): Promise<any> => {
    const response = await apiClient.post('/config/thresholds/test', {
      threshold: thresholdData,
      scenario,
    });
    return response.data;
  },

  // Get analytics for a specific threshold
  getThresholdAnalytics: async (id: string): Promise<any> => {
    const response = await apiClient.get(`/config/thresholds/${id}/analytics`);
    return response.data;
  },

  // Get buckets affected by a threshold
  getAffectedBuckets: async (thresholdData: Partial<GenerationThreshold>): Promise<any[]> => {
    const response = await apiClient.post('/config/thresholds/affected-buckets', thresholdData);
    return response.data;
  },

  // Bulk activate/deactivate thresholds
  bulkUpdateThresholdStatus: async (thresholdIds: string[], isActive: boolean): Promise<void> => {
    await apiClient.post('/config/thresholds/bulk-update-status', {
      thresholdIds,
      isActive,
    });
  },

  // ===== Commit Criteria =====
  getAllCommitCriteria: async (): Promise<CommitCriteria[]> => {
    const response = await apiClient.get('/config/commit-criteria');
    return response.data;
  },

  getCommitCriteriaById: async (id: string): Promise<CommitCriteria> => {
    const response = await apiClient.get(`/config/commit-criteria/${id}`);
    return response.data;
  },

  createCommitCriteria: async (criteria: Partial<CommitCriteria>): Promise<CommitCriteria> => {
    const response = await apiClient.post('/config/commit-criteria', criteria);
    return response.data;
  },

  updateCommitCriteria: async (id: string, criteria: Partial<CommitCriteria>): Promise<CommitCriteria> => {
    const response = await apiClient.put(`/config/commit-criteria/${id}`, criteria);
    return response.data;
  },

  deleteCommitCriteria: async (id: string): Promise<void> => {
    await apiClient.delete(`/config/commit-criteria/${id}`);
  },

  // ===== File Naming Templates =====
  getAllTemplates: async (): Promise<FileNamingTemplate[]> => {
    const response = await apiClient.get('/config/templates');
    return response.data;
  },

  getTemplateById: async (id: string): Promise<FileNamingTemplate> => {
    const response = await apiClient.get(`/config/templates/${id}`);
    return response.data;
  },

  createTemplate: async (template: Partial<FileNamingTemplate>): Promise<FileNamingTemplate> => {
    const response = await apiClient.post('/config/templates', template);
    return response.data;
  },

  updateTemplate: async (id: string, template: Partial<FileNamingTemplate>): Promise<FileNamingTemplate> => {
    const response = await apiClient.put(`/config/templates/${id}`, template);
    return response.data;
  },

  deleteTemplate: async (id: string): Promise<void> => {
    await apiClient.delete(`/config/templates/${id}`);
  },

  // ===== Validation =====
  validateConfiguration: async () => {
    const response = await apiClient.get('/config/validate');
    return response.data;
  },
};
