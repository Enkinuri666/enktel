import assert from 'node:assert/strict';
import { test } from 'node:test';

import { REASONS, createResolver, exactCandidates } from './resolve.mjs';
import { buildIndex } from './xmltv.mjs';

/** A catalog small enough to reason about, shaped like iptv-org's. */
const CATALOG = [
  { id: 'MBC1.ae', names: ['MBC 1'], country: 'AE' },
  { id: 'MBC1.mu', names: ['MBC 1'], country: 'MU' },
  { id: 'AlkassOne.qa', names: ['Alkass One'], country: 'QA' },
  { id: 'AbuDhabiSports1.ae', names: ['Abu Dhabi Sports 1', 'AD Sports 1'], country: 'AE' },
  { id: 'OSNMoviesAction.ae', names: ['OSN Movies Action'], country: 'AE' },
  { id: 'ANNNews.in', names: ['ANN News'], country: 'IN' },
  { id: 'FearFactor.us', names: ['Fear Factor'], country: 'US' },
];

const LOGOS = new Map(CATALOG.map((c) => [c.id, `https://logos.example/${c.id}.png`]));
const GUIDES = new Map([['MBC1.ae', ['shahid.mbc.net']]]);

const resolver = (opts) =>
  createResolver({ channels: CATALOG, logos: LOGOS, guideSites: GUIDES, ...opts }).resolve;

test('a country hint picks between two channels of the same name', () => {
  const r = resolver()({ name: 'MBC-1', tvgCountry: 'AE' });
  assert.equal(r.tvgId, 'MBC1.ae');
  assert.equal(r.idVia, 'exact');
  assert.deepEqual(r.guideSites, ['shahid.mbc.net']);
});

test('without a hint the same name is refused rather than guessed', () => {
  const r = resolver()({ name: 'MBC-1' });
  assert.equal(r.tvgId, '', 'picking the first of two is a coin flip');
  assert.equal(r.idReason, REASONS.AMBIGUOUS);
  assert.deepEqual(r.ambiguousWith.sort(), ['MBC1.ae', 'MBC1.mu']);
});

test('an ambiguous channel still gets the brand logo', () => {
  // The logos are the same picture whichever regional feed it is.
  assert.ok(resolver()({ name: 'MBC-1' }).tvgLogo, 'artwork is cosmetic, an id is not');
});

test('panel shorthand reaches the catalog spelling', () => {
  assert.equal(resolver()({ name: 'Alkas-1', tvgCountry: 'QA' }).tvgId, 'AlkassOne.qa');
  assert.equal(resolver()({ name: 'AD-Sports1', tvgCountry: 'AE' }).tvgId, 'AbuDhabiSports1.ae');
});

test('the catalog name replaces the panel shorthand once the channel is known', () => {
  assert.equal(resolver()({ name: 'Alkas-1', tvgCountry: 'QA' }).display, 'Alkass One');
});

test('a timeshift feed takes the logo but not the id', () => {
  const r = resolver()({ name: 'OSN-MOVIES-ACTION+2', tvgCountry: 'AE' });
  assert.equal(r.timeshift, 2);
  assert.equal(r.tvgId, '', 'the parent guide would be two hours out');
  assert.equal(r.idReason, REASONS.TIMESHIFT);
  assert.equal(r.tvgLogo, LOGOS.get('OSNMoviesAction.ae'));
});

test('strictCountry refuses an exact match from the wrong country', () => {
  // The Arab News Network is not the Indian ANN News.
  const r = resolver({ strictCountry: true })({ name: 'ANN-news', tvgCountry: 'SY' });
  assert.equal(r.tvgId, '');
  assert.equal(r.idReason, REASONS.COUNTRY);
  assert.equal(r.rejected, 'ANNNews.in');
  assert.equal(r.tvgLogo, '', 'a different broadcaster, so its artwork is wrong too');
});

test('without strictCountry the same match is kept', () => {
  // A playlist's tvg-country says where a stream can be watched, not where the
  // channel is from: an AU index tags "Fear Factor" AU and it is still the US
  // channel.
  const r = resolver()({ name: 'Fear Factor', tvgCountry: 'AU' });
  assert.equal(r.tvgId, 'FearFactor.us');
});

test('a curated id is used as given', () => {
  const r = resolver()({ name: 'anything at all', channel: 'MBC1.ae' });
  assert.equal(r.tvgId, 'MBC1.ae');
  assert.equal(r.idVia, 'curated');
});

test('a curated id the catalog no longer has is reported, not used', () => {
  const r = resolver()({ name: 'MBC-1', tvgCountry: 'AE', channel: 'Retired.ae' });
  assert.equal(r.tvgId, '', 'a dead hand-written id must not be silently kept');
  assert.equal(r.idReason, REASONS.UNKNOWN_ID);
});

test('a name that is not a name resolves to nothing', () => {
  const r = resolver()({ name: '-4' });
  assert.equal(r.tvgId, '');
  assert.equal(r.idReason, REASONS.UNNAMED);
});

test('exactCandidates reports the choice instead of making it', () => {
  const index = buildIndex(CATALOG);
  assert.deepEqual(exactCandidates(index, ['MBC 1'], '').ids.sort(), ['MBC1.ae', 'MBC1.mu']);
  assert.deepEqual(exactCandidates(index, ['MBC 1'], 'AE'), { ids: ['MBC1.ae'], narrowed: true });
  assert.deepEqual(exactCandidates(index, ['Nothing Here'], ''), { ids: [], narrowed: false });
});

test('a country hint that matches nothing does not narrow anything away', () => {
  const index = buildIndex(CATALOG);
  const { ids, narrowed } = exactCandidates(index, ['MBC 1'], 'GB');
  assert.equal(narrowed, false);
  assert.equal(ids.length, 2, 'still ambiguous rather than silently empty');
});

test('a name in a non-Latin script is a name', () => {
  // /[a-z]/ would file "الجزيرة" alongside "-1" as unnamed.
  const r = resolver()({ name: 'الجزيرة' });
  assert.notEqual(r.idReason, REASONS.UNNAMED);
});

test('an unnamed channel keeps the string the panel published', () => {
  // "-1" cleans up to "1", which reads as a channel number it is not.
  assert.equal(resolver()({ name: '-1' }).display, '-1');
});
