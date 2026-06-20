import { NextRequest, NextResponse } from "next/server";

const RESEND_API_KEY = process.env.RESEND_API_KEY || "";
const RESEND_AUDIENCE_ID = process.env.RESEND_AUDIENCE_ID || "";

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const email = typeof body?.email === "string" ? body.email.trim() : "";

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return NextResponse.json({ error: "Please enter a valid email address." }, { status: 400 });
  }

  if (!RESEND_API_KEY || !RESEND_AUDIENCE_ID) {
    console.log("[newsletter] not configured, logging only:", email);
    return NextResponse.json({ ok: true });
  }

  try {
    // Resend hosts the subscriber list itself (an "audience"), so this works
    // without us needing our own persistent storage.
    const res = await fetch(`https://api.resend.com/audiences/${RESEND_AUDIENCE_ID}/contacts`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${RESEND_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, unsubscribed: false }),
      signal: AbortSignal.timeout(10000),
    });

    if (!res.ok && res.status !== 409) {
      const text = await res.text().catch(() => "");
      console.error("[newsletter] Resend error:", res.status, text);
      return NextResponse.json({ error: "Couldn't subscribe right now. Please try again shortly." }, { status: 502 });
    }
  } catch (err) {
    console.error("[newsletter] subscribe failed:", err);
    return NextResponse.json({ error: "Couldn't subscribe right now. Please try again shortly." }, { status: 502 });
  }

  return NextResponse.json({ ok: true });
}
