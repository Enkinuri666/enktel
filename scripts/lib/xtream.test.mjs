import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  apiUrl,
  call,
  episodeUrls,
  flattenEpisodes,
  liveUrls,
  login,
  matchesCountry,
  movieUrls,
  normalizeCategory,
  normalizeChannel,
  normalizeMovie,
  normalizeSeries,
  normalizeServer,
  parseYear,
} from './xtream.mjs';

const CREDS = { server: 'http://panel.example:8080', username: 'u', password: 'p' };

/** Stand-in for fetch that answers with one canned body. */
function stubFetch(body, { ok = true, status = 200 } = {}) {
  const calls = [];
  const impl = async (url) => {
    calls.push(url);
    return {
      ok,
      status,
      text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
    };
  };
  impl.calls = calls;
  return impl;
}

test('normalizeServer trims slashes and assumes http', () => {
  assert.equal(normalizeServer('http://a.example:8080/'), 'http://a.example:8080');
  assert.equal(normalizeServer('a.example:8080'), 'http://a.example:8080');
  assert.equal(normalizeServer('https://a.example///'), 'https://a.example');
  assert.throws(() => normalizeServer('  '), /server is required/);
});

test('apiUrl encodes credentials and the action', () => {
  const url = new URL(apiUrl({ ...CREDS, password: 'p@ss word' }, 'get_vod_streams', { category_id: 7 }));
  assert.equal(url.pathname, '/player_api.php');
  assert.equal(url.searchParams.get('username'), 'u');
  assert.equal(url.searchParams.get('password'), 'p@ss word');
  assert.equal(url.searchParams.get('action'), 'get_vod_streams');
  assert.equal(url.searchParams.get('category_id'), '7');
});

test('apiUrl omits the action when there is none', () => {
  assert.equal(new URL(apiUrl(CREDS)).searchParams.has('action'), false);
});

test('call parses JSON and reports a non-JSON body', async () => {
  const list = await call(CREDS, 'get_live_categories', {}, { fetchImpl: stubFetch([{ category_id: '1' }]) });
  assert.deepEqual(list, [{ category_id: '1' }]);

  await assert.rejects(
    call(CREDS, 'get_live_categories', {}, { retries: 0, fetchImpl: stubFetch('<html>nope</html>') }),
    /non-JSON/,
  );
});

test('call retries then gives up with the action in the message', async () => {
  let attempts = 0;
  const flaky = async () => {
    attempts++;
    throw new Error('socket hang up');
  };
  await assert.rejects(
    call(CREDS, 'get_series', {}, { retries: 1, fetchImpl: flaky }),
    /get_series failed: socket hang up/,
  );
  assert.equal(attempts, 2);
});

test('login accepts an active line and rejects the rest', async () => {
  const active = await login(CREDS, {
    fetchImpl: stubFetch({ user_info: { auth: 1, status: 'Active', max_connections: '2' } }),
  });
  assert.equal(active.ok, true);
  assert.equal(active.info.max_connections, '2');

  const rejected = await login(CREDS, { fetchImpl: stubFetch({ user_info: { auth: 0 } }) });
  assert.equal(rejected.ok, false);
  assert.match(rejected.error, /rejected/);

  const expired = await login(CREDS, {
    fetchImpl: stubFetch({ user_info: { auth: 1, status: 'Expired' } }),
  });
  assert.equal(expired.ok, false);
  assert.match(expired.error, /Expired/);

  const empty = await login(CREDS, { fetchImpl: stubFetch({}) });
  assert.equal(empty.ok, false);
});

test('liveUrls puts the preferred container first', () => {
  const hls = liveUrls(CREDS, 42);
  assert.equal(hls[0], 'http://panel.example:8080/live/u/p/42.m3u8');
  assert.equal(hls[1], 'http://panel.example:8080/live/u/p/42.ts');

  const ts = liveUrls(CREDS, 42, false);
  assert.equal(ts[0], 'http://panel.example:8080/live/u/p/42.ts');
});

