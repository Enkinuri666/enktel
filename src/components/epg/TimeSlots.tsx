"use client";
import { useEffect, useState } from "react";

interface TimeSlotsProps {
  startHour: number;
  endHour: number;
  pixelsPerMinute: number;
}

export default function TimeSlots({ startHour, endHour, pixelsPerMinute }: TimeSlotsProps) {
  const [currentMinutes, setCurrentMinutes] = useState<number | null>(null);

  useEffect(() => {
    function calc() {
      const now = new Date();
      const mins = (now.getHours() - startHour) * 60 + now.getMinutes();
      setCurrentMinutes(mins);
    }
    calc();
    const id = setInterval(calc, 60000);
    return () => clearInterval(id);
  }, [startHour]);

  const slots: { label: string; minuteOffset: number }[] = [];
  for (let h = startHour; h <= endHour; h++) {
    slots.push({ label: `${String(h % 24).padStart(2, "0")}:00`, minuteOffset: (h - startHour) * 60 });
    if (h < endHour) {
      slots.push({ label: `${String(h % 24).padStart(2, "0")}:30`, minuteOffset: (h - startHour) * 60 + 30 });
    }
  }

  const totalWidth = (endHour - startHour) * 60 * pixelsPerMinute;

  return (
    <div className="relative h-8 shrink-0" style={{ width: `${totalWidth}px` }}>
      {slots.map((slot) => (
        <div
          key={slot.label}
          className="absolute top-0 h-full flex items-center"
          style={{ left: `${slot.minuteOffset * pixelsPerMinute}px` }}
        >
          <span className="text-brand-muted text-xs pl-1 font-medium">{slot.label}</span>
          <div className="absolute bottom-0 left-0 w-px h-2 bg-brand-border" />
        </div>
      ))}
      {/* Current time indicator */}
      {currentMinutes !== null && currentMinutes >= 0 && currentMinutes <= (endHour - startHour) * 60 && (
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
