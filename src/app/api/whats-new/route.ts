import { NextResponse } from "next/server";
import { fetchNowPlaying, fetchOnTheAir } from "@/lib/tmdb";
import { mockMovies, mockTVShows, getMockUpcomingEvents } from "@/lib/mock-data";
import { fetchEPGData } from "@/lib/epg";
import { getRealUpcomingEvents } from "@/lib/sportsApi";
import { channels } from "@/lib/channels";
import { withFallback } from "@/lib/dataSource";

export const dynamic = "force-dynamic";

// A representative spread of channels to surface as "Live Channel Highlights".
const HIGHLIGHT_CHANNEL_IDS = ["sky-sports-main", "sky-cinema-comedy", "bbc-news", "cbbc", "discovery", "mtv"];

export async function GET() {
  const { data: vod, source } = await withFallback(
    async () => {
      if (!process.env.TMDB_API_KEY) throw new Error("no TMDB key");
      const [movies, shows] = await Promise.all([fetchNowPlaying(), fetchOnTheAir()]);
      return { movies, shows };
    },
    () => ({ movies: mockMovies, shows: mockTVShows }),
    { sourceName: "tmdb" }
  );

  const now = new Date();
  const programs = await fetchEPGData();

  const liveHighlights = HIGHLIGHT_CHANNEL_IDS.map((id) => {
    const channel = channels.find((c) => c.id === id);
    if (!channel) return null;
    const current = programs.find(
      (p) => p.channelId === id && new Date(p.startTime) <= now && new Date(p.endTime) > now
    );
    if (!current) return null;
    return {
      channelId: channel.id,
      channelName: channel.name,
      category: channel.category,
      nowShowing: current.title,
      description: current.description,
    };
  }).filter((h): h is NonNullable<typeof h> => h !== null);

  const { data: events } = await withFallback(
    async () => {
      const real = await getRealUpcomingEvents();
      if (real.length === 0) throw new Error("no live events");
      return real;
    },
    () => getMockUpcomingEvents(),
    { sourceName: "thesportsdb" }
  );

  return NextResponse.json({
    vod: { movies: vod.movies.slice(0, 6), shows: vod.shows.slice(0, 6) },
    liveHighlights,
    events: events.slice(0, 4),
    source,
    updatedAt: now.toISOString(),
  });
}
