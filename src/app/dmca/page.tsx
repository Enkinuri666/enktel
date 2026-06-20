import type { Metadata } from "next";
import LegalLayout from "@/components/legal/LegalLayout";

export const metadata: Metadata = { title: "DMCA Policy" };

export default function DmcaPage() {
  return (
    <LegalLayout title="DMCA Policy" updated="20 June 2026">
      <section>
        <h2>1. Respect for Intellectual Property</h2>
        <p>
          Enktel IPTV is a reseller of streaming access and respects the intellectual property rights of
          content owners and broadcasters. All channel logos, trademarks, and broadcast content belong to
          their respective owners.
        </p>
      </section>
      <section>
        <h2>2. Filing a Takedown Notice</h2>
        <p>
          If you believe content accessible through our service infringes your copyright, please send a
          notice via <a href="/contact">our Contact page</a> including:
        </p>
        <ul>
          <li>Identification of the copyrighted work claimed to be infringed.</li>
          <li>Identification of the material you claim is infringing, with enough detail to locate it.</li>
          <li>Your contact information (name, address, phone, email).</li>
          <li>A statement that you have a good-faith belief the use is unauthorized.</li>
          <li>A statement, under penalty of perjury, that the information is accurate and you are authorized to act.</li>
        </ul>
      </section>
      <section>
        <h2>3. Our Response</h2>
        <p>
          Upon receiving a valid notice, we will investigate and, where appropriate, remove or disable access
          to the affected content within a reasonable timeframe.
        </p>
      </section>
    </LegalLayout>
  );
}
