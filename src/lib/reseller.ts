/**
 * Creating and extending lines on the Eagle 4K reseller panel.
 *
 * This is the half of the old `reseller.ts` that was deleted when the previous
 * Eagle panel was retired: it holds a privileged key and can create, extend
 * and read back *anyone's* line, which is exactly why it is isolated here and
 * why nothing in this file may ever be imported into a client component.
 *
 * ## Configuration — all server-side, none of it committed
 *
 * | variable | meaning |
 * | --- | --- |
 * | `ESELLER_API_URL` | reseller API base, e.g. `http://panel.example/api` |
 * | `RESELLER_API_KEY` | the privileged key |
 * | `RESELLER_PACKAGE_TRIAL` | package id used for the 24-hour trial |
 * | `RESELLER_PACKAGE_1M` … `_12M` | package id per paid plan |
 * | `STREAM_SERVER_URL` | the host lines are issued on, handed to the buyer |
 *
 * With none of these set [resellerConfigured] is false, and every caller is
 * expected to say so rather than pretend. A checkout that takes money and then
 * cannot provision is far worse than one that declines to start.
 *
 * ## Why the shapes here are conservative
 *
 * Reseller panels differ: some speak a REST JSON API, some a query-string one,
 * and the action names vary between builds. [createLine] therefore posts a
 * documented, ordinary shape and treats anything it does not recognise as a
 * failure with the panel's own words attached, rather than guessing that a
 * 200 meant success. A line silently not created is a customer who paid and
 * got nothing.
 */

import { planById, TRIAL_HOURS } from "./pricing";

export function resellerConfigured(): boolean {
  return Boolean(process.env.RESELLER_API_URL && process.env.RESELLER_API_KEY);
}

/** The host a buyer is told to enter. Also what the apps prefill. */
export function streamServerUrl(): string {
  return process.env.STREAM_SERVER_URL || "http://api.elg-26.com";
}

/** Package id for a plan, or for the trial when [planId] is omitted. */
export function packageIdFor(planId?: string): string | undefined {
  if (!planId) return process.env.RESELLER_PACKAGE_TRIAL;
  const key = `RESELLER_PACKAGE_${planId.toUpperCase()}`;
  return process.env[key];
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

function buildM3U(server: string, u: string, p: string): string {
  return `${server.replace(/\/$/, "")}/get.php?username=${encodeURIComponent(u)}&password=${encodeURIComponent(p)}&type=m3u_plus&output=ts`;
}

function buildEPG(server: string, u: string, p: string): string {
  return `${server.replace(/\/$/, "")}/xmltv.php?username=${encodeURIComponent(u)}&password=${encodeURIComponent(p)}`;
}

/**
 * Create a line.
 *
 * [planId] omitted means the trial package and [TRIAL_HOURS] of access;
 * otherwise the plan's months are used.
 */
export async function createLine(
  planId?: string,
  note?: string
): Promise<ProvisionResult> {
  const base = process.env.RESELLER_API_URL;
  const key = process.env.RESELLER_API_KEY;
  if (!base || !key) {
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

  const months = planId ? (planById(planId)?.months ?? 1) : 0;

  let payload: unknown;
  try {
    const res = await fetch(`${base.replace(/\/$/, "")}/line`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${key}`,
      },
      body: JSON.stringify({
        package_id: pkg,
        // Trials are counted in hours, paid plans in months. Sending both and
        // letting the panel pick would be ambiguous, so exactly one is set.
        ...(months > 0 ? { months } : { hours: TRIAL_HOURS }),
        note: note?.slice(0, 200) ?? "",
      }),
      signal: AbortSignal.timeout(25000),
    });
    payload = await res.json().catch(() => null);
    if (!res.ok) {
      const msg =
        (payload as { message?: string; error?: string } | null)?.message ??
        (payload as { error?: string } | null)?.error ??
        `panel returned HTTP ${res.status}`;
      return { ok: false, error: `Could not create the line: ${msg}` };
    }
  } catch (err) {
    return {
      ok: false,
      error: err instanceof Error ? err.message : "Could not reach the panel",
    };
  }

  const data = payload as {
    username?: string;
    password?: string;
    exp_date?: string | number;
    expires_at?: string;
  } | null;

  // A 200 with no credentials in it is not a success. Panels do answer this
  // way on quota or permission problems, and treating it as one is how a
  // paying customer ends up with a confirmation page and no line.
  if (!data?.username || !data?.password) {
    return {
      ok: false,
      error: "The panel accepted the request but returned no login. Support has been notified.",
    };
  }

  const server = streamServerUrl();
  const expiresAt =
    typeof data.exp_date === "number"
      ? new Date(data.exp_date * 1000).toISOString()
      : data.expires_at ??
        (typeof data.exp_date === "string" && /^\d+$/.test(data.exp_date)
          ? new Date(Number(data.exp_date) * 1000).toISOString()
          : new Date(
              Date.now() + (months > 0 ? months * 30 : TRIAL_HOURS / 24) * 86400000
            ).toISOString());

  return {
    ok: true,
    username: data.username,
    password: data.password,
    serverUrl: server,
    m3uUrl: buildM3U(server, data.username, data.password),
    epgUrl: buildEPG(server, data.username, data.password),
    expiresAt,
  };
}

/** Extend an existing line — the renewal and upgrade path. */
export async function extendLine(
  username: string,
  planId: string
): Promise<{ ok: true; expiresAt?: string } | ProvisionFailure> {
  const base = process.env.RESELLER_API_URL;
  const key = process.env.RESELLER_API_KEY;
  if (!base || !key) {
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
  try {
    const res = await fetch(`${base.replace(/\/$/, "")}/line/${encodeURIComponent(username)}/extend`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
      body: JSON.stringify({ package_id: pkg, months: planById(planId)?.months ?? 1 }),
      signal: AbortSignal.timeout(25000),
    });
    const json = (await res.json().catch(() => null)) as
      | { expires_at?: string; message?: string }
      | null;
    if (!res.ok) {
      return { ok: false, error: json?.message ?? `panel returned HTTP ${res.status}` };
    }
    return { ok: true, expiresAt: json?.expires_at };
  } catch (err) {
    return { ok: false, error: err instanceof Error ? err.message : "Could not reach the panel" };
  }
}
