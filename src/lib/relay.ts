/**
 * Relay helpers, kept out of the route module.
 *
 * A Next.js `route.ts` may only export the fields the App Router recognises —
 * `GET`, `dynamic`, and the rest of that fixed set. Exporting anything else,
 * including a pure helper, fails the build with a route-type error. So the
 * logic lives here, where it is also directly testable.
 */

/**
 * Hosts the relay will fetch from, when nothing is configured.
 *
 * An unrestricted proxy is an open proxy: anyone who finds the endpoint can
 * point it at any address on the internet and bill the traffic to us, and at
 * anything inside the platform's own network besides. The allowlist is the
 * whole security model.
 */
export const DEFAULT_RELAY_HOSTS = ["api.elg-26.com", "line.enktel.online"];

/** The configured allowlist, or the default when unset. */
export function relayHosts(configured: string | undefined): string[] {
  const list = (configured || "")
    .split(",")
    .map((h) => h.trim().toLowerCase())
    .filter(Boolean);
  return list.length ? list : DEFAULT_RELAY_HOSTS;
}

/**
 * Suffix match on a dot boundary.
 *
 * A plain `endsWith` would let `evil-elg-26.com` match an allowlisted
 * `elg-26.com`, which hands the open proxy back to whoever registers the
 * lookalike.
 */
export function hostAllowed(hostname: string, allow: string[]): boolean {
  const host = hostname.trim().toLowerCase();
  if (!host) return false;
  return allow.some((a) => host === a || host.endsWith(`.${a}`));
}

/**
 * Addresses that must never be fetched, whatever the allowlist says.
 *
 * A hostname pointing inward is how a proxy becomes a way to read the
 * platform's own metadata service. The allowlist is what actually bounds this;
 * this is the second lock.
 */
const BLOCKED =
  /^(localhost$|127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1\]?$|0\.0\.0\.0$)/i;

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
 * straight back to the host we were routing around. Relative URIs resolve
 * against the playlist's own URL first, as a player would.
 *
 * `URI="…"` attributes are rewritten as well — a decryption key fetched
 * direct from an unreachable host fails just as hard as a segment.
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
        return line.replace(/URI="([^"]*)"/g, (_m, uri: string) => `URI="${wrap(uri)}"`);
      }
      return wrap(line);
    })
    .join("\n");
}
