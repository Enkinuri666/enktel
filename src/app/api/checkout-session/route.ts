import { NextRequest, NextResponse } from "next/server";
import { stripe, stripeEnabled } from "@/lib/stripe";
import { PLAN_NAME, PLAN_PRICE_EUR, provisionSubscription } from "@/lib/reseller";

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || "http://localhost:3000";

// In-memory cache so a page refresh on the success page doesn't provision
// a second IPTV line for the same paid Stripe session.
const provisionedSessions = new Map<string, unknown>();

export async function POST(req: NextRequest) {
  if (!stripeEnabled) {
    return NextResponse.json({ error: "Payments are not configured yet — contact support" }, { status: 503 });
  }

  try {
    const { name, email, plan } = (await req.json()) as { name: string; email: string; plan: string };

    if (!name || !email || !plan || !PLAN_PRICE_EUR[plan]) {
      return NextResponse.json({ error: "Missing or invalid fields" }, { status: 400 });
    }

    const session = await stripe.checkout.sessions.create({
      mode: "payment",
      payment_method_types: ["card"],
      customer_email: email,
      line_items: [
        {
          price_data: {
            currency: "eur",
            product_data: { name: `Enktel IPTV — ${PLAN_NAME[plan]}` },
            unit_amount: Math.round(PLAN_PRICE_EUR[plan] * 100),
          },
          quantity: 1,
        },
      ],
      metadata: { name, email, plan },
      success_url: `${SITE_URL}/checkout/success?session_id={CHECKOUT_SESSION_ID}`,
      cancel_url: `${SITE_URL}/checkout?plan=${plan}`,
    });

    return NextResponse.json({ url: session.url });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : "Could not start checkout" },
      { status: 500 }
    );
  }
}

export async function GET(req: NextRequest) {
  if (!stripeEnabled) {
    return NextResponse.json({ error: "Payments are not configured yet" }, { status: 503 });
  }

  const sessionId = req.nextUrl.searchParams.get("session_id");
  if (!sessionId) {
    return NextResponse.json({ error: "Missing session_id" }, { status: 400 });
  }

  try {
    if (provisionedSessions.has(sessionId)) {
      return NextResponse.json({ success: true, subscription: provisionedSessions.get(sessionId) });
    }

    const session = await stripe.checkout.sessions.retrieve(sessionId);

    if (session.payment_status !== "paid") {
      return NextResponse.json({ error: "Payment not completed" }, { status: 402 });
    }

    const { email, plan } = session.metadata as { name: string; email: string; plan: string };

    const result = await provisionSubscription(plan, email);
    if (!result.ok) {
      return NextResponse.json(
        { error: `Payment succeeded but activation failed: ${result.error}. Contact support with your session ID: ${sessionId}` },
        { status: 502 }
      );
    }

    provisionedSessions.set(sessionId, result.subscription);
    return NextResponse.json({ success: true, subscription: result.subscription });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : "Could not verify payment" },
      { status: 500 }
    );
  }
}
