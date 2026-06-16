"use client";
import { useState, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { Shield, CreditCard, Check, Loader2, Trophy } from "lucide-react";
import Button from "@/components/ui/Button";
import Spinner from "@/components/ui/Spinner";

const PLANS = {
  monthly: {
    name: "Monthly",
    price: 19.99,
    duration: "1 month",
    isPromo: false,
    regularPrice: null as number | null,
  },
  quarter: {
    name: "3 Months",
    price: 59,
    duration: "3 months",
    isPromo: true,
    regularPrice: null as number | null,
  },
  annual: {
    name: "12 Months",
    price: 99,
    duration: "12 months",
    isPromo: true,
    regularPrice: 189 as number | null,
  },
};

interface SubscriptionResult {
  id: string;
  username: string;
  password: string;
  m3uUrl: string;
  epgUrl: string;
  panelSync: boolean;
}

function CheckoutContent() {
  const searchParams = useSearchParams();
  const planId = (searchParams.get("plan") || "annual") as keyof typeof PLANS;
  const plan = PLANS[planId] || PLANS.annual;

  const [form, setForm] = useState({ name: "", email: "" });
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<SubscriptionResult | null>(null);
  const [error, setError] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const res = await fetch("/api/subscribe", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: form.name, email: form.email, plan: planId }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Something went wrong");
      setSuccess(data.subscription);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }

  if (success) {
    return (
      <div className="max-w-lg mx-auto px-4 py-20 text-center">
        <div className="w-20 h-20 bg-green-500/20 border border-green-500/40 rounded-full flex items-center justify-center mx-auto mb-6">
          <Check className="w-10 h-10 text-green-400" />
        </div>
        <h1 className="text-3xl font-bold text-white mb-3">Subscription Active!</h1>
        <p className="text-brand-muted mb-8">
          Welcome to Enktel IPTV! Your {plan.name} subscription is now live. Here are your credentials:
        </p>
        <div className="bg-brand-card border border-brand-border rounded-xl p-5 text-left space-y-4 mb-8">
          <div>
            <p className="text-brand-muted text-xs mb-1">Subscription ID</p>
            <p className="text-white font-mono text-sm">{success.id}</p>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <p className="text-brand-muted text-xs mb-1">Username</p>
              <p className="text-white font-mono text-sm font-bold">{success.username}</p>
            </div>
            <div>
              <p className="text-brand-muted text-xs mb-1">Password</p>
              <p className="text-white font-mono text-sm font-bold">{success.password}</p>
            </div>
          </div>
          <div>
            <p className="text-brand-muted text-xs mb-1">M3U Playlist URL</p>
            <p className="text-brand-primary font-mono text-xs break-all">{success.m3uUrl}</p>
          </div>
          <div>
            <p className="text-brand-muted text-xs mb-1">EPG / XML TV URL</p>
            <p className="text-brand-primary font-mono text-xs break-all">{success.epgUrl}</p>
          </div>
          {!success.panelSync && (
            <div className="bg-yellow-500/10 border border-yellow-500/30 rounded-lg px-4 py-3 text-yellow-400 text-xs">
              Your credentials will be activated on our streaming servers within a few minutes.
            </div>
          )}
        </div>
        <p className="text-brand-muted text-sm">
          Visit your{" "}
          <a href="/dashboard" className="text-brand-primary hover:underline">dashboard</a>{" "}
          to manage your subscription and access setup guides.
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10">

      {/* World Cup promo strip */}
      {plan.isPromo && (
        <div className="flex items-center gap-3 bg-gradient-to-r from-[#0a3a1e] to-[#0d5c2b] border border-green-700/40 rounded-xl px-5 py-3 mb-8">
          <Trophy className="w-5 h-5 text-yellow-400 shrink-0" />
          <p className="text-green-200 text-sm">
            <strong className="text-yellow-400">FIFA World Cup 2026 Offer</strong> — You&apos;re getting our promotional price. Watch every match live in 4K.
          </p>
        </div>
      )}

      <h1 className="text-3xl font-bold text-white mb-8">
        Complete Your{" "}
        <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
          Order
        </span>
      </h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Form */}
        <div className="lg:col-span-2">
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Personal Details */}
            <div className="bg-brand-card border border-brand-border rounded-xl p-6">
              <h2 className="text-white font-semibold mb-4">Your Details</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
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
                </div>
              </div>
              <p className="text-brand-muted/60 text-xs mt-3">
                Your subscription credentials will be sent to this email address.
              </p>
            </div>

            {/* Payment */}
            <div className="bg-brand-card border border-brand-border rounded-xl p-6">
              <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
                <CreditCard className="w-5 h-5 text-brand-primary" />
                Payment Details
              </h2>
              <div className="bg-brand-bg border border-brand-border rounded-lg p-4 mb-4">
                <div className="flex items-center gap-3 mb-3">
                  <Shield className="w-5 h-5 text-green-400" />
                  <span className="text-green-400 text-sm font-medium">Secure payment powered by Stripe</span>
                </div>
                <div className="space-y-3">
                  <div>
                    <label className="block text-brand-muted text-xs mb-1">Card Number</label>
                    <input
                      type="text"
                      disabled
                      placeholder="4242 4242 4242 4242"
                      className="w-full bg-brand-card border border-brand-border rounded-lg px-4 py-2.5 text-brand-muted text-sm placeholder:text-brand-muted/30 cursor-not-allowed"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-brand-muted text-xs mb-1">Expiry</label>
                      <input type="text" disabled placeholder="MM / YY" className="w-full bg-brand-card border border-brand-border rounded-lg px-4 py-2.5 text-brand-muted text-sm placeholder:text-brand-muted/30 cursor-not-allowed" />
                    </div>
                    <div>
                      <label className="block text-brand-muted text-xs mb-1">CVC</label>
                      <input type="text" disabled placeholder="123" className="w-full bg-brand-card border border-brand-border rounded-lg px-4 py-2.5 text-brand-muted text-sm placeholder:text-brand-muted/30 cursor-not-allowed" />
                    </div>
                  </div>
                  <p className="text-brand-muted/50 text-xs text-center">
                    Stripe integration ready — connect your Stripe keys to enable live payments
                  </p>
                </div>
              </div>
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
                  Processing...
                </>
              ) : (
                <>
                  <Shield className="w-4 h-4 mr-2" />
                  Pay &euro;{plan.price} &mdash; {plan.name}
                </>
              )}
            </Button>
          </form>
        </div>

        {/* Order Summary */}
        <div className="lg:col-span-1">
          <div className="bg-brand-card border border-brand-border rounded-xl p-6 sticky top-24">
            <h2 className="text-white font-semibold mb-5">Order Summary</h2>

            {plan.isPromo && (
              <div className="flex items-center gap-2 bg-yellow-400/10 border border-yellow-400/20 rounded-lg px-3 py-2 mb-4">
                <Trophy className="w-4 h-4 text-yellow-400 shrink-0" />
                <span className="text-yellow-400 text-xs font-bold">World Cup 2026 Promo Price</span>
              </div>
            )}

            <div className="border border-brand-border rounded-xl p-4 mb-5 bg-brand-primary/5">
              <div className="flex items-center justify-between mb-3">
                <span className="text-white font-bold text-lg">{plan.name} Plan</span>
                <span className="text-brand-primary text-xs border border-brand-primary/30 bg-brand-primary/10 px-2 py-0.5 rounded-full">
                  {plan.duration}
                </span>
              </div>
              <div className="text-brand-muted text-sm space-y-1">
                {plan.regularPrice && (
                  <div className="flex justify-between">
                    <span>Regular price</span>
                    <span className="line-through text-brand-muted/60">&euro;{plan.regularPrice}</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span>Your price</span>
                  <span className="text-white font-bold">&euro;{plan.price}</span>
                </div>
                {plan.regularPrice && (
                  <div className="flex justify-between">
                    <span>You save</span>
                    <span className="text-green-400 font-bold">&euro;{plan.regularPrice - plan.price}</span>
                  </div>
                )}
              </div>
            </div>

            <div className="flex items-center justify-between text-xl font-black border-t border-brand-border pt-4 mb-5">
              <span className="text-white">Total today</span>
              <span className="text-brand-primary">&euro;{plan.price}</span>
            </div>

            <div className="space-y-2.5">
              {[
                "Instant activation after payment",
                "Credentials sent by email",
                "10,000+ channels included",
                "All Croatian & Balkan channels",
                "World Cup 2026 matches live",
                "24/7 customer support",
              ].map((item) => (
                <div key={item} className="flex items-center gap-2 text-xs text-brand-muted">
                  <Check className="w-3.5 h-3.5 text-green-400 shrink-0" />
                  {item}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function CheckoutPage() {
  return (
    <Suspense fallback={<Spinner className="py-20" />}>
      <CheckoutContent />
    </Suspense>
  );
}
