import type { MetadataRoute } from "next";

const routes = [
  "",
  "/blog",
  "/epg",
  "/updates",
  "/web-player",
  "/player",
  "/whats-new",
  "/search",
  "/latest-releases",
  "/coming-soon",
  "/pricing",
  // The free trial is a conversion page and belongs in search. /checkout is
  // deliberately absent: it is a step inside a purchase, and a search result
  // that drops someone into it with no plan chosen is a dead end wearing a
  // landing page's clothes. robots.ts disallows it for the same reason.
  "/trial",
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
