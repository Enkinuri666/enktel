#!/usr/bin/env python3
"""
Builds the illustrated welcome guide — the one handed to a new subscriber.

Distinct from `build-docs.py`, which produces the reference manual and the
testers guide. Those are text: they answer "how do I do X" for someone who
already has the app. This one answers "what did I just buy and how do I start
watching on the thing in front of me", and it does that with pictures, because
a new subscriber with a Fire Stick in one hand does not read prose.

    python3 scripts/build-welcome-guide.py
    python3 scripts/build-welcome-guide.py --shots /path/to/screenshots --out .

Screenshots are supplied rather than invented. The `--shots` directory is
expected to hold `pc/`, `web/` and `android/` subdirectories; any image that is
missing is simply left out, and the page says so rather than the document
implying a screen exists that nobody has photographed. See `docs/SCREENSHOTS.md`
for how each set is produced.

Requires reportlab (pip install reportlab).
"""
import argparse
import re
import sys
from io import BytesIO
from pathlib import Path

try:
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_CENTER, TA_LEFT
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.units import mm
    from reportlab.lib.utils import ImageReader
    from reportlab.platypus import (
        BaseDocTemplate, Flowable, Frame, Image, KeepTogether, NextPageTemplate,
        PageBreak, PageTemplate, Paragraph, Spacer, Table, TableStyle,
    )
except ImportError:
    sys.exit("reportlab is required: pip install reportlab")

ROOT = Path(__file__).resolve().parent.parent
GRADLE = ROOT / "androidtv" / "app" / "build.gradle.kts"

# ── brand ───────────────────────────────────────────────────────────────
# Taken from the apps themselves — pc/tailwind.config.ts and the Android
# theme — so the document and the product are the same blue rather than two
# blues that nearly match.

BG = colors.HexColor("#0A0E17")      # app background
SURFACE = colors.HexColor("#121826")
SURFACE_HI = colors.HexColor("#1B2333")
BRAND = colors.HexColor("#3B9DFF")
BRAND_DEEP = colors.HexColor("#1B6AE5")
PURPLE = colors.HexColor("#8B5CF6")
OK = colors.HexColor("#34D399")
LIVE = colors.HexColor("#EF4444")

INK = colors.HexColor("#111827")
DIM = colors.HexColor("#4B5563")
FAINT = colors.HexColor("#9AA4BF")
RULE = colors.HexColor("#E5E7EB")
WASH = colors.HexColor("#F5F7FB")
PAPER = colors.white

PAGE_W, PAGE_H = A4
MARGIN = 18 * mm


def app_version() -> tuple[str, str]:
    """versionName and versionCode, read from the build file rather than typed."""
    src = GRADLE.read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', src)
    code = re.search(r"versionCode\s*=\s*(\d+)", src)
    if not name or not code:
        sys.exit(f"Could not read versionName/versionCode from {GRADLE}")
    return name.group(1), code.group(1)


# ── styles ──────────────────────────────────────────────────────────────

def styles() -> dict:
    base = getSampleStyleSheet()
    def s(name, **kw):
        parent = kw.pop("parent", base["Normal"])
        return ParagraphStyle(name, parent=parent, **kw)

    return {
        "cover_title": s("cover_title", fontName="Helvetica-Bold", fontSize=42,
                         leading=44, textColor=colors.white, spaceAfter=0),
        "cover_sub": s("cover_sub", fontName="Helvetica", fontSize=13.5, leading=19,
                       textColor=colors.HexColor("#B8C4DC"), spaceBefore=10),
        "cover_meta": s("cover_meta", fontName="Helvetica", fontSize=9.5, leading=13,
                        textColor=colors.HexColor("#7C89A6")),
        "eyebrow": s("eyebrow", fontName="Helvetica-Bold", fontSize=8.5, leading=11,
                     textColor=BRAND, spaceAfter=3),
        "h1": s("h1", fontName="Helvetica-Bold", fontSize=21, leading=25,
                textColor=INK, spaceBefore=0, spaceAfter=4),
        "h2": s("h2", fontName="Helvetica-Bold", fontSize=12.5, leading=16,
                textColor=INK, spaceBefore=12, spaceAfter=4),
        "lede": s("lede", fontName="Helvetica", fontSize=11, leading=16,
                  textColor=DIM, spaceAfter=10),
        "body": s("body", fontName="Helvetica", fontSize=9.6, leading=14.2,
                  textColor=INK, spaceAfter=7),
        "caption": s("caption", fontName="Helvetica", fontSize=8.2, leading=11.5,
                     textColor=DIM, spaceBefore=5, spaceAfter=2),
        "figno": s("figno", fontName="Helvetica-Bold", fontSize=8.2, leading=11.5,
                   textColor=BRAND),
        "note": s("note", fontName="Helvetica", fontSize=9, leading=13,
                  textColor=INK),
        "step": s("step", fontName="Helvetica", fontSize=9.6, leading=14,
                  textColor=INK),
        "tiny": s("tiny", fontName="Helvetica", fontSize=7.8, leading=10.4,
                  textColor=DIM),
        "toc": s("toc", fontName="Helvetica", fontSize=10, leading=17, textColor=INK),
        "toc_num": s("toc_num", fontName="Helvetica-Bold", fontSize=10, leading=17,
                     textColor=BRAND),
    }


