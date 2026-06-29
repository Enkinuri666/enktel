"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Sparkles, Loader2, MessageCircle, ShieldCheck, Clock, Tv } from "lucide-react";
import Button from "@/components/ui/Button";
import { DEVICE_GUIDES } from "@/lib/deviceGuides";
import { saveSubscription, loadSubscription } from "@/lib/subscriptionStorage";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const whatsappNumber = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER || "";
const TRIAL_USED_KEY = "enktel_trial_used";

function getBrowserFingerprint(): string {
  try {
    const parts = [
      screen.width,
      screen.height,
      screen.colorDepth,
      Intl.DateTimeFormat().resolvedOptions().timeZone,
      navigator.language,
      navigator.hardwareConcurrency || 0,
      navigator.maxTouchPoints || 0,
      new Date().getTimezoneOffset(),
    ];
    const raw = parts.join("|");
    let hash = 0;
    for (let i = 0; i < raw.length; i++) {
      hash = ((hash << 5) - hash + raw.charCodeAt(i)) | 0;
    }
    return Math.abs(hash).toString(36);
  } catch {
    return "";
  }
}

export default function TrialPage() {
  const router = useRouter();
  const [form, setForm] = useState({ name: "", email: "", device: DEVICE_GUIDES[0].id as string });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [alreadyUsed, setAlreadyUsed] = useState(false);
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    if (localStorage.getItem(TRIAL_USED_KEY)) {
      setAlreadyUsed(true);
    }
    if (loadSubscription()?.isTrial) {
      setAlreadyUsed(true);
    }
    setChecked(true);
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    setSubmitted(true);
    try {
      const fp = getBrowserFingerprint();
      const res = await fetch("/api/trial", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, fp }),
      });
      const data = await res.json();
      if (!res.ok || !data.subscription) throw new Error(data.error || "Something went wrong");
      localStorage.setItem(TRIAL_USED_KEY, Date.now().toString());
      saveSubscription(data.subscription);
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
      setLoading(false);
    }
  }

  if (!checked) return null;

  if (alreadyUsed) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-14">
        <div className="bg-brand-card border border-brand-border rounded-xl p-10 text-center">
          <Tv className="w-10 h-10 text-brand-muted mx-auto mb-4" />
          <h1 className="text-2xl font-bold text-white mb-2">You&apos;ve already used your free trial</h1>
          <p className="text-brand-muted text-sm mb-6 max-w-md mx-auto">
            Each customer gets one 24-hour trial. If you already have credentials, log in to your dashboard.
            Ready for full access? Check out our plans.
          </p>
          <div className="flex items-center justify-center gap-3 flex-wrap">
            <Link href="/dashboard">
              <Button>Go to Dashboard</Button>
            </Link>
            <Link href="/login">
              <Button variant="outline">Log In</Button>
            </Link>
            <Link href="/pricing">
              <Button variant="outline">View Plans</Button>
            </Link>
          </div>
          {whatsappNumber && (
            <a
              href={`https://wa.me/${whatsappNumber}?text=${encodeURIComponent("Hi, I'd like to discuss upgrading from my trial.")}`}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-5 inline-flex items-center gap-2 text-green-400 hover:text-green-300 text-sm font-semibold"
            >
              <MessageCircle className="w-4 h-4" />
              Chat with us on WhatsApp
            </a>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-14">
      <div className="text-center mb-10">
        <div className="inline-flex items-center gap-2 bg-brand-primary/10 border border-brand-primary/30 text-brand-primary text-xs font-bold px-3 py-1.5 rounded-full mb-4">
          <Sparkles className="w-3.5 h-3.5" /> 100% FREE — NO CARD REQUIRED
        </div>
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-3">
          Try Enktel IPTV{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Free for 24 Hours
          </span>
        </h1>
        <p className="text-brand-muted">
          Full access to {CHANNEL_COUNT_LABEL} live channels, 4K streaming, and the VOD library. Your stream
          credentials are generated instantly and emailed to you.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-brand-card border border-brand-border rounded-xl p-6 space-y-5">
        <div>
          <label className="block text-brand-muted text-sm mb-1.5">Full Name *</label>
          <input
            type="text"
            required
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm placeholder:text-brand-muted/50 focus:outline-none focus:border-brand-primary transition-colors"
            placeholder="John Smith"
          />
        </div>
        <div>
          <label className="block text-brand-muted text-sm mb-1.5">Email Address *</label>
          <input
            type="email"
            required
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm placeholder:text-brand-muted/50 focus:outline-none focus:border-brand-primary transition-colors"
            placeholder="john@example.com"
          />
          <p className="text-brand-muted/60 text-xs mt-1.5">
            We&apos;ll send your username, password, and setup guide here.
          </p>
        </div>
        <div>
          <label className="block text-brand-muted text-sm mb-1.5">What will you watch on?</label>
          <select
            value={form.device}
            onChange={(e) => setForm({ ...form, device: e.target.value })}
            className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm focus:outline-none focus:border-brand-primary transition-colors"
          >
            {DEVICE_GUIDES.map((d) => (
              <option key={d.id} value={d.id}>{d.label}</option>
            ))}
          </select>
          <p className="text-brand-muted/60 text-xs mt-1.5">
            We&apos;ll tailor your setup instructions and dashboard guide to this device.
          </p>
        </div>

        {error && (
          <div className="bg-red-500/10 border border-red-500/30 rounded-lg px-4 py-3 text-sm space-y-2">
            <p className="text-red-400">{error}</p>
            {whatsappNumber && (
              <a
                href={`https://wa.me/${whatsappNumber}?text=${encodeURIComponent("Hi, I just tried to sign up for a free trial but got an error. My email is: " + form.email)}`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 text-green-400 hover:text-green-300 font-semibold"
              >
                <MessageCircle className="w-3.5 h-3.5" />
                Get help on WhatsApp
              </a>
            )}
          </div>
        )}

        <Button type="submit" size="lg" fullWidth loading={loading} disabled={loading || (submitted && !!error)}>
          {loading ? (
            <>
              <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              Setting up your trial...
            </>
          ) : submitted && error ? (
            "Check your email or contact WhatsApp"
          ) : (
            "Start My Free Trial"
          )}
        </Button>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
          <div className="flex items-center gap-2.5 text-xs text-brand-muted">
            <Clock className="w-4 h-4 text-brand-primary shrink-0" />
            Instant activation, no waiting
          </div>
          <div className="flex items-center gap-2.5 text-xs text-brand-muted">
            <ShieldCheck className="w-4 h-4 text-green-400 shrink-0" />
            No payment details needed
          </div>
        </div>
      </form>

      {whatsappNumber && (
        <a
          href={`https://wa.me/${whatsappNumber}`}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-5 flex items-center justify-center gap-2.5 bg-brand-card border border-brand-border rounded-lg p-4 hover:border-green-500/40 transition-colors text-sm text-brand-muted"
        >
          <MessageCircle className="w-4 h-4 text-green-400 shrink-0" />
          Questions before you start? <span className="text-white font-semibold">Chat with us on WhatsApp</span>
        </a>
      )}
    </div>
  );
}
