import { NextRequest, NextResponse } from "next/server";
import { provisionSubscription } from "@/lib/reseller";
import { sendWelcomeEmail } from "@/lib/email";
import { checkTrialAllowed, commitTrial, gateResponse, trialGateEnabled } from "@/lib/trialGate";

const TRIAL_COOKIE = "enktel_trial";
const COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days

// The per-email, per-IP and per-device limits used to live in module-level
// Maps right here. On Vercel that is not a rate limiter: every invocation may
// land on a different serverless instance, instances are recycled constantly,
// and a cold start begins with the maps empty — so the "one free trial per
// device" rule did not actually hold, and every trial is a real line on the
// reseller panel. They now live in Redis; see src/lib/trialGate.ts.

function getClientIp(req: NextRequest): string {
  return (
    req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ||
    req.headers.get("x-real-ip") ||
    "unknown"
  );
}

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);

  // Two callers, two payload shapes.
  //
  // The website posts { name, email, device, fp } from a form. The TV and
  // mobile apps post { device_id, duration_hours, client, version } and have
  // no form at all — a no-keyboard, one-button signup is the whole point of
  // the in-app trial card. This route only ever understood the first shape,
  // so every request from an app failed the name/email check and came back
  // 400 "Please provide a valid name and email." The button could not
  // succeed, on any device, since it shipped.
  const deviceId = typeof body?.device_id === "string" ? body.device_id.trim() : "";
  const isAppClient = deviceId.length > 0;

  // The cookie gate is browser-only. An app sends no cookies, and its
  // per-device limit is enforced on device_id below.
  const trialCookie = req.cookies.get(TRIAL_COOKIE)?.value;
  if (!isAppClient && trialCookie) {
    return NextResponse.json(
      { error: "You've already used your free trial on this device. Log in to your dashboard or contact us on WhatsApp to upgrade." },
      { status: 403 }
    );
  }

  const name = typeof body?.name === "string" ? body.name.trim() : "";
  const email = typeof body?.email === "string" ? body.email.trim().toLowerCase() : "";
  const device = typeof body?.device === "string"
    ? body.device.trim()
    : typeof body?.client === "string" ? body.client.trim() : "";
  // An app has no email to key the limit on, so its stable install id plays
  // the part the browser fingerprint plays for the web form.
  const fingerprint = isAppClient
    ? deviceId
    : typeof body?.fp === "string" ? body.fp.trim() : "";

  if (!isAppClient && (!name || !email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))) {
    return NextResponse.json({ error: "Please provide a valid name and email." }, { status: 400 });
  }

  const ip = getClientIp(req);

  // One gate for all three limits, backed by a store that survives a cold
  // start. An app has no email to key on, so its stable install id plays the
  // part the browser fingerprint plays for the web form.
  const verdict = await checkTrialAllowed({
    deviceId: fingerprint,
    ip,
    email: email || undefined,
  });
  if (!verdict.allowed) {
    if (verdict.reason === "device_used") {
      console.warn(`[trial] repeat device blocked: ${fingerprint}`);
    }
    const { status, error } = gateResponse(verdict);
    return NextResponse.json({ error }, { status });
  }

  if (!trialGateEnabled) {
    // Loud, because a production deploy without the Redis integration attached
    // is silently handing out unlimited trials.
    console.warn("[trial] no KV/Redis configured — the one-trial-per-device limit is NOT enforced");
  }

  const result = await provisionSubscription("trial", email);
  if (!result.ok) {
    console.error(`[trial] Provisioning failed for ${email}: ${result.error}`);
    return NextResponse.json(
      { error: "Something went wrong showing your credentials, but your account may have been created. Please check your email for login details, or contact us on WhatsApp." },
      { status: 502 }
    );
  }

  // Recorded only now, after the panel has confirmed a line exists. Writing it
  // before provisioning would mean a panel timeout — not the caller's fault,
  // and something they will retry — permanently consuming the one trial they
  // were entitled to.
  await commitTrial({
    deviceId: fingerprint,
    ip,
    email: email || undefined,
    expiresAt: new Date(result.subscription.endDate).getTime(),
  });

  const subscription = { ...result.subscription, device };
  // No email to send to when the caller is an app — the credentials are
  // handed straight back and used in place.
  if (email) {
    sendWelcomeEmail({ to: email, name, subscription: result.subscription, device }).catch(() => {});
  }

  // Apps read a flat payload: server_url + username + password + expires_at.
  // The nested { subscription } envelope is what the website expects, and
  // carried no server field at all — so even once a request got past the
  // name/email gate the app would have failed on "Trial response missing
  // server URL". Both shapes are returned; each caller reads its own.
  if (isAppClient) {
    return NextResponse.json({
      server_url: result.subscription.serverUrl,
      username: result.subscription.username,
      password: result.subscription.password,
      expires_at: new Date(result.subscription.endDate).getTime(),
      subscription,
    });
  }

  const res = NextResponse.json({ subscription });
  res.cookies.set(TRIAL_COOKIE, "1", {
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    maxAge: COOKIE_MAX_AGE,
    path: "/",
  });
  return res;
}
