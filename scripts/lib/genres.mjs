/**
 * Genre classification for channel lineups.
 *
 * Scraped playlists arrive grouped by whatever each feed felt like — "General",
 * "Undefined", a country name, a language. A storefront lineup wants genre
 * buckets per country instead, so this maps a channel onto one.
 *
 * Rules are ordered and the first match wins. Order is the whole design here:
 * a pay-per-view channel is full of sports words, a 24/7 series stream is full
 * of entertainment words, and a sports news channel is sports rather than news.
 * Reordering this list changes what the lineup looks like.
 */

/** Genre buckets, in the order they are tested and the order they are listed. */
export const GENRES = [
  'PPV / Main Events',
  'Sports',
  '24/7 Series',
  'News',
  'Documentary',
  'Reality',
  'Kids',
  'Religion',
  'Movies',
  'Music',
  'Entertainment',
];

/**
 * Sports brands worth recognising by name, including the ones a generic
 * /sport/ pattern would miss entirely — MUTV, DAZN, Willow, Optus.
 */
const SPORTS_BRANDS =
  /\b(bein ?sports?|be in ?sports?|fox ?sports?|eurosport|sportklub|sport ?klub|sportska|sports? ?tv|espn|espnu|mutv|man ?utd ?tv|lfc ?tv|mufc|optus ?sports?|stan ?(sports?|events?)|paramount\+? ?sports?|sky ?sports?|tnt ?sports?|bt ?sports?|dazn|willow|supersport|arena ?sports?|viaplay ?sports?|premier ?sports?|laliga|serie ?a|bundesliga|nba|nfl|mlb|nhl|wnba|ufc|motogp|formula ?1|f1 ?tv|motorsport|racing ?tv|golf ?channel|tennis ?channel|sportsnet|tsn|nbcsn|cbs ?sports?|nbc ?sports?|abc ?sports?|kayo|fox ?footy|nrl|afl|super ?rugby|cricket|sport ?1|sport ?2|sport ?[0-9]|hrt ?sports?|max ?sports?|nova ?sports?|match ?tv|alkass?|al ?kass?|ad ?sports?|abu ?dhabi ?sports?|dubai ?sports?|dubai ?racing|sharjah ?sports?|jordan ?sports?|iraqia ?sports?|nile ?sports?|mbc ?pro|ssc ?sports?|oman ?sports?|\bfight\b)\b/i;

/**
 * Unambiguous pay-per-view terms: nothing but a PPV channel is called this.
 */
const PPV_STRONG =
  /\b(ppv|pay ?per ?view|fight ?night|prelims?|undercard|boxing|wrestling|wwe|aew|bellator|one ?championship)\b/i;

/**
 * Weak ones. "Main Event" is a PPV staple *and* the name of Sky's flagship
 * sports channel, so it only means PPV on a channel with no sports brand on
 * it — otherwise "Sky Sports Main Event" gets filed as pay-per-view.
 */
const PPV_WEAK = /\b(main ?event|event ?[0-9]+)\b/i;

