"use client";
import Link from "next/link";
import { Radio } from "lucide-react";

export type MagazineSection = "updates" | "web-player" | "whats-new" | "latest-releases";

export const MAGAZINE_TABS: { id: MagazineSection; href: string; label: string }[] = [
  { id: "updates", href: "/updates", label: "Ecosystem Update" },
  { id: "web-player", href: "/web-player", label: "Web Player" },
  { id: "whats-new", href: "/whats-new", label: "What's New" },
  { id: "latest-releases", href: "/latest-releases", label: "Latest Releases" },
];

interface MagazineNavProps {
  active: MagazineSection;
  kicker: string;
  title: React.ReactNode;
  description: string;
}

// Shared masthead + section tabs for the four "Enktel Wire" pages, so
// visitors experience them as departments of one magazine rather than four
// unrelated pages. Each page keeps its own body/functionality — this just
// standardizes the header and cross-navigation.
export default function MagazineNav({ active, kicker, title, description }: MagazineNavProps) {
  return (
    <div className="border-b border-brand-border/60 mb-10">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6 pb-8">
        <div className="flex items-center justify-between flex-wrap gap-3 mb-6">
          <div className="flex items-center gap-2">
            <Radio className="w-4 h-4 text-brand-secondary" />
            <span className="text-brand-secondary text-xs font-black uppercase tracking-[0.25em]">Enktel Wire</span>
          </div>
          <span className="text-brand-muted text-xs">Ecosystem news, product updates &amp; what&apos;s new to watch</span>
        </div>

        <div className="flex items-center gap-1 mb-8 overflow-x-auto -mx-1 px-1 scrollbar-thin">
          {MAGAZINE_TABS.map((tab) => (
            <Link
              key={tab.id}
              href={tab.href}
              className={`shrink-0 px-4 py-2 rounded-full text-sm font-semibold whitespace-nowrap transition-colors ${
                tab.id === active
                  ? "bg-brand-primary text-white shadow-lg shadow-brand-primary/25"
                  : "bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40"
              }`}
            >
              {tab.label}
            </Link>
          ))}
        </div>

        <p className="text-brand-secondary text-sm font-bold uppercase tracking-widest mb-3">{kicker}</p>
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-3 leading-tight">{title}</h1>
        <p className="text-brand-muted text-lg max-w-2xl">{description}</p>
      </div>
    </div>
  );
}
