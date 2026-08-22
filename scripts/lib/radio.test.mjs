import assert from 'node:assert/strict';
import { test } from 'node:test';

import { looksLikeRadio } from './radio.mjs';

test('the publisher flag wins outright', () => {
  assert.equal(looksLikeRadio({ name: 'Some TV', radio: true }), true);
  assert.equal(looksLikeRadio({ name: 'BBC Radio 2', radio: true }), true);
});

test('stations are recognised from the name', () => {
  assert.equal(looksLikeRadio({ name: 'BBC Radio 2' }), true);
  assert.equal(looksLikeRadio({ name: 'BBC Radio 5 Sports Extra' }), true);
  assert.equal(looksLikeRadio({ name: '92.7 Mix FM' }), true);
  assert.equal(looksLikeRadio({ name: '99.7 Bridge FM' }), true);
  assert.equal(looksLikeRadio({ name: 'SBS Radio 4' }), true);
  assert.equal(looksLikeRadio({ name: 'KPVM Ace Country Radio' }), true);
});

/**
 * The reason this needs a classifier at all rather than a substring match:
 * every one of these is a television broadcaster with "radio" in its name.
 */
test('television with radio in the name stays television', () => {
  assert.equal(looksLikeRadio({ name: 'Radio Javan TV (1080p)' }), false);
  assert.equal(looksLikeRadio({ name: 'Radio y Televisión Martí (720p)' }), false);
  assert.equal(looksLikeRadio({ name: 'Radio Tele Sentinel' }), false);
  assert.equal(looksLikeRadio({ name: 'CFM TV Channel 98 (360p)' }), false);
});

/** No word boundary before the hint means it is part of another word. */
test('a hint inside a longer word does not count', () => {
  assert.equal(looksLikeRadio({ name: 'iHeartRadio Countdown AUS' }), false);
  assert.equal(looksLikeRadio({ name: 'CFM Sports' }), false);
});

test('a radio group title is the publisher telling us', () => {
  assert.equal(looksLikeRadio({ name: 'Triple J', group: 'AU - Radio' }), true);
  assert.equal(looksLikeRadio({ name: 'Triple J', group: 'AU - Music' }), false);
});

test('an ordinary channel is not radio', () => {
  assert.equal(looksLikeRadio({ name: 'BBC One' }), false);
  assert.equal(looksLikeRadio({ name: 'Sky Sports Main Event' }), false);
  assert.equal(looksLikeRadio({}), false);
});
