/**
 * Xtream Codes `player_api.php` client and URL builders.
 *
 * Deliberately mirrors `pc/src-tauri/src/xtream.rs` — same actions, same
 * user agent, same stream URL shapes — so a catalog exported here resolves to
 * exactly the URLs the desktop and Android players would build themselves.
 *
 * Pure functions live at the bottom and are covered by `xtream.test.mjs`;
 * the network calls take an injectable `fetchImpl` for the same reason.
 */

/** The panel's WAF treats unknown agents worse than it treats VLC. */
export const PANEL_UA = 'VLC/3.0.20 LibVLC/3.0.20';

/** Strip a trailing slash so `${base}/...` never doubles up. */
export function normalizeServer(server) {
  const trimmed = String(server ?? '').trim().replace(/\/+$/, '');
  if (!trimmed) throw new Error('server is required');
  return /^https?:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;
}

/**
 * Build a `player_api.php` URL.
 *
 * @param {{server: string, username: string, password: string}} creds
 * @param {string} [action]
 * @param {Record<string,string|number>} [params]
 */
export function apiUrl(creds, action, params = {}) {
  const url = new URL(`${normalizeServer(creds.server)}/player_api.php`);
  url.searchParams.set('username', creds.username);
  url.searchParams.set('password', creds.password);
  if (action) url.searchParams.set('action', action);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, String(v));
  return url.href;
}

/**
 * Call the panel and parse the JSON body.
 *
 * Panels answer errors with a 200 and an HTML body more often than with a
 * status code, so a parse failure is reported with a slice of what came back.
 *
 * @returns {Promise<any>}
 */
export async function call(creds, action, params = {}, opts = {}) {
  const {
    timeout = 60_000,
    retries = 2,
    fetchImpl = fetch,
    onRetry = () => {},
  } = opts;

  const url = apiUrl(creds, action, params);
  let lastError;

  for (let attempt = 0; attempt <= retries; attempt++) {
    if (attempt) {
      onRetry(attempt, lastError);
      await new Promise((r) => setTimeout(r, 1000 * 2 ** (attempt - 1)));
    }
    try {
      const res = await fetchImpl(url, {
        headers: { 'user-agent': PANEL_UA, accept: 'application/json,*/*' },
        signal: AbortSignal.timeout(timeout),
      });
      if (!res.ok) throw new Error(`panel HTTP ${res.status}`);

      const text = await res.text();
      try {
        return JSON.parse(text);
      } catch {
        throw new Error(`panel returned non-JSON: ${text.slice(0, 200)}`);
      }
    } catch (err) {
      lastError = err;
    }
  }

  throw new Error(`${action ?? 'auth'} failed: ${lastError?.message ?? 'unknown error'}`);
}

/**
 * Authenticate and report what the line is allowed to do.
 *
 * @returns {Promise<{ok: boolean, error?: string, info?: object, server?: object}>}
 */
export async function login(creds, opts = {}) {
  const body = await call(creds, undefined, {}, opts);
  const info = body?.user_info;

  if (!info || String(info.auth) === '0') {
    return { ok: false, error: 'panel rejected these credentials' };
  }
  if (info.status && !/^active$/i.test(String(info.status))) {
    return { ok: false, error: `line is ${info.status}` };
  }

  return { ok: true, info, server: body?.server_info ?? {} };
}

// ---- Stream URL builders --------------------------------------------------
// The first shape is what the player tries first; the rest are the fallback
// chain for panels with quirky layouts.

export function liveUrls(creds, streamId, preferHls = true) {
  const base = normalizeServer(creds.server);
  const { username: u, password: p } = creds;
  const ordered = preferHls
    ? [`${base}/live/${u}/${p}/${streamId}.m3u8`, `${base}/live/${u}/${p}/${streamId}.ts`]
    : [`${base}/live/${u}/${p}/${streamId}.ts`, `${base}/live/${u}/${p}/${streamId}.m3u8`];
  return [...ordered, `${base}/live/${u}/${p}/${streamId}`, `${base}/${u}/${p}/${streamId}.m3u8`];
}

export function movieUrls(creds, streamId, ext = 'mp4') {
  return vodUrls(creds, 'movie', streamId, ext);
}

