#!/usr/bin/env node
/**
 * Export a full Xtream Codes catalog — live channels, movies and series — from
 * a panel line you hold credentials for, as M3U and plain text.
 *
 *   XTREAM_SERVER=http://panel.example:8080 \
 *   XTREAM_USERNAME=… XTREAM_PASSWORD=… \
 *   node scripts/export-xtream-catalog.mjs --countries HR,GB,US,AU \
 *     --expect-movies 190000 --expect-series 75000
 *
 * Why this exists alongside `scrape-m3u8.mjs`: the public free-to-air indexes
 * that scraper reads carry live channels and essentially no VOD. A catalog of
 * ~190k movies and ~75k series only exists on a provider's panel, and the only
 * legitimate way to enumerate it is to ask that panel with a line that is
 * entitled to it. This tool does exactly that and nothing more — it reads the
 * documented `player_api.php` actions, in order, with your own credentials.
 *
 * ── Credentials never land in the repo ──────────────────────────────────────
 * They are read from the environment (or --env-file) and never written to a
 * committed file. Note that every Xtream stream URL *embeds* the username and
 * password, so the exported catalog is itself a secret: the default output
 * directory (data/catalog/) is gitignored. Keep it that way.
 *
 * Catalogs at this scale are fetched per category rather than in one call —
 * a single get_vod_streams over 190k titles times out on most panels — and
 * streamed to disk as they arrive, so memory stays flat.
 */
import { createWriteStream } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { once } from 'node:events';

import {
  call,
  episodeUrls,
  flattenEpisodes,
  liveUrls,
  login,
  matchesCountry,
  movieUrls,
  normalizeCategory,
  normalizeChannel,
  normalizeMovie,
  normalizeSeries,
} from './lib/xtream.mjs';

const DEFAULTS = {
  out: 'data/catalog',
  timeout: 60_000,
  concurrency: 6,
  episodeConcurrency: 12,
};

const HELP = `
Export an Xtream Codes catalog (live + VOD + series) as M3U and text.

Usage: node scripts/export-xtream-catalog.mjs [options]

Credentials (required — from env or flags, never committed)
  XTREAM_SERVER / --server <url>
  XTREAM_USERNAME / --username <user>
  XTREAM_PASSWORD / --password <pass>
  --env-file <path>        read the three above from a dotenv-style file

Selection
  --countries HR,GB,US,AU  restrict LIVE channels to these countries, matched
                           against panel category names (VOD is never filtered
                           by country — catalogs are not organised that way)
  --sections live,vod,series   what to export (default: all three)
  --episodes               also enumerate every episode of every series
                           (one request per series — slow at scale)
  --episodes-limit <n>     enumerate episodes for the first n series only

Expectations
  --expect-movies <n>      warn if the movie count is more than 10% under n
  --expect-series <n>      warn if the series count is more than 10% under n
  --tolerance <pct>        how far under is acceptable (default 10)
  --strict                 exit non-zero when an expectation is not met

Output
  --out <dir>              default ${DEFAULTS.out} (gitignored — URLs hold your password)
  --formats m3u,txt,json   default m3u,txt
  --concurrency <n>        category fetches in flight (default ${DEFAULTS.concurrency})
  --timeout <ms>           per-request timeout (default ${DEFAULTS.timeout})
  --quiet
`.trim();

// ---------------------------------------------------------------------------
// argv + credentials
// ---------------------------------------------------------------------------

