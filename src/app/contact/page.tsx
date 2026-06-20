"use client";
import { useState } from "react";
import { Mail, MessageCircle, Check } from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";

const WHATSAPP_NUMBER = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER || "";

export default function ContactPage() {
  const [form, setForm] = useState({ name: "", email: "", message: "" });
  const [status, setStatus] = useState<"idle" | "loading" | "done" | "error">("idle");
  const [error, setError] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setStatus("loading");
    setError("");

    try {
      const res = await fetch("/api/contact", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Something went wrong");
      setStatus("done");
      setForm({ name: "", email: "", message: "" });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
      setStatus("error");
    }
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="text-center mb-10">
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-3">Contact Support</h1>
        <p className="text-brand-muted">
          Questions about setup, billing, or a free trial? We typically respond within a few hours.
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-10">
        {WHATSAPP_NUMBER && (
          <Card className="p-5 flex items-center gap-4">
            <div className="w-10 h-10 rounded-full flex items-center justify-center shrink-0" style={{ background: "#25D366" }}>
              <MessageCircle className="w-5 h-5 text-white" />
            </div>
            <div>
              <p className="text-white font-semibold text-sm">WhatsApp</p>
              <a
                href={`https://wa.me/${WHATSAPP_NUMBER}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-brand-primary text-sm hover:underline"
              >
                Chat with us instantly
              </a>
            </div>
          </Card>
        )}
        <Card className="p-5 flex items-center gap-4">
          <div className="w-10 h-10 bg-brand-primary/10 border border-brand-primary/30 rounded-full flex items-center justify-center shrink-0">
            <Mail className="w-5 h-5 text-brand-primary" />
          </div>
          <div>
            <p className="text-white font-semibold text-sm">Email</p>
            <a href="mailto:support@enktel.tv" className="text-brand-primary text-sm hover:underline">
              support@enktel.tv
            </a>
          </div>
        </Card>
      </div>

      {status === "done" ? (
        <Card className="p-8 text-center">
          <div className="w-14 h-14 bg-green-500/20 border border-green-500/40 rounded-full flex items-center justify-center mx-auto mb-4">
            <Check className="w-7 h-7 text-green-400" />
          </div>
          <h2 className="text-white font-bold text-lg mb-2">Message sent!</h2>
          <p className="text-brand-muted text-sm">We&apos;ll get back to you as soon as possible.</p>
        </Card>
      ) : (
        <Card className="p-6 sm:p-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-white text-sm font-medium mb-1.5">Name</label>
              <input
                type="text"
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="w-full bg-white/5 border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm outline-none focus:border-brand-primary"
              />
            </div>
            <div>
              <label className="block text-white text-sm font-medium mb-1.5">Email</label>
              <input
                type="email"
                required
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                className="w-full bg-white/5 border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm outline-none focus:border-brand-primary"
              />
            </div>
            <div>
              <label className="block text-white text-sm font-medium mb-1.5">Message</label>
              <textarea
                required
                rows={5}
                value={form.message}
                onChange={(e) => setForm({ ...form, message: e.target.value })}
                className="w-full bg-white/5 border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm outline-none focus:border-brand-primary resize-none"
              />
            </div>
            {error && <p className="text-red-400 text-sm">{error}</p>}
            <Button type="submit" loading={status === "loading"} fullWidth size="lg">
              Send Message
            </Button>
          </form>
        </Card>
      )}
    </div>
  );
}
