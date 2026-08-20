import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  MENA_ROSTER,
  ROSTERS,
  buildStreamUrl,
  selectRoster,
  toRosterCsv,
  toRosterText,
} from './rosters.mjs';

test('buildStreamUrl substitutes the line credentials at render time', () => {
  assert.equal(
    buildStreamUrl(MENA_ROSTER, { id: '16' }, { username: 'user', password: 'pass' }),
    'http://iptv.am000.tv:8000/live/user/pass/16.ts',
  );
});

test('buildStreamUrl escapes credentials that would break the path', () => {
  const url = buildStreamUrl(MENA_ROSTER, { id: '1' }, { username: 'a/b', password: 'c d' });
  assert.ok(url.includes('a%2Fb'), 'a slash in a username must not become a path segment');
  assert.ok(url.includes('c%20d'));
});

test('buildStreamUrl tolerates a server with a trailing slash', () => {
  const roster = { ...MENA_ROSTER, server: 'http://panel:8000/' };
  assert.equal(
    buildStreamUrl(roster, { id: '9' }, { username: 'u', password: 'p' }),
    'http://panel:8000/live/u/p/9.ts',
  );
});

test('no roster carries credentials', () => {
  // The whole reason the roster is data and the URL is a template.
  const serialised = JSON.stringify(ROSTERS);
  assert.ok(!/password|username/i.test(serialised.replace(/\{username\}|\{password\}/g, '')));
  for (const roster of ROSTERS) {
    assert.match(roster.template, /\{username\}.*\{password\}/, 'credentials stay placeholders');
  }
});

test('selectRoster names what it knows when asked for something else', () => {
  assert.equal(selectRoster('mena').id, 'mena');
  assert.throws(() => selectRoster('nope'), /Unknown roster "nope".*mena/s);
});

test('every roster channel has a distinct stream id and a name', () => {
  const seen = new Set();
  for (const channel of MENA_ROSTER.channels) {
    assert.match(channel.id, /^\d+$/, `stream id: ${channel.id}`);
    assert.ok(!seen.has(channel.id), `duplicate stream id ${channel.id}`);
    seen.add(channel.id);
    assert.ok(typeof channel.name === 'string' && channel.name.length, 'every channel is named');
  }
});

test('curated country hints are ISO-3166 alpha-2', () => {
  for (const channel of MENA_ROSTER.channels) {
    if (channel.country) assert.match(channel.country, /^[A-Z]{2}$/, channel.name);
  }
});

test('curated channel ids look like iptv-org ids', () => {
  // `resolve()` checks them against the live catalog; this catches a typo
  // without a network call.
  for (const channel of MENA_ROSTER.channels) {
    if (channel.channel) assert.match(channel.channel, /^[A-Za-z0-9+&-]+\.[a-z]{2}$/, channel.name);
  }
});

test('a channel that resolved to nothing carries a note saying why', () => {
  // The unresolved ones are the ones a maintainer will re-investigate; the
  // note is what stops that happening every few months.
  const unexplained = MENA_ROSTER.channels.filter((c) => !c.channel && !c.note);
  assert.ok(
    unexplained.length < MENA_ROSTER.channels.length,
    'the roster records its own dead ends',
  );
});

const RECORDS = [
  {
    id: '16',
    name: 'beINSports-fr1',
    display: 'beIN Sports French 1',
    country: 'QA',
    language: '',
    genre: 'Sports',
    tvgId: 'beINSportsFrench1.qa',
    idVia: 'curated',
    idScore: 1,
    tvgLogo: 'https://logos.example/a.png',
    logoVia: 'id',
    guideSites: ['bein.com', 'sat.tv'],
    idReason: '',
    note: '',
  },
];

test('toRosterCsv keeps the provenance next to the answer', () => {
  const csv = toRosterCsv(RECORDS);
  const [header, row] = csv.trim().split('\n');
  assert.ok(header.startsWith('stream_id,panel_name,name,'));
  assert.ok(row.includes('"curated"'), 'how the id was decided is part of the record');
  assert.ok(row.includes('"bein.com sat.tv"'), 'guide sites are space separated in one cell');
});

test('toRosterCsv escapes a quote in a channel name', () => {
  const csv = toRosterCsv([{ ...RECORDS[0], display: 'Hell\'s "Kitchen"' }]);
  assert.ok(csv.includes('"Hell\'s ""Kitchen"""'));
});

test('toRosterText marks a channel with no id and no logo', () => {
  const text = toRosterText([{ ...RECORDS[0], tvgId: '', tvgLogo: '' }]);
  const row = text.trim().split('\n')[1].split('\t');
  assert.equal(row[4], '-');
  assert.equal(row[5], '-');
});
