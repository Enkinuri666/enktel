const rowA = ["HRT 1", "Nova TV", "RTL Hrvatska", "Sky Sports", "BBC One", "CNN", "Eurosport", "Doma TV", "N1 Info", "beIN Sports"];
const rowB = ["CMC TV", "RTS 1", "FTV", "Premier Sports", "Cartoon Network", "HBO", "Discovery", "Hayat TV", "POP TV", "24sata TV"];

function MarqueeRow({ items, reverse }: { items: string[]; reverse?: boolean }) {
  return (
    <div className="flex overflow-hidden select-none">
      <div className={`flex shrink-0 gap-3 pr-3 ${reverse ? "animate-marquee-reverse" : "animate-marquee"}`}>
        {[...items, ...items].map((name, i) => (
          <span
            key={`${name}-${i}`}
            className="cyber-panel rounded-full px-4 py-1.5 text-xs font-semibold text-brand-muted whitespace-nowrap"
          >
            {name}
          </span>
        ))}
      </div>
    </div>
  );
}

export default function ChannelMarquee() {
  return (
    <section className="relative py-10 overflow-hidden cyber-grid">
      <div
        className="absolute inset-0 pointer-events-none"
        style={{ background: "linear-gradient(90deg, #060910 0%, transparent 12%, transparent 88%, #060910 100%)" }}
      />
      <div className="space-y-3">
        <MarqueeRow items={rowA} />
        <MarqueeRow items={rowB} reverse />
      </div>
    </section>
  );
}
