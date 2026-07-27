import React from 'react';
import type { RepositoryStatus } from '../types/repository';
import { CheckCircle2, AlertCircle, Loader2, GitBranch, FileCode, Cpu } from 'lucide-react';

interface StatusBadgeProps {
  status: RepositoryStatus;
  errorMessage?: string | null;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, errorMessage }) => {
  switch (status) {
    case 'INDEXED':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          <CheckCircle2 className="w-3.5 h-3.5" />
          INDEXED
        </span>
      );

    case 'CLONING':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20 animate-pulse">
          <GitBranch className="w-3.5 h-3.5 animate-spin" />
          CLONING...
        </span>
      );

    case 'PARSING':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-blue-500/10 text-blue-400 border border-blue-500/20 animate-pulse">
          <FileCode className="w-3.5 h-3.5 animate-pulse" />
          PARSING...
        </span>
      );

    case 'EMBEDDING':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 animate-pulse">
          <Cpu className="w-3.5 h-3.5 animate-bounce" />
          EMBEDDING...
        </span>
      );

    case 'FAILED':
      return (
        <span
          className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20 cursor-help"
          title={errorMessage || 'Repository indexing failed'}
        >
          <AlertCircle className="w-3.5 h-3.5" />
          FAILED
        </span>
      );

    case 'PENDING':
    default:
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-slate-500/10 text-slate-400 border border-slate-500/20">
          <Loader2 className="w-3.5 h-3.5 animate-spin" />
          PENDING
        </span>
      );
  }
};
