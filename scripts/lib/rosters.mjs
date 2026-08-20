/**
 * Curated channel rosters.
 *
 * A *source* (see `sources.mjs`) is a public index the scraper reads. A
 * *roster* is the opposite: a fixed list of channels a particular line
 * carries, which nobody publishes and which has to be written down.
 *
 * ## No credentials here, on purpose
 *
 * An Xtream stream URL embeds the line's username and password in its path —
 * `/live/{username}/{password}/{id}.ts` — which is why `data/catalog/` is
 * gitignored and why `export-xtream-catalog.mjs` takes its credentials from
 * the environment. A roster is the part of that data that *is* safe to keep:
 * the channel list, the stream ids, and everything matched onto them. The
 * credentials are substituted at render time by `buildStreamUrl()` and the
 * rendered playlist goes to a gitignored directory.
 *
 * ## What each field is for
 *
 * - `id`      the panel's stream id — the only part of the URL that varies
 * - `name`    exactly as the panel publishes it, shorthand and typos included,
 *             because that is the string the matcher has to cope with
 * - `country` an editorial hint. Not decoration: "MBC 1" matches four catalog
 *             channels (Emirati, Mauritian, Egyptian, American) and without a
 *             country the matcher takes whichever it saw first.
 * - `channel` an iptv-org channel id, set **only** where the matcher cannot
 *             get there on its own — a transliteration it cannot bridge
 *             ("Rusiya Al-Yaum" is RT Arabic), or a name the catalog files
 *             differently. Every one of these is checked against the catalog
 *             at build time, so a renamed or retired id fails loudly.
 * - `note`    why a channel has a curated id, or why it has none. The
 *             unresolved ones matter as much: "not in the catalog" is a real
 *             answer, and re-deriving it every few months is waste.
 */

/**
 * Build a stream URL for one roster channel.
 *
 * @param {{server: string, template: string}} roster
 * @param {{id: string}} channel
 * @param {{username: string, password: string}} credentials
 * @returns {string}
 */
export function buildStreamUrl(roster, channel, credentials) {
  const server = String(roster.server ?? '').replace(/\/+$/, '');
  return String(roster.template)
    .replace('{server}', server)
    .replace('{username}', encodeURIComponent(credentials.username ?? ''))
    .replace('{password}', encodeURIComponent(credentials.password ?? ''))
    .replace('{id}', String(channel.id));
}

/**
 * The MENA line: 159 channels, mostly Arabic-language, heavy on beIN, OSN,
 * MBC and the Gulf state broadcasters.
 *
 * Seven entries at the end are published by the panel with no name at all
 * (`-1` … `-7`). They are kept rather than dropped: they are real streams on
 * the line, and a roster that quietly loses seven of them is worse than one
 * that says it does not know what they are.
 */
