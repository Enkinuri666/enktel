import { NextRequest, NextResponse } from "next/server";
import { captureOrder, paypalConfigured } from "@/lib/paypal";
import { planById } from "@/lib/pricing";
import { createLine, extendLine } from "@/lib/reseller";

export const dynamic = "force-dynamic";

/**
 * Take the payment, then provision.
 *
 * In that order, and never the reverse: a line created before the money lands
 * is a line given away to anyone who starts a checkout and abandons it.
 *
 * The plan is read from the *captured order's* own reference rather than from
 * the request body, so what gets provisioned is what PayPal says was bought.
 */
export async function POST(req: NextRequest) {
  if (!paypalConfigured()) {
    return NextResponse.json({ error: "Checkout is not switched on yet." }, { status: 503 });
  }

  let orderId = "";
  // Renewing needs the line's password as well as its username, because the
  // panel's extend call does. It arrives here, over HTTPS to our own server,
  // rather than in the PayPal reference or a query string: PayPal has no
  // business holding a customer's stream password, and a URL ends up in
  // browser history, logs and referrers.
  let renewPassword = "";
  try {
    const body = (await req.json()) as { orderId?: string; renewPassword?: string };
    orderId = (body.orderId ?? "").trim();
    renewPassword = (body.renewPassword ?? "").trim();
  } catch {
    return NextResponse.json({ error: "Send a JSON body." }, { status: 400 });
  }
  if (!orderId) return NextResponse.json({ error: "Missing order id." }, { status: 400 });

  const capture = await captureOrder(orderId);
  if (!capture.ok) {
    return NextResponse.json(
      { error: `Payment was not completed (${capture.status}).` },
      { status: 402 }
    );
  }

  let ref: { p?: string; e?: string; r?: string } = {};
  try {
    ref = JSON.parse(capture.reference ?? "{}");
  } catch {
    /* an unreadable reference is recoverable — the payment still stands */
  }
  const plan = planById(ref.p ?? "");

  // Past this point the customer has paid. Nothing below may answer with an
  // error that suggests otherwise: every failure here is ours to fix by hand,
  // and it says so.
  if (!plan) {
    return NextResponse.json({
      paid: true,
      pending: true,
      message:
        "Payment received. We could not match it to a plan automatically — support will set your line up and email you shortly.",
      amount: capture.amount,
      currency: capture.currency,
    });
  }

  const result = ref.r
    ? await extendLine(ref.r, renewPassword, plan.id)
    : await createLine(plan.id, `${plan.name} · ${ref.e ?? capture.payerEmail ?? ""}`);

  if (!result.ok) {
    return NextResponse.json({
      paid: true,
      pending: true,
      plan: plan.id,
      message: result.error,
      amount: capture.amount,
      currency: capture.currency,
    });
  }

  if (ref.r) {
    return NextResponse.json({
      paid: true,
      renewed: true,
      plan: plan.id,
      expiresAt: "expiresAt" in result ? result.expiresAt : undefined,
      amount: capture.amount,
      currency: capture.currency,
    });
  }

  return NextResponse.json({
    paid: true,
    plan: plan.id,
    ...result,
    amount: capture.amount,
    currency: capture.currency,
  });
}
