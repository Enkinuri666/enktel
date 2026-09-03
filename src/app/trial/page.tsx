"use client";
import { useState } from "react";
import { Zap, Check, Copy } from "lucide-react";
import { TRIAL_HOURS, PLANS, formatPrice, CURRENCY } from "@/lib/pricing";

interface TrialLine {
  hours: number;
  username: string;
  password: string;
  serverUrl: string;
  m3uUrl: string;
  epgUrl: string;
  expiresAt: string;
}

export default function TrialPage() {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const [line, setLine] = useState<TrialLine | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    setPending(false);
    try {
      const res = await fetch("/api/trial", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      const json = await res.json();
      if (!res.ok) {
        setError(json.error ?? "Something went wrong.");
        setPending(Boolean(json.pending));
      } else {
        setLine(json as TrialLine);
      }
    } catch {
      setError("Could not reach the server. Try again in a moment.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="min-h-[70vh] px-4 sm:px-6 lg:px-8 py-16">
      <div className="max-w-2xl mx-auto">
        <div className="text-center mb-10">
          <p className="text-brand-primary text-sm font-bold uppercase tracking-widest mb-3">
            No card required
          </p>
          <h1 className="text-3xl sm:text-5xl font-black text-white mb-4">
            Start your free{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
              {TRIAL_HOURS}-hour trial
            </span>
          </h1>
          <p className="text-brand-muted text-lg">
            Full access — every live channel, every PPV event and the whole VOD
            library — for {TRIAL_HOURS} hours. If you like it, passes start at{" "}
            {formatPrice(PLANS[0].price)} {CURRENCY}.
          </p>
        </div>

        {!line && (
          <form
            onSubmit={submit}
            className="bg-brand-card border border-brand-border rounded-2xl p-6 sm:p-8"
          >
            <label htmlFor="email" className="block text-white font-semibold mb-2">
              Email address
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className="w-full rounded-xl bg-black/30 border border-brand-border px-4 py-3 text-white placeholder:text-brand-muted/60 focus:border-brand-primary focus:outline-none"
            />
            <p className="text-brand-muted text-xs mt-2">
              We send your login here, and nothing else you did not ask for.
            </p>
            <button
              type="submit"
              disabled={busy}
              className="mt-5 w-full rounded-xl bg-gradient-to-r from-brand-primary to-brand-secondary px-4 py-3 font-bold text-white disabled:opacity-60 inline-flex items-center justify-center gap-2"
            >
              <Zap className="w-4 h-4" />
              {busy ? "Setting it up…" : `Start my ${TRIAL_HOURS}-hour trial`}
            </button>

            {error && (
              <p
                className={`mt-4 text-sm ${pending ? "text-brand-secondary" : "text-red-400"}`}
              >
                {error}
              </p>
            )}
          </form>
        )}

        {line && (
          <div className="bg-brand-card border border-brand-primary/50 rounded-2xl p-6 sm:p-8">
            <div className="flex items-center gap-2 text-brand-secondary font-bold mb-4">
              <Check className="w-5 h-5" />
              Your trial is live
            </div>
            <dl className="space-y-3 text-sm">
              {[
                ["Server URL", line.serverUrl],
                ["Username", line.username],
                ["Password", line.password],
                ["M3U playlist", line.m3uUrl],
                ["EPG / XMLTV", line.epgUrl],
              ].map(([label, value]) => (
                <div key={label}>
                  <dt className="text-brand-muted">{label}</dt>
                  <dd className="flex items-center gap-2">
                    <code className="flex-1 break-all rounded bg-black/40 px-2 py-1.5 text-white">
                      {value}
                    </code>
                    <button
                      type="button"
                      aria-label={`Copy ${label}`}
                      onClick={() => navigator.clipboard?.writeText(value)}
                      className="rounded p-1.5 text-brand-muted hover:text-white hover:bg-white/10"
                    >
                      <Copy className="w-4 h-4" />
                    </button>
                  </dd>
                </div>
              ))}
            </dl>
            <p className="text-brand-muted text-xs mt-5">
              Expires {new Date(line.expiresAt).toLocaleString()}. Enter the server,
              username and password in the Enktel app under Settings → Playlists.
            </p>
          </div>
        )}
      </div>
    </main>
  );
}
