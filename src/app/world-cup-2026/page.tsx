"use client";
import Link from "next/link";
import Image from "next/image";
import useSWR from "swr";
import { Trophy, Radio, MapPin } from "lucide-react";
import Button from "@/components/ui/Button";
import Spinner from "@/components/ui/Spinner";
import BoardingPass from "@/components/ui/BoardingPass";
import { WorldCupMatch } from "@/lib/worldCup";
import MatchPromoVideo from "@/components/world-cup/MatchPromoVideo";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-GB", {
    weekday: "short",
    day: "2-digit",
    month: "short",
  });
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", hour12: false });
}

function MatchCard({ match }: { match: WorldCupMatch }) {
  const hasScore = match.homeScore !== null && match.awayScore !== null;
  const gateCode = match.group ? `G${match.group}` : match.round ? `R${match.round}` : "WC26";

  return (
    <BoardingPass
      className={match.status === "live" ? "border-brand-accent/60" : "hover:border-brand-secondary/40"}
      stub={
        <>
          <span className="text-brand-muted text-[10px] font-bold uppercase tracking-widest font-mono-flight">Gate</span>
          <span className="text-white font-black text-2xl font-mono-flight">{gateCode}</span>
          <span className="text-brand-muted text-[11px] font-mono-flight text-center">
            {formatDate(match.startTime)}<br />{formatTime(match.startTime)}
          </span>
          {match.status === "live" ? (
            <span className="flex items-center gap-1 text-brand-accent text-[10px] font-bold uppercase animate-pulse">
              <Radio className="w-3 h-3" /> Live
            </span>
          ) : match.status === "finished" ? (
            <span className="text-brand-muted text-[10px] font-bold uppercase">Landed</span>
          ) : (
            <span className="text-green-400 text-[10px] font-bold uppercase">Boarding</span>
          )}
        </>
      }
    >
      <div className="flex items-center justify-between mb-3 text-xs text-brand-muted font-mono-flight">
        <span>{formatDate(match.startTime)} · {formatTime(match.startTime)}</span>
        {(match.group || match.round) && (
          <span className="text-yellow-400 text-[10px] font-bold uppercase tracking-wide bg-yellow-400/10 px-2 py-0.5 rounded-full">
            {match.group ? `Group ${match.group}` : `Round ${match.round}`}
          </span>
        )}
      </div>
      <div className="flex items-center justify-between gap-3">
        <span className="flex items-center gap-2 flex-1 min-w-0">
          {match.homeTeamBadge && (
            <Image src={match.homeTeamBadge} alt="" width={20} height={20} className="w-5 h-5 object-contain shrink-0" unoptimized />
          )}
          <span className="text-white font-bold text-base truncate">{match.homeTeam}</span>
        </span>
        <span className="text-white font-black text-lg shrink-0 px-3 font-mono-flight">
          {hasScore ? `${match.homeScore} – ${match.awayScore}` : "vs"}
        </span>
        <span className="flex items-center gap-2 flex-1 min-w-0 justify-end">
          <span className="text-white font-bold text-base truncate text-right">{match.awayTeam}</span>
          {match.awayTeamBadge && (
            <Image src={match.awayTeamBadge} alt="" width={20} height={20} className="w-5 h-5 object-contain shrink-0" unoptimized />
          )}
        </span>
      </div>
      {(match.venue || match.city) && (
        <p className="text-brand-muted text-xs mt-3 text-center flex items-center justify-center gap-1">
          <MapPin className="w-3 h-3 shrink-0" />
          {[match.venue, match.city, match.country].filter(Boolean).join(", ")}
        </p>
      )}
    </BoardingPass>
  );
}

export default function WorldCup2026Page() {
  const { data, isLoading } = useSWR<{ matches: WorldCupMatch[] }>("/api/world-cup", fetcher, {
    refreshInterval: 60000,
  });

  const matches = data?.matches || [];
  const liveOrUpcoming = matches.filter((m) => m.status === "live" || m.status === "upcoming");
  const finished = matches.filter((m) => m.status === "finished").reverse();

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="flex items-center gap-3 bg-gradient-to-r from-[#0a3a1e] to-[#0d5c2b] border border-green-700/40 rounded-xl px-5 py-4 mb-8">
        <Trophy className="w-8 h-8 text-yellow-400 shrink-0" />
        <div>
          <p className="text-yellow-400 text-xs font-black uppercase tracking-widest">2026</p>
          <h1 className="text-2xl sm:text-3xl font-black text-white">FIFA World Cup on Enktel IPTV</h1>
        </div>
      </div>

      <p className="text-brand-muted text-lg mb-8 max-w-2xl">
        Watch every match live in 4K Ultra HD, alongside your Croatian &amp; Balkan channels. No need
        for a separate sports subscription — it&apos;s all included in your plan. Fixtures and results
        below update automatically.
      </p>

      <div className="mb-8 max-w-xl">
        <MatchPromoVideo className="aspect-[16/9]" />
      </div>

      <div className="bg-brand-card border border-brand-border rounded-xl p-6 mb-8">
        <h2 className="text-white font-bold text-xl mb-5 flex items-center gap-2">
          <Radio className="w-5 h-5 text-brand-secondary" />
          Upcoming Matches
        </h2>

        {isLoading ? (
          <Spinner className="py-8" />
        ) : liveOrUpcoming.length === 0 ? (
          <p className="text-brand-muted">
            No fixture is currently scheduled from our live data source. Check back closer to kickoff —
            this updates automatically as new matches are announced.
          </p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {liveOrUpcoming.map((match) => (
              <MatchCard key={match.id} match={match} />
            ))}
          </div>
        )}
      </div>

      {finished.length > 0 && (
        <div className="bg-brand-card border border-brand-border rounded-xl p-6 mb-10">
          <h2 className="text-white font-bold text-xl mb-5">Recent Results</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {finished.slice(0, 6).map((match) => (
              <MatchCard key={match.id} match={match} />
            ))}
          </div>
        </div>
      )}

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
