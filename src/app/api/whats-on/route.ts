import { NextResponse } from "next/server";
import { buildWhatsOn } from "@/lib/mock-data";
import { fetchEPGData } from "@/lib/epg";

export const dynamic = "force-dynamic";

export async function GET() {
  const programs = await fetchEPGData();
  const items = buildWhatsOn(programs, new Date());

  return NextResponse.json({ items, updatedAt: new Date().toISOString() });
}
