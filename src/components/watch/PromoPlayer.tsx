"use client";
// Full multi-scene animated promo player ("the launch video") for the
// standalone /watch page. Ported from a design-canvas prototype that used a
// custom Stage/Sprite timeline engine; recolored to the real Enktel brand
// (purple/cyan) and re-pointed at real data (lib/plans, lib/channels) so the
// numbers here can never drift from what's actually charged on /pricing.

import React from "react";
import Link from "next/link";
import Image from "next/image";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";
import { PLAN_PRICE_EUR, PLAN_REGULAR_PRICE_EUR } from "@/lib/plans";

// ───────────────────────── Timeline engine ─────────────────────────
const Easing = {
  linear: (t: number) => t,
  easeOutCubic: (t: number) => --t * t * t + 1,
  easeInCubic: (t: number) => t * t * t,
  easeOutQuad: (t: number) => t * (2 - t),
  easeOutExpo: (t: number) => (t === 1 ? 1 : 1 - Math.pow(2, -10 * t)),
  easeOutBack: (t: number) => {
    const c1 = 1.70158, c3 = c1 + 1;
    return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
  },
};
const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, v));

type TimelineValue = { time: number; duration: number; playing: boolean };
const TimelineContext = React.createContext<TimelineValue>({ time: 0, duration: 1, playing: false });
const useTime = () => React.useContext(TimelineContext).time;
const useTimeline = () => React.useContext(TimelineContext);

type SpriteValue = { localTime: number; progress: number; duration: number };
const SpriteContext = React.createContext<SpriteValue>({ localTime: 0, progress: 0, duration: 0 });
const useSprite = () => React.useContext(SpriteContext);

function Sprite({
  start = 0,
  end = Infinity,
  children,
}: {
  start?: number;
  end?: number;
  children: React.ReactNode | ((v: SpriteValue) => React.ReactNode);
}) {
  const { time } = useTimeline();
  const visible = time >= start && time <= end;
  if (!visible) return null;
  const duration = end - start;
  const localTime = Math.max(0, time - start);
  const progress = duration > 0 && isFinite(duration) ? clamp(localTime / duration, 0, 1) : 0;
  const value = { localTime, progress, duration };
  return (
    <SpriteContext.Provider value={value}>
      {typeof children === "function" ? children(value) : children}
    </SpriteContext.Provider>
  );
}

function Stage({
  duration,
  children,
}: {
  duration: number;
  children: React.ReactNode;
}) {
  const [time, setTime] = React.useState(0);
  const [playing, setPlaying] = React.useState(true);
  const rafRef = React.useRef<number | null>(null);
  const lastTsRef = React.useRef<number | null>(null);

  React.useEffect(() => {
    if (!playing) {
      lastTsRef.current = null;
      return;
    }
    const step = (ts: number) => {
      if (lastTsRef.current == null) lastTsRef.current = ts;
      const dt = (ts - lastTsRef.current) / 1000;
      lastTsRef.current = ts;
      setTime((t) => {
        const next = t + dt;
        return next >= duration ? next % duration : next;
      });
      rafRef.current = requestAnimationFrame(step);
    };
    rafRef.current = requestAnimationFrame(step);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      lastTsRef.current = null;
    };
  }, [playing, duration]);

  const ctxValue = React.useMemo(() => ({ time, duration, playing }), [time, duration, playing]);

  return (
    <div className="flex flex-col items-center gap-4 w-full">
      <div
        className="relative w-full rounded-2xl overflow-hidden shadow-2xl"
        style={{ aspectRatio: "16 / 9", background: "#07090B", boxShadow: "0 30px 90px rgba(0,0,0,0.55)" }}
      >
        <TimelineContext.Provider value={ctxValue}>{children}</TimelineContext.Provider>
      </div>
      <PlaybackBar time={time} duration={duration} playing={playing} onToggle={() => setPlaying((p) => !p)} />
    </div>
  );
}

