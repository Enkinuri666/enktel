import { NextResponse } from "next/server";

/**
 * Digital Asset Links, served at `/.well-known/assetlinks.json` via a rewrite
 * in next.config.mjs.
 *
 * The TV app declares `android:autoVerify="true"` on its
 * `https://enktel.tv/play/...` intent filter. Android only honours that if it
 * can fetch this file over HTTPS and find the app's package name and signing
 * certificate in it. Without it the deep link still *works* — Android just
 * shows a disambiguation dialog instead of opening the app — so the failure is
 * quiet and easy to miss, which is why the reason is spelled out below rather
 * than left as an empty array.
 *
 * The fingerprint is configuration, not code: it belongs to the release
 * keystore, which is not in this repository and must never be. Set
 * ANDROID_CERT_SHA256 in the Vercel project alongside TMDB_API_KEY. Get the
 * value with:
 *
 *   keytool -list -v -keystore <release.jks> -alias <alias> | grep SHA256
 *
 * Multiple fingerprints are allowed — comma- or whitespace-separated — which
 * you need when the upload key and the app-signing key differ, or while
 * rotating a key.
 */
export const dynamic = "force-dynamic";

/** Both flavours ship from this domain; mobile carries the `.mobile` suffix. */
const PACKAGES = ["tv.enktel.app", "tv.enktel.app.mobile"];

/** `AA:BB:…:FF` — 32 hex pairs. Anything else is a typo, not a certificate. */
const FINGERPRINT = /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/;

function fingerprints(): { valid: string[]; rejected: string[] } {
  const raw = process.env.ANDROID_CERT_SHA256 ?? "";
  const candidates = raw
    .split(/[\s,]+/)
    .map((s) => s.trim().toUpperCase())
    .filter(Boolean);
  return {
    valid: candidates.filter((c) => FINGERPRINT.test(c)),
    rejected: candidates.filter((c) => !FINGERPRINT.test(c)),
  };
}

export async function GET() {
  const { valid, rejected } = fingerprints();

  if (valid.length === 0) {
    // Deliberately not an empty `[]` with a 200. A well-formed but empty asset
    // links file is indistinguishable from a correctly configured one that
    // simply does not list your app — Android reports the same verification
    // failure for both, and you would have nothing to go on. Saying which side
    // is unconfigured is the whole point.
    return NextResponse.json(
      {
        error: "assetlinks is not configured",
        reason:
          rejected.length > 0
            ? `ANDROID_CERT_SHA256 is set but no value matched the expected AA:BB:…:FF form (${rejected.length} rejected)`
            : "ANDROID_CERT_SHA256 is not set on this deployment",
      },
      { status: 503, headers: { "Cache-Control": "no-store" } },
    );
  }

  const statements = PACKAGES.map((pkg) => ({
    relation: ["delegate_permission/common.handle_all_urls"],
    target: {
      namespace: "android_app",
      package_name: pkg,
      sha256_cert_fingerprints: valid,
    },
  }));

  return new NextResponse(JSON.stringify(statements, null, 2), {
    status: 200,
    headers: {
      // Android's verifier requires application/json exactly; it rejects the
      // file outright on any other content type, including text/plain.
      "Content-Type": "application/json",
      // Verification runs at install time and on demand. An hour is long
      // enough to be cheap and short enough that adding a rotated key does
      // not take a day to take effect.
      "Cache-Control": "public, max-age=3600",
    },
  });
}
