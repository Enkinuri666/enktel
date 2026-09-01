"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { LogIn, Loader2, MessageCircle } from "lucide-react";
import Button from "@/components/ui/Button";
import { saveSubscription } from "@/lib/subscriptionStorage";

const whatsappNumber = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER || "";

export default function LoginPage() {
  const router = useRouter();
  const [form, setForm] = useState({ username: "", password: "" });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      });
      const data = await res.json();
      if (!res.ok || !data.subscription) throw new Error(data.error || "Something went wrong");
      saveSubscription(data.subscription);
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
      setLoading(false);
    }
  }

  return (
    <div className="max-w-md mx-auto px-4 sm:px-6 lg:px-8 py-14">
      <div className="text-center mb-10">
        <div className="inline-flex items-center gap-2 bg-brand-primary/10 border border-brand-primary/30 text-brand-primary text-xs font-bold px-3 py-1.5 rounded-full mb-4">
          <LogIn className="w-3.5 h-3.5" /> ACCOUNT LOGIN
        </div>
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-3">
          Access Your{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Dashboard
          </span>
        </h1>
        <p className="text-brand-muted">
          Sign in with the same IPTV username and password you stream with — no separate account needed.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-brand-card border border-brand-border rounded-xl p-6 space-y-5">
        <div>
          <label className="block text-brand-muted text-sm mb-1.5">Username *</label>
          <input
            type="text"
            required
            autoComplete="username"
            value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })}
            className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm placeholder:text-brand-muted/50 focus:outline-none focus:border-brand-primary transition-colors"
            placeholder="Your IPTV username"
          />
        </div>
        <div>
          <label className="block text-brand-muted text-sm mb-1.5">Password *</label>
          <input
            type="password"
            required
            autoComplete="current-password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm placeholder:text-brand-muted/50 focus:outline-none focus:border-brand-primary transition-colors"
            placeholder="Your IPTV password"
          />
        </div>

        {error && (
          <div className="bg-red-500/10 border border-red-500/30 rounded-lg px-4 py-3 text-red-400 text-sm">
            {error}
          </div>
        )}

        <Button type="submit" size="lg" fullWidth loading={loading}>
          {loading ? (
            <>
              <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              Signing in...
            </>
          ) : (
            "Log In"
          )}
        </Button>

        <p className="text-center text-brand-muted text-xs">
          Don&apos;t have an active line?{" "}
          <Link href="/watch" className="text-brand-primary hover:underline">
            Start a free trial
          </Link>
        </p>
      </form>

      {whatsappNumber && (
        <a
          href={`https://wa.me/${whatsappNumber}`}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-5 flex items-center justify-center gap-2.5 bg-brand-card border border-brand-border rounded-lg p-4 hover:border-green-500/40 transition-colors text-sm text-brand-muted"
        >
          <MessageCircle className="w-4 h-4 text-green-400 shrink-0" />
          Can&apos;t log in? <span className="text-white font-semibold">Chat with us on WhatsApp</span>
        </a>
      )}
    </div>
  );
}
