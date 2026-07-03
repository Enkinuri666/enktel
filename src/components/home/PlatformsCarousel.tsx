"use client";
import { motion } from "framer-motion";

// Wordmark-only chips (no third-party logo artwork) — consistent with how
// channel/platform names are referenced elsewhere on the site (see
// CroatianPromo, PromoPlayer). Avoids implying partnership/endorsement that
// copied brand logos could suggest.
const STREAMING_PLATFORMS = [
  "Netflix", "Disney+", "Amazon Prime Video", "HBO Max", "Apple TV+",
  "Paramount+", "Peacock", "Hulu", "discovery+", "Crunchyroll", "BritBox", "Now TV",
];

const SPORTS_NETWORKS = [
  "Sky Sports", "TNT Sports", "ESPN", "beIN Sports", "DAZN", "Fox Sports",
  "NBC Sports", "Eurosport", "Arena Sport", "SportKlub", "Setanta Sports", "Premier Sports",
];

function MarqueeRow({ items, reverse = false, dotColor }: { items: string[]; reverse?: boolean; dotColor: string }) {
  const doubled = [...items, ...items];
  return (
    <div
      className="flex overflow-hidden"
      style={{
        maskImage: "linear-gradient(90deg, transparent 0%, #000 8%, #000 92%, transparent 100%)",
        WebkitMaskImage: "linear-gradient(90deg, transparent 0%, #000 8%, #000 92%, transparent 100%)",
      }}
    >
      <div className={`flex items-center gap-3 shrink-0 py-1 ${reverse ? "animate-marquee-reverse" : "animate-marquee"}`}>
        {doubled.map((name, i) => (
          <div
            key={`${name}-${i}`}
            className="flex items-center gap-2.5 bg-brand-card border border-brand-border rounded-xl px-5 py-3 shrink-0 whitespace-nowrap"
          >
            <span className="w-1.5 h-1.5 rounded-full shrink-0" style={{ background: dotColor }} />
            <span className="text-white font-bold text-sm tracking-tight">{name}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function PlatformsCarousel() {
  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8 overflow-hidden">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-10">
          <motion.p initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-brand-secondary text-sm font-bold uppercase tracking-widest mb-3">
            So Much More Than Live TV
          </motion.p>
          <motion.h2 initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="text-3xl sm:text-5xl font-black text-white mb-4">
            Every Platform.{" "}
            <span className="bg-gradient-to-r from-brand-secondary to-brand-primary bg-clip-text text-transparent">Every Match.</span>
          </motion.h2>
          <motion.p initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.1 }} className="text-brand-muted text-lg max-w-2xl mx-auto">
            Top international streaming platforms and the sports networks that carry the biggest leagues — all included in your channel lineup.
          </motion.p>
        </div>

        <motion.div initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} transition={{ delay: 0.15 }} className="space-y-4">
          <div>
            <p className="text-brand-muted text-xs font-bold uppercase tracking-widest mb-3 px-1">Streaming Platforms</p>
            <MarqueeRow items={STREAMING_PLATFORMS} dotColor="#00D4FF" />
          </div>
          <div>
            <p className="text-brand-muted text-xs font-bold uppercase tracking-widest mb-3 px-1">Sports Networks</p>
            <MarqueeRow items={SPORTS_NETWORKS} reverse dotColor="#FF4757" />
          </div>
        </motion.div>

        <p className="text-brand-muted/50 text-xs text-center mt-8 max-w-2xl mx-auto">
          All trademarks, channel names, and brand names belong to their respective owners. Enktel IPTV is an independent reseller service and is not affiliated with or endorsed by the platforms or networks listed above.
        </p>
      </div>
    </section>
  );
}
