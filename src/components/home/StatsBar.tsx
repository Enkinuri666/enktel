import { Tv, Zap, Clock, Globe } from "lucide-react";

const stats = [
  { icon: Tv, value: "10,000+", label: "Live Channels" },
  { icon: Zap, value: "4K Ultra HD", label: "Stream Quality" },
  { icon: Clock, value: "99.9%", label: "Uptime Guarantee" },
  { icon: Globe, value: "150+", label: "Countries Covered" },
];

export default function StatsBar() {
  return (
    <section className="bg-brand-card border-y border-brand-border py-6">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-6">
          {stats.map((stat, i) => (
            <div key={stat.label} className="flex items-center gap-3">
              {i > 0 && (
                <div className="hidden lg:block w-px h-10 bg-brand-border" />
              )}
              <div className="flex items-center gap-3 flex-1">
                <div className="w-10 h-10 rounded-lg bg-brand-primary/10 flex items-center justify-center shrink-0">
                  <stat.icon className="w-5 h-5 text-brand-primary" />
                </div>
                <div>
                  <div className="text-white font-bold text-lg leading-none">{stat.value}</div>
                  <div className="text-brand-muted text-xs mt-0.5">{stat.label}</div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
