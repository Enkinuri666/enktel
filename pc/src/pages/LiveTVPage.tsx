import { useEffect, useMemo, useState } from 'react';
import Player from '@/components/Player';
import { useLive, useCategories, resolveLiveUrls } from '@/lib/queries';
import type { Channel } from '@/lib/xtream';
import { Radio, Search } from 'lucide-react';

/**
 * Live TV shell: category sidebar + channel list + video pane. Selecting a
 * channel resolves the six-shape URL fallback via the Rust backend, then
 * hands the first candidate to the Player. Reused hls.js instance means
 * zapping between channels feels like a channel change on a set-top box.
 */
export default function LiveTVPage() {
  const live = useLive();
  const cats = useCategories('live');
  const [cat, setCat] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const [current, setCurrent] = useState<Channel | null>(null);
  const [url, setUrl] = useState<string | null>(null);

  const channels = live.data ?? [];
  const categories = cats.data ?? [];

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return channels.filter((c) => {
      if (cat && c.category_id !== cat) return false;
      if (needle && !c.name.toLowerCase().includes(needle)) return false;
      return true;
    });
  }, [channels, cat, q]);

  // Auto-select the first channel once the catalogue loads so the video
  // pane isn't stuck on an empty state.
  useEffect(() => {
    if (!current && filtered.length > 0) setCurrent(filtered[0]);
  }, [filtered, current]);

  // Resolve URL every time the selected channel changes.
  useEffect(() => {
    let cancelled = false;
    async function pick() {
      if (!current) { setUrl(null); return; }
      const urls = await resolveLiveUrls(current.stream_id, true);
      if (!cancelled) setUrl(urls[0] ?? null);
    }
    pick();
    return () => { cancelled = true; };
  }, [current]);

  return (
    <div className="flex h-full">
      {/* Category sidebar (compact) */}
      <aside className="w-52 shrink-0 border-r border-white/5 bg-surface/60 backdrop-blur flex flex-col">
        <div className="px-4 py-3 border-b border-white/5">
          <div className="text-[10px] font-black tracking-widest text-textDim">CATEGORIES</div>
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          <button
            onClick={() => setCat(null)}
            className={`w-full text-left px-4 py-2 text-sm hover:bg-white/5 ${
              cat === null ? 'bg-brand/15 border-l-2 border-brand' : 'border-l-2 border-transparent'
            }`}
          >
            All · {channels.length}
          </button>
          {categories.map((c) => {
            const count = channels.filter((ch) => ch.category_id === c.id).length;
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

      {/* Channel list */}
      <aside className="w-72 shrink-0 border-r border-white/5 bg-surface/40 flex flex-col">
        <div className="px-4 py-3 border-b border-white/5">
          <div className="text-[10px] font-black tracking-widest text-textDim mb-1">CHANNELS</div>
          <div className="relative">
            <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-textDim" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search channels…"
              className="w-full bg-surfaceHi/60 border border-white/10 rounded-md pl-8 pr-2 py-1.5 text-sm focus:outline-none focus:border-brand"
            />
          </div>
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          {live.isLoading && (
            <div className="px-4 py-6 text-xs text-textDim">Loading channels…</div>
          )}
          {live.isError && (
            <div className="px-4 py-6 text-xs text-live">Couldn't load channels — check profile in Settings.</div>
          )}
          {filtered.slice(0, 500).map((c) => (
            <button
              key={c.stream_id}
              onClick={() => setCurrent(c)}
              className={`w-full text-left px-4 py-2.5 flex items-center gap-3 hover:bg-white/5 ${
                current?.stream_id === c.stream_id ? 'bg-brand/15 border-l-2 border-brand' : 'border-l-2 border-transparent'
              }`}
            >
              <span className="text-xs font-black text-brand w-8">{c.num || '—'}</span>
              {c.logo ? (
                <img src={c.logo} className="w-8 h-8 rounded object-contain bg-black/50 shrink-0" />
              ) : (
                <span className="w-8 h-8 rounded bg-surfaceHi grid place-items-center text-[10px] shrink-0">
                  {c.name.slice(0, 2).toUpperCase()}
                </span>
              )}
              <span className="text-sm font-semibold truncate flex-1">{c.name}</span>
              {current?.stream_id === c.stream_id && (
                <span className="h-2 w-2 rounded-full bg-live animate-livePulse" />
              )}
            </button>
          ))}
          {filtered.length > 500 && (
            <div className="px-4 py-4 text-[10px] text-textDim text-center">
              Showing 500 of {filtered.length} — search to refine.
            </div>
          )}
        </div>
      </aside>

      {/* Player */}
      <section className="flex-1 flex flex-col bg-black">
        <div className="flex-1 relative">
          {url ? (
            <Player src={url} live autoPlay />
          ) : (
            <div className="absolute inset-0 grid place-items-center text-textDim text-sm">
              {current ? 'Resolving stream…' : 'Pick a channel to start.'}
            </div>
          )}
        </div>
        <div className="glass px-6 py-3 border-t border-white/5 flex items-center gap-6">
          <Radio size={16} className="text-live animate-livePulse" />
          <div>
            <div className="text-[10px] font-black tracking-widest text-live">● LIVE</div>
            <div className="text-lg font-bold">{current?.name ?? '—'}</div>
          </div>
          <div className="ml-auto text-[10px] text-textDim">
            Ctrl+K for command palette · scroll list to zap · Space to pause
          </div>
        </div>
      </section>
    </div>
  );
}
