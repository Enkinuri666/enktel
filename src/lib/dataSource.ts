// Shared "try the real source, fall back to mock data" pattern used by every
// API route that mixes a live external API with bundled mock data, so the
// fallback/error-handling logic and the `source` field reported to the
// client stay consistent across routes.
export async function withFallback<T>(
  attempt: () => Promise<T>,
  fallback: () => T,
  options: { isEmpty?: (data: T) => boolean; sourceName: string } = { sourceName: "live" }
): Promise<{ data: T; source: string }> {
  try {
    const data = await attempt();
    if (options.isEmpty?.(data)) return { data: fallback(), source: "mock-fallback" };
    return { data, source: options.sourceName };
  } catch {
    return { data: fallback(), source: "mock-fallback" };
  }
}
