import { EPGProgram } from "@/types";
import { generateLiveSchedule } from "./mock-data";
import { getRealEpgPrograms } from "./epgSource";

// Merges real, live-fetched programme data (where a free EPG source covers
// the channel - see src/lib/epgSource.ts) with the simulated wall-clock
// schedule for every other channel, so every channel always has full
// "what's on" coverage even if the real source is partially or fully
// unreachable.
export async function fetchEPGData(): Promise<EPGProgram[]> {
  const simulated = generateLiveSchedule(new Date());

  let real: Record<string, EPGProgram[]> = {};
  try {
    real = await getRealEpgPrograms();
  } catch {
    real = {};
  }

  const realChannelIds = new Set(Object.keys(real));
  const merged = simulated.filter((p) => !realChannelIds.has(p.channelId));
  for (const programs of Object.values(real)) merged.push(...programs);
  return merged;
}

export function getProgramsForChannel(
  programs: EPGProgram[],
  channelId: string,
  startTime: Date,
  endTime: Date
): EPGProgram[] {
  return programs.filter((p) => {
    if (p.channelId !== channelId) return false;
    const pStart = new Date(p.startTime);
    const pEnd = new Date(p.endTime);
    return pStart < endTime && pEnd > startTime;
  });
}
