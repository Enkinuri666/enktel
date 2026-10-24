#!/usr/bin/env python3
"""
Builds the welcome email a new subscriber is sent — HTML and plain text.

The guide told people to use "the link in your welcome email" for months while
no such email existed. This is that email, generated from the same
`enktel_links` the guide uses, so the two cannot disagree about an address.

    python3 scripts/build-welcome-email.py
    python3 scripts/build-welcome-email.py --out .

Writes `docs/welcome-email.html` and `docs/welcome-email.txt`. Both carry
merge fields for whatever sends them:

    {{USERNAME}}  {{PASSWORD}}  {{SERVER}}  {{EXPIRES}}  {{PLAN}}

The double-brace form is what Mailchimp, Brevo, Resend and most CRMs read.
`--fill` swaps in sample values instead, for looking at it before it goes out.

## Why the HTML is built the way it is

Email clients are twenty years behind. Tables rather than flexbox, inline
styles rather than a stylesheet, no web fonts and no external images: Outlook
renders with Word's engine, Gmail strips `<style>` from forwarded mail, and a
good half of clients block remote images until asked. Everything here survives
that.

Which is a constraint on the technique, not on the result. The colour is the
app's own — the Deep Space palette out of EnktelTheme — and it is carried by
background colours, borders and type rather than by images or gradients, so it
arrives intact in a client from 2007. No remote image means nothing to block,
and nothing that shows as a grey box with a cross in it on first open.
"""
import argparse
import html
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from enktel_links import (  # noqa: E402
    DEFAULT_SERVER, RELEASES, SITE, SUPPORT, WEB_PLAYER,
    app_version, mobile_apk, renew_url, tv_apk, welcome_guide,
)

ROOT = Path(__file__).resolve().parent.parent

SUBJECT = "Your EnkTel subscription is live — here's how to start watching"

SAMPLE = {
    "USERNAME": "enktel_demo",
    "PASSWORD": "8fj3kd92",
    "SERVER": DEFAULT_SERVER,
    "EXPIRES": "5 October 2026",
    "PLAN": "3 months",
}

# Brand, taken from the app's own palette rather than approximated.
#
# These are the Deep Space values in EnktelTheme — the near-black the app
# paints behind everything, its neon cyan, its purple. The email used to run
# on a generic blue that appeared nowhere in the product, so the first thing a
# new subscriber saw looked like a different company's mail. A welcome email
# is the only picture of the app somebody has before they install it.
INK = "#0B0E14"          # app background
SURFACE = "#121824"      # app surface — the credentials card
SURFACE_HIGH = "#1A2332" # a panel lifted off that surface
NEON = "#00F0FF"         # app primary
NEON_DEEP = "#00A8B5"    # the same, dark enough for white text on it
PURPLE = "#7B2CBF"       # app secondary
GREEN = "#10B981"        # app "ok"
# Not from the app, and deliberately: the desktop card needs an accent that is
# neither of its neighbours, and green is spoken for — it means "your plan is
# fine" in the renewal panel below. Slate is the family the app's dim text
# already comes from, so it sits with the rest rather than beside it.
SLATE = "#475569"
TEXT_ON_DARK = "#F8FAFC"
DIM_ON_DARK = "#94A3B8"

# The light half. A whole message in near-black reads as a system alert, and
# Gmail's dark mode inverts unpredictable parts of it; the page stays paper,
# and the dark is spent where it carries meaning.
BODY_TEXT = "#1F2937"
MUTED = "#6B7280"
HAIRLINE = "#E5E7EB"
PAPER = "#FFFFFF"
WASH = "#F5F7FB"

# Kept as aliases so link colours and anything else reading them still work.
BRAND = NEON
BRAND_DEEP = NEON_DEEP


def button(href: str, label: str, bg: str = NEON, fg: str = INK) -> str:
    """A link that looks like a button without needing CSS to do it.

    Padding on the anchor, background on the cell: Outlook drops padding from
    anchors, every other client honours it, and the cell colour means the shape
    is right in both.

    Ink on neon by default, which is how the app draws its own primary action —
    and the right way round for contrast, because white on #00F0FF is barely
    legible while near-black on it is unmissable.
    """
    return (
        f'<table role="presentation" cellpadding="0" cellspacing="0" border="0">'
        f'<tr><td align="center" bgcolor="{bg}" '
        f'style="border-radius:6px;">'
        f'<a href="{href}" '
        f'style="display:inline-block;padding:12px 24px;font-family:Arial,Helvetica,sans-serif;'
        f'font-size:14px;font-weight:bold;color:{fg};text-decoration:none;letter-spacing:0.2px;">'
        f'{label}</a></td></tr></table>'
    )


