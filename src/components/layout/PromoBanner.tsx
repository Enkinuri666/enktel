"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { PLANS, formatPrice, TRIAL_HOURS } from "@/lib/pricing";

const DISMISS_KEY = "enktel.promo.finals.dismissed";

/**
 * The seasonal promotion strip.
 *
 * Sits above the ticker because it is the one thing on the page with a
 * deadline attached; everything else can be scrolled to.
 *
 * Dismissible, and the dismissal sticks. A promo bar that reappears on every
 * navigation stops being urgent and starts being furniture — people learn to
 * look past that whole strip, including the next thing put in it.
 *
 * The price is read from the plan rather than written into the sentence, so
 * this cannot advertise a figure the checkout disagrees with.
 */
export default function PromoBanner() {
  const [hidden, setHidden] = useState(true);

  // Rendered hidden until the stored flag is read, so a viewer who dismissed
  // it does not get a flash of the bar on every page load.
  useEffect(() => {
    try {
      setHidden(window.localStorage.getItem(DISMISS_KEY) === "1");
    } catch {
      setHidden(false);
    }
  }, []);

  if (hidden) return null;

  const starter = PLANS[0];

  return (
    <div className="relative bg-gradient-to-r from-brand-accent via-brand-primary to-brand-secondary text-white">
      <div className="mx-auto max-w-7xl px-4 py-2 pr-10 text-center text-xs sm:text-sm font-semibold">
        🚨 <span className="font-black uppercase tracking-wide">September Finals Promo</span>{" "}
        — don&apos;t miss a minute of the AFL &amp; NRL finals. A 1-Month Enktel Pass
        is {formatPrice(starter.price)}, and every UFC PPV is included.{" "}
        <Link href="/trial" className="underline underline-offset-2 font-black hover:opacity-80">
          Start your free {TRIAL_HOURS}-hour trial
        </Link>
      </div>
      <button
        type="button"
        aria-label="Dismiss promotion"
        onClick={() => {
          setHidden(true);
          try {
            window.localStorage.setItem(DISMISS_KEY, "1");
          } catch {
            /* a viewer with storage blocked simply sees it again next visit */
          }
        }}
        className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 hover:bg-black/20"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
