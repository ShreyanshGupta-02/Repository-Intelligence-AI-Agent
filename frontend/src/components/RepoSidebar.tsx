import React, { useState } from 'react';
import type { RepositoryResponse } from '../types/repository';
import type { ChatSessionResponseDto } from '../types/chat';
import { repositoryService } from '../services/repositoryService';
import { useAuth } from '../context/AuthContext';
import { StatusBadge } from './StatusBadge';
import { ConfirmDialog } from './ConfirmDialog';
import { Plus, Trash2, GitPullRequest, Search, RefreshCw, FolderGit2, AlertCircle, MessageSquare, LogIn, Calendar, MessageSquarePlus } from 'lucide-react';

interface RepoSidebarProps {
  repositories: RepositoryResponse[];
  selectedRepoId: string | null;
  onSelectRepo: (repo: RepositoryResponse) => void;
  onRepoCreated: (repo: RepositoryResponse) => void;
  onRepoDeleted: (repoId: string) => void;
  onOpenSearchModal?: () => void;
  onOpenAuthModal?: () => void;
  chatSessions?: ChatSessionResponseDto[];
  activeSessionId?: string | null;
  onSelectSession?: (sessionId: string) => void;
  onDeleteSession?: (sessionId: string) => void;
  onNewChat?: () => void;
}

