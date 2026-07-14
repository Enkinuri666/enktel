import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { Analytics } from "@vercel/analytics/next";
import { SpeedInsights } from "@vercel/speed-insights/next";
import "./globals.css";
import Navbar from "@/components/layout/Navbar";
import TopTicker from "@/components/layout/TopTicker";
import Footer from "@/components/layout/Footer";
import ChatLauncher from "@/components/layout/ChatLauncher";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

const inter = Inter({
  subsets: ["latin", "latin-ext"],
  weight: ["300", "400", "500", "600", "700", "800", "900"],
  display: "swap",
  variable: "--font-inter",
});

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || "https://enktel.tv"),
  title: {
    default: "Enktel IPTV – Stream Beyond Limits",
    template: "%s | Enktel IPTV",
  },
  description:
    `Premium IPTV service with ${CHANNEL_COUNT_LABEL} channels, 4K Ultra HD quality, 99.9% uptime, and UK & international content. Stream live TV, sports, movies, and more.`,
  manifest: "/manifest.json",
  keywords: [
    "IPTV", "live TV", "stream", "channels", "EPG", "4K", "sports", "movies",
    "Sky Sports", "Premier League", "IPTV subscription", "UK IPTV",
  ],
  openGraph: {
    title: "Enktel IPTV – Stream Beyond Limits",
    description: `Premium IPTV with ${CHANNEL_COUNT_LABEL} channels. 4K quality. 99.9% uptime.`,
    type: "website",
    locale: "en_GB",
    images: [{ url: "/og-image.png", width: 1200, height: 630, alt: "Enktel IPTV" }],
  },
  twitter: {
    card: "summary_large_image",
    title: "Enktel IPTV – Stream Beyond Limits",
    description: `Premium IPTV with ${CHANNEL_COUNT_LABEL} channels. 4K quality.`,
    images: ["/og-image.png"],
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={inter.variable}>
      <body className={`${inter.className} bg-brand-bg text-white antialiased`}>
        <div className="fixed top-0 inset-x-0 z-50 flex flex-col">
          <TopTicker />
          <Navbar />
        </div>
        <main className="pt-24 min-h-screen">{children}</main>
        <Footer />
        <ChatLauncher />
        <Analytics />
        <SpeedInsights />
      </body>
    </html>
  );
}
