/**
 * Telling a radio station from a television channel.
 *
 * A playlist that tags its stations `radio="true"` needs none of this — the
 * publisher already answered. This is for the feeds that don't, where the name
 * is the only evidence there is.
 *
 * The trap is that "Radio" in a channel name frequently belongs to a
 * *television* broadcaster: `Radio Javan TV`, `Radio y Televisión Martí`,
 * `Radio Tele Sentinel`. Matching the word alone files all three as radio and
 * empties them out of Live TV, which is the same mistake as reading a
 * "Movies" genre bucket as VOD, only in reverse. So a television marker
 * anywhere in the name settles it before the radio hints are considered.
 */

/**
 * Words that mean this is television, whatever else the name says.
 *
 * `tele` is included for the Romance-language broadcasters that abbreviate it
 * that way; on its own it is never a radio marker.
 */
const TELEVISION =
  /\b(tv|tele|telly|television|televisi[oó]n|televisione|telewizja|fernsehen|canal|kanal)\b/i;

/**
 * Radio markers.
 *
 * Word-bounded on purpose. `iHeartRadio` is a television countdown show and
 * `CFM TV` is a station: neither has a boundary before its "radio"/"fm", so
 * neither matches, which is the behaviour we want.
 */
const RADIO_HINT = /\b(fm|am)\b|\bradio\b|\bradyo\b|\bstereo\b/i;

/** A group title that says radio outright is the publisher telling us. */
const RADIO_GROUP = /\bradio\b/i;

/**
 * @param {{name?: string, tvgName?: string, group?: string, radio?: boolean}} entry
 * @returns {boolean}
 */
export function looksLikeRadio(entry) {
  // The publisher's own flag always wins; nothing below can override it.
  if (entry?.radio) return true;

  const name = `${entry?.name ?? ''} ${entry?.tvgName ?? ''}`.trim();
  if (TELEVISION.test(name)) return false;

  if (RADIO_GROUP.test(String(entry?.group ?? ''))) return true;
  return RADIO_HINT.test(name);
}
