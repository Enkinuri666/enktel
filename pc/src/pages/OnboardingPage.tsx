import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSettings, type Profile } from '@/stores/settings';
import { xtreamLogin } from '@/lib/xtream';
import { parseSetup, suggestedName, friendlyError } from '@/lib/setupLink';

/**
 * The host to assume when someone pastes a login and no address.
 *
 * A reseller hands out a username and a password because all their customers
 * are on one panel. The old form covered that by leaving the server field for
 * the user to fill; a single paste box has nowhere to put it, so it lives
 * here. `VITE_DEFAULT_SERVER` overrides it at build time, matching the phone's
 * `ENK_DEFAULT_SERVER`.
 */
const DEFAULT_SERVER = import.meta.env.VITE_DEFAULT_SERVER ?? 'https://x-api.cc';

/**
 * First run on the desktop: one box, then watch.
 *
 * This asked for Xtream-vs-M3U, a playlist name, a server URL
 * "(http://host:port)", a username and a password — five questions, the first
 * of which is a protocol question. The phone stopped asking any of that at
 * 1.69.0, and leaving the desktop as it was would have meant one subscriber
 * being walked through two different setups for the same account.
 *
 * `parseSetup` is a port of the phone's parser and `check-setup-link.mjs` runs
 * the phone's own test cases against it, so the two cannot drift into
 * accepting different things.
 */
export default function OnboardingPage() {
  const nav = useNavigate();
  const setProfile = useSettings((s) => s.setProfile);
  const [pasted, setPasted] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  /** Shown only once a paste has turned out not to carry a login. */
  const [askForLogin, setAskForLogin] = useState(false);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (testing) return;
    setError(null);

    const setup = parseSetup(pasted, username, password, DEFAULT_SERVER);
    if (setup.kind === 'unrecognised') {
      setError(setup.reason);
      return;
    }
    if (setup.kind === 'needsCredentials') {
      // Not a failure and not phrased as one: the address arrived, the login
      // did not. Ask for exactly the two things missing.
      setAskForLogin(true);
      setError('Almost there — now your username and password.');
      return;
    }

    const name = suggestedName(setup);

    if (setup.kind === 'xtream') {
      // Pre-flight the credentials — surfaces "wrong password" / "server not
      // reachable" up front rather than dropping someone onto an empty Home
      // screen with no explanation.
      setTesting(true);
      const r = await xtreamLogin({
        server: setup.server, username: setup.username, password: setup.password,
      });
      setTesting(false);
      if (!r.ok) {
        // Was `r.error` verbatim, which is how a mistyped password came out as
        // "Panel rejected the credentials" and a bad host as a raw network
        // error. Neither says what to do next.
        setError(friendlyError(r.error));
        setAskForLogin(true);
        return;
      }
    }

    const profile: Profile = setup.kind === 'xtream'
      ? { kind: 'xtream', name, server: setup.server, username: setup.username, password: setup.password }
      : { kind: 'm3u', name, m3uUrl: setup.url, epgUrl: '' };
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
        <h1 className="text-2xl font-black mb-1">Paste the link your provider sent you</h1>
        <p className="text-sm text-textDim mb-6">
          It's in your welcome email. You can paste the whole message — we'll find the
          parts we need.
        </p>

        <div className="space-y-3">
          <label className="block">
            <span className="text-[10px] font-black tracking-widest text-textDim">
              PASTE YOUR LINK OR DETAILS HERE
            </span>
            <textarea
              value={pasted}
              onChange={(e) => setPasted(e.target.value)}
              rows={3}
              spellCheck={false}
              autoFocus
              className="mt-1 w-full resize-y bg-white/5 rounded-md px-3 py-2 text-sm outline-none border border-white/10 focus:border-brand font-mono"
            />
          </label>

          {askForLogin && (
            <>
              <Field label="Username" value={username} onChange={setUsername} />
              <Field label="Password" value={password} onChange={setPassword} type="password" />
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
          disabled={testing}
          className="mt-6 w-full rounded-md bg-brand text-white font-bold py-2.5 disabled:opacity-40 hover:bg-brand-deep transition"
        >
          {testing ? 'Connecting…' : 'Start watching'}
        </button>

        <p className="mt-5 text-center text-xs text-textDim">
          Your details stay on this computer.
        </p>
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
