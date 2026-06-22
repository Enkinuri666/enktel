// Ambient motion backdrop for the Hero — a CSS-only loop (perspective channel
// grid + drifting particles + a slow light sweep) standing in for a literal
// background video. Pure CSS keyframes (no rAF/state) so it costs nothing on
// the main thread and never competes with the foreground hero copy for focus.

const PARTICLES = Array.from({ length: 22 }, (_, i) => ({
  x: (i * 137.5) % 100,
  delay: -((i * 53) % 90) / 10, // negative delay staggers entry into an already-running loop
  duration: 6 + ((i * 71.3) % 50) / 10,
  size: 1.5 + ((i * 53) % 30) / 10,
  accent: i % 3 === 0,
}));

export default function HeroVideoBackdrop() {
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none select-none">
      {/* Aurora orbs */}
      <div
        className="absolute -top-40 -left-40 w-[1000px] h-[1000px] rounded-full orb"
        style={{ background: "radial-gradient(circle, rgba(47,111,255,0.38) 0%, transparent 65%)" }}
      />
      <div
        className="absolute -bottom-56 -right-56 w-[900px] h-[900px] rounded-full"
        style={{ background: "radial-gradient(circle, rgba(31,216,242,0.28) 0%, transparent 65%)", animation: "orbFloat 12s ease-in-out infinite reverse" }}
      />
      <div
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] rounded-full"
        style={{ background: "radial-gradient(circle, rgba(206,44,26,0.12) 0%, transparent 65%)", animation: "orbFloat 9s ease-in-out infinite 2s" }}
      />

      {/* Perspective channel-grid floor */}
      <div
        className="absolute left-[-60%] right-[-60%] bottom-[-22%] h-[60%] hero-grid"
        style={{
          transform: "perspective(620px) rotateX(73deg)",
          transformOrigin: "bottom center",
          backgroundImage:
            "linear-gradient(to right, rgba(47,111,255,0.22) 1px, transparent 1px), linear-gradient(to bottom, rgba(47,111,255,0.22) 1px, transparent 1px)",
          backgroundSize: "78px 78px",
          WebkitMaskImage: "linear-gradient(to top, #000 0%, #000 26%, transparent 72%)",
          maskImage: "linear-gradient(to top, #000 0%, #000 26%, transparent 72%)",
        }}
      />
      <div
        className="absolute left-1/5 right-1/5 bottom-[28%] h-44"
        style={{ background: "radial-gradient(60% 100% at 50% 100%, rgba(47,111,255,0.22) 0%, transparent 70%)", filter: "blur(8px)" }}
      />

      {/* Drifting particles */}
      {PARTICLES.map((p, i) => (
        <div
          key={i}
          className="absolute bottom-0 rounded-full hero-particle"
          style={{
            left: `${p.x}%`,
            width: p.size,
            height: p.size,
            background: p.accent ? "#1FD8F2" : "#2F6FFF",
            animationDuration: `${p.duration}s`,
            animationDelay: `${p.delay}s`,
          }}
        />
      ))}

      {/* Slow diagonal light sweep — broadcast-glint touch */}
      <div
        className="absolute -inset-y-1/2 left-0 w-1/3 hero-sweep"
        style={{ background: "linear-gradient(75deg, transparent 0%, rgba(255,255,255,0.5) 50%, transparent 100%)" }}
      />

      <div className="absolute inset-0 dot-grid" />
      <div
        className="absolute bottom-0 left-0 right-0 h-40"
        style={{ background: "linear-gradient(to bottom, transparent, #060910)" }}
      />
    </div>
  );
}
