import { gunzipSync } from "zlib";
import { EPGProgram } from "@/types";

interface SourceChannelMapping {
  file: string;
  sourceId: string;
}

// Channels (by our internal channel id) with a known match in a free,
// publicly hosted XMLTV guide (epgshare01.online), refreshed daily by that
// project's own scrapers. Channels not listed here have no free real-data
// source and fall back to the simulated schedule in src/lib/mock-data.ts.
const EPG_SOURCE_MAP: Record<string, SourceChannelMapping> = {
  "nova-tv": { file: "HR1", sourceId: "Nova.TV.HD.hr" },
  "rtl-hrvatska": { file: "HR1", sourceId: "RTL.HD.hr" },
  "rtl-2": { file: "HR1", sourceId: "RTL.2.HD.hr" },
  "doma-tv": { file: "HR1", sourceId: "Doma.TV.HD.hr" },
  "cmc-tv": { file: "HR1", sourceId: "CMC.hr" },
  "arena-sport-1": { file: "HR1", sourceId: "Arena.Sport.1.HD.hr" },
  "arena-sport-2": { file: "HR1", sourceId: "Arena.Sport.2.HD.hr" },
  "hayat-tv": { file: "BA1", sourceId: "Hayat.HD.ba" },
  ftv: { file: "BA1", sourceId: "FTV.HD.(BIH).ba" },
  "rts-1": { file: "RS1", sourceId: "RTS.1.HD.rs" },
  pink: { file: "RS1", sourceId: "Pink.HD.rs" },
  "nova-s": { file: "RS1", sourceId: "NOVA.S.HD.(RS).rs" },
  "bbc-news": { file: "UK1", sourceId: "BBC.NEWS.HD.uk" },
  cbbc: { file: "UK1", sourceId: "CBBC.HD.uk" },
  cbeebies: { file: "UK1", sourceId: "CBeebies.HD.uk" },
  "cnn-intl": { file: "UK1", sourceId: "CNN.HD.uk" },
  channel4: { file: "UK1", sourceId: "Channel.4.HD.uk" },
  channel5: { file: "UK1", sourceId: "Channel.5.HD.uk" },
  e4: { file: "UK1", sourceId: "E4.HD.uk" },
  film4: { file: "UK1", sourceId: "Film4.HD.uk" },
  itv1: { file: "UK1", sourceId: "ITV1.HD.uk" },
  kerrang: { file: "UK1", sourceId: "Kerrang!.uk" },
  mtv: { file: "UK1", sourceId: "MTV.HD.uk" },
  "nat-geo": { file: "UK1", sourceId: "Nat.Geo.HD.uk" },
  discovery: { file: "UK1", sourceId: "Discovery.HD.uk" },
  "nick-jr": { file: "UK1", sourceId: "Nick.Jr..HD.uk" },
  "sky-sports-football": { file: "UK1", sourceId: "Sky.Sports.Football.HD.uk" },
  "sky-news": { file: "UK1", sourceId: "Sky.News.HD.uk" },
  dave: { file: "UK1", sourceId: "U.and.Dave.HD.uk" },
  gold: { file: "UK1", sourceId: "U.and.GOLD.HD.uk" },
};

// The "Eagle 4K" filter on the EPG page shows exactly this set: channels we
// can actually back with real (non-simulated) programme data.
export const REAL_EPG_CHANNEL_IDS: ReadonlySet<string> = new Set(Object.keys(EPG_SOURCE_MAP));

const SOURCE_URL = (file: string) => `https://epgshare01.online/epgshare01/epg_ripper_${file}.xml.gz`;

// These guides are regenerated roughly daily upstream, so a multi-hour
// cache keeps us fresh without re-downloading/parsing multi-MB XML files
// on every request.
const CACHE_TTL_MS = 3 * 60 * 60 * 1000;
const fileCache = new Map<string, { fetchedAt: number; programs: Map<string, EPGProgram[]> }>();

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
async function fetchAndParseFile(file: string, sourceIds: string[]): Promise<Map<string, EPGProgram[]>> {
  const wanted = new Set(sourceIds);
  const result = new Map<string, EPGProgram[]>();
  for (const id of sourceIds) result.set(id, []);

  const res = await fetch(SOURCE_URL(file), {
    signal: AbortSignal.timeout(20000),
    next: { revalidate: CACHE_TTL_MS / 1000 },
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

async function getFilePrograms(file: string): Promise<Map<string, EPGProgram[]>> {
  const cached = fileCache.get(file);
  if (cached && Date.now() - cached.fetchedAt < CACHE_TTL_MS) {
    return cached.programs;
  }

  const sourceIds = Object.values(EPG_SOURCE_MAP)
    .filter((m) => m.file === file)
    .map((m) => m.sourceId);

  const programs = await fetchAndParseFile(file, sourceIds);
  fileCache.set(file, { fetchedAt: Date.now(), programs });
  return programs;
}

// Real EPG schedules pulled from a free, publicly hosted XMLTV guide, for
// the subset of channels it carries. Returns an empty object (never
// throws) if every source is unreachable, so callers can safely fall back
// to the simulated schedule for everything.
export async function getRealEpgPrograms(): Promise<Record<string, EPGProgram[]>> {
  const files = Array.from(new Set(Object.values(EPG_SOURCE_MAP).map((m) => m.file)));
  const filePrograms = await Promise.all(
    files.map(async (file) => {
      try {
        return { file, programs: await getFilePrograms(file) };
      } catch {
        return { file, programs: new Map<string, EPGProgram[]>() };
      }
    })
  );

  const byFile = new Map(filePrograms.map((f) => [f.file, f.programs]));
  const result: Record<string, EPGProgram[]> = {};

  for (const [channelId, mapping] of Object.entries(EPG_SOURCE_MAP)) {
    const programs = byFile.get(mapping.file)?.get(mapping.sourceId) || [];
    if (programs.length === 0) continue;
    result[channelId] = programs.map((p, i) => ({ ...p, id: `${channelId}-live-${i}`, channelId }));
  }

  return result;
}