def neon_rule() -> str:
    """The three-colour hairline under the masthead.

    A gradient would be one line of CSS and Gmail strips background-image from
    most contexts, so it is three cells of flat colour instead — which renders
    identically everywhere, including in Word's engine.
    """
    return (
        f'<table role="presentation" width="100%" cellpadding="0" cellspacing="0" '
        f'border="0"><tr>'
        f'<td height="4" bgcolor="{NEON}" style="height:4px;line-height:4px;font-size:0;">&nbsp;</td>'
        f'<td height="4" bgcolor="{NEON_DEEP}" style="height:4px;line-height:4px;font-size:0;">&nbsp;</td>'
        f'<td height="4" bgcolor="{PURPLE}" style="height:4px;line-height:4px;font-size:0;">&nbsp;</td>'
        f'</tr></table>'
    )


def device_row(title: str, blurb: str, href: str, cta: str, accent: str = NEON_DEEP,
               kicker: str = "", last=False) -> str:
    """One device, as a card with a colour of its own.

    A hairline between three identical blocks made the three ways to watch read
    as one long list of text. Each gets a tinted spine and a coloured kicker
    instead, so the eye can find the one device it came for.
    """
    gap = "" if last else "padding-bottom:12px;"
    return f"""
        <tr><td style="{gap}">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                 bgcolor="{WASH}" style="background-color:{WASH};border-radius:8px;
                        border-left:4px solid {accent};">
            <tr><td style="padding:16px 18px;">
              <p style="margin:0 0 3px 0;font-family:Arial,Helvetica,sans-serif;font-size:10px;
                        font-weight:bold;letter-spacing:1.4px;color:{accent};
                        text-transform:uppercase;">{kicker}</p>
              <p style="margin:0 0 4px 0;font-family:Arial,Helvetica,sans-serif;font-size:15px;
                        font-weight:bold;color:{BODY_TEXT};">{title}</p>
              <p style="margin:0 0 13px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                        line-height:19px;color:{MUTED};">{blurb}</p>
              {button(href, cta, bg=accent, fg="#ffffff")}
            </td></tr>
          </table>
        </td></tr>"""


def build_html(version: str) -> str:
    guide = welcome_guide(version)
    renew = renew_url()
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(SUBJECT)}</title>
</head>
<body style="margin:0;padding:0;background-color:{WASH};">

<!-- Preheader: the grey line a client shows beside the subject. Hidden in the
     body itself, so it does not appear twice. -->
<div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">
  Your username, your password, and the four ways to watch.
</div>

<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
       bgcolor="{WASH}" style="background-color:{WASH};">
