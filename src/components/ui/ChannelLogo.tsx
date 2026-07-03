"use client";
import { useState } from "react";
import { clsx } from "clsx";

// Curated gradient pairs — picked deterministically per channel so the
// channel wall looks like a colourful logo grid instead of broken images.
const palettes: [string, string][] = [
  ["#6C63FF", "#5348d4"],
  ["#00D4FF", "#0EA5E9"],
  ["#FF4757", "#E11D48"],
  ["#22C55E", "#16A34A"],
  ["#F59E0B", "#D97706"],
  ["#A855F7", "#7E22CE"],
  ["#EC4899", "#DB2777"],
  ["#14B8A6", "#0D9488"],
  ["#3B82F6", "#2563EB"],
];

function hash(str: string): number {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) | 0;
  return Math.abs(h);
}

// Produces a short, readable badge label: prefers an existing acronym
// (BBC, HRT, RTL…), keeps a trailing channel number (HRT 1 → HRT1), and
// otherwise falls back to initials or the first letters of a single word.
function abbreviate(name: string): string {
  const words = name.replace(/\bHD\b/gi, "").replace(/\s+/g, " ").trim().split(" ").filter(Boolean);

  const acronym = words.find(
    (w) => w !== "TV" && w.length >= 2 && w.length <= 4 && w === w.toUpperCase() && /[A-Z]/.test(w)
  );
  if (acronym) {
    const next = words[words.indexOf(acronym) + 1];
    if (next && /^\d+$/.test(next)) return (acronym + next).slice(0, 5);
    return acronym;
  }

  const meaningful = words.filter((w) => w.toUpperCase() !== "TV");
  if (meaningful.length <= 1) return (meaningful[0] || name).slice(0, 4).toUpperCase();
  return meaningful
    .map((w) => (/^\d+$/.test(w) ? w : w[0]))
    .join("")
    .slice(0, 4)
    .toUpperCase();
}

const sizes = {
  sm: "w-8 h-8 text-[9px] rounded-lg",
  md: "w-12 h-12 text-[11px] rounded-xl",
  lg: "w-14 h-14 text-sm rounded-2xl",
};

interface ChannelLogoProps {
  name: string;
  id?: string;
  logoUrl?: string;
  size?: keyof typeof sizes;
  className?: string;
}

// Real channel logos come from many different third-party hosts (varying
// per EPG data update), so a plain <img> is used here rather than
// next/image, which would require every host pre-registered in
// next.config.js. Falls back to the generated gradient badge below if
// there's no logo, or if the real one fails to load.
export default function ChannelLogo({ name, id, logoUrl, size = "md", className }: ChannelLogoProps) {
  const [imageFailed, setImageFailed] = useState(false);

  if (logoUrl && !imageFailed) {
    return (
      <div
        className={clsx(
          "flex items-center justify-center shrink-0 overflow-hidden bg-white/5 border border-white/10 p-1",
          sizes[size],
          className
        )}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={logoUrl}
          alt={name}
          title={name}
          className="w-full h-full object-contain"
          onError={() => setImageFailed(true)}
          loading="lazy"
        />
      </div>
    );
  }

  const [from, to] = palettes[hash(id || name) % palettes.length];
  return (
    <div
      role="img"
      aria-label={name}
      title={name}
      className={clsx(
        "flex items-center justify-center font-black text-white shrink-0 tracking-tight select-none",
        sizes[size],
        className
      )}
      style={{
        background: `linear-gradient(135deg, ${from}, ${to})`,
        boxShadow: `0 2px 10px ${from}40`,
      }}
    >
      {abbreviate(name)}
    </div>
  );
}
