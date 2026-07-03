"use client";
import Link from "next/link";
import { motion } from "framer-motion";
import { MonitorPlay, ArrowRight, Sparkles } from "lucide-react";
import { WEB_PLAYER_URL } from "@/lib/webplayer";
import PlayerMockup from "@/components/webplayer/mockups/PlayerMockup";

export default function WebPlayerBanner() {
  return (
    <section className="py-14 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="relative overflow-hidden rounded-3xl border border-brand-secondary/30 bg-gradient-to-br from-[#0D1220] via-brand-card to-[#0D1220] p-8 sm:p-12"
        >
          <div className="absolute -top-20 -right-20 w-80 h-80 bg-brand-secondary/10 rounded-full blur-3xl" />
          <div className="absolute bottom-0 left-1/3 w-64 h-64 bg-brand-primary/10 rounded-full blur-3xl" />

          <div className="relative z-10 grid grid-cols-1 lg:grid-cols-2 gap-10 items-center">
            <div>
              <div className="inline-flex items-center gap-2 bg-brand-secondary/10 border border-brand-secondary/30 rounded-full px-4 py-1.5 mb-5">
                <Sparkles className="w-3.5 h-3.5 text-brand-secondary" />
                <span className="text-brand-secondary text-xs font-bold uppercase tracking-wider">Just Launched · Free for All Members</span>
              </div>

              <h2 className="text-3xl sm:text-4xl font-black text-white mb-3 leading-tight">
                Introducing the New{" "}
                <span className="bg-gradient-to-r from-brand-secondary to-brand-primary bg-clip-text text-transparent">
                  Enktel Web Player
                </span>
              </h2>

              <p className="text-brand-muted text-base sm:text-lg mb-6 max-w-lg">
                Watch every live channel and browse the full TV guide right in your browser at{" "}
                <span className="text-white font-semibold">watch.enktel.tv</span> — no app, no box, no extra cost.
                Included free with your Enktel subscription. Just log in with your existing IPTV details.
              </p>

              <div className="flex flex-wrap gap-3">
                <a href={WEB_PLAYER_URL} target="_blank" rel="noopener noreferrer">
                  <button className="group flex items-center gap-3 bg-brand-secondary hover:bg-cyan-400 text-brand-bg font-bold px-7 py-3.5 rounded-xl transition-all duration-300 hover:-translate-y-0.5 hover:shadow-xl hover:shadow-brand-secondary/30">
                    <MonitorPlay className="w-4 h-4 shrink-0" />
                    Watch at watch.enktel.tv
                    <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                  </button>
                </a>
                <Link href="/web-player">
                  <button className="flex items-center gap-2 border border-brand-border hover:border-brand-secondary/50 text-white font-semibold px-6 py-3.5 rounded-xl transition-colors">
                    See Features &amp; How It Works
                  </button>
                </Link>
              </div>
            </div>

            <div className="relative">
              <div className="rounded-2xl overflow-hidden border border-white/10 shadow-2xl shadow-black/40 relative aspect-[16/10]">
                <PlayerMockup />
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
