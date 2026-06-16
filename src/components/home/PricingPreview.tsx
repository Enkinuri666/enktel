"use client";
import Link from "next/link";
import { Check, Zap } from "lucide-react";
import Button from "@/components/ui/Button";
import { motion } from "framer-motion";

const plans = [
  {
    id: "starter",
    name: "Starter",
    price: 8.99,
    connections: 1,
    quality: "Full HD",
    catchUp: "7 Days",
    features: ["5,000+ Channels", "Full HD Quality", "7-Day Catch-Up", "VOD Library", "EPG Guide"],
    highlighted: false,
  },
  {
    id: "pro",
    name: "Pro",
    price: 14.99,
    connections: 1,
    quality: "4K Ultra HD",
    catchUp: "14 Days",
    features: ["10,000+ Channels", "4K Ultra HD", "14-Day Catch-Up", "VOD Library", "EPG Guide", "Priority Support"],
    highlighted: true,
  },
  {
    id: "ultimate",
    name: "Ultimate",
    price: 24.99,
    connections: 1,
    quality: "4K Ultra HD",
    catchUp: "30 Days",
    features: ["10,000+ Channels", "4K Ultra HD", "30-Day Catch-Up", "VOD Library", "EPG Guide", "VIP Support", "Adult Content"],
    highlighted: false,
  },
];

export default function PricingPreview() {
  return (
    <section className="py-20 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-12">
          <motion.h2
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="text-3xl sm:text-4xl font-bold text-white mb-4"
          >
            Simple,{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
              Transparent
            </span>{" "}
            Pricing
          </motion.h2>
          <p className="text-brand-muted">No hidden fees. Cancel anytime.</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-10">
          {plans.map((plan, i) => (
            <motion.div
              key={plan.id}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.1 }}
            >
              {plan.highlighted ? (
                <div className="relative p-[1px] rounded-2xl bg-gradient-to-b from-brand-primary/60 to-brand-secondary/60 shadow-xl shadow-brand-primary/20">
                  {/* Most Popular badge */}
                  <div className="absolute -top-3 left-1/2 -translate-x-1/2 z-10">
                    <span className="bg-brand-primary text-white text-xs font-bold px-4 py-1 rounded-full flex items-center gap-1">
                      <Zap className="w-3 h-3 fill-current" /> Most Popular
                    </span>
                  </div>
                  {/* Glow */}
                  <div className="absolute inset-0 rounded-2xl bg-brand-primary/5 blur-xl -z-10 scale-105" />
                  <div className="bg-brand-card rounded-[15px] p-6">
                    <h3 className="text-white font-bold text-xl mb-1">{plan.name}</h3>
                    <div className="mb-6">
                      <span className="text-5xl font-black text-white">&pound;{plan.price}</span>
                      <span className="text-brand-muted text-sm">/month</span>
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
                      <Button variant="primary" fullWidth>
                        Get Started
                      </Button>
                    </Link>
                  </div>
                </div>
              ) : (
                <div className="relative rounded-2xl border border-brand-border bg-brand-card p-6 h-full">
                  <h3 className="text-white font-bold text-xl mb-1">{plan.name}</h3>
                  <div className="mb-6">
                    <span className="text-5xl font-black text-white">&pound;{plan.price}</span>
                    <span className="text-brand-muted text-sm">/month</span>
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
                    <Button variant="outline" fullWidth>
                      Get Started
                    </Button>
                  </Link>
                </div>
              )}
            </motion.div>
          ))}
        </div>

        <div className="text-center">
          <Link href="/pricing">
            <Button variant="ghost" size="lg">
              View All Plans &amp; Features
            </Button>
          </Link>
        </div>
      </div>
    </section>
  );
}
