import Image from "next/image";

export default function LoginMockup() {
  return (
    <div className="absolute inset-0 flex items-center justify-center bg-[radial-gradient(60%_80%_at_50%_0%,rgba(108,99,255,0.18),transparent_60%)] p-4 sm:p-6">
      <div className="w-full max-w-[280px] bg-[#0D1220] border border-brand-border rounded-xl p-5 shadow-xl">
        <div className="flex flex-col items-center mb-4">
          <Image src="/logo-icon.png" alt="" width={32} height={32} className="w-8 h-8 mb-2" />
          <p className="text-white text-sm font-bold">Sign in to Enktel</p>
          <p className="text-brand-muted text-[10px] mt-0.5 text-center leading-relaxed">
            Use your existing IPTV username &amp; password
          </p>
        </div>
        <div className="space-y-2.5">
          <div className="bg-brand-bg border border-brand-border rounded-md px-3 py-2 text-[11px] text-brand-muted">Username</div>
          <div className="bg-brand-bg border border-brand-border rounded-md px-3 py-2 text-[11px] text-brand-muted">••••••••••</div>
          <div className="bg-gradient-to-r from-brand-primary to-[#5348d4] rounded-md px-3 py-2 text-[11px] font-bold text-white text-center">
            Sign In
          </div>
        </div>
        <p className="text-brand-muted/60 text-[9px] text-center mt-3">
          Same login you use on Smart TV &amp; Firestick
        </p>
      </div>
    </div>
  );
}
