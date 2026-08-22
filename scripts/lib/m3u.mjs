/**
 * M3U/M3U8 parsing, link extraction and normalisation.
 *
 * Kept free of I/O so the whole thing is testable with `node --test`.
 * The attribute names mirror the ones the Android client already reads in
 * `tv.enktel.app.data.diag.M3uAttrs`, so a playlist scraped here parses the
 * same way once it reaches the player.
 */

import { normalizeCountry } from './countries.mjs';

// Re-exported so callers parsing playlists don't need a second import for the
// country handling that parsing already applies.
export { normalizeCountry };

/** Matches a bare stream URL anywhere in a blob of HTML, JSON or plain text. */
const STREAM_URL_RE =
  /https?:\/\/[^\s"'`<>\\)\]}]+?\.(?:m3u8|m3u|mpd)(?:\?[^\s"'`<>\\)\]}]*)?/gi;

/** `key="value"` pairs on an #EXTINF line. Values may contain commas. */
const ATTR_RE = /([A-Za-z0-9_-]+)="([^"]*)"/g;

/**
 * Pull every playlist/stream URL out of arbitrary text.
 *
 * Works on HTML (href/src), JSON payloads and raw playlist bodies alike —
 * we only care about the URL shape, not the container.
 *
 * @param {string} text
 * @param {string} [baseUrl] resolve protocol-relative and root-relative hits
 * @returns {string[]} unique URLs in first-seen order
 */
export function extractStreamUrls(text, baseUrl) {
  if (!text) return [];
  const found = new Set();

  for (const m of text.matchAll(STREAM_URL_RE)) {
    found.add(decodeEntities(m[0]));
  }

  // Protocol-relative and site-relative links only resolve if we know where
  // the document came from.
  if (baseUrl) {
    const relative =
      /(?:href|src|data-url|url)\s*[=:]\s*["']((?:\/\/|\/)[^"']+?\.(?:m3u8|m3u|mpd)(?:\?[^"']*)?)["']/gi;
    for (const m of text.matchAll(relative)) {
      try {
        found.add(new URL(decodeEntities(m[1]), baseUrl).href);
      } catch {
        // Unresolvable against this base — drop it rather than guess.
      }
    }
  }

  return [...found].map(trimTrailingPunctuation).filter(isHttpUrl);
}

