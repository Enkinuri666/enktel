#!/usr/bin/env node
/**
 * Resolve a channel roster: EPG id, logo, country and genre for every channel.
 *
 *   node scripts/build-roster.mjs                        # the built-in MENA roster
 *   node scripts/build-roster.mjs --playlist some.m3u    # any playlist file
 *   node scripts/build-roster.mjs --check-logos          # probe every logo URL
 *
 * The scraper's other tools start from a playlist that already carries half
 * the metadata. A roster carries none — a panel publishes a stream id and a
 * name an operator typed — so everything a player needs has to be worked out
 * from that name, against the iptv-org catalog. See `lib/resolve.mjs` for how,
 * and `lib/rosters.mjs` for why no credentials live in this repository.
 *
 * ## Output
 *
 * The resolved metadata (`.csv`, `.txt`, `.report.json`) is credential-free
 * and belongs in git. The **playlist** is not: a roster's stream URLs embed
 * the line's username and password, so it is written to the gitignored
 * `data/catalog/` and only when credentials are supplied. A `--playlist` run
 * has no such problem — its URLs came from a file that already had them — so
 * its `.m3u` is written alongside the rest.
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

import {
  credentialsFromEnv,
  mergeCredentials,
  missingCredentials,
  parseEnvFile,
  redact,
} from './lib/credentials.mjs';
import { normalizeCountry } from './lib/countries.mjs';
import { GENRES, lineupGroup, lineupSorter } from './lib/genres.mjs';
import { fetchCatalog, fetchGuideSites, fetchLogos, loadGuideIndex, UA } from './lib/guide-sources.mjs';
import { parsePlaylist, toM3u } from './lib/m3u.mjs';
import { createResolver } from './lib/resolve.mjs';
import { ROSTERS, buildStreamUrl, selectRoster, toRosterCsv, toRosterText } from './lib/rosters.mjs';

const DEFAULTS = {
  roster: 'mena',
  out: 'data/rosters',
  streamOut: 'data/catalog',
  threshold: 0.9,
  logoThreshold: 0.85,
  concurrency: 16,
  timeout: 10_000,
  defaultGenre: 'Entertainment',
};

const HELP = `
Resolve a channel roster to EPG ids, logos, countries and genres.

Usage: node scripts/build-roster.mjs [options]

Input
  --roster <id>          built-in roster (default ${DEFAULTS.roster}); one of ${ROSTERS.map((r) => r.id).join(', ')}
  --playlist <file>      resolve a playlist file instead of a built-in roster
  --guide <url>          extra XMLTV guide for id matching (repeatable)
  --no-catalog           skip the iptv-org channel catalog
  --no-logos             skip logo resolution

Matching
  --threshold <0..1>     EPG id cutoff (default ${DEFAULTS.threshold})
  --logo-threshold <0..1>  logo cutoff (default ${DEFAULTS.logoThreshold}, looser on purpose)
  --default-genre <g>    bucket for channels no rule matches (default ${DEFAULTS.defaultGenre})

Genres: ${GENRES.join(', ')}

Verification
  --check-logos          request every resolved logo and report the dead ones
  --concurrency <n>      parallel probes (default ${DEFAULTS.concurrency})
  --timeout <ms>         per probe (default ${DEFAULTS.timeout})

Credentials (roster mode only, for the playable playlist)
  XTREAM_USERNAME / --username <user>
  XTREAM_PASSWORD / --password <pass>
  XTREAM_SERVER   / --server <url>     overrides the roster's own server
  --env-file <path>      read the three above from a dotenv-style file

Output
  --out <dir>            resolved metadata (default ${DEFAULTS.out})
  --stream-out <dir>     playable playlist (default ${DEFAULTS.streamOut}, gitignored)
  --prefix <name>        default: the roster id
  --quiet
`.trim();

function parseArgs(argv) {
  const opts = {
    roster: DEFAULTS.roster,
    playlist: '',
    guides: [],
    catalog: true,
    logos: true,
    threshold: DEFAULTS.threshold,
    logoThreshold: DEFAULTS.logoThreshold,
    defaultGenre: DEFAULTS.defaultGenre,
    checkLogos: false,
    concurrency: DEFAULTS.concurrency,
    timeout: DEFAULTS.timeout,
    out: DEFAULTS.out,
    streamOut: DEFAULTS.streamOut,
    prefix: '',
    envFile: '',
    quiet: false,
    help: false,
    ...credentialsFromEnv(process.env),
  };

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
      case '--roster':
        opts.roster = next();
        break;
      case '--playlist':
        opts.playlist = next();
        break;
      case '--guide':
        opts.guides.push(next());
        break;
      case '--no-catalog':
        opts.catalog = false;
        break;
      case '--no-logos':
        opts.logos = false;
        break;
      case '--threshold':
        opts.threshold = Number(next());
        break;
      case '--logo-threshold':
        opts.logoThreshold = Number(next());
        break;
      case '--default-genre':
        opts.defaultGenre = next();
        break;
      case '--check-logos':
        opts.checkLogos = true;
        break;
      case '--concurrency':
        opts.concurrency = Number(next());
        break;
      case '--timeout':
        opts.timeout = Number(next());
        break;
      case '--out':
        opts.out = next();
        break;
      case '--stream-out':
        opts.streamOut = next();
        break;
      case '--prefix':
        opts.prefix = next();
        break;
      case '--server':
        opts.server = next();
        break;
      case '--username':
        opts.username = next();
        break;
      case '--password':
        opts.password = next();
        break;
      case '--env-file':
        opts.envFile = next();
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

const log = (opts, ...args) => {
  if (!opts.quiet) console.error(...args);
};

function countBy(items, keyOf) {
  const counts = {};
  for (const item of items) {
    const key = keyOf(item);
    if (key) counts[key] = (counts[key] ?? 0) + 1;
  }
  return counts;
}

const pct = (part, whole) => `${((part / (whole || 1)) * 100).toFixed(1)}%`;

/** Run `worker` over `items`, at most `limit` at a time. */
async function mapLimit(items, limit, worker) {
  const results = new Array(items.length);
  let next = 0;
  const runners = Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, async () => {
    for (let i = next++; i < items.length; i = next++) {
      results[i] = await worker(items[i], i);
    }
  });
  await Promise.all(runners);
  return results;
}

