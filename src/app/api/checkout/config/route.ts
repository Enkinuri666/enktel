import { NextResponse } from "next/server";
import { paypalClientId, paypalConfigured } from "@/lib/paypal";

export const dynamic = "force-dynamic";

/**
 * The public half of the PayPal configuration.
 *
 * Served rather than inlined at build time so credentials can be added to a
 * deployment without rebuilding, and so a deployment without them returns an
 * empty string — which the checkout renders as "not switched on yet" instead
 * of a button that fails when pressed.
 */
export async function GET() {
  return NextResponse.json({ clientId: paypalConfigured() ? paypalClientId() : "" });
}
