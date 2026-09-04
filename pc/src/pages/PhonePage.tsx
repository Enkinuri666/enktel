import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle, Check, Download, Loader2, Pause, Play, RefreshCw,
  RotateCcw, Search, Smartphone, Trash2, X,
} from 'lucide-react';
import {
  act, baseUrl, defaultDir, discover, downloads, eta, files, humanBytes, humanRate,
  onProgress, pair, save,
  type Device, type Job, type JobAction, type RemoteFile, type SaveProgress,
} from '../lib/link';

/**
 * "My devices" — the desktop half of Send to PC.
 *
 * Three things happen on this one screen, in the order someone actually does
 * them: find the phone, type the PIN it is showing, then take the files and
 * drive the queue. Splitting that across three screens would mean two of them
 * are always empty.
 *
 * Everything is held in component state and nothing is written to disk except
 * the files themselves. The pairing token belongs to a share that ends when
 * the viewer stops it on the phone, so persisting it would only produce a
 * client that looks connected to something that is gone.
 */

type Phase = 'idle' | 'scanning' | 'pairing' | 'linked';

/** One save in flight, keyed by the file's share token. */
type Transfer = { received: number; total: number; done: boolean; error: string | null };

export default function PhonePage() {
  const [phase, setPhase] = useState<Phase>('idle');
  const [devices, setDevices] = useState<Device[]>([]);
  const [manual, setManual] = useState('');
  const [chosen, setChosen] = useState<Device | null>(null);
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');

  const [base, setBase] = useState('');
  const [token, setToken] = useState('');
  const [deviceName, setDeviceName] = useState('');

  const [remoteFiles, setRemoteFiles] = useState<RemoteFile[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [saveDir, setSaveDir] = useState('');
  const [transfers, setTransfers] = useState<Record<string, Transfer>>({});
  const [saved, setSaved] = useState<Record<string, string>>({});
  const [confirmCancel, setConfirmCancel] = useState<Job | null>(null);

  // Read inside the poll loop, which must not restart every time they change.
  const linked = useRef({ base: '', token: '' });
  linked.current = { base, token };

  useEffect(() => {
    defaultDir().then(setSaveDir).catch(() => setSaveDir(''));
  }, []);

  // ---- discovery ----------------------------------------------------------

  const scan = useCallback(async () => {
    setPhase('scanning');
    setError('');
    try {
      const found = await discover(1800);
      setDevices(found);
      if (found.length === 0) {
        setError(
          'Nothing answered. Check both devices are on the same Wi-Fi and that ' +
            'the phone is showing a PIN, or type its address below.',
        );
      }
    } catch (e) {
      setError(String(e));
    } finally {
      setPhase((p) => (p === 'scanning' ? 'idle' : p));
    }
  }, []);

  useEffect(() => {
    void scan();
  }, [scan]);

  // ---- pairing ------------------------------------------------------------

  async function doPair(target: string) {
    setPhase('pairing');
    setError('');
    try {
      const p = await pair(target, pin);
      setBase(target);
      setToken(p.token);
      setDeviceName(p.device);
      setPhase('linked');
      setPin('');
    } catch (e) {
      setError(String(e).replace(/^Error:\s*/, ''));
      setPhase('idle');
    }
  }

  function unlink() {
    setPhase('idle');
    setBase('');
    setToken('');
    setDeviceName('');
    setRemoteFiles([]);
    setJobs([]);
    setTransfers({});
    setSaved({});
    setChosen(null);
    void scan();
  }

  // ---- live state ---------------------------------------------------------

  // Polled rather than pushed: the phone's server answers requests and does
  // not hold sockets open, which is the right shape for something that has to
  // survive the phone sleeping. Two seconds is fast enough to watch a progress
  // bar and slow enough to be invisible on a home network.
  useEffect(() => {
    if (phase !== 'linked') return;
    let alive = true;

    async function tick() {
      const { base: b, token: t } = linked.current;
      if (!b || !t) return;
      try {
        const [f, j] = await Promise.all([files(b, t), downloads(b, t)]);
        if (!alive) return;
        setRemoteFiles(f);
        setJobs(j);
        setError('');
      } catch (e) {
        if (!alive) return;
        // A 401 here means the share was stopped and restarted on the phone,
        // so the token is for a server that no longer exists. Saying so beats
        // a screen that quietly stops updating.
        setError(String(e).replace(/^Error:\s*/, ''));
      }
    }

    void tick();
    const id = window.setInterval(tick, 2000);
    return () => {
      alive = false;
      window.clearInterval(id);
    };
  }, [phase]);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    onProgress((p: SaveProgress) => {
      setTransfers((prev) => ({
        ...prev,
        [p.token]: {
          received: p.received,
          total: p.total,
          done: p.done,
          error: p.error,
        },
      }));
    }).then((f) => {
      unlisten = f;
    });
    return () => unlisten?.();
  }, []);

  // ---- actions ------------------------------------------------------------

  async function fetchOne(f: RemoteFile) {
    setTransfers((p) => ({ ...p, [f.token]: { received: 0, total: f.size, done: false, error: null } }));
    try {
      const path = await save(base, token, f.token, f.name, saveDir);
      setSaved((p) => ({ ...p, [f.token]: path }));
    } catch (e) {
      setTransfers((p) => ({
        ...p,
        [f.token]: { received: 0, total: f.size, done: true, error: String(e).replace(/^Error:\s*/, '') },
      }));
    }
  }

  async function fetchAll() {
    // Deliberately one at a time. Four parallel six-gigabyte pulls over house
    // Wi-Fi are slower in total than four in a row and make every one of them
    // look stalled.
    for (const f of remoteFiles) {
      if (saved[f.token]) continue;
      // eslint-disable-next-line no-await-in-loop
      await fetchOne(f);
    }
  }

  async function drive(job: Job, action: JobAction) {
    try {
      const applied = await act(base, token, job.id, action);
      if (!applied) setJobs((prev) => prev.filter((j) => j.id !== job.id));
    } catch (e) {
      setError(String(e).replace(/^Error:\s*/, ''));
    }
  }

  const pending = useMemo(
    () => jobs.filter((j) => j.status !== 'DONE'),
    [jobs],
  );

  // ---- render -------------------------------------------------------------

  if (phase !== 'linked') {
    return (
      <div className="p-8 max-w-3xl">
        <Header />

        <p className="text-textDim text-sm mb-6 leading-relaxed">
          On the phone or TV box, open <strong className="text-text">Downloads</strong> and press{' '}
          <strong className="text-text">Send to PC</strong>. It will show an address and a PIN.
          This app looks for it on your network — pick it below and type the PIN.
        </p>

        <div className="glass rounded-xl p-5 mb-4">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold">Devices on this network</h2>
            <button
              type="button"
              onClick={() => void scan()}
              disabled={phase === 'scanning'}
              className="chip"
            >
              {phase === 'scanning' ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <RefreshCw className="h-3.5 w-3.5" />
              )}
              {phase === 'scanning' ? 'Looking…' : 'Scan again'}
            </button>
          </div>

          {devices.length === 0 && phase !== 'scanning' && (
            <p className="text-textDim text-sm flex items-center gap-2">
              <Search className="h-4 w-4 shrink-0" />
              No EnkTel device answered.
            </p>
          )}

          <ul className="space-y-2">
            {devices.map((d) => (
              <li key={`${d.ip}:${d.port}`}>
                <button
                  type="button"
                  onClick={() => {
                    setChosen(d);
                    setManual('');
                  }}
                  data-selected={chosen?.ip === d.ip && chosen?.port === d.port}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg border border-white/10
                             bg-white/[0.03] hover:bg-white/[0.07] focus-ring text-left
                             data-[selected=true]:border-brand/60 data-[selected=true]:bg-brand/20"
                >
                  <Smartphone className="h-4 w-4 shrink-0 text-brand" />
                  <span className="flex-1">
                    <span className="block text-sm font-semibold">{d.name}</span>
                    <span className="block text-xs text-textDim">{baseUrl(d)}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>

          <label className="block mt-4 text-xs text-textDim" htmlFor="manual">
            Or type the address the phone is showing
          </label>
          <input
            id="manual"
            value={manual}
            onChange={(e) => {
              setManual(e.target.value);
              setChosen(null);
            }}
            placeholder="http://192.168.1.24:8787"
            className="mt-1 w-full rounded-lg bg-black/30 border border-border px-3 py-2 text-sm
                       outline-none focus:border-brand"
          />
        </div>

        <div className="glass rounded-xl p-5">
          <label className="block text-sm font-semibold mb-2" htmlFor="pin">
            PIN shown on the device
          </label>
          <div className="flex gap-3">
            <input
              id="pin"
              value={pin}
              onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, 6))}
              inputMode="numeric"
              autoComplete="off"
              placeholder="000000"
              className="w-40 rounded-lg bg-black/30 border border-border px-3 py-2 tracking-[0.4em]
                         text-lg outline-none focus:border-brand"
            />
            <button
              type="button"
              disabled={pin.length !== 6 || phase === 'pairing' || (!chosen && !manual.trim())}
              onClick={() => void doPair(chosen ? baseUrl(chosen) : normalise(manual))}
              className="px-5 rounded-lg bg-brand text-white font-semibold disabled:opacity-40
                         disabled:cursor-not-allowed focus-ring"
            >
              {phase === 'pairing' ? 'Connecting…' : 'Connect'}
            </button>
          </div>
          <p className="text-xs text-textDim mt-3">
            The PIN is new every time sharing starts, and the phone stops accepting guesses
            after ten wrong ones.
          </p>
        </div>

        {error && <Notice text={error} />}
      </div>
    );
  }

  return (
    <div className="p-8 max-w-4xl">
      <Header />

      <div className="flex items-center justify-between mb-6">
        <p className="text-sm">
          Connected to <strong>{deviceName}</strong>{' '}
          <span className="text-textDim">· {base}</span>
        </p>
        <button type="button" onClick={unlink} className="chip">
          <X className="h-3.5 w-3.5" />
          Disconnect
        </button>
      </div>

      {error && <Notice text={error} />}

      {/* ---- save location ---- */}
      <div className="glass rounded-xl p-5 mb-5">
        <label className="block text-sm font-semibold mb-2" htmlFor="dir">
          Save files to
        </label>
        <input
          id="dir"
          value={saveDir}
          onChange={(e) => setSaveDir(e.target.value)}
          spellCheck={false}
          className="w-full rounded-lg bg-black/30 border border-border px-3 py-2 text-sm
                     outline-none focus:border-brand font-mono"
        />
      </div>

      {/* ---- files ready to take ---- */}
      <section className="glass rounded-xl p-5 mb-5">
        <div className="flex items-center justify-between mb-3">
          <h2 className="font-semibold">Ready to copy across ({remoteFiles.length})</h2>
          {remoteFiles.length > 1 && (
            <button type="button" onClick={() => void fetchAll()} className="chip" disabled={!saveDir}>
              <Download className="h-3.5 w-3.5" />
              Get all
            </button>
          )}
        </div>

        {remoteFiles.length === 0 ? (
          <p className="text-textDim text-sm">
            Nothing finished downloading on that device yet. Only completed files are offered —
            a half-written one would arrive looking whole and be unplayable.
          </p>
        ) : (
          <ul className="space-y-2">
            {remoteFiles.map((f) => {
              const t = transfers[f.token];
              const path = saved[f.token];
              const busy = t && !t.done;
              const pct = t && t.total > 0 ? Math.min(100, Math.round((t.received / t.total) * 100)) : 0;
              return (
                <li key={f.token} className="rounded-lg border border-white/10 bg-white/[0.03] p-3">
                  <div className="flex items-center gap-3">
                    <span className="flex-1 min-w-0">
                      <span className="block text-sm font-medium truncate">{f.name}</span>
                      <span className="block text-xs text-textDim">
                        {humanBytes(f.size)}
                        {busy && t.total > 0 && ` · ${humanBytes(t.received)} of ${humanBytes(t.total)}`}
                      </span>
                    </span>
                    {path ? (
                      <span className="text-ok text-xs flex items-center gap-1.5 shrink-0">
                        <Check className="h-4 w-4" />
                        Saved
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => void fetchOne(f)}
                        disabled={!!busy || !saveDir}
                        className="chip shrink-0"
                      >
                        {busy ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          <Download className="h-3.5 w-3.5" />
                        )}
                        {busy ? `${pct}%` : t?.error ? 'Try again' : 'Get'}
                      </button>
                    )}
                  </div>

                  {busy && (
                    <div className="mt-2 h-1.5 rounded-full bg-white/10 overflow-hidden">
                      <div className="h-full bg-brand transition-[width]" style={{ width: `${pct}%` }} />
                    </div>
                  )}
                  {path && <p className="mt-1.5 text-xs text-textDim font-mono truncate">{path}</p>}
                  {t?.error && (
                    <p className="mt-1.5 text-xs text-live flex items-start gap-1.5">
                      <AlertTriangle className="h-3.5 w-3.5 shrink-0 mt-px" />
                      {t.error}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {/* ---- the phone's queue ---- */}
      <section className="glass rounded-xl p-5">
        <h2 className="font-semibold mb-1">That device&rsquo;s downloads ({pending.length} in progress)</h2>
        <p className="text-xs text-textDim mb-3">
          These are downloading on the phone, not here. Pausing or cancelling one from this
          screen does it there.
        </p>

        {jobs.length === 0 ? (
          <p className="text-textDim text-sm">Its download queue is empty.</p>
        ) : (
          <ul className="space-y-2">
            {jobs.map((j) => (
              <li key={j.id} className="rounded-lg border border-white/10 bg-white/[0.03] p-3">
                <div className="flex items-center gap-3">
                  <span className="flex-1 min-w-0">
                    <span className="block text-sm font-medium truncate">{j.title}</span>
                    <span className="block text-xs text-textDim truncate">
                      {[
                        j.subtitle,
                        statusLabel(j),
                        humanRate(j.speedBps),
                        eta(j.downloadedBytes, j.sizeBytes, j.speedBps),
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </span>
                  </span>

                  <span className="flex items-center gap-1.5 shrink-0">
                    {j.status === 'RUNNING' && (
                      <IconButton label="Pause" onClick={() => void drive(j, 'pause')}>
                        <Pause className="h-4 w-4" />
                      </IconButton>
                    )}
                    {j.status === 'PAUSED' && (
                      <IconButton label="Resume" onClick={() => void drive(j, 'resume')}>
                        <Play className="h-4 w-4" />
                      </IconButton>
                    )}
                    {j.status === 'FAILED' && (
                      <IconButton label="Retry" onClick={() => void drive(j, 'retry')}>
                        <RotateCcw className="h-4 w-4" />
                      </IconButton>
                    )}
                    <IconButton label="Cancel" danger onClick={() => setConfirmCancel(j)}>
                      <Trash2 className="h-4 w-4" />
                    </IconButton>
                  </span>
                </div>

                {j.status !== 'DONE' && (
                  <div className="mt-2 h-1.5 rounded-full bg-white/10 overflow-hidden">
                    <div
                      className={`h-full transition-[width] ${j.status === 'FAILED' ? 'bg-live' : 'bg-brand'}`}
                      style={{ width: `${Math.max(0, Math.min(100, j.progressPct))}%` }}
                    />
                  </div>
                )}
                {j.error && (
                  <p className="mt-1.5 text-xs text-live flex items-start gap-1.5">
                    <AlertTriangle className="h-3.5 w-3.5 shrink-0 mt-px" />
                    {j.error}
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {confirmCancel && (
        <ConfirmCancel
          job={confirmCancel}
          onClose={() => setConfirmCancel(null)}
          onConfirm={() => {
            void drive(confirmCancel, 'cancel');
            setConfirmCancel(null);
          }}
        />
      )}
    </div>
  );
}

// ---- bits -----------------------------------------------------------------

function Header() {
  return (
    <div className="rail-header">
      <h1 className="text-xl font-bold">My devices</h1>
    </div>
  );
}

function Notice({ text }: { text: string }) {
  return (
    <p className="mb-4 rounded-lg border border-live/40 bg-live/10 px-3 py-2 text-sm flex items-start gap-2">
      <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-live" />
      {text}
    </p>
  );
}

function IconButton({
  label, onClick, danger, children,
}: {
  label: string;
  onClick: () => void;
  danger?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={`rounded-md p-1.5 focus-ring border border-white/10 ${
        danger ? 'text-live hover:bg-live/15' : 'text-textDim hover:text-text hover:bg-white/10'
      }`}
    >
      {children}
    </button>
  );
}

/**
 * Cancelling deletes the part-file on the phone as well as the queue row — the
 * same thing its own cancel button does. Destructive and one click away, so it
 * asks.
 */
function ConfirmCancel({
  job, onClose, onConfirm,
}: {
  job: Job;
  onClose: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/60 p-6">
      <div className="glass-strong rounded-xl p-6 max-w-md">
        <h3 className="font-bold mb-2">Cancel this download?</h3>
        <p className="text-sm text-textDim mb-5">
          <strong className="text-text">{job.title}</strong> will be removed from that
          device&rsquo;s queue and whatever has already downloaded will be deleted from it.
          Nothing already saved on this PC is touched.
        </p>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="chip">
            Keep it
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="px-4 py-1.5 rounded-full text-xs font-semibold bg-live text-white focus-ring"
          >
            Cancel download
          </button>
        </div>
      </div>
    </div>
  );
}

function statusLabel(j: Job): string {
  switch (j.status) {
    case 'RUNNING':
      return `${j.progressPct}%`;
    case 'PAUSED':
      return `Paused at ${j.progressPct}%`;
    case 'QUEUED':
      return 'Waiting';
    case 'DONE':
      return `Finished · ${humanBytes(j.sizeBytes)}`;
    case 'FAILED':
      return 'Failed';
    default:
      return j.status;
  }
}

/** Accept "192.168.1.24", "192.168.1.24:8787" or a full URL. */
function normalise(raw: string): string {
  const s = raw.trim().replace(/\/+$/, '');
  if (!s) return s;
  const withScheme = /^https?:\/\//i.test(s) ? s : `http://${s}`;
  // A bare host means the port the app uses by default; anything else the
  // viewer typed is kept, because the phone falls back to a random port when
  // 8787 is taken and prints the real one.
  return /:\d+$/.test(withScheme) ? withScheme : `${withScheme}:8787`;
}
