#!/usr/bin/env node
/**
 * Turn a scraped playlist into a storefront lineup: genre buckets per country,
 * every channel carrying an EPG id and a logo.
 *
 *   node scripts/build-lineup.mjs
 *   node scripts/build-lineup.mjs --countries AU,US,GB,HR --genres Sports,News
 *   node scripts/build-lineup.mjs --guide https://example.com/epg.xml.gz
 *
 * Three things happen to every channel, in order:
 *   1. a missing `tvg-id` is matched against the guide indexes (same matcher
 *      as match-epg.mjs);
 *   2. a missing `tvg-logo` is filled from the iptv-org logo set, keyed by the
 *      id resolved in step 1 — which is why the order matters;
 *   3. its genre is classified and the group title becomes "<CC> - <Genre>".
 *
 * Channels are then sorted by country (in the order given), then genre, then
 * name. A channel no genre rule matches falls into a default bucket rather
 * than being silently dropped — see `--default-genre`.
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

import { parsePlaylist, toCsv, toM3u, toText } from './lib/m3u.mjs';
import { countryOf, normalizeCountry } from './lib/countries.mjs';
import { GENRES, classifyGenre, lineupGroup, lineupSorter } from './lib/genres.mjs';
import { fetchLogos, loadGuideIndex } from './lib/guide-sources.mjs';
import { matchChannel } from './lib/xmltv.mjs';

const DEFAULTS = {
  playlist: 'data/playlists/enktel-core.m3u',
  out: 'data/playlists',
  prefix: 'enktel-lineup',
  countries: ['AU', 'US', 'GB', 'HR'],
  threshold: 0.9,
};

const HELP = `
Build a genre-grouped, country-sorted lineup with EPG ids and logos.

Usage: node scripts/build-lineup.mjs [options]

Input
  --playlist <file>    source playlist (default ${DEFAULTS.playlist})
  --guide <url>        extra XMLTV guide for id matching (repeatable)
  --no-catalog         skip the iptv-org channel catalog
  --no-logos           skip logo backfill

Selection
  --countries A,B      countries to include, in lineup order
                       (default ${DEFAULTS.countries.join(',')})
  --genres A,B         only these genres (default: all)
  --default-genre <g>  bucket for channels no rule matches (default Entertainment)
  --drop-unclassified  drop them instead of bucketing them

Matching
  --threshold <0..1>   fuzzy id-match cutoff (default ${DEFAULTS.threshold})

Output
  --out <dir>          default ${DEFAULTS.out}
  --prefix <name>      default ${DEFAULTS.prefix}
  --split              also write one .m3u per country
  --quiet

Genres: ${GENRES.join(', ')}
`.trim();

function parseArgs(argv) {
  const opts = {
    playlist: DEFAULTS.playlist,
    guides: [],
    catalog: true,
    logos: true,
    countries: [],
    genres: [],
    defaultGenre: 'Entertainment',
    threshold: DEFAULTS.threshold,
    out: DEFAULTS.out,
    prefix: DEFAULTS.prefix,
    split: false,
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
      case '--countries':
      case '--country':
        opts.countries.push(...list(next()).map(normalizeCountry).filter(Boolean));
        break;
      case '--genres':
        opts.genres.push(...list(next()));
        break;
      case '--default-genre':
        opts.defaultGenre = next();
        break;
      case '--drop-unclassified':
        opts.defaultGenre = '';
        break;
      case '--threshold':
        opts.threshold = Number(next());
        break;
      case '--out':
        opts.out = next();
        break;
      case '--prefix':
        opts.prefix = next();
        break;
      case '--split':
        opts.split = true;
        break;
      case '--quiet':
        opts.quiet = true;
        break;
      default:
        throw new Error(`Unknown flag: ${arg}`);
    }
  }

  if (!opts.countries.length) opts.countries = [...DEFAULTS.countries];
  return opts;
}

const log = (opts, ...args) => {
  if (!opts.quiet) console.error(...args);
};

function countBy(items, keyOf) {
  const counts = {};
  for (const item of items) {
    const key = keyOf(item);
    counts[key] = (counts[key] ?? 0) + 1;
  }
  return counts;
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

  const playlistPath = path.resolve(process.cwd(), opts.playlist);
  let body;
  try {
    body = await readFile(playlistPath, 'utf8');
  } catch {
    console.error(`Cannot read playlist: ${opts.playlist}`);
    process.exit(2);
  }

  const { header, entries } = parsePlaylist(body);
  log(opts, `${entries.length} channels in ${opts.playlist}`);

  const { index, sources } = await loadGuideIndex({
    guides: opts.guides,
    catalog: opts.catalog,
    onLog: (msg) => log(opts, msg),
  });

  let logos = new Map();
  if (opts.logos) {
    try {
      logos = await fetchLogos();
      log(opts, `  ✓ logos: ${logos.size} channels`);
    } catch (err) {
      log(opts, `  ✗ logos: ${err.message}`);
    }
  }

  const stats = {
    idAlready: 0,
    idMatched: 0,
    idMissing: 0,
    logoAlready: 0,
    logoFilled: 0,
    logoMissing: 0,
    unclassified: 0,
    defaulted: 0,
    droppedCountry: 0,
    droppedGenre: 0,
  };

  const wanted = new Set(opts.countries);
  const wantedGenres = opts.genres.length ? new Set(opts.genres) : null;
  const lineup = [];

  for (const entry of entries) {
    entry.tvgCountry = countryOf(entry);
    if (!wanted.has(entry.tvgCountry)) {
      stats.droppedCountry++;
      continue;
    }

    // 1. EPG id.
    if (entry.tvgId) {
      stats.idAlready++;
    } else {
      const match = index.size ? matchChannel(entry, index, { threshold: opts.threshold }) : null;
      if (match) {
        entry.tvgId = match.id;
        stats.idMatched++;
      } else {
        stats.idMissing++;
      }
    }

    // 2. Logo, keyed by the id resolved above.
    if (entry.tvgLogo) {
      stats.logoAlready++;
    } else if (entry.tvgId && logos.has(entry.tvgId)) {
      entry.tvgLogo = logos.get(entry.tvgId);
      stats.logoFilled++;
    } else {
      stats.logoMissing++;
    }

    // 3. Genre. A general-interest channel matches no rule, and there are
    // hundreds of them — dropping those would quietly delete a third of the
    // lineup, so they fall into the default bucket and are counted separately.
    entry.genre = classifyGenre(entry);
    if (!entry.genre) {
      stats.unclassified++;
      if (!opts.defaultGenre) {
        stats.droppedGenre++;
        continue;
      }
      entry.genre = opts.defaultGenre;
      stats.defaulted++;
    }

    if (wantedGenres && !wantedGenres.has(entry.genre)) {
      stats.droppedGenre++;
      continue;
    }

    entry.group = lineupGroup(entry.tvgCountry, entry.genre);
    lineup.push(entry);
  }

  lineup.sort(lineupSorter(opts.countries));

  const outDir = path.resolve(process.cwd(), opts.out);
  await mkdir(outDir, { recursive: true });
  const base = path.join(outDir, opts.prefix);
  const written = [];

  const epgUrl = header['x-tvg-url'] ?? '';
  await writeFile(`${base}.m3u`, toM3u(lineup, { epgUrl }), 'utf8');
  await writeFile(`${base}.txt`, toText(lineup), 'utf8');
  await writeFile(`${base}.csv`, toCsv(lineup), 'utf8');
  written.push(`${opts.prefix}.m3u`, `${opts.prefix}.txt`, `${opts.prefix}.csv`);

  if (opts.split) {
    for (const country of opts.countries) {
      const subset = lineup.filter((e) => e.tvgCountry === country);
      if (!subset.length) continue;
      const file = `${base}.${country.toLowerCase()}.m3u`;
      await writeFile(file, toM3u(subset, { epgUrl }), 'utf8');
      written.push(path.basename(file));
    }
  }

  const report = {
    generatedAt: new Date().toISOString(),
    playlist: opts.playlist,
    countries: opts.countries,
    genres: opts.genres.length ? opts.genres : GENRES,
    guideChannels: index.size,
    guideSources: sources,
    epgUrl: epgUrl || null,
    totals: {
      input: entries.length,
      lineup: lineup.length,
      ...stats,
      epgCoverage: pct(lineup.filter((e) => e.tvgId).length, lineup.length),
      logoCoverage: pct(lineup.filter((e) => e.tvgLogo).length, lineup.length),
    },
    byCountry: countBy(lineup, (e) => e.tvgCountry),
    byGroup: countBy(lineup, (e) => e.group),
  };

  await writeFile(`${base}.report.json`, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  written.push(`${opts.prefix}.report.json`);

  console.log(
    `${lineup.length} channels — EPG ${report.totals.epgCoverage}, logos ${report.totals.logoCoverage} → ${path.relative(process.cwd(), outDir)}/{${written.join(', ')}}`,
  );

  if (!opts.quiet) {
    for (const country of opts.countries) {
      const groups = Object.entries(report.byGroup)
        .filter(([g]) => g.startsWith(`${country} - `))
        .map(([g, n]) => `${g.slice(country.length + 3)} ${n}`)
        .join(', ');
      log(opts, `  ${country}: ${report.byCountry[country] ?? 0} — ${groups || 'none'}`);
    }
  }
}

function pct(part, whole) {
  return `${((part / (whole || 1)) * 100).toFixed(1)}%`;
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
