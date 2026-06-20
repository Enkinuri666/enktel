import { Metadata } from "next";
import { Suspense } from "react";
import EPGGrid from "@/components/epg/EPGGrid";
import Spinner from "@/components/ui/Spinner";

export const metadata: Metadata = {
  title: "EPG Guide",
  description: "Electronic Program Guide — browse live TV schedules for all channels. See what's on now and coming up.",
};

export default function EPGPage() {
  return (
    <div className="max-w-full px-4 sm:px-6 lg:px-8 py-10">
      <div className="max-w-7xl mx-auto mb-8">
        <h1 className="text-4xl sm:text-5xl font-black text-white mb-3">
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
        <Suspense fallback={<Spinner className="py-20" />}>
          <EPGGrid />
        </Suspense>
      </div>
    </div>
  );
}
