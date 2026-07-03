"use client";
import { useState, useEffect, useRef, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { Shield, Lock, Check, Trophy, MessageCircle, BadgeCheck, AlertTriangle } from "lucide-react";
import Spinner from "@/components/ui/Spinner";
import { PlanId, PLAN_PRICE_EUR, PLAN_REGULAR_PRICE_EUR, PLAN_DURATION_LABEL } from "@/lib/plans";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";
import { saveSubscription } from "@/lib/subscriptionStorage";

const whatsappNumber = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER || "";
const paypalClientId = process.env.NEXT_PUBLIC_PAYPAL_CLIENT_ID || "";

const PLANS: Record<PlanId, { name: string; price: number; duration: string; isPromo: boolean; regularPrice: number | null }> = {
  monthly: {
    name: "Monthly",
    price: PLAN_PRICE_EUR.monthly,
    duration: PLAN_DURATION_LABEL.monthly,
    isPromo: false,
    regularPrice: PLAN_REGULAR_PRICE_EUR.monthly ?? null,
  },
  quarter: {
    name: "3 Months",
    price: PLAN_PRICE_EUR.quarter,
    duration: PLAN_DURATION_LABEL.quarter,
    isPromo: true,
    regularPrice: PLAN_REGULAR_PRICE_EUR.quarter ?? null,
  },
  annual: {
    name: "12 Months",
    price: PLAN_PRICE_EUR.annual,
    duration: PLAN_DURATION_LABEL.annual,
    isPromo: true,
    regularPrice: PLAN_REGULAR_PRICE_EUR.annual ?? null,
  },
};

function CheckoutContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const planId = (searchParams.get("plan") || "annual") as PlanId;
  const plan = PLANS[planId] || PLANS.annual;

  const [form, setForm] = useState({ name: "", email: "" });
  const [formValid, setFormValid] = useState(false);
  const [error, setError] = useState("");
  const [paypalReady, setPaypalReady] = useState(false);
  const paypalContainerRef = useRef<HTMLDivElement>(null);
  const paypalButtonsRendered = useRef(false);
  const formRef = useRef(form);
  formRef.current = form;

  useEffect(() => {
    const nameOk = form.name.trim().length > 0;
    const emailOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim());
    setFormValid(nameOk && emailOk);
  }, [form]);

  useEffect(() => {
    if (!paypalClientId || paypalButtonsRendered.current) return;

    const existingScript = document.querySelector('script[src*="paypal.com/sdk/js"]');
    if (existingScript) {
      if ((window as unknown as Record<string, unknown>).paypal) {
        setPaypalReady(true);
      }
      return;
    }

    const script = document.createElement("script");
    script.src = `https://www.paypal.com/sdk/js?client-id=${paypalClientId}&currency=EUR&intent=capture`;
    script.async = true;
    script.onload = () => setPaypalReady(true);
    script.onerror = () => setError("Failed to load PayPal. Please refresh and try again.");
    document.body.appendChild(script);
  }, []);

  useEffect(() => {
    if (!paypalReady || !paypalContainerRef.current || paypalButtonsRendered.current) return;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const paypal = (window as any).paypal;
    if (!paypal?.Buttons) return;

    paypalButtonsRendered.current = true;

    paypal.Buttons({
      style: {
        layout: "vertical",
        color: "blue",
        shape: "rect",
        label: "pay",
        height: 50,
      },
      createOrder: async () => {
        setError("");
        const f = formRef.current;
        if (!f.name.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim())) {
          throw new Error("Please fill in your name and email first.");
        }
        const res = await fetch("/api/checkout-session", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: "create", plan: planId }),
        });
        const data = await res.json();
        if (!res.ok || !data.orderId) throw new Error(data.error || "Could not create order");
        return data.orderId;
      },
      onApprove: async (data: { orderID: string }) => {
        setError("");
        const f = formRef.current;
        const res = await fetch("/api/checkout-session", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            action: "capture",
            orderId: data.orderID,
            name: f.name.trim(),
            email: f.email.trim(),
            plan: planId,
          }),
        });
        const result = await res.json();
        if (!res.ok || !result.subscription) {
          setError(result.error || "Payment succeeded but activation failed. Contact support.");
          return;
        }
        saveSubscription(result.subscription);
        router.push("/checkout/success");
      },
      onError: (err: Error) => {
        setError(err?.message || "PayPal encountered an error. Please try again.");
      },
    }).render(paypalContainerRef.current);
  }, [paypalReady, planId, router]);

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10">

      {plan.isPromo && (
        <div className="flex items-center gap-3 bg-gradient-to-r from-[#080B16] to-[#0D1F3C] border border-blue-900/40 rounded-xl px-5 py-3 mb-8">
          <Trophy className="w-5 h-5 text-brand-accent shrink-0" />
          <p className="text-blue-100/80 text-sm">
            <strong className="text-brand-accent">FIFA World Cup 2026 Offer</strong> — You&apos;re getting our promotional price. Watch every match live in 4K.
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
        <div className="lg:col-span-2">
          <div className="space-y-6">
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
                Your subscription credentials will be activated automatically once payment is confirmed.
              </p>
            </div>

            <div className="bg-brand-card border border-brand-border rounded-xl p-6">
              <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
                <Lock className="w-5 h-5 text-brand-primary" />
                Payment
              </h2>

              {!formValid && (
                <div className="bg-brand-bg border border-brand-border rounded-lg p-4 flex items-center gap-3 mb-4">
                  <AlertTriangle className="w-5 h-5 text-yellow-400 shrink-0" />
                  <p className="text-brand-muted text-sm">
                    Please enter your <strong className="text-white">name</strong> and <strong className="text-white">email</strong> above before paying.
                  </p>
                </div>
              )}

              <div className="bg-brand-bg border border-brand-border rounded-lg p-4 flex items-center gap-3 mb-5">
                <Shield className="w-5 h-5 text-green-400 shrink-0" />
                <p className="text-brand-muted text-sm">
                  Pay securely with <strong className="text-white">PayPal</strong> or any debit/credit card.
                  We never see or store your payment details.
                </p>
              </div>

              <div
                ref={paypalContainerRef}
                className={`min-h-[60px] transition-opacity ${formValid ? "opacity-100" : "opacity-40 pointer-events-none"}`}
              >
                {!paypalReady && (
                  <div className="flex items-center justify-center py-6">
                    <Spinner />
                  </div>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="bg-brand-bg border border-brand-border rounded-lg p-4 flex items-center gap-3">
                <BadgeCheck className="w-5 h-5 text-green-400 shrink-0" />
                <p className="text-brand-muted text-xs">
                  <a href="/refund-policy" className="text-white font-semibold hover:underline">
                    48-hour refund window
                  </a>{" "}
                  if the service isn&apos;t for you.
                </p>
              </div>
              {whatsappNumber && (
                <a
                  href={`https://wa.me/${whatsappNumber}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="bg-brand-bg border border-brand-border rounded-lg p-4 flex items-center gap-3 hover:border-green-500/40 transition-colors"
                >
                  <MessageCircle className="w-5 h-5 text-green-400 shrink-0" />
                  <p className="text-brand-muted text-xs">
                    Questions before you buy? <span className="text-white font-semibold">Chat with us on WhatsApp</span>
                  </p>
                </a>
              )}
            </div>

            {error && (
              <div className="bg-red-500/10 border border-red-500/30 rounded-lg px-4 py-3 text-red-400 text-sm">
                {error}
              </div>
            )}
          </div>
        </div>

        <div className="lg:col-span-1">
          <div className="bg-brand-card border border-brand-border rounded-xl p-6 sticky top-24">
            <h2 className="text-white font-semibold mb-5">Order Summary</h2>

            {plan.isPromo && (
              <div className="flex items-center gap-2 bg-brand-accent/10 border border-brand-accent/20 rounded-lg px-3 py-2 mb-4">
                <Trophy className="w-4 h-4 text-brand-accent shrink-0" />
                <span className="text-brand-accent text-xs font-bold">World Cup 2026 Promo Price</span>
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
                `${CHANNEL_COUNT_LABEL} channels included`,
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
