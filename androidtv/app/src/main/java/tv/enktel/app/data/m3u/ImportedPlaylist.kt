package tv.enktel.app.data.m3u

/**
 * A playlist file the viewer imported, held alongside a profile rather than
 * instead of one.
 *
 * Importing used to create a profile and switch to it. Every screen in the app
 * reads the *active* profile, so the effect of importing a twenty-channel file
 * was that the several thousand channels the viewer already had disappeared —
 * they were still in the database, under the profile that was no longer
 * active, which is worse than losing them because nothing says so. The file
 * was not added to the lineup; it replaced it.
 *
 * So an import is an attachment now. It belongs to a profile, its channels are
 * written into that profile's catalogue at sync time, and they arrive under
 * categories of their own so the viewer can still see which file they came
 * from and can still take them away again.
 */
data class ImportedPlaylist(
    /** Creation time, and the identity used to remove one. */
    val id: Long,
    /** The profile these channels are added to. */
    val profileId: Long,
    /** The document's name, which becomes the category prefix. */
    val name: String,
    /** `file://` into app storage — see [PlaylistFiles]. */
    val url: String,
    /**
     * A small, stable number owned by this attachment.
     *
     * Channels need a `streamId` that is unique within the profile, because
     * favourites are stored against it. Positional ids would do if the list of
     * attachments never changed, but removing the first of three would
     * renumber the other two and silently move every favourite they held onto
     * a different channel. A slot is assigned once, at import, and kept.
     */
    val slot: Int,
) {
    /**
     * The `streamId` for the n-th channel in this file.
     *
     * Deliberately far above anything a real source produces. An M3U profile
     * numbers its rows from 1, and panels issue stream ids in the tens of
     * thousands to low millions; starting at nine billion leaves no plausible
     * way for an attachment to land on an id the host profile is already using.
     * A collision would not fail loudly — the row key is `profileId:streamId`
     * and one channel would simply overwrite the other.
     */
    fun streamIdFor(index: Int): Long = BASE + slot.toLong() * SPAN + index

    /**
     * The category a channel of this file goes into.
     *
     * The file's own groups are kept, prefixed, so a large import stays
     * navigable instead of collapsing into one list of four hundred channels;
     * an ungrouped file just gets the one category named after itself. Either
     * way the prefix is what separates these from the channels that were
     * already there.
     */
    fun categoryFor(group: String): String {
        val g = group.trim()
        return if (g.isEmpty()) name else "$name · $g"
    }

    companion object {
        private const val BASE = 9_000_000_000L
        private const val SPAN = 1_000_000L

        /** How many channels one attachment can hold before ids would overlap. */
        val MAX_CHANNELS: Int = SPAN.toInt()

        /**
         * Encode the list for a single preferences string.
         *
         * Tab-separated because a document's name can contain very nearly
         * anything else, and a URL can contain no whitespace at all. The name
         * is stripped of tabs and newlines on the way in for that reason —
         * a record that cannot round-trip is worse than a slightly altered
         * name.
         */
        fun encode(items: List<ImportedPlaylist>): String =
            items.joinToString("\n") { p ->
                listOf(
                    p.id.toString(),
                    p.profileId.toString(),
                    sanitize(p.name),
                    p.url,
                    p.slot.toString(),
                ).joinToString("\t")
            }

        /** Decode, skipping any line that is not a whole record. */
        fun decode(raw: String?): List<ImportedPlaylist> =
            raw.orEmpty().split('\n').mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val f = line.split('\t')
                if (f.size < 5) return@mapNotNull null
                val id = f[0].toLongOrNull() ?: return@mapNotNull null
                val profileId = f[1].toLongOrNull() ?: return@mapNotNull null
                val slot = f[4].toIntOrNull() ?: return@mapNotNull null
                if (f[3].isBlank()) return@mapNotNull null
                ImportedPlaylist(id = id, profileId = profileId, name = f[2], url = f[3], slot = slot)
            }

        /**
         * The lowest slot nothing else on this profile is using.
         *
         * Reused once an attachment is removed, which is what keeps the number
         * small and bounded by how many files are attached rather than by how
         * many have ever been attached.
         */
        fun nextSlot(existing: List<ImportedPlaylist>, profileId: Long): Int {
            val taken = existing.filter { it.profileId == profileId }.map { it.slot }.toHashSet()
            var slot = 0
            while (slot in taken) slot++
            return slot
        }

        private fun sanitize(name: String): String =
            name.replace('\t', ' ').replace('\n', ' ').trim().ifBlank { "Imported playlist" }
    }
}
