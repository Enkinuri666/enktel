"use client";
import { QRCodeSVG } from "qrcode.react";

interface QRCodeProps {
  value?: string;
  size?: number;
  className?: string;
}

/** Branded QR code — defaults to the enktel.tv boarding gate. */
export default function QRCode({ value = "https://enktel.tv", size = 96, className = "" }: QRCodeProps) {
  return (
    <div className={`bg-white p-2 rounded-lg inline-flex ${className}`}>
      <QRCodeSVG value={value} size={size} bgColor="#ffffff" fgColor="#060910" level="M" />
    </div>
  );
}
