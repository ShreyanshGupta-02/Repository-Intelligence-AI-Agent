import { api } from './api';
import type { RepositoryResponse, CreateRepositoryRequest, ArchitectureSummaryDto } from '../types/repository';
import type { SearchResultDto } from '../types/chat';

export const repositoryService = {
  /**
   * Register and clone a new GitHub repository
   */
  async createRepository(data: CreateRepositoryRequest): Promise<RepositoryResponse> {
    const res = await api.post<RepositoryResponse>('/repositories', data);
    return res.data;
  },

  /**
   * Get list of all repositories
   */
  async getAllRepositories(): Promise<RepositoryResponse[]> {
    const res = await api.get<RepositoryResponse[]>('/repositories');
    return res.data;
  },

  /**
   * Get a single repository by ID (used for status polling)
   */
  async getRepositoryById(id: string): Promise<RepositoryResponse> {
    const res = await api.get<RepositoryResponse>(`/repositories/${id}`);
    return res.data;
  },

  /**
   * Delete a repository by ID
   */
  async deleteRepository(id: string): Promise<void> {
    await api.delete(`/repositories/${id}`);
  },

  /**
   * Semantic vector search on code chunks
   */
  async search(repositoryId: string, query: string, topK: number = 5): Promise<SearchResultDto[]> {
    const res = await api.get<SearchResultDto[]>(`/repositories/${repositoryId}/search`, {
      params: { query, topK },
    });
    return res.data;
  },

  /**
   * Get or generate Architecture Summary / README for a repository
   */
  async getArchitectureSummary(repositoryId: string, forceRegenerate: boolean = false): Promise<ArchitectureSummaryDto> {
    const res = await api.get<ArchitectureSummaryDto>(`/repositories/${repositoryId}/summary`, {
      params: { forceRegenerate },
    });
    return res.data;
  },

  /**
   * Health check endpoint
   */
  async checkHealth(): Promise<boolean> {
    try {
      const res = await api.get('/health');
      return res.status === 200;
    } catch {
      return false;
    }
  },
};
