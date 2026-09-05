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

## Why the HTML looks dated

Because email clients are. Tables rather than flexbox, inline styles rather
than a stylesheet, no web fonts and no external images: Outlook renders with
Word's engine, Gmail strips `<style>` from forwarded mail, and a good half of
clients block remote images until asked. Everything here survives that. It is
not how anyone would build a web page in 2026, and an email is not a web page.
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

# Brand, matched to the apps rather than approximated.
INK = "#0A0E17"
SURFACE = "#121826"
BRAND = "#3B9DFF"
BRAND_DEEP = "#1B6AE5"
BODY_TEXT = "#1F2937"
MUTED = "#6B7280"
HAIRLINE = "#E5E7EB"
PAPER = "#FFFFFF"
WASH = "#F5F7FB"


def button(href: str, label: str) -> str:
    """A link that looks like a button without needing CSS to do it.

    Padding on the anchor, background on the cell: Outlook drops padding from
    anchors, every other client honours it, and the cell colour means the shape
    is right in both.
    """
    return (
        f'<table role="presentation" cellpadding="0" cellspacing="0" border="0">'
        f'<tr><td align="center" bgcolor="{BRAND_DEEP}" '
        f'style="border-radius:6px;">'
        f'<a href="{href}" '
        f'style="display:inline-block;padding:11px 22px;font-family:Arial,Helvetica,sans-serif;'
        f'font-size:14px;font-weight:bold;color:#ffffff;text-decoration:none;">'
        f'{label}</a></td></tr></table>'
    )


