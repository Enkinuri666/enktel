"use client";
import { useState } from "react";
import useSWR from "swr";
import { Search } from "lucide-react";
import { Channel } from "@/types";
import ChannelCard from "./ChannelCard";
import CategoryFilter from "./CategoryFilter";
import Spinner from "@/components/ui/Spinner";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface ChannelsData {
  channels: Channel[];
  total: number;
}

export default function ChannelGrid({ initialCategory = "All" }: { initialCategory?: string }) {
  const [category, setCategory] = useState(initialCategory);
  const [search, setSearch] = useState("");

  const { data, isLoading } = useSWR<ChannelsData>(
    `/api/channels?category=${encodeURIComponent(category)}`,
    fetcher
  );

  const channels = (data?.channels || []).filter((c) =>
    c.name.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div>
      <div className="flex flex-col sm:flex-row gap-4 mb-6">
        {/* Search */}
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-brand-muted" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search channels..."
            className="w-full cyber-panel rounded-xl pl-10 pr-4 py-2.5 text-white text-sm placeholder:text-brand-muted focus:outline-none focus:border-brand-secondary/50 transition-colors"
          />
        </div>
        {/* Category Filter */}
        <div className="flex-1 overflow-hidden">
          <CategoryFilter selected={category} onChange={setCategory} />
        </div>
      </div>

      {isLoading ? (
        <Spinner className="py-16" />
      ) : (
        <>
          <div className="flex items-center justify-between mb-4">
            <p className="text-brand-muted text-sm">
              Showing <span className="text-white font-semibold">{channels.length}</span> channels
              {search && <span> matching &ldquo;{search}&rdquo;</span>}
            </p>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {channels.map((channel) => (
              <ChannelCard key={channel.id} channel={channel} />
            ))}
          </div>
          {channels.length === 0 && (
            <div className="text-center text-brand-muted py-16">
              No channels found. Try a different search or category.
            </div>
          )}
        </>
      )}
    </div>
  );
}
