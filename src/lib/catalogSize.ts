/**
 * How big the subscription is, in the words the site uses.
 *
 * Its own module rather than a constant inside channels.ts: these labels are
 * quoted in pricing copy, the comparison table and page metadata, and every
 * one of those would otherwise pull the seven-hundred-line channel catalogue
 * into its bundle — including the checkout route, which has no use for it.
 *
 * One home matters because the numbers are advertising claims. The site said
 * 35,000+ channels in the chat assistant and 10,000+ in the comparison table
 * at the same time, which is the kind of thing a reader notices and a
 * competitor screenshots.
 */
export const CHANNEL_COUNT = 35000;
export const CHANNEL_COUNT_LABEL = "35,000+";
export const VOD_COUNT_LABEL = "200,000+";
export const SERIES_COUNT_LABEL = "35,000+";
