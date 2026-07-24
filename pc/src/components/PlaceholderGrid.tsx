export default function PlaceholderGrid({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="p-10">
      <div className="rail-header mb-6">
        <span className="text-3xl font-black tracking-tight">{title}</span>
      </div>
      {subtitle && <p className="text-textDim mb-8">{subtitle}</p>}
      <div className="grid grid-cols-6 gap-4">
        {Array.from({ length: 18 }).map((_, i) => (
          <div key={i} className="aspect-[2/3] rounded-lg bg-surfaceHi/70 relative overflow-hidden">
            <div className="absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/5 to-transparent animate-shimmer" />
          </div>
        ))}
      </div>
    </div>
  );
}
