import { NextRequest, NextResponse } from "next/server";

function generateToken(length = 32): string {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  let result = "";
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { name, email, plan } = body;

    if (!name || !email || !plan) {
      return NextResponse.json({ error: "Missing required fields" }, { status: 400 });
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      return NextResponse.json({ error: "Invalid email address" }, { status: 400 });
    }

    const userId = generateToken(16);
    const subscriptionId = `ENK-${Date.now()}-${Math.random().toString(36).substr(2, 6).toUpperCase()}`;
    const siteUrl = process.env.NEXT_PUBLIC_SITE_URL || "https://enktel.tv";

    const subscription = {
      id: subscriptionId,
      userId,
      plan,
      status: "active",
      startDate: new Date().toISOString(),
      endDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      m3uUrl: `${siteUrl}/playlist/${userId}.m3u`,
      epgUrl: `${siteUrl}/epg/${userId}.xml`,
    };

    return NextResponse.json({
      success: true,
      message: "Subscription created successfully",
      subscription,
    });
  } catch {
    return NextResponse.json({ error: "Internal server error" }, { status: 500 });
  }
}
