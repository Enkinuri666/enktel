import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  channelNameVariants,
  collapseDoubles,
  displayName,
  foldPlurals,
  splitArticle,
  splitCompound,
  splitGlued,
  splitLanguagePrefix,
  splitTimeshift,
} from './panel-names.mjs';

/** Do these two names have a spelling in common? */
function meet(a, b) {
  const right = new Set(channelNameVariants(b));
  return channelNameVariants(a).filter((v) => right.has(v));
}

test('splitCompound separates the words a panel glued together', () => {
  assert.equal(splitCompound('MBCProSports1'), 'MBC Pro Sports 1');
  assert.equal(splitCompound('PalestineToday'), 'Palestine Today');
  assert.equal(splitCompound('TNNTunisia'), 'TNN Tunisia');
  assert.equal(splitCompound('AlSharqiya'), 'Al Sharqiya');
});

test('splitCompound keeps a brand acronym intact', () => {
  // "beIN" is the broadcaster's own capitalisation; splitting the lowercase
  // run off it would produce "be IN Sports".
  assert.equal(splitCompound('beINSports'), 'beIN Sports');
});

test('splitGlued un-glues an all-caps name camel case cannot help with', () => {
  assert.equal(splitGlued('dubaisport 1'), 'dubai sport 1');
  assert.equal(splitGlued('beinsport 13'), 'bein sport 13');
});

test('splitGlued leaves a word that only looks like a prefix alone', () => {
  // "syrian" starts with "syria"; splitting it invents a channel called
  // "Syria N".
  assert.equal(splitGlued('syrian drama'), 'syrian drama');
});

test('splitArticle separates the Arabic definite article', () => {
  assert.equal(splitArticle('aljazeera mubasher'), 'al jazeera mubasher');
  assert.equal(splitArticle('alarabiya'), 'al arabiya');
});

test('splitArticle does not treat a short word as an article', () => {
  assert.equal(splitArticle('alt tv'), 'alt tv', '"alt" is not "al t"');
  assert.equal(splitArticle('all news'), 'all news');
});

test('collapseDoubles folds the consonants transliteration disagrees about', () => {
  assert.equal(collapseDoubles('alkass'), 'alkas');
  assert.equal(collapseDoubles('jannah'), 'janah');
});

test('collapseDoubles leaves digits alone', () => {
  assert.equal(collapseDoubles('sports 11'), 'sports 11', '11 must not become 1');
});

test('foldPlurals only folds tokens long enough to survive it', () => {
  assert.equal(foldPlurals('dubai sports 1'), 'dubai sport 1');
  assert.equal(foldPlurals('sky news'), 'sky news', 'news is not a plural');
  assert.equal(foldPlurals('majd kids'), 'majd kids');
});

test('splitLanguagePrefix reads the marker a panel puts in front', () => {
  assert.deepEqual(splitLanguagePrefix('EN:BeinSport-11'), {
    name: 'BeinSport-11',
    language: 'eng',
  });
  assert.deepEqual(splitLanguagePrefix('FR:BEINSPORT-13'), {
    name: 'BEINSPORT-13',
    language: 'fra',
  });
  assert.deepEqual(splitLanguagePrefix('MBC-1'), { name: 'MBC-1', language: '' });
});

test('splitTimeshift reads a +N suffix however it is glued on', () => {
  assert.deepEqual(splitTimeshift('OSN-MOVIES-ACTION+2'), {
    name: 'OSN-MOVIES-ACTION',
    offset: 2,
  });
  assert.deepEqual(splitTimeshift('Channel +1'), { name: 'Channel', offset: 1 });
});

test('splitTimeshift ignores a + used as a separator', () => {
  // "OSN-MBC+DRAMA" is one channel's name, not a two-hour shift.
  assert.deepEqual(splitTimeshift('OSN-MBC+DRAMA'), { name: 'OSN-MBC+DRAMA', offset: 0 });
  assert.deepEqual(splitTimeshift('Rotana Aflam+'), { name: 'Rotana Aflam+', offset: 0 });
});

test('a panel name and its catalog name share a spelling', () => {
  // The whole point of the module: both sides go through the same mill, and
  // meet somewhere in the middle.
  assert.ok(meet('DUBAISPORT1', 'Dubai Sports 1').length, 'glued + plural');
  assert.ok(meet('Alkas-1', 'Alkass One').length, 'doubled consonant + number word');
  assert.ok(meet('AD-Sports1', 'AD Sports 1').length, 'separator');
  assert.ok(meet('aljazeera_mubasher', 'Al Jazeera Mubasher').length, 'article');
  assert.ok(meet('Toyor-AlJanah', 'Toyor Al-Jannah').length, 'both at once');
  assert.ok(meet('beINSports-fr1', 'beIN Sports fr 1').length, 'brand acronym');
});

test('unrelated names share no spelling', () => {
  assert.deepEqual(meet('MBC-1', 'MBC-2'), [], 'the number is the whole distinction');
  assert.deepEqual(meet('Alkas-1', 'Alkass Two'), []);
});

test('displayName turns a shouted panel name into something readable', () => {
  assert.equal(displayName('AL-JAZEERA'), 'Al Jazeera');
  assert.equal(displayName('DUBAISPORT1'), 'Dubai Sport 1');
  assert.equal(displayName('aljazeera_mubasher'), 'Aljazeera Mubasher');
  assert.equal(displayName('MAJD-KIDS'), 'Majd Kids');
});

test('displayName keeps acronyms and brand capitalisation', () => {
  assert.equal(displayName('AD-SPORTS2'), 'AD Sports 2', 'AD is an acronym');
  assert.equal(displayName('IQRAA-TV'), 'Iqraa TV');
  assert.equal(displayName('beINSports-fr1'), 'beIN Sports Fr 1', 'beIN is a brand spelling');
});

test('displayName keeps the timeshift marker it strips for matching', () => {
  assert.equal(displayName('OSN-MOVIES-ACTION+2'), 'OSN Movies Action +2');
});

test('channelNameVariants is stable and never empty for a real name', () => {
  const variants = channelNameVariants('MBC-1');
  assert.ok(variants.length >= 2);
  assert.equal(variants[0], 'MBC-1', 'the input itself comes first');
  assert.equal(new Set(variants).size, variants.length, 'no duplicates');
  assert.deepEqual(channelNameVariants('   '), []);
});
