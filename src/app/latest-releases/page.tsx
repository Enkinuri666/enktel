"use client";
import { useState } from "react";
import useSWR from "swr";
import { Star, ChevronLeft, ChevronRight, Flame, Sparkles } from "lucide-react";
import { Movie, TVShow } from "@/types";
import Spinner from "@/components/ui/Spinner";
import MediaPoster from "@/components/ui/MediaPoster";
import Badge from "@/components/ui/Badge";

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
type SortMode = "popular" | "rating" | "newest";

const PAGE_SIZE = 20;
const SPOTLIGHT_SIZE = 6;
// Threshold for surfacing the "Crowd Favorite" badge / blockbuster quick filter —
// chosen so it catches well-known mainstream hits without being so strict it's ever empty.
const BLOCKBUSTER_RATING_THRESHOLD = 7.5;

const LANGUAGE_NAMES: Record<string, string> = {
  en: "English",
  hr: "Croatian",
  sr: "Serbian",
  bs: "Bosnian",
  es: "Spanish",
  fr: "French",
  de: "German",
  it: "Italian",
  ja: "Japanese",
  ko: "Korean",
};

function dateOf(item: Movie | TVShow): string {
  return item.type === "movie" ? item.releaseDate : item.firstAirDate;
}

function sortItems(items: (Movie | TVShow)[], mode: SortMode): (Movie | TVShow)[] {
  const sorted = [...items];
  if (mode === "popular") sorted.sort((a, b) => b.popularity - a.popularity);
  else if (mode === "rating") sorted.sort((a, b) => b.rating - a.rating);
  else sorted.sort((a, b) => new Date(dateOf(b)).getTime() - new Date(dateOf(a)).getTime());
  return sorted;
}

