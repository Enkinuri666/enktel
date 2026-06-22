"use client";
import { useState } from "react";
import useSWR from "swr";
import Link from "next/link";
import { Newspaper, Star, Clock, Film, Tv, Sparkles } from "lucide-react";
import { BlogPost, BlogPostKind } from "@/types";
import Spinner from "@/components/ui/Spinner";
import MediaPoster from "@/components/ui/MediaPoster";
import Badge from "@/components/ui/Badge";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface BlogData {
  posts: BlogPost[];
}

type FeedFilter = "all" | BlogPostKind;

const FILTERS: { value: FeedFilter; label: string }[] = [
  { value: "all", label: "All Posts" },
  { value: "now-showing", label: "Now Showing" },
  { value: "on-air", label: "On Air" },
  { value: "coming-soon", label: "Coming Soon" },
];

function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const hours = Math.round(diffMs / (60 * 60 * 1000));
  if (hours < 1) return "Just now";
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  return `${days}d ago`;
}

function readTime(excerpt: string): string {
  const words = excerpt.split(/\s+/).filter(Boolean).length;
  return `${Math.max(1, Math.round(words / 200))} min read`;
}

export default function BlogPage() {
  const [filter, setFilter] = useState<FeedFilter>("all");
  const { data, isLoading } = useSWR<BlogData>("/api/blog", fetcher, { refreshInterval: 60 * 60 * 1000 });

  const posts = data?.posts || [];
  const filtered = filter === "all" ? posts : posts.filter((p) => p.kind === filter);
  const [featured, ...rest] = filtered;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-8">
        <div className="inline-flex items-center gap-2 bg-brand-primary/10 border border-brand-primary/30 text-brand-primary text-xs font-bold px-3 py-1.5 rounded-full mb-4">
          <Newspaper className="w-3.5 h-3.5" /> AUTO-UPDATING ENTERTAINMENT BLOG
        </div>
        <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">
          The Enktel{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Blog
          </span>
        </h1>
        <p className="text-brand-muted text-lg max-w-2xl">
          Fresh stories on what&apos;s screening now, what&apos;s on air, and what&apos;s coming soon —
          generated straight from live entertainment data, refreshed throughout the day.
        </p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3 mb-8">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={`px-5 py-2 rounded-full text-sm font-medium transition-colors ${
              filter === f.value
                ? "bg-brand-primary text-white shadow-lg shadow-brand-primary/25"
                : "bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <Spinner className="py-20" />
      ) : filtered.length === 0 ? (
        <p className="text-brand-muted text-center py-16">No posts in this category right now. Check back soon.</p>
      ) : (
        <>
          {/* Featured post */}
          <Link
            href={`/watch`}
            className="group block mb-10 bg-brand-card border border-brand-border rounded-2xl overflow-hidden hover:border-brand-primary/40 transition-colors"
          >
            <div className="grid grid-cols-1 md:grid-cols-[1fr,1.4fr]">
              <div className="relative aspect-[16/9] md:aspect-auto">
                <MediaPoster
                  posterPath={featured.backdropPath || featured.posterPath}
                  title={featured.title}
                  type={featured.mediaType}
                  className="aspect-[16/9] md:aspect-auto md:h-full"
                />
              </div>
              <div className="p-6 md:p-8 flex flex-col justify-center">
                <div className="flex items-center gap-2 mb-3">
                  <Badge variant="primary">{featured.section}</Badge>
                  <span className="text-brand-muted text-xs flex items-center gap-1">
                    <Clock className="w-3 h-3" /> {timeAgo(featured.publishedAt)}
                  </span>
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold text-white mb-3 group-hover:text-brand-primary transition-colors">
                  {featured.title}
                </h2>
                <p className="text-brand-muted mb-4 line-clamp-3">{featured.excerpt}</p>
                <div className="flex items-center gap-4 text-sm text-brand-muted">
                  {featured.rating > 0 && (
                    <span className="flex items-center gap-1">
                      <Star className="w-3.5 h-3.5 text-yellow-400 fill-yellow-400" /> {featured.rating.toFixed(1)}
                    </span>
                  )}
                  <span>{readTime(featured.excerpt)}</span>
                </div>
              </div>
            </div>
          </Link>

          {/* Feed grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {rest.map((post) => (
              <article
                key={post.id}
                className="bg-brand-card border border-brand-border rounded-xl overflow-hidden hover:border-brand-primary/40 hover:shadow-lg hover:shadow-brand-primary/10 transition-all duration-300"
              >
                <div className="relative">
                  <MediaPoster
                    posterPath={post.backdropPath || post.posterPath}
                    title={post.title}
                    type={post.mediaType}
                    className="aspect-[16/9]"
                  />
                  <div className="absolute top-2 left-2">
                    <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-brand-primary/80 text-white flex items-center gap-1">
                      {post.mediaType === "movie" ? <Film className="w-3 h-3" /> : <Tv className="w-3 h-3" />}
                      {post.section}
                    </span>
                  </div>
                </div>
                <div className="p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-brand-muted text-xs flex items-center gap-1">
                      <Clock className="w-3 h-3" /> {timeAgo(post.publishedAt)}
                    </span>
                    {post.rating > 0 && (
                      <span className="text-brand-muted text-xs flex items-center gap-1">
                        <Star className="w-3 h-3 text-yellow-400 fill-yellow-400" /> {post.rating.toFixed(1)}
                      </span>
                    )}
                  </div>
                  <h3 className="text-white font-semibold mb-1.5 line-clamp-2">{post.title}</h3>
                  <p className="text-brand-muted text-sm line-clamp-3 mb-3">{post.excerpt}</p>
                  <div className="flex flex-wrap gap-1">
                    {post.genres.slice(0, 2).map((g) => (
                      <span key={g} className="text-xs bg-white/5 text-brand-muted px-2 py-0.5 rounded-full">
                        {g}
                      </span>
                    ))}
                  </div>
                </div>
              </article>
            ))}
          </div>
        </>
      )}

      <div className="mt-12 bg-gradient-to-br from-brand-primary/10 to-brand-secondary/10 border border-brand-primary/30 rounded-2xl p-6 sm:p-8 flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <Sparkles className="w-6 h-6 text-brand-primary shrink-0" />
          <div>
            <h3 className="text-white font-bold">Want to watch what you just read about?</h3>
            <p className="text-brand-muted text-sm">Start a free 24-hour trial — no card required.</p>
          </div>
        </div>
        <Link
          href="/trial"
          className="shrink-0 bg-brand-primary hover:bg-brand-primary/90 text-white font-semibold px-6 py-3 rounded-xl transition-colors"
        >
          Start Free Trial
        </Link>
      </div>
    </div>
  );
}
