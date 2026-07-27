import React, { useState, useEffect } from 'react';
import { Header } from '../components/Header';
import { RepoSidebar } from '../components/RepoSidebar';
import { ChatMessageList } from '../components/ChatMessageList';
import { SemanticSearchModal } from '../components/SemanticSearchModal';
import { ArchitectureSummaryModal } from '../components/ArchitectureSummaryModal';
import { AuthModal } from '../components/AuthModal';
import type { RepositoryResponse } from '../types/repository';
import type { ChatMessageItem, ChatSessionResponseDto } from '../types/chat';
import { repositoryService } from '../services/repositoryService';
import { chatService } from '../services/chatService';
import { useAuth } from '../context/AuthContext';
import { Send, AlertCircle, RefreshCw, FolderGit2, Bot, BookOpen, MessageSquarePlus, LogIn } from 'lucide-react';

export const ChatPage: React.FC = () => {
  const { user, isAuthenticated } = useAuth();
  const [repositories, setRepositories] = useState<RepositoryResponse[]>([]);
  const [selectedRepo, setSelectedRepo] = useState<RepositoryResponse | null>(null);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [inputQuestion, setInputQuestion] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Chat Sessions state
  const [chatSessions, setChatSessions] = useState<ChatSessionResponseDto[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);

  // Auth & Search & Summary Modal States
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  const [isSearchModalOpen, setIsSearchModalOpen] = useState(false);
  const [isSummaryModalOpen, setIsSummaryModalOpen] = useState(false);

  // 1. Reactive Load: Fetch repositories when auth state changes (login/user switch), reset state on logout
  useEffect(() => {
    if (isAuthenticated && user) {
      fetchRepositories();
    } else {
      setRepositories([]);
      setSelectedRepo(null);
      setMessages([]);
      setChatSessions([]);
      setActiveSessionId(null);
    }
  }, [isAuthenticated, user]);

  // Load chat sessions when selected repo changes
  useEffect(() => {
    if (selectedRepo && isAuthenticated) {
      fetchChatSessions(selectedRepo.id);
    } else {
      setChatSessions([]);
    }
  }, [selectedRepo, isAuthenticated]);

  const fetchRepositories = async () => {
    try {
      const data = await repositoryService.getAllRepositories();
      setRepositories(data);

      if (data.length > 0) {
        if (!selectedRepo || !data.some(r => r.id === selectedRepo.id)) {
          setSelectedRepo(data[0]);
        }
      } else {
        setSelectedRepo(null);
      }
    } catch (err: any) {
      console.error('Failed to load repositories', err);
      setRepositories([]);
      setSelectedRepo(null);
    }
  };

  const fetchChatSessions = async (repoId: string) => {
    try {
      const sessions = await chatService.getSessions(repoId);
      setChatSessions(sessions);
    } catch (err) {
      console.error('Failed to load chat sessions', err);
      setChatSessions([]);
    }
  };

  // Background Polling for status transitions
  useEffect(() => {
    const hasUnfinishedRepo = repositories.some(
      r => r.status === 'PENDING' || r.status === 'CLONING' || r.status === 'PARSING' || r.status === 'EMBEDDING'
    );

    if (!hasUnfinishedRepo) return;

    const interval = setInterval(async () => {
      try {
        const updatedRepos = await repositoryService.getAllRepositories();
        setRepositories(updatedRepos);

        if (selectedRepo) {
          const matching = updatedRepos.find(r => r.id === selectedRepo.id);
          if (matching) setSelectedRepo(matching);
        }
      } catch (err) {
        console.error('Status polling error', err);
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [repositories, selectedRepo]);

  // Handle Repository Selection
  const handleSelectRepo = (repo: RepositoryResponse) => {
    setSelectedRepo(repo);
    setMessages([]);
    setActiveSessionId(null);
    setError(null);
  };

  // Handle Repository Created
  const handleRepoCreated = (newRepo: RepositoryResponse) => {
    setRepositories(prev => [newRepo, ...prev]);
    setSelectedRepo(newRepo);
    setMessages([]);
    setActiveSessionId(null);
  };

  // Handle Repository Deleted
  const handleRepoDeleted = (deletedId: string) => {
    setRepositories(prev => prev.filter(r => r.id !== deletedId));
    if (selectedRepo?.id === deletedId) {
      const remaining = repositories.filter(r => r.id !== deletedId);
      setSelectedRepo(remaining.length > 0 ? remaining[0] : null);
      setMessages([]);
      setActiveSessionId(null);
    }
  };

  // Select a Chat Session from history
  const handleSelectSession = async (sessionId: string) => {
    if (!selectedRepo) return;
    setActiveSessionId(sessionId);
    setIsLoading(true);
    setError(null);
    try {
      const history = await chatService.getSessionMessages(selectedRepo.id, sessionId);
      const formattedMsgs: ChatMessageItem[] = history.map(msg => ({
        id: msg.id,
        role: msg.role.toString().toLowerCase() === 'user' ? 'user' : 'assistant',
        content: msg.content,
        timestamp: new Date(msg.createdAt),
      }));
      setMessages(formattedMsgs);
    } catch (err: any) {
      setError(err.message || 'Failed to load conversation history');
    } finally {
      setIsLoading(false);
    }
  };

  // Delete a Chat Session
  const handleDeleteSession = async (sessionId: string) => {
    if (!selectedRepo) return;
    try {
      await chatService.deleteSession(selectedRepo.id, sessionId);
      setChatSessions(prev => prev.filter(s => s.id !== sessionId));
      if (activeSessionId === sessionId) {
        handleNewChat();
      }
    } catch (err: any) {
      setError(err.message || 'Failed to delete chat session');
    }
  };

  // Start New Chat
  const handleNewChat = () => {
    setActiveSessionId(null);
    setMessages([]);
    setError(null);
  };

  // Handle Send Chat Question
  const handleSendQuestion = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();

    if (!isAuthenticated) {
      setIsAuthModalOpen(true);
      return;
    }

    if (!inputQuestion.trim() || !selectedRepo || isLoading) return;

    if (selectedRepo.status !== 'INDEXED') {
      setError(`Repository is currently in status '${selectedRepo.status}'. Please wait until indexing completes.`);
      return;
    }

    const questionText = inputQuestion.trim();
    setInputQuestion('');
    setError(null);

    const userMsg: ChatMessageItem = {
      id: crypto.randomUUID(),
      role: 'user',
      content: questionText,
      timestamp: new Date(),
    };

    setMessages(prev => [...prev, userMsg]);
    setIsLoading(true);

    try {
      const response = await chatService.sendMessage(selectedRepo.id, {
        question: questionText,
        sessionId: activeSessionId,
        topK: 5,
      });

      if (response.sessionId) {
        setActiveSessionId(response.sessionId);
        fetchChatSessions(selectedRepo.id);
      }

      const assistantMsg: ChatMessageItem = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.answer,
        sources: response.sources,
        timestamp: new Date(),
      };

      setMessages(prev => [...prev, assistantMsg]);
    } catch (err: any) {
      setError(err.message || 'Failed to generate response');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#0b0f19] text-slate-100 flex flex-col font-sans">
      {/* Top Navbar */}
      <Header onOpenAuthModal={() => setIsAuthModalOpen(true)} />

      <div className="flex-1 flex overflow-hidden">
        {/* Left Repository Sidebar */}
        <RepoSidebar
          repositories={repositories}
          selectedRepoId={selectedRepo?.id || null}
          onSelectRepo={handleSelectRepo}
          onRepoCreated={handleRepoCreated}
          onRepoDeleted={handleRepoDeleted}
          onOpenSearchModal={() => setIsSearchModalOpen(true)}
          onOpenAuthModal={() => setIsAuthModalOpen(true)}
          chatSessions={chatSessions}
          activeSessionId={activeSessionId}
          onSelectSession={handleSelectSession}
          onDeleteSession={handleDeleteSession}
          onNewChat={handleNewChat}
        />

        {/* Main Content / Chat Workspace */}
        <main className="flex-1 flex flex-col h-[calc(100vh-4rem)] bg-slate-950/40 relative">
          {/* Active Repo Top Bar */}
          {selectedRepo && (
            <div className="h-12 px-6 border-b border-slate-800/80 bg-slate-900/40 backdrop-blur-sm flex items-center justify-between z-10">
              <div className="flex items-center space-x-3 min-w-0">
                <FolderGit2 className="w-4 h-4 text-indigo-400 flex-shrink-0" />
                <span className="font-semibold text-xs text-slate-200 truncate">{selectedRepo.name}</span>
                <span className="text-[10px] text-slate-500 font-mono hidden sm:inline">{selectedRepo.githubUrl}</span>
              </div>

              <div className="flex items-center space-x-3 text-xs">
                <button
                  onClick={handleNewChat}
                  className="px-3 py-1.5 rounded-lg bg-indigo-600/20 hover:bg-indigo-600/30 text-indigo-300 border border-indigo-500/30 font-medium flex items-center gap-1.5 transition-all shadow-sm"
                  title="Start a fresh chat conversation"
                >
                  <MessageSquarePlus className="w-3.5 h-3.5 text-indigo-400" />
                  <span>+ New Chat</span>
                </button>

                {selectedRepo.status === 'INDEXED' && (
                  <button
                    onClick={() => setIsSummaryModalOpen(true)}
                    className="px-3 py-1.5 rounded-lg bg-gradient-to-r from-indigo-600/30 to-purple-600/30 hover:from-indigo-600/40 hover:to-purple-600/40 text-indigo-300 border border-indigo-500/30 font-medium flex items-center gap-1.5 transition-all shadow-sm"
                  >
                    <BookOpen className="w-3.5 h-3.5 text-indigo-400" />
                    <span>Architecture Overview</span>
                  </button>
                )}

                {selectedRepo.status !== 'INDEXED' && (
                  <span className="text-amber-400 text-[11px] animate-pulse">
                    Indexing pipeline active ({selectedRepo.status})...
                  </span>
                )}
              </div>
            </div>
          )}

          {/* Chat Messages Area */}
          {!isAuthenticated ? (
            <div className="flex-1 flex flex-col items-center justify-center text-center p-6 space-y-5">
              <div className="relative">
                <div className="w-20 h-20 rounded-3xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-cyan-400 p-[1px] shadow-2xl shadow-indigo-500/30">
                  <div className="w-full h-full bg-slate-950 rounded-[23px] flex items-center justify-center">
                    <Bot className="w-10 h-10 text-cyan-400 animate-pulse" />
                  </div>
                </div>
                <div className="absolute -inset-2 bg-indigo-500/20 rounded-full blur-xl -z-10"></div>
              </div>

              <div className="space-y-2 max-w-md">
                <h3 className="text-xl font-bold bg-gradient-to-r from-white via-slate-200 to-indigo-200 bg-clip-text text-transparent">
                  Repository Intelligence AI
                </h3>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Sign in to clone GitHub repositories, perform RAG Q&A with Groq LLM, generate architecture summaries, and store chat history.
                </p>
              </div>

              <button
                onClick={() => setIsAuthModalOpen(true)}
                className="px-6 py-3 rounded-xl bg-gradient-to-r from-indigo-600 via-indigo-500 to-cyan-500 hover:from-indigo-500 hover:to-cyan-400 text-white text-xs font-semibold shadow-lg shadow-indigo-500/25 flex items-center gap-2 transition-all cursor-pointer"
              >
                <LogIn className="w-4 h-4" />
                <span>Sign In to Get Started</span>
              </button>
            </div>
          ) : !selectedRepo ? (
            <div className="flex-1 flex flex-col items-center justify-center text-center p-6 space-y-4">
              <div className="w-16 h-16 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-slate-500">
                <Bot className="w-8 h-8" />
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-200">No Repository Selected</h3>
                <p className="text-xs text-slate-400 mt-1 max-w-sm">
                  Select a repository from the left sidebar or paste a public GitHub URL to begin RAG chat.
                </p>
              </div>
            </div>
          ) : (
            <>
              <ChatMessageList
                messages={messages}
                isLoading={isLoading}
                activeRepoName={selectedRepo.name}
                onSelectExampleQuestion={(questionText) => setInputQuestion(questionText)}
              />

              {/* Error Toast */}
              {error && (
                <div className="px-6 py-2">
                  <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs flex items-center justify-between">
                    <div className="flex items-center space-x-2">
                      <AlertCircle className="w-4 h-4 flex-shrink-0" />
                      <span>{error}</span>
                    </div>
                    <button onClick={() => setError(null)} className="text-slate-400 hover:text-white text-xs">
                      Dismiss
                    </button>
                  </div>
                </div>
              )}

              {/* Message Input Form */}
              <div className="p-4 border-t border-slate-800/80 bg-slate-950/80 backdrop-blur-md">
                <form onSubmit={handleSendQuestion} className="max-w-4xl mx-auto flex items-center gap-3">
                  <div className="relative flex-1">
                    <input
                      type="text"
                      placeholder={
                        !isAuthenticated
                          ? "Please sign in to ask questions..."
                          : selectedRepo.status === 'INDEXED'
                          ? `Ask a question about ${selectedRepo.name}...`
                          : `Indexing repository (${selectedRepo.status})... please wait.`
                      }
                      value={inputQuestion}
                      onChange={(e) => setInputQuestion(e.target.value)}
                      onClick={() => {
                        if (!isAuthenticated) setIsAuthModalOpen(true);
                      }}
                      disabled={isLoading || (isAuthenticated && selectedRepo.status !== 'INDEXED')}
                      className="w-full px-4 py-3 bg-slate-900/90 border border-slate-800 rounded-xl text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 disabled:opacity-50 transition-all shadow-inner"
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={isLoading || (isAuthenticated && (!inputQuestion.trim() || selectedRepo.status !== 'INDEXED'))}
                    className="p-3 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-400 text-white shadow-lg shadow-indigo-500/20 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                  >
                    {isLoading ? (
                      <RefreshCw className="w-4 h-4 animate-spin" />
                    ) : (
                      <Send className="w-4 h-4" />
                    )}
                  </button>
                </form>
                <div className="text-center pt-2">
                  <p className="text-[10px] text-slate-500">
                    Repository AI • Intelligent Code Analysis & Contextual Retrieval
                  </p>
                </div>
              </div>
            </>
          )}
        </main>
      </div>

      {/* Auth Modal */}
      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={() => setIsAuthModalOpen(false)}
      />

      {/* Semantic Search Direct Tester Modal */}
      {selectedRepo && (
        <SemanticSearchModal
          isOpen={isSearchModalOpen}
          onClose={() => setIsSearchModalOpen(false)}
          repositoryId={selectedRepo.id}
          repoName={selectedRepo.name}
        />
      )}
      {/* Architecture Summary Modal */}
      {selectedRepo && (
        <ArchitectureSummaryModal
          isOpen={isSummaryModalOpen}
          onClose={() => setIsSummaryModalOpen(false)}
          repositoryId={selectedRepo.id}
          repoName={selectedRepo.name}
        />
      )}
    </div>
  );
};
