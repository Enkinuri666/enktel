import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import type { Reply } from './xtream';

/**
 * The desktop end of "send this to my PC".
 *
 * The Android app runs a small server on the house network and shows a PIN;
 * these are the Tauri commands in `src-tauri/src/link.rs` that talk to it.
 * Kept dependency-free like `xtream.ts` — no React in here — so the page can
 * stay a view over it.
 *
 * The pairing token is deliberately *not* persisted. It dies with the phone's
 * share, which the viewer stops when they walk away; keeping it on disk would
 * leave a credential for a server that no longer exists, and the price of
 * re-pairing is typing six digits.
 */

export type Device = { name: string; ip: string; port: number };
export type Paired = { token: string; device: string; app: string };
export type RemoteFile = { token: string; name: string; size: number };

export type Job = {
  id: string;
  title: string;
  subtitle: string;
  /** QUEUED | RUNNING | PAUSED | DONE | FAILED */
  status: string;
  progressPct: number;
  sizeBytes: number;
  downloadedBytes: number;
  speedBps: number;
  error: string;
};

export type JobAction = 'pause' | 'resume' | 'retry' | 'cancel';

export type SaveProgress = {
  token: string;
  name: string;
  received: number;
  total: number;
  done: boolean;
  error: string | null;
};

/** Unwrap a `Reply<T>`, turning a refusal into a thrown Error. */
function unwrap<T>(reply: Reply<T>): T {
  if (!reply.ok) throw new Error(reply.error);
  return reply.data;
}

export async function discover(timeoutMs = 1500): Promise<Device[]> {
  return unwrap(await invoke<Reply<Device[]>>('link_discover', { timeoutMs }));
}

export function baseUrl(d: Device): string {
  return `http://${d.ip}:${d.port}`;
}

export async function pair(base: string, pin: string): Promise<Paired> {
  return unwrap(await invoke<Reply<Paired>>('link_pair', { base, pin }));
}

export async function files(base: string, token: string): Promise<RemoteFile[]> {
  return unwrap(await invoke<Reply<RemoteFile[]>>('link_files', { base, token }));
}

export async function downloads(base: string, token: string): Promise<Job[]> {
  return unwrap(await invoke<Reply<Job[]>>('link_downloads', { base, token }));
}

/** Returns false when the download had already gone from the phone's queue. */
export async function act(
  base: string,
  token: string,
  id: string,
  action: JobAction,
): Promise<boolean> {
  return unwrap(await invoke<Reply<boolean>>('link_act', { base, token, id, action }));
}

export async function defaultDir(): Promise<string> {
  return unwrap(await invoke<Reply<string>>('link_default_dir'));
}

/** Resolves with the full path the file was written to. */
export async function save(
  base: string,
  token: string,
  fileToken: string,
  name: string,
  dir: string,
): Promise<string> {
  return unwrap(
    await invoke<Reply<string>>('link_save', { base, token, fileToken, name, dir }),
  );
}

/** Subscribe to save progress. Returns the unlisten function. */
export function onProgress(fn: (p: SaveProgress) => void): Promise<() => void> {
  return listen<SaveProgress>('link://progress', (e) => fn(e.payload));
}

// ---- formatting -----------------------------------------------------------

export function humanBytes(n: number): string {
  if (!Number.isFinite(n) || n <= 0) return '—';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let v = n;
  let u = 0;
  while (v >= 1024 && u < units.length - 1) {
    v /= 1024;
    u += 1;
  }
  return u === 0 ? `${n} B` : `${v.toFixed(1)} ${units[u]}`;
}

export function humanRate(bps: number): string {
  return bps > 0 ? `${humanBytes(bps)}/s` : '';
}

/**
 * Time left, or an empty string when it cannot honestly be said.
 *
 * A stalled transfer has no answer, and inventing one — "12 days" from a rate
 * that just dipped to a trickle — is worse than showing nothing.
 */
export function eta(received: number, total: number, bps: number): string {
  if (bps <= 0 || total <= 0 || received >= total) return '';
  const secs = Math.round((total - received) / bps);
  if (secs > 60 * 60 * 24) return '';
  if (secs < 60) return `${secs}s left`;
  const mins = Math.round(secs / 60);
  if (mins < 60) return `${mins} min left`;
  const hours = Math.floor(mins / 60);
  return `${hours}h ${mins % 60}m left`;
}
