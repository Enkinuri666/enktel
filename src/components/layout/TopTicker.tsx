"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import useSWR from "swr";
import { Radio, Tv, ShieldCheck, AlertTriangle } from "lucide-react";
import { WhatsOnItem } from "@/types";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface HealthSource {
  status: "operational" | "degraded" | "down";
}

// Live Zagreb/Sarajevo/Belgrade clock - the region the bulk of Enktel's
// audience is in or watching from abroad, so it's the one fixed reference
// point worth ticking every second rather than re-deriving per render.
function useZagrebClock() {
  const [time, setTime] = useState<string | null>(null);
  useEffect(() => {
    const tick = () =>
      setTime(
        new Date().toLocaleTimeString("en-GB", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
          timeZone: "Europe/Zagreb",
        })
      );
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);
  return time;
}

export default function TopTicker() {
  const zagrebTime = useZagrebClock();
  const { data: whatsOn } = useSWR<{ items: WhatsOnItem[] }>("/api/whats-on", fetcher, {
    refreshInterval: 5 * 60 * 1000,
  });
  const { data: health } = useSWR<{ sources: HealthSource[] }>("/api/health", fetcher, {
    refreshInterval: 60 * 1000,
  });

  const liveItems = (whatsOn?.items || []).slice(0, 8);
  const allOperational = !health || health.sources.every((s) => s.status === "operational");

  const segments: { key: string; node: React.ReactNode }[] = [
    {
      key: "clock",
      node: (
        <span className="flex items-center gap-1.5 text-white/80">
          🕐 Zagreb / Sarajevo / Belgrade <span className="text-white font-semibold">{zagrebTime ?? "--:--:--"}</span>
        </span>
      ),
    },
    {
      key: "channels",
      node: (
        <span className="flex items-center gap-1.5 text-white/80">
          <Tv className="w-3.5 h-3.5 text-brand-secondary" />
          <span className="text-white font-semibold">{CHANNEL_COUNT_LABEL}</span> channels online
        </span>
      ),
    },
    {
      key: "status",
      node: allOperational ? (
        <span className="flex items-center gap-1.5 text-green-400 font-medium">
          <ShieldCheck className="w-3.5 h-3.5" /> All systems operational
        </span>
      ) : (
        <span className="flex items-center gap-1.5 text-yellow-400 font-medium">
          <AlertTriangle className="w-3.5 h-3.5" /> Some sources degraded
        </span>
      ),
    },
    ...liveItems.map((item) => ({
      key: item.channel.id,
      node: (
        <Link
          href={`/epg?channel=${encodeURIComponent(item.channel.id)}`}
          className="flex items-center gap-1.5 text-white/80 hover:text-white transition-colors"
        >
          <Radio className="w-3.5 h-3.5 text-brand-accent" />
          <span className="text-white font-semibold">{item.channel.name}</span>
          <span className="text-white/50">now:</span> {item.currentProgram.title}
        </Link>
      ),
    })),
  ];

  const renderSegments = (suffix: string) =>
    segments.map(({ key, node }, i) => (
      <span key={`${suffix}-${key}`} className="flex items-center shrink-0">
        {i > 0 && <span className="text-amber-400/40 mx-5 shrink-0 font-mono-flight">◆</span>}
        {node}
      </span>
    ));

  return (
    <div
      className="h-9 overflow-hidden border-b border-white/10 shrink-0 flex items-stretch"
      style={{ background: "linear-gradient(90deg, #07090f 0%, #0c1020 50%, #07090f 100%)" }}
    >
      <div className="hidden sm:flex items-center gap-2 px-4 bg-amber-400/10 border-r border-amber-400/20 shrink-0">
        <span className="text-amber-400 text-[10px] font-bold font-mono-flight tracking-widest">✈ DEPARTURES</span>
      </div>
      <div className="flex-1 overflow-hidden">
        <div className="h-full flex items-center">
          <div className="flex items-center whitespace-nowrap animate-marquee text-xs font-mono-flight">
            {renderSegments("a")}
            <span className="text-amber-400/40 mx-5 shrink-0">◆</span>
            {renderSegments("b")}
          </div>
        </div>
      </div>
    </div>
  );
}