export const MENA_ROSTER = {
  id: 'mena',
  name: 'MENA line — beIN, OSN, MBC and the Gulf broadcasters',
  server: 'http://iptv.am000.tv:8000',
  template: '{server}/live/{username}/{password}/{id}.ts',
  /** Not HLS: this line publishes MPEG-TS, which most players handle anyway. */
  kind: 'mpegts',
  /** Its countries are hand-checked, so one that disagrees is evidence. */
  strictCountry: true,
  channels: [
  { id: "16", name: "beINSports-fr1", country: "QA", channel: "beINSportsFrench1.qa" },
  { id: "17", name: "beINSports-fr2", country: "QA", channel: "beINSportsFrench2.qa" },
  { id: "566", name: "BEINSPORTS-FR3", country: "QA", channel: "beINSportsFrench3.qa" },
  { id: "73", name: "Alkas-1", country: "QA" },
  { id: "405", name: "Alkas-2", country: "QA" },
  { id: "104", name: "Alkas-3", country: "QA" },
  { id: "315", name: "Alkas-4", country: "QA" },
  { id: "89", name: "AD-Sports1", country: "AE" },
  { id: "114", name: "AD-SPORTS2", country: "AE" },
  { id: "90", name: "AD-Sports3", country: "AE" },
  { id: "648", name: "DUBAISPORT1", country: "AE" },
  { id: "646", name: "DubaiSport-3", country: "AE" },
  { id: "647", name: "DUBAISPORT-4", country: "AE", note: "The catalog carries Dubai Sports 1-3 only." },
  { id: "357", name: "EUROSPORT-eng", note: "Eurosport has no English-language feed in the catalog." },
  { id: "40", name: "MBCProSports1", country: "SA", note: "MBC Pro Sports is not in the iptv-org catalog." },
  { id: "39", name: "MBCProSports2", country: "SA", note: "MBC Pro Sports is not in the iptv-org catalog." },
  { id: "383", name: "osn-yahala-hd", country: "AE" },
  { id: "21", name: "OSN-MBC+DRAMA", country: "AE", channel: "MBCDrama.ae", note: "MBC Drama as OSN carries it; same schedule." },
  { id: "24", name: "OSN-Yahala-Shabab", country: "AE" },
  { id: "384", name: "OSN-AlYoum", country: "AE" },
  { id: "153", name: "OSN-CINEMA1", country: "AE" },
  { id: "403", name: "OSN_Cinma_2", country: "AE" },
  { id: "26", name: "OSN-MOVIES-ACTION", country: "AE" },
  { id: "18", name: "OSN-MOVIES-ACTION+2", country: "AE" },
  { id: "25", name: "OSN-MOVIES-HD", country: "AE" },
  { id: "19", name: "OSN-MOVIES-DRAMA", country: "AE" },
  { id: "323", name: "OSN-MOVIES-premier", country: "AE", channel: "OSNMoviesPremiere.ae" },
  { id: "412", name: "OSN-MOVIES-premier2", country: "AE" },
  { id: "27", name: "OSN-Movie-FESTIVAL", country: "AE" },
  { id: "292", name: "OSN-MOVIES-Comedy", country: "AE" },
  { id: "454", name: "OSN-StarMovie", country: "AE" },
  { id: "151", name: "OSN_Starworld", country: "AE" },
  { id: "399", name: "OSN-BoxOffice", country: "AE" },
  { id: "109", name: "OSN-BoxOffice1", country: "AE" },
  { id: "625", name: "OSN-FIGHT-HD", country: "AE" },
  { id: "387", name: "Bein-Movies1", country: "QA", channel: "beINMovies1Premiere.qa" },
  { id: "343", name: "Bein-Movies2", country: "QA", channel: "beINMovies2Action.qa" },
  { id: "534", name: "Bein-Movies3", country: "QA", channel: "beINMovies3Drama.qa" },
  { id: "404", name: "OSN-Disney", country: "AE" },
  { id: "624", name: "OSN-movie-kids", country: "AE" },
  { id: "626", name: "OSN-NATGEOWILD", country: "AE" },
  { id: "82", name: "Nat-Geo-AD", country: "AE" },
  { id: "91", name: "Animal-Planet", note: "Animal Planet has no MENA feed in the catalog." },
  { id: "124", name: "DISCOVERY-CH", note: "Discovery has no MENA feed in the catalog." },
  { id: "33", name: "ART-AFLAM1", country: "SA" },
  { id: "32", name: "ART-AFLAM2", country: "SA" },
  { id: "76", name: "ART-CINEMA", country: "SA" },
  { id: "35", name: "ART-Hekayat1", country: "SA", channel: "ARTHekayat.sa", note: "The catalog files the first of the pair without a number." },
  { id: "34", name: "ART-Hekayat2", country: "SA" },
  { id: "149", name: "EUROSPORT-1", note: "Ambiguous: Eurosport 1 exists per country and none is a MENA feed." },
  { id: "150", name: "EUROSPORT-FR", country: "FR", channel: "Eurosport1.fr" },
  { id: "127", name: "Sharjah-Sport", country: "AE" },
  { id: "126", name: "Sharjah", country: "AE" },
  { id: "141", name: "Palestine", country: "PS" },
  { id: "459", name: "PalestineToday", country: "PS" },
  { id: "147", name: "AL-NASS", country: "EG", channel: "ElnasTV.eg", note: "Al Nas, the Egyptian religious channel." },
  { id: "145", name: "Abu_Dhabi", country: "AE" },
  { id: "390", name: "TRT-arabic", country: "TR", channel: "TRTArabi.tr" },
  { id: "393", name: "Zaytoona", country: "TN", channel: "ZitounaTV.tn" },
  { id: "117", name: "MTV-Libanon", country: "LB", channel: "MTVLebanon.lb" },
  { id: "439", name: "Qatar", country: "QA" },
  { id: "106", name: "CBC-tv", country: "EG" },
  { id: "112", name: "TNNTunisia", country: "TN" },
  { id: "118", name: "Cima", country: "EG" },
  { id: "120", name: "Future", country: "LB", note: "Future TV Lebanon closed in 2019." },
  { id: "92", name: "OTV-Lebanon", country: "LB", channel: "OTV.lb" },
  { id: "102", name: "Syria-ikhbaria", country: "SY" },
  { id: "101", name: "Syria-Aloula", country: "SY" },
  { id: "99", name: "Syria", country: "SY" },
  { id: "94", name: "Nile-Comedy", country: "EG" },
  { id: "95", name: "Nile-cinema", country: "EG" },
  { id: "410", name: "NILE-DRAMA", country: "EG" },
  { id: "65", name: "Syria-Education", country: "SY", channel: "SyrianEducationalTV.sy" },
  { id: "49", name: "ZEE-Aflam", country: "AE" },
  { id: "50", name: "Zee-Alwan", country: "AE" },
  { id: "51", name: "SyrianDrama", country: "SY", channel: "SyriaDrama.sy" },
  { id: "54", name: "Baraem", country: "QA" },
  { id: "56", name: "Rotana-Classic", country: "SA" },
  { id: "57", name: "Rotana-Cinema", country: "SA" },
  { id: "58", name: "Rotana", country: "SA" },
  { id: "42", name: "OOD", note: "Unidentified: OOD matches no catalog channel." },
  { id: "48", name: "MBC-1", country: "AE" },
  { id: "47", name: "MBC-2", country: "AE" },
  { id: "46", name: "MBC-3", country: "AE" },
  { id: "45", name: "MBC-4", country: "AE" },
  { id: "43", name: "MBC-MAX", country: "SA" },
  { id: "44", name: "MBC-DRAMA", country: "AE" },
  { id: "241", name: "MBC-ACTION", country: "AE" },
  { id: "61", name: "LBC", country: "LB", channel: "LBCInternational.lb", note: "The catalog's bare \"LBC\" is the Saudi-owned pan-Arab channel; a Lebanese line listing it beside MTV Lebanon and OTV means LBCI." },
  { id: "111", name: "Iraqiya", country: "IQ", channel: "AlIraqia.iq" },
  { id: "435", name: "Baghdad", country: "IQ", note: "Ambiguous: Al-Baghdadia 1/2 and Hona Baghdad all answer to Baghdad." },
  { id: "148", name: "Iraqia-Sport", country: "IQ" },
  { id: "612", name: "AlSharqiya", country: "IQ" },
  { id: "396", name: "Sharqiya-News", country: "IQ" },
  { id: "63", name: "Dubai-One", country: "AE" },
  { id: "64", name: "DUBAI", country: "AE" },
  { id: "67", name: "Cbc-drama", country: "EG" },
  { id: "71", name: "alarabiya", country: "AE" },
  { id: "74", name: "AL-MANAR", country: "LB" },
  { id: "81", name: "ROYA", country: "JO" },
  { id: "83", name: "Jordan-tv", country: "JO" },
  { id: "445", name: "Sama-Jordan", country: "JO", note: "Sama Jordan is not in the iptv-org catalog." },
  { id: "346", name: "Jordan-Sport", country: "JO" },
  { id: "155", name: "ANN-news", country: "SY", note: "The catalog's ANN News is an unrelated Indian channel; the Arab News Network is not in it." },
  { id: "87", name: "AL-JAZEERA", country: "QA" },
  { id: "88", name: "aljazeera_mubasher", country: "QA" },
  { id: "140", name: "NBN", country: "LB" },
  { id: "100", name: "BBC-Arabia", country: "GB", channel: "BBCArabic.uk" },
  { id: "446", name: "Saudi-Quran", country: "SA", channel: "AlQuranAlKareemTV.sa" },
  { id: "72", name: "Al-Hadath", country: "SA" },
  { id: "488", name: "Oman", country: "OM" },
  { id: "486", name: "MTV", note: "Ambiguous: MTV exists in a dozen countries." },
  { id: "628", name: "Quran-TV", note: "Ambiguous: several unrelated Quran TV channels." },
  { id: "629", name: "Quran-Alfateh", note: "Quran Al-Fateh is not in the iptv-org catalog." },
  { id: "107", name: "Mecca", country: "SA", channel: "MakkahTV.sa" },
  { id: "115", name: "IQRAA-TV", country: "SA", channel: "IqraaArabic.sa" },
  { id: "444", name: "Al-Majd", country: "SA", channel: "AlMajdPublicChannel.sa", note: "The general Al-Majd channel, not one of its strands." },
  { id: "425", name: "Almajd-Quran", country: "SA", channel: "AlMajdHolyQuran.sa" },
  { id: "421", name: "MAJD-KIDS", country: "SA" },
  { id: "483", name: "Fatafeat", country: "AE" },
  { id: "437", name: "Toyor-AlJanah", country: "JO" },
  { id: "119", name: "Jeem", country: "QA" },
  { id: "80", name: "Spacetoon", country: "AE", channel: "SpacetoonArabic.ae" },
  { id: "68", name: "CN-ARABIA", country: "AE", channel: "CartoonNetworkArabic.ae" },
  { id: "146", name: "AJYAL", country: "PS" },
  { id: "440", name: "LCD-Aflam", note: "Unidentified: LCD Aflam matches no catalog channel." },
  { id: "450", name: "Russia-Al-Yaum", country: "RU", channel: "RTArabic.ru", note: "RT Arabic, published under its former name Rusiya Al-Yaum." },
  { id: "419", name: "ADTV", country: "AE" },
  { id: "442", name: "Russia-Today", country: "RU" },
  { id: "293", name: "BeinSport-Sd1", country: "QA" },
  { id: "294", name: "BeinSport-Sd2", country: "QA" },
  { id: "295", name: "BeinSport-Sd3", country: "QA" },
  { id: "296", name: "BeinSport-Sd4", country: "QA" },
  { id: "297", name: "BeinSport-Sd5", country: "QA" },
  { id: "298", name: "BeinSport-Sd6", country: "QA" },
  { id: "299", name: "BeinSport-Sd7", country: "QA" },
  { id: "300", name: "BeinSport-Sd8", country: "QA" },
  { id: "301", name: "BeinSport-Sd9", country: "QA", note: "The catalog carries beIN Sports 1-8; there is no 9." },
  { id: "302", name: "BeinSport-Sd10", country: "QA", note: "The catalog carries beIN Sports 1-8; there is no 10." },
  { id: "575", name: "EN:BeinSport-11", country: "QA", note: "The panel numbers its English feeds 11-12; the catalog names them English 1-3, and the two numberings do not line up." },
  { id: "577", name: "EN:BeinSport-12", country: "QA", note: "The panel numbers its English feeds 11-12; the catalog names them English 1-3, and the two numberings do not line up." },
  { id: "632", name: "FR:BEINSPORT-13", country: "QA", note: "The panel numbers four French feeds 13-16; the catalog carries French 1-3." },
  { id: "633", name: "FR:BEINSPORT-14", country: "QA", note: "The panel numbers four French feeds 13-16; the catalog carries French 1-3." },
  { id: "634", name: "FR:BEINSPORT-15", country: "QA", note: "The panel numbers four French feeds 13-16; the catalog carries French 1-3." },
  { id: "635", name: "FR:BEINSPORT-16", country: "QA", note: "The panel numbers four French feeds 13-16; the catalog carries French 1-3." },
  { id: "441", name: "Sky-Atlantic", country: "GB", note: "Sky Atlantic exists per country; none is a MENA feed." },
  { id: "443", name: "SkySport-1", note: "Sky Sports 1 was renamed in 2017 and has no successor id." },
  { id: "422", name: "KANAL7-HD", country: "TR" },
  { id: "627", name: "KIDS-MOVIE1", note: "Panel-local branding, not a published channel." },
  { id: "639", name: "KIDS-MOVIE2", note: "Panel-local branding, not a published channel." },
  { id: "640", name: "KIDS-MOVIE3", note: "Panel-local branding, not a published channel." },
  { id: "641", name: "KIDS-MOVIE4", note: "Panel-local branding, not a published channel." },
  { id: "592", name: "-1", note: "Unnamed on the panel." },
  { id: "424", name: "-2", note: "Unnamed on the panel." },
  { id: "423", name: "-3", note: "Unnamed on the panel." },
  { id: "608", name: "-4", note: "Unnamed on the panel." },
  { id: "609", name: "-5", note: "Unnamed on the panel." },
  { id: "610", name: "-6", note: "Unnamed on the panel." },
  { id: "611", name: "-7", note: "Unnamed on the panel." },  ],
};

