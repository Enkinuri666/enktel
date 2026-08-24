/**
 * Channel-name repair for panel-style playlists.
 *
 * The public indexes the scraper reads publish tidy names — "Abu Dhabi Sports
 * 1", "Al Jazeera Mubasher". A provider panel publishes whatever the operator
 * typed into the admin form: `AD-Sports1`, `aljazeera_mubasher`, `OSN_Cinma_2`,
 * `beINSports-fr1`, `DUBAISPORT1`. Those never match a catalog name, so the
 * channel gets no EPG id and no logo however good the catalog is.
 *
 * This produces the plausible spellings of one such name so the matcher has
 * something to hit. Nothing here decides *which* channel a name belongs to —
 * it only widens the set of strings worth comparing; the matcher's country
 * guard and discriminator rule still decide what actually matches.
 *
 * The same expansion is applied to **both sides** of a comparison — panel name
 * and catalog name alike. That symmetry is the point: "Alkass One" and
 * "Alkas-1" only meet in the middle if both are put through the same mill.
 */

import { COUNTRY_PATTERNS } from './countries.mjs';

/**
 * Broadcaster abbreviations that appear glued or shortened in panel names.
 *
 * Deliberately small and MENA-specific: every entry is a shortening a panel
 * operator actually used in the roster this was built for, not a guess at
 * what some other panel might do. A wrong expansion invents a channel, so the
 * bar for adding one is that the abbreviation is unambiguous in context.
 */
export const ABBREVIATIONS = {
  ad: 'abu dhabi',
  cn: 'cartoon network',
  natgeo: 'national geographic',
  nat: 'national',
  geo: 'geographic',
  ann: 'arab news network',
  trt: 'trt',
  osn: 'osn',
  art: 'art',
  mbc: 'mbc',
};

/**
 * Words that are glued to a following word often enough to be worth splitting
 * on sight. Panel operators drop the space far more often than they drop a
 * letter, and an all-caps name (`DUBAISPORT1`) gives camel-case splitting
 * nothing to work with.
 */
const GLUED_PREFIXES = [
  'dubai',
  'abudhabi',
  'sharjah',
  'palestine',
  'jordan',
  'saudi',
  'sky',
  'bein',
  'rotana',
  'iraqia',
  'iraqiya',
];

/** Number words the iptv-org catalog uses where a panel writes a digit. */
const NUMBER_WORDS = {
  one: '1',
  two: '2',
  three: '3',
  four: '4',
  five: '5',
  six: '6',
  seven: '7',
  eight: '8',
  nine: '9',
  ten: '10',
  eleven: '11',
  twelve: '12',
  thirteen: '13',
  fourteen: '14',
  fifteen: '15',
  sixteen: '16',
};

/** Language markers a panel puts in front of a name: `EN:`, `FR:`, `AR -`. */
const LANGUAGE_PREFIX_RE = /^\s*(en|eng|fr|fre|ar|arabic|tr|de|es|it|nl|pt)\s*[:|-]\s*/i;

const LANGUAGE_CODES = {
  en: 'eng',
  eng: 'eng',
  fr: 'fra',
  fre: 'fra',
  ar: 'ara',
  arabic: 'ara',
  tr: 'tur',
  de: 'deu',
  es: 'spa',
  it: 'ita',
  nl: 'nld',
  pt: 'por',
};

/**
 * A `+2`/`+1` suffix is a timeshift feed, not a variant spelling.
 *
 * It matters far beyond cosmetics: a timeshifted channel shares its parent's
 * *schedule* but not its *clock*, so handing it the parent's EPG id puts every
 * programme on screen an hour or two out. Callers use this to take the logo
 * from the parent and refuse the id.
 */
const TIMESHIFT_RE = /\+\s?(\d{1,2})\s*(?:h|hr|hrs)?$/i;

/**
 * @param {string} raw
 * @returns {{name: string, offset: number}} name without the marker, and the
 *   shift in hours (0 when there is none)
 */
