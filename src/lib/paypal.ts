/**
 * PayPal Orders v2, for taking payment for an access pass.
 *
 * ## Why the amount is never taken from the browser
 *
 * The client tells us *which plan*; it never tells us what that plan costs.
 * Everything money-related is looked up server-side from `@/lib/pricing`,
 * because a price posted from a page is a price the buyer can edit — the
 * oldest hole in web checkout, and the one worth being boring about.
 *
 * ## Configuration
 *
 * `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, and `PAYPAL_ENV` (`live` or
 * `sandbox`, default `sandbox`). Absent any of them, [paypalConfigured] is
 * false and the checkout says so plainly instead of rendering a button that
 * fails on click.
 */

import { CURRENCY } from "./pricing";

const LIVE = "https://api-m.paypal.com";
const SANDBOX = "https://api-m.sandbox.paypal.com";

export function paypalBase(): string {
  return process.env.PAYPAL_ENV === "live" ? LIVE : SANDBOX;
}

export function paypalConfigured(): boolean {
  return Boolean(process.env.PAYPAL_CLIENT_ID && process.env.PAYPAL_CLIENT_SECRET);
}

/** The client id the browser SDK needs. Public by design; the secret is not. */
export function paypalClientId(): string {
  return process.env.PAYPAL_CLIENT_ID ?? "";
}

async function accessToken(): Promise<string> {
  const id = process.env.PAYPAL_CLIENT_ID;
  const secret = process.env.PAYPAL_CLIENT_SECRET;
  if (!id || !secret) throw new Error("PayPal is not configured");

  const res = await fetch(`${paypalBase()}/v1/oauth2/token`, {
    method: "POST",
    headers: {
      Authorization: `Basic ${Buffer.from(`${id}:${secret}`).toString("base64")}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: "grant_type=client_credentials",
    signal: AbortSignal.timeout(15000),
  });
  if (!res.ok) {
    throw new Error(`PayPal rejected the credentials (HTTP ${res.status})`);
  }
  const json = (await res.json()) as { access_token?: string };
  if (!json.access_token) throw new Error("PayPal returned no access token");
  return json.access_token;
}

export interface CreatedOrder {
  id: string;
}

/**
 * Open an order for [amount], described by [description].
 *
 * `custom_id` carries our own reference through to the capture and the
 * webhook, so a payment can always be tied back to the plan and the buyer
 * without trusting anything the browser sends back.
 */
export async function createOrder(
  amount: number,
  description: string,
  reference: string
): Promise<CreatedOrder> {
  const token = await accessToken();
  const res = await fetch(`${paypalBase()}/v2/checkout/orders`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      intent: "CAPTURE",
      purchase_units: [
        {
          description: description.slice(0, 127),
          custom_id: reference.slice(0, 127),
          amount: {
            currency_code: CURRENCY,
            value: amount.toFixed(2),
          },
        },
      ],
      application_context: {
        brand_name: "Enktel IPTV",
        shipping_preference: "NO_SHIPPING",
        user_action: "PAY_NOW",
      },
    }),
    signal: AbortSignal.timeout(20000),
  });
  const json = (await res.json()) as { id?: string; message?: string };
  if (!res.ok || !json.id) {
    throw new Error(json.message || `PayPal could not open the order (HTTP ${res.status})`);
  }
  return { id: json.id };
}

export interface CaptureResult {
  ok: boolean;
  status: string;
  /** What PayPal says was actually paid, which is what we bill against. */
  amount?: string;
  currency?: string;
  payerEmail?: string;
  reference?: string;
}

/**
 * Take the money.
 *
 * The captured amount is read back out of PayPal's response rather than
 * assumed: this is the only figure that reflects what the buyer was actually
 * charged, and provisioning should follow the payment, not the intent.
 */
export async function captureOrder(orderId: string): Promise<CaptureResult> {
  const token = await accessToken();
  const res = await fetch(
    `${paypalBase()}/v2/checkout/orders/${encodeURIComponent(orderId)}/capture`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      signal: AbortSignal.timeout(25000),
    }
  );
  const json = (await res.json()) as {
    status?: string;
    message?: string;
    payer?: { email_address?: string };
    purchase_units?: {
      custom_id?: string;
      payments?: {
        captures?: { amount?: { value?: string; currency_code?: string } }[];
      };
    }[];
  };
  if (!res.ok) {
    return { ok: false, status: json.message || `HTTP ${res.status}` };
  }
  const unit = json.purchase_units?.[0];
  const capture = unit?.payments?.captures?.[0];
  return {
    ok: json.status === "COMPLETED",
    status: json.status ?? "UNKNOWN",
    amount: capture?.amount?.value,
    currency: capture?.amount?.currency_code,
    payerEmail: json.payer?.email_address,
    reference: unit?.custom_id,
  };
}