def device_row(title: str, blurb: str, href: str, cta: str, last=False) -> str:
    border = "" if last else f"border-bottom:1px solid {HAIRLINE};"
    return f"""
        <tr><td style="padding:18px 0;{border}">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
            <tr>
              <td style="font-family:Arial,Helvetica,sans-serif;font-size:15px;
                         font-weight:bold;color:{BODY_TEXT};padding-bottom:4px;">{title}</td>
            </tr>
            <tr>
              <td style="font-family:Arial,Helvetica,sans-serif;font-size:13px;
                         line-height:19px;color:{MUTED};padding-bottom:12px;">{blurb}</td>
            </tr>
            <tr><td>{button(href, cta)}</td></tr>
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

    <!-- Header -->
    <tr><td bgcolor="{INK}" style="background-color:{INK};padding:26px 30px;">
      <span style="font-family:Arial,Helvetica,sans-serif;font-size:19px;font-weight:bold;
                   color:#ffffff;letter-spacing:0.5px;">ENK<span style="color:{BRAND};">TEL</span></span>
      <span style="font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#7C89A6;
                   letter-spacing:1.5px;">&nbsp; IPTV</span>
    </td></tr>

    <!-- Lede -->
    <tr><td style="padding:30px 30px 6px 30px;">
      <h1 style="margin:0 0 10px 0;font-family:Arial,Helvetica,sans-serif;font-size:24px;
                 line-height:30px;color:{BODY_TEXT};">Your subscription is live</h1>
      <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:14px;
                line-height:21px;color:{MUTED};">
        One login works on everything you own — TV, phone, tablet, computer and
        browser. There is no account to create. The details below are all you need.
      </p>
    </td></tr>

    <!-- Credentials -->
    <tr><td style="padding:22px 30px 6px 30px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
             bgcolor="{WASH}" style="background-color:{WASH};border-radius:8px;">
        <tr><td style="padding:18px 20px;">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                 style="font-family:Arial,Helvetica,sans-serif;font-size:14px;color:{BODY_TEXT};">
            <tr>
              <td width="34%" style="padding:5px 0;color:{MUTED};font-size:13px;">Username</td>
              <td style="padding:5px 0;font-weight:bold;">{{{{USERNAME}}}}</td>
            </tr>
            <tr>
              <td style="padding:5px 0;color:{MUTED};font-size:13px;">Password</td>
              <td style="padding:5px 0;font-weight:bold;">{{{{PASSWORD}}}}</td>
            </tr>
            <tr>
              <td style="padding:5px 0;color:{MUTED};font-size:13px;">Server address</td>
              <td style="padding:5px 0;font-weight:bold;">{{{{SERVER}}}}</td>
            </tr>
            <tr>
              <td style="padding:5px 0;color:{MUTED};font-size:13px;">Plan</td>
              <td style="padding:5px 0;">{{{{PLAN}}}} &middot; runs until {{{{EXPIRES}}}}</td>
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
      <h2 style="margin:0 0 4px 0;font-family:Arial,Helvetica,sans-serif;font-size:16px;
                 color:{BODY_TEXT};">Start here</h2>
      <p style="margin:0 0 14px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                line-height:19px;color:{MUTED};">
        The web player needs nothing installed, so it is the quickest way to check
        everything works. If it plays here, your subscription is fine — anything
        else is that device's setup.
      </p>
      {button(WEB_PLAYER, "Open the web player")}
    </td></tr>

    <!-- Devices -->
    <tr><td style="padding:8px 30px 0 30px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        {device_row(
            "Phone &amp; tablet",
            "The full app, including downloads you can watch with no signal at all.",
            mobile_apk(version), "Download for Android")}
        {device_row(
            "TV, Fire Stick &amp; Android TV",
            "The same app, laid out for a remote. On a Fire TV install "
            "<b>Downloader</b> first, then enter the address below and pick the file "
            "whose name contains <b>-tv-</b>.",
            RELEASES, "All downloads")}
        {device_row(
            "Windows PC",
            "A desktop player, and the other half of Send to PC — it pulls finished "
            "downloads off your phone over your own Wi-Fi.<br><br>"
            "Windows will warn that it does not recognise the publisher. Choose "
            "<b>More info</b>, then <b>Run anyway</b>. That warning is about our "
            "certificate, not about the file.",
            RELEASES, "Get the installer", last=True)}
      </table>
    </td></tr>

    <!-- Guide -->
    <tr><td style="padding:22px 30px 0 30px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
             style="border:1px solid {HAIRLINE};border-radius:8px;">
        <tr><td style="padding:18px 20px;">
          <p style="margin:0 0 6px 0;font-family:Arial,Helvetica,sans-serif;font-size:15px;
                    font-weight:bold;color:{BODY_TEXT};">The illustrated guide</p>
          <p style="margin:0 0 14px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                    line-height:19px;color:{MUTED};">
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
             bgcolor="{WASH}" style="background-color:{WASH};border-radius:8px;">
        <tr><td style="padding:18px 20px;">
          <p style="margin:0 0 6px 0;font-family:Arial,Helvetica,sans-serif;font-size:15px;
                    font-weight:bold;color:{BODY_TEXT};">When it is time to renew</p>
          <p style="margin:0 0 14px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                    line-height:19px;color:{MUTED};">
            Your plan runs until <b>{{{{EXPIRES}}}}</b>. Renewing is a message rather
            than a form: the link below opens a chat with your username already in
            it, we take payment there and extend the line you are already using.
            Nothing to reinstall, and no settings to enter again.
          </p>
          {button(renew, "Renew my subscription")}
        </td></tr>
      </table>
    </td></tr>

    <!-- Footer -->
    <tr><td style="padding:26px 30px 30px 30px;">
      <p style="margin:0 0 6px 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;
                line-height:20px;color:{MUTED};">
        Stuck? Reply to this email, or use
        <a href="{SUPPORT}" style="color:{BRAND_DEEP};">our contact page</a>. Tell us your
        username and which device you are on — never your password.
      </p>
      <p style="margin:14px 0 0 0;font-family:Arial,Helvetica,sans-serif;font-size:11px;
                line-height:17px;color:#9AA4BF;">
        EnkTel IPTV &middot; <a href="{SITE}" style="color:#9AA4BF;">enktel.tv</a><br>
        App version {version}
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

Keep this email. Nobody at EnkTel will ever ask you for your password.


START HERE

The web player needs nothing installed, so it is the quickest way to check
everything works. If it plays here, your subscription is fine - anything else
is that device's setup.

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
