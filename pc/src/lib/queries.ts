import { useQuery } from '@tanstack/react-query';
import { useSettings, type Profile, type XtreamProfile } from '@/stores/settings';
import {
  xtreamCategories, xtreamLive, xtreamMovies, xtreamSeries,
  xtreamLiveUrl, xtreamMovieUrl, xtreamEpisodeUrl, unwrap,
  type XtreamCreds,
} from './xtream';

/**
 * React Query hooks for the Xtream catalog. Every hook pulls the active
 * profile out of the Zustand settings store, so pages just call
 * `const { data } = useMovies()` and get typed content back.
 *
 * Cache staleness is intentionally long (1 hour) — an Xtream catalog rarely
 * changes minute-to-minute, and 5 s stale semantics would refetch on every
 * sidebar click.
 */

const STALE_MS = 60 * 60 * 1000; // 1 h

function credsFrom(p: Profile | null): XtreamCreds | null {
  if (!p || p.kind !== 'xtream') return null;
  const x = p as XtreamProfile;
  return { server: x.server, username: x.username, password: x.password };
}

export function useCategories(kind: 'live' | 'vod' | 'series') {
  const profile = useSettings((s) => s.profile);
  const creds = credsFrom(profile);
  return useQuery({
    queryKey: ['categories', kind, creds?.server, creds?.username],
    enabled: !!creds,
    staleTime: STALE_MS,
    queryFn: async () => unwrap(await xtreamCategories(creds!, kind)),
  });
}

export function useLive() {
  const profile = useSettings((s) => s.profile);
  const creds = credsFrom(profile);
  return useQuery({
    queryKey: ['live', creds?.server, creds?.username],
    enabled: !!creds,
    staleTime: STALE_MS,
    queryFn: async () => unwrap(await xtreamLive(creds!)),
  });
}

export function useMovies() {
  const profile = useSettings((s) => s.profile);
  const creds = credsFrom(profile);
  return useQuery({
    queryKey: ['movies', creds?.server, creds?.username],
    enabled: !!creds,
    staleTime: STALE_MS,
    queryFn: async () => unwrap(await xtreamMovies(creds!)),
  });
}

export function useSeriesList() {
  const profile = useSettings((s) => s.profile);
  const creds = credsFrom(profile);
  return useQuery({
    queryKey: ['series', creds?.server, creds?.username],
    enabled: !!creds,
    staleTime: STALE_MS,
    queryFn: async () => unwrap(await xtreamSeries(creds!)),
  });
}

// ---- One-shot URL builders (async, not cached — just convenience) --------

export async function resolveLiveUrls(streamId: number, preferHls = true): Promise<string[]> {
  const p = useSettings.getState().profile;
  const c = credsFrom(p);
  if (!c) return [];
  return xtreamLiveUrl(c, streamId, preferHls);
}

export async function resolveMovieUrls(streamId: number, containerExtension = 'mp4'): Promise<string[]> {
  const p = useSettings.getState().profile;
  const c = credsFrom(p);
  if (!c) return [];
  return xtreamMovieUrl(c, streamId, containerExtension);
}

export async function resolveEpisodeUrls(episodeId: number, containerExtension = 'mp4'): Promise<string[]> {
  const p = useSettings.getState().profile;
  const c = credsFrom(p);
  if (!c) return [];
  return xtreamEpisodeUrl(c, episodeId, containerExtension);
}
