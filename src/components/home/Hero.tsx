"use client";
import Link from "next/link";
import Image from "next/image";
import { motion } from "framer-motion";
import { Archivo, IBM_Plex_Mono } from "next/font/google";
import { Play, ArrowRight, Check, Wifi } from "lucide-react";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const archivo = Archivo({ subsets: ["latin"], weight: ["700", "800", "900"], variable: "--font-archivo" });
const plexMono = IBM_Plex_Mono({ subsets: ["latin"], weight: ["400", "500"], variable: "--font-mono" });

const ACCENT = "#2f8fff";

const trustBadges = ["No Contract", "No Auto-Renewal", "Instant Activation", "4K Ultra HD"];

const liveChannels = [
  { name: "HRT 1", show: "Dnevnik 2", progress: 68, genre: "News", color: "#6C63FF" },
  { name: "Nova TV", show: "Doma ljubav", progress: 42, genre: "Drama", color: "#CE2C1A" },
  { name: "Sky Sports", show: "Premier League LIVE", progress: 85, genre: "Sport", color: "#00D4FF" },
];

export default function Hero() {
  return (
    <section className={`${archivo.variable} ${plexMono.variable} relative min-h-screen flex items-center overflow-hidden`} style={{ background: "#06080c" }}>

      {/* ── Generated cinematic backdrop ── */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none select-none opacity-35">
        <Image src="/images/hero-bg.png" alt="" fill priority className="object-cover" />
      </div>

      {/* ── Cinematic vignette + single electric-blue glow ── */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none select-none">
        <div className="absolute -top-40 -left-40 w-[1000px] h-[1000px] rounded-full orb"
          style={{ background: `radial-gradient(circle, ${ACCENT}33 0%, transparent 65%)` }} />
        <div className="absolute -bottom-56 -right-56 w-[900px] h-[900px] rounded-full"
          style={{ background: `radial-gradient(circle, ${ACCENT}22 0%, transparent 65%)`, animation: "orbFloat 12s ease-in-out infinite reverse" }} />
        <div className="absolute inset-0 cyber-grid" />
        <div className="absolute inset-0" style={{ background: "radial-gradient(ellipse 80% 60% at 50% 50%, transparent 40%, #06080c 100%)" }} />
        <div className="absolute bottom-0 left-0 right-0 h-40"
          style={{ background: "linear-gradient(to bottom, transparent, #06080c)" }} />
      </div>

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-16 w-full">
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_460px] gap-12 lg:gap-20 items-center min-h-[78vh]">

          {/* LEFT */}
          <div>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              className="inline-flex items-center gap-2.5 mb-8 rounded-full px-4 py-2 text-xs font-medium border uppercase tracking-widest"
              style={{ background: "rgba(255,255,255,0.04)", borderColor: "rgba(255,255,255,0.1)", fontFamily: "var(--font-mono)" }}>
              <span className="text-base leading-none">🇭🇷</span>
              <span className="text-white/80">Croatia&apos;s #1 IPTV Service</span>
              <span className="w-px h-4 bg-white/20" />
              <span style={{ color: ACCENT }}>50+ Countries</span>
            </motion.div>

            <motion.div initial={{ opacity: 0, y: 40 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="mb-8">
              <h1 className="font-black tracking-tighter leading-[0.92]" style={{ fontFamily: "var(--font-archivo)" }}>
                <span className="block text-white" style={{ fontSize: "clamp(3rem, 8.5vw, 6rem)" }}>Stream Without</span>
                <span className="block" style={{
                  fontSize: "clamp(3rem, 8.5vw, 6rem)",
                  color: ACCENT,
                }}>Limits.</span>
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
                  style={{ background: `linear-gradient(135deg, ${ACCENT} 0%, #1c6fd6 100%)` }}>
                  <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
                    style={{ background: `linear-gradient(135deg, #5aa6ff 0%, ${ACCENT} 100%)` }} />
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
            className="hidden lg:flex flex-col gap-3 relative scanline-overlay">

            <div className="absolute inset-0 -z-10 scale-110 rounded-3xl"
              style={{ background: `radial-gradient(ellipse at 50% 50%, ${ACCENT}33 0%, transparent 70%)`, filter: "blur(24px)" }} />

            <div className="flex items-center justify-between px-1 mb-1">
              <div className="flex items-center gap-2">
                <span className="relative flex h-2.5 w-2.5">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75" />
                  <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-red-500" />
                </span>
                <span className="text-white/60 text-xs font-bold uppercase tracking-widest neon-flicker" style={{ fontFamily: "var(--font-mono)" }}>Streaming Live</span>
              </div>
              <span className="text-white/30 text-xs" style={{ fontFamily: "var(--font-mono)" }}>3 channels</span>
            </div>

            {liveChannels.map((ch, i) => (
              <motion.div key={ch.name}
                initial={{ opacity: 0, x: 30 }} animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.45 + i * 0.12 }}
                className="relative overflow-hidden rounded-2xl p-4 border corner-brackets"
                style={{ background: "rgba(13,18,32,0.85)", backdropFilter: "blur(24px)", borderColor: `${ACCENT}30` }}>
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
                className="rounded-2xl p-4 text-center neon-edge"
                style={{ background: `${ACCENT}1a`, backdropFilter: "blur(20px)" }}>
                <div className="text-3xl font-black text-white" style={{ fontFamily: "var(--font-archivo)" }}>{CHANNEL_COUNT_LABEL}</div>
                <div className="text-xs font-semibold mt-0.5" style={{ color: ACCENT }}>Live Channels</div>
              </motion.div>
              <motion.div animate={{ y: [0, 7, 0] }} transition={{ duration: 4, repeat: Infinity, ease: "easeInOut", delay: 0.7 }}
                className="rounded-2xl p-4 text-center neon-edge"
                style={{ background: `${ACCENT}10`, backdropFilter: "blur(20px)" }}>
                <div className="text-3xl font-black text-white" style={{ fontFamily: "var(--font-archivo)" }}>99.9%</div>
                <div className="text-xs font-semibold mt-0.5" style={{ color: ACCENT }}>Uptime SLA</div>
              </motion.div>
            </div>
          </motion.div>

        </div>
      </div>

      {/* ── Bottom live-channel ticker ── */}
      <div className="absolute bottom-0 left-0 right-0 z-10 border-t border-white/10 overflow-hidden"
        style={{ background: "rgba(6,8,12,0.85)", backdropFilter: "blur(12px)" }}>
        <div className="flex items-center py-2.5">
          <div className="flex shrink-0 items-center gap-2 px-4 border-r border-white/10 shrink-0" style={{ fontFamily: "var(--font-mono)" }}>
            <span className="relative flex h-1.5 w-1.5 shrink-0">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75" />
              <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-red-500" />
            </span>
            <span className="text-[11px] font-medium uppercase tracking-widest text-white/70 whitespace-nowrap">On Air</span>
          </div>
          <div className="flex overflow-hidden">
            <div className="flex shrink-0 gap-8 pr-8 animate-marquee" style={{ fontFamily: "var(--font-mono)" }}>
              {[...liveChannels, ...liveChannels].map((ch, i) => (
                <span key={`${ch.name}-${i}`} className="text-[11px] text-white/50 whitespace-nowrap shrink-0">
                  <span className="font-semibold" style={{ color: ch.color }}>{ch.name}</span>
                  {" "}— {ch.show} <span className="text-white/30">· {ch.genre}</span>
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