# ── drawing bits ────────────────────────────────────────────────────────

class Rule(Flowable):
    """A hairline the width of the frame."""
    def __init__(self, colour=RULE, thickness=0.6, space=4):
        super().__init__()
        self.colour, self.thickness, self.space = colour, thickness, space
        self.width, self.height = 0, thickness + space * 2

    def wrap(self, aw, ah):
        self.width = aw
        return aw, self.height

    def draw(self):
        self.canv.setStrokeColor(self.colour)
        self.canv.setLineWidth(self.thickness)
        self.canv.line(0, self.space, self.width, self.space)


class Shot(Flowable):
    """
    A screenshot, framed.

    Drawn rather than dropped in with Image() so it gets the rounded dark
    surround the apps have — a bright screenshot floating on white looks like
    a mistake, and the border is what makes a page of them read as a set.
    """
    PAD = 3.2

    def __init__(self, path: Path, max_w: float, max_h: float, radius=4.5):
        super().__init__()
        self.radius = radius
        self.path = _embeddable(path)
        reader = ImageReader(self.path)
        iw, ih = reader.getSize()
        scale = min(max_w / iw, max_h / ih)
        self.iw, self.ih = iw * scale, ih * scale
        self.width = self.iw + self.PAD * 2
        self.height = self.ih + self.PAD * 2

    def wrap(self, aw, ah):
        return self.width, self.height

    def draw(self):
        c = self.canv
        c.saveState()
        c.setFillColor(SURFACE_HI)
        c.setStrokeColor(SURFACE_HI)
        c.roundRect(0, 0, self.width, self.height, self.radius, stroke=1, fill=1)
        c.drawImage(self.path, self.PAD, self.PAD, self.iw, self.ih,
                    preserveAspectRatio=True, anchor='c')
        c.restoreState()


def _embeddable(path: Path):
    """
    A screenshot re-encoded as JPEG for embedding.

    The PNGs in `docs/screenshots` are the lossless originals and stay that
    way. reportlab embeds a PNG with Flate, which on a screenful of poster art
    barely compresses at all — the guide came to 13 MB, which is past what an
    email will carry. reportlab passes a JPEG straight through as DCTDecode,
    and at this printed size (82 mm across) the difference is not visible.

    Falls back to the original path if Pillow is not installed, so the script
    still works, just heavier.
    """
    try:
        from PIL import Image as PILImage
    except ImportError:
        return str(path)
    im = PILImage.open(path)
    if im.mode not in ("RGB", "L"):
        im = im.convert("RGB")
    buf = BytesIO()
    im.save(buf, format="JPEG", quality=88, optimize=True, progressive=True)
    buf.seek(0)
    return ImageReader(buf)


class Callout(Flowable):
    """A tinted box for the one thing on a page that must not be missed."""
    def __init__(self, text: str, style, tint=WASH, bar=BRAND, width=None):
        super().__init__()
        self.para = Paragraph(text, style)
        self.tint, self.bar = tint, bar
        self._w = width

    def wrap(self, aw, ah):
        self.width = self._w or aw
        inner = self.width - 12 * mm
        _, h = self.para.wrap(inner, ah)
        self.height = h + 9
        return self.width, self.height

    def draw(self):
        c = self.canv
        c.setFillColor(self.tint)
        c.setStrokeColor(self.tint)
        c.roundRect(0, 0, self.width, self.height, 3, stroke=1, fill=1)
        c.setFillColor(self.bar)
        c.rect(0, 0, 2.2, self.height, stroke=0, fill=1)
        self.para.drawOn(c, 7 * mm, 4.5)


