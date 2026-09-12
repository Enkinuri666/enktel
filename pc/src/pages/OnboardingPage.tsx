import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSettings, type Profile } from '@/stores/settings';
import { xtreamLogin } from '@/lib/xtream';
import { parseSetup, suggestedName, friendlyError } from '@/lib/setupLink';

/**
 * The host to assume when someone gives a login and no address.
 *
 * A reseller hands out a username and a password because all their customers
 * are on one panel. The old form covered that by leaving the server field for
 * the user to fill, which meant every subscriber typing the same address; it
 * lives here instead. `VITE_DEFAULT_SERVER` overrides it at build time,
 * matching the phone's `ENK_DEFAULT_SERVER`.
 */
const DEFAULT_SERVER = import.meta.env.VITE_DEFAULT_SERVER ?? 'https://x-api.cc';

/**
 * First run on the desktop: sign in.
 *
 * This asked for Xtream-vs-M3U, a playlist name, a server URL
 * "(http://host:port)", a username and a password — five questions, the first
 * of which is a protocol question. It became a single paste box, which was too
 * far the other way: the two things an EnkTel subscriber is handed are a
 * username and a password, and a box labelled "paste your link" is not where
 * someone holding two words looks.
 *
 * So the same shape as the phone's `OnboardingScreen`: the two fields in
 * front, the server already known because it is one panel for every
 * subscriber, and one extra line for anyone on a different provider or
 * holding a link. Deliberately identical — the two apps are handed the same
 * welcome email by the same person, and one of them asking different
 * questions reads as the other being broken.
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
  /** The link/server row, opened by anyone not on the default panel. */
  const [showLink, setShowLink] = useState(false);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (testing) return;
    setError(null);

    const setup = parseSetup(pasted, username, password, DEFAULT_SERVER);
    if (setup.kind === 'unrecognised') {
      // With the fields in front, the common failure is an empty one rather
      // than an unreadable paste.
      setError(
        !username.trim() || !password.trim()
          ? 'Enter the username and password your provider sent you.'
          : setup.reason,
      );
      return;
    }
    if (setup.kind === 'needsCredentials') {
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
        <h1 className="text-2xl font-black mb-1">Sign in to EnkTel</h1>
        <p className="text-sm text-textDim mb-6">
          Use the username and password from your welcome email.
        </p>

        <div className="space-y-3">
          <Field label="Username" value={username} onChange={setUsername} autoFocus />
          <Field label="Password" value={password} onChange={setPassword} type="password" />

          {!showLink ? (
            <div className="pt-1">
              {/* Stated rather than hidden: someone whose provider is not ours
                  needs to know the app has assumed one. */}
              <p className="text-xs text-textDim mb-2">
                Connecting to {DEFAULT_SERVER.replace(/^https?:\/\//, '')}
              </p>
              <button
                onClick={() => setShowLink(true)}
                className="text-xs font-bold text-brand hover:underline"
              >
                Different provider, or a setup link
              </button>
            </div>
          ) : (
            <label className="block">
              <span className="text-[10px] font-black tracking-widest text-textDim">
                SERVER ADDRESS, OR PASTE YOUR SETUP LINK
              </span>
              <textarea
                value={pasted}
                onChange={(e) => setPasted(e.target.value)}
                rows={3}
                spellCheck={false}
                className="mt-1 w-full resize-y bg-white/5 rounded-md px-3 py-2 text-sm outline-none border border-white/10 focus:border-brand font-mono"
              />
              <span className="mt-1 block text-xs text-textDim">
                A link from your provider works here too — it carries the server, and
                often the login as well.
              </span>
            </label>
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
          {testing ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="mt-5 text-center text-xs text-textDim">
          Your details stay on this computer.
        </p>
      </div>
    </div>
  );
}

function Field({ label, value, onChange, type = 'text', autoFocus = false }: {
  label: string; value: string; onChange: (v: string) => void; type?: string; autoFocus?: boolean;
}) {
  return (
    <label className="block">
      <span className="text-[10px] font-black tracking-widest text-textDim">{label.toUpperCase()}</span>
      <input
        type={type}
        autoFocus={autoFocus}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1 w-full bg-white/5 rounded-md px-3 py-2 text-sm outline-none border border-white/10 focus:border-brand"
      />
    </label>
  );
}
