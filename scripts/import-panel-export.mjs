#!/usr/bin/env node
/**
 * Import a panel's own M3U export into credential-free catalogs.
 *
 *   node scripts/import-panel-export.mjs --input export.m3u
 *
 * A panel's "export playlist" button hands you every line the account can see
 * — live, movies and every episode of every series — with the metadata already
 * attached: tvg-logo, tvg-ID, group-title, channel numbers. That metadata is
 * the valuable part and it is safe to keep.
 *
 * **The URLs are not.** Every one of them embeds the line's username and
 * password in its path, so an export is a credential in playlist form: commit
 * it, or bundle it into an APK anyone can unzip, and the account is public.
 * This reads the export, keeps the metadata and the stream *id*, and discards
 * the credentials at the parse step — `splitStreamUrl` matches them precisely
 * so that it can drop them, and never returns them. Rendering a playable
 * playlist is `build-roster.mjs`'s job, from credentials supplied at run time.
 *
 * Series are collapsed to one row per series rather than one per episode. A
 * quarter of a million episode rows is not a catalog anyone browses, and every
 * episode of a series shares its poster, its group and its stream prefix; the
 * episode count, season count and id range are kept so nothing is lost.
 */
import { createReadStream } from 'node:fs';
import { createInterface } from 'node:readline';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

import { parseExtInf } from './lib/m3u.mjs';

const DEFAULTS = { out: 'data/rosters', prefix: 'enktel-line' };

const HELP = `
Import a panel M3U export into credential-free catalogs.

Usage: node scripts/import-panel-export.mjs --input <file> [options]

  --input <file>     the panel's exported .m3u (required)
  --out <dir>        default ${DEFAULTS.out}
  --prefix <name>    default ${DEFAULTS.prefix}
  --keep-adult       keep the adult categories (dropped by default)
  --quiet
`.trim();

/**
 * Split an Xtream stream URL into the parts that are safe to keep.
 *
 * The two shapes a panel serves:
 *   /{kind}/{username}/{password}/{id}.{ext}   live, movie, series
 *   /{username}/{password}/{id}                the legacy live form
 *
 * Returns the host, the kind and the id. The credential segments are matched
 * so that they can be *discarded* — they are deliberately not returned, and
 * nothing downstream can reach them.
 *
 * @param {string} url
 * @returns {{host: string, kind: string, id: string, ext: string}|null}
 */
export function splitStreamUrl(url) {
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return null;
  }

  const segments = parsed.pathname.split('/').filter(Boolean);
  const host = parsed.host;

  const split = (file) => {
    const dot = file.lastIndexOf('.');
    return dot === -1 ? { id: file, ext: '' } : { id: file.slice(0, dot), ext: file.slice(dot + 1) };
  };

  // /{kind}/{user}/{pass}/{id}.{ext}
  if (segments.length === 4 && /^(live|movie|series)$/i.test(segments[0])) {
    return { host, kind: segments[0].toLowerCase(), ...split(segments[3]) };
  }

  // /{user}/{pass}/{id} — the legacy live form, with no kind in the path.
  if (segments.length === 3) {
    return { host, kind: 'live', ...split(segments[2]) };
  }

  return null;
}

/** `Show Name S01 E02 Episode Title` → the show, the season and the episode. */
const EPISODE_RE = /^(.*?)\s+S(\d{1,3})\s*E(\d{1,4})\b(.*)$/i;

/**
 * @param {string} name
 * @returns {{series: string, season: number, episode: number}|null}
 */
export function splitEpisode(name) {
  const m = EPISODE_RE.exec(String(name ?? '').trim());
  if (!m) return null;
  const series = m[1].trim();
  if (!series) return null;
  return { series, season: Number(m[2]), episode: Number(m[3]) };
}

