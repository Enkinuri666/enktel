#!/usr/bin/env node
/**
 * Find a public, credential-free stream for each channel on a panel line.
 *
 *   node scripts/match-upstream.mjs \
 *     --roster data/rosters/enktel-line.live.csv \
 *     --public data/playlists/enktel-scrape.m3u
 *
 * A panel URL is `/live/{user}/{pass}/{id}.m3u8` and says nothing whatever
 * about where the panel got the stream. The origin is server-side
 * configuration; it is not encoded in the URL, not exposed by `player_api.php`,
 * and not derivable by any amount of parsing. So this does not "extract" an
 * upstream — nothing can.
 *
 * What it does instead is the question worth answering: **is this same channel
 * published free somewhere?** Panel channels are joined to the verified-alive
 * public index by EPG id first, then by normalised name, and the ones that hit
 * come out as a playlist that needs no account.
 *
 * Expect the yield to be low, and expect that to be correct. A paid line's
 * value is exactly the channels nobody publishes free — the premium sport, the
 * movie networks, the VOD. A high match rate here would mean the matcher was
 * lying.
 *
 * Precision over recall, for the reason the EPG matcher gives: a wrong stream
 * is worse than a missing one. A viewer who gets an obviously absent channel
 * looks elsewhere; a viewer who gets a *different* channel under the right name
 * files a bug against the wrong thing.
 */
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

import { parsePlaylist, toM3u } from './lib/m3u.mjs';
import { normalizeName } from './lib/xmltv.mjs';

const DEFAULTS = {
  roster: 'data/rosters/enktel-line.live.csv',
  public: 'data/playlists/enktel-scrape.m3u',
  out: 'data/rosters',
  prefix: 'enktel-line-public',
};

const HELP = `
Find public streams for the channels on a panel line.

Usage: node scripts/match-upstream.mjs [options]

  --roster <file>   credential-free line catalog (default ${DEFAULTS.roster})
  --public <file>   verified public playlist (default ${DEFAULTS.public})
  --out <dir>       default ${DEFAULTS.out}
  --prefix <name>   default ${DEFAULTS.prefix}
  --quiet
`.trim();

/**
 * Fold an EPG id to something two namespaces can agree on.
 *
 * Panels and the open indexes carry the same channel under ids that differ
 * only in case and in iptv-org's feed suffix — `7flix.au` against
 * `7Flix.au@SD`. Comparing them raw matched 3 channels out of 18,154; folding
 * both sides first matched 353, and every one of those is the same channel by
 * construction, since the id is an identifier rather than a description.
 *
 * @param {string} id
 * @returns {string}
 */
export function foldEpgId(id) {
  return String(id ?? '')
    .toLowerCase()
    .split('@')[0]
    .trim();
}

/** Minimal RFC-4180 row reader — the catalogs quote every cell. */
export function parseCsvRow(row) {
  const out = [];
  let cell = '';
  let quoted = false;

  for (let i = 0; i < row.length; i++) {
    const ch = row[i];
    if (quoted) {
      if (ch === '"') {
        if (row[i + 1] === '"') {
          cell += '"';
          i++;
        } else {
          quoted = false;
        }
      } else {
        cell += ch;
      }
    } else if (ch === '"') {
      quoted = true;
    } else if (ch === ',') {
      out.push(cell);
      cell = '';
    } else {
      cell += ch;
    }
  }

  out.push(cell);
  return out;
}

/**
 * Index the public playlist by folded id and by normalised name.
 *
 * First occurrence wins on both: the public index is already de-duplicated and
 * liveness-checked, so an earlier entry is no worse than a later one.
 *
 * @param {Array<object>} entries
 */
export function indexPublic(entries) {
  const byId = new Map();
  const byName = new Map();

  for (const entry of entries) {
    if (!entry.url) continue;
    const id = foldEpgId(entry.tvgId);
    if (id && !byId.has(id)) byId.set(id, entry);
    const name = normalizeName(entry.name);
    if (name && !byName.has(name)) byName.set(name, entry);
  }

  return { byId, byName };
}

