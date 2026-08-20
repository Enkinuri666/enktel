import assert from 'node:assert/strict';
import { test } from 'node:test';

import { GENRES, classifyGenre, lineupGroup, lineupSorter } from './genres.mjs';

const genreOf = (name, group = '') => classifyGenre({ name, group });

test('the named sports brands all land in Sports', () => {
  for (const name of [
    'beIN Sports 1',
    'BeIN SPORTS MAX 4',
    'FOX Sports 503',
    'Eurosport 2',
    'SportKlub 1 HD',
    'Sportska Televizija',
    'ESPN Deportes',
    'MUTV',
    'Optus Sport 1',
    'Stan Sport 1',
    'Stan Events 3',
    'Paramount+ Sports',
    'Sky Sports Main Event',
    'TNT Sports 1',
    'DAZN 1',
    'SuperSport Arena',
    'Arena Sport 1 HR',
    'Premier Sports 2',
    'Kayo Sports',
    'Willow Cricket',
  ]) {
    assert.equal(genreOf(name), 'Sports', `${name} should be Sports`);
  }
});

test('a generic sports word is enough when no brand matches', () => {
  assert.equal(genreOf('Rugby Channel'), 'Sports');
  assert.equal(genreOf('Football Now'), 'Sports');
  assert.equal(genreOf('Undefined Channel', 'Sports'), 'Sports', 'the group is a fallback');
});

test('PPV wins over Sports because a PPV channel reads as sport', () => {
  assert.equal(genreOf('ESPN+ PPV Main Event'), 'PPV / Main Events');
  assert.equal(genreOf('UFC Fight Night PPV'), 'PPV / Main Events');
  assert.equal(genreOf('WWE Main Event'), 'PPV / Main Events');
  assert.equal(genreOf('Boxing PPV 3'), 'PPV / Main Events');
});

test('24/7 series streams are their own bucket, not Entertainment', () => {
  assert.equal(genreOf('Baywatch 24/7'), '24/7 Series');
  assert.equal(genreOf('24-7 Star Trek'), '24/7 Series');
  assert.equal(genreOf('The Office Marathon'), '24/7 Series');
});

test('news, documentary, reality, kids, movies and music are separated', () => {
  assert.equal(genreOf('Sky News'), 'News');
  assert.equal(genreOf('HRT Vijesti'), 'News');
  assert.equal(genreOf('Al Jazeera English'), 'News');
  assert.equal(genreOf('Discovery Channel'), 'Documentary');
  assert.equal(genreOf('National Geographic Wild'), 'Documentary');
  assert.equal(genreOf('Bravo'), 'Reality');
  assert.equal(genreOf('Love Island 24'), 'Reality');
  assert.equal(genreOf('Cartoon Network'), 'Kids');
  assert.equal(genreOf('HBO 2'), 'Movies');
  assert.equal(genreOf('MTV Hits'), 'Music');
});

test('a sports news channel is filed as sport, not news', () => {
  assert.equal(genreOf('Sky Sports News'), 'Sports');
});

test('classifyGenre admits when nothing fits', () => {
  assert.equal(genreOf('Channel 4001'), '');
  assert.equal(genreOf(''), '');
  assert.equal(classifyGenre({}), '');
});

test('classifyGenre prefers the name over a careless group title', () => {
  assert.equal(classifyGenre({ name: 'Sky Sports Main Event', group: 'Undefined' }), 'Sports');
});

test('lineupGroup puts the country first', () => {
  assert.equal(lineupGroup('gb', 'Sports'), 'GB - Sports');
  assert.equal(lineupGroup('HR', ''), 'HR - Other');
  assert.equal(lineupGroup('', 'News'), 'News');
  assert.equal(lineupGroup('', ''), 'Other');
});

test('lineupSorter orders by country, then genre, then name', () => {
  const entries = [
    { tvgCountry: 'US', genre: 'News', name: 'CNN' },
    { tvgCountry: 'AU', genre: 'Sports', name: 'Optus Sport' },
    { tvgCountry: 'AU', genre: 'PPV / Main Events', name: 'Main Event' },
    { tvgCountry: 'US', genre: 'News', name: 'ABC News' },
    { tvgCountry: 'ZZ', genre: 'News', name: 'Elsewhere' },
  ];

  entries.sort(lineupSorter(['AU', 'US', 'GB', 'HR']));

  assert.deepEqual(
    entries.map((e) => `${e.tvgCountry}/${e.name}`),
    ['AU/Main Event', 'AU/Optus Sport', 'US/ABC News', 'US/CNN', 'ZZ/Elsewhere'],
  );
});

