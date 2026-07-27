import { invoke } from '@tauri-apps/api/core';

/**
 * Thin TypeScript wrappers around the Tauri commands defined in
 * `src-tauri/src/main.rs`. Every call returns a `Reply<T>` — inspect `.ok`
 * before touching `.data` or `.error`, matching the Rust side.
 *
 * Kept intentionally dependency-free (no React Query in here) so it can be
 * used from stores, workers, or one-off scripts without pulling the
 * component tree along.
 */

// ---- Types (mirror the Rust structs) --------------------------------------

export type Reply<T> = { ok: true; data: T; error?: undefined } | { ok: false; data?: undefined; error: string };

export type ServerInfo = {
  url: string;
  port: number;
  timezone: string | null;
  time_now: string | null;
  server_protocol: string | null;
};

export type Category = { id: string; name: string };

export type Channel = {
  stream_id: number;
  name: string;
  num: number;
  logo: string;
  category_id: string;
  epg_channel_id: string;
  tv_archive: boolean;
};

export type Movie = {
  stream_id: number;
  name: string;
  poster: string;
  category_id: string;
  rating: number;
  container_extension: string;
  added: number;
  year: number | null;
  tmdb_id: number | null;
};

export type Series = {
  series_id: number;
  name: string;
  cover: string;
  category_id: string;
  rating: number;
  plot: string;
  year: number | null;
  tmdb_id: number | null;
};

// ---- Credentials envelope -------------------------------------------------

export type XtreamCreds = { server: string; username: string; password: string };

// ---- Command wrappers -----------------------------------------------------

async function reply<T>(cmd: string, args: Record<string, unknown>): Promise<Reply<T>> {
  // Tauri's invoke returns whatever the Rust command returned. Because our
  // Reply<T> struct is `#[derive(Serialize)]` it comes back as-is.
  return await invoke<Reply<T>>(cmd, args);
}

export async function xtreamLogin(c: XtreamCreds): Promise<Reply<ServerInfo>> {
  return reply<ServerInfo>('xtream_login', c);
}

export async function xtreamCategories(c: XtreamCreds, kind: 'live' | 'vod' | 'series'): Promise<Reply<Category[]>> {
  return reply<Category[]>('xtream_categories', { ...c, kind });
}

export async function xtreamLive(c: XtreamCreds): Promise<Reply<Channel[]>> {
  return reply<Channel[]>('xtream_live', c);
}

export async function xtreamMovies(c: XtreamCreds): Promise<Reply<Movie[]>> {
  return reply<Movie[]>('xtream_movies', c);
}

export async function xtreamSeries(c: XtreamCreds): Promise<Reply<Series[]>> {
  return reply<Series[]>('xtream_series', c);
}

export async function xtreamLiveUrl(c: XtreamCreds, streamId: number, preferHls = true): Promise<string[]> {
  return invoke<string[]>('xtream_live_url', { ...c, streamId, preferHls });
}

export async function xtreamMovieUrl(c: XtreamCreds, streamId: number, containerExtension = 'mp4'): Promise<string[]> {
  return invoke<string[]>('xtream_movie_url', { ...c, streamId, containerExtension });
}

export async function xtreamEpisodeUrl(c: XtreamCreds, episodeId: number, containerExtension = 'mp4'): Promise<string[]> {
  return invoke<string[]>('xtream_episode_url', { ...c, episodeId, containerExtension });
}

/** Helper: unwrap a Reply<T>; throw on error. Useful with React Query which
 *  wants a rejected promise on failure so its retry/backoff kicks in. */
export function unwrap<T>(r: Reply<T>): T {
  if (r.ok) return r.data;
  throw new Error(r.error);
}
