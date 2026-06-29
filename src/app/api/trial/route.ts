import { NextRequest, NextResponse } from "next/server";
import { provisionSubscription } from "@/lib/reseller";
import { sendWelcomeEmail } from "@/lib/email";

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const name = typeof body?.name === "string" ? body.name.trim() : "";
  const email = typeof body?.email === "string" ? body.email.trim() : "";
  const device = typeof body?.device === "string" ? body.device.trim() : "";

  if (!name || !email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return NextResponse.json({ error: "Please provide a valid name and email." }, { status: 400 });
  }

  const result = await provisionSubscription("trial", email);
  if (!result.ok) {
    console.error(`[trial] Provisioning failed for ${email}: ${result.error}`);
    return NextResponse.json(
      { error: "We're having trouble activating your trial right now. Please try again in a few minutes or contact us on WhatsApp for help." },
      { status: 502 }
    );
  }

  const subscription = { ...result.subscription, device };
  sendWelcomeEmail({ to: email, name, subscription: result.subscription, device }).catch(() => {});

  return NextResponse.json({ subscription });
}
