import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  credentialsFromEnv,
  mergeCredentials,
  missingCredentials,
  parseEnvFile,
  redact,
} from './credentials.mjs';

test('credentialsFromEnv reads the three keys and defaults the rest', () => {
  assert.deepEqual(
    credentialsFromEnv({ XTREAM_SERVER: 'http://p', XTREAM_USERNAME: 'u' }),
    { server: 'http://p', username: 'u', password: '' },
  );
  assert.deepEqual(credentialsFromEnv(), { server: '', username: '', password: '' });
});

test('parseEnvFile handles the shapes a dotenv file comes in', () => {
  const parsed = parseEnvFile(
    ['# a comment', 'XTREAM_SERVER=http://panel:8080', "export XTREAM_USERNAME='user'", 'XTREAM_PASSWORD="p a s s"'].join('\n'),
  );
  assert.deepEqual(parsed, { server: 'http://panel:8080', username: 'user', password: 'p a s s' });
});

test('parseEnvFile ignores keys these tools do not own', () => {
  assert.deepEqual(parseEnvFile('AWS_SECRET_ACCESS_KEY=nope\nPATH=/bin'), {});
});

test('mergeCredentials fills gaps without overriding what was given', () => {
  const merged = mergeCredentials(
    { server: 'http://flag', username: '', password: '' },
    { server: 'http://file', username: 'fromfile', password: 'fromfile' },
  );
  assert.equal(merged.server, 'http://flag', 'an explicit flag wins');
  assert.equal(merged.username, 'fromfile');
});

test('missingCredentials names the environment variables to set', () => {
  assert.deepEqual(missingCredentials({ server: 'http://p', username: 'u', password: '' }), [
    'XTREAM_PASSWORD',
  ]);
  assert.deepEqual(missingCredentials({ server: 'http://p', username: 'u', password: 'p' }), []);
});

test('redact blanks the password wherever it appears', () => {
  const url = 'http://panel:8080/live/user/sup3rsecret/16.ts';
  assert.equal(redact(url, 'sup3rsecret'), 'http://panel:8080/live/user/••••/16.ts');
});

test('redact leaves a short password alone rather than corrupting the text', () => {
  // "add" appears inside "address"; blanking it makes the message unreadable
  // without making it any safer.
  assert.equal(redact('address', 'add'), 'address');
});
