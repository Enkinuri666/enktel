import { NextRequest, NextResponse } from "next/server";
import { paypalEnabled, createOrder, captureOrder } from "@/lib/paypal";
import { PLAN_NAME, PLAN_PRICE_EUR, provisionSubscription } from "@/lib/reseller";
import { sendWelcomeEmail } from "@/lib/email";

const provisionedOrders = new Map<string, unknown>();

export async function POST(req: NextRequest) {
  if (!paypalEnabled) {
    return NextResponse.json({ error: "Payments are not configured yet — contact support" }, { status: 503 });
  }

  try {
    const body = await req.json();
    const { plan, action, orderId, name, email } = body as {
      plan?: string;
      action?: string;
      orderId?: string;
      name?: string;
      email?: string;
    };

    if (action === "create" && plan && PLAN_PRICE_EUR[plan]) {
      const order = await createOrder(
        PLAN_NAME[plan] || plan,
        PLAN_PRICE_EUR[plan]
      );
      return NextResponse.json({ orderId: order.id });
    }

    if (action === "capture" && orderId && name && email && plan) {
      if (provisionedOrders.has(orderId)) {
        return NextResponse.json({ success: true, subscription: provisionedOrders.get(orderId) });
      }

      const capture = await captureOrder(orderId);
      if (capture.status !== "COMPLETED") {
        return NextResponse.json({ error: "Payment not completed" }, { status: 402 });
      }

      const result = await provisionSubscription(plan, email);
      if (!result.ok) {
        return NextResponse.json(
          { error: `Payment succeeded but activation failed: ${result.error}. Contact support with your order ID: ${orderId}` },
          { status: 502 }
        );
      }

      provisionedOrders.set(orderId, result.subscription);
      sendWelcomeEmail({ to: email, name, subscription: result.subscription }).catch(() => {});
      return NextResponse.json({ success: true, subscription: result.subscription });
    }

    return NextResponse.json({ error: "Missing or invalid fields" }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : "Could not process payment" },
      { status: 500 }
    );
  }
}
