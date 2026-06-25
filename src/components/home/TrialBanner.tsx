"use client";
import Link from "next/link";
import Image from "next/image";
import { motion } from "framer-motion";
import { Sparkles, ArrowRight, Clock, ShieldCheck } from "lucide-react";

export default function TrialBanner() {
  return (
    <section className="py-14 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="relative overflow-hidden rounded-3xl border border-brand-primary/30 bg-gradient-to-br from-brand-primary via-[#5348d4] to-brand-secondary p-8 sm:p-12"
        >
          {/* Decorative glow + logo watermark */}
          <div className="absolute -top-16 -right-16 w-72 h-72 bg-white/10 rounded-full blur-3xl" />
          <div className="absolute bottom-0 left-0 w-56 h-56 bg-black/10 rounded-full blur-3xl" />
          <Image
            src="/logo-icon.png"
            alt=""
            width={220}
            height={220}
            className="absolute -right-6 -bottom-10 w-44 sm:w-56 opacity-15 pointer-events-none select-none"
          />

          <div className="relative z-10 flex flex-col lg:flex-row items-start lg:items-center justify-between gap-8">
            <div className="max-w-xl">
              <div className="inline-flex items-center gap-2 bg-white/15 border border-white/25 rounded-full px-4 py-1.5 mb-5">
                <Sparkles className="w-4 h-4 text-white" />
                <span className="text-white text-xs font-bold uppercase tracking-wider">100% Free — No Card Required</span>
              </div>

              <h2 className="text-3xl sm:text-4xl font-black text-white mb-3 leading-tight">
                Try Enktel IPTV Free for 24 Hours
              </h2>
              <p className="text-white/80 text-base sm:text-lg mb-6">
                Full access to every live channel, 4K streams, and the VOD library. Get instant credentials and
                a setup guide emailed to you the moment you sign up.
              </p>

              <div className="flex flex-wrap items-center gap-x-6 gap-y-2 mb-7">
                <div className="flex items-center gap-2 text-white/85 text-sm font-medium">
                  <Clock className="w-4 h-4 shrink-0" /> Instant activation
                </div>
                <div className="flex items-center gap-2 text-white/85 text-sm font-medium">
                  <ShieldCheck className="w-4 h-4 shrink-0" /> No payment details needed
                </div>
              </div>

              <Link href="/trial">
                <button className="group flex items-center gap-3 bg-white text-brand-primary font-bold px-8 py-4 rounded-xl transition-all duration-300 hover:-translate-y-1 hover:shadow-2xl hover:shadow-black/20">
                  Start My Free Trial
                  <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                </button>
              </Link>
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
