"use client";
import useSWR from "swr";
import { clsx } from "clsx";
import Card from "@/components/ui/Card";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

interface SourceStatus {
  name: string;
  status: "operational" | "degraded" | "down";
  latencyMs: number;
}

const statusStyles: Record<SourceStatus["status"], { label: string; dot: string; text: string }> = {
  operational: { label: "Operational", dot: "bg-green-400", text: "text-green-400" },
  degraded: { label: "Degraded", dot: "bg-yellow-400", text: "text-yellow-400" },
  down: { label: "Unreachable", dot: "bg-red-400", text: "text-red-400" },
};

export default function DataSourceStatus() {
  const { data, isLoading } = useSWR<{ sources: SourceStatus[]; checkedAt: string }>(
    "/api/health",
    fetcher,
    { refreshInterval: 60000 }
  );

  return (
    <div className="mt-8">
      <h2 className="text-white font-semibold text-sm mb-3">Live Data Sources</h2>
      <Card className="divide-y divide-brand-border">
        {isLoading ? (
          <div className="px-5 py-4 text-brand-muted text-sm">Checking data sources...</div>
        ) : (
          (data?.sources || []).map((s) => {
            const style = statusStyles[s.status];
            return (
              <div key={s.name} className="flex items-center justify-between px-5 py-4">
                <span className="text-white text-sm font-medium">{s.name}</span>
                <div className="flex items-center gap-3">
                  <span className="text-brand-muted text-xs">{s.latencyMs}ms</span>
                  <span className={clsx("flex items-center gap-1.5 text-xs font-semibold", style.text)}>
                    <span className={clsx("w-2 h-2 rounded-full", style.dot)} /> {style.label}
                  </span>
                </div>
              </div>
            );
          })
        )}
      </Card>
      {data?.checkedAt && (
        <p className="text-brand-muted text-xs mt-2">
          Last checked: {new Date(data.checkedAt).toLocaleTimeString()}
        </p>
      )}
    </div>
  );
}
