/**
 * Deciding when a supplied channel is one the lineup already carries.
 *
 * Its own module because getting this wrong is quiet and expensive: a false
 * match overwrites a working channel's URL with an unrelated stream, and
 * nothing downstream notices — the lineup still parses, the build still passes,
 * and a viewer picks Bravo and gets Croatian music videos.
 */

/**
 * A channel's name with the aggregator's annotations taken off.
 *
 * These lists append picture quality and an uptime note to the title —
 * "OTV (720p) [Not 24/7]" — and both are commentary about the stream rather
 * than part of what the channel is called. Feeding the whole thing to the
 * genre classifier put four live local stations into "24/7 Series", on the
 * strength of the very tag that says they are not.
 */
export function cleanName(name) {
  return String(name || '')
    .replace(/\([^)]*\)/g, ' ')
    .replace(/\[[^\]]*\]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * What two lists have to agree on before one channel counts as the other.
 *
 * Deliberately **not** `normalizeName`, which is the EPG matcher's key and
 * drops "TV" as a noise word. That is right there — a guide calls "Nova" and
 * "Nova TV" one channel — and wrong here, where it collapsed two pairs that
 * are nothing of the kind:
 *
 *   "Bravo! TV" (Croatian music)  -> "bravo" -> "Bravo"    (US - Reality)
 *   "TV Nova"   (a local station) -> "nova"  -> "Nova TV"  (the broadcaster)
 *
 * So word order and the word "TV" both survive, and only the quality and
 * uptime tags are stripped. `@` folds to `a` because Croatian broadcasters
 * stylise it that way — Nov@ TV is Nova TV, and that one *should* match.
 */
export function matchKey(name) {
  return cleanName(name)
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/@/g, 'a')
    .replace(/\b(hd|sd|fhd|uhd|4k|1080p|720p|576p|480p)\b/g, ' ')
    .replace(/[^a-z0-9]/g, '');
}

/**
 * The country half of the key.
 *
 * A channel name is only unique inside one country's section — the lineup
 * carries 2,300 of them across six — so the group has to agree as well. These
 * lists use both ISO-3166 and their own shorthand for the same place.
 */
export function normalizeCc(cc, fallback = '') {
  const up = String(cc || fallback || '').trim().toUpperCase();
  if (up === 'BH') return 'BA';
  if (up === 'SR') return 'RS';
  return up;
}

/** `CC|name`, the key a merge actually looks up. */
export function mergeKey(country, name) {
  return `${normalizeCc(country)}|${matchKey(name)}`;
}

/** The country prefix of a lineup group, `HR - Music` -> `HR`. */
export function groupCountry(group) {
  return normalizeCc(String(group || '').split(' - ')[0]);
}

/**
 * Strip the tracking junk aggregators staple onto a URL.
 *
 * `?checkedby:iptvcat.net` is not a query parameter — it has no `=` — and the
 * same stream appears in these lists both with and without it. Left alone it
 * defeats deduplication and one channel becomes two.
 */
export function cleanUrl(raw) {
  return String(raw || '')
    .trim()
    .replace(/[?&]checkedby:[^&]*/gi, '')
    .replace(/\?$/, '')
    .replace(/&$/, '');
}
