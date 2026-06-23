"use client";
import Link from "next/link";
import Image from "next/image";
import { motion } from "framer-motion";
import { Play, ArrowRight, Check, Wifi } from "lucide-react";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const trustBadges = ["No Contract", "No Auto-Renewal", "Instant Activation", "4K Ultra HD"];

const liveChannels = [
  { name: "HRT 1", show: "Dnevnik 2", progress: 68, genre: "News", color: "#6C63FF" },
  { name: "Nova TV", show: "Doma ljubav", progress: 42, genre: "Drama", color: "#CE2C1A" },
  { name: "Sky Sports", show: "Premier League LIVE", progress: 85, genre: "Sport", color: "#00D4FF" },
];

export default function Hero() {
  return (
    <section className="relative min-h-screen flex items-center overflow-hidden" style={{ background: "#060910" }}>

      {/* ── Vivid aurora orbs ── */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none select-none">
        <div className="absolute -top-40 -left-40 w-[1000px] h-[1000px] rounded-full orb"
          style={{ background: "radial-gradient(circle, rgba(108,99,255,0.38) 0%, transparent 65%)" }} />
        <div className="absolute -bottom-56 -right-56 w-[900px] h-[900px] rounded-full"
          style={{ background: "radial-gradient(circle, rgba(0,212,255,0.28) 0%, transparent 65%)", animation: "orbFloat 12s ease-in-out infinite reverse" }} />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] rounded-full"
          style={{ background: "radial-gradient(circle, rgba(206,44,26,0.12) 0%, transparent 65%)", animation: "orbFloat 9s ease-in-out infinite 2s" }} />
        <div className="absolute inset-0 dot-grid" />
        <div className="absolute bottom-0 left-0 right-0 h-40"
          style={{ background: "linear-gradient(to bottom, transparent, #060910)" }} />
      </div>

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-16 w-full">
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_460px] gap-12 lg:gap-20 items-center min-h-[82vh]">

          {/* LEFT */}
          <div className="relative">
            <motion.div initial={{ opacity: 0, scale: 0.7 }} animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.7 }}
              className="absolute -top-6 -left-4 sm:left-0 w-28 h-28 sm:w-36 sm:h-36 pointer-events-none select-none z-0">
              <Image
                src="/wolf-head.png"
                alt=""
                fill
                className="object-contain wolf-howl"
                priority
              />
            </motion.div>

            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              className="relative inline-flex items-center gap-2.5 mb-8 ml-20 sm:ml-32 rounded-full px-4 py-2 text-sm font-semibold border"
              style={{ background: "rgba(255,255,255,0.04)", borderColor: "rgba(255,255,255,0.1)" }}>
              <span className="text-xl leading-none">🇭🇷</span>
              <span className="text-white/90">Croatia&apos;s #1 IPTV Service</span>
              <span className="w-px h-4 bg-white/20" />
              <span style={{ color: "#00D4FF" }}>50+ Countries</span>
            </motion.div>

            <motion.div initial={{ opacity: 0, y: 40 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="mb-8">
              <h1 className="font-black tracking-tighter leading-[0.86]">
                <span className="block text-white" style={{ fontSize: "clamp(3.5rem, 10vw, 7rem)" }}>WATCH</span>
                <span className="block" style={{
                  fontSize: "clamp(2.8rem, 8.5vw, 5.8rem)",
                  background: "linear-gradient(120deg, #6C63FF 0%, #00D4FF 55%, #6C63FF 100%)",
                  backgroundSize: "200% auto",
                  WebkitBackgroundClip: "text",
                  WebkitTextFillColor: "transparent",
                  backgroundClip: "text",
                }}>CROATIAN TV.</span>
                <span className="block text-white/75 font-light" style={{ fontSize: "clamp(2rem, 6.5vw, 4.5rem)" }}>WATCH THE WORLD.</span>
              </h1>
            </motion.div>

            <motion.p initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
              className="leading-relaxed mb-8 max-w-xl" style={{ color: "rgba(255,255,255,0.55)", fontSize: "1.1rem" }}>
              Stream <strong className="text-white">HRT</strong>, <strong className="text-white">Nova TV</strong>,{" "}
              <strong className="text-white">RTL Hrvatska</strong>, <strong className="text-white">Doma TV</strong>{" "}
              and <strong className="text-white">{CHANNEL_COUNT_LABEL}</strong> channels from anywhere in the world —
              in crystal-clear <strong className="text-white">4K Ultra HD</strong>.
            </motion.p>

            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
              className="flex flex-col sm:flex-row gap-4 mb-10">
              <Link href="/checkout?plan=annual">
                <button className="group relative flex items-center gap-3 text-white font-bold px-8 py-4 rounded-xl text-base transition-all duration-300 hover:-translate-y-1 w-full sm:w-auto justify-center overflow-hidden btn-glow"
                  style={{ background: "linear-gradient(135deg, #6C63FF 0%, #5348d4 100%)" }}>
                  <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
                    style={{ background: "linear-gradient(135deg, #7b73ff 0%, #6C63FF 100%)" }} />
                  <Play className="w-5 h-5 fill-white shrink-0 relative z-10" />
                  <span className="relative z-10">Start Watching</span>
                  <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform shrink-0 relative z-10" />
                </button>
              </Link>
              <Link href="/channels">
                <button className="flex items-center gap-3 text-white font-semibold px-8 py-4 rounded-xl text-base transition-all duration-300 hover:-translate-y-0.5 w-full sm:w-auto justify-center border"
                  style={{ background: "rgba(255,255,255,0.04)", borderColor: "rgba(255,255,255,0.12)" }}>
                  Browse Channels
                </button>
              </Link>
            </motion.div>

            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.45 }}
              className="flex flex-wrap gap-x-8 gap-y-2">
              {trustBadges.map((badge) => (
                <div key={badge} className="flex items-center gap-2 text-sm" style={{ color: "rgba(255,255,255,0.4)" }}>
                  <Check className="w-4 h-4 shrink-0" style={{ color: "#4ade80" }} />
                  {badge}
                </div>
              ))}
            </motion.div>
          </div>

          {/* RIGHT */}
          <motion.div initial={{ opacity: 0, x: 60 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.2, duration: 0.9 }}
            className="hidden lg:flex flex-col gap-3 relative">

            <div className="absolute inset-0 -z-10 scale-110 rounded-3xl"
              style={{ background: "radial-gradient(ellipse at 50% 50%, rgba(108,99,255,0.22) 0%, transparent 70%)", filter: "blur(24px)" }} />

            <div className="flex items-center justify-between px-1 mb-1">
              <div className="flex items-center gap-2">
                <span className="relative flex h-2.5 w-2.5">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75" />
                  <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-red-500" />
                </span>
                <span className="text-white/60 text-xs font-bold uppercase tracking-widest">Streaming Live</span>
              </div>
              <span className="text-white/30 text-xs">3 channels</span>
            </div>

            {liveChannels.map((ch, i) => (
              <motion.div key={ch.name}
                initial={{ opacity: 0, x: 30 }} animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.45 + i * 0.12 }}
                className="relative overflow-hidden rounded-2xl p-4 border"
                style={{ background: "rgba(13,18,32,0.85)", backdropFilter: "blur(24px)", borderColor: "rgba(255,255,255,0.07)" }}>
                <div className="absolute left-0 top-0 bottom-0 w-[3px] rounded-l-2xl" style={{ background: ch.color }} />
                <div className="flex items-center gap-3 mb-3">
                  <div className="w-11 h-11 rounded-xl flex items-center justify-center text-white text-xs font-black shrink-0"
                    style={{ background: `linear-gradient(135deg, ${ch.color}cc, ${ch.color}66)`, boxShadow: `0 4px 16px ${ch.color}40` }}>
                    {ch.name.replace(" ", "")}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-white font-semibold text-sm truncate">{ch.show}</div>
                    <div className="text-white/40 text-xs mt-0.5">{ch.name} · {ch.genre}</div>
                  </div>
                  <div className="flex items-center gap-1 shrink-0 px-2 py-1 rounded-lg" style={{ background: "rgba(74,222,128,0.1)" }}>
                    <Wifi className="w-3 h-3" style={{ color: "#4ade80" }} />
                    <span className="text-xs font-bold" style={{ color: "#4ade80" }}>4K</span>
                  </div>
                </div>
                <div className="h-1.5 rounded-full overflow-hidden" style={{ background: "rgba(255,255,255,0.08)" }}>
                  <div className="h-full rounded-full" style={{ width: `${ch.progress}%`, background: `linear-gradient(90deg, ${ch.color}, ${ch.color}99)` }} />
                </div>
                <div className="flex justify-between mt-1.5">
                  <span className="text-white/25 text-xs">{ch.progress}% through</span>
                  <span className="text-white/25 text-xs">{Math.round((100 - ch.progress) / 100 * 60)} min left</span>
                </div>
              </motion.div>
            ))}

            <div className="grid grid-cols-2 gap-3 mt-2">
              <motion.div animate={{ y: [0, -7, 0] }} transition={{ duration: 4.5, repeat: Infinity, ease: "easeInOut" }}
                className="rounded-2xl border p-4 text-center"
                style={{ background: "rgba(108,99,255,0.14)", backdropFilter: "blur(20px)", borderColor: "rgba(108,99,255,0.3)" }}>
                <div className="text-3xl font-black text-white">{CHANNEL_COUNT_LABEL}</div>
                <div className="text-xs font-semibold mt-0.5" style={{ color: "#6C63FF" }}>Live Channels</div>
              </motion.div>
              <motion.div animate={{ y: [0, 7, 0] }} transition={{ duration: 4, repeat: Infinity, ease: "easeInOut", delay: 0.7 }}
                className="rounded-2xl border p-4 text-center"
                style={{ background: "rgba(0,212,255,0.1)", backdropFilter: "blur(20px)", borderColor: "rgba(0,212,255,0.25)" }}>
                <div className="text-3xl font-black text-white">99.9%</div>
                <div className="text-xs font-semibold mt-0.5" style={{ color: "#00D4FF" }}>Uptime SLA</div>
              </motion.div>
            </div>
          </motion.div>

        </div>
      </div>
    </section>
  );
}
