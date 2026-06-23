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
    </div>
  );
}
