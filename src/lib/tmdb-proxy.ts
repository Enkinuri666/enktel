/**
 * Helpers for the TMDB proxy, kept out of the route module.
 *
 * A Next.js `route.ts` may only export the fields the App Router recognises,
 * so exporting a helper from one fails the build. Here they are also directly
 * testable.
 */

const TMDB_BASE = "https://api.themoviedb.org/3";

/**
 * The only TMDB paths this proxy will forward.
 *
 * Exactly what `TmdbClient` calls, and nothing else. Left open, the endpoint
 * would let anyone spend our rate limit on the whole of TMDB, and a key that
 * gets throttled stops enrichment for every install at once.
 */
const ALLOWED = /^(search\/(movie|tv)|(movie|tv)\/\d+(\/videos)?)$/;

export function tmdbPathAllowed(path: string): boolean {
  return ALLOWED.test(path.trim());
}

/**
 * Query parameters worth forwarding.
 *
 * A closed set rather than a passthrough: `api_key` is ours to set, and
 * letting a caller supply their own alongside it is how a proxy ends up
 * making requests nobody here intended.
 */
const FORWARD = new Set(["language", "query", "include_adult", "append_to_response", "page", "year"]);

/** Build the upstream URL, with our key and only the parameters we allow. */
export function tmdbUpstream(path: string, params: URLSearchParams, apiKey: string): string {
  const out = new URLSearchParams();
  for (const [k, v] of params) {
    if (FORWARD.has(k)) out.set(k, v);
  }
  out.set("api_key", apiKey);
  return `${TMDB_BASE}/${path}?${out.toString()}`;
}

/**
 * Film metadata does not change, and every install looks up the same few
 * thousand titles, so this is cached hard and revalidated lazily.
 */
export const TMDB_CACHE_CONTROL =
  "public, max-age=86400, s-maxage=604800, stale-while-revalidate=2592000";
