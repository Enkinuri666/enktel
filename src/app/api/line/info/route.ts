import { NextRequest, NextResponse } from "next/server";
import { fetchLineInfo, verifyStreamCredentials } from "@/lib/reseller";

/**
 * What the panel will say about the caller's own line.
 *
 * ### Why the credentials are checked twice
 *
 * The reseller API key is trusted by the panel for *every* line on the
 * account, so `action=user&type=info` will answer for any username it is given
 * — including one the caller does not own. The only thing between a stranger
 * and someone else's account details is this route proving ownership first,
 * and it proves it the way the panel itself would: by authenticating the
 * username and password against `player_api.php`, which succeeds only for
 * whoever actually holds the line.
 *
 * So the sequence is deliberate and not redundant. Authenticate as the
 * customer, then and only then ask the panel as the reseller.
 *
 * ### What this endpoint is for, and what it is not
 *
 * It reports. The Eagle developer API's documented surface is create, extend,
 * list and info; there is no action that ends an active session and none that
 * clears an ISP lock. A "kick the other device" endpoint cannot be built on
 * it, and this route exists partly to make the boundary explicit: the app can
 * tell a viewer their line is occupied, and it can hang up its own stream, but
 * freeing someone else's session is not something the panel will do for a
 * customer.
 *
 * The panel payload is passed through as received. The response shape is
 * undocumented and varies between builds, so the fields are to be discovered
 * from a real call rather than modelled from a guess.
 */
export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const username = typeof body?.username === "string" ? body.username.trim() : "";
  const password = typeof body?.password === "string" ? body.password : "";
  const serverUrl = typeof body?.server_url === "string" ? body.server_url.trim() : undefined;

  if (!username || !password) {
    return NextResponse.json(
      { ok: false, error: "username and password are required." },
      { status: 400 }
    );
  }

  const owns = serverUrl
    ? await verifyStreamCredentials(username, password, serverUrl)
    : await verifyStreamCredentials(username, password);
  if (!owns.ok) {
    // The same answer whether the line does not exist or the password is
    // wrong, so this cannot be used to enumerate usernames.
    return NextResponse.json(
      { ok: false, error: "Invalid username or password." },
      { status: 401 }
    );
  }

  const info = await fetchLineInfo(username, password);
  if (!info.ok) {
    return NextResponse.json({ ok: false, error: info.error }, { status: 502 });
  }

  return NextResponse.json({ ok: true, ...info.info });
}
