import { NextResponse } from "next/server";
import { getMockUpcomingEvents } from "@/lib/mock-data";
import { getRealUpcomingEvents } from "@/lib/sportsApi";

export const dynamic = "force-dynamic";

export async function GET() {
  let events = await getRealUpcomingEvents();
  let source = "thesportsdb";
  if (events.length === 0) {
    events = getMockUpcomingEvents();
    source = "mock-fallback";
  }
  return NextResponse.json({ events, source, updatedAt: new Date().toISOString() });
}
