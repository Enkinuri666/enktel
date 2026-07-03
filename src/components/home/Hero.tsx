"use client";
import Link from "next/link";
import Image from "next/image";
import { motion } from "framer-motion";
import { Play, ArrowRight, Check, Wifi } from "lucide-react";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";
import HeroVideoBackdrop from "./HeroVideoBackdrop";

const trustBadges = ["No Contract", "No Auto-Renewal", "Instant Activation", "4K Ultra HD"];

const liveChannels = [
  { name: "HRT 1", show: "Dnevnik 2", progress: 68, genre: "News", colorClass: "bg-brand-primary", color: "#6C63FF" },
  { name: "Nova TV", show: "Doma ljubav", progress: 42, genre: "Drama", colorClass: "bg-brand-hr", color: "#CE2C1A" },
  { name: "Sky Sports", show: "Premier League LIVE", progress: 85, genre: "Sport", colorClass: "bg-brand-secondary", color: "#00D4FF" },
];

export default function Hero() {
  return (
    <section className="relative min-h-screen flex items-center overflow-hidden bg-brand-bg">

      <HeroVideoBackdrop />

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-16 w-full">

        {/* Centerpiece animated logo */}
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.8, ease: "easeOut" }}
          className="flex justify-center mb-10"
        >
          <motion.div
            animate={{ y: [0, -10, 0] }}
            transition={{ duration: 5, repeat: Infinity, ease: "easeInOut" }}
            className="relative"
          >
            <div
              className="absolute inset-0 -z-10 rounded-full orb blur-[28px]"
              style={{
                background:
                  "radial-gradient(circle, rgba(0,212,255,0.5) 0%, rgba(108,99,255,0.35) 45%, transparent 75%)",
              }}
            />
            <div className="absolute -inset-5 rounded-full border border-white/15 spin-slow" />
            <div className="absolute -inset-9 rounded-full border border-dashed border-white/[0.08]" />
            <Image
              src="/logo-icon.png"
              alt="Enktel"
              width={160}
              height={160}
              priority
              className="relative w-24 h-24 sm:w-32 sm:h-32 lg:w-40 lg:h-40 drop-shadow-[0_0_50px_rgba(0,212,255,0.6)]"
            />
          </motion.div>
        </motion.div>

        <div className="grid grid-cols-1 lg:grid-cols-[1fr_460px] gap-12 lg:gap-20 items-center min-h-[70vh]">

          {/* LEFT */}
          <div>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              className="inline-flex items-center gap-2.5 mb-8 rounded-full px-4 py-2 text-sm font-semibold border bg-white/[0.04] border-white/10">
              <span className="text-xl leading-none">🇭🇷</span>
              <span className="text-white/90">Croatia&apos;s #1 IPTV Service</span>
              <span className="w-px h-4 bg-white/20" />
              <span className="text-brand-secondary">50+ Countries</span>
            </motion.div>

            <motion.div initial={{ opacity: 0, y: 40 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="mb-8">
              <h1 className="font-black tracking-tighter leading-[0.86]">
                <span className="block text-white" style={{ fontSize: "clamp(3.5rem, 10vw, 7rem)" }}>WATCH</span>
                <span className="block gradient-text" style={{
                  fontSize: "clamp(2.8rem, 8.5vw, 5.8rem)",
                  backgroundImage: "linear-gradient(120deg, #6C63FF 0%, #00D4FF 55%, #6C63FF 100%)",
                  backgroundSize: "200% auto",
                }}>CROATIAN TV.</span>
                <span className="block text-white/75 font-light" style={{ fontSize: "clamp(2rem, 6.5vw, 4.5rem)" }}>WATCH THE WORLD.</span>
              </h1>
            </motion.div>

            <motion.p initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
              className="text-white/55 text-lg leading-relaxed mb-8 max-w-xl">
              Stream <strong className="text-white">HRT</strong>, <strong className="text-white">Nova TV</strong>,{" "}
              <strong className="text-white">RTL Hrvatska</strong>, <strong className="text-white">Doma TV</strong>{" "}
              and <strong className="text-white">{CHANNEL_COUNT_LABEL}</strong> channels from anywhere in the world —
              in crystal-clear <strong className="text-white">4K Ultra HD</strong>.
            </motion.p>

            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }}
              className="flex flex-col sm:flex-row gap-4 mb-10">
              <Link href="/pricing">
                <button className="group relative flex items-center gap-3 text-white font-bold px-8 py-4 rounded-2xl text-base transition-all duration-300 hover:-translate-y-1 w-full sm:w-auto justify-center overflow-hidden btn-glow bg-gradient-to-br from-brand-primary to-[#5348d4]">
                  <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-300 bg-gradient-to-br from-[#7b73ff] to-brand-primary" />
                  <Play className="w-5 h-5 fill-white shrink-0 relative z-10" />
                  <span className="relative z-10">Start Watching</span>
                  <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform shrink-0 relative z-10" />
                </button>
              </Link>
              <Link href="/epg">
                <button className="flex items-center gap-3 text-white font-semibold px-8 py-4 rounded-2xl text-base transition-all duration-300 hover:-translate-y-0.5 w-full sm:w-auto justify-center border bg-white/[0.04] border-white/[0.12] hover:border-white/25 hover:bg-white/[0.07]">
                  Browse Channels
                </button>
              </Link>
              <Link href="/watch" className="flex items-center gap-2 text-sm font-semibold self-center sm:ml-1 text-white/60 hover:text-white/80 transition-colors">
                <span className="w-9 h-9 rounded-full flex items-center justify-center border border-white/[0.18] shrink-0">
                  <Play className="w-3.5 h-3.5 fill-white" />
                </span>
                Watch Video
              </Link>
            </motion.div>

            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.45 }}
              className="flex flex-wrap gap-x-8 gap-y-2">
              {trustBadges.map((badge) => (
                <div key={badge} className="flex items-center gap-2 text-sm text-white/40">
                  <Check className="w-4 h-4 shrink-0 text-green-400" />
                  {badge}
                </div>
              ))}
            </motion.div>
          </div>

          {/* RIGHT */}
          <motion.div initial={{ opacity: 0, x: 60 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.2, duration: 0.9 }}
            className="hidden lg:flex flex-col gap-3 relative">

            <div className="absolute inset-0 -z-10 scale-110 rounded-3xl blur-[24px]"
              style={{ background: "radial-gradient(ellipse at 50% 50%, rgba(108,99,255,0.22) 0%, transparent 70%)" }} />

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
                className="relative overflow-hidden rounded-2xl p-4 border bg-brand-card/85 backdrop-blur-xl border-white/[0.07] hover:border-white/[0.14] transition-colors duration-300">
                <div className={`absolute left-0 top-0 bottom-0 w-[3px] rounded-l-2xl ${ch.colorClass}`} />
                <div className="flex items-center gap-3 mb-3">
                  <div className="w-11 h-11 rounded-xl flex items-center justify-center text-white text-xs font-black shrink-0"
                    style={{ background: `linear-gradient(135deg, ${ch.color}cc, ${ch.color}66)`, boxShadow: `0 4px 16px ${ch.color}40` }}>
                    {ch.name.replace(" ", "")}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-white font-semibold text-sm truncate">{ch.show}</div>
                    <div className="text-white/40 text-xs mt-0.5">{ch.name} · {ch.genre}</div>
                  </div>
                  <div className="flex items-center gap-1 shrink-0 px-2 py-1 rounded-lg bg-green-400/10">
                    <Wifi className="w-3 h-3 text-green-400" />
                    <span className="text-xs font-bold text-green-400">4K</span>
                  </div>
                </div>
                <div className="h-1.5 rounded-full overflow-hidden bg-white/[0.08]">
                  <div className="h-full rounded-full transition-all duration-700" style={{ width: `${ch.progress}%`, background: `linear-gradient(90deg, ${ch.color}, ${ch.color}99)` }} />
                </div>
                <div className="flex justify-between mt-1.5">
                  <span className="text-white/25 text-xs">{ch.progress}% through</span>
                  <span className="text-white/25 text-xs">{Math.round((100 - ch.progress) / 100 * 60)} min left</span>
                </div>
              </motion.div>
            ))}

            <div className="grid grid-cols-2 gap-3 mt-2">
              <motion.div animate={{ y: [0, -7, 0] }} transition={{ duration: 4.5, repeat: Infinity, ease: "easeInOut" }}
                className="rounded-2xl border p-4 text-center backdrop-blur-xl bg-brand-primary/[0.14] border-brand-primary/30">
                <div className="text-3xl font-black text-white">{CHANNEL_COUNT_LABEL}</div>
                <div className="text-xs font-semibold mt-0.5 text-brand-primary">Live Channels</div>
              </motion.div>
              <motion.div animate={{ y: [0, 7, 0] }} transition={{ duration: 4, repeat: Infinity, ease: "easeInOut", delay: 0.7 }}
                className="rounded-2xl border p-4 text-center backdrop-blur-xl bg-brand-secondary/10 border-brand-secondary/25">
                <div className="text-3xl font-black text-white">99.9%</div>
                <div className="text-xs font-semibold mt-0.5 text-brand-secondary">Uptime SLA</div>
              </motion.div>
            </div>
          </motion.div>

        </div>
      </div>
    </section>
  );
}
