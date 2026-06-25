"use client";
import Link from "next/link";
import { motion } from "framer-motion";
import { KeyRound, MonitorSmartphone, Clock, LifeBuoy, ArrowRight } from "lucide-react";

const points = [
  {
    icon: KeyRound,
    title: "Your Credentials, Always On Hand",
    description: "Username, password, M3U playlist and EPG URLs are all in one place — copy them with a single click whenever you set up a new device.",
  },
  {
    icon: MonitorSmartphone,
    title: "Step-by-Step Setup Guides",
    description: "Pick your device — Firestick, Smart TV, MAG box, mobile, PC or router — and follow a tailored walkthrough to get streaming in minutes.",
  },
  {
    icon: Clock,
    title: "Track & Manage Your Plan",
    description: "See exactly how much time is left on your trial or subscription, and upgrade, renew, or manage your account anytime, right from the dashboard.",
  },
  {
    icon: LifeBuoy,
    title: "Troubleshooting & Support",
    description: "Quick fixes for buffering, login issues, and EPG problems — plus 24/7 WhatsApp support a tap away if you need a hand.",
  },
];

export default function DashboardExplainer() {
  return (
    <section className="py-20 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="text-center mb-14">
          <motion.p initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} viewport={{ once: true }} className="text-brand-secondary text-sm font-bold uppercase tracking-widest mb-3">
            Your Account, Simplified
          </motion.p>
          <motion.h2 initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="text-3xl sm:text-5xl font-black text-white mb-4">
            Everything Lives in Your{" "}
            <span className="bg-gradient-to-r from-brand-primary to-brand-secondary bg-clip-text text-transparent">Dashboard</span>
          </motion.h2>
          <motion.p initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.1 }} className="text-brand-muted text-lg max-w-2xl mx-auto">
            The moment you sign up, your personal dashboard is ready — credentials, setup help, and account management in one simple screen.
          </motion.p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-10">
          {points.map((point, i) => (
            <motion.div
              key={point.title}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.08 }}
              className="bg-brand-card border border-brand-border rounded-2xl p-6 hover:border-brand-secondary/40 transition-all duration-300"
            >
              <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-brand-primary to-brand-secondary p-0.5 mb-4">
                <div className="w-full h-full bg-brand-card rounded-[10px] flex items-center justify-center">
                  <point.icon className="w-6 h-6 text-white" />
                </div>
              </div>
              <h3 className="text-white font-bold text-base mb-2">{point.title}</h3>
              <p className="text-brand-muted text-sm leading-relaxed">{point.description}</p>
            </motion.div>
          ))}
        </div>

        <div className="flex justify-center">
          <Link href="/trial">
            <button className="group flex items-center gap-3 bg-brand-primary hover:bg-purple-600 text-white font-bold px-8 py-4 rounded-xl transition-all duration-300 hover:-translate-y-0.5 hover:shadow-xl hover:shadow-brand-primary/30">
              Get Your Free Trial Dashboard
              <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
            </button>
          </Link>
        </div>
      </div>
    </section>
  );
}