function parseArgs(argv) {
  const opts = {
    server: process.env.XTREAM_SERVER ?? '',
    username: process.env.XTREAM_USERNAME ?? '',
    password: process.env.XTREAM_PASSWORD ?? '',
    envFile: null,
    countries: [],
    sections: ['live', 'vod', 'series'],
    episodes: false,
    episodesLimit: 0,
    expectMovies: 0,
    expectSeries: 0,
    tolerance: 10,
    strict: false,
    out: DEFAULTS.out,
    formats: ['m3u', 'txt'],
    concurrency: DEFAULTS.concurrency,
    timeout: DEFAULTS.timeout,
    quiet: false,
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
      case '--countries':
      case '--country':
        opts.countries.push(...list(next()).map((c) => c.toUpperCase()));
        break;
      case '--sections':
        opts.sections = list(next());
        break;
      case '--episodes':
        opts.episodes = true;
        break;
      case '--episodes-limit':
        opts.episodes = true;
        opts.episodesLimit = Number(next());
        break;
      case '--expect-movies':
        opts.expectMovies = Number(next());
        break;
      case '--expect-series':
        opts.expectSeries = Number(next());
        break;
      case '--tolerance':
        opts.tolerance = Number(next());
        break;
      case '--strict':
        opts.strict = true;
        break;
      case '--out':
        opts.out = next();
        break;
      case '--formats':
        opts.formats = list(next());
        break;
      case '--concurrency':
        opts.concurrency = Number(next());
        break;
      case '--timeout':
        opts.timeout = Number(next());
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

/** Minimal dotenv reader — only the three keys this tool needs. */
async function loadEnvFile(file, opts) {
  const text = await readFile(file, 'utf8');
  for (const line of text.split(/\r?\n/)) {
    const m = /^\s*(?:export\s+)?([A-Z0-9_]+)\s*=\s*(.*)$/.exec(line);
    if (!m) continue;
    const value = m[2].trim().replace(/^(['"])(.*)\1$/, '$2');
    if (m[1] === 'XTREAM_SERVER' && !opts.server) opts.server = value;
    if (m[1] === 'XTREAM_USERNAME' && !opts.username) opts.username = value;
    if (m[1] === 'XTREAM_PASSWORD' && !opts.password) opts.password = value;
  }
}

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

const log = (opts, ...args) => {
  if (!opts.quiet) console.error(...args);
};

/**
 * Keep a password out of error output.
 *
 * Only used on free text (panel errors, stack traces), never on structured
 * output: a blind replace over JSON will happily eat a substring of a key —
 * a one-character password turns "expectations" into "ex••••ectations". Short
 * passwords are left alone for the same reason; the report and the catalog
 * files are kept credential-free by construction instead.
 */
function redact(text, password) {
  if (!password || password.length < 6) return text;
  return String(text).split(password).join('••••');
}

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

/**
 * A write stream that respects backpressure — at 190k titles the naive
 * `.write()` loop buffers the whole catalog in memory.
 */
function sink(file) {
  const stream = createWriteStream(file, { encoding: 'utf8' });
  return {
    file,
    async write(chunk) {
      if (!stream.write(chunk)) await once(stream, 'drain');
    },
    async close() {
      stream.end();
      await once(stream, 'finish');
    },
  };
}

const attr = (v) => String(v ?? '').replace(/"/g, "'");
const tsv = (v) => String(v ?? '').replace(/[\t\r\n]+/g, ' ');

// ---------------------------------------------------------------------------
// sections
// ---------------------------------------------------------------------------

/** Fetch categories once, then their streams one category at a time. */
async function fetchByCategory(creds, opts, { categoriesAction, streamsAction, normalize, label, keepCategory }) {
  const raw = await call(creds, categoriesAction, {}, { timeout: opts.timeout });
  if (!Array.isArray(raw)) throw new Error(`${categoriesAction} did not return a list`);

  let categories = raw.map(normalizeCategory).filter((c) => c.id);
  const total = categories.length;
  if (keepCategory) categories = categories.filter((c) => keepCategory(c));

  log(opts, `  ${label}: ${categories.length}/${total} categories`);

  let done = 0;
  const perCategory = await pool(categories, opts.concurrency, async (category) => {
    let items = [];
    try {
      const list = await call(creds, streamsAction, { category_id: category.id }, { timeout: opts.timeout });
      items = Array.isArray(list) ? list.map(normalize) : [];
    } catch (err) {
      log(opts, `  ! ${label} category "${category.name}": ${redact(err.message, creds.password)}`);
    }
    done++;
    if (!opts.quiet && done % 25 === 0) {
      process.stderr.write(`    … ${done}/${categories.length} categories\r`);
    }
    return { category, items };
  });

  return perCategory;
}

async function exportLive(creds, opts, out) {
  const groups = await fetchByCategory(creds, opts, {
    categoriesAction: 'get_live_categories',
    streamsAction: 'get_live_streams',
    normalize: normalizeChannel,
    label: 'live',
    // Country filtering only makes sense for live: panels group channels by
    // country but organise VOD by genre.
    keepCategory: opts.countries.length ? (c) => matchesCountry(c.name, opts.countries) : null,
  });

  const m3u = opts.formats.includes('m3u') ? sink(path.join(out, 'live.m3u')) : null;
  const txt = opts.formats.includes('txt') ? sink(path.join(out, 'live.txt')) : null;
  const json = opts.formats.includes('json') ? sink(path.join(out, 'live.json')) : null;

  await m3u?.write('#EXTM3U\n');
  await txt?.write('# name\tcategory\tarchive_days\turl\n');
  await json?.write('[\n');

  let count = 0;
  const byCategory = {};
  const missedCountries = new Set(opts.countries);

  for (const { category, items } of groups) {
    if (!items.length) continue;
    byCategory[category.name] = items.length;
    for (const code of opts.countries) {
      if (matchesCountry(category.name, [code])) missedCountries.delete(code);
    }

    for (const channel of items) {
      const [url] = liveUrls(creds, channel.streamId);
      await m3u?.write(
        `#EXTINF:-1 tvg-id="${attr(channel.epgChannelId)}" tvg-name="${attr(channel.name)}"` +
          ` tvg-logo="${attr(channel.logo)}" group-title="${attr(category.name)}"` +
          (channel.tvArchive ? ` catchup="default" catchup-days="${channel.archiveDays}"` : '') +
          `,${channel.name}\n${url}\n`,
      );
      await txt?.write(
        `${tsv(channel.name)}\t${tsv(category.name)}\t${channel.tvArchive ? channel.archiveDays : 0}\t${url}\n`,
      );
      await json?.write(
        `${count ? ',\n' : ''}${JSON.stringify({ ...channel, category: category.name, url })}`,
      );
      count++;
    }
  }

  await json?.write('\n]\n');
  for (const s of [m3u, txt, json]) await s?.close();

  return { count, byCategory, missedCountries: [...missedCountries] };
}

async function exportMovies(creds, opts, out) {
  const groups = await fetchByCategory(creds, opts, {
    categoriesAction: 'get_vod_categories',
    streamsAction: 'get_vod_streams',
    normalize: normalizeMovie,
    label: 'movies',
  });

  const m3u = opts.formats.includes('m3u') ? sink(path.join(out, 'movies.m3u')) : null;
  const txt = opts.formats.includes('txt') ? sink(path.join(out, 'movies.txt')) : null;
  const json = opts.formats.includes('json') ? sink(path.join(out, 'movies.json')) : null;

  await m3u?.write('#EXTM3U\n');
  await txt?.write('# title\tyear\tcategory\trating\turl\n');
  await json?.write('[\n');

  let count = 0;
  const seen = new Set();
  const byCategory = {};

  for (const { category, items } of groups) {
    let kept = 0;
    for (const movie of items) {
      // The same title is listed under several categories on most panels.
      if (!movie.streamId || seen.has(movie.streamId)) continue;
      seen.add(movie.streamId);

      const [url] = movieUrls(creds, movie.streamId, movie.ext);
      await m3u?.write(
        `#EXTINF:-1 tvg-name="${attr(movie.name)}" tvg-logo="${attr(movie.poster)}"` +
          ` group-title="${attr(category.name)}",${movie.name}\n${url}\n`,
      );
      await txt?.write(
        `${tsv(movie.name)}\t${movie.year ?? ''}\t${tsv(category.name)}\t${movie.rating || ''}\t${url}\n`,
      );
      await json?.write(
        `${count ? ',\n' : ''}${JSON.stringify({ ...movie, category: category.name, url })}`,
      );
      count++;
      kept++;
    }
    if (kept) byCategory[category.name] = kept;
  }

  await json?.write('\n]\n');
  for (const s of [m3u, txt, json]) await s?.close();

  return { count, byCategory };
}

async function exportSeries(creds, opts, out) {
  const groups = await fetchByCategory(creds, opts, {
    categoriesAction: 'get_series_categories',
    streamsAction: 'get_series',
    normalize: normalizeSeries,
    label: 'series',
  });

  const txt = opts.formats.includes('txt') ? sink(path.join(out, 'series.txt')) : null;
  const json = opts.formats.includes('json') ? sink(path.join(out, 'series.json')) : null;

  await txt?.write('# title\tyear\tcategory\trating\tseries_id\n');
  await json?.write('[\n');

  let count = 0;
  const seen = new Set();
  const byCategory = {};
  const unique = [];

  for (const { category, items } of groups) {
    let kept = 0;
    for (const series of items) {
      if (!series.seriesId || seen.has(series.seriesId)) continue;
      seen.add(series.seriesId);

      unique.push({ ...series, category: category.name });
      await txt?.write(
        `${tsv(series.name)}\t${series.year ?? ''}\t${tsv(category.name)}\t${series.rating || ''}\t${series.seriesId}\n`,
      );
      await json?.write(
        `${count ? ',\n' : ''}${JSON.stringify({ ...series, category: category.name })}`,
      );
      count++;
      kept++;
    }
    if (kept) byCategory[category.name] = kept;
  }

  await json?.write('\n]\n');
  for (const s of [txt, json]) await s?.close();

  let episodes = 0;
  if (opts.episodes && unique.length) {
    episodes = await exportEpisodes(creds, opts, out, unique);
  }

  return { count, byCategory, episodes };
}

/**
 * Episodes need one `get_series_info` call per series — 75k requests at the
 * catalog sizes this tool is built for, hence opt-in and separately limited.
 */
async function exportEpisodes(creds, opts, out, seriesList) {
  const target = opts.episodesLimit > 0 ? seriesList.slice(0, opts.episodesLimit) : seriesList;
  log(opts, `  episodes: expanding ${target.length} series (one request each)…`);

  const m3u = opts.formats.includes('m3u') ? sink(path.join(out, 'episodes.m3u')) : null;
  const txt = opts.formats.includes('txt') ? sink(path.join(out, 'episodes.txt')) : null;
  await m3u?.write('#EXTM3U\n');
  await txt?.write('# series\tseason\tepisode\ttitle\turl\n');

  let count = 0;
  let done = 0;
  let failed = 0;

  // Writes are serialised through this queue so concurrent fetches cannot
  // interleave half-written lines.
  let queue = Promise.resolve();
  const enqueue = (fn) => {
    queue = queue.then(fn);
    return queue;
  };

  await pool(target, DEFAULTS.episodeConcurrency, async (series) => {
    try {
      const info = await call(creds, 'get_series_info', { series_id: series.seriesId }, { timeout: opts.timeout });
      const list = flattenEpisodes(info, series);

      await enqueue(async () => {
        for (const ep of list) {
          const [url] = episodeUrls(creds, ep.episodeId, ep.ext);
          const label = `${ep.seriesName} S${String(ep.season).padStart(2, '0')}E${String(ep.episode).padStart(2, '0')} — ${ep.title}`;
          await m3u?.write(
            `#EXTINF:-1 tvg-name="${attr(label)}" group-title="${attr(series.category)}",${label}\n${url}\n`,
          );
          await txt?.write(
            `${tsv(ep.seriesName)}\t${ep.season}\t${ep.episode}\t${tsv(ep.title)}\t${url}\n`,
          );
          count++;
        }
      });
    } catch {
      failed++;
    }
    done++;
    if (!opts.quiet && done % 100 === 0) {
      process.stderr.write(`    … ${done}/${target.length} series, ${count} episodes\r`);
    }
  });

  await queue;
  for (const s of [m3u, txt]) await s?.close();
  if (failed) log(opts, `  ! ${failed} series returned no episode data`);

  return count;
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

/** Did we get roughly what the caller expected? */
function checkExpectation(label, actual, expected, tolerancePct) {
  if (!expected) return null;
  const floor = Math.floor(expected * (1 - tolerancePct / 100));
  return {
    label,
    expected,
    actual,
    floor,
    met: actual >= floor,
    shortfall: actual >= floor ? 0 : expected - actual,
  };
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

  if (opts.envFile) await loadEnvFile(opts.envFile, opts);

  if (!opts.server || !opts.username || !opts.password) {
    console.error(
      'Missing credentials. Set XTREAM_SERVER, XTREAM_USERNAME and XTREAM_PASSWORD ' +
        '(or pass --server/--username/--password, or --env-file).\n\n' +
        HELP,
    );
    process.exit(2);
  }

  const creds = { server: opts.server, username: opts.username, password: opts.password };
  const startedAt = Date.now();

  log(opts, `Authenticating against ${opts.server}…`);
  const auth = await login(creds, { timeout: opts.timeout });
  if (!auth.ok) {
    console.error(`Panel refused the line: ${auth.error}`);
    process.exit(1);
  }
  log(
    opts,
    `  ✓ line active` +
      (auth.info?.exp_date ? `, expires ${new Date(Number(auth.info.exp_date) * 1000).toISOString().slice(0, 10)}` : '') +
      (auth.info?.max_connections ? `, ${auth.info.max_connections} connection(s)` : ''),
  );

  const out = path.resolve(process.cwd(), opts.out);
  await mkdir(out, { recursive: true });

  const report = {
    startedAt: new Date().toISOString(),
    server: opts.server,
    countries: opts.countries,
    sections: opts.sections,
    totals: {},
  };

  if (opts.sections.includes('live')) {
    const live = await exportLive(creds, opts, out);
    report.totals.live = live.count;
    report.liveByCategory = live.byCategory;
    log(opts, `  ✓ live: ${live.count} channels`);
    if (live.missedCountries.length) {
      log(opts, `  ! no live categories matched: ${live.missedCountries.join(', ')}`);
      report.missedCountries = live.missedCountries;
    }
  }

  if (opts.sections.includes('vod')) {
    const movies = await exportMovies(creds, opts, out);
    report.totals.movies = movies.count;
    report.moviesByCategory = movies.byCategory;
    log(opts, `  ✓ movies: ${movies.count}`);
  }

  if (opts.sections.includes('series')) {
    const series = await exportSeries(creds, opts, out);
    report.totals.series = series.count;
    report.totals.episodes = series.episodes;
    report.seriesByCategory = series.byCategory;
    log(opts, `  ✓ series: ${series.count}${series.episodes ? ` (${series.episodes} episodes)` : ''}`);
  }

  const expectations = [
    checkExpectation('movies', report.totals.movies ?? 0, opts.expectMovies, opts.tolerance),
    checkExpectation('series', report.totals.series ?? 0, opts.expectSeries, opts.tolerance),
  ].filter(Boolean);

  report.expectations = expectations;
  report.durationMs = Date.now() - startedAt;

  // The report holds counts and category names only — no stream URLs, so no
  // credentials — and is written verbatim rather than passed through redact().
  await writeFile(path.join(out, 'catalog.report.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  const summary = Object.entries(report.totals)
    .map(([k, v]) => `${v.toLocaleString('en-US')} ${k}`)
    .join(', ');
  console.log(`${summary} → ${path.relative(process.cwd(), out)}/`);

  let failed = false;
  for (const e of expectations) {
    if (e.met) {
      console.log(`  ✓ ${e.label}: ${e.actual.toLocaleString('en-US')} (expected ~${e.expected.toLocaleString('en-US')})`);
    } else {
      failed = true;
      console.error(
        `  ✗ ${e.label}: ${e.actual.toLocaleString('en-US')} — ${e.shortfall.toLocaleString('en-US')} short of ~${e.expected.toLocaleString('en-US')} ` +
          `(floor ${e.floor.toLocaleString('en-US')} at ${opts.tolerance}% tolerance). ` +
          `The line may be on a package that does not carry the full catalog.`,
      );
    }
  }

  console.error(
    '\nThese files contain your line credentials in every stream URL. ' +
      `Keep ${path.relative(process.cwd(), out)}/ out of version control.`,
  );

  if (failed && opts.strict) process.exit(1);
}

main().catch((err) => {
  console.error(redact(err?.stack ?? String(err), process.env.XTREAM_PASSWORD ?? ''));
  process.exit(1);
});
