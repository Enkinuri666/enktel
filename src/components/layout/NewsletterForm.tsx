"use client";
import { useState } from "react";
import { Mail, Check } from "lucide-react";

export default function NewsletterForm() {
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState<"idle" | "loading" | "done" | "error">("idle");
  const [error, setError] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setStatus("loading");
    setError("");

    try {
      const res = await fetch("/api/newsletter", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Something went wrong");
      setStatus("done");
      setEmail("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
      setStatus("error");
    }
  }

  if (status === "done") {
    return (
      <p className="flex items-center gap-2 text-green-400 text-xs font-medium">
        <Check className="w-4 h-4" /> Thanks! You&apos;re on the list.
      </p>
    );
  }

  return (
    <form onSubmit={handleSubmit}>
      <div className="flex gap-2">
        <div className="flex-1 flex items-center bg-white/5 border border-brand-border rounded-lg px-3 gap-2">
          <Mail className="w-4 h-4 text-brand-muted shrink-0" />
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Your email"
            className="bg-transparent text-white text-xs py-2 w-full outline-none placeholder:text-brand-muted/50"
          />
        </div>
      </div>
      <button
        type="submit"
        disabled={status === "loading"}
        className="mt-2 w-full bg-brand-primary hover:bg-purple-600 disabled:opacity-50 text-white text-xs font-semibold py-2 rounded-lg transition-colors"
      >
        {status === "loading" ? "Subscribing..." : "Subscribe"}
      </button>
      {error && <p className="text-red-400 text-xs mt-1.5">{error}</p>}
    </form>
  );
}