function PlaybackBar({
  time,
  duration,
  playing,
  onToggle,
}: {
  time: number;
  duration: number;
  playing: boolean;
  onToggle: () => void;
}) {
  const pct = duration > 0 ? (time / duration) * 100 : 0;
  const fmt = (t: number) => {
    const total = Math.max(0, t);
    const m = Math.floor(total / 60);
    const s = Math.floor(total % 60);
    return `${m}:${String(s).padStart(2, "0")}`;
  };
  return (
    <div className="flex items-center gap-3 w-full max-w-2xl px-4 py-2.5 rounded-xl border" style={{ background: "rgba(13,18,32,0.85)", borderColor: "rgba(255,255,255,0.08)" }}>
      <button
        onClick={onToggle}
        className="w-8 h-8 flex items-center justify-center rounded-lg shrink-0 text-white/90 hover:bg-white/10 transition-colors"
        aria-label={playing ? "Pause" : "Play"}
      >
        {playing ? (
          <svg width="14" height="14" viewBox="0 0 14 14"><rect x="3" y="2" width="3" height="10" fill="currentColor" /><rect x="8" y="2" width="3" height="10" fill="currentColor" /></svg>
        ) : (
          <svg width="14" height="14" viewBox="0 0 14 14"><path d="M3 2l9 5-9 5V2z" fill="currentColor" /></svg>
        )}
      </button>
      <span className="text-xs text-white/50 font-mono tabular-nums w-9 text-right">{fmt(time)}</span>
      <div className="flex-1 h-1.5 rounded-full" style={{ background: "rgba(255,255,255,0.1)" }}>
        <div className="h-full rounded-full" style={{ width: `${pct}%`, background: "linear-gradient(90deg, #2F6FFF, #1FD8F2)" }} />
      </div>
      <span className="text-xs text-white/30 font-mono tabular-nums w-9">{fmt(duration)}</span>
    </div>
  );
}

// ───────────────────────── Brand tokens ─────────────────────────
const C = {
  primary: "#2F6FFF",
  primaryBright: "#5B8AFF",
  primaryDeep: "#0E1B3E",
  secondary: "#1FD8F2",
  white: "#F4F7F5",
  mute: "rgba(244,247,245,0.56)",
  card: "rgba(255,255,255,0.04)",
  line: "rgba(255,255,255,0.09)",
};
const FD = "'Inter', system-ui, sans-serif";

// ───────────────────────── Motion helpers ─────────────────────────
function Reveal({
  delay = 0,
  y = 26,
  dur = 0.65,
  ease = Easing.easeOutCubic,
  children,
  style = {},
}: {
  delay?: number;
  y?: number;
  dur?: number;
  ease?: (t: number) => number;
  children: React.ReactNode;
  style?: React.CSSProperties;
}) {
  const { localTime, duration } = useSprite();
  const lt = localTime - delay;
  const inE = ease(clamp(lt / dur, 0, 1));
  const outE = Easing.easeInCubic(clamp((duration - localTime) / 0.5, 0, 1));
  const op = clamp(inE, 0, 1) * outE;
  const ty = (1 - inE) * y;
  return (
    <div style={{ opacity: op, transform: `translate3d(0,${ty.toFixed(2)}px,0)`, willChange: "transform,opacity", ...style }}>
      {children}
    </div>
  );
}

function Kicker({ children, delay = 0 }: { children: React.ReactNode; delay?: number }) {
  return (
    <Reveal delay={delay} y={14} dur={0.5}>
      <div className="inline-flex items-center gap-2.5" style={{ fontFamily: "ui-monospace, monospace", fontSize: "0.8rem", letterSpacing: "0.28em", textTransform: "uppercase", color: C.secondary }}>
        <span style={{ width: 22, height: 2, background: C.secondary, display: "inline-block" }} />
        {children}
      </div>
    </Reveal>
  );
}

function SceneFrame({ children }: { children: React.ReactNode }) {
  const { progress } = useSprite();
  const sc = 1 + 0.02 * Easing.easeOutQuad(clamp(progress, 0, 1));
  return (
    <div className="absolute inset-0 flex flex-col items-center justify-center text-center px-[6%]" style={{ transform: `scale(${sc})`, transformOrigin: "center" }}>
      {children}
    </div>
  );
}

function Chip({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border px-3.5 py-2 text-[0.78rem] font-semibold whitespace-nowrap" style={{ background: C.card, borderColor: C.line, color: C.white, fontFamily: FD }}>
      <span className="w-1.5 h-1.5 rounded-full" style={{ background: C.primary }} />
      {label}
    </div>
  );
}

