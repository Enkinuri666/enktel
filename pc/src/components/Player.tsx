import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import { useSettings } from '@/stores/settings';

/**
 * Universal video player. Picks the best strategy for the given URL:
 *  - .m3u8 → hls.js (or native HLS on Safari, unused on Windows but kept for
 *    parity)
 *  - .mpd  → shaka-player (lazily imported so the initial bundle stays lean)
 *  - anything else → HTML5 <video> directly
 *
 * Exposes onBuffering + onError so the parent can overlay branded UI.
 */
export type PlayerProps = {
  src: string;
  autoPlay?: boolean;
  live?: boolean;
  onBuffering?: (buffering: boolean) => void;
  onError?: (msg: string) => void;
  onReady?: (video: HTMLVideoElement) => void;
  className?: string;
};

export default function Player({
  src, autoPlay = true, live = false, onBuffering, onError, onReady, className,
}: PlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [buffering, setBuffering] = useState(true);
  // v1.26.0 port from android — Streaming Companion Mode locks hls.js to
  // its top-quality level and enlarges the segment buffer so a Discord
  // screen-share viewer doesn't see bitrate flapping or micro-stalls.
  const companionMode = useSettings((s) => s.companionMode);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !src) return;
    onBuffering?.(true);
    setBuffering(true);

    const isHls = /\.m3u8(\?|$)/i.test(src);
    const isDash = /\.mpd(\?|$)/i.test(src);

    const cleanup = () => {
      hlsRef.current?.destroy();
      hlsRef.current = null;
    };

    if (isHls && Hls.isSupported()) {
      const hls = new Hls({
        lowLatencyMode: live && !companionMode,
        // Companion Mode: bigger sync window + backBuffer so a hiccup in
        // your outbound Discord stream doesn't drain the media element.
        liveSyncDurationCount: live ? (companionMode ? 6 : 3) : 5,
        enableWorker: true,
        backBufferLength: companionMode ? 120 : (live ? 30 : 90),
        // Cap the forward-fetch further out so we've always got runway.
        maxBufferLength: companionMode ? 60 : 30,
        maxMaxBufferLength: companionMode ? 300 : 60,
        // Companion Mode: don't cap level by measured bandwidth — the
        // Discord path is what's variable, not the source feed. capLevelToPlayerSize
        // would down-select when the window is small, which is wrong here.
        capLevelToPlayerSize: !companionMode,
      });
      hlsRef.current = hls;
      hls.loadSource(src);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (companionMode && hls.levels.length > 0) {
          // Pin to top level and stop the ABR from re-picking. Users who need
          // the source to downshift can toggle Companion Mode off in Settings.
          hls.currentLevel = hls.levels.length - 1;
          hls.autoLevelCapping = hls.levels.length - 1;
        }
        if (autoPlay) video.play().catch(() => {});
        onReady?.(video);
      });
      hls.on(Hls.Events.ERROR, (_ev, data) => {
        if (data.fatal) {
          onError?.(data.details ?? data.type);
        }
      });
    } else if (isDash) {
      // Lazy shaka — keeps the initial bundle smaller and only pays the ~200kb
      // cost the first time the user opens a DASH stream.
      import('shaka-player').then(async (mod) => {
        const shaka: any = mod.default ?? mod;
        try { shaka.polyfill.installAll(); } catch { /* already installed */ }
        const player = new shaka.Player(video);
        player.addEventListener('error', (e: any) => onError?.(String(e.detail ?? e.code)));
        try {
          await player.load(src);
          if (autoPlay) video.play().catch(() => {});
          onReady?.(video);
        } catch (err: any) {
          onError?.(err?.message ?? 'DASH load failed');
        }
      });
    } else {
      video.src = src;
      if (autoPlay) video.play().catch(() => {});
      onReady?.(video);
    }

    // Buffering tracking via native events. HLS.js fires FRAG_BUFFERED too but
    // the video element covers everyone.
    const onWaiting = () => { setBuffering(true); onBuffering?.(true); };
    const onCanPlay = () => { setBuffering(false); onBuffering?.(false); };
    const onErr = () => onError?.(`Video error (${video.error?.code ?? '?'})`);
    video.addEventListener('waiting', onWaiting);
    video.addEventListener('canplay', onCanPlay);
    video.addEventListener('playing', onCanPlay);
    video.addEventListener('error', onErr);

    return () => {
      video.removeEventListener('waiting', onWaiting);
      video.removeEventListener('canplay', onCanPlay);
      video.removeEventListener('playing', onCanPlay);
      video.removeEventListener('error', onErr);
      cleanup();
    };
  }, [src, autoPlay, live, onBuffering, onError, onReady, companionMode]);

  return (
    <div className={`relative w-full h-full bg-black ${className ?? ''}`}>
      <video
        ref={videoRef}
        className="w-full h-full object-contain"
        playsInline
        controls={false}
      />
      {buffering && (
        <div className="absolute inset-0 grid place-items-center pointer-events-none">
          <div className="glass rounded-2xl p-5 flex flex-col items-center gap-3">
            <div className="relative h-16 w-16">
              <div className="absolute inset-0 rounded-full border-2 border-brand/25 animate-[spin_1.4s_linear_infinite]" />
              <div className="absolute inset-2 rounded-full border-2 border-live/70 border-t-transparent animate-[spin_2s_linear_infinite_reverse]" />
              <div className="absolute inset-0 grid place-items-center">
                <span className="text-[10px] font-black">
                  <span className="text-white">ENK</span>
                  <span className="text-brand">TEL</span>
                </span>
              </div>
            </div>
            <span className="text-xs font-semibold text-textDim">Buffering</span>
          </div>
        </div>
      )}
    </div>
  );
}
