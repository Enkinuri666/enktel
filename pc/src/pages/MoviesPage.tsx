import { useState } from 'react';
import CategoryGrid, { type PosterItem } from '@/components/CategoryGrid';
import Player from '@/components/Player';
import { useCategories, useMovies, resolveMovieUrls } from '@/lib/queries';
import type { Movie } from '@/lib/xtream';
import { X } from 'lucide-react';

type MovieItem = PosterItem & { movie: Movie };

/**
 * Movies view: real Xtream catalog behind CategoryGrid. Selecting a poster
 * resolves the URL fallback chain (mirroring StreamUrlResolver on the
 * Android app) and pops a fullscreen Player overlay. Esc / close-button
 * dismisses back to the grid.
 */
export default function MoviesPage() {
  const cats = useCategories('vod');
  const movies = useMovies();
  const [playing, setPlaying] = useState<{ movie: Movie; url: string } | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const items: MovieItem[] = (movies.data ?? []).map((m) => ({
    key: String(m.stream_id),
    name: m.name,
    poster: m.poster,
    categoryId: m.category_id,
    rating: m.rating,
    year: m.year,
    movie: m,
  }));

  async function openMovie(item: MovieItem) {
    setPending(true);
    setError(null);
    const urls = await resolveMovieUrls(item.movie.stream_id, item.movie.container_extension);
    setPending(false);
    // Take the first candidate URL. Player surfaces playback errors via
    // onError; if we hit repeated failures a later pass will walk the
    // fallback chain the same way the Android engine does.
    if (urls[0]) setPlaying({ movie: item.movie, url: urls[0] });
    else setError('No playable URL for this title.');
  }

  return (
    <>
      <CategoryGrid<MovieItem>
        title="Movies"
        subtitle={movies.isLoading ? 'Loading library…' : `${items.length} titles`}
        categories={cats.data ?? []}
        items={items}
        loading={movies.isLoading}
        onOpen={openMovie}
      />
      {(playing || pending || error) && (
        <div className="absolute inset-0 z-40 bg-black/90 backdrop-blur-sm flex flex-col">
          <div className="flex items-center gap-3 p-4 border-b border-white/5">
            <div>
              <div className="text-[10px] font-black tracking-widest text-brand">▶ NOW PLAYING</div>
              <div className="text-lg font-bold">{playing?.movie.name ?? (pending ? 'Resolving stream…' : 'Playback issue')}</div>
            </div>
            <button
              onClick={() => { setPlaying(null); setError(null); }}
              className="ml-auto p-2 rounded-lg hover:bg-white/10"
              aria-label="Close player"
            >
              <X />
            </button>
          </div>
          <div className="flex-1 relative">
            {playing ? (
              <Player src={playing.url} autoPlay />
            ) : error ? (
              <div className="absolute inset-0 grid place-items-center text-live text-sm">{error}</div>
            ) : (
              <div className="absolute inset-0 grid place-items-center text-textDim text-sm">Resolving…</div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
