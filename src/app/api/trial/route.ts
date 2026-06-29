import { NextRequest, NextResponse } from "next/server";
import { provisionSubscription } from "@/lib/reseller";
import { sendWelcomeEmail } from "@/lib/email";

const TRIAL_COOLDOWN_MS = 10 * 60 * 1000;
const recentTrials = new Map<string, number>();

function cleanupOldEntries() {
  const now = Date.now();
  recentTrials.forEach((ts, key) => {
    if (now - ts > TRIAL_COOLDOWN_MS) recentTrials.delete(key);
  });
}

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const name = typeof body?.name === "string" ? body.name.trim() : "";
  const email = typeof body?.email === "string" ? body.email.trim().toLowerCase() : "";
  const device = typeof body?.device === "string" ? body.device.trim() : "";

  if (!name || !email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return NextResponse.json({ error: "Please provide a valid name and email." }, { status: 400 });
  }

  cleanupOldEntries();
  const lastAttempt = recentTrials.get(email);
  if (lastAttempt && Date.now() - lastAttempt < TRIAL_COOLDOWN_MS) {
    return NextResponse.json(
      { error: "A trial was already requested for this email. Check your inbox for your login details, or contact us on WhatsApp if you need help." },
      { status: 429 }
    );
  }

  recentTrials.set(email, Date.now());

  const result = await provisionSubscription("trial", email);
  if (!result.ok) {
    console.error(`[trial] Provisioning failed for ${email}: ${result.error}`);
    return NextResponse.json(
      { error: "Something went wrong showing your credentials, but your account may have been created. Please check your email for login details, or contact us on WhatsApp." },
      { status: 502 }
    );
  }

  const subscription = { ...result.subscription, device };
  sendWelcomeEmail({ to: email, name, subscription: result.subscription, device }).catch(() => {});

  return NextResponse.json({ subscription });
}
