import type { MetadataRoute } from "next";

const routes = [
  "",
  "/channels",
  "/epg",
  "/whats-new",
  "/world-cup-2026",
  "/search",
  "/latest-releases",
  "/coming-soon",
  "/pricing",
  "/faqs",
  "/setup-guides",
  "/contact",
  "/status",
  "/privacy",
  "/terms",
  "/refund-policy",
  "/cookie-policy",
  "/dmca",
];

export default function sitemap(): MetadataRoute.Sitemap {
  const siteUrl = process.env.NEXT_PUBLIC_SITE_URL || "https://enktel.tv";
  const now = new Date();

  return routes.map((route) => ({
    url: `${siteUrl}${route}`,
    lastModified: now,
    changeFrequency: route === "" ? "daily" : "weekly",
    priority: route === "" ? 1 : 0.6,
  }));
}
