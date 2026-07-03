"use client";
import Image from "next/image";
import { motion } from "framer-motion";
import { Trophy, Goal, Medal, Flag, Shield } from "lucide-react";

// A seasonal reskin of the hero centerpiece: the Enktel wolf-head mark at
// the core, wrapped in a World Cup 2026 broadcast-graphics treatment (pitch
// green + gold, a medal-style orbit ring, and a "2026" badge) instead of the
// generic category-icon animation this replaces.
const ORBIT_ITEMS = [
  { Icon: Trophy, color: "#FACC15" },
  { Icon: Goal, color: "#22C55E" },
  { Icon: Flag, color: "#FACC15" },
  { Icon: Medal, color: "#22C55E" },
  { Icon: Shield, color: "#FACC15" },
];

const CONFETTI = [
  { x: "8%", y: "18%", color: "#FACC15" },
  { x: "88%", y: "12%", color: "#22C55E" },
  { x: "92%", y: "62%", color: "#FACC15" },
  { x: "12%", y: "78%", color: "#22C55E" },
  { x: "50%", y: "2%", color: "#ffffff" },
  { x: "2%", y: "48%", color: "#FACC15" },
];

const ORBIT_RADIUS = 132;
const ORBIT_DURATION = 30;

export default function HeroWolfAnimation() {
  return (
    <div className="relative flex items-center justify-center w-[280px] h-[280px] sm:w-[320px] sm:h-[320px]">
      {/* Pitch-green / gold ambient glow */}
      <div
        className="absolute inset-0 -z-10 rounded-full blur-[40px]"
        style={{ background: "radial-gradient(circle, rgba(34,197,94,0.35) 0%, rgba(250,204,21,0.18) 50%, transparent 75%)" }}
      />

      {/* Sonar pulses, pitch green / gold */}
      {[0, 1, 2].map((i) => (
        <motion.div
          key={i}
          className="absolute rounded-full border"
          style={{
            width: 130,
            height: 130,
            borderColor: i % 2 === 0 ? "rgba(34,197,94,0.45)" : "rgba(250,204,21,0.4)",
          }}
          animate={{ scale: [1, 2.2], opacity: [0.6, 0] }}
          transition={{ duration: 3.4, repeat: Infinity, ease: "easeOut", delay: i * 1.1 }}
        />
      ))}

      {/* Dashed gold "medal ring" orbit path */}
      <div
        className="absolute rounded-full border border-dashed"
        style={{ width: ORBIT_RADIUS * 2, height: ORBIT_RADIUS * 2, borderColor: "rgba(250,204,21,0.3)" }}
      />

      {/* Orbiting World Cup 2026 icons */}
      <motion.div
        className="absolute inset-0"
        animate={{ rotate: 360 }}
        transition={{ duration: ORBIT_DURATION, repeat: Infinity, ease: "linear" }}
      >
        {ORBIT_ITEMS.map(({ Icon, color }, i) => {
          const angle = (360 / ORBIT_ITEMS.length) * i;
          return (
            <div
              key={i}
              className="absolute inset-0 flex items-center justify-center"
              style={{ transform: `rotate(${angle}deg)` }}
            >
              <div style={{ transform: `translateY(-${ORBIT_RADIUS}px)` }}>
                <motion.div
                  animate={{ rotate: -360 }}
                  transition={{ duration: ORBIT_DURATION, repeat: Infinity, ease: "linear" }}
                  className="w-10 h-10 rounded-xl flex items-center justify-center backdrop-blur-xl border"
                  style={{ background: `${color}22`, borderColor: `${color}55`, boxShadow: `0 0 20px ${color}33` }}
                >
                  <Icon className="w-4 h-4" style={{ color }} />
                </motion.div>
              </div>
            </div>
          );
        })}
      </motion.div>

      {/* Drifting confetti */}
      {CONFETTI.map((c, i) => (
        <motion.span
          key={i}
          className="absolute w-1.5 h-1.5 rounded-full"
          style={{ left: c.x, top: c.y, background: c.color }}
          animate={{ y: [0, -14, 0], opacity: [0.2, 0.9, 0.2] }}
          transition={{ duration: 2.4 + i * 0.3, repeat: Infinity, ease: "easeInOut", delay: i * 0.25 }}
        />
      ))}

      {/* Central wolf-head mark, breathing gently inside a gold medal rim */}
      <motion.div
        animate={{ scale: [1, 1.04, 1] }}
        transition={{ duration: 3.6, repeat: Infinity, ease: "easeInOut" }}
        className="relative w-32 h-32 sm:w-36 sm:h-36 rounded-full flex items-center justify-center border-2"
        style={{
          borderColor: "rgba(250,204,21,0.5)",
          boxShadow: "0 0 50px rgba(34,197,94,0.4), 0 0 30px rgba(250,204,21,0.25)",
          background: "radial-gradient(circle at 35% 30%, rgba(255,255,255,0.08), rgba(6,20,12,0.55) 65%)",
        }}
      >
        <Image
          src="/logo-icon.png"
          alt="Enktel wolf"
          width={800}
          height={743}
          className="w-[92%] h-[92%] object-contain drop-shadow-[0_0_18px_rgba(0,212,255,0.45)]"
          priority
        />
      </motion.div>

      {/* World Cup 2026 medallion badge */}
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ delay: 0.6, type: "spring" }}
        className="absolute bottom-1 right-1 sm:bottom-2 sm:right-2 flex items-center gap-1 rounded-full px-2.5 py-1 border backdrop-blur-xl"
        style={{
          background: "linear-gradient(135deg, rgba(34,197,94,0.9), rgba(10,58,30,0.9))",
          borderColor: "rgba(250,204,21,0.6)",
          boxShadow: "0 0 16px rgba(250,204,21,0.35)",
        }}
      >
        <Trophy className="w-3 h-3 text-yellow-300" />
        <span className="text-[10px] font-black text-yellow-300 tracking-wide">2026</span>
      </motion.div>
    </div>
  );
}
