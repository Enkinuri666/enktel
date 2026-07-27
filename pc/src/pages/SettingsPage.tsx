import { useSettings } from '@/stores/settings';
import { Check, LogOut, Palette, User } from 'lucide-react';

const THEMES: { id: 'enktel_blue' | 'crimson' | 'emerald' | 'amber' | 'monochrome' | 'midnight' | 'high_contrast'; label: string; swatch: [string, string] }[] = [
  { id: 'enktel_blue', label: 'EnkTel Blue', swatch: ['#3B9DFF', '#8B5CF6'] },
  { id: 'crimson', label: 'Crimson Wolf', swatch: ['#FF4D4F', '#FF8A65'] },
  { id: 'emerald', label: 'Emerald', swatch: ['#10B981', '#34D399'] },
  { id: 'amber', label: 'Amber', swatch: ['#F59E0B', '#FCD34D'] },
  { id: 'midnight', label: 'Midnight Purple', swatch: ['#A78BFA', '#EC4899'] },
  { id: 'monochrome', label: 'Monochrome', swatch: ['#EAEAEA', '#858585'] },
  { id: 'high_contrast', label: 'High Contrast', swatch: ['#FFEB3B', '#00E5FF'] },
];

/**
 * Settings home: active profile, theme picker, voice toggle, log-out.
 * Playback / shortcut sub-panels will live under nested routes
 * (`/settings/playback`, `/settings/shortcuts`) as they land; the shell
 * here already sits under a `settings/*` route in App.tsx so those slot
 * in without needing a router edit.
 */
export default function SettingsPage() {
  const profile = useSettings((s) => s.profile);
  const theme = useSettings((s) => s.theme);
  const wakeWordEnabled = useSettings((s) => s.wakeWordEnabled);
  const setTheme = useSettings((s) => s.setTheme);
  const setWakeWord = useSettings((s) => s.setWakeWordEnabled);
  const setProfile = useSettings((s) => s.setProfile);

  return (
    <div className="p-10 max-w-4xl space-y-10">
      <h1 className="text-3xl font-black tracking-tight">Settings</h1>

      {/* Profile */}
      <section>
        <div className="flex items-center gap-2 mb-3">
          <User size={16} className="text-brand" />
          <h2 className="text-xs font-black tracking-widest text-textDim">PROFILE</h2>
        </div>
        {profile ? (
          <div className="glass rounded-xl p-5 flex items-start gap-4">
            <div className="flex-1">
              <div className="text-lg font-bold">{profile.name}</div>
              <div className="text-xs text-textDim mt-0.5 uppercase tracking-wider">
                {profile.kind === 'xtream' ? 'XTREAM CODES' : 'M3U PLAYLIST'}
              </div>
              <div className="text-xs text-textDim mt-2 font-mono break-all">
                {profile.kind === 'xtream' ? profile.server : profile.m3uUrl}
              </div>
              {profile.kind === 'xtream' && (
                <div className="text-xs text-textDim mt-1">
                  user: <span className="font-mono">{profile.username}</span>
                </div>
              )}
            </div>
            <button
              onClick={() => setProfile(null)}
              className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-semibold bg-live/15 hover:bg-live/25 text-live"
            >
              <LogOut size={14} />
              Sign out
            </button>
          </div>
        ) : (
          <div className="glass rounded-xl p-5 text-textDim text-sm">
            No profile yet — restart the app to open onboarding.
          </div>
        )}
      </section>

      {/* Theme */}
      <section>
        <div className="flex items-center gap-2 mb-3">
          <Palette size={16} className="text-brand" />
          <h2 className="text-xs font-black tracking-widest text-textDim">THEME</h2>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {THEMES.map((t) => {
            const active = theme === t.id;
            return (
              <button
                key={t.id}
                onClick={() => setTheme(t.id)}
                className={`text-left p-4 rounded-xl border transition ${
                  active
                    ? 'border-brand bg-brand/10'
                    : 'border-white/5 bg-surface/60 hover:border-white/20'
                }`}
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex gap-1">
                    <span
                      className="w-4 h-4 rounded-sm"
                      style={{ backgroundColor: t.swatch[0] }}
                    />
                    <span
                      className="w-4 h-4 rounded-sm"
                      style={{ backgroundColor: t.swatch[1] }}
                    />
                  </div>
                  {active && <Check size={16} className="text-brand" />}
                </div>
                <div className="text-sm font-semibold">{t.label}</div>
              </button>
            );
          })}
        </div>
      </section>

      {/* Voice */}
      <section>
        <div className="flex items-center gap-2 mb-3">
          <h2 className="text-xs font-black tracking-widest text-textDim">VOICE COMMANDS</h2>
        </div>
        <div className="glass rounded-xl p-5 flex items-center justify-between">
          <div>
            <div className="text-sm font-semibold">"Hey Enki" wake word</div>
            <div className="text-xs text-textDim mt-0.5">
              Listen for the wake word using the Web Speech API. Requires microphone permission.
            </div>
          </div>
          <button
            onClick={() => setWakeWord(!wakeWordEnabled)}
            className={`px-4 py-2 rounded-lg text-sm font-semibold ${
              wakeWordEnabled ? 'bg-brand text-black' : 'bg-white/10 hover:bg-white/15'
            }`}
          >
            {wakeWordEnabled ? 'On' : 'Off'}
          </button>
        </div>
      </section>

      {/* About */}
      <section>
        <h2 className="text-xs font-black tracking-widest text-textDim mb-3">ABOUT</h2>
        <div className="text-xs text-textDim space-y-1">
          <div>EnkTel IPTV for Windows · Tauri 2 + React 18</div>
          <div>Renderer: WebView2 (Chromium)</div>
          <div>Streaming: hls.js · shaka-player · native &lt;video&gt;</div>
        </div>
      </section>
    </div>
  );
}