def numbered_steps(items: list[str], st: dict, width: float) -> Table:
    """Steps as a two-column table so the numerals stay in a tidy gutter."""
    rows = []
    for i, text in enumerate(items, 1):
        rows.append([
            Paragraph(f'<font color="#3B9DFF"><b>{i}</b></font>', st["step"]),
            Paragraph(text, st["step"]),
        ])
    t = Table(rows, colWidths=[7 * mm, width - 7 * mm])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 1.5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 0),
    ]))
    return t


def spec_table(rows: list[tuple[str, str]], st: dict, width: float) -> Table:
    data = [[Paragraph(f"<b>{k}</b>", st["body"]), Paragraph(v, st["body"])]
            for k, v in rows]
    t = Table(data, colWidths=[width * 0.32, width * 0.68])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, 0), (-1, -2), 0.4, RULE),
    ]))
    return t


# ── page furniture ──────────────────────────────────────────────────────

def cover_page(canvas, doc):
    """Full-bleed dark cover. The only page that is not on white."""
    c = canvas
    c.saveState()
    c.setFillColor(BG)
    c.rect(0, 0, PAGE_W, PAGE_H, stroke=0, fill=1)

    # A soft brand wash top-right, built from concentric discs because
    # reportlab has no gradient primitive worth the trouble.
    for i in range(26, 0, -1):
        t = i / 26.0
        r = 150 * mm * t
        c.setFillColor(colors.Color(
            BRAND_DEEP.red, BRAND_DEEP.green, BRAND_DEEP.blue,
            alpha=0.030 * (1 - t) + 0.004,
        ))
        c.circle(PAGE_W * 0.92, PAGE_H * 0.88, r, stroke=0, fill=1)

    # Wordmark
    c.setFont("Helvetica-Bold", 15)
    c.setFillColor(colors.white)
    c.drawString(MARGIN, PAGE_H - MARGIN - 4, "ENK")
    w = c.stringWidth("ENK", "Helvetica-Bold", 15)
    c.setFillColor(BRAND)
    c.drawString(MARGIN + w, PAGE_H - MARGIN - 4, "TEL")
    w += c.stringWidth("TEL", "Helvetica-Bold", 15)
    c.setFillColor(colors.HexColor("#7C89A6"))
    c.setFont("Helvetica", 9.5)
    c.drawString(MARGIN + w + 5, PAGE_H - MARGIN - 4, "IPTV")

    # Accent rule, directly under the wordmark. It sat mid-page once and
    # struck straight through the title.
    c.setStrokeColor(BRAND)
    c.setLineWidth(2.4)
    y = PAGE_H - MARGIN - 13
    c.line(MARGIN, y, MARGIN + 26 * mm, y)
    c.restoreState()


def content_bg(canvas, doc):
    """Runs before the frame: paper only."""
    canvas.saveState()
    canvas.setFillColor(PAPER)
    canvas.rect(0, 0, PAGE_W, PAGE_H, stroke=0, fill=1)
    canvas.restoreState()


def content_furniture(canvas, doc):
    """
    Runs after the frame, which is the whole point.

    `afterFlowable` updates the section name as the content is laid out, so a
    header drawn before the frame names the *previous* section — the first
    page of "The web player" was captioned "Contents".
    """
    c = canvas
    c.saveState()

    # Header wordmark, quiet.
    c.setFont("Helvetica-Bold", 8)
    c.setFillColor(colors.HexColor("#9AA4BF"))
    c.drawString(MARGIN, PAGE_H - MARGIN + 6, "ENKTEL IPTV")

    section = getattr(doc, "current_section", "")
    if section:
        c.setFont("Helvetica", 8)
        c.drawRightString(PAGE_W - MARGIN, PAGE_H - MARGIN + 6, section)

    c.setStrokeColor(RULE)
    c.setLineWidth(0.5)
    c.line(MARGIN, PAGE_H - MARGIN + 1, PAGE_W - MARGIN, PAGE_H - MARGIN + 1)

    # Footer
    c.setFont("Helvetica", 7.6)
    c.setFillColor(FAINT)
    c.drawString(MARGIN, MARGIN - 8, getattr(doc, "footer_left", ""))
    c.drawRightString(PAGE_W - MARGIN, MARGIN - 8, str(doc.page))
    c.restoreState()