export const RepoSidebar: React.FC<RepoSidebarProps> = ({
  repositories,
  selectedRepoId,
  onSelectRepo,
  onRepoCreated,
  onRepoDeleted,
  onOpenAuthModal,
  chatSessions = [],
  activeSessionId = null,
  onSelectSession,
  onDeleteSession,
  onNewChat,
}) => {
  const { isAuthenticated } = useAuth();
  const [activeTab, setActiveTab] = useState<'repos' | 'history'>('repos');
  const [urlInput, setUrlInput] = useState('');
  const [isCloning, setIsCloning] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [filterText, setFilterText] = useState('');

  // Confirmation dialog states
  const [repoToDelete, setRepoToDelete] = useState<RepositoryResponse | null>(null);
  const [sessionToDelete, setSessionToDelete] = useState<ChatSessionResponseDto | null>(null);
  const [isDeletingRepo, setIsDeletingRepo] = useState(false);

  // Form submit handler to clone a new repository
  const handleCloneRepo = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!isAuthenticated) {
      if (onOpenAuthModal) onOpenAuthModal();
      return;
    }

    if (!urlInput.trim()) return;

    const trimmedUrl = urlInput.trim();

    // Validate basic GitHub URL structure
    if (!trimmedUrl.includes('github.com/')) {
      setErrorMsg('Please enter a valid GitHub repository URL (e.g. https://github.com/owner/repo)');
      return;
    }

    setIsCloning(true);
    setErrorMsg(null);

    try {
      const newRepo = await repositoryService.createRepository({ githubUrl: trimmedUrl });
      setUrlInput('');
      onRepoCreated(newRepo);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to clone repository');
    } finally {
      setIsCloning(false);
    }
  };

  const handleOpenDeleteRepoDialog = (e: React.MouseEvent, repo: RepositoryResponse) => {
    e.stopPropagation();
    setRepoToDelete(repo);
  };

  const handleConfirmDeleteRepo = async () => {
    if (!repoToDelete) return;
    setIsDeletingRepo(true);
    try {
      await repositoryService.deleteRepository(repoToDelete.id);
      onRepoDeleted(repoToDelete.id);
      setRepoToDelete(null);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to delete repository');
      setRepoToDelete(null);
    } finally {
      setIsDeletingRepo(false);
    }
  };

  const handleOpenDeleteSessionDialog = (e: React.MouseEvent, session: ChatSessionResponseDto) => {
    e.stopPropagation();
    setSessionToDelete(session);
  };

  const handleConfirmDeleteSession = () => {
    if (!sessionToDelete || !onDeleteSession) return;
    onDeleteSession(sessionToDelete.id);
    setSessionToDelete(null);
  };

  const filteredRepos = repositories.filter(r =>
    r.name.toLowerCase().includes(filterText.toLowerCase()) ||
    r.githubUrl.toLowerCase().includes(filterText.toLowerCase())
  );

  // Group chat sessions date-wise (Today, Yesterday, Previous 7 Days, Older)
  const groupSessionsByDate = (sessions: ChatSessionResponseDto[]) => {
    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const yesterdayStart = todayStart - 86400000;
    const sevenDaysStart = todayStart - 6 * 86400000;

    const groups: { label: string; items: ChatSessionResponseDto[] }[] = [
      { label: 'Today', items: [] },
      { label: 'Yesterday', items: [] },
      { label: 'Previous 7 Days', items: [] },
      { label: 'Older', items: [] },
    ];

    sessions.forEach(session => {
      const sessionTime = new Date(session.createdAt).getTime();
      if (sessionTime >= todayStart) {
        groups[0].items.push(session);
      } else if (sessionTime >= yesterdayStart) {
        groups[1].items.push(session);
      } else if (sessionTime >= sevenDaysStart) {
        groups[2].items.push(session);
      } else {
        groups[3].items.push(session);
      }
    });

    return groups.filter(g => g.items.length > 0);
  };

  const sessionGroups = groupSessionsByDate(chatSessions);

  return (
    <aside className="w-80 border-r border-slate-800/80 bg-slate-950/60 backdrop-blur-md flex flex-col h-[calc(100vh-4rem)]">
      {/* Top Section Tab Switcher */}
      <div className="flex border-b border-slate-800/80 bg-slate-900/40">
        <button
          onClick={() => setActiveTab('repos')}
          className={`flex-1 py-3 text-xs font-semibold flex items-center justify-center gap-2 border-b-2 transition-all ${
            activeTab === 'repos'
              ? 'border-indigo-500 text-indigo-300 bg-indigo-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <FolderGit2 className="w-3.5 h-3.5" />
          <span>Repositories ({repositories.length})</span>
        </button>
        <button
          onClick={() => setActiveTab('history')}
          className={`flex-1 py-3 text-xs font-semibold flex items-center justify-center gap-2 border-b-2 transition-all ${
            activeTab === 'history'
              ? 'border-indigo-500 text-indigo-300 bg-indigo-500/5'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <MessageSquare className="w-3.5 h-3.5" />
          <span>Chat History ({chatSessions.length})</span>
        </button>
      </div>

      {activeTab === 'repos' ? (
        <>
          {/* Top Header & URL Clone Form / Unauthenticated Card */}
          {!isAuthenticated ? (
            <div className="p-4 border-b border-slate-800/80">
              <div
                onClick={onOpenAuthModal}
                className="group relative overflow-hidden rounded-2xl bg-gradient-to-b from-indigo-950/50 via-slate-900/80 to-slate-950/90 border border-indigo-500/30 p-4 transition-all duration-300 hover:border-indigo-500/50 hover:shadow-lg hover:shadow-indigo-500/10 cursor-pointer"
              >
                <div className="absolute -top-10 -right-10 h-28 w-28 rounded-full bg-indigo-500/10 blur-2xl group-hover:bg-indigo-500/20 transition-all"></div>
                
                <div className="flex items-center space-x-3 mb-3">
                  <div className="h-9 w-9 rounded-xl bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 group-hover:scale-105 transition-transform">
                    <LogIn className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-xs text-slate-100 group-hover:text-indigo-300 transition-colors">
                      Sign In Required
                    </h3>
                    <p className="text-[11px] text-slate-400">Access RAG agent features</p>
                  </div>
                </div>

                <p className="text-[11px] text-slate-400 leading-relaxed mb-4">
                  Sign in to clone GitHub repositories, analyze code architecture, and save your chat history.
                </p>

                <button
                  onClick={onOpenAuthModal}
                  className="w-full py-2.5 px-3 rounded-xl bg-gradient-to-r from-indigo-600 via-indigo-500 to-cyan-500 hover:from-indigo-500 hover:to-cyan-400 text-white text-xs font-semibold shadow-lg shadow-indigo-500/25 flex items-center justify-center space-x-2 transition-all cursor-pointer"
                >
                  <LogIn className="w-4 h-4" />
                  <span>Sign In / Register</span>
                </button>
              </div>
            </div>
          ) : (
            <div className="p-4 border-b border-slate-800/80 space-y-3">
              {/* Clone Form */}
              <form onSubmit={handleCloneRepo} className="space-y-2">
                <div className="relative">
                  <input
                    type="url"
                    placeholder="https://github.com/owner/repo"
                    value={urlInput}
                    onChange={(e) => setUrlInput(e.target.value)}
                    className="w-full px-3 py-2 pl-9 bg-slate-900/90 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 disabled:opacity-50 transition-all"
                    disabled={isCloning}
                  />
                  <GitPullRequest className="w-4 h-4 text-slate-500 absolute left-2.5 top-2.5" />
                </div>

                <button
                  type="submit"
                  disabled={isCloning || !urlInput.trim()}
                  className="w-full py-2 px-3 rounded-lg bg-gradient-to-r from-indigo-600 to-indigo-500 hover:from-indigo-500 hover:to-indigo-400 text-white text-xs font-semibold shadow-md shadow-indigo-500/20 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center space-x-1.5 transition-all"
                >
                  {isCloning ? (
                    <>
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      <span>Initiating Clone...</span>
                    </>
                  ) : (
                    <>
                      <Plus className="w-3.5 h-3.5" />
                      <span>Clone & Index Repo</span>
                    </>
                  )}
                </button>
              </form>

              {errorMsg && (
                <div className="p-2 rounded bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs flex items-start gap-1.5">
                  <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                  <span>{errorMsg}</span>
                </div>
              )}
            </div>
          )}

          {/* Filter / Search Box */}
          {repositories.length > 0 && (
            <div className="px-4 py-2 border-b border-slate-800/40">
              <div className="relative">
                <input
                  type="text"
                  placeholder="Filter repositories..."
                  value={filterText}
                  onChange={(e) => setFilterText(e.target.value)}
                  className="w-full px-3 py-1.5 pl-8 bg-slate-900/60 border border-slate-800/80 rounded-md text-xs text-slate-300 placeholder-slate-500 focus:outline-none focus:border-slate-700"
                />
                <Search className="w-3.5 h-3.5 text-slate-500 absolute left-2.5 top-2" />
              </div>
            </div>
          )}

          {/* Repositories List */}
          <div className="flex-1 overflow-y-auto p-3 space-y-2">
            {repositories.length === 0 ? (
              <div className="text-center py-10 px-4 space-y-3">
                <div className="w-12 h-12 rounded-full bg-slate-900 border border-slate-800 flex items-center justify-center mx-auto text-slate-500">
                  <FolderGit2 className="w-6 h-6" />
                </div>
                <p className="text-xs text-slate-400 font-medium">No repositories indexed yet</p>
                <p className="text-[11px] text-slate-500">
                  Paste a public GitHub URL above to clone and index your repository.
                </p>
              </div>
            ) : filteredRepos.length === 0 ? (
              <p className="text-center py-6 text-xs text-slate-500">No matching repositories found</p>
            ) : (
              filteredRepos.map((repo) => {
                const isSelected = repo.id === selectedRepoId;

                return (
                  <div
                    key={repo.id}
                    onClick={() => onSelectRepo(repo)}
                    className={`group p-3 rounded-xl border transition-all duration-200 cursor-pointer ${
                      isSelected
                        ? 'bg-indigo-950/40 border-indigo-500/50 shadow-lg shadow-indigo-500/10'
                        : 'bg-slate-900/60 border-slate-800/80 hover:bg-slate-850 hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <h3 className="font-semibold text-xs text-slate-200 truncate group-hover:text-indigo-300 transition-colors">
                          {repo.name}
                        </h3>
                        <p className="text-[10px] text-slate-500 truncate mt-0.5 font-mono">{repo.githubUrl}</p>
                      </div>

                      <button
                        onClick={(e) => handleOpenDeleteRepoDialog(e, repo)}
                        className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-rose-500/20 text-slate-500 hover:text-rose-400 transition-all"
                        title="Delete repository"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>

                    <div className="mt-2.5 flex items-center justify-between text-[10px] text-slate-400">
                      <StatusBadge status={repo.status} errorMessage={repo.errorMessage} />
                      <span className="text-slate-500">
                        {new Date(repo.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </>
      ) : (
        /* Chat History Tab Content */
        <div className="flex-1 flex flex-col h-full overflow-hidden">
          {/* New Chat Button */}
          <div className="p-4 border-b border-slate-800/80">
            <button
              onClick={onNewChat}
              className="w-full py-2 px-3 rounded-lg bg-indigo-600/20 hover:bg-indigo-600/30 border border-indigo-500/30 text-indigo-300 text-xs font-semibold flex items-center justify-center gap-2 transition-all shadow-sm"
            >
              <MessageSquarePlus className="w-4 h-4 text-indigo-400" />
              <span>+ New Chat Session</span>
            </button>
          </div>

          {/* Date-Categorized Chat Sessions List */}
          <div className="flex-1 overflow-y-auto p-3 space-y-4">
            {!selectedRepoId ? (
              <div className="text-center py-10 px-4 space-y-2">
                <MessageSquare className="w-8 h-8 text-slate-600 mx-auto" />
                <p className="text-xs text-slate-400">Select a repository to view its chat history</p>
              </div>
            ) : chatSessions.length === 0 ? (
              <div className="text-center py-10 px-4 space-y-2">
                <MessageSquare className="w-8 h-8 text-slate-600 mx-auto" />
                <p className="text-xs text-slate-400">No chat history for this repository yet</p>
                <p className="text-[11px] text-slate-500">Ask a question to start a new persistent session!</p>
              </div>
            ) : (
              sessionGroups.map(group => (
                <div key={group.label} className="space-y-1.5">
                  <div className="px-2 flex items-center gap-1.5 text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                    <Calendar className="w-3 h-3 text-indigo-400" />
                    <span>{group.label}</span>
                  </div>

                  <div className="space-y-1">
                    {group.items.map(session => {
                      const isSelected = session.id === activeSessionId;
                      return (
                        <div
                          key={session.id}
                          onClick={() => onSelectSession && onSelectSession(session.id)}
                          className={`group p-2.5 rounded-lg border transition-all cursor-pointer flex items-center justify-between gap-2 ${
                            isSelected
                              ? 'bg-indigo-950/50 border-indigo-500/50 text-indigo-200 shadow-sm'
                              : 'bg-slate-900/50 border-slate-800/60 text-slate-300 hover:bg-slate-850 hover:border-slate-700'
                          }`}
                        >
                          <div className="flex items-center space-x-2 min-w-0 flex-1">
                            <MessageSquare className={`w-3.5 h-3.5 flex-shrink-0 ${isSelected ? 'text-indigo-400' : 'text-slate-500'}`} />
                            <span className="text-xs truncate font-medium">{session.title}</span>
                          </div>

                          {onDeleteSession && (
                            <button
                              onClick={(e) => handleOpenDeleteSessionDialog(e, session)}
                              className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-rose-500/20 text-slate-500 hover:text-rose-400 transition-all"
                              title="Delete conversation"
                            >
                              <Trash2 className="w-3 h-3" />
                            </button>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* Delete Repository Confirmation Dialog */}
      <ConfirmDialog
        isOpen={!!repoToDelete}
        onClose={() => setRepoToDelete(null)}
        onConfirm={handleConfirmDeleteRepo}
        title="Delete Repository"
        message={`Are you sure you want to delete '${repoToDelete?.name}' from your indexed repositories? This action will remove all vector embeddings and indexed code chunks.`}
        confirmText="Delete Repository"
        variant="danger"
        isLoading={isDeletingRepo}
      />

      {/* Delete Chat Session Confirmation Dialog */}
      <ConfirmDialog
        isOpen={!!sessionToDelete}
        onClose={() => setSessionToDelete(null)}
        onConfirm={handleConfirmDeleteSession}
        title="Delete Chat Conversation"
        message={`Are you sure you want to delete '${sessionToDelete?.title}'? All chat messages in this session will be permanently deleted.`}
        confirmText="Delete Conversation"
        variant="danger"
      />
    </aside>
  );
};
