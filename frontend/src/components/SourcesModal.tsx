import React, { useState, useEffect } from 'react';
import type { SearchResultDto } from '../types/chat';
import { X, FileCode, Copy, Check, Hash, Sparkles, Layers } from 'lucide-react';

interface SourcesModalProps {
  isOpen: boolean;
  onClose: () => void;
  sources: SearchResultDto[];
  initialSelectedIndex?: number;
}

export const SourcesModal: React.FC<SourcesModalProps> = ({
  isOpen,
  onClose,
  sources,
  initialSelectedIndex = 0,
}) => {
  const [selectedIndex, setSelectedIndex] = useState<number>(initialSelectedIndex);
  const [copied, setCopied] = useState<boolean>(false);

  useEffect(() => {
    if (isOpen) {
      setSelectedIndex(initialSelectedIndex >= 0 && initialSelectedIndex < sources.length ? initialSelectedIndex : 0);
      setCopied(false);
    }
  }, [isOpen, initialSelectedIndex, sources.length]);

  if (!isOpen || sources.length === 0) return null;

  const currentSource = sources[selectedIndex] || sources[0];

  const handleCopy = () => {
    if (!currentSource) return;
    navigator.clipboard.writeText(currentSource.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-fade-in">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-4xl max-h-[85vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between bg-slate-950/50">
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-cyan-400">
              <Layers className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h3 className="font-bold text-sm text-slate-100">Retrieved Code Context</h3>
                <span className="px-2 py-0.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-[10px] font-semibold">
                  {sources.length} {sources.length === 1 ? 'Source' : 'Sources'}
                </span>
              </div>
              <p className="text-[11px] text-slate-400">
                Grounding code chunks retrieved from repository for AI answer generation
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content Body: Left File List + Right Code Preview */}
        <div className="flex-1 flex min-h-0 overflow-hidden">
          {/* File Selector Sidebar */}
          <div className="w-64 border-r border-slate-800/80 bg-slate-950/40 flex flex-col overflow-y-auto p-3 space-y-1.5">
            <div className="px-2 py-1 text-[10px] font-bold text-slate-500 uppercase tracking-wider">
              Referenced Chunks
            </div>
            {sources.map((src, idx) => {
              const isSelected = idx === selectedIndex;
              return (
                <button
                  key={src.chunkId || idx}
                  onClick={() => setSelectedIndex(idx)}
                  className={`w-full p-2.5 rounded-xl border text-left transition-all flex flex-col gap-1 ${
                    isSelected
                      ? 'bg-indigo-950/50 border-indigo-500/50 text-slate-100 shadow-sm'
                      : 'bg-slate-900/60 border-slate-800/80 text-slate-400 hover:bg-slate-850 hover:text-slate-200'
                  }`}
                >
                  <div className="flex items-center justify-between gap-1.5">
                    <div className="flex items-center space-x-1.5 min-w-0">
                      <FileCode className={`w-3.5 h-3.5 flex-shrink-0 ${isSelected ? 'text-cyan-400' : 'text-slate-500'}`} />
                      <span className="font-mono text-xs font-semibold truncate">{src.fileName || src.filePath.split('/').pop()}</span>
                    </div>
                    {src.score !== undefined && src.score !== null && (
                      <span className="text-[9px] font-mono font-semibold px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 flex-shrink-0">
                        {(src.score * 100).toFixed(0)}%
                      </span>
                    )}
                  </div>

                  <div className="flex items-center justify-between text-[10px] text-slate-500 font-mono">
                    <span className="truncate max-w-[130px]">{src.filePath}</span>
                    <span>L{src.startLine}-{src.endLine}</span>
                  </div>
                </button>
              );
            })}
          </div>

          {/* Main Code View Pane */}
          <div className="flex-1 flex flex-col bg-slate-950 relative overflow-hidden">
            {/* Source Header Bar */}
            <div className="px-5 py-3 border-b border-slate-800/80 bg-slate-900/60 flex items-center justify-between text-xs">
              <div className="flex items-center space-x-2 min-w-0">
                <FileCode className="w-4 h-4 text-cyan-400 flex-shrink-0" />
                <span className="font-mono text-slate-200 font-semibold truncate">{currentSource.filePath}</span>
                <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono text-[10px] border border-slate-700">
                  {currentSource.language}
                </span>
                <span className="text-slate-500 flex items-center gap-1 font-mono text-[11px]">
                  <Hash className="w-3 h-3 text-slate-500" />
                  Lines {currentSource.startLine}-{currentSource.endLine}
                </span>
              </div>

              <button
                onClick={handleCopy}
                className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center gap-1.5 transition-colors border border-slate-700"
              >
                {copied ? (
                  <>
                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                    <span className="text-emerald-400">Copied!</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5 text-slate-400" />
                    <span>Copy Code</span>
                  </>
                )}
              </button>
            </div>

            {/* Code Body Container */}
            <div className="flex-1 overflow-auto p-5 font-mono text-xs text-slate-200 leading-relaxed bg-slate-950">
              <pre className="whitespace-pre overflow-x-auto font-mono">
                {currentSource.content}
              </pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
