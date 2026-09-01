import { NextRequest, NextResponse } from "next/server";
import { verifyStreamCredentials } from "@/lib/xtream";

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const username = typeof body?.username === "string" ? body.username.trim() : "";
  const password = typeof body?.password === "string" ? body.password.trim() : "";
  const server = typeof body?.server === "string" ? body.server.trim() : "";

  if (!username || !password) {
    return NextResponse.json({ error: "Please enter your username and password." }, { status: 400 });
  }

  // The server is the viewer's to supply now.
  //
  // This used to fall back to the Eagle host, which is retired. A login with
  // no server would have been checked against a panel that is not there and
  // come back "Invalid username or password" — the one message guaranteed to
  // send someone hunting for a typo that does not exist.
  if (!server) {
    return NextResponse.json(
      { error: "Please enter your provider's server address." },
      { status: 400 }
    );
  }
  if (!/^https?:\/\//i.test(server)) {
    return NextResponse.json(
      { error: "Server address should start with http:// or https://" },
      { status: 400 }
    );
  }

  const result = await verifyStreamCredentials(username, password, server);
  if (!result.ok) {
    return NextResponse.json({ error: result.error }, { status: 401 });
  }

  return NextResponse.json({ subscription: result.subscription });
}
