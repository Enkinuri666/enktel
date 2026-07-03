"use client";
import { motion } from "framer-motion";
import BrowserFrame from "@/components/webplayer/BrowserFrame";
import LoginMockup from "@/components/webplayer/mockups/LoginMockup";
import GuideMockup from "@/components/webplayer/mockups/GuideMockup";
import PlayerMockup from "@/components/webplayer/mockups/PlayerMockup";
import ChannelsMockup from "@/components/webplayer/mockups/ChannelsMockup";

const pages = [
  {
    path: "/login",
    title: "1. Sign In",
    description: "Land on watch.enktel.tv and sign in with the exact same username and password from your Enktel IPTV subscription — no separate account to create.",
    Mockup: LoginMockup,
  },
  {
    path: "/guide",
    title: "2. Live TV Guide",
    description: "See what's on now and next across every channel in a familiar EPG grid, with a live timeline so you never miss the start of a show.",
    Mockup: GuideMockup,
  },
  {
    path: "/channels",
    title: "3. Browse Channels",
    description: "Filter channels by category — Croatian & Balkan, Sports, Movies, UK & International — and jump straight to the one you want.",
    Mockup: ChannelsMockup,
  },
  {
    path: "/watch/hrt-1",
    title: "4. Watch Live",
    description: "Click any channel or guide entry to start streaming instantly, with playback controls and a quick-switch channel list right in the player.",
    Mockup: PlayerMockup,
  },
];

export default function WebPlayerScreenshots() {
  return (
    <section className="py-16 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-14">
          <motion.p initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-brand-primary text-sm font-bold uppercase tracking-widest mb-3">
            A Look Inside
          </motion.p>
          <motion.h2 initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="text-3xl sm:text-5xl font-black text-white mb-4">
            Every Screen,{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">Walked Through</span>
          </motion.h2>
          <motion.p initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.1 }} className="text-brand-muted text-lg max-w-2xl mx-auto">
            Illustrative previews of the watch.enktel.tv experience, from sign-in to live playback.
          </motion.p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {pages.map((page, i) => (
            <motion.div
              key={page.path}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.08 }}
            >
              <BrowserFrame path={page.path}>
                <page.Mockup />
              </BrowserFrame>
              <div className="mt-4 px-1">
                <h3 className="text-white font-bold text-base mb-1.5">{page.title}</h3>
                <p className="text-brand-muted text-sm leading-relaxed">{page.description}</p>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
