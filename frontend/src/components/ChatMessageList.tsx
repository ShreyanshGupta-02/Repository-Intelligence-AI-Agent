import React, { useState, useEffect, useRef } from 'react';
import type { ChatMessageItem, SearchResultDto } from '../types/chat';
import { SourceAttributionCard } from './SourceAttributionCard';
import { SourcesModal } from './SourcesModal';
import { Bot, User, Sparkles, ArrowDown } from 'lucide-react';

interface ChatMessageListProps {
  messages: ChatMessageItem[];
  isLoading: boolean;
  activeRepoName: string | null;
  onSelectExampleQuestion?: (question: string) => void;
}

const LoadingStatusIndicator: React.FC = () => {
  const steps = [
    'Searching codebase...',
    'Analyzing repository context...',
    'Synthesizing response...',
  ];
  const [currentStep, setCurrentStep] = React.useState(0);

  React.useEffect(() => {
    const interval = setInterval(() => {
      setCurrentStep((prev) => (prev + 1) % steps.length);
    }, 2200);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="flex gap-4 max-w-4xl mr-auto items-start animate-fade-in">
      <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-indigo-600 to-purple-600 text-white flex items-center justify-center flex-shrink-0">
        <Bot className="w-4 h-4 animate-spin" />
      </div>
      <div className="p-4 rounded-2xl glass-panel text-slate-300 text-xs rounded-tl-none border-slate-800 flex items-center space-x-3">
        <div className="flex space-x-1.5">
          <div className="w-2 h-2 rounded-full bg-indigo-400 animate-bounce"></div>
          <div className="w-2 h-2 rounded-full bg-indigo-400 animate-bounce [animation-delay:0.2s]"></div>
          <div className="w-2 h-2 rounded-full bg-cyan-400 animate-bounce [animation-delay:0.4s]"></div>
        </div>
        <span className="text-slate-300 font-medium text-xs transition-all duration-300">
          {steps[currentStep]}
        </span>
      </div>
    </div>
  );
};

export const ChatMessageList: React.FC<ChatMessageListProps> = ({
  messages,
  isLoading,
  activeRepoName,
  onSelectExampleQuestion,
}) => {
  const [modalSources, setModalSources] = useState<SearchResultDto[] | null>(null);
  const [modalInitialIndex, setModalInitialIndex] = useState<number>(0);
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);

  // ChatGPT & Claude style scrolling refs & state
  const containerRef = useRef<HTMLDivElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showScrollBottomButton, setShowScrollBottomButton] = useState<boolean>(false);

  const scrollToBottom = (smooth = true) => {
    messagesEndRef.current?.scrollIntoView({
      behavior: smooth ? 'smooth' : 'auto',
    });
  };

  // Auto-scroll on new message or loading state change
  useEffect(() => {
    scrollToBottom(true);
  }, [messages.length, isLoading]);

  // Listen to scroll events to show/hide "Scroll to bottom" button
  const handleScroll = () => {
    if (!containerRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = containerRef.current;
    const isScrolledUp = scrollHeight - scrollTop - clientHeight > 120;
    setShowScrollBottomButton(isScrolledUp);
  };

  const handleOpenSourcesModal = (sources: SearchResultDto[], index: number) => {
    setModalSources(sources);
    setModalInitialIndex(index);
    setIsModalOpen(true);
  };

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      className="flex-1 overflow-y-auto p-6 space-y-6 relative scroll-smooth"
    >
      {messages.length === 0 ? (
        <div className="h-full flex flex-col items-center justify-center text-center max-w-lg mx-auto py-12 space-y-4">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-indigo-600/20 via-indigo-500/10 to-cyan-500/20 border border-indigo-500/30 flex items-center justify-center shadow-xl shadow-indigo-500/10">
            <Sparkles className="w-8 h-8 text-indigo-400" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-100">
              Ask anything about <span className="text-indigo-400">{activeRepoName || 'this repository'}</span>
            </h3>
            <p className="text-xs text-slate-400 mt-1 leading-relaxed">
              Every answer is strictly grounded in code retrieved directly from your repository.
            </p>
          </div>

          {/* Quick Starter Suggestions */}
          <div className="w-full pt-4 space-y-2 text-left">
            <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Example questions to try:</p>
            <div className="grid grid-cols-1 gap-2">
              <div
                onClick={() => onSelectExampleQuestion && onSelectExampleQuestion("Explain the core architecture and main entry points of this repository.")}
                className="p-3 rounded-lg bg-slate-900/60 border border-slate-800 text-xs text-slate-300 hover:border-indigo-500/40 hover:bg-slate-850 transition-all cursor-pointer flex items-center justify-between group"
              >
                <span>💡 "Explain the core architecture and main entry points of this repository."</span>
                <span className="text-[10px] text-indigo-400 opacity-0 group-hover:opacity-100 transition-opacity font-semibold">Ask →</span>
              </div>
              <div
                onClick={() => onSelectExampleQuestion && onSelectExampleQuestion("Where are API requests or core data handlers defined?")}
                className="p-3 rounded-lg bg-slate-900/60 border border-slate-800 text-xs text-slate-300 hover:border-indigo-500/40 hover:bg-slate-850 transition-all cursor-pointer flex items-center justify-between group"
              >
                <span>⚡ "Where are API requests or core data handlers defined?"</span>
                <span className="text-[10px] text-indigo-400 opacity-0 group-hover:opacity-100 transition-opacity font-semibold">Ask →</span>
              </div>
              <div
                onClick={() => onSelectExampleQuestion && onSelectExampleQuestion("How is configuration, error handling, or validation implemented?")}
                className="p-3 rounded-lg bg-slate-900/60 border border-slate-800 text-xs text-slate-300 hover:border-indigo-500/40 hover:bg-slate-850 transition-all cursor-pointer flex items-center justify-between group"
              >
                <span>🔍 "How is configuration, error handling, or validation implemented?"</span>
                <span className="text-[10px] text-indigo-400 opacity-0 group-hover:opacity-100 transition-opacity font-semibold">Ask →</span>
              </div>
            </div>
          </div>
        </div>
      ) : (
        messages.map((msg) => {
          const isUser = msg.role === 'user';

          return (
            <div
              key={msg.id}
              className={`flex gap-4 max-w-4xl ${isUser ? 'ml-auto flex-row-reverse' : 'mr-auto'}`}
            >
              {/* Avatar */}
              <div
                className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 text-xs font-bold shadow-md ${isUser
                  ? 'bg-gradient-to-tr from-cyan-600 to-blue-500 text-white'
                  : 'bg-gradient-to-tr from-indigo-600 to-purple-600 text-white'
                  }`}
              >
                {isUser ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
              </div>

              {/* Message Content Bubble */}
              <div className={`space-y-3 min-w-0 ${isUser ? 'items-end' : 'items-start'}`}>
                <div
                  className={`p-4 rounded-2xl text-xs leading-relaxed shadow-sm ${isUser
                    ? 'bg-indigo-600 text-white rounded-tr-none'
                    : 'glass-panel text-slate-200 rounded-tl-none border-slate-800/90'
                    }`}
                >
                  {/* Message Text with preserved whitespace formatting */}
                  <div className="whitespace-pre-wrap font-sans text-xs leading-relaxed space-y-2">
                    {msg.content}
                  </div>
                </div>

                {/* Compact Sources Link Pills (for Assistant responses) */}
                {!isUser && msg.sources && msg.sources.length > 0 && (
                  <SourceAttributionCard
                    sources={msg.sources}
                    onOpenModal={(idx) => handleOpenSourcesModal(msg.sources!, idx)}
                  />
                )}

                <div className="text-[10px] text-slate-500 px-1">
                  {msg.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
              </div>
            </div>
          );
        })
      )}

      {/* Typing / Loading indicator */}
      {isLoading && <LoadingStatusIndicator />}

      {/* End Ref for Auto Scroll */}
      <div ref={messagesEndRef} />

      {/* Floating ChatGPT / Claude style Scroll-to-Bottom Circular Button */}
      {showScrollBottomButton && (
        <div className="sticky bottom-4 left-0 right-0 flex justify-center z-30 pointer-events-none">
          <button
            onClick={() => scrollToBottom(true)}
            className="w-8 h-8 rounded-full bg-slate-900/90 hover:bg-slate-800 border border-slate-700/80 text-slate-100 shadow-2xl shadow-black/60 flex items-center justify-center backdrop-blur-md transition-all duration-200 hover:scale-110 cursor-pointer pointer-events-auto group"
            title="Scroll to latest message"
          >
            <ArrowDown className="w-4 h-4 text-slate-200 group-hover:text-white transition-colors" />
          </button>
        </div>
      )}

      {/* Code Context Sources Modal */}
      {modalSources && (
        <SourcesModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          sources={modalSources}
          initialSelectedIndex={modalInitialIndex}
        />
      )}
    </div>
  );
};
