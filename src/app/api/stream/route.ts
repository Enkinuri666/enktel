import { NextRequest, NextResponse } from "next/server";

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
 */

/**
 * Hosts this relay will fetch from.
 *
 * An unrestricted proxy is an open proxy: anyone who finds the endpoint can
 * point it at any address on the internet and bill the traffic to us, and at
 * anything inside the platform's own network besides. The allowlist is the
 * whole security model, so it is a suffix match against a closed set rather
 * than a pattern anyone can widen from the query string.
 */
const DEFAULT_HOSTS = ["api.elg-26.com", "line.enktel.online"];

function allowedHosts(): string[] {
  const configured = (process.env.ENKTEL_RELAY_HOSTS || "")
    .split(",")
    .map((h) => h.trim().toLowerCase())
    .filter(Boolean);
  return configured.length ? configured : DEFAULT_HOSTS;
}

/** Suffix match on a dot boundary, so "elg-26.com" cannot be matched by "evil-elg-26.com". */
export function hostAllowed(hostname: string, allow: string[]): boolean {
  const host = hostname.toLowerCase();
  return allow.some((a) => host === a || host.endsWith(`.${a}`));
}

/**
 * Addresses that must never be fetched, whatever the allowlist says.
 *
 * A hostname that resolves inward is how a proxy becomes a way to read the
 * platform's own metadata service and internal services. Checked on the literal
 * host because that is what we can check without resolving; the allowlist is
 * what actually bounds this, and this is the second lock.
 */
const BLOCKED = /^(localhost$|127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1\]?$|0\.0\.0\.0$)/i;

export function isBlockedHost(hostname: string): boolean {
  return BLOCKED.test(hostname.trim());
}

/** Is this body an HLS playlist rather than media? */
export function looksLikePlaylist(url: string, contentType: string | null): boolean {
  if (/\bmpegurl\b/i.test(contentType ?? "")) return true;
  return /\.m3u8(\?|$)/i.test(url);
}

/**
 * Rewrite an HLS playlist so its segments come back through the relay too.
 *
 * Without this, relay is pointless for HLS: the manifest arrives from us and
 * every segment in it still points at the upstream, so the device goes
 * straight back to the host we were routing around. Relative URIs are resolved
 * against the playlist's own URL first, since that is what the player would
 * have done.
 *
 * URI attributes inside tags (`#EXT-X-KEY:URI="…"`, media and stream-inf
 * renditions) are rewritten as well — a key fetched direct from a blocked host
 * fails just as hard as a segment.
 */
export function rewritePlaylist(body: string, playlistUrl: string, relayBase: string): string {
  const wrap = (raw: string): string => {
    const trimmed = raw.trim();
    if (!trimmed || trimmed.startsWith("#")) return raw;
    let absolute: string;
    try {
      absolute = new URL(trimmed, playlistUrl).href;
    } catch {
      return raw;
    }
    return `${relayBase}?u=${encodeURIComponent(absolute)}`;
  };

  return body
    .split(/\r?\n/)
    .map((line) => {
      if (!line.trim()) return line;
      if (line.startsWith("#")) {
        // Rewrite any URI="…" attribute in place, leaving the rest of the tag.
        return line.replace(/URI="([^"]*)"/g, (_m, uri: string) => `URI="${wrap(uri)}"`);
      }
      return wrap(line);
    })
    .join("\n");
}

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
  if (isBlockedHost(url.hostname)) {
    return NextResponse.json({ error: "Host not relayable" }, { status: 403 });
  }
  if (!hostAllowed(url.hostname, allowedHosts())) {
    // Deliberately not naming the allowlist: a probe should learn nothing
    // beyond "no" about what this relay will reach.
    return NextResponse.json({ error: "Host not relayable" }, { status: 403 });
  }

  // Range is what makes VOD seeking work; without it the player can only ever
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
    return NextResponse.json(
      { error: `Upstream returned HTTP ${upstream.status}` },
      { status: upstream.status === 403 || upstream.status === 401 ? upstream.status : 502 },
    );
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
        // A live manifest is rewritten every few seconds by the origin; caching
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