class Guide(BaseDocTemplate):
    def __init__(self, path, **kw):
        super().__init__(str(path), pagesize=A4,
                         leftMargin=MARGIN, rightMargin=MARGIN,
                         topMargin=MARGIN, bottomMargin=MARGIN, **kw)
        frame_cover = Frame(MARGIN, MARGIN, PAGE_W - 2 * MARGIN,
                            PAGE_H - 2 * MARGIN, id="cover",
                            leftPadding=0, rightPadding=0,
                            topPadding=0, bottomPadding=0)
        frame_body = Frame(MARGIN, MARGIN, PAGE_W - 2 * MARGIN,
                           PAGE_H - 2 * MARGIN, id="body",
                           leftPadding=0, rightPadding=0,
                           topPadding=0, bottomPadding=0)
        self.addPageTemplates([
            PageTemplate(id="cover", frames=[frame_cover], onPage=cover_page),
            PageTemplate(id="body", frames=[frame_body],
                         onPage=content_bg, onPageEnd=content_furniture),
        ])
        self.current_section = ""
        self.footer_left = ""

    def afterFlowable(self, flowable):
        """Section name in the running header, set by a marker flowable."""
        if isinstance(flowable, SectionMark):
            self.current_section = flowable.name


class SectionMark(Flowable):
    """Zero-height marker that renames the running header from here on."""
    def __init__(self, name):
        super().__init__()
        self.name = name
        self.width = self.height = 0

    def wrap(self, aw, ah):
        return 0, 0

    def draw(self):
        pass


# ── content ─────────────────────────────────────────────────────────────
# The words. Kept here rather than in a template file because there is one
# document and a second file to keep in step would be a second thing to
# forget.

TAGLINE = "Stream Beyond Limits"

INTRO = (
    "Your subscription is one login that works on everything you own. There is no "
    "app store to search and no account to create — the username and password on "
    "your welcome email are the whole of it. This guide shows you each way to watch, "
    "in the order most people set them up."
)

WAYS = [
    ("The web player", "watch.enktel.tv",
     "Nothing to install. Open a browser, sign in, and watch. Start here to check "
     "your line works before you set anything else up."),
    ("Phone &amp; tablet", "Android APK",
     "The full app, including downloads for offline watching on a plane or a commute."),
    ("TV &amp; Fire Stick", "Android TV APK",
     "The same app, laid out for a remote control and a room-sized screen."),
    ("Windows PC", "Installer",
     "A desktop player, and the other half of Send to PC — it pulls finished "
     "downloads off your phone over your own Wi-Fi."),
]

