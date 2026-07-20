package tv.enktel.app.data.repo

import kotlinx.coroutines.flow.Flow
import tv.enktel.app.data.db.WatchlistDao
import tv.enktel.app.data.db.WatchlistItem

class WatchlistRepository(private val dao: WatchlistDao) {
    fun all(profileId: Long): Flow<List<WatchlistItem>> = dao.all(profileId)
    fun ofKind(profileId: Long, kind: String): Flow<List<WatchlistItem>> = dao.ofKind(profileId, kind)
    fun isSavedFlow(profileId: Long, kind: String, refId: Long): Flow<Boolean> =
        dao.isSavedFlow("$profileId:$kind:$refId")

    suspend fun toggle(profileId: Long, kind: String, refId: Long, name: String, poster: String) {
        val key = "$profileId:$kind:$refId"
        if (dao.isSaved(key)) dao.remove(key)
        else dao.add(WatchlistItem(key = key, profileId = profileId, kind = kind, refId = refId, name = name, poster = poster))
    }
}
