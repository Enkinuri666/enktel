import { Radio } from "lucide-react";

export default function MatchPromoVideo({ className = "" }: { className?: string }) {
  return (
    <div className={`relative overflow-hidden rounded-2xl border border-yellow-500/30 ${className}`}>
      <video
        className="w-full h-full object-cover"
        autoPlay
        muted
        loop
        playsInline
        disablePictureInPicture
        preload="auto"
      >
        <source src="/videos/panama-vs-croatia-promo.mp4" type="video/mp4" />
      </video>
      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/10 to-transparent pointer-events-none" />
      <div className="absolute bottom-0 left-0 right-0 p-4 sm:p-5 flex items-end justify-between gap-3">
        <div>
          <span className="inline-flex items-center gap-1.5 text-yellow-400 text-[10px] font-black uppercase tracking-widest bg-black/40 px-2 py-1 rounded-full mb-2">
            <Radio className="w-3 h-3" /> Group Stage
          </span>
          <h3 className="text-white font-black text-lg sm:text-xl leading-tight drop-shadow">
            Panama 🇵🇦 vs Croatia 🇭🇷
          </h3>
          <p className="text-white/70 text-xs sm:text-sm">Live in 4K Ultra HD on Enktel IPTV</p>
        </div>
      </div>
    </div>
  );
}
