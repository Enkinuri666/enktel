#!/usr/bin/env node
/**
 * Fill in the missing `tvg-id` on a playlist by matching channel names against
 * EPG guides.
 *
 *   node scripts/match-epg.mjs --playlist data/playlists/enktel-core.m3u
 *   node scripts/match-epg.mjs --guide https://example.com/epg.xml.gz
 *   node scripts/match-epg.mjs --playlist my.m3u --threshold 0.9 --overwrite
 *
 * A channel with no `tvg-id` gets no programme data in any player, however
 * good the guide is — the two are only connected by that id. Roughly a third
 * of what the scraper collects arrives without one.
 *
 * Two id namespaces are used, and both are cheap:
 *   • the iptv-org channel catalog (41k channels with names, alternate names
 *     and countries) — the namespace iptv-org's own `tvg-id`s come from;
 *   • any XMLTV guide passed with --guide, read only as far as its channel
 *     list, so even a 200 MB guide costs a few hundred KB.
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

import { parsePlaylist, toM3u, toText } from './lib/m3u.mjs';
import { loadGuideIndex } from './lib/guide-sources.mjs';
import { matchChannel } from './lib/xmltv.mjs';

const HELP = `
Fill in missing tvg-id values by matching channel names against EPG guides.

Usage: node scripts/match-epg.mjs [options]

Input
  --playlist <file>    playlist to annotate (default data/playlists/enktel-core.m3u)
  --guide <url>        XMLTV guide to match against (repeatable, .gz supported)
  --no-catalog         skip the iptv-org channel catalog

Matching
  --threshold <0..1>   fuzzy-match cutoff (default 0.9)
  --overwrite          re-match channels that already have a tvg-id

Output
  --out <dir>          default alongside the playlist
  --prefix <name>      default the playlist's own name
  --quiet
`.trim();

function parseArgs(argv) {
  const opts = {
    playlist: 'data/playlists/enktel-core.m3u',
    guides: [],
    catalog: true,
    threshold: 0.9,
    overwrite: false,
    out: null,
    prefix: null,
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
      case '--playlist':
        opts.playlist = next();
        break;
      case '--guide':
        opts.guides.push(next());
        break;
      case '--no-catalog':
        opts.catalog = false;
        break;
      case '--threshold':
        opts.threshold = Number(next());
        break;
      case '--overwrite':
        opts.overwrite = true;
        break;
      case '--out':
        opts.out = next();
        break;
      case '--prefix':
        opts.prefix = next();
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
  const body = await readFile(playlistPath, 'utf8').catch(() => {
    console.error(`Cannot read playlist: ${opts.playlist}`);
    process.exit(2);
  });

  const { header, entries } = parsePlaylist(body);
  log(opts, `${entries.length} channels in ${opts.playlist}`);

  const { index, sources } = await loadGuideIndex({
    guides: opts.guides,
    catalog: opts.catalog,
    onLog: (msg) => log(opts, msg),
  });

  if (!index.size) {
    console.error('No guide channels loaded — nothing to match against.');
    process.exit(1);
  }

  log(opts, `Matching against ${index.size} guide channels…`);

  const stats = { already: 0, exact: 0, fuzzy: 0, unmatched: 0 };
  const unmatched = [];
  const matched = [];
  let done = 0;

  for (const entry of entries) {
    if (entry.tvgId && !opts.overwrite) {
      stats.already++;
      continue;
    }

    const match = matchChannel(entry, index, { threshold: opts.threshold });

    if (match) {
      entry.tvgId = match.id;
      matched.push({
        via: match.via,
        score: match.score,
        name: entry.name,
        country: entry.tvgCountry,
        id: match.id,
      });
      stats[match.via]++;
    } else {
      stats.unmatched++;
      unmatched.push(entry);
    }

    if (!opts.quiet && ++done % 250 === 0) {
      process.stderr.write(`  … ${done} matched so far\r`);
    }
  }

  const outDir = opts.out ? path.resolve(process.cwd(), opts.out) : path.dirname(playlistPath);
  const prefix = opts.prefix ?? path.basename(playlistPath).replace(/\.m3u8?$/i, '');
  await mkdir(outDir, { recursive: true });

  const base = path.join(outDir, prefix);
  await writeFile(`${base}.epg.m3u`, toM3u(entries, { epgUrl: header['x-tvg-url'] ?? '' }), 'utf8');
  await writeFile(`${base}.epg-unmatched.txt`, toText(unmatched), 'utf8');

  // Every id this run assigned, so a wrong one can be found and argued with
  // rather than discovered by a viewer looking at the wrong programme.
  const auditRows = matched.map((m) =>
    [m.via, m.score.toFixed(3), m.name, m.country || '?', m.id].join('\t'),
  );
  await writeFile(
    `${base}.epg-matches.txt`,
    `${['# via\tscore\tchannel\tcountry\tassigned_tvg_id', ...auditRows].join('\n')}\n`,
    'utf8',
  );

  const report = {
    playlist: opts.playlist,
    generatedAt: new Date().toISOString(),
    guideChannels: index.size,
    sources,
    threshold: opts.threshold,
    totals: { channels: entries.length, ...stats },
    coverage: `${(((entries.length - stats.unmatched) / (entries.length || 1)) * 100).toFixed(1)}%`,
  };
  await writeFile(`${base}.epg-report.json`, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  console.log(
    `${stats.exact} exact + ${stats.fuzzy} fuzzy matched, ${stats.already} already had an id, ` +
      `${stats.unmatched} unmatched (${report.coverage} coverage) → ` +
      `${path.relative(process.cwd(), base)}.epg.m3u`,
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
