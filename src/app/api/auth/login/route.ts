import { NextRequest, NextResponse } from "next/server";
import { verifyStreamCredentials } from "@/lib/reseller";

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  const username = typeof body?.username === "string" ? body.username.trim() : "";
  const password = typeof body?.password === "string" ? body.password.trim() : "";

  if (!username || !password) {
    return NextResponse.json({ error: "Please enter your username and password." }, { status: 400 });
  }

  const result = await verifyStreamCredentials(username, password);
  if (!result.ok) {
    return NextResponse.json({ error: result.error }, { status: 401 });
  }

  return NextResponse.json({ subscription: result.subscription });
}
