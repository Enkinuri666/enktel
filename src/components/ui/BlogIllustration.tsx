import { clsx } from "clsx";

const PALETTES = [
  { from: "#2F6FFF", to: "#0a1330" },
  { from: "#1FD8F2", to: "#081a24" },
  { from: "#CE2C1A", to: "#1a0a08" },
  { from: "#7C3AED", to: "#100a24" },
  { from: "#F59E0B", to: "#1c1408" },
];

function hashSeed(seed: string): number {
  let h = 0;
  for (let i = 0; i < seed.length; i++) {
    h = (h << 5) - h + seed.charCodeAt(i);
    h |= 0;
  }
  return Math.abs(h);
}

interface BlogIllustrationProps {
  seed: string;
  icon: string;
  label?: string;
  className?: string;
}

/**
 * Deterministic, branded "header art" for blog posts that don't have a real
 * media backdrop (editorial reviews/guides/weekly issues) — same seed always
 * produces the same look, and the aspect ratio matches the TMDB post cards
 * so every header image across the blog is the same size.
 */
export default function BlogIllustration({ seed, icon, label, className = "" }: BlogIllustrationProps) {
  const hash = hashSeed(seed);
  const palette = PALETTES[hash % PALETTES.length];
  const rotation = (hash % 7) - 3;

  return (
    <div
      className={clsx("relative aspect-[16/9] overflow-hidden", className)}
      style={{ background: `linear-gradient(135deg, ${palette.from}33 0%, ${palette.to} 70%)` }}
    >
      <div
        className="absolute checker-trim inset-x-0 top-0 h-1.5 opacity-60"
        style={{ backgroundColor: palette.from }}
      />
      <span
        className="absolute text-[5.5rem] sm:text-[6.5rem] leading-none opacity-15 select-none"
        style={{ right: "-0.5rem", bottom: "-1.25rem", transform: `rotate(${rotation}deg)` }}
      >
        {icon}
      </span>
      <span className="absolute top-3 left-3 text-2xl leading-none drop-shadow">{icon}</span>
      {label && (
        <span
          className="absolute bottom-3 left-3 text-[10px] font-bold uppercase tracking-widest font-mono-flight px-2 py-1 rounded-full"
          style={{ background: "rgba(0,0,0,0.35)", color: palette.from }}
        >
          {label}
        </span>
      )}
      <div className="barcode-strip absolute bottom-0 left-0 right-0 opacity-20" style={{ height: "0.6rem" }} />
    </div>
  );
}
