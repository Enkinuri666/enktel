"use client";
import { useEffect, useState } from "react";
import useSWR from "swr";
import Link from "next/link";
import { Newspaper, Star, Clock, Film, Tv, Sparkles, ArrowRight } from "lucide-react";
import { BlogPost, BlogPostKind } from "@/types";
import Spinner from "@/components/ui/Spinner";
import MediaPoster from "@/components/ui/MediaPoster";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { loadSubscription, StoredSubscription } from "@/lib/subscriptionStorage";
import { editorialPosts, CATEGORY_LABELS, CATEGORY_DESCRIPTIONS } from "@/lib/editorialContent";
import { EditorialCategory } from "@/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface BlogData {
  posts: BlogPost[];
}

type FeedFilter = "all" | BlogPostKind | EditorialCategory;

const FILTERS: { value: FeedFilter; label: string }[] = [
  { value: "all", label: "All Posts" },
  { value: "now-showing", label: "Now Showing" },
  { value: "on-air", label: "On Air" },
  { value: "coming-soon", label: "Coming Soon" },
  { value: "weekly-mag", label: "Tel-Vision Weekly" },
  { value: "player-review", label: "Player Reviews" },
  { value: "troubleshooting", label: "Fire TV Fixes" },
];

const EDITORIAL_CATEGORIES: EditorialCategory[] = ["weekly-mag", "player-review", "troubleshooting"];

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

function EditorialCard({ slug, icon, category, title, excerpt, readMinutes }: {
  slug: string;
  icon: string;
  category: EditorialCategory;
  title: string;
  excerpt: string;
  readMinutes: number;
}) {
  return (
    <Link
      href={`/blog/${slug}`}
      className="block bg-brand-card border border-brand-border rounded-xl p-5 hover:border-brand-primary/40 hover:shadow-lg hover:shadow-brand-primary/10 transition-all duration-300"
    >
      <div className="flex items-center gap-2 mb-3">
        <span className="text-xl leading-none">{icon}</span>
        <span className="text-xs font-bold uppercase tracking-wide text-brand-primary bg-brand-primary/10 px-2 py-0.5 rounded-full">
          {CATEGORY_LABELS[category]}
        </span>
      </div>
      <h3 className="text-white font-semibold mb-1.5 line-clamp-2">{title}</h3>
      <p className="text-brand-muted text-sm line-clamp-3 mb-3">{excerpt}</p>
      <span className="text-brand-secondary text-sm font-medium flex items-center gap-1">
        {readMinutes} min read <ArrowRight className="w-3.5 h-3.5" />
      </span>
    </Link>
  );
}

