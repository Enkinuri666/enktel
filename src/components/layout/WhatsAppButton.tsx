"use client";

const WHATSAPP_NUMBER = process.env.NEXT_PUBLIC_WHATSAPP_NUMBER || "";
const DEFAULT_MESSAGE = "Hi! I'm interested in Enktel IPTV — can you help me get started?";

export default function WhatsAppButton() {
  if (!WHATSAPP_NUMBER) return null;

  const href = `https://wa.me/${WHATSAPP_NUMBER}?text=${encodeURIComponent(DEFAULT_MESSAGE)}`;

  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      aria-label="Chat with us on WhatsApp"
      className="fixed bottom-5 right-5 z-[9999] flex items-center justify-center w-14 h-14 rounded-full shadow-lg shadow-black/40 transition-transform duration-300 hover:scale-110"
      style={{ background: "#25D366" }}
    >
      <span className="absolute inline-flex h-full w-full rounded-full bg-[#25D366] opacity-60 animate-ping" />
      <svg
        viewBox="0 0 32 32"
        className="relative w-7 h-7 fill-white"
        aria-hidden="true"
      >
        <path d="M16.004 3C9.376 3 4 8.373 4 15c0 2.347.687 4.533 1.872 6.37L4 29l7.84-1.83A11.93 11.93 0 0 0 16.004 27C22.63 27 28 21.627 28 15S22.63 3 16.004 3Zm6.97 17.13c-.295.83-1.45 1.5-2.36 1.69-.63.13-1.45.24-4.21-.9-3.54-1.46-5.82-5.02-6-5.26-.18-.24-1.44-1.92-1.44-3.66 0-1.74.91-2.6 1.23-2.95.32-.35.7-.44.94-.44.23 0 .47.002.67.012.21.01.5-.08.78.6.3.7.97 2.36 1.06 2.53.09.17.15.37.03.59-.12.22-.18.36-.36.55-.18.2-.37.45-.53.6-.18.17-.37.36-.16.71.21.35.94 1.55 2.02 2.51 1.39 1.24 2.57 1.63 2.95 1.81.38.18.61.16.84-.08.23-.24.97-1.06 1.23-1.43.26-.37.51-.31.85-.18.34.12 2.16 1.02 2.53 1.2.37.18.61.27.7.42.09.15.09.86-.21 1.69Z" />
      </svg>
    </a>
  );
}
