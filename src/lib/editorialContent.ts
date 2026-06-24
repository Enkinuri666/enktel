import { EditorialPost } from "@/types";

export const CATEGORY_LABELS: Record<EditorialPost["category"], string> = {
  "player-review": "Player Reviews",
  troubleshooting: "Fire TV Troubleshooting",
  "weekly-mag": "Tel-Vision Weekly",
};

export const CATEGORY_DESCRIPTIONS: Record<EditorialPost["category"], string> = {
  "player-review": "Honest, Enktel-tested breakdowns of the IPTV apps our members actually use.",
  troubleshooting: "Quick fixes for the Fire TV problems our support inbox sees the most.",
  "weekly-mag": "Our members-only e-magazine — a weekly lap around what's worth watching.",
};

export const editorialPosts: EditorialPost[] = [
  // ── Player reviews ──────────────────────────────────────────
  {
    slug: "iptv-smarters-pro-review",
    category: "player-review",
    title: "IPTV Smarters Pro Review: Still the Best All-Rounder for Enktel Playlists?",
    excerpt:
      "We ran Enktel's feeds through it on Fire TV, Android, and iOS for months. It did not crash once, which we found almost suspicious.",
    icon: "📱",
    publishedAt: "2026-06-15T09:00:00.000Z",
    readMinutes: 6,
    sections: [
      {
        paragraphs: [
          "IPTV Smarters Pro is the app we hand new Enktel members first, mostly so they stop calling us. We've now run our own M3U and XMLTV feeds through it on Fire TV, Android, iOS, and a couple of Smart TVs old enough to have their own opinions about HDMI, and it has yet to do anything embarrassing. That's the whole review, really, but we're contractually obligated to pad it out.",
        ],
      },
      {
        heading: "Setup with Enktel credentials",
        paragraphs: [
          "Adding your Enktel line takes under a minute, which is faster than it takes most people to find the remote. Open the app, choose \"Login with Xtream Codes API\", and paste in the Server URL, username, and password from your Enktel dashboard. The app pulls your full channel list and EPG automatically — no manual M3U upload required, though that option is sitting there too, for the three people on Earth who enjoy pasting URLs by hand.",
          "On Fire TV specifically, install via Downloader using the APK link from the IPTV Smarters Pro website, because Amazon, in its infinite wisdom, has not seen fit to stock it. Sideload as usual. A base Fire TV Stick handles SD/HD without complaint; 4K wants a Fire TV Stick 4K Max or Cube, because asking a decade-old potato to render 4K is how you start a support ticket.",
        ],
      },
      {
        heading: "What we liked",
        paragraphs: [
          "EPG rendering stays fast even with Enktel's full channel count loaded, and the catch-up/timeshift menu — where a channel supports it — is the cleanest we've tested. Multi-screen support and a parental PIN are quietly the two most-used features in any household with more than one opinion about what's on.",
        ],
      },
      {
        heading: "What to watch out for",
        paragraphs: [
          "The free tier serves ads on the home screen with the enthusiasm of a timeshare pitch; the one-time Pro unlock removes them and earns its keep if this is your daily driver. We'd also turn off EPG auto-refresh on every app open and put it on a schedule instead — your cold-start time will thank you, particularly on Fire TV hardware that remembers when 720p was exciting.",
        ],
      },
      {
        heading: "Enktel verdict",
        paragraphs: [
          "If you want one app that covers phones, tablets, Fire TV, and Smart TVs without drama, this is the safest first install we know of. Pair it with your Enktel Xtream Codes login and you'll be streaming before your tea's gone cold.",
        ],
      },
    ],
  },
  {
    slug: "tivimate-fire-tv-review",
    category: "player-review",
    title: "TiviMate on Fire TV: Why It's Our Top Pick for Live TV + EPG",
    excerpt:
      "TiviMate's guide grid is the closest thing to a real TV guide we've found in an app that isn't, in fact, a TV guide.",
    icon: "📺",
    publishedAt: "2026-06-12T09:00:00.000Z",
    readMinutes: 5,
    sections: [
      {
        paragraphs: [
          "TiviMate was built by people who clearly missed the experience of channel-surfing with a physical remote and decided to do something about it. Load an Enktel playlist into it on a Fire TV Stick or Cube and the EPG grid — channels down the side, a timeline scrolling along the top — is the closest thing to an old cable box guide we've found in any third-party app, minus the part where the cable box also breaks down every winter.",
        ],
      },
      {
        heading: "Adding your Enktel line",
        paragraphs: [
          "From the home screen, choose \"Add playlist\" → \"Xtream Codes login\", then enter your Enktel Server URL, username, and password exactly as shown in your dashboard — exactly, not \"close enough,\" a distinction that has generated more support tickets than we'd like to admit. TiviMate fetches your full channel list and EPG in one step; an extra EPG/XMLTV URL can be merged in under playlist settings if you're the sort of person who needs two TV guides agreeing with each other.",
        ],
      },
      {
        heading: "Premium vs free tier",
        paragraphs: [
          "The free tier is perfectly serviceable for one playlist, in the same way a bicycle is perfectly serviceable transport. TiviMate Premium adds multiple playlists, a second EPG source, and channel-list backup/restore, which matters a great deal on the day your Fire TV dies and you have to rebuild your favourites from memory.",
        ],
      },
      {
        heading: "Performance notes",
        paragraphs: [
          "Channel-switching latency against Enktel streams is the lowest we've measured of any Fire TV player, and the picture-in-picture EPG browsing — keep watching while you scroll the guide — is the kind of feature you don't know you need until halftime, when everyone in the room suddenly has opinions about the other match.",
        ],
      },
      {
        heading: "Enktel verdict",
        paragraphs: [
          "If you watch mostly live channels rather than VOD, this is our top Fire TV pick, full stop. Sideload it via Downloader, log in with your Enktel credentials, and enjoy a guide experience your old satellite box would have charged you extra for.",
        ],
      },
    ],
  },
  {
    slug: "perfect-player-vs-vlc",
    category: "player-review",
    title: "Perfect Player vs VLC: Which One Should You Use With Enktel?",
    excerpt:
      "One is built for IPTV. The other is a Swiss Army knife that happens to also open M3U files. Neither will judge you, but we might.",
    icon: "⚔️",
    publishedAt: "2026-06-08T09:00:00.000Z",
    readMinutes: 5,
    sections: [
      {
        paragraphs: [
          "Perfect Player and VLC come up in support tickets constantly, usually right after someone's main IPTV app has done something inexplicable. They are not the same kind of tool, despite both technically playing video, in the same way a scalpel and a chainsaw both technically cut.",
        ],
      },
      {
        heading: "Perfect Player",
        paragraphs: [
          "Perfect Player is built for IPTV and nothing else, which is the entire point. Paste your Enktel M3U playlist URL and EPG/XMLTV URL under Settings → General, and it assembles a channel list with a TV-guide style overlay, using a fraction of the resources a full Xtream-style app demands. This makes it the dignified choice for older Android boxes and the Fire TV Stick (1st gen / Lite), hardware that struggles to open a second tab, let alone a second app.",
          "The trade-off is a UI that looks like it was designed in 2014, because it largely was, and no native VOD or series browsing. It's a live-TV tool that does one thing, and does it without apologising for the interface.",
        ],
      },
      {
        heading: "VLC",
        paragraphs: [
          "VLC is not an IPTV app. It is a general media player that will open an M3U the same way it'll open a wedding video from 2009 — without complaint, without context, and without an EPG. Paste your Enktel M3U URL into \"Open Network Stream\" and it plays, but you get a flat list of streams and nothing resembling a guide. It is, in effect, a TV with the channel numbers sanded off.",
          "Where VLC actually earns its spot on your device is as a diagnostic tool. If a channel refuses to play in your main app, opening the same stream URL directly in VLC tells you in seconds whether the stream itself is the problem or your app is simply being difficult — a distinction worth knowing before you write us an angry message.",
        ],
      },
      {
        heading: "Enktel verdict",
        paragraphs: [
          "Run Perfect Player as a lightweight daily driver on older or weaker hardware. Keep VLC installed purely as a troubleshooting tool — it will rarely be your full-time player, but it remains the fastest way to answer \"is it the app or the stream?\" without filing a ticket first.",
        ],
      },
    ],
  },

  // ── Fire TV troubleshooting ─────────────────────────────────
  {
    slug: "fire-tv-buffering-fix",
    category: "troubleshooting",
    title: "Fire TV Buffering Constantly? Here's the Real Fix",
    excerpt:
      "Before you fire off an angry message about your Enktel line, check these four things first — buffering on Fire TV is almost always a Wi-Fi problem wearing a stream's clothes.",
    icon: "🔄",
    publishedAt: "2026-06-18T09:00:00.000Z",
    readMinutes: 4,
    sections: [
      {
        paragraphs: [
          "Buffering is the top ticket in our inbox, and on Fire TV the culprit is almost always one of four things — rarely the Enktel stream itself, since the exact same line usually plays clean on a phone or laptop sitting on the same network.",
        ],
      },
      {
        heading: "1. Wi-Fi signal, not your internet plan",
        paragraphs: [
          "Fire TV Sticks have a small internal antenna and are notoriously sensitive to distance from your router and to 2.4GHz interference from microwaves, baby monitors, and neighbouring Wi-Fi. Open Settings → Network on your Fire TV and check the signal bars — anything below \"Good\" will cause buffering even on a fast internet plan. Moving the router closer, switching the Fire TV to 5GHz, or adding a Wi-Fi extender usually fixes this outright.",
        ],
      },
      {
        heading: "2. Ethernet beats Wi-Fi every time",
        paragraphs: [
          "If your Fire TV model supports the official Fire TV Ethernet Adapter (or you're on a Fire TV Cube with built-in Ethernet), use it. A wired connection removes Wi-Fi variability entirely and is the single biggest reliability upgrade you can make for 4K streaming.",
        ],
      },
      {
        heading: "3. Background app memory",
        paragraphs: [
          "Fire TV's limited RAM means several IPTV apps left running in the background can starve your active player of memory. Go to Settings → Applications → Manage Installed Applications and force-close apps you're not using, or restart the Fire TV (Settings → My Fire TV → Restart) before a big viewing session.",
        ],
      },
      {
        heading: "4. Lower your stream quality as a test",
        paragraphs: [
          "Most Enktel-compatible players let you set a preferred resolution per channel or globally. If 4K buffers but HD plays smoothly, your connection is the bottleneck, not your subscription — drop to HD until your network situation improves, or test with a wired connection per step 2.",
        ],
      },
      {
        heading: "Still stuck?",
        paragraphs: [
          "If buffering happens on a specific channel only, and other channels play fine, message us on WhatsApp from your dashboard with the channel name and time — that's a server-side signal we can act on immediately, rather than a network issue on your end.",
        ],
      },
    ],
  },
  {
    slug: "fire-tv-app-wont-install",
    category: "troubleshooting",
    title: "App Won't Install on Fire TV Stick? Try This First",
    excerpt:
      "Nine times out of ten, the APK is fine. The culprit is storage, a buried toggle, or a Downloader cache holding a grudge.",
    icon: "📦",
    publishedAt: "2026-06-16T09:00:00.000Z",
    readMinutes: 4,
    sections: [
      {
        paragraphs: [
          "\"Installation failed,\" or an app that quietly never shows up after sideloading, is almost always one of three causes — check them in this order before you start questioning the APK itself.",
        ],
      },
      {
        heading: "1. Apps from Unknown Sources isn't enabled",
        paragraphs: [
          "Go to Settings → My Fire TV → Developer Options, and make sure both ADB Debugging and Apps from Unknown Sources are turned ON (on newer Fire OS, this may be a per-app toggle inside Downloader's own settings instead). Without this, Fire OS silently blocks the install with no clear error.",
        ],
      },
      {
        heading: "2. Storage is full",
        paragraphs: [
          "Entry-level Fire TV Sticks ship with very little onboard storage, and IPTV apps plus their EPG cache can fill it fast. Check Settings → My Fire TV → About → Storage. If you're under 500MB free, installs will fail or hang indefinitely — see our storage guide below for how to clear space.",
        ],
      },
      {
        heading: "3. A corrupted Downloader cache",
        paragraphs: [
          "If you've tried installing the same APK more than once, Downloader sometimes holds onto a partial download. Clear its cache via Settings → Applications → Manage Installed Applications → Downloader → Clear Cache, then redownload the APK fresh rather than retrying the same file.",
        ],
      },
      {
        heading: "Still failing?",
        paragraphs: [
          "Restart the Fire TV fully (not just the app) after making any of the above changes — Fire OS doesn't always pick up Developer Options changes until a reboot.",
        ],
      },
    ],
  },
  {
    slug: "fire-tv-epg-not-loading",
    category: "troubleshooting",
    title: "EPG Not Loading on Fire TV: 5-Minute Fix",
    excerpt:
      "A blank guide rarely means anything is actually broken — it usually means a URL got retyped slightly wrong or a cache needs a nudge.",
    icon: "🗓️",
    publishedAt: "2026-06-10T09:00:00.000Z",
    readMinutes: 4,
    sections: [
      {
        paragraphs: [
          "Channels playing fine while the guide sits blank, says \"No data,\" or spins forever is one of the most common Fire TV tickets — and one of the fastest to fix yourself, no waiting on us required.",
        ],
      },
      {
        heading: "1. Re-check your EPG URL character-for-character",
        paragraphs: [
          "Copy your EPG / XMLTV URL fresh from your Enktel dashboard rather than retyping it — a single dropped character is the most common cause of a guide that never populates. Paste it into your player's EPG settings field exactly as shown.",
        ],
      },
      {
        heading: "2. Force an EPG refresh",
        paragraphs: [
          "Most players (TiviMate, IPTV Smarters Pro) have a manual \"Refresh EPG\" or \"Update now\" option buried in playlist or guide settings. Use it rather than waiting for the automatic refresh window, especially right after adding or re-adding your line.",
        ],
      },
      {
        heading: "3. Give it time on first load",
        paragraphs: [
          "On first setup, a full EPG with several days of programme data across Enktel's channel list can take a minute or two to fully populate, especially on older Fire TV hardware. If the guide shows partial data, leave the app open rather than force-closing it.",
        ],
      },
      {
        heading: "4. Clear the app's cache",
        paragraphs: [
          "If the guide is stuck showing old or wrong data, Settings → Applications → Manage Installed Applications → [your player] → Clear Cache (not Clear Data, which would remove your saved login) usually resolves a corrupted local EPG cache.",
        ],
      },
    ],
  },
  {
    slug: "fire-tv-remote-not-pairing",
    category: "troubleshooting",
    title: "Fire TV Remote Not Pairing? Step-by-Step Reset",
    excerpt:
      "Not an Enktel issue in the slightest, but a dead remote stops you watching just as effectively as a dead stream — here's the fix.",
    icon: "🎮",
    publishedAt: "2026-06-05T09:00:00.000Z",
    readMinutes: 3,
    sections: [
      {
        paragraphs: [
          "An unresponsive remote is a Bluetooth/hardware problem with nothing to do with your Enktel subscription — but it still leaves you stuck on the couch staring at a frozen screen, so here's the reset sequence that resolves it most often.",
        ],
      },
      {
        heading: "1. Basic reset",
        paragraphs: [
          "Unplug the Fire TV from power for 30 seconds, then plug it back in. Hold the Home button on the remote for 10–20 seconds while the device boots back up — this often re-establishes the Bluetooth link automatically.",
        ],
      },
      {
        heading: "2. Re-pair manually",
        paragraphs: [
          "If that doesn't work, go to Settings → Controllers & Bluetooth Devices on the Fire TV using a different remote (the Fire TV app on your phone works as a temporary stand-in), remove the unresponsive remote from the list, then hold its Home button to re-enter pairing mode.",
        ],
      },
      {
        heading: "3. Check the batteries",
        paragraphs: [
          "It sounds obvious, but partially-drained batteries are a very common cause of intermittent pairing — low enough to power the remote's lights but not enough for a stable Bluetooth connection. Swap them before assuming it's a software fault.",
        ],
      },
    ],
  },
  {
    slug: "fire-tv-storage-full",
    category: "troubleshooting",
    title: "Fire TV Storage Full: How to Free Up Space Without Losing Your Apps",
    excerpt:
      "Entry-level Fire TV Sticks run out of room the moment you install more than one IPTV app and let the EPG caches pile up. Here's what to clear first.",
    icon: "🧹",
    publishedAt: "2026-06-02T09:00:00.000Z",
    readMinutes: 4,
    sections: [
      {
        paragraphs: [
          "\"Storage space running low\" warnings, sluggish app launches, and installs that just won't finish all trace back to the same cause on base Fire TV Stick models: tiny onboard storage, slowly buried under app data and EPG caches.",
        ],
      },
      {
        heading: "1. Clear cache before clearing data",
        paragraphs: [
          "Go to Settings → Applications → Manage Installed Applications, tap each large app, and use \"Clear Cache\" first. This wipes temporary EPG/thumbnail data without logging you out or losing your Enktel playlist settings. Only use \"Clear Data\" as a last resort — that does remove your saved login.",
        ],
      },
      {
        heading: "2. Uninstall apps you don't actually use",
        paragraphs: [
          "If you tested two or three IPTV players while finding your favourite, uninstall the ones you settled against — each one keeps its own EPG cache running in the background, and that adds up fast.",
        ],
      },
      {
        heading: "3. Disable apps you can't uninstall",
        paragraphs: [
          "Some pre-installed Fire TV apps can't be removed but can be disabled, which frees the storage they're holding. Check Settings → Applications → Manage Installed Applications for a \"Disable\" option on apps you never open.",
        ],
      },
      {
        heading: "4. Consider a storage-friendlier player",
        paragraphs: [
          "If you're consistently tight on space, switch your primary player to something lighter — see our Perfect Player vs VLC comparison for a lower-footprint option that's kinder to older hardware.",
        ],
      },
    ],
  },

  // ── Tel-Vision Weekly (e-magazine) ──────────────────────────
  {
    slug: "tel-vision-weekly-issue-1",
    category: "weekly-mag",
    title: "Tel-Vision Weekly — Issue #1: Welcome to Your New Sunday Read",
    excerpt:
      "Launching our members-only weekly round-up of what's worth watching across entertainment and sport — straight to your dashboard every week.",
    icon: "🗞️",
    publishedAt: "2026-06-21T09:00:00.000Z",
    readMinutes: 5,
    sections: [
      {
        paragraphs: [
          "Welcome to the first issue of Tel-Vision Weekly — Enktel's new members-only e-magazine. Every week we'll round up what's worth your time across entertainment and live sport, in one quick read, without you needing to dig through ten different sites to find it.",
        ],
      },
      {
        heading: "What this series will cover",
        paragraphs: [
          "Each issue lands in three parts: an entertainment lap (what's freshly streaming, what's worth catching up on, what's landing soon — pulled from the same live feed powering the rest of our Blog), a sport lap (a pointer to the week's headline fixtures across the leagues and tournaments on your Enktel guide), and a tip of the week — usually something practical for getting more out of your Enktel setup.",
        ],
      },
      {
        heading: "This week's entertainment lap",
        paragraphs: [
          "Check the \"Now Showing\" and \"On Air\" filters on the main Blog feed for what's live right now — that feed updates throughout the day straight from real schedule data, so it's always current rather than something we wrote once and forgot about.",
        ],
      },
      {
        heading: "This week's sport lap",
        paragraphs: [
          "With the FIFA World Cup 2026 group stage in full swing, the World Cup hub on your dashboard is the fastest way to see real fixture times, live scores, and kickoff countdowns — including Croatia's group matches.",
        ],
      },
      {
        heading: "Tip of the week",
        paragraphs: [
          "If you haven't already, set your preferred device under Dashboard → Setup Guides — it tailors the step-by-step instructions to exactly your hardware (Fire TV, Android, Smart TV, or iOS) instead of showing you a generic guide.",
        ],
      },
      {
        paragraphs: [
          "That's issue one. See you next week.",
        ],
      },
    ],
  },
];

export function getEditorialPost(slug: string): EditorialPost | undefined {
  return editorialPosts.find((p) => p.slug === slug);
}

export function getEditorialPostsByCategory(category?: EditorialPost["category"]): EditorialPost[] {
  const sorted = [...editorialPosts].sort(
    (a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime()
  );
  return category ? sorted.filter((p) => p.category === category) : sorted;
}
