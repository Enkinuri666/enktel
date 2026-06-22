"use client";
import Link from "next/link";
import Image from "next/image";
import useSWR from "swr";
import { Trophy } from "lucide-react";
import { WorldCupMatch } from "@/lib/worldCup";
import MatchPromoVideo from "@/components/world-cup/MatchPromoVideo";

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
      {match.group && (
        <span className="text-yellow-400/80 text-[10px] font-bold uppercase shrink-0">Grp {match.group}</span>
      )}
      {match.homeTeamBadge && (
        <Image src={match.homeTeamBadge} alt="" width={16} height={16} className="w-4 h-4 object-contain shrink-0" unoptimized />
      )}
      <span className="text-white text-sm font-semibold whitespace-nowrap">
        {match.homeTeam} {hasScore ? match.homeScore : ""} – {hasScore ? match.awayScore : ""} {match.awayTeam}
      </span>
      {match.awayTeamBadge && (
        <Image src={match.awayTeamBadge} alt="" width={16} height={16} className="w-4 h-4 object-contain shrink-0" unoptimized />
      )}
      {!hasScore && (
        <span className="text-brand-muted text-xs whitespace-nowrap">
          {new Date(match.startTime).toLocaleDateString("en-GB", { day: "2-digit", month: "short" })}{" "}
          {new Date(match.startTime).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", hour12: false })}
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
    <section className="bg-gradient-to-r from-[#0a3a1e] via-[#0d5c2b] to-[#0a3a1e] border-y border-green-700/40 py-3">
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

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 mt-3">
        <Link href="/world-cup-2026" className="block max-w-xl">
          <MatchPromoVideo className="aspect-[16/8]" />
        </Link>
      </div>
    </section>
  );
}
