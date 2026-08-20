# scripts/

Maintenance tooling that runs outside the Next.js app and the Android client.
Plain Node ESM, no dependencies beyond the runtime — `node scripts/<name>.mjs`.

## `scrape-m3u8.mjs` — IPTV playlist scraper

Collects `.m3u8` stream URLs from public IPTV indexes, de-duplicates them,
optionally checks that they still respond, and writes the result out as text.

```bash
npm run scrape:m3u8                       # every built-in source
npm run scrape:m3u8 -- --list-sources     # what it reads
npm run scrape:m3u8 -- --country GB,IE --check
npm run scrape:m3u8 -- --crawl https://example.com/page-with-playlist-links
```

### Sources

`--list-sources` prints them. All are publicly published free-to-air / FAST
indexes maintained by the open IPTV community:

| id                  | what it is                                          |
| ------------------- | --------------------------------------------------- |
| `iptv-org`          | iptv-org main index, grouped by category            |
| `iptv-org-country`  | same streams, grouped by country                    |
| `iptv-org-language` | same streams, grouped by language                   |
| `iptv-org-category` | same streams, grouped by genre                      |
| `iptv-org-api`      | iptv-org JSON API — widest coverage, richest metadata |
| `free-tv`           | Free-TV/IPTV community playlist                     |
| `mjh-au-brisbane`   | Brisbane free-to-air, with a matching XMLTV guide   |
| `iptvcat-enktel`    | a saved iptvcat list — AU, XK, BA and HR            |

The overlapping indexes are deliberate: each one knows something the others
don't (a logo, an EPG id, a country), and de-duplication merges those fields
onto a single entry per stream URL.

Point it somewhere else with `--crawl <url>`, repeatable, or `--from <file>`
for a newline-separated list. A URL that returns a playlist is parsed
directly; anything else is treated as a page, mined for `.m3u`/`.m3u8`/`.mpd`
links, and any index playlist among them is followed (one level by default,
`--depth`).

This reads what publishers put on the open web. It does not sign in anywhere,
does not carry credentials, and is not a way to reach a paid panel — keep
private playlist URLs out of `scripts/lib/sources.mjs` and pass them with
`--crawl` if you need to run one through the parser.

### Filtering

Defaults keep HLS only and drop adult, DRM-protected and radio-only entries;
`--all-kinds`, `--include-nsfw`, `--include-drm`, `--include-radio` turn those
off. `--country`, `--group <regex>`, `--name <regex>` and `--limit` narrow it
further. Country codes are folded to ISO-3166 first, so `--country GB` also
matches the feeds that label Britain `UK`.

Filters run *after* de-duplication, so a channel whose country only one source
knows still passes.

### Liveness

`--check` requests the first 2 KB of every stream and, for HLS, confirms the
body really is a playlist — a lot of listed URLs answer 200 with an error page.
Dead entries are dropped unless `--keep-dead` is passed, which keeps them
annotated with the failure instead. `--check-limit n` probes only the first n,
`--concurrency` (default 24) and `--check-timeout` (default 8000 ms) tune the
sweep. Per-channel user agents and referrers from the playlist are replayed on
the probe, since some CDNs need them.

### Output

Into `--out` (default `data/playlists/`), prefixed by `--prefix`:

| file           | contents                                                   |
| -------------- | ---------------------------------------------------------- |
| `*.m3u`        | playlist with tvg attributes and per-channel HTTP headers  |
| `*.txt`        | tab-separated `name · group · country · url`               |
| `*.urls.txt`   | bare stream URLs, one per line                             |
| `*.json`       | full records including probe results                       |
| `*.csv`        | spreadsheet-friendly                                       |
| `*.report.json`| per-source counts, timings, filters, group/country totals   |

Pick a subset with `--formats m3u,urls`.

The generated `.m3u` round-trips through the same attributes the Android
client reads in `tv.enktel.app.data.diag.M3uAttrs`, so anything scraped here
loads in the player unchanged.

## `export-xtream-catalog.mjs` — panel catalog exporter

The public indexes above carry live channels and essentially **no VOD**. A
catalog of ~190k movies and ~75k series does not exist on the open web — it
lives on a provider's panel, and the only way to enumerate it is to ask that
panel with a line entitled to it. That is what this script does: the
documented `player_api.php` actions, with your own credentials.

```bash
export XTREAM_SERVER=http://line.enktel.online
export XTREAM_USERNAME=…  XTREAM_PASSWORD=…
npm run export:catalog -- --countries HR,GB,US,AU \
  --expect-movies 190000 --expect-series 75000
```

