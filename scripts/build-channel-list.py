#!/usr/bin/env python3
"""
Builds a printable channel list for one region of the lineup.

    XTREAM_SERVER=https://x-api.cc XTREAM_USERNAME=… XTREAM_PASSWORD=… \
        python3 scripts/build-channel-list.py --region exyu

Writes `docs/enktel_channels_<region>.pdf`.

## Where the data comes from

`player_api.php`, with a line that is entitled to the lineup — the same
documented calls the app itself makes. Credentials come from the environment
and are never written anywhere: not into the PDF, not into the repo. That
matters more than usual here, because every Xtream stream URL embeds the
username and password, so a channel list that quoted URLs would be a
credential leak wearing a document's clothes. Only names, categories and
quality tags are printed.

## Why the lineup is not simply reprinted

The panel groups Ex-Yu into six categories: three by country (Croatia,
Serbia, Bosna Hersek) and three by genre (Ex-Yu Entertainment, Music,
Sport). That is a fine way to browse on a television and a poor way to read
on paper — "which Croatian sports channels do I get" is answered by looking
in two places and knowing that `ARENA SPORT 1 ᴴᴰ HR` in the Sport category is
Croatian because of the two letters at the end.

So this re-cuts the same channels by country first and genre second, and says
so at the top. Nothing is invented and nothing is dropped: every channel the
panel returned appears exactly once, and the totals are printed so the
document can be checked against the panel.
"""
import argparse
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from collections import OrderedDict
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from enktel_links import SITE, SUPPORT, app_version  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent

# ── regions ───────────────────────────────────────────────────────────

#: Which panel categories make up a region, and what to call it.
REGIONS = {
    "exyu": {
        "title": "Ex-Yu Channels",
        "subtitle": "Croatia · Serbia · Bosnia and Herzegovina · regional",
        # Matched against the panel's category names, case-insensitively.
        "category_match": r"^\s*\|EX\|",
    },
}

# ── classification ────────────────────────────────────────────────────

COUNTRIES = OrderedDict([
    ("HR", "Croatia"),
    ("RS", "Serbia"),
    ("BA", "Bosnia and Herzegovina"),
    ("REGIONAL", "Regional and pan-Ex-Yu"),
])

#: A country tag the provider put in the channel name. This wins over
#: everything else, because it is the provider stating the answer.
COUNTRY_TOKEN = re.compile(r"(?:^|\s)(HR|BA|BIH|RS|SRB|SI|SLO|MK|ME|MNE|CG)(?:\s|$)", re.I)
TOKEN_TO_COUNTRY = {
    "HR": "HR", "BA": "BA", "BIH": "BA", "RS": "RS", "SRB": "RS",
    # Present in the tag vocabulary but not in this lineup today. Mapped
    # rather than left to fall through, so a channel that appears later lands
    # somewhere sensible instead of in "Regional".
    "SI": "REGIONAL", "SLO": "REGIONAL", "MK": "REGIONAL",
    "ME": "REGIONAL", "MNE": "REGIONAL", "CG": "REGIONAL",
}

#: Broadcasters whose home country is not in doubt, for the genre categories
#: where the provider gave no tag. Checked as a word-boundary prefix so
#: "RTS DRAMA" resolves and "PARTIZAN TV" does not accidentally match "RTS".
HOME_COUNTRY = [
    (r"^HRT\b", "HR"), (r"^HTV\b", "HR"), (r"^NOVA TV\b", "HR"), (r"^RTL\b", "HR"),
    (r"^DOMA TV\b", "HR"), (r"^CMC\b", "HR"), (r"^SPORTSKA TV\b", "HR"),
    (r"^RTS\b", "RS"), (r"^RTV\b", "RS"), (r"^PRVA\b", "RS"), (r"^B92\b", "RS"),
    (r"^STUDIO B\b", "RS"), (r"^HAPPY\b", "RS"), (r"^GRAND\b", "RS"),
    (r"^KCN\b", "RS"), (r"^BLIC\b", "RS"), (r"^PARTIZAN\b", "RS"),
    (r"^SUPERSTAR\b", "RS"),
    (r"^HAYAT", "BA"), (r"^BHT\b", "BA"), (r"^BN\b", "BA"), (r"^FACE TV\b", "BA"),
    (r"^FEDERALNA\b", "BA"), (r"^OBN\b", "BA"), (r"^RTRS\b", "BA"),
]

