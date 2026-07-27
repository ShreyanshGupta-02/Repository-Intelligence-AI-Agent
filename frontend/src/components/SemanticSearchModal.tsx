import React, { useState } from 'react';
import type { SearchResultDto } from '../types/chat';
import { repositoryService } from '../services/repositoryService';
import { X, Search, FileCode, Hash, Loader2, Sparkles } from 'lucide-react';

interface SemanticSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  repositoryId: string;
  repoName: string;
}

export const SemanticSearchModal: React.FC<SemanticSearchModalProps> = ({
  isOpen,
  onClose,
  repositoryId,
  repoName,
}) => {
  const [query, setQuery] = useState('');
  const [topK, setTopK] = useState(5);
  const [results, setResults] = useState<SearchResultDto[] | null>(null);
  const [isSearching, setIsSearching] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;

    setIsSearching(true);
    setErrorMsg(null);

    try {
      const res = await repositoryService.search(repositoryId, query.trim(), topK);
      setResults(res);
    } catch (err: any) {
      setErrorMsg(err.message || 'Semantic search failed');
    } finally {
      setIsSearching(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-3xl glass-panel rounded-2xl border border-slate-800 shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
        {/* Header */}
        <div className="p-4 border-b border-slate-800/80 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Sparkles className="w-5 h-5 text-cyan-400" />
            <h2 className="font-bold text-sm text-slate-100">
              Direct Semantic Vector Search <span className="text-slate-400 font-normal">({repoName})</span>
            </h2>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Search Input Form */}
        <form onSubmit={handleSearch} className="p-4 border-b border-slate-800/40 bg-slate-900/40 space-y-3">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <input
                type="text"
                placeholder="Enter query to test vector similarity (e.g. 'HTML node parsing')"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="w-full px-3 py-2 pl-9 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              />
              <Search className="w-4 h-4 text-slate-500 absolute left-2.5 top-2.5" />
            </div>

            <div className="flex items-center space-x-2">
              <label className="text-xs text-slate-400 whitespace-nowrap">topK:</label>
              <select
                value={topK}
                onChange={(e) => setTopK(Number(e.target.value))}
                className="px-2 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 focus:outline-none"
              >
                <option value={3}>3</option>
                <option value={5}>5</option>
                <option value={10}>10</option>
              </select>
            </div>

            <button
              type="submit"
              disabled={isSearching || !query.trim()}
              className="px-4 py-2 bg-cyan-600 hover:bg-cyan-500 text-white font-semibold text-xs rounded-lg disabled:opacity-50 flex items-center space-x-1.5 transition-colors"
            >
              {isSearching ? <Loader2 className="w-4 h-4 animate-spin" /> : <span>Search</span>}
            </button>
          </div>
        </form>

        {/* Results Body */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {errorMsg && (
            <p className="text-xs text-rose-400 bg-rose-500/10 p-3 rounded-lg border border-rose-500/20">
              {errorMsg}
            </p>
          )}

          {results === null ? (
            <p className="text-center text-xs text-slate-500 py-12">
              Enter a search query to inspect nearest neighbor code chunks in Neon PGVector.
            </p>
          ) : results.length === 0 ? (
            <p className="text-center text-xs text-slate-400 py-12">
              No matching code chunks found for this query in target repository.
            </p>
          ) : (
            results.map((res, idx) => (
              <div key={res.chunkId || idx} className="p-3 rounded-xl bg-slate-900/80 border border-slate-800 space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center space-x-2 font-mono text-cyan-400">
                    <FileCode className="w-4 h-4 text-indigo-400" />
                    <span>{res.filePath}</span>
                    <span className="text-slate-500 font-sans text-[10px]">({res.language})</span>
                  </div>

                  <div className="flex items-center space-x-3 text-[11px]">
                    <span className="text-slate-400 font-mono flex items-center gap-1">
                      <Hash className="w-3 h-3 text-slate-500" />
                      L{res.startLine}-{res.endLine}
                    </span>
                    {res.score !== undefined && res.score !== null && (
                      <span className="px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 font-mono text-[10px] border border-emerald-500/20 font-bold">
                        Score: {(res.score * 100).toFixed(1)}%
                      </span>
                    )}
                  </div>
                </div>

                <pre className="font-mono text-[11px] text-slate-300 bg-slate-950 p-2.5 rounded-lg overflow-x-auto whitespace-pre leading-relaxed border border-slate-800/60 max-h-48">
                  {res.content}
                </pre>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
