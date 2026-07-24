import { useState } from 'react';
import Player from '@/components/Player';

type Channel = { key: string; num: number; name: string; url: string };

// Demo data — replaced by the Xtream/M3U query once the Rust backend + `invoke`
// wiring lands. Keeps the page compilable + demoable in the meantime.
const DEMO_CHANNELS: Channel[] = [
  { key: 'demo-hls',  num: 1, name: 'Big Buck Bunny (HLS)', url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8' },
  { key: 'demo-hls2', num: 2, name: 'Apple HLS Test',       url: 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8' },
];

/**
 * Live TV shell: left channel column + right video pane. Selecting a channel
 * swaps the src on the Player component so hls.js is reused with a light
 * dispose+recreate under the hood.
 */
export default function LiveTVPage() {
  const [current, setCurrent] = useState<Channel>(DEMO_CHANNELS[0]);

  return (
    <div className="flex h-full">
      {/* Channel list */}
      <aside className="w-80 shrink-0 border-r border-white/5 bg-surface/60 backdrop-blur flex flex-col">
        <div className="px-4 py-3 border-b border-white/5">
          <div className="text-[10px] font-black tracking-widest text-textDim">CHANNELS</div>
          <div className="text-lg font-black">Live TV</div>
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          {DEMO_CHANNELS.map((c) => (
            <button
              key={c.key}
              onClick={() => setCurrent(c)}
              className={`w-full text-left px-4 py-2.5 flex items-center gap-3 hover:bg-white/5 ${
                current.key === c.key ? 'bg-brand/15 border-l-2 border-brand' : 'border-l-2 border-transparent'
              }`}
            >
              <span className="text-xs font-black text-brand w-8">{c.num}</span>
              <span className="text-sm font-semibold truncate flex-1">{c.name}</span>
              {current.key === c.key && (
                <span className="h-2 w-2 rounded-full bg-live animate-livePulse" />
              )}
            </button>
          ))}
        </div>
      </aside>

      {/* Player */}
      <section className="flex-1 flex flex-col bg-black">
        <div className="flex-1 relative">
          <Player src={current.url} live autoPlay />
        </div>
        <div className="glass px-6 py-3 border-t border-white/5 flex items-center gap-6">
          <div>
            <div className="text-[10px] font-black tracking-widest text-live">● LIVE</div>
            <div className="text-lg font-bold">{current.name}</div>
          </div>
          <div className="ml-auto text-[10px] text-textDim">
            Ctrl+K for the command palette · scroll the list to zap · Space to pause
          </div>
        </div>
      </section>
    </div>
  );
}
