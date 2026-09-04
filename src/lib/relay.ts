import { LINEUP_RELAY_HOSTS } from "./relay-hosts.generated";

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
 *
 * The panel's whole domain rather than one host under it. Lines are issued on
 * per-line subdomains — `185233706813.elg-26.com` alongside `api.elg-26.com` —
 * and `hostAllowed` matches on a dot boundary, so listing only `api.` refused
 * every line that came back on a subdomain of its own. `elg-26.com` covers
 * them and stays bounded to the reseller's own domain.
 *
 * `x-api.cc` is the new Xtream default and `enktel.online` covers the two
 * named backups, `line.` and `vpn.`. The latter was removed once as retired
 * and is back because the reseller now lists it as a fallback — worth saying
 * plainly rather than quietly re-adding, since the earlier instruction was the
 * opposite.
 *
 * One caveat on what this can fix. The relay runs on Vercel, so it reaches a
 * panel from a datacenter address. Where a panel refuses those — which is a
 * plausible reading of all four of these hosts failing at once from one build
 * IP while working from a home connection — routing through the relay makes
 * the block worse, not better. It is the answer to a geo-block, not to a
 * datacenter-range block.
 */
export const DEFAULT_RELAY_HOSTS = ["x-api.cc", "elg-26.com", "enktel.online"];

/**
 * The allowlist: the panel hosts, plus every host the published lineup points
 * at, plus anything configured.
 *
 * The lineup hosts have to be here or the relay cannot answer the case it
 * exists for. Geo-blocks land overwhelmingly on the free lineup — 1,073 hosts
 * this project publishes itself — and with only the two panel hosts listed,
 * every one of those was refused by us before it ever reached the upstream
 * that was blocking it.
 *
 * Generated rather than widened: `LINEUP_RELAY_HOSTS` is derived from the
 * playlist by scripts/gen-relay-hosts.mjs, so the list stays bounded to hosts
 * this project already sends viewers to. Allowing everything would make this
 * an open proxy, which is the one thing the allowlist exists to prevent.
 *
 * ENKTEL_RELAY_HOSTS *adds* to that set rather than replacing it. Replacing
 * was the old behaviour and it is a trap: setting the variable to add one
 * reseller host would silently drop all thousand-odd lineup hosts and turn the
 * geo-block recovery back off, with nothing to indicate why.
 */
export function relayHosts(configured: string | undefined): string[] {
  const list = (configured || "")
    .split(",")
    .map((h) => h.trim().toLowerCase())
    .filter(Boolean);
  // Deduped with a plain loop rather than a Set: this tsconfig targets below
  // ES2015, where spreading a Set needs downlevelIteration. Spreading arrays
  // and iterating one with for..of are both fine there.
  const merged = [...DEFAULT_RELAY_HOSTS, ...LINEUP_RELAY_HOSTS, ...list];
  const seen: Record<string, true> = {};
  const out: string[] = [];
  for (const host of merged) {
    if (!seen[host]) {
      seen[host] = true;
      out.push(host);
    }
  }
  return out;
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
