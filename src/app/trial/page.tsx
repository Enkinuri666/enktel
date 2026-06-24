"use client";
import { useEffect } from "react";
import { Sparkles, MessageCircle, ShieldCheck, Clock } from "lucide-react";
import Button from "@/components/ui/Button";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const whatsappNumber = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER || "";
const TRIAL_MESSAGE = "Hi! I'd like to start my free 24-hour Enktel IPTV trial.";
const whatsappHref = whatsappNumber
  ? `https://wa.me/${whatsappNumber}?text=${encodeURIComponent(TRIAL_MESSAGE)}`
  : "";

export default function TrialPage() {
  useEffect(() => {
    if (whatsappHref) window.location.href = whatsappHref;
  }, []);

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-14 text-center">
      <div className="inline-flex items-center gap-2 bg-brand-primary/10 border border-brand-primary/30 text-brand-primary text-xs font-bold px-3 py-1.5 rounded-full mb-4">
        <Sparkles className="w-3.5 h-3.5" /> 100% FREE — NO CARD REQUIRED
      </div>
      <h1 className="text-3xl sm:text-4xl font-black text-white mb-3">
        Try Enktel IPTV{" "}
        <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
          Free for 24 Hours
        </span>
      </h1>
      <p className="text-brand-muted mb-8">
        Full access to {CHANNEL_COUNT_LABEL} live channels, 4K streaming, and the VOD library.
        Message us on WhatsApp and we&apos;ll set up your trial credentials right away.
      </p>

      {whatsappHref ? (
        <a href={whatsappHref} target="_blank" rel="noopener noreferrer">
          <Button size="lg">
            <MessageCircle className="w-4 h-4 mr-2" />
            Start My Free Trial on WhatsApp
          </Button>
        </a>
      ) : (
        <div className="bg-brand-card border border-brand-border rounded-lg px-4 py-3 text-brand-muted text-sm">
          Trial signup is temporarily unavailable — please check back soon.
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-8 max-w-md mx-auto">
        <div className="flex items-center gap-2.5 text-xs text-brand-muted">
          <Clock className="w-4 h-4 text-brand-primary shrink-0" />
          Instant activation, no waiting
        </div>
        <div className="flex items-center gap-2.5 text-xs text-brand-muted">
          <ShieldCheck className="w-4 h-4 text-green-400 shrink-0" />
          No payment details needed
        </div>
      </div>
    </div>
  );
}
