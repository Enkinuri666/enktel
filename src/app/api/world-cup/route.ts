import { NextResponse } from "next/server";
import { getRealWorldCupMatches } from "@/lib/worldCup";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const matches = await getRealWorldCupMatches();
    return NextResponse.json({ matches, updatedAt: new Date().toISOString() });
  } catch {
    return NextResponse.json({ matches: [], updatedAt: new Date().toISOString() });
  }
}
