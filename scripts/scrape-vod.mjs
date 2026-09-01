#!/usr/bin/env node
/**
 * VOD scraper — public-domain and openly-licensed films, documentaries and
 * series, written out as a playlist the player already knows how to import.
 *
 * The on-demand counterpart to `scrape-m3u8.mjs`. That one collects live
 * channels from public free-to-air indexes; this one collects individual
 * titles from the Internet Archive, and takes them only where the item carries
 * a licence that permits redistribution. See `lib/vod-sources.mjs` for the
 * licence rule and why it is an allowlist.
 *
 *   node scripts/scrape-vod.mjs                       # everything, both languages
 *   node scripts/scrape-vod.mjs --lang exyu           # HR / SRB / BIH only
 *   node scripts/scrape-vod.mjs --kind movies --limit 200
 *   node scripts/scrape-vod.mjs --allow-noncommercial # see the licence note
 *
 * Writes `data/playlists/<prefix>.m3u` plus a `.report.json` beside it.
 */
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import {
  KINDS,
  LANGUAGE_GROUPS,
  buildQuery,
  downloadUrl,
  isRedistributable,
  pickPlayableFile,
} from './lib/vod-sources.mjs';

const UA = 'EnktelVodScraper/1.0 (+https://enktel.tv)';
const SEARCH = 'https://archive.org/advancedsearch.php';
const METADATA = 'https://archive.org/metadata';

const DEFAULTS = {
  kinds: Object.keys(KINDS),
  langs: Object.keys(LANGUAGE_GROUPS),
  limit: 300,
  concurrency: 6,
  prefix: 'enktel-vod',
  outDir: 'data/playlists',
  allowNonCommercial: false,
  quiet: false,
};

const HELP = `
scrape-vod.mjs — public-domain / openly-licensed VOD

  --kind <a,b>            ${Object.keys(KINDS).join(', ')} (default: all)
  --lang <a,b>            ${Object.keys(LANGUAGE_GROUPS).join(', ')} (default: all)
  --limit <n>             max titles per kind+language pair (default ${DEFAULTS.limit})
  --concurrency <n>       parallel metadata lookups (default ${DEFAULTS.concurrency})
  --prefix <name>         output basename (default ${DEFAULTS.prefix})
  --out <dir>             output directory (default ${DEFAULTS.outDir})
  --allow-noncommercial   include CC BY-NC* items (off by default — see lib notes)
  --quiet
  --help
`;

function parseArgs(argv) {
  const o = { ...DEFAULTS };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i];
    switch (a) {
      case '--kind': o.kinds = next().split(',').map((s) => s.trim()).filter(Boolean); break;
      case '--lang': o.langs = next().split(',').map((s) => s.trim()).filter(Boolean); break;
      case '--limit': o.limit = Number(next()); break;
      case '--concurrency': o.concurrency = Number(next()); break;
      case '--prefix': o.prefix = next(); break;
      case '--out': o.outDir = next(); break;
      case '--allow-noncommercial': o.allowNonCommercial = true; break;
      case '--quiet': o.quiet = true; break;
      case '--help': case '-h': console.log(HELP); process.exit(0); break;
      default: throw new Error(`Unknown option: ${a}`);
    }
  }
  for (const k of o.kinds) if (!KINDS[k]) throw new Error(`Unknown kind: ${k}`);
  for (const l of o.langs) if (!LANGUAGE_GROUPS[l]) throw new Error(`Unknown language: ${l}`);
  if (!Number.isFinite(o.limit) || o.limit < 1) throw new Error('--limit must be a positive number');
  return o;
}

const log = (o, ...a) => { if (!o.quiet) console.error(...a); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getJson(url, { retries = 3 } = {}) {
  let lastErr;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': UA, Accept: 'application/json' },
        signal: AbortSignal.timeout(30_000),
      });
      // 429 and 5xx are worth waiting out; the Archive throttles bursts.
      if (res.status === 429 || res.status >= 500) throw new Error(`HTTP ${res.status}`);
      if (!res.ok) return null;
      return await res.json();
    } catch (err) {
      lastErr = err;
      if (attempt < retries) await sleep(800 * 2 ** attempt);
    }
  }
  throw lastErr;
}

/** Run `worker` over `items` with a bounded number in flight. */
async function pool(items, limit, worker) {
  const out = [];
  let i = 0;
  const runners = Array.from({ length: Math.max(1, limit) }, async () => {
    while (i < items.length) {
      const idx = i++;
      out[idx] = await worker(items[idx], idx);
    }
  });
  await Promise.all(runners);
  return out;
}

/** One page of search results. */
async function search(query, { rows, page }) {
  const p = new URLSearchParams();
  p.set('q', query);
  for (const f of ['identifier', 'title', 'year', 'description', 'licenseurl', 'language', 'subject']) {
    p.append('fl[]', f);
  }
  p.set('rows', String(rows));
  p.set('page', String(page));
  p.set('output', 'json');
  const json = await getJson(`${SEARCH}?${p}`);
  return json?.response?.docs ?? [];
}

