import { NextResponse } from "next/server";
import OpenAI from "openai";
import { CHAT_TOOLS, runChatTool } from "@/lib/chatTools";
import { throttle } from "@/lib/rateLimiter";

export const dynamic = "force-dynamic";
// The shared OpenRouter key is capped at one request every 20 seconds, and a
// single turn can make several requests (one per tool-call round). Give the
// function enough headroom to sit through that spacing instead of timing out
// mid-conversation. Raise this further (subject to your Vercel plan's limit)
// if MAX_TOOL_ITERATIONS grows.
export const maxDuration = 90;

const SYSTEM_PROMPT = `You are the Enktel Assistant, a friendly and concise AI chat helper embedded on enktel.tv, a premium IPTV & streaming subscription service (live TV, sports, movies, and VOD).

You help with:
- Customer service: plans, pricing, billing policy, refunds, device limits
- Technical support: setup guides per device, buffering/playlist troubleshooting
- Entertainment & the live TV guide: what's on now/next on a channel, upcoming sports fixtures, browsing channels by category

Ground any factual answer — pricing, channel availability, setup steps, policy details, what's on now — by calling the relevant tool rather than guessing. If a tool returns no match, say so plainly instead of inventing details.

You cannot access a specific customer's account, process payments, issue refunds, or reset credentials. For anything account-specific, tell the person to use Live Chat, WhatsApp, or the Contact page — all reachable from the same chat button used to open you. Never claim to have taken an account action on the customer's behalf.

Keep replies short and conversational (2-4 sentences in most cases) — this is a live chat widget, not an email. Plain text only, no markdown headers or tables.`;

// OpenRouter is OpenAI-API-compatible — same SDK, same request/response
// shape, just a different base URL, key, and a provider-prefixed model slug.
const OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";
// Must stay one of the free models allowlisted by the account's workspace
// guardrail (openrouter.ai/settings/privacy) — gpt-5.5 isn't served by any
// provider that satisfies that policy, which surfaced as a 404 "no endpoints
// available" error. gpt-oss-20b supports the same tool-calling flow used
// here and was already verified end-to-end against this exact route.
const MODEL = "openai/gpt-oss-20b:free";
// Kept low on purpose: each iteration costs at least one 20-second rate-limit
// wait (see src/lib/rateLimiter.ts), so this bounds a single reply to roughly
// one tool-call round plus a final answer before the 20s spacing dominates.
const MAX_TOOL_ITERATIONS = 3;
const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || "https://enktel.tv";

interface ChatMessageInput {
  role: "user" | "assistant";
  content: string;
}

export async function POST(request: Request) {
  const apiKey = process.env.OPENROUTER_API_KEY;
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

  const history = (incoming as ChatMessageInput[])
    .slice(-20)
    .filter((m) => (m?.role === "user" || m?.role === "assistant") && typeof m?.content === "string");

  if (history.length === 0) {
    return NextResponse.json({ error: "Missing messages" }, { status: 400 });
  }

  const client = new OpenAI({
    apiKey,
    baseURL: OPENROUTER_BASE_URL,
    defaultHeaders: {
      "HTTP-Referer": SITE_URL,
      "X-Title": "Enktel Assistant",
    },
  });

  let messages: OpenAI.Chat.Completions.ChatCompletionMessageParam[] = [
    { role: "system", content: SYSTEM_PROMPT },
    ...history.map((m) => ({ role: m.role, content: m.content } as OpenAI.Chat.Completions.ChatCompletionMessageParam)),
  ];

  try {
    let finalText = "";

    for (let i = 0; i < MAX_TOOL_ITERATIONS; i++) {
      const response = await throttle(() =>
        client.chat.completions.create({
          model: MODEL,
          max_tokens: 2048,
          tools: CHAT_TOOLS,
          messages,
        })
      );

      const message = response.choices[0]?.message;
      finalText = message?.content?.trim() || "";

      const toolCalls = message?.tool_calls?.filter((tc) => tc.type === "function") ?? [];

      if (toolCalls.length === 0) {
        break;
      }

      messages = [...messages, message as OpenAI.Chat.Completions.ChatCompletionMessageParam];

      const toolResults: OpenAI.Chat.Completions.ChatCompletionMessageParam[] = await Promise.all(
        toolCalls.map(async (tc) => {
          let args: Record<string, unknown> = {};
          try {
            args = JSON.parse(tc.function.arguments || "{}");
          } catch {
            args = {};
          }
          const result = await runChatTool(tc.function.name, args);
          return { role: "tool", tool_call_id: tc.id, content: result };
        })
      );

      messages = [...messages, ...toolResults];
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