test('GENRES lists every genre the rules can produce', () => {
  const produced = [
    genreOf('beIN Sports'),
    genreOf('UFC PPV'),
    genreOf('Friends 24/7'),
    genreOf('Sky News'),
    genreOf('Discovery'),
    genreOf('Bravo'),
    genreOf('Cartoon Network'),
    genreOf('HBO'),
    genreOf('MTV'),
    genreOf('General Entertainment'),
  ];
  for (const genre of produced) assert.ok(GENRES.includes(genre), `${genre} missing from GENRES`);
});

test('MENA sports brands are recognised without the word "sport"', () => {
  // Same reasoning as MUTV and DAZN: a generic /sport/ pattern never sees
  // these.
  assert.equal(classifyGenre({ name: 'Alkass One' }), 'Sports');
  assert.equal(classifyGenre({ name: 'Alkas 1' }), 'Sports');
  assert.equal(classifyGenre({ name: 'Dubai Racing 2' }), 'Sports');
  assert.equal(classifyGenre({ name: 'MBC Pro Sports 1' }), 'Sports');
  assert.equal(classifyGenre({ name: 'OSN Fight HD' }), 'Sports');
});

test('religious channels get their own bucket instead of Entertainment', () => {
  assert.equal(classifyGenre({ name: 'Saudi Quran' }), 'Religion');
  assert.equal(classifyGenre({ name: 'Iqraa TV' }), 'Religion');
  assert.equal(classifyGenre({ name: 'Al Majd' }), 'Religion');
  assert.equal(classifyGenre({ name: 'Zaytoona' }), 'Religion');
  assert.equal(classifyGenre({ name: 'Makkah TV' }), 'Religion');
  assert.equal(classifyGenre({ name: 'EWTN' }), 'Religion');
});

test('a religious children\'s channel is Kids, not Religion', () => {
  // Kids is tested first on purpose: this is where a viewer looks for them.
  assert.equal(classifyGenre({ name: 'Al-Majd Kids' }), 'Kids');
  assert.equal(classifyGenre({ name: 'Toyor Al-Jannah' }), 'Kids');
});

test('Arabic kids brands carry no word the English rules would see', () => {
  assert.equal(classifyGenre({ name: 'Spacetoon Arabic' }), 'Kids');
  assert.equal(classifyGenre({ name: 'Baraem' }), 'Kids');
  assert.equal(classifyGenre({ name: 'JeemTV' }), 'Kids');
  assert.equal(classifyGenre({ name: 'Ajyal TV' }), 'Kids');
});

test('"aflam" and "cinema" are Movies whatever the transliteration', () => {
  assert.equal(classifyGenre({ name: 'ART Aflam 1' }), 'Movies');
  assert.equal(classifyGenre({ name: 'OSN Cinma 2' }), 'Movies');
  assert.equal(classifyGenre({ name: 'beIN Box Office 1' }), 'Movies');
});

test('Arabic news brands are News', () => {
  assert.equal(classifyGenre({ name: 'Alarabiya' }), 'News');
  assert.equal(classifyGenre({ name: 'Al Hadath' }), 'News');
  assert.equal(classifyGenre({ name: 'Alikhbaria Syria' }), 'News');
  assert.equal(classifyGenre({ name: 'Al Jazeera Mubasher' }), 'News');
});

test('"wild" alone is not a documentary', () => {
  // A bare /wild/ files "Wild 'N Out" and "The Wild Wild West" as factual.
  assert.equal(classifyGenre({ name: 'Nat Geo Wild' }), 'Documentary');
  assert.equal(classifyGenre({ name: 'OSN Natgeowild' }), 'Documentary');
  assert.equal(classifyGenre({ name: 'WildEarth' }), 'Documentary');
  assert.notEqual(classifyGenre({ name: "Wild 'N Out" }), 'Documentary');
  assert.notEqual(classifyGenre({ name: 'The Wild Wild West' }), 'Documentary');
});

test('"Al Nassr" is a football club, not the religious channel "Al Nas"', () => {
  assert.equal(classifyGenre({ name: 'Al Nas TV' }), 'Religion');
  assert.notEqual(classifyGenre({ name: 'Al Nassr TV' }), 'Religion');
});
