"use client";
import { useEffect, useState } from "react";
import { notFound } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Clock, Tv } from "lucide-react";
import Button from "@/components/ui/Button";
import { loadSubscription, StoredSubscription } from "@/lib/subscriptionStorage";
import { getEditorialPost, CATEGORY_LABELS } from "@/lib/editorialContent";

function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const hours = Math.round(diffMs / (60 * 60 * 1000));
  if (hours < 1) return "Just now";
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  return `${days}d ago`;
}

export default function EditorialPostPage({ params }: { params: { slug: string } }) {
  const post = getEditorialPost(params.slug);
  const [sub, setSub] = useState<StoredSubscription | null>(null);
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    setSub(loadSubscription());
    setChecked(true);
  }, []);

  if (!post) notFound();

  if (checked && !sub) {
    return (
      <div className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-20 text-center">
        <Tv className="w-10 h-10 text-brand-muted mx-auto mb-4" />
        <h1 className="text-white font-bold text-2xl mb-2">Members-only article</h1>
        <p className="text-brand-muted text-sm mb-6 max-w-md mx-auto">
          Log in with your Enktel subscription to read this article and the rest of the Blog.
        </p>
        <div className="flex items-center justify-center gap-3 flex-wrap">
          <Link href="/login">
            <Button>Log In</Button>
          </Link>
          <Link href="/trial">
            <Button variant="outline">Start Free Trial</Button>
          </Link>
        </div>
      </div>
    );
  }

  if (!sub) return null;

  return (
    <article className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-10">
      <Link
        href="/blog"
        className="inline-flex items-center gap-1.5 text-brand-muted hover:text-white text-sm font-medium mb-6 transition-colors"
      >
        <ArrowLeft className="w-4 h-4" /> Back to Blog
      </Link>

      <div className="flex items-center gap-2 mb-4">
        <span className="text-2xl leading-none">{post.icon}</span>
        <span className="text-xs font-bold uppercase tracking-wide text-brand-primary bg-brand-primary/10 border border-brand-primary/30 px-2.5 py-1 rounded-full">
          {CATEGORY_LABELS[post.category]}
        </span>
      </div>

      <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3 leading-tight">{post.title}</h1>

      <div className="flex items-center gap-4 text-brand-muted text-sm mb-8">
        <span className="flex items-center gap-1">
          <Clock className="w-3.5 h-3.5" /> {timeAgo(post.publishedAt)}
        </span>
        <span>{post.readMinutes} min read</span>
      </div>

      <div className="space-y-6">
        {post.sections.map((section, i) => (
          <div key={i}>
            {section.heading && (
              <h2 className="text-white font-bold text-xl mb-2">{section.heading}</h2>
            )}
            {section.paragraphs.map((p, j) => (
              <p key={j} className="text-brand-muted leading-relaxed mb-3">
                {p}
              </p>
            ))}
          </div>
        ))}
      </div>

      <div className="mt-12 bg-gradient-to-br from-brand-primary/10 to-brand-secondary/10 border border-brand-primary/30 rounded-2xl p-6 flex flex-col sm:flex-row items-center justify-between gap-4">
        <p className="text-white font-semibold text-sm">More guides and weekly issues are waiting on the Blog.</p>
        <Link
          href="/blog"
          className="shrink-0 bg-brand-primary hover:bg-brand-primary/90 text-white font-semibold px-5 py-2.5 rounded-xl transition-colors text-sm"
        >
          Browse the Blog
        </Link>
      </div>
    </article>
  );
}
