/**
 * Works out what someone just pasted into the setup box.
 *
 * A port of `androidtv/.../data/repo/SetupLink.kt`, deliberately kept
 * behaviour-for-behaviour identical. The two apps are handed the same welcome
 * email by the same subscriber, and a desktop that rejects what the phone
 * accepted is worse than either being strict — it reads as one of them being
 * broken. `setupLink.spec.mjs` runs the Kotlin test suite's cases against this
 * file for exactly that reason.
 *
 * The desktop form used to ask for Xtream-vs-M3U, a playlist name, a server
 * URL "(http://host:port)", a username and a password. The phone stopped
 * asking any of that at 1.69.0; this is the other half of that change.
 */

/** What a paste turned out to be. */
export type Setup =
  | { kind: 'xtream'; server: string; username: string; password: string }
  | { kind: 'm3u'; url: string }
  /** A host was recognised, the login was not. Ask for two fields. */
  | { kind: 'needsCredentials'; server: string }
  /** Nothing usable. `reason` is written to be shown to a subscriber. */
  | { kind: 'unrecognised'; reason: string };

/** Endpoints whose path names a panel, whether or not they carry a login. */
const CREDENTIALLED = ['get.php', 'player_api.php', 'panel_api.php', 'xmltv.php'];

const USER_LABELS = ['username', 'user name', 'user', 'login'];
const PASS_LABELS = ['password', 'pass', 'pwd'];
const SERVER_LABELS = ['server address', 'server url', 'server', 'host', 'portal', 'url', 'dns'];

/**
 * A server address reduced to scheme, host and port.
 *
 * The same rules as `PlaylistRepository.normalizeServer` on Android: strip a
 * pasted endpoint path and query, and when no scheme was given, infer one —
 * a bare host on a non-standard port is plain HTTP in this ecosystem, a bare
 * hostname is HTTPS. The two must agree, because a subscriber who set the
 * phone up by pasting one line will paste the same line here.
 */
export function normalizeServer(raw: string): string {
  let s = raw.trim().replace(/\/+$/, '');
  if (!s) return '';
  if (!/^https?:\/\//i.test(s)) {
    const host = s.split('/')[0];
    const port = host.includes(':') ? Number(host.split(':').pop()) : NaN;
    s = Number.isFinite(port) && port !== 443 ? `http://${s}` : `https://${s}`;
  }
  s = s
    .split('/player_api.php')[0]
    .split('/get.php')[0]
    .split('/panel_api.php')[0]
    .split('/xmltv.php')[0];
  return s.split('?')[0].replace(/\/+$/, '');
}

/**
 * @param defaultServer the host to assume when the paste names none. A
 *   reseller very often hands out a username and a password and nothing else;
 *   blank disables the fallback, because inventing a host for someone whose
 *   provider we do not know sends them to a stranger's panel.
 */
export function parseSetup(
  raw: string,
  typedUser = '',
  typedPass = '',
  defaultServer = '',
): Setup {
  const text = raw.trim();
  if (!text && !typedUser.trim()) {
    return { kind: 'unrecognised', reason: 'Paste the link or details your provider sent you.' };
  }

  const labelled = readLabelled(text);
  const url = firstUrl(text);

  const user = typedUser.trim() || labelled.user;
  const pass = typedPass.trim() || labelled.pass;

  // Best case: the address and the login in one string, nothing to type.
  if (url && isCredentialled(url)) {
    const q = queryParams(url);
    const u = user || q['username'] || '';
    const p = pass || q['password'] || '';
    const server = normalizeServer(url);
    return u && p
      ? { kind: 'xtream', server, username: u, password: p }
      : { kind: 'needsCredentials', server };
  }

  // A plain playlist link, only when there is no login to pair it with: a
  // panel profile gets categories, VOD, series and catch-up that a flat
  // playlist cannot.
  if (url && looksLikePlaylist(url) && !user) {
    return { kind: 'm3u', url };
  }

  const server = url
    ? normalizeServer(url)
    : labelled.server
      ? normalizeServer(labelled.server)
      : '';

  if (server) {
    return user && pass
      ? { kind: 'xtream', server, username: user, password: pass }
      : { kind: 'needsCredentials', server };
  }

  if (user && pass && defaultServer) {
    return { kind: 'xtream', server: normalizeServer(defaultServer), username: user, password: pass };
  }

  return {
    kind: 'unrecognised',
    reason:
      "That doesn't look like a playlist link. Paste the whole line your " +
      'provider sent, starting with http.',
  };
}

