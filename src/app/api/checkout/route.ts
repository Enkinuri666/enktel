import { NextRequest, NextResponse } from "next/server";
import { planById, formatPrice } from "@/lib/pricing";
import { createOrder, paypalConfigured } from "@/lib/paypal";

export const dynamic = "force-dynamic";

/**
 * Open a PayPal order for a plan.
 *
 * The body names a plan; it never carries a price. The amount is looked up
 * here from the plan table, so a buyer editing the request can change *what
 * they are buying* but never *what it costs*.
 */
export async function POST(req: NextRequest) {
  if (!paypalConfigured()) {
    return NextResponse.json(
      { error: "Card and PayPal checkout is not switched on yet. Contact support to complete your order." },
      { status: 503 }
    );
  }

  let planId = "";
  let email = "";
  let renewUsername = "";
  try {
    const body = (await req.json()) as {
      plan?: string;
      email?: string;
      renewUsername?: string;
    };
    planId = (body.plan ?? "").trim();
    email = (body.email ?? "").trim().toLowerCase();
    renewUsername = (body.renewUsername ?? "").trim();
  } catch {
    return NextResponse.json({ error: "Send a JSON body." }, { status: 400 });
  }

  const plan = planById(planId);
  if (!plan) return NextResponse.json({ error: "Unknown plan." }, { status: 400 });

  const reference = JSON.stringify({
    p: plan.id,
    e: email.slice(0, 80),
    r: renewUsername.slice(0, 40),
  });

  try {
    const order = await createOrder(
      plan.price,
      `Enktel ${plan.name} — ${formatPrice(plan.price)}`,
      reference
    );
    return NextResponse.json({ id: order.id });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : "Could not start the payment." },
      { status: 502 }
    );
  }
}