Credentials come from `XTREAM_SERVER` / `XTREAM_USERNAME` / `XTREAM_PASSWORD`,
from `--server/--username/--password`, or from `--env-file <path>`. They are
never written to a committed file, and the password is redacted from the
report and from error output.

> **The output is itself a credential.** Every Xtream stream URL embeds the
> line's username and password. `data/catalog/` is gitignored for that reason —
> if you change `--out`, make sure the new location is ignored too.

### What it pulls

| section  | actions                                        | output                              |
| -------- | ---------------------------------------------- | ----------------------------------- |
| `live`   | `get_live_categories`, `get_live_streams`      | `live.m3u`, `live.txt`              |
| `vod`    | `get_vod_categories`, `get_vod_streams`        | `movies.m3u`, `movies.txt`          |
| `series` | `get_series_categories`, `get_series`          | `series.txt`                        |
| episodes | `get_series_info` per series (`--episodes`)    | `episodes.m3u`, `episodes.txt`      |

Plus `catalog.report.json` — per-category counts, totals, timings.

Everything is fetched **per category** rather than in one call: a single
`get_vod_streams` across 190k titles times out on most panels. Results stream
to disk as they arrive, so memory stays flat regardless of catalog size, and
titles listed under several categories are de-duplicated by stream id.

`--episodes` is one request per series — at 75k series that is 75k requests, so
it is opt-in and `--episodes-limit` exists for sampling.

### Countries

`--countries HR,GB,US,AU` filters **live** channels by matching panel category
names (`UK | SPORTS`, `EX-YU HR`, `USA ENTERTAINMENT`) against the patterns in
`scripts/lib/xtream.mjs`. Matching is word-anchored, so `US` does not match
`RUSSIA` and `AU` does not match `AUSTRIA`. Any requested country that matched
no category at all is reported — a silent zero is the failure mode worth
catching. VOD is deliberately not country-filtered: panels organise it by
genre, not by country.

### Expectations

`--expect-movies 190000 --expect-series 75000` checks the export against what
the catalog is supposed to hold and prints a per-section pass/fail, tolerating
`--tolerance` percent under (default 10). `--strict` turns a shortfall into a
non-zero exit, which is what you want in CI: a line downgraded to a package
without the full VOD library shows up as a number, not as a support ticket.

### Stream URLs

Built by the same rules as `pc/src-tauri/src/xtream.rs` and the Android
`StreamUrlResolver` — `/live/{u}/{p}/{id}.m3u8`, `/movie/{u}/{p}/{id}.{ext}`,
`/series/{u}/{p}/{id}.{ext}`, with the same container fallback chain, since
panels routinely lie about `container_extension`. Requests go out with the
VLC user agent the rest of the codebase uses, because the WAFs in front of
these panels treat unknown agents worse.

## `match-epg.mjs` — fill in missing EPG ids

A channel with no `tvg-id` gets no programme data in any player, however good
the guide is. This matches channel names against guide channel lists and
assigns the ids.

```bash
npm run match:epg -- --playlist data/playlists/enktel-core.m3u
npm run match:epg -- --guide https://example.com/epg.xml.gz
```

Two id namespaces, both cheap:

- the **iptv-org channel catalog** (~40k channels with alternate names and
  countries) — the namespace iptv-org's own `tvg-id`s come from;
- any **XMLTV guide** passed with `--guide`, read *only as far as its channel
  list*. XMLTV puts every `<channel>` before every `<programme>`, so the reader
  inflates the stream and abandons it at the first programme — a 200 MB guide
  costs a few hundred KB.

Matching is exact-first on a normalised name (`HRT 1 HD (1080p) [Geo-blocked]`
and `HRT1` both reduce to `hrt 1`), then a Dice coefficient over name tokens
above `--threshold`, default **0.9**. Two rules keep precision up, because a
wrong id is worse than a missing one — the viewer gets confidently incorrect
programme data instead of an obviously empty guide:

- **no cross-country matches** when both sides declare a country;
- **a word that appears on only one side disqualifies the pair if it is a
  number or a place name.** Dice treats every word as equally informative,
  which is how `Hell's Kitchen Germany` scores 0.86 against `Hell's Kitchen`
  and takes a US guide. Adding this cut wrong fuzzy matches from ~50 to 12 on
  the full scrape.

