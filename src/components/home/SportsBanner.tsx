"use client";
import Link from "next/link";
import { motion } from "framer-motion";
import { Trophy, ArrowRight, Zap } from "lucide-react";

const sports = [
  { emoji: "⚽", name: "HNL" },
  { emoji: "🏆", name: "Premier League" },
  { emoji: "🥇", name: "La Liga" },
  { emoji: "🏆", name: "Champions League" },
  { emoji: "🏎️", name: "Formula 1" },
  { emoji: "🎾", name: "Wimbledon" },
  { emoji: "🏀", name: "NBA" },
  { emoji: "🏉", name: "Rugby" },
  { emoji: "🥊", name: "Boxing" },
  { emoji: "⚾", name: "MLB" },
];

export default function SportsBanner() {
  return (
    <section className="relative py-16 px-4 sm:px-6 lg:px-8 overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-r from-brand-bg via-brand-primary/5 to-brand-bg" />
      <div className="absolute inset-0 bg-gradient-to-b from-transparent via-brand-accent/3 to-transparent" />

      <div className="relative z-10 max-w-7xl mx-auto">
        <div className="bg-gradient-to-br from-brand-card to-brand-bg border border-brand-border rounded-3xl p-8 sm:p-12 overflow-hidden relative">
          {/* Decorative glow */}
          <div className="absolute top-0 right-0 w-96 h-96 bg-brand-accent/10 rounded-full blur-3xl" />
          <div className="absolute bottom-0 left-0 w-64 h-64 bg-brand-primary/10 rounded-full blur-3xl" />

          <div className="relative z-10 flex flex-col lg:flex-row items-center lg:items-start gap-10">
            <div className="flex-1">
              <div className="inline-flex items-center gap-2 bg-brand-accent/10 border border-brand-accent/30 rounded-full px-4 py-2 mb-6">
                <Zap className="w-4 h-4 text-brand-accent fill-brand-accent" />
                <span className="text-brand-accent text-sm font-bold">2,000+ SPORT CHANNELS</span>
              </div>

              <h2 className="text-4xl sm:text-5xl font-black text-white mb-4 leading-tight">
                Live Sport.<br />
                <span className="bg-gradient-to-r from-brand-accent to-brand-primary bg-clip-text text-transparent">
                  Never Miss a Match.
                </span>
              </h2>

              <p className="text-brand-muted text-lg mb-8 max-w-lg">
                From the Croatian HNL to the Premier League, Formula 1 to Wimbledon — watch every match, race, and tournament live in stunning 4K.
              </p>

              <Link href="/channels?category=Sports">
                <button className="group flex items-center gap-3 bg-brand-accent hover:bg-red-600 text-white font-bold px-8 py-4 rounded-xl transition-all duration-300 hover:shadow-xl hover:shadow-brand-accent/30 hover:-translate-y-0.5">
                  <Trophy className="w-5 h-5 shrink-0" />
                  Browse Sport Channels
                  <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                </button>
              </Link>
            </div>

            <div className="flex-1 w-full">
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-2 xl:grid-cols-3 gap-3">
                {sports.map((s, i) => (
                  <motion.div
                    key={s.name}
                    initial={{ opacity: 0, scale: 0.9 }}
                    whileInView={{ opacity: 1, scale: 1 }}
                    viewport={{ once: true }}
                    transition={{ delay: i * 0.06 }}
                    className="bg-white/5 backdrop-blur border border-white/10 hover:border-brand-accent/40 rounded-xl px-4 py-3 flex items-center gap-3 transition-all duration-300 hover:bg-white/10"
                  >
                    <span className="text-2xl">{s.emoji}</span>
                    <span className="text-white text-sm font-semibold">{s.name}</span>
                  </motion.div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
