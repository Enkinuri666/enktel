import { NextResponse } from "next/server";
import { fetchNowPlaying, fetchOnTheAir } from "@/lib/tmdb";
import { mockMovies, mockTVShows, getMockUpcomingEvents } from "@/lib/mock-data";
import { fetchEPGData } from "@/lib/epg";
import { channels } from "@/lib/channels";

export const dynamic = "force-dynamic";

// A representative spread of channels to surface as "Live Channel Highlights".
const HIGHLIGHT_CHANNEL_IDS = ["sky-sports-main", "sky-cinema-premiere", "bbc-news", "cbbc", "discovery", "mtv"];

export async function GET() {
  let movies = mockMovies;
  let shows = mockTVShows;
  let source = "mock";

  if (process.env.TMDB_API_KEY) {
    try {
      [movies, shows] = await Promise.all([fetchNowPlaying(), fetchOnTheAir()]);
      source = "tmdb";
    } catch {
      source = "mock-fallback";
    }
  }

  const now = new Date();
  const programs = fetchEPGData();

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

  const events = getMockUpcomingEvents().slice(0, 4);

  return NextResponse.json({
    vod: { movies: movies.slice(0, 6), shows: shows.slice(0, 6) },
    liveHighlights,
    events,
    source,
    updatedAt: now.toISOString(),
  });
}
