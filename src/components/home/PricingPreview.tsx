"use client";
import Link from "next/link";
import { Check, Trophy, Flame, Star } from "lucide-react";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import { motion } from "framer-motion";
import { PLAN_PRICE, PLAN_REGULAR_PRICE } from "@/lib/plans";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const plans = [
  {
    id: "monthly",
    name: "Monthly",
    price: PLAN_PRICE.monthly,
    duration: "per month",
    badge: null,
    highlighted: false,
    features: [`${CHANNEL_COUNT_LABEL} Live Channels`, "Croatian & Balkan Channels", "4K Ultra HD Quality", "30-Day Catch-Up TV", "Full VOD Library", "EPG Guide"],
  },
  {
    id: "quarter",
    name: "3 Months",
    price: PLAN_PRICE.quarter,
    duration: "one-time · 3 months",
    badge: "WORLD CUP 2026",
    highlighted: false,
    promoNote: `≈ $${(PLAN_PRICE.quarter / 3).toFixed(2)}/month`,
    features: [`${CHANNEL_COUNT_LABEL} Live Channels`, "Croatian & Balkan Channels", "4K Ultra HD Quality", "30-Day Catch-Up TV", "Full VOD Library", "All World Cup 2026 Matches"],
  },
  {
    id: "annual",
    name: "12 Months",
    price: PLAN_PRICE.annual,
    regularPrice: PLAN_REGULAR_PRICE.annual,
    duration: "one-time · 12 months",
    badge: "BEST VALUE",
    highlighted: true,
    promoNote: `≈ $${(PLAN_PRICE.annual / 12).toFixed(2)}/month — Save $${(PLAN_REGULAR_PRICE.annual ?? PLAN_PRICE.annual) - PLAN_PRICE.annual}`,
    features: [`${CHANNEL_COUNT_LABEL} Live Channels`, "Croatian & Balkan Channels", "4K Ultra HD Quality", "30-Day Catch-Up TV", "Full VOD Library", "All World Cup 2026 Matches", "VIP Support"],
  },
];

export default function PricingPreview() {
  return (
    <section className="py-20 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">

        {/* World Cup promo header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="flex items-center justify-center gap-3 mb-8"
        >
          <Link
            href="/world-cup-2026"
            className="flex items-center gap-3 bg-gradient-to-r from-[#080B16]/90 to-[#0D1F3C]/90 border border-blue-900/40 rounded-full px-6 py-3 hover:border-blue-700/60 transition-colors"
          >
            <span className="text-2xl">🏆</span>
            <span className="text-white text-sm font-bold">FIFA World Cup 2026 — Promotional Pricing Active</span>
            <span className="bg-brand-accent text-white text-xs font-black px-3 py-0.5 rounded-full">SAVE UP TO $90</span>
          </Link>
        </motion.div>

        <div className="text-center mb-12">
          <motion.h2
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-3xl sm:text-5xl font-black text-white mb-4"
          >
            Choose Your{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
              Plan
            </span>
          </motion.h2>
          <p className="text-brand-muted">All features included. No hidden fees. No auto-renewal.</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-10 items-start">
          {plans.map((plan, i) => (
            <motion.div
              key={plan.id}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.1 }}
            >
              {plan.highlighted ? (
                <div className="relative p-[1.5px] rounded-2xl bg-gradient-to-b from-brand-accent/80 via-white/40 to-blue-500/60 shadow-2xl shadow-brand-primary/20">
                  <div className="absolute -top-4 left-1/2 -translate-x-1/2 z-10">
                    <span
                      className="inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-black text-white shadow-lg"
                      style={{ background: "linear-gradient(90deg, #FF4757, #3B82F6)" }}
                    >
                      <Star className="w-3 h-3 fill-current" /> BEST VALUE — SAVE $90
                    </span>
                  </div>
                  <div className="bg-brand-card rounded-[14px] p-6 pt-8">
                    <PlanCardContent plan={plan} />
                  </div>
                </div>
              ) : (
                <div className={`rounded-2xl border p-6 ${plan.badge ? "border-blue-900/40 bg-gradient-to-b from-[#0D1F3C]/30 to-brand-card" : "border-brand-border bg-brand-card"}`}>
                  <PlanCardContent plan={plan} />
                </div>
              )}
            </motion.div>
          ))}
        </div>

        <div className="text-center">
          <Link href="/pricing">
            <Button variant="ghost" size="lg">
              View Full Plan Details &amp; FAQ
            </Button>
          </Link>
        </div>
      </div>
    </section>
  );
}

function PlanCardContent({ plan }: { plan: typeof plans[0] }) {
  return (
    <>
      {plan.badge && (
        <div className="mb-3">
          <Badge variant={plan.badge === "WORLD CUP 2026" ? "accent" : "warning"} size="md">
            {plan.badge === "WORLD CUP 2026" ? <Trophy className="w-3 h-3" /> : <Flame className="w-3 h-3" />}
            {plan.badge === "WORLD CUP 2026" ? "WORLD CUP 2026" : "MOST POPULAR"}
          </Badge>
        </div>
      )}

      <h3 className="text-white font-black text-xl mb-1">{plan.name}</h3>

      <div className="mb-5">
        {"regularPrice" in plan && plan.regularPrice && (
          <div className="text-brand-muted line-through text-sm mb-0.5">&#36;{plan.regularPrice}</div>
        )}
        <div className="flex items-end gap-1">
          <span className="text-5xl font-black text-white leading-none">&#36;{plan.price}</span>
        </div>
        <p className="text-brand-muted text-xs mt-1">{plan.duration}</p>
        {"promoNote" in plan && plan.promoNote && (
          <p className="text-green-400 text-xs mt-0.5 font-semibold">{plan.promoNote}</p>
        )}
      </div>

      <ul className="space-y-2 mb-6">
        {plan.features.map((f) => (
          <li key={f} className="flex items-center gap-2 text-sm text-brand-muted">
            <Check className="w-4 h-4 text-green-400 shrink-0" />
            {f}
          </li>
        ))}
      </ul>

      <Link href={`/checkout?plan=${plan.id}`}>
        <Button variant={plan.highlighted ? "primary" : plan.badge ? "secondary" : "outline"} fullWidth>
          {plan.id === "monthly" ? "Subscribe Monthly" : `Get ${plan.name}`}
        </Button>
      </Link>
    </>
  );
}
