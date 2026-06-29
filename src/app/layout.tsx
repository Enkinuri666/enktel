import type { Metadata } from "next";
import Script from "next/script";
import { Analytics } from "@vercel/analytics/next";
import "./globals.css";
import Navbar from "@/components/layout/Navbar";
import TopTicker from "@/components/layout/TopTicker";
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
        <div className="fixed top-0 inset-x-0 z-50 flex flex-col">
          <TopTicker />
          <Navbar />
        </div>
        <main className="pt-24 min-h-screen">{children}</main>
        <Footer />
        <WhatsAppButton />
        <Analytics />
        <Script id="tawk-to" strategy="afterInteractive">
          {`
            var Tawk_API=Tawk_API||{}, Tawk_LoadStart=new Date();
            (function(){
            var s1=document.createElement("script"),s0=document.getElementsByTagName("script")[0];
            s1.async=true;
            s1.src='https://embed.tawk.to/6a147b54b418e81c3672f7a8/default';
            s1.charset='UTF-8';
            s1.setAttribute('crossorigin','*');
            s0.parentNode.insertBefore(s1,s0);
            })();
          `}
        </Script>
      </body>
    </html>
  );
}
