// v1.26.0 port from android — one-shot Discord webhook announcer for the
// "🎧 Share to <voice channel>" button on the player screens. Sibling to
// the auto-publisher on mobile; here we go straight to the webhook via a
// fetch when the user actually presses the button.

export type SharePayload =
  | { kind: 'vod'; title: string; year?: number; poster?: string; genre?: string }
  | { kind: 'live'; channelName: string; logo?: string; programTitle?: string }
  | { kind: 'sport'; eventTitle: string; league?: string; channelName?: string; isLive?: boolean };

const LIVE_COLOR = 0xef4444;
const VOD_COLOR = 0x34d399;
const SPORT_COLOR = 0x3b9dff;

function renderEmbed(payload: SharePayload, voice: string) {
  if (payload.kind === 'vod') {
    const yearBit = payload.year ? ` (${payload.year})` : '';
    const genreBit = payload.genre ? `\n${payload.genre}` : '';
    return {
      title: `🎬 Now streaming in ${voice}`,
      description: `**${payload.title}**${yearBit}${genreBit}\n\n_Join the voice channel to watch along._`,
      color: VOD_COLOR,
      thumbnail: payload.poster ? { url: payload.poster } : undefined,
    };
  }
  if (payload.kind === 'live') {
    const progBit = payload.programTitle ? `\n${payload.programTitle}` : '';
    return {
      title: `📺 Now streaming Live TV in ${voice}`,
      description: `**${payload.channelName}**${progBit}\n\n_Join the voice channel to watch along._`,
      color: LIVE_COLOR,
      thumbnail: payload.logo ? { url: payload.logo } : undefined,
    };
  }
  const prefix = payload.isLive ? '🔴 LIVE · ' : '';
  const league = payload.league ? `\n${payload.league}` : '';
  const chan = payload.channelName ? `\non ${payload.channelName}` : '';
  return {
    title: `⚽ Now streaming Sports in ${voice}`,
    description: `${prefix}**${payload.eventTitle}**${league}${chan}\n\n_Join the voice channel to watch along._`,
    color: SPORT_COLOR,
    thumbnail: undefined,
  };
}

export async function shareToDiscord(
  webhookUrl: string,
  voiceChannel: string,
  payload: SharePayload,
): Promise<{ ok: true } | { ok: false; error: string }> {
  if (!webhookUrl) return { ok: false, error: 'No Discord webhook configured' };
  const embed = renderEmbed(payload, voiceChannel);
  try {
    const res = await fetch(webhookUrl, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        content: '@here — come watch!',
        // Explicit empty mention parse so @here doesn't actually ping the role.
        // The visible token is preserved in the message body for readability.
        allowed_mentions: { parse: [] },
        embeds: [embed],
      }),
    });
    if (!res.ok) return { ok: false, error: `Discord returned ${res.status}` };
    return { ok: true };
  } catch (e: unknown) {
    return { ok: false, error: e instanceof Error ? e.message : String(e) };
  }
}
