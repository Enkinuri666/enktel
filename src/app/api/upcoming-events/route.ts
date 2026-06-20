import { NextResponse } from "next/server";
import { getMockUpcomingEvents } from "@/lib/mock-data";
import { getRealUpcomingEvents } from "@/lib/sportsApi";
import { withFallback } from "@/lib/dataSource";

export const dynamic = "force-dynamic";

export async function GET() {
  const { data: events, source } = await withFallback(
    async () => {
      const real = await getRealUpcomingEvents();
      if (real.length === 0) throw new Error("no live events");
      return real;
    },
    () => getMockUpcomingEvents(),
    { sourceName: "thesportsdb" }
  );
  return NextResponse.json({ events, source, updatedAt: new Date().toISOString() });
}