/**
 * @param {{tvgId: string, name: string}} channel
 * @param {ReturnType<typeof indexPublic>} index
 * @returns {{entry: object, via: 'id'|'name'}|null}
 */
export function findPublic(channel, index) {
  const id = foldEpgId(channel.tvgId);
  if (id) {
    const hit = index.byId.get(id);
    // An id match is an identity match; nothing else needs to agree.
    if (hit) return { entry: hit, via: 'id' };
  }

  const name = normalizeName(channel.name);
  if (name) {
    const hit = index.byName.get(name);
    if (hit) return { entry: hit, via: 'name' };
  }

  return null;
}

function parseArgs(argv) {
  const opts = { ...DEFAULTS, quiet: false, help: false };
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
      case '--public':
        opts.public = next();
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

  const log = (...args) => {
    if (!opts.quiet) console.error(...args);
  };

  const rosterCsv = await readFile(path.resolve(process.cwd(), opts.roster), 'utf8');
  const rows = rosterCsv.split(/\r?\n/).slice(1).filter((r) => r.trim());
  // stream_id, chno, name, group, tvg_id, tvg_logo, ext
  const roster = rows.map((r) => {
    const f = parseCsvRow(r);
    return { streamId: f[0], chno: f[1], name: f[2], group: f[3], tvgId: f[4], tvgLogo: f[5] };
  });

  const publicBody = await readFile(path.resolve(process.cwd(), opts.public), 'utf8');
  const { entries: publicEntries } = parsePlaylist(publicBody);
  const index = indexPublic(publicEntries);

  log(`${roster.length} line channels against ${publicEntries.length} public streams`);
  log(`  public index: ${index.byId.size} ids, ${index.byName.size} names`);

  const matched = [];
  const stats = { viaId: 0, viaName: 0, unmatched: 0 };

  for (const channel of roster) {
    const hit = findPublic(channel, index);
    if (!hit) {
      stats.unmatched++;
      continue;
    }
    stats[hit.via === 'id' ? 'viaId' : 'viaName']++;
    matched.push({
      // The line's own presentation — its name, artwork and grouping — over a
      // stream that anyone can open.
      name: channel.name,
      url: hit.entry.url,
      group: channel.group,
      tvgId: channel.tvgId || hit.entry.tvgId,
      tvgName: channel.name,
      tvgLogo: channel.tvgLogo || hit.entry.tvgLogo,
      tvgCountry: hit.entry.tvgCountry,
      tvgLanguage: hit.entry.tvgLanguage,
      http: hit.entry.http,
      matchedVia: hit.via,
      publicName: hit.entry.name,
    });
  }

  const outDir = path.resolve(process.cwd(), opts.out);
  await mkdir(outDir, { recursive: true });
  const base = path.join(outDir, opts.prefix);

  await writeFile(`${base}.m3u`, toM3u(matched), 'utf8');
  await writeFile(
    `${base}.txt`,
    `${['# line name\tmatched via\tpublic name\turl', ...matched.map((m) =>
      [m.name, m.matchedVia, m.publicName, m.url].join('\t'),
    )].join('\n')}\n`,
    'utf8',
  );

  const report = {
    generatedAt: new Date().toISOString(),
    roster: path.basename(opts.roster),
    publicIndex: path.basename(opts.public),
    totals: {
      lineChannels: roster.length,
      publicStreams: publicEntries.length,
      matched: matched.length,
      ...stats,
      coverage: `${((matched.length / (roster.length || 1)) * 100).toFixed(1)}%`,
    },
    note:
      'A panel stream URL does not encode its upstream, so nothing here is "the ' +
      'origin of" a panel URL. These are independent public streams of the same ' +
      'channels. A low coverage figure is the expected result: the channels a ' +
      'line is paid for are the ones nobody publishes free.',
  };
  await writeFile(`${base}.report.json`, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  console.log(
    `${matched.length} of ${roster.length} line channels have a public stream ` +
      `(${report.totals.coverage}) — ${stats.viaId} by id, ${stats.viaName} by name`,
  );
  log(`  → ${path.relative(process.cwd(), outDir)}/${opts.prefix}.{m3u,txt,report.json}`);
}

if (process.argv[1] && import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
