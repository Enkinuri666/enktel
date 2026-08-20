import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  dedupe,
  extractStreamUrls,
  looksLikeIndexPlaylist,
  normalizeCountry,
  normalizeUrl,
  parseExtInf,
  parsePlaylist,
  streamKind,
  toCsv,
  toM3u,
  toText,
  toUrlList,
} from './m3u.mjs';

test('extractStreamUrls finds absolute links in HTML', () => {
  const html = `
    <a href="https://a.example/live/index.m3u8">One</a>
    <video src="http://b.example/x.m3u8?token=abc"></video>
    <p>plain https://c.example/y.mpd here.</p>
  `;
  assert.deepEqual(extractStreamUrls(html), [
    'https://a.example/live/index.m3u8',
    'http://b.example/x.m3u8?token=abc',
    'https://c.example/y.mpd',
  ]);
});

test('extractStreamUrls resolves relative links against the page URL', () => {
  const html = `<a href="/streams/one.m3u8">a</a><a href="//cdn.example/two.m3u8">b</a>`;
  assert.deepEqual(extractStreamUrls(html, 'https://host.example/page.html'), [
    'https://host.example/streams/one.m3u8',
    'https://cdn.example/two.m3u8',
  ]);
});

test('extractStreamUrls decodes escaped URLs and drops duplicates', () => {
  const json = `{"a":"https:\\/\\/x.example\\/a.m3u8?x=1&amp;y=2","b":"https://x.example/a.m3u8?x=1&y=2"}`;
  assert.deepEqual(extractStreamUrls(json), ['https://x.example/a.m3u8?x=1&y=2']);
});

test('extractStreamUrls ignores non-http schemes and empty input', () => {
  assert.deepEqual(extractStreamUrls('rtmp://x.example/a.m3u8'), []);
  assert.deepEqual(extractStreamUrls(''), []);
});

test('parseExtInf reads attributes and the trailing title', () => {
  const e = parseExtInf(
    '#EXTINF:-1 tvg-id="bbc1.uk" tvg-logo="http://x/l.png" group-title="UK, Sport",BBC One HD',
  );
  assert.equal(e.duration, -1);
  assert.equal(e.attrs['tvg-id'], 'bbc1.uk');
  assert.equal(e.attrs['group-title'], 'UK, Sport');
  assert.equal(e.title, 'BBC One HD');
});

test('parseExtInf tolerates a bare duration with no attributes', () => {
  assert.deepEqual(parseExtInf('#EXTINF:-1,Channel 4'), {
    duration: -1,
    attrs: {},
    title: 'Channel 4',
  });
  assert.equal(parseExtInf('not an extinf line'), null);
});

test('parsePlaylist reads channels with their directives', () => {
  const body = [
    '#EXTM3U x-tvg-url="https://epg.example/guide.xml.gz"',
    '#EXTINF:-1 tvg-id="a.us" tvg-logo="https://l/a.png" tvg-country="us" group-title="News",Alpha',
    '#EXTVLCOPT:http-user-agent=CustomAgent/1.0',
    '#EXTVLCOPT:http-referrer=https://ref.example/',
    'https://a.example/alpha/index.m3u8',
    '#EXTINF:-1,Beta',
    '#EXTGRP:Sport',
    'https://b.example/beta.m3u8',
  ].join('\n');

  const { header, entries } = parsePlaylist(body, { sourceId: 's1', sourceUrl: 'https://s1' });

  assert.equal(header['x-tvg-url'], 'https://epg.example/guide.xml.gz');
  assert.equal(entries.length, 2);
  assert.equal(entries[0].name, 'Alpha');
  assert.equal(entries[0].tvgCountry, 'US');
  assert.equal(entries[0].http['user-agent'], 'CustomAgent/1.0');
  assert.equal(entries[0].http.referer, 'https://ref.example/');
  assert.equal(entries[0].sourceId, 's1');
  assert.equal(entries[1].group, 'Sport');
});

test('parsePlaylist flags DRM entries and skips non-URL lines', () => {
  const body = [
    '#EXTM3U',
    '#EXTINF:-1,Guarded',
    '#KODIPROP:inputstream.adaptive.license_key=https://key.example/k',
    'https://d.example/g.mpd',
    '#EXTINF:-1,Broken',
    'not-a-url',
    '#EXTINF:-1,Plain',
    'https://p.example/p.m3u8',
  ].join('\n');

  const { entries } = parsePlaylist(body);
  assert.equal(entries.length, 2);
  assert.equal(entries[0].drm, true);
  assert.equal(entries[1].name, 'Plain');
  assert.equal(entries[1].drm, false);
});

test('parsePlaylist reads #EXTHTTP headers', () => {
  const body = [
    '#EXTM3U',
    '#EXTINF:-1,Hdr',
    '#EXTHTTP:{"User-Agent":"UA/2","Referer":"https://r.example/"}',
    'https://h.example/h.m3u8',
  ].join('\n');

  const [entry] = parsePlaylist(body).entries;
  assert.equal(entry.http['user-agent'], 'UA/2');
  assert.equal(entry.http.referer, 'https://r.example/');
});

test('parsePlaylist falls back to the host when a channel has no title', () => {
  const [entry] = parsePlaylist('#EXTM3U\nhttps://bare.example/s.m3u8').entries;
  assert.equal(entry.name, 'bare.example');
});

