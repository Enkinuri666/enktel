import Link from "next/link";
import { LucideIcon, ArrowRight } from "lucide-react";

interface PromoStripProps {
  icon: LucideIcon;
  eyebrow: string;
  title: string;
  subtitle: string;
  ctaLabel: string;
  ctaHref: string;
  theme?: "primary" | "cyan" | "gold";
}

const themes = {
  primary: { glow: "rgba(108,99,255,0.18)", text: "text-brand-primary", border: "border-brand-primary/25" },
  cyan: { glow: "rgba(0,212,255,0.18)", text: "text-brand-secondary", border: "border-brand-secondary/25" },
  gold: { glow: "rgba(245,158,11,0.18)", text: "text-amber-400", border: "border-amber-400/25" },
};

export default function PromoStrip({ icon: Icon, eyebrow, title, subtitle, ctaLabel, ctaHref, theme = "primary" }: PromoStripProps) {
  const t = themes[theme];
  return (
    <section className="py-6 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <Link href={ctaHref} className="group block">
          <div
            className={`relative overflow-hidden neon-edge rounded-2xl px-6 py-5 sm:px-8 sm:py-6 flex flex-col sm:flex-row items-center gap-4 sm:gap-6 ${t.border}`}
            style={{ background: `radial-gradient(ellipse 80% 100% at 0% 0%, ${t.glow}, transparent 60%), rgba(13,18,32,0.7)` }}
          >
            <div className={`icon-glow w-12 h-12 rounded-xl flex items-center justify-center shrink-0 bg-white/5 ${t.border} border`}>
              <Icon className={`w-6 h-6 ${t.text}`} />
            </div>
            <div className="flex-1 min-w-0 text-center sm:text-left">
              <p className={`text-xs font-bold uppercase tracking-widest ${t.text} mb-0.5`}>{eyebrow}</p>
              <h3 className="text-white font-bold text-base sm:text-lg leading-snug">{title}</h3>
              <p className="text-brand-muted text-sm">{subtitle}</p>
            </div>
            <div className={`shrink-0 flex items-center gap-1.5 text-sm font-semibold ${t.text} group-hover:gap-2.5 transition-all`}>
              {ctaLabel} <ArrowRight className="w-4 h-4" />
            </div>
          </div>
        </Link>
      </div>
    </section>
  );
}
