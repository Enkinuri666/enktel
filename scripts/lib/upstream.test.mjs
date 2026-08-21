import assert from 'node:assert/strict';
import { test } from 'node:test';

import { findPublic, foldEpgId, indexPublic, parseCsvRow } from '../match-upstream.mjs';

test('foldEpgId folds case and the iptv-org feed suffix', () => {
  // The whole reason the id join was worth fixing: these are one channel.
  assert.equal(foldEpgId('7flix.au'), foldEpgId('7Flix.au@SD'));
  assert.equal(foldEpgId('BBCOne.uk@HD'), 'bbcone.uk');
  assert.equal(foldEpgId('  MBC1.ae  '), 'mbc1.ae');
  assert.equal(foldEpgId(''), '');
  assert.equal(foldEpgId(undefined), '');
});

test('parseCsvRow handles quoted cells, commas and escaped quotes', () => {
  assert.deepEqual(parseCsvRow('"1","2","BBC One"'), ['1', '2', 'BBC One']);
  assert.deepEqual(parseCsvRow('"1","","A, B"'), ['1', '', 'A, B']);
  assert.deepEqual(parseCsvRow('"He said ""hi"""'), ['He said "hi"']);
});

const publicEntries = [
  { name: '7flix', url: 'https://example.com/7flix.m3u8', tvgId: '7Flix.au@SD', tvgLogo: 'a.png' },
  { name: 'ABC Kids (720p)', url: 'https://example.com/abckids.m3u8', tvgId: 'ABCKids.au', tvgLogo: '' },
  { name: 'No URL', url: '', tvgId: 'Dropped.au', tvgLogo: '' },
];

test('indexPublic skips entries with no URL', () => {
  const index = indexPublic(publicEntries);
  assert.equal(index.byId.has('dropped.au'), false);
  assert.equal(index.byId.has('7flix.au'), true);
});

test('findPublic matches across id namespaces', () => {
  const index = indexPublic(publicEntries);
  const hit = findPublic({ tvgId: '7flix.au', name: 'SEVEN FLIX' }, index);
  assert.equal(hit.via, 'id');
  assert.equal(hit.entry.url, 'https://example.com/7flix.m3u8');
});

test('findPublic falls back to the normalised name when there is no id', () => {
  const index = indexPublic(publicEntries);
  // "(720p)" and "Kids" casing are both normalised away on each side.
  const hit = findPublic({ tvgId: '', name: 'ABC KIDS' }, index);
  assert.equal(hit.via, 'name');
  assert.equal(hit.entry.url, 'https://example.com/abckids.m3u8');
});

test('findPublic returns null rather than guessing', () => {
  const index = indexPublic(publicEntries);
  // The overwhelmingly common case: a premium channel nobody publishes free.
  assert.equal(findPublic({ tvgId: 'beINSports1.qa', name: 'BEIN SPORTS 1' }, index), null);
  assert.equal(findPublic({ tvgId: '', name: '' }, index), null);
});
