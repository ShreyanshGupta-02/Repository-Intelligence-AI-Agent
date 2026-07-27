import { api } from './api';
import type { HealthResponse } from '../types/api';

/**
 * Calls the backend health check endpoint.
 */
export const checkHealth = async (): Promise<HealthResponse> => {
  const response = await api.get<HealthResponse>('/health');
  return response.data;
};
