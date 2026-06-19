import { EPGProgram } from "@/types";
import { generateLiveSchedule } from "./mock-data";

export function fetchEPGData(): EPGProgram[] {
  return generateLiveSchedule(new Date());
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