const RULES = [
  { genre: 'PPV / Main Events', pattern: PPV_STRONG },
  { genre: 'Sports', pattern: SPORTS_BRANDS },
  {
    genre: 'PPV / Main Events',
    pattern: PPV_WEAK,
    // Only reached when no sports brand matched above.
  },
  { genre: 'Sports', pattern: /\b(sports?|futbol|football|soccer|rugby|basket(ball)?|hockey)\b/i },
  // Before Entertainment: these are entertainment channels by content, but the
  // point of the bucket is that they loop one series forever.
  {
    genre: '24/7 Series',
    pattern: /(\b24\s*[\/x-]?\s*7\b|\bround ?the ?clock\b|\ball ?day\b.*\bmarathon\b|\bmarathon\b)/i,
  },
  {
    genre: 'News',
    pattern:
      /\b(news|cnn|msnbc|cnbc|bloomberg|euronews|al ?jazeera|sky ?news|bbc ?news|fox ?news|newsmax|gb ?news|abc ?news|nbc ?news|cbs ?news|france ?24|dw|rt|n1|hrt ?vijesti|vijesti|dnevnik|weather|meteo|al ?arabiya|alarabiya|al ?hadath|al ?mayadeen|ikhbaria|alikhbaria|akhbar|mubasher|russia ?today|russia ?al ?yaum)\b/i,
  },
  {
    genre: 'Documentary',
    pattern:
      /\b(document(ary|aries)|discovery|nat ?geo|national ?geographic|history|animal ?planet|pbs|curiosity|smithsonian|viasat ?(explore|nature|history)|science|crime ?\+? ?investigation|natgeo|nat ?geo ?wild|wild ?life|wildlife|wild ?earth|wathaeqia|真)\b/i,
  },
  {
    genre: 'Reality',
    pattern:
      /\b(reality|real ?life|tlc|bravo|hayu|e!|e ?entertainment|big ?brother|love ?island|married ?at ?first ?sight|keeping ?up|housewives|survivor|masterchef|come ?dine)\b/i,
  },
  {
    genre: 'Kids',
    pattern:
      /\b(kids?|cartoon|nick(elodeon| ?jr)?|disney( ?junior| ?jr| ?xd)?|boomerang|baby ?tv|pbs ?kids|cbeebies|cbbc|anime|toon|spacetoon|baraem|jeem|toyor|ajyal|kidzone|majd ?kids|jeem ?tv|jeemtv)\b/i,
  },
  // After Kids on purpose: Al-Majd Kids and Toyor Al-Jannah are religious
  // children's channels, and a viewer looks for them under Kids.
  {
    genre: 'Religion',
    pattern:
      /\b(quran|qur.?an|islam(ic)?|iqraa?|al ?majd|majd|zaytoona|zitouna|ezzitouna|makkah|mecca|madinah|sunnah|hadeeth|nabawy|risalah|tawheed|azhar|al ?nas+|elnas|christian|gospel|catholic|ewtn|religio(n|us))\b/i,
  },
  {
    genre: 'Movies',
    pattern:
      /\b(movies?|cinema|cinma|sinama|film(s|ovi)?|aflam|hbo|starz|showtime|cinemax|box ?office|paramount ?network|amc)\b/i,
  },
  {
    genre: 'Music',
    pattern: /\b(music|mtv|vh1|kerrang|clubbing|radio|fm|hits|kiss|nova ?fm|triple ?j)\b/i,
  },
  {
    genre: 'Entertainment',
    pattern:
      /\b(entertainment|general|drama|comedy|series|lifestyle|variety|zabava|opći|opci)\b/i,
  },
];

/**
 * Which genre bucket does this channel belong in?
 *
 * The channel's own name is tested before its group, because feeds are far
 * more careless with group titles than with names — "Undefined" is a common
 * group for a channel plainly called "Sky Sports Main Event".
 *
 * @param {{name?: string, group?: string, tvgName?: string}} entry
 * @returns {string} a value from GENRES, or '' when nothing fits
 */
export function classifyGenre(entry) {
  const name = `${entry?.name ?? ''} ${entry?.tvgName ?? ''}`.trim();
  const group = String(entry?.group ?? '');

  for (const text of [name, group]) {
    if (!text) continue;
    for (const rule of RULES) {
      if (rule.pattern.test(text)) return rule.genre;
    }
  }

  return '';
}

/**
 * The group title a lineup entry gets: country first, then genre, so an
 * alphabetical player sorts a country's genres together.
 *
 * @param {string} country ISO-3166 alpha-2
 * @param {string} genre
 * @returns {string}
 */
export function lineupGroup(country, genre) {
  const cc = String(country || '').toUpperCase();
  if (!cc) return genre || 'Other';
  return genre ? `${cc} - ${genre}` : `${cc} - Other`;
}

/**
 * Sort comparator for a lineup: country in the requested order, then genre in
 * GENRES order, then channel name.
 *
 * @param {string[]} countryOrder
 */
export function lineupSorter(countryOrder) {
  const countryRank = new Map(countryOrder.map((c, i) => [c.toUpperCase(), i]));
  const genreRank = new Map(GENRES.map((g, i) => [g, i]));
  const rank = (map, key) => map.get(key) ?? Number.MAX_SAFE_INTEGER;

  return (a, b) =>
    rank(countryRank, (a.tvgCountry || '').toUpperCase()) -
      rank(countryRank, (b.tvgCountry || '').toUpperCase()) ||
    rank(genreRank, a.genre) - rank(genreRank, b.genre) ||
    (a.name || '').localeCompare(b.name || '');
}
