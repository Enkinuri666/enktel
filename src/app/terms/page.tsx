import type { Metadata } from "next";
import LegalLayout from "@/components/legal/LegalLayout";

export const metadata: Metadata = { title: "Terms of Service" };

export default function TermsPage() {
  return (
    <LegalLayout title="Terms of Service" updated="20 June 2026">
      <section>
        <h2>1. Acceptance of Terms</h2>
        <p>
          By subscribing to or using Enktel IPTV, you agree to these Terms of Service. If you do not agree,
          please do not use the service.
        </p>
      </section>
      <section>
        <h2>2. The Service</h2>
        <p>
          Enktel IPTV provides access to live television channels, video-on-demand content, and an electronic
          program guide via internet streaming. Channel availability, quality, and uptime may vary and are not
          guaranteed at 100%.
        </p>
      </section>
      <section>
        <h2>3. Account &amp; Device Limits</h2>
        <p>
          Each subscription is licensed for use on one device at a time, as stated on the Pricing page.
          Sharing credentials beyond this limit may result in suspension without refund.
        </p>
      </section>
      <section>
        <h2>4. Payments</h2>
        <p>
          Monthly plans renew unless cancelled. 3-month and 12-month plans are one-time payments with no
          automatic renewal. All prices are shown in EUR and are inclusive of any applicable processing fees
          unless stated otherwise.
        </p>
      </section>
      <section>
        <h2>5. Acceptable Use</h2>
        <ul>
          <li>You will not resell, rebroadcast, or redistribute the service to third parties.</li>
          <li>You will not attempt to reverse-engineer, scrape, or disrupt our infrastructure.</li>
          <li>You are responsible for ensuring your use of the service complies with local laws.</li>
        </ul>
      </section>
      <section>
        <h2>6. Service Availability</h2>
        <p>
          We target 99.9% uptime but do not guarantee uninterrupted service. Scheduled maintenance or
          upstream provider issues may occasionally cause downtime. See our <a href="/status">Status Page</a>{" "}
          for current service health.
        </p>
      </section>
      <section>
        <h2>7. Termination</h2>
        <p>
          We may suspend or terminate accounts that violate these Terms, engage in fraud, or abuse the
          service. See our <a href="/refund-policy">Refund Policy</a> for details on cancellations.
        </p>
      </section>
      <section>
        <h2>8. Limitation of Liability</h2>
        <p>
          Enktel IPTV is provided &quot;as is&quot;. To the maximum extent permitted by law, we are not liable
          for indirect or consequential damages arising from use of the service.
        </p>
      </section>
      <section>
        <h2>9. Changes to These Terms</h2>
        <p>We may update these Terms from time to time. Continued use of the service after changes constitutes acceptance.</p>
      </section>
    </LegalLayout>
  );
}
