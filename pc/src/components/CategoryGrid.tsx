import { useMemo, useState } from 'react';
import { Star } from 'lucide-react';

/**
 * Shared VOD/Series layout: left category sidebar + right poster grid.
 * MoviesPage and SeriesPage both use this; the grid item shape and the
 * category shape are generic so a "watchlist" or "recordings" view could
 * reuse it later.
 */

export type CategoryLike = { id: string; name: string };

export type PosterItem = {
  key: string;
  name: string;
  poster: string;
  categoryId: string;
  rating?: number;
  year?: number | null;
};

export type CategoryGridProps<T extends PosterItem> = {
  title: string;
  subtitle?: string;
  categories: CategoryLike[];
  items: T[];
  loading?: boolean;
  onOpen: (item: T) => void;
};

export default function CategoryGrid<T extends PosterItem>({
  title, subtitle, categories, items, loading, onOpen,
}: CategoryGridProps<T>) {
  const [cat, setCat] = useState<string | null>(null);
  // Simple client-side search over the current category — keeps the grid
  // responsive on a 30k-title panel where a full-catalogue debounce would
  // stutter Rust ↔ WebView round trips.
  const [q, setQ] = useState('');

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return items.filter((m) => {
      if (cat && m.categoryId !== cat) return false;
      if (needle && !m.name.toLowerCase().includes(needle)) return false;
      return true;
    });
  }, [items, cat, q]);

  return (
    <div className="flex h-full">
      {/* Category sidebar */}
      <aside className="w-64 shrink-0 border-r border-white/5 bg-surface/60 backdrop-blur flex flex-col">
        <div className="px-4 py-3 border-b border-white/5">
          <div className="text-[10px] font-black tracking-widest text-textDim">CATEGORIES</div>
          <div className="text-lg font-black">{title}</div>
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          <button
            onClick={() => setCat(null)}
            className={`w-full text-left px-4 py-2 text-sm hover:bg-white/5 ${
              cat === null ? 'bg-brand/15 border-l-2 border-brand' : 'border-l-2 border-transparent'
            }`}
          >
            All · {items.length}
          </button>
          {categories.map((c) => {
            const count = items.filter((m) => m.categoryId === c.id).length;
            if (count === 0) return null;
            return (
              <button
                key={c.id}
                onClick={() => setCat(c.id)}
                className={`w-full text-left px-4 py-2 text-sm hover:bg-white/5 flex justify-between items-center ${
                  cat === c.id ? 'bg-brand/15 border-l-2 border-brand' : 'border-l-2 border-transparent'
                }`}
                title={c.name}
              >
                <span className="truncate">{c.name}</span>
                <span className="text-[10px] text-textDim ml-2 shrink-0">{count}</span>
              </button>
            );
          })}
        </div>
      </aside>

      {/* Grid */}
      <section className="flex-1 overflow-y-auto">
        <div className="p-8">
          <div className="flex items-baseline gap-4 mb-6">
            <span className="text-3xl font-black tracking-tight">{title}</span>
            {subtitle && <span className="text-textDim text-sm">{subtitle}</span>}
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search this category…"
              className="ml-auto bg-surfaceHi/60 border border-white/10 rounded-md px-3 py-1.5 text-sm w-72 focus:outline-none focus:border-brand"
            />
          </div>

          {loading && items.length === 0 ? (
            <div className="grid grid-cols-6 gap-4">
              {Array.from({ length: 18 }).map((_, i) => (
                <div key={i} className="aspect-[2/3] rounded-lg bg-surfaceHi/70 relative overflow-hidden">
                  <div className="absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/5 to-transparent animate-shimmer" />
                </div>
              ))}
            </div>
          ) : filtered.length === 0 ? (
            <div className="text-textDim text-sm py-16 text-center">
              {q ? `No matches for "${q}"` : 'No titles in this category.'}
            </div>
          ) : (
            <div className="grid grid-cols-6 gap-4">
              {filtered.slice(0, 500).map((m) => (
                <button
                  key={m.key}
                  onClick={() => onOpen(m)}
                  className="text-left group focus:outline-none"
                >
                  <div className="aspect-[2/3] rounded-lg bg-surfaceHi/70 overflow-hidden relative ring-1 ring-white/5 group-hover:ring-brand/70 group-focus:ring-brand transition">
                    {m.poster ? (
                      <img
                        src={m.poster}
                        alt={m.name}
                        loading="lazy"
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                      />
                    ) : (
                      <div className="w-full h-full grid place-items-center text-textDim text-xs">
                        {m.name.slice(0, 2)}
                      </div>
                    )}
                    {m.rating != null && m.rating > 0 && (
                      <span className="absolute top-2 right-2 bg-black/70 backdrop-blur px-1.5 py-0.5 rounded text-[10px] font-bold flex items-center gap-1">
                        <Star size={10} className="fill-current text-amber-400" /> {m.rating.toFixed(1)}
                      </span>
                    )}
                  </div>
                  <div className="mt-1.5 text-xs font-semibold line-clamp-2">{m.name}</div>
                  {m.year != null && m.year > 0 && (
                    <div className="text-[10px] text-textDim">{m.year}</div>
                  )}
                </button>
              ))}
              {filtered.length > 500 && (
                <div className="col-span-6 text-center text-xs text-textDim py-4">
                  Showing 500 of {filtered.length} — refine your search or category.
                </div>
              )}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