function decodeEntities(s) {
  return s
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'")
    .replace(/\\\//g, '/');
}

/** Regex greed can swallow a closing bracket or sentence punctuation. */
function trimTrailingPunctuation(url) {
  return url.replace(/[.,;:!]+$/, '');
}

function isHttpUrl(value) {
  try {
    const u = new URL(value);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

/**
 * Parse the attribute soup on a single #EXTINF line.
 *
 * @param {string} line
 * @returns {{duration: number, attrs: Record<string,string>, title: string}|null}
 */
export function parseExtInf(line) {
  const m = /^#EXTINF:\s*(-?\d+(?:\.\d+)?)(.*)$/i.exec(line.trim());
  if (!m) return null;

  const duration = Number(m[1]);
  const rest = m[2] ?? '';

  const attrs = {};
  for (const a of rest.matchAll(ATTR_RE)) {
    attrs[a[1].toLowerCase()] = a[2];
  }

  // The display title is everything after the last comma that is not inside a
  // quoted attribute value.
  const title = rest.slice(lastUnquotedComma(rest) + 1).trim();

  return { duration, attrs, title };
}

function lastUnquotedComma(s) {
  let inQuotes = false;
  let idx = -1;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === '"') inQuotes = !inQuotes;
    else if (c === ',' && !inQuotes) idx = i;
  }
  return idx;
}

/**
 * Parse a full M3U/M3U8 playlist into channel entries.
 *
 * Tolerates the directives real-world playlists carry between the #EXTINF and
 * its URL: #EXTGRP, #EXTVLCOPT, #KODIPROP, #EXTHTTP.
 *
 * @param {string} body
 * @param {{sourceUrl?: string, sourceId?: string}} [meta]
 * @returns {{header: Record<string,string>, entries: Array<object>}}
 */
export function parsePlaylist(body, meta = {}) {
  const lines = String(body).split(/\r?\n/);
  const header = {};
  const entries = [];

  let pending = null;

  const reset = () => {
    pending = null;
  };

  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;

    if (/^#EXTM3U/i.test(line)) {
      for (const a of line.matchAll(ATTR_RE)) header[a[1].toLowerCase()] = a[2];
      continue;
    }

    if (/^#EXTINF/i.test(line)) {
      const parsed = parseExtInf(line);
      pending = parsed
        ? { ...parsed, group: parsed.attrs['group-title'] ?? '', http: {}, props: {} }
        : null;
      continue;
    }

    if (/^#EXTGRP:/i.test(line)) {
      if (pending) pending.group = line.slice(line.indexOf(':') + 1).trim();
      continue;
    }

    if (/^#EXTVLCOPT:/i.test(line)) {
      const opt = line.slice(line.indexOf(':') + 1);
      const eq = opt.indexOf('=');
      if (pending && eq > 0) {
        const key = opt.slice(0, eq).trim().toLowerCase();
        const value = opt.slice(eq + 1).trim();
        if (key === 'http-user-agent') pending.http['user-agent'] = value;
        else if (key === 'http-referrer' || key === 'http-referer') {
          pending.http.referer = value;
        }
      }
      continue;
    }

    if (/^#KODIPROP:/i.test(line)) {
      const prop = line.slice(line.indexOf(':') + 1);
      const eq = prop.indexOf('=');
      if (pending && eq > 0) pending.props[prop.slice(0, eq).trim()] = prop.slice(eq + 1).trim();
      continue;
    }

    if (/^#EXTHTTP:/i.test(line)) {
      try {
        const obj = JSON.parse(line.slice(line.indexOf(':') + 1));
        if (pending) {
          for (const [k, v] of Object.entries(obj)) pending.http[k.toLowerCase()] = String(v);
        }
      } catch {
        // Malformed #EXTHTTP is common; the entry is still usable without it.
      }
      continue;
    }

    if (line.startsWith('#')) continue;

    if (!isHttpUrl(line)) {
      reset();
      continue;
    }

    const attrs = pending?.attrs ?? {};
    entries.push({
      name: pending?.title || attrs['tvg-name'] || hostOf(line),
      url: line,
      group: pending?.group ?? '',
      tvgId: attrs['tvg-id'] ?? '',
      tvgName: attrs['tvg-name'] ?? '',
      tvgLogo: attrs['tvg-logo'] ?? '',
      tvgCountry: normalizeCountry(attrs['tvg-country']),
      tvgLanguage: attrs['tvg-language'] ?? '',
      radio: /^true$/i.test(attrs.radio ?? ''),
      http: pending?.http ?? {},
      props: pending?.props ?? {},
      drm: Object.keys(pending?.props ?? {}).some((k) => /license|drm/i.test(k)),
      kind: streamKind(line),
      sourceId: meta.sourceId ?? '',
      sourceUrl: meta.sourceUrl ?? '',
    });

    reset();
  }

  return { header, entries };
}

function hostOf(url) {
  try {
    return new URL(url).hostname;
  } catch {
    return url;
  }
}

/**
 * Classify a stream URL by container so callers can keep HLS only.
 *
 * @param {string} url
 * @returns {'hls'|'dash'|'mpegts'|'playlist'|'other'}
 */
export function streamKind(url) {
  const path = pathOf(url).toLowerCase();
  const query = queryOf(url).toLowerCase();
  if (path.endsWith('.m3u8') || query.includes('.m3u8') || /[?&]type=m3u8/.test(query)) return 'hls';
  if (path.endsWith('.mpd')) return 'dash';
  if (path.endsWith('.ts')) return 'mpegts';
  if (path.endsWith('.m3u')) return 'playlist';
  return 'other';
}

/** True for URLs a client would open as a playlist of channels, not a stream. */
export function looksLikeIndexPlaylist(url) {
  const path = pathOf(url).toLowerCase();
  return path.endsWith('.m3u') || /\b(index|playlist|channels|all)\.m3u8?$/.test(path);
}

function pathOf(url) {
  try {
    return new URL(url).pathname;
  } catch {
    return url;
  }
}

function queryOf(url) {
  try {
    return new URL(url).search;
  } catch {
    return '';
  }
}

/**
 * Canonical form used for de-duplication.
 *
 * Case-folds the host, drops the default port, the fragment and a trailing
 * slash, but keeps the query — for a lot of panels the query *is* the channel.
 *
 * @param {string} url
 * @returns {string}
 */
