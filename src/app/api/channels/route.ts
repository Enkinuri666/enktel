import { NextResponse } from "next/server";
import { channels } from "@/lib/channels";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const category = searchParams.get("category");

  const result = category && category !== "All"
    ? channels.filter((c) => c.category === category)
    : channels;

  return NextResponse.json({ channels: result, total: result.length });
}
