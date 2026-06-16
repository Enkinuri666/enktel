import { NextResponse } from "next/server";
import { fetchUpcomingMovies } from "@/lib/tmdb";
import { mockUpcomingMovies } from "@/lib/mock-data";

export const revalidate = 3600;

export async function GET() {
  if (!process.env.TMDB_API_KEY) {
    return NextResponse.json({ movies: mockUpcomingMovies, source: "mock" });
  }

  try {
    const movies = await fetchUpcomingMovies();
    return NextResponse.json({ movies, source: "tmdb" });
  } catch {
    return NextResponse.json({ movies: mockUpcomingMovies, source: "mock-fallback" });
  }
}
