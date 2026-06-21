import { ProvisionedSubscription } from "./reseller";
import { getDeviceGuide } from "./deviceGuides";

const RESEND_API_KEY = process.env.RESEND_API_KEY || "";
const FROM_EMAIL = process.env.RESEND_FROM_EMAIL || "Enktel IPTV <onboarding@resend.dev>";
const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || "https://enktel.tv";
const WHATSAPP_NUMBER = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER || "";

interface WelcomeEmailOptions {
  to: string;
  name: string;
  subscription: ProvisionedSubscription;
  device?: string;
}

function whatsappLine(): string {
  if (!WHATSAPP_NUMBER) return "";
  const href = `https://wa.me/${WHATSAPP_NUMBER}`;
  return `Need a hand? Our support team is on WhatsApp 24/7: ${href}`;
}

function deviceSteps(device?: string): string {
  const guide = getDeviceGuide(device);
  if (!guide) {
    return `Head to ${SITE_URL}/setup-guides and pick your device for step-by-step setup instructions.`;
  }
  return [
    `Setting up on ${guide.label} (${guide.app}):`,
    ...guide.steps.map((s) => `  ${s.step}. ${s.title} — ${s.description}`),
  ].join("\n");
}

function buildText(opts: WelcomeEmailOptions): string {
  const { name, subscription: sub } = opts;
  const isTrial = sub.isTrial;
  const expiry = new Date(sub.endDate).toLocaleString("en-GB");

  return `Hi ${name},

${isTrial ? "Your 24-hour Enktel IPTV trial is live!" : "Welcome to Enktel IPTV — your subscription is now active!"}

Here are your stream credentials:
  Username: ${sub.username}
  Password: ${sub.password}
  M3U Playlist URL: ${sub.m3uUrl}
  EPG / XMLTV URL: ${sub.epgUrl}

${isTrial ? `Your trial expires: ${expiry}. Love it? Upgrade any time at ${SITE_URL}/pricing.` : `Your subscription is valid until: ${expiry}.`}

Getting started:
${deviceSteps(opts.device)}

Manage your subscription, copy your URLs again, and find detailed troubleshooting any time in your dashboard:
${SITE_URL}/dashboard

${whatsappLine()}

Thanks for choosing Enktel IPTV.
`;
}

function buildHtml(opts: WelcomeEmailOptions): string {
  const { name, subscription: sub, device } = opts;
  const isTrial = sub.isTrial;
  const expiry = new Date(sub.endDate).toLocaleString("en-GB");
  const guide = getDeviceGuide(device);
  const whatsappHref = WHATSAPP_NUMBER ? `https://wa.me/${WHATSAPP_NUMBER}` : "";

  const stepsHtml = guide
    ? `<p style="color:#9aa3b2;font-size:14px;margin:0 0 8px;">Setting up on <strong style="color:#fff;">${guide.label}</strong> (${guide.app}):</p>
       <ol style="color:#c7ccd6;font-size:13px;line-height:1.6;padding-left:20px;margin:0;">
         ${guide.steps.map((s) => `<li style="margin-bottom:6px;"><strong style="color:#fff;">${s.title}</strong> — ${s.description}</li>`).join("")}
       </ol>`
    : `<p style="color:#9aa3b2;font-size:14px;margin:0;">Head to <a href="${SITE_URL}/setup-guides" style="color:#00d4ff;">${SITE_URL}/setup-guides</a> and pick your device for step-by-step setup instructions.</p>`;

  return `<!DOCTYPE html>
<html>
<body style="margin:0;padding:0;background:#0b0f1a;font-family:Arial,Helvetica,sans-serif;">
  <div style="max-width:560px;margin:0 auto;padding:32px 24px;">
    <h1 style="color:#fff;font-size:22px;margin:0 0 4px;">
      ${isTrial ? "Your 24-Hour Trial is Live 🎉" : "Welcome to Enktel IPTV 🎉"}
    </h1>
    <p style="color:#9aa3b2;font-size:14px;margin:0 0 24px;">Hi ${name}, ${isTrial ? "your free trial has been activated." : "your subscription is now active."}</p>

    <div style="background:#141a2b;border:1px solid #232b40;border-radius:12px;padding:20px;margin-bottom:20px;">
      <p style="color:#9aa3b2;font-size:12px;margin:0 0 4px;">Username</p>
      <p style="color:#fff;font-family:monospace;font-size:15px;font-weight:bold;margin:0 0 12px;">${sub.username}</p>
      <p style="color:#9aa3b2;font-size:12px;margin:0 0 4px;">Password</p>
      <p style="color:#fff;font-family:monospace;font-size:15px;font-weight:bold;margin:0 0 12px;">${sub.password}</p>
      <p style="color:#9aa3b2;font-size:12px;margin:0 0 4px;">M3U Playlist URL</p>
      <p style="color:#00d4ff;font-family:monospace;font-size:12px;word-break:break-all;margin:0 0 12px;">${sub.m3uUrl}</p>
      <p style="color:#9aa3b2;font-size:12px;margin:0 0 4px;">EPG / XMLTV URL</p>
      <p style="color:#00d4ff;font-family:monospace;font-size:12px;word-break:break-all;margin:0;">${sub.epgUrl}</p>
    </div>

    <p style="color:${isTrial ? "#fbbf24" : "#9aa3b2"};font-size:13px;margin:0 0 24px;">
      ${isTrial
        ? `Your trial expires <strong>${expiry}</strong>. Love it? <a href="${SITE_URL}/pricing" style="color:#00d4ff;">Upgrade to a full plan</a> any time before it ends to keep watching without interruption.`
        : `Your subscription is valid until <strong>${expiry}</strong>.`}
    </p>

    <div style="background:#141a2b;border:1px solid #232b40;border-radius:12px;padding:20px;margin-bottom:24px;">
      <h2 style="color:#fff;font-size:15px;margin:0 0 10px;">Getting Started</h2>
      ${stepsHtml}
    </div>

    <a href="${SITE_URL}/dashboard" style="display:inline-block;background:#7c3aed;color:#fff;text-decoration:none;font-weight:bold;font-size:14px;padding:12px 24px;border-radius:8px;margin-bottom:20px;">
      Open Your Dashboard
    </a>

    ${whatsappHref ? `<p style="color:#9aa3b2;font-size:13px;margin:16px 0 0;">Need a hand? Our support team is on <a href="${whatsappHref}" style="color:#25D366;font-weight:bold;">WhatsApp 24/7</a> — just say hi.</p>` : ""}

    <p style="color:#5a6275;font-size:12px;margin:32px 0 0;">Thanks for choosing Enktel IPTV.</p>
  </div>
</body>
</html>`;
}

export async function sendWelcomeEmail(opts: WelcomeEmailOptions): Promise<void> {
  if (!RESEND_API_KEY) {
    console.log("[email] Resend not configured, skipping welcome email for", opts.to);
    return;
  }

  try {
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${RESEND_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from: FROM_EMAIL,
        to: [opts.to],
        subject: opts.subscription.isTrial
          ? "Your 24-Hour Enktel IPTV Trial is Live"
          : "Welcome to Enktel IPTV — Your Subscription is Active",
        text: buildText(opts),
        html: buildHtml(opts),
      }),
      signal: AbortSignal.timeout(10000),
    });

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      console.error("[email] Resend error:", res.status, text);
    }
  } catch (err) {
    console.error("[email] welcome email send failed:", err);
  }
}
