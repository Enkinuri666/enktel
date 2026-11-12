#!/usr/bin/env bash
#
# Run the Android unit tests, retrying once if — and only if — Robolectric
# failed to download its android-all jar.
#
# Robolectric does not get that jar through Gradle. It fetches it itself, over
# plain HTTP from Maven Central, the first time a @RunWith(RobolectricTestRunner)
# class is loaded, into ~/.m2/repository/org/robolectric. When the runner's
# connection hiccups mid-transfer every Robolectric class in the suite dies at
# once, before any test body runs:
#
#   BrowsePositionTest > classMethod FAILED
#       java.lang.AssertionError at MavenArtifactFetcher.java:129
#         Caused by: java.io.IOException at HttpURLConnection.java:2034
#
# That is what killed v1.79.0's first release build: 968 tests passed, lint
# passed, and the six Robolectric classes failed on a socket. The jar is now
# cached by the workflows, so this path should only be reachable on a cold
# cache — but a cold cache is exactly when a release is most likely to be
# blocked by it, so handle it rather than hope.
#
# The retry is deliberately narrow. Re-running a test suite until it passes
# hides real flakes, and a test that fails twice for its own reasons must stay
# failed. So the output is searched for MavenArtifactFetcher, and anything else
# fails the step on the first attempt.

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$here/../../androidtv"

tasks=(:app:testTvDebugUnitTest :app:testMobileDebugUnitTest)
log_dir="${RUNNER_TEMP:-/tmp}"

run_tests() {
  local log="$1"; shift
  # pipefail is set, so this reports Gradle's exit status, not tee's.
  ./gradlew "${tasks[@]}" --no-daemon "$@" 2>&1 | tee "$log"
}

attempt_one="$log_dir/unit-tests-attempt-1.log"
attempt_two="$log_dir/unit-tests-attempt-2.log"

# Anything passed to this script is handed to Gradle, so a human can run the
# same thing CI runs with --offline or --tests.
if run_tests "$attempt_one" "$@"; then
  exit 0
fi

if ! grep -q 'MavenArtifactFetcher' "$attempt_one"; then
  echo "Unit tests failed on their own merits — not retrying." >&2
  exit 1
fi

echo "::warning::Robolectric could not download its android-all jar (MavenArtifactFetcher). Retrying the unit tests once."
run_tests "$attempt_two" "$@"
