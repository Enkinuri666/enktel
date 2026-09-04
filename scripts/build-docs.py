#!/usr/bin/env python3
"""
Builds the user manual and the testers guide as PDFs.

The two PDFs that existed before this script did not have one: they were
committed as binaries, and by the time anyone looked they were forty-nine
minor versions behind the app with no way to tell what in them was still
true. Generating them from a script is what stops that happening again —
the version comes from the build file rather than from a human retyping
it, so a document can no longer claim to describe a build it does not.

    python3 scripts/build-docs.py            # writes both PDFs to docs/
    python3 scripts/build-docs.py --out .

Requires reportlab (pip install reportlab).
"""
import argparse
import re
import sys
from pathlib import Path

try:
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_LEFT
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.units import mm
    from reportlab.platypus import (
        BaseDocTemplate, Frame, KeepTogether, PageTemplate, Paragraph,
        Spacer, Table, TableStyle,
    )
except ImportError:
    sys.exit("reportlab is required: pip install reportlab")

ROOT = Path(__file__).resolve().parent.parent
GRADLE = ROOT / "androidtv" / "app" / "build.gradle.kts"

BLUE = colors.HexColor("#2563EB")
INK = colors.HexColor("#111827")
DIM = colors.HexColor("#4B5563")
FAINT = colors.HexColor("#9CA3AF")
RULE = colors.HexColor("#E5E7EB")
WASH = colors.HexColor("#F3F4F6")


def app_version() -> tuple[str, str]:
    """versionName and versionCode, read from the build file.

    Read rather than passed in: a version typed into a document by hand is
    how the previous PDFs came to describe v1.12.0 of an app on 1.60.49.
    """
    src = GRADLE.read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', src)
    code = re.search(r"versionCode\s*=\s*(\d+)", src)
    if not name or not code:
        sys.exit(f"Could not read versionName/versionCode from {GRADLE}")
    return name.group(1), code.group(1)


def styles() -> dict:
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "title", parent=base["Title"], fontName="Helvetica-Bold",
            fontSize=26, leading=30, textColor=INK, spaceAfter=2, alignment=TA_LEFT,
        ),
        "sub": ParagraphStyle(
            "sub", parent=base["Normal"], fontName="Helvetica",
            fontSize=11, leading=15, textColor=DIM, spaceAfter=16,
        ),
        "h1": ParagraphStyle(
            "h1", parent=base["Heading1"], fontName="Helvetica-Bold",
            fontSize=15, leading=19, textColor=BLUE, spaceBefore=16, spaceAfter=6,
        ),
        "h2": ParagraphStyle(
            "h2", parent=base["Heading2"], fontName="Helvetica-Bold",
            fontSize=11, leading=14, textColor=INK, spaceBefore=10, spaceAfter=3,
        ),
        "body": ParagraphStyle(
            "body", parent=base["Normal"], fontName="Helvetica",
            fontSize=9.5, leading=13.5, textColor=INK, spaceAfter=6,
        ),
        "note": ParagraphStyle(
            "note", parent=base["Normal"], fontName="Helvetica-Oblique",
            fontSize=9, leading=12.5, textColor=DIM, spaceAfter=6,
        ),
        "cell": ParagraphStyle(
            "cell", parent=base["Normal"], fontName="Helvetica",
            fontSize=8.5, leading=11.5, textColor=INK,
        ),
        "cellb": ParagraphStyle(
            "cellb", parent=base["Normal"], fontName="Helvetica-Bold",
            fontSize=8.5, leading=11.5, textColor=INK,
        ),
    }


def table(rows, st, widths):
    data = [[Paragraph(rows[0][0], st["cellb"]), Paragraph(rows[0][1], st["cellb"])]]
    data += [[Paragraph(a, st["cell"]), Paragraph(b, st["cell"])] for a, b in rows[1:]]
    t = Table(data, colWidths=widths, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), WASH),
        ("LINEBELOW", (0, 0), (-1, -1), 0.4, RULE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
    ]))
    return t


def bullets(items, st):
    return [Paragraph(f"&bull;&nbsp;&nbsp;{x}", st["body"]) for x in items]


