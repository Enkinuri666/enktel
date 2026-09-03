"use client";
import { Suspense, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { Check, Copy, ShieldCheck } from "lucide-react";
import {
  PLANS,
  planById,
  formatPrice,
  perMonth,
  savingPercent,
  CURRENCY,
} from "@/lib/pricing";

interface CaptureResponse {
  paid?: boolean;
  pending?: boolean;
  renewed?: boolean;
  message?: string;
  username?: string;
  password?: string;
  serverUrl?: string;
  m3uUrl?: string;
  epgUrl?: string;
  expiresAt?: string;
}

declare global {
  interface Window {
    paypal?: {
      Buttons: (opts: Record<string, unknown>) => { render: (sel: string) => void };
    };
  }
}

function CheckoutInner() {
  const params = useSearchParams();
  const initial = params.get("plan") ?? "3m";
  const renewFor = params.get("renew") ?? "";

  const [planId, setPlanId] = useState(planById(initial) ? initial : "3m");
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [result, setResult] = useState<CaptureResponse | null>(null);
  const [sdkReady, setSdkReady] = useState(false);
  const [clientId, setClientId] = useState<string | null>(null);

  const plan = useMemo(() => planById(planId) ?? PLANS[1], [planId]);

  // The client id comes from the server rather than being inlined at build
  // time, so a deployment can be given credentials without a rebuild — and so
  // a build with none renders the honest fallback instead of a dead button.
  useEffect(() => {
    fetch("/api/checkout/config")
      .then((r) => r.json())
      .then((j: { clientId?: string }) => setClientId(j.clientId ?? ""))
      .catch(() => setClientId(""));
  }, []);

  useEffect(() => {
    if (!clientId) return;
    const id = "paypal-sdk";
    if (document.getElementById(id)) {
      setSdkReady(true);
      return;
    }
    const s = document.createElement("script");
    s.id = id;
    s.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(clientId)}&currency=${CURRENCY}&intent=capture`;
    s.onload = () => setSdkReady(true);
    s.onerror = () => setError("PayPal could not be loaded.");
    document.body.appendChild(s);
  }, [clientId]);

  useEffect(() => {
    if (!sdkReady || !window.paypal || result) return;
    const host = document.getElementById("paypal-buttons");
    if (!host) return;
    host.innerHTML = "";
    window.paypal
      .Buttons({
        style: { layout: "vertical", shape: "pill", label: "pay" },
        createOrder: async () => {
          const res = await fetch("/api/checkout", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ plan: plan.id, email, renewUsername: renewFor }),
          });
          const json = await res.json();
          if (!res.ok) throw new Error(json.error ?? "Could not start the payment.");
          return json.id;
        },
        onApprove: async (data: { orderID: string }) => {
          const res = await fetch("/api/checkout/capture", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ orderId: data.orderID }),
          });
          const json = (await res.json()) as CaptureResponse & { error?: string };
          if (!res.ok) {
            setError(json.error ?? "Payment could not be completed.");
            return;
          }
          setResult(json);
        },
        onError: () => setError("PayPal reported an error. Nothing has been charged."),
      })
      .render("#paypal-buttons");
  }, [sdkReady, plan, email, renewFor, result]);

  if (result?.paid) {
    return (
      <div className="bg-brand-card border border-brand-primary/50 rounded-2xl p-6 sm:p-8">
        <div className="flex items-center gap-2 text-brand-secondary font-bold mb-3">
          <Check className="w-5 h-5" />
          Payment received
        </div>
        {result.pending || result.renewed || !result.username ? (
          <p className="text-brand-muted text-sm">
            {result.message ??
              "Your line has been extended. It may take a minute to show on your devices."}
          </p>
        ) : (
          <dl className="space-y-3 text-sm">
            {[
              ["Server URL", result.serverUrl],
              ["Username", result.username],
              ["Password", result.password],
              ["M3U playlist", result.m3uUrl],
              ["EPG / XMLTV", result.epgUrl],
            ]
              .filter(([, v]) => Boolean(v))
              .map(([label, value]) => (
                <div key={label as string}>
                  <dt className="text-brand-muted">{label}</dt>
                  <dd className="flex items-center gap-2">
                    <code className="flex-1 break-all rounded bg-black/40 px-2 py-1.5 text-white">
                      {value}
                    </code>
                    <button
                      type="button"
                      aria-label={`Copy ${label}`}
                      onClick={() => navigator.clipboard?.writeText(String(value))}
                      className="rounded p-1.5 text-brand-muted hover:text-white hover:bg-white/10"
                    >
                      <Copy className="w-4 h-4" />
                    </button>
                  </dd>
                </div>
              ))}
          </dl>
        )}
        <Link
          href="/dashboard"
          className="mt-6 inline-block rounded-xl bg-gradient-to-r from-brand-primary to-brand-secondary px-5 py-2.5 text-sm font-bold text-white"
        >
          Go to my dashboard
        </Link>
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_360px] items-start">
      <div className="bg-brand-card border border-brand-border rounded-2xl p-6 sm:p-8">
        <h2 className="text-white font-bold text-lg mb-4">
          {renewFor ? "Add more time" : "Choose your pass"}
        </h2>
        {renewFor && (
          <p className="text-brand-muted text-sm mb-4">
            Extending the line <code className="text-white">{renewFor}</code>. Your
            existing username and password stay the same.
          </p>
        )}
        <div className="space-y-2">
          {PLANS.map((p) => {
            const saving = savingPercent(p);
            return (
              <label
                key={p.id}
                className={`flex cursor-pointer items-center gap-3 rounded-xl border p-4 transition-colors ${
                  p.id === plan.id
                    ? "border-brand-primary bg-brand-primary/10"
                    : "border-brand-border hover:border-brand-primary/50"
                }`}
              >
                <input
                  type="radio"
                  name="plan"
                  value={p.id}
                  checked={p.id === plan.id}
                  onChange={() => setPlanId(p.id)}
                  className="accent-[--color-brand-primary]"
                />
                <span className="flex-1">
                  <span className="block text-white font-semibold">
                    {p.name} — {p.tagline}
                  </span>
                  <span className="block text-brand-muted text-xs">
                    {p.months > 1 ? `${formatPrice(perMonth(p))}/month` : "billed monthly"}
                    {saving > 0 ? ` · save ${saving}%` : ""}
                  </span>
                </span>
                <span className="text-right">
                  <span className="block text-brand-muted text-xs line-through">
                    {formatPrice(p.wasPrice)}
                  </span>
                  <span className="block text-white font-black">{formatPrice(p.price)}</span>
                </span>
              </label>
            );
          })}
        </div>

        <label htmlFor="email" className="mt-6 block text-white font-semibold mb-2">
          Email for your login
        </label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
          className="w-full rounded-xl bg-black/30 border border-brand-border px-4 py-3 text-white placeholder:text-brand-muted/60 focus:border-brand-primary focus:outline-none"
        />
      </div>

      <aside className="bg-brand-card border border-brand-border rounded-2xl p-6 lg:sticky lg:top-24">
        <div className="flex items-baseline justify-between mb-1">
          <span className="text-brand-muted text-sm">{plan.name}</span>
          <span className="text-brand-muted text-sm line-through">
            {formatPrice(plan.wasPrice)}
          </span>
        </div>
        <div className="flex items-baseline gap-2 mb-4">
          <span className="text-white text-3xl font-black">{formatPrice(plan.price)}</span>
          <span className="text-brand-muted text-sm font-semibold">{CURRENCY}</span>
        </div>

        {clientId === null && <p className="text-brand-muted text-sm">Loading checkout…</p>}

        {clientId === "" && (
          <div className="text-sm">
            <p className="text-brand-secondary font-semibold mb-2">
              Card &amp; PayPal checkout is not switched on yet.
            </p>
            <p className="text-brand-muted">
              Message us and we will send a payment link and activate your line by
              hand — usually within a few minutes.
            </p>
            <Link
              href="/contact"
              className="mt-4 block rounded-xl bg-gradient-to-r from-brand-primary to-brand-secondary px-4 py-3 text-center font-bold text-white"
            >
              Contact us to order
            </Link>
          </div>
        )}

        {clientId ? <div id="paypal-buttons" /> : null}

        {error && <p className="mt-3 text-sm text-red-400">{error}</p>}

        <p className="mt-4 flex items-start gap-2 text-xs text-brand-muted">
          <ShieldCheck className="w-4 h-4 shrink-0 mt-0.5" />
          No lock-in contract. Cancel any time — a pass simply stops at the end of
          its term.
        </p>
      </aside>
    </div>
  );
}

export default function CheckoutPage() {
  return (
    <main className="min-h-[70vh] px-4 sm:px-6 lg:px-8 py-16">
      <div className="max-w-5xl mx-auto">
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-8">Checkout</h1>
        <Suspense fallback={<p className="text-brand-muted">Loading…</p>}>
          <CheckoutInner />
        </Suspense>
      </div>
    </main>
  );
}