<tr><td align="center" style="padding:24px 12px;">

  <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0"
         style="width:600px;max-width:100%;background-color:{PAPER};border-radius:10px;overflow:hidden;">

    <!-- Masthead and hero, in one dark band.
         The headline lives in the dark rather than under it: it is the app's
         own background, so the message opens looking like the thing it is
         about instead of like a receipt. -->
    <tr><td bgcolor="{INK}" style="background-color:{INK};padding:28px 30px 30px 30px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td style="font-family:Arial,Helvetica,sans-serif;font-size:19px;font-weight:bold;
                     color:{TEXT_ON_DARK};letter-spacing:0.5px;">ENK<span
                     style="color:{NEON};">TEL</span><span
                     style="font-size:11px;color:{DIM_ON_DARK};letter-spacing:1.5px;">&nbsp; IPTV</span></td>
          <td align="right">
            <!-- The live pill. Colour doing a job: it says the account is on,
                 which is the one fact the whole message exists to deliver. -->
            <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
              <td bgcolor="{SURFACE_HIGH}" style="background-color:{SURFACE_HIGH};border-radius:999px;
                         padding:5px 12px;font-family:Arial,Helvetica,sans-serif;font-size:10px;
                         font-weight:bold;letter-spacing:1.2px;color:{GREEN};">&#9679; LIVE</td>
            </tr></table>
          </td>
        </tr>
      </table>
      <h1 style="margin:22px 0 10px 0;font-family:Arial,Helvetica,sans-serif;font-size:28px;
                 line-height:34px;color:{TEXT_ON_DARK};">Your subscription<br>is live</h1>
      <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:14px;
                line-height:22px;color:{DIM_ON_DARK};">
        One login works on everything you own — TV, phone, tablet, computer and
        browser. There is no account to create. The details below are all you need.
      </p>
    </td></tr>
    <tr><td style="font-size:0;line-height:0;">{neon_rule()}</td></tr>

    <!-- Credentials.
         The one block somebody comes back to this email for, so it is the one
         block that looks different from everything else: the app's own surface
         colour, with the labels in neon and the values in white. -->
    <tr><td style="padding:26px 30px 6px 30px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
             bgcolor="{SURFACE}" style="background-color:{SURFACE};border-radius:10px;">
        <tr><td style="padding:20px 22px;">
          <p style="margin:0 0 12px 0;font-family:Arial,Helvetica,sans-serif;font-size:10px;
                    font-weight:bold;letter-spacing:1.4px;color:{NEON};">YOUR DETAILS</p>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                 style="font-family:Arial,Helvetica,sans-serif;font-size:15px;color:{TEXT_ON_DARK};">
            <tr>
              <td width="34%" style="padding:6px 0;color:{DIM_ON_DARK};font-size:13px;">Username</td>
              <td style="padding:6px 0;font-weight:bold;">{{{{USERNAME}}}}</td>
            </tr>
            <tr>
              <td style="padding:6px 0;color:{DIM_ON_DARK};font-size:13px;">Password</td>
              <td style="padding:6px 0;font-weight:bold;">{{{{PASSWORD}}}}</td>
            </tr>
            <tr>
              <td style="padding:6px 0;color:{DIM_ON_DARK};font-size:13px;">Server address</td>
              <td style="padding:6px 0;font-weight:bold;">{{{{SERVER}}}}</td>
            </tr>
            <tr>
              <td style="padding:6px 0;color:{DIM_ON_DARK};font-size:13px;">Plan</td>
              <td style="padding:6px 0;color:{TEXT_ON_DARK};">{{{{PLAN}}}} &middot; runs until <span style="color:{GREEN};font-weight:bold;">{{{{EXPIRES}}}}</span></td>
            </tr>
          </table>
          <!-- The one-paste line.
               Composed from the three fields above rather than being a fourth
               merge field, so it cannot disagree with them. Our own apps no
               longer need it — they ask for the username and password and
               know the server — but it is what a third-party player wants,
               and the apps still accept it if someone pastes it anyway; see
               SetupLink. -->
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                 style="font-family:Arial,Helvetica,sans-serif;font-size:14px;color:{TEXT_ON_DARK};
                        border-top:1px solid {SURFACE_HIGH};margin-top:14px;">
            <tr>
              <td style="padding:14px 0 6px 0;color:{DIM_ON_DARK};font-size:13px;">
                Setup link &mdash; for VLC and other players, and for anyone
                setting up somewhere that asks for a link
              </td>
            </tr>
            <tr>
              <td bgcolor="{INK}" style="background-color:{INK};border-radius:6px;
                         padding:10px 12px;font-family:Consolas,Menlo,monospace;font-size:12px;
                         line-height:18px;color:{NEON};word-break:break-all;">
                {{{{SERVER}}}}/get.php?username={{{{USERNAME}}}}&amp;password={{{{PASSWORD}}}}&amp;type=m3u_plus&amp;output=ts
              </td>
            </tr>
          </table>
        </td></tr>
      </table>
      <p style="margin:10px 0 0 0;font-family:Arial,Helvetica,sans-serif;font-size:12px;
                line-height:18px;color:{MUTED};">
        Keep this email. Nobody at EnkTel will ever ask you for your password.
      </p>
    </td></tr>

    <!-- Start here -->
    <tr><td style="padding:24px 30px 0 30px;">
      <p style="margin:0 0 3px 0;font-family:Arial,Helvetica,sans-serif;font-size:10px;
                font-weight:bold;letter-spacing:1.4px;color:{NEON_DEEP};">STEP ONE</p>
      <h2 style="margin:0 0 4px 0;font-family:Arial,Helvetica,sans-serif;font-size:18px;
                 color:{BODY_TEXT};">Start here</h2>
      <p style="margin:0 0 14px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                line-height:19px;color:{MUTED};">
        Install the app, open it, and type the <b>username</b> and <b>password</b>
        above. The server is already set, so there is nothing else to fill in.<br><br>
        Or try the web player first: it needs nothing installed, so it is the
        quickest way to check everything works. If it plays there, your
        subscription is fine &mdash; anything else is that device's setup.
      </p>
      {button(WEB_PLAYER, "Open the web player")}
    </td></tr>

    <!-- Devices -->
    <tr><td style="padding:26px 30px 0 30px;">
      <p style="margin:0 0 3px 0;font-family:Arial,Helvetica,sans-serif;font-size:10px;
                font-weight:bold;letter-spacing:1.4px;color:{PURPLE};">EVERYWHERE ELSE</p>
      <h2 style="margin:0 0 14px 0;font-family:Arial,Helvetica,sans-serif;font-size:18px;
                 color:{BODY_TEXT};">On your other devices</h2>
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        {device_row(
            "Phone &amp; tablet",
            "The full app, including downloads you can watch with no signal at all.",
            mobile_apk(version), "Download for Android",
            accent=NEON_DEEP, kicker="Android")}
        {device_row(
            "TV, Fire Stick &amp; Android TV",
            "The same app, laid out for a remote. On a Fire TV install "
            "<b>Downloader</b> first, then enter the address below and pick the file "
            "whose name contains <b>-tv-</b>.",
            RELEASES, "All downloads",
            accent=PURPLE, kicker="Big screen")}
        {device_row(
            "Windows PC",
            "A desktop player, and the other half of Send to PC — it pulls finished "
            "downloads off your phone over your own Wi-Fi.<br><br>"
            "Windows will warn that it does not recognise the publisher. Choose "
            "<b>More info</b>, then <b>Run anyway</b>. That warning is about our "
            "certificate, not about the file.",
            RELEASES, "Get the installer",
            accent=SLATE, kicker="Desktop", last=True)}
      </table>
    </td></tr>

    <!-- Guide -->
    <tr><td style="padding:22px 30px 0 30px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
             bgcolor="{INK}" style="background-color:{INK};border-radius:10px;">
        <tr><td style="padding:20px 22px;">
          <p style="margin:0 0 3px 0;font-family:Arial,Helvetica,sans-serif;font-size:10px;
                    font-weight:bold;letter-spacing:1.4px;color:{NEON};">EVERY SCREEN, EXPLAINED</p>
          <p style="margin:0 0 6px 0;font-family:Arial,Helvetica,sans-serif;font-size:17px;
                    font-weight:bold;color:{TEXT_ON_DARK};">The illustrated guide</p>
          <p style="margin:0 0 15px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                    line-height:20px;color:{DIM_ON_DARK};">
            Every screen of every app, with the setup steps for each one and the
            answers to the six things that usually go wrong.
          </p>
          {button(guide, "Open the guide (PDF)")}
        </td></tr>
      </table>
    </td></tr>

    <!-- Renewing.

         Here rather than in a separate email nearer the date, because this is
         the message people keep. It is also the only place the answer to "do I
         lose my setup?" can be given before they have started worrying about
         it. -->
    <tr><td style="padding:22px 30px 0 30px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
             bgcolor="{WASH}" style="background-color:{WASH};border-radius:8px;
                    border-left:4px solid {GREEN};">
        <tr><td style="padding:18px 20px;">
          <p style="margin:0 0 3px 0;font-family:Arial,Helvetica,sans-serif;font-size:10px;
                    font-weight:bold;letter-spacing:1.4px;color:{GREEN};">NO RUSH</p>
          <p style="margin:0 0 6px 0;font-family:Arial,Helvetica,sans-serif;font-size:15px;
                    font-weight:bold;color:{BODY_TEXT};">When it is time to renew</p>
          <p style="margin:0 0 14px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                    line-height:19px;color:{MUTED};">
            Your plan runs until <b>{{{{EXPIRES}}}}</b>. Renewing is a message rather
            than a form: the link below opens a chat with your username already in
            it, we take payment there and extend the line you are already using.
            Nothing to reinstall, and no settings to enter again.
          </p>
          {button(renew, "Renew my subscription", bg=GREEN, fg="#ffffff")}
        </td></tr>
      </table>
    </td></tr>

    <!-- Footer. Dark, to close the message the way it opened. -->
    <tr><td style="padding:26px 30px 0 30px;">
      <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                line-height:20px;color:{MUTED};">
        Stuck? Reply to this email, or use
        <a href="{SUPPORT}" style="color:{NEON_DEEP};font-weight:bold;">our contact page</a>.
        Tell us your username and which device you are on — never your password.
      </p>
    </td></tr>
    <tr><td style="padding:22px 0 0 0;font-size:0;line-height:0;">{neon_rule()}</td></tr>
    <tr><td bgcolor="{INK}" style="background-color:{INK};padding:20px 30px 24px 30px;">
      <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                font-weight:bold;letter-spacing:0.5px;color:{TEXT_ON_DARK};">ENK<span
                style="color:{NEON};">TEL</span></p>
      <p style="margin:6px 0 0 0;font-family:Arial,Helvetica,sans-serif;font-size:11px;
                line-height:17px;color:{DIM_ON_DARK};">
        <a href="{SITE}" style="color:{DIM_ON_DARK};">enktel.tv</a> &middot; App version {version}
      </p>
    </td></tr>

  </table>

