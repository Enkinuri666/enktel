import { NextResponse } from "next/server";
import Anthropic from "@anthropic-ai/sdk";
import { CHAT_TOOLS, runChatTool } from "@/lib/chatTools";

export const dynamic = "force-dynamic";

const SYSTEM_PROMPT = `You are the Enktel Assistant, a friendly and concise AI chat helper embedded on enktel.tv, a premium IPTV & streaming subscription service (live TV, sports, movies, and VOD).

You help with:
- Customer service: plans, pricing, billing policy, refunds, device limits
- Technical support: setup guides per device, buffering/playlist troubleshooting
- Entertainment & the live TV guide: what's on now/next on a channel, upcoming sports fixtures, browsing channels by category

Ground any factual answer — pricing, channel availability, setup steps, policy details, what's on now — by calling the relevant tool rather than guessing. If a tool returns no match, say so plainly instead of inventing details.

You cannot access a specific customer's account, process payments, issue refunds, or reset credentials. For anything account-specific, tell the person to use Live Chat, WhatsApp, or the Contact page — all reachable from the same chat button used to open you. Never claim to have taken an account action on the customer's behalf.

Keep replies short and conversational (2-4 sentences in most cases) — this is a live chat widget, not an email. Plain text only, no markdown headers or tables.`;

const MODEL = "claude-opus-4-8";
const MAX_TOOL_ITERATIONS = 4;

interface ChatMessageInput {
  role: "user" | "assistant";
  content: string;
}

export async function POST(request: Request) {
  const apiKey = process.env.ANTHROPIC_API_KEY;
  if (!apiKey) {
    return NextResponse.json(
      { error: "The AI assistant isn't configured yet. Please use Live Chat or WhatsApp instead." },
      { status: 503 }
    );
  }

  const body = await request.json().catch(() => null);
  const incoming: unknown = body?.messages;

  if (!Array.isArray(incoming) || incoming.length === 0) {
    return NextResponse.json({ error: "Missing messages" }, { status: 400 });
  }

  const messages: Anthropic.MessageParam[] = (incoming as ChatMessageInput[])
    .slice(-20)
    .filter((m) => (m?.role === "user" || m?.role === "assistant") && typeof m?.content === "string")
    .map((m) => ({ role: m.role, content: m.content }));

  if (messages.length === 0) {
    return NextResponse.json({ error: "Missing messages" }, { status: 400 });
  }

  const client = new Anthropic({ apiKey });

  try {
    let loopMessages = messages;
    let finalText = "";

    for (let i = 0; i < MAX_TOOL_ITERATIONS; i++) {
      const response = await client.messages.create({
        model: MODEL,
        max_tokens: 2048,
        system: SYSTEM_PROMPT,
        thinking: { type: "adaptive" },
        tools: CHAT_TOOLS,
        messages: loopMessages,
      });

      const textBlocks = response.content.filter(
        (b): b is Anthropic.TextBlock => b.type === "text"
      );
      finalText = textBlocks.map((b) => b.text).join("\n").trim();

      const toolUses = response.content.filter(
        (b): b is Anthropic.ToolUseBlock => b.type === "tool_use"
      );

      if (response.stop_reason !== "tool_use" || toolUses.length === 0) {
        break;
      }

      loopMessages = [...loopMessages, { role: "assistant", content: response.content }];

      const toolResults: Anthropic.ToolResultBlockParam[] = await Promise.all(
        toolUses.map(async (tu) => ({
          type: "tool_result" as const,
          tool_use_id: tu.id,
          content: await runChatTool(tu.name, tu.input as Record<string, unknown>),
        }))
      );

      loopMessages = [...loopMessages, { role: "user", content: toolResults }];
    }

    return NextResponse.json({
      reply: finalText || "Sorry, I couldn't put together an answer for that — try rephrasing, or use Live Chat to reach a person.",
    });
  } catch (err) {
    console.error("chat api error:", err);
    return NextResponse.json(
      { error: "Something went wrong reaching the assistant. Please try Live Chat instead." },
      { status: 502 }
    );
  }
}