export function episodeUrls(creds, episodeId, ext = 'mp4') {
  return vodUrls(creds, 'series', episodeId, ext);
}

function vodUrls(creds, kind, id, ext) {
  const base = normalizeServer(creds.server);
  const prefix = `${base}/${kind}/${creds.username}/${creds.password}/${id}`;
  // Panels routinely lie about container_extension, so widen the same way the
  // Android StreamUrlResolver does.
  const seen = new Set();
  const out = [];
  for (const candidate of [ext || 'mp4', 'mp4', 'mkv', 'ts', 'avi']) {
    if (!seen.has(candidate)) {
      seen.add(candidate);
      out.push(`${prefix}.${candidate}`);
    }
  }
  return out;
}

// ---- Response normalisers -------------------------------------------------
// Field names and types vary between panel builds; these flatten the variants
// into the shapes `pc/src/lib/xtream.ts` already declares.

const str = (v) => (v === null || v === undefined ? '' : String(v));
const num = (v) => {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
};

export function normalizeCategory(raw) {
  return { id: str(raw?.category_id), name: str(raw?.category_name).trim() };
}

export function normalizeChannel(raw) {
  return {
    streamId: num(raw?.stream_id),
    name: str(raw?.name).trim(),
    num: num(raw?.num),
    logo: str(raw?.stream_icon),
    categoryId: str(raw?.category_id),
    epgChannelId: str(raw?.epg_channel_id),
    tvArchive: num(raw?.tv_archive) > 0,
    archiveDays: num(raw?.tv_archive_duration),
  };
}

export function normalizeMovie(raw) {
  return {
    streamId: num(raw?.stream_id),
    name: str(raw?.name).trim(),
    poster: str(raw?.stream_icon ?? raw?.cover),
    categoryId: str(raw?.category_id),
    rating: num(raw?.rating),
    ext: str(raw?.container_extension) || 'mp4',
    added: num(raw?.added),
    year: parseYear(raw?.year ?? raw?.releaseDate ?? raw?.release_date ?? raw?.name),
    tmdbId: raw?.tmdb ? num(raw.tmdb) : null,
  };
}

export function normalizeSeries(raw) {
  return {
    seriesId: num(raw?.series_id),
    name: str(raw?.name).trim(),
    cover: str(raw?.cover),
    categoryId: str(raw?.category_id),
    rating: num(raw?.rating),
    plot: str(raw?.plot),
    year: parseYear(raw?.year ?? raw?.releaseDate ?? raw?.release_date ?? raw?.name),
    tmdbId: raw?.tmdb ? num(raw.tmdb) : null,
    lastModified: num(raw?.last_modified),
  };
}

/** Pull a four-digit year out of "2019", "2019-04-01" or "Title (2019)". */
export function parseYear(value) {
  const m = /(19|20)\d{2}/.exec(str(value));
  return m ? Number(m[0]) : null;
}

/**
 * Flatten `get_series_info` into one record per episode.
 *
 * @param {object} info the panel's series_info payload
 * @param {object} series the series record the episodes belong to
 */
export function flattenEpisodes(info, series) {
  const seasons = info?.episodes;
  if (!seasons || typeof seasons !== 'object') return [];

  const out = [];
  for (const [seasonKey, list] of Object.entries(seasons)) {
    if (!Array.isArray(list)) continue;
    for (const ep of list) {
      out.push({
        episodeId: num(ep?.id),
        seriesId: series.seriesId,
        seriesName: series.name,
        season: num(ep?.season ?? seasonKey),
        episode: num(ep?.episode_num),
        title: str(ep?.title).trim() || `Episode ${num(ep?.episode_num)}`,
        ext: str(ep?.container_extension) || 'mp4',
        categoryId: series.categoryId,
        added: num(ep?.added),
      });
    }
  }

  out.sort((a, b) => a.season - b.season || a.episode - b.episode);
  return out;
}

// ---- Country matching -----------------------------------------------------
// Panel categories are named, not coded — "UK | SPORTS", "EX-YU HR", "USA
// ENTERTAINMENT" — so category names are matched with the same patterns the
// public scraper uses on group titles.
export { COUNTRY_PATTERNS, matchesCountry } from './countries.mjs';
