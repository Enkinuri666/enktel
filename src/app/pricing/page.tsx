"use client";
import { useState } from "react";
import Link from "next/link";
import { Check, ChevronDown, Trophy, Flame, Star } from "lucide-react";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import { motion } from "framer-motion";
import { PLAN_PRICE_EUR, PLAN_REGULAR_PRICE_EUR, PLAN_DURATION_LABEL } from "@/lib/plans";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const trialPlan = {
  id: "trial",
  name: "24-Hour Trial",
  price: 0,
  totalPrice: 0,
  duration: "24 Hours",
  promo: false,
  badge: null as string | null,
  regularPrice: null as number | null,
  saving: null as number | null,
  highlighted: false,
  isTrial: true,
  description: "Full access, no card required. See for yourself before you subscribe.",
  features: [
    `${CHANNEL_COUNT_LABEL} Live Channels`,
    "Croatian & Balkan Channels",
    "4K Ultra HD Quality",
    "Full VOD Library",
    "Instant Activation",
  ],
};

const plans = [
  {
    id: "monthly",
    name: "Monthly",
    price: PLAN_PRICE_EUR.monthly,
    totalPrice: PLAN_PRICE_EUR.monthly,
    duration: PLAN_DURATION_LABEL.monthly,
    promo: false,
    badge: null,
    highlighted: false,
    description: "Flexible month-to-month, cancel anytime.",
    features: [
      `${CHANNEL_COUNT_LABEL} Live Channels`,
      "Croatian & Balkan Channels",
      "4K Ultra HD Quality",
      "30-Day Catch-Up TV",
      "Full VOD Library",
      "Electronic Program Guide",
      "24/7 Support",
    ],
  },
  {
    id: "quarter",
    name: "3 Months",
    price: PLAN_PRICE_EUR.quarter,
    totalPrice: PLAN_PRICE_EUR.quarter,
    duration: PLAN_DURATION_LABEL.quarter,
    promo: true,
    badge: "WORLD CUP 2026",
    regularPrice: PLAN_REGULAR_PRICE_EUR.quarter ?? null,
    saving: null,
    highlighted: false,
    description: "Stream every World Cup 2026 match live — start to finish.",
    features: [
      `${CHANNEL_COUNT_LABEL} Live Channels`,
      "Croatian & Balkan Channels",
      "4K Ultra HD Quality",
      "30-Day Catch-Up TV",
      "Full VOD Library",
      "Electronic Program Guide",
      "All World Cup 2026 Matches",
      "24/7 Priority Support",
    ],
  },
  {
    id: "annual",
    name: "12 Months",
    price: PLAN_PRICE_EUR.annual,
    totalPrice: PLAN_PRICE_EUR.annual,
    duration: PLAN_DURATION_LABEL.annual,
    promo: true,
    badge: "BEST VALUE",
    regularPrice: PLAN_REGULAR_PRICE_EUR.annual ?? null,
    saving: (PLAN_REGULAR_PRICE_EUR.annual ?? PLAN_PRICE_EUR.annual) - PLAN_PRICE_EUR.annual,
    highlighted: true,
    description: "Full year access. Watch it all — World Cup and beyond.",
    features: [
      `${CHANNEL_COUNT_LABEL} Live Channels`,
      "Croatian & Balkan Channels",
      "4K Ultra HD Quality",
      "30-Day Catch-Up TV",
      "Full VOD Library",
      "Electronic Program Guide",
      "All World Cup 2026 Matches",
      "Adult Content (optional)",
      "Fastest Servers",
      "24/7 VIP Support",
    ],
  },
];

const faqs = [
  {
    q: "What devices does Enktel IPTV support?",
    a: "Enktel IPTV works on Smart TVs (Samsung, LG, Sony), Amazon Firestick, MAG boxes, Android/iOS phones and tablets, Windows/Mac computers, and more. Any device that supports IPTV players like TiviMate, IPTV Smarters, or VLC.",
  },
  {
    q: "How do I receive my subscription details?",
    a: "After subscribing, you'll receive your M3U playlist URL and EPG URL instantly. You can also find them in your dashboard at any time.",
  },
  {
    q: "Can I trial Enktel IPTV before purchasing?",
    a: "Yes! Start a free 24-hour trial — no card required — and get instant access to your stream credentials. Visit the Free Trial page to sign up in seconds.",
  },
  {
    q: "Will I get all FIFA World Cup 2026 matches?",
    a: "Yes. All World Cup 2026 matches will be available live across multiple broadcast channels. Our 3-Month and 12-Month plans cover the entire tournament period.",
  },
  {
    q: "Which Croatian channels are included?",
    a: "We include HRT 1, HRT 2, HRT 3, HRT 4, Nova TV, RTL Hrvatska, RTL 2, Doma TV, CMC TV, N1 Info, 24sata TV, and many more Croatian and Balkan channels.",
  },
  {
    q: "Can I cancel my subscription?",
    a: "Monthly subscriptions can be cancelled at any time. 3-month and 12-month plans are one-time payments — no automatic renewal.",
  },
  {
    q: "What payment methods do you accept?",
    a: "We accept all major credit/debit cards via Stripe, as well as PayPal and cryptocurrency.",
  },
];

