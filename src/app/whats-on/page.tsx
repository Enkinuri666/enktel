"use client";
import { useState } from "react";
import useSWR from "swr";
import { Radio } from "lucide-react";
import { WhatsOnItem } from "@/types";
import Spinner from "@/components/ui/Spinner";
import { channelCategories } from "@/lib/channels";
import { clsx } from "clsx";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

export default function WhatsOnPage() {
  const [category, setCategory] = useState("All");

  const { data, isLoading } = useSWR<{ items: WhatsOnItem[] }>(
    `/api/whats-on?category=${category}`,
    fetcher,
    { refreshInterval: 60000 }
  );

  const items = data?.items || [];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-8">
        <div className="flex items-center gap-3 mb-3">
          <span className="w-3 h-3 bg-brand-accent rounded-full animate-pulse" />
          <h1 className="text-3xl sm:text-4xl font-bold text-white">
            What&apos;s On <span className="text-brand-accent">Now</span>
          </h1>
        </div>
        <p className="text-brand-muted text-lg">
          See what&apos;s currently airing across all channels. Auto-updates every minute.
        </p>
      </div>

      {/* Category filter */}
      <div className="flex items-center gap-2 overflow-x-auto pb-3 mb-6 scrollbar-thin">
        {channelCategories.map((cat) => (
          <button
            key={cat}
            onClick={() => setCategory(cat)}
            className={clsx(
              "px-4 py-1.5 rounded-full text-sm font-medium whitespace-nowrap transition-colors shrink-0",
              category === cat
                ? "bg-brand-accent text-white"
                : "bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-accent/40"
            )}
          >
            {cat}
          </button>
        ))}
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : items.length === 0 ? (
        <div className="text-center text-brand-muted py-20">
          <Radio className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p>No programs currently airing in this category.</p>
        </div>
      ) : (
        <>
          <p className="text-brand-muted text-sm mb-6">
            Showing <span className="text-white font-semibold">{items.length}</span> live programs
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-10">
            {items.map((item) => (
              <div
                key={item.channel.id}
                className="bg-brand-card border border-brand-border rounded-xl p-4 hover:border-brand-accent/40 transition-all duration-300"
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
                <h3 className="text-white font-semibold text-sm mb-1">
                  {item.currentProgram.title}
                </h3>
                {item.currentProgram.rating && (
                  <span className="text-xs bg-white/10 text-brand-muted px-2 py-0.5 rounded mr-2">
                    {item.currentProgram.rating}
                  </span>
                )}
                <p className="text-brand-muted text-xs mb-3 mt-1 line-clamp-2">
                  {item.currentProgram.description}
                </p>
                {/* Progress */}
                <div className="mb-2">
                  <div className="h-1.5 bg-brand-border rounded-full overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-brand-accent to-brand-primary rounded-full"
                      style={{ width: `${Math.min(item.progressPercent, 100)}%` }}
                    />
                  </div>
                </div>
                <div className="flex items-center justify-between text-xs text-brand-muted">
                  <span>{formatTime(item.currentProgram.startTime)}</span>
                  <span className="text-brand-accent font-medium">{item.progressPercent}% done</span>
                  <span>{formatTime(item.currentProgram.endTime)}</span>
                </div>
                {item.nextProgram && (
                  <div className="mt-3 pt-3 border-t border-brand-border">
                    <p className="text-brand-muted text-xs">
                      Up next: <span className="text-white font-medium">{item.nextProgram.title}</span>
                      <span className="text-brand-muted ml-1">at {formatTime(item.nextProgram.startTime)}</span>
                    </p>
                  </div>
                )}
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