test('movie and episode URLs widen the container guess without repeats', () => {
  const movie = movieUrls(CREDS, 7, 'mkv');
  assert.equal(movie[0], 'http://panel.example:8080/movie/u/p/7.mkv');
  assert.deepEqual(new Set(movie).size, movie.length, 'no duplicate extensions');
  assert.ok(movie.includes('http://panel.example:8080/movie/u/p/7.mp4'));

  assert.equal(episodeUrls(CREDS, 9, '')[0], 'http://panel.example:8080/series/u/p/9.mp4');
});

test('normalizers flatten the panel field names', () => {
  assert.deepEqual(normalizeCategory({ category_id: 3, category_name: '  UK | SPORT ' }), {
    id: '3',
    name: 'UK | SPORT',
  });

  const channel = normalizeChannel({
    stream_id: '11',
    name: ' Sky Sports ',
    num: '4',
    stream_icon: 'http://l/i.png',
    category_id: '3',
    epg_channel_id: 'sky.uk',
    tv_archive: '1',
    tv_archive_duration: '7',
  });
  assert.deepEqual(channel, {
    streamId: 11,
    name: 'Sky Sports',
    num: 4,
    logo: 'http://l/i.png',
    categoryId: '3',
    epgChannelId: 'sky.uk',
    tvArchive: true,
    archiveDays: 7,
  });

  const movie = normalizeMovie({ stream_id: '5', name: 'Dune', container_extension: null, year: '2021' });
  assert.equal(movie.ext, 'mp4', 'a missing container falls back to mp4');
  assert.equal(movie.year, 2021);
  assert.equal(movie.tmdbId, null);

  const series = normalizeSeries({ series_id: '8', name: 'Fargo', releaseDate: '2014-04-15', tmdb: '60622' });
  assert.equal(series.seriesId, 8);
  assert.equal(series.year, 2014);
  assert.equal(series.tmdbId, 60622);
});

test('parseYear digs a year out of whatever the panel sent', () => {
  assert.equal(parseYear('2019'), 2019);
  assert.equal(parseYear('2019-04-01'), 2019);
  assert.equal(parseYear('The Irishman (2019)'), 2019);
  assert.equal(parseYear(''), null);
  assert.equal(parseYear(null), null);
});

test('flattenEpisodes sorts across seasons and keeps series context', () => {
  const series = { seriesId: 8, name: 'Fargo', categoryId: '2' };
  const episodes = flattenEpisodes(
    {
      episodes: {
        2: [{ id: '21', season: 2, episode_num: '1', title: 'Waiting for Dutch', container_extension: 'mkv' }],
        1: [
          { id: '12', season: 1, episode_num: '2', title: 'The Rooster Prince' },
          { id: '11', season: 1, episode_num: '1', title: 'The Crocodile Dilemma' },
        ],
      },
    },
    series,
  );

  assert.deepEqual(
    episodes.map((e) => `S${e.season}E${e.episode}`),
    ['S1E1', 'S1E2', 'S2E1'],
  );
  assert.equal(episodes[0].seriesName, 'Fargo');
  assert.equal(episodes[0].ext, 'mp4');
  assert.equal(episodes[2].ext, 'mkv');
});

test('flattenEpisodes tolerates a panel with no episode data', () => {
  const series = { seriesId: 1, name: 'X', categoryId: '1' };
  assert.deepEqual(flattenEpisodes({}, series), []);
  assert.deepEqual(flattenEpisodes({ episodes: [] }, series), []);
  assert.deepEqual(flattenEpisodes(null, series), []);
});

// Matching rules live in countries.test.mjs; this pins the re-export the
// exporter filters live categories with, on names shaped like a panel's.
test('xtream re-exports matchesCountry for panel category names', () => {
  assert.equal(matchesCountry('UK | SPORTS', ['GB']), true);
  assert.equal(matchesCountry('EX-YU | HR', ['HR']), true);
  assert.equal(matchesCountry('AU | GENERAL', ['AU']), true);
  assert.equal(matchesCountry('FRANCE | GENERAL', ['GB', 'US', 'AU', 'HR']), false);
});