/**
 * Categories a general-audience storefront should not surface by default.
 *
 * Dropped rather than bucketed: an adult category reaching a lineup by
 * accident is a different class of mistake from a mis-filed sports channel,
 * and the scraper already defaults the same way (`--include-nsfw`).
 */
const ADULT_RE = /\b(xxx|adult|porn|erotic|18\+)\b/i;

/** @param {string} group */
export function isAdult(group) {
  return ADULT_RE.test(String(group ?? ''));
}

/**
 * A panel writes a placeholder id when it has no guide for a channel.
 *
 * It is not an id. Carried forward it produces a channel that claims EPG
 * coverage in every count and shows an empty grid on screen.
 *
 * @param {string} id
 */
export function realEpgId(id) {
  const text = String(id ?? '').trim();
  return /^dummy(\.epg)?$/i.test(text) ? '' : text;
}

function parseArgs(argv) {
  const opts = {
    input: '',
    out: DEFAULTS.out,
    prefix: DEFAULTS.prefix,
    adult: false,
    quiet: false,
    help: false,
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
      case '--input':
        opts.input = next();
        break;
      case '--out':
        opts.out = next();
        break;
      case '--prefix':
        opts.prefix = next();
        break;
      case '--keep-adult':
        opts.adult = true;
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

const csvCell = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
const csvRow = (values) => values.map(csvCell).join(',');

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
  if (!opts.input) {
    console.error(`--input is required.\n\n${HELP}`);
    process.exit(2);
  }

  const log = (...args) => {
    if (!opts.quiet) console.error(...args);
  };

  const live = [];
  const movies = [];
  /** @type {Map<string, object>} series key → one collapsed row */
  const series = new Map();
  const stats = { entries: 0, live: 0, movies: 0, episodes: 0, adultDropped: 0, unparsable: 0 };
  const hosts = new Set();
  const groups = new Map();

  let pending = null;
  const rl = createInterface({
    input: createReadStream(path.resolve(process.cwd(), opts.input), { encoding: 'utf8' }),
    crlfDelay: Infinity,
  });

  for await (const raw of rl) {
    const line = raw.trim();
    if (!line) continue;

    if (line.startsWith('#EXTINF')) {
      pending = parseExtInf(line);
      continue;
    }
    if (line.startsWith('#')) continue;
    if (!pending) continue;

    const attrs = pending.attrs ?? {};
    const group = attrs['group-title'] ?? '';
    const name = pending.title || attrs['tvg-name'] || '';
    pending = null;

    stats.entries++;

    if (!opts.adult && isAdult(group)) {
      stats.adultDropped++;
      continue;
    }

    const stream = splitStreamUrl(line);
    if (!stream) {
      stats.unparsable++;
      continue;
    }

    hosts.add(stream.host);
    groups.set(group, (groups.get(group) ?? 0) + 1);
    const logo = attrs['tvg-logo'] ?? '';

    if (stream.kind === 'live') {
      stats.live++;
      live.push({
        id: stream.id,
        name,
        group,
        tvgId: realEpgId(attrs['tvg-id']),
        tvgLogo: logo,
        chno: attrs['tvg-chno'] ?? '',
        ext: stream.ext,
      });
      continue;
    }

    if (stream.kind === 'movie') {
      stats.movies++;
      movies.push({ id: stream.id, name, group, tvgLogo: logo, ext: stream.ext });
      continue;
    }

    stats.episodes++;
    const parts = splitEpisode(name);
    const title = parts ? parts.series : name;
    // A separator no channel name can contain, so a title ending in the
    // next one’s group cannot collide with it.
    const key = `${title}\u0000${group}`;
    const row = series.get(key);

    if (row) {
      row.episodes++;
      if (parts) row.seasons.add(parts.season);
      if (Number(stream.id) < Number(row.firstId)) row.firstId = stream.id;
      if (Number(stream.id) > Number(row.lastId)) row.lastId = stream.id;
      if (!row.tvgLogo && logo) row.tvgLogo = logo;
    } else {
      series.set(key, {
        name: title,
        group,
        tvgLogo: logo,
        episodes: 1,
        seasons: new Set(parts ? [parts.season] : []),
        firstId: stream.id,
        lastId: stream.id,
        ext: stream.ext,
      });
    }
  }

  const outDir = path.resolve(process.cwd(), opts.out);
  await mkdir(outDir, { recursive: true });
  const base = path.join(outDir, opts.prefix);

  const write = (suffix, header, rows) =>
    writeFile(`${base}.${suffix}`, `${[csvRow(header), ...rows].join('\n')}\n`, 'utf8');

  await write(
    'live.csv',
    ['stream_id', 'chno', 'name', 'group', 'tvg_id', 'tvg_logo', 'ext'],
    live.map((e) => csvRow([e.id, e.chno, e.name, e.group, e.tvgId, e.tvgLogo, e.ext])),
  );

  await write(
    'movies.csv',
    ['stream_id', 'name', 'group', 'poster', 'ext'],
    movies.map((e) => csvRow([e.id, e.name, e.group, e.tvgLogo, e.ext])),
  );

  const seriesRows = [...series.values()].sort((a, b) => a.name.localeCompare(b.name));
  await write(
    'series.csv',
    ['name', 'group', 'seasons', 'episodes', 'first_stream_id', 'last_stream_id', 'poster', 'ext'],
    seriesRows.map((s) =>
      csvRow([s.name, s.group, s.seasons.size || 1, s.episodes, s.firstId, s.lastId, s.tvgLogo, s.ext]),
    ),
  );

  // A roster the resolver can pick up directly, so the live side can have its
  // missing ids and logos filled without a second parse of a 400 MB export.
  // `strictCountry` is false: these countries come from the panel's group
  // titles, which is the same weak evidence a scraped playlist's tvg-country
  // is, not the curated statement a hand-written roster's country is.
  await writeFile(
    `${base}.live.roster.json`,
    `${JSON.stringify(
      {
        id: `${opts.prefix}-live`,
        name: `${opts.prefix} — live channels`,
        server: '',
        template: '{server}/live/{username}/{password}/{id}.m3u8',
        strictCountry: false,
        channels: live.map((e) => ({
          id: e.id,
          name: e.name,
          group: e.group,
          tvgId: e.tvgId,
          tvgLogo: e.tvgLogo,
          chno: e.chno,
        })),
      },
      null,
      1,
    )}\n`,
    'utf8',
  );

  const report = {
    generatedAt: new Date().toISOString(),
    input: path.basename(opts.input),
    hosts: [...hosts],
    totals: {
      ...stats,
      series: seriesRows.length,
      liveWithEpgId: live.filter((e) => e.tvgId).length,
      liveWithLogo: live.filter((e) => e.tvgLogo).length,
      moviesWithPoster: movies.filter((e) => e.tvgLogo).length,
      seriesWithPoster: seriesRows.filter((s) => s.tvgLogo).length,
    },
    groups: Object.fromEntries([...groups].sort((a, b) => b[1] - a[1])),
  };
  await writeFile(`${base}.import-report.json`, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  console.log(
    `${stats.entries} entries → live ${stats.live}, movies ${stats.movies}, ` +
      `series ${seriesRows.length} (${stats.episodes} episodes)` +
      (stats.adultDropped ? `, ${stats.adultDropped} adult dropped` : ''),
  );
  log(`  EPG ids on live: ${report.totals.liveWithEpgId}/${stats.live}`);
  log(
    `  artwork: live ${report.totals.liveWithLogo}, movies ${report.totals.moviesWithPoster}, ` +
      `series ${report.totals.seriesWithPoster}`,
  );
  log(`  → ${path.relative(process.cwd(), outDir)}/${opts.prefix}.{live,movies,series}.csv`);
}

// Only run when invoked directly, so the parsers above stay unit-testable.
if (process.argv[1] && import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
