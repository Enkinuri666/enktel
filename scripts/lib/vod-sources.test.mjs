import test from 'node:test';
import assert from 'node:assert/strict';
import {
  isRedistributable,
  pickPlayableFile,
  buildQuery,
  downloadUrl,
  LANGUAGE_GROUPS,
  KINDS,
} from './vod-sources.mjs';

// ── licence acceptance ─────────────────────────────────────────────────
//
// This is the whole basis on which anything is collected, so it is tested
// from the "must not pass" side first. A false positive here puts something
// in the catalogue that has no business being there.

test('accepts public-domain and permissive Creative Commons licences', () => {
  for (const url of [
    'http://creativecommons.org/publicdomain/mark/1.0/',
    'https://creativecommons.org/publicdomain/zero/1.0/',
    'http://creativecommons.org/licenses/by/4.0/',
    'http://creativecommons.org/licenses/by-sa/3.0/',
    'http://creativecommons.org/licenses/by-nd/4.0/',
  ]) {
    assert.equal(isRedistributable(url), true, url);
  }
});

test('a missing or unrecognised licence is a no, not a maybe', () => {
  // The important half. An item with nothing declared is unknown, and unknown
  // has to be treated the same as refused — "it was downloadable" is not a
  // licence.
  for (const url of [undefined, null, '', '   ', 'all rights reserved', 42, {}]) {
    assert.equal(isRedistributable(url), false, String(url));
  }
});

test('non-commercial licences are excluded unless explicitly allowed', () => {
  const nc = 'http://creativecommons.org/licenses/by-nc/4.0/';
  assert.equal(isRedistributable(nc), false);
  assert.equal(isRedistributable(nc, { allowNonCommercial: true }), true);
});

test('a bare creativecommons.org mention does not pass', () => {
  // The reason the allowlist is URL prefixes rather than a keyword search:
  // every CC licence URL contains "creativecommons.org", including the ones
  // deliberately excluded, so a substring test would let everything through.
  assert.equal(isRedistributable('http://creativecommons.org/'), false);
  assert.equal(isRedistributable('see creativecommons.org for terms'), false);
});

test('http and https forms of the same licence both pass', () => {
  assert.equal(isRedistributable('http://creativecommons.org/publicdomain/mark/1.0/'), true);
  assert.equal(isRedistributable('https://creativecommons.org/publicdomain/mark/1.0/'), true);
});

// ── file choice ────────────────────────────────────────────────────────

test('prefers H.264 MP4 over other formats', () => {
  // Every Android device this runs on decodes H.264 in hardware; a 1 GB stick
  // will not manage Theora at feature length in software.
  const pick = pickPlayableFile([
    { name: 'film.ogv', format: 'Ogg Video', size: '900000000' },
    { name: 'film.mp4', format: 'h.264', size: '400000000' },
    { name: 'film.webm', format: 'WebM', size: '500000000' },
  ]);
  assert.equal(pick.name, 'film.mp4');
});

test('within one format, prefers the largest that is still sane to stream', () => {
  const pick = pickPlayableFile([
    { name: 'small.mp4', format: 'h.264', size: '100000000' },
    { name: 'big.mp4', format: 'h.264', size: '900000000' },
  ]);
  assert.equal(pick.name, 'big.mp4');
});

test('skips an unwieldy master when a normal copy exists', () => {
  const pick = pickPlayableFile([
    { name: 'master.mp4', format: 'h.264', size: String(9 * 1024 ** 3) },
    { name: 'web.mp4', format: 'h.264', size: '600000000' },
  ]);
  assert.equal(pick.name, 'web.mp4');
});

test('falls back to the smallest copy when every copy is enormous', () => {
  // An unwieldy file that plays beats no entry at all.
  const pick = pickPlayableFile([
    { name: 'huge-a.mp4', format: 'h.264', size: String(9 * 1024 ** 3) },
    { name: 'huge-b.mp4', format: 'h.264', size: String(7 * 1024 ** 3) },
  ]);
  assert.equal(pick.name, 'huge-b.mp4');
});

