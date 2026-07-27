import React, { useState, useEffect } from 'react';
import type { ArchitectureSummaryDto } from '../types/repository';
import { repositoryService } from '../services/repositoryService';
import { X, Sparkles, RefreshCw, Copy, Check, BookOpen, CheckCircle2, Zap } from 'lucide-react';

interface ArchitectureSummaryModalProps {
  isOpen: boolean;
  onClose: () => void;
  repositoryId: string;
  repoName: string;
}

export const ArchitectureSummaryModal: React.FC<ArchitectureSummaryModalProps> = ({
  isOpen,
  onClose,
  repositoryId,
  repoName,
}) => {
  const [summaryData, setSummaryData] = useState<ArchitectureSummaryDto | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (isOpen && repositoryId) {
      loadSummary(false);
    }
  }, [isOpen, repositoryId]);

  const loadSummary = async (forceRegenerate: boolean = false) => {
    setIsLoading(true);
    setErrorMsg(null);

    try {
      const data = await repositoryService.getArchitectureSummary(repositoryId, forceRegenerate);
      setSummaryData(data);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to generate architecture summary');
    } finally {
      setIsLoading(false);
    }
  };

  const handleCopy = () => {
    if (!summaryData?.summary) return;
    navigator.clipboard.writeText(summaryData.summary);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-4xl glass-panel rounded-2xl border border-slate-800 shadow-2xl flex flex-col max-h-[90vh] overflow-hidden">
        {/* Header */}
        <div className="p-4 border-b border-slate-800/80 flex items-center justify-between bg-slate-900/60">
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-600 flex items-center justify-center text-white shadow-md shadow-indigo-500/20">
              <BookOpen className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h2 className="font-bold text-sm text-slate-100">Architecture Overview & Tech Summary</h2>
                {summaryData && (
                  summaryData.isCached ? (
                    <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" />
                      CACHED IN DB
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 flex items-center gap-1">
                      <Zap className="w-3 h-3 animate-pulse" />
                      FRESHLY GENERATED
                    </span>
                  )
                )}
              </div>
              <p className="text-xs text-slate-400 font-mono mt-0.5">{repoName}</p>
            </div>
          </div>

          <div className="flex items-center space-x-2">
            {summaryData && (
              <>
                <button
                  onClick={handleCopy}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium flex items-center gap-1.5 transition-colors border border-slate-700"
                >
                  {copied ? (
                    <>
                      <Check className="w-3.5 h-3.5 text-emerald-400" />
                      <span className="text-emerald-400">Copied</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3.5 h-3.5 text-slate-400" />
                      <span>Copy Markdown</span>
                    </>
                  )}
                </button>

                <button
                  onClick={() => loadSummary(true)}
                  disabled={isLoading}
                  className="px-3 py-1.5 rounded-lg bg-indigo-600/20 hover:bg-indigo-600/30 text-indigo-300 border border-indigo-500/30 text-xs font-medium flex items-center gap-1.5 transition-colors disabled:opacity-50"
                  title="Force re-generation via Groq"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
                  <span>Regenerate</span>
                </button>
              </>
            )}

            <button
              onClick={onClose}
              className="p-1.5 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Modal Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {errorMsg && (
            <p className="text-xs text-rose-400 bg-rose-500/10 p-3 rounded-xl border border-rose-500/20">
              {errorMsg}
            </p>
          )}

          {isLoading ? (
            <div className="py-20 flex flex-col items-center justify-center space-y-4 text-center">
              <div className="w-12 h-12 rounded-xl bg-indigo-600/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                <Sparkles className="w-6 h-6 animate-spin" />
              </div>
              <div>
                <h3 className="font-semibold text-sm text-slate-200">Analyzing Repository Architecture...</h3>
                <p className="text-xs text-slate-400 mt-1 max-w-sm">
                  Extracting structural code chunks and generating a comprehensive architecture overview via Groq LLM.
                </p>
              </div>
            </div>
          ) : summaryData?.summary ? (
            <div className="prose prose-invert prose-xs max-w-none space-y-4">
              <div className="p-5 rounded-2xl bg-slate-900/90 border border-slate-800/90 text-xs leading-relaxed text-slate-200 whitespace-pre-wrap font-sans">
                {summaryData.summary}
              </div>
            </div>
          ) : (
            <p className="text-center text-xs text-slate-500 py-12">
              No architecture summary available. Click "Regenerate" to create one.
            </p>
          )}
        </div>
      </div>
    </div>
  );
};