export default function BlogPage() {
  const [filter, setFilter] = useState<FeedFilter>("all");
  const [sub, setSub] = useState<StoredSubscription | null>(null);
  const [checked, setChecked] = useState(false);
  const { data, isLoading } = useSWR<BlogData>("/api/blog", fetcher, { refreshInterval: 60 * 60 * 1000 });

  useEffect(() => {
    setSub(loadSubscription());
    setChecked(true);
  }, []);

  const posts = data?.posts || [];
  const isEditorialFilter = EDITORIAL_CATEGORIES.includes(filter as EditorialCategory);
  const filtered = filter === "all" || isEditorialFilter ? posts : posts.filter((p) => p.kind === filter);
  const [featured, ...rest] = filtered;
  const editorialFiltered = isEditorialFilter
    ? editorialPosts.filter((p) => p.category === filter)
    : editorialPosts;

  if (checked && !sub) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-20 text-center">
        <Newspaper className="w-10 h-10 text-brand-muted mx-auto mb-4" />
        <h1 className="text-white font-bold text-2xl mb-2">The Blog is members-only</h1>
        <p className="text-brand-muted text-sm mb-6 max-w-md mx-auto">
          Log in with your Enktel subscription to read player reviews, Fire TV troubleshooting guides,
          Tel-Vision Weekly, and the live entertainment feed.
        </p>
        <div className="flex items-center justify-center gap-3 flex-wrap">
          <Link href="/login">
            <Button>Log In</Button>
          </Link>
          <Link href="/watch">
            <Button variant="outline">Start Free Trial</Button>
          </Link>
        </div>
      </div>
    );
  }

  if (!sub) return null;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <div className="mb-8">
        <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">
          The Enktel{" "}
          <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">
            Blog
          </span>
        </h1>
        <p className="text-brand-muted text-lg max-w-2xl">
          Fresh stories on what&apos;s screening now, what&apos;s on air, and what&apos;s coming soon, plus
          player reviews, Fire TV troubleshooting guides, and Tel-Vision Weekly — our members-only e-magazine.
        </p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3 mb-8">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={`px-4 py-1.5 sm:px-5 sm:py-2 rounded-full text-sm font-medium transition-colors ${
              filter === f.value
                ? "bg-brand-primary text-white shadow-lg shadow-brand-primary/25"
                : "bg-brand-card border border-brand-border text-brand-muted hover:text-white hover:border-brand-primary/40"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {isEditorialFilter ? (
        editorialFiltered.length === 0 ? (
          <p className="text-brand-muted text-center py-16">No posts in this category right now. Check back soon.</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {editorialFiltered.map((post) => (
              <EditorialCard key={post.slug} {...post} />
            ))}
          </div>
        )
      ) : (
        <>
          {/* Tel-Vision Weekly + guides strip */}
          {filter === "all" && (
            <div className="mb-10">
              <div className="flex items-center gap-2 mb-4">
                <Sparkles className="w-4 h-4 text-brand-secondary" />
                <h2 className="text-white font-bold text-lg">Tel-Vision Weekly &amp; Guides</h2>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                {editorialPosts.slice(0, 3).map((post) => (
                  <EditorialCard key={post.slug} {...post} />
                ))}
              </div>
            </div>
          )}

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
                <div className="flex items-center justify-between gap-4">
                  <div className="flex items-center gap-4 text-sm text-brand-muted">
                    {featured.rating > 0 && (
                      <span className="flex items-center gap-1">
                        <Star className="w-3.5 h-3.5 text-yellow-400 fill-yellow-400" /> {featured.rating.toFixed(1)}
                      </span>
                    )}
                    <span>{readTime(featured.excerpt)}</span>
                  </div>
                  <span className="text-brand-secondary text-sm font-medium flex items-center gap-1 shrink-0">
                    Read more <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-0.5 transition-transform" />
                  </span>
                </div>
              </div>
            </div>
          </Link>

          {/* Feed grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {rest.map((post) => (
              <Link
                href="/watch"
                key={post.id}
                className="group block bg-brand-card border border-brand-border rounded-xl overflow-hidden hover:border-brand-primary/40 hover:shadow-lg hover:shadow-brand-primary/10 transition-all duration-300"
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
                  <h3 className="text-white font-semibold mb-1.5 line-clamp-2 group-hover:text-brand-primary transition-colors">
                    {post.title}
                  </h3>
                  <p className="text-brand-muted text-sm line-clamp-3 mb-3">{post.excerpt}</p>
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex flex-wrap gap-1">
                      {post.genres.slice(0, 2).map((g) => (
                        <span key={g} className="text-xs bg-white/5 text-brand-muted px-2 py-0.5 rounded-full">
                          {g}
                        </span>
                      ))}
                    </div>
                    <ArrowRight className="w-3.5 h-3.5 text-brand-secondary shrink-0 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </div>
                </div>
              </Link>
            ))}
          </div>
          </>
          )}
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
          href="/watch"
          className="shrink-0 bg-brand-primary hover:bg-brand-primary/90 text-white font-semibold px-6 py-3 rounded-xl transition-colors"
        >
          Start Free Trial
        </Link>
      </div>
    </div>
  );
}
