import { ReactNode } from "react";
import { clsx } from "clsx";

interface BoardingPassProps {
  children: ReactNode;
  stub?: ReactNode;
  className?: string;
  light?: boolean;
}

/**
 * Airport boarding-pass styled card: a main panel plus a perforated,
 * notch-punched "stub" panel on the right — the recurring ticket motif
 * used across the site for CTAs, pricing, and the World Cup matches.
 */
export default function BoardingPass({ children, stub, className = "", light = false }: BoardingPassProps) {
  return (
    <div
      className={clsx(
        "flex flex-col sm:flex-row",
        light ? "bg-brand-card" : "bg-gradient-to-br from-brand-card to-[#0a0e1a]",
        "border border-brand-border rounded-2xl overflow-hidden",
        className
      )}
    >
      <div className="flex-1 p-6 sm:p-7">{children}</div>
      {stub && (
        <div
          className={clsx(
            "ticket-card",
            light && "ticket-card-light",
            "shrink-0 w-full sm:w-44 border-t sm:border-t-0 sm:border-l border-dashed border-white/15",
            "flex flex-col items-center justify-center gap-2 p-5 bg-black/15"
          )}
        >
          {stub}
        </div>
      )}
    </div>
  );
}
