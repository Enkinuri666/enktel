"use client";
import { useMemo, useRef, useState } from "react";
import { motion } from "framer-motion";
import { Search } from "lucide-react";
import { channels, channelCategories } from "@/lib/channels";
import type { Channel } from "@/types";
import ChannelLogo from "@/components/ui/ChannelLogo";

interface ChannelRailProps {
  activeId: string;
  onSelect: (channel: Channel) => void;
}

// A remote-friendly channel list: Up/Down moves the highlighted row without
// needing a pointer, matching how a Fire TV/Android TV D-pad behaves in a
// browser (it dispatches plain ArrowUp/ArrowDown key events). Each button
// keeps a real, visible focus ring sized for a 10-foot viewing distance
// rather than a subtle desktop-style outline.
export default function ChannelRail({ activeId, onSelect }: ChannelRailProps) {
  const [category, setCategory] = useState("All");
  const [query, setQuery] = useState("");
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const filtered = useMemo(() => {
    let list = category === "All" ? channels : channels.filter((c) => c.category === category);
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      list = list.filter((c) => c.name.toLowerCase().includes(q));
    }
    return list;
  }, [category, query]);

  function onKeyDown(e: React.KeyboardEvent, index: number) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      itemRefs.current[index + 1]?.focus();
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      itemRefs.current[index - 1]?.focus();
    }
  }

  return (
    <div className="flex flex-col h-full bg-brand-card/60 border border-brand-border rounded-2xl overflow-hidden backdrop-blur-xl">
      <div className="p-3 border-b border-brand-border/60 space-y-2.5 shrink-0">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-brand-muted" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Find a channel…"
            className="w-full bg-brand-bg border border-brand-border rounded-lg pl-9 pr-3 py-2 text-sm text-white placeholder:text-brand-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-secondary"
          />
        </div>
        <div className="flex gap-1.5 overflow-x-auto pb-1 scrollbar-thin">
          {channelCategories.map((cat) => (
            <button
              key={cat}
              onClick={() => setCategory(cat)}
              className={`shrink-0 px-3 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-secondary ${
                category === cat
                  ? "bg-brand-primary text-white"
                  : "bg-white/5 text-brand-muted hover:text-white"
              }`}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-2 space-y-1">
        {filtered.map((channel, i) => {
          const active = channel.id === activeId;
          return (
            <motion.button
              key={channel.id}
              ref={(el) => { itemRefs.current[i] = el; }}
              onClick={() => onSelect(channel)}
              onKeyDown={(e) => onKeyDown(e, i)}
              whileHover={{ x: 3 }}
              className={`w-full flex items-center gap-3 px-2.5 py-2 rounded-xl text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-secondary ${
                active ? "bg-brand-primary/25 border border-brand-primary/50" : "border border-transparent hover:bg-white/5"
              }`}
            >
              <ChannelLogo name={channel.name} id={channel.id} logoUrl={channel.logoUrl} size="sm" />
              <span className="min-w-0 flex-1">
                <span className={`block text-sm font-semibold truncate ${active ? "text-white" : "text-brand-muted"}`}>
                  {channel.name}
                </span>
                <span className="block text-[11px] text-brand-muted/70 truncate">{channel.category}</span>
              </span>
              {active && <span className="w-2 h-2 rounded-full bg-brand-accent shrink-0 animate-pulse" />}
            </motion.button>
          );
        })}
        {filtered.length === 0 && (
          <p className="text-brand-muted text-sm text-center py-8">No channels match.</p>
        )}
      </div>
    </div>
  );
}
