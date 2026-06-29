import { PLAN_PRICE_EUR } from "./plans";

const API_BASE = "http://api.elg-26.com/api/dev_api.php";
const API_KEY = process.env.RESELLER_API_KEY || "";

// Confirmed panel package_id for the 24-hour trial line.
const TRIAL_PACKAGE_ID = 143; // EAGLE_4K_24H_TEST (1 connection)

export const TRIAL_DURATION_HOURS = 24;

// Reseller panel package IDs (confirmed against the panel's package list)
export const PLAN_PACKAGE: Record<string, number> = {
  monthly: 149, // EAGLE_4K_1M    (30 days, 1 connection)
  quarter: 150, // EAGLE_4K_3M    (90 days, 1 connection)
  annual: 152, // EAGLE_4K_1YEAR (365 days, 1 connection)
};

export const PLAN_DURATION_DAYS: Record<string, number> = {
  monthly: 30,
  quarter: 90,
  annual: 365,
};

export { PLAN_PRICE_EUR };

export const PLAN_NAME: Record<string, string> = {
  monthly: "Monthly Plan",
  quarter: "3 Month Plan",
  annual: "12 Month Plan",
  trial: "24-Hour Free Trial",
};

interface PanelLineResult {
  status: boolean;
  message?: string;
  username: string;
  password: string;
  url: string;
}

async function createLine(
  note: string,
  packageId: number
): Promise<{ ok: true; data: PanelLineResult } | { ok: false; error: string }> {
  const params = new URLSearchParams({
    action: "user",
    type: "create",
    package_id: String(packageId),
    note: note,
    country: "GB",
    api_key: API_KEY,
  });

  const url = `${API_BASE}?${params.toString()}`;

  try {
    const res = await fetch(url, {
      method: "GET",
      headers: { "User-Agent": "EnktelIPTV/1.0" },
      signal: AbortSignal.timeout(12000),
    });

    const text = await res.text();

    if (!res.ok) {
      console.error(`[reseller] Panel HTTP ${res.status} for package ${packageId}: ${text.slice(0, 500)}`);
      return { ok: false, error: `Panel HTTP ${res.status}` };
    }
    if (!text || text.trim() === "") {
      console.error(`[reseller] Empty response from panel for package ${packageId}`);
      return { ok: false, error: "Empty response from panel" };
    }

    const json: PanelLineResult = JSON.parse(text);
    if (!json.status) {
      console.error(`[reseller] Panel rejected request for package ${packageId}: ${JSON.stringify(json)}`);
      return { ok: false, error: json.message || "Panel returned status=false" };
    }

    return { ok: true, data: json };
  } catch (err) {
    console.error(`[reseller] Network error for package ${packageId}:`, err);
    return { ok: false, error: err instanceof Error ? err.message : "Network error" };
  }
}

function buildM3U(serverUrl: string, username: string, password: string): string {
  const base = serverUrl.replace(/\/$/, "");
  return `${base}/get.php?username=${username}&password=${password}&type=m3u_plus&output=ts`;
}

function buildEPG(serverUrl: string, username: string, password: string): string {
  const base = serverUrl.replace(/\/$/, "");
  return `${base}/xmltv.php?username=${username}&password=${password}`;
}

// Default stream server for credential lookups where no panel-provisioned
// `url` is already known (e.g. a customer logging in with a line that was
// created directly in the reseller panel rather than through this site).
export const STREAM_SERVER_URL = "http://api.elg-26.com";

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

// Verifies a username/password against the IPTV stream server using the
// standard Xtream Codes `player_api.php` endpoint — the same server already
// used for get.php/xmltv.php above. This works for any active line on the
// panel, regardless of whether it was provisioned through this site.
export async function verifyStreamCredentials(
  username: string,
  password: string,
  serverUrl: string = STREAM_SERVER_URL
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

export interface ProvisionedSubscription {
  id: string;
  plan: string;
  status: string;
  startDate: string;
  endDate: string;
  username: string;
  password: string;
  m3uUrl: string;
  epgUrl: string;
  panelSync: boolean;
  isTrial: boolean;
}

export async function provisionSubscription(
  plan: string,
  email: string
): Promise<{ ok: true; subscription: ProvisionedSubscription } | { ok: false; error: string }> {
  if (!API_KEY) return { ok: false, error: "Service not configured" };

  const isTrial = plan === "trial";

  const packageId = isTrial ? TRIAL_PACKAGE_ID : PLAN_PACKAGE[plan] ?? 149;
  const durationMs = isTrial
    ? TRIAL_DURATION_HOURS * 60 * 60 * 1000
    : (PLAN_DURATION_DAYS[plan] ?? 30) * 24 * 60 * 60 * 1000;
  const subscriptionId = `ENK-${Date.now()}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;

  const result = await createLine(`${email}|${plan}`, packageId);
  if (!result.ok) return { ok: false, error: result.error };

  const primary = result.data;
  const serverUrl = primary.url || "http://api.elg-26.com";
  const startDate = new Date();
  const endDate = new Date(startDate.getTime() + durationMs);

  return {
    ok: true,
    subscription: {
      id: subscriptionId,
      plan,
      status: "active",
      startDate: startDate.toISOString(),
      endDate: endDate.toISOString(),
      username: primary.username,
      password: primary.password,
      m3uUrl: buildM3U(serverUrl, primary.username, primary.password),
      epgUrl: buildEPG(serverUrl, primary.username, primary.password),
      panelSync: true,
      isTrial,
    },
  };
}
