import { NextResponse } from "next/server";
import { fetchEPGData } from "@/lib/epg";
import { getMockWhatsOn } from "@/lib/mock-data";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const channelId = searchParams.get("channelId");
  const mode = searchParams.get("mode");

  if (mode === "whats-on") {
    const items = getMockWhatsOn();
    return NextResponse.json({ items });
  }

  const programs = fetchEPGData();
  const filtered = channelId ? programs.filter((p) => p.channelId === channelId) : programs;
  return NextResponse.json({ programs: filtered, count: filtered.length });
}
