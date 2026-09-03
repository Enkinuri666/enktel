import { WEB_PLAYER_URL } from "@/lib/webplayer";

export interface PlatformFeature {
  name: string;
  url: string;
  description: string;
}

// Single source of truth for "what can Enktel do / where do I find X" answers
// — used by the AI assistant's get_platform_features tool so it stays
// grounded in the site's actual current feature set instead of guessing.
export const PLATFORM_FEATURES: PlatformFeature[] = [
  {
    name: "Web Player",
    url: WEB_PLAYER_URL,
    description:
      "Watch live channels and browse the full TV guide directly in any browser — laptop, desktop, or tablet. Included free with every Enktel subscription, same login as the Smart TV/Firestick/MAG app. No app to download and no separate IPTV player (like TiviMate or IPTV Smarters) to buy or configure — it works the moment you log in.",
  },
  {
    name: "Latest Releases",
    url: "/latest-releases",
    description:
      "Browse and filter the newest movies and TV shows in the VOD library — filter by movies/shows, language, genre, or 'blockbusters only', sorted by popularity, rating, or newest.",
  },
  {
    name: "What's New",
    url: "/whats-new",
    description:
      "A weekly-updating page covering the latest added movies & series, live channel highlights, and upcoming sports & PPV events, each with where to find it on the guide.",
  },
  {
    name: "Enktel Wire",
    url: "/updates",
    description:
      "The hub tying the ecosystem's latest additions together — Ask Enktel AI, the Web Player, the refreshed site design, and other recent updates — with quick links to What's New and Latest Releases.",
  },
  {
    name: "Ask Enktel AI",
    url: "/",
    description:
      "This assistant — answers support and setup questions, looks up what's on live channels, pricing, and upcoming sports fixtures, right from the chat launcher on any page.",
  },
  {
    name: "EPG Guide",
    url: "/epg",
    description: "The full electronic program guide — browse every channel's schedule, search, and filter by category.",
  },
  {
    name: "Pricing & Plans",
    url: "/pricing",
    description: "Every Enktel access pass, priced in Australian dollars — 1, 3, 6 and 12 months, with a free 24-hour trial and no lock-in contract.",
  },
  {
    name: "Dashboard",
    url: "/dashboard",
    description: "Where a customer finds their M3U playlist URL, EPG URL, username, and password after subscribing.",
  },
];
