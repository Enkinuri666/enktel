"use client";
import { motion } from "framer-motion";
import { Tv, Trophy, Film, Radio, Music, Newspaper } from "lucide-react";

// A branded, looping "live broadcast" visual for the hero centerpiece:
// sonar pulses reading as a live signal, category icons orbiting a glass
// core, and an equalizer pulsing inside it — no static logo, no claims,
// just a continuous, on-brand animation.
const ORBIT_ITEMS = [
  { Icon: Tv, color: "#6C63FF" },
  { Icon: Trophy, color: "#FF4757" },
  { Icon: Film, color: "#00D4FF" },
  { Icon: Radio, color: "#6C63FF" },
  { Icon: Music, color: "#FF4757" },
  { Icon: Newspaper, color: "#00D4FF" },
];

const BARS = [0.35, 0.9, 0.55, 1, 0.45];
const ORBIT_RADIUS = 128;
const ORBIT_DURATION = 26;

export default function HeroBroadcastAnimation() {
  return (
    <div className="relative flex items-center justify-center w-[280px] h-[280px] sm:w-[320px] sm:h-[320px]">
      {/* Ambient glow */}
      <div
        className="absolute inset-0 -z-10 rounded-full blur-[36px]"
        style={{ background: "radial-gradient(circle, rgba(0,212,255,0.35) 0%, rgba(108,99,255,0.28) 45%, transparent 75%)" }}
      />

      {/* Sonar pulses */}
      {[0, 1, 2].map((i) => (
        <motion.div
          key={i}
          className="absolute rounded-full border"
          style={{
            width: 120,
            height: 120,
            borderColor: i % 2 === 0 ? "rgba(0,212,255,0.4)" : "rgba(108,99,255,0.4)",
          }}
          animate={{ scale: [1, 2.3], opacity: [0.55, 0] }}
          transition={{ duration: 3.6, repeat: Infinity, ease: "easeOut", delay: i * 1.2 }}
        />
      ))}

      {/* Dashed orbit path */}
      <div
        className="absolute rounded-full border border-dashed border-white/10"
        style={{ width: ORBIT_RADIUS * 2, height: ORBIT_RADIUS * 2 }}
      />

      {/* Orbiting category icons */}
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

      {/* Central glass core with pulsing equalizer */}
      <motion.div
        animate={{ scale: [1, 1.05, 1] }}
        transition={{ duration: 3.2, repeat: Infinity, ease: "easeInOut" }}
        className="relative w-28 h-28 sm:w-32 sm:h-32 rounded-full flex items-center justify-center border border-white/15 backdrop-blur-2xl"
        style={{
          background: "radial-gradient(circle at 35% 30%, rgba(255,255,255,0.12), rgba(13,18,32,0.6) 60%)",
          boxShadow: "0 0 60px rgba(0,212,255,0.35), inset 0 0 30px rgba(108,99,255,0.25)",
        }}
      >
        <div className="flex items-end gap-1.5 h-8">
          {BARS.map((h, i) => (
            <motion.span
              key={i}
              className="w-1.5 rounded-full"
              style={{ background: i % 2 === 0 ? "#00D4FF" : "#6C63FF" }}
              animate={{ height: [`${h * 40}%`, "100%", `${h * 40}%`] }}
              transition={{ duration: 1.1 + i * 0.15, repeat: Infinity, ease: "easeInOut", delay: i * 0.12 }}
            />
          ))}
        </div>
      </motion.div>
    </div>
  );
}
