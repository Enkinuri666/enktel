"use client";
import Link from "next/link";
import { PLANS, formatPrice, perMonth, savingPercent, CURRENCY } from "@/lib/pricing";

/**
 * Adding time to a line that already exists.
 *
 * Deliberately not the pricing cards from the marketing pages. Someone already
 * signed in is not choosing whether to subscribe, they are choosing how long to
 * extend — so this is a compact row of amounts rather than a feature pitch, and
 * every link carries `renew`, which is what keeps their existing username and
 * password instead of issuing a second line.
 *
 * A trial has no line worth extending, so it gets the plain checkout link and
 * is told plainly that the credentials will change.
 */
export default function RenewalPanel({
  username,
  isTrial,
}: {
  username: string;
  isTrial: boolean;
}) {
  return (
    <div className="bg-brand-card border border-brand-border rounded-2xl p-6">
      <h3 className="text-white font-bold text-lg">
        {isTrial ? "Upgrade to a full pass" : "Add more time"}
      </h3>
      <p className="text-brand-muted text-sm mt-1 mb-5">
        {isTrial
          ? "Your trial line stops when it expires. A paid pass provisions a permanent line — the credentials will change, and we email them the moment payment clears."
          : "Extending keeps this same username and password. Nothing on your devices needs changing."}{" "}
        All prices in {CURRENCY}.
      </p>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {PLANS.map((plan) => {
          const saving = savingPercent(plan);
          return (
            <Link
              key={plan.id}
              href={
                isTrial
                  ? `/checkout?plan=${plan.id}`
                  : `/checkout?plan=${plan.id}&renew=${encodeURIComponent(username)}`
              }
              className={`rounded-xl border p-4 transition-colors ${
                plan.highlight
                  ? "border-brand-primary/60 bg-brand-primary/5 hover:border-brand-primary"
                  : "border-brand-border hover:border-brand-primary/50"
              }`}
            >
              <div className="flex items-baseline justify-between">
                <span className="text-white font-semibold text-sm">
                  {plan.months} {plan.months === 1 ? "month" : "months"}
                </span>
                {saving > 0 && (
                  <span className="text-[10px] font-black uppercase tracking-wide text-brand-secondary">
                    −{saving}%
                  </span>
                )}
              </div>
              <div className="mt-2 flex items-baseline gap-1.5">
                <span className="text-brand-muted text-xs line-through">
                  {formatPrice(plan.wasPrice)}
                </span>
                <span className="text-white text-xl font-black">
                  {formatPrice(plan.price)}
                </span>
              </div>
              {plan.months > 1 && (
                <p className="text-brand-muted text-xs mt-1">
                  {formatPrice(perMonth(plan))}/month
                </p>
              )}
            </Link>
          );
        })}
      </div>
    </div>
  );
}
