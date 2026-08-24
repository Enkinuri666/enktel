import { NextRequest, NextResponse } from "next/server";
import { relayStream } from "@/lib/relay-handler";

/**
 * Relay playback, from wherever this project's functions run by default.
 *
 * Direct playback has the device open the stream host itself, which is fastest
 * and is the right default. It fails when the path between *that device* and
 * *that host* is the problem — a network that blocks the host, an origin that
 * refuses the device's address, a player that will not mix schemes. Relay
 * replaces that path with one we control, and the viewer only ever talks to
 * this origin.
 *
 * This is the endpoint the Direct/Relay switch uses. The country-pinned ones
 * next door (`us`, `gb`) are what the app reaches for on its own when a host
 * answers 403, because that refusal is about *where the request came from* and
 * this endpoint has no opinion about where that is.
 *
 * The logic lives in @/lib/relay-handler: a route module may only export the
 * fields the App Router recognises, and three regions sharing one SSRF filter
 * is the entire point of putting it there.
 */

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest): Promise<NextResponse> {
  return relayStream(request, "/api/stream");
}
