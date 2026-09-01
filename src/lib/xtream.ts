/**
 * Talking to an Xtream Codes panel as a *viewer*.
 *
 * This file used to be `reseller.ts` and carried two very different things: a
 * reseller API client that created and provisioned lines on the Eagle panel
 * using a privileged key, and a plain credential check that any Xtream server
 * answers. Eagle is retired, so the provisioning half is gone along with the
 * plan tables, the package ids and the API key it needed.
 *
 * What is left works against any panel, because it only ever presents the
 * viewer's own username and password — there is no privileged key in this
 * file any more, and nothing here can create, modify or read back somebody
 * else's line.
 */

/** Standard Xtream playlist URL for a line. */
function buildM3U(serverUrl: string, username: string, password: string): string {
  const base = serverUrl.replace(/\/$/, "");
  return `${base}/get.php?username=${username}&password=${password}&type=m3u_plus&output=ts`;
}

/** Standard XMLTV guide URL for a line. */
function buildEPG(serverUrl: string, username: string, password: string): string {
  const base = serverUrl.replace(/\/$/, "");
  return `${base}/xmltv.php?username=${username}&password=${password}`;
}

interface XtreamUserInfo {
  auth: number;
  status: string;
  exp_date: string | null;
  created_at: string | null;
}

interface XtreamAuthResponse {
  user_info?: XtreamUserInfo;
}

export interface LoginSubscription {
  id: string;
  plan: string;
  status: string;
  startDate: string;
  endDate: string;
  username: string;
  password: string;
  m3uUrl: string;
  epgUrl: string;
  isTrial: boolean;
}

/**
 * Check a username and password against an Xtream panel.
 *
 * `serverUrl` is required. It used to default to the Eagle host, which meant a
 * caller that forgot to pass one silently checked credentials against a panel
 * that is no longer there — succeeding for nobody and reporting it as a bad
 * password. Making it explicit turns that into a caller-side error instead.
 */
export async function verifyStreamCredentials(
  username: string,
  password: string,
  serverUrl: string
): Promise<{ ok: true; subscription: LoginSubscription } | { ok: false; error: string }> {
  const base = serverUrl.replace(/\/$/, "");
  const params = new URLSearchParams({ username, password });

  let json: XtreamAuthResponse;
  try {
    const res = await fetch(`${base}/player_api.php?${params.toString()}`, {
      method: "GET",
      headers: { "User-Agent": "EnktelIPTV/1.0" },
      signal: AbortSignal.timeout(12000),
    });
    if (!res.ok) return { ok: false, error: `Server returned HTTP ${res.status}` };
    json = await res.json();
  } catch (err) {
    return { ok: false, error: err instanceof Error ? err.message : "Network error" };
  }

  const info = json.user_info;
  if (!info || Number(info.auth) !== 1) {
    return { ok: false, error: "Invalid username or password." };
  }
  if (info.status !== "Active") {
    return { ok: false, error: `Account is ${info.status?.toLowerCase() || "inactive"}.` };
  }

  const startDate = info.created_at
    ? new Date(Number(info.created_at) * 1000).toISOString()
    : new Date().toISOString();
  const endDate = info.exp_date
    ? new Date(Number(info.exp_date) * 1000).toISOString()
    : new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString();

  return {
    ok: true,
    subscription: {
      id: `ENK-${username}`,
      plan: "active",
      status: "active",
      startDate,
      endDate,
      username,
      password,
      m3uUrl: buildM3U(base, username, password),
      epgUrl: buildEPG(base, username, password),
      isTrial: false,
    },
  };
}
