/**
 * Where the VOD scraper looks, and what it is allowed to take.
 *
 * The channel scraper (`scrape-m3u8.mjs`) collects *live* streams from public
 * free-to-air indexes. This is its on-demand counterpart: films, documentaries
 * and series that are in the public domain or carry a licence that permits
 * redistribution.
 *
 * Nothing is taken on the strength of being downloadable. A film being freely
 * served from a public archive says nothing about whether it may be
 * redistributed — plenty of what sits on the open web was uploaded by someone
 * with no right to put it there. Two independent things have to agree before
 * an item is collected:
 *
 *  - it carries a licence declaration that permits redistribution, checked by
 *    [isRedistributable]; and
 *  - it belongs to a curated Archive collection, listed in [KINDS].
 *
 * Neither is sufficient alone. The licence field is filled in by the uploader,
 * so on an unmoderated upload it is a claim about the rights rather than
 * evidence of them; collection membership is set by the Archive. Requiring
 * both means a false declaration has to survive someone else's cataloguing to
 * get through. See the note on [KINDS] for what happened when only the licence
 * was required.
 */

/**
 * Licences whose terms permit redistributing the work as-is.
 *
 * Matched against Internet Archive's `licenseurl` field, which is what the
 * uploader declared. Deliberately an allowlist of URL prefixes rather than a
 * keyword search: "creativecommons.org" appears in the URL of every CC licence
 * including the ones excluded below, so a substring test for it would let
 * everything through.
 *
 * ND (no-derivatives) is included because this redistributes the work whole
 * and unmodified, which ND permits. SA (share-alike) is included for the same
 * reason — no derivative is being made.
 */
export const REDISTRIBUTABLE_LICENCES = [
  'creativecommons.org/publicdomain/mark/',
  'creativecommons.org/publicdomain/zero/',
  'creativecommons.org/licenses/by/',
  'creativecommons.org/licenses/by-sa/',
  'creativecommons.org/licenses/by-nd/',
];

/**
 * Non-commercial licences, off by default.
 *
 * NC forbids "commercial use", and what that covers is genuinely unsettled —
 * an app with any paid tier, advertising, or even a paid sibling product can
 * fall on the wrong side of it. That is a judgement about the business rather
 * than about the file, so it is a flag (`--allow-noncommercial`) rather than a
 * default, and the default is the cautious one.
 */
export const NONCOMMERCIAL_LICENCES = [
  'creativecommons.org/licenses/by-nc/',
  'creativecommons.org/licenses/by-nc-sa/',
  'creativecommons.org/licenses/by-nc-nd/',
];

/**
 * True when [licenseUrl] is one this may redistribute.
 *
 * Returns false for a missing, empty or unrecognised licence. That is the
 * important half: an item with no declared licence is not "probably fine", it
 * is unknown, and unknown is the same as no for this purpose.
 */