SECTIONS = [
    dict(
        key="web",
        num="01",
        title="The web player",
        where="watch.enktel.tv",
        lede="No installation, no waiting. If you can open a browser you can watch — "
             "which makes this the fastest way to confirm your line is live.",
        steps=[
            "Go to <b>watch.enktel.tv</b> in any browser.",
            "Enter the <b>username</b> and <b>password</b> from your welcome email. "
            "The server address is already filled in, so most people only type two things.",
            "Press <b>Sign In</b>. Your channels, films and series load straight away.",
        ],
        callout="Signing in here proves the line itself is working. If the web player "
                "plays and an app does not, the problem is that device's setup — not "
                "your subscription.",
        shots=[
            ("web/web-signin.png", "Sign in", "Two fields and a button. <b>Advanced</b> "
             "is there for a different server, an M3U playlist or a Stalker portal, and "
             "most people never open it."),
            ("web/web-home.png", "Home", "Live TV, Movies and Series in rows, with your "
             "most recent activity first."),
            ("web/web-live.png", "Live TV", "Every channel your package carries, grouped "
             "by country and genre."),
            ("web/web-movies.png", "Movies", "The on-demand library, filterable by "
             "category and year."),
            ("web/web-series.png", "Series", "Box sets, organised by season and episode."),
            ("web/web-settings.png", "Settings", "Playback, appearance and account, in "
             "one place."),
        ],
    ),
    dict(
        key="mobile",
        num="02",
        title="Phone and tablet",
        where="Android APK",
        lede="The full app. Everything the web player does, plus downloads you can watch "
             "with no signal at all — and the ability to hand those downloads to your PC.",
        steps=[
            "On the phone, allow installing apps from your browser when Android asks.",
            "Open the APK link from your welcome email and install it.",
            "Open EnkTel and enter your <b>username</b> and <b>password</b>. "
            "The server address is prefilled.",
            "Give it a moment on first run while the channel list and guide load.",
        ],
        callout="Downloads keep running when you leave the screen or switch apps — the "
                "app holds a foreground service so Android cannot quietly stop them.",
        shots=[
            ("android/onboarding-mobile.png", "Sign in", "The same two fields as the web player. "
             "Xtream Codes is the default; M3U and Stalker are on the same screen if your "
             "line needs one."),
            ("android/downloads-mobile.png", "Downloads", "Films and episodes saved to the device. "
             "Each one shows progress, speed and where it was saved; the folder button "
             "opens it, and <b>Send to PC</b> hands the finished ones to your computer."),
        ],
    ),
    dict(
        key="tv",
        num="03",
        title="TV, Fire Stick and Android TV",
        where="Android TV APK",
        lede="The same application, built for a remote instead of a fingertip: bigger "
             "targets, a focus ring you can follow from the sofa, and a layout that "
             "respects the overscan a television crops.",
        steps=[
            "On a Fire TV, install <b>Downloader</b> from the Amazon appstore. On other "
            "Android TV boxes, use whichever sideloading tool you already have.",
            "Enter the APK link from your welcome email.",
            "Install, then open EnkTel from your apps row.",
            "Sign in with the same <b>username</b> and <b>password</b>. One subscription "
            "covers every device.",
        ],
        callout="Use the <b>tv</b> APK on a television and the <b>mobile</b> APK on a "
                "phone. They are the same app with different layouts, and installing the "
                "wrong one is the single most common setup mistake.",
        shots=[
            ("android/onboarding-bigscreen-tv.png", "Sign in on TV", "The same screen, sized for a "
             "room rather than a hand."),
            ("android/downloads-bigscreen-tv.png", "Downloads on TV", "Saved films and episodes, "
             "navigable entirely with a remote."),
        ],
    ),
    dict(
        key="pc",
        num="04",
        title="Windows PC",
        where="Installer",
        lede="A desktop player in its own right — and the other half of Send to PC, which "
             "moves finished downloads off your phone and onto your computer over your "
             "own Wi-Fi, with nothing going near the internet.",
        steps=[
            "Run the installer from your welcome email. Windows will warn about an "
            "unrecognised app: choose <b>More info</b>, then <b>Run anyway</b>.",
            "Open EnkTel and sign in with the same <b>username</b> and <b>password</b>.",
            "For Send to PC: on the phone open <b>Downloads</b> and press "
            "<b>Send to PC</b>. It shows an address and a six-digit PIN.",
            "On the PC open <b>My devices</b>. It finds the phone by itself — pick it, "
            "type the PIN, choose a folder, and press <b>Get all</b>.",
        ],
        callout="A transfer that is interrupted resumes from the byte it reached rather "
                "than starting the film again, and the PIN is new every time sharing "
                "starts.",
        shots=[
            ("pc/pc-onboarding.png", "Sign in", "Server, username and password — the same "
             "details as every other device."),
            ("pc/pc-home.png", "Home", "A featured title, quick links, and themed rows "
             "built from your own library."),
            ("pc/pc-live.png", "Live TV", "The full channel list with categories down "
             "the side."),
            ("pc/pc-movies.png", "Movies", "The on-demand library at desktop scale."),
            ("pc/pc-series.png", "Series", "Box sets, by season."),
            ("pc/pc-devices-scan.png", "My devices &mdash; finding the phone",
             "The PC looks for devices that are sharing, so there is no address to read "
             "off a television and retype."),
            ("pc/pc-devices-paired.png", "My devices &mdash; connected",
             "Finished downloads ready to copy across, and the phone's own download "
             "queue, which you can pause, resume, retry or cancel from here."),
            ("pc/pc-settings.png", "Settings", "Playback, appearance and account."),
        ],
    ),
]