/** Every roster, by id. */
export const ROSTERS = [MENA_ROSTER];

/**
 * @param {string} id
 * @returns {typeof MENA_ROSTER}
 */
export function selectRoster(id) {
  const roster = ROSTERS.find((r) => r.id === id);
  if (!roster) {
    throw new Error(`Unknown roster "${id}". Known: ${ROSTERS.map((r) => r.id).join(', ')}.`);
  }
  return roster;
}

// ---- Rendering ------------------------------------------------------------

const CSV_COLUMNS = [
  'stream_id',
  'panel_name',
  'name',
  'country',
  'language',
  'genre',
  'tvg_id',
  'id_via',
  'id_score',
  'tvg_logo',
  'logo_via',
  'guide_sites',
  'unresolved_reason',
  'note',
];

/**
 * The roster as a spreadsheet — every resolved field, and how it was decided.
 *
 * Deliberately carries `id_via` and `unresolved_reason` alongside the answer:
 * a table of ids with no provenance cannot be audited, and these ids are part
 * hand-written.
 *
 * @param {Array<object>} records
 * @returns {string}
 */
export function toRosterCsv(records) {
  const cell = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const rows = records.map((r) =>
    [
      r.id,
      r.name,
      r.display,
      r.country,
      r.language,
      r.genre,
      r.tvgId,
      r.idVia,
      r.idScore || '',
      r.tvgLogo,
      r.logoVia,
      (r.guideSites ?? []).join(' '),
      r.idReason,
      r.note ?? '',
    ]
      .map(cell)
      .join(','),
  );
  return `${[CSV_COLUMNS.join(','), ...rows].join('\n')}\n`;
}

/**
 * The roster as a readable table, one channel per line.
 *
 * @param {Array<object>} records
 * @returns {string}
 */
export function toRosterText(records) {
  const rows = records.map((r) =>
    [r.id, r.display, r.genre, r.country, r.tvgId || '-', r.tvgLogo ? 'logo' : '-'].join('\t'),
  );
  return `${['# stream_id\tname\tgenre\tcountry\ttvg_id\tlogo', ...rows].join('\n')}\n`;
}
