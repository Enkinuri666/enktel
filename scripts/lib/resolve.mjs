/**
 * Resolving one channel: which EPG id it takes, which logo, which genre.
 *
 * `build-lineup.mjs` did this inline, which was fine while the input was a
 * scraped playlist whose entries already carried half the answer. A panel
 * roster carries none of it — no id, no logo, no country, no group — and gets
 * three extra problems with it:
 *
 *   1. the name is a panel operator's shorthand (`DUBAISPORT1`), so it has to
 *      be matched on its other spellings as well as its own;
 *   2. a lot of brands exist once per country, so "MBC 1" without a country
 *      hint is a coin flip between the Emirati and the Mauritian channel;
 *   3. a `+2` feed shares its parent's schedule but not its clock.
 *
 * The rule underneath all three: **a logo is cosmetic, an EPG id is not.** A
 * logo from the wrong regional feed of the right brand is the same picture. A
 * `tvg-id` from the wrong regional feed is a guide full of programmes that are
 * not on. So artwork is resolved generously and ids are resolved strictly, and
 * every refusal is recorded with a reason rather than left as a silent blank.
 */
import { countryOf } from './countries.mjs';
import { classifyGenre } from './genres.mjs';
import { channelNameVariants, displayName, splitLanguagePrefix, splitTimeshift } from './panel-names.mjs';
import { buildIndex, matchChannel, normalizeName } from './xmltv.mjs';

/** Why a channel ended up without an EPG id. */
export const REASONS = {
  TIMESHIFT: 'timeshift',
  AMBIGUOUS: 'ambiguous',
  COUNTRY: 'country-mismatch',
  NO_MATCH: 'no-match',
  UNNAMED: 'unnamed',
  UNKNOWN_ID: 'unknown-curated-id',
};

/**
 * Widen catalog names to the spellings a panel might use.
 *
 * The expansion has to happen on this side too, not just on the entry's: a
 * panel writing "Alkas-1" and a catalog holding "Alkass One" only meet if both
 * are put through the same mill.
 *
 * @param {Array<{id: string, names: string[], country?: string}>} channels
 * @returns {Array<object>}
 */
export function expandCatalog(channels) {
  return channels.map((channel) => ({
    ...channel,
    names: [...new Set(channel.names.flatMap((name) => channelNameVariants(name)))],
  }));
}

/**
 * Every distinct channel id an exact name hit could mean.
 *
 * `matchChannel` picks one and moves on, which is the right behaviour for a
 * feed that declares its country. For one that does not, "Animal Planet"
 * matches fifteen regional channels and picking the first is a guess wearing a
 * score of 1.0. This reports the choice so the caller can decline to make it.
 *
 * @param {ReturnType<typeof buildIndex>} index
 * @param {string[]} names
 * @param {string} country
 * @returns {{ids: string[], narrowed: boolean}} ids for the first name that
 *   hits, and whether the country hint picked exactly one of them
 */
export function exactCandidates(index, names, country) {
  for (const name of names) {
    const bucket = index.byName.get(normalizeName(name));
    if (!bucket?.length) continue;

    const ids = [...new Set(bucket.map((c) => c.id))];
    if (!country) return { ids, narrowed: false };

    const inCountry = [...new Set(bucket.filter((c) => c.country === country).map((c) => c.id))];
    return inCountry.length ? { ids: inCountry, narrowed: true } : { ids, narrowed: false };
  }

  return { ids: [], narrowed: false };
}

/**
 * Build a resolver over the iptv-org reference data.
 *
 * @param {{
 *   channels: Array<{id: string, names: string[], country?: string}>,
 *   logos?: Map<string, string>,
 *   guideSites?: Map<string, string[]>,
 *   threshold?: number,
 *   logoThreshold?: number,
 * }} opts
 */
