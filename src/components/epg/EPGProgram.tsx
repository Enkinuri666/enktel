"use client";
import { useState } from "react";
import { EPGProgram as EPGProgramType } from "@/types";
import { clsx } from "clsx";

interface EPGProgramProps {
  program: EPGProgramType;
  widthPx: number;
  isCurrentlyAiring: boolean;
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

export default function EPGProgramCell({ program, widthPx, isCurrentlyAiring }: EPGProgramProps) {
  const [showTooltip, setShowTooltip] = useState(false);

  return (
    <div
      className="relative shrink-0"
      style={{ width: `${widthPx}px` }}
      onMouseEnter={() => setShowTooltip(true)}
      onMouseLeave={() => setShowTooltip(false)}
    >
      <div
        className={clsx(
          "h-full mx-0.5 rounded-lg border px-2 py-1 overflow-hidden cursor-pointer transition-colors",
          isCurrentlyAiring
            ? "border-brand-primary/70 bg-brand-primary/20 hover:bg-brand-primary/30"
            : "border-brand-border bg-brand-card/70 hover:border-brand-primary/40 hover:bg-brand-card"
        )}
      >
        {widthPx > 60 && (
          <>
            {isCurrentlyAiring && (
              <span className="inline-block bg-brand-accent text-white text-xs font-bold px-1 py-0.5 rounded mb-0.5 leading-none">
                LIVE
              </span>
            )}
            <div className="text-white text-xs font-medium truncate leading-tight">
              {program.title}
            </div>
            {widthPx > 100 && (
              <div className="text-brand-muted text-xs truncate">
                {formatTime(program.startTime)}
              </div>
            )}
          </>
        )}
      </div>

      {/* Tooltip */}
      {showTooltip && (
        <div className="absolute bottom-full left-0 z-50 mb-2 w-64 cyber-panel rounded-xl p-3 shadow-xl shadow-black/50 pointer-events-none">
          <div className="flex items-start justify-between gap-2 mb-1">
            <h4 className="text-white font-semibold text-sm leading-tight">{program.title}</h4>
            {isCurrentlyAiring && (
              <span className="bg-brand-accent text-white text-xs font-bold px-1.5 py-0.5 rounded shrink-0">LIVE</span>
            )}
          </div>
          <div className="text-brand-muted text-xs mb-2">
            {formatTime(program.startTime)} – {formatTime(program.endTime)}
            {program.rating && <span className="ml-2 bg-white/10 px-1 rounded">{program.rating}</span>}
          </div>
          {program.description && (
            <p className="text-brand-muted text-xs leading-relaxed line-clamp-3">{program.description}</p>
          )}
        </div>
      )}
    </div>
  );
}
