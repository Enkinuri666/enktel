import { UpcomingEvent } from "@/types";

// TheSportsDB's shared free-tier test key. Good enough for the "next event"
// lookups we do here (one cheap request per league, cached for a while).
const SPORTSDB_BASE = "https://www.thesportsdb.com/api/v1/json/123";

interface LeagueConfig {
  id: string;
  sport: string;
  emoji: string;
  channel: string;
  isPPV: boolean;
  /** Friendlier competition name than TheSportsDB's strLeague, where useful. */
  competition?: string;
}

// One representative league per sport we want to surface, mapped to an
// Enktel channel that actually exists in src/lib/channels.ts so the
// "find it on" claim is consistent with the rest of the site.
const LEAGUES: LeagueConfig[] = [
  { id: "4429", sport: "Football", emoji: "🏆", channel: "Sky Sports Main Event", isPPV: false, competition: "FIFA World Cup" },
  { id: "4629", sport: "Football", emoji: "⚽", channel: "Arena Sport 1", isPPV: false, competition: "HNL" },
  { id: "4328", sport: "Football", emoji: "🏆", channel: "Sky Sports Football", isPPV: false, competition: "Premier League" },
  { id: "4480", sport: "Football", emoji: "🏆", channel: "BT Sport 1", isPPV: false, competition: "Champions League" },
  { id: "4335", sport: "Football", emoji: "⚽", channel: "Eurosport 1", isPPV: false, competition: "La Liga" },
  { id: "4331", sport: "Football", emoji: "⚽", channel: "BT Sport 2", isPPV: false, competition: "Bundesliga" },
  { id: "4332", sport: "Football", emoji: "⚽", channel: "Eurosport 1", isPPV: false, competition: "Serie A" },
  { id: "4370", sport: "Motorsport", emoji: "🏎️", channel: "Sky Sports Main Event", isPPV: false, competition: "Formula 1" },
  { id: "4443", sport: "Combat Sports", emoji: "🥊", channel: "ESPN", isPPV: true, competition: "UFC" },
  { id: "4387", sport: "Basketball", emoji: "🏀", channel: "ESPN", isPPV: false, competition: "NBA" },
];

interface SportsDbEvent {
  idEvent: string;
  strEvent: string;
  strLeague: string;
  dateEvent: string;
  strTime: string;
  strTimestamp?: string;
}

async function fetchNextEvent(league: LeagueConfig): Promise<UpcomingEvent | null> {
  try {
    const res = await fetch(`${SPORTSDB_BASE}/eventsnextleague.php?id=${league.id}`, {
      next: { revalidate: 1800 },
      signal: AbortSignal.timeout(5000),
    });
    if (!res.ok) return null;
    const data = await res.json();
    const event: SportsDbEvent | undefined = data?.events?.[0];
    if (!event) return null;

    const startTime = event.strTimestamp
      ? `${event.strTimestamp}Z`
      : new Date(`${event.dateEvent}T${event.strTime || "00:00:00"}Z`).toISOString();

    return {
      id: `sportsdb-${event.idEvent}`,
      title: event.strEvent,
      competition: league.competition || event.strLeague,
      sport: league.sport,
      emoji: league.emoji,
      channel: league.channel,
      startTime,
      isPPV: league.isPPV,
      isLive: new Date(startTime).getTime() <= Date.now(),
    };
  } catch {
    return null;
  }
}

// Real upcoming fixtures pulled from TheSportsDB's free API, one per
// tracked league/competition. Returns whatever resolved successfully -
// callers should fall back to mock data if this comes back empty.
export async function getRealUpcomingEvents(): Promise<UpcomingEvent[]> {
  const results = await Promise.all(LEAGUES.map(fetchNextEvent));
  return results
    .filter((e): e is UpcomingEvent => e !== null)
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime());
}