// ───────────────────────── Ambient backdrop ─────────────────────────
const PARTICLES = Array.from({ length: 24 }, (_, i) => ({
  x: (i * 137.5) % 100,
  y: (i * 71.3) % 100,
  s: 1.2 + ((i * 53) % 30) / 12,
  ph: (i % 7) / 7,
}));
function Backdrop() {
  const t = useTime();
  const offset = (t * 30) % 64;
  return (
    <div className="absolute inset-0 overflow-hidden" style={{ background: `radial-gradient(120% 80% at 50% -10%, ${C.primary}1c 0%, transparent 45%), #07090B` }}>
      <div
        className="absolute left-[-60%] right-[-60%] bottom-[-22%] h-[70%]"
        style={{
          transform: "perspective(620px) rotateX(73deg)",
          transformOrigin: "bottom center",
          backgroundImage: `linear-gradient(to right, ${C.primary}3a 1px, transparent 1px), linear-gradient(to bottom, ${C.primary}3a 1px, transparent 1px)`,
          backgroundSize: "64px 64px",
          backgroundPositionY: `${offset}px`,
          WebkitMaskImage: "linear-gradient(to top, #000 0%, #000 26%, transparent 72%)",
          maskImage: "linear-gradient(to top, #000 0%, #000 26%, transparent 72%)",
        }}
      />
      {PARTICLES.map((p, i) => {
        const tw = 0.25 + 0.55 * (0.5 + 0.5 * Math.sin(t * 1.3 + p.ph * 6.28));
        const yy = (p.y - (t * 5 + p.ph * 30)) % 100;
        return (
          <div
            key={i}
            className="absolute rounded-full"
            style={{ left: `${p.x}%`, top: `${(yy + 100) % 100}%`, width: p.s, height: p.s, background: i % 3 === 0 ? C.secondary : C.primary, opacity: tw * 0.55 }}
          />
        );
      })}
      <div className="absolute inset-0" style={{ boxShadow: "inset 0 0 200px rgba(0,0,0,0.8)" }} />
    </div>
  );
}

// ───────────────────────── Scenes ─────────────────────────
function SceneLogo() {
  return (
    <Sprite start={0} end={6}>
      <SceneFrame>
        <div className="flex flex-col items-center gap-5">
          <Kicker delay={0.3}>Enktel IPTV · Now Launching</Kicker>
          <Reveal delay={0.8} y={28} dur={0.75} ease={Easing.easeOutBack}>
            <div className="flex items-center gap-3">
              <Image src="/logo-icon.png" alt="" width={56} height={56} className="w-12 h-12 md:w-14 md:h-14" />
              <span className="font-black tracking-tight text-3xl md:text-4xl text-white" style={{ fontFamily: FD }}>EnkTel</span>
              <span className="font-black text-lg md:text-xl rounded-full px-3 py-1" style={{ fontFamily: FD, color: "#0A1A2B", background: C.secondary }}>IPTV</span>
            </div>
          </Reveal>
          <Reveal delay={1.6} y={18} dur={0.6}>
            <div className="font-black tracking-tight leading-none text-2xl md:text-4xl text-white" style={{ fontFamily: FD }}>
              STREAM <span style={{ color: C.secondary }}>BEYOND LIMITS</span>
            </div>
          </Reveal>
          <Reveal delay={2.2} y={14} dur={0.5}>
            <div className="text-sm md:text-base" style={{ color: C.mute, fontFamily: FD }}>
              The all-new <span className="text-white font-semibold">enktel.tv</span> is here.
            </div>
          </Reveal>
        </div>
      </SceneFrame>
    </Sprite>
  );
}

function SceneScale() {
  const stats = [
    { label: CHANNEL_COUNT_LABEL, sub: "LIVE CHANNELS" },
    { label: "50+", sub: "COUNTRIES" },
    { label: "4K", sub: "ULTRA HD" },
  ];
  const chips = ["HRT", "Nova TV", "RTL Hrvatska", "Doma TV", "Arena Sport", "Sky Sports", "BBC"];
  return (
    <Sprite start={5.8} end={12}>
      <SceneFrame>
        <div className="flex flex-col items-center gap-7">
          <Reveal delay={0.2} y={24} dur={0.6}>
            <div className="font-black tracking-tight leading-tight text-2xl md:text-4xl" style={{ fontFamily: FD }}>
              <span className="text-white">WATCH CROATIAN TV.</span>
              <br />
              <span style={{ color: C.secondary }}>WATCH THE WORLD.</span>
            </div>
          </Reveal>
          <div className="flex gap-8 md:gap-14">
            {stats.map((s, i) => (
              <Reveal key={i} delay={0.9 + i * 0.15} y={18} dur={0.55}>
                <div className="text-center">
                  <div className="font-black text-3xl md:text-5xl text-white tracking-tight" style={{ fontFamily: FD }}>{s.label}</div>
                  <div className="text-[0.65rem] md:text-xs mt-1.5 tracking-[0.2em]" style={{ color: C.mute, fontFamily: "ui-monospace, monospace" }}>{s.sub}</div>
                </div>
              </Reveal>
            ))}
          </div>
          <Reveal delay={1.8} y={14} dur={0.5}>
            <div className="flex gap-2 flex-wrap justify-center max-w-md md:max-w-2xl">
              {chips.map((c) => <Chip key={c} label={c} />)}
            </div>
          </Reveal>
        </div>
      </SceneFrame>
    </Sprite>
  );
}

