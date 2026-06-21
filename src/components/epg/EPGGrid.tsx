"use client";
import { useRef, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import useSWR from "swr";
import { channels as allChannels, channelCategories } from "@/lib/channels";
import { getProgramsForChannel } from "@/lib/epg";
import { TIMEZONE_OPTIONS, DEFAULT_TIMEZONE, detectBrowserTimezone } from "@/lib/timezone";
import { EPGProgram, Channel } from "@/types";
import EPGRow from "./EPGRow";
import TimeSlots from "./TimeSlots";
import Spinner from "@/components/ui/Spinner";
import Breadcrumbs from "@/components/ui/Breadcrumbs";
import { clsx } from "clsx";

const fetcher = (url: string) => fetch(url).then((r) => r.json());
const PIXELS_PER_MINUTE = 2;

interface EPGData {
  programs: EPGProgram[];
}

export default function EPGGrid() {
  const { data, isLoading } = useSWR<EPGData>("/api/epg", fetcher, {
    revalidateOnFocus: false,
    refreshInterval: 5 * 60 * 1000,
  });

  const searchParams = useSearchParams();
  const focusChannelId = searchParams.get("channel");
  const [category, setCategory] = useState("All");
  // Default to a fixed zone for the initial (server-matching) render, then
  // upgrade to the visitor's real zone post-mount to avoid a hydration
  // mismatch between server and client renders.
  const [timeZone, setTimeZone] = useState(DEFAULT_TIMEZONE);
  useEffect(() => {
    setTimeZone(detectBrowserTimezone());
  }, []);

  const scrollRef = useRef<HTMLDivElement>(null);
  const rowRefs = useRef<Record<string, HTMLDivElement | null>>({});

  const now = new Date();
  const startTime = new Date(now);
  startTime.setHours(now.getHours() - 2, 0, 0, 0);
  const endTime = new Date(startTime);
  endTime.setHours(startTime.getHours() + 12, 0, 0, 0);

  const filteredChannels: Channel[] = allChannels
    .filter((c) => category === "All" || c.category === category);

  const focusChannel = focusChannelId ? allChannels.find((c) => c.id === focusChannelId) : undefined;

  const programs = data?.programs || [];

  // Auto-scroll to current time on load
  useEffect(() => {
    if (scrollRef.current) {
      const minutesFromStart = 120; // 2 hours of past
      scrollRef.current.scrollLeft = minutesFromStart * PIXELS_PER_MINUTE - 80;
    }
  }, []);

  // If we arrived via a "Tune to" / search deep link (?channel=id), jump
  // straight to that channel's category so its row is actually rendered.
  useEffect(() => {
    if (!focusChannelId) return;
    const targetChannel = allChannels.find((c) => c.id === focusChannelId);
    if (targetChannel) setCategory(targetChannel.category);
  }, [focusChannelId]);

  // Once that category's rows are rendered, scroll the target row into view.
  useEffect(() => {
    if (!focusChannelId || isLoading) return;
    const row = rowRefs.current[focusChannelId];
    row?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [focusChannelId, isLoading, category]);

  return (
    <div className="flex flex-col h-full">
      {focusChannel && (
        <Breadcrumbs items={[{ label: "EPG Guide", href: "/epg" }, { label: focusChannel.name }]} />
      )}

      {/* Filters row: category, timezone */}
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-thin">
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

        <div className="flex items-center gap-3 shrink-0">
          <select
            value={timeZone}
            onChange={(e) => setTimeZone(e.target.value)}
            className="bg-brand-card border border-brand-border text-white text-sm rounded-full px-3 py-1.5 focus:outline-none focus:border-brand-primary/40"
          >
            {TIMEZONE_OPTIONS.map((tz) => (
              <option key={tz.value} value={tz.value}>
                {tz.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : (
        <div className="overflow-auto rounded-xl border border-brand-border" ref={scrollRef}>
          {/* Time header */}
          <div className="flex sticky top-0 z-30 bg-brand-bg border-b border-brand-border">
            <div className="w-40 shrink-0 border-r border-brand-border bg-brand-bg z-40 sticky left-0" />
            <TimeSlots
              startTime={startTime}
              endTime={endTime}
              pixelsPerMinute={PIXELS_PER_MINUTE}
              timeZone={timeZone}
            />
          </div>

          {/* Channel rows */}
          {filteredChannels.map((channel) => {
            const channelPrograms = getProgramsForChannel(programs, channel.id, startTime, endTime);
            return (
              <div
                key={channel.id}
                ref={(el) => { rowRefs.current[channel.id] = el; }}
                className={clsx(channel.id === focusChannelId && "ring-2 ring-brand-primary/60 rounded-lg")}
              >
                <EPGRow
                  channel={channel}
                  programs={channelPrograms}
                  startTime={startTime}
                  endTime={endTime}
                  pixelsPerMinute={PIXELS_PER_MINUTE}
                  timeZone={timeZone}
                />
              </div>
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
