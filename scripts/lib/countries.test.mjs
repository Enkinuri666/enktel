import assert from 'node:assert/strict';
import { test } from 'node:test';

import { countryOf, inferCountry, matchesCountry, normalizeCountry } from './countries.mjs';

test('normalizeCountry folds the codes feeds disagree on', () => {
  assert.equal(normalizeCountry('uk'), 'GB', 'iptv-org says UK where Free-TV says GB');
  assert.equal(normalizeCountry('GB'), 'GB');
  assert.equal(normalizeCountry('el'), 'GR');
  assert.equal(normalizeCountry('INT'), '', 'not a country');
  assert.equal(normalizeCountry('WORLD'), '');
  assert.equal(normalizeCountry(undefined), '');
});

test('matchesCountry recognises how feeds and panels name groups', () => {
  assert.equal(matchesCountry('UK | SPORTS', ['GB']), true);
  assert.equal(matchesCountry('United Kingdom Entertainment', ['GB']), true);
  assert.equal(matchesCountry('EX-YU | HR', ['HR']), true);
  assert.equal(matchesCountry('Hrvatska', ['HR']), true);
  assert.equal(matchesCountry('Croatia', ['HR']), true);
  assert.equal(matchesCountry('USA | NEWS', ['US']), true);
  assert.equal(matchesCountry('Australia Sport', ['AU']), true);
});

test('matchesCountry accepts the alias on the query side too', () => {
  assert.equal(matchesCountry('United Kingdom', ['UK']), true, '--country UK means GB');
});

test('matchesCountry does not match a country inside another word', () => {
  assert.equal(matchesCountry('RUSSIA', ['US']), false, '"US" must not match inside RUSSIA');
  assert.equal(matchesCountry('AUSTRIA', ['AU']), false, '"AU" must not match inside AUSTRIA');
  assert.equal(matchesCountry('Auto & Vehicles', ['AU']), false);
  assert.equal(matchesCountry('FRANCE', ['GB', 'US', 'AU', 'HR']), false);
});

test('matchesCountry with no filter keeps everything', () => {
  assert.equal(matchesCountry('anything', []), true);
  assert.equal(matchesCountry('anything', undefined), true);
});

test('inferCountry reads a country out of a group title', () => {
  assert.equal(inferCountry('Croatia'), 'HR');
  assert.equal(inferCountry('United Kingdom'), 'GB');
  assert.equal(inferCountry('USA | NEWS'), 'US');
  assert.equal(inferCountry('Brisbane, Australia'), 'AU');
});

test('inferCountry admits when a group says nothing about a country', () => {
  assert.equal(inferCountry('Movies'), '');
  assert.equal(inferCountry('Undefined'), '');
  assert.equal(inferCountry(''), '');
  assert.equal(inferCountry(null), '');
});

test('countryOf trusts the coded attribute over the group name', () => {
  assert.equal(countryOf({ tvgCountry: 'GB', group: 'Croatia', name: 'HRT 1' }), 'GB');
});

test('countryOf falls back to group then name', () => {
  assert.equal(countryOf({ tvgCountry: '', group: 'Croatia', name: 'HRT 1' }), 'HR');
  assert.equal(countryOf({ tvgCountry: '', group: 'General', name: 'BBC One UK' }), 'GB');
  assert.equal(countryOf({ tvgCountry: '', group: 'Movies', name: 'Cinema One' }), '');
  assert.equal(countryOf({}), '');
});
