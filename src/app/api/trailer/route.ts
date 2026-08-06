import { NextResponse } from "next/server";

/**
 * Trailer lookup for the apps.
 *
 * The Android/TV app could already find trailers — but only if the user went
 * into Settings and pasted their own TMDB API key, which essentially nobody
 * does. Without one every trailer path returned null and no-opped silently, so
 * the feature read as "trailers don't work" rather than "trailers need setup".
 *
 * This endpoint moves the key server-side, where it already lives for the
 * marketing site's own TMDB usage. The app asks by TMDB id when the catalogue
 * carries one and by title otherwise, and gets back a YouTube video id.
 *
 * Answers are cached for a day: a film's trailer does not change, and TMDB's
 * free tier is rate-limited per key rather than per caller.
 */
export const revalidate = 86400;

const TMDB = "https://api.themoviedb.org/3";

type Video = {
  key?: string;
  site?: string;
  type?: string;
  name?: string;
  official?: boolean;
  size?: number;
};

async function tmdb(path: string, params: Record<string, string> = {}) {
  const key = process.env.TMDB_API_KEY;
  if (!key) return null;
  const qs = new URLSearchParams({ api_key: key, ...params });
  const res = await fetch(`${TMDB}${path}?${qs}`, {
    next: { revalidate },
    headers: { Accept: "application/json" },
  });
  if (!res.ok) return null;
  return res.json();
}

/**
 * Picks what a viewer would call *the* trailer.
 *
 * Order matters more than it looks: TMDB's video list for a popular film is
 * mostly clips, featurettes and behind-the-scenes reels, and taking the first
 * YouTube entry lands on a five-second logo sting about a third of the time.
 */
function pickTrailer(videos: Video[]): Video | null {
  const yt = videos.filter((v) => (v.site || "").toLowerCase() === "youtube" && v.key);
  if (yt.length === 0) return null;
  const byType = (t: string, officialOnly: boolean) =>
    yt.find(
      (v) =>
        (v.type || "").toLowerCase() === t && (!officialOnly || v.official === true)
    );
  return (
    byType("trailer", true) ||
    byType("trailer", false) ||
    byType("teaser", true) ||
    byType("teaser", false) ||
    yt[0]
  );
}

async function videosFor(kind: "movie" | "tv", id: number): Promise<Video[]> {
  // English first, then unfiltered — a lot of non-US catalogue titles only
  // carry a trailer in their original language, and returning nothing for
  // those is worse than returning a trailer the user cannot read the title of.
  const en = await tmdb(`/${kind}/${id}/videos`, { language: "en-US" });
  const enResults: Video[] = en?.results ?? [];
  if (pickTrailer(enResults)) return enResults;
  const any = await tmdb(`/${kind}/${id}/videos`);
  return any?.results ?? [];
}

async function searchId(kind: "movie" | "tv", title: string, year?: string) {
  const params: Record<string, string> = { query: title };
  if (year) params[kind === "movie" ? "year" : "first_air_date_year"] = year;
  const res = await tmdb(`/search/${kind}`, params);
  const first = res?.results?.[0];
  return typeof first?.id === "number" ? (first.id as number) : null;
}

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const kind = searchParams.get("type") === "tv" ? "tv" : "movie";
  const tmdbId = Number(searchParams.get("tmdb") || 0);
  const title = (searchParams.get("title") || "").trim();
  const year = (searchParams.get("year") || "").trim() || undefined;

  if (!process.env.TMDB_API_KEY) {
    // Say which side is unconfigured. A bare null here is indistinguishable
    // from "this film has no trailer", and the app would report the wrong one.
    return NextResponse.json(
      { key: null, reason: "trailer lookup is not configured on the server" },
      { status: 503 }
    );
  }
  if (!tmdbId && !title) {
    return NextResponse.json(
      { key: null, reason: "pass tmdb=<id> or title=<name>" },
      { status: 400 }
    );
  }

  try {
    const id = tmdbId > 0 ? tmdbId : await searchId(kind, title, year);
    if (!id) {
      return NextResponse.json({ key: null, reason: "no match on TMDB", tmdb: null });
    }
    const best = pickTrailer(await videosFor(kind, id));
    if (!best?.key) {
      return NextResponse.json({ key: null, reason: "no trailer on TMDB", tmdb: id });
    }
    return NextResponse.json({
      key: best.key,
      name: best.name ?? "",
      site: "youtube",
      type: (best.type ?? "").toLowerCase(),
      official: best.official === true,
      tmdb: id,
    });
  } catch {
    return NextResponse.json(
      { key: null, reason: "TMDB request failed" },
      { status: 502 }
    );
  }
}
