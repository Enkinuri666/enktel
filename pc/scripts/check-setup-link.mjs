/**
 * Parity check: the desktop parser must answer exactly as the phone's does.
 *
 * These are the cases from `androidtv/app/src/test/java/tv/enktel/app/
 * SetupLinkTest.kt`, in the same order and with the same expectations. The
 * two apps are handed the same welcome email by the same subscriber, and a
 * desktop that rejects what the phone accepted reads as one of them being
 * broken — so the way to keep them honest is to run one suite against both.
 *
 *     node scripts/check-setup-link.mjs      (or: npm run check:setup-link)
 *
 * esbuild rather than a test runner: it is already a Vite dependency, this is
 * one pure module with no DOM, and adding vitest to a Tauri app for fifteen
 * string assertions is a dependency nobody asked for.
 */
import { build } from 'esbuild';
import { pathToFileURL } from 'node:url';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const dir = mkdtempSync(join(tmpdir(), 'setuplink-'));
const out = join(dir, 'setupLink.mjs');
await build({
  entryPoints: ['src/lib/setupLink.ts'],
  outfile: out,
  format: 'esm',
  bundle: false,
  logLevel: 'silent',
});
const { parseSetup, suggestedName, normalizeServer } = await import(pathToFileURL(out).href);

let failures = 0;
function check(name, actual, expected) {
  const a = JSON.stringify(actual);
  const e = JSON.stringify(expected);
  if (a === e) {
    console.log(`  ok   ${name}`);
  } else {
    failures++;
    console.log(`  FAIL ${name}\n         expected ${e}\n         actual   ${a}`);
  }
}

console.log('setupLink parity with SetupLinkTest.kt\n');

check(
  'the m3u link from our own welcome email becomes an xtream line',
  parseSetup(
    'http://mastercode.hostpp.live:80/get.php?username=z19ci5cbxe9dre' +
      '&password=ibv7ybuqcp3b1f&type=m3u_plus&output=ts',
  ),
  { kind: 'xtream', server: 'http://mastercode.hostpp.live:80', username: 'z19ci5cbxe9dre', password: 'ibv7ybuqcp3b1f' },
);

check(
  'the setup link the welcome email composes is accepted verbatim',
  parseSetup('https://x-api.cc/get.php?username=enktel_demo&password=8fj3kd92&type=m3u_plus&output=ts'),
  { kind: 'xtream', server: 'https://x-api.cc', username: 'enktel_demo', password: '8fj3kd92' },
);

check(
  'a player_api link carries the same credentials',
  parseSetup('http://host.tv:8080/player_api.php?username=u1&password=p1'),
  { kind: 'xtream', server: 'http://host.tv:8080', username: 'u1', password: 'p1' },
);

check(
  'percent-encoded credentials are decoded',
  parseSetup('http://host.tv:8080/get.php?username=a%40b.com&password=p%20%26q&type=m3u_plus'),
  { kind: 'xtream', server: 'http://host.tv:8080', username: 'a@b.com', password: 'p &q' },
);

check(
  "the welcome email's credentials block pasted whole",
  parseSetup(
    [
      'Username:       enktel_demo',
      'Password:       8fj3kd92',
      'Server address: http://x-api.cc:8080',
      'Plan:           3 months, runs until 5 October 2026',
    ].join('\n'),
  ),
  { kind: 'xtream', server: 'http://x-api.cc:8080', username: 'enktel_demo', password: '8fj3kd92' },
);

check(
  'a labelled block with no scheme on the host still resolves',
  parseSetup('user: bob\npass: hunter2\nserver: panel.example.com:8080'),
  { kind: 'xtream', server: 'http://panel.example.com:8080', username: 'bob', password: 'hunter2' },
);

check(
  'a bare host asks for the login rather than failing',
  parseSetup('http://panel.example.com:8080'),
  { kind: 'needsCredentials', server: 'http://panel.example.com:8080' },
);

