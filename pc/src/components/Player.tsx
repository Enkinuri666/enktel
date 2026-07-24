import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';

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
        lowLatencyMode: live,
        liveSyncDurationCount: live ? 3 : 5,
        enableWorker: true,
        backBufferLength: live ? 30 : 90,
      });
      hlsRef.current = hls;
      hls.loadSource(src);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
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
  }, [src, autoPlay, live, onBuffering, onError, onReady]);

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
