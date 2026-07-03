import { gunzipSync } from "zlib";
import { EPGProgram } from "@/types";
import { channels } from "@/lib/channels";

interface SourceChannelMapping {
  file: string;
  sourceId: string;
}

// Channels (by our internal channel id) with no match in the primary feed
// below (mostly UK broadcasters) — kept on a free, publicly hosted XMLTV
// guide (epgshare01.online), refreshed daily by that project's own
// scrapers. Every legacy channel in src/lib/channels.ts must have an entry
// here - channels with no findable real source are removed from the
// catalog entirely rather than shown with invented schedule data.
const LEGACY_EPG_SOURCE_MAP: Record<string, SourceChannelMapping> = {
  "nova-tv": { file: "HR1", sourceId: "Nova.TV.HD.hr" },
  "rtl-hrvatska": { file: "HR1", sourceId: "RTL.HD.hr" },
  "rtl-2": { file: "HR1", sourceId: "RTL.2.HD.hr" },
  "doma-tv": { file: "HR1", sourceId: "Doma.TV.HD.hr" },
  "cmc-tv": { file: "HR1", sourceId: "CMC.hr" },
  "hayat-tv": { file: "BA1", sourceId: "Hayat.HD.ba" },
  "rts-1": { file: "RS1", sourceId: "RTS.1.HD.rs" },
  pink: { file: "RS1", sourceId: "Pink.HD.rs" },
  "nova-s": { file: "RS1", sourceId: "NOVA.S.HD.(RS).rs" },
  "arena-sport-1": { file: "HR1", sourceId: "Arena.Sport.1.HD.hr" },
  "arena-sport-2": { file: "HR1", sourceId: "Arena.Sport.2.HD.hr" },
  "sky-sports-main": { file: "UK1", sourceId: "SkySpMainEvHD.uk" },
  "sky-sports-football": { file: "UK1", sourceId: "Sky.Sports.Football.HD.uk" },
  "sky-sports-cricket": { file: "UK1", sourceId: "SkySpCricket.HD.uk" },
  "tnt-sports-1": { file: "UK1", sourceId: "TNT.Sports.1.HD.uk" },
  "tnt-sports-2": { file: "UK1", sourceId: "TNT.Sports.2.HD.uk" },
  "sky-cinema-comedy": { file: "UK1", sourceId: "Sky.Cinema.Comedy.uk" },
  film4: { file: "UK1", sourceId: "Film4.HD.uk" },
  "bbc-news": { file: "UK1", sourceId: "BBC.NEWS.HD.uk" },
  "sky-news": { file: "UK1", sourceId: "Sky.News.HD.uk" },
  "cnn-intl": { file: "UK1", sourceId: "CNN.HD.uk" },
  "bbc-world": { file: "AE1", sourceId: "BBC.World.News.ae" },
  "bbc-one": { file: "UK1", sourceId: "BBC.One.Lon.HD.uk" },
  "bbc-two": { file: "UK1", sourceId: "BBC.Two.HD.uk" },
  itv1: { file: "UK1", sourceId: "ITV1.HD.uk" },
  itv2: { file: "UK1", sourceId: "ITV2.HD.uk" },
  channel4: { file: "UK1", sourceId: "Channel.4.HD.uk" },
  channel5: { file: "UK1", sourceId: "Channel.5.HD.uk" },
  e4: { file: "UK1", sourceId: "E4.HD.uk" },
  dave: { file: "UK1", sourceId: "U.and.Dave.HD.uk" },
  gold: { file: "UK1", sourceId: "U.and.GOLD.HD.uk" },
  "sky-one": { file: "UK1", sourceId: "Sky.One.HD.uk" },
  cbbc: { file: "UK1", sourceId: "CBBC.HD.uk" },
  cbeebies: { file: "UK1", sourceId: "CBeebies.HD.uk" },
  "cartoon-network": { file: "UK1", sourceId: "Cartoon.Net.HD.uk" },
  "nick-jr": { file: "UK1", sourceId: "Nick.Jr..HD.uk" },
  "disney-channel": { file: "CZ1", sourceId: "Disney.Channel.cz" },
  "disney-jr": { file: "CZ1", sourceId: "Disney.Junior.cz" },
  "bbc-four": { file: "UK1", sourceId: "BBC.Four.HD.uk" },
  eden: { file: "UK1", sourceId: "U.and.Eden.uk" },
  kerrang: { file: "UK1", sourceId: "Kerrang!.uk" },
};

