"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import useSWR from "swr";
import { Trophy, Radio, Ticket } from "lucide-react";
import Button from "@/components/ui/Button";
import Spinner from "@/components/ui/Spinner";
import Badge from "@/components/ui/Badge";
import { UpcomingEvent } from "@/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

function formatCountdown(startTime: string, now: number): string {
  const diff = new Date(startTime).getTime() - now;
  if (diff <= 0) return "LIVE NOW";

  const totalMinutes = Math.floor(diff / 60000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;

  if (days > 0) return `in ${days}d ${hours}h`;
  if (hours > 0) return `in ${hours}h ${minutes}m`;
  return `in ${minutes}m`;
}

export default function WorldCup2026Page() {
  const { data, isLoading } = useSWR<{ events: UpcomingEvent[] }>(
    "/api/upcoming-events",
    fetcher,
    { refreshInterval: 60000 }
  );
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(id);
  }, []);

  const matches = (data?.events || []).filter((e) => e.competition === "FIFA World Cup");

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="relative flex items-center gap-3 bg-gradient-to-r from-[#0a3a1e] to-[#0d5c2b] border border-green-700/40 rounded-xl px-5 py-4 mb-8 overflow-hidden">
        <Image src="/images/world-cup-bg.png" alt="" fill className="object-cover opacity-30" />
        <Trophy className="relative z-10 w-8 h-8 text-yellow-400 shrink-0" />
        <div className="relative z-10">
          <p className="text-yellow-400 text-xs font-black uppercase tracking-widest">2026</p>
          <h1 className="text-2xl sm:text-3xl font-black text-white">FIFA World Cup on Enktel IPTV</h1>
        </div>
      </div>

      <p className="text-brand-muted text-lg mb-8 max-w-2xl">
        Watch every match live in 4K Ultra HD, alongside your Croatian &amp; Balkan channels. No need
        for a separate sports subscription — it&apos;s all included in your plan.
      </p>

      <div className="cyber-panel rounded-xl p-6 mb-10">
        <h2 className="text-white font-bold text-xl mb-5 flex items-center gap-2">
          <Radio className="w-5 h-5 text-brand-secondary" />
          Next Match
        </h2>

        {isLoading ? (
          <Spinner className="py-8" />
        ) : matches.length === 0 ? (
          <p className="text-brand-muted">
            No fixture is currently scheduled from our live data source. Check back closer to kickoff —
            this updates automatically as new matches are announced.
          </p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {matches.map((match) => {
              const countdown = formatCountdown(match.startTime, now);
              const isLiveNow = countdown === "LIVE NOW";
              return (
                <div
                  key={match.id}
                  className="bg-brand-bg border border-brand-border rounded-xl p-5 hover:border-brand-secondary/40 transition-all"
                >
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-2xl">⚽</span>
                    {match.isPPV && (
                      <Badge variant="warning">
                        <Ticket className="w-3 h-3" /> PPV
                      </Badge>
                    )}
                  </div>
                  <h3 className="text-white font-bold text-lg mb-3">{match.title}</h3>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-brand-muted">{match.channel}</span>
                    <span
                      className={`font-bold px-3 py-1 rounded-full ${
                        isLiveNow
                          ? "bg-brand-accent/20 text-brand-accent border border-brand-accent/30 animate-pulse"
                          : "bg-brand-secondary/10 text-brand-secondary border border-brand-secondary/30"
                      }`}
                    >
                      {countdown}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="text-center">
        <Link href="/checkout?plan=quarter">
          <Button size="lg">
            <Trophy className="w-4 h-4 mr-2" />
            Get World Cup Access
          </Button>
        </Link>
      </div>
    </div>
  );
}
