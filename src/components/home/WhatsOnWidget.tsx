"use client";
import Link from "next/link";
import useSWR from "swr";
import { ChevronRight, Radio } from "lucide-react";
import Spinner from "@/components/ui/Spinner";
import { WhatsOnItem } from "@/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

export default function WhatsOnWidget() {
  const { data, isLoading } = useSWR<{ items: WhatsOnItem[] }>(
    "/api/whats-on",
    fetcher,
    { refreshInterval: 60000 }
  );

  const items = data?.items?.slice(0, 6) || [];

  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <div className="w-2 h-2 bg-brand-accent rounded-full animate-pulse" />
            <h2 className="text-2xl sm:text-3xl font-bold text-white">
              What&apos;s On <span className="text-brand-accent">Now</span>
            </h2>
          </div>
          <Link
            href="/whats-on"
            className="flex items-center gap-1 text-brand-primary hover:text-brand-secondary transition-colors text-sm font-medium"
          >
            See all <ChevronRight className="w-4 h-4" />
          </Link>
        </div>

        {isLoading ? (
          <Spinner className="py-12" />
        ) : items.length === 0 ? (
          <p className="text-brand-muted text-center py-12">No live programs found.</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {items.map((item) => (
              <div
                key={item.channel.id}
                className="bg-brand-card border border-brand-border rounded-xl p-4 hover:border-brand-primary/40 transition-all duration-300"
              >
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-2 min-w-0">
                    <Radio className="w-4 h-4 text-brand-accent shrink-0" />
                    <span className="text-brand-muted text-xs font-medium truncate">
                      {item.channel.name}
                    </span>
                  </div>
                  <span className="bg-brand-accent/20 text-brand-accent border border-brand-accent/30 text-xs font-bold px-2 py-0.5 rounded-full shrink-0 ml-2">
                    LIVE
                  </span>
                </div>
                <h3 className="text-white font-semibold text-sm mb-1 line-clamp-1">
                  {item.currentProgram.title}
                </h3>
                <p className="text-brand-muted text-xs mb-3 line-clamp-2">
                  {item.currentProgram.description}
                </p>
                {/* Progress bar */}
                <div className="mb-2">
                  <div className="h-1 bg-brand-border rounded-full overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-brand-primary to-brand-secondary rounded-full transition-all"
                      style={{ width: `${Math.min(item.progressPercent, 100)}%` }}
                    />
                  </div>
                </div>
                <div className="flex items-center justify-between text-xs text-brand-muted">
                  <span>{formatTime(item.currentProgram.startTime)}</span>
                  <span>{item.progressPercent}%</span>
                  <span>{formatTime(item.currentProgram.endTime)}</span>
                </div>
                {item.nextProgram && (
                  <div className="mt-3 pt-3 border-t border-brand-border">
                    <span className="text-brand-muted text-xs">
                      Up next: <span className="text-white">{item.nextProgram.title}</span>
                    </span>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
