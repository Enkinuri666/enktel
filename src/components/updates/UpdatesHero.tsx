"use client";
import Link from "next/link";
import { motion } from "framer-motion";
import { Sparkles, MonitorPlay, ArrowRight, MessageSquareText } from "lucide-react";
import { WEB_PLAYER_URL } from "@/lib/webplayer";

function openAiAssistant() {
  window.dispatchEvent(new Event("enktel:open-ai-assistant"));
}

// The lead story: the two biggest additions to the Enktel ecosystem this
// quarter, presented like a magazine cover feature rather than a product
// banner. Secondary updates get their own cards in UpdatesStoryGrid below.
export default function UpdatesHero() {
  return (
    <section className="relative py-12 sm:py-16 px-4 sm:px-6 lg:px-8 overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-b from-brand-primary/5 via-transparent to-transparent pointer-events-none" />
      <div className="relative z-10 max-w-6xl mx-auto">
        <div className="grid lg:grid-cols-2 gap-6">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            className="relative rounded-3xl border border-brand-secondary/30 bg-gradient-to-br from-brand-secondary/10 via-brand-card to-brand-card p-7 sm:p-9 overflow-hidden"
          >
            <div className="absolute -top-16 -right-16 w-56 h-56 bg-brand-secondary/20 rounded-full blur-3xl pointer-events-none" />
            <div className="relative">
              <div className="inline-flex items-center gap-2 bg-brand-secondary/15 border border-brand-secondary/30 rounded-full px-3 py-1 mb-5">
                <Sparkles className="w-3.5 h-3.5 text-brand-secondary" />
                <span className="text-brand-secondary text-xs font-bold uppercase tracking-wider">Lead Story</span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-black text-white mb-3 leading-tight">
                Meet Ask Enktel AI, your new co-pilot
              </h2>
              <p className="text-brand-muted text-base leading-relaxed mb-6">
                A built-in assistant that answers technical questions, walks through setup on any device, and
                pulls up what&apos;s on now, what&apos;s next, and upcoming sports fixtures — all inside one chat,
                without leaving the page.
              </p>
              <button
                onClick={openAiAssistant}
                className="group inline-flex items-center gap-2 bg-brand-secondary hover:bg-cyan-400 text-brand-bg font-bold px-6 py-3 rounded-xl transition-all duration-300 hover:-translate-y-0.5"
              >
                <MessageSquareText className="w-4 h-4" />
                Ask Enktel AI
                <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </button>
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ delay: 0.1 }}
            className="relative rounded-3xl border border-brand-primary/30 bg-gradient-to-br from-brand-primary/10 via-brand-card to-brand-card p-7 sm:p-9 overflow-hidden"
          >
            <div className="absolute -bottom-16 -left-16 w-56 h-56 bg-brand-primary/20 rounded-full blur-3xl pointer-events-none" />
            <div className="relative">
              <div className="inline-flex items-center gap-2 bg-brand-primary/15 border border-brand-primary/30 rounded-full px-3 py-1 mb-5">
                <MonitorPlay className="w-3.5 h-3.5 text-brand-primary" />
                <span className="text-brand-primary text-xs font-bold uppercase tracking-wider">Lead Story</span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-black text-white mb-3 leading-tight">
                The Enktel Web Player is here
              </h2>
              <p className="text-brand-muted text-base leading-relaxed mb-6">
                Live channels and the full program guide, streaming straight in your browser at{" "}
                <span className="text-white font-semibold">watch.enktel.tv</span>. No app, no extra device, no
                extra charge — included free with your existing login.
              </p>
              <div className="flex flex-wrap gap-3">
                <a href={WEB_PLAYER_URL} target="_blank" rel="noopener noreferrer">
                  <button className="group inline-flex items-center gap-2 bg-brand-primary hover:bg-violet-500 text-white font-bold px-6 py-3 rounded-xl transition-all duration-300 hover:-translate-y-0.5">
                    Launch watch.enktel.tv
                    <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                  </button>
                </a>
                <Link
                  href="/web-player"
                  className="inline-flex items-center gap-1 text-brand-muted hover:text-white text-sm font-semibold px-4 py-3"
                >
                  Read the full story →
                </Link>
              </div>
            </div>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
