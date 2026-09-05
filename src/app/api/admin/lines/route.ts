import { NextRequest, NextResponse } from "next/server";
import { createHash, timingSafeEqual } from "node:crypto";
import { deleteLine } from "@/lib/reseller";

export const dynamic = "force-dynamic";

/**
 * Operator-only line management. Currently: delete one.
 *
 * ## Why this is not the cron route's auth
 *
 * `api/cron/warm-cache` guards itself with `if (process.env.CRON_SECRET && …)`,
 * which means an unset variable leaves it open. That is survivable for a route
 * that warms a cache. It is not survivable for one that destroys lines, so
 * this fails closed: no `ADMIN_API_KEY` in the environment, no route. There is
 * no development bypass, because the panel it talks to is the live one either
 * way — there is no sandbox behind this key.
 *
 * The comparison is constant time over SHA-256 digests. Digests rather than
 * the raw strings because `timingSafeEqual` throws on a length mismatch, and
 * that throw is itself an oracle for the key's length.
 *
 *     curl -X DELETE https://enktel.tv/api/admin/lines \
 *       -H "Authorization: Bearer $ADMIN_API_KEY" \
 *       -H 'Content-Type: application/json' \
 *       -d '{"username":"z19ci5cbxe9dre"}'
 */
function authorised(req: NextRequest): boolean {
  const expected = process.env.ADMIN_API_KEY;
  if (!expected) return false;

  const header = req.headers.get("authorization") ?? "";
  const presented = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!presented) return false;

  return timingSafeEqual(
    createHash("sha256").update(presented).digest(),
    createHash("sha256").update(expected).digest()
  );
}

export async function DELETE(req: NextRequest) {
  if (!authorised(req)) {
    // One message for "no key configured", "no key sent" and "wrong key". A
    // response that distinguishes them tells an attacker which half of the
    // problem to work on.
    return NextResponse.json({ error: "Not authorised." }, { status: 401 });
  }

  let username = "";
  let password: string | undefined;
  try {
    const body = (await req.json()) as { username?: string; password?: string };
    username = (body.username ?? "").trim();
    password = body.password?.trim() || undefined;
  } catch {
    return NextResponse.json({ error: "Send a JSON body with a username." }, { status: 400 });
  }

  if (!username) {
    return NextResponse.json({ error: "Send a JSON body with a username." }, { status: 400 });
  }

  const result = await deleteLine(username, password);
  if (!result.ok) {
    // The panel's own words, verbatim. This route has one operator and no
    // customers, so a message that says exactly what the panel objected to is
    // worth more than a tidy one.
    return NextResponse.json(
      { error: result.error, unconfigured: Boolean(result.unconfigured) },
      { status: result.unconfigured ? 503 : 502 }
    );
  }

  return NextResponse.json({ deleted: username });
}
