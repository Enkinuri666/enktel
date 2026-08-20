#!/usr/bin/env node
/**
 * Scrape public IPTV playlist indexes for .m3u8 stream URLs and write them out
 * as plain text.
 *
 *   node scripts/scrape-m3u8.mjs                       # all built-in sources
 *   node scripts/scrape-m3u8.mjs --list-sources
 *   node scripts/scrape-m3u8.mjs --sources iptv-org-api --country US,GB
 *   node scripts/scrape-m3u8.mjs --crawl https://example.com/page-with-links
 *   node scripts/scrape-m3u8.mjs --check --check-limit 500
 *
 * Writes into data/playlists/ by default: an .m3u the player can load, a
 * tab-separated .txt, a bare URL list, JSON, CSV, and a run report.
 *
 * Only aggregates streams their publishers put on the open web; it neither
 * bypasses authentication nor touches subscription panels.
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { gunzipSync } from 'node:zlib';

import {
  dedupe,
  extractStreamUrls,
  looksLikeIndexPlaylist,
  normalizeUrl,
  parsePlaylist,
  streamKind,
  toCsv,
  toM3u,
  toText,
  toUrlList,
} from './lib/m3u.mjs';
import { countryOf, normalizeCountry } from './lib/countries.mjs';
import { IPTV_ORG_API, SOURCES, selectSources } from './lib/sources.mjs';

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36';

const DEFAULTS = {
  out: 'data/playlists',
  prefix: 'enktel-scrape',
  formats: ['m3u', 'txt', 'urls', 'json', 'csv'],
  timeout: 45_000,
  checkTimeout: 8_000,
  concurrency: 24,
  retries: 2,
  depth: 1,
};

// ---------------------------------------------------------------------------
// argv
// ---------------------------------------------------------------------------

function parseArgs(argv) {
  const opts = {
    sources: [],
    crawl: [],
    from: null,
    out: DEFAULTS.out,
    prefix: DEFAULTS.prefix,
    formats: DEFAULTS.formats,
    depth: DEFAULTS.depth,
    timeout: DEFAULTS.timeout,
    checkTimeout: DEFAULTS.checkTimeout,
    concurrency: DEFAULTS.concurrency,
    limit: 0,
    check: false,
    checkLimit: 0,
    keepDead: false,
    onlyHls: true,
    includeNsfw: false,
    includeDrm: false,
    includeRadio: false,
    country: [],
    group: null,
    name: null,
    epgUrl: '',
    quiet: false,
    listSources: false,
    help: false,
  };

  const list = (v) =>
    String(v)
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    const next = () => {
      const v = argv[++i];
      if (v === undefined) throw new Error(`${arg} needs a value`);
      return v;
    };

    switch (arg) {
      case '--help':
      case '-h':
        opts.help = true;
        break;
      case '--list-sources':
        opts.listSources = true;
        break;
      case '--sources':
        opts.sources.push(...list(next()));
        break;
      case '--crawl':
        opts.crawl.push(next());
        break;
      case '--from':
        opts.from = next();
        break;
      case '--out':
        opts.out = next();
        break;
      case '--prefix':
        opts.prefix = next();
        break;
      case '--formats':
        opts.formats = list(next());
        break;
      case '--depth':
        opts.depth = Number(next());
        break;
      case '--timeout':
        opts.timeout = Number(next());
        break;
      case '--check-timeout':
        opts.checkTimeout = Number(next());
        break;
      case '--concurrency':
        opts.concurrency = Number(next());
        break;
      case '--limit':
        opts.limit = Number(next());
        break;
      case '--check':
        opts.check = true;
        break;
      case '--check-limit':
        opts.check = true;
        opts.checkLimit = Number(next());
        break;
      case '--keep-dead':
        opts.keepDead = true;
        break;
      case '--all-kinds':
        opts.onlyHls = false;
        break;
      case '--include-nsfw':
        opts.includeNsfw = true;
        break;
      case '--include-drm':
        opts.includeDrm = true;
        break;
      case '--include-radio':
        opts.includeRadio = true;
        break;
      case '--country':
        opts.country.push(...list(next()).map(normalizeCountry).filter(Boolean));
        break;
      case '--group':
        opts.group = new RegExp(next(), 'i');
        break;
      case '--name':
        opts.name = new RegExp(next(), 'i');
        break;
      case '--epg-url':
        opts.epgUrl = next();
        break;
      case '--quiet':
        opts.quiet = true;
        break;
      default:
        throw new Error(`Unknown flag: ${arg}`);
    }
  }

  return opts;
}

const HELP = `
Scrape public IPTV indexes for .m3u8 URLs and write them out as text.

Usage: node scripts/scrape-m3u8.mjs [options]

Sources
  --sources a,b        only these built-in sources (default: all)
  --list-sources       print the built-in sources and exit
  --crawl <url>        also scrape this page/playlist (repeatable)
  --from <file>        newline-separated list of extra URLs to scrape
  --depth <n>          follow playlist links found while crawling (default 1)

Filtering
  --country US,GB      keep channels from these countries
  --group <regex>      keep channels whose group matches
  --name <regex>       keep channels whose name matches
  --all-kinds          keep DASH/MPEG-TS too (default: HLS .m3u8 only)
  --include-nsfw       keep adult channels (excluded by default)
  --include-drm        keep DRM-protected entries (excluded by default)
  --include-radio      keep radio-only entries (excluded by default)
  --limit <n>          stop after n channels

Liveness
  --check              probe each stream and drop the dead ones
  --check-limit <n>    probe only the first n (implies --check)
  --keep-dead          keep failures, annotated, instead of dropping them
  --check-timeout <ms> per-probe timeout (default ${DEFAULTS.checkTimeout})
  --concurrency <n>    parallel probes (default ${DEFAULTS.concurrency})

Output
  --out <dir>          output directory (default ${DEFAULTS.out})
  --prefix <name>      output file prefix (default ${DEFAULTS.prefix})
  --formats m3u,txt    any of m3u,txt,urls,json,csv (default: all)
  --epg-url <url>      x-tvg-url to stamp into the generated .m3u
  --quiet              only print the summary
`.trim();

// ---------------------------------------------------------------------------
// fetching
// ---------------------------------------------------------------------------

const log = (opts, ...args) => {
  if (!opts.quiet) console.error(...args);
};

/**
 * Fetch with a timeout, a browser UA and a couple of backed-off retries.
 * Gzipped bodies (.gz) are inflated.
 */
