import { EPGProgram } from "@/types";
import { getRealEpgPrograms } from "./epgSource";

// Returns only real, sourced programme data (see src/lib/epgSource.ts). No
// channel is shown with invented schedule data - if a real source is
// unreachable, that channel simply has no programmes until it recovers.
export async function fetchEPGData(): Promise<EPGProgram[]> {
  let real: Record<string, EPGProgram[]> = {};
  try {
    real = await getRealEpgPrograms();
  } catch {
    real = {};
  }

  const merged: EPGProgram[] = [];
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
