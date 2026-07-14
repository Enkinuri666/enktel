"use client";
import { useEffect, useRef } from "react";
import { motion } from "framer-motion";
import { Rewind, Radio } from "lucide-react";
import type { EPGProgram } from "@/types";

interface CatchupTimelineProps {
  programs: EPGProgram[];
  now: Date;
  windowStart: Date;
  windowEnd: Date;
  selectedId: string | null;
  onSelect: (program: EPGProgram | null) => void;
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

// The catch-up / rewind feature: a horizontal strip of the channel's actual
// programme blocks (sized proportionally to their real duration) that can be
// scrubbed with Left/Right arrow keys as easily as by dragging — the whole
// point of building this as real keyboard navigation rather than a
// pointer-only slider is that a TV remote's D-pad has no pointer at all.
export default function CatchupTimeline({ programs, now, windowStart, windowEnd, selectedId, onSelect }: CatchupTimelineProps) {
  const totalMs = windowEnd.getTime() - windowStart.getTime();
  const nowPct = Math.min(100, Math.max(0, ((now.getTime() - windowStart.getTime()) / totalMs) * 100));
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const liveRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (selectedId === null) return;
    const idx = programs.findIndex((p) => p.id === selectedId);
    itemRefs.current[idx]?.scrollIntoView({ inline: "center", block: "nearest", behavior: "smooth" });
  }, [selectedId, programs]);

  function onKeyDown(e: React.KeyboardEvent, index: number) {
    if (e.key === "ArrowRight") {
      e.preventDefault();
      itemRefs.current[index + 1]?.focus();
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      if (index === 0) liveRef.current?.focus();
      else itemRefs.current[index - 1]?.focus();
    }
  }

  const isLive = selectedId === null;

  return (
    <div className="rounded-2xl bg-brand-card/60 border border-brand-border backdrop-blur-xl p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <Rewind className="w-4 h-4 text-brand-secondary" />
          <h3 className="text-white font-bold text-sm">Rewind &amp; Catch-Up</h3>
        </div>
        <button
          ref={liveRef}
          onClick={() => onSelect(null)}
          onKeyDown={(e) => {
            if (e.key === "ArrowRight") {
              e.preventDefault();
              itemRefs.current[0]?.focus();
            }
          }}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-black uppercase tracking-wide transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-secondary ${
            isLive ? "bg-brand-accent text-white" : "bg-white/5 text-brand-muted hover:text-white"
          }`}
        >
          <Radio className="w-3 h-3" /> Go Live
        </button>
      </div>

      <div className="relative overflow-x-auto scrollbar-thin pb-2">
        <div className="relative flex gap-1 min-w-max">
          {programs.map((p, i) => {
            const start = new Date(p.startTime).getTime();
            const end = new Date(p.endTime).getTime();
            const durationMin = Math.max(5, (end - start) / 60000);
            const isPast = end <= now.getTime();
            const selected = p.id === selectedId;
            return (
              <motion.button
                key={p.id}
                ref={(el) => { itemRefs.current[i] = el; }}
                onClick={() => onSelect(p)}
                onKeyDown={(e) => onKeyDown(e, i)}
                whileHover={{ y: -2 }}
                style={{ width: `${durationMin * 2.6}px` }}
                className={`shrink-0 rounded-lg px-2.5 py-2 text-left border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-secondary ${
                  selected
                    ? "bg-brand-secondary/25 border-brand-secondary/60"
                    : isPast
                    ? "bg-white/5 border-white/10 hover:border-white/25"
                    : "bg-brand-primary/10 border-brand-primary/25 hover:border-brand-primary/50"
                }`}
              >
                <p className="text-[10px] text-brand-muted font-mono">{formatTime(p.startTime)}</p>
                <p className={`text-xs font-semibold truncate ${selected ? "text-white" : "text-brand-muted"}`}>{p.title}</p>
              </motion.button>
            );
          })}
          {programs.length === 0 && (
            <p className="text-brand-muted text-sm py-4 px-1">No schedule data available for this channel right now.</p>
          )}
        </div>

        {/* "Now" marker */}
        <div
          className="absolute top-0 bottom-2 w-px bg-brand-accent/80 pointer-events-none"
          style={{ left: `${nowPct}%` }}
        >
          <span className="absolute -top-1 -translate-x-1/2 text-[9px] font-black text-brand-accent uppercase tracking-wide whitespace-nowrap">Now</span>
        </div>
      </div>
    </div>
  );
}