</td></tr>
</table>
</body>
</html>
"""


def build_text(version: str) -> str:
    """The plain-text alternative.

    Not optional. A message with no text part is scored as spam by most
    filters, and a text part that just says "view this in a browser" is worse
    than none — it is the version some people will actually read.
    """
    return f"""Your EnkTel subscription is live

One login works on everything you own - TV, phone, tablet, computer and
browser. There is no account to create. The details below are all you need.

  Username:       {{{{USERNAME}}}}
  Password:       {{{{PASSWORD}}}}
  Server address: {{{{SERVER}}}}
  Plan:           {{{{PLAN}}}}, runs until {{{{EXPIRES}}}}

Setup link - for VLC and other players, and anywhere that asks for a link:

  {{{{SERVER}}}}/get.php?username={{{{USERNAME}}}}&password={{{{PASSWORD}}}}&type=m3u_plus&output=ts

The EnkTel apps do not need it - they ask for the username and password and
already know the server. Keep this email. Nobody at EnkTel will ever ask you
for your password.


START HERE

Install the app, open it, and type the username and password above. The
server is already set, so there is nothing else to fill in.

Or try the web player first: it needs nothing installed, so it is the quickest
way to check everything works. If it plays there, your subscription is fine -
anything else is that device's setup.

  {WEB_PLAYER}


