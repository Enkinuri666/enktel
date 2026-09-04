import type { Metadata } from "next";
import LegalLayout from "@/components/legal/LegalLayout";

export const metadata: Metadata = { title: "Refund Policy" };

export default function RefundPolicyPage() {
  return (
    <LegalLayout title="Refund Policy" updated="20 June 2026">
      <section>
        <h2>1. Free Trial First</h2>
        <p>
          We offer a free 24-hour trial you can <a href="/trial">start yourself</a> before you buy — we
          strongly recommend testing the service on your device first, as this helps avoid most refund
          requests.
        </p>
      </section>
      <section>
        <h2>2. Eligibility</h2>
        <ul>
          <li>Refund requests must be made within 48 hours of purchase.</li>
          <li>Your subscription must not have been used beyond reasonable testing (e.g. a handful of streams).</li>
          <li>Refunds are not available for monthly plans after the first 7 days of a billing cycle.</li>
          <li>3-month and 12-month plans are eligible for a pro-rated refund only within the first 48 hours.</li>
        </ul>
      </section>
      <section>
        <h2>3. Non-Refundable Situations</h2>
        <ul>
          <li>Account suspended for sharing credentials beyond the 1-device limit or other Terms of Service violations.</li>
          <li>Dissatisfaction due to local network restrictions, ISP throttling, or incompatible unsupported devices not disclosed at purchase.</li>
          <li>Change of mind after extended use of the service.</li>
        </ul>
      </section>
      <section>
        <h2>4. How to Request a Refund</h2>
        <p>
          Contact us via <a href="/contact">our Contact page</a> or WhatsApp with your subscription ID and
          reason for the request. Approved refunds are processed back to your original payment method within
          5–10 business days.
        </p>
      </section>
    </LegalLayout>
  );
}
