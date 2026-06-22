export const SUBSCRIPTION_STORAGE_KEY = "enktel_subscription";

export interface StoredSubscription {
  id: string;
  plan: string;
  status: string;
  startDate: string;
  endDate: string;
  username: string;
  password: string;
  m3uUrl: string;
  epgUrl: string;
  isTrial?: boolean;
  device?: string;
}

export const SUBSCRIPTION_CHANGED_EVENT = "enktel:subscription-changed";

export function saveSubscription(sub: StoredSubscription) {
  localStorage.setItem(SUBSCRIPTION_STORAGE_KEY, JSON.stringify(sub));
  window.dispatchEvent(new Event(SUBSCRIPTION_CHANGED_EVENT));
}

export function loadSubscription(): StoredSubscription | null {
  const raw = localStorage.getItem(SUBSCRIPTION_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredSubscription;
  } catch {
    return null;
  }
}

export function clearSubscription() {
  localStorage.removeItem(SUBSCRIPTION_STORAGE_KEY);
  window.dispatchEvent(new Event(SUBSCRIPTION_CHANGED_EVENT));
}
