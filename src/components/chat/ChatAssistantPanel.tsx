"use client";
import { useEffect, useRef, useState } from "react";
import { Sparkles, Send, X, Loader2 } from "lucide-react";

type Role = "user" | "assistant";
interface ChatMessage {
  role: Role;
  content: string;
  isError?: boolean;
}

const SUGGESTIONS = [
  "What's on HRT 1 right now?",
  "Can I watch without installing an app?",
  "What are your pricing plans?",
  "Any big matches on this week?",
];

// The shared API key backing this assistant is rate-limited to one request
// every 20 seconds (see src/lib/rateLimiter.ts) — replies can take a while,
// especially if the assistant needs a tool-call round first, or if another
// visitor's message is queued ahead of this one. Keep the wait from reading
// as "broken" by upgrading the status copy the longer it takes.
const THINKING_MESSAGES = [
  { afterMs: 0, text: "Thinking…" },
  { afterMs: 6000, text: "Still thinking — checking live data…" },
  { afterMs: 15000, text: "Almost there — replies can take up to 30–40s…" },
];

export default function ChatAssistantPanel({ onClose }: { onClose: () => void }) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [thinkingText, setThinkingText] = useState(THINKING_MESSAGES[0].text);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, loading]);

  useEffect(() => {
    if (!loading) return;
    setThinkingText(THINKING_MESSAGES[0].text);
    const timers = THINKING_MESSAGES.slice(1).map(({ afterMs, text }) =>
      setTimeout(() => setThinkingText(text), afterMs)
    );
    return () => timers.forEach(clearTimeout);
  }, [loading]);

  async function sendMessage(text: string) {
    const trimmed = text.trim();
    if (!trimmed || loading) return;

    const next: ChatMessage[] = [...messages, { role: "user", content: trimmed }];
    setMessages(next);
    setInput("");
    setLoading(true);

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: next.map((m) => ({ role: m.role, content: m.content })) }),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok || !data?.reply) {
        setMessages((prev) => [
          ...prev,
          { role: "assistant", content: data?.error || "Something went wrong. Please try Live Chat instead.", isError: true },
        ]);
      } else {
        setMessages((prev) => [...prev, { role: "assistant", content: data.reply }]);
      }
    } catch {
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content: "Couldn't reach the assistant — check your connection or try Live Chat.", isError: true },
      ]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="w-[min(380px,90vw)] h-[min(560px,75vh)] rounded-2xl overflow-hidden border border-white/10 bg-brand-card shadow-2xl shadow-black/50 flex flex-col animate-fade-in">
      <div className="px-4 py-3 bg-gradient-to-r from-brand-primary to-brand-secondary flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <Sparkles className="w-4 h-4 text-white" />
          <div>
            <p className="text-white font-bold text-sm leading-tight">Enktel Assistant</p>
            <p className="text-white/80 text-[11px] leading-tight">Support, setup help &amp; live TV guide</p>
          </div>
        </div>
        <button onClick={onClose} aria-label="Close assistant" className="text-white/80 hover:text-white transition-colors">
          <X className="w-4 h-4" />
        </button>
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-3 py-3 space-y-3">
        {messages.length === 0 && (
          <div className="text-center px-2 py-4">
            <p className="text-brand-muted text-sm mb-4">
              Ask me about pricing, setup, what&apos;s on now, or upcoming matches.
            </p>
            <div className="flex flex-col gap-2">
              {SUGGESTIONS.map((s) => (
                <button
                  key={s}
                  onClick={() => sendMessage(s)}
                  disabled={loading}
                  className="text-left text-xs text-white bg-white/5 hover:bg-white/10 border border-white/10 rounded-lg px-3 py-2 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((m, i) => (
          <div key={i} className={`flex ${m.role === "user" ? "justify-end" : "justify-start"}`}>
            <div
              className={`max-w-[85%] rounded-xl px-3 py-2 text-sm leading-relaxed ${
                m.role === "user"
                  ? "bg-brand-primary text-white"
                  : m.isError
                  ? "bg-brand-accent/10 border border-brand-accent/30 text-white"
                  : "bg-white/5 border border-white/10 text-white"
              }`}
            >
              {m.content}
            </div>
          </div>
        ))}

        {loading && (
          <div className="flex justify-start">
            <div className="bg-white/5 border border-white/10 rounded-xl px-3 py-2 flex items-center gap-2">
              <Loader2 className="w-3.5 h-3.5 text-brand-secondary animate-spin" />
              <span className="text-brand-muted text-xs">{thinkingText}</span>
            </div>
          </div>
        )}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          sendMessage(input);
        }}
        className="p-3 border-t border-white/10 flex items-center gap-2 shrink-0"
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={loading ? "Waiting for a reply…" : "Ask a question…"}
          disabled={loading}
          className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder:text-brand-muted focus:outline-none focus:border-brand-primary/50 disabled:opacity-60"
        />
        <button
          type="submit"
          disabled={loading || !input.trim()}
          aria-label="Send"
          className="w-9 h-9 shrink-0 rounded-lg bg-brand-primary hover:bg-purple-600 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center transition-colors"
        >
          <Send className="w-4 h-4 text-white" />
        </button>
      </form>
    </div>
  );
}