test('ignores non-video files and returns null when there is no video', () => {
  assert.equal(pickPlayableFile([
    { name: 'cover.jpg', format: 'JPEG', size: '40000' },
    { name: 'notes.txt', format: 'Text', size: '900' },
  ]), null);
  assert.equal(pickPlayableFile([]), null);
  assert.equal(pickPlayableFile(null), null);
});

test('a file with no usable size is still selectable', () => {
  // The Archive occasionally omits size. That must not make an item invisible.
  const pick = pickPlayableFile([{ name: 'film.mp4', format: 'h.264' }]);
  assert.equal(pick.name, 'film.mp4');
});

// ── queries and URLs ───────────────────────────────────────────────────

test('every query filters on both language and a declared licence', () => {
  for (const kind of Object.keys(KINDS)) {
    for (const lang of Object.keys(LANGUAGE_GROUPS)) {
      const q = buildQuery(kind, lang);
      assert.match(q, /language:\(/, `${kind}/${lang} must constrain language`);
      assert.match(q, /licenseurl:\[\* TO \*\]/, `${kind}/${lang} must require a licence`);
      assert.match(q, /NOT licenseurl:\(\*by-nc\*\)/, `${kind}/${lang} must exclude NC by default`);
    }
  }
});

test('every query requires membership of a curated collection', () => {
  // The regression this exists for: an earlier version selected by
  // `mediatype:(movies)` minus the kinds it did not want. `mediatype:(movies)`
  // is the Archive's bucket for any moving image, so what came back was home
  // videos, ad reels and screen recordings — and, because the licence field is
  // uploader-supplied, some plainly commercial television carrying a Creative
  // Commons declaration nobody with the rights had made.
  //
  // Collection membership is what the uploader cannot set for themselves, so
  // no kind may go back to selecting on mediatype alone.
  for (const kind of Object.keys(KINDS)) {
    for (const lang of Object.keys(LANGUAGE_GROUPS)) {
      const q = buildQuery(kind, lang);
      assert.match(q, /collection:\(/, `${kind}/${lang} must require a curated collection`);
      assert.doesNotMatch(
        q,
        /AND NOT \(/,
        `${kind}/${lang} selects by subtraction, which lets everything through`,
      );
    }
  }
});

test('the kinds name distinct collections rather than overlapping ones', () => {
  // Two kinds sharing a collection would put the same item on two rails under
  // two labels, which reads as a duplicate rather than as a second listing.
  const seen = new Map();
  for (const [kind, { query }] of Object.entries(KINDS)) {
    for (const id of query.match(/collection:\(([^)]*)\)/)[1].split(' OR ')) {
      assert.ok(!seen.has(id), `${id} is claimed by both ${seen.get(id)} and ${kind}`);
      seen.set(id, kind);
    }
  }
});

test('allowing non-commercial drops the NC exclusion but keeps the licence requirement', () => {
  const q = buildQuery('movies', 'en', { allowNonCommercial: true });
  assert.match(q, /licenseurl:\[\* TO \*\]/);
  assert.doesNotMatch(q, /by-nc/);
});

test('the Ex-Yu group covers Croatian, Serbian and Bosnian spellings', () => {
  const v = LANGUAGE_GROUPS.exyu.values.map((s) => s.toLowerCase());
  for (const needed of ['hrv', 'srp', 'bos', 'croatian', 'serbian', 'bosnian']) {
    assert.ok(v.includes(needed), `missing ${needed}`);
  }
});

test('an unknown kind or language is an error rather than a silent empty query', () => {
  assert.throws(() => buildQuery('nope', 'en'), /Unknown kind/);
  assert.throws(() => buildQuery('movies', 'nope'), /Unknown language/);
});

test('download URLs escape identifiers and filenames', () => {
  assert.equal(
    downloadUrl('Big Boy', 'a film.mp4'),
    'https://archive.org/download/Big%20Boy/a%20film.mp4',
  );
});
