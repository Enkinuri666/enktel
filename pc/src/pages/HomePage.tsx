import { Link } from 'react-router-dom';
import { Play, Info, Tv, Trophy, Film, Popcorn } from 'lucide-react';

/**
 * Home hero. Full-bleed splash art (placeholder gradient until live data
 * arrives), a big Play button, three quick-access glass tiles for Live TV,
 * Sports and Movies, plus continuation-rails placeholder.
 */
export default function HomePage() {
  return (
    <div className="min-h-full">
      {/* Hero */}
      <section className="relative h-[62vh] min-h-[420px] overflow-hidden">
        <div
          className="absolute inset-0"
          style={{
            background:
              'linear-gradient(120deg, #1B6AE5 0%, #3B9DFF 30%, #8B5CF6 70%, #0A0E17 100%)',
          }}
        />
        <div className="absolute inset-0 bg-hero-fade" />
        <div className="absolute inset-0 flex flex-col justify-end p-10">
          <span className="text-[11px] font-black tracking-widest text-brand mb-2">
            ▶ FEATURED
          </span>
          <h1 className="text-5xl font-black leading-tight mb-3 max-w-2xl">
            Welcome to EnkTel IPTV
          </h1>
          <p className="text-textDim max-w-xl mb-6">
            Your entire library, sports lineup and channel guide — reimagined for the
            desktop. Premium Live TV, movies, series, catch-up and DVR, all in one
            lightweight app.
          </p>
          <div className="flex gap-3">
            <Link to="/live" className="inline-flex items-center gap-2 rounded-md bg-white text-black px-5 py-2.5 font-bold hover:bg-white/85 focus-ring">
              <Play className="h-4 w-4" /> Play Live TV
            </Link>
            <Link to="/guide" className="inline-flex items-center gap-2 rounded-md bg-white/15 hover:bg-white/25 text-white px-5 py-2.5 font-bold backdrop-blur focus-ring">
              <Info className="h-4 w-4" /> Open TV Guide
            </Link>
          </div>
        </div>
      </section>

      {/* Quick-access rail */}
      <section className="px-10 -mt-16 relative z-10">
        <div className="grid grid-cols-4 gap-4">
          <QuickTile to="/live"    icon={Tv}      label="Live TV"    color="from-brand to-brand/40"/>
          <QuickTile to="/sports"  icon={Trophy}  label="Sports Hub" color="from-live to-live/40" />
          <QuickTile to="/movies"  icon={Film}    label="Movies"     color="from-ok to-ok/40"     />
          <QuickTile to="/series"  icon={Popcorn} label="Series"     color="from-brand-purple to-brand-purple/40" />
        </div>
      </section>

      {/* Placeholders for content rails — the real data is loaded via
          React Query once we've wired the Xtream / M3U client. */}
      <section className="px-10 py-10 space-y-8">
        <Rail title="Continue Watching" />
        <Rail title="Latest Releases" />
        <Rail title="Trending on EnkTel" />
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

function Rail({ title }: { title: string }) {
  return (
    <div>
      <div className="rail-header">
        <span className="text-lg font-black tracking-tight">{title}</span>
        <span className="text-[10px] text-textDim">Placeholder</span>
      </div>
      <div className="flex gap-3 overflow-x-auto pb-3">
        {Array.from({ length: 8 }).map((_, i) => (
          <div
            key={i}
            className="shrink-0 w-40 h-56 rounded-lg bg-surfaceHi/80 overflow-hidden relative"
          >
            <div className="absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/5 to-transparent animate-shimmer" />
          </div>
        ))}
      </div>
    </div>
  );
}
