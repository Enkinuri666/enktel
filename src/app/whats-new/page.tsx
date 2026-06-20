"use client";
import useSWR from "swr";
import Link from "next/link";
import { Film, Radio, Ticket, Sparkles } from "lucide-react";
import Spinner from "@/components/ui/Spinner";
import Badge from "@/components/ui/Badge";
import MediaPoster from "@/components/ui/MediaPoster";
import { Movie, TVShow, UpcomingEvent } from "@/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface LiveHighlight {
  channelId: string;
  channelName: string;
  category: string;
  nowShowing: string;
  description: string;
}

interface WhatsNewData {
  vod: { movies: Movie[]; shows: TVShow[] };
  liveHighlights: LiveHighlight[];
  events: UpcomingEvent[];
  updatedAt: string;
}

function formatCountdown(startTime: string): string {
  const diff = new Date(startTime).getTime() - Date.now();
  if (diff <= 0) return "LIVE NOW";
  const totalMinutes = Math.floor(diff / 60000);
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0) return `in ${days}d ${hours}h`;
  if (hours > 0) return `in ${hours}h ${minutes}m`;
  return `in ${minutes}m`;
}

export default function WhatsNewPage() {
  const { data, isLoading } = useSWR<WhatsNewData>("/api/whats-new", fetcher, {
    refreshInterval: 60000,
  });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-10">
        <div className="flex items-center gap-2 mb-3">
          <Sparkles className="w-5 h-5 text-brand-secondary" />
          <span className="text-brand-secondary text-sm font-bold uppercase tracking-wide">Auto-Updating</span>
        </div>
        <h1 className="text-4xl sm:text-5xl font-black text-white mb-3">
          What&apos;s New on{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Enktel IPTV
          </span>
        </h1>
        <p className="text-brand-muted text-lg max-w-2xl">
          Everything new this week — latest movies and series, live channel highlights, and upcoming sports &amp; PPV events.
          Each item tells you exactly where to find it on your activated Enktel device.
        </p>
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : (
        <div className="space-y-14">

          {/* VOD */}
          <section>
            <div className="flex items-center gap-3 mb-5">
              <Film className="w-5 h-5 text-brand-primary" />
              <h2 className="text-xl sm:text-2xl font-bold text-white">Latest Movies &amp; Series</h2>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
              {[...(data?.vod.movies || []), ...(data?.vod.shows || [])].map((item) => {
                const isMovie = item.type === "movie";
                return (
                  <div
                    key={`${item.type}-${item.id}`}
                    className="bg-brand-card border border-brand-border rounded-xl overflow-hidden hover:border-brand-primary/40 transition-all duration-300"
                  >
                    <MediaPoster posterPath={item.posterPath} title={item.title} type={item.type} />
                    <div className="p-3">
                      <h3 className="text-white text-sm font-semibold line-clamp-1 mb-1">{item.title}</h3>
                      <p className="text-brand-muted/70 text-xs">
                        Find it in: <span className="text-brand-secondary">{isMovie ? "Movies → New Releases" : "Series → New & Returning"}</span>
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
            <div className="text-center mt-5">
              <Link href="/latest-releases" className="text-brand-primary hover:text-brand-secondary text-sm font-medium">
                Browse the full VOD library →
              </Link>
            </div>
          </section>

          {/* Live channel highlights */}
          <section>
            <div className="flex items-center gap-3 mb-5">
              <Radio className="w-5 h-5 text-brand-accent" />
              <h2 className="text-xl sm:text-2xl font-bold text-white">Live Channel Highlights</h2>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {(data?.liveHighlights || []).map((h) => (
                <Link
                  key={h.channelId}
                  href={`/epg?channel=${encodeURIComponent(h.channelId)}`}
                  className="block bg-brand-card border border-brand-border rounded-xl p-4 hover:border-brand-accent/40 transition-all duration-300"
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-brand-muted text-xs font-medium">{h.category}</span>
                    <Badge variant="accent" className="font-bold">LIVE</Badge>
                  </div>
                  <h3 className="text-white font-semibold text-sm mb-1">{h.nowShowing}</h3>
                  <p className="text-brand-muted text-xs mb-3 line-clamp-2">{h.description}</p>
                  <p className="text-brand-secondary text-xs font-medium">
                    Tune to: {h.channelName}
                  </p>
                </Link>
              ))}
            </div>
            <div className="text-center mt-5">
              <Link href="/epg" className="text-brand-primary hover:text-brand-secondary text-sm font-medium">
                See the full EPG guide →
              </Link>
            </div>
          </section>

          {/* Sports & PPV */}
          <section>
            <div className="flex items-center gap-3 mb-5">
              <Ticket className="w-5 h-5 text-yellow-400" />
              <h2 className="text-xl sm:text-2xl font-bold text-white">Upcoming Sports &amp; PPV</h2>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {(data?.events || []).map((event) => (
                <div
                  key={event.id}
                  className="bg-brand-card border border-brand-border rounded-xl p-4 hover:border-brand-secondary/40 transition-all duration-300"
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xl">{event.emoji}</span>
                    {event.isPPV && (
                      <Badge variant="warning" className="font-bold">
                        <Ticket className="w-3 h-3" /> PPV
                      </Badge>
                    )}
                  </div>
                  <h3 className="text-white font-semibold text-sm mb-2 line-clamp-2">{event.title}</h3>
                  <p className="text-brand-secondary text-xs font-medium mb-1">Find it on: {event.channel}</p>
                  <p className="text-brand-muted text-xs">{formatCountdown(event.startTime)}</p>
                </div>
              ))}
            </div>
          </section>

        </div>
      )}
    </div>
  );
}