TROUBLE = [
    ("Nothing plays on any device",
     "Check the web player first at <b>watch.enktel.tv</b>. If that fails too, the line "
     "itself needs attention — contact support with your username."),
    ("It plays in the browser but not in the app",
     "That device has the wrong server address or the wrong APK. Re-enter the details "
     "exactly as they appear on the web player's Advanced panel."),
    ("The PC cannot find my phone",
     "Both devices must be on the same Wi-Fi, and the phone must be showing a PIN. Some "
     "routers block devices from seeing each other &mdash; look for &ldquo;AP isolation&rdquo; "
     "or &ldquo;client isolation&rdquo; and turn it off. Failing that, type the address "
     "the phone is showing into the box under the device list; that always works."),
    ("Send to PC will not start on mobile data",
     "That is deliberate. On mobile data the &ldquo;local network&rdquo; is your "
     "carrier&rsquo;s, and the app will not open a server onto it. Join Wi-Fi and try again."),
    ("A download stopped when I left the app",
     "It should not &mdash; downloads and sharing both run as foreground services. If it "
     "does, check the phone&rsquo;s battery optimisation settings and exempt EnkTel."),
    ("Buffering on live channels",
     "Try another stream for the same channel from the channel&rsquo;s menu, then "
     "<b>Settings &rarr; Playback</b> to raise the buffer. On a Fire Stick, closing "
     "other apps frees the memory the decoder needs."),
]