/**
 * Is this logo actually there?
 *
 * A `tvg-logo` pointing at a 404 is worse than an empty one — the player shows
 * a broken-image placeholder where it would otherwise show the channel name.
 * HEAD first, since that is all this needs; some CDNs refuse it, so a rejected
 * HEAD falls back to a ranged GET rather than being reported as dead.
 */
async function checkLogo(url, timeout) {
  const request = (method, headers) =>
    fetch(url, {
      method,
      redirect: 'follow',
      signal: AbortSignal.timeout(timeout),
      headers: { 'user-agent': UA, ...headers },
    });

  try {
    let res = await request('HEAD');
    if (res.status === 405 || res.status === 403 || res.status === 501) {
      res = await request('GET', { range: 'bytes=0-1023' });
      res.body?.cancel?.().catch(() => {});
    }
    const type = res.headers.get('content-type') ?? '';
    if (!res.ok) return { ok: false, status: res.status, error: `HTTP ${res.status}` };
    // A 200 carrying an HTML error page is the common failure here.
    if (type && !/^image\//i.test(type) && !/octet-stream/i.test(type)) {
      return { ok: false, status: res.status, error: `not an image (${type})` };
    }
    return { ok: true, status: res.status, type };
  } catch (err) {
    return { ok: false, status: 0, error: err.message };
  }
}