export function normalizeUrl(url) {
  try {
    const u = new URL(url.trim());
    u.hash = '';
    u.hostname = u.hostname.toLowerCase();
    if (
      (u.protocol === 'http:' && u.port === '80') ||
      (u.protocol === 'https:' && u.port === '443')
    ) {
      u.port = '';
    }
    if (u.pathname.length > 1 && u.pathname.endsWith('/')) {
      u.pathname = u.pathname.replace(/\/+$/, '');
    }
    return u.href;
  } catch {
    return url.trim();
  }
}

/**
 * Merge entries from several sources, keeping the first occurrence of each
 * stream URL and recording which other sources carried it.
 *
 * @param {Array<object>} entries
 * @returns {Array<object>}
 */
export function dedupe(entries) {
  const byUrl = new Map();
  for (const entry of entries) {
    const key = normalizeUrl(entry.url);
    const existing = byUrl.get(key);
    if (!existing) {
      byUrl.set(key, { ...entry, seenIn: [entry.sourceId].filter(Boolean) });
      continue;
    }
    if (entry.sourceId && !existing.seenIn.includes(entry.sourceId)) {
      existing.seenIn.push(entry.sourceId);
    }
    // Prefer the richer record: later sources often carry the logo or the id.
    for (const field of ['tvgId', 'tvgLogo', 'tvgCountry', 'tvgLanguage', 'group']) {
      if (!existing[field] && entry[field]) existing[field] = entry[field];
    }
  }
  return [...byUrl.values()];
}

/**
 * Render entries back to an M3U playlist, preserving the attributes a player
 * needs (logo, EPG id, per-channel user agent).
 *
 * @param {Array<object>} entries
 * @param {{epgUrl?: string}} [opts]
 * @returns {string}
 */
export function toM3u(entries, opts = {}) {
  const out = [opts.epgUrl ? `#EXTM3U x-tvg-url="${opts.epgUrl}"` : '#EXTM3U'];

  for (const e of entries) {
    const attrs = [
      e.tvgId && `tvg-id="${escapeAttr(e.tvgId)}"`,
      e.tvgName && `tvg-name="${escapeAttr(e.tvgName)}"`,
      e.tvgLogo && `tvg-logo="${escapeAttr(e.tvgLogo)}"`,
      e.tvgCountry && `tvg-country="${escapeAttr(e.tvgCountry)}"`,
      e.tvgLanguage && `tvg-language="${escapeAttr(e.tvgLanguage)}"`,
      e.group && `group-title="${escapeAttr(e.group)}"`,
      // Round-trips the flag `parsePlaylist` reads. Leaving it out was silent
      // data loss: a source that tags its stations `radio="true"` had that
      // stripped on the way through, so every station arrived downstream
      // looking like a television channel — filling Live TV and leaving the
      // radio section empty.
      e.radio && 'radio="true"',
    ].filter(Boolean);

    out.push(`#EXTINF:-1 ${attrs.join(' ')},${e.name}`.replace(/\s+,/, ','));
    if (e.http?.['user-agent']) out.push(`#EXTVLCOPT:http-user-agent=${e.http['user-agent']}`);
    if (e.http?.referer) out.push(`#EXTVLCOPT:http-referrer=${e.http.referer}`);
    out.push(e.url);
  }

  return `${out.join('\n')}\n`;
}

function escapeAttr(value) {
  return String(value).replace(/"/g, "'");
}

/**
 * Plain-text listing: one channel per line, tab separated.
 *
 * @param {Array<object>} entries
 * @returns {string}
 */
export function toText(entries) {
  const rows = entries.map((e) =>
    [e.name || '', e.group || '', e.tvgCountry || '', e.url].join('\t'),
  );
  return `${['# name\tgroup\tcountry\turl', ...rows].join('\n')}\n`;
}

/**
 * Bare URL list — one m3u8 per line, nothing else.
 *
 * @param {Array<object>} entries
 * @returns {string}
 */
export function toUrlList(entries) {
  return `${entries.map((e) => e.url).join('\n')}\n`;
}

/**
 * @param {Array<object>} entries
 * @returns {string}
 */
export function toCsv(entries) {
  const cell = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const head = ['name', 'group', 'country', 'language', 'tvg_id', 'kind', 'source', 'url'];
  const rows = entries.map((e) =>
    [e.name, e.group, e.tvgCountry, e.tvgLanguage, e.tvgId, e.kind, e.sourceId, e.url]
      .map(cell)
      .join(','),
  );
  return `${[head.join(','), ...rows].join('\n')}\n`;
}