ON YOUR OTHER DEVICES

Phone & tablet - the full app, including downloads you can watch with no
signal at all:

  {mobile_apk(version)}

TV, Fire Stick & Android TV - the same app, laid out for a remote. On a Fire
TV install "Downloader" from the Amazon appstore first, then enter the address
below and pick the file whose name contains -tv- (the -mobile- one is for
phones and will look wrong on a television):

  {RELEASES}

Windows PC - a desktop player, and the other half of Send to PC, which pulls
finished downloads off your phone over your own Wi-Fi:

  {RELEASES}

Windows will warn that it does not recognise the publisher. Choose "More info",
then "Run anyway". That warning is about our certificate, not about the file.


THE ILLUSTRATED GUIDE

Every screen of every app, with the setup steps for each one and the answers
to the six things that usually go wrong:

  {welcome_guide(version)}


WHEN IT IS TIME TO RENEW

Your plan runs until {{{{EXPIRES}}}}. Renewing is a message rather than a form:
the link below opens a chat with your username already in it, we take payment
there and extend the line you are already using. Nothing to reinstall, and no
settings to enter again.

  {renew_url()}


Stuck? Reply to this email, or use {SUPPORT}
Tell us your username and which device you are on, never your password.

EnkTel IPTV - {SITE}
App version {version}
"""


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", default=str(ROOT / "docs"),
                    help="directory to write the email into")
    ap.add_argument("--fill", action="store_true",
                    help="substitute sample values for the merge fields, to preview it")
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    version, _ = app_version()

    html_body = build_html(version)
    text_body = build_text(version)

    if args.fill:
        for k, v in SAMPLE.items():
            html_body = html_body.replace("{{" + k + "}}", v)
            text_body = text_body.replace("{{" + k + "}}", v)

    suffix = "-preview" if args.fill else ""
    paths = [
        (out / f"welcome-email{suffix}.html", html_body),
        (out / f"welcome-email{suffix}.txt", text_body),
    ]
    for path, content in paths:
        path.write_text(content, encoding="utf-8")
        print(f"{path}  ({path.stat().st_size:,} bytes)")

    print(f"\nSubject: {SUBJECT}")
    if not args.fill:
        print("Merge fields: " + "  ".join("{{" + k + "}}" for k in SAMPLE))


if __name__ == "__main__":
    main()
