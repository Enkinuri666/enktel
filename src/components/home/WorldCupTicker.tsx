"use client";
import Link from "next/link";
import useSWR from "swr";
import { Trophy } from "lucide-react";
import { WorldCupMatch } from "@/lib/worldCup";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

function MatchChip({ match }: { match: WorldCupMatch }) {
  const hasScore = match.homeScore !== null && match.awayScore !== null;
  return (
    <Link
      href="/world-cup-2026"
      className="flex items-center gap-2.5 shrink-0 bg-white/5 border border-white/10 rounded-full px-4 py-1.5 hover:bg-white/10 transition-colors"
    >
      {match.status === "live" && (
        <span className="flex items-center gap-1 text-brand-accent text-[10px] font-black uppercase">
          <span className="w-1.5 h-1.5 bg-brand-accent rounded-full animate-pulse" /> Live
        </span>
      )}
      <span className="text-white text-sm font-semibold whitespace-nowrap">
        {match.homeTeam} {hasScore ? match.homeScore : ""} – {hasScore ? match.awayScore : ""} {match.awayTeam}
      </span>
      {!hasScore && (
        <span className="text-brand-muted text-xs whitespace-nowrap">
          {new Date(match.startTime).toLocaleDateString("en-GB", { day: "2-digit", month: "short" })}
        </span>
      )}
    </Link>
  );
}

export default function WorldCupTicker() {
  const { data } = useSWR<{ matches: WorldCupMatch[] }>("/api/world-cup", fetcher, {
    refreshInterval: 60000,
  });

  const matches = data?.matches || [];
  if (matches.length === 0) return null;

  // Most relevant: recently finished + the next upcoming, capped so the
  // ticker stays readable rather than scrolling the whole tournament.
  const finished = matches.filter((m) => m.status === "finished" || m.status === "live").slice(-4);
  const upcoming = matches.filter((m) => m.status === "upcoming").slice(0, 4);
  const display = [...finished, ...upcoming];

  return (
    <section className="bg-gradient-to-r from-[#0a3a1e] via-[#0d5c2b] to-[#0a3a1e] border-y border-green-700/40 py-3 overflow-hidden">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center gap-4">
        <Link
          href="/world-cup-2026"
          className="flex items-center gap-2 shrink-0 text-yellow-400 font-black text-sm uppercase tracking-wide"
        >
          <Trophy className="w-4 h-4" /> World Cup 2026
        </Link>
        <div className="flex items-center gap-2 overflow-x-auto scrollbar-thin pb-0.5">
          {display.map((m) => (
            <MatchChip key={m.id} match={m} />
          ))}
        </div>
      </div>
    </section>
  );
}
