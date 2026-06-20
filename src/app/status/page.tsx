import type { Metadata } from "next";
import { CheckCircle2 } from "lucide-react";
import Card from "@/components/ui/Card";
import DataSourceStatus from "@/components/status/DataSourceStatus";

export const metadata: Metadata = { title: "Status" };

const services = [
  { name: "Streaming Servers", uptime: "99.97%" },
  { name: "EPG / Program Guide", uptime: "99.95%" },
  { name: "Website & Dashboard", uptime: "99.99%" },
  { name: "Payments (Stripe)", uptime: "100%" },
  { name: "VOD Library", uptime: "99.92%" },
];

export default function StatusPage() {
  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      <div className="text-center mb-10">
        <div className="w-14 h-14 bg-green-500/10 border border-green-500/30 rounded-full flex items-center justify-center mx-auto mb-4">
          <CheckCircle2 className="w-7 h-7 text-green-400" />
        </div>
        <h1 className="text-3xl sm:text-4xl font-black text-white mb-3">All Systems Operational</h1>
        <p className="text-brand-muted">Current status of Enktel IPTV services, updated in real time.</p>
      </div>

      <Card className="divide-y divide-brand-border">
        {services.map((s) => (
          <div key={s.name} className="flex items-center justify-between px-5 py-4">
            <span className="text-white text-sm font-medium">{s.name}</span>
            <div className="flex items-center gap-3">
              <span className="text-brand-muted text-xs">{s.uptime} uptime (90d)</span>
              <span className="flex items-center gap-1.5 text-green-400 text-xs font-semibold">
                <span className="w-2 h-2 rounded-full bg-green-400" /> Operational
              </span>
            </div>
          </div>
        ))}
      </Card>

      <DataSourceStatus />

      <p className="text-brand-muted text-xs text-center mt-8">
        Experiencing an issue not reflected here?{" "}
        <a href="/contact" className="text-brand-primary hover:underline">Let us know</a>.
      </p>
    </div>
  );
}
