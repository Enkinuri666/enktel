import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type XtreamProfile = { kind: 'xtream'; name: string; server: string; username: string; password: string };
export type M3UProfile = { kind: 'm3u'; name: string; m3uUrl: string; epgUrl?: string };
export type Profile = XtreamProfile | M3UProfile;

type SettingsState = {
  profile: Profile | null;
  theme: 'enktel_blue' | 'crimson' | 'emerald' | 'amber' | 'monochrome' | 'midnight' | 'high_contrast';
  wakeWordEnabled: boolean;
  // v1.26.0 port from android — Discord Watch Party + Streaming Companion Mode.
  // Companion Mode locks hls.js to its top level and bumps the segment
  // buffer so Discord screen-share viewers don't see quality flapping
  // during our screen-share sessions. Webhook URL + voice channel drive
  // the one-tap "Share to <channel>" button on the players.
  discordWebhook: string;
  discordVoiceChannel: string;
  companionMode: boolean;
  setProfile: (p: Profile | null) => void;
  setTheme: (t: SettingsState['theme']) => void;
  setWakeWordEnabled: (v: boolean) => void;
  setDiscordWebhook: (v: string) => void;
  setDiscordVoiceChannel: (v: string) => void;
  setCompanionMode: (v: boolean) => void;
};

export const useSettings = create<SettingsState>()(
  persist(
    (set) => ({
      profile: null,
      theme: 'enktel_blue',
      wakeWordEnabled: false,
      discordWebhook: '',
      discordVoiceChannel: "Richard's Hangout",
      companionMode: false,
      setProfile: (p) => set({ profile: p }),
      setTheme: (t) => set({ theme: t }),
      setWakeWordEnabled: (v) => set({ wakeWordEnabled: v }),
      setDiscordWebhook: (v) => set({ discordWebhook: v.trim() }),
      setDiscordVoiceChannel: (v) => set({ discordVoiceChannel: v.trim() || "Richard's Hangout" }),
      setCompanionMode: (v) => set({ companionMode: v }),
    }),
    { name: 'enktel-settings' },
  ),
);
