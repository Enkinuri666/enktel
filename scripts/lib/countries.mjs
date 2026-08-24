/**
 * Country identification, shared by the public scraper and the panel exporter.
 *
 * The two tools face the same problem from opposite ends: playlists label a
 * channel with an attribute (`tvg-country="hr"`) that is frequently missing,
 * and panels don't label channels at all — they put them in a category called
 * something like "EX-YU | HR". Both need to answer "is this Croatian?".
 */

/**
 * Feeds disagree on a handful of codes — iptv-org labels Britain "UK" where
 * Free-TV uses the ISO "GB" — and some use pseudo-codes for groupings that
 * aren't countries at all.
 */
const COUNTRY_ALIASES = { UK: 'GB', EL: 'GR', EU: '', INT: '', WORLD: '' };

/**
 * @param {string} code
 * @returns {string} ISO-3166 alpha-2, or '' when it isn't a country code
 */
export function normalizeCountry(code) {
  const upper = String(code ?? '')
    .trim()
    .toUpperCase()
    .replace(/[^A-Z]/g, '');
  if (upper in COUNTRY_ALIASES) return COUNTRY_ALIASES[upper];
  return upper.length === 2 ? upper : '';
}

/**
 * How a country shows up in a group or category name.
 *
 * Anchored on word boundaries so "AU" does not swallow "AUSTRIA" and "US"
 * does not swallow "RUSSIA". Only the countries the storefront actually
 * merchandises are listed — anything else falls back to the coded attribute.
 */
export const COUNTRY_PATTERNS = {
  HR: /\b(hr|cro|croatia|croatian|hrvatska|ex[\s-]?yu|exyu|balkan|adria)\b/i,
  GB: /\b(uk|gb|british|britain|united[\s-]kingdom|england|scotland|wales)\b/i,
  US: /\b(us|usa|u\.s\.a?|america|american|united[\s-]states)\b/i,
  AU: /\b(au|aus|australia|australian|aussie|oceania)\b/i,
  IE: /\b(ie|irl|ireland|irish)\b/i,
  CA: /\b(ca|can|canada|canadian)\b/i,
  DE: /\b(de|ger|germany|german|deutsch(land)?)\b/i,
  RS: /\b(rs|srb|serbia|serbian|srbija)\b/i,
  BA: /\b(ba|bih|bosnia|bosnian)\b/i,
  SI: /\b(si|slo|slovenia|slovenian|slovenija)\b/i,
  XK: /\b(xk|kos|kosovo|kosova|kosove|kosovar)\b/i,
  // MENA. The panel rosters are almost entirely Arabic-language, and without
  // these every one of their channels falls back to '' — which then loses the
  // matcher's country guard, and "MBC 1" quietly takes the Mauritian id.
  AE: /\b(ae|uae|emirates|emirati|abu ?dhabi|dubai|sharjah|ajman)\b/i,
  SA: /\b(sa|ksa|saudi|arabia|arabian|riyadh|jeddah)\b/i,
  QA: /\b(qa|qat|qatar|qatari|doha)\b/i,
  EG: /\b(eg|egy|egypt|egyptian|masr|misr|cairo|nile)\b/i,
  LB: /\b(lb|lbn|lebanon|lebanese|liban|libanon|beirut)\b/i,
  SY: /\b(sy|syr|syria|syrian|souriya|damascus)\b/i,
  IQ: /\b(iq|irq|iraq|iraqi|iraqia|iraqiya|baghdad)\b/i,
  JO: /\b(jo|jor|jordan|jordanian|amman)\b/i,
  PS: /\b(ps|pse|palestine|palestinian|filastin|gaza)\b/i,
  OM: /\b(om|omn|oman|omani|muscat)\b/i,
  KW: /\b(kw|kwt|kuwait|kuwaiti)\b/i,
  BH: /\b(bh|bhr|bahrain|bahraini)\b/i,
  TN: /\b(tn|tun|tunisia|tunisian|tunis|tunisie)\b/i,
  MA: /\b(ma|mar|morocco|moroccan|maroc|maghreb)\b/i,
  DZ: /\b(dz|alg|algeria|algerian|algerie)\b/i,
  TR: /\b(tr|tur|turkey|turkish|turkiye|turk)\b/i,
  FR: /\b(fr|fra|france|french|francais)\b/i,
};

/**
 * Does this free-text name belong to one of the requested countries?
 *
 * An empty `countries` list means "no filter", so everything matches.
 *
 * @param {string} name a group title or panel category name
 * @param {string[]} countries ISO-3166 alpha-2 codes
 */
export function matchesCountry(name, countries) {
  if (!countries?.length) return true;
  const text = String(name ?? '');
  return countries.some((code) => COUNTRY_PATTERNS[normalizeCountry(code)]?.test(text) ?? false);
}

/**
 * Work out a country from free text when the coded attribute is missing.
 *
 * The country-grouped indexes carry `group-title="Croatia"` and no
 * `tvg-country` at all, so without this a request for HR returns a dozen
 * channels out of the hundreds actually present. Returns the first pattern
 * that matches, or '' when nothing does — which is the honest answer for a
 * group called "Movies".
 *
 * @param {string} name a group title, channel name, or both
 * @returns {string} ISO-3166 alpha-2, or ''
 */
export function inferCountry(name) {
  const text = String(name ?? '');
  if (!text.trim()) return '';
  for (const [code, pattern] of Object.entries(COUNTRY_PATTERNS)) {
    if (pattern.test(text)) return code;
  }
  return '';
}

/**
 * The country to file a parsed channel under: the coded attribute when a feed
 * set one, otherwise whatever its group and name give away.
 *
 * @param {{tvgCountry?: string, group?: string, name?: string}} entry
 * @returns {string} ISO-3166 alpha-2, or ''
 */
export function countryOf(entry) {
  return entry?.tvgCountry || inferCountry(`${entry?.group ?? ''} ${entry?.name ?? ''}`);
}
