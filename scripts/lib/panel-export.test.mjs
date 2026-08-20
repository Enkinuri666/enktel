import assert from 'node:assert/strict';
import { test } from 'node:test';

import { isAdult, realEpgId, splitEpisode, splitStreamUrl } from '../import-panel-export.mjs';

test('splitStreamUrl keeps the id and throws the credentials away', () => {
  const parsed = splitStreamUrl('http://line.example/live/myuser/mypass/197100.m3u8');
  assert.deepEqual(parsed, { host: 'line.example', kind: 'live', id: '197100', ext: 'm3u8' });
  // The whole point: nothing in the result can leak the line.
  assert.ok(!JSON.stringify(parsed).includes('myuser'));
  assert.ok(!JSON.stringify(parsed).includes('mypass'));
});

test('splitStreamUrl reads the movie and series forms', () => {
  assert.deepEqual(splitStreamUrl('http://p:8080/movie/u/p/1136879.mkv'), {
    host: 'p:8080',
    kind: 'movie',
    id: '1136879',
    ext: 'mkv',
  });
  assert.equal(splitStreamUrl('http://p/series/u/p/1966342.mkv').kind, 'series');
});

test('splitStreamUrl reads the legacy live form with no kind in the path', () => {
  assert.deepEqual(splitStreamUrl('http://line.example/myuser/mypass/54321'), {
    host: 'line.example',
    kind: 'live',
    id: '54321',
    ext: '',
  });
});

test('splitStreamUrl refuses a URL it does not recognise', () => {
  assert.equal(splitStreamUrl('http://example.com/a/b/c/d/e'), null);
  assert.equal(splitStreamUrl('not a url'), null);
});

test('splitEpisode collapses an episode onto its series', () => {
  assert.deepEqual(splitEpisode('Stuart Fails to Save the Universe S01 E02 Spoiler: Bert Is Magic'), {
    series: 'Stuart Fails to Save the Universe',
    season: 1,
    episode: 2,
  });
  assert.deepEqual(splitEpisode("'Allo 'Allo! S09 E06"), {
    series: "'Allo 'Allo!",
    season: 9,
    episode: 6,
  });
});

test('splitEpisode leaves a title that only looks like one alone', () => {
  assert.equal(splitEpisode('Se7en'), null);
  assert.equal(splitEpisode('The Movie'), null);
  assert.equal(splitEpisode('S01 E01'), null, 'no series name to collapse onto');
});

test('realEpgId rejects the placeholder a panel writes for "no guide"', () => {
  // Carried forward it produces a channel that counts as covered and shows an
  // empty grid.
  assert.equal(realEpgId('dummy.epg'), '');
  assert.equal(realEpgId('DUMMY'), '');
  assert.equal(realEpgId('7flix.au'), '7flix.au');
  assert.equal(realEpgId(''), '');
});

test('isAdult matches the categories a storefront should not surface', () => {
  assert.equal(isAdult('XXX | ✪ FOR ADULTS 2023'), true);
  assert.equal(isAdult('XXX FOR FREE PORNIVEOS'), true);
  assert.equal(isAdult('|AU| ✪ AUSTRALIA'), false);
  assert.equal(isAdult('DOCUMENTARY'), false);
});
