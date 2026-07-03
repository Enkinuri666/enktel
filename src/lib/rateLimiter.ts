// Enforces a minimum spacing between calls to a rate-limited upstream API —
// the shared OpenRouter key backing the chat assistant allows unlimited
// requests but only one every 20 seconds, and that limit is global across
// every visitor using the widget, not per-browser. Callers are queued and
// delayed rather than rejected, so concurrent conversations still complete,
// just spaced out.
//
// This is in-memory and process-scoped: correct as long as requests are
// served by a single warm instance, which holds for this app's traffic
// level. If the assistant needs to scale across multiple concurrent
// serverless instances, replace the module-level state below with a shared
// store (e.g. Vercel KV / Upstash) keyed the same way.
const MIN_INTERVAL_MS = 20_000;

let queueTail: Promise<void> = Promise.resolve();
let lastCallAt = 0;

export function throttle<T>(fn: () => Promise<T>): Promise<T> {
  const run = queueTail.then(async () => {
    const wait = Math.max(0, lastCallAt + MIN_INTERVAL_MS - Date.now());
    if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
    lastCallAt = Date.now();
    return fn();
  });
  // Advance the queue regardless of outcome so one failed call doesn't wedge
  // every request behind it.
  queueTail = run.then(
    () => undefined,
    () => undefined
  );
  return run;
}