Writes `*.epg.m3u`, `*.epg-matches.txt` (every id assigned, with method and
score, so a wrong one can be found and argued with), `*.epg-unmatched.txt` and
a report.

## `build-lineup.mjs` — genre buckets per country

Turns a scraped playlist into a storefront lineup: `AU - Sports`,
`US - News`, `GB - Documentary`, sorted by country then genre then name.

```bash
npm run build:lineup                       # AU, US, GB, HR
npm run build:lineup -- --genres Sports,News --split
```

Each channel gets three passes, in this order: a missing `tvg-id` is matched
(as above), then a missing `tvg-logo` is filled from the iptv-org logo set
**keyed by that id** — which is why the order matters — then its genre is
classified and the group title is rewritten.

Logos prefer one that is in use, in a raster format (an SVG renders as nothing
in a good few IPTV clients), and the largest of those.

Genre rules live in `scripts/lib/genres.mjs` and are ordered; first match wins.
The order is the design:

- **PPV / Main Events** before Sports — a PPV channel is wall-to-wall sports
  vocabulary. But `main event` is *weak*: it only means PPV on a channel with
  no sports brand on it, or `Sky Sports Main Event` gets filed as pay-per-view.
- **Sports** is brand-led — beIN Sports, Fox Sports, Eurosport, SportKlub,
  Sportska, ESPN, MUTV, Optus Sport, Stan Sport/Events, Paramount+ Sports, Sky
  Sports, TNT, DAZN, SuperSport, Arena Sport, Alkass, AD Sports, Dubai Sports,
  MBC Pro, Kayo, Willow and the league names — since a generic `/sport/`
  pattern misses MUTV and DAZN entirely. The brand has to carry the sports
  word with it: bare `beIN` swept up beIN Movies and beIN Box Office, the same
  way a bare `main event` made Sky Sports Main Event pay-per-view.
- **24/7 Series** before Entertainment: they are entertainment by content, but
  the point of the bucket is that they loop one series forever.
- **Kids** before **Religion**: Al-Majd Kids and Toyor Al-Jannah are religious
  children's channels, and Kids is where a viewer looks for them.
- A sports news channel is **Sports**, not News.

Channels no rule matches go to `--default-genre` (default `Entertainment`) and
are counted separately in the report; `--drop-unclassified` drops them instead.
On the current core set that is 671 of 2,923 — dropping them silently would
delete nearly a quarter of the lineup. It was 786 before the Religion bucket
existed: 115 channels that matched no rule at all were sitting in
Entertainment, which is where 3ABN, EWTN and Iqraa all had to go.

## `build-roster.mjs` — resolve a roster to EPG ids and logos

`build-lineup.mjs` starts from a scraped playlist, which already carries half
the metadata. A **roster** carries none of it: a provider panel publishes a
stream id and whatever name an operator typed into the admin form, and that is
all. `AD-Sports1`. `aljazeera_mubasher`. `OSN_Cinma_2`. `DUBAISPORT1`. None of
those match a catalog name, so without help every one of them gets no EPG id
and no logo, however good the catalog is.

```bash
npm run build:roster                                    # the built-in MENA roster
npm run build:roster -- --playlist some.m3u --prefix mylist
npm run build:roster -- --check-logos                   # probe every logo URL
```

### Rosters carry no credentials

An Xtream stream URL embeds the line's username and password in its path, so a
roster stores the parts that are safe — the panel host, the stream ids, and
everything resolved onto them — and `buildStreamUrl()` substitutes the
credentials at render time from `XTREAM_USERNAME` / `XTREAM_PASSWORD`. The
resolved metadata (`.csv`, `.txt`, `.report.json`) goes to `data/rosters/` and
is committed; the playable `.m3u` goes to the gitignored `data/catalog/` and is
only written when credentials are supplied. A `--playlist` run has nothing to
hide — its URLs came from a file that already had them — so its `.m3u` is
written with the rest.

### Matching a name nobody tidied

`lib/panel-names.mjs` produces the plausible spellings of a panel name and the
same expansion runs over the **catalog** names too. That symmetry is the point:
"Alkass One" and "Alkas-1" only meet if both are put through the same mill.

