"use client";
import { useState } from "react";
import useSWR from "swr";
import { Star, ChevronLeft, ChevronRight } from "lucide-react";
import { Movie, TVShow } from "@/types";
import Spinner from "@/components/ui/Spinner";
import MediaPoster from "@/components/ui/MediaPoster";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

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

interface ReleasesData {
  movies: Movie[];
  shows: TVShow[];
}

type MediaFilter = "all" | "movies" | "shows";

const PAGE_SIZE = 20;

export default function LatestReleasesPage() {
  const [filter, setFilter] = useState<MediaFilter>("all");
  const [page, setPage] = useState(1);

  const { data, isLoading } = useSWR<ReleasesData>("/api/latest-releases", fetcher);

  const movies = data?.movies || [];
  const shows = data?.shows || [];

  const allItems =
    filter === "movies"
      ? movies
      : filter === "shows"
      ? shows
      : [...movies, ...shows];

  const totalPages = Math.ceil(allItems.length / PAGE_SIZE);
  const paginatedItems = allItems.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-8">
        <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">
          Latest{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Releases
          </span>
        </h1>
        <p className="text-brand-muted text-lg">
          The newest movies and TV shows available on Enktel IPTV.
        </p>
      </div>

      {/* Filter */}
      <div className="flex items-center gap-3 mb-6">
        {(["all", "movies", "shows"] as MediaFilter[]).map((f) => (
          <button
            key={f}
            onClick={() => { setFilter(f); setPage(1); }}
            className={`px-5 py-2 rounded-full text-sm font-medium transition-colors ${
              filter === f
                ? "bg-brand-primary text-white shadow-lg shadow-brand-primary/25"
                : "bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40"
            }`}
          >
            {f === "all" ? "All" : f === "movies" ? "Movies" : "TV Shows"}
          </button>
        ))}
        <span className="text-brand-muted text-sm ml-2">{allItems.length} items</span>
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4 mb-8">
            {paginatedItems.map((item) => {
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
                    <p className="text-brand-muted text-xs mb-2 line-clamp-2">{item.overview}</p>
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

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-3">
              <button
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page === 1}
                className="p-2 rounded-lg bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="text-brand-muted text-sm">
                Page <span className="text-white font-semibold">{page}</span> of{" "}
                <span className="text-white font-semibold">{totalPages}</span>
              </span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={page === totalPages}
                className="p-2 rounded-lg bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
