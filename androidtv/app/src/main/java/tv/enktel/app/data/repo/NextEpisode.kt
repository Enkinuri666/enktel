package tv.enktel.app.data.repo

/**
 * What follows the episode now playing.
 *
 * The successor rule is not "the next row in the list I happened to be looking
 * at". A season's last episode is followed by the first episode of the next
 * season, and a viewer who reaches a finale that has a season after it should
 * roll into it rather than be told the series is over.
 *
 * Pure arithmetic over the season map, so the boundaries — the last episode of
 * a season, the last episode of the series, a season with nothing in it — can
 * be tested rather than discovered by a viewer at midnight.
 */
object NextEpisode {

    /**
     * The episode after [currentEpisodeId], or null if there is none.
     *
     * Seasons are visited in numeric order and episodes in the order the map
     * holds them, which [ContentRepository.seriesDetails] has already sorted by
     * episode number. Empty seasons are stepped over rather than ending the
     * run: a panel that lists a season it has no episodes for is common, and
     * treating it as the end of the series would strand the viewer on it.
     */
    fun after(
        seasons: Map<Int, List<EpisodeInfo>>,
        currentEpisodeId: Long,
    ): EpisodeInfo? {
        val flat = seasons.keys.sorted().flatMap { seasons[it].orEmpty() }
        val idx = flat.indexOfFirst { it.id == currentEpisodeId }
        if (idx < 0) return null
        return flat.getOrNull(idx + 1)
    }

    /** "S2 E4 · The Bells" — what the countdown card announces. */
    fun label(e: EpisodeInfo): String = "S${e.season} E${e.episode} · ${e.title}"

    /** "Thrones S2E4 · The Bells" — what the player shows as its own title. */
    fun title(seriesName: String, e: EpisodeInfo): String =
        "$seriesName S${e.season}E${e.episode} · ${e.title}"
}