def build(out_path: Path, shots_dir: Path) -> Path:
    st = styles()
    version, code = app_version()
    doc = Guide(out_path,
                title="EnkTel IPTV — Welcome Guide",
                author="EnkTel IPTV",
                subject="Getting started on every device")
    doc.footer_left = f"EnkTel IPTV · Welcome Guide · v{version}"

    W = PAGE_W - 2 * MARGIN
    story = []
    missing: list[str] = []

    def shot(rel: str, max_w: float, max_h: float):
        """A framed screenshot, or None when it has not been captured."""
        p = shots_dir / rel
        if not p.is_file():
            missing.append(rel)
            return None
        return Shot(p, max_w, max_h)

    # ---- cover -------------------------------------------------------
    story.append(Spacer(1, PAGE_H * 0.36))
    story.append(Paragraph("Welcome to<br/>EnkTel IPTV", st["cover_title"]))
    story.append(Spacer(1, 16))
    story.append(Paragraph(
        f"{TAGLINE}. One subscription, every screen you own — "
        "TV, phone, tablet, computer and browser.", st["cover_sub"]))
    story.append(Spacer(1, PAGE_H * 0.30))
    story.append(Paragraph(
        f"Getting started guide &nbsp;·&nbsp; App version {version} (build {code})",
        st["cover_meta"]))
    story.append(NextPageTemplate("body"))
    story.append(PageBreak())

    # ---- contents ----------------------------------------------------
    story.append(SectionMark("Contents"))
    story.append(Paragraph("BEFORE YOU START", st["eyebrow"]))
    story.append(Paragraph("Four ways to watch", st["h1"]))
    story.append(Paragraph(INTRO, st["lede"]))
    story.append(Rule())
    story.append(Spacer(1, 6))

    rows = []
    for i, (name, where, blurb) in enumerate(WAYS, 1):
        rows.append([
            Paragraph(f'<font color="#3B9DFF"><b>{i:02d}</b></font>', st["toc_num"]),
            Paragraph(f"<b>{name}</b><br/>"
                      f'<font size="8" color="#4B5563">{blurb}</font>', st["toc"]),
            Paragraph(f'<font size="8" color="#9AA4BF">{where}</font>', st["toc"]),
        ])
    t = Table(rows, colWidths=[10 * mm, W - 45 * mm, 35 * mm])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("LINEBELOW", (0, 0), (-1, -2), 0.4, RULE),
        ("ALIGN", (2, 0), (2, -1), "RIGHT"),
    ]))
    story.append(t)
    story.append(Spacer(1, 14))
    story.append(Callout(
        "<b>Your login is the same everywhere.</b> One username and password, on as many "
        "devices as your package allows streams. Setting up a second device never means "
        "a second account.", st["note"]))

    story.append(Spacer(1, 14))
    story.append(Paragraph("What you need", st["h2"]))
    story.append(spec_table([
        ("Your details", "Username, password and server address — on your welcome email."),
        ("Web player", "Any modern browser. Nothing to install."),
        ("Phone / tablet", "Android 6.0 or newer."),
        ("TV / Fire Stick", "Fire TV, Fire Stick, or any Android TV box."),
        ("Windows PC", "Windows 10 or 11, 64-bit."),
    ], st, W))
    story.append(PageBreak())

    # ---- per-platform sections ---------------------------------------
    fig = 0
    for sec in SECTIONS:
        story.append(SectionMark(sec["title"]))
        story.append(Paragraph(f"{sec['num']} &nbsp; {sec['where'].upper()}", st["eyebrow"]))
        story.append(Paragraph(sec["title"], st["h1"]))
        story.append(Paragraph(sec["lede"], st["lede"]))
        story.append(Rule())
        story.append(Spacer(1, 4))
        story.append(Paragraph("Setting it up", st["h2"]))
        story.append(numbered_steps(sec["steps"], st, W))
        story.append(Spacer(1, 8))
        story.append(Callout(sec["callout"], st["note"]))
        story.append(Spacer(1, 14))

        # Screenshots follow straight on rather than starting a page of their
        # own — the steps rarely fill half a page, and a section that breaks
        # there reads as though something is missing.
        story.append(Paragraph("What you will see", st["h2"]))
        story.append(Spacer(1, 4))

        col_w = (W - 8 * mm) / 2
        pending = []
        for rel, caption, blurb in sec["shots"]:
            fig += 1
            img = shot(rel, col_w - 2 * Shot.PAD, 105 * mm)
            if img is None:
                fig -= 1
                continue
            cell = [
                img,
                Paragraph(f'<font color="#3B9DFF"><b>Fig {fig}</b></font> &nbsp; '
                          f"<b>{caption}</b>", st["caption"]),
                Paragraph(blurb, st["tiny"]),
            ]
            pending.append(cell)

        for i in range(0, len(pending), 2):
            pair = pending[i:i + 2]
            left = pair[0]
            right = pair[1] if len(pair) > 1 else [Spacer(1, 1)]
            row = Table([[left, right]], colWidths=[col_w, col_w])
            row.setStyle(TableStyle([
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (0, -1), 0),
                ("RIGHTPADDING", (0, 0), (0, -1), 4 * mm),
                ("LEFTPADDING", (1, 0), (1, -1), 4 * mm),
                ("RIGHTPADDING", (1, 0), (1, -1), 0),
                ("TOPPADDING", (0, 0), (-1, -1), 0),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6 * mm),
            ]))
            story.append(row)

        if not pending:
            story.append(Callout(
                "Screenshots for this section have not been captured yet.",
                st["note"], tint=colors.HexColor("#FFF7ED"), bar=colors.HexColor("#F59E0B")))
        story.append(PageBreak())

    # ---- troubleshooting ---------------------------------------------
    story.append(SectionMark("If something goes wrong"))
    story.append(Paragraph("HELP", st["eyebrow"]))
    story.append(Paragraph("If something goes wrong", st["h1"]))
    story.append(Paragraph(
        "Most setup problems are one of the six below, and the first check narrows it "
        "down faster than anything else.", st["lede"]))
    story.append(Rule())
    story.append(Spacer(1, 4))
    for q, a in TROUBLE:
        story.append(KeepTogether([
            Paragraph(q, st["h2"]),
            Paragraph(a, st["body"]),
        ]))
    story.append(Spacer(1, 10))
    story.append(Callout(
        "<b>Still stuck?</b> Contact support with your username and which device you are "
        "on. Do not send your password — nobody at EnkTel will ask for it.",
        st["note"]))

    doc.build(story)
    return out_path, missing, version


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", default=str(ROOT / "docs"),
                    help="directory to write the PDF into")
    ap.add_argument("--shots", default=str(ROOT / "docs" / "screenshots"),
                    help="directory holding pc/, web/ and android/ screenshots")
    args = ap.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    shots_dir = Path(args.shots)

    path, missing, version = build(out_dir / "enktel_welcome_guide.pdf", shots_dir)
    size = path.stat().st_size
    print(f"{path}  ({size:,} bytes)  v{version}")
    if missing:
        # Named rather than counted: "3 missing" tells you nothing about which
        # page now has a gap in it.
        print(f"  {len(missing)} screenshot(s) not found under {shots_dir}:")
        for m in missing:
            print(f"    - {m}")


if __name__ == "__main__":
    main()
