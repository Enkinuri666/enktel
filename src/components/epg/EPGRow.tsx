import { Channel, EPGProgram } from "@/types";
import EPGProgramCell from "./EPGProgram";
import ChannelLogo from "@/components/ui/ChannelLogo";

interface EPGRowProps {
  channel: Channel;
  programs: EPGProgram[];
  startTime: Date;
  endTime: Date;
  pixelsPerMinute: number;
  timeZone: string;
}

export default function EPGRow({ channel, programs, startTime, endTime, pixelsPerMinute, timeZone }: EPGRowProps) {
  const now = new Date();
  const totalMinutes = (endTime.getTime() - startTime.getTime()) / 60000;
  const totalWidth = totalMinutes * pixelsPerMinute;

  return (
    <div className="flex h-14 border-b border-brand-border/50">
      {/* Channel name - sticky */}
      <div className="w-40 shrink-0 sticky left-0 z-20 bg-brand-bg border-r border-brand-border flex items-center gap-2 px-3">
        <ChannelLogo name={channel.name} id={channel.id} size="sm" />
        <span className="text-white text-xs font-medium line-clamp-2 leading-tight">{channel.name}</span>
      </div>

      {/* Programs timeline */}
      <div
        className="relative flex items-center"
        style={{ width: `${totalWidth}px`, minWidth: `${totalWidth}px` }}
      >
        {programs.map((program) => {
          const pStart = new Date(program.startTime);
          const pEnd = new Date(program.endTime);
          const clampedStart = pStart < startTime ? startTime : pStart;
          const clampedEnd = pEnd > endTime ? endTime : pEnd;
          const offsetMinutes = (clampedStart.getTime() - startTime.getTime()) / 60000;
          const durationMinutes = (clampedEnd.getTime() - clampedStart.getTime()) / 60000;
          const widthPx = durationMinutes * pixelsPerMinute;
          const leftPx = offsetMinutes * pixelsPerMinute;
          const isCurrentlyAiring = pStart <= now && pEnd > now;

          return (
            <div
              key={program.id}
              className="absolute inset-y-1"
              style={{ left: `${leftPx}px`, width: `${widthPx}px` }}
            >
              <EPGProgramCell
                program={program}
                widthPx={widthPx}
                isCurrentlyAiring={isCurrentlyAiring}
                timeZone={timeZone}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
}
