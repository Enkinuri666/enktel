import { NextResponse } from "next/server";

/**
 * XMLTV guide for the free-to-air lineup, served from our own domain.
 *
 * The app used to fetch the guide straight from the upstream publisher, and a
 * device reported `Failed to connect to raw.githubusercontent.com/185.199.110.133`
 * — a GitHub Pages address, reached by following the upstream's redirect. The
 * playlist was coming off `raw.githubusercontent.com` too, so both pieces of
 * runtime data depended on GitHub being reachable from the viewer's network.
 * On a mobile network that is not a safe assumption, and it is not something
 * the app can retry its way out of.
 *
 * So the fetch happens here instead. The server follows whatever redirects the
 * upstream uses, over connectivity we control, and the viewer only ever talks
 * to this origin. The response is cached at the edge so a guide refresh across
 * many devices costs one upstream fetch, not one per device.
 */

/** Where the guide really comes from. Overridable without a redeploy. */
const UPSTREAM = process.env.ENKTEL_GUIDE_UPSTREAM || "https://i.mjh.nz/au/Brisbane/epg.xml.gz";

/**
 * A guide changes a few times a day at most, and a stale one is far better
 * than none: `stale-while-revalidate` keeps serving the last good copy while
 * the next is fetched, so an upstream outage is invisible to viewers.
 */
const CACHE_CONTROL = "public, max-age=1800, s-maxage=3600, stale-while-revalidate=86400";

export const dynamic = "force-dynamic";

export async function GET() {
  let upstream: Response;
  try {
    upstream = await fetch(UPSTREAM, {
      headers: { "user-agent": "EnktelIPTV/1.0", accept: "*/*" },
      redirect: "follow",
      signal: AbortSignal.timeout(25_000),
    });
  } catch (err) {
    const reason = err instanceof Error ? err.message : "unknown error";
    return NextResponse.json({ error: `Guide upstream unreachable: ${reason}` }, { status: 502 });
  }

  if (!upstream.ok || !upstream.body) {
    return NextResponse.json(
      { error: `Guide upstream returned HTTP ${upstream.status}` },
      { status: 502 },
    );
  }

  // Streamed rather than buffered: an XMLTV guide runs to tens of megabytes,
  // and holding one in memory to hand it straight back is the difference
  // between a request that works and one that hits the function memory cap.
  // Deliberately no `content-encoding` passthrough. `fetch` decompresses a
  // gzipped body on the way in while often still reporting the upstream's
  // `content-encoding: gzip`, so copying that header onto an already-decoded
  // stream tells the client to gunzip plain XML — which fails, and fails
  // silently in a parser that just finds no `<channel>` elements. Whatever
  // arrives here is forwarded as-is and the client decides from the bytes:
  // `gunzipIfNeeded` on the Android side sniffs the gzip magic number rather
  // than trusting any header, so both forms land correctly.
  return new NextResponse(upstream.body, {
    status: 200,
    headers: {
      "content-type": upstream.headers.get("content-type") ?? "application/xml",
      "cache-control": CACHE_CONTROL,
    },
  });
}
