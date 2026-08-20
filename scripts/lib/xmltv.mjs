/**
 * XMLTV channel extraction and channel-name matching.
 *
 * Used to fill in the `tvg-id` that most scraped playlists leave empty. A
 * channel without one gets no programme data in any player, so a guide that
 * covers it is worthless until the two are connected by name.
 *
 * Only the `<channel>` block at the head of a guide is of interest — the
 * `<programme>` entries after it are where the megabytes are, and none of
 * them matter here.
 */
import { inferCountry, normalizeCountry } from './countries.mjs';

/**
 * Cut a guide off at the first programme.
 *
 * XMLTV puts every `<channel>` before every `<programme>`, so this discards
 * the bulk of the file without losing anything. A 200 MB guide has its
 * channel list in the first few hundred KB.
 *
 * @param {string} xml
 * @returns {string}
 */
export function channelBlock(xml) {
  const text = String(xml ?? '');
  const cut = text.indexOf('<programme');
  return cut === -1 ? text : text.slice(0, cut);
}

const CHANNEL_RE = /<channel\b[^>]*\bid\s*=\s*"([^"]*)"[^>]*>([\s\S]*?)<\/channel>/gi;
const DISPLAY_NAME_RE = /<display-name\b[^>]*>([\s\S]*?)<\/display-name>/gi;
const ICON_RE = /<icon\b[^>]*\bsrc\s*=\s*"([^"]*)"/i;

/**
 * Parse `<channel>` elements into id + every name they go by.
 *
 * @param {string} xml a full guide or a `channelBlock()` of one
 * @returns {Array<{id: string, names: string[], icon: string}>}
 */
export function extractChannels(xml) {
  const out = [];

  for (const m of channelBlock(xml).matchAll(CHANNEL_RE)) {
    const id = decodeXml(m[1]).trim();
    if (!id) continue;

    const body = m[2];
    const names = [];
    for (const n of body.matchAll(DISPLAY_NAME_RE)) {
      const name = decodeXml(n[1]).replace(/\s+/g, ' ').trim();
      if (name && !names.includes(name)) names.push(name);
    }

    out.push({ id, names, icon: ICON_RE.exec(body)?.[1] ?? '' });
  }

  return out;
}

function decodeXml(value) {
  return String(value ?? '')
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
    .replace(/&amp;/g, '&');
}

// ---- Name normalisation ---------------------------------------------------

/** Quality and status noise that says nothing about which channel this is. */
const NOISE =
  /\b(fhd|uhd|hd|sd|4k|8k|1080p?|720p?|576p?|480p?|360p?|240p?|hevc|h ?265|h ?264|raw|backup|alt|multi|geo ?blocked|not ?24 ?7|24 ?7|live|tv|channel)\b/g;

/**
 * Reduce a channel name to something two feeds might agree on.
 *
 * Strips bracketed annotations (`(1080p)`, `[Not 24/7]`), accents, quality
 * markers and punctuation. "HRT 1 HD (1080p) [Geo-blocked]" and "HRT1" both
 * come out as "hrt 1".
 *
 * @param {string} name
 * @returns {string}
 */
export function normalizeName(name) {
  let text = String(name ?? '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');

  text = text
    .replace(/\([^)]*\)/g, ' ')
    .replace(/\[[^\]]*\]/g, ' ')
    .replace(/[^a-z0-9+]+/g, ' ')
    .replace(NOISE, ' ')
    // "hrt1" and "hrt 1" must land in the same place.
    .replace(/([a-z])(\d)/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim();

  return text;
}

/** @returns {string[]} */
export function nameTokens(name) {
  const normalized = normalizeName(name);
  return normalized ? normalized.split(' ') : [];
}

/**
 * Dice coefficient over name tokens: 1 for identical, 0 for nothing shared.
 *
 * Token-based rather than character-based so "BBC One" scores well against
 * "One BBC" but poorly against "BBC Two" — word order is noise, word identity
 * is not.
 *
 * @returns {number} 0..1
 */
export function similarity(a, b) {
  const left = new Set(nameTokens(a));
  const right = new Set(nameTokens(b));
  if (!left.size || !right.size) return 0;

  let shared = 0;
  for (const token of left) if (right.has(token)) shared++;
  return (2 * shared) / (left.size + right.size);
}

// ---- Index and matching ---------------------------------------------------

/**
 * The country an EPG id declares, from the suffix convention both iptv-org
 * (`BBCOne.uk`) and the common rippers (`HTV1.HD.hr`) use.
 *
 * Run through the same folding the playlist side uses, so a `.uk` id and a
 * `tvg-country="GB"` channel are recognised as the same country instead of
 * disqualifying each other.
 *
 * @param {string} id
 * @returns {string} ISO-3166 alpha-2, or ''
 */
export function countryFromId(id) {
  const m = /\.([a-z]{2})$/i.exec(String(id ?? '').trim());
  return m ? normalizeCountry(m[1]) : '';
}

/**
 * Build a lookup over guide channels.
 *
 * @param {Array<{id: string, names: string[], country?: string, source?: string}>} channels
 */
