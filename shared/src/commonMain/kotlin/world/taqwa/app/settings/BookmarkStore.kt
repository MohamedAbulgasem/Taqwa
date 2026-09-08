package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** An ayah the reader kept (spec 2b §2.2). */
data class Bookmark(val surah: Int, val ayah: Int, val createdAt: Long)

/**
 * Bookmarks in the app's DataStore as a string set (spec 2b §2.2): hundreds at most, so no
 * database; and separate from the bundled Quran database, which is replaced wholesale on upgrade.
 * [now] is injectable so tests control the ordering clock.
 */
class BookmarkStore(
    private val store: DataStore<Preferences>,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val bookmarks: Flow<List<Bookmark>> = store.data.map { prefs -> parse(prefs[SettingsKeys.QURAN_BOOKMARKS].orEmpty()) }

    /** Adds the ayah if absent, removes it if present; returns true when it is now bookmarked. */
    suspend fun toggle(surah: Int, ayah: Int): Boolean {
        var added = false
        store.edit { prefs ->
            val current = parse(prefs[SettingsKeys.QURAN_BOOKMARKS].orEmpty())
            val existing = current.firstOrNull { it.surah == surah && it.ayah == ayah }
            val next = if (existing == null) {
                added = true
                current + Bookmark(surah, ayah, now())
            } else {
                current - existing
            }
            prefs[SettingsKeys.QURAN_BOOKMARKS] = next.map { encode(it) }.toSet()
        }
        return added
    }

    suspend fun remove(surah: Int, ayah: Int) {
        store.edit { prefs ->
            val current = parse(prefs[SettingsKeys.QURAN_BOOKMARKS].orEmpty())
            prefs[SettingsKeys.QURAN_BOOKMARKS] = current.filterNot { it.surah == surah && it.ayah == ayah }.map { encode(it) }.toSet()
        }
    }

    private fun encode(b: Bookmark) = "${b.surah}:${b.ayah}:${b.createdAt}"

    /** Newest first; an entry that does not parse is dropped rather than failing every reader of
     * the flow — a preference file is user data and must never be able to crash the app. */
    private fun parse(raw: Set<String>): List<Bookmark> = raw.mapNotNull { entry ->
        val parts = entry.split(':')
        if (parts.size != 3) return@mapNotNull null
        val surah = parts[0].toIntOrNull() ?: return@mapNotNull null
        val ayah = parts[1].toIntOrNull() ?: return@mapNotNull null
        val at = parts[2].toLongOrNull() ?: return@mapNotNull null
        Bookmark(surah, ayah, at)
    }.sortedByDescending { it.createdAt }
}
