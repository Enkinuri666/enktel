import { NextResponse } from "next/server";
import { fetchNowPlaying, fetchOnTheAir } from "@/lib/tmdb";
import { mockMovies, mockTVShows } from "@/lib/mock-data";

export const revalidate = 3600;

export async function GET() {
  if (!process.env.TMDB_API_KEY) {
    return NextResponse.json({ movies: mockMovies, shows: mockTVShows, source: "mock" });
  }

  try {
    const [movies, shows] = await Promise.all([fetchNowPlaying(), fetchOnTheAir()]);
    return NextResponse.json({ movies, shows, source: "tmdb" });
  } catch {
    return NextResponse.json({ movies: mockMovies, shows: mockTVShows, source: "mock-fallback" });
  }
}
