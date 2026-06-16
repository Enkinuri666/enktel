"use client";
import Link from "next/link";
import { motion } from "framer-motion";
import { Play, ArrowRight, Check, Signal } from "lucide-react";

const channelChips = [
  "HRT 1", "HRT 2", "Nova TV", "RTL HR",
  "Doma TV", "CMC TV", "N1 Info", "RTL 2",
  "Sky Sports", "Premier League", "BBC One", "CNN Int'l",
];

const trustBadges = ["No Contract", "Cancel Anytime", "Instant Activation", "24/7 Support"];

export default function Hero() {
  return (
    <section className="relative min-h-screen flex items-center overflow-hidden bg-brand-bg">
      {/* Background */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-40 -left-40 w-[800px] h-[800px] bg-brand-primary/10 rounded-full blur-[140px]" />
        <div className="absolute -bottom-40 -right-20 w-[700px] h-[700px] bg-brand-secondary/8 rounded-full blur-[140px]" />
        <div className="absolute top-1/3 right-1/3 w-[400px] h-[400px] bg-[#CE2C1A]/5 rounded-full blur-[100px]" />
        <div className="absolute inset-0 opacity-[0.025]" style={{ backgroundImage: "linear-gradient(rgba(108,99,255,1) 1px, transparent 1px), linear-gradient(90deg, rgba(108,99,255,1) 1px, transparent 1px)", backgroundSize: "80px 80px" }} />
      </div>

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-16 w-full">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-16 items-center min-h-[80vh]">

          {/* LEFT */}
          <div>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="inline-flex items-center gap-2 bg-brand-card border border-brand-border rounded-full px-4 py-2 mb-8">
              <span className="text-xl">🇭🇷</span>
              <span className="text-white text-sm font-semibold">Croatia&apos;s #1 IPTV Service</span>
              <span className="w-px h-4 bg-brand-border" />
              <span className="text-brand-primary text-sm font-medium">50+ Countries</span>
            </motion.div>

            <motion.h1 initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="text-5xl sm:text-6xl lg:text-7xl font-black leading-[0.92] tracking-tight mb-8">
              <span className="text-white block">Watch</span>
              <span className="bg-gradient-to-r from-brand-primary via-brand-secondary to-brand-primary bg-clip-text text-transparent block">Croatian TV.</span>
              <span className="text-white block">Watch the</span>
              <span className="text-brand-muted font-extralight block">World.</span>
            </motion.h1>

            <motion.p initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }} className="text-brand-muted text-lg leading-relaxed mb-8 max-w-lg">
              Stream <strong className="text-white">HRT</strong>, <strong className="text-white">Nova TV</strong>, <strong className="text-white">RTL Hrvatska</strong>, <strong className="text-white">Doma TV</strong> and <strong className="text-white">10,000+</strong> channels from anywhere in the world — in crystal-clear <strong className="text-white">4K Ultra HD</strong>.
            </motion.p>

            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }} className="flex flex-col sm:flex-row gap-4 mb-8">
              <Link href="/checkout?plan=pro">
                <button className="group flex items-center gap-3 bg-brand-primary hover:bg-purple-600 text-white font-bold px-8 py-4 rounded-xl text-base transition-all duration-300 hover:shadow-xl hover:shadow-brand-primary/40 hover:-translate-y-0.5 w-full sm:w-auto justify-center">
                  <Play className="w-5 h-5 fill-white shrink-0" />
                  Start Watching
                  <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform shrink-0" />
                </button>
              </Link>
              <Link href="/channels">
                <button className="flex items-center gap-3 bg-white/5 hover:bg-white/10 border border-white/10 hover:border-brand-primary/50 text-white font-semibold px-8 py-4 rounded-xl text-base transition-all duration-300 w-full sm:w-auto justify-center">
                  Browse Channels
                </button>
              </Link>
            </motion.div>

            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.4 }} className="flex flex-wrap gap-x-6 gap-y-2">
              {trustBadges.map((badge) => (
                <div key={badge} className="flex items-center gap-2 text-sm text-brand-muted">
                  <Check className="w-4 h-4 text-green-400 shrink-0" />
                  {badge}
                </div>
              ))}
            </motion.div>
          </div>

          {/* RIGHT */}
          <motion.div initial={{ opacity: 0, x: 50 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.2, duration: 0.8 }} className="hidden lg:block relative">
            {/* Glow behind card */}
            <div className="absolute inset-0 bg-brand-primary/10 rounded-3xl blur-3xl -z-10 scale-110" />

            {/* Now Playing Card */}
            <div className="bg-brand-card/90 backdrop-blur-xl border border-brand-border/80 rounded-2xl p-6 mb-4 shadow-2xl shadow-black/50">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <span className="relative flex h-2.5 w-2.5">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75" />
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-red-500" />
                  </span>
                  <span className="text-red-400 text-xs font-bold uppercase tracking-widest">Live Now</span>
                </div>
                <span className="text-brand-muted text-xs bg-brand-border/50 px-2 py-0.5 rounded-full">19:00 – 20:00</span>
              </div>
              <div className="flex items-center gap-4 mb-4">
                <div className="w-14 h-14 bg-gradient-to-br from-brand-primary to-brand-secondary rounded-xl flex items-center justify-center text-white text-sm font-black shadow-lg shadow-brand-primary/30 shrink-0">
                  HRT1
                </div>
                <div>
                  <div className="text-white font-bold text-lg">Dnevnik 2</div>
                  <div className="text-brand-muted text-sm">HRT 1 · News &amp; Current Affairs</div>
                </div>
              </div>
              <div className="h-2 bg-brand-border rounded-full overflow-hidden mb-2">
                <div className="h-full w-[68%] bg-gradient-to-r from-brand-primary to-brand-secondary rounded-full" />
              </div>
              <div className="flex justify-between">
                <span className="text-brand-muted text-xs">68% through</span>
                <span className="text-brand-muted text-xs">32 min remaining</span>
              </div>
            </div>

            {/* Channel chips */}
            <div className="grid grid-cols-3 gap-2 mb-4">
              {channelChips.map((ch, i) => (
                <motion.div key={ch} initial={{ opacity: 0, scale: 0.8 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: 0.5 + i * 0.04 }} className="bg-brand-card/80 backdrop-blur border border-brand-border hover:border-brand-primary/40 rounded-lg px-3 py-2.5 text-center transition-colors group cursor-default">
                  <span className="text-white text-xs font-medium group-hover:text-brand-primary transition-colors">{ch}</span>
                </motion.div>
              ))}
            </div>

            {/* Floating badges */}
            <motion.div animate={{ y: [0, -10, 0] }} transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }} className="absolute -top-8 -right-8 bg-gradient-to-br from-brand-primary/30 to-brand-primary/10 backdrop-blur-xl border border-brand-primary/40 rounded-2xl px-5 py-4 text-center shadow-xl">
              <div className="text-3xl font-black text-white leading-none">10K+</div>
              <div className="text-brand-primary text-xs font-semibold mt-1">Live Channels</div>
            </motion.div>

            <motion.div animate={{ y: [0, 10, 0] }} transition={{ duration: 3.5, repeat: Infinity, ease: "easeInOut", delay: 1 }} className="absolute -bottom-8 -left-8 bg-gradient-to-br from-brand-secondary/30 to-brand-secondary/10 backdrop-blur-xl border border-brand-secondary/40 rounded-2xl px-5 py-4 text-center shadow-xl">
              <div className="text-3xl font-black text-white leading-none">99.9%</div>
              <div className="text-brand-secondary text-xs font-semibold mt-1">Uptime</div>
            </motion.div>
          </motion.div>

        </div>
      </div>
    </section>
  );
}
