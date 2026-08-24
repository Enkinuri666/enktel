#!/usr/bin/env node
/**
 * Fold a supplied playlist into the published lineup.
 *
 * The lineup is normally generated end to end by the scraper, which verifies
 * every stream before publishing it. That is the right pipeline and this does
 * not replace it — it is for the case where somebody hands over a list of
 * channels they already know work, usually for a region the scrape covers
 * thinly. The Croatian set is the example: twelve channels, three of them the
 * same broadcaster on a host that answers nobody outside the country.
 *
 * A supplied URL wins the primary slot. Whoever sent it has tested it from
 * where they are, which is more than this project can say about a scraped one,
 * and the URL being replaced is kept as an alternate rather than dropped — the
 * failover chain will reach for it if the new one ever stops.
 *
 *   node scripts/merge-channels.mjs --input <file.m3u> [--country HR] [--dry]
 */

import { readFileSync, writeFileSync } from "node:fs";
import process from "node:process";

import { parsePlaylist, toM3u, normalizeUrl } from "./lib/m3u.mjs";
import { classifyGenre, lineupGroup } from "./lib/genres.mjs";
import { cleanName, cleanUrl, groupCountry, matchKey, normalizeCc } from "./lib/merge.mjs";

const LINEUP = "public/playlists/enktel-lineup.m3u";
const DATA_LINEUP = "data/playlists/enktel-lineup.m3u";

const args = process.argv.slice(2);
const opt = (name, fallback = null) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : fallback;
};
const has = (name) => args.includes(`--${name}`);

const inputPath = opt("input");
if (!inputPath) {
  console.error("usage: node scripts/merge-channels.mjs --input <file.m3u> [--country HR] [--dry]");
  process.exit(2);
}
const defaultCountry = (opt("country", "HR") || "HR").toUpperCase();

const supplied = parsePlaylist(readFileSync(inputPath, "utf8"));
const lineup = parsePlaylist(readFileSync(LINEUP, "utf8"));

// ---- normalise what was supplied ------------------------------------

const seenUrl = new Set();
const seenName = new Map();
const incoming = [];

for (const e of supplied.entries) {
  const url = cleanUrl(e.url);
  if (!url) continue;
  const key = normalizeUrl(url);
  if (seenUrl.has(key)) continue;
  seenUrl.add(key);

  const name = String(e.name || "").trim();
  const nk = matchKey(name);
  if (!nk) continue;

  // The same channel twice, on different hosts, is one channel with a spare.
  if (seenName.has(nk)) {
    seenName.get(nk).alternates.push(url);
    continue;
  }

  // `tvgCountry`, which is what parsePlaylist calls it — reading
  // `attrs["tvg-country"]` silently returned undefined and filed every
  // Serbian and Bosnian channel under Croatia.
  const country = normalizeCc(e.tvgCountry, defaultCountry);

  const row = {
    name,
    url,
    tvgId: e.tvgId || "",
    logo: e.tvgLogo || "",
    country,
    // The supplied groups are the sender's own scheme — "Hrvatska",
    // "Glazbeni", "Lokalni". The lineup's is `CC - Genre`, which is what the
    // local-first sort and the whole channel list are built around, so the
    // genre is re-derived rather than carried over.
    // Classified on the name with its tags removed. "[Not 24/7]" is a note
    // about uptime, and feeding it in whole put four live local stations into
    // "24/7 Series" — on the strength of the very tag that says they are not.
    genre: classifyGenre({ name: cleanName(name), group: e.group || "" }),
    alternates: [],
  };
  seenName.set(nk, row);
  incoming.push(row);
}

// ---- merge ------------------------------------------------------------

/**
 * Keyed by country *and* name.
 *
 * The second lock on the problem above: a channel name is only unique inside
 * one country's section, and the lineup carries 2,303 of them across four.
 */
const byKey = new Map();
lineup.entries.forEach((e, i) => {
  const cc = groupCountry(e.group);
  const k = matchKey(e.name);
  if (k && cc && !byKey.has(`${cc}|${k}`)) byKey.set(`${cc}|${k}`, i);
});

let updated = 0;
let added = 0;
const demoted = new Map(); // tvg-id -> URLs displaced from the primary slot

for (const row of incoming) {
  const k = `${row.country}|${matchKey(row.name)}`;
  const at = byKey.get(k);

  if (at !== undefined) {
    const existing = lineup.entries[at];
    if (normalizeUrl(existing.url) !== normalizeUrl(row.url)) {
      // The one that was there is not wrong, merely unverified from where the
      // sender is. It goes to the back of the queue rather than the bin.
      const id = existing.tvgId || row.tvgId;
      if (id) demoted.set(id, [...(demoted.get(id) ?? []), existing.url]);
      existing.url = row.url;
      updated += 1;
    }
    // Ids and logos are only ever filled in, never overwritten: the lineup's
    // came from iptv-org's index and the supplied ones are mostly blank.
    if (!existing.tvgId && row.tvgId) existing.tvgId = row.tvgId;
    if (!existing.logo && row.logo) existing.logo = row.logo;
    if (row.alternates.length && (existing.tvgId || row.tvgId)) {
      const id = existing.tvgId || row.tvgId;
      demoted.set(id, [...(demoted.get(id) ?? []), ...row.alternates]);
    }
  } else {
    lineup.entries.push({
      name: row.name,
      url: row.url,
      tvgId: row.tvgId,
      logo: row.logo,
      group: lineupGroup(row.country, row.genre),
      chno: 0,
      radio: false,
    });
    added += 1;
    if (row.alternates.length && row.tvgId) {
      demoted.set(row.tvgId, [...(demoted.get(row.tvgId) ?? []), ...row.alternates]);
    }
  }
}

const summary = {
  supplied: supplied.entries.length,
  afterDedupe: incoming.length,
  updated,
  added,
  displacedUrls: [...demoted.values()].reduce((n, v) => n + v.length, 0),
  lineupTotal: lineup.entries.length,
};
console.log(JSON.stringify(summary, null, 2));

if (has("dry")) process.exit(0);

const rendered = toM3u(lineup.entries, { epgUrl: lineup.epgUrl });
for (const target of [LINEUP, DATA_LINEUP]) {
  try {
    writeFileSync(target, rendered);
    console.log(`wrote ${target}`);
  } catch (err) {
    console.warn(`skipped ${target}: ${err.message}`);
  }
}

// Hand the displaced URLs to the alternates builder, which merges them with
// what iptv-org knows rather than replacing it.
writeFileSync(
  "data/playlists/enktel-alternates.seed.json",
  JSON.stringify(Object.fromEntries(demoted), null, 2),
);
console.log(`wrote data/playlists/enktel-alternates.seed.json (${demoted.size} channels)`);
