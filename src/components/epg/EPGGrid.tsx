"use client";
import { useRef, useEffect, useState } from "react";
import useSWR from "swr";
import { channels as allChannels, channelCategories } from "@/lib/channels";
import { getProgramsForChannel } from "@/lib/epg";
import { EPGProgram, Channel } from "@/types";
import EPGRow from "./EPGRow";
import TimeSlots from "./TimeSlots";
import Spinner from "@/components/ui/Spinner";
import { clsx } from "clsx";

const fetcher = (url: string) => fetch(url).then((r) => r.json());
const PIXELS_PER_MINUTE = 2;

interface EPGData {
  programs: EPGProgram[];
}

export default function EPGGrid() {
  const { data, isLoading } = useSWR<EPGData>("/api/epg", fetcher, {
    revalidateOnFocus: false,
  });

  const [category, setCategory] = useState("All");
  const scrollRef = useRef<HTMLDivElement>(null);

  const now = new Date();
  const startTime = new Date(now);
  startTime.setHours(now.getHours() - 2, 0, 0, 0);
  const endTime = new Date(startTime);
  endTime.setHours(startTime.getHours() + 12, 0, 0, 0);

  const filteredChannels: Channel[] = category === "All"
    ? allChannels
    : allChannels.filter((c) => c.category === category);

  const programs = data?.programs || [];

  // Auto-scroll to current time on load
  useEffect(() => {
    if (scrollRef.current) {
      const minutesFromStart = 120; // 2 hours of past
      scrollRef.current.scrollLeft = minutesFromStart * PIXELS_PER_MINUTE - 80;
    }
  }, []);

  return (
    <div className="flex flex-col h-full">
      {/* Category filter */}
      <div className="flex items-center gap-2 overflow-x-auto pb-3 mb-4 scrollbar-thin">
        {channelCategories.map((cat) => (
          <button
            key={cat}
            onClick={() => setCategory(cat)}
            className={clsx(
              "px-4 py-1.5 rounded-full text-sm font-medium whitespace-nowrap transition-colors shrink-0",
              category === cat
                ? "bg-brand-primary text-white"
                : "bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40"
            )}
          >
            {cat}
          </button>
        ))}
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : (
        <div className="overflow-auto rounded-xl border border-brand-border" ref={scrollRef}>
          {/* Time header */}
          <div className="flex sticky top-0 z-30 bg-brand-bg border-b border-brand-border">
            <div className="w-40 shrink-0 border-r border-brand-border bg-brand-bg z-40 sticky left-0" />
            <TimeSlots
              startHour={startTime.getHours()}
              endHour={endTime.getHours()}
              pixelsPerMinute={PIXELS_PER_MINUTE}
            />
          </div>

          {/* Channel rows */}
          {filteredChannels.map((channel) => {
            const channelPrograms = getProgramsForChannel(programs, channel.id, startTime, endTime);
            return (
              <EPGRow
                key={channel.id}
                channel={channel}
                programs={channelPrograms}
                startTime={startTime}
                endTime={endTime}
                pixelsPerMinute={PIXELS_PER_MINUTE}
              />
            );
          })}

          {filteredChannels.length === 0 && (
            <div className="text-center text-brand-muted py-16">
              No channels found in this category.
            </div>
          )}
        </div>
      )}
    </div>
  );
}
