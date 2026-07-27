import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSettings, type Profile } from '@/stores/settings';
import { xtreamLogin } from '@/lib/xtream';

export default function OnboardingPage() {
  const nav = useNavigate();
  const setProfile = useSettings((s) => s.setProfile);
  const [mode, setMode] = useState<'xtream' | 'm3u'>('xtream');
  const [form, setForm] = useState({
    name: 'My playlist', server: '', username: '', password: '', m3uUrl: '', epgUrl: '',
  });
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit =
    !testing && ((mode === 'xtream' && form.server && form.username && form.password) ||
    (mode === 'm3u' && form.m3uUrl));

  const submit = async () => {
    setError(null);
    if (mode === 'xtream') {
      // Pre-flight the Xtream credentials — surfaces "wrong password" /
      // "server not reachable" up-front so the user isn't dropped into an
      // empty Home screen with no explanation.
      setTesting(true);
      const r = await xtreamLogin({
        server: form.server, username: form.username, password: form.password,
      });
      setTesting(false);
      if (!r.ok) {
        setError(r.error);
        return;
      }
    }
    const profile: Profile = mode === 'xtream'
      ? { kind: 'xtream', name: form.name, server: form.server, username: form.username, password: form.password }
      : { kind: 'm3u', name: form.name, m3uUrl: form.m3uUrl, epgUrl: form.epgUrl };
    setProfile(profile);
    nav('/');
  };

  return (
    <div className="min-h-full grid place-items-center p-10">
      <div className="glass-strong w-[520px] rounded-2xl p-8 shadow-glass">
        <div className="flex items-center gap-2 mb-1">
          <div className="h-2 w-2 rounded-full bg-live" />
          <span className="text-xs font-black tracking-widest text-textDim">ENKTEL IPTV</span>
        </div>
        <h1 className="text-2xl font-black mb-6">Connect your playlist</h1>

        <div className="mb-4 flex gap-2">
          <button className="chip" data-selected={mode === 'xtream'} onClick={() => setMode('xtream')}>Xtream Codes</button>
          <button className="chip" data-selected={mode === 'm3u'} onClick={() => setMode('m3u')}>M3U playlist</button>
        </div>

        <div className="space-y-3">
          <Field label="Playlist name" value={form.name} onChange={(v) => setForm({ ...form, name: v })} />
          {mode === 'xtream' ? (
            <>
              <Field label="Server URL (http://host:port)" value={form.server} onChange={(v) => setForm({ ...form, server: v })} />
              <Field label="Username" value={form.username} onChange={(v) => setForm({ ...form, username: v })} />
              <Field label="Password" value={form.password} onChange={(v) => setForm({ ...form, password: v })} type="password" />
            </>
          ) : (
            <>
              <Field label="M3U URL" value={form.m3uUrl} onChange={(v) => setForm({ ...form, m3uUrl: v })} />
              <Field label="EPG / XMLTV URL (optional)" value={form.epgUrl} onChange={(v) => setForm({ ...form, epgUrl: v })} />
            </>
          )}
        </div>

        {error && (
          <div className="mt-4 rounded-md border border-live/50 bg-live/10 text-live text-xs p-3">
            {error}
          </div>
        )}
        <button
          onClick={submit}
          disabled={!canSubmit}
          className="mt-6 w-full rounded-md bg-brand text-white font-bold py-2.5 disabled:opacity-40 hover:bg-brand-deep transition"
        >
          {testing ? 'Testing connection…' : 'Connect & Import'}
        </button>
      </div>
    </div>
  );
}

function Field({ label, value, onChange, type = 'text' }: {
  label: string; value: string; onChange: (v: string) => void; type?: string;
}) {
  return (
    <label className="block">
      <span className="text-[10px] font-black tracking-widest text-textDim">{label.toUpperCase()}</span>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1 w-full bg-white/5 rounded-md px-3 py-2 text-sm outline-none border border-white/10 focus:border-brand"
      />
    </label>
  );
}
