"use client";
import Link from "next/link";
import { motion } from "framer-motion";
import { Radio, Palette, Layers, Trophy, MessageCircleHeart } from "lucide-react";

type Accent = "secondary" | "primary" | "accent";

interface Story {
  icon: typeof Radio;
  accent: Accent;
  kicker: string;
  title: string;
  blurb: string;
  href?: string;
  linkLabel?: string;
}

// Tailwind's scanner needs full, literal class strings — not string
// interpolation — to pick them up, so each accent maps to complete classes
// rather than building `bg-${accent}/15` at render time.
const ACCENT_CLASSES: Record<Accent, { badge: string; icon: string; kicker: string }> = {
  secondary: {
    badge: "bg-brand-secondary/15 border-brand-secondary/30",
    icon: "text-brand-secondary",
    kicker: "text-brand-secondary",
  },
  primary: {
    badge: "bg-brand-primary/15 border-brand-primary/30",
    icon: "text-brand-primary",
    kicker: "text-brand-primary",
  },
  accent: {
    badge: "bg-brand-accent/15 border-brand-accent/30",
    icon: "text-brand-accent",
    kicker: "text-brand-accent",
  },
};

const STORIES: Story[] = [
  {
    icon: Radio,
    accent: "secondary",
    kicker: "Homepage",
    title: "A new on-air look",
    blurb:
      "The homepage now opens with a live broadcast-style animation instead of a static logo — Enktel on air, from the first second.",
    href: "/",
    linkLabel: "See the homepage",
  },
  {
    icon: Palette,
    accent: "primary",
    kicker: "Design",
    title: "Neon glass, top to bottom",
    blurb:
      "A refreshed visual theme — glassmorphism cards and neon gradients — now runs consistently across every page on enktel.tv.",
  },
  {
    icon: Layers,
    accent: "secondary",
    kicker: "Ecosystem",
    title: "One account, every screen",
    blurb:
      "Smart TV, Firestick, and MAG apps at enktel.tv, or the new browser player at watch.enktel.tv — same login, same subscription, any device.",
    href: "/web-player",
    linkLabel: "See the Web Player",
  },
  {
    icon: Trophy,
    accent: "accent",
    kicker: "Content",
    title: "Streaming platforms & sports, at a glance",
    blurb:
      "A scrollable carousel on the homepage now surfaces the streaming platforms and live sports fixtures included in your subscription.",
    href: "/#platforms",
    linkLabel: "Browse what's included",
  },
  {
    icon: MessageCircleHeart,
    accent: "primary",
    kicker: "Support",
    title: "One chat icon for everything",
    blurb:
      "Live Chat, WhatsApp, and Ask Enktel AI now live behind a single branded launcher in the corner — no more duplicate bubbles competing for space.",
  },
];

export default function UpdatesStoryGrid() {
  return (
    <section className="py-6 px-4 sm:px-6 lg:px-8">
      <div className="max-w-6xl mx-auto">
        <p className="text-brand-secondary text-xs font-black uppercase tracking-[0.25em] mb-6">
          Also in this issue
        </p>
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {STORIES.map((story, i) => {
            const Icon = story.icon;
            const classes = ACCENT_CLASSES[story.accent];
            const card = (
              <>
                <div
                  className={`w-11 h-11 rounded-xl border flex items-center justify-center shrink-0 mb-4 ${classes.badge}`}
                >
                  <Icon className={`w-5 h-5 ${classes.icon}`} />
                </div>
                <p className={`text-xs font-bold uppercase tracking-widest mb-2 ${classes.kicker}`}>
                  {story.kicker}
                </p>
                <h3 className="text-white font-bold text-lg mb-2 leading-snug">{story.title}</h3>
                <p className="text-brand-muted text-sm leading-relaxed mb-3">{story.blurb}</p>
                {story.href && (
                  <span className="text-sm font-semibold text-white/80 group-hover:text-white transition-colors">
                    {story.linkLabel} →
                  </span>
                )}
              </>
            );

            return (
              <motion.div
                key={story.title}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.05 }}
              >
                {story.href ? (
                  <Link
                    href={story.href}
                    className="group block h-full rounded-2xl bg-brand-card border border-brand-border hover:border-white/20 p-6 transition-colors"
                  >
                    {card}
                  </Link>
                ) : (
                  <div className="h-full rounded-2xl bg-brand-card border border-brand-border p-6">{card}</div>
                )}
              </motion.div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
