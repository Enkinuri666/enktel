#!/usr/bin/env node
/**
 * A second and third source for every channel that has one.
 *
 * Relaying answers a block by asking from another country. It cannot answer a
 * block no available country satisfies — a Croatian broadcaster is not served
 * from Washington or London — and it cannot answer a host that is simply down.
 * The other move is to stop asking that host at all and play the same channel
 * from somewhere else entirely, which works regardless of where the viewer is.
 *
 * Two sources are merged, both keyed on the channel's EPG id:
 *
 *  - **The lineup's own duplicates.** 211 ids appear more than once in the
 *    published playlist — FailArmy seventeen times — because the scrape found
 *    the same channel on several hosts. Today those are separate rows in the
 *    channel list, which is clutter that happens to be exactly the data this
 *    needs.
 *  - **iptv-org's stream index**, the same project the logos and guide ids
 *    come from. 16,822 streams, of which the ones sharing an id with a lineup
 *    channel are alternates by definition.
 *
 * Measured against the current lineup: 1,041 of 2,303 channels (45.2%) gain at
 * least one alternate. Three of the twelve Croatian channels do — HRT 1, 2 and
 * 3, which are precisely the ones a US or UK relay cannot help.
 *
 *   node scripts/build-alternates.mjs
 */

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname } from "node:path";

const PLAYLIST = "public/playlists/enktel-lineup.m3u";
/**
 * URLs displaced from the primary slot by scripts/merge-channels.mjs.
 *
 * When a supplied list replaces a channel's URL, the one it replaced is not
 * discarded — it was working for somebody. These come first among the
 * alternates, ahead of anything iptv-org contributes, because this project has
 * actually served them.
 */
const SEED = "data/playlists/enktel-alternates.seed.json";
const OUT = "data/playlists/enktel-alternates.json";
const PUBLIC_OUT = "public/playlists/enktel-alternates.json";
const STREAMS = "https://raw.githubusercontent.com/iptv-org/api/gh-pages/streams.json";

/**
 * How many alternates to keep per channel.
 *
 * Every device downloads this file on every sync, so it is a size budget, not
 * a quality one. Three sources means two things have to fail before a viewer
 * sees an error, and past that the returns are small next to the bytes.
 */
const MAX_ALTERNATES = 3;

/** iptv-org ids carry a feed suffix the lineup's do not: `Nova.hr@SD`. */
const fold = (id) => (id || "").split("@")[0].trim().toLowerCase();

function parseLineup(text) {
  const rows = [];
  let id = null;
  for (const raw of text.split("\n")) {
    const line = raw.trim();
    if (line.startsWith("#EXTINF")) {
      const m = /tvg-id="([^"]*)"/.exec(line);
      id = m ? m[1] : "";
    } else if (/^https?:\/\//i.test(line) && id !== null) {
      if (id.trim()) rows.push({ id, url: line });
      id = null;
    }
  }
  return rows;
}

async function iptvOrgStreams() {
  const res = await fetch(STREAMS, { signal: AbortSignal.timeout(120_000) });
  if (!res.ok) throw new Error(`iptv-org streams.json: HTTP ${res.status}`);
  return res.json();
}

const rows = parseLineup(readFileSync(PLAYLIST, "utf8"));

let seed = {};
try {
  seed = JSON.parse(readFileSync(SEED, "utf8"));
} catch {
  // Absent until a list has been merged, which is the normal state.
}

// What the lineup already publishes, in order, per channel.
const own = new Map();
for (const { id, url } of rows) {
  if (!own.has(id)) own.set(id, []);
  own.get(id).push(url);
}

const upstream = new Map();
for (const s of await iptvOrgStreams()) {
  const c = fold(s.channel);
  if (!c || !s.url) continue;
  if (!upstream.has(c)) upstream.set(c, []);
  upstream.get(c).push(s.url);
}

const out = {};
let channels = 0;
let urls = 0;
for (const [id, mine] of own) {
  const primary = mine[0];
  const pool = [];
  // Displaced primaries first — this project published them until a moment ago.
  for (const u of seed[id] ?? []) if (u !== primary && !pool.includes(u)) pool.push(u);
  // Then the lineup's own duplicates: verified alive by this project's own
  // checker, where iptv-org's are only claimed.
  for (const u of mine.slice(1)) if (!pool.includes(u)) pool.push(u);
  for (const u of upstream.get(fold(id)) ?? []) {
    if (u !== primary && !pool.includes(u)) pool.push(u);
  }
  const kept = pool.slice(0, MAX_ALTERNATES);
  if (kept.length) {
    out[id] = kept;
    channels += 1;
    urls += kept.length;
  }
}

const body = JSON.stringify(out);
for (const target of [OUT, PUBLIC_OUT]) {
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, body);
}

const pct = ((channels / own.size) * 100).toFixed(1);
console.log(
  `${channels} of ${own.size} channels (${pct}%) have an alternate; ` +
    `${urls} URLs, ${(body.length / 1024).toFixed(0)} KB -> ${PUBLIC_OUT}`,
);
