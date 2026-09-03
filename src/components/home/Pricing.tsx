"use client";
import { motion } from "framer-motion";
import Link from "next/link";
import { Check, Zap } from "lucide-react";
import {
  PLANS,
  INCLUDED_IN_EVERY_PLAN,
  CURRENCY,
  formatPrice,
  perMonth,
  savingPercent,
  monthlyEquivalent,
  TRIAL_HOURS,
} from "@/lib/pricing";

/**
 * The plan cards.
 *
 * Every figure on screen comes from `@/lib/pricing` — including the per-month
 * and saving lines, which are computed there rather than written into the
 * copy. A price advertised here that disagrees with the one charged at
 * checkout is the worst bug this page can have, so there is only one place to
 * change a number.
 */
export default function Pricing() {
  return (
    <section id="pricing" className="py-20 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-14">
          <motion.p
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true }}
            className="text-brand-primary text-sm font-bold uppercase tracking-widest mb-3"
          >
            Transparent, Unbeatable Pricing
          </motion.p>
          <motion.h2
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-3xl sm:text-5xl font-black text-white mb-4"
          >
            Choose Your{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
              Enktel Access Pass
            </span>
          </motion.h2>
          <motion.p
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ delay: 0.1 }}
            className="text-brand-muted text-lg max-w-2xl mx-auto"
          >
            No lock-in contracts, no hidden installation fees, and no mid-year price
            hikes. Cancel anytime. All prices in {CURRENCY}.
          </motion.p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 items-start">
          {PLANS.map((plan, i) => {
            const saving = savingPercent(plan);
            const featured = plan.highlight !== undefined;
            return (
              <motion.div
                key={plan.id}
                initial={{ opacity: 0, y: 30 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.07 }}
                className={`relative bg-brand-card border rounded-2xl p-6 flex flex-col transition-all duration-300 hover:-translate-y-1 ${
                  featured
                    ? "border-brand-primary/60 shadow-xl shadow-brand-primary/10"
                    : "border-brand-border hover:border-brand-primary/50"
                }`}
              >
                {plan.highlight && (
                  <span className="absolute -top-3 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full bg-gradient-to-r from-brand-primary to-brand-secondary px-3 py-1 text-[11px] font-black uppercase tracking-wider text-white">
                    {plan.highlight === "popular" ? "Most Popular" : "Best Value"}
                  </span>
                )}

                <h3 className="text-white font-bold text-lg">{plan.name}</h3>
                <p className="text-brand-primary text-sm font-semibold mb-4">
                  {plan.tagline}
                </p>

                <div className="flex items-baseline gap-2 flex-wrap">
                  <span className="text-brand-muted line-through text-base">
                    {formatPrice(plan.wasPrice)}
                  </span>
                  <span className="text-white text-4xl font-black">
                    {formatPrice(plan.price)}
                  </span>
                  <span className="text-brand-muted text-sm font-semibold">
                    {CURRENCY}
                  </span>
                </div>

                {plan.months > 1 && (
                  <p className="text-brand-secondary text-sm font-semibold mt-1">
                    Just {formatPrice(perMonth(plan))}/month
                  </p>
                )}
                {saving > 0 && (
                  <p className="text-brand-muted text-xs mt-1">
                    Save {saving}% vs {formatPrice(monthlyEquivalent(plan))} paying
                    monthly
                  </p>
                )}

                <p className="text-brand-muted text-sm leading-relaxed mt-4">
                  {plan.blurb}
                </p>

                <ul className="mt-4 space-y-2 flex-1">
                  {(plan.months === 1
                    ? INCLUDED_IN_EVERY_PLAN
                    : ["Everything in the 1-Month Pass", ...plan.perks]
                  ).map((perk) => (
                    <li key={perk} className="flex gap-2 text-sm text-brand-muted">
                      <Check className="w-4 h-4 text-brand-secondary shrink-0 mt-0.5" />
                      <span>{perk}</span>
                    </li>
                  ))}
                </ul>

                <Link
                  href={`/checkout?plan=${plan.id}`}
                  className={`mt-6 block rounded-xl px-4 py-3 text-center text-sm font-bold transition-colors ${
                    featured
                      ? "bg-gradient-to-r from-brand-primary to-brand-secondary text-white hover:opacity-90"
                      : "bg-white/5 text-white border border-brand-border hover:border-brand-primary/60"
                  }`}
                >
                  Get {plan.months === 12 ? "12 Months" : `${plan.months} Month${plan.months > 1 ? "s" : ""}`} Access
                </Link>
              </motion.div>
            );
          })}
        </div>

        <p className="text-center text-brand-muted text-sm mt-10">
          Not ready to commit?{" "}
          <Link
            href="/trial"
            className="text-brand-primary font-semibold hover:underline inline-flex items-center gap-1"
          >
            <Zap className="w-4 h-4" />
            Start your free {TRIAL_HOURS}-hour trial
          </Link>{" "}
          — full access, no card required.
        </p>
      </div>
    </section>
  );
}