export function buildIndex(channels) {
  /** @type {Map<string, Array<object>>} exact normalized name → candidates */
  const byName = new Map();
  /**
   * token → candidates containing it. Without this the fuzzy pass compares
   * every channel against all 41k catalog entries; two names that share no
   * word cannot score above any useful threshold, so only the ones reachable
   * through a shared token are worth scoring at all.
   * @type {Map<string, Array<object>>}
   */
  const byToken = new Map();
  const all = [];

  for (const channel of channels) {
    const country = normalizeCountry(channel.country) || countryFromId(channel.id);
    // Tokens are computed once here rather than on every comparison.
    const tokenSets = channel.names.map((name) => new Set(nameTokens(name)));
    const record = {
      id: channel.id,
      names: channel.names,
      tokenSets,
      country,
      source: channel.source ?? '',
    };
    all.push(record);

    for (const name of channel.names) {
      const key = normalizeName(name);
      if (!key) continue;
      const bucket = byName.get(key);
      if (bucket) bucket.push(record);
      else byName.set(key, [record]);
    }

    const seen = new Set();
    for (const tokens of tokenSets) {
      for (const token of tokens) {
        if (seen.has(token)) continue;
        seen.add(token);
        const bucket = byToken.get(token);
        if (bucket) bucket.push(record);
        else byToken.set(token, [record]);
      }
    }
  }

  return { byName, byToken, all, size: all.length };
}

/** Dice coefficient over two prepared token sets. */
function diceSets(left, right) {
  if (!left.size || !right.size) return 0;
  const [small, large] = left.size <= right.size ? [left, right] : [right, left];
  let shared = 0;
  for (const token of small) if (large.has(token)) shared++;
  return (2 * shared) / (left.size + right.size);
}

/**
 * Is a word that appears on only one side enough to make these different
 * channels?
 *
 * Dice treats every word as equally informative, which is how "Hell's Kitchen
 * Germany" scores 0.86 against "Hell's Kitchen" and takes a US guide, and how
 * "Plus Belle la Vie 2" lands on "Plus Belle la Vie". A number or a place name
 * is never incidental in a channel name — it is the whole distinction — so a
 * difference in one disqualifies the pair however well the rest scores.
 */
function isDiscriminator(token) {
  return /^\d+$/.test(token) || Boolean(inferCountry(token));
}

/** Tokens on exactly one side of the pair. */
function symmetricDifference(left, right) {
  const out = [];
  for (const token of left) if (!right.has(token)) out.push(token);
  for (const token of right) if (!left.has(token)) out.push(token);
  return out;
}

/**
 * Find the guide id for a channel.
 *
 * Exact normalized-name hits are taken first; only if none exists does it fall
 * back to scoring every candidate, which is the expensive path. A country hint
 * breaks ties in both cases — three feeds carry a "Sport 1" and they are not
 * the same channel.
 *
 * @param {{name: string, tvgName?: string, tvgCountry?: string}} entry
 * @param {ReturnType<typeof buildIndex>} index
 * @param {{threshold?: number}} [opts]
 * @returns {{id: string, score: number, via: 'exact'|'fuzzy', country: string}|null}
 */
export function matchChannel(entry, index, opts = {}) {
  // 0.9 rather than a looser cut because a wrong tvg-id is worse than a
  // missing one: the viewer gets confidently incorrect programme data instead
  // of an obviously empty guide.
  const { threshold = 0.9 } = opts;
  const country = (entry.tvgCountry || '').toUpperCase();
  const candidateNames = [entry.name, entry.tvgName].filter(Boolean);

  for (const name of candidateNames) {
    const bucket = index.byName.get(normalizeName(name));
    if (!bucket?.length) continue;
    const pick = preferCountry(bucket, country);
    return { id: pick.id, score: 1, via: 'exact', country: pick.country };
  }

  // Only candidates sharing at least one word can clear the threshold, so the
  // inverted index decides who gets scored. Falls back to a full sweep for an
  // index built without one.
  const entryTokenSets = candidateNames.map((name) => new Set(nameTokens(name)));
  const candidates = index.byToken ? gatherCandidates(index, entryTokenSets) : index.all;

  let best = null;
  for (const candidate of candidates) {
    // A country mismatch is disqualifying when both sides claim one: a fuzzy
    // name match across countries is how "Sport 1" ends up with a Croatian
    // guide on a German channel.
    if (country && candidate.country && candidate.country !== country) continue;

    const candidateTokenSets =
      candidate.tokenSets ?? candidate.names.map((name) => new Set(nameTokens(name)));

    for (const candidateTokens of candidateTokenSets) {
      for (const entryTokens of entryTokenSets) {
        const score = diceSets(entryTokens, candidateTokens);
        if (score < threshold || (best && score <= best.score)) continue;
        if (symmetricDifference(entryTokens, candidateTokens).some(isDiscriminator)) continue;
        best = { id: candidate.id, score, via: 'fuzzy', country: candidate.country };
      }
    }
  }

  return best;
}

/** Every candidate that shares a word with any of the entry's names. */
function gatherCandidates(index, entryTokenSets) {
  const out = new Set();
  for (const tokens of entryTokenSets) {
    for (const token of tokens) {
      const bucket = index.byToken.get(token);
      if (bucket) for (const record of bucket) out.add(record);
    }
  }
  return out;
}

function preferCountry(candidates, country) {
  if (!country) return candidates[0];
  return candidates.find((c) => c.country === country) ?? candidates[0];
}
