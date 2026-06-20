import { NextResponse } from "next/server";
import { channels } from "@/lib/channels";
import { fetchNowPlaying, fetchOnTheAir } from "@/lib/tmdb";
import { mockMovies, mockTVShows } from "@/lib/mock-data";
import { withFallback } from "@/lib/dataSource";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const q = (searchParams.get("q") || "").trim().toLowerCase();

  if (!q) {
    return NextResponse.json({ channels: [], movies: [], shows: [] });
  }

  const matchedChannels = channels
    .filter((c) => c.name.toLowerCase().includes(q) || c.category.toLowerCase().includes(q))
    .slice(0, 12);

  const { data } = await withFallback(
    async () => {
      if (!process.env.TMDB_API_KEY) throw new Error("no TMDB key");
      const [movies, shows] = await Promise.all([fetchNowPlaying(), fetchOnTheAir()]);
      return { movies, shows };
    },
    () => ({ movies: mockMovies, shows: mockTVShows }),
    { sourceName: "tmdb" }
  );

  const matchedMovies = data.movies.filter((m) => m.title.toLowerCase().includes(q)).slice(0, 12);
  const matchedShows = data.shows.filter((s) => s.title.toLowerCase().includes(q)).slice(0, 12);

  return NextResponse.json({ channels: matchedChannels, movies: matchedMovies, shows: matchedShows });
}