def build(path: Path, title: str, subtitle: str, story_fn, version: str):
    doc = BaseDocTemplate(
        str(path), pagesize=A4,
        leftMargin=20 * mm, rightMargin=20 * mm,
        topMargin=18 * mm, bottomMargin=18 * mm,
        title=f"EnkTel IPTV — {title} v{version}",
        author="EnkTel", subject=subtitle,
    )

    def decorate(canvas, d):
        canvas.saveState()
        canvas.setFont("Helvetica", 7.5)
        canvas.setFillColor(FAINT)
        canvas.drawString(20 * mm, 11 * mm, f"EnkTel IPTV — {title} — v{version}")
        canvas.drawRightString(A4[0] - 20 * mm, 11 * mm, f"Page {canvas.getPageNumber()}")
        canvas.setStrokeColor(RULE)
        canvas.setLineWidth(0.4)
        canvas.line(20 * mm, 14 * mm, A4[0] - 20 * mm, 14 * mm)
        canvas.restoreState()

    frame = Frame(
        doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="body",
        leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0,
    )
    doc.addPageTemplates([PageTemplate(id="main", frames=[frame], onPage=decorate)])
    doc.build(story_fn())
    return path


# ── user manual ────────────────────────────────────────────────────────

def manual_story(st, version, code):
    s = []
    s.append(Paragraph("EnkTel IPTV", st["title"]))
    s.append(Paragraph(
        f"User Manual &nbsp;&middot;&nbsp; version {version} (build {code}) &nbsp;&middot;&nbsp; "
        "Android TV, Fire TV, phone and tablet", st["sub"]))

    s.append(Paragraph("Getting started", st["h1"]))
    s.append(Paragraph(
        "EnkTel plays the channels and films you already have access to. It is a player, not a "
        "provider: you point it at a source, and it shows you what that source carries.", st["body"]))
    s.append(Paragraph("Your first launch", st["h2"]))
    s.append(Paragraph(
        "If you have no source set up, EnkTel opens on free-to-air television straight away — a "
        "lineup of public channels that need no account, plus a library of public-domain and "
        "openly-licensed films under Movies. You can watch immediately and add your own source later.",
        st["body"]))
    s.append(Paragraph("Adding your own source", st["h2"]))
    s.extend(bullets([
        "<b>Xtream Codes</b> — enter the server address, your username and your password.",
        "<b>M3U playlist</b> — enter the playlist URL, and an EPG/XMLTV URL if your provider "
        "publishes one separately.",
        "<b>Film library</b> — optional, and offered with either of the above. This is an "
        "on-demand playlist: its titles are filed under Movies rather than mixed into the "
        "channel list.",
    ], st))
    s.append(Paragraph(
        "Credentials are stored on the device and are not sent anywhere except to the provider "
        "they belong to.", st["note"]))

    s.append(Paragraph("Finding your way around", st["h1"]))
    s.append(table([
        ("Section", "What it holds"),
        ("Home", "Continue watching, plus rails drawn from across your catalogue."),
        ("Live TV", "The channel list. Number entry jumps straight to a channel."),
        ("Guide", "The programme grid, with a preview of the highlighted channel."),
        ("Movies / Series", "On-demand titles, with details, artwork and episode lists."),
        ("Sports", "Fixtures and live scores; a finder for what is on now."),
        ("Watchlist / Lists", "Titles you saved, and lists you made yourself."),
        ("Recordings", "Scheduled and completed recordings."),
        ("Downloads", "Titles saved to the device for offline playback."),
        ("Search", "Across channels, films and series at once."),
        ("Settings", "Everything below."),
    ], st, [38 * mm, 122 * mm]))

    s.append(Paragraph("Watching", st["h1"]))
    s.append(Paragraph("While a stream is playing", st["h2"]))
    s.append(table([
        ("Control", "What it does"),
        ("Aspect", "Cycles Fit, Stretch and Zoom. Fit preserves the picture's shape; Stretch "
                   "fills the screen and distorts; Zoom fills the screen by cropping."),
        ("Subtitles", "Picks a track. Styling is set once in Settings and applies to live and "
                      "on-demand alike."),
        ("Audio", "Picks a language or track where the stream carries more than one."),
        ("Dock", "Shrinks playback into a corner window so you can browse without stopping it."),
        ("Multi-view", "Two streams side by side."),
    ], st, [30 * mm, 130 * mm]))
    s.append(Paragraph(
        "Multi-view opens a second stream, so it needs a source that permits two simultaneous "
        "connections. On a line limited to one device the second picture will fail to start — "
        "that is the provider's limit, not a fault in the app.", st["note"]))
    s.append(Paragraph("Catch-up and recording", st["h2"]))
    s.append(Paragraph(
        "Where a provider offers catch-up, past programmes are reachable from the guide. "
        "Recordings can be set to start early and end late, which is worth doing because "
        "broadcast schedules drift; both paddings are in Settings under Recording.", st["body"]))

    s.append(Paragraph("Real-Debrid", st["h1"]))
    s.append(Paragraph(
        "Real-Debrid is a service you subscribe to separately. It takes a link you already hold "
        "and returns a direct, unthrottled one, and it holds whatever you have added to your own "
        "account. EnkTel is a client for that account: it presents your token, reads back what "
        "the service says is yours, and plays it.", st["body"]))
    s.append(Paragraph("Connecting it", st["h2"]))
    s.append(Paragraph(
        "Settings &rarr; Playlists &rarr; Real-Debrid, and paste the API token from your account "
        "page on real-debrid.com. The screen is empty and does nothing without one.", st["body"]))
    s.append(Paragraph("What the screen does", st["h2"]))
    s.append(table([
        ("Search my account", "Matches release-style filenames, so \u201cthe batman\u201d finds "
                              "The.Batman.2022.2160p.mkv. It searches your account and nothing else."),
        ("Play a link", "Paste a hoster link you hold; Real-Debrid turns it into a direct one."),
        ("Add a magnet", "Paste a magnet you hold. See below."),
        ("My downloads", "Your download history, already direct, so these play at once."),
        ("In my account", "Files in the account. Resolved when you play, because an "
                          "unrestricted link expires. A row holding several files says so "
                          "\u2014 open it to pick an episode."),
    ], st, [38 * mm, 122 * mm]))
    s.append(Paragraph("Adding a magnet", st["h2"]))
    s.append(Paragraph(
        "EnkTel first asks Real-Debrid whether it already holds that torrent, because the answer "
        "changes what to expect: a cached one is ready in seconds, and one that has to be fetched "
        "can take minutes. It then adds it and asks which files you want.", st["body"]))
    s.append(Paragraph(
        "Where a torrent has several files, videos are ticked for you and samples are not — "
        "change it before you confirm. Fetching a season pack whole pulls every episode to watch "
        "one, which is what the picker is there to avoid.", st["body"]))
    s.append(Paragraph(
        "The screen then waits about two minutes and plays it. If the download is still going "
        "when that runs out, nothing has failed: your account carries on fetching, and the title "
        "appears under In my account when it lands.", st["note"]))
    s.append(Paragraph("What it will not do", st["h2"]))
    s.append(Paragraph(
        "Nothing on this screen searches anywhere for content. Every link is one you supplied or "
        "one already in your account — Real-Debrid publishes no endpoint that looks for titles, "
        "and EnkTel does not go anywhere else to find them. Requests are also spaced deliberately: "
        "the service counts refused requests toward its own rate limit, so an app that asks too "
        "quickly gets the account blocked rather than the answer sooner.", st["body"]))

    s.append(Paragraph("Settings", st["h1"]))
    s.append(table([
        ("Playlists", "Add, sync, export and remove sources. Import a playlist file from the "
                      "device. Set a film library or a provider-specific User-Agent per source. "
                      "Manage categories, and test network speed."),
        ("Playback", "Subtitle colour, edge and background; loudness normalisation; auto-play "
                     "next episode; skip intro; picture-in-picture and the dock."),
        ("Recording", "Start-early and end-late padding, the download folder, dialogue boost "
                      "and an EPG timezone offset."),
        ("Network", "Buffer profiles for live and on-demand separately, and a minimum buffer "
                    "override."),
        ("Parental", "A PIN that gates restricted content."),
        ("Sports & Voice", "Live scores, the sport finder, and the teams you follow."),
        ("Real-Debrid", "Your API token, and the account screen described above."),
        ("Appearance", "Theme and layout."),
        ("About", "Version, diagnostics and crash reports."),
    ], st, [34 * mm, 126 * mm]))

    s.append(Paragraph("Downloads", st["h1"]))
    s.append(Paragraph(
        "Saved films and episodes play offline. Where they are kept is worth setting once: by "
        "default they go to a folder Android keeps private to the app, which means uninstalling "
        "clears them and no file manager can open it. Settings \u2192 Recording \u2192 Pick download "
        "folder puts them somewhere ordinary instead \u2014 one that survives uninstalling and can be "
        "read from a PC over USB.", st["body"]))
    s.append(Paragraph(
        "Each finished download has a folder button. Where you have picked a folder it opens it; "
        "where you have not, it explains why nothing can and offers to set one. Changing the "
        "folder affects future downloads \u2014 files already saved stay where they are.", st["note"]))

    s.append(Paragraph("Sending a download to a PC", st["h2"]))
    s.append(Paragraph(
        "Tap Send to PC on the Downloads screen. The app shows an address and a PIN; type the "
        "address into a browser on a computer on the same Wi-Fi, enter the PIN, and pick a title "
        "to save it. There is nothing to install on the computer, no account, and the file does "
        "not leave your home network.", st["body"]))
    s.append(Paragraph(
        "The PIN is new every time you start, and ten wrong attempts stop the server until you "
        "start it again. It keeps running while you use the rest of the app; stop it from the "
        "Downloads screen or from its notification. Large files resume by themselves if the "
        "connection drops. Sending needs Wi-Fi \u2014 on mobile data the app will not open a server, "
        "because there the local network is your carrier's.", st["note"]))

    s.append(Paragraph("Renewing your line", st["h1"]))
    s.append(Paragraph(
        "When a line is within a fortnight of running out, the account panel says so and offers a "
        "link to renew it. The link carries your username, which is what extends the line you "
        "already have rather than issuing a second one \u2014 so your existing details keep working "
        "and nothing on your devices needs changing.", st["body"]))
    s.append(Paragraph(
        "No prices are shown in the app. They live on the website, which is the only copy that can "
        "be kept current \u2014 a price built into an installed app would go on being shown after it "
        "changed. Where a device has no web browser, which is common on a television, the address "
        "is shown instead so you can open it on a phone.", st["note"]))
    s.append(Paragraph("Buffering, and why live and on-demand differ", st["h1"]))
    s.append(Paragraph(
        "Live and on-demand get different buffers from the same profile, on purpose. A film is a "
        "complete file, so holding more of it ahead of you protects against a slow network. A "
        "live channel is not: the provider keeps only a short rolling window of it, and asking "
        "for more than they have retained is a request for something already deleted. Buffering "
        "a live stream harder therefore makes it less reliable, not more.", st["body"]))
    s.append(Paragraph(
        "If live playback stutters, the profile to try is a smaller one, not a larger one. If a "
        "film stutters, a larger one is the right move.", st["note"]))

    s.append(Paragraph("Voice", st["h1"]))
    s.append(Paragraph(
        'Say "Hey Enki" and then what you want, or use the Mic entry in the navigation. Commands '
        "cover playback, moving between sections, and finding something to watch — for example "
        '"browse live TV", "any new films", or "any sports on right now". A separate voice '
        "command guide lists the full set.", st["body"]))

    s.append(Paragraph("When something is wrong", st["h1"]))
    s.append(table([
        ("Symptom", "Where to look"),
        ("Live stutters or stalls", "Network &rarr; Live buffer profile. Try a <i>smaller</i> "
                                    "profile; see the buffering note above."),
        ("A film buffers", "Network &rarr; VOD buffer profile. Try a larger profile."),
        ("Channels rejected with correct details",
         "Playlists &rarr; the source's User-Agent. Some providers filter on it, which presents "
         "as a wrong password."),
        ("Second stream will not start", "Your source's simultaneous-connection limit."),
        ("Guide is empty or wrong", "Playlists &rarr; Refresh EPG, and the EPG timezone offset "
                                    "under Recording."),
        ("Catalogue looks stale", "Playlists &rarr; Sync now."),
    ], st, [45 * mm, 115 * mm]))
    return s


