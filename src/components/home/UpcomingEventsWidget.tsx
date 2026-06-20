"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import useSWR from "swr";
import { Radio, Ticket, ChevronRight, Trophy } from "lucide-react";
import Spinner from "@/components/ui/Spinner";
import Badge from "@/components/ui/Badge";
import { UpcomingEvent } from "@/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

const WORLD_CUP_COMPETITION = "FIFA World Cup";

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

export default function UpcomingEventsWidget() {
  const { data, isLoading } = useSWR<{ events: UpcomingEvent[] }>(
    "/api/upcoming-events",
    fetcher,
    { refreshInterval: 60000 }
  );
  const [now, setNow] = useState(() => Date.now());
  const [sportFilter, setSportFilter] = useState("All");
  const [channelFilter, setChannelFilter] = useState("All");

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(id);
  }, []);

  const allEvents = data?.events || [];
  const sports = ["All", ...Array.from(new Set(allEvents.map((e) => e.sport)))];
  const eventChannels = ["All", ...Array.from(new Set(allEvents.map((e) => e.channel)))];

  const filtered = allEvents.filter(
    (e) => (sportFilter === "All" || e.sport === sportFilter) && (channelFilter === "All" || e.channel === channelFilter)
  );

  // Pull World Cup fixtures into a dedicated featured strip so our headline
  // event is never buried as one row among the other leagues.
  const worldCupEvents = filtered.filter((e) => e.competition === WORLD_CUP_COMPETITION).slice(0, 3);
  const otherEvents = filtered.filter((e) => e.competition !== WORLD_CUP_COMPETITION).slice(0, 6);
  const hasAnything = worldCupEvents.length > 0 || otherEvents.length > 0;

  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <div className="w-2 h-2 bg-brand-secondary rounded-full animate-pulse" />
            <h2 className="text-2xl sm:text-3xl font-bold text-white">
              Upcoming Live{" "}
              <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
                Sports &amp; PPV
              </span>
            </h2>
          </div>
          <Link
            href="/channels?category=Sports"
            className="flex items-center gap-1 text-brand-primary hover:text-brand-secondary transition-colors text-sm font-medium"
          >
            See all <ChevronRight className="w-4 h-4" />
          </Link>
        </div>

        {!isLoading && allEvents.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 mb-8">
            <select
              value={sportFilter}
              onChange={(e) => setSportFilter(e.target.value)}
              className="bg-brand-card border border-brand-border text-brand-muted hover:text-white text-sm rounded-full px-4 py-2 focus:outline-none focus:border-brand-primary/40"
            >
              {sports.map((s) => (
                <option key={s} value={s}>
                  {s === "All" ? "All Sports" : s}
                </option>
              ))}
            </select>
            <select
              value={channelFilter}
              onChange={(e) => setChannelFilter(e.target.value)}
              className="bg-brand-card border border-brand-border text-brand-muted hover:text-white text-sm rounded-full px-4 py-2 focus:outline-none focus:border-brand-primary/40"
            >
              {eventChannels.map((c) => (
                <option key={c} value={c}>
                  {c === "All" ? "All Channels" : c}
                </option>
              ))}
            </select>
          </div>
        )}

        {isLoading ? (
          <Spinner className="py-12" />
        ) : !hasAnything ? (
          <p className="text-brand-muted text-center py-12">No upcoming events match these filters.</p>
        ) : (
          <div className="space-y-6">
            {/* Featured: FIFA World Cup 2026 */}
            {worldCupEvents.length > 0 && (
              <div className="relative overflow-hidden rounded-2xl border border-green-700/40 bg-gradient-to-r from-[#0a3a1e] via-[#0d5c2b] to-[#0a3a1e]">
                <div
                  className="absolute inset-0 opacity-10"
                  style={{ backgroundImage: "radial-gradient(circle at 15% 50%, #22c55e 0%, transparent 50%), radial-gradient(circle at 85% 50%, #fbbf24 0%, transparent 50%)" }}
                />
                <div className="relative z-10 p-5 sm:p-6">
                  <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
                    <div className="flex items-center gap-3">
                      <Trophy className="w-7 h-7 text-yellow-400 shrink-0" />
                      <div>
                        <p className="text-yellow-400 text-xs font-black uppercase tracking-widest">Happening Now · 2026</p>
                        <h3 className="text-white font-black text-lg sm:text-xl">FIFA World Cup — Live on Enktel</h3>
                      </div>
                    </div>
                    <Link
                      href="/world-cup-2026"
                      className="flex items-center gap-1 text-yellow-300 hover:text-yellow-200 transition-colors text-sm font-semibold"
                    >
                      Full schedule <ChevronRight className="w-4 h-4" />
                    </Link>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                    {worldCupEvents.map((event) => {
                      const countdown = formatCountdown(event.startTime, now);
                      const isLiveNow = countdown === "LIVE NOW";
                      return (
                        <div
                          key={event.id}
                          className="bg-black/30 border border-green-700/40 rounded-xl p-4 hover:border-yellow-400/40 transition-all duration-300"
                        >
                          <div className="flex items-center justify-between mb-3">
                            <span className="text-xl shrink-0">⚽</span>
                            <span
                              className={`text-xs font-bold px-2 py-1 rounded-full ${
                                isLiveNow
                                  ? "bg-brand-accent/20 text-brand-accent border border-brand-accent/30 animate-pulse"
                                  : "bg-yellow-400/15 text-yellow-300 border border-yellow-400/30"
                              }`}
                            >
                              {countdown}
                            </span>
                          </div>
                          <h4 className="text-white font-bold text-sm mb-3 line-clamp-2">{event.title}</h4>
                          <span className="flex items-center gap-1.5 text-xs text-green-200">
                            <Radio className="w-3.5 h-3.5 text-yellow-400 shrink-0" />
                            {event.channel}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            )}

            {/* Other live sports */}
            {otherEvents.length > 0 && (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {otherEvents.map((event) => {
                  const countdown = formatCountdown(event.startTime, now);
                  const isLiveNow = countdown === "LIVE NOW";
                  return (
                    <div
                      key={event.id}
                      className="bg-brand-card border border-brand-border rounded-xl p-4 hover:border-brand-secondary/40 transition-all duration-300"
                    >
                      <div className="flex items-start justify-between mb-3">
                        <div className="flex items-center gap-2 min-w-0">
                          <span className="text-xl shrink-0">{event.emoji}</span>
                          <span className="text-brand-muted text-xs font-medium truncate">
                            {event.competition}
                          </span>
                        </div>
                        {event.isPPV && (
                          <Badge variant="warning" className="shrink-0 ml-2">
                            <Ticket className="w-3 h-3" /> PPV
                          </Badge>
                        )}
                      </div>

                      <h3 className="text-white font-semibold text-sm mb-3 line-clamp-2">
                        {event.title}
                      </h3>

                      <div className="flex items-center justify-between text-xs">
                        <span className="flex items-center gap-1.5 text-brand-muted">
                          <Radio className="w-3.5 h-3.5 text-brand-secondary shrink-0" />
                          {event.channel}
                        </span>
                        <span
                          className={`font-bold px-2 py-1 rounded-full ${
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
        )}
      </div>
    </section>
  );
}
