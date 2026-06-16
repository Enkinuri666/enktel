import { NextRequest, NextResponse } from "next/server";

const API_BASE = "http://api.elg-26.com/api/dev_api.php";
const API_KEY = process.env.RESELLER_API_KEY || "";

// Package IDs from the reseller panel
// Starter / Pro / Ultimate all use 1-month lines (package 149).
// For multi-connection plans we create multiple lines.
const PLAN_PACKAGE: Record<string, number> = {
  starter: 149,   // EAGLE_4k__1M — 1 connection
  pro:     149,   // 2 × EAGLE_4k__1M lines
  ultimate: 149,  // 4 × EAGLE_4k__1M lines
};

const PLAN_CONNECTIONS: Record<string, number> = {
  starter: 1,
  pro:     1,
  ultimate: 1,
};

interface PanelLineResult {
  status: boolean;
  message?: string;
  username: string;
  password: string;
  url: string;          // e.g. "http://server.elg-26.com:8080"
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

    const connections = PLAN_CONNECTIONS[plan] ?? 1;
    const packageId = PLAN_PACKAGE[plan] ?? 149;
    const noteBase = `${email}|${plan}`;
    const subscriptionId = `ENK-${Date.now()}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;

    // Create one line per connection (all packages are single-connection)
    const linePromises = Array.from({ length: connections }, (_, i) =>
      createLine(`${noteBase}|conn${i + 1}`, packageId)
    );
    const results = await Promise.all(linePromises);

    const failed = results.filter((r) => !r.ok);
    const succeeded = results.filter((r): r is { ok: true; data: PanelLineResult } => r.ok);

    if (succeeded.length === 0) {
      // All failed — return a useful error
      const firstError = failed[0] && !failed[0].ok ? failed[0].error : "Panel unavailable";
      return NextResponse.json(
        { error: `Could not activate subscription: ${firstError}. Please contact support.` },
        { status: 502 }
      );
    }

    // Primary line (first one) drives the URLs shown to the customer
    const primary = succeeded[0].data;
    const serverUrl = primary.url || "http://api.elg-26.com";

    // Build credentials list for all lines created
    const lines = succeeded.map((r, i) => ({
      connection: i + 1,
      username: r.data.username,
      password: r.data.password,
      m3uUrl:   buildM3U(serverUrl, r.data.username, r.data.password),
      epgUrl:   buildEPG(serverUrl, r.data.username, r.data.password),
    }));

    const startDate = new Date();
    const endDate = new Date(startDate.getTime() + 30 * 24 * 60 * 60 * 1000);

    const subscription = {
      id:          subscriptionId,
      plan,
      status:      "active",
      connections: succeeded.length,
      startDate:   startDate.toISOString(),
      endDate:     endDate.toISOString(),
      // Convenience fields for single-line display (primary connection)
      username:    primary.username,
      password:    primary.password,
      m3uUrl:      buildM3U(serverUrl, primary.username, primary.password),
      epgUrl:      buildEPG(serverUrl, primary.username, primary.password),
      // All lines (for multi-connection plans)
      lines,
      panelSync:   true,
      partialSync: failed.length > 0,
    };

    const message = failed.length > 0
      ? `Subscription activated with ${succeeded.length}/${connections} connections. Contact support for the remaining line(s).`
      : `Subscription created and activated. ${connections > 1 ? `${connections} connections ready.` : ""}`;

    return NextResponse.json({ success: true, message, subscription });
  } catch {
    return NextResponse.json({ error: "Internal server error" }, { status: 500 });
  }
}
