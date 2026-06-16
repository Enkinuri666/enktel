import { NextResponse } from "next/server";
import { getMockWhatsOn } from "@/lib/mock-data";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const category = searchParams.get("category");

  let items = getMockWhatsOn();

  if (category && category !== "All") {
    items = items.filter((item) => item.channel.category === category);
  }

  return NextResponse.json({ items, updatedAt: new Date().toISOString() });
}
