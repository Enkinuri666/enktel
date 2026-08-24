// Single source of truth for plan pricing - the actual numbers charged via
// PayPal (src/app/api/checkout-session/route.ts -> src/lib/reseller.ts) and
// the numbers shown on the pricing/checkout pages must always agree, so
// every page imports from here instead of hardcoding its own copy.
export type PlanId = "monthly" | "quarter" | "annual";

/**
 * The currency every plan is priced and charged in.
 *
 * This has to be one constant rather than a literal in each place, because
 * three separate spots have to agree and only one of them fails loudly:
 *
 *  - the order body sent to PayPal (`src/lib/paypal.ts`)
 *  - the `currency` query param on the PayPal JS SDK script tag
 *    (`src/app/checkout/page.tsx`)
 *  - every price rendered on a page
 *
 * If the SDK is loaded for one currency and the order is created in another,
 * PayPal refuses to render the buttons and the checkout page shows an empty
 * box — no error the user can act on. If a *display* disagrees with the order,
 * nothing breaks at all: the customer is simply charged a different number
 * from the one they agreed to, which is worse.
 */
export const PLAN_CURRENCY = "USD";

/** Prefix for rendered prices. Kept next to the code so they cannot drift. */
export const PLAN_CURRENCY_SYMBOL = "$";

// Keyed loosely by `string` (not just `PlanId`) so routes that read an
// unchecked `plan` value from a request body can safely index these without
// a cast, while still being populated with exactly the three known plans.
export const PLAN_PRICE: Record<string, number> = {
  monthly: 29.99,
  quarter: 59,
  annual: 99,
};

export const PLAN_REGULAR_PRICE: Partial<Record<string, number>> = {
  annual: 189,
};

export const PLAN_DURATION_LABEL: Record<string, string> = {
  monthly: "1 month",
  quarter: "3 months",
  annual: "12 months",
};

/** `$99`, `$29.99` — one place decides how a price is written. */
export function formatPrice(amount: number): string {
  const body = Number.isInteger(amount) ? String(amount) : amount.toFixed(2);
  return `${PLAN_CURRENCY_SYMBOL}${body}`;
}
