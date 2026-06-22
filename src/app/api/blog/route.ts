import { NextResponse } from "next/server";
import { fetchNowPlaying, fetchOnTheAir, fetchUpcomingMovies } from "@/lib/tmdb";
import { mockMovies, mockTVShows, mockUpcomingMovies } from "@/lib/mock-data";
import { withFallback } from "@/lib/dataSource";
import { BlogPost, BlogPostKind, Movie, TVShow } from "@/types";

export const revalidate = 3600;

const TITLE_TEMPLATES: Record<BlogPostKind, (title: string) => string> = {
  "now-showing": (title) => `${title}: Now Streaming on Enktel`,
  "on-air": (title) => `${title} Is Back: New Episodes Streaming Now`,
  "coming-soon": (title) => `${title}: What to Expect`,
};

const SECTION_LABELS: Record<BlogPostKind, string> = {
  "now-showing": "Now Showing",
  "on-air": "On Air",
  "coming-soon": "Coming Soon",
};

function toPosts(items: (Movie | TVShow)[], kind: BlogPostKind, publishedFrom: Date): BlogPost[] {
  return items.map((item, i) => ({
    id: `${kind}-${item.type}-${item.id}`,
    kind,
    section: SECTION_LABELS[kind],
    title: TITLE_TEMPLATES[kind](item.title),
    excerpt: item.overview || "No synopsis available yet — check back soon.",
    posterPath: item.posterPath,
    backdropPath: item.backdropPath,
    publishedAt: new Date(publishedFrom.getTime() - i * 6 * 60 * 60 * 1000).toISOString(),
    rating: item.rating,
    genres: item.genres,
    mediaType: item.type,
  }));
}

export async function GET() {
  const { data, source } = await withFallback(
    async () => {
      if (!process.env.TMDB_API_KEY) throw new Error("no TMDB key");
      const [nowPlaying, onTheAir, upcoming] = await Promise.all([
        fetchNowPlaying(),
        fetchOnTheAir(),
        fetchUpcomingMovies(),
      ]);
      return { nowPlaying, onTheAir, upcoming };
    },
    () => ({ nowPlaying: mockMovies, onTheAir: mockTVShows, upcoming: mockUpcomingMovies }),
    { sourceName: "tmdb" }
  );

  const now = new Date();
  const posts: BlogPost[] = [
    ...toPosts(data.nowPlaying, "now-showing", now),
    ...toPosts(data.onTheAir, "on-air", now),
    ...toPosts(data.upcoming, "coming-soon", now),
  ].sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime());

  return NextResponse.json({ posts, source });
}