test('streamKind classifies by container', () => {
  assert.equal(streamKind('https://x/a.m3u8'), 'hls');
  assert.equal(streamKind('https://x/a.m3u8?token=1'), 'hls');
  assert.equal(streamKind('https://x/play.php?type=m3u8'), 'hls');
  assert.equal(streamKind('https://x/a.mpd'), 'dash');
  assert.equal(streamKind('https://x/a.ts'), 'mpegts');
  assert.equal(streamKind('https://x/list.m3u'), 'playlist');
  assert.equal(streamKind('https://x/play.php?id=9'), 'other');
});

test('looksLikeIndexPlaylist separates indexes from single streams', () => {
  assert.equal(looksLikeIndexPlaylist('https://x/index.country.m3u'), true);
  assert.equal(looksLikeIndexPlaylist('https://x/playlist.m3u8'), true);
  assert.equal(looksLikeIndexPlaylist('https://x/chan/hls-1.m3u8'), false);
});

// The folding rules themselves are covered in countries.test.mjs; this only
// pins the re-export the parser exposes.
test('m3u re-exports normalizeCountry', () => {
  assert.equal(normalizeCountry('uk'), 'GB');
});

test('parsePlaylist normalizes the country attribute', () => {
  const body = '#EXTM3U\n#EXTINF:-1 tvg-country="uk",BBC\nhttps://a/b.m3u8';
  assert.equal(parsePlaylist(body).entries[0].tvgCountry, 'GB');
});

test('normalizeUrl folds host case, default ports and trailing slashes', () => {
  assert.equal(normalizeUrl('HTTP://Example.COM:80/a/'), 'http://example.com/a');
  assert.equal(normalizeUrl('https://example.com:443/a#frag'), 'https://example.com/a');
  assert.equal(normalizeUrl('https://x/a.m3u8?b=1'), 'https://x/a.m3u8?b=1');
  assert.equal(normalizeUrl('  not a url  '), 'not a url');
});

test('dedupe keeps one entry per URL and records every source', () => {
  const merged = dedupe([
    { name: 'A', url: 'https://x/a.m3u8', group: '', tvgId: '', tvgLogo: '', sourceId: 's1' },
    { name: 'A', url: 'HTTPS://X/a.m3u8', group: 'News', tvgId: 'a.us', tvgLogo: '', sourceId: 's2' },
    { name: 'B', url: 'https://x/b.m3u8', group: '', tvgId: '', tvgLogo: '', sourceId: 's1' },
  ]);

  assert.equal(merged.length, 2);
  assert.deepEqual(merged[0].seenIn, ['s1', 's2']);
  assert.equal(merged[0].group, 'News', 'later sources fill in missing metadata');
  assert.equal(merged[0].tvgId, 'a.us');
});

test('toM3u renders attributes and per-channel headers', () => {
  const out = toM3u(
    [
      {
        name: 'Alpha',
        url: 'https://a/x.m3u8',
        group: 'News',
        tvgId: 'a.us',
        tvgLogo: 'https://l/a.png',
        tvgCountry: 'US',
        http: { 'user-agent': 'UA/1' },
      },
    ],
    { epgUrl: 'https://epg/g.xml' },
  );

  assert.equal(
    out,
    '#EXTM3U x-tvg-url="https://epg/g.xml"\n' +
      '#EXTINF:-1 tvg-id="a.us" tvg-logo="https://l/a.png" tvg-country="US" group-title="News",Alpha\n' +
      '#EXTVLCOPT:http-user-agent=UA/1\n' +
      'https://a/x.m3u8\n',
  );
});

test('toM3u output round-trips back through the parser', () => {
  const entries = [
    {
      name: 'Alpha',
      url: 'https://a/x.m3u8',
      group: 'News',
      tvgId: 'a.us',
      tvgName: 'Alpha',
      tvgLogo: '',
      tvgCountry: 'US',
      tvgLanguage: '',
      http: {},
    },
  ];

  const [parsed] = parsePlaylist(toM3u(entries)).entries;
  assert.equal(parsed.name, 'Alpha');
  assert.equal(parsed.url, 'https://a/x.m3u8');
  assert.equal(parsed.group, 'News');
  assert.equal(parsed.tvgId, 'a.us');
  assert.equal(parsed.tvgCountry, 'US');
});

test('text, url-list and csv renderers emit one row per channel', () => {
  const entries = [
    { name: 'Alpha', url: 'https://a/x.m3u8', group: 'News', tvgCountry: 'US', kind: 'hls', sourceId: 's1' },
    { name: 'Be"ta', url: 'https://b/y.m3u8', group: 'Sport', tvgCountry: 'GB', kind: 'hls', sourceId: 's1' },
  ];

  assert.deepEqual(toText(entries).trim().split('\n'), [
    '# name\tgroup\tcountry\turl',
    'Alpha\tNews\tUS\thttps://a/x.m3u8',
    'Be"ta\tSport\tGB\thttps://b/y.m3u8',
  ]);

  assert.equal(toUrlList(entries), 'https://a/x.m3u8\nhttps://b/y.m3u8\n');

  const csv = toCsv(entries).trim().split('\n');
  assert.equal(csv[0], 'name,group,country,language,tvg_id,kind,source,url');
  assert.match(csv[2], /^"Be""ta","Sport"/, 'quotes are doubled');
});
