import { NextRequest, NextResponse } from "next/server";
import { provisionSubscription } from "@/lib/reseller";
import { sendWelcomeEmail } from "@/lib/email";

const TRIAL_COOKIE = "enktel_trial";
const COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days

const EMAIL_COOLDOWN_MS = 10 * 60 * 1000;
const IP_WINDOW_MS = 24 * 60 * 60 * 1000;
const MAX_TRIALS_PER_IP = 3;
const FP_WINDOW_MS = 24 * 60 * 60 * 1000;
const MAX_TRIALS_PER_FP = 3;

const recentEmails = new Map<string, number>();
const ipTrials = new Map<string, number[]>();
const fpTrials = new Map<string, number[]>();

function cleanup() {
  const now = Date.now();
  recentEmails.forEach((ts, k) => {
    if (now - ts > EMAIL_COOLDOWN_MS) recentEmails.delete(k);
  });
  ipTrials.forEach((timestamps, k) => {
    const valid = timestamps.filter((t) => now - t < IP_WINDOW_MS);
    if (valid.length === 0) ipTrials.delete(k);
    else ipTrials.set(k, valid);
  });
  fpTrials.forEach((timestamps, k) => {
    const valid = timestamps.filter((t) => now - t < FP_WINDOW_MS);
    if (valid.length === 0) fpTrials.delete(k);
    else fpTrials.set(k, valid);
  });
}

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

  cleanup();

  const lastEmail = recentEmails.get(email);
  if (lastEmail && Date.now() - lastEmail < EMAIL_COOLDOWN_MS) {
    return NextResponse.json(
      { error: "A trial was already requested for this email. Check your inbox for your login details, or contact us on WhatsApp if you need help." },
      { status: 429 }
    );
  }

  const ip = getClientIp(req);
  if (ip !== "unknown") {
    const ipHits = ipTrials.get(ip) || [];
    if (ipHits.length >= MAX_TRIALS_PER_IP) {
      console.warn(`[trial] IP rate limit hit: ${ip} (${ipHits.length} trials in 24h)`);
      return NextResponse.json(
        { error: "Too many trial requests from your network. Please try again later or contact us on WhatsApp." },
        { status: 429 }
      );
    }
  }

  if (fingerprint) {
    const fpHits = fpTrials.get(fingerprint) || [];
    if (fpHits.length >= MAX_TRIALS_PER_FP) {
      console.warn(`[trial] Fingerprint rate limit hit: ${fingerprint}`);
      return NextResponse.json(
        { error: "You've already used your free trial. Log in to your dashboard or contact us on WhatsApp to upgrade." },
        { status: 429 }
      );
    }
  }

  recentEmails.set(email, Date.now());

  const result = await provisionSubscription("trial", email);
  if (!result.ok) {
    console.error(`[trial] Provisioning failed for ${email}: ${result.error}`);
    return NextResponse.json(
      { error: "Something went wrong showing your credentials, but your account may have been created. Please check your email for login details, or contact us on WhatsApp." },
      { status: 502 }
    );
  }

  if (ip !== "unknown") {
    const ipHits = ipTrials.get(ip) || [];
    ipHits.push(Date.now());
    ipTrials.set(ip, ipHits);
  }
  if (fingerprint) {
    const fpHits = fpTrials.get(fingerprint) || [];
    fpHits.push(Date.now());
    fpTrials.set(fingerprint, fpHits);
  }

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
