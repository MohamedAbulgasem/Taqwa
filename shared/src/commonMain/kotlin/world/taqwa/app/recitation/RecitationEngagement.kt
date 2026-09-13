package world.taqwa.app.recitation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import world.taqwa.app.settings.SettingsKeys

/**
 * The one fact the catalogue refresh is gated on (privacy spec §2.1): has this person ever used
 * recitation. Until they have, the app makes no network request at all, and the privacy policy
 * says so in those words.
 *
 * Engaged is the stored flag, or — for an install from 0.11.0, which wrote no flag, and for a
 * reinstall over files left behind — any surah in the download registry. The flag is read first
 * (one DataStore read) and the registry only when the flag is unset.
 */
class RecitationEngagement(
    private val store: DataStore<Preferences>,
    private val library: RecitationLibrary,
) {
    suspend fun isEngaged(): Boolean {
        if (store.data.first()[SettingsKeys.RECITATION_ENGAGED] == true) return true
        return library.hasAnyDownloads()
    }

    /** Idempotent: the flag is written once and never cleared. */
    suspend fun mark() {
        if (store.data.first()[SettingsKeys.RECITATION_ENGAGED] == true) return
        store.edit { it[SettingsKeys.RECITATION_ENGAGED] = true }
    }
}
