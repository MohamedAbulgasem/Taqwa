package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import world.taqwa.app.adhkar.AdhkarProgress
import world.taqwa.app.adhkar.AdhkarSet

/**
 * Where the Morning & Evening Adhkar keep their place (spec §3.3) and their one setting.
 *
 * The values are handed back **raw**, as the strings [AdhkarProgress.encode] wrote: whether a
 * stored string is still today's, and whether it still fits the set, is decided by
 * [AdhkarProgress.forDate] where the date and the page count are known. Nothing is aggregated and
 * nothing is kept past the day — a new date overwrites, by design.
 *
 * An interface so that the adhkar view model's tests can use a memory fake; the app's is
 * [DataStoreAdhkarStore].
 */
interface AdhkarStore {
    /** [set]'s stored progress, `"<date>|c1,c2,…"`, or null before anything has been counted. */
    fun progress(set: AdhkarSet): Flow<String?>

    /** Writes [progress] as the set's one stored value, replacing whatever date was there. */
    suspend fun save(set: AdhkarSet, progress: AdhkarProgress)

    /** The daily istighfar, `"<date>|n"` — one counter for both sets — or null. */
    fun istighfar(): Flow<String?>

    suspend fun saveIstighfar(date: LocalDate, count: Int)

    /** Whether the translation shows under the Arabic; off until the reader switches it on. */
    val translation: Flow<Boolean>

    suspend fun setTranslation(on: Boolean)
}

/** The app's [AdhkarStore]: the process-wide DataStore beside [TasbeehStore] and [BookmarkStore]. */
class DataStoreAdhkarStore(private val store: DataStore<Preferences>) : AdhkarStore {
    override fun progress(set: AdhkarSet): Flow<String?> = store.data.map { prefs ->
        when (set) {
            AdhkarSet.MORNING -> prefs[SettingsKeys.ADHKAR_MORNING]
            AdhkarSet.EVENING -> prefs[SettingsKeys.ADHKAR_EVENING]
        }
    }

    override suspend fun save(set: AdhkarSet, progress: AdhkarProgress) {
        val key = when (set) {
            AdhkarSet.MORNING -> SettingsKeys.ADHKAR_MORNING
            AdhkarSet.EVENING -> SettingsKeys.ADHKAR_EVENING
        }
        store.edit { it[key] = progress.encode() }
    }

    override fun istighfar(): Flow<String?> = store.data.map { it[SettingsKeys.ADHKAR_ISTIGHFAR] }

    override suspend fun saveIstighfar(date: LocalDate, count: Int) {
        store.edit { it[SettingsKeys.ADHKAR_ISTIGHFAR] = AdhkarProgress(date, listOf(count)).encode() }
    }

    override val translation: Flow<Boolean> = store.data.map { it[SettingsKeys.ADHKAR_TRANSLATION] ?: false }

    override suspend fun setTranslation(on: Boolean) {
        store.edit { it[SettingsKeys.ADHKAR_TRANSLATION] = on }
    }
}
