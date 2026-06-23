import Link from "next/link";
import { ChevronRight, Tv } from "lucide-react";

export default function EPGTeaser() {
  return (
    <section className="py-14 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <Link
          href="/epg"
          className="group flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-brand-card border border-brand-border rounded-2xl p-6 sm:p-8 hover:border-brand-primary/40 transition-all duration-300"
        >
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-brand-primary/20 border border-brand-primary/30 flex items-center justify-center shrink-0">
              <Tv className="w-6 h-6 text-brand-primary" />
            </div>
            <div>
              <h2 className="text-xl sm:text-2xl font-bold text-white mb-1">
                Full Live TV Guide
              </h2>
              <p className="text-brand-muted text-sm">
                See what&apos;s on now and next, filter by timezone, and jump straight to a channel — all in one place.
              </p>
            </div>
          </div>
          <span className="flex items-center gap-1 text-brand-primary group-hover:text-brand-secondary transition-colors text-sm font-semibold shrink-0">
            Open EPG Guide <ChevronRight className="w-4 h-4" />
          </span>
        </Link>
      </div>
    </section>
  );
}
