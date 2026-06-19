"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import useSWR from "swr";
import { Radio, Ticket, ChevronRight } from "lucide-react";
import Spinner from "@/components/ui/Spinner";
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

export default function UpcomingEventsWidget() {
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

  const events = data?.events?.slice(0, 6) || [];

  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <div className="w-2 h-2 bg-brand-secondary rounded-full animate-pulse" />
            <h2 className="text-2xl sm:text-3xl font-bold text-white">
              Upcoming Live <span className="text-brand-secondary">Sports &amp; PPV</span>
            </h2>
          </div>
          <Link
            href="/channels?category=Sports"
            className="flex items-center gap-1 text-brand-primary hover:text-brand-secondary transition-colors text-sm font-medium"
          >
            See all <ChevronRight className="w-4 h-4" />
          </Link>
        </div>

        {isLoading ? (
          <Spinner className="py-12" />
        ) : events.length === 0 ? (
          <p className="text-brand-muted text-center py-12">No upcoming events found.</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {events.map((event) => {
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
                      <span className="flex items-center gap-1 bg-yellow-400/15 text-yellow-400 border border-yellow-400/30 text-xs font-bold px-2 py-0.5 rounded-full shrink-0 ml-2">
                        <Ticket className="w-3 h-3" /> PPV
                      </span>
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
    </section>
  );
}
