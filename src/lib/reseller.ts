const API_BASE = "http://api.elg-26.com/api/dev_api.php";
const API_KEY = process.env.RESELLER_API_KEY || "";

// Reseller panel package IDs
export const PLAN_PACKAGE: Record<string, number> = {
  monthly: 149, // EAGLE_4k__1M  (1 month)
  quarter: 150, // EAGLE_4k__3M  (3 months)
  annual: 152, // EAGLE_4k__1Y  (12 months)
};

export const PLAN_DURATION_DAYS: Record<string, number> = {
  monthly: 30,
  quarter: 90,
  annual: 365,
};

export const PLAN_PRICE_EUR: Record<string, number> = {
  monthly: 19.99,
  quarter: 59,
  annual: 99,
};

export const PLAN_NAME: Record<string, string> = {
  monthly: "Monthly Plan",
  quarter: "3 Month Plan",
  annual: "12 Month Plan",
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

  try {
    const res = await fetch(`${API_BASE}?${params.toString()}`, {
      method: "GET",
      headers: { "User-Agent": "EnktelIPTV/1.0" },
      signal: AbortSignal.timeout(12000),
    });

    const text = await res.text();

    if (!res.ok) return { ok: false, error: `Panel HTTP ${res.status}` };
    if (!text || text.trim() === "") return { ok: false, error: "Empty response from panel" };

    const json: PanelLineResult = JSON.parse(text);
    if (!json.status) return { ok: false, error: json.message || "Panel returned status=false" };

    return { ok: true, data: json };
  } catch (err) {
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
}

export async function provisionSubscription(
  plan: string,
  email: string
): Promise<{ ok: true; subscription: ProvisionedSubscription } | { ok: false; error: string }> {
  if (!API_KEY) return { ok: false, error: "Service not configured" };

  const packageId = PLAN_PACKAGE[plan] ?? 149;
  const durationDays = PLAN_DURATION_DAYS[plan] ?? 30;
  const subscriptionId = `ENK-${Date.now()}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;

  const result = await createLine(`${email}|${plan}`, packageId);
  if (!result.ok) return { ok: false, error: result.error };

  const primary = result.data;
  const serverUrl = primary.url || "http://api.elg-26.com";
  const startDate = new Date();
  const endDate = new Date(startDate.getTime() + durationDays * 24 * 60 * 60 * 1000);

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
    },
  };
}