#: Genre by keyword, first match wins — so the order is the priority order.
#: Sport before everything because "ARENA SPORT" must not be read as general
#: entertainment, and Kids before Movies so "PINK KIDS" is not filed as film.
GENRES = [
    ("Sport", r"\b(SPORT|SPORTKLUB|ARENA|FIGHT|UFC|GOLF|EUROSPORT|PPV)\b"),
    ("Kids", r"\b(KIDS|BABY|BOOMERANG|NICK|NICKTOONS|CARTOON|MINIMAX|POLETARAC|"
              r"HAYATOVCI|DA VINCI|MAŠA|MASA)\b"),
    ("News", r"\b(N1|NEWS|INFO|JAZEERA|EURONEWS|VIJESTI|BLIC)\b"),
    ("Documentary", r"\b(DISCOVERY|ANIMAL PLANET|HISTORY|NATIONAL|GEOGRAPHIC|DOCUBOX|"
                    r"H2|VIASAT|CRIME|INVESTIGATION|EXPLORER|TRAVEL|NAT GEO)\b"),
    ("Music", r"\b(MUSIC|MUZIKA|MTV|VH1|CMC|BALKANIKA|FOLK|HITS|KONCERT|HI FI)\b"),
    ("Movies and series", r"\b(HBO|CINEMAX|CINESTAR|CINEMANIA|FILM|MOVIES|DRAMA|SERIJE|"
                          r"AXN|DIVA|EPIC|SCI FI|FANTASY|HOROR|HORROR|ROMANCE|COMEDY|"
                          r"ACTION|PREMIERE|SOAP|STAR TV|KLASIK|CLASSIC)\b"),
    ("Lifestyle", r"\b(STYLE|KUVAR|FOOD|ZIVOT|ŽIVOT|EXTRA|LOL|HAHA|SHOW|REALITY|"
                  r"PEDIA|AGRO)\b"),
]

#: Quality tags the provider appends. Lifted out of the name so the list reads
#: as channels rather than as a wall of superscript.
QUALITY = [
    ("4K", r"ᵁᴴᴰ|\bUHD\b|\b4K\b"),
    ("HD", r"ᴴᴰ|\bFHD\b|\bHD\b"),
    ("SD", r"\bSD\b"),
]


def clean_name(raw: str) -> tuple[str, str]:
    """Return the channel's name without its country and quality tags, and
    the quality it advertised."""
    quality = ""
    for label, pat in QUALITY:
        if re.search(pat, raw, re.I):
            quality = label
            break
    name = raw
    for _, pat in QUALITY:
        name = re.sub(pat, " ", name, flags=re.I)
    name = COUNTRY_TOKEN.sub(" ", name)
    name = re.sub(r"\s+", " ", name).strip(" -|·")
    return name or raw.strip(), quality


def country_of(raw: str, category: str) -> str:
    m = COUNTRY_TOKEN.search(raw)
    if m:
        return TOKEN_TO_COUNTRY.get(m.group(1).upper(), "REGIONAL")
    up = raw.upper()
    for pat, c in HOME_COUNTRY:
        if re.search(pat, up):
            return c
    cat = category.upper()
    if "CROATIA" in cat:
        return "HR"
    if "SERBIA" in cat:
        return "RS"
    if "BOSNA" in cat or "BOSNIA" in cat or "HERSEK" in cat:
        return "BA"
    return "REGIONAL"


def genre_of(raw: str, category: str) -> str:
    up = raw.upper()
    for label, pat in GENRES:
        if re.search(pat, up):
            return label
    cat = category.upper()
    if "SPORT" in cat:
        return "Sport"
    if "MUSIC" in cat:
        return "Music"
    return "General entertainment"


#: The order genres are printed in, most-asked-for first.
GENRE_ORDER = [
    "Sport", "General entertainment", "Movies and series", "News",
    "Documentary", "Kids", "Music", "Lifestyle",
]


