const countries = [
  { flag: "🇭🇷", name: "Croatia" },
  { flag: "🇧🇦", name: "Bosnia" },
  { flag: "🇷🇸", name: "Serbia" },
  { flag: "🇸🇮", name: "Slovenia" },
  { flag: "🇲🇰", name: "N. Macedonia" },
  { flag: "🇲🇪", name: "Montenegro" },
  { flag: "🇬🇧", name: "United Kingdom" },
  { flag: "🇩🇪", name: "Germany" },
  { flag: "🇦🇹", name: "Austria" },
  { flag: "🇨🇭", name: "Switzerland" },
  { flag: "🇮🇹", name: "Italy" },
  { flag: "🇫🇷", name: "France" },
  { flag: "🇪🇸", name: "Spain" },
  { flag: "🇳🇱", name: "Netherlands" },
  { flag: "🇺🇸", name: "USA" },
  { flag: "🇦🇺", name: "Australia" },
  { flag: "🇨🇦", name: "Canada" },
  { flag: "🇸🇪", name: "Sweden" },
  { flag: "🇳🇴", name: "Norway" },
  { flag: "🇩🇰", name: "Denmark" },
];

export default function CountryBanner() {
  const doubled = [...countries, ...countries];
  return (
    <div className="bg-brand-card/60 border-y border-brand-border/50 py-4 overflow-hidden">
      <div className="flex items-center gap-3 mb-2 px-6">
        <div className="h-px flex-1 bg-brand-border/50" />
        <span className="text-brand-muted text-xs font-semibold uppercase tracking-widest">Channels from 50+ Countries</span>
        <div className="h-px flex-1 bg-brand-border/50" />
      </div>
      <div className="relative overflow-hidden">
        <div className="flex animate-marquee whitespace-nowrap gap-0">
          {doubled.map((c, i) => (
            <div key={i} className="inline-flex items-center gap-2 px-6 py-1 shrink-0">
              <span className="text-2xl">{c.flag}</span>
              <span className="text-brand-muted text-sm font-medium">{c.name}</span>
              <span className="text-brand-border/50 ml-4">•</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
