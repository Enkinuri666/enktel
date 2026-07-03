"use client";
import { useEffect, useRef, useState } from "react";
import { MessageCircle, Sparkles, X } from "lucide-react";
import ChatAssistantPanel from "@/components/chat/ChatAssistantPanel";

const WHATSAPP_NUMBER = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER || "";
const WHATSAPP_MESSAGE = "Hi! I'm interested in Enktel IPTV — can you help me get started?";

declare global {
  interface Window {
    Tawk_API?: {
      hideWidget?: () => void;
      showWidget?: () => void;
      maximize?: () => void;
      minimize?: () => void;
      onChatMinimized?: () => void;
      onChatHidden?: () => void;
      onLoad?: () => void;
    };
  }
}

type PanelState = "closed" | "menu" | "ai";

// A single branded launcher replaces the separate Tawk.to bubble and WhatsApp
// icon, which used to stack on top of each other in the same corner with no
// indication of which one to use for what. Tawk's own launcher bubble is
// hidden (see layout.tsx's onLoad hook) and driven entirely from here, so the
// live chat panel still opens inline on the page — never a new tab. The AI
// assistant is a third option in the same menu, rendered in-page as well.
export default function ChatLauncher() {
  const [panel, setPanel] = useState<PanelState>("closed");
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setPanel("closed");
      }
    }
    if (panel === "menu") document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, [panel]);

  function openLiveChat() {
    setPanel("closed");
    const tawk = window.Tawk_API;
    if (tawk?.showWidget && tawk?.maximize) {
      tawk.showWidget();
      tawk.maximize();
    }
  }

  function openWhatsApp() {
    setPanel("closed");
    if (!WHATSAPP_NUMBER) return;
    const href = `https://wa.me/${WHATSAPP_NUMBER}?text=${encodeURIComponent(WHATSAPP_MESSAGE)}`;
    window.open(href, "_blank", "noopener,noreferrer");
  }

  return (
    <div ref={wrapperRef} className="fixed bottom-5 right-5 z-[9999] flex flex-col items-end gap-3">
      {panel === "ai" && <ChatAssistantPanel onClose={() => setPanel("closed")} />}

      {panel === "menu" && (
        <div className="w-64 rounded-2xl overflow-hidden border border-white/10 bg-brand-card shadow-2xl shadow-black/50 animate-fade-in">
          <div className="px-4 py-3 bg-gradient-to-r from-brand-primary to-brand-secondary">
            <p className="text-white font-bold text-sm">Need a hand?</p>
            <p className="text-white/80 text-xs">Pick how you&apos;d like to reach us.</p>
          </div>
          <div className="p-2">
            <button
              onClick={() => setPanel("ai")}
              className="w-full flex items-center gap-3 rounded-xl px-3 py-2.5 hover:bg-white/5 transition-colors text-left"
            >
              <span className="w-9 h-9 rounded-lg bg-brand-secondary/15 border border-brand-secondary/30 flex items-center justify-center shrink-0">
                <Sparkles className="w-4 h-4 text-brand-secondary" />
              </span>
              <span>
                <span className="block text-white text-sm font-semibold">Ask Enktel AI</span>
                <span className="block text-brand-muted text-xs">Instant help &amp; live TV guide</span>
              </span>
            </button>
            <button
              onClick={openLiveChat}
              className="w-full flex items-center gap-3 rounded-xl px-3 py-2.5 hover:bg-white/5 transition-colors text-left"
            >
              <span className="w-9 h-9 rounded-lg bg-brand-primary/15 border border-brand-primary/30 flex items-center justify-center shrink-0">
                <MessageCircle className="w-4 h-4 text-brand-primary" />
              </span>
              <span>
                <span className="block text-white text-sm font-semibold">Live Chat</span>
                <span className="block text-brand-muted text-xs">Opens right here, instantly</span>
              </span>
            </button>
            {WHATSAPP_NUMBER && (
              <button
                onClick={openWhatsApp}
                className="w-full flex items-center gap-3 rounded-xl px-3 py-2.5 hover:bg-white/5 transition-colors text-left"
              >
                <span className="w-9 h-9 rounded-lg bg-[#25D366]/15 border border-[#25D366]/30 flex items-center justify-center shrink-0">
                  <svg viewBox="0 0 32 32" className="w-4 h-4 fill-[#25D366]" aria-hidden="true">
                    <path d="M16.004 3C9.376 3 4 8.373 4 15c0 2.347.687 4.533 1.872 6.37L4 29l7.84-1.83A11.93 11.93 0 0 0 16.004 27C22.63 27 28 21.627 28 15S22.63 3 16.004 3Zm6.97 17.13c-.295.83-1.45 1.5-2.36 1.69-.63.13-1.45.24-4.21-.9-3.54-1.46-5.82-5.02-6-5.26-.18-.24-1.44-1.92-1.44-3.66 0-1.74.91-2.6 1.23-2.95.32-.35.7-.44.94-.44.23 0 .47.002.67.012.21.01.5-.08.78.6.3.7.97 2.36 1.06 2.53.09.17.15.37.03.59-.12.22-.18.36-.36.55-.18.2-.37.45-.53.6-.18.17-.37.36-.16.71.21.35.94 1.55 2.02 2.51 1.39 1.24 2.57 1.63 2.95 1.81.38.18.61.16.84-.08.23-.24.97-1.06 1.23-1.43.26-.37.51-.31.85-.18.34.12 2.16 1.02 2.53 1.2.37.18.61.27.7.42.09.15.09.86-.21 1.69Z" />
                  </svg>
                </span>
                <span>
                  <span className="block text-white text-sm font-semibold">WhatsApp</span>
                  <span className="block text-brand-muted text-xs">Continues in WhatsApp</span>
                </span>
              </button>
            )}
          </div>
        </div>
      )}

      <button
        onClick={() => setPanel((p) => (p === "closed" ? "menu" : "closed"))}
        aria-label={panel === "closed" ? "Open support menu" : "Close support menu"}
        aria-expanded={panel !== "closed"}
        className="relative flex items-center justify-center w-14 h-14 rounded-full border border-white/10 bg-gradient-to-br from-brand-primary to-brand-secondary shadow-lg shadow-brand-primary/40 transition-transform duration-300 hover:scale-110"
      >
        {panel === "closed" && <span className="absolute inline-flex h-full w-full rounded-full bg-brand-primary opacity-40 animate-ping" />}
        {panel === "closed" ? (
          <MessageCircle className="relative w-6 h-6 text-white" />
        ) : (
          <X className="relative w-5 h-5 text-white" />
        )}
      </button>
    </div>
  );
}
