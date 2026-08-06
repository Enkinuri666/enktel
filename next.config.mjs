/** @type {import('next').NextConfig} */
const nextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "image.tmdb.org",
      },
    ],
  },
  async rewrites() {
    return [
      // Digital Asset Links has to live at this exact path for Android's
      // verifier to find it, and a directory named `.well-known` under app/ is
      // not routable — the file-system router skips dot-prefixed segments. A
      // rewrite is the reliable way to serve it from a route handler, which is
      // what lets the fingerprint come from an env var instead of being
      // committed to the repository.
      {
        source: "/.well-known/assetlinks.json",
        destination: "/api/assetlinks",
      },
    ];
  },
};

export default nextConfig;