/** A sensible profile name, so nobody has to invent one. */
export function suggestedName(s: Setup): string {
  switch (s.kind) {
    case 'xtream':
    case 'needsCredentials':
      return hostOf(s.server);
    case 'm3u':
      return hostOf(s.url);
    default:
      return 'My playlist';
  }
}

/**
 * Turn a failure into something worth reading.
 *
 * Same wording as the Android app's `friendly()`. A subscriber who reads one
 * message on their phone and a different one here concludes the two apps
 * disagree about their account.
 */
export function friendlyError(message: string): string {
  const m = message || '';
  const has = (s: string) => m.toLowerCase().includes(s.toLowerCase());
  if (has('rejected the credentials') || has('invalid username') || has('unauthorised') || has('unauthorized')) {
    return "That username or password wasn't accepted. Check them for a stray space, then try again.";
  }
  if (has('unknownhost') || has('unable to resolve host') || has('enotfound') || has('getaddrinfo')) {
    return "Couldn't reach that address. Check your internet, and check the address for a typo.";
  }
  if (has('timeout') || has('timed out')) {
    return "The provider didn't answer in time. It may be busy — try again in a minute.";
  }
  if (has('certificate') || has('ssl') || has('tls')) {
    return "That server's security certificate couldn't be checked. Try http:// instead of https://.";
  }
  return m || "Couldn't connect. Check the link and try again.";
}

// ── the pieces ────────────────────────────────────────────────────────

function hostOf(url: string): string {
  try {
    const u = new URL(url.includes('://') ? url : `http://${url}`);
    return u.hostname.replace(/^www\./, '') || 'My playlist';
  } catch {
    return 'My playlist';
  }
}

/**
 * Pull `Username: x` / `Password: y` / `Server: z` out of pasted text.
 *
 * Split on the first `:` or `=`, because "Server address: http://host:8080"
 * contains three colons and only the first is the separator. Matched on a
 * line's leading label rather than searched for anywhere in it — a password
 * can contain the word "user".
 */
function readLabelled(text: string): { user: string; pass: string; server: string } {
  let user = '';
  let pass = '';
  let server = '';
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const cut = [...trimmed].findIndex((c) => c === ':' || c === '=');
    if (cut <= 0) continue;
    const label = trimmed.slice(0, cut).trim().toLowerCase().replace(/[*\-•\s]+$/, '');
    const value = trimmed.slice(cut + 1).trim();
    if (!value) continue;
    if (!user && USER_LABELS.includes(label)) user = value;
    else if (!pass && PASS_LABELS.includes(label)) pass = value;
    else if (!server && SERVER_LABELS.includes(label)) server = value;
  }
  return { user, pass, server };
}

/** The first http(s) address in the text, stripped of trailing prose. */
function firstUrl(text: string): string | null {
  const m = /https?:\/\/\S+/i.exec(text);
  if (!m) return null;
  return m[0].replace(/[.,;)>"']+$/, '');
}

function isCredentialled(url: string): boolean {
  const lower = url.toLowerCase();
  return CREDENTIALLED.some((e) => lower.includes(`/${e}`));
}

function looksLikePlaylist(url: string): boolean {
  const lower = url.toLowerCase();
  if (lower.includes('.m3u')) return true;
  const path = lower.split('://')[1]?.split('/').slice(1).join('/') ?? '';
  return path.length > 0;
}

function queryParams(url: string): Record<string, string> {
  const q = url.split('?')[1];
  if (!q) return {};
  const out: Record<string, string> = {};
  for (const pair of q.split('&')) {
    const k = pair.split('=')[0];
    if (!k) continue;
    const v = pair.slice(k.length + 1);
    try {
      out[k.toLowerCase()] = decodeURIComponent(v);
    } catch {
      out[k.toLowerCase()] = v;
    }
  }
  return out;
}
