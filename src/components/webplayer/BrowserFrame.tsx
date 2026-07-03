import { Lock } from "lucide-react";

export default function BrowserFrame({
  path,
  children,
}: {
  path: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-xl overflow-hidden border border-brand-border bg-[#07090F] shadow-2xl shadow-black/40">
      <div className="flex items-center gap-3 px-3.5 py-2.5 bg-[#11151F] border-b border-brand-border">
        <div className="flex items-center gap-1.5 shrink-0">
          <span className="w-2.5 h-2.5 rounded-full bg-[#FF5F57]" />
          <span className="w-2.5 h-2.5 rounded-full bg-[#FEBC2E]" />
          <span className="w-2.5 h-2.5 rounded-full bg-[#28C840]" />
        </div>
        <div className="flex-1 flex items-center gap-1.5 bg-[#060910] border border-white/5 rounded-md px-2.5 py-1 text-[11px] text-brand-muted min-w-0">
          <Lock className="w-3 h-3 shrink-0 text-green-500/80" />
          <span className="truncate font-mono">watch.enktel.tv{path}</span>
        </div>
      </div>
      <div className="aspect-[16/10] bg-[#0A0E17] relative overflow-hidden">{children}</div>
    </div>
  );
}
