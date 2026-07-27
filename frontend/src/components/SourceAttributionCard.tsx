import React from 'react';
import type { SearchResultDto } from '../types/chat';
import { FileCode, Layers, ExternalLink } from 'lucide-react';

interface SourceAttributionCardProps {
  sources: SearchResultDto[];
  onOpenModal: (index: number) => void;
}

export const SourceAttributionCard: React.FC<SourceAttributionCardProps> = ({ sources, onOpenModal }) => {
  if (!sources || sources.length === 0) return null;

  return (
    <div className="pt-2 border-t border-slate-800/40">
      <div className="flex flex-wrap items-center gap-2">
        {/* Render individual file link pills */}
        {sources.slice(0, 3).map((src, idx) => {
          const fileName = src.fileName || src.filePath.split('/').pop() || src.filePath;
          return (
            <button
              key={src.chunkId || idx}
              onClick={() => onOpenModal(idx)}
              className="group flex items-center space-x-1.5 px-2.5 py-1 rounded-lg bg-slate-900/90 hover:bg-indigo-950/60 border border-slate-800/90 hover:border-indigo-500/40 text-slate-300 hover:text-indigo-300 text-xs font-mono transition-all shadow-sm cursor-pointer"
              title={`View snippet from ${src.filePath} (Lines ${src.startLine}-${src.endLine})`}
            >
              <FileCode className="w-3.5 h-3.5 text-cyan-400 group-hover:scale-110 transition-transform flex-shrink-0" />
              <span className="truncate max-w-[150px] font-medium">{fileName}</span>
              <span className="text-[10px] text-slate-500 font-sans">#L{src.startLine}</span>
            </button>
          );
        })}

        {/* Overflow Pill */}
        {sources.length > 3 && (
          <button
            onClick={() => onOpenModal(3)}
            className="px-2.5 py-1 rounded-lg bg-slate-900/80 hover:bg-slate-800 border border-slate-800 text-indigo-400 text-xs font-medium transition-all cursor-pointer"
          >
            +{sources.length - 3} more
          </button>
        )}

        {/* View All Sources Button Pill */}
        <button
          onClick={() => onOpenModal(0)}
          className="flex items-center space-x-1.5 px-2.5 py-1 rounded-lg bg-indigo-500/10 hover:bg-indigo-500/20 border border-indigo-500/20 text-indigo-300 text-xs font-medium transition-all cursor-pointer ml-auto"
        >
          <Layers className="w-3.5 h-3.5 text-indigo-400" />
          <span>{sources.length} Sources</span>
          <ExternalLink className="w-3 h-3 text-indigo-400" />
        </button>
      </div>
    </div>
  );
};
