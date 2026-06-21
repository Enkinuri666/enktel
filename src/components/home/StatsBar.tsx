import { Tv, Users, Clock, Globe } from "lucide-react";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const stats = [
  { icon: Tv, value: CHANNEL_COUNT_LABEL, label: "Live Channels", color: "#6C63FF" },
  { icon: Globe, value: "50+", label: "Countries Covered", color: "#00D4FF" },
  { icon: Clock, value: "99.9%", label: "Uptime Guarantee", color: "#FF4757" },
  { icon: Users, value: "50,000+", label: "Happy Customers", color: "#6C63FF" },
];

export default function StatsBar() {
  return (
    <section className="py-8 px-4 sm:px-6 lg:px-8">
      <div className="max-w-7xl mx-auto">
        <div className="cyber-panel rounded-2xl grid grid-cols-2 lg:grid-cols-4 divide-x-0 lg:divide-x divide-y lg:divide-y-0 divide-white/5 overflow-hidden">
          {stats.map((stat) => (
            <div key={stat.label} className="flex items-center gap-4 p-6 relative group">
              <div
                className="icon-glow w-12 h-12 rounded-xl border flex items-center justify-center shrink-0 group-hover:scale-110 transition-transform"
                style={{ backgroundColor: `${stat.color}1a`, borderColor: `${stat.color}33` }}
              >
                <stat.icon className="w-6 h-6" style={{ color: stat.color }} />
              </div>
              <div>
                <div className="text-2xl font-black text-white">{stat.value}</div>
                <div className="text-brand-muted text-xs mt-0.5">{stat.label}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
