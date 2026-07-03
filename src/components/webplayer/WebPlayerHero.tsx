"use client";
import { motion } from "framer-motion";
import { MonitorPlay, ArrowRight, Sparkles } from "lucide-react";
import { WEB_PLAYER_URL } from "@/lib/webplayer";
import PlayerMockup from "@/components/webplayer/mockups/PlayerMockup";

export default function WebPlayerHero() {
  return (
    <section className="relative py-16 sm:py-20 px-4 sm:px-6 lg:px-8 overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-b from-brand-secondary/5 via-transparent to-transparent" />
      <div className="relative z-10 max-w-5xl mx-auto text-center">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          className="inline-flex items-center gap-2 bg-brand-secondary/10 border border-brand-secondary/30 rounded-full px-4 py-1.5 mb-6"
        >
          <Sparkles className="w-3.5 h-3.5 text-brand-secondary" />
          <span className="text-brand-secondary text-xs font-bold uppercase tracking-wider">New · Included Free With Every Subscription</span>
        </motion.div>

        <motion.h1
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="text-4xl sm:text-6xl font-black text-white mb-5 leading-tight"
        >
          The Enktel Web Player{" "}
          <span className="bg-gradient-to-r from-brand-secondary to-brand-primary bg-clip-text text-transparent">
            Is Here
          </span>
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="text-brand-muted text-lg sm:text-xl max-w-2xl mx-auto mb-9"
        >
          Live TV and a full program guide, streaming straight in your browser at{" "}
          <span className="text-white font-semibold">watch.enktel.tv</span>. No app to install, no extra device,
          no extra charge — just log in with the same details you already use.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="flex flex-wrap justify-center gap-3 mb-14"
        >
          <a href={WEB_PLAYER_URL} target="_blank" rel="noopener noreferrer">
            <button className="group flex items-center gap-3 bg-brand-secondary hover:bg-cyan-400 text-brand-bg font-bold px-8 py-4 rounded-xl transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-brand-secondary/30">
              <MonitorPlay className="w-5 h-5 shrink-0" />
              Launch watch.enktel.tv
              <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
            </button>
          </a>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
          className="max-w-3xl mx-auto rounded-2xl overflow-hidden border border-white/10 shadow-2xl shadow-black/50 relative aspect-[16/9]"
        >
          <PlayerMockup />
        </motion.div>
      </div>
    </section>
  );
}
