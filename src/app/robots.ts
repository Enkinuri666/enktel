import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  const siteUrl = process.env.NEXT_PUBLIC_SITE_URL || "https://enktel.tv";
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      // /checkout joins these: it is a step inside a purchase, not a page
      // anyone should arrive at from a search result, and indexing it puts
      // people into the middle of a flow with nothing selected.
      disallow: ["/api/", "/dashboard", "/checkout"],
    },
    sitemap: `${siteUrl}/sitemap.xml`,
  };
}
