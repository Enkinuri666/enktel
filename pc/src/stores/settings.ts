import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type XtreamProfile = { kind: 'xtream'; name: string; server: string; username: string; password: string };
export type M3UProfile = { kind: 'm3u'; name: string; m3uUrl: string; epgUrl?: string };
export type Profile = XtreamProfile | M3UProfile;

type SettingsState = {
  profile: Profile | null;
  theme: 'enktel_blue' | 'crimson' | 'emerald' | 'amber' | 'monochrome' | 'midnight' | 'high_contrast';
  wakeWordEnabled: boolean;
  setProfile: (p: Profile | null) => void;
  setTheme: (t: SettingsState['theme']) => void;
  setWakeWordEnabled: (v: boolean) => void;
};

export const useSettings = create<SettingsState>()(
  persist(
    (set) => ({
      profile: null,
      theme: 'enktel_blue',
      wakeWordEnabled: false,
      setProfile: (p) => set({ profile: p }),
      setTheme: (t) => set({ theme: t }),
      setWakeWordEnabled: (v) => set({ wakeWordEnabled: v }),
    }),
    { name: 'enktel-settings' },
  ),
);
