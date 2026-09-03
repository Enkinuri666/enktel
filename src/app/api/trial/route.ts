import { NextRequest, NextResponse } from "next/server";
import { createLine } from "@/lib/reseller";
import { TRIAL_HOURS } from "@/lib/pricing";

export const dynamic = "force-dynamic";

/**
 * Issue a {@link TRIAL_HOURS}-hour trial line.
 *
 * The email is taken so the trial can be tied to a person and so the login can
 * be re-sent, and it is passed to the panel as a note rather than used as the
 * username — a username derived from an email address is guessable, and these
 * lines are free to create.
 */
export async function POST(req: NextRequest) {
  let email = "";
  try {
    const body = (await req.json()) as { email?: string };
    email = (body.email ?? "").trim().toLowerCase();
  } catch {
    return NextResponse.json({ error: "Send a JSON body with an email." }, { status: 400 });
  }

  // Deliberately shallow. Address validation by regex is a losing game and a
  // wrong rejection here costs a trial signup; the panel and the mail that
  // follows are what actually establish whether it works.
  if (!email || !email.includes("@") || email.length > 254) {
    return NextResponse.json({ error: "Enter a valid email address." }, { status: 400 });
  }

  const line = await createLine(undefined, `24h trial · ${email}`);
  if (!line.ok) {
    // Configuration gaps are not the visitor's fault and must not read as a
    // rejection of them, so they answer 503 (try later) rather than 400.
    return NextResponse.json(
      { error: line.error, pending: Boolean(line.unconfigured) },
      { status: line.unconfigured ? 503 : 502 }
    );
  }

  return NextResponse.json({
    hours: TRIAL_HOURS,
    username: line.username,
    password: line.password,
    serverUrl: line.serverUrl,
    m3uUrl: line.m3uUrl,
    epgUrl: line.epgUrl,
    expiresAt: line.expiresAt,
  });
}
