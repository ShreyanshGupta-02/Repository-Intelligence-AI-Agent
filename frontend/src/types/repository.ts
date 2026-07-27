export type RepositoryStatus = 'PENDING' | 'CLONING' | 'PARSING' | 'EMBEDDING' | 'INDEXED' | 'FAILED';

export interface RepositoryResponse {
  id: string;
  githubUrl: string;
  name: string;
  status: RepositoryStatus;
  errorMessage?: string | null;
  architectureSummary?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ArchitectureSummaryDto {
  repositoryId: string;
  repositoryName: string;
  summary: string;
  isCached: boolean;
}

export interface CreateRepositoryRequest {
  githubUrl: string;
}