const LEGACY_SOURCE_URL = (file: string) => `https://epgshare01.online/epgshare01/epg_ripper_${file}.xml.gz`;

// These guides are regenerated roughly daily upstream, so a multi-hour
// cache keeps us fresh without re-downloading/parsing multi-MB XML files
// on every request.
const LEGACY_CACHE_TTL_MS = 3 * 60 * 60 * 1000;
const legacyFileCache = new Map<string, { fetchedAt: number; programs: Map<string, EPGProgram[]> }>();

function decodeXmlEntities(s: string): string {
  return s
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'");
}

// XMLTV times look like "20260620180000 +0200".
function parseXmltvTime(raw: string): string {
  const m = raw.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})\s*([+-]\d{4})?$/);
  if (!m) return new Date(raw).toISOString();
  const [, y, mo, d, h, mi, s, tz] = m;
  const offset = tz ? `${tz.slice(0, 3)}:${tz.slice(3)}` : "+00:00";
  return new Date(`${y}-${mo}-${d}T${h}:${mi}:${s}${offset}`).toISOString();
}

function mapCategory(raw: string): string {
  const lower = raw.toLowerCase();
  if (/sport/.test(lower)) return "Sports";
  if (/news/.test(lower)) return "News";
  if (/movie|film/.test(lower)) return "Movies";
  if (/document/.test(lower)) return "Documentary";
  if (/kids|children|cartoon|animat/.test(lower)) return "Kids";
  if (/music/.test(lower)) return "Music";
  if (/drama|soap|series/.test(lower)) return "Drama";
  return "Entertainment";
}

// One non-greedy pass over the whole XMLTV document. Attribute order on
// the <programme> tag varies between sources, so attributes are pulled
// out of the opening tag individually rather than assumed to be in a
// fixed order.
async function fetchAndParseLegacyFile(file: string, sourceIds: string[]): Promise<Map<string, EPGProgram[]>> {
  const wanted = new Set(sourceIds);
  const result = new Map<string, EPGProgram[]>();
  for (const id of sourceIds) result.set(id, []);

  const res = await fetch(LEGACY_SOURCE_URL(file), {
    signal: AbortSignal.timeout(20000),
    next: { revalidate: LEGACY_CACHE_TTL_MS / 1000 },
  });
  if (!res.ok) throw new Error(`EPG source ${file} returned ${res.status}`);
  const gz = Buffer.from(await res.arrayBuffer());
  const xml = gunzipSync(gz).toString("utf-8");

  const programmeRe = /<programme\s+([^>]*)>([\s\S]*?)<\/programme>/g;
  const counters = new Map<string, number>();
  let match: RegExpExecArray | null;
  while ((match = programmeRe.exec(xml)) !== null) {
    const [, attrs, inner] = match;
    const channelMatch = attrs.match(/channel="([^"]+)"/);
    const channel = channelMatch?.[1];
    if (!channel || !wanted.has(channel)) continue;

    const startMatch = attrs.match(/start="([^"]+)"/);
    const stopMatch = attrs.match(/stop="([^"]+)"/);
    if (!startMatch || !stopMatch) continue;

    const titleMatch = inner.match(/<title[^>]*>([^<]*)<\/title>/);
    const descMatch = inner.match(/<desc[^>]*>([^<]*)<\/desc>/);
    const iconMatch = inner.match(/<icon src="([^"]*)"/);
    const categoryMatches: string[] = [];
    const categoryRe = /<category[^>]*>([^<]*)<\/category>/g;
    let categoryMatch: RegExpExecArray | null;
    while ((categoryMatch = categoryRe.exec(inner)) !== null) categoryMatches.push(categoryMatch[1]);

    const count = counters.get(channel) || 0;
    counters.set(channel, count + 1);

    result.get(channel)!.push({
      id: `live-${channel}-${count}`,
      channelId: "",
      title: titleMatch ? decodeXmlEntities(titleMatch[1]) : "Programme",
      description: descMatch ? decodeXmlEntities(descMatch[1]) : "",
      startTime: parseXmltvTime(startMatch[1]),
      endTime: parseXmltvTime(stopMatch[1]),
      category: mapCategory(categoryMatches.join(" ")),
      imageUrl: iconMatch ? iconMatch[1] : undefined,
      source: "live",
    });
  }

  return result;
}

