import { Metadata } from "next";
import { Tv, Keyboard, Rewind } from "lucide-react";
import LivePlayer from "@/components/player/LivePlayer";

export const metadata: Metadata = {
  title: "Live Player Preview — Try the Enktel Web Player",
  description:
    "An interactive preview of the Enktel Web Player: browse real channels, see what's on, and rewind into the day's schedule with the catch-up timeline. Fully navigable with a TV remote on Fire TV and Android TV browsers.",
};

export default function PlayerPreviewPage() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-8">
        <div className="flex items-center gap-2 mb-3">
          <Tv className="w-5 h-5 text-brand-secondary" />
          <span className="text-brand-secondary text-sm font-bold uppercase tracking-wide">Interactive Preview</span>
        </div>
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-3">
          Try the{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Enktel Web Player
          </span>
        </h1>
        <p className="text-brand-muted text-lg max-w-2xl mb-4">
          A live look at the real experience — real channels, a real programme guide, and the new rewind/catch-up
          timeline. This preview page doesn&apos;t stream a subscriber&apos;s actual feed (that needs your own login,
          at watch.enktel.tv), but everything else here — switching channels, browsing the guide, and scrubbing
          through the day — is real and fully working.
        </p>
        <div className="flex flex-wrap gap-3">
          <span className="inline-flex items-center gap-1.5 bg-brand-card border border-brand-border rounded-full px-3 py-1.5 text-xs text-brand-muted">
            <Keyboard className="w-3.5 h-3.5 text-brand-secondary" /> Arrow keys + Enter — works with a TV remote
          </span>
          <span className="inline-flex items-center gap-1.5 bg-brand-card border border-brand-border rounded-full px-3 py-1.5 text-xs text-brand-muted">
            <Tv className="w-3.5 h-3.5 text-brand-secondary" /> Built for Fire TV &amp; Android TV browsers
          </span>
          <span className="inline-flex items-center gap-1.5 bg-brand-card border border-brand-border rounded-full px-3 py-1.5 text-xs text-brand-muted">
            <Rewind className="w-3.5 h-3.5 text-brand-secondary" /> Rewind up to the last few hours per channel
          </span>
        </div>
      </div>

      <LivePlayer />
    </div>
  );
}
