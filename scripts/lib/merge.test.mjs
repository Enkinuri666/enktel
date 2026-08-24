import assert from 'node:assert/strict';
import { test } from 'node:test';

import { cleanName, cleanUrl, groupCountry, matchKey, mergeKey, normalizeCc } from './merge.mjs';

/**
 * Both false matches below were real, caught by reading the merge report
 * rather than by anything in the build. Each would have replaced a working
 * channel's URL with an unrelated stream and left the lineup parsing fine.
 */
test('a name is not confused with a different channel that shares a word', () => {
  // "Bravo! TV" is a Croatian music channel; "Bravo" is US reality.
  assert.notEqual(matchKey('Bravo! TV'), matchKey('Bravo'));
  // "TV Nova" is a small local station; "Nova TV" is the national broadcaster.
  assert.notEqual(matchKey('TV Nova'), matchKey('Nova TV'));
});

test('the same channel matches across the tags a list adds', () => {
  assert.equal(matchKey('HRT 1 HD'), matchKey('HRT 1 (720p)'));
  assert.equal(matchKey('SBTV'), matchKey('SBTV (1080p) [Not 24/7]'));
  assert.equal(matchKey('Extra TV'), matchKey('Extra TV (1080p)'));
  assert.equal(matchKey('Bravo! kidsTV'), matchKey('Bravo! Kids TV'));
});

/** Croatian broadcasters stylise an "a" as "@". Nov@ TV is Nova TV. */
test('a stylised @ reads as the letter it stands for', () => {
  assert.equal(matchKey('Nov@ TV'), matchKey('Nova TV'));
  assert.equal(matchKey('Nov@ TV'), 'novatv');
});

test('accents do not split a channel in two', () => {
  assert.equal(matchKey('Nová TV'), matchKey('Nova TV'));
});

test('country is half the key, because names repeat across borders', () => {
  assert.notEqual(mergeKey('HR', 'Euronews'), mergeKey('RS', 'Euronews'));
  assert.equal(mergeKey('HR', 'HRT 1 HD'), mergeKey('HR', 'HRT 1 (720p)'));
});

test('the shorthand these lists use maps onto ISO codes', () => {
  assert.equal(normalizeCc('BH'), 'BA');
  assert.equal(normalizeCc('SR'), 'RS');
  assert.equal(normalizeCc('hr'), 'HR');
  assert.equal(normalizeCc('', 'HR'), 'HR');
});

test('a group yields the country it belongs to', () => {
  assert.equal(groupCountry('HR - Music'), 'HR');
  assert.equal(groupCountry('US - Reality'), 'US');
  assert.equal(groupCountry(''), '');
});

test('annotations come off the name before it is classified', () => {
  // Fed in whole, "[Not 24/7]" put live local stations into "24/7 Series".
  assert.equal(cleanName('OTV (720p) [Not 24/7]'), 'OTV');
  assert.equal(cleanName('Klasik TV (576p) [Not 24/7]'), 'Klasik TV');
  assert.equal(cleanName('  HRT 1 HD  '), 'HRT 1 HD');
});

test('the aggregator marker comes off a URL so one stream stays one channel', () => {
  assert.equal(
    cleanUrl('https://live.leveex.hr/hls/live.m3u8?checkedby:iptvcat.net'),
    'https://live.leveex.hr/hls/live.m3u8',
  );
  assert.equal(
    cleanUrl('https://pool.alter-media.hr:1936/live/myStream/playlist.m3u8?DVR=&checkedby:iptvcat.net'),
    'https://pool.alter-media.hr:1936/live/myStream/playlist.m3u8?DVR=',
  );
  // A URL that carries real parameters keeps every one of them.
  assert.equal(
    cleanUrl('https://str.gledamsport.com/proxy.php?c=nova&path=mono.m3u8&type=playlist'),
    'https://str.gledamsport.com/proxy.php?c=nova&path=mono.m3u8&type=playlist',
  );
});
