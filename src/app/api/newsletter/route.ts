import { NextRequest, NextResponse } from "next/server";

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const email = typeof body?.email === "string" ? body.email.trim() : "";

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return NextResponse.json({ error: "Please enter a valid email address." }, { status: 400 });
  }

  // TODO: wire to a real list provider (e.g. Mailchimp, ConvertKit, Resend) once configured.
  console.log("[newsletter] subscribed:", email);

  return NextResponse.json({ ok: true });
}
