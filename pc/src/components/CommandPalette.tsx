import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Home, Tv, CalendarDays, Film, Popcorn, Trophy,
  Search, Bookmark, Radio, Settings, Command,
} from 'lucide-react';

type Cmd = { id: string; label: string; keywords: string; icon: any; run: () => void };

/**
 * Ctrl+K command palette. Fuzzy-ish substring match over label + keywords.
 * Extensible: adding a new command in the pages layer is a matter of pushing
 * onto a global registry (TODO).
 */
export default function CommandPalette({
  open, onClose,
}: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const [q, setQ] = useState('');

  useEffect(() => {
    if (!open) setQ('');
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [open, onClose]);

  const commands: Cmd[] = useMemo(() => [
    { id: 'home',   label: 'Go to Home',       keywords: 'home dashboard',        icon: Home,          run: () => { navigate('/'); onClose(); } },
    { id: 'live',   label: 'Live TV',          keywords: 'tv channels live iptv', icon: Tv,            run: () => { navigate('/live'); onClose(); } },
    { id: 'guide',  label: 'TV Guide (EPG)',   keywords: 'epg schedule guide',    icon: CalendarDays,  run: () => { navigate('/guide'); onClose(); } },
    { id: 'movies', label: 'Movies',           keywords: 'movies vod films',      icon: Film,          run: () => { navigate('/movies'); onClose(); } },
    { id: 'series', label: 'Series',           keywords: 'series shows tv',       icon: Popcorn,       run: () => { navigate('/series'); onClose(); } },
    { id: 'sport',  label: 'Sports Hub',       keywords: 'sports live match',     icon: Trophy,        run: () => { navigate('/sports'); onClose(); } },
    { id: 'search', label: 'Search',           keywords: 'search find lookup',    icon: Search,        run: () => { navigate('/search'); onClose(); } },
    { id: 'watch',  label: 'Watchlist',        keywords: 'watchlist saved list',  icon: Bookmark,      run: () => { navigate('/watchlist'); onClose(); } },
    { id: 'rec',    label: 'Recordings (DVR)', keywords: 'dvr recording save',    icon: Radio,         run: () => { navigate('/recordings'); onClose(); } },
    { id: 'set',    label: 'Settings',         keywords: 'settings preferences',  icon: Settings,      run: () => { navigate('/settings'); onClose(); } },
  ], [navigate, onClose]);

  const filtered = commands.filter(c => {
    if (!q.trim()) return true;
    const needle = q.toLowerCase();
    return c.label.toLowerCase().includes(needle) || c.keywords.includes(needle);
  });

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[90] bg-black/60 backdrop-blur-sm grid place-items-start pt-32" onClick={onClose}>
      <div
        className="w-[560px] max-w-[92vw] glass-strong rounded-xl shadow-glass border-white/10 mx-auto overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 px-4 py-3 border-b border-white/5">
          <Command className="h-4 w-4 text-brand" />
          <input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Type a command or search…"
            className="flex-1 bg-transparent outline-none text-sm placeholder:text-textDim"
          />
          <kbd className="text-[10px] text-textDim border border-white/10 rounded px-1">Esc</kbd>
        </div>
        <ul className="max-h-80 overflow-y-auto py-1">
          {filtered.map((c) => (
            <li key={c.id}>
              <button
                onClick={c.run}
                className="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-white/5 text-sm"
              >
                <c.icon className="h-4 w-4 text-textDim" />
                <span>{c.label}</span>
              </button>
            </li>
          ))}
          {filtered.length === 0 && (
            <li className="px-4 py-6 text-center text-xs text-textDim">
              No matching command.
            </li>
          )}
        </ul>
      </div>
    </div>
  );
}
