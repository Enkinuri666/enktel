const SPORTSDB_BASE = "https://www.thesportsdb.com/api/v1/json/123";
const WORLD_CUP_LEAGUE_ID = "4429";

export interface WorldCupMatch {
  id: string;
  homeTeam: string;
  awayTeam: string;
  homeScore: number | null;
  awayScore: number | null;
  startTime: string;
  status: "upcoming" | "live" | "finished";
  venue: string | null;
  city: string | null;
  country: string | null;
  group: string | null;
  round: string | null;
  homeTeamBadge: string | null;
  awayTeamBadge: string | null;
}

interface SportsDbEvent {
  idEvent: string;
  strHomeTeam: string;
  strAwayTeam: string;
  intHomeScore: string | null;
  intAwayScore: string | null;
  dateEvent: string;
  strTime: string;
  strTimestamp?: string;
  strVenue?: string | null;
  strCity?: string | null;
  strCountry?: string | null;
  strGroup?: string | null;
  intRound?: string | null;
  strHomeTeamBadge?: string | null;
  strAwayTeamBadge?: string | null;
}

// A match is realistically over within ~2.5 hours of kickoff (90 mins +
// extra time + stoppages); past that we trust TheSportsDB's final score
// over our own "is it still live" guess.
const MATCH_DURATION_MS = 2.5 * 60 * 60 * 1000;

function toMatch(event: SportsDbEvent): WorldCupMatch {
  const startTime = event.strTimestamp
    ? `${event.strTimestamp}Z`
    : new Date(`${event.dateEvent}T${event.strTime || "00:00:00"}Z`).toISOString();

  const homeScore = event.intHomeScore !== null && event.intHomeScore !== undefined ? Number(event.intHomeScore) : null;
  const awayScore = event.intAwayScore !== null && event.intAwayScore !== undefined ? Number(event.intAwayScore) : null;

  const elapsed = Date.now() - new Date(startTime).getTime();
  let status: WorldCupMatch["status"];
  if (homeScore !== null && awayScore !== null && elapsed > 0) {
    status = elapsed > MATCH_DURATION_MS ? "finished" : "live";
  } else {
    status = elapsed >= 0 ? "live" : "upcoming";
  }

  return {
    id: `wc-${event.idEvent}`,
    homeTeam: event.strHomeTeam,
    awayTeam: event.strAwayTeam,
    homeScore,
    awayScore,
    startTime,
    status,
    venue: event.strVenue || null,
    city: event.strCity || null,
    country: event.strCountry || null,
    group: event.strGroup || null,
    round: event.intRound || null,
    homeTeamBadge: event.strHomeTeamBadge || null,
    awayTeamBadge: event.strAwayTeamBadge || null,
  };
}

async function fetchEvents(endpoint: string): Promise<SportsDbEvent[]> {
  const res = await fetch(`${SPORTSDB_BASE}/${endpoint}?id=${WORLD_CUP_LEAGUE_ID}`, {
    next: { revalidate: 300 },
    signal: AbortSignal.timeout(5000),
  });
  if (!res.ok) return [];
  const data = await res.json();
  return Array.isArray(data?.events) ? data.events : [];
}

// Combines TheSportsDB's "next" and "past" fixtures for the World Cup
// league into a single, deduplicated, time-sorted list of real matches —
// finished ones carry their final score, upcoming ones don't yet.
export async function getRealWorldCupMatches(): Promise<WorldCupMatch[]> {
  const [next, past] = await Promise.all([
    fetchEvents("eventsnextleague.php"),
    fetchEvents("eventspastleague.php"),
  ]);

  const byId = new Map<string, SportsDbEvent>();
  for (const e of [...past, ...next]) byId.set(e.idEvent, e);

  return Array.from(byId.values())
    .map(toMatch)
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime());
}
