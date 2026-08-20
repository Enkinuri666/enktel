import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  buildIndex,
  channelBlock,
  countryFromId,
  extractChannels,
  matchChannel,
  nameTokens,
  normalizeName,
  similarity,
} from './xmltv.mjs';

const GUIDE = `<?xml version="1.0" encoding="UTF-8"?>
<tv generator-info-name="test">
  <channel id="HTV1.HD.hr">
    <display-name lang="hr">HTV1 HD</display-name>
    <display-name lang="hr">HRT 1</display-name>
    <icon src="https://logo/hrt1.png" />
  </channel>
  <channel id="BBCOne.uk">
    <display-name>BBC One</display-name>
  </channel>
  <channel id="Sport1.de">
    <display-name><![CDATA[Sport 1 & More]]></display-name>
  </channel>
  <programme start="20260101000000" channel="BBCOne.uk">
    <title>Never read this far</title>
  </programme>
</tv>`;

test('channelBlock stops at the first programme', () => {
  const block = channelBlock(GUIDE);
  assert.ok(block.includes('BBCOne.uk'));
  assert.ok(!block.includes('Never read this far'));
  assert.equal(channelBlock('<tv></tv>'), '<tv></tv>', 'a guide with no programmes is kept whole');
});

test('extractChannels reads ids, every display name, and the icon', () => {
  const channels = extractChannels(GUIDE);
  assert.equal(channels.length, 3);
  assert.deepEqual(channels[0], {
    id: 'HTV1.HD.hr',
    names: ['HTV1 HD', 'HRT 1'],
    icon: 'https://logo/hrt1.png',
  });
  assert.deepEqual(channels[2].names, ['Sport 1 & More'], 'CDATA and entities are decoded');
});

test('extractChannels survives a guide with nothing in it', () => {
  assert.deepEqual(extractChannels(''), []);
  assert.deepEqual(extractChannels('<tv></tv>'), []);
  assert.deepEqual(extractChannels('<channel><display-name>No id</display-name></channel>'), []);
});

test('normalizeName strips quality, annotations and accents', () => {
  assert.equal(normalizeName('HRT 1 HD (1080p) [Geo-blocked]'), 'hrt 1');
  assert.equal(normalizeName('HRT1'), 'hrt 1', 'glued digits are split so both spellings agree');
  assert.equal(normalizeName('Nova TV Ⓢ'), 'nova');
  assert.equal(normalizeName('Sky Sports Main Event FHD'), 'sky sports main event');
  assert.equal(normalizeName('Télé-Québec'), 'tele quebec');
  assert.equal(normalizeName('  '), '');
});

test('nameTokens splits a normalized name', () => {
  assert.deepEqual(nameTokens('BBC One HD'), ['bbc', 'one']);
  assert.deepEqual(nameTokens('(1080p)'), []);
});

test('similarity rewards shared words and ignores order', () => {
  assert.equal(similarity('BBC One', 'BBC One HD'), 1);
  assert.equal(similarity('BBC One', 'One BBC'), 1);
  assert.ok(similarity('BBC One', 'BBC Two') < 0.6, 'a different number is a different channel');
  assert.equal(similarity('BBC One', ''), 0);
});

test('countryFromId reads the suffix convention', () => {
  assert.equal(countryFromId('BBCOne.uk'), 'GB', 'folded to ISO so it matches tvg-country="GB"');
  assert.equal(countryFromId('HTV1.HD.hr'), 'HR');
  assert.equal(countryFromId('SomeChannel'), '');
  assert.equal(countryFromId(''), '');
});

test('matchChannel takes an exact normalized name first', () => {
  const index = buildIndex(extractChannels(GUIDE));
  const match = matchChannel({ name: 'HRT 1 (1080p)', tvgCountry: 'HR' }, index);
  assert.equal(match.id, 'HTV1.HD.hr');
  assert.equal(match.via, 'exact');
  assert.equal(match.score, 1);
});

test('matchChannel falls back to fuzzy above the threshold', () => {
  const index = buildIndex([{ id: 'SkySportsMainEvent.uk', names: ['Sky Sports Main Event'] }]);

  const loose = matchChannel({ name: 'Sky Sports Main Event HD', tvgCountry: '' }, index);
  assert.equal(loose.id, 'SkySportsMainEvent.uk');

  const unrelated = matchChannel({ name: 'Cartoon Network', tvgCountry: '' }, index);
  assert.equal(unrelated, null);
});

test('matchChannel refuses a match that differs by a place name', () => {
  const index = buildIndex([{ id: 'HellsKitchen.us', names: ["Hell's Kitchen"] }]);

  // Scores 0.86 on words alone, but "Germany" is the whole point of the name.
  assert.equal(matchChannel({ name: "Hell's Kitchen Germany (1080p)" }, index, { threshold: 0.8 }), null);
  assert.equal(matchChannel({ name: "Hell's Kitchen (1080p)" }, index).id, 'HellsKitchen.us');
});

test('matchChannel refuses a match that differs by a number', () => {
  const index = buildIndex([{ id: 'PlusBelleLaVie.fr', names: ['Plus Belle la Vie'] }]);
  assert.equal(matchChannel({ name: 'Plus Belle la Vie 2' }, index, { threshold: 0.8 }), null);
});

test('matchChannel will not match across a declared country boundary', () => {
  const index = buildIndex([
    { id: 'Sport1.de', names: ['Sport 1'] },
    { id: 'Sport1.hr', names: ['Sport 1'] },
  ]);

  const croatian = matchChannel({ name: 'Sport 1', tvgCountry: 'HR' }, index);
  assert.equal(croatian.id, 'Sport1.hr', 'the country hint picks between identical names');

  const french = matchChannel({ name: 'Sport 1 France', tvgCountry: 'FR' }, index);
  assert.equal(french, null, 'no cross-country fuzzy match');
});

test('matchChannel uses tvg-name when the display name does not match', () => {
  const index = buildIndex([{ id: 'BBCOne.uk', names: ['BBC One'] }]);
  const match = matchChannel({ name: 'Channel 4001', tvgName: 'BBC One', tvgCountry: 'GB' }, index);
  assert.equal(match.id, 'BBCOne.uk');
});

test('buildIndex fills country from the id when none is given', () => {
  const index = buildIndex(extractChannels(GUIDE));
  assert.equal(index.size, 3);
  assert.equal(index.all.find((c) => c.id === 'BBCOne.uk').country, 'GB');
});
