import { NextRequest, NextResponse } from "next/server";
import { relayStream } from "@/lib/relay-handler";

/**
 * The relay, asking from the United Kingdom.
 *
 * 307 channels in the lineup are British, and the ones that refuse a foreign
 * address — broadcaster streams especially — will not accept the American
 * endpoint either. A block is satisfied by one country, not by "somewhere
 * else".
 *
 * `lhr1` is London. See the US route on what a plan without per-function
 * regions does with this.
 */

export const dynamic = "force-dynamic";
export const preferredRegion = "lhr1";

export async function GET(request: NextRequest): Promise<NextResponse> {
  return relayStream(request, "/api/stream/gb");
}
