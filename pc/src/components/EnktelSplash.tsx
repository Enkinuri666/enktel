import { useEffect, useState } from 'react';

/**
 * Full-screen animated startup splash — matches the mobile app's SplashCanvas
 * intent: three concentric signal rings pulse outward from a big ENKTEL / IPTV
 * wordmark, one live-red dot orbits the wordmark. Fades out after ~1.6s.
 */
export default function EnktelSplash({ onDone }: { onDone: () => void }) {
  const [fading, setFading] = useState(false);
  useEffect(() => {
    const t1 = window.setTimeout(() => setFading(true), 1400);
    const t2 = window.setTimeout(onDone, 1900);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, [onDone]);

  return (
    <div
      className={`fixed inset-0 z-[100] flex items-center justify-center bg-bg transition-opacity duration-500 ${
        fading ? 'opacity-0 pointer-events-none' : 'opacity-100'
      }`}
      style={{
        background:
          'radial-gradient(1200px 800px at 30% 20%, rgba(59,157,255,0.15), transparent 60%),' +
          'radial-gradient(900px 700px at 70% 80%, rgba(139,92,246,0.15), transparent 60%),' +
          '#0A0E17',
      }}
    >
      <div className="relative flex flex-col items-center">
        <div className="relative h-56 w-56 flex items-center justify-center">
          {[0, 0.8, 1.6].map((delay, i) => (
            <div
              key={i}
              className="absolute inset-0 rounded-full border-2 border-brand animate-splashRing"
              style={{ animationDelay: `${delay}s` }}
            />
          ))}
          {/* Orbiting live-red dot */}
          <div
            className="absolute inset-0 animate-[spin_2.6s_linear_infinite]"
            style={{ transformOrigin: 'center' }}
          >
            <span
              className="absolute h-3 w-3 rounded-full bg-live shadow-[0_0_16px_rgba(239,68,68,0.6)]"
              style={{ top: '4px', left: 'calc(50% - 6px)' }}
            />
          </div>
          {/* Wordmark */}
          <div className="relative flex items-center gap-2">
            <div className="h-3 w-3 rounded-full bg-live animate-livePulse" />
            <span className="text-4xl font-black tracking-tight">
              <span className="text-white">ENK</span>
              <span className="text-brand">TEL</span>
            </span>
            <span className="text-sm font-bold tracking-widest text-textDim ml-1">IPTV</span>
          </div>
        </div>
        <p className="mt-4 text-xs font-semibold text-textDim tracking-wider">
          PREMIUM LIVE TV · SPORTS · MOVIES · SERIES
        </p>
      </div>
    </div>
  );
}
