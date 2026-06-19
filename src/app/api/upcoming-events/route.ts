import { NextResponse } from "next/server";
import { getMockUpcomingEvents } from "@/lib/mock-data";

export const dynamic = "force-dynamic";

export async function GET() {
  const events = getMockUpcomingEvents();
  return NextResponse.json({ events, updatedAt: new Date().toISOString() });
}