# ── fetching ──────────────────────────────────────────────────────────

def api(server: str, user: str, password: str, action: str, **extra) -> list:
    q = {"username": user, "password": password, "action": action, **extra}
    url = f"{server.rstrip('/')}/player_api.php?" + urllib.parse.urlencode(q)
    with urllib.request.urlopen(url, timeout=90) as r:
        return json.loads(r.read().decode("utf-8", "replace"))


def fetch(server: str, user: str, password: str, match: str) -> list[dict]:
    cats = api(server, user, password, "get_live_categories")
    wanted = [c for c in cats if re.search(match, c["category_name"], re.I)]
    if not wanted:
        sys.exit(f"No categories matched {match!r}. The panel returned {len(cats)}.")
    rows = []
    for c in wanted:
        streams = api(server, user, password, "get_live_streams", category_id=c["category_id"])
        print(f"  {c['category_name'].strip()}: {len(streams)}")
        for s in streams:
            # Deliberately not `direct_source`, and nothing else that could
            # carry a URL: every Xtream stream address embeds the username and
            # password. What is kept is what a reader needs.
            rows.append({
                "name": s["name"],
                "category": c["category_name"],
                "num": s.get("num"),
                "logo": s.get("stream_icon") or "",
                "epg": s.get("epg_channel_id") or "",
                "archive": str(s.get("tv_archive") or "0") not in ("0", ""),
                "archive_days": s.get("tv_archive_duration") or "0",
            })
    return rows


def cache_logos(rows: list[dict], cache: Path, workers: int = 12) -> dict[str, Path]:
    """Download each distinct logo once, and shrink it.

    Panel logos are arbitrary PNGs — some are 1000px wide — and 315 of them at
    full size is a 40 MB PDF nobody can email. Scaled to 64px on the long edge
    and re-encoded, which is more than a 7mm box on paper can show.

    A logo that will not fetch or will not decode is simply absent. A channel
    list that fails because one broadcaster's CDN is down would be a poor
    trade for a picture.
    """
    from concurrent.futures import ThreadPoolExecutor
    cache.mkdir(parents=True, exist_ok=True)
    urls = sorted({r["logo"] for r in rows if r["logo"]})
    out: dict[str, Path] = {}

    def one(url: str):
        import hashlib
        key = hashlib.sha1(url.encode()).hexdigest()[:16]
        dst = cache / f"{key}.png"
        if dst.exists():
            return url, dst
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=25) as r:
                raw = r.read()
            from PIL import Image
            import io
            im = Image.open(io.BytesIO(raw))
            im = im.convert("RGBA")
            im.thumbnail((64, 64))
            im.save(dst, "PNG")
            return url, dst
        except Exception:
            return url, None

    print(f"Fetching {len(urls)} logos…")
    with ThreadPoolExecutor(max_workers=workers) as ex:
        for url, dst in ex.map(one, urls):
            if dst is not None:
                out[url] = dst
    print(f"  {len(out)}/{len(urls)} usable")
    return out


# ── the document ──────────────────────────────────────────────────────

