"use client";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { MAGAZINE_TABS, type MagazineSection } from "./MagazineNav";

const BLURBS: Record<MagazineSection, string> = {
  updates: "The cover story — AI Assistant, Web Player, and everything else new across Enktel.",
  "web-player": "Watch Enktel in any browser, no app or box required — see how it works.",
  "whats-new": "This week's new movies, series, live channel highlights, and upcoming sports & PPV.",
  "latest-releases": "Browse and filter the newest movies and TV shows added to the library.",
};

interface MagazineCrossLinksProps {
  active: MagazineSection;
}

// Footer cross-promo grid shown at the bottom of each "Enktel Wire" page,
// pointing readers at the other three departments of the same magazine.
export default function MagazineCrossLinks({ active }: MagazineCrossLinksProps) {
  const others = MAGAZINE_TABS.filter((tab) => tab.id !== active);

  return (
    <div className="border-t border-brand-border/60 mt-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <p className="text-brand-secondary text-xs font-black uppercase tracking-[0.25em] mb-6">
          More from Enktel Wire
        </p>
        <div className="grid sm:grid-cols-3 gap-4">
          {others.map((tab) => (
            <Link
              key={tab.id}
              href={tab.href}
              className="group block p-5 rounded-2xl bg-brand-card border border-brand-border hover:border-brand-primary/40 transition-colors"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-white font-bold">{tab.label}</span>
                <ArrowUpRight className="w-4 h-4 text-brand-muted group-hover:text-brand-secondary transition-colors" />
              </div>
              <p className="text-brand-muted text-sm leading-relaxed">{BLURBS[tab.id]}</p>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