export default function LatestReleasesPage() {
  const [filter, setFilter] = useState<MediaFilter>("all");
  const [language, setLanguage] = useState("All");
  const [genre, setGenre] = useState("All");
  const [sort, setSort] = useState<SortMode>("popular");
  const [blockbustersOnly, setBlockbustersOnly] = useState(false);
  const [page, setPage] = useState(1);

  const { data, isLoading } = useSWR<ReleasesData>("/api/latest-releases", fetcher);

  const movies = data?.movies || [];
  const shows = data?.shows || [];

  const baseItems =
    filter === "movies"
      ? movies
      : filter === "shows"
      ? shows
      : [...movies, ...shows];

  const languages = ["All", ...Array.from(new Set(baseItems.map((i) => i.language)))];
  const genres = ["All", ...Array.from(new Set(baseItems.flatMap((i) => i.genres)))].sort();

  // Spotlight always reflects the most popular picks for the current media-type filter,
  // independent of language/genre/sort/blockbuster filters — it's the "don't know where
  // to start" shortcut, so it shouldn't disappear just because someone narrowed the grid.
  const spotlightItems = sortItems(baseItems, "popular").slice(0, SPOTLIGHT_SIZE);

  const allItems = baseItems.filter(
    (i) =>
      (language === "All" || i.language === language) &&
      (genre === "All" || i.genres.includes(genre)) &&
      (!blockbustersOnly || i.rating >= BLOCKBUSTER_RATING_THRESHOLD)
  );

  const sortedItems = sortItems(allItems, sort);

  const totalPages = Math.ceil(sortedItems.length / PAGE_SIZE);
  const paginatedItems = sortedItems.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

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

      {/* Spotlight: not sure what to watch? */}
      {!isLoading && spotlightItems.length > 0 && (
        <div className="mb-10">
          <div className="flex items-center gap-2 mb-4">
            <Flame className="w-5 h-5 text-brand-accent" />
            <h2 className="text-white font-bold text-lg">Not sure what to watch? Start here</h2>
          </div>
          <div className="flex gap-4 overflow-x-auto pb-2 scrollbar-thin">
            {spotlightItems.map((item, i) => {
              const isMovie = item.type === "movie";
              const year = dateOf(item) ? new Date(dateOf(item)).getFullYear() : "";
              return (
                <div
                  key={`spotlight-${item.type}-${item.id}`}
                  className="relative shrink-0 w-44 bg-brand-card border border-brand-border rounded-xl overflow-hidden hover:border-brand-primary/40 transition-colors"
                >
                  <div className="relative">
                    <MediaPoster posterPath={item.posterPath} title={item.title} type={item.type} />
                    <div className="absolute top-2 left-2">
                      <Badge variant="gold" size="sm">#{i + 1} Popular</Badge>
                    </div>
                    <div className="absolute top-2 right-2">
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${isMovie ? "bg-brand-primary/80 text-white" : "bg-brand-secondary/80 text-brand-bg"}`}>
                        {isMovie ? "MOVIE" : "TV"}
                      </span>
                    </div>
                  </div>
                  <div className="p-3">
                    <h3 className="text-white text-sm font-semibold line-clamp-1 mb-1">{item.title}</h3>
                    <div className="flex items-center justify-between">
                      <span className="text-brand-muted text-xs">{year}</span>
                      {item.rating > 0 && <RatingStars rating={item.rating} />}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3 mb-6">
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

        <button
          onClick={() => { setBlockbustersOnly((v) => !v); setPage(1); }}
          className={`flex items-center gap-1.5 px-5 py-2 rounded-full text-sm font-medium transition-colors ${
            blockbustersOnly
              ? "bg-brand-accent text-white shadow-lg shadow-brand-accent/25"
              : "bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-accent/40"
          }`}
          title="Show only widely-loved, highly-rated titles"
        >
          <Sparkles className="w-3.5 h-3.5" /> Blockbusters Only
        </button>

        <select
          value={sort}
          onChange={(e) => { setSort(e.target.value as SortMode); setPage(1); }}
          className="bg-brand-card border border-brand-border text-brand-muted hover:text-white text-sm rounded-full px-4 py-2 focus:outline-none focus:border-brand-primary/40"
        >
          <option value="popular">Sort: Most Popular</option>
          <option value="rating">Sort: Highest Rated</option>
          <option value="newest">Sort: Newest</option>
        </select>
        <select
          value={language}
          onChange={(e) => { setLanguage(e.target.value); setPage(1); }}
          className="bg-brand-card border border-brand-border text-brand-muted hover:text-white text-sm rounded-full px-4 py-2 focus:outline-none focus:border-brand-primary/40"
        >
          {languages.map((l) => (
            <option key={l} value={l}>
              {l === "All" ? "All Languages" : LANGUAGE_NAMES[l] || l.toUpperCase()}
            </option>
          ))}
        </select>
        <select
          value={genre}
          onChange={(e) => { setGenre(e.target.value); setPage(1); }}
          className="bg-brand-card border border-brand-border text-brand-muted hover:text-white text-sm rounded-full px-4 py-2 focus:outline-none focus:border-brand-primary/40"
        >
          {genres.map((g) => (
            <option key={g} value={g}>
              {g === "All" ? "All Genres" : g}
            </option>
          ))}
        </select>
        <span className="text-brand-muted text-sm ml-2">{sortedItems.length} items</span>
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4 mb-8">
            {paginatedItems.map((item) => {
              const isMovie = item.type === "movie";
              const title = item.title;
              const year = dateOf(item) ? new Date(dateOf(item)).getFullYear() : "";
              const isCrowdFavorite = item.rating >= BLOCKBUSTER_RATING_THRESHOLD;
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
                    {isCrowdFavorite && (
                      <div className="absolute top-2 right-2">
                        <Badge variant="warning" size="sm">
                          <Flame className="w-3 h-3" /> Crowd Favorite
                        </Badge>
                      </div>
                    )}
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

          {paginatedItems.length === 0 && (
            <p className="text-brand-muted text-center py-16">
              No titles match these filters. Try clearing a filter or turning off &quot;Blockbusters Only&quot;.
            </p>
          )}

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
