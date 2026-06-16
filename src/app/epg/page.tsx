import { Metadata } from "next";
import EPGGrid from "@/components/epg/EPGGrid";

export const metadata: Metadata = {
  title: "EPG Guide",
  description: "Electronic Program Guide — browse live TV schedules for all channels. See what's on now and coming up.",
};

export default function EPGPage() {
  return (
    <div className="max-w-full px-4 sm:px-6 lg:px-8 py-10">
      <div className="max-w-7xl mx-auto mb-8">
        <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">
          EPG{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Guide
          </span>
        </h1>
        <p className="text-brand-muted text-lg">
          Browse the Electronic Program Guide. See what&apos;s on now and coming up across all channels.
        </p>
      </div>
      <div className="max-w-full">
        <EPGGrid />
      </div>
    </div>
  );
}
