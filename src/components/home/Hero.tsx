"use client";
import Link from "next/link";
import Image from "next/image";
import useSWR from "swr";
import { motion } from "framer-motion";
import { Play, ArrowRight, Check } from "lucide-react";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";
import { WhatsOnItem } from "@/types";
import HeroVideoBackdrop from "./HeroVideoBackdrop";
import FlightBoard, { FlightBoardRow } from "@/components/ui/FlightBoard";
import QRCode from "@/components/ui/QRCode";

const trustBadges = ["No Contract", "No Auto-Renewal", "Instant Activation", "4K Ultra HD"];

const fetcher = (url: string) => fetch(url).then((r) => r.json());

function channelCode(name: string): string {
  const letters = name.replace(/[^a-zA-Z0-9]/g, "").toUpperCase();
  return letters.slice(0, 3) || "TV";
}

function toBoardRows(items: WhatsOnItem[]): FlightBoardRow[] {
  return items.slice(0, 4).map((item) => ({
    code: channelCode(item.channel.name),
    destination: item.currentProgram.title,
    gate: item.channel.country?.slice(0, 3).toUpperCase() || "INT",
    status: "LIVE",
    href: `/epg?channel=${encodeURIComponent(item.channel.id)}`,
  }));
}

export default function Hero() {
  const { data } = useSWR<{ items: WhatsOnItem[] }>("/api/whats-on", fetcher, {
    refreshInterval: 5 * 60 * 1000,
  });
  const boardRows = toBoardRows(data?.items || []);

  return (
    <section className="relative min-h-screen flex items-center overflow-hidden" style={{ background: "#060910" }}>

      {/* ── Animated hero backdrop (channel grid + drifting particles) ── */}
      <HeroVideoBackdrop />

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-24 pb-16 w-full">

        {/* ── Centerpiece animated logo ── */}
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
              className="absolute inset-0 -z-10 rounded-full orb"
              style={{
                background:
                  "radial-gradient(circle, rgba(31,216,242,0.5) 0%, rgba(47,111,255,0.35) 45%, transparent 75%)",
                filter: "blur(28px)",
              }}
            />
            <div className="absolute -inset-5 rounded-full border border-white/15 spin-slow" />
            <div className="absolute -inset-9 rounded-full border border-dashed border-white/8" />
            <Image
              src="/logo-icon.png"
              alt="Enktel"
              width={160}
              height={160}
              priority
              className="relative w-24 h-24 sm:w-32 sm:h-32 lg:w-40 lg:h-40 drop-shadow-[0_0_50px_rgba(31,216,242,0.6)]"
            />
          </motion.div>
        </motion.div>

        <div className="grid grid-cols-1 lg:grid-cols-[1fr_460px] gap-12 lg:gap-20 items-center min-h-[70vh]">

          {/* LEFT */}
          <div>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              className="inline-flex items-center gap-2.5 mb-8 rounded-full px-4 py-2 text-sm font-semibold border"
              style={{ background: "rgba(255,255,255,0.04)", borderColor: "rgba(255,255,255,0.1)" }}>
              <span className="text-xl leading-none">🇭🇷</span>
              <span className="text-white/90">Croatia&apos;s #1 IPTV Service</span>
              <span className="w-px h-4 bg-white/20" />
              <span style={{ color: "#1FD8F2" }}>50+ Countries</span>
            </motion.div>

            <motion.div initial={{ opacity: 0, y: 40 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="mb-8">
              <h1 className="font-black tracking-tighter leading-[0.86]">
                <span className="block text-white" style={{ fontSize: "clamp(3.5rem, 10vw, 7rem)" }}>WATCH</span>
                <span className="block" style={{
                  fontSize: "clamp(2.8rem, 8.5vw, 5.8rem)",
                  background: "linear-gradient(120deg, #2F6FFF 0%, #1FD8F2 55%, #2F6FFF 100%)",
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
                  style={{ background: "linear-gradient(135deg, #2F6FFF 0%, #1947CC 100%)" }}>
                  <div className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
                    style={{ background: "linear-gradient(135deg, #5B8AFF 0%, #2F6FFF 100%)" }} />
                  <Play className="w-5 h-5 fill-white shrink-0 relative z-10" />
                  <span className="relative z-10">Start Watching</span>
                  <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform shrink-0 relative z-10" />
                </button>
              </Link>
              <Link href="/epg">
                <button className="flex items-center gap-3 text-white font-semibold px-8 py-4 rounded-xl text-base transition-all duration-300 hover:-translate-y-0.5 w-full sm:w-auto justify-center border"
                  style={{ background: "rgba(255,255,255,0.04)", borderColor: "rgba(255,255,255,0.12)" }}>
                  Browse Channels
                </button>
              </Link>
              <Link href="/watch" className="flex items-center gap-2 text-sm font-semibold self-center sm:ml-1" style={{ color: "rgba(255,255,255,0.6)" }}>
                <span className="w-9 h-9 rounded-full flex items-center justify-center border shrink-0" style={{ borderColor: "rgba(255,255,255,0.18)" }}>
                  <Play className="w-3.5 h-3.5 fill-white" />
                </span>
                Watch Video
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

            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.55 }}
              className="ticket-card ticket-card-light mt-10 inline-flex items-center gap-4 border border-dashed border-white/15 rounded-2xl px-5 py-4"
              style={{ background: "rgba(255,255,255,0.03)" }}>
              <QRCode size={64} />
              <div>
                <div className="text-white/40 text-[10px] font-bold uppercase tracking-widest font-mono-flight">Boarding Pass</div>
                <div className="text-white font-semibold text-sm">Scan to board enktel.tv</div>
                <div className="text-white/40 text-xs font-mono-flight mt-0.5">GATE B1 · SEAT ANY · CLASS 4K</div>
              </div>
            </motion.div>
          </div>

          {/* RIGHT */}
          <motion.div initial={{ opacity: 0, x: 60 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.2, duration: 0.9 }}
            className="hidden lg:flex flex-col gap-3 relative">

            <div className="absolute inset-0 -z-10 scale-110 rounded-3xl"
              style={{ background: "radial-gradient(ellipse at 50% 50%, rgba(47,111,255,0.22) 0%, transparent 70%)", filter: "blur(24px)" }} />

            <div className="flex items-center justify-between px-1 mb-1">
              <div className="flex items-center gap-2">
                <span className="relative flex h-2.5 w-2.5">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75" />
                  <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-red-500" />
                </span>
                <span className="text-white/60 text-xs font-bold uppercase tracking-widest">Streaming Live</span>
              </div>
              <span className="text-white/30 text-xs">{boardRows.length || CHANNEL_COUNT_LABEL} channels</span>
            </div>

            <FlightBoard rows={boardRows} />

            <div className="grid grid-cols-2 gap-3 mt-2">
              <motion.div animate={{ y: [0, -7, 0] }} transition={{ duration: 4.5, repeat: Infinity, ease: "easeInOut" }}
                className="rounded-2xl border p-4 text-center"
                style={{ background: "rgba(47,111,255,0.14)", backdropFilter: "blur(20px)", borderColor: "rgba(47,111,255,0.3)" }}>
                <div className="text-3xl font-black text-white">{CHANNEL_COUNT_LABEL}</div>
                <div className="text-xs font-semibold mt-0.5" style={{ color: "#2F6FFF" }}>Live Channels</div>
              </motion.div>
              <motion.div animate={{ y: [0, 7, 0] }} transition={{ duration: 4, repeat: Infinity, ease: "easeInOut", delay: 0.7 }}
                className="rounded-2xl border p-4 text-center"
                style={{ background: "rgba(31,216,242,0.1)", backdropFilter: "blur(20px)", borderColor: "rgba(31,216,242,0.25)" }}>
                <div className="text-3xl font-black text-white">99.9%</div>
                <div className="text-xs font-semibold mt-0.5" style={{ color: "#1FD8F2" }}>Uptime SLA</div>
              </motion.div>
            </div>
          </motion.div>

        </div>
      </div>
    </section>
  );
}
