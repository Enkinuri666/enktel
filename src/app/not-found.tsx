import Link from "next/link";
import { Tv, Home } from "lucide-react";
import Button from "@/components/ui/Button";

export default function NotFound() {
  return (
    <div className="max-w-lg mx-auto px-4 py-24 text-center">
      <div className="w-20 h-20 bg-brand-primary/10 border border-brand-primary/30 rounded-full flex items-center justify-center mx-auto mb-6">
        <Tv className="w-10 h-10 text-brand-primary" />
      </div>
      <h1 className="text-5xl font-black text-white mb-3">404</h1>
      <h2 className="text-xl font-bold text-white mb-3">This channel doesn&apos;t exist</h2>
      <p className="text-brand-muted mb-8">
        The page you&apos;re looking for has been moved, deleted, or never aired. Let&apos;s get you back to
        live TV.
      </p>
      <Link href="/">
        <Button size="lg">
          <Home className="w-4 h-4 mr-2" /> Back to Home
        </Button>
      </Link>
    </div>
  );
}
