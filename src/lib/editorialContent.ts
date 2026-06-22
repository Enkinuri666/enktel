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
      "Cross-platform, fast EPG loading, and a clean catch-up UI. We put Enktel's M3U and XMLTV feeds through it on Fire TV, Android, and iOS.",
    icon: "📱",
    publishedAt: "2026-06-15T09:00:00.000Z",
    readMinutes: 6,
    sections: [
      {
        paragraphs: [
          "IPTV Smarters Pro is the app we point most new Enktel members toward first, and after months of running our own M3U and XMLTV URLs through it across Fire TV, Android, iOS, and a couple of Smart TVs, it's still our default recommendation for anyone who wants one app that just works everywhere.",
        ],
      },
      {
        heading: "Setup with Enktel credentials",
        paragraphs: [
          "Adding your Enktel line takes under a minute: open the app, choose \"Login with Xtream Codes API\", and paste in the Server URL, username, and password from your Enktel dashboard. The app pulls your full channel list and EPG automatically — no manual M3U upload needed, though that option exists too if you prefer pasting your playlist and XMLTV URLs directly.",
          "On Fire TV specifically, install via Downloader using the APK link from the IPTV Smarters Pro website (it isn't on the Amazon Appstore), then sideload as usual. Once it's in, performance on even a base Fire TV Stick (non-4K) is solid for SD/HD channels; 4K channels are smoother on a Fire TV Stick 4K Max or Fire TV Cube.",
        ],
      },
      {
        heading: "What we liked",
        paragraphs: [
          "EPG rendering is fast even with Enktel's full channel count loaded, and the catch-up/timeshift menu (when a channel supports it) is one of the cleanest implementations we've tested. Multi-screen and parental PIN controls are also genuinely useful if more than one person uses the line.",
        ],
      },
      {
        heading: "What to watch out for",
        paragraphs: [
          "The free version carries ads on the home screen; the one-time Pro unlock removes them and is worth it if you're using this as your daily driver. We'd also recommend disabling EPG auto-refresh on a schedule rather than on every app open — it shaves a few seconds off cold-start time, especially on older Fire TV hardware.",
        ],
      },
      {
        heading: "Enktel verdict",
        paragraphs: [
          "If you want a single player that covers phones, tablets, Fire TV, and Smart TVs without compromise, IPTV Smarters Pro is the safest first install. Pair it with your Enktel Xtream Codes login from your dashboard and you'll be streaming in minutes.",
        ],
      },
    ],
  },
  {
    slug: "tivimate-fire-tv-review",
    category: "player-review",
    title: "TiviMate on Fire TV: Why It's Our Top Pick for Live TV + EPG",
    excerpt:
      "If live TV and a proper TV-guide grid are what you care about most, TiviMate's Premium EPG layout is hard to beat on Fire TV.",
    icon: "📺",
    publishedAt: "2026-06-12T09:00:00.000Z",
    readMinutes: 5,
    sections: [
      {
        paragraphs: [
          "TiviMate was built around the TV experience first, and it shows the moment you load an Enktel playlist into it on a Fire TV Stick or Cube. The EPG grid view — channels down the side, a scrolling timeline across the top — is the closest thing to a traditional cable guide that we've tested in any third-party app.",
        ],
      },
      {
        heading: "Adding your Enktel line",
        paragraphs: [
          "From the home screen, choose \"Add playlist\" → \"Xtream Codes login\", then enter your Enktel Server URL, username, and password exactly as shown in your dashboard. TiviMate will fetch your full channel list and EPG in one step; you can add your EPG/XMLTV URL separately under playlist settings if you want to merge multiple guide sources.",
        ],
      },
      {
        heading: "Premium vs free tier",
        paragraphs: [
          "The free tier covers single-playlist use fine, but TiviMate Premium unlocks multiple playlists, a second EPG source, and channel-list backup/restore — handy if you ever reset your Fire TV or move to a new device and want your favourites and channel order to come with you.",
        ],
      },
      {
        heading: "Performance notes",
        paragraphs: [
          "Channel-switching latency is consistently the lowest we've measured against Enktel streams compared to other Fire TV players, and the picture-in-picture EPG browsing (keep watching while you scroll the guide) is genuinely useful during live sport.",
        ],
      },
      {
        heading: "Enktel verdict",
        paragraphs: [
          "For members who mostly watch live channels rather than VOD, TiviMate paired with your Enktel Xtream login is our top Fire TV recommendation. Sideload it via Downloader, log in with your Enktel credentials, and you've got the closest thing to a satellite-box guide experience on a streaming stick.",
        ],
      },
    ],
  },
  {
    slug: "perfect-player-vs-vlc",
    category: "player-review",
    title: "Perfect Player vs VLC: Which One Should You Use With Enktel?",
    excerpt:
      "Two very different philosophies — one is a dedicated IPTV player, the other a general media player that happens to handle M3U fine. Here's when to use which.",
    icon: "⚔️",
    publishedAt: "2026-06-08T09:00:00.000Z",
    readMinutes: 5,
    sections: [
      {
        paragraphs: [
          "Perfect Player and VLC come up constantly in support tickets as alternatives when someone's primary IPTV app is misbehaving, and they solve different problems. Here's how each performs with an Enktel playlist loaded.",
        ],
      },
      {
        heading: "Perfect Player",
        paragraphs: [
          "Perfect Player is purpose-built for IPTV: paste in your Enktel M3U playlist URL and EPG/XMLTV URL under Settings → General, and it builds a channel list with a TV-guide style EPG overlay. It's lighter on resources than most full Xtream-style apps, which makes it a good pick for older Android boxes or Fire TV Stick (1st gen / Lite) hardware that struggles with heavier players.",
          "The trade-off is a less polished UI and no native VOD/series browsing — it's a live-TV-first tool, which is exactly what makes it fast.",
        ],
      },
      {
        heading: "VLC",
        paragraphs: [
          "VLC isn't an IPTV app at all — it's a general media player that can open an M3U file or URL like any other media source. You can paste your Enktel M3U URL directly into VLC's \"Open Network Stream\" dialog and it'll play, but you lose EPG, channel categories, and any kind of guide entirely; it's just a flat list of streams.",
          "Where VLC earns its place is as a diagnostic tool: if a channel won't play in your main app, opening the same stream URL directly in VLC tells you in seconds whether the problem is the stream itself or the app rendering it.",
        ],
      },
      {
        heading: "Enktel verdict",
        paragraphs: [
          "Use Perfect Player as a lightweight daily driver on older or lower-powered hardware. Keep VLC installed purely as a troubleshooting tool to isolate stream issues — it's rarely the best full-time choice, but it's the fastest way to rule out \"is it the app or the stream?\" when something stops working.",
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
      "Before you blame your Enktel line, check these four things — most buffering on Fire TV traces back to Wi-Fi, not the stream.",
    icon: "🔄",
    publishedAt: "2026-06-18T09:00:00.000Z",
    readMinutes: 4,
    sections: [
      {
        paragraphs: [
          "Buffering is the single most common ticket we get, and on Fire TV the cause is almost always one of four things — rarely the Enktel stream itself, since the same line typically plays clean on a phone or laptop on the same network.",
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
      "Most install failures on Fire TV come down to storage, Developer Options, or a stale Downloader cache — not a broken APK.",
    icon: "📦",
    publishedAt: "2026-06-16T09:00:00.000Z",
    readMinutes: 4,
    sections: [
      {
        paragraphs: [
          "\"Installation failed\" or an app that silently never appears after sideloading is almost always one of three causes, in this order of likelihood.",
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
      "A blank or stuck TV guide is almost always a stale cache or a re-typed EPG URL — here's the checklist before you assume something's broken.",
    icon: "🗓️",
    publishedAt: "2026-06-10T09:00:00.000Z",
    readMinutes: 4,
    sections: [
      {
        paragraphs: [
          "Channels playing fine but the guide showing blank, \"No data\", or spinning forever is one of the most common Fire TV tickets — and one of the quickest to fix yourself.",
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
      "Not an Enktel issue, but it stops you watching just the same — here's the fastest way to get an unresponsive Fire TV remote back online.",
    icon: "🎮",
    publishedAt: "2026-06-05T09:00:00.000Z",
    readMinutes: 3,
    sections: [
      {
        paragraphs: [
          "A remote that's gone unresponsive is a hardware/Bluetooth issue rather than anything to do with your Enktel subscription, but since it blocks you from watching, here's the reset sequence that resolves it most often.",
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
      "Entry-level Fire TV Sticks run out of space fast once you've got two or three IPTV apps with EPG caches installed. Here's what to clear first.",
    icon: "🧹",
    publishedAt: "2026-06-02T09:00:00.000Z",
    readMinutes: 4,
    sections: [
      {
        paragraphs: [
          "\"Storage space running low\" warnings, slow app launches, and failed installs almost always trace back to the same cause on base Fire TV Stick models: limited onboard storage filled up by app data and EPG caches.",
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
          "With the FIFA World Cup 2026 group stage in full swing, the World Cup hub on your dashboard is the fastest way to see real fixture times, live scores, and kickoff countdowns — including Croatia's group matches. We've also added a looping promo clip for the upcoming Panama vs Croatia group match on the homepage and World Cup page, so you won't miss the kickoff.",
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