export function createResolver({
  channels,
  logos = new Map(),
  guideSites = new Map(),
  // The strict cut the merged scraper already settled on for ids.
  threshold = 0.9,
  // Looser than the id cut, because the worst outcome is the right brand's
  // other regional logo — the same picture. Not much looser: at 0.7 a channel
  // called "Kids Movie 1" takes "One Movies", which is a different brand
  // entirely, and a wrong brand mark is not cosmetic.
  logoThreshold = 0.85,
  /**
   * Does a country hint that disagrees with the catalog disqualify an exact
   * name match?
   *
   * Only when the hint means what the catalog means. A **roster** country is
   * an editorial statement about which broadcaster a channel is, so "ANN News,
   * Syria" against "ANN News, India" is real evidence that they are different
   * channels. A **playlist** `tvg-country` is usually a statement about where
   * the stream can be watched: an AU index tags "Fear Factor" AU while the
   * catalog files it US, and they are the same channel. Applying roster
   * strictness to a scraped playlist threw away 51 correct matches out of 200.
   */
  strictCountry = false,
} = {}) {
  const index = buildIndex(expandCatalog(channels));
  const nameOf = new Map(index.all.map((c) => [c.id, c.names[0]]));

  /**
   * @param {{name: string, tvgId?: string, tvgLogo?: string, tvgCountry?: string, group?: string}} entry
   * @returns {object} the entry's resolved fields, plus how each was decided
   */
  function resolve(entry) {
    const raw = String(entry?.name ?? '').trim();
    const { name: base, offset } = splitTimeshift(raw);
    const { language } = splitLanguagePrefix(base);
    const altNames = channelNameVariants(raw);
    const country = entry.tvgCountry || countryOf(entry);

    // A hand-written id that no longer exists is the failure mode curation
    // invites: the catalog renames or retires a channel and the roster keeps
    // pointing at a dead id, silently, for as long as nobody looks. Checking
    // it here turns that into a line in the report.
    const curated = String(entry?.channel ?? '').trim();
    const curatedKnown = curated && nameOf.has(curated);

    const result = {
      name: raw,
      country,
      language,
      timeshift: offset,
      tvgId: entry.tvgId ?? (curatedKnown ? curated : ''),
      tvgLogo: entry.tvgLogo ?? '',
      guideSites: [],
      idVia: entry.tvgId ? 'given' : curatedKnown ? 'curated' : '',
      idScore: entry.tvgId || curatedKnown ? 1 : 0,
      idReason: curated && !curatedKnown ? REASONS.UNKNOWN_ID : '',
      logoVia: entry.tvgLogo ? 'given' : '',
      genre: '',
      display: displayName(raw),
    };

    // A name that is nothing but punctuation and a number — the panel roster
    // has seven — identifies no channel, and no amount of matching will make
    // one up. Tested against letters in *any* script: an Arabic-only name is a
    // name, and `/[a-z]/` would file it here alongside "-1".
    if (!altNames.length || !/\p{L}/u.test(raw)) {
      result.idReason = REASONS.UNNAMED;
      // Show what the panel published rather than the cleaned-up "1": these
      // are only identifiable by the string the operator left behind.
      result.display = raw;
      result.genre = classifyGenre({ name: raw, group: entry.group });
      return result;
    }

    const match = matchChannel({ ...entry, name: raw, tvgCountry: country, altNames }, index, {
      threshold,
    });

    if (!result.tvgId && !curated) {
      if (offset) {
        // Shares the parent's schedule, not its clock: the parent's id would
        // put every programme on screen `offset` hours out.
        result.idReason = REASONS.TIMESHIFT;
      } else if (!match) {
        result.idReason = REASONS.NO_MATCH;
      } else {
        const { ids, narrowed } = exactCandidates(index, altNames, country);
        if (match.via === 'exact' && ids.length > 1 && !narrowed) {
          // The name is exactly right and still means several channels. Taking
          // the first is how a MENA panel's "Animal Planet" ends up with an
          // Australian guide.
          result.idReason = REASONS.AMBIGUOUS;
          result.ambiguousWith = ids;
        } else if (strictCountry && country && match.country && match.country !== country) {
          // One candidate, and it is somewhere else. The fuzzy pass already
          // refuses to cross a declared border; an exact name hit is not
          // better evidence, it is the same evidence — "ANN News" is a Syrian
          // channel here and an Indian one in the catalog, and they are not
          // related. Unlike the ambiguous case, this is a different
          // broadcaster rather than another feed of the same brand, so its
          // artwork is wrong too.
          result.idReason = REASONS.COUNTRY;
          result.rejected = match.id;
        } else {
          result.tvgId = match.id;
          result.idVia = match.via;
          result.idScore = Number(match.score.toFixed(3));
        }
      }
    }

    // Artwork. The id is tried first because it is the exact answer; a channel
    // that was refused an id still gets the brand's logo from the looser pass.
    if (!result.tvgLogo) {
      if (result.tvgId && logos.has(result.tvgId)) {
        result.tvgLogo = logos.get(result.tvgId);
        result.logoVia = 'id';
      } else if (result.idReason !== REASONS.COUNTRY) {
        const loose =
          match ??
          matchChannel({ ...entry, name: raw, tvgCountry: country, altNames }, index, {
            threshold: logoThreshold,
          });
        if (loose && logos.has(loose.id)) {
          result.tvgLogo = logos.get(loose.id);
          result.logoVia = result.tvgId ? 'id' : 'name';
          result.logoFrom = loose.id;
        }
      }
    }

    if (result.tvgId) {
      result.guideSites = guideSites.get(result.tvgId) ?? [];
      // The catalog's own spelling beats the panel's shorthand once we know
      // which channel this is.
      result.display = nameOf.get(result.tvgId) || result.display;
    }

    result.genre = classifyGenre({ name: result.display, tvgName: raw, group: entry.group });
    return result;
  }

  return { resolve, index };
}
