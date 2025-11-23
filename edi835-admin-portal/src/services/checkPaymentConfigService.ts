import apiClient from './apiClient';

/**
 * Check payment configuration DTO.
 */
export interface CheckPaymentConfig {
  id: string;
  configKey: string;
  configValue: string;
  description: string;
  valueType: 'STRING' | 'INTEGER' | 'BOOLEAN' | 'EMAIL';
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy: string;
  displayName: string;
  isValid: boolean;
}

/**
 * Request for updating a check payment configuration.
 */
export interface UpdateCheckPaymentConfigRequest {
  configValue?: string;
  description?: string;
  isActive?: boolean;
  updatedBy?: string;
}

/**
 * Request for creating a new check payment configuration.
 */
export interface CreateCheckPaymentConfigRequest {
  configKey: string;
  configValue: string;
  description?: string;
  valueType: 'STRING' | 'INTEGER' | 'BOOLEAN' | 'EMAIL';
  isActive?: boolean;
  createdBy?: string;
}

/**
 * Service for check payment configuration operations.
 */
export const checkPaymentConfigService = {
  /**
   * Gets all check payment configurations.
   */
  getAllConfigs: async (): Promise<CheckPaymentConfig[]> => {
    const response = await apiClient.get('/check-payment-config');
    return response.data;
  },

  /**
   * Gets only active check payment configurations.
   */
  getActiveConfigs: async (): Promise<CheckPaymentConfig[]> => {
    const response = await apiClient.get('/check-payment-config/active');
    return response.data;
  },

  /**
   * Gets a specific configuration by ID.
   */
  getConfigById: async (id: string): Promise<CheckPaymentConfig> => {
    const response = await apiClient.get(`/check-payment-config/${id}`);
    return response.data;
  },

  /**
   * Gets a specific configuration by key.
   */
  getConfigByKey: async (configKey: string): Promise<CheckPaymentConfig> => {
    const response = await apiClient.get(`/check-payment-config/key/${configKey}`);
    return response.data;
  },

  /**
   * Updates a configuration by ID.
   */
  updateConfig: async (
    id: string,
    request: UpdateCheckPaymentConfigRequest
  ): Promise<CheckPaymentConfig> => {
    const response = await apiClient.put(`/check-payment-config/${id}`, request);
    return response.data;
  },

  /**
   * Updates a configuration by key.
   */
  updateConfigByKey: async (
    configKey: string,
    request: UpdateCheckPaymentConfigRequest
  ): Promise<CheckPaymentConfig> => {
    const response = await apiClient.put(`/check-payment-config/key/${configKey}`, request);
    return response.data;
  },

  /**
   * Creates a new configuration.
   */
  createConfig: async (
    request: CreateCheckPaymentConfigRequest
  ): Promise<CheckPaymentConfig> => {
    const response = await apiClient.post('/check-payment-config', request);
    return response.data;
  },

  /**
   * Toggles the active state of a configuration.
   */
  toggleActive: async (id: string): Promise<CheckPaymentConfig> => {
    const response = await apiClient.post(`/check-payment-config/${id}/toggle-active`);
    return response.data;
  },

  /**
   * Gets configurations by value type.
   */
  getConfigsByType: async (
    valueType: 'STRING' | 'INTEGER' | 'BOOLEAN' | 'EMAIL'
  ): Promise<CheckPaymentConfig[]> => {
    const response = await apiClient.get(`/check-payment-config/type/${valueType}`);
    return response.data;
  },
};

export default checkPaymentConfigService;
