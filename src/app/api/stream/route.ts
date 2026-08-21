import { NextRequest, NextResponse } from "next/server";
import {
  hostAllowed,
  isBlockedHost,
  looksLikePlaylist,
  relayHosts,
  rewritePlaylist,
} from "@/lib/relay";

/**
 * Relay playback: fetch a stream server-side and hand it back from this origin.
 *
 * Direct playback has the device open the stream host itself, which is fastest
 * and is the right default. It fails when the path between *that device* and
 * *that host* is the problem — a network that blocks the host, an origin that
 * refuses the device's address, a player that will not mix schemes. Relay
 * replaces that path with one we control, and the viewer only ever talks to
 * this origin.
 *
 * **This is a network path, not an access grant.** The upstream URL arrives
 * from the client and is forwarded exactly as given — nothing here adds,
 * substitutes or knows any credentials. A request that would have been refused
 * upstream is refused identically through the relay. It moves *where* the
 * request comes from, and nothing else.
 *
 * The helpers live in @/lib/relay because a route module may only export the
 * fields the App Router recognises.
 */

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const target = request.nextUrl.searchParams.get("u");
  if (!target) {
    return NextResponse.json({ error: "Missing ?u=" }, { status: 400 });
  }

  let url: URL;
  try {
    url = new URL(target);
  } catch {
    return NextResponse.json({ error: "?u= is not a valid URL" }, { status: 400 });
  }

  if (url.protocol !== "http:" && url.protocol !== "https:") {
    return NextResponse.json({ error: "Only http(s) can be relayed" }, { status: 400 });
  }
  // Deliberately one message for both refusals: a probe should learn nothing
  // beyond "no" about what this relay will reach.
  if (isBlockedHost(url.hostname) || !hostAllowed(url.hostname, relayHosts(process.env.ENKTEL_RELAY_HOSTS))) {
    return NextResponse.json({ error: "Host not relayable" }, { status: 403 });
  }

  // Range is what makes VOD seeking work; without it a player can only ever
  // start a file from the beginning.
  const range = request.headers.get("range");
  const upstreamHeaders: Record<string, string> = {
    "user-agent": request.headers.get("x-relay-user-agent") || "EnktelIPTV/1.0",
    accept: "*/*",
  };
  if (range) upstreamHeaders.range = range;

  let upstream: Response;
  try {
    upstream = await fetch(url.href, {
      headers: upstreamHeaders,
      redirect: "follow",
      signal: AbortSignal.timeout(30_000),
    });
  } catch (err) {
    const reason = err instanceof Error ? err.message : "unknown error";
    return NextResponse.json({ error: `Upstream unreachable: ${reason}` }, { status: 502 });
  }

  if (!upstream.ok && upstream.status !== 206) {
    // An auth refusal is passed through as itself — the client needs to see
    // that its credentials were rejected, not a generic gateway error.
    const status = upstream.status === 401 || upstream.status === 403 ? upstream.status : 502;
    return NextResponse.json({ error: `Upstream returned HTTP ${upstream.status}` }, { status });
  }

  const contentType = upstream.headers.get("content-type");

  // A manifest is small and has to be rewritten, so it is read in full. Media
  // is streamed — a segment or a film must never be buffered in memory here.
  if (looksLikePlaylist(url.href, contentType)) {
    const body = await upstream.text();
    const relayBase = new URL("/api/stream", request.nextUrl.origin).href;
    return new NextResponse(rewritePlaylist(body, url.href, relayBase), {
      status: 200,
      headers: {
        "content-type": contentType ?? "application/vnd.apple.mpegurl",
        // A live manifest is rewritten by the origin every few seconds; caching
        // one hands viewers a window that has already moved on.
        "cache-control": "no-store",
      },
    });
  }

  const headers: Record<string, string> = {
    "content-type": contentType ?? "application/octet-stream",
    "cache-control": "no-store",
  };
  for (const h of ["content-length", "content-range", "accept-ranges"]) {
    const v = upstream.headers.get(h);
    if (v) headers[h] = v;
  }

  return new NextResponse(upstream.body, { status: upstream.status, headers });
}
