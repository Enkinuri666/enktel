"use client";
import { useState } from "react";
import useSWR from "swr";
import Link from "next/link";
import { Star, ChevronRight } from "lucide-react";
import Spinner from "@/components/ui/Spinner";
import MediaPoster from "@/components/ui/MediaPoster";
import { Movie, TVShow } from "@/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface ReleasesData {
  movies: Movie[];
  shows: TVShow[];
}

function RatingStars({ rating }: { rating: number }) {
  const stars = Math.round(rating / 2);
  return (
    <div className="flex items-center gap-1">
      {[1, 2, 3, 4, 5].map((s) => (
        <Star
          key={s}
          className={`w-3 h-3 ${s <= stars ? "text-yellow-400 fill-yellow-400" : "text-brand-border"}`}
        />
      ))}
      <span className="text-brand-muted text-xs ml-1">{rating.toFixed(1)}</span>
    </div>
  );
}

type MediaFilter = "all" | "movies" | "shows";

export default function LatestReleases() {
  const [filter, setFilter] = useState<MediaFilter>("all");
  const { data, isLoading } = useSWR<ReleasesData>("/api/latest-releases", fetcher, {
    refreshInterval: 3600000,
  });

  const movies = data?.movies || [];
  const shows = data?.shows || [];

  const items =
    filter === "movies"
      ? movies
      : filter === "shows"
      ? shows
      : [...movies.slice(0, 4), ...shows.slice(0, 4)];

  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8 bg-brand-card/30">
      <div className="max-w-7xl mx-auto">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
          <h2 className="text-2xl sm:text-3xl font-bold text-white">
            Latest{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
              Releases
            </span>
          </h2>
          <div className="flex items-center gap-2">
            {(["all", "movies", "shows"] as MediaFilter[]).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                  filter === f
                    ? "bg-brand-primary text-white"
                    : "text-brand-muted hover:text-white bg-white/5 hover:bg-white/10"
                }`}
              >
                {f === "all" ? "All" : f === "movies" ? "Movies" : "TV Shows"}
              </button>
            ))}
          </div>
        </div>

        <div className="flex items-center justify-between mb-4">
          <span />
          <Link
            href="/latest-releases"
            className="flex items-center gap-1 text-brand-primary hover:text-brand-secondary transition-colors text-sm font-medium"
          >
            View all <ChevronRight className="w-4 h-4" />
          </Link>
        </div>

        {isLoading ? (
          <Spinner className="py-12" />
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {items.slice(0, 8).map((item) => {
              const isMovie = item.type === "movie";
              const title = item.title;
              const date = isMovie ? (item as Movie).releaseDate : (item as TVShow).firstAirDate;
              const year = date ? new Date(date).getFullYear() : "";
              return (
                <div
                  key={`${item.type}-${item.id}`}
                  className="bg-brand-card border border-brand-border rounded-xl overflow-hidden hover:border-brand-primary/40 hover:shadow-lg hover:shadow-brand-primary/10 transition-all duration-300 group"
                >
                  <div className="relative">
                    <MediaPoster posterPath={item.posterPath} title={title} type={item.type} />
                    <div className="absolute top-2 left-2">
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${isMovie ? "bg-brand-primary/80 text-white" : "bg-brand-secondary/80 text-brand-bg"}`}>
                        {isMovie ? "MOVIE" : "TV"}
                      </span>
                    </div>
                  </div>
                  <div className="p-3">
                    <h3 className="text-white text-sm font-semibold line-clamp-1 mb-1">{title}</h3>
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-brand-muted text-xs">{year}</span>
                      {item.rating > 0 && <RatingStars rating={item.rating} />}
                    </div>
                    <div className="flex flex-wrap gap-1">
                      {item.genres.slice(0, 2).map((g) => (
                        <span key={g} className="text-xs bg-white/5 text-brand-muted px-2 py-0.5 rounded-full">
                          {g}
                        </span>
                      ))}
                    </div>
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