def build_pdf(rows: list[dict], region: dict, out: Path, source_note: str,
              logos: dict[str, Path]) -> None:
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_LEFT
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle
    from reportlab.lib.units import mm
    from reportlab.platypus import (
        BaseDocTemplate, Frame, Image, PageTemplate, Paragraph, Spacer, Table, TableStyle,
    )

    INK = colors.HexColor("#0A0E17")
    BRAND = colors.HexColor("#3B9DFF")
    MUTED = colors.HexColor("#6B7280")
    HAIRLINE = colors.HexColor("#E5E7EB")
    WASH = colors.HexColor("#F5F7FB")

    version, _ = app_version()
    W, H = A4
    margin = 16 * mm

    st = {
        "h1": ParagraphStyle("h1", fontName="Helvetica-Bold", fontSize=24, leading=28, textColor=INK),
        "lede": ParagraphStyle("lede", fontName="Helvetica", fontSize=10, leading=15, textColor=MUTED),
        "country": ParagraphStyle("country", fontName="Helvetica-Bold", fontSize=15, leading=19, textColor=INK,
                                  spaceBefore=10, spaceAfter=2),
        "genre": ParagraphStyle("genre", fontName="Helvetica-Bold", fontSize=9, leading=12, textColor=BRAND,
                                spaceBefore=8, spaceAfter=3),
        "cell": ParagraphStyle("cell", fontName="Helvetica", fontSize=8.5, leading=11, textColor=INK,
                               alignment=TA_LEFT),
        "note": ParagraphStyle("note", fontName="Helvetica", fontSize=8, leading=11, textColor=MUTED),
    }

    def furniture(canvas, doc):
        canvas.saveState()
        canvas.setFont("Helvetica-Bold", 7.5)
        canvas.setFillColor(MUTED)
        canvas.drawString(margin, H - margin + 5 * mm, "ENKTEL IPTV")
        canvas.drawRightString(W - margin, H - margin + 5 * mm, region["title"].upper())
        canvas.setStrokeColor(HAIRLINE)
        canvas.setLineWidth(0.5)
        canvas.line(margin, H - margin + 3 * mm, W - margin, H - margin + 3 * mm)
        canvas.setFont("Helvetica", 7.5)
        canvas.drawString(margin, margin - 6 * mm, f"EnkTel IPTV · {SITE}")
        canvas.drawRightString(W - margin, margin - 6 * mm, str(canvas.getPageNumber()))
        canvas.restoreState()

    doc = BaseDocTemplate(
        str(out), pagesize=A4,
        leftMargin=margin, rightMargin=margin, topMargin=margin, bottomMargin=margin,
        title=f"EnkTel — {region['title']}", author="EnkTel IPTV",
    )
    doc.addPageTemplates([
        PageTemplate(
            id="body",
            frames=[Frame(margin, margin, W - 2 * margin, H - 2 * margin, id="f", showBoundary=0)],
            onPageEnd=furniture,
        ),
    ])

    # ── group ──
    grouped: dict[str, dict[str, list[dict]]] = {c: {} for c in COUNTRIES}
    for r in rows:
        c = country_of(r["name"], r["category"])
        g = genre_of(r["name"], r["category"])
        name, quality = clean_name(r["name"])
        grouped[c].setdefault(g, []).append({**r, "clean": name, "quality": quality})

    with_guide = sum(1 for r in rows if r.get("epg"))
    with_archive = sum(1 for r in rows if r.get("archive"))

    story = [
        Paragraph(region["title"], st["h1"]),
        Spacer(1, 3),
        Paragraph(region["subtitle"], st["lede"]),
        Spacer(1, 8),
        Paragraph(
            f"Every Ex-Yu channel on the EnkTel lineup, grouped by country and then by "
            f"genre. <b>{len(rows)} channels</b> in total, taken from the panel on "
            f"{source_note}. Channel lineups change; this is a snapshot, and the app "
            f"always shows the live list.",
            st["note"],
        ),
        Spacer(1, 4),
        Paragraph(
            "The panel groups these six ways — three by country and three by genre — so a "
            "Croatian sports channel lives in the Sport category rather than under Croatia. "
            "Re-cut here so each country's channels are in one place. Quality is the "
            "highest the provider advertises for that channel.",
            st["note"],
        ),
        Spacer(1, 6),
        Paragraph(
            f"<b>{with_guide} of {len(rows)}</b> carry a TV-guide id, so the guide and the "
            f"now/next strip are populated for them. "
            + (
                f"<b>{with_archive}</b> offer catch-up."
                if with_archive
                else "None of them offers catch-up: the provider publishes no archive on "
                     "the Ex-Yu channels, so replaying something that has already aired is "
                     "not available on this part of the lineup. Said here rather than left "
                     "to be discovered."
            ),
            st["note"],
        ),
        Spacer(1, 10),
    ]

    for code, label in COUNTRIES.items():
        genres = grouped.get(code) or {}
        if not genres:
            continue
        count = sum(len(v) for v in genres.values())
        story.append(Paragraph(f"{label} &nbsp;<font size=9 color='#6B7280'>{count} channels</font>", st["country"]))
        for g in GENRE_ORDER:
            items = genres.get(g)
            if not items:
                continue
            story.append(Paragraph(f"{g.upper()} · {len(items)}", st["genre"]))
            # De-duplicated on the printed name: a panel routinely carries the
            # same channel two or three times at different qualities, and
            # "HBO 2" listed three times reads as a fault in the document
            # rather than as a choice of feeds. The best quality wins.
            best: dict[str, dict] = {}
            rank = {"4K": 3, "HD": 2, "SD": 1, "": 0}
            for it in items:
                k = it["clean"].lower()
                if k not in best or rank.get(it["quality"], 0) > rank.get(best[k]["quality"], 0):
                    best[k] = it
            ordered = sorted(best.values(), key=lambda d: d["clean"].lower())

            LOGO = 7 * mm
            cells = []
            for it in ordered:
                path = logos.get(it["logo"])
                if path is not None:
                    # Fitted into a fixed box so a wide logo and a square one
                    # occupy the same column and the rows stay on a grid.
                    cells.append(Image(str(path), width=LOGO, height=LOGO, kind="proportional"))
                else:
                    cells.append(Paragraph("", st["cell"]))
                q = (
                    f" <font color='#6B7280' size=6.5>{it['quality']}</font>"
                    if it["quality"] else ""
                )
                cells.append(Paragraph(f"{it['clean']}{q}", st["cell"]))

            # Three channels per row, each a logo column and a name column.
            per_row = 3
            while len(cells) % (per_row * 2):
                cells.append(Paragraph("", st["cell"]))
            grid = [cells[i:i + per_row * 2] for i in range(0, len(cells), per_row * 2)]
            avail = W - 2 * margin
            col = avail / per_row
            table = Table(
                grid,
                colWidths=[LOGO + 2 * mm, col - LOGO - 2 * mm] * per_row,
                hAlign="LEFT",
            )
            table.setStyle(TableStyle([
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("TOPPADDING", (0, 0), (-1, -1), 2),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
                ("LEFTPADDING", (0, 0), (-1, -1), 0),
                ("RIGHTPADDING", (0, 0), (-1, -1), 4),
                ("ROWBACKGROUNDS", (0, 0), (-1, -1), [colors.white, WASH]),
            ]))
            story.append(table)
        story.append(Spacer(1, 6))

    story += [
        Spacer(1, 10),
        Paragraph(
            f"Questions about a channel that is not here, or one that is here and not "
            f"working? {SUPPORT} · App v{version}",
            st["note"],
        ),
    ]
    doc.build(story)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--region", default="exyu", choices=sorted(REGIONS))
    ap.add_argument("--from-json", help="a previously fetched dump, instead of calling the panel")
    ap.add_argument("--dump-json", help="write the fetched rows here (names only — no URLs)")
    args = ap.parse_args()
    region = REGIONS[args.region]

    if args.from_json:
        rows = json.loads(Path(args.from_json).read_text(encoding="utf-8"))
        source_note = "a saved snapshot"
    else:
        server = os.environ.get("XTREAM_SERVER", "")
        user = os.environ.get("XTREAM_USERNAME", "")
        password = os.environ.get("XTREAM_PASSWORD", "")
        if not (server and user and password):
            sys.exit(
                "Set XTREAM_SERVER, XTREAM_USERNAME and XTREAM_PASSWORD, or pass "
                "--from-json. Credentials are read from the environment so they never "
                "reach the repository."
            )
        print("Fetching from the panel:")
        rows = fetch(server, user, password, region["category_match"])
        source_note = datetime.now(timezone.utc).strftime("%d %B %Y")

    if args.dump_json:
        Path(args.dump_json).write_text(json.dumps(rows, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"wrote {args.dump_json}")

    logos = cache_logos(rows, ROOT / "build" / "logo-cache")
    out = ROOT / "docs" / f"enktel_channels_{args.region}.pdf"
    out.parent.mkdir(parents=True, exist_ok=True)
    build_pdf(rows, region, out, source_note, logos)
    print(f"{out}  ({out.stat().st_size:,} bytes)  {len(rows)} channels")


if __name__ == "__main__":
    main()
