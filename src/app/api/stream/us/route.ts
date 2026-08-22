import { NextRequest, NextResponse } from "next/server";
import { relayStream } from "@/lib/relay-handler";

/**
 * The relay, asking from the United States.
 *
 * 2,446 of the 2,923 channels in the published lineup are American — 83.7% —
 * and the usual reason one of them will not play is that its host serves the
 * United States and refuses everywhere else. Nothing about the request changes
 * here except the country it leaves from.
 *
 * `iad1` is Washington DC. Any US region would do; this one is Vercel's
 * default and therefore the least surprising.
 *
 * Per-function regions are a paid-plan feature. What a plan without them does
 * with this pin — ignore it, or refuse the deployment — is decided by the
 * preview build on the pull request that added it, not by this comment. If it
 * refuses, the fix is to drop to one region and accept that only that
 * country's blocks are answered.
 */

export const dynamic = "force-dynamic";
export const preferredRegion = "iad1";

export async function GET(request: NextRequest): Promise<NextResponse> {
  return relayStream(request, "/api/stream/us");
}