function SceneSports() {
  const flags = ["🇭🇷", "🇬🇧", "🇺🇸", "🇧🇦", "🇷🇸"];
  return (
    <Sprite start={11.8} end={18.2}>
      <SceneFrame>
        <div className="flex flex-col items-center gap-6">
          <Kicker delay={0.3}>Live Sports & PPV</Kicker>
          <Reveal delay={0.55} y={30} dur={0.7} ease={Easing.easeOutBack}>
            <div className="font-black tracking-tight leading-[0.95] text-3xl md:text-5xl text-white" style={{ fontFamily: FD }}>
              FOOTBALL. UFC. <span style={{ color: C.secondary }}>BOXING.</span>
            </div>
          </Reveal>
          <Reveal delay={1.2} y={16} dur={0.55}>
            <div className="font-bold text-base md:text-xl" style={{ fontFamily: FD, color: C.white }}>
              Every big match. <span style={{ color: C.primaryBright }}>Live, in 4K.</span>
            </div>
          </Reveal>
          <Reveal delay={1.7} y={14} dur={0.55}>
            <div className="flex gap-3">
              {flags.map((f, i) => (
                <div key={i} className="w-12 h-12 md:w-14 md:h-14 rounded-xl flex items-center justify-center text-2xl border" style={{ background: C.card, borderColor: C.line }}>{f}</div>
              ))}
            </div>
          </Reveal>
        </div>
      </SceneFrame>
    </Sprite>
  );
}

function SceneFeatures() {
  const feats = ["Member Portal", "Live EPG", "Latest Releases", "Coming Soon", "Live Sports & PPV", "Setup Guides", "Help & FAQ", "24/7 Support"];
  return (
    <Sprite start={18} end={26}>
      <SceneFrame>
        <div className="flex flex-col items-center gap-6 max-w-2xl">
          <Kicker delay={0.2}>Introducing the new enktel.tv</Kicker>
          <Reveal delay={0.5} y={24} dur={0.6}>
            <div className="font-black tracking-tight text-2xl md:text-4xl text-white" style={{ fontFamily: FD }}>
              Your whole world, <span style={{ color: C.secondary }}>one portal.</span>
            </div>
          </Reveal>
          <div className="grid grid-cols-4 gap-2 md:gap-3">
            {feats.map((f, i) => (
              <Reveal key={f} delay={0.9 + i * 0.1} y={16} dur={0.45}>
                <div className="rounded-lg border px-2.5 py-2.5 text-[0.65rem] md:text-xs font-semibold text-center" style={{ background: C.card, borderColor: C.line, color: C.white, fontFamily: FD }}>{f}</div>
              </Reveal>
            ))}
          </div>
        </div>
      </SceneFrame>
    </Sprite>
  );
}

