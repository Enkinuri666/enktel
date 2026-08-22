import { NextRequest, NextResponse } from "next/server";
import { OMDB_CACHE_CONTROL, imdbIdAllowed, omdbUpstream } from "@/lib/omdb-proxy";

/**
 * IMDb ratings, proxied with our key so devices do not need one.
 *
 * TMDB gives us IMDb's *id* for a title but not IMDb's rating — `vote_average`
 * in a TMDB payload is TMDB's own score, computed from a different audience,
 * and printing it under an IMDb label would be a lie. IMDb publishes no free
 * API; OMDb is the long-standing mirror that does, keyed by exactly the id
 * TMDB hands over.
 *
 * Same shape as /api/tmdb next door, for the same reasons: the key lives in an
 * environment variable rather than in the APK (an APK is a zip, so a key in one
 * is a published key), only the one call the app makes is reachable, and the
 * response is cached hard at the edge so OMDb sees one request per title
 * instead of one per device.
 */

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const key = process.env.OMDB_API_KEY;
  if (!key) {
    return NextResponse.json({ error: "OMDb is not configured on this server" }, { status: 503 });
  }

  const imdbId = request.nextUrl.searchParams.get("i") ?? "";
  if (!imdbIdAllowed(imdbId)) {
    return NextResponse.json({ error: "Not an IMDb id" }, { status: 400 });
  }

  let res: Response;
  try {
    res = await fetch(omdbUpstream(imdbId, key), {
      headers: { accept: "application/json" },
      signal: AbortSignal.timeout(15_000),
    });
  } catch (err) {
    const reason = err instanceof Error ? err.message : "unknown error";
    return NextResponse.json({ error: `OMDb unreachable: ${reason}` }, { status: 502 });
  }

  const body = await res.text();

  // OMDb answers 200 with `{"Response":"False"}` for a title it does not have,
  // so a successful request is not a successful lookup. That body is passed
  // through as-is and the client reads it — but it is not cached as though it
  // were an answer, because a title OMDb gains later should not be shadowed by
  // a week-old miss at the edge.
  const missed = body.includes('"Response":"False"');

  return new NextResponse(body, {
    status: res.status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": res.ok && !missed ? OMDB_CACHE_CONTROL : "no-store",
    },
  });
}
