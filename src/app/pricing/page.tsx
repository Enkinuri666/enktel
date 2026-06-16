"use client";
import { useState } from "react";
import Link from "next/link";
import { Check, X, Zap, ChevronDown } from "lucide-react";
import Button from "@/components/ui/Button";
import { motion } from "framer-motion";

const plans = [
  {
    id: "starter",
    name: "Starter",
    price: 8.99,
    annualPrice: 7.19,
    connections: 1,
    channels: "5,000+",
    quality: "Full HD 1080p",
    catchUp: "7 Days",
    vod: true,
    prioritySupport: false,
    features: [
      "5,000+ Live Channels",
      "Full HD 1080p Quality",
      "7-Day Catch-Up TV",
      "VOD Library Access",
      "Electronic Program Guide",
      "Email Support",
    ],
    notIncluded: ["4K Ultra HD", "Priority Support", "Sports Package"],
    highlighted: false,
    color: "brand-primary",
  },
  {
    id: "pro",
    name: "Pro",
    price: 14.99,
    annualPrice: 11.99,
    connections: 1,
    channels: "10,000+",
    quality: "4K Ultra HD",
    catchUp: "14 Days",
    vod: true,
    prioritySupport: true,
    features: [
      "10,000+ Live Channels",
      "4K Ultra HD Quality",
      "14-Day Catch-Up TV",
      "Full VOD Library",
      "Electronic Program Guide",
      "Priority Support",
      "Sports Package Included",
    ],
    notIncluded: ["30-Day Catch-Up"],
    highlighted: true,
    color: "brand-primary",
  },
  {
    id: "ultimate",
    name: "Ultimate",
    price: 24.99,
    annualPrice: 19.99,
    connections: 1,
    channels: "10,000+",
    quality: "4K Ultra HD",
    catchUp: "30 Days",
    vod: true,
    prioritySupport: true,
    features: [
      "10,000+ Live Channels",
      "4K Ultra HD Quality",
      "30-Day Catch-Up TV",
      "Full VOD Library",
      "Electronic Program Guide",
      "Priority VIP Support",
      "Sports Package Included",
      "Adult Content (optional)",
      "Fastest Servers",
    ],
    notIncluded: [],
    highlighted: false,
    color: "brand-secondary",
  },
];

const faqs = [
  {
    q: "What devices does Enktel IPTV support?",
    a: "Enktel IPTV works on Smart TVs (Samsung, LG, Sony), Amazon Firestick, MAG boxes, Android/iOS phones and tablets, Windows/Mac computers, and more. Any device that supports IPTV players like TiviMate, IPTV Smarters, or VLC.",
  },
  {
    q: "How do I receive my subscription details?",
    a: "After subscribing, you'll receive your M3U playlist URL and EPG URL via email within minutes. You can also find them in your dashboard.",
  },
  {
    q: "Can I trial Enktel IPTV before purchasing?",
    a: "Yes! Contact our support team for a 24-hour free trial. We're confident you'll love the quality.",
  },
  {
    q: "What is catch-up TV?",
    a: "Catch-up TV lets you rewind and watch programmes that aired in the past. With the Pro plan you get 14 days catch-up, and Ultimate gives you 30 days.",
  },
  {
    q: "Can I cancel my subscription?",
    a: "Yes, you can cancel at any time. Your subscription remains active until the end of the billing period.",
  },
  {
    q: "What payment methods do you accept?",
    a: "We accept all major credit/debit cards via Stripe, as well as PayPal and cryptocurrency.",
  },
];