# ── testers guide ──────────────────────────────────────────────────────

def testers_story(st, version, code):
    s = []
    s.append(Paragraph("EnkTel IPTV", st["title"]))
    s.append(Paragraph(
        f"Testers Guide &nbsp;&middot;&nbsp; version {version} (build {code})", st["sub"]))

    s.append(Paragraph("Installing", st["h1"]))
    s.append(Paragraph(
        "Builds are sideloaded from the GitHub release. Two APKs are published per release: "
        "<b>tv</b> for Android TV and Fire TV, <b>mobile</b> for phones and tablets. Install the "
        "one matching the device — they are separate builds, not one file that adapts.", st["body"]))
    s.extend(bullets([
        "<b>Fire TV</b> — install via Downloader or ADB. No developer certificate is needed.",
        "<b>Android TV</b> — allow installation from the source you are downloading with, then "
        "open the APK.",
        "<b>Phone / tablet</b> — open the APK and allow installation when prompted.",
    ], st))
    s.append(Paragraph(
        "This release carries a database migration. Installing over an older build is safe and "
        "keeps your data; going back to 1.60.x afterwards is not, and needs app data cleared "
        "first. Test the upgrade path over your existing install before wiping anything — an "
        "upgrade that loses profiles is exactly the bug worth catching.", st["note"]))

    s.append(Paragraph(f"What changed in {version}", st["h1"]))
    s.append(table([
        ("Change", "What to look at"),
        ("The paid layer is gone",
         "No trial signup, no upgrade screen, no checkout. Confirm nothing anywhere still "
         "offers to sell a subscription, and that no screen links to a page that 404s."),
        ("Free tier has a film library",
         "A fresh install with no source should reach both live channels and a Movies library "
         "with artwork. Check the titles play, not just that they list."),
        ("Bring your own film library",
         "Onboarding and Settings both accept an on-demand playlist URL. Its titles must land "
         "under Movies, never in the channel list."),
        ("Catalogue sync memory",
         "A large catalogue should sync without the app being killed. This is the fix worth "
         "re-checking on the smallest device you have."),
    ], st, [42 * mm, 118 * mm]))

    s.append(Paragraph("What to exercise", st["h1"]))
    s.append(Paragraph("Worth the most attention", st["h2"]))
    s.extend(bullets([
        "<b>Upgrade from your existing install.</b> Profiles, favourites, watch progress, "
        "recordings and downloads must all survive.",
        "<b>First launch on a clean install.</b> With no source configured you should end up "
        "watching something, not staring at a login form.",
        "<b>A film library URL of your own</b>, entered at onboarding and again in Settings. A "
        "URL that is not a playlist should fail visibly rather than silently.",
        "<b>Playback on the weakest device you have.</b> A 1&nbsp;GB stick is where memory and "
        "buffering problems appear first.",
    ], st))
    s.append(Paragraph("Also worth a pass", st["h2"]))
    s.extend(bullets([
        "Live and on-demand buffer profiles, including whether a smaller live profile helps a "
        "stuttering channel.",
        "Subtitle styling on live TV as well as on films — the two used to be styled by "
        "different code.",
        "Aspect cycling: Fit, Stretch, Zoom, and back to Fit.",
        "The dock: playback continuing while you browse, and returning to full screen.",
        "Remote and D-pad reach on every screen — anything focusable should be reachable, and "
        "the focus highlight should sit on what is actually selected.",
    ], st))

    s.append(Paragraph("Known constraints", st["h1"]))
    s.append(table([
        ("Behaviour", "Why"),
        ("Multi-view needs two connections",
         "It opens a second stream. A line limited to one device cannot show two pictures; "
         "this is the provider's limit, not a bug."),
        ("The free film library is English-language",
         "It is drawn from curated public-domain archive collections, which hold effectively "
         "nothing in Croatian, Serbian or Bosnian. Not a gap in the app."),
        ("A film library that fails to load is silent",
         "It is deliberately best-effort so that a failed download cannot take your channels "
         "down with it. Check the sync summary to see whether titles arrived."),
    ], st, [50 * mm, 110 * mm]))

    s.append(Paragraph("Reporting", st["h1"]))
    s.append(Paragraph("A report that can be acted on says:", st["body"]))
    s.extend(bullets([
        "<b>Device and OS</b> — model and version, plus whether it is the tv or mobile build.",
        f"<b>App version</b> — {version}, build {code}. Settings &rarr; About shows it.",
        "<b>Source type</b> — Xtream, M3U, or the free tier. Never send credentials.",
        "<b>What you did, what happened, what you expected</b> — in that order.",
        "<b>Whether it repeats</b>, and whether it also happens on the previous build.",
    ], st))
    s.append(Paragraph(
        "Settings &rarr; About carries diagnostics and any crash report. Attach that rather than "
        "describing it. Redact the server address and username if the report includes them.",
        st["note"]))
    return s


# ── entry point ────────────────────────────────────────────────────────

def main() -> int:
    ap = argparse.ArgumentParser(description="Build the EnkTel PDF documentation.")
    ap.add_argument("--out", default="docs", help="output directory (default: docs)")
    args = ap.parse_args()

    version, code = app_version()
    st = styles()
    out = (ROOT / args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)

    made = [
        build(out / "enktel_user_manual.pdf", "User Manual",
              "How to set up and use EnkTel IPTV",
              lambda: manual_story(st, version, code), version),
        build(out / "enktel_testers_guide.pdf", "Testers Guide",
              f"What to exercise in EnkTel IPTV {version}",
              lambda: testers_story(st, version, code), version),
    ]
    for p in made:
        print(f"{p.relative_to(ROOT)}  ({p.stat().st_size:,} bytes)  v{version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
