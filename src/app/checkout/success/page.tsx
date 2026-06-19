"use client";
import { useEffect, useState, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { Check, AlertTriangle } from "lucide-react";
import Spinner from "@/components/ui/Spinner";

interface SubscriptionResult {
  id: string;
  plan: string;
  username: string;
  password: string;
  m3uUrl: string;
  epgUrl: string;
  panelSync: boolean;
}

function SuccessContent() {
  const searchParams = useSearchParams();
  const sessionId = searchParams.get("session_id");

  const [subscription, setSubscription] = useState<SubscriptionResult | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!sessionId) {
      setError("Missing checkout session.");
      setLoading(false);
      return;
    }

    fetch(`/api/checkout-session?session_id=${encodeURIComponent(sessionId)}`)
      .then((res) => res.json().then((data) => ({ ok: res.ok, data })))
      .then(({ ok, data }) => {
        if (!ok) throw new Error(data.error || "Could not confirm payment");
        setSubscription(data.subscription);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Something went wrong"))
      .finally(() => setLoading(false));
  }, [sessionId]);

  if (loading) {
    return (
      <div className="max-w-lg mx-auto px-4 py-20 text-center">
        <Spinner className="py-12" />
        <p className="text-brand-muted mt-4">Confirming your payment and activating your subscription...</p>
      </div>
    );
  }

  if (error || !subscription) {
    return (
      <div className="max-w-lg mx-auto px-4 py-20 text-center">
        <div className="w-20 h-20 bg-red-500/20 border border-red-500/40 rounded-full flex items-center justify-center mx-auto mb-6">
          <AlertTriangle className="w-10 h-10 text-red-400" />
        </div>
        <h1 className="text-3xl font-bold text-white mb-3">Something Went Wrong</h1>
        <p className="text-brand-muted">{error || "We couldn't confirm your subscription."}</p>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto px-4 py-20 text-center">
      <div className="w-20 h-20 bg-green-500/20 border border-green-500/40 rounded-full flex items-center justify-center mx-auto mb-6">
        <Check className="w-10 h-10 text-green-400" />
      </div>
      <h1 className="text-3xl font-bold text-white mb-3">Payment Successful!</h1>
      <p className="text-brand-muted mb-8">
        Welcome to Enktel IPTV! Your subscription is now live. Here are your credentials:
      </p>
      <div className="bg-brand-card border border-brand-border rounded-xl p-5 text-left space-y-4 mb-8">
        <div>
          <p className="text-brand-muted text-xs mb-1">Subscription ID</p>
          <p className="text-white font-mono text-sm">{subscription.id}</p>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <p className="text-brand-muted text-xs mb-1">Username</p>
            <p className="text-white font-mono text-sm font-bold">{subscription.username}</p>
          </div>
          <div>
            <p className="text-brand-muted text-xs mb-1">Password</p>
            <p className="text-white font-mono text-sm font-bold">{subscription.password}</p>
          </div>
        </div>
        <div>
          <p className="text-brand-muted text-xs mb-1">M3U Playlist URL</p>
          <p className="text-brand-primary font-mono text-xs break-all">{subscription.m3uUrl}</p>
        </div>
        <div>
          <p className="text-brand-muted text-xs mb-1">EPG / XML TV URL</p>
          <p className="text-brand-primary font-mono text-xs break-all">{subscription.epgUrl}</p>
        </div>
      </div>
      <p className="text-brand-muted text-sm">
        Visit your{" "}
        <a href="/dashboard" className="text-brand-primary hover:underline">dashboard</a>{" "}
        to manage your subscription and access setup guides.
      </p>
    </div>
  );
}

export default function CheckoutSuccessPage() {
  return (
    <Suspense fallback={<Spinner className="py-20" />}>
      <SuccessContent />
    </Suspense>
  );
}
