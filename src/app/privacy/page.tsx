import type { Metadata } from "next";
import LegalLayout from "@/components/legal/LegalLayout";

export const metadata: Metadata = { title: "Privacy Policy" };

export default function PrivacyPage() {
  return (
    <LegalLayout title="Privacy Policy" updated="20 June 2026">
      <section>
        <h2>1. Introduction</h2>
        <p>
          Enktel IPTV (&quot;Enktel&quot;, &quot;we&quot;, &quot;us&quot;) respects your privacy. This policy explains
          what information we collect when you use our website and service, how we use it, and the choices
          you have.
        </p>
      </section>
      <section>
        <h2>2. Information We Collect</h2>
        <ul>
          <li>Account details: email address, username, and password (stored securely hashed).</li>
          <li>Payment information: processed directly by PayPal — we never store your full card details.</li>
          <li>Usage data: device type, app version, and IP address for fraud prevention and troubleshooting.</li>
          <li>Communications: messages you send us via WhatsApp, email, or contact forms.</li>
        </ul>
      </section>
      <section>
        <h2>3. How We Use Your Information</h2>
        <ul>
          <li>To provision and maintain your IPTV subscription and credentials.</li>
          <li>To process payments and send order confirmations.</li>
          <li>To provide customer support and respond to inquiries.</li>
          <li>To detect and prevent fraud, abuse, or account sharing outside plan limits.</li>
        </ul>
      </section>
      <section>
        <h2>4. Sharing of Information</h2>
        <p>
          We share data only with the service providers needed to run Enktel: our payment processor (PayPal)
          and our streaming/reseller infrastructure partner. We do not sell your personal information to
          third parties.
        </p>
      </section>
      <section>
        <h2>5. Data Retention</h2>
        <p>
          We retain account and billing information for as long as your subscription is active, and for a
          reasonable period afterward to comply with tax and accounting obligations.
        </p>
      </section>
      <section>
        <h2>6. Your Rights</h2>
        <p>
          You may request access to, correction of, or deletion of your personal data at any time by
          contacting <a href="/contact">our support team</a>.
        </p>
      </section>
      <section>
        <h2>7. Contact</h2>
        <p>Questions about this policy can be sent via our <a href="/contact">Contact page</a>.</p>
      </section>
    </LegalLayout>
  );
}
