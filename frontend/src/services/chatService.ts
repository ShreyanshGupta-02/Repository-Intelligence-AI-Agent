import { api } from './api';
import type { ChatRequestDto, ChatResponseDto, ChatSessionResponseDto, ChatMessageDto } from '../types/chat';

export const chatService = {
  /**
   * Send a question to RAG Chat and receive context-grounded AI answer
   */
  async sendMessage(repositoryId: string, data: ChatRequestDto): Promise<ChatResponseDto> {
    const res = await api.post<ChatResponseDto>(`/repositories/${repositoryId}/chat`, data);
    return res.data;
  },

  /**
   * Get all chat sessions for a repository
   */
  async getSessions(repositoryId: string): Promise<ChatSessionResponseDto[]> {
    const res = await api.get<ChatSessionResponseDto[]>(`/repositories/${repositoryId}/chat/sessions`);
    return res.data;
  },

  /**
   * Get all messages for a specific chat session
   */
  async getSessionMessages(repositoryId: string, sessionId: string): Promise<ChatMessageDto[]> {
    const res = await api.get<ChatMessageDto[]>(`/repositories/${repositoryId}/chat/sessions/${sessionId}/messages`);
    return res.data;
  },

  /**
   * Delete a chat session
   */
  async deleteSession(repositoryId: string, sessionId: string): Promise<void> {
    await api.delete(`/repositories/${repositoryId}/chat/sessions/${sessionId}`);
  },
};
