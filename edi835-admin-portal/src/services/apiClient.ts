import axios, { AxiosInstance, AxiosError } from 'axios';
import { toast } from 'react-toastify';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

// Create axios instance
const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor
apiClient.interceptors.request.use(
  (config) => {
    // Add auth token if available
    const token = localStorage.getItem('auth_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response) {
      // Server responded with error status
      const status = error.response.status;
      const data: any = error.response.data;

      switch (status) {
        case 400:
          toast.error(data.error || 'Bad request');
          break;
        case 401:
          toast.error('Unauthorized. Please log in again.');
          // Handle logout/redirect
          break;
        case 403:
          toast.error('Access forbidden');
          break;
        case 404:
          toast.error(data.error || 'Resource not found');
          break;
        case 500:
          toast.error(data.error || 'Internal server error');
          break;
        default:
          toast.error(`Error: ${status} - ${data.error || 'Unknown error'}`);
      }
    } else if (error.request) {
      // Request made but no response
      toast.error('No response from server. Please check your connection.');
    } else {
      // Error in request setup
      toast.error(`Request error: ${error.message}`);
    }

    return Promise.reject(error);
  }
);

export default apiClient;
