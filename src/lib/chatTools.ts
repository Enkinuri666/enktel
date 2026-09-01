import type { OpenAI } from "openai";
import { channels, channelCategories, CHANNEL_COUNT_LABEL } from "@/lib/channels";
import { fetchEPGData } from "@/lib/epg";
import { DEVICE_GUIDES } from "@/lib/deviceGuides";
import { searchFaqs } from "@/lib/faqs";
import { getMockUpcomingEvents } from "@/lib/mock-data";
import { getRealUpcomingEvents } from "@/lib/sportsApi";
import { withFallback } from "@/lib/dataSource";
import { PLATFORM_FEATURES } from "@/lib/platformFeatures";

// Tool schemas passed to the OpenAI API (Chat Completions function-calling
// format). Keep names/descriptions specific — the model leans on the
// description to decide *when* to call each tool, not just what it returns.
export const CHAT_TOOLS: OpenAI.Chat.Completions.ChatCompletionTool[] = [
  {
    type: "function",
    function: {
      name: "search_channels",
      description:
        "Search the Enktel channel lineup by name or category. Use this when the customer asks whether a specific channel is included, or wants a list of channels in a category (e.g. Sports, Croatian & Balkan, Movies).",
      parameters: {
        type: "object",
        properties: {
          query: { type: "string", description: "Channel name or partial name to search for, e.g. 'HRT' or 'Sky Sports'." },
          category: { type: "string", enum: channelCategories as unknown as string[], description: "Filter to a specific category. Use 'All' for no filter." },
        },
      },
    },
  },
  {
    type: "function",
    function: {
      name: "whats_on_channel",
      description:
        "Look up what's currently playing (and what's on next) on a specific live TV channel. Use this whenever the customer asks what's on now, what's playing, or for a channel's current programme.",
      parameters: {
        type: "object",
        properties: {
          channelName: { type: "string", description: "The channel name, e.g. 'HRT 1', 'Nova TV', 'Sky Sports Main Event'." },
        },
        required: ["channelName"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "get_setup_guide",
      description:
        "Get step-by-step setup instructions for a specific device (Firestick, Smart TV, MAG box, phone/tablet, PC, router) or for the Web Player (watch.enktel.tv, works in any browser with no app to install). Use this whenever the customer asks how to install or configure Enktel on a device, or how to watch without downloading a separate IPTV player app.",
      parameters: {
        type: "object",
        properties: {
          device: { type: "string", description: "The device name, e.g. 'firestick', 'smart tv', 'iphone', 'android', 'mag box', 'pc', 'router', or 'web player' / 'browser'." },
        },
        required: ["device"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "search_faqs",
      description:
        "Search Enktel's frequently asked questions (billing, refunds, buffering, playlist issues, device limits, etc.). Use this for account policy or troubleshooting questions before answering from general knowledge.",
      parameters: {
        type: "object",
        properties: {
          query: { type: "string", description: "Keywords describing the customer's question." },
        },
        required: ["query"],
      },
    },
  },
  {
    type: "function",
    function: {
      name: "get_upcoming_events",
      description:
        "Get the list of upcoming live sports fixtures and pay-per-view events (football, UFC, F1, etc.) with channel and kickoff time. Use this for 'what's on later' or 'any big matches coming up' style questions.",
      parameters: { type: "object", properties: {} },
    },
  },
  {
    type: "function",
    function: {
      name: "get_platform_features",
      description:
        "Get the list of Enktel's own website/platform features and where to find them — including the Web Player (watch.enktel.tv, a free browser player that's an alternative to downloading and paying for a separate IPTV player app), Latest Releases, What's New, the Enktel Wire hub, the EPG guide, and the dashboard. Use this whenever the customer asks what Enktel offers beyond live TV, how to watch without a third-party app, or where to find new movies/shows/updates.",
      parameters: { type: "object", properties: {} },
    },
  },
];

function fuzzyFindChannel(name: string) {
  const needle = name.trim().toLowerCase();
  return (
    channels.find((c) => c.name.toLowerCase() === needle) ||
    channels.find((c) => c.name.toLowerCase().includes(needle)) ||
    channels.find((c) => needle.includes(c.name.toLowerCase()))
  );
}

function fuzzyFindDevice(name: string) {
  const needle = name.trim().toLowerCase();
  const aliases: Record<string, string> = {
    firestick: "firestick",
    "fire stick": "firestick",
    "fire tv": "firestick",
    "smart tv": "smart-tv",
    samsung: "smart-tv",
    lg: "smart-tv",
    mag: "mag",
    "mag box": "mag",
    iphone: "mobile",
    ipad: "mobile",
    android: "mobile",
    phone: "mobile",
    tablet: "mobile",
    pc: "pc",
    windows: "pc",
    mac: "pc",
    computer: "pc",
    vlc: "pc",
    router: "router",
    "web player": "web-player",
    webplayer: "web-player",
    browser: "web-player",
    "watch.enktel.tv": "web-player",
    "no app": "web-player",
    "without an app": "web-player",
  };
  for (const [alias, id] of Object.entries(aliases)) {
    if (needle.includes(alias)) return DEVICE_GUIDES.find((d) => d.id === id);
  }
  return DEVICE_GUIDES.find((d) => d.label.toLowerCase().includes(needle));
}

export async function runChatTool(name: string, input: Record<string, unknown>): Promise<string> {
  switch (name) {
    case "search_channels": {
      const query = typeof input.query === "string" ? input.query.trim().toLowerCase() : "";
      const category = typeof input.category === "string" ? input.category : "All";
      let results = category && category !== "All" ? channels.filter((c) => c.category === category) : channels;
      if (query) results = results.filter((c) => c.name.toLowerCase().includes(query));
      const sample = results.slice(0, 25).map((c) => `${c.name} (${c.category}, ${c.isHD ? "HD" : "SD"})`);
      return JSON.stringify({
        totalMatching: results.length,
        sample,
        note: `This is a curated sample list for the site's guide UI — the real Enktel lineup carries ${CHANNEL_COUNT_LABEL} channels in total.`,
      });
    }

    case "whats_on_channel": {
      const channelName = typeof input.channelName === "string" ? input.channelName : "";
      const channel = fuzzyFindChannel(channelName);
      if (!channel) {
        return JSON.stringify({ found: false, message: `No channel matching "${channelName}" in the sample guide — it may still be in the full lineup.` });
      }
      const programs = await fetchEPGData();
      const now = new Date();
      const channelPrograms = programs
        .filter((p) => p.channelId === channel.id)
        .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime());
      const current = channelPrograms.find((p) => new Date(p.startTime) <= now && new Date(p.endTime) > now);
      if (!current) {
        return JSON.stringify({ found: true, channel: channel.name, message: "No live schedule data available for this channel right now." });
      }
      const next = channelPrograms[channelPrograms.indexOf(current) + 1];
      return JSON.stringify({
        found: true,
        channel: channel.name,
        now: { title: current.title, endsAt: current.endTime },
        next: next ? { title: next.title, startsAt: next.startTime } : null,
      });
    }

    case "get_setup_guide": {
      const device = typeof input.device === "string" ? input.device : "";
      const guide = fuzzyFindDevice(device);
      if (!guide) {
        return JSON.stringify({ found: false, message: `No specific guide for "${device}" — point the customer to the Setup Guides page or ask which device they use.` });
      }
      return JSON.stringify({
        found: true,
        device: guide.label,
        recommendedApp: guide.app,
        steps: guide.steps.map((s) => `${s.step}. ${s.title}: ${s.description}`),
      });
    }

    case "search_faqs": {
      const query = typeof input.query === "string" ? input.query : "";
      const matches = searchFaqs(query);
      if (matches.length === 0) return JSON.stringify({ found: false });
      return JSON.stringify({ found: true, matches: matches.map((m) => ({ question: m.q, answer: m.a })) });
    }

    case "get_upcoming_events": {
      const { data: events } = await withFallback(
        async () => {
          const real = await getRealUpcomingEvents();
          if (real.length === 0) throw new Error("no live events");
          return real;
        },
        () => getMockUpcomingEvents(),
        { sourceName: "thesportsdb" }
      );
      return JSON.stringify({
        events: events.slice(0, 8).map((e) => ({
          title: e.title,
          competition: e.competition,
          channel: e.channel,
          startTime: e.startTime,
          isLive: e.isLive,
          isPPV: e.isPPV,
        })),
      });
    }

    case "get_platform_features": {
      return JSON.stringify({ features: PLATFORM_FEATURES });
    }

    default:
      return JSON.stringify({ error: `Unknown tool: ${name}` });
  }
}
