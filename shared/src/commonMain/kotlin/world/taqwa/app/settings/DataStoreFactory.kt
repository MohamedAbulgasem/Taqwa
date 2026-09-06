package world.taqwa.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

internal const val SETTINGS_FILE = "taqwa.preferences_pb"

expect fun dataStoreDirectory(): String

/**
 * DataStore forbids two live instances over one file; every consumer must share this one.
 * `AppContainer`, `SystemEventReceiver` and `BackgroundRefreshBridge` can all be alive in the
 * same process at once, so each must observe the exact same `DataStore` over
 * `taqwa.preferences_pb` rather than opening its own — `by lazy` (SYNCHRONIZED by default)
 * guarantees a single, thread-safe instance no matter which caller touches it first.
 */
private val sharedDataStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath { "${dataStoreDirectory()}/$SETTINGS_FILE".toPath() }
}

fun createDataStore(): DataStore<Preferences> = sharedDataStore