export default function PricingPage() {
  const [annual, setAnnual] = useState(false);
  const [openFaq, setOpenFaq] = useState<number | null>(null);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      {/* Header */}
      <div className="text-center mb-12">
        <h1 className="text-4xl sm:text-5xl font-bold text-white mb-4">
          Simple,{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Transparent
          </span>{" "}
          Pricing
        </h1>
        <p className="text-brand-muted text-xl mb-8">
          No hidden fees. No contracts. Cancel anytime.
        </p>
        {/* Annual toggle */}
        <div className="inline-flex items-center gap-3 bg-brand-card border border-brand-border rounded-full px-5 py-3">
          <span className={`text-sm font-medium ${!annual ? "text-white" : "text-brand-muted"}`}>Monthly</span>
          <button
            onClick={() => setAnnual(!annual)}
            className={`relative w-12 h-6 rounded-full transition-colors ${annual ? "bg-brand-primary" : "bg-brand-border"}`}
          >
            <span
              className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-all ${annual ? "left-7" : "left-1"}`}
            />
          </button>
          <span className={`text-sm font-medium ${annual ? "text-white" : "text-brand-muted"}`}>
            Annual <span className="text-green-400 font-bold">(Save 20%)</span>
          </span>
        </div>
      </div>

      {/* Plans */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-16">
        {plans.map((plan, i) => (
          <motion.div
            key={plan.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.1 }}
            className={`relative rounded-2xl border p-7 ${
              plan.highlighted
                ? "border-brand-primary bg-gradient-to-b from-brand-primary/15 to-brand-card shadow-2xl shadow-brand-primary/20 scale-105"
                : "border-brand-border bg-brand-card"
            }`}
          >
            {plan.highlighted && (
              <div className="absolute -top-4 left-1/2 -translate-x-1/2">
                <span className="bg-brand-primary text-white text-sm font-bold px-5 py-1.5 rounded-full flex items-center gap-1.5">
                  <Zap className="w-3.5 h-3.5 fill-current" /> Most Popular
                </span>
              </div>
            )}

            <h2 className="text-white font-bold text-2xl mb-1">{plan.name}</h2>
            <p className="text-brand-muted text-sm mb-4">
              1 device &bull; {plan.quality}
            </p>

            <div className="mb-6">
              <div className="flex items-end gap-1">
                <span className="text-5xl font-bold text-white">
                  &pound;{annual ? plan.annualPrice.toFixed(2) : plan.price.toFixed(2)}
                </span>
                <span className="text-brand-muted mb-1">/mo</span>
              </div>
              {annual && (
                <p className="text-green-400 text-sm mt-1">
                  Saving &pound;{((plan.price - plan.annualPrice) * 12).toFixed(2)}/year
                </p>
              )}
            </div>

            <ul className="space-y-3 mb-8">
              {plan.features.map((f) => (
                <li key={f} className="flex items-start gap-2.5 text-sm">
                  <Check className="w-4 h-4 text-green-400 shrink-0 mt-0.5" />
                  <span className="text-brand-muted">{f}</span>
                </li>
              ))}
              {plan.notIncluded.map((f) => (
                <li key={f} className="flex items-start gap-2.5 text-sm">
                  <X className="w-4 h-4 text-brand-border shrink-0 mt-0.5" />
                  <span className="text-brand-border">{f}</span>
                </li>
              ))}
            </ul>

            <Link href={`/checkout?plan=${plan.id}&billing=${annual ? "annual" : "monthly"}`}>
              <Button
                variant={plan.highlighted ? "primary" : "outline"}
                size="lg"
                fullWidth
              >
                Get {plan.name}
              </Button>
            </Link>
          </motion.div>
        ))}
      </div>

      {/* Feature comparison table */}
      <div className="mb-16 overflow-x-auto">
        <h2 className="text-2xl font-bold text-white mb-6 text-center">Full Feature Comparison</h2>
        <table className="w-full bg-brand-card border border-brand-border rounded-xl overflow-hidden">
          <thead>
            <tr className="border-b border-brand-border">
              <th className="text-left px-6 py-4 text-brand-muted text-sm font-medium">Feature</th>
              {plans.map((p) => (
                <th key={p.id} className={`px-6 py-4 text-sm font-bold ${p.highlighted ? "text-brand-primary" : "text-white"}`}>
                  {p.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {[
              { label: "Channels", values: ["5,000+", "10,000+", "10,000+"] },
              { label: "Stream Quality", values: ["Full HD", "4K UHD", "4K UHD"] },
              { label: "Catch-Up TV", values: ["7 Days", "14 Days", "30 Days"] },
              { label: "VOD Library", values: ["✓", "✓", "✓"] },
              { label: "EPG Guide", values: ["✓", "✓", "✓"] },
              { label: "Sports Package", values: ["✗", "✓", "✓"] },
              { label: "Priority Support", values: ["✗", "✓", "✓"] },
              { label: "VIP Support", values: ["✗", "✗", "✓"] },
              { label: "Adult Content", values: ["✗", "✗", "✓"] },
            ].map((row) => (
              <tr key={row.label} className="border-b border-brand-border/50 hover:bg-white/2 transition-colors">
                <td className="px-6 py-3.5 text-brand-muted text-sm">{row.label}</td>
                {row.values.map((v, i) => (
                  <td key={i} className={`px-6 py-3.5 text-center text-sm ${v === "✓" ? "text-green-400" : v === "✗" ? "text-brand-border" : "text-white"}`}>
                    {v}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* FAQ */}
      <div className="max-w-3xl mx-auto">
        <h2 className="text-2xl font-bold text-white mb-6 text-center">Frequently Asked Questions</h2>
        <div className="space-y-3">
          {faqs.map((faq, i) => (
            <div key={i} className="bg-brand-card border border-brand-border rounded-xl overflow-hidden">
              <button
                onClick={() => setOpenFaq(openFaq === i ? null : i)}
                className="w-full flex items-center justify-between px-5 py-4 text-left"
              >
                <span className="text-white font-medium text-sm pr-4">{faq.q}</span>
                <ChevronDown
                  className={`w-4 h-4 text-brand-muted shrink-0 transition-transform ${openFaq === i ? "rotate-180" : ""}`}
                />
              </button>
              {openFaq === i && (
                <div className="px-5 pb-4 text-brand-muted text-sm leading-relaxed border-t border-brand-border pt-3">
                  {faq.a}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
