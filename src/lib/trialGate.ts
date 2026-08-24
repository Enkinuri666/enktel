import { Redis } from "@upstash/redis";

/**
 * Durable "has this device already had a free trial?" bookkeeping.
 *
 * ### Why this exists
 *
 * The trial route used to hold its limits in module-level `Map`s. On Vercel
 * that is not a rate limiter, it is a decoration: every serverless invocation
 * may land on a different instance, instances are recycled constantly, and a
 * cold start begins with the maps empty. So the "one free trial per device"
 * rule the whole thing was built around did not actually hold — retrying a few
 * times was usually enough to get another one, and every trial is a real line
 * on the reseller panel that costs real money.
 *
 * Upstash Redis rather than `@vercel/kv`: Vercel KV is deprecated, and an
 * existing KV store is migrated to Upstash under Vercel Integrations. This is
 * the client that store actually speaks, and `@vercel/kv` was a thin wrapper
 * over it.
 *
 * ### Configuration
 *
 * Reads whichever pair of environment variables is present:
 *
 *  - `KV_REST_API_URL` / `KV_REST_API_TOKEN` — what a migrated Vercel KV store
 *    still injects
 *  - `UPSTASH_REDIS_REST_URL` / `UPSTASH_REDIS_REST_TOKEN` — what a fresh
 *    Marketplace Redis integration injects
 *
 * Supporting both means the same code works whether the store predates the
 * migration or was created after it, which is not knowable from here.
 */

const url = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL || "";
const token = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN || "";

/** False in local dev and in any preview without the integration attached. */
export const trialGateEnabled = Boolean(url && token);

const redis = trialGateEnabled ? new Redis({ url, token }) : null;

/**
 * How long a device is remembered.
 *
 * A year, not forever. The rule being enforced is "one trial", and keeping the
 * key indefinitely would mean a device that changed hands — a resold stick, a
 * factory reset that happened to keep the same id — is locked out for good with
 * no way to appeal. A year is far longer than anyone abusing this would wait.
 */
const DEVICE_TTL_SECONDS = 365 * 24 * 60 * 60;

/** Trials allowed from one IP in a day. A household may have several devices. */
const MAX_TRIALS_PER_IP = 3;
const IP_TTL_SECONDS = 24 * 60 * 60;

/** One trial request per email per ten minutes, to absorb double-taps. */
const EMAIL_COOLDOWN_SECONDS = 10 * 60;

export interface TrialRecord {
  createdAt: number;
  expiresAt: number;
}

export type GateVerdict =
  | { allowed: true }
  | { allowed: false; reason: "device_used"; record: TrialRecord | null }
  | { allowed: false; reason: "ip_limit" }
  | { allowed: false; reason: "email_cooldown" }
  | { allowed: false; reason: "unavailable" };

/**
 * Namespaced so the store can be shared with anything else later without two
 * features colliding on a bare device id.
 */
const deviceKey = (id: string) => `trial:device:${id}`;
const ipKey = (ip: string) => `trial:ip:${ip}`;
const emailKey = (email: string) => `trial:email:${email.toLowerCase()}`;

/**
 * Can this request have a trial?
 *
 * Records nothing — call [commitTrial] once the line actually exists, so a
 * panel failure does not burn the caller's one attempt.
 *
 * ### Unconfigured and failing are different
 *
 * With no store configured this returns `allowed`. Local development and
 * preview deployments have no integration attached, and refusing every trial
 * there would make the feature untestable outside production.
 *
 * A store that *is* configured but throws returns `unavailable`, which the
 * route turns into a "try again shortly" rather than a trial. That asymmetry is
 * deliberate: an absent store is a permanent, known state, while a failing one
 * is transient and retryable — and treating a transient failure as permission
 * is exactly how an outage becomes an unbounded free-line giveaway.
 */
export async function checkTrialAllowed(opts: {
  deviceId: string;
  ip: string;
  email?: string;
}): Promise<GateVerdict> {
  if (!redis) return { allowed: true };

  try {
    const { deviceId, ip, email } = opts;

    if (deviceId) {
      const existing = await redis.get<TrialRecord>(deviceKey(deviceId));
      // Deliberately not "…and the trial is still running". The rule is one
      // trial per device, so an expired trial is still a used one — that is
      // the whole point of the gate, and checking expiry here would hand out a
      // fresh trial every 24 hours for ever.
      if (existing) return { allowed: false, reason: "device_used", record: existing };
    }

    if (email) {
      const recent = await redis.get(emailKey(email));
      if (recent) return { allowed: false, reason: "email_cooldown" };
    }

    if (ip && ip !== "unknown") {
      const hits = await redis.get<number>(ipKey(ip));
      if (typeof hits === "number" && hits >= MAX_TRIALS_PER_IP) {
        return { allowed: false, reason: "ip_limit" };
      }
    }

    return { allowed: true };
  } catch (err) {
    console.error("[trialGate] store unreachable:", err);
    return { allowed: false, reason: "unavailable" };
  }
}

/**
 * Record a trial that was actually provisioned.
 *
 * Separate from the check so the write happens after the panel has confirmed a
 * line exists. Recording up front would mean a panel timeout — which is not the
 * caller's fault and which they will retry — permanently consuming the one
 * trial they were entitled to.
 *
 * Never throws. A trial that succeeded but could not be written down is worth
 * less than a trial that failed after the customer already had their
 * credentials, so the failure is logged and swallowed.
 */
export async function commitTrial(opts: {
  deviceId: string;
  ip: string;
  email?: string;
  expiresAt: number;
}): Promise<void> {
  if (!redis) return;

  const { deviceId, ip, email, expiresAt } = opts;
  try {
    const writes: Promise<unknown>[] = [];

    if (deviceId) {
      const record: TrialRecord = { createdAt: Date.now(), expiresAt };
      writes.push(redis.set(deviceKey(deviceId), record, { ex: DEVICE_TTL_SECONDS }));
    }
    if (email) {
      writes.push(redis.set(emailKey(email), 1, { ex: EMAIL_COOLDOWN_SECONDS }));
    }
    if (ip && ip !== "unknown") {
      // INCR then EXPIRE, so the 24h window starts at the first trial from this
      // address rather than sliding forward with every one after it. A sliding
      // window would let a steady trickle stay under the cap indefinitely.
      writes.push(
        redis.incr(ipKey(ip)).then((n) => (n === 1 ? redis.expire(ipKey(ip), IP_TTL_SECONDS) : null))
      );
    }

    await Promise.all(writes);
  } catch (err) {
    console.error("[trialGate] failed to record trial:", err);
  }
}

/** What a blocked caller should be told, and with which status code. */
export function gateResponse(verdict: Exclude<GateVerdict, { allowed: true }>): {
  status: number;
  error: string;
} {
  switch (verdict.reason) {
    case "device_used":
      // 409, which the Android client already maps to "This device already
      // used its free trial" — a better message than the generic 429 text it
      // was getting before.
      return {
        status: 409,
        error:
          "This device has already used its free 24-hour trial. " +
          "Get 12 months of full access for $99 USD — no subscription, nothing to cancel.",
      };
    case "email_cooldown":
      return {
        status: 429,
        error:
          "A trial was already requested for this email. Check your inbox for your login details, " +
          "or contact us on WhatsApp if you need help.",
      };
    case "ip_limit":
      return {
        status: 429,
        error:
          "Too many trial requests from your network. Please try again later or contact us on WhatsApp.",
      };
    case "unavailable":
      return {
        status: 503,
        error: "Trial signup is temporarily unavailable. Please try again in a few minutes.",
      };
  }
}