async function getLegacyFilePrograms(file: string): Promise<Map<string, EPGProgram[]>> {
  const cached = legacyFileCache.get(file);
  if (cached && Date.now() - cached.fetchedAt < LEGACY_CACHE_TTL_MS) {
    return cached.programs;
  }

  const sourceIds = Object.values(LEGACY_EPG_SOURCE_MAP)
    .filter((m) => m.file === file)
    .map((m) => m.sourceId);

  const programs = await fetchAndParseLegacyFile(file, sourceIds);
  legacyFileCache.set(file, { fetchedAt: Date.now(), programs });
  return programs;
}

// Real EPG schedules for the legacy (mostly UK) channels not covered by the
// primary feed below, pulled from a free, publicly hosted XMLTV guide.
// Returns an empty object (never throws) if every source is unreachable,
// rather than fabricating data.
async function getLegacyEpgPrograms(): Promise<Record<string, EPGProgram[]>> {
  const files = Array.from(new Set(Object.values(LEGACY_EPG_SOURCE_MAP).map((m) => m.file)));
  const filePrograms = await Promise.all(
    files.map(async (file) => {
      try {
        return { file, programs: await getLegacyFilePrograms(file) };
      } catch {
        return { file, programs: new Map<string, EPGProgram[]>() };
      }
    })
  );

  const byFile = new Map(filePrograms.map((f) => [f.file, f.programs]));
  const result: Record<string, EPGProgram[]> = {};

  for (const [channelId, mapping] of Object.entries(LEGACY_EPG_SOURCE_MAP)) {
    const programs = byFile.get(mapping.file)?.get(mapping.sourceId) || [];
    if (programs.length === 0) continue;
    result[channelId] = programs.map((p, i) => ({ ...p, id: `${channelId}-live-${i}`, channelId }));
  }

  return result;
}

// ─────────────────────────────────────────────────────────────────────────
// Primary source: a single up-to-date XMLTV feed (IPTVEditor-generated)
// covering ~600 real US, Australian, and Croatian/Balkan channels with real
// logos, replacing what used to be a thin curated demo. Every non-legacy
// channel in src/lib/channels.ts uses this feed's own channel id directly
// as both its `id` and `epgId`, so no separate id-mapping table is needed
// here — the wanted id set is just "every channel not on the legacy map".
// ─────────────────────────────────────────────────────────────────────────
const PRIMARY_EPG_URL = "https://opop.pro/tWcaWbtxE5UZ9t";

// The upstream feed's own cache-control advertises an 8-minute refresh
// window; 20 minutes balances staying current against re-downloading and
// re-parsing a ~35MB document (proportionally larger than any single
// legacy file, so cached separately here rather than via Next's fetch
// cache, which is meant for much smaller responses).
const PRIMARY_CACHE_TTL_MS = 20 * 60 * 1000;
let primaryFeedCache: { fetchedAt: number; programs: Map<string, EPGProgram[]> } | null = null;

const primaryChannelCategory = new Map(channels.map((c) => [c.id, c.category] as const));

