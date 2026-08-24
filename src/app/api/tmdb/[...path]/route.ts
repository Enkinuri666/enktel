import { NextRequest, NextResponse } from "next/server";
import { TMDB_CACHE_CONTROL, tmdbPathAllowed, tmdbUpstream } from "@/lib/tmdb-proxy";

/**
 * TMDB, proxied with our key so devices do not need one.
 *
 * Metadata enrichment was opt-in and effectively off: it required every viewer
 * to go and register their own TMDB key and paste it into Settings, so almost
 * no install ever had plots, backdrops or ratings. Compiling a key into the
 * APK is not the alternative — an APK is a zip, and a key in one is a
 * published key that gets rate-limited or revoked for everybody at once.
 *
 * So the key stays here, in an environment variable, and devices ask us. One
 * key, one place to rotate it, and the app works out of the box. A viewer who
 * wants their own key can still set one in Settings, and the app then talks to
 * TMDB directly and skips this entirely.
 *
 * Responses are cached hard at the edge: film metadata does not change, and
 * the same few thousand titles are looked up by every install.
 */

export const dynamic = "force-dynamic";

// `params` is a plain object here, not a promise: this app is on Next 14,
// where the async-params signature does not exist yet.
export async function GET(
  request: NextRequest,
  { params }: { params: { path: string[] } },
) {
  const key = process.env.TMDB_API_KEY;
  if (!key) {
    return NextResponse.json({ error: "TMDB is not configured on this server" }, { status: 503 });
  }

  const joined = (params.path ?? []).join("/");

  // An unrestricted proxy would let anyone spend our rate limit on any TMDB
  // endpoint, so only the handful the app actually calls are reachable.
  if (!tmdbPathAllowed(joined)) {
    return NextResponse.json({ error: "Not a proxied TMDB path" }, { status: 403 });
  }

  const upstream = tmdbUpstream(joined, request.nextUrl.searchParams, key);

  let res: Response;
  try {
    res = await fetch(upstream, {
      headers: { accept: "application/json" },
      signal: AbortSignal.timeout(15_000),
    });
  } catch (err) {
    const reason = err instanceof Error ? err.message : "unknown error";
    return NextResponse.json({ error: `TMDB unreachable: ${reason}` }, { status: 502 });
  }

  const body = await res.text();

  // TMDB's own status is passed through — a 404 for an unmatched title is a
  // real answer the client handles, not a gateway failure to paper over.
  return new NextResponse(body, {
    status: res.status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": res.ok ? TMDB_CACHE_CONTROL : "no-store",
    },
  });
}
