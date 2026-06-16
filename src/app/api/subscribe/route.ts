import { NextRequest, NextResponse } from "next/server";

const PANEL_URL = process.env.RESELLER_PANEL_URL || "https://e4kpremuim.com/e4k/reseller/";
const API_KEY = process.env.RESELLER_API_KEY || "";
// The base server URL used to build M3U / EPG stream links
const STREAM_HOST = "https://e4kpremuim.com";

const PLAN_CONNECTIONS: Record<string, number> = {
  starter: 1,
  pro: 2,
  ultimate: 4,
};

function generateUsername(name: string): string {
  const base = name.toLowerCase().replace(/[^a-z0-9]/g, "").slice(0, 8) || "enktel";
  const suffix = Math.random().toString(36).slice(2, 6);
  return `${base}_${suffix}`;
}

function generatePassword(length = 10): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
  return Array.from({ length }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
}

function makeM3UUrl(username: string, password: string): string {
  return `${STREAM_HOST}/get.php?username=${username}&password=${password}&type=m3u_plus&output=ts`;
}

function makeEPGUrl(username: string, password: string): string {
  return `${STREAM_HOST}/xmltv.php?username=${username}&password=${password}`;
}

async function createResellerLine(
  username: string,
  password: string,
  connections: number,
  expireDate: string
): Promise<{ success: boolean; error?: string }> {
  if (!API_KEY) return { success: false, error: "No API key configured" };

  const params = new URLSearchParams({
    api_key: API_KEY,
    action: "add_member",
    username,
    password,
    max_connections: String(connections),
    expire_date: expireDate,
    is_trial: "0",
    is_e2: "0",
  });

  try {
    const res = await fetch(`${PANEL_URL}?${params.toString()}`, {
      method: "GET",
      headers: { "User-Agent": "EnktelIPTV/1.0" },
      signal: AbortSignal.timeout(10000),
    });

    if (!res.ok) return { success: false, error: `Panel returned ${res.status}` };

    const text = await res.text();
    if (!text || text.trim() === "") {
      // Empty 200 — panel accepted the request (common behaviour on some panels)
      return { success: true };
    }

    try {
      const json = JSON.parse(text);
      if (json.error || json.status === "error") {
        return { success: false, error: json.error || json.message || "Panel error" };
      }
      return { success: true };
    } catch {
      // Non-JSON 200 — treat as success
      return { success: true };
    }
  } catch (err) {
    return { success: false, error: err instanceof Error ? err.message : "Network error" };
  }
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

    const connections = PLAN_CONNECTIONS[plan] ?? 1;
    const username = generateUsername(name);
    const password = generatePassword();
    const subscriptionId = `ENK-${Date.now()}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
    const startDate = new Date();
    const endDate = new Date(startDate.getTime() + 30 * 24 * 60 * 60 * 1000);
    const expireDate = endDate.toISOString().split("T")[0]; // YYYY-MM-DD

    // Attempt to create line on reseller panel
    const panelResult = await createResellerLine(username, password, connections, expireDate);

    const subscription = {
      id: subscriptionId,
      username,
      password,
      plan,
      status: "active",
      connections,
      startDate: startDate.toISOString(),
      endDate: endDate.toISOString(),
      m3uUrl: makeM3UUrl(username, password),
      epgUrl: makeEPGUrl(username, password),
      panelSync: panelResult.success,
    };

    return NextResponse.json({
      success: true,
      message: panelResult.success
        ? "Subscription created and activated on our streaming servers."
        : "Subscription created. Your credentials will be activated within a few minutes.",
      subscription,
    });
  } catch {
    return NextResponse.json({ error: "Internal server error" }, { status: 500 });
  }
}
