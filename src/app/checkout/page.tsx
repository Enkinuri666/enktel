"use client";
import { useState, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { Shield, CreditCard, Check, Loader2 } from "lucide-react";
import Button from "@/components/ui/Button";
import Spinner from "@/components/ui/Spinner";

const PLANS = {
  starter: { name: "Starter", monthlyPrice: 8.99, annualPrice: 7.19 },
  pro: { name: "Pro", monthlyPrice: 14.99, annualPrice: 11.99 },
  ultimate: { name: "Ultimate", monthlyPrice: 24.99, annualPrice: 19.99 },
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
  const planId = (searchParams.get("plan") || "pro") as keyof typeof PLANS;
  const billing = searchParams.get("billing") || "monthly";

  const plan = PLANS[planId] || PLANS.pro;
  const price = billing === "annual" ? plan.annualPrice : plan.monthlyPrice;

  const [form, setForm] = useState({ name: "", email: "", address: "", city: "", postcode: "" });
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
          Welcome to Enktel IPTV! Your subscription is now active. Here are your credentials:
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
              Your credentials will be activated on our streaming servers within a few minutes. You&apos;ll receive a confirmation email shortly.
            </div>
          )}
        </div>
        <p className="text-brand-muted text-sm">
          These details have been sent to your email. Visit your{" "}
          <a href="/dashboard" className="text-brand-primary hover:underline">dashboard</a> to manage your subscription.
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
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
              <h2 className="text-white font-semibold mb-4">Personal Details</h2>
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
                <div className="sm:col-span-2">
                  <label className="block text-brand-muted text-sm mb-1.5">Address</label>
                  <input
                    type="text"
                    value={form.address}
                    onChange={(e) => setForm({ ...form, address: e.target.value })}
                    className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm placeholder:text-brand-muted/50 focus:outline-none focus:border-brand-primary transition-colors"
                    placeholder="123 High Street"
                  />
                </div>
                <div>
                  <label className="block text-brand-muted text-sm mb-1.5">City</label>
                  <input
                    type="text"
                    value={form.city}
                    onChange={(e) => setForm({ ...form, city: e.target.value })}
                    className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm placeholder:text-brand-muted/50 focus:outline-none focus:border-brand-primary transition-colors"
                    placeholder="London"
                  />
                </div>
                <div>
                  <label className="block text-brand-muted text-sm mb-1.5">Postcode</label>
                  <input
                    type="text"
                    value={form.postcode}
                    onChange={(e) => setForm({ ...form, postcode: e.target.value })}
                    className="w-full bg-brand-bg border border-brand-border rounded-lg px-4 py-2.5 text-white text-sm placeholder:text-brand-muted/50 focus:outline-none focus:border-brand-primary transition-colors"
                    placeholder="SW1A 1AA"
                  />
                </div>
              </div>
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
                      <input
                        type="text"
                        disabled
                        placeholder="MM / YY"
                        className="w-full bg-brand-card border border-brand-border rounded-lg px-4 py-2.5 text-brand-muted text-sm placeholder:text-brand-muted/30 cursor-not-allowed"
                      />
                    </div>
                    <div>
                      <label className="block text-brand-muted text-xs mb-1">CVC</label>
                      <input
                        type="text"
                        disabled
                        placeholder="123"
                        className="w-full bg-brand-card border border-brand-border rounded-lg px-4 py-2.5 text-brand-muted text-sm placeholder:text-brand-muted/30 cursor-not-allowed"
                      />
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
                  Subscribe for &pound;{price.toFixed(2)}/month
                </>
              )}
            </Button>
          </form>
        </div>

        {/* Order Summary */}
        <div className="lg:col-span-1">
          <div className="bg-brand-card border border-brand-border rounded-xl p-6 sticky top-24">
            <h2 className="text-white font-semibold mb-5">Order Summary</h2>
            <div className="border border-brand-border rounded-xl p-4 mb-5 bg-brand-primary/5">
              <div className="flex items-center justify-between mb-2">
                <span className="text-white font-bold text-lg">{plan.name} Plan</span>
                <span className="text-brand-primary text-xs border border-brand-primary/30 bg-brand-primary/10 px-2 py-0.5 rounded-full">
                  {billing === "annual" ? "Annual" : "Monthly"}
                </span>
              </div>
              <div className="text-brand-muted text-sm space-y-1">
                <div className="flex justify-between">
                  <span>Price</span>
                  <span className="text-white">&pound;{price.toFixed(2)}/mo</span>
                </div>
                {billing === "annual" && (
                  <div className="flex justify-between">
                    <span>Annual savings</span>
                    <span className="text-green-400">&pound;{((plan.monthlyPrice - plan.annualPrice) * 12).toFixed(2)}</span>
                  </div>
                )}
              </div>
            </div>
            <div className="flex items-center justify-between text-lg font-bold border-t border-brand-border pt-4 mb-5">
              <span className="text-white">Total today</span>
              <span className="text-brand-primary">&pound;{price.toFixed(2)}</span>
            </div>
            <div className="space-y-2.5">
              {[
                "Cancel anytime, no contracts",
                "Instant activation after payment",
                "30-day money-back guarantee",
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
