import { useEffect } from 'react';
import { Command, Search, Mic } from 'lucide-react';
import { getCurrentWindow } from '@tauri-apps/api/window';

/**
 * Custom title bar since we hide the Windows chrome for a Netflix-style
 * edge-to-edge look. Left side is app-drag; the buttons are marked
 * app-no-drag so they still respond to clicks.
 */
export default function TitleBar({ onCommandPalette }: { onCommandPalette: () => void }) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        onCommandPalette();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onCommandPalette]);

  const call = (fn: 'minimize' | 'toggleMaximize' | 'close') => async () => {
    try {
      const w = getCurrentWindow();
      if (fn === 'minimize') await w.minimize();
      else if (fn === 'toggleMaximize') await w.toggleMaximize();
      else await w.close();
    } catch {
      // Running in browser mode (npm run dev) — no window control, no-op.
    }
  };

  return (
    <header className="h-11 shrink-0 app-drag flex items-center justify-between px-3 bg-surface/95 backdrop-blur border-b border-white/5">
      <div className="flex items-center gap-2">
        <div className="h-2 w-2 rounded-full bg-live" />
        <span className="text-sm font-black tracking-tight">
          <span className="text-white">ENK</span>
          <span className="text-brand">TEL</span>
          <span className="text-textDim ml-1 text-[10px] font-bold tracking-widest">IPTV</span>
        </span>
      </div>

      <div className="flex items-center gap-2 app-no-drag">
        <button
          onClick={onCommandPalette}
          className="chip"
          title="Command palette (Ctrl+K)"
        >
          <Command className="h-3 w-3" />
          <span>Ctrl+K</span>
        </button>
        <button className="chip" title="Search">
          <Search className="h-3 w-3" />
        </button>
        <button className="chip" title="Voice">
          <Mic className="h-3 w-3" />
        </button>
        <div className="w-3" />
        <button
          onClick={call('minimize')}
          className="h-6 w-8 grid place-items-center rounded hover:bg-white/10"
          title="Minimize"
          aria-label="Minimize"
        >
          <div className="h-px w-3 bg-textDim" />
        </button>
        <button
          onClick={call('toggleMaximize')}
          className="h-6 w-8 grid place-items-center rounded hover:bg-white/10"
          title="Maximize"
          aria-label="Maximize"
        >
          <div className="h-3 w-3 border border-textDim rounded-sm" />
        </button>
        <button
          onClick={call('close')}
          className="h-6 w-8 grid place-items-center rounded hover:bg-live/60"
          title="Close"
          aria-label="Close"
        >
          <div className="relative h-3 w-3">
            <div className="absolute inset-0 rotate-45 top-1.5 h-px bg-textDim" />
            <div className="absolute inset-0 -rotate-45 top-1.5 h-px bg-textDim" />
          </div>
        </button>
      </div>
    </header>
  );
}
