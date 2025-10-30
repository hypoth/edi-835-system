// src/services/api.ts

import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { toast } from 'react-toastify';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

// Create axios instance
const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - add auth headers from ecosystem
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Auth token will be provided by the ecosystem
    // Example: reading from header or cookie set by auth provider
    const token = getAuthToken();
    
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    
    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

// Response interceptor - handle errors globally
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response) {
      const status = error.response.status;
      const data = error.response.data as any;

      switch (status) {
        case 401:
          toast.error('Unauthorized. Please login again.');
          // Redirect to login handled by ecosystem
          window.location.href = '/login';
          break;
        case 403:
          toast.error('Access denied. Insufficient permissions.');
          break;
        case 404:
          toast.error('Resource not found.');
          break;
        case 500:
          toast.error('Server error. Please try again later.');
          break;
        default:
          toast.error(data?.message || 'An error occurred');
      }
    } else if (error.request) {
      toast.error('Network error. Please check your connection.');
    }
    
    return Promise.reject(error);
  }
);

// Helper function to get auth token from ecosystem
function getAuthToken(): string | null {
  // This will be provided by your ecosystem's auth mechanism
  // Example implementations:
  // 1. From cookie: document.cookie
  // 2. From sessionStorage: sessionStorage.getItem('token')
  // 3. From custom header injected by auth proxy
  return sessionStorage.getItem('auth_token');
}

export default api;