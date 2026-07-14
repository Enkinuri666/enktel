"use client";
import { useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Play, Pause, Volume2, VolumeX, Maximize, Minimize, Settings, Radio, Rewind } from "lucide-react";
import type { Channel, EPGProgram } from "@/types";

interface PlayerScreenProps {
  channel: Channel;
  program: EPGProgram | null;
  isLive: boolean;
  now: Date;
}

const QUALITIES = ["Auto", "4K Ultra HD", "1080p", "720p"];

// There's no real per-channel stream to embed on a public marketing site
// (that requires a subscriber's own credentials, generated per-account in
// the real member portal) — so "the screen" is an honest animated
// placeholder rather than a fake <video> pointed at content that isn't
// actually this channel. Everything around it (channel switching, the
// catch-up timeline, fullscreen) is genuinely functional.
export default function PlayerScreen({ channel, program, isLive, now }: PlayerScreenProps) {
  const [playing, setPlaying] = useState(true);
  const [muted, setMuted] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [quality, setQuality] = useState("Auto");
  const [fullscreen, setFullscreen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  async function toggleFullscreen() {
    if (!containerRef.current) return;
    if (!document.fullscreenElement) {
      await containerRef.current.requestFullscreen().catch(() => {});
      setFullscreen(true);
    } else {
      await document.exitFullscreen().catch(() => {});
      setFullscreen(false);
    }
  }

  const progressPct = program
    ? isLive
      ? Math.min(
          100,
          Math.max(
            0,
            ((now.getTime() - new Date(program.startTime).getTime()) /
              (new Date(program.endTime).getTime() - new Date(program.startTime).getTime())) *
              100
          )
        )
      : 6
    : 0;

  return (
    <div
      ref={containerRef}
      className="relative w-full aspect-video rounded-2xl overflow-hidden border border-white/10 shadow-2xl shadow-black/50 bg-[#05070d]"
    >
      {/* Animated broadcast-screen placeholder */}
      <div className="absolute inset-0 flex items-center justify-center overflow-hidden">
        <div
          className="absolute inset-0"
          style={{ background: "radial-gradient(120% 90% at 50% 10%, rgba(108,99,255,0.18) 0%, transparent 60%), #05070d" }}
        />
        <motion.div
          className="absolute inset-0 opacity-[0.07]"
          style={{
            backgroundImage: "repeating-linear-gradient(0deg, #fff 0px, transparent 1px, transparent 3px)",
          }}
          animate={playing ? { backgroundPositionY: [0, 40] } : {}}
          transition={{ duration: 1.2, repeat: Infinity, ease: "linear" }}
        />

        <AnimatePresence mode="wait">
          <motion.div
            key={channel.id}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            transition={{ duration: 0.35 }}
            className="relative flex flex-col items-center gap-4"
          >
            <div
              className="w-24 h-24 sm:w-32 sm:h-32 rounded-full flex items-center justify-center border-2 overflow-hidden bg-white/5 p-3"
              style={{
                borderColor: "rgba(255,255,255,0.15)",
                boxShadow: "0 0 60px rgba(108,99,255,0.35)",
              }}
            >
              {channel.logoUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={channel.logoUrl} alt={channel.name} className="w-full h-full object-contain" />
              ) : (
                <span className="text-white font-black text-2xl">{channel.name.slice(0, 3).toUpperCase()}</span>
              )}
            </div>
            {playing && (
              <div className="flex items-end gap-1 h-6">
                {[0.4, 0.9, 0.5, 1, 0.35].map((h, i) => (
                  <motion.span
                    key={i}
                    className="w-1 rounded-full bg-brand-secondary"
                    animate={{ height: [`${h * 30}%`, "100%", `${h * 30}%`] }}
                    transition={{ duration: 0.9 + i * 0.1, repeat: Infinity, ease: "easeInOut", delay: i * 0.1 }}
                  />
                ))}
              </div>
            )}
          </motion.div>
        </AnimatePresence>
      </div>

      {/* Status badge */}
      <div className="absolute top-3 left-3 sm:top-4 sm:left-4">
        <span
          className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-black uppercase tracking-wide backdrop-blur ${
            isLive ? "bg-brand-accent/90 text-white" : "bg-brand-secondary/90 text-brand-bg"
          }`}
        >
          {isLive ? <Radio className="w-3 h-3" /> : <Rewind className="w-3 h-3" />}
          {isLive ? "Live" : "Catch-Up"} · {channel.name}
        </span>
      </div>

      {/* Bottom overlay: now playing + controls */}
      <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 via-black/40 to-transparent px-3 sm:px-5 pt-10 pb-3 sm:pb-4">
        <p className="text-white text-sm sm:text-base font-semibold truncate mb-2">
          {program ? program.title : "No schedule data"}
        </p>
        <div className="h-1 rounded-full bg-white/15 mb-3 overflow-hidden">
          <div className="h-full rounded-full bg-gradient-to-r from-brand-secondary to-brand-primary" style={{ width: `${progressPct}%` }} />
        </div>

        <div className="flex items-center gap-2 sm:gap-3">
          <button
            onClick={() => setPlaying((p) => !p)}
            aria-label={playing ? "Pause" : "Play"}
            className="w-9 h-9 sm:w-10 sm:h-10 flex items-center justify-center rounded-full bg-white/10 hover:bg-white/20 text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-secondary"
          >
            {playing ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4 ml-0.5" />}
          </button>
          <button
            onClick={() => setMuted((m) => !m)}
            aria-label={muted ? "Unmute" : "Mute"}
            className="w-9 h-9 sm:w-10 sm:h-10 flex items-center justify-center rounded-full bg-white/10 hover:bg-white/20 text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-secondary"
          >
            {muted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
          </button>

          <div className="flex-1" />

          <div className="relative">
            <button
              onClick={() => setSettingsOpen((s) => !s)}
              aria-label="Quality settings"
              aria-expanded={settingsOpen}
              className="w-9 h-9 sm:w-10 sm:h-10 flex items-center justify-center rounded-full bg-white/10 hover:bg-white/20 text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-secondary"
            >
              <Settings className="w-4 h-4" />
            </button>
            {settingsOpen && (
              <div className="absolute bottom-12 right-0 w-40 rounded-xl bg-brand-card border border-brand-border shadow-xl overflow-hidden">
                {QUALITIES.map((q) => (
                  <button
                    key={q}
                    onClick={() => {
                      setQuality(q);
                      setSettingsOpen(false);
                    }}
                    className={`w-full text-left px-3 py-2 text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-secondary ${
                      q === quality ? "bg-brand-primary/25 text-white" : "text-brand-muted hover:bg-white/5 hover:text-white"
                    }`}
                  >
                    {q}
                  </button>
                ))}
              </div>
            )}
          </div>

          <button
            onClick={toggleFullscreen}
            aria-label={fullscreen ? "Exit fullscreen" : "Fullscreen"}
            className="w-9 h-9 sm:w-10 sm:h-10 flex items-center justify-center rounded-full bg-white/10 hover:bg-white/20 text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-secondary"
          >
            {fullscreen ? <Minimize className="w-4 h-4" /> : <Maximize className="w-4 h-4" />}
          </button>
        </div>
      </div>
    </div>
  );
}
