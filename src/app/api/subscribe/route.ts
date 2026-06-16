import { NextRequest, NextResponse } from "next/server";

const API_BASE = "http://api.elg-26.com/api/dev_api.php";
const API_KEY = process.env.RESELLER_API_KEY || "";

// Reseller panel package IDs
const PLAN_PACKAGE: Record<string, number> = {
  monthly: 149,  // EAGLE_4k__1M  (1 month)
  quarter: 150,  // EAGLE_4k__3M  (3 months)
  annual:  152,  // EAGLE_4k__1Y  (12 months)
};

const PLAN_DURATION_DAYS: Record<string, number> = {
  monthly: 30,
  quarter: 90,
  annual:  365,
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
    action:     "user",
    type:       "create",
    package_id: String(packageId),
    note:       note,
    country:    "GB",
    api_key:    API_KEY,
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

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { name, email, plan } = body as { name: string; email: string; plan: string };

    if (!name || !email || !plan) {
      return NextResponse.json({ error: "Missing required fields" }, { status: 400 });
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return NextResponse.json({ error: "Invalid email address" }, { status: 400 });
    }
    if (!API_KEY) {
      return NextResponse.json({ error: "Service not configured — contact support" }, { status: 503 });
    }

    const packageId = PLAN_PACKAGE[plan] ?? 149;
    const durationDays = PLAN_DURATION_DAYS[plan] ?? 30;
    const subscriptionId = `ENK-${Date.now()}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;

    const result = await createLine(`${email}|${plan}`, packageId);

    if (!result.ok) {
      return NextResponse.json(
        { error: `Could not activate subscription: ${result.error}. Please contact support.` },
        { status: 502 }
      );
    }

    const primary = result.data;
    const serverUrl = primary.url || "http://api.elg-26.com";
    const startDate = new Date();
    const endDate = new Date(startDate.getTime() + durationDays * 24 * 60 * 60 * 1000);

    const subscription = {
      id:        subscriptionId,
      plan,
      status:    "active",
      startDate: startDate.toISOString(),
      endDate:   endDate.toISOString(),
      username:  primary.username,
      password:  primary.password,
      m3uUrl:    buildM3U(serverUrl, primary.username, primary.password),
      epgUrl:    buildEPG(serverUrl, primary.username, primary.password),
      panelSync: true,
    };

    return NextResponse.json({
      success: true,
      message: "Subscription created and activated.",
      subscription,
    });
  } catch {
    return NextResponse.json({ error: "Internal server error" }, { status: 500 });
  }
}
