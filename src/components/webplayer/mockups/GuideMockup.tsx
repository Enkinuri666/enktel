const rows = [
  { ch: "HRT 1", now: "Dnevnik", now_w: "38%", next: "Potjera" },
  { ch: "Nova TV", now: "Superstar", now_w: "58%", next: "IN Magazin" },
  { ch: "Arena Sport 1", now: "HNL: Live", now_w: "72%", next: "Sport Klub" },
  { ch: "BBC One", now: "News at Six", now_w: "22%", next: "EastEnders" },
  { ch: "Sky Sports", now: "Premier League", now_w: "45%", next: "Match Replay" },
];

export default function GuideMockup() {
  return (
    <div className="absolute inset-0 flex flex-col p-3 sm:p-4">
      <div className="flex items-center justify-between mb-2.5 shrink-0">
        <span className="text-white text-[11px] font-bold">Live TV Guide</span>
        <div className="flex gap-1">
          <span className="bg-brand-primary/20 border border-brand-primary/40 text-brand-primary text-[9px] font-semibold px-2 py-0.5 rounded-full">Now</span>
          <span className="bg-white/5 border border-white/10 text-brand-muted text-[9px] font-semibold px-2 py-0.5 rounded-full">Next</span>
        </div>
      </div>
      <div className="flex-1 space-y-1.5 overflow-hidden">
        {rows.map((r) => (
          <div key={r.ch} className="flex items-center gap-2 bg-white/[0.03] border border-white/5 rounded-md px-2 py-1.5">
            <span className="text-white text-[9px] font-semibold w-16 shrink-0 truncate">{r.ch}</span>
            <div className="flex-1 flex gap-1 min-w-0">
              <div className="h-4 rounded bg-brand-primary/30 border border-brand-primary/50 flex items-center px-1.5" style={{ width: r.now_w }}>
                <span className="text-[8px] text-white truncate">{r.now}</span>
              </div>
              <div className="h-4 rounded bg-white/5 border border-white/10 flex items-center px-1.5 flex-1 min-w-0">
                <span className="text-[8px] text-brand-muted truncate">{r.next}</span>
              </div>
            </div>
          </div>
        ))}
      </div>
      <div className="mt-2 h-px bg-white/5 relative shrink-0">
        <div className="absolute -top-2 left-[28%] w-px h-3 bg-brand-accent" />
        <div className="absolute -top-3 left-[28%] -translate-x-1/2 text-[7px] text-brand-accent font-bold">NOW</div>
      </div>
    </div>
  );
}
