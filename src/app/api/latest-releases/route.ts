import { NextResponse } from "next/server";
import { fetchNowPlaying, fetchOnTheAir } from "@/lib/tmdb";
import { mockMovies, mockTVShows } from "@/lib/mock-data";
import { withFallback } from "@/lib/dataSource";

export const revalidate = 3600;

export async function GET() {
  const { data, source } = await withFallback(
    async () => {
      if (!process.env.TMDB_API_KEY) throw new Error("no TMDB key");
      const [movies, shows] = await Promise.all([fetchNowPlaying(), fetchOnTheAir()]);
      return { movies, shows };
    },
    () => ({ movies: mockMovies, shows: mockTVShows }),
    { sourceName: "tmdb" }
  );
  return NextResponse.json({ movies: data.movies, shows: data.shows, source });
}
