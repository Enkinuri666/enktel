/**
 * Creating and extending lines on the Eagle reseller panel.
 *
 * Holds a privileged key that can create, extend and read back *anyone's*
 * line, which is why it is isolated here and why nothing in this file may ever
 * be imported into a client component.
 *
 * ## The API, as it actually behaves
 *
 * Documented at `manager-eagle.com/pntv/reseller/devapi.php` (behind the panel
 * login). Everything is a **GET** against one endpoint with query parameters,
 * and the key travels as `api_key`:
 *
 *     .../dev_api.php?action=user&type=create&package_id=109&api_key=…
 *     .../dev_api.php?action=user&type=extend&username=…&password=…&package_id=…
 *     .../dev_api.php?action=user&type=info&username=…&password=…
 *
 * Two things the documentation does not say, both confirmed against the live
 * panel and both able to break this silently:
 *
 * 1. **Responses are array-wrapped.** The docs show `{"status":true,…}`; the
 *    panel returns `[{"status":true,…}]`. Reading the documented shape gets
 *    `undefined` for every field, which looks exactly like a refusal.
 * 2. **Failure still comes back HTTP 200.** `status` is the only thing that
 *    says whether it worked, so a check on `res.ok` alone would report a line
 *    that was never created as a success — a customer who paid and got
 *    nothing.
 *
 * ## Configuration
 *
 * | variable | default | meaning |
 * | --- | --- | --- |
 * | `RESELLER_API_URL` | the live endpoint | full URL of `dev_api.php` |
 * | `RESELLER_API_KEY` | — | the privileged key; nothing works without it |
 * | `RESELLER_PACKAGE_TRIAL` | `101` | 24-hour package |
 * | `RESELLER_PACKAGE_1M/_3M/_6M/_12M` | `109/110/111/10` | paid packages |
 * | `RESELLER_TEMPLATE_ID` | `1028` | channel template a new line is built on |
 * | `STREAM_SERVER_URL` | `https://x-api.cc` | fallback host for a line |
 *
 * The package ids are this reseller's own, read from `action=packages`, so
 * they are defaults rather than secrets. Only the key must come from the
 * environment, and it is the one thing that must never be committed.
 *
 * `RESELLER_TEMPLATE_ID` is 1028 — EAGLE_LITE — on the account holder's
 * instruction. It was left unset until they named one, because the panel also
 * offers EAGLE_ARABIC (1029) and EAGLE_FRENCH (1030) and picking wrong hands a
 * paying customer the wrong channel list. It is still env-overridable, so
 * changing lineup is a Vercel setting rather than a deploy.
 */

import { planById, TRIAL_HOURS } from "./pricing";

/** Package ids read from this reseller's own panel. */
const DEFAULT_PACKAGES: Record<string, string> = {
  TRIAL: "101", // EAGLE_24HOURS — 0 credits
  "1M": "109", // EAGLE_1 Month — 1 credit
  "3M": "110", // EAGLE_3 Months — 3 credits
  "6M": "111", // EAGLE_6 Months — 6 credits
  "12M": "10", // EAGLE_1 Year — 12 credits
};

const DEFAULT_ENDPOINT = "http://api.elg-26.com/api/dev_api.php";

/** EAGLE_LITE. See the note at the top of this file. */
const DEFAULT_TEMPLATE_ID = "1028";

/** Which channel template a new line is built on. */
export function templateId(): string {
  return process.env.RESELLER_TEMPLATE_ID || DEFAULT_TEMPLATE_ID;
}

export function resellerConfigured(): boolean {
  return Boolean(process.env.RESELLER_API_KEY);
}

function endpoint(): string {
  return process.env.RESELLER_API_URL || DEFAULT_ENDPOINT;
}

/** The host a buyer is told to enter, when the panel does not name one. */
export function streamServerUrl(): string {
  return process.env.STREAM_SERVER_URL || "https://x-api.cc";
}

/** Package id for a plan, or for the trial when [planId] is omitted. */
export function packageIdFor(planId?: string): string | undefined {
  const slot = planId ? planId.toUpperCase() : "TRIAL";
  return process.env[`RESELLER_PACKAGE_${slot}`] || DEFAULT_PACKAGES[slot];
}

export interface ProvisionedLine {
  username: string;
  password: string;
  serverUrl: string;
  m3uUrl: string;
  epgUrl: string;
  /** ISO 8601. */
  expiresAt: string;
}

export interface ProvisionFailure {
  ok: false;
  /** Safe to show a customer. */
  error: string;
  /** True when the cause is missing configuration rather than a panel refusal. */
  unconfigured?: boolean;
}

export type ProvisionResult = ({ ok: true } & ProvisionedLine) | ProvisionFailure;