| rule | example |
| --- | --- |
| camel-case split, brand acronyms kept | `beINSports` → `beIN Sports` |
| glued prefixes an all-caps name hides | `DUBAISPORT1` → `dubai sport 1` |
| Arabic article, split and dropped | `aljazeera` → `al jazeera` → `jazeera` |
| doubled consonants transliteration argues about | `Alkass` → `Alkas` |
| plural fold | `Dubai Sports 1` → `Dubai Sport 1` |
| number words | `Alkass One` → `Alkass 1` |
| trailing country code an indexer appended | `Arena Sport 3 BA` → `Arena Sport 3` |

Each of those is bounded, because each is a way to lose information. Doubled
consonants only fold in tokens of six characters or more — `OOD` folding to
`od` collides with `ODD TV`, and `Iqraa` folding to `Iqra` takes a different
broadcaster. The trailing-code rule only fires on codes the project recognises
as countries, since `HD` is a quality marker and `AD` is Abu Dhabi — dropping
it turns `Nat Geo AD` into `Nat Geo`, which matches the wrong National
Geographic outright. And it is tried last, after every more faithful spelling.

### A logo is cosmetic; an EPG id is not

The two are resolved at different strictness, and that is the design rather
than an accident:

- a **logo** from the wrong regional feed of the right brand is the same
  picture, so artwork is matched at `--logo-threshold` (0.85);
- a **`tvg-id`** from the wrong regional feed is a guide full of programmes
  that are not on, so ids are matched at `--threshold` (0.9) and refused
  outright in four cases, each recorded in the report with its reason:

| reason | what it means |
| --- | --- |
| `ambiguous` | the name is exactly right and means several channels — "Animal Planet" matches fifteen. Keeps the logo, refuses the id. |
| `country-mismatch` | one candidate, in the wrong country. "ANN News" is Syrian here and Indian in the catalog; a different broadcaster, so its artwork is refused too. |
| `timeshift` | a `+2` feed shares its parent's schedule but not its clock. Takes the parent's logo, refuses its id. |
| `unnamed` | the panel published no name. Seven of the MENA roster's entries are called `-1` … `-7`. |

`country-mismatch` only applies to a **roster**, whose countries are curated
and therefore mean something about which broadcaster a channel is. A scraped
playlist's `tvg-country` usually says where a stream can be watched — an
Australian index tags "Fear Factor" `AU` and the catalog files it `US`, and
they are the same channel. Applying roster strictness to the iptvcat list threw
away 51 correct matches out of 200.

### Curated ids

`lib/rosters.mjs` carries an iptv-org id for the 28 channels the matcher cannot
reach on its own — a transliteration it cannot bridge (RT Arabic still
publishes as "Rusiya Al-Yaum"), or a name the catalog files differently. Every
one is checked against the live catalog on each run, so a retired id is
reported (`unknown-curated-id`) rather than silently kept.

The **unresolved** ones are recorded too, with a note saying why: MBC Pro
Sports and Sama Jordan are not in the catalog, Future TV Lebanon closed in
2019, and the panel numbers four French beIN feeds 13-16 where the catalog
names three. "Not in the catalog" is a real answer, and re-deriving it every
few months is waste.

### Where a roster comes from

A roster is written down because nobody publishes it. Two ways to get one
without typing it out:

- **From a line you own** — `npm run export:catalog` enumerates the panel's
  live channels through `player_api.php` and writes `live.txt`, whose stream
  ids and names are exactly a roster's two required fields. The browser player
  at `watch.enktel.tv` is a front end over that same panel and the same login,
  so it is not a second source of channels — it needs the line's credentials
  either way, and the documented API is the better door.
- **From a published list** — `npm run build:roster -- --playlist <file>`
  takes any M3U. Use `npm run scrape:m3u8 -- --crawl <url>` first if the list
  is behind a page rather than at a playlist URL.

### Results

| roster | channels | EPG id | guide-covered | logo |
| --- | --- | --- | --- | --- |
| `enktel-mena` | 159 | 101 (63.5%) | 83 (52.2%) | 109 (68.6%) |
| `enktel-iptvcat` | 200 | 155 (77.5%) | 99 (49.5%) | 165 (82.5%) |

**Guide-covered** is the number that decides whether a viewer sees programmes.
An id nothing publishes a guide for looks like success in a playlist and like
an empty grid on screen, so `guides.json` is checked separately and reported
separately.

## Tests

```bash
npm run test:scripts
```

`node --test` over `scripts/lib/*.test.mjs` — playlist parsing, link
extraction, normalisation, de-duplication, output renderers, the panel client's
retry and auth handling, episode flattening, country matching, panel-name
repair, credential handling and every id-refusal rule. Network calls are
stubbed; the suite makes no requests.