/** Channels to resolve, from a built-in roster or a playlist file. */
async function loadInput(opts) {
  if (opts.playlist) {
    const body = await readFile(path.resolve(process.cwd(), opts.playlist), 'utf8');
    const { header, entries } = parsePlaylist(body);
    return {
      mode: 'playlist',
      label: opts.playlist,
      epgUrl: header['x-tvg-url'] ?? '',
      channels: entries.map((e, i) => ({
        id: String(i + 1),
        name: e.name,
        country: normalizeCountry(e.tvgCountry),
        url: e.url,
        group: e.group,
        tvgId: e.tvgId,
        tvgLogo: e.tvgLogo,
        http: e.http,
      })),
    };
  }

  const roster = selectRoster(opts.roster);
  return { mode: 'roster', label: roster.name, epgUrl: '', roster, channels: roster.channels };
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

  if (opts.envFile) {
    opts = { ...opts, ...mergeCredentials(opts, parseEnvFile(await readFile(opts.envFile, 'utf8'))) };
  }

  let input;
  try {
    input = await loadInput(opts);
  } catch (err) {
    console.error(err.message);
    process.exit(2);
  }

  const prefix = opts.prefix || (input.mode === 'roster' ? `enktel-${opts.roster}` : 'enktel-roster');
  log(opts, `${input.channels.length} channels in ${input.label}`);

  // The catalog is loaded directly rather than through loadGuideIndex, because
  // the resolver needs the raw channel list to widen its names — the index
  // loadGuideIndex returns is already built.
  const channels = [];
  const sources = [];

  if (opts.catalog) {
    try {
      const found = await fetchCatalog();
      channels.push(...found);
      sources.push({ id: 'iptv-org-catalog', count: found.length, ok: true });
      log(opts, `  ✓ iptv-org catalog: ${found.length} channels`);
    } catch (err) {
      sources.push({ id: 'iptv-org-catalog', ok: false, error: err.message });
      log(opts, `  ✗ iptv-org catalog: ${err.message}`);
    }
  }

  if (opts.guides.length) {
    const extra = await loadGuideIndex({
      guides: opts.guides,
      catalog: false,
      onLog: (msg) => log(opts, msg),
    });
    channels.push(...extra.index.all.map((c) => ({ id: c.id, names: c.names, country: c.country })));
    sources.push(...extra.sources);
  }

  let logos = new Map();
  if (opts.logos) {
    try {
      logos = await fetchLogos();
      log(opts, `  ✓ logos: ${logos.size} channels`);
    } catch (err) {
      log(opts, `  ✗ logos: ${err.message}`);
    }
  }

  let guideSites = new Map();
  try {
    guideSites = await fetchGuideSites();
    log(opts, `  ✓ guide coverage: ${guideSites.size} channels`);
  } catch (err) {
    log(opts, `  ✗ guide coverage: ${err.message}`);
  }

  const { resolve } = createResolver({
    channels,
    logos,
    guideSites,
    threshold: opts.threshold,
    logoThreshold: opts.logoThreshold,
    // A roster's countries are curated; a playlist's are whatever the index
    // that published it decided they were.
    strictCountry: input.mode === 'roster',
  });

  const records = input.channels.map((channel) => {
    const resolved = resolve({
      name: channel.name,
      channel: channel.channel,
      tvgId: channel.tvgId,
      tvgLogo: channel.tvgLogo,
      tvgCountry: channel.country,
      group: channel.group,
    });
    return { ...channel, ...resolved, note: channel.note ?? '' };
  });

  if (opts.checkLogos) {
    const withLogos = records.filter((r) => r.tvgLogo);
    log(opts, `Checking ${withLogos.length} logo URLs…`);
    const seen = new Map();
    await mapLimit(withLogos, opts.concurrency, async (record) => {
      if (!seen.has(record.tvgLogo)) {
        seen.set(record.tvgLogo, await checkLogo(record.tvgLogo, opts.timeout));
      }
      record.logoCheck = seen.get(record.tvgLogo);
    });
    const dead = withLogos.filter((r) => !r.logoCheck?.ok);
    log(opts, `  ${withLogos.length - dead.length} live, ${dead.length} dead`);
    for (const r of dead) log(opts, `  ✗ ${r.display}: ${r.logoCheck.error}`);
  }

  // A general-interest channel matches no genre rule and there are plenty of
  // them; bucketing beats dropping, but the count is worth keeping separate so
  // "Entertainment" is never mistaken for a classification that happened.
  let unclassified = 0;
  for (const record of records) {
    if (!record.genre) {
      unclassified++;
      record.genre = opts.defaultGenre;
      record.genreVia = 'default';
    } else {
      record.genreVia = 'rule';
    }
    record.group = lineupGroup(record.country, record.genre);
  }

  const countryOrder = [...new Set(records.map((r) => r.country).filter(Boolean))].sort();
  records.sort(lineupSorter(countryOrder));

  // ---- write ----
  const outDir = path.resolve(process.cwd(), opts.out);
  await mkdir(outDir, { recursive: true });
  const base = path.join(outDir, prefix);
  const written = [];

  await writeFile(`${base}.csv`, toRosterCsv(records), 'utf8');
  await writeFile(`${base}.txt`, toRosterText(records), 'utf8');
  written.push(`${prefix}.csv`, `${prefix}.txt`);

  const playable = records.map((r) => ({
    name: r.display,
    url: r.url ?? '',
    group: r.group,
    tvgId: r.tvgId,
    tvgName: r.display,
    tvgLogo: r.tvgLogo,
    tvgCountry: r.country,
    tvgLanguage: r.language,
    http: r.http ?? {},
  }));

  let streamFile = '';
  if (input.mode === 'playlist') {
    // These URLs came from a file that already carried them; nothing to hide.
    await writeFile(`${base}.m3u`, toM3u(playable, { epgUrl: input.epgUrl }), 'utf8');
    written.push(`${prefix}.m3u`);
  } else {
    const missing = missingCredentials(opts);
    if (missing.length) {
      log(
        opts,
        `No playlist written: set ${missing.join(', ')} to render stream URLs for this roster.`,
      );
    } else {
      const server = opts.server || input.roster.server;
      const roster = { ...input.roster, server };
      for (const [i, record] of records.entries()) {
        playable[i].url = buildStreamUrl(roster, record, opts);
      }
      const streamDir = path.resolve(process.cwd(), opts.streamOut);
      await mkdir(streamDir, { recursive: true });
      streamFile = path.join(streamDir, `${prefix}.m3u`);
      await writeFile(streamFile, toM3u(playable, { epgUrl: input.epgUrl }), 'utf8');
    }
  }

  const withId = records.filter((r) => r.tvgId);
  const report = {
    generatedAt: new Date().toISOString(),
    source: input.label,
    mode: input.mode,
    sources,
    thresholds: { id: opts.threshold, logo: opts.logoThreshold },
    totals: {
      channels: records.length,
      epgIds: withId.length,
      epgCoverage: pct(withId.length, records.length),
      // An id nothing publishes a guide for is not the same as an id: this is
      // the number that decides whether a viewer sees programmes.
      guideCovered: records.filter((r) => r.guideSites?.length).length,
      guideCoverage: pct(
        records.filter((r) => r.guideSites?.length).length,
        records.length,
      ),
      logos: records.filter((r) => r.tvgLogo).length,
      logoCoverage: pct(records.filter((r) => r.tvgLogo).length, records.length),
      curated: records.filter((r) => r.idVia === 'curated').length,
      unclassified,
    },
    idsBy: countBy(records, (r) => r.idVia),
    unresolvedBy: countBy(records, (r) => r.idReason),
    byCountry: countBy(records, (r) => r.country),
    byGenre: countBy(records, (r) => r.genre),
    unresolved: records
      .filter((r) => !r.tvgId)
      .map((r) => ({
        id: r.id,
        name: r.name,
        display: r.display,
        country: r.country,
        reason: r.idReason,
        ...(r.ambiguousWith ? { ambiguousWith: r.ambiguousWith } : {}),
        ...(r.rejected ? { rejected: r.rejected } : {}),
        ...(r.tvgLogo ? { logoFrom: r.logoFrom ?? '' } : {}),
        ...(r.note ? { note: r.note } : {}),
      })),
    ...(opts.checkLogos
      ? {
          logoCheck: {
            checked: records.filter((r) => r.logoCheck).length,
            dead: records
              .filter((r) => r.logoCheck && !r.logoCheck.ok)
              .map((r) => ({ id: r.id, display: r.display, url: r.tvgLogo, error: r.logoCheck.error })),
          },
        }
      : {}),
  };

  await writeFile(`${base}.report.json`, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  written.push(`${prefix}.report.json`);

  console.log(
    `${records.length} channels — EPG ${report.totals.epgCoverage} (guide ${report.totals.guideCoverage}), logos ${report.totals.logoCoverage} → ${path.relative(process.cwd(), outDir)}/{${written.join(', ')}}`,
  );
  if (streamFile) {
    console.log(`playlist → ${path.relative(process.cwd(), streamFile)} (gitignored: it carries the line's credentials)`);
  }

  if (!opts.quiet) {
    for (const [genre, count] of Object.entries(report.byGenre).sort((a, b) => b[1] - a[1])) {
      log(opts, `  ${genre}: ${count}`);
    }
  }
}

main().catch((err) => {
  console.error(redact(err?.stack ?? String(err), process.env.XTREAM_PASSWORD ?? ''));
  process.exit(1);
});