function SceneWhy() {
  const pillars = [
    { big: "99.9%", label: "UPTIME" },
    { big: "60s", label: "TO SET UP" },
    { big: "24/7", label: "SUPPORT" },
  ];
  return (
    <Sprite start={25.8} end={31}>
      <SceneFrame>
        <div className="flex flex-col items-center gap-7">
          <Reveal delay={0.2} y={24} dur={0.6}>
            <div className="font-black tracking-tight leading-tight text-xl md:text-3xl text-white" style={{ fontFamily: FD }}>
              LAG-FREE. CRYSTAL CLEAR. <span style={{ color: C.secondary }}>ALWAYS ON.</span>
            </div>
          </Reveal>
          <div className="flex gap-8 md:gap-16">
            {pillars.map((p, i) => (
              <Reveal key={p.label} delay={0.8 + i * 0.15} y={18} dur={0.55}>
                <div className="text-center">
                  <div className="font-black text-3xl md:text-5xl tracking-tight" style={{ fontFamily: FD, color: C.secondary }}>{p.big}</div>
                  <div className="text-[0.65rem] md:text-xs mt-1.5 tracking-[0.18em]" style={{ color: C.white, fontFamily: "ui-monospace, monospace" }}>{p.label}</div>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </SceneFrame>
    </Sprite>
  );
}

function ScenePricing() {
  const annualSavings = (PLAN_REGULAR_PRICE_EUR.annual ?? PLAN_PRICE_EUR.annual) - PLAN_PRICE_EUR.annual;
  return (
    <Sprite start={30.8} end={38}>
      <SceneFrame>
        <div className="flex flex-col items-center gap-6">
          <Kicker delay={0.2}>Simple, Transparent Pricing</Kicker>
          <Reveal delay={0.45} y={22} dur={0.6}>
            <div className="font-black tracking-tight text-2xl md:text-4xl text-white" style={{ fontFamily: FD }}>
              Stream beyond limits — <span style={{ color: C.secondary }}>for less.</span>
            </div>
          </Reveal>
          <div className="flex gap-4 md:gap-7">
            <Reveal delay={0.85} y={26} dur={0.6} ease={Easing.easeOutBack}>
              <div className="rounded-2xl border px-6 py-6 text-left w-44 md:w-56" style={{ background: C.card, borderColor: C.line }}>
                <div className="text-[0.65rem] tracking-[0.2em]" style={{ color: C.mute, fontFamily: "ui-monospace, monospace" }}>3 MONTHS</div>
                <div className="font-black text-3xl md:text-4xl text-white mt-1.5" style={{ fontFamily: FD }}>&euro;{PLAN_PRICE_EUR.quarter}</div>
              </div>
            </Reveal>
            <Reveal delay={1.05} y={26} dur={0.6} ease={Easing.easeOutBack}>
              <div className="relative rounded-2xl border-2 px-6 py-6 text-left w-44 md:w-56" style={{ background: C.primaryDeep, borderColor: C.secondary, boxShadow: `0 0 40px ${C.secondary}33` }}>
                <div className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full px-3 py-1 text-[0.6rem] font-bold tracking-wider whitespace-nowrap" style={{ background: C.secondary, color: "#06122B" }}>BEST VALUE</div>
                <div className="text-[0.65rem] tracking-[0.2em]" style={{ color: C.primaryBright, fontFamily: "ui-monospace, monospace" }}>12 MONTHS</div>
                <div className="font-black text-3xl md:text-4xl text-white mt-1.5" style={{ fontFamily: FD }}>&euro;{PLAN_PRICE_EUR.annual}</div>
                <div className="text-[0.65rem] mt-0.5 font-semibold" style={{ color: "#7ef0a8" }}>Save &euro;{annualSavings}</div>
              </div>
            </Reveal>
          </div>
          <Reveal delay={1.5} y={14} dur={0.5}>
            <div className="text-xs md:text-sm" style={{ color: C.mute, fontFamily: FD }}>No contract · Cancel anytime</div>
          </Reveal>
        </div>
      </SceneFrame>
    </Sprite>
  );
}

function SceneCTA() {
  return (
    <Sprite start={37.8} end={46}>
      <SceneFrame>
        <div className="flex flex-col items-center gap-5">
          <Reveal delay={0.2} y={26} dur={0.7} ease={Easing.easeOutBack}>
            <div className="flex items-center gap-2.5">
              <Image src="/logo-icon.png" alt="" width={44} height={44} className="w-9 h-9 md:w-11 md:h-11" />
              <span className="font-black tracking-tight text-2xl md:text-3xl text-white" style={{ fontFamily: FD }}>EnkTel</span>
              <span className="font-black text-base md:text-lg rounded-full px-2.5 py-0.5" style={{ fontFamily: FD, color: "#0A1A2B", background: C.secondary }}>IPTV</span>
            </div>
          </Reveal>
          <Reveal delay={1.0} y={18} dur={0.6}>
            <div className="font-black text-xl md:text-3xl tracking-tight" style={{ fontFamily: FD, color: C.secondary }}>STREAM BEYOND LIMITS</div>
          </Reveal>
          <Reveal delay={1.5} y={16} dur={0.6}>
            <div className="font-black tracking-tight text-2xl md:text-4xl text-white" style={{ fontFamily: FD }}>
              Visit <span style={{ color: C.secondary }}>enktel.tv</span>
            </div>
          </Reveal>
          <Reveal delay={2.1} y={14} dur={0.55} ease={Easing.easeOutBack}>
            <Link
              href="/checkout?plan=annual"
              className="inline-flex items-center gap-2.5 font-bold text-sm md:text-base rounded-xl px-6 py-3 transition-transform hover:-translate-y-0.5"
              style={{ fontFamily: FD, color: "#06122B", background: C.secondary }}
            >
              Start Watching →
            </Link>
          </Reveal>
        </div>
      </SceneFrame>
    </Sprite>
  );
}

const DURATION = 46;

export default function PromoPlayer() {
  return (
    <Stage duration={DURATION}>
      <Backdrop />
      <SceneLogo />
      <SceneScale />
      <SceneSports />
      <SceneFeatures />
      <SceneWhy />
      <ScenePricing />
      <SceneCTA />
    </Stage>
  );
}
