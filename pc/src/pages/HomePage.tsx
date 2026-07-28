import { Link, useNavigate } from 'react-router-dom';
import { Play, Info, Tv, Trophy, Film, Popcorn } from 'lucide-react';
import { useMemo } from 'react';
import { useMovies } from '@/lib/queries';
import { computeHomeRails } from '@/lib/recommendations';
import type { Movie } from '@/lib/xtream';

/**
 * v1.26.0 port from android — themed home rails with cross-rail dedup so a
 * single hot title doesn't dominate every strip. Uses the same
 * computeHomeRails() algorithm as the mobile/TV app: Latest Releases picks
 * first, then Coming Soon → Top Picks → Trending → New This Week → mood
 * rails each drawing from what's still unclaimed.
 */
export default function HomePage() {
  const nav = useNavigate();
  const movies = useMovies();
  const rails = useMemo(() => {
    if (!movies.data || movies.data.length === 0) return null;
    return computeHomeRails(movies.data, Math.floor(Date.now() / 1000));
  }, [movies.data]);

  const hero = rails?.latestReleases[0] ?? null;
  const heroBg = hero?.poster
    ? `linear-gradient(120deg, rgba(10,14,23,0.75) 0%, rgba(10,14,23,0.35) 45%, rgba(10,14,23,0.85) 100%), url("${hero.poster}") center/cover`
    : 'linear-gradient(120deg, #1B6AE5 0%, #3B9DFF 30%, #8B5CF6 70%, #0A0E17 100%)';

  return (
    <div className="min-h-full">
      {/* Hero */}
      <section className="relative h-[62vh] min-h-[420px] overflow-hidden">
        <div className="absolute inset-0" style={{ background: heroBg }} />
        <div className="absolute inset-0 bg-hero-fade" />
        <div className="absolute inset-0 flex flex-col justify-end p-10">
          <span className="text-[11px] font-black tracking-widest text-brand mb-2">▶ FEATURED</span>
          <h1 className="text-5xl font-black leading-tight mb-3 max-w-2xl">
            {hero?.name ?? 'Welcome to EnkTel IPTV'}
          </h1>
          <p className="text-textDim max-w-xl mb-6">
            {hero
              ? 'Your latest catalog pick — press Play to watch, or explore the rails below.'
              : 'Your entire library, sports lineup and channel guide — reimagined for the desktop. Premium Live TV, movies, series, catch-up and DVR, all in one lightweight app.'}
          </p>
          <div className="flex gap-3">
            {hero ? (
              <button
                onClick={() => nav(`/movies?id=${hero.stream_id}`)}
                className="inline-flex items-center gap-2 rounded-md bg-white text-black px-5 py-2.5 font-bold hover:bg-white/85 focus-ring"
              >
                <Play className="h-4 w-4" /> Play {hero.name}
              </button>
            ) : (
              <Link
                to="/live"
                className="inline-flex items-center gap-2 rounded-md bg-white text-black px-5 py-2.5 font-bold hover:bg-white/85 focus-ring"
              >
                <Play className="h-4 w-4" /> Play Live TV
              </Link>
            )}
            <Link
              to="/guide"
              className="inline-flex items-center gap-2 rounded-md bg-white/15 hover:bg-white/25 text-white px-5 py-2.5 font-bold backdrop-blur focus-ring"
            >
              <Info className="h-4 w-4" /> Open TV Guide
            </Link>
          </div>
        </div>
      </section>

      {/* Quick-access rail */}
      <section className="px-10 -mt-16 relative z-10">
        <div className="grid grid-cols-4 gap-4">
          <QuickTile to="/live" icon={Tv} label="Live TV" color="from-brand to-brand/40" />
          <QuickTile to="/sports" icon={Trophy} label="Sports Hub" color="from-live to-live/40" />
          <QuickTile to="/movies" icon={Film} label="Movies" color="from-ok to-ok/40" />
          <QuickTile to="/series" icon={Popcorn} label="Series" color="from-brand-purple to-brand-purple/40" />
        </div>
      </section>

      {/* Themed rails */}
      <section className="px-10 py-10 space-y-10">
        {!rails && movies.isLoading && <RailSkeleton title="Loading your library…" />}
        {rails && (
          <>
            <Rail title="🆕  Latest Releases" subtitle="fresh in your catalog" items={rails.latestReleases} nav={nav} />
            {rails.comingSoon.length > 0 && (
              <Rail title="🎬  Coming Soon" subtitle="counting down" items={rails.comingSoon} nav={nav} />
            )}
            {rails.topPicks.length > 0 && (
              <Rail title="⭐  Top Picks" subtitle="highest-rated in your library" items={rails.topPicks} nav={nav} />
            )}
            {rails.trending.length > 0 && (
              <Rail title="🔥  Trending on EnkTel" subtitle="everyone's watching" items={rails.trending} nav={nav} />
            )}
            {rails.newThisWeek.length > 0 && (
              <Rail title="New This Week" subtitle="added in the last 14 days" items={rails.newThisWeek} nav={nav} />
            )}
            {rails.moodFastPaced.length > 0 && (
              <Rail title="🔥  Fast-Paced Thrillers" subtitle="keep the adrenaline high" items={rails.moodFastPaced} nav={nav} />
            )}
            {rails.moodGritty.length > 0 && (
              <Rail title="🌒  Gritty & Tension-Filled" subtitle="shadowy, morally grey" items={rails.moodGritty} nav={nav} />
            )}
            {rails.moodMindBending.length > 0 && (
              <Rail title="🧠  Mind-Bending Plots" subtitle="sci-fi and mystery, top-rated" items={rails.moodMindBending} nav={nav} />
            )}
            {rails.moodLateNight.length > 0 && (
              <Rail title="🌙  Late-Night Background" subtitle="easy, comforting picks" items={rails.moodLateNight} nav={nav} />
            )}
            {rails.moodFeelGood.length > 0 && (
              <Rail title="☀️  Feel-Good & Warm-Fuzzy" subtitle="wholesome vibes" items={rails.moodFeelGood} nav={nav} />
            )}
          </>
        )}
      </section>
    </div>
  );
}

