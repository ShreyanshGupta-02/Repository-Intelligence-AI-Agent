export type { UserDto } from './api';

export interface SearchResultDto {
  chunkId: string;
  filePath: string;
  fileName: string;
  language: string;
  startLine: number;
  endLine: number;
  content: string;
  score?: number | null;
}

export interface ChatRequestDto {
  question: string;
  sessionId?: string | null;
  topK?: number;
}

export interface ChatResponseDto {
  sessionId: string;
  question: string;
  answer: string;
  sources: SearchResultDto[];
}

export interface ChatMessageItem {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: SearchResultDto[];
  timestamp: Date;
}

export interface ChatSessionResponseDto {
  id: string;
  title: string;
  repositoryId: string;
  createdAt: string;
}

export interface ChatMessageDto {
  id: string;
  role: 'USER' | 'ASSISTANT' | 'user' | 'assistant';
  content: string;
  createdAt: string;
}