/**
 * The panel's address for a line, reduced to what a player actually wants.
 *
 * `dev_api.php` answers with the whole m3u_plus link — scheme, host, port,
 * `/get.php`, and the credentials in the query — not the bare host that the
 * field name suggests. Passed through untouched it was returned as
 * `serverUrl`, so a new subscriber was shown a 140-character URL where a
 * server address belongs, and {@link buildM3U} and {@link buildEPG} then
 * appended their own path to it and produced `…&output=ts/get.php?…`: a link
 * no panel serves. The Android app survived it only because
 * `PlaylistRepository.normalizeServer` strips exactly that shape; VLC or
 * TiviMate, handed the same string, does not.
 */
function serverOrigin(raw: string): string {
  const trimmed = raw.trim().replace(/\/+$/, "");
  if (!trimmed) return "";

  let candidate = trimmed;
  if (!/^https?:\/\//i.test(candidate)) {
    // The same rule the app applies, deliberately: a bare host carrying a
    // non-standard port is plain HTTP in this ecosystem, a bare hostname is
    // HTTPS. The two are shown the same string and should not disagree
    // about it.
    const host = candidate.split("/")[0];
    const port = host.includes(":") ? Number(host.split(":").pop()) : NaN;
    candidate = Number.isFinite(port) && port !== 443 ? `http://${candidate}` : `https://${candidate}`;
  }

  try {
    const u = new URL(candidate);
    // `URL` blanks the port when it is the scheme's default, so :80 on an
    // http:// panel would be quoted back without it. Kept: the Host header
    // then differs, and enough panels in this ecosystem vhost on the port
    // that dropping it is not ours to decide.
    const stated = /^[^/]*\/\/[^/@]*:(\d+)/.exec(candidate)?.[1];
    const port = u.port || stated || "";
    return port ? `${u.protocol}//${u.hostname}:${port}` : `${u.protocol}//${u.hostname}`;
  } catch {
    // An unparseable shape we have not seen before. Strip the path by hand
    // rather than return nothing: something a player can use beats a blank
    // field on a confirmation page.
    return trimmed.replace(/\/(get|xmltv|player_api|panel_api)\.php.*$/i, "").replace(/\/+$/, "");
  }
}

function buildM3U(server: string, u: string, p: string): string {
  return `${server.replace(/\/$/, "")}/get.php?username=${encodeURIComponent(u)}&password=${encodeURIComponent(p)}&type=m3u_plus&output=ts`;
}

function buildEPG(server: string, u: string, p: string): string {
  return `${server.replace(/\/$/, "")}/xmltv.php?username=${encodeURIComponent(u)}&password=${encodeURIComponent(p)}`;
}

/** One panel reply, unwrapped. Exported for tests. */
export interface PanelReply {
  status?: boolean | string;
  message?: string;
  username?: string;
  password?: string;
  url?: string;
  exp_date?: string | number;
  [k: string]: unknown;
}

/**
 * Unwrap whatever the panel sent.
 *
 * It answers with a single-element array where the docs promise an object, so
 * both are accepted — and anything else becomes null rather than a partial
 * object that would read as a refusal further down.
 */
export function unwrapReply(payload: unknown): PanelReply | null {
  if (Array.isArray(payload)) {
    const first = payload[0];
    return first && typeof first === "object" ? (first as PanelReply) : null;
  }
  if (payload && typeof payload === "object") return payload as PanelReply;
  return null;
}

/**
 * Did it work?
 *
 * `status` arrives as a real boolean today, but panels of this family are
 * careless about JSON types and `"true"` costs nothing to accept. Anything
 * else — false, absent, `"0"` — is a failure.
 */
export function replySucceeded(reply: PanelReply | null): boolean {
  return reply?.status === true || reply?.status === "true";
}

/** Build a request URL. Exported so the parameter shape can be tested. */
export function apiUrl(params: Record<string, string | undefined>): string {
  const url = new URL(endpoint());
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== "") url.searchParams.set(k, v);
  }
  url.searchParams.set("api_key", process.env.RESELLER_API_KEY ?? "");
  return url.toString();
}

async function callPanel(
  params: Record<string, string | undefined>
): Promise<{ reply: PanelReply | null; error?: string }> {
  try {
    const res = await fetch(apiUrl(params), {
      method: "GET",
      signal: AbortSignal.timeout(25000),
    });
    const payload = await res.json().catch(() => null);
    const reply = unwrapReply(payload);
    if (!res.ok) {
      return { reply, error: reply?.message || `panel returned HTTP ${res.status}` };
    }
    return { reply };
  } catch (err) {
    return {
      reply: null,
      error: err instanceof Error ? err.message : "could not reach the panel",
    };
  }
}

