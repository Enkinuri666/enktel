import type { Metadata } from "next";
import "./globals.css";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import WhatsAppButton from "@/components/layout/WhatsAppButton";
import { CHANNEL_COUNT_LABEL } from "@/lib/channels";

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || "https://enktel.tv"),
  title: {
    default: "Enktel IPTV – Stream Beyond Limits",
    template: "%s | Enktel IPTV",
  },
  description:
    `Premium IPTV service with ${CHANNEL_COUNT_LABEL} channels, 4K Ultra HD quality, 99.9% uptime, and UK & international content. Stream live TV, sports, movies, and more.`,
  keywords: [
    "IPTV", "live TV", "stream", "channels", "EPG", "4K", "sports", "movies",
    "Sky Sports", "Premier League", "IPTV subscription", "UK IPTV",
  ],
  openGraph: {
    title: "Enktel IPTV – Stream Beyond Limits",
    description: `Premium IPTV with ${CHANNEL_COUNT_LABEL} channels. 4K quality. 99.9% uptime.`,
    type: "website",
    locale: "en_GB",
  },
  twitter: {
    card: "summary_large_image",
    title: "Enktel IPTV – Stream Beyond Limits",
    description: `Premium IPTV with ${CHANNEL_COUNT_LABEL} channels. 4K quality.`,
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800;900&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="bg-brand-bg text-white antialiased">
        <Navbar />
        <main className="pt-16 min-h-screen">{children}</main>
        <Footer />
        <WhatsAppButton />
      </body>
    </html>
  );
}
