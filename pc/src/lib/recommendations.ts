// v1.26.0 port from android RecommendationsRepository.homeRails() —
// aggregated home-page rail computation with cross-rail dedup so a title
// that overlaps five moods doesn't appear in five rails at once.

import type { Movie } from './xtream';

export type HomeRails = {
  latestReleases: Movie[];
  comingSoon: Movie[];
  topPicks: Movie[];
  trending: Movie[];
  newThisWeek: Movie[];
  moodFastPaced: Movie[];
  moodGritty: Movie[];
  moodMindBending: Movie[];
  moodLateNight: Movie[];
  moodFeelGood: Movie[];
};

const MOOD_KEYWORDS = {
  fastPaced: ['action', 'adventure', 'thriller', 'war', 'crime'],
  gritty: ['crime', 'thriller', 'noir', 'mystery', 'drama'],
  mindBending: ['sci-fi', 'science', 'mystery', 'thriller', 'fantasy'],
  lateNight: ['comedy', 'sitcom', 'family', 'romance', 'animation'],
  feelGood: ['animation', 'family', 'romance', 'music', 'biography'],
};

function hasKeyword(m: Movie, needles: string[]): boolean {
  // Xtream's `Movie` doesn't expose a genre field directly — we approximate
  // by matching the title itself, which is often "The Bourne Identity [Action, Thriller]"
  // etc. in Xtream panels. When TMDB enrichment lands we'll swap this for
  // the genre field the enrichment worker populates.
  const hay = m.name.toLowerCase();
  return needles.some((k) => hay.includes(k));
}

export function computeHomeRails(allMovies: Movie[], nowSec: number): HomeRails {
  const withPoster = allMovies.filter((m) => m.poster && m.poster.length > 0);
  const week = 14 * 24 * 3600;
  const currentYear = new Date(nowSec * 1000).getFullYear();

  // Latest Releases: newest by `added`, then by year. Always picks first so
  // it never gets crowded out by mood rails.
  const latest = [...withPoster]
    .sort((a, b) => {
      if (b.added !== a.added) return b.added - a.added;
      return (b.year ?? 0) - (a.year ?? 0);
    })
    .slice(0, 24);

  const used = new Set<string>(latest.map((m) => `${m.stream_id}`));

  function pick(list: Movie[], limit: number): Movie[] {
    const out: Movie[] = [];
    for (const m of list) {
      const k = `${m.stream_id}`;
      if (used.has(k)) continue;
      out.push(m);
      used.add(k);
      if (out.length >= limit) break;
    }
    return out;
  }

  const comingSoon = pick(
    [...withPoster]
      .filter((m) => (m.year ?? 0) >= currentYear)
      .sort((a, b) => {
        if ((b.year ?? 0) !== (a.year ?? 0)) return (b.year ?? 0) - (a.year ?? 0);
        return b.added - a.added;
      }),
    20,
  );

  const trending = pick(
    [...withPoster]
      .filter((m) => m.rating >= 6.5)
      .sort((a, b) => {
        if (b.rating !== a.rating) return b.rating - a.rating;
        return (b.year ?? 0) - (a.year ?? 0);
      }),
    18,
  );

  const topPicks = pick(
    [...withPoster].filter((m) => m.rating >= 7.0).sort((a, b) => b.rating - a.rating),
    18,
  );

  const newThis = pick(
    [...withPoster].filter((m) => m.added > nowSec - week).sort((a, b) => b.added - a.added),
    18,
  );

  function moodPool(keywords: string[], minRating: number): Movie[] {
    return [...withPoster]
      .filter((m) => m.rating >= minRating && hasKeyword(m, keywords))
      .sort((a, b) => b.rating - a.rating);
  }

  return {
    latestReleases: latest,
    comingSoon,
    topPicks,
    trending,
    newThisWeek: newThis,
    moodFastPaced: pick(moodPool(MOOD_KEYWORDS.fastPaced, 6.5), 14),
    moodGritty: pick(moodPool(MOOD_KEYWORDS.gritty, 6.8), 14),
    moodMindBending: pick(moodPool(MOOD_KEYWORDS.mindBending, 7.0), 14),
    moodLateNight: pick(moodPool(MOOD_KEYWORDS.lateNight, 5.5), 14),
    moodFeelGood: pick(moodPool(MOOD_KEYWORDS.feelGood, 6.0), 14),
  };
}