async function fetchText(url, opts, { retries = DEFAULTS.retries } = {}) {
  let lastError;

  for (let attempt = 0; attempt <= retries; attempt++) {
    if (attempt) await sleep(500 * 2 ** (attempt - 1));
    try {
      const res = await fetch(url, {
        redirect: 'follow',
        signal: AbortSignal.timeout(opts.timeout),
        headers: { 'user-agent': UA, accept: '*/*' },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      if (/\.gz($|\?)/i.test(url)) {
        return gunzipSync(Buffer.from(await res.arrayBuffer())).toString('utf8');
      }
      return await res.text();
    } catch (err) {
      lastError = err;
    }
  }

  throw lastError;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Run `worker` over `items` with a fixed number of workers in flight. */
async function pool(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;

  const runners = Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, async () => {
    while (cursor < items.length) {
      const index = cursor++;
      results[index] = await worker(items[index], index);
    }
  });

  await Promise.all(runners);
  return results;
}

// ---------------------------------------------------------------------------
// source handlers
// ---------------------------------------------------------------------------

async function loadM3uSource(source, opts) {
  const body = await fetchText(source.url, opts);
  const { header, entries } = parsePlaylist(body, {
    sourceId: source.id,
    sourceUrl: source.url,
  });
  return { entries, epgUrl: header['x-tvg-url'] ?? '' };
}

/**
 * The iptv-org JSON API: streams carry the URL and headers, channels carry the
 * name, country and categories, logos carry the artwork. Joined on channel id.
 */
async function loadIptvOrgApi(source, opts) {
  const streams = JSON.parse(await fetchText(source.url, opts));

  const channels = new Map();
  try {
    for (const c of JSON.parse(await fetchText(IPTV_ORG_API.channels, opts))) {
      channels.set(c.id, c);
    }
  } catch (err) {
    log(opts, `  ! channel metadata unavailable (${err.message}); using stream titles`);
  }

  const logos = new Map();
  try {
    for (const l of JSON.parse(await fetchText(IPTV_ORG_API.logos, opts))) {
      if (l.channel && l.url && !logos.has(l.channel)) logos.set(l.channel, l.url);
    }
  } catch {
    // Logos are cosmetic — a playlist without them still plays.
  }

  const entries = streams
    .filter((s) => typeof s.url === 'string' && s.url)
    .map((s) => {
      const channel = s.channel ? channels.get(s.channel) : null;
      const http = {};
      if (s.user_agent) http['user-agent'] = s.user_agent;
      if (s.referrer) http.referer = s.referrer;

      return {
        name: channel?.name || s.title || s.channel || 'Unknown',
        url: s.url,
        group: channel?.categories?.[0]
          ? titleCase(channel.categories[0])
          : s.quality
            ? `Quality ${s.quality}`
            : 'Undefined',
        tvgId: s.channel ?? '',
        tvgName: channel?.name ?? s.title ?? '',
        tvgLogo: (s.channel && logos.get(s.channel)) || '',
        tvgCountry: normalizeCountry(channel?.country),
        tvgLanguage: '',
        radio: false,
        nsfw: Boolean(channel?.is_nsfw),
        quality: s.quality ?? '',
        http,
        props: {},
        drm: false,
        kind: streamKind(s.url),
        sourceId: source.id,
        sourceUrl: source.url,
      };
    });

  return { entries, epgUrl: '' };
}

function titleCase(value) {
  return String(value).replace(/\b\w/g, (c) => c.toUpperCase());
}

/**
 * Scrape an arbitrary URL. A playlist body is parsed directly; anything else
 * is treated as a page and mined for playlist links, which are then followed
 * up to `--depth`.
 */
async function crawl(url, opts, depth, seen, sourceId = 'crawl') {
  if (seen.has(normalizeUrl(url)) || depth < 0) return [];
  seen.add(normalizeUrl(url));

  let body;
  try {
    body = await fetchText(url, opts);
  } catch (err) {
    log(opts, `  ! ${url} — ${err.message}`);
    return [];
  }

  if (/^\s*#EXTM3U/i.test(body)) {
    const { entries } = parsePlaylist(body, { sourceId, sourceUrl: url });
    log(opts, `  · ${url} — playlist, ${entries.length} channels`);
    return entries;
  }

  const links = extractStreamUrls(body, url);
  log(opts, `  · ${url} — page, ${links.length} playlist/stream links`);

  const entries = [];
  const follow = [];

  for (const link of links) {
    if (depth > 0 && looksLikeIndexPlaylist(link)) {
      follow.push(link);
      continue;
    }
    entries.push({
      name: labelFromUrl(link),
      url: link,
      group: 'Scraped',
      tvgId: '',
      tvgName: '',
      tvgLogo: '',
      tvgCountry: '',
      tvgLanguage: '',
      radio: false,
      http: {},
      props: {},
      drm: false,
      kind: streamKind(link),
      sourceId,
      sourceUrl: url,
    });
  }

  for (const link of follow) {
    entries.push(...(await crawl(link, opts, depth - 1, seen, sourceId)));
  }

  return entries;
}

/** Best-effort channel name for a URL found loose on a page. */
function labelFromUrl(url) {
  try {
    const u = new URL(url);
    const parts = u.pathname.split('/').filter(Boolean);
    const file = parts.pop() ?? '';
    const slug = /^(index|playlist|master|chunklist|tracks|live|stream)/i.test(file)
      ? (parts.pop() ?? file)
      : file;
    const cleaned = slug.replace(/\.(m3u8?|mpd)$/i, '').replace(/[-_+]+/g, ' ').trim();
    return cleaned ? titleCase(cleaned) : u.hostname;
  } catch {
    return url;
  }
}

// ---------------------------------------------------------------------------
// liveness
// ---------------------------------------------------------------------------

/**
 * Probe a stream: ask for the first couple of KB and, for HLS, confirm the
 * body really is a playlist. Cheap enough to run over thousands of URLs.
 */
async function probe(entry, opts) {
  const started = Date.now();
  const headers = { 'user-agent': entry.http?.['user-agent'] || UA, range: 'bytes=0-2047' };
  if (entry.http?.referer) headers.referer = entry.http.referer;

  try {
    const res = await fetch(entry.url, {
      redirect: 'follow',
      signal: AbortSignal.timeout(opts.checkTimeout),
      headers,
    });
    const ms = Date.now() - started;

    if (!res.ok && res.status !== 206) {
      return { alive: false, status: res.status, ms, reason: `HTTP ${res.status}` };
    }

    if (entry.kind === 'hls') {
      const body = (await res.text()).slice(0, 4096);
      if (!/#EXTM3U/i.test(body)) {
        return { alive: false, status: res.status, ms, reason: 'not an HLS playlist' };
      }
      return {
        alive: true,
        status: res.status,
        ms,
        variant: /#EXT-X-STREAM-INF/i.test(body) ? 'master' : 'media',
      };
    }

    return { alive: true, status: res.status, ms };
  } catch (err) {
    return {
      alive: false,
      status: 0,
      ms: Date.now() - started,
      reason: err.name === 'TimeoutError' ? 'timeout' : err.message,
    };
  }
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

function keep(entry, opts) {
  if (opts.onlyHls && entry.kind !== 'hls') return false;
  if (!opts.includeNsfw && (entry.nsfw || /\b(xxx|adult|porn)\b/i.test(entry.group))) return false;
  if (!opts.includeDrm && entry.drm) return false;
  if (!opts.includeRadio && entry.radio) return false;
  if (opts.country.length && !opts.country.includes(entry.tvgCountry)) return false;
  if (opts.group && !opts.group.test(entry.group || '')) return false;
  if (opts.name && !opts.name.test(entry.name || '')) return false;
  return true;
}

async function main() {
  let opts;
  try {
    opts = parseArgs(process.argv.slice(2));
  } catch (err) {
    console.error(`${err.message}\n\n${HELP}`);
    process.exit(2);
  }

  if (opts.help) {
    console.log(HELP);
    return;
  }

  if (opts.listSources) {
    for (const s of SOURCES) console.log(`${s.id.padEnd(20)} ${s.name}\n${' '.repeat(21)}${s.url}`);
    return;
  }

  const startedAt = Date.now();
  const report = { startedAt: new Date().toISOString(), sources: [], filters: {}, totals: {} };
  const collected = [];
  let epgUrl = opts.epgUrl;
  // Only one guide URL fits in the generated playlist header, but a run that
  // merges several sources has several. Record them all so a per-source guide
  // — the Brisbane one, say — isn't silently lost behind whichever source
  // happened to be loaded first.
  const epgUrls = [];

  const sources = selectSources(opts.sources);
  log(opts, `Scraping ${sources.length} source(s)…`);

  for (const source of sources) {
    const t0 = Date.now();
    try {
      const loader = source.kind === 'iptv-org-api' ? loadIptvOrgApi : loadM3uSource;
      const { entries, epgUrl: found } = await loader(source, opts);
      const sourceEpg = source.epgUrl || found;
      if (sourceEpg) epgUrls.push({ sourceId: source.id, url: sourceEpg });
      if (!epgUrl && sourceEpg) epgUrl = sourceEpg;

      // A single-country source knows something its group titles don't say.
      if (source.country) {
        for (const entry of entries) {
          if (!entry.tvgCountry) entry.tvgCountry = normalizeCountry(source.country);
        }
      }

      collected.push(...entries);
      log(opts, `  ✓ ${source.id.padEnd(20)} ${entries.length} channels`);
      report.sources.push({ id: source.id, url: source.url, ok: true, count: entries.length, ms: Date.now() - t0 });
    } catch (err) {
      log(opts, `  ✗ ${source.id.padEnd(20)} ${err.message}`);
      report.sources.push({ id: source.id, url: source.url, ok: false, error: err.message, ms: Date.now() - t0 });
    }
  }

  const extra = [...opts.crawl];
  if (opts.from) {
    const listed = (await readFile(opts.from, 'utf8'))
      .split(/\r?\n/)
      .map((l) => l.trim())
      .filter((l) => l && !l.startsWith('#'));
    extra.push(...listed);
  }

  if (extra.length) {
    log(opts, `Crawling ${extra.length} extra URL(s)…`);
    const seen = new Set();
    for (const url of extra) {
      const t0 = Date.now();
      const entries = await crawl(url, opts, opts.depth, seen);
      collected.push(...entries);
      report.sources.push({ id: 'crawl', url, ok: true, count: entries.length, ms: Date.now() - t0 });
    }
  }

  // De-duplicate before filtering: dedupe() merges metadata across sources, so
  // a channel whose country or group only one source knows still passes the
  // filters that depend on it.
  const beforeDedupe = collected.length;
  const unique = dedupe(collected);

  // Backfill the country from the group title where no feed set the attribute.
  // Done after dedupe (so a country any one source knew is already merged in)
  // and before filtering, so --country sees it and the output says which
  // country a channel is from instead of filing it under Unknown.
  for (const entry of unique) entry.tvgCountry = countryOf(entry);

  let entries = unique.filter((e) => keep(e, opts));
  const afterFilter = entries.length;
  if (opts.limit > 0) entries = entries.slice(0, opts.limit);

  log(
    opts,
    `Collected ${beforeDedupe} → ${unique.length} unique → ${afterFilter} after filtering` +
      (opts.limit ? ` → ${entries.length} after --limit` : ''),
  );

  if (opts.check && entries.length) {
    const target = opts.checkLimit > 0 ? entries.slice(0, opts.checkLimit) : entries;
    log(opts, `Probing ${target.length} stream(s) with ${opts.concurrency} in flight…`);

    let done = 0;
    let alive = 0;
    await pool(target, opts.concurrency, async (entry) => {
      const result = await probe(entry, opts);
      entry.check = result;
      if (result.alive) alive++;
      if (!opts.quiet && ++done % 100 === 0) {
        process.stderr.write(`  … ${done}/${target.length} (${alive} alive)\r`);
      }
    });

    log(opts, `  ${alive}/${target.length} responded`);
    if (!opts.keepDead) entries = entries.filter((e) => !e.check || e.check.alive);
    report.totals.probed = target.length;
    report.totals.alive = alive;
  }

  entries.sort(
    (a, b) => (a.group || '').localeCompare(b.group || '') || (a.name || '').localeCompare(b.name || ''),
  );

  const outDir = path.resolve(process.cwd(), opts.out);
  await mkdir(outDir, { recursive: true });
  const base = path.join(outDir, opts.prefix);
  const written = [];

  const writers = {
    m3u: [`${base}.m3u`, () => toM3u(entries, { epgUrl })],
    txt: [`${base}.txt`, () => toText(entries)],
    urls: [`${base}.urls.txt`, () => toUrlList(entries)],
    json: [`${base}.json`, () => `${JSON.stringify(entries, null, 2)}\n`],
    csv: [`${base}.csv`, () => toCsv(entries)],
  };

  for (const format of opts.formats) {
    const writer = writers[format];
    if (!writer) {
      console.error(`Unknown format "${format}" — expected one of ${Object.keys(writers).join(', ')}`);
      process.exit(2);
    }
    await writeFile(writer[0], writer[1](), 'utf8');
    written.push(path.relative(process.cwd(), writer[0]));
  }

  report.filters = {
    onlyHls: opts.onlyHls,
    includeNsfw: opts.includeNsfw,
    includeDrm: opts.includeDrm,
    includeRadio: opts.includeRadio,
    country: opts.country,
    group: opts.group?.source ?? null,
    name: opts.name?.source ?? null,
    limit: opts.limit || null,
  };
  report.totals = {
    ...report.totals,
    collected: beforeDedupe,
    unique: unique.length,
    afterFilter,
    written: entries.length,
    byGroup: countBy(entries, (e) => e.group || 'Undefined'),
    byCountry: countBy(entries, (e) => e.tvgCountry || 'Unknown'),
  };
  report.epgUrl = epgUrl || null;
  report.epgUrls = epgUrls;
  report.durationMs = Date.now() - startedAt;
  report.files = written;

  await writeFile(`${base}.report.json`, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  written.push(path.relative(process.cwd(), `${base}.report.json`));

  console.log(`${entries.length} channels → ${written.join(', ')}`);
}

function countBy(items, keyOf) {
  const counts = {};
  for (const item of items) {
    const key = keyOf(item);
    counts[key] = (counts[key] ?? 0) + 1;
  }
  return Object.fromEntries(Object.entries(counts).sort((a, b) => b[1] - a[1]));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