export function splitTimeshift(raw) {
  const text = String(raw ?? '').trim();
  const m = TIMESHIFT_RE.exec(text);
  if (!m) return { name: text, offset: 0 };
  return { name: text.slice(0, m.index).trim(), offset: Number(m[1]) };
}

/**
 * Pull a leading language marker off a name.
 *
 * @param {string} raw
 * @returns {{name: string, language: string}} ISO-639-3 code, or ''
 */
export function splitLanguagePrefix(raw) {
  const text = String(raw ?? '').trim();
  const m = LANGUAGE_PREFIX_RE.exec(text);
  if (!m) return { name: text, language: '' };
  return { name: text.slice(m[0].length).trim(), language: LANGUAGE_CODES[m[1].toLowerCase()] ?? '' };
}

/**
 * Insert the spaces a panel name is missing.
 *
 * Three boundaries, in order: an acronym followed by a capitalised word
 * (`MBCPro` → `MBC Pro`), a lowercase letter followed by an uppercase one
 * (`beINSports` → `beIN Sports`), and a letter followed by a digit
 * (`Sports1` → `Sports 1`).
 *
 * @param {string} raw
 * @returns {string}
 */
export function splitCompound(raw) {
  return String(raw ?? '')
    .replace(/([A-Z]{2,})([A-Z][a-z])/g, '$1 $2')
    // A glued quality or medium suffix: "JeemTV" and "ADTV" both hide a word
    // the normaliser strips as noise — but only once it can see it.
    .replace(/([a-z])(TV|HD|SD|FHD|UHD)\b/g, '$1 $2')
    .replace(/([A-Z]{2,})(TV|HD)\b/g, '$1 $2')
    .replace(/([a-z])([A-Z][a-z])/g, '$1 $2')
    .replace(/([A-Za-z])(\d)/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Split a glued brand word an all-caps name hides.
 *
 * `DUBAISPORT1` case-folds to one token that camel-case splitting cannot
 * touch, so the known prefixes are tried against it directly.
 *
 * @param {string} lower a lowercased, space-separated name
 * @returns {string}
 */
export function splitGlued(lower) {
  return String(lower ?? '')
    .split(' ')
    .map((token) => {
      for (const prefix of GLUED_PREFIXES) {
        // The remainder has to be a word in its own right: "syrian" starts
        // with "syria" and splitting it would invent a channel called
        // "Syria N".
        if (token.startsWith(prefix) && token.length - prefix.length >= 3) {
          return `${prefix} ${token.slice(prefix.length)}`;
        }
      }
      return token;
    })
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Separate the Arabic definite article from the word it is glued to.
 *
 * Feeds are evenly split between "Alarabiya" and "Al Arabiya", "AlJazeera" and
 * "Al Jazeera". Applied to both sides of a comparison it makes the choice
 * irrelevant. Only run on a token long enough that the remainder is a word —
 * "alt" and "all" are not articles.
 *
 * @param {string} lower
 * @returns {string}
 */
export function splitArticle(lower) {
  return String(lower ?? '')
    .split(' ')
    .map((token) =>
      /^(al|el)[a-z]{4,}$/.test(token) ? `${token.slice(0, 2)} ${token.slice(2)}` : token,
    )
    .join(' ');
}

/**
 * Fold the doubled consonants transliterated Arabic disagrees about.
 *
 * "Alkass" and "Alkas", "Hekayat" and "Hekkayat" are the same name romanised
 * by two different people. Applied symmetrically this costs nothing and
 * recovers a whole family of matches. Digits and the rest of the name are
 * untouched, so "Sports 11" does not become "Sports 1".
 *
 * Only tokens long enough that the fold cannot change what the word *is* are
 * touched — "OOD" folding to "od" collides with "ODD TV", and "Iqraa" folding
 * to "Iqra" takes a different broadcaster's channel.
 *
 * @param {string} lower
 * @returns {string}
 */
export function collapseDoubles(lower) {
  return String(lower ?? '')
    .split(' ')
    .map((token) => (token.length >= 6 ? token.replace(/([a-z])\1+/g, '$1') : token))
    .join(' ');
}

/**
 * Drop a leading Arabic definite article.
 *
 * The catalog files the channel as "Al Iraqia Sport" and the panel calls it
 * "Iraqia-Sport"; "Al-Majd Kids" and "MAJD-KIDS" are the same channel. The
 * article is grammar, not identity, and whether a romanisation keeps it is a
 * coin flip — so both spellings have to exist on both sides.
 *
 * @param {string} lower
 * @returns {string}
 */
export function dropArticle(lower) {
  const text = String(lower ?? '').trim();
  const stripped = text.replace(/^(?:al|el)\s+/, '');
  return stripped || text;
}

/**
 * Drop a plural "s" the two sides disagree about.
 *
 * "Dubai Sport 1" and "Dubai Sports 1" are one channel, and token-based Dice
 * scores them 0.67 — nowhere near the cutoff — because it sees two unrelated
 * words. Only tokens long enough that the singular is still a word are folded,
 * so "news" and "kids" survive intact.
 *
 * @param {string} lower
 * @returns {string}
 */
export function foldPlurals(lower) {
  return String(lower ?? '')
    .split(' ')
    .map((token) => (token.length >= 6 && token.endsWith('s') ? token.slice(0, -1) : token))
    .join(' ');
}

/**
 * Drop a bare two-letter country code an indexer appended.
 *
 * "Arena Sport 3 BA" is "Arena Sport 3" with a note about which country's feed
 * it is; the code is not part of the name and scores as a whole extra token,
 * which drops a perfect match to 0.86.
 *
 * Restricted to codes this project actually recognises as countries, because
 * plenty of two-letter suffixes are not one: "HD" is a quality marker and "AD"
 * is Abu Dhabi, and dropping it turns "Nat Geo AD" into "Nat Geo", which
 * matches the wrong National Geographic outright. Spelled-out places are left
 * alone too — "Al Jazeera Balkans" is not "Al Jazeera".
 *
 * @param {string} name
 * @returns {string}
 */
export function dropTrailingCountryCode(name) {
  const text = String(name ?? '').trim();
  const m = /\s+([A-Z]{2})$/.exec(text);
  if (!m || !(m[1] in COUNTRY_PATTERNS)) return text;
  return text.slice(0, m.index).trim() || text;
}

/** Swap English number words for digits, and digits for words. */
function numberVariants(lower) {
  const out = new Set();

  const toDigits = lower.replace(/\b([a-z]+)\b/g, (word) => NUMBER_WORDS[word] ?? word);
  if (toDigits !== lower) out.add(toDigits);

  const wordFor = Object.fromEntries(Object.entries(NUMBER_WORDS).map(([w, d]) => [d, w]));
  const toWords = lower.replace(/\b(\d{1,2})\b/g, (digit) => wordFor[digit] ?? digit);
  if (toWords !== lower) out.add(toWords);

  return out;
}

/**
 * Every spelling of a channel name worth comparing.
 *
 * Ordered most- to least- faithful to the input, so a caller that stops at the
 * first hit prefers the least-transformed one. The input itself is always
 * first and the set never contains duplicates or empties.
 *
 * @param {string} raw
 * @returns {string[]}
 */
export function channelNameVariants(raw) {
  const { name: untimeshifted } = splitTimeshift(raw);
  const { name: unprefixed } = splitLanguagePrefix(untimeshifted);
  const base = String(unprefixed ?? '').trim();
  if (!base) return [];

  const variants = new Set([base]);

  // Separators a panel uses interchangeably with a space.
  const spaced = base.replace(/[_.\-|/]+/g, ' ').replace(/\s+/g, ' ').trim();
  variants.add(spaced);

  const compound = splitCompound(spaced);
  variants.add(compound);

  // Everything below works on a case-folded string, which is what the
  // matcher's own normaliser will do to it anyway.
  const lower = compound.toLowerCase();
  const stages = new Set([lower]);

  const glued = splitGlued(lower);
  stages.add(glued);

  for (const stage of [...stages]) {
    stages.add(splitArticle(stage));
  }

  for (const stage of [...stages]) {
    stages.add(dropArticle(stage));
  }

  for (const stage of [...stages]) {
    stages.add(collapseDoubles(stage));
    stages.add(foldPlurals(stage));
  }

  for (const stage of [...stages]) {
    for (const variant of numberVariants(stage)) stages.add(variant);
  }

  for (const stage of stages) {
    for (const expanded of expandAbbreviations(stage)) variants.add(expanded);
    variants.add(stage);
  }

  // Last, so it is only reached when no more faithful spelling matched: this
  // one deletes information, and the deleted token is occasionally the answer.
  for (const variant of [...variants]) {
    variants.add(dropTrailingCountryCode(variant));
  }

  return [...variants].map((v) => v.replace(/\s+/g, ' ').trim()).filter(Boolean);
}

/**
 * Expand a known abbreviation, keeping the unexpanded form too.
 *
 * Returns a set rather than one string because the catalog is inconsistent
 * about which form it stores — "AD Sports 1" is an alt name of "Abu Dhabi
 * Sports 1", and either could be the one present.
 *
 * @param {string} lower
 * @returns {Set<string>}
 */
export function expandAbbreviations(lower) {
  const tokens = String(lower ?? '').split(' ');
  const out = new Set();

  const expanded = tokens
    .map((token) => ABBREVIATIONS[token] ?? token)
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();

  if (expanded) out.add(expanded);
  return out;
}

/**
 * Acronyms to keep shouted when the whole name arrives in capitals.
 *
 * A name written entirely in caps carries no signal about which of its tokens
 * are acronyms, so "AL-JAZEERA" would keep "AL" as one. Anything not on this
 * list gets title-cased like an ordinary word.
 */
const ACRONYMS = new Set([
  'AD', 'ANN', 'ART', 'BBC', 'CBC', 'CN', 'HD', 'LBC', 'MBC', 'MTV', 'NBN',
  'OSN', 'OTV', 'RT', 'TNN', 'TRT', 'TV', 'UAE',
]);

/**
 * A name fit to show a viewer.
 *
 * Panel names are shouted, glued and abbreviated; a lineup should not be. This
 * un-glues and title-cases while leaving the brand capitalisation the catalog
 * uses alone where it is known.
 *
 * @param {string} raw
 * @returns {string}
 */
export function displayName(raw) {
  const { name: untimeshifted, offset } = splitTimeshift(raw);
  const { name: unprefixed } = splitLanguagePrefix(untimeshifted);

  const spaced = splitCompound(
    String(unprefixed ?? '')
      .replace(/[_.\-|/]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim(),
  );

  const shouted = !/[a-z]/.test(String(raw ?? ''));

  // An all-caps panel name gives camel-case splitting nothing to work with,
  // so try the glued-prefix split as well; it only fires when it finds one.
  const unglued = splitGlued(spaced.toLowerCase());
  const words = unglued === spaced.toLowerCase() ? spaced : unglued;

  const titled = words
    .split(' ')
    .filter(Boolean)
    .map((token) => {
      if (/^\d+$/.test(token)) return token;
      // A token that is already mixed-case is a brand spelling worth keeping:
      // "beIN" must not become "Bein".
      if (/[a-z]/.test(token) && /[A-Z]/.test(token)) return token;
      if (token.length <= 3 && token === token.toUpperCase()) {
        if (!shouted || ACRONYMS.has(token)) return token;
      }
      return token[0].toUpperCase() + token.slice(1).toLowerCase();
    })
    .join(' ');

  return offset ? `${titled} +${offset}` : titled;
}
