import { Play, Volume2, Maximize, Settings } from "lucide-react";

const sidebar = ["HRT 1", "Nova TV", "RTL Hrvatska", "Arena Sport 1", "BBC One", "Sky Sports"];

export default function PlayerMockup() {
  return (
    <div className="absolute inset-0 flex">
      <div className="flex-1 relative bg-gradient-to-br from-[#141935] via-[#0A0E17] to-[#0A0E17] flex items-center justify-center">
        <div className="w-11 h-11 sm:w-12 sm:h-12 rounded-full bg-white/10 border border-white/20 flex items-center justify-center backdrop-blur">
          <Play className="w-5 h-5 text-white fill-white ml-0.5" />
        </div>
        <div className="absolute top-2.5 left-2.5 bg-black/50 backdrop-blur px-2 py-1 rounded text-[9px] text-white font-semibold flex items-center gap-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-brand-accent animate-pulse" /> LIVE · HRT 1
        </div>
        <div className="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/80 to-transparent px-2.5 pt-6 pb-2">
          <p className="text-white text-[9px] font-semibold truncate">Dnevnik HRT — 19:30</p>
          <div className="flex items-center justify-between mt-1.5">
            <div className="h-0.5 flex-1 bg-white/20 rounded-full mr-2">
              <div className="h-full w-2/5 bg-brand-secondary rounded-full" />
            </div>
            <div className="flex items-center gap-1.5 text-white/80 shrink-0">
              <Volume2 className="w-2.5 h-2.5" />
              <Settings className="w-2.5 h-2.5" />
              <Maximize className="w-2.5 h-2.5" />
            </div>
          </div>
        </div>
      </div>
      <div className="w-[72px] sm:w-20 bg-[#0D1220] border-l border-brand-border p-1.5 space-y-1 overflow-hidden shrink-0">
        <p className="text-brand-muted text-[7px] font-bold uppercase tracking-wider px-1 mb-1">Channels</p>
        {sidebar.map((ch, i) => (
          <div
            key={ch}
            className={`text-[7px] font-medium px-1.5 py-1 rounded truncate ${
              i === 0 ? "bg-brand-primary/25 text-white border border-brand-primary/40" : "text-brand-muted"
            }`}
          >
            {ch}
          </div>
        ))}
      </div>
    </div>
  );
}
