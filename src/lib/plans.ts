// Single source of truth for plan pricing - the actual numbers charged via
// Stripe (src/app/api/checkout-session/route.ts -> src/lib/reseller.ts) and
// the numbers shown on the pricing/checkout pages must always agree, so
// every page imports from here instead of hardcoding its own copy.
export type PlanId = "monthly" | "quarter" | "annual";

// Keyed loosely by `string` (not just `PlanId`) so routes that read an
// unchecked `plan` value from a request body can safely index these without
// a cast, while still being populated with exactly the three known plans.
export const PLAN_PRICE_EUR: Record<string, number> = {
  monthly: 19.99,
  quarter: 59,
  annual: 99,
};

export const PLAN_REGULAR_PRICE_EUR: Partial<Record<string, number>> = {
  annual: 189,
};

export const PLAN_DURATION_LABEL: Record<string, string> = {
  monthly: "1 month",
  quarter: "3 months",
  annual: "12 months",
};
