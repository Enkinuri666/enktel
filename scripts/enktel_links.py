"""
Where things live, and which version they are.

Shared by the welcome guide and the welcome email. They quote the same
addresses at a new subscriber, and two copies of a URL is two copies to
forget: the guide said "the link in your welcome email" for months while the
email did not exist, which is precisely the failure mode this file removes.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLE = ROOT / "androidtv" / "app" / "build.gradle.kts"

REPO = "https://github.com/Enkinuri666/enktel"

#: Always current, and short enough to type on a remote control. An asset URL
#: carries the version and dies at the next release; this one does not.
RELEASES = f"{REPO}/releases/latest"

WEB_PLAYER = "https://watch.enktel.tv"
SITE = "https://enktel.tv"
SUPPORT = f"{SITE}/contact"

#: The host a new line is set up against. Matches DEFAULT_SERVER in the app's
#: build file and STREAM_SERVER_URL on the site.
DEFAULT_SERVER = "https://x-api.cc"


def app_version() -> tuple[str, str]:
    """versionName and versionCode, read from the build file rather than typed.

    Read rather than passed in: a version typed into a document by hand is how
    the previous PDFs came to describe v1.12.0 of an app on 1.60.49.
    """
    src = GRADLE.read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', src)
    code = re.search(r"versionCode\s*=\s*(\d+)", src)
    if not name or not code:
        sys.exit(f"Could not read versionName/versionCode from {GRADLE}")
    return name.group(1), code.group(1)


def asset(name: str, version: str) -> str:
    return f"{REPO}/releases/download/v{version}/{name}"


def tv_apk(version: str) -> str:
    return asset(f"enktel-tv-{version}.apk", version)


def mobile_apk(version: str) -> str:
    return asset(f"enktel-mobile-{version}.apk", version)


def welcome_guide(version: str) -> str:
    return asset("enktel_welcome_guide.pdf", version)
