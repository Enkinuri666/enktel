const categories = ["All", "Croatian & Balkan", "Sports", "UK & Ireland", "Movies"];
const tiles = ["HRT 1", "HRT 2", "Nova TV", "RTL HR", "Arena Sport 1", "SportKlub 1", "BBC One", "Sky Sports", "Cinemax"];

export default function ChannelsMockup() {
  return (
    <div className="absolute inset-0 flex flex-col p-3 sm:p-4">
      <div className="flex items-center gap-1.5 mb-3 shrink-0 overflow-hidden">
        {categories.map((c, i) => (
          <span
            key={c}
            className={`text-[8px] font-semibold px-2 py-1 rounded-full whitespace-nowrap ${
              i === 0 ? "bg-brand-primary text-white" : "bg-white/5 text-brand-muted border border-white/10"
            }`}
          >
            {c}
          </span>
        ))}
      </div>
      <div className="grid grid-cols-3 gap-1.5 flex-1">
        {tiles.map((t) => (
          <div
            key={t}
            className="bg-white/[0.03] border border-white/5 rounded-md flex flex-col items-center justify-center gap-1 py-2"
          >
            <div className="w-5 h-5 rounded bg-gradient-to-br from-brand-primary to-brand-secondary" />
            <span className="text-[7px] text-white font-medium truncate px-1">{t}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
