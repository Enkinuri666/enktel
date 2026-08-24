/**
 * Helpers for the OMDb proxy, kept out of the route module.
 *
 * A Next.js `route.ts` may only export the fields the App Router recognises,
 * so exporting a helper from one fails the build. Here they are also directly
 * testable.
 */

const OMDB_BASE = "https://www.omdbapi.com/";

/**
 * IMDb's own id shape: `tt` and at least seven digits.
 *
 * This is the whole of the allowlist. OMDb can also be queried by title (`t=`)
 * and by free-text search (`s=`), and neither is reachable through here: the
 * app only ever looks up an id it already got from TMDB, and a proxy that
 * accepts arbitrary searches is a proxy that spends our quota on whatever
 * anyone feels like asking.
 */
const IMDB_ID = /^tt\d{7,}$/;

export function imdbIdAllowed(id: string): boolean {
  return IMDB_ID.test(id.trim());
}

/** Build the upstream URL, with our key and nothing the caller supplied. */
export function omdbUpstream(imdbId: string, apiKey: string): string {
  const params = new URLSearchParams();
  params.set("i", imdbId.trim());
  // Short plot: the app already has TMDB's synopsis, which is better written
  // and localised. Asking for the long one just makes the response bigger.
  params.set("plot", "short");
  params.set("r", "json");
  params.set("apikey", apiKey);
  return `${OMDB_BASE}?${params.toString()}`;
}

/**
 * Ratings do not change in a way anyone notices within a day, and the same few
 * thousand titles are looked up by every install. Cache hard at the edge so
 * OMDb sees one request per title rather than one per device.
 */
export const OMDB_CACHE_CONTROL = "public, s-maxage=86400, stale-while-revalidate=604800";
