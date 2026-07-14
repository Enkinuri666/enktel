"use client";
import { useEffect, useMemo, useState } from "react";
import useSWR from "swr";
import { motion } from "framer-motion";
import { channels } from "@/lib/channels";
import { getProgramsForChannel } from "@/lib/epg";
import type { Channel, EPGProgram } from "@/types";
import ChannelRail from "./ChannelRail";
import PlayerScreen from "./PlayerScreen";
import CatchupTimeline from "./CatchupTimeline";
import Spinner from "@/components/ui/Spinner";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

const DEFAULT_CHANNEL = channels.find((c) => c.id === "hrt1.hr") || channels[0];

// Ties the channel rail, the screen, and the catch-up timeline together.
// The only state that matters is *which channel* and *which programme* are
// selected — everything else (live vs. catch-up badge, progress bar,
// timeline highlight) is derived from that.
export default function LivePlayer() {
  const [channel, setChannel] = useState<Channel>(DEFAULT_CHANNEL);
  const [selectedProgramId, setSelectedProgramId] = useState<string | null>(null);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const interval = setInterval(() => setNow(new Date()), 30000);
    return () => clearInterval(interval);
  }, []);

  // Reset back to live whenever the viewer switches channels — landing on
  // an arbitrary past slot from the previous channel would be confusing.
  useEffect(() => {
    setSelectedProgramId(null);
  }, [channel.id]);

  const { data, isLoading } = useSWR<{ programs: EPGProgram[] }>(
    `/api/epg?channelId=${encodeURIComponent(channel.id)}`,
    fetcher,
    { refreshInterval: 5 * 60 * 1000 }
  );

  const windowStart = useMemo(() => {
    const d = new Date(now);
    d.setHours(d.getHours() - 8, 0, 0, 0);
    return d;
  }, [now]);
  const windowEnd = useMemo(() => {
    const d = new Date(now);
    d.setHours(d.getHours() + 1, 59, 59, 999);
    return d;
  }, [now]);

  const timelinePrograms = useMemo(() => {
    const all = data?.programs || [];
    return getProgramsForChannel(all, channel.id, windowStart, windowEnd).sort(
      (a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
    );
  }, [data, channel.id, windowStart, windowEnd]);

  const liveProgram = useMemo(
    () => timelinePrograms.find((p) => new Date(p.startTime) <= now && new Date(p.endTime) > now) || null,
    [timelinePrograms, now]
  );

  const selectedProgram = selectedProgramId ? timelinePrograms.find((p) => p.id === selectedProgramId) || null : null;
  const isLive = selectedProgramId === null;
  const displayedProgram = isLive ? liveProgram : selectedProgram;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-5">
      <div className="space-y-5">
        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
          <PlayerScreen channel={channel} program={displayedProgram} isLive={isLive} now={now} />
        </motion.div>

        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, delay: 0.1 }}>
          {isLoading ? (
            <div className="rounded-2xl bg-brand-card/60 border border-brand-border p-8">
              <Spinner />
            </div>
          ) : (
            <CatchupTimeline
              programs={timelinePrograms}
              now={now}
              windowStart={windowStart}
              windowEnd={windowEnd}
              selectedId={selectedProgramId}
              onSelect={(p) => setSelectedProgramId(p ? p.id : null)}
            />
          )}
        </motion.div>
      </div>

      <motion.div
        initial={{ opacity: 0, x: 16 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.5, delay: 0.15 }}
        className="h-[420px] lg:h-[640px] lg:sticky lg:top-24"
      >
        <ChannelRail activeId={channel.id} onSelect={setChannel} />
      </motion.div>
    </div>
  );
}
