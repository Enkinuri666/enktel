"use client";
import { Suspense, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import useSWR from "swr";
import Link from "next/link";
import { Search as SearchIcon } from "lucide-react";
import Spinner from "@/components/ui/Spinner";
import Breadcrumbs from "@/components/ui/Breadcrumbs";
import MediaPoster from "@/components/ui/MediaPoster";
import ChannelLogo from "@/components/ui/ChannelLogo";
import { Channel, Movie, TVShow } from "@/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface SearchResults {
  channels: Channel[];
  movies: Movie[];
  shows: TVShow[];
}

function SearchPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const q = searchParams.get("q") || "";
  const [input, setInput] = useState(q);

  const { data, isLoading } = useSWR<SearchResults>(
    q ? `/api/search?q=${encodeURIComponent(q)}` : null,
    fetcher
  );

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    router.push(`/search?q=${encodeURIComponent(input)}`);
  }

  const channels = data?.channels || [];
  const movies = data?.movies || [];
  const shows = data?.shows || [];
  const hasResults = channels.length > 0 || movies.length > 0 || shows.length > 0;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <Breadcrumbs items={q ? [{ label: "Search", href: "/search" }, { label: `"${q}"` }] : [{ label: "Search" }]} />
      <form onSubmit={handleSubmit} className="relative mb-10 max-w-xl">
        <SearchIcon className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-brand-muted" />
        <input
          autoFocus
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Search channels, movies, TV shows..."
          className="w-full bg-brand-card border border-brand-border rounded-full pl-11 pr-4 py-3 text-white placeholder:text-brand-muted focus:outline-none focus:border-brand-primary/40"
        />
      </form>

      {!q ? (
        <p className="text-brand-muted">Start typing to search across channels, movies, and TV shows.</p>
      ) : isLoading ? (
        <Spinner className="py-20" />
      ) : !hasResults ? (
        <p className="text-brand-muted">No results for &quot;{q}&quot;.</p>
      ) : (
        <div className="space-y-12">
          {channels.length > 0 && (
            <section>
              <h2 className="text-xl font-bold text-white mb-4">Channels</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {channels.map((c) => (
                  <Link
                    key={c.id}
                    href={`/epg?channel=${encodeURIComponent(c.id)}`}
                    className="flex items-center gap-3 bg-brand-card border border-brand-border rounded-xl p-3 hover:border-brand-primary/40 transition-colors"
                  >
                    <ChannelLogo name={c.name} id={c.id} logoUrl={c.logoUrl} size="sm" />
                    <div>
                      <p className="text-white text-sm font-medium">{c.name}</p>
                      <p className="text-brand-muted text-xs">{c.category}</p>
                    </div>
                  </Link>
                ))}
              </div>
            </section>
          )}

          {movies.length > 0 && (
            <section>
              <h2 className="text-xl font-bold text-white mb-4">Movies</h2>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
                {movies.map((m) => (
                  <div key={m.id} className="bg-brand-card border border-brand-border rounded-xl overflow-hidden">
                    <MediaPoster posterPath={m.posterPath} title={m.title} type="movie" />
                    <p className="text-white text-sm font-medium p-2 line-clamp-1">{m.title}</p>
                  </div>
                ))}
              </div>
            </section>
          )}

          {shows.length > 0 && (
            <section>
              <h2 className="text-xl font-bold text-white mb-4">TV Shows</h2>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
                {shows.map((s) => (
                  <div key={s.id} className="bg-brand-card border border-brand-border rounded-xl overflow-hidden">
                    <MediaPoster posterPath={s.posterPath} title={s.title} type="tv" />
                    <p className="text-white text-sm font-medium p-2 line-clamp-1">{s.title}</p>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      )}
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense fallback={<Spinner className="py-20" />}>
      <SearchPageInner />
    </Suspense>
  );
}
