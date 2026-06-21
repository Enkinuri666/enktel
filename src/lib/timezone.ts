export interface TimezoneOption {
  value: string;
  label: string;
}

// A short, practical list covering the regions Enktel's audience actually
// lives in, rather than the full IANA database.
export const TIMEZONE_OPTIONS: TimezoneOption[] = [
  { value: "Europe/Zagreb", label: "Zagreb / Sarajevo / Belgrade (CET)" },
  { value: "Europe/London", label: "London (GMT/BST)" },
  { value: "Europe/Berlin", label: "Berlin / Paris / Rome (CET)" },
  { value: "America/New_York", label: "New York (ET)" },
  { value: "America/Los_Angeles", label: "Los Angeles (PT)" },
  { value: "Australia/Sydney", label: "Sydney (AET)" },
  { value: "UTC", label: "UTC" },
];

export const DEFAULT_TIMEZONE = "Europe/Zagreb";

// Falls back to the default if the browser's detected zone isn't one we
// offer in the picker, so the dropdown never shows a value with no label.
export function detectBrowserTimezone(): string {
  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return TIMEZONE_OPTIONS.some((o) => o.value === tz) ? tz : DEFAULT_TIMEZONE;
  } catch {
    return DEFAULT_TIMEZONE;
  }
}

export function formatTimeInZone(iso: string, timeZone: string): string {
  return new Date(iso).toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone,
  });
}
