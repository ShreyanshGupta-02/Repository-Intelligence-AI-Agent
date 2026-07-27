import React, { useEffect, useState } from 'react';
import { Bot, GitBranch, ExternalLink, Activity, LogIn, LogOut } from 'lucide-react';
import { repositoryService } from '../services/repositoryService';
import { useAuth } from '../context/AuthContext';
import { AuthModal } from './AuthModal';

interface HeaderProps {
  onOpenAuthModal?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ onOpenAuthModal }) => {
  const [isHealthy, setIsHealthy] = useState<boolean | null>(null);
  const [internalAuthModalOpen, setInternalAuthModalOpen] = useState(false);
  const { user, isAuthenticated, logout } = useAuth();

  const handleOpenAuth = () => {
    if (onOpenAuthModal) {
      onOpenAuthModal();
    } else {
      setInternalAuthModalOpen(true);
    }
  };

  useEffect(() => {
    const check = async () => {
      const healthy = await repositoryService.checkHealth();
      setIsHealthy(healthy);
    };
    check();
    const interval = setInterval(check, 15000);
    return () => clearInterval(interval);
  }, []);

  return (
    <>
      <header className="h-16 border-b border-slate-800/80 bg-slate-950/80 backdrop-blur-md px-6 flex items-center justify-between sticky top-0 z-30">
        {/* Brand Identity */}
        <div className="flex items-center space-x-3">
          <div className="h-10 w-10 rounded-xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-cyan-400 p-[1px] shadow-lg shadow-indigo-500/20">
            <div className="h-full w-full bg-slate-950 rounded-[11px] flex items-center justify-center">
              <Bot className="w-5 h-5 text-cyan-400 animate-pulse" />
            </div>
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="font-bold text-base tracking-wide bg-gradient-to-r from-white via-slate-200 to-slate-400 bg-clip-text text-transparent">
                Repository Intelligence
              </h1>
              <span className="px-2 py-0.5 text-[10px] font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 rounded-full">
                RAG Agent
              </span>
            </div>
            <p className="text-xs text-slate-400 flex items-center gap-1.5">
              <span>Repository AI System</span>
              <span className="inline-block w-1 h-1 rounded-full bg-slate-600"></span>
              <span className="text-slate-500">Intelligent Code Analysis & RAG Agent</span>
            </p>
          </div>
        </div>

        {/* Right controls */}
        <div className="flex items-center space-x-3">
          {/* Backend health status pill */}
          <div className="flex items-center space-x-2 px-3 py-1.5 rounded-lg bg-slate-900/80 border border-slate-800 text-xs">
            <Activity className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-slate-400">Backend:</span>
            {isHealthy === null ? (
              <span className="text-slate-400 animate-pulse">Checking...</span>
            ) : isHealthy ? (
              <span className="flex items-center gap-1.5 text-emerald-400 font-medium">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping"></span>
                Connected
              </span>
            ) : (
              <span className="flex items-center gap-1.5 text-rose-400 font-medium">
                <span className="w-2 h-2 rounded-full bg-rose-500"></span>
                Offline
              </span>
            )}
          </div>

          {/* Portfolio / GitHub button (placed to the left of user auth) */}
          <a
            href="https://github.com"
            target="_blank"
            rel="noreferrer"
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium transition-colors border border-slate-700"
          >
            <GitBranch className="w-4 h-4" />
            <span className="hidden sm:inline">GitHub</span>
            <ExternalLink className="w-3 h-3 text-slate-400" />
          </a>

          {/* User Auth Controls (on far right) */}
          {isAuthenticated && user ? (
            <div className="flex items-center space-x-2 bg-slate-900/80 border border-slate-800 rounded-lg p-1">
              <div className="flex items-center space-x-2 px-2.5 py-1 text-xs">
                <div className="w-5 h-5 rounded-full bg-indigo-600 text-white flex items-center justify-center text-[10px] font-bold">
                  {user.username.charAt(0).toUpperCase()}
                </div>
                <span className="font-semibold text-slate-200">{user.username}</span>
              </div>
              <button
                onClick={logout}
                className="p-1.5 rounded hover:bg-slate-800 text-slate-400 hover:text-rose-400 transition-colors"
                title="Sign Out"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <button
              onClick={handleOpenAuth}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-md shadow-indigo-500/20 transition-all"
            >
              <LogIn className="w-4 h-4" />
              <span>Sign In</span>
            </button>
          )}
        </div>
      </header>

      {/* Auth Modal */}
      {!onOpenAuthModal && (
        <AuthModal
          isOpen={internalAuthModalOpen}
          onClose={() => setInternalAuthModalOpen(false)}
        />
      )}
    </>
  );
};
