import { NextResponse } from "next/server";
import { buildWhatsOn } from "@/lib/mock-data";
import { fetchEPGData } from "@/lib/epg";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const category = searchParams.get("category");

  const programs = await fetchEPGData();
  let items = buildWhatsOn(programs, new Date());

  if (category && category !== "All") {
    items = items.filter((item) => item.channel.category === category);
  }

  return NextResponse.json({ items, updatedAt: new Date().toISOString() });
}