function QuickTile({
  to, icon: Icon, label, color,
}: { to: string; icon: any; label: string; color: string }) {
  return (
    <Link
      to={to}
      className={`glass rounded-xl px-5 py-4 flex items-center gap-3 hover:scale-[1.02] transition-transform focus-ring`}
    >
      <div className={`h-10 w-10 rounded-md bg-gradient-to-br ${color} grid place-items-center`}>
        <Icon className="h-5 w-5 text-white" />
      </div>
      <div>
        <div className="text-sm font-bold">{label}</div>
        <div className="text-[10px] text-textDim">Open</div>
      </div>
    </Link>
  );
}

function Rail({
  title, subtitle, items, nav,
}: {
  title: string;
  subtitle?: string;
  items: Movie[];
  nav: ReturnType<typeof useNavigate>;
}) {
  return (
    <div>
      <div className="rail-header flex items-baseline gap-3 mb-2">
        <span className="text-lg font-black tracking-tight">{title}</span>
        {subtitle && <span className="text-[10px] text-textDim uppercase tracking-widest">{subtitle}</span>}
        <span className="ml-auto text-[10px] text-textDim">{items.length}</span>
      </div>
      <div className="flex gap-3 overflow-x-auto pb-3">
        {items.map((m) => (
          <button
            key={m.stream_id}
            onClick={() => nav(`/movies?id=${m.stream_id}`)}
            className="shrink-0 w-40 rounded-lg overflow-hidden relative group text-left focus-ring"
            title={m.name}
          >
            <div className="w-40 h-56 bg-surfaceHi relative overflow-hidden">
              {m.poster ? (
                <img src={m.poster} alt={m.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform" />
              ) : (
                <div className="w-full h-full grid place-items-center text-textDim text-xs px-2 text-center">{m.name}</div>
              )}
              {m.rating > 0 && (
                <span className="absolute top-1 right-1 text-[10px] font-black bg-black/70 text-ok px-1.5 py-0.5 rounded">
                  ★ {m.rating.toFixed(1)}
                </span>
              )}
            </div>
            <div className="mt-1.5 text-xs font-semibold truncate">{m.name}</div>
            {m.year && <div className="text-[10px] text-textDim">{m.year}</div>}
          </button>
        ))}
      </div>
    </div>
  );
}

function RailSkeleton({ title }: { title: string }) {
  return (
    <div>
      <div className="rail-header flex items-baseline gap-3 mb-2">
        <span className="text-lg font-black tracking-tight">{title}</span>
      </div>
      <div className="flex gap-3 overflow-x-auto pb-3">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="shrink-0 w-40 h-56 rounded-lg bg-surfaceHi/80 overflow-hidden relative">
            <div className="absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/5 to-transparent animate-shimmer" />
          </div>
        ))}
      </div>
    </div>
  );
}
