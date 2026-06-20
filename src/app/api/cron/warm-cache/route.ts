import { NextResponse } from "next/server";
import { getRealEpgPrograms } from "@/lib/epgSource";
import { getRealUpcomingEvents } from "@/lib/sportsApi";

export const dynamic = "force-dynamic";

// Triggered on a schedule (see vercel.json) to pre-fetch and cache the
// real EPG/sports data ahead of time, so the first real visitor of the
// window hits a warm in-memory cache instead of waiting on a multi-MB
// XMLTV download or a round trip to TheSportsDB.
export async function GET(request: Request) {
  const authHeader = request.headers.get("authorization");
  if (process.env.CRON_SECRET && authHeader !== `Bearer ${process.env.CRON_SECRET}`) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const [epgResult, eventsResult] = await Promise.allSettled([getRealEpgPrograms(), getRealUpcomingEvents()]);

  return NextResponse.json({
    epg: epgResult.status === "fulfilled" ? Object.keys(epgResult.value).length : "error",
    events: eventsResult.status === "fulfilled" ? eventsResult.value.length : "error",
    warmedAt: new Date().toISOString(),
  });
}