check(
  'a bare host plus typed fields is a complete line',
  parseSetup('http://panel.example.com:8080', 'bob', 'hunter2'),
  { kind: 'xtream', server: 'http://panel.example.com:8080', username: 'bob', password: 'hunter2' },
);

check(
  'typed fields beat whatever the pasted link carried',
  parseSetup('http://host.tv:8080/get.php?username=old&password=stale', 'new', 'fresh'),
  { kind: 'xtream', server: 'http://host.tv:8080', username: 'new', password: 'fresh' },
);

check(
  'a plain m3u link stays an m3u profile',
  parseSetup('https://lists.example.com/playlists/abc123.m3u8'),
  { kind: 'm3u', url: 'https://lists.example.com/playlists/abc123.m3u8' },
);

check(
  'a playlist link with a typed login is treated as a panel',
  parseSetup('https://lists.example.com/playlists/abc123.m3u8', 'bob', 'hunter2').kind,
  'xtream',
);

check(
  'a url pulled out of surrounding prose loses its punctuation',
  parseSetup('Here you go: http://host.tv:8080/get.php?username=u1&password=p1&type=m3u_plus.').password,
  'p1',
);

check(
  "a username and password alone fall back to the build's own server",
  parseSetup('', 'bob', 'hunter2', 'https://x-api.cc'),
  { kind: 'xtream', server: 'https://x-api.cc', username: 'bob', password: 'hunter2' },
);

check(
  'labelled credentials with no host use the default too',
  parseSetup('Username: bob\nPassword: hunter2', '', '', 'https://x-api.cc').server,
  'https://x-api.cc',
);

check(
  'a pasted host still beats the default',
  parseSetup('Server: http://other.tv:8080\nUsername: bob\nPassword: hunter2', '', '', 'https://x-api.cc').server,
  'http://other.tv:8080',
);

check(
  'no default server means credentials alone are not enough',
  parseSetup('', 'bob', 'hunter2', '').kind,
  'unrecognised',
);

check(
  'a username with no password is not enough for the default host',
  parseSetup('', 'bob', '', 'https://x-api.cc').kind,
  'unrecognised',
);

check('empty input asks for the link', parseSetup('').kind, 'unrecognised');
check('whitespace input asks for the link', parseSetup('   \n  ').kind, 'unrecognised');

check(
  'an xtream shape with no credentials in it asks rather than guesses',
  parseSetup('http://host.tv:8080/get.php?type=m3u_plus').kind,
  'needsCredentials',
);

check(
  'the suggested name is the host, not My Playlist',
  suggestedName(parseSetup('http://host.tv:8080/get.php?username=u&password=p')),
  'host.tv',
);

check(
  'a password containing the word user is not mistaken for a username',
  parseSetup('Server: http://host.tv:8080\nUsername: bob\nPassword: myuser2024'),
  { kind: 'xtream', server: 'http://host.tv:8080', username: 'bob', password: 'myuser2024' },
);

// normalizeServer's own rules, which the Android side pins in
// PlaylistRepositoryTest. Same inputs, same answers.
check('normalizeServer keeps an explicit scheme', normalizeServer('https://x-api.cc'), 'https://x-api.cc');
check('normalizeServer defaults a bare hostname to https', normalizeServer('x-api.cc'), 'https://x-api.cc');
check('normalizeServer defaults a non-standard port to http', normalizeServer('panel.example.com:8080'), 'http://panel.example.com:8080');
check('normalizeServer treats 443 as https', normalizeServer('panel.example.com:443'), 'https://panel.example.com:443');
check('normalizeServer strips an endpoint path', normalizeServer('https://x-api.cc/player_api.php'), 'https://x-api.cc');
check('normalizeServer strips a query', normalizeServer('https://x-api.cc/get.php?username=u&password=p'), 'https://x-api.cc');

rmSync(dir, { recursive: true, force: true });

console.log(failures === 0 ? '\nall parity cases pass' : `\n${failures} case(s) diverge from the phone`);
process.exit(failures === 0 ? 0 : 1);
