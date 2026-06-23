import { clsx } from "clsx";

export interface FlightBoardRow {
  code: string;
  destination: string;
  gate: string;
  status: "LIVE" | "BOARDING" | "ON TIME" | "SOON";
  href?: string;
}

const STATUS_STYLES: Record<FlightBoardRow["status"], string> = {
  LIVE: "text-brand-accent",
  BOARDING: "text-amber-400",
  "ON TIME": "text-green-400",
  SOON: "text-brand-secondary",
};

/** Airport split-flap departures board, repurposed to show live/upcoming content as "flights". */
export default function FlightBoard({ rows, className = "" }: { rows: FlightBoardRow[]; className?: string }) {
  return (
    <div className={clsx("bg-[#05070c] border border-brand-border rounded-2xl overflow-hidden", className)}>
      <div className="flex items-center justify-between px-4 py-2.5 bg-amber-400/10 border-b border-amber-400/20">
        <span className="text-amber-400 text-[11px] font-bold font-mono-flight tracking-widest">✈ DEPARTURES</span>
        <span className="text-brand-muted text-[11px] font-mono-flight">ENKTEL TERMINAL 1</span>
      </div>
      <div className="grid grid-cols-[3.2rem_1fr_3.2rem_4.5rem] sm:grid-cols-[4rem_1fr_4rem_5.5rem] gap-2 px-4 py-2 text-[10px] uppercase tracking-wider text-brand-muted border-b border-white/5 font-mono-flight">
        <span>Code</span>
        <span>Programme</span>
        <span>Gate</span>
        <span className="text-right">Status</span>
      </div>
      <div className="divide-y divide-white/5">
        {rows.map((row, i) => {
          const Row = (
            <div
              className="board-row grid grid-cols-[3.2rem_1fr_3.2rem_4.5rem] sm:grid-cols-[4rem_1fr_4rem_5.5rem] gap-2 px-4 py-2.5 items-center font-mono-flight flap-in"
              style={{ animationDelay: `${i * 60}ms` }}
            >
              <span className="text-white font-bold text-xs sm:text-sm">{row.code}</span>
              <span className="text-white/90 text-xs sm:text-sm truncate">{row.destination}</span>
              <span className="text-brand-muted text-xs">{row.gate}</span>
              <span className={clsx("text-right text-[11px] sm:text-xs font-bold", STATUS_STYLES[row.status])}>
                {row.status === "LIVE" ? (
                  <span className="inline-flex items-center gap-1 justify-end">
                    <span className="w-1.5 h-1.5 rounded-full bg-brand-accent animate-pulse" /> {row.status}
                  </span>
                ) : (
                  row.status
                )}
              </span>
            </div>
          );
          return row.href ? (
            <a key={row.code + i} href={row.href} className="block">
              {Row}
            </a>
          ) : (
            <div key={row.code + i}>{Row}</div>
          );
        })}
      </div>
      <div className="barcode-strip opacity-30" />
    </div>
  );
}
