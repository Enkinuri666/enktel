"use client";
import { useEffect, useState } from "react";

interface TimeSlotsProps {
  startTime: Date;
  endTime: Date;
  pixelsPerMinute: number;
  timeZone: string;
}

export default function TimeSlots({ startTime, endTime, pixelsPerMinute, timeZone }: TimeSlotsProps) {
  const [nowMs, setNowMs] = useState<number | null>(null);

  useEffect(() => {
    function calc() {
      setNowMs(Date.now());
    }
    calc();
    const id = setInterval(calc, 60000);
    return () => clearInterval(id);
  }, []);

  const totalMinutes = (endTime.getTime() - startTime.getTime()) / 60000;
  const slots: { label: string; minuteOffset: number }[] = [];
  for (let m = 0; m <= totalMinutes; m += 30) {
    const label = new Date(startTime.getTime() + m * 60000).toLocaleTimeString("en-GB", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
      timeZone,
    });
    slots.push({ label, minuteOffset: m });
  }

  const totalWidth = totalMinutes * pixelsPerMinute;
  const currentMinutes = nowMs !== null ? (nowMs - startTime.getTime()) / 60000 : null;

  return (
    <div className="relative h-8 shrink-0" style={{ width: `${totalWidth}px` }}>
      {slots.map((slot) => (
        <div
          key={slot.minuteOffset}
          className="absolute top-0 h-full flex items-center"
          style={{ left: `${slot.minuteOffset * pixelsPerMinute}px` }}
        >
          <span className="text-brand-muted text-xs pl-1 font-medium">{slot.label}</span>
          <div className="absolute bottom-0 left-0 w-px h-2 bg-brand-border" />
        </div>
      ))}
      {/* Current time indicator */}
      {currentMinutes !== null && currentMinutes >= 0 && currentMinutes <= totalMinutes && (
        <div
          className="absolute top-0 h-full w-0.5 bg-brand-accent z-10"
          style={{ left: `${currentMinutes * pixelsPerMinute}px` }}
        >
          <div className="w-2 h-2 bg-brand-accent rounded-full -translate-x-0.5" />
        </div>
      )}
    </div>
  );
}
