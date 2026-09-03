"use client";
import Image from "next/image";
import { motion } from "framer-motion";
import { Trophy, Goal, Medal, Flag, Star } from "lucide-react";

// The hero centerpiece: the Enktel wolf-head mark at the core, wrapped in a
// broadcast-graphics treatment in Enktel's own red accent plus navy blue and
// white.
//
// Deliberately not tied to one tournament. This carried a World Cup 2026
// treatment and a "2026" medallion, which is the kind of decoration that is
// wrong the moment the event passes and then quietly stays wrong — the badge
// now says what is true year-round.
const ORBIT_ITEMS = [
  { Icon: Trophy, color: "#FF4757" },
  { Icon: Goal, color: "#3B82F6" },
  { Icon: Flag, color: "#FF4757" },
  { Icon: Medal, color: "#3B82F6" },
  { Icon: Star, color: "#FFFFFF" },
];

const CONFETTI = [
  { x: "8%", y: "18%", color: "#FF4757" },
  { x: "88%", y: "12%", color: "#3B82F6" },
  { x: "92%", y: "62%", color: "#FFFFFF" },
  { x: "12%", y: "78%", color: "#3B82F6" },
  { x: "50%", y: "2%", color: "#ffffff" },
  { x: "2%", y: "48%", color: "#FF4757" },
];

const ORBIT_RADIUS = 132;
const ORBIT_DURATION = 30;

export default function HeroWolfAnimation() {
  return (
    <div className="relative flex items-center justify-center w-[280px] h-[280px] sm:w-[320px] sm:h-[320px]">
      {/* Red / navy ambient glow */}
      <div
        className="absolute inset-0 -z-10 rounded-full blur-[40px]"
        style={{ background: "radial-gradient(circle, rgba(255,71,87,0.32) 0%, rgba(59,130,246,0.22) 50%, transparent 75%)" }}
      />

      {/* Sonar pulses, red / navy blue */}
      {[0, 1, 2].map((i) => (
        <motion.div
          key={i}
          className="absolute rounded-full border"
          style={{
            width: 130,
            height: 130,
            borderColor: i % 2 === 0 ? "rgba(255,71,87,0.45)" : "rgba(59,130,246,0.4)",
          }}
          animate={{ scale: [1, 2.2], opacity: [0.6, 0] }}
          transition={{ duration: 3.4, repeat: Infinity, ease: "easeOut", delay: i * 1.1 }}
        />
      ))}

      {/* Dashed white "medal ring" orbit path */}
      <div
        className="absolute rounded-full border border-dashed"
        style={{ width: ORBIT_RADIUS * 2, height: ORBIT_RADIUS * 2, borderColor: "rgba(255,255,255,0.25)" }}
      />

      {/* Orbiting sport-category icons */}
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

      {/* Central wolf-head mark, breathing gently inside a red/navy medal rim */}
      <motion.div
        animate={{ scale: [1, 1.04, 1] }}
        transition={{ duration: 3.6, repeat: Infinity, ease: "easeInOut" }}
        className="relative w-32 h-32 sm:w-36 sm:h-36 rounded-full flex items-center justify-center border-2"
        style={{
          borderColor: "rgba(255,255,255,0.45)",
          boxShadow: "0 0 50px rgba(59,130,246,0.4), 0 0 30px rgba(255,71,87,0.3)",
          background: "radial-gradient(circle at 35% 30%, rgba(255,255,255,0.08), rgba(8,11,22,0.6) 65%)",
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

      {/* Quality medallion badge */}
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ delay: 0.6, type: "spring" }}
        className="absolute bottom-1 right-1 sm:bottom-2 sm:right-2 flex items-center gap-1 rounded-full px-2.5 py-1 border backdrop-blur-xl"
        style={{
          background: "linear-gradient(135deg, rgba(255,71,87,0.92), rgba(13,31,60,0.92))",
          borderColor: "rgba(255,255,255,0.5)",
          boxShadow: "0 0 16px rgba(59,130,246,0.4)",
        }}
      >
        <Star className="w-3 h-3 text-white" />
        <span className="text-[10px] font-black text-white tracking-wide">4K</span>
      </motion.div>
    </div>
  );
}
