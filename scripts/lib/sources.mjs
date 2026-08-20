/**
 * Where the scraper looks.
 *
 * Every entry here is a publicly published index of free-to-air / FAST
 * channels — the same lists the open IPTV community maintains. Nothing in
 * this file points at a subscription panel, and nothing here ships
 * credentials. Add your own source with `--crawl <url>` rather than putting
 * anything private in a committed file.
 *
 * kind:
 *   'm3u'          plain M3U/M3U8 playlist to parse
 *   'iptv-org-api' the iptv-org JSON API (streams joined to channel metadata)
 *   'page'         an HTML page to scrape for playlist links
 */
export const SOURCES = [
  {
    id: 'iptv-org',
    name: 'iptv-org — main index',
    kind: 'm3u',
    url: 'https://iptv-org.github.io/iptv/index.m3u',
    note: 'Every stream the project tracks, grouped by category.',
  },
  {
    id: 'iptv-org-country',
    name: 'iptv-org — grouped by country',
    kind: 'm3u',
    url: 'https://iptv-org.github.io/iptv/index.country.m3u',
    note: 'Same streams as the main index with country group titles.',
  },
  {
    id: 'iptv-org-language',
    name: 'iptv-org — grouped by language',
    kind: 'm3u',
    url: 'https://iptv-org.github.io/iptv/index.language.m3u',
    note: 'Same streams with language group titles.',
  },
  {
    id: 'iptv-org-category',
    name: 'iptv-org — grouped by category',
    kind: 'm3u',
    url: 'https://iptv-org.github.io/iptv/index.category.m3u',
    note: 'Same streams with genre group titles.',
  },
  {
    id: 'iptv-org-api',
    name: 'iptv-org — JSON API',
    kind: 'iptv-org-api',
    url: 'https://iptv-org.github.io/api/streams.json',
    note: 'Widest coverage; carries quality, user-agent and referrer per stream.',
  },
  {
    id: 'free-tv',
    name: 'Free-TV/IPTV',
    kind: 'm3u',
    url: 'https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8',
    note: 'Community playlist of publicly available channels.',
  },
  {
    id: 'mjh-au-brisbane',
    name: 'matthuisman — Australia / Brisbane',
    kind: 'm3u',
    // The directory publishes the same 245 channels in several flavours. The
    // `tvh-` and `kodi-` variants wrap each stream in a `pipe://ffmpeg …`
    // command for those specific players, which is not a URL and not
    // something a normal client can open — `raw` is the plain-HTTP build, and
    // the only one worth scraping. It carries free-to-air TV and radio
    // together; radio entries are tagged `radio="true"` and so need
    // --include-radio to survive the default filters.
    url: 'https://i.mjh.nz/au/Brisbane/raw.m3u8',
    epgUrl: 'https://i.mjh.nz/au/Brisbane/epg.xml.gz',
    // Its group titles say "Brisbane", which no country pattern matches and
    // no feed here sets tvg-country. Without this the whole list would be
    // filtered out of an --country AU run.
    country: 'AU',
    note: 'Brisbane free-to-air TV (184) and radio (61), with a matching EPG.',
  },
];

/** Companion endpoints for the JSON source, joined in for channel metadata. */
export const IPTV_ORG_API = {
  channels: 'https://iptv-org.github.io/api/channels.json',
  logos: 'https://iptv-org.github.io/api/logos.json',
};

/**
 * @param {string[]} ids
 * @returns {typeof SOURCES}
 */
export function selectSources(ids) {
  if (!ids?.length) return SOURCES;
  const known = new Map(SOURCES.map((s) => [s.id, s]));
  const picked = [];
  for (const id of ids) {
    const source = known.get(id);
    if (!source) {
      throw new Error(
        `Unknown source "${id}". Known: ${[...known.keys()].join(', ')} (or use --crawl <url>).`,
      );
    }
    picked.push(source);
  }
  return picked;
}
