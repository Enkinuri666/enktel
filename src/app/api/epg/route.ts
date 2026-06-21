import { NextResponse } from "next/server";
import { fetchEPGData } from "@/lib/epg";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const channelId = searchParams.get("channelId");

  const programs = await fetchEPGData();

  const filtered = channelId ? programs.filter((p) => p.channelId === channelId) : programs;
  return NextResponse.json({ programs: filtered, count: filtered.length });
}
