/**
 * Fetchers for the EPG-side reference data: guide channel lists, the iptv-org
 * channel catalog, and channel logos.
 *
 * Shared by `match-epg.mjs` and `build-lineup.mjs` so both resolve a channel's
 * id and artwork the same way.
 */
import { createGunzip } from 'node:zlib';
import { Readable } from 'node:stream';

import { normalizeCountry } from './countries.mjs';
import { buildIndex, extractChannels } from './xmltv.mjs';

export const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36';

export const IPTV_ORG = {
  channels: 'https://iptv-org.github.io/api/channels.json',
  logos: 'https://iptv-org.github.io/api/logos.json',
};

/** Stop reading a guide once this much has arrived without a channel list. */
const MAX_HEAD_BYTES = 32 * 1024 * 1024;

async function getJson(url, timeout = 120_000) {
  const res = await fetch(url, {
    redirect: 'follow',
    signal: AbortSignal.timeout(timeout),
    headers: { 'user-agent': UA, accept: 'application/json' },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/**
 * Read an XMLTV guide only as far as its channel list.
 *
 * The response is consumed chunk by chunk (inflating on the way when it is
 * gzipped) and abandoned as soon as the first `<programme` appears, which in
 * XMLTV comes after the last `<channel>`. Downloading the rest would mean
 * hundreds of megabytes of data this discards.
 *
 * @param {string} url
 * @returns {Promise<string>}
 */
export async function fetchGuideHead(url) {
  const res = await fetch(url, {
    redirect: 'follow',
    signal: AbortSignal.timeout(120_000),
    headers: { 'user-agent': UA, accept: '*/*' },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);

  const gzipped =
    /\.gz($|\?)/i.test(url) || /gzip/i.test(res.headers.get('content-encoding') ?? '');

  let stream = Readable.fromWeb(res.body);
  if (gzipped) stream = stream.pipe(createGunzip());

  let text = '';
  try {
    for await (const chunk of stream) {
      text += chunk.toString('utf8');
      if (text.includes('<programme') || text.length > MAX_HEAD_BYTES) break;
    }
  } finally {
    stream.destroy?.();
    // The body may already be closed by the break above; cancelling a closed
    // body is not an error worth surfacing.
    res.body?.cancel?.().catch(() => {});
  }

  return text;
}

/**
 * The iptv-org channel catalog, shaped like guide channels.
 *
 * This is the namespace iptv-org's own `tvg-id`s come from, which makes it the
 * most useful single index to match against.
 */
export async function fetchCatalog() {
  const channels = await getJson(IPTV_ORG.channels);
  return channels
    .filter((c) => c?.id && c?.name && !c.closed)
    .map((c) => ({
      id: c.id,
      names: [c.name, ...(Array.isArray(c.alt_names) ? c.alt_names : [])].filter(Boolean),
      country: normalizeCountry(c.country),
      source: 'iptv-org',
    }));
}

/**
 * Channel id → logo URL.
 *
 * Several logos exist per channel; prefer one that is actually in use, in a
 * raster format players reliably draw (an SVG renders as nothing in a good
 * few IPTV clients), and the largest of those, since a logo is scaled down
 * far more gracefully than up.
 *
 * @returns {Promise<Map<string, string>>}
 */
export async function fetchLogos() {
  const logos = await getJson(IPTV_ORG.logos);
  const best = new Map();

  for (const logo of logos) {
    if (!logo?.channel || !logo?.url) continue;
    const candidate = {
      url: logo.url,
      inUse: logo.in_use !== false,
      raster: !/^svg$/i.test(logo.format ?? ''),
      area: (Number(logo.width) || 0) * (Number(logo.height) || 0),
    };

    const current = best.get(logo.channel);
    if (!current || betterLogo(candidate, current)) best.set(logo.channel, candidate);
  }

  return new Map([...best].map(([id, logo]) => [id, logo.url]));
}

function betterLogo(candidate, current) {
  if (candidate.inUse !== current.inUse) return candidate.inUse;
  if (candidate.raster !== current.raster) return candidate.raster;
  return candidate.area > current.area;
}

/**
 * Load every requested id namespace and build one match index over them.
 *
 * @param {{guides?: string[], catalog?: boolean, onLog?: (msg: string) => void}} opts
 * @returns {Promise<{index: ReturnType<typeof buildIndex>, sources: Array<object>}>}
 */
export async function loadGuideIndex({ guides = [], catalog = true, onLog = () => {} } = {}) {
  const channels = [];
  const sources = [];

  if (catalog) {
    try {
      const found = await fetchCatalog();
      channels.push(...found);
      sources.push({ id: 'iptv-org-catalog', count: found.length, ok: true });
      onLog(`  ✓ iptv-org catalog: ${found.length} channels`);
    } catch (err) {
      sources.push({ id: 'iptv-org-catalog', ok: false, error: err.message });
      onLog(`  ✗ iptv-org catalog: ${err.message}`);
    }
  }

  for (const url of guides) {
    try {
      const found = extractChannels(await fetchGuideHead(url)).map((c) => ({ ...c, source: url }));
      channels.push(...found);
      sources.push({ id: url, count: found.length, ok: true });
      onLog(`  ✓ ${url}: ${found.length} channels`);
    } catch (err) {
      sources.push({ id: url, ok: false, error: err.message });
      onLog(`  ✗ ${url}: ${err.message}`);
    }
  }

  return { index: buildIndex(channels), sources };
}