async function fetchAndParsePrimaryFeed(wantedIds: Set<string>): Promise<Map<string, EPGProgram[]>> {
  const result = new Map<string, EPGProgram[]>();
  for (const id of Array.from(wantedIds)) result.set(id, []);

  const res = await fetch(PRIMARY_EPG_URL, {
    signal: AbortSignal.timeout(45000),
    cache: "no-store",
  });
  if (!res.ok) throw new Error(`Primary EPG source returned ${res.status}`);
  const xml = await res.text();

  const programmeRe = /<programme\s+([^>]*)>([\s\S]*?)<\/programme>/g;
  const counters = new Map<string, number>();
  let match: RegExpExecArray | null;
  while ((match = programmeRe.exec(xml)) !== null) {
    const [, attrs, inner] = match;
    const channelMatch = attrs.match(/channel="([^"]+)"/);
    const channel = channelMatch?.[1];
    if (!channel || !wantedIds.has(channel)) continue;

    const startMatch = attrs.match(/start="([^"]+)"/);
    const stopMatch = attrs.match(/stop="([^"]+)"/);
    if (!startMatch || !stopMatch) continue;

    const titleMatch = inner.match(/<title[^>]*>([^<]*)<\/title>/);
    const descMatch = inner.match(/<desc[^>]*>([^<]*)<\/desc>/);
    const iconMatch = inner.match(/<icon src="([^"]*)"/);
    const ratingMatch = inner.match(/<rating[^>]*>\s*<value>([^<]*)<\/value>/);

    const count = counters.get(channel) || 0;
    counters.set(channel, count + 1);

    result.get(channel)!.push({
      id: `live-${channel}-${count}`,
      channelId: "",
      title: titleMatch ? decodeXmlEntities(titleMatch[1]) : "Programme",
      description: descMatch ? decodeXmlEntities(descMatch[1]) : "",
      startTime: parseXmltvTime(startMatch[1]),
      endTime: parseXmltvTime(stopMatch[1]),
      // This feed has no per-programme <category> tag, unlike the legacy
      // source — fall back to the channel's own catalog category instead.
      category: primaryChannelCategory.get(channel) || "Entertainment",
      rating: ratingMatch ? decodeXmlEntities(ratingMatch[1]) : undefined,
      imageUrl: iconMatch ? iconMatch[1] : undefined,
      source: "live",
    });
  }

  return result;
}

async function getPrimaryEpgPrograms(): Promise<Record<string, EPGProgram[]>> {
  const wantedIds = new Set(channels.map((c) => c.id).filter((id) => !LEGACY_EPG_SOURCE_MAP[id]));

  if (primaryFeedCache && Date.now() - primaryFeedCache.fetchedAt < PRIMARY_CACHE_TTL_MS) {
    const result: Record<string, EPGProgram[]> = {};
    for (const id of Array.from(wantedIds)) {
      const programs = primaryFeedCache.programs.get(id);
      if (programs && programs.length > 0) result[id] = programs.map((p, i) => ({ ...p, id: `${id}-live-${i}`, channelId: id }));
    }
    return result;
  }

  try {
    const programs = await fetchAndParsePrimaryFeed(wantedIds);
    primaryFeedCache = { fetchedAt: Date.now(), programs };
    const result: Record<string, EPGProgram[]> = {};
    for (const id of Array.from(wantedIds)) {
      const list = programs.get(id);
      if (list && list.length > 0) result[id] = list.map((p, i) => ({ ...p, id: `${id}-live-${i}`, channelId: id }));
    }
    return result;
  } catch {
    // Serve stale cached data rather than nothing if a refetch fails.
    if (primaryFeedCache) {
      const result: Record<string, EPGProgram[]> = {};
      for (const id of Array.from(wantedIds)) {
        const programs = primaryFeedCache.programs.get(id);
        if (programs && programs.length > 0) result[id] = programs.map((p, i) => ({ ...p, id: `${id}-live-${i}`, channelId: id }));
      }
      return result;
    }
    return {};
  }
}

// Real EPG schedules merged from both sources — the primary up-to-date
// feed for the channels it carries, and the legacy XMLTV mirrors for the
// remaining ones. Never invents schedule data for a channel neither source
// carries.
export async function getRealEpgPrograms(): Promise<Record<string, EPGProgram[]>> {
  const [legacy, primary] = await Promise.all([
    getLegacyEpgPrograms().catch(() => ({}) as Record<string, EPGProgram[]>),
    getPrimaryEpgPrograms().catch(() => ({}) as Record<string, EPGProgram[]>),
  ]);
  return { ...legacy, ...primary };
}
