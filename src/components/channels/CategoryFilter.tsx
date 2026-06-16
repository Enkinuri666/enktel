"use client";
import { channelCategories } from "@/lib/channels";
import { ChannelCategory } from "@/types";
import { clsx } from "clsx";

interface CategoryFilterProps {
  selected: string;
  onChange: (cat: string) => void;
}

export default function CategoryFilter({ selected, onChange }: CategoryFilterProps) {
  return (
    <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-thin">
      {channelCategories.map((cat: ChannelCategory) => (
        <button
          key={cat}
          onClick={() => onChange(cat)}
          className={clsx(
            "px-4 py-1.5 rounded-full text-sm font-medium whitespace-nowrap transition-colors shrink-0",
            selected === cat
              ? "bg-brand-primary text-white shadow-lg shadow-brand-primary/25"
              : "bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40"
          )}
        >
          {cat}
        </button>
      ))}
    </div>
  );
}