const first = (v) => (Array.isArray(v) ? v[0] : v);

/** Everything the playlist needs about one title, or null if it is unusable. */
async function resolve(doc, kind, lang, opts) {
  const meta = await getJson(`${METADATA}/${encodeURIComponent(doc.identifier)}`);
  if (!meta) return null;

  // Re-check the licence against the item's own metadata rather than trusting
  // the search index alone: the two can disagree, and the item record is the
  // authority.
  const licence = first(meta.metadata?.licenseurl) ?? first(doc.licenseurl);
  if (!isRedistributable(licence, { allowNonCommercial: opts.allowNonCommercial })) return null;

  const file = pickPlayableFile(meta.files);
  if (!file) return null;

  const title = String(first(meta.metadata?.title) ?? first(doc.title) ?? doc.identifier).trim();
  const year = String(first(doc.year) ?? '').slice(0, 4);
  return {
    id: doc.identifier,
    title: year ? `${title} (${year})` : title,
    url: downloadUrl(doc.identifier, file.name),
    group: `${KINDS[kind].group} — ${LANGUAGE_GROUPS[lang].label}`,
    logo: `https://archive.org/services/img/${encodeURIComponent(doc.identifier)}`,
    licence,
    kind,
    lang,
  };
}

/** M3U escaping: the attribute block is quoted, the display name is not. */
const attr = (s) => String(s).replace(/"/g, "'");
const line = (s) => String(s).replace(/[\r\n]+/g, ' ').trim();

function toM3U(entries) {
  const out = ['#EXTM3U'];
  for (const e of entries) {
    out.push(
      `#EXTINF:-1 tvg-id="" tvg-name="${attr(e.title)}" tvg-logo="${attr(e.logo)}" group-title="${attr(e.group)}",${line(e.title)}`,
    );
    out.push(e.url);
  }
  return `${out.join('\n')}\n`;
}

async function main() {
  let opts;
  try {
    opts = parseArgs(process.argv.slice(2));
  } catch (err) {
    console.error(String(err.message || err));
    console.error(HELP);
    process.exit(2);
  }

  const seen = new Set();
  const entries = [];
  const report = { ranAt: new Date().toISOString(), pairs: [], skipped: { noLicence: 0, noFile: 0 } };

  for (const kind of opts.kinds) {
    for (const lang of opts.langs) {
      const query = buildQuery(kind, lang, { allowNonCommercial: opts.allowNonCommercial });
      log(opts, `→ ${kind} / ${lang}`);

      const docs = [];
      const perPage = 100;
      for (let page = 1; docs.length < opts.limit; page++) {
        const batch = await search(query, { rows: Math.min(perPage, opts.limit - docs.length), page });
        if (batch.length === 0) break;
        docs.push(...batch);
        if (batch.length < perPage) break;
      }

      const resolved = await pool(docs, opts.concurrency, (d) => resolve(d, kind, lang, opts).catch(() => null));
      let kept = 0;
      for (const e of resolved) {
        if (!e) { report.skipped.noFile++; continue; }
        // The same film is often in several collections; keep the first.
        if (seen.has(e.id)) continue;
        seen.add(e.id);
        entries.push(e);
        kept++;
      }
      report.pairs.push({ kind, lang, found: docs.length, kept });
      log(opts, `  ${docs.length} found, ${kept} usable`);
    }
  }

  entries.sort((a, b) => a.group.localeCompare(b.group) || a.title.localeCompare(b.title));

  await mkdir(opts.outDir, { recursive: true });
  const base = `${opts.outDir}/${opts.prefix}`;

  // A run that collects nothing is a normal outcome here — the licence and
  // collection rules are strict, and for some language groups the Archive
  // genuinely holds nothing that passes them. What is not acceptable is
  // quietly replacing a good playlist with an empty one, so say what happened
  // and leave whatever is on disk alone.
  if (entries.length === 0) {
    console.error(
      `Nothing passed the licence and collection rules for ` +
        `kind=${opts.kinds.join(',')} lang=${opts.langs.join(',')}. ` +
        `${base}.m3u left untouched.`,
    );
    process.exit(3);
  }

  await writeFile(`${base}.m3u`, toM3U(entries), 'utf8');
  report.total = entries.length;
  report.byGroup = entries.reduce((acc, e) => ({ ...acc, [e.group]: (acc[e.group] || 0) + 1 }), {});
  await writeFile(`${base}.report.json`, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  log(opts, `\n${entries.length} titles → ${base}.m3u`);
  for (const [g, n] of Object.entries(report.byGroup)) log(opts, `  ${n.toString().padStart(5)}  ${g}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