export default function PricingPage() {
  const [openFaq, setOpenFaq] = useState<number | null>(null);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">

      {/* World Cup Promo Banner */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="relative overflow-hidden rounded-2xl mb-12 bg-gradient-to-r from-[#0a3a1e] via-[#0d5c2b] to-[#0a3a1e] border border-green-700/40"
      >
        <div className="absolute inset-0 opacity-10" style={{ backgroundImage: "radial-gradient(circle at 20% 50%, #22c55e 0%, transparent 50%), radial-gradient(circle at 80% 50%, #fbbf24 0%, transparent 50%)" }} />
        <div className="relative z-10 flex flex-col sm:flex-row items-center gap-4 px-6 py-5">
          <div className="flex items-center gap-3 shrink-0">
            <span className="text-4xl">🏆</span>
            <div>
              <p className="text-yellow-400 font-black text-sm uppercase tracking-widest">FIFA World Cup 2026</p>
              <p className="text-white font-bold text-lg">Limited Time Promotional Pricing</p>
            </div>
          </div>
          <div className="h-px sm:h-10 w-full sm:w-px bg-green-700/50 shrink-0" />
          <p className="text-green-200 text-sm text-center sm:text-left">
            Celebrate the World Cup with our biggest ever discount. Watch every match live in 4K — plus {CHANNEL_COUNT_LABEL} channels all year round.
          </p>
          <div className="shrink-0">
            <span className="bg-yellow-400 text-black text-xs font-black px-4 py-2 rounded-full uppercase tracking-wide whitespace-nowrap">
              Save up to €90
            </span>
          </div>
        </div>
      </motion.div>

      {/* Header */}
      <div className="text-center mb-12">
        <h1 className="text-4xl sm:text-5xl font-black text-white mb-4">
          Choose Your{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Plan
          </span>
        </h1>
        <p className="text-brand-muted text-xl">
          All plans include every feature. Pay once, stream everything.
        </p>
        <Link href="/trial" className="inline-flex items-center gap-1.5 text-brand-secondary text-sm font-semibold hover:underline mt-3">
          Not sure yet? Try Enktel free for 24 hours →
        </Link>
      </div>

      {/* Trial option */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="relative rounded-2xl border border-brand-secondary/40 bg-gradient-to-r from-brand-secondary/10 to-brand-card p-7 mb-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-5"
      >
        <div>
          <div className="flex items-center gap-2 mb-2">
            <Badge variant="default" size="sm">100% FREE</Badge>
            <h2 className="text-white font-black text-xl">{trialPlan.name}</h2>
          </div>
          <p className="text-brand-muted text-sm max-w-md">{trialPlan.description}</p>
        </div>
        <Link href="/trial" className="shrink-0 w-full sm:w-auto">
          <Button variant="secondary" size="lg" fullWidth>
            Start My Free Trial
          </Button>
        </Link>
      </motion.div>

      {/* Plans */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-16 items-start">
        {plans.map((plan, i) => (
          <motion.div
            key={plan.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.1 }}
          >
            {plan.highlighted ? (
              <div className="relative p-[1.5px] rounded-2xl bg-gradient-to-b from-yellow-400/80 via-brand-primary/60 to-brand-secondary/60 shadow-2xl shadow-brand-primary/20">
                <div className="absolute -top-4 left-1/2 -translate-x-1/2 z-10">
                  <Badge variant="gold" className="px-5 py-1.5 shadow-lg shadow-yellow-400/30">
                    <Star className="w-3.5 h-3.5 fill-current" /> BEST VALUE — SAVE €90
                  </Badge>
                </div>
                <div className="bg-brand-card rounded-[14px] p-7">
                  <PlanCard plan={plan} />
                </div>
              </div>
            ) : (
              <div className={`relative rounded-2xl border p-7 ${plan.promo ? "border-green-700/50 bg-gradient-to-b from-green-950/30 to-brand-card" : "border-brand-border bg-brand-card"}`}>
                <PlanCard plan={plan} />
              </div>
            )}
          </motion.div>
        ))}
      </div>

      {/* Quick comparison strip */}
      <div className="grid grid-cols-3 divide-x divide-brand-border bg-brand-card border border-brand-border rounded-2xl mb-16 overflow-hidden text-center">
        {plans.map((plan) => (
          <div key={plan.id} className="px-3 py-5 sm:px-6">
            <p className="text-brand-muted text-xs uppercase tracking-wide mb-1">{plan.name}</p>
            <p className="text-white font-black text-lg sm:text-xl mb-1">€{plan.price}</p>
            <p className="text-brand-muted text-xs mb-2">{plan.duration}</p>
            {plan.id === "monthly" ? (
              <Badge variant="default" size="sm">No World Cup</Badge>
            ) : (
              <Badge variant="warning" size="sm">
                <Trophy className="w-3 h-3" /> World Cup
              </Badge>
            )}
          </div>
        ))}
      </div>

      {/* What's included */}
      <div className="bg-brand-card border border-brand-border rounded-2xl p-8 mb-16">
        <h2 className="text-2xl font-black text-white mb-2 text-center">Everything Included in Every Plan</h2>
        <p className="text-brand-muted text-center mb-8">No feature is locked behind a higher tier. Every plan gets the full Enktel experience.</p>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[
            { emoji: "🇭🇷", text: "Croatian & Balkan Channels" },
            { emoji: "⚽", text: "All FIFA World Cup 2026 Matches" },
            { emoji: "📺", text: `${CHANNEL_COUNT_LABEL} Live Channels` },
            { emoji: "🎬", text: "4K Ultra HD Streaming" },
            { emoji: "⏪", text: "30-Day Catch-Up TV" },
            { emoji: "🎥", text: "Full VOD Movie & Series Library" },
            { emoji: "📅", text: "Electronic Program Guide (EPG)" },
            { emoji: "🌍", text: "50+ Countries' Channels" },
            { emoji: "📱", text: "Works on All Devices" },
          ].map((item) => (
            <div key={item.text} className="flex items-center gap-3 bg-brand-bg border border-brand-border rounded-xl px-4 py-3">
              <span className="text-xl">{item.emoji}</span>
              <span className="text-white text-sm font-medium">{item.text}</span>
            </div>
          ))}
        </div>
      </div>

      {/* FAQ */}
      <div className="max-w-3xl mx-auto">
        <h2 className="text-2xl font-black text-white mb-6 text-center">Frequently Asked Questions</h2>
        <div className="space-y-3">
          {faqs.map((faq, i) => (
            <div key={i} className="bg-brand-card border border-brand-border rounded-xl overflow-hidden">
              <button
                onClick={() => setOpenFaq(openFaq === i ? null : i)}
                className="w-full flex items-center justify-between px-5 py-4 text-left"
              >
                <span className="text-white font-medium text-sm pr-4">{faq.q}</span>
                <ChevronDown className={`w-4 h-4 text-brand-muted shrink-0 transition-transform ${openFaq === i ? "rotate-180" : ""}`} />
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

function PlanCard({ plan }: { plan: typeof plans[0] }) {
  return (
    <>
      {/* Badge row */}
      <div className="flex items-center gap-2 mb-3 min-h-[28px]">
        {plan.badge === "WORLD CUP 2026" && (
          <Badge variant="warning" size="md">
            <Trophy className="w-3 h-3" /> WORLD CUP 2026
          </Badge>
        )}
        {plan.badge === "BEST VALUE" && (
          <Badge variant="warning" size="md">
            <Flame className="w-3 h-3" /> MOST POPULAR
          </Badge>
        )}
      </div>

      <h2 className="text-white font-black text-2xl mb-1">{plan.name}</h2>
      <p className="text-brand-muted text-sm mb-5">{plan.description}</p>

      {/* Price */}
      <div className="mb-6">
        {plan.regularPrice && (
          <div className="flex items-center gap-2 mb-1">
            <span className="text-brand-muted line-through text-lg">€{plan.regularPrice}</span>
            <span className="bg-green-500/20 text-green-400 text-xs font-bold px-2 py-0.5 rounded-full">SAVE €{plan.saving}</span>
          </div>
        )}
        <div className="flex items-end gap-1.5">
          <span className="text-6xl font-black text-white leading-none">€{plan.price}</span>
        </div>
        <p className="text-brand-muted text-sm mt-1">
          {plan.id === "monthly" ? "per month" : `one-time · ${plan.duration}`}
        </p>
        {plan.id === "quarter" && (
          <p className="text-green-400 text-xs mt-1">≈ €19.67/month</p>
        )}
        {plan.id === "annual" && (
          <p className="text-green-400 text-xs mt-1">≈ €8.25/month</p>
        )}
      </div>

      <ul className="space-y-2.5 mb-8">
        {plan.features.map((f) => (
          <li key={f} className="flex items-start gap-2.5 text-sm">
            <Check className="w-4 h-4 text-green-400 shrink-0 mt-0.5" />
            <span className="text-brand-muted">{f}</span>
          </li>
        ))}
      </ul>

      <Link href={`/checkout?plan=${plan.id}`}>
        <Button
          variant={plan.highlighted ? "primary" : plan.promo ? "secondary" : "outline"}
          size="lg"
          fullWidth
        >
          {plan.id === "monthly" ? "Subscribe Monthly" : `Get ${plan.name} — €${plan.price}`}
        </Button>
      </Link>
    </>
  );
}