/**
 * Create a line.
 *
 * [planId] omitted means the trial package.
 */
export async function createLine(
  planId?: string,
  note?: string
): Promise<ProvisionResult> {
  if (!resellerConfigured()) {
    return {
      ok: false,
      unconfigured: true,
      error:
        "Automatic activation is not switched on yet. Your order is recorded — we will send your login by email shortly.",
    };
  }

  const pkg = packageIdFor(planId);
  if (!pkg) {
    return {
      ok: false,
      unconfigured: true,
      error:
        "That plan has no package configured on the panel yet. We will activate it by hand and email your login.",
    };
  }

  const { reply, error } = await callPanel({
    action: "user",
    type: "create",
    package_id: pkg,
    template_id: templateId(),
    note: note?.slice(0, 200),
  });

  if (error) return { ok: false, error: `Could not create the line: ${error}` };

  // A 200 that says status:false is a refusal — out of credits, bad package,
  // key revoked. Treating it as success is how a paying customer ends up with
  // a confirmation page and no line.
  if (!replySucceeded(reply)) {
    return {
      ok: false,
      error: reply?.message
        ? `Could not create the line: ${reply.message}`
        : "The panel refused the request. Support has been notified.",
    };
  }
  if (!reply?.username || !reply?.password) {
    return {
      ok: false,
      error: "The panel reported success but returned no login. Support has been notified.",
    };
  }

  // The panel names the host for this line; ours is only the fallback. It
  // knows which server the line was issued on and we are guessing. What it
  // names it with is a full playlist URL, so it is reduced to an origin
  // before anything is built on top of it — see serverOrigin.
  const server = serverOrigin(reply.url || streamServerUrl());
  const months = planId ? (planById(planId)?.months ?? 1) : 0;

  return {
    ok: true,
    username: reply.username,
    password: reply.password,
    serverUrl: server,
    m3uUrl: buildM3U(server, reply.username, reply.password),
    epgUrl: buildEPG(server, reply.username, reply.password),
    expiresAt: expiryFrom(reply.exp_date, months),
  };
}

/**
 * When the line runs out.
 *
 * The panel's own date wins. The computed fallback is deliberately rough —
 * thirty-day months — because it is only ever shown, never used to decide
 * whether access has ended; the panel decides that.
 */
export function expiryFrom(expDate: unknown, months: number): string {
  if (typeof expDate === "number" && expDate > 0) {
    return new Date(expDate * 1000).toISOString();
  }
  if (typeof expDate === "string" && expDate.trim() !== "") {
    if (/^\d+$/.test(expDate)) return new Date(Number(expDate) * 1000).toISOString();
    const parsed = Date.parse(expDate);
    if (!Number.isNaN(parsed)) return new Date(parsed).toISOString();
  }
  const days = months > 0 ? months * 30 : TRIAL_HOURS / 24;
  return new Date(Date.now() + days * 86400000).toISOString();
}

/**
 * Extend an existing line.
 *
 * Takes the password as well as the username because the panel does: there is
 * no documented call that goes from a username alone to an extension, which is
 * why the renewal flow has to collect both rather than carrying just the name
 * in a link.
 */
export async function extendLine(
  username: string,
  password: string,
  planId: string
): Promise<{ ok: true; expiresAt?: string } | ProvisionFailure> {
  if (!resellerConfigured()) {
    return {
      ok: false,
      unconfigured: true,
      error:
        "Automatic renewal is not switched on yet. Your payment is recorded — we will extend your line shortly.",
    };
  }
  const pkg = packageIdFor(planId);
  if (!pkg) {
    return { ok: false, unconfigured: true, error: "That plan has no package configured yet." };
  }
  if (!username || !password) {
    return {
      ok: false,
      error: "Renewing needs both the username and password from your dashboard.",
    };
  }

  const { reply, error } = await callPanel({
    action: "user",
    type: "extend",
    username,
    password,
    package_id: pkg,
  });

  if (error) return { ok: false, error: `Could not extend the line: ${error}` };
  if (!replySucceeded(reply)) {
    return {
      ok: false,
      error: reply?.message
        ? `Could not extend the line: ${reply.message}`
        : "The panel refused the renewal. Support has been notified.",
    };
  }
  return { ok: true, expiresAt: reply?.exp_date ? expiryFrom(reply.exp_date, 0) : undefined };
}

/** Read a line back — used to confirm an extension actually landed. */
export async function lineInfo(
  username: string,
  password: string
): Promise<PanelReply | null> {
  if (!resellerConfigured()) return null;
  const { reply } = await callPanel({
    action: "user",
    type: "info",
    username,
    password,
  });
  return replySucceeded(reply) ? reply : null;
}
