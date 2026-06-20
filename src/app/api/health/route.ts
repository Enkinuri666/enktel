import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

interface SourceCheck {
  name: string;
  url: string;
}

// The external data sources the site depends on for "self-updating"
// content. Checked with a short timeout so one slow/unreachable source
// can't hold up the whole status response.
const SOURCES: SourceCheck[] = [
  { name: "TMDB (Movies & TV)", url: "https://api.themoviedb.org/3/configuration" },
  { name: "TheSportsDB (Sports fixtures)", url: "https://www.thesportsdb.com/api/v1/json/123/eventsnextleague.php?id=4328" },
  { name: "epgshare01 (EPG / XMLTV)", url: "https://epgshare01.online/epgshare01/epg_ripper_HR1.xml.gz" },
];

async function checkSource(source: SourceCheck) {
  const startedAt = Date.now();
  try {
    const res = await fetch(source.url, { method: "GET", signal: AbortSignal.timeout(8000) });
    // A reachable upstream that simply rejects an unauthenticated/test request
    // (401/403, or 404 for a moved test endpoint) still counts as "operational" -
    // we're checking whether the service itself is up, not validating credentials.
    const status = res.status >= 500 ? "degraded" : "operational";
    return { name: source.name, status, latencyMs: Date.now() - startedAt };
  } catch {
    return { name: source.name, status: "down", latencyMs: Date.now() - startedAt };
  }
}

export async function GET() {
  const sources = await Promise.all(SOURCES.map(checkSource));
  return NextResponse.json({ sources, checkedAt: new Date().toISOString() });
}