export function isRedistributable(licenseUrl, { allowNonCommercial = false } = {}) {
  if (typeof licenseUrl !== 'string') return false;
  const url = licenseUrl.trim().toLowerCase();
  if (!url) return false;
  // Strip the scheme so http:// and https:// forms both match — the Archive
  // has both, and older records overwhelmingly use http.
  const bare = url.replace(/^https?:\/\//, '');
  const allowed = allowNonCommercial
    ? [...REDISTRIBUTABLE_LICENCES, ...NONCOMMERCIAL_LICENCES]
    : REDISTRIBUTABLE_LICENCES;
  return allowed.some((prefix) => bare.startsWith(prefix));
}

/**
 * Video formats worth offering, best first.
 *
 * H.264 in MP4 leads because every Android device this app runs on decodes it
 * in hardware, including a Fire TV Stick Lite. Ogg Theora is last because
 * almost nothing decodes it in hardware and a 1 GB stick will not manage it in
 * software at feature length.
 */
const FORMAT_RANK = [
  /h\.?264/i,
  /mpeg-?4|mp4/i,
  /webm|vp[89]/i,
  /ogg|theora/i,
];

const VIDEO_EXT = /\.(mp4|m4v|webm|ogv)$/i;

/** Above this, a file is likelier to be an unwieldy master than something to stream. */
const MAX_SENSIBLE_BYTES = 4 * 1024 * 1024 * 1024;

/**
 * Choose the file to stream from an Archive item's file list.
 *
 * An item typically carries the same film several times: a large original, a
 * smaller re-encode, sometimes Ogg. Picking wrong is the difference between a
 * film that plays on a TV stick and one that buffers forever or refuses to
 * decode, so the choice is by decoder-friendliness first and size second —
 * within the best available format, the largest file under the cap, which is
 * the best quality that is still a sane thing to stream.
 *
 * Returns null when nothing playable is present.
 */
export function pickPlayableFile(files) {
  if (!Array.isArray(files)) return null;
  const candidates = files.filter(
    (f) => f && typeof f.name === 'string' && VIDEO_EXT.test(f.name),
  );
  if (candidates.length === 0) return null;

  const rankOf = (f) => {
    const hay = `${f.format || ''} ${f.name || ''}`;
    const i = FORMAT_RANK.findIndex((re) => re.test(hay));
    return i === -1 ? FORMAT_RANK.length : i;
  };
  const sizeOf = (f) => {
    const n = Number(f.size);
    return Number.isFinite(n) && n > 0 ? n : 0;
  };

  const withinCap = candidates.filter((f) => sizeOf(f) <= MAX_SENSIBLE_BYTES);
  // If every copy is enormous, take the smallest rather than giving up: an
  // unwieldy file that plays beats no entry at all.
  const pool = withinCap.length > 0 ? withinCap : [...candidates].sort((a, b) => sizeOf(a) - sizeOf(b)).slice(0, 1);

  return pool.slice().sort((a, b) => {
    const r = rankOf(a) - rankOf(b);
    if (r !== 0) return r;
    return sizeOf(b) - sizeOf(a);
  })[0];
}

/** Playable URL for a file inside an Archive item. */
export function downloadUrl(identifier, fileName) {
  return `https://archive.org/download/${encodeURIComponent(identifier)}/${encodeURIComponent(fileName)}`;
}

/**
 * The language groups this collects, and the values the Archive labels them
 * with. Both ISO codes and English names appear in the wild, sometimes on the
 * same collection, so each group lists every spelling worth asking for.
 */
export const LANGUAGE_GROUPS = {
  en: {
    label: 'English',
    values: ['eng', 'English', 'en'],
  },
  exyu: {
    label: 'Ex-Yu (HR / SRB / BIH)',
    values: [
      'hrv', 'Croatian', 'hr',
      'srp', 'Serbian', 'sr', 'scc',
      'bos', 'Bosnian', 'bs',
      'Serbo-Croatian', 'hbs',
    ],
  },
};

/**
 * What to ask the Archive for, per kind.
 *
 * Each kind is a set of *curated* Archive collections — ones a librarian
 * assembled, not ones an uploader tagged themselves into.
 *
 * That is the whole design, and it replaces an earlier version of this file
 * that asked for `mediatype:(movies)` and subtracted the kinds it did not
 * want. Two things were wrong with it, both found by running it at full size
 * rather than at the twelve-per-kind that proved the pipeline:
 *
 *  1. `mediatype:(movies)` is the Archive's bucket for *any* moving image, not
 *     for films. Subtracting documentaries and TV from it leaves home videos,
 *     advertising reels, screen recordings, station idents and phone clips —
 *     864 "titles" of which almost none belonged in a catalogue.
 *
 *  2. `licenseurl` is typed in by whoever uploaded the item, so on unmoderated
 *     uploads it is a claim rather than a fact. The full run surfaced items
 *     naming Hallmark and Paramount as their producers, carrying a Creative
 *     Commons declaration that is plainly not the rightsholder's. A licence
 *     check that trusts the uploader is not a licence check.
 *
 * Collection membership is the missing half: it is set by the Archive, not by
 * the uploader, so requiring it turns the licence field back into corroborating
 * evidence instead of the only evidence. [isRedistributable] still runs on
 * every item — this narrows what gets asked for, it does not replace the check.
 */
const CURATED = {
  // The Archive's curated film collections: public-domain features, plus the
  // shorts and silents that sit beside them.
  movies: ['feature_films', 'short_films', 'silent_films'],
  // Prelinger is the canonical curated ephemeral-film archive; `ephemera` is
  // the wider collection around it. NASA and the US government film
  // collections are genuinely public domain and were measured too, but they
  // hold raw ISS footage and congressional proceedings — public record rather
  // than anything anyone would browse a VOD rail for.
  documentaries: ['prelinger', 'ephemera'],
  // Off-air recordings of series whose copyright has lapsed. `classic_tv` is
  // curated; `animationandcartoons`, measured alongside it, is a broad parent
  // that mixes genuine classic shorts with fan uploads, so it is left out.
  series: ['classic_tv'],
};

const collectionClause = (ids) => `collection:(${ids.join(' OR ')})`;

export const KINDS = {
  movies: {
    label: 'Movies',
    group: 'Public Domain Movies',
    query: `mediatype:(movies) AND ${collectionClause(CURATED.movies)}`,
  },
  documentaries: {
    label: 'Documentaries',
    group: 'Public Domain Documentaries',
    query: `mediatype:(movies) AND ${collectionClause(CURATED.documentaries)}`,
  },
  series: {
    label: 'Series',
    group: 'Public Domain Series',
    query: `mediatype:(movies) AND ${collectionClause(CURATED.series)}`,
  },
};

/** Build the Archive query for one kind and one language group. */
export function buildQuery(kindId, languageId, { allowNonCommercial = false } = {}) {
  const kind = KINDS[kindId];
  const lang = LANGUAGE_GROUPS[languageId];
  if (!kind) throw new Error(`Unknown kind: ${kindId}`);
  if (!lang) throw new Error(`Unknown language group: ${languageId}`);

  const langClause = `language:(${lang.values.map((v) => `"${v}"`).join(' OR ')})`;
  // Ask the Archive to exclude unlicensed items rather than fetching and
  // discarding them — on a collection this size that is the difference between
  // a few hundred requests and a few thousand. isRedistributable still checks
  // every row that comes back; this is a narrowing, not the safeguard.
  const licenceClause = allowNonCommercial
    ? 'licenseurl:[* TO *]'
    : 'licenseurl:[* TO *] AND NOT licenseurl:(*by-nc*)';

  return `${kind.query} AND ${langClause} AND ${licenceClause}`;
}
