import axios from "axios";
import xml2js from "xml2js";
import { EPGProgram } from "@/types";
import { mockEPGPrograms } from "./mock-data";

const EPG_SOURCES = [
  "https://iptv-org.github.io/epg/guides/uk/sky.com.epg.xml",
];

let epgCache: { data: EPGProgram[]; timestamp: number } | null = null;
const CACHE_TTL = 30 * 60 * 1000; // 30 minutes

function parseXMLTVDate(dateStr: string): string {
  const clean = dateStr.trim().split(" ")[0];
  const year = clean.substring(0, 4);
  const month = clean.substring(4, 6);
  const day = clean.substring(6, 8);
  const hour = clean.substring(8, 10);
  const min = clean.substring(10, 12);
  const sec = clean.substring(12, 14);
  return `${year}-${month}-${day}T${hour}:${min}:${sec}Z`;
}

export async function fetchEPGData(): Promise<EPGProgram[]> {
  if (epgCache && Date.now() - epgCache.timestamp < CACHE_TTL) {
    return epgCache.data;
  }

  for (const source of EPG_SOURCES) {
    try {
      const res = await axios.get(source, { timeout: 10000 });
      const parser = new xml2js.Parser({ explicitArray: false });
      const parsed = await parser.parseStringPromise(res.data);
      const programmes = parsed?.tv?.programme;

      if (!programmes) continue;

      const list = Array.isArray(programmes) ? programmes : [programmes];
      const programs: EPGProgram[] = list.slice(0, 500).map((p: Record<string, unknown>, i: number) => {
        const attrs = p.$ as Record<string, string>;
        const title = typeof p.title === "object" ? (p.title as Record<string, string>)._ || String(p.title) : String(p.title || "");
        const desc = typeof p.desc === "object" ? (p.desc as Record<string, string>)._ || "" : String(p.desc || "");
        const cat = typeof p.category === "object" ? (p.category as Record<string, string>)._ || "" : String(p.category || "");
        return {
          id: `epg-${i}`,
          channelId: attrs?.channel || "",
          title,
          description: desc,
          startTime: parseXMLTVDate(attrs?.start || ""),
          endTime: parseXMLTVDate(attrs?.stop || ""),
          category: cat,
        };
      });

      epgCache = { data: programs, timestamp: Date.now() };
      return programs;
    } catch {
      // Fall through to next source or mock data
    }
  }

  return mockEPGPrograms;
}

export function getProgramsForChannel(
  programs: EPGProgram[],
  channelId: string,
  startTime: Date,
  endTime: Date
): EPGProgram[] {
  return programs.filter((p) => {
    if (p.channelId !== channelId) return false;
    const pStart = new Date(p.startTime);
    const pEnd = new Date(p.endTime);
    return pStart < endTime && pEnd > startTime;
  });
}
