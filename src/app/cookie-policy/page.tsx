import type { Metadata } from "next";
import LegalLayout from "@/components/legal/LegalLayout";

export const metadata: Metadata = { title: "Cookie Policy" };

export default function CookiePolicyPage() {
  return (
    <LegalLayout title="Cookie Policy" updated="20 June 2026">
      <section>
        <h2>1. What Are Cookies</h2>
        <p>
          Cookies are small text files stored on your device that help websites function and remember your
          preferences.
        </p>
      </section>
      <section>
        <h2>2. Cookies We Use</h2>
        <ul>
          <li><strong>Essential cookies</strong> — required for login sessions and checkout to function.</li>
          <li><strong>Preference cookies</strong> — remember settings like your selected channel category filters.</li>
          <li><strong>Analytics cookies</strong> — help us understand site usage so we can improve the experience.</li>
        </ul>
      </section>
      <section>
        <h2>3. Managing Cookies</h2>
        <p>
          You can disable cookies in your browser settings at any time. Disabling essential cookies may
          prevent checkout and account features from working correctly.
        </p>
      </section>
      <section>
        <h2>4. Third-Party Cookies</h2>
        <p>
          Our payment processor (Stripe) may set its own cookies during checkout for fraud prevention. These
          are governed by Stripe&apos;s own privacy policy.
        </p>
      </section>
    </LegalLayout>
  );
}
